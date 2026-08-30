#include "cuda_calibration.h"
#include "cuda_calibration_cache.h"

#include "../algos.h"
#include "../bigmath_ffm.h"
#include "../cuda_ntt.h"

#include <algorithm>
#include <array>
#include <chrono>
#include <cstdio>
#include <cstring>
#include <cstdint>
#include <exception>
#include <iomanip>
#include <limits>
#include <memory>
#include <sstream>
#include <string>
#include <utility>
#include <vector>

#if defined(BIGMATH_HAS_CUDA)
#include <cuda_runtime.h>
#endif

#if defined(_MSC_VER) && (defined(_M_X64) || defined(_M_IX86))
#include <intrin.h>
#elif defined(__GNUC__) && (defined(__x86_64__) || defined(__i386__))
#include <cpuid.h>
#endif

namespace bigmath::runtime {
namespace {

using Clock = std::chrono::steady_clock;

constexpr std::array<uint64_t, 6> CANDIDATE_BITS = {
	UINT64_C(131072), UINT64_C(262144), UINT64_C(524288),
	UINT64_C(1048576), UINT64_C(2097152), UINT64_C(4194304)
};
constexpr std::array<uint64_t, 4> RATIOS = {1, 2, 8, 64};
constexpr int WINDOWS = 3;

#if defined(BIGMATH_HAS_CUDA) && defined(BIGMATH_HAS_GMP)

std::string cpu_identity() {
	std::array<char, 49> brand{};
#if defined(_MSC_VER) && (defined(_M_X64) || defined(_M_IX86))
	int registers[4]{};
	__cpuid(registers, static_cast<int>(UINT32_C(0x80000000)));
	const uint32_t maximum = static_cast<uint32_t>(registers[0]);
	if (maximum >= UINT32_C(0x80000004)) {
		for (uint32_t leaf = UINT32_C(0x80000002); leaf <= UINT32_C(0x80000004); leaf++) {
			__cpuid(registers, static_cast<int>(leaf));
			std::memcpy(
				brand.data() + static_cast<size_t>(leaf - UINT32_C(0x80000002)) * 16,
				registers,
				16
			);
		}
	}
#elif defined(__GNUC__) && (defined(__x86_64__) || defined(__i386__))
	const unsigned maximum = __get_cpuid_max(UINT32_C(0x80000000), nullptr);
	if (maximum >= UINT32_C(0x80000004)) {
		for (unsigned leaf = UINT32_C(0x80000002); leaf <= UINT32_C(0x80000004); leaf++) {
			unsigned eax = 0;
			unsigned ebx = 0;
			unsigned ecx = 0;
			unsigned edx = 0;
			__cpuid(leaf, eax, ebx, ecx, edx);
			const std::array<unsigned, 4> registers = {eax, ebx, ecx, edx};
			std::memcpy(
				brand.data() + static_cast<size_t>(leaf - UINT32_C(0x80000002)) * 16,
				registers.data(),
				16
			);
		}
	}
#endif
	std::string result(brand.data());
	const size_t start = result.find_first_not_of(' ');
	if (start == std::string::npos) return "unknown";
	const size_t end = result.find_last_not_of(' ');
	return result.substr(start, end - start + 1);
}

std::string calibration_cache_key(uint64_t budget_millis, uint64_t workspace_budget_bytes) {
	int device = -1;
	if (cudaGetDevice(&device) != cudaSuccess || device < 0) return {};
	cudaDeviceProp properties{};
	if (cudaGetDeviceProperties(&properties, device) != cudaSuccess) return {};
	int driver_version = 0;
	int runtime_version = 0;
	if (cudaDriverGetVersion(&driver_version) != cudaSuccess ||
			cudaRuntimeGetVersion(&runtime_version) != cudaSuccess) {
		return {};
	}

	std::ostringstream key;
	key << "schema=1"
		<< "\nabi=" << bigmath_abi_version()
		<< "\nbuild=" << bigmath_build_id()
		<< "\ndevice=";
	key << std::hex << std::setfill('0');
	for (char byte : properties.uuid.bytes) {
		key << std::setw(2) << static_cast<unsigned>(static_cast<unsigned char>(byte));
	}
	key << std::dec
		<< "\ncompute=" << properties.major << '.' << properties.minor
		<< "\ndriver=" << driver_version
		<< "\nruntime=" << runtime_version
		<< "\ncpu=" << cpu_identity()
		<< "\ngmp=" << gmp_version
		<< "\nmpfr=" << mpfr_get_version()
		<< "\nbudgetMillis=" << budget_millis
		<< "\nworkspaceBytes=" << workspace_budget_bytes;
	return key.str();
}

class ScopedMpz {
public:
	ScopedMpz() { mpz_init(value_); }
	~ScopedMpz() { mpz_clear(value_); }
	mpz_ptr get() { return value_; }
	mpz_srcptr get() const { return value_; }

