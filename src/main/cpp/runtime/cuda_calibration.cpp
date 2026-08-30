#include "cuda_calibration.h"

#include "../algos.h"
#include "../bigmath_ffm.h"
#include "../cuda_ntt.h"

#include <algorithm>
#include <array>
#include <chrono>
#include <cstdint>
#include <limits>
#include <utility>
#include <vector>

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

CudaCalibrationProfile calibrate_cuda(uint64_t budget_millis) {
	CudaCalibrationProfile profile;
#if !defined(BIGMATH_HAS_CUDA) || !defined(BIGMATH_HAS_GMP)
	(void)budget_millis;
	return profile;
#else
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
	return profile;
#endif
}

}