	ScopedMpz(const ScopedMpz &) = delete;
	ScopedMpz &operator=(const ScopedMpz &) = delete;

private:
	mpz_t value_;
};

void deterministic_value(mpz_ptr out, uint64_t bits, uint64_t seed) {
	const size_t limbs = static_cast<size_t>((bits + 63) / 64);
	std::vector<uint64_t> data(std::max<size_t>(1, limbs));
	uint64_t state = seed;
	for (uint64_t &limb : data) {
		state ^= state >> 12;
		state ^= state << 25;
		state ^= state >> 27;
		limb = state * UINT64_C(2685821657736338717);
	}
	const unsigned top_bits = static_cast<unsigned>((bits - 1) % 64 + 1);
	if (top_bits < 64) data.back() &= (UINT64_C(1) << top_bits) - 1;
	data.back() |= UINT64_C(1) << (top_bits - 1);
	mpz_import(out, data.size(), -1, sizeof(uint64_t), 0, 0, data.data());
}

template<typename Operation>
std::pair<bool, uint64_t> timed(Operation operation) {
	const auto started = Clock::now();
	const bool completed = operation();
	const uint64_t nanos = static_cast<uint64_t>(
		std::chrono::duration_cast<std::chrono::nanoseconds>(Clock::now() - started).count()
	);
	return {completed, nanos};
}

uint64_t average(const std::array<uint64_t, WINDOWS> &values) {
	uint64_t total = 0;
	for (uint64_t value : values) total += value / WINDOWS;
	return total;
}

uint64_t warm_average(const std::array<uint64_t, WINDOWS> &values) {
	return (values[1] / 2) + (values[2] / 2);
}

int transform_log2(uint64_t left_bits, uint64_t right_bits) {
	const uint64_t digits = (left_bits + 15) / 16 + (right_bits + 15) / 16 - 1;
	uint64_t transform = 1;
	int log = 0;
	while (transform < digits && log + 1 < static_cast<int>(CUDA_TRANSFORM_BUCKETS)) {
		transform <<= 1;
		log++;
	}
	return transform < digits ? -1 : log;
}

bool measure_case(
		mpz_ptr left,
		mpz_ptr right,
		bool square,
		int transform_log,
		DispatchProfileCell &cell
) {
	ScopedMpz expected;
	ScopedMpz actual;
	std::array<uint64_t, WINDOWS> cpu_times{};
	std::array<uint64_t, WINDOWS> cufft_times{};
	std::array<uint64_t, WINDOWS> ntt_times{};
	bool cufft_correct = true;
	bool ntt_correct = (UINT64_C(1) << transform_log) <=
		static_cast<uint64_t>(bigmath::cuda::NTT_MAX_TRANSFORM_SIZE);

	for (int window = 0; window < WINDOWS; window++) {
		const auto cpu = timed([&] {
			mpz_mul(expected.get(), left, square ? left : right);
			return true;
		});
		cpu_times[window] = cpu.second;

		const auto cufft = timed([&] {
			return bigmath::cuda_multiply_direct(
				actual.get(),
				left,
				square ? left : right,
				bigmath::DirectCudaBackend::CUFFT,
				0,
				window != 0,
				window == 0
			);
		});
		cufft_times[window] = cufft.second;
		cufft_correct &= cufft.first && mpz_cmp(actual.get(), expected.get()) == 0;
		if (window == 0 && cufft_correct) {
			const bool warmed = bigmath::cuda_multiply_direct(
				actual.get(),
				left,
				square ? left : right,
				bigmath::DirectCudaBackend::CUFFT,
				0,
				true,
				false
			);
			cufft_correct &= warmed && mpz_cmp(actual.get(), expected.get()) == 0;
		}

		if (ntt_correct) {
			const auto ntt = timed([&] {
				return bigmath::cuda_multiply_direct(
					actual.get(),
					left,
					square ? left : right,
					bigmath::DirectCudaBackend::NTT,
					0,
					false,
					window == 0
				);
			});
			ntt_times[window] = ntt.second;
			ntt_correct &= cufft.first && ntt.first &&
				mpz_cmp(actual.get(), expected.get()) == 0 &&
				ntt.second <= cufft.second - cufft.second / 10;
		}
	}

	cell.measured = true;
	cell.cpu_nanos = average(cpu_times);
	cell.cufft_cold_nanos = cufft_times[0];
	cell.cufft_nanos = warm_average(cufft_times);
	cell.ntt_cold_nanos = ntt_correct ? ntt_times[0] : 0;
	cell.ntt_nanos = ntt_correct ? warm_average(ntt_times) : 0;
	if (ntt_correct && cell.ntt_nanos < cell.cpu_nanos) {
		cell.backend = CalibratedBackend::NTT;
	} else if (cufft_correct && cell.cufft_nanos < cell.cpu_nanos) {
		cell.backend = CalibratedBackend::CUFFT;
	} else {
		cell.backend = CalibratedBackend::CPU;
	}
	return cufft_correct;
}

#endif

}

CudaCalibrationProfile calibrate_cuda(uint64_t budget_millis, uint64_t workspace_budget_bytes) {
	CudaCalibrationProfile profile;
#if !defined(BIGMATH_HAS_CUDA) || !defined(BIGMATH_HAS_GMP)
	(void)budget_millis;
	(void)workspace_budget_bytes;
	return profile;
#else
	std::unique_ptr<CudaCalibrationCache> cache;
	try {
		const std::string key = calibration_cache_key(budget_millis, workspace_budget_bytes);
		if (!key.empty()) {
			cache = std::make_unique<CudaCalibrationCache>(key);
			if (cache->load(profile)) return profile;
		}
	} catch (const std::exception &exception) {
		std::fprintf(stderr, "Bigmath CUDA calibration cache unavailable: %s\n", exception.what());
		cache.reset();
	} catch (...) {
		std::fprintf(stderr, "Bigmath CUDA calibration cache unavailable\n");
		cache.reset();
	}

	const uint64_t bounded_millis = std::min<uint64_t>(budget_millis, 60000);
	const auto deadline = Clock::now() + std::chrono::milliseconds(bounded_millis);
	bool measured = false;
	for (uint64_t max_bits : CANDIDATE_BITS) {
		for (size_t shape = 0; shape < RATIOS.size(); shape++) {
			if (Clock::now() >= deadline) break;
			const uint64_t min_bits = std::max<uint64_t>(16, max_bits / RATIOS[shape]);
			const int transform = transform_log2(max_bits, min_bits);
			if (transform < 0) continue;
			ScopedMpz left;
			ScopedMpz right;
			deterministic_value(left.get(), max_bits, UINT64_C(0x9e3779b97f4a7c15) ^ max_bits ^ shape);
			deterministic_value(right.get(), min_bits, UINT64_C(0xbf58476d1ce4e5b9) ^ max_bits ^ shape);
			DispatchProfileCell &cell = profile.cells[shape][static_cast<size_t>(transform)];
			if (!measure_case(left.get(), right.get(), false, transform, cell)) continue;
			measured = true;
			if (cell.backend != CalibratedBackend::CPU && profile.threshold_bits[shape] == UINT64_MAX) {
				profile.threshold_bits[shape] = max_bits;
			}
			if (cell.backend == CalibratedBackend::NTT) {
				profile.ntt_transform_mask |= UINT32_C(1) << transform;
			}
		}

		if (Clock::now() >= deadline) break;
		const int square_transform = transform_log2(max_bits, max_bits);
		if (square_transform >= 0) {
			ScopedMpz square;
			deterministic_value(square.get(), max_bits, UINT64_C(0x94d049bb133111eb) ^ max_bits);
			DispatchProfileCell &cell = profile.cells[4][static_cast<size_t>(square_transform)];
			if (measure_case(square.get(), square.get(), true, square_transform, cell)) {
				measured = true;
				if (cell.backend != CalibratedBackend::CPU && profile.square_threshold_bits == UINT64_MAX) {
					profile.square_threshold_bits = max_bits;
				}
				if (cell.backend == CalibratedBackend::NTT) {
					profile.ntt_transform_mask |= UINT32_C(1) << square_transform;
				}
			}
		}
	}
	profile.completed = measured;
	if (profile.completed && cache != nullptr) {
		try {
			cache->store(profile);
		} catch (const std::exception &exception) {
			std::fprintf(stderr, "Bigmath CUDA calibration cache write failed: %s\n", exception.what());
		} catch (...) {
			std::fprintf(stderr, "Bigmath CUDA calibration cache write failed\n");
		}
	}
	return profile;
#endif
}

}
