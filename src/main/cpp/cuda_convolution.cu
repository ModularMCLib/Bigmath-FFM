#include "cuda_convolution.h"

#include <algorithm>
#include <chrono>
#include <cmath>
#include <condition_variable>
#include <cstdint>
#include <cstring>
#include <limits>
#include <memory>
#include <mutex>
#include <utility>
#include <vector>

#include <cuda_runtime.h>
#include <cufft.h>

namespace bigmath::cuda {

static constexpr int SPECTRUM_CACHE_WAYS = 4;

__global__ static void pointwise_multiply(cufftDoubleComplex *__restrict__ left,
		const cufftDoubleComplex *__restrict__ right,
		int n) {
	const int i = blockIdx.x * blockDim.x + threadIdx.x;
	if (i >= n) {
		return;
	}
	const double real = left[i].x * right[i].x - left[i].y * right[i].y;
	const double imag = left[i].x * right[i].y + left[i].y * right[i].x;
	left[i].x = real;
	left[i].y = imag;
}

__global__ static void load_u16_digits(const uint16_t *__restrict__ digits,
		int digit_count,
		double *__restrict__ out,
		int n) {
	const int i = blockIdx.x * blockDim.x + threadIdx.x;
	if (i >= n) {
		return;
	}
	out[i] = i < digit_count ? static_cast<double>(digits[i]) : 0.0;
}

static bool next_pow2(size_t value, int &out) {
	if (value > static_cast<size_t>(std::numeric_limits<int>::max() / 2)) {
		return false;
	}
	int p = 1;
	while (static_cast<size_t>(p) < value) {
		p <<= 1;
	}
	out = p;
	return true;
}

static bool coefficients_fit_double(size_t result_size, unsigned bits_per_digit) {
	if (bits_per_digit == 0 || bits_per_digit >= 32) {
		return false;
	}
	const long double max_digit = static_cast<long double>((uint64_t{1} << bits_per_digit) - 1);
	const long double max_coefficient = static_cast<long double>(result_size) * max_digit * max_digit;
	return max_coefficient < static_cast<long double>(uint64_t{1} << 50);
}

static bool round_nonnegative_to_u64(double value, uint64_t &out) {
	if (!std::isfinite(value) || value <= -0.5 || value >= std::ldexp(1.0, 64)) {
		return false;
	}
	out = static_cast<uint64_t>(value + 0.5);
	return true;
}

struct CudaConvolutionWorkspace {
	struct CachedSpectrum {
		int n = 0;
		unsigned bits_per_digit = 0;
		bool ready = false;
		uint64_t last_used = 0;
		std::vector<uint16_t> digits;

		void clear() {
			n = 0;
			bits_per_digit = 0;
			ready = false;
			last_used = 0;
			digits.clear();
		}

		bool matches(const std::vector<uint16_t> &value, int candidate_n, unsigned candidate_bits_per_digit) const {
			return ready &&
					n == candidate_n &&
					bits_per_digit == candidate_bits_per_digit &&
					digits.size() == value.size() &&
					std::memcmp(digits.data(), value.data(), sizeof(uint16_t) * value.size()) == 0;
		}

		void store(const std::vector<uint16_t> &value,
				int candidate_n,
				unsigned candidate_bits_per_digit,
				uint64_t use_tick) {
			digits = value;
			n = candidate_n;
			bits_per_digit = candidate_bits_per_digit;
			last_used = use_tick;
			ready = true;
		}
	};

	double *da = nullptr;
	double *db = nullptr;
	uint16_t *digits_a = nullptr;
	uint16_t *digits_b = nullptr;
	cufftDoubleComplex *fa = nullptr;
	cufftDoubleComplex *fb = nullptr;
	cufftDoubleComplex *cached_fa[SPECTRUM_CACHE_WAYS] = {};
	cufftDoubleComplex *cached_fb[SPECTRUM_CACHE_WAYS] = {};
	cudaStream_t stream = nullptr;
	cufftHandle forward_plan = 0;
	cufftHandle inverse_plan = 0;
	void *fft_work = nullptr;
	size_t fft_work_bytes = 0;
	int plan_size = 0;
	int capacity = 0;
	int spectrum_cache_capacity = 0;
	uint64_t spectrum_cache_tick = 0;
	uint16_t *host_digits_a = nullptr;
	uint16_t *host_digits_b = nullptr;
	int host_digits_capacity = 0;
	double *host_result = nullptr;          // pinned (page-locked) staging for fast D2H
	int host_result_capacity = 0;
	size_t base_device_bytes = 0;
	size_t spectrum_device_bytes = 0;
	CachedSpectrum cache_a[SPECTRUM_CACHE_WAYS];
	CachedSpectrum cache_b[SPECTRUM_CACHE_WAYS];

	~CudaConvolutionWorkspace() {
		release();
	}

	void release_plan() {
		if (forward_plan != 0) {
			cufftDestroy(forward_plan);
			forward_plan = 0;
		}
		if (inverse_plan != 0) {
			cufftDestroy(inverse_plan);
			inverse_plan = 0;
		}
		cudaFree(fft_work);
		fft_work = nullptr;
		fft_work_bytes = 0;
		plan_size = 0;
	}

	void release_spectrum_cache() {
		for (int i = 0; i < SPECTRUM_CACHE_WAYS; i++) {
			cudaFree(cached_fa[i]);
			cudaFree(cached_fb[i]);
			cached_fa[i] = nullptr;
			cached_fb[i] = nullptr;
			cache_a[i].clear();
			cache_b[i].clear();
		}
		spectrum_cache_capacity = 0;
		spectrum_device_bytes = 0;
		spectrum_cache_tick = 0;
	}

	void release() {
		release_plan();
		release_spectrum_cache();
		cudaFree(da);
		cudaFree(db);
		cudaFree(digits_a);
		cudaFree(digits_b);
		cudaFree(fa);
		cudaFree(fb);
		da = nullptr;
		db = nullptr;
		digits_a = nullptr;
		digits_b = nullptr;
		fa = nullptr;
		fb = nullptr;
		capacity = 0;
		base_device_bytes = 0;
		cudaFreeHost(host_digits_a);
		cudaFreeHost(host_digits_b);
		host_digits_a = nullptr;
		host_digits_b = nullptr;
		host_digits_capacity = 0;
		cudaFreeHost(host_result);
		host_result = nullptr;
		host_result_capacity = 0;
		if (stream != nullptr) {
			cudaStreamDestroy(stream);
			stream = nullptr;
		}
	}

	size_t device_bytes() const {
		return base_device_bytes + spectrum_device_bytes + fft_work_bytes;
	}

	bool ensure_stream() {
		return stream != nullptr || cudaStreamCreateWithFlags(&stream, cudaStreamNonBlocking) == cudaSuccess;
	}

	bool ensure_capacity(int n) {
		if (capacity >= n) {
			return true;
		}
		release();
		if (!ensure_stream()) return false;
		const int spectrum_size = n / 2 + 1;
		if (cudaMalloc(&da, sizeof(double) * n) != cudaSuccess) {
			release();
			return false;
		}
		if (cudaMalloc(&db, sizeof(double) * n) != cudaSuccess) {
			release();
			return false;
		}
		if (cudaMalloc(&digits_a, sizeof(uint16_t) * n) != cudaSuccess) {
			release();
			return false;
		}
		if (cudaMalloc(&digits_b, sizeof(uint16_t) * n) != cudaSuccess) {
			release();
			return false;
		}
		if (cudaMalloc(&fa, sizeof(cufftDoubleComplex) * spectrum_size) != cudaSuccess) {
			release();
			return false;
		}
		if (cudaMalloc(&fb, sizeof(cufftDoubleComplex) * spectrum_size) != cudaSuccess) {
			release();
			return false;
		}
		capacity = n;
		base_device_bytes = sizeof(double) * static_cast<size_t>(n) * 2 +
			sizeof(uint16_t) * static_cast<size_t>(n) * 2 +
			sizeof(cufftDoubleComplex) * static_cast<size_t>(spectrum_size) * 2;
		return true;
	}

	bool ensure_spectrum_cache_capacity(int n) {
		if (spectrum_cache_capacity >= n) {
			return true;
		}
		release_spectrum_cache();
		const int spectrum_size = n / 2 + 1;
		for (int i = 0; i < SPECTRUM_CACHE_WAYS; i++) {
			if (cudaMalloc(&cached_fa[i], sizeof(cufftDoubleComplex) * spectrum_size) != cudaSuccess) {
				release_spectrum_cache();
				return false;
			}
			if (cudaMalloc(&cached_fb[i], sizeof(cufftDoubleComplex) * spectrum_size) != cudaSuccess) {
				release_spectrum_cache();
				return false;
			}
		}
		spectrum_cache_capacity = n;
		spectrum_device_bytes = sizeof(cufftDoubleComplex) *
			static_cast<size_t>(spectrum_size) * 2 * SPECTRUM_CACHE_WAYS;
		return true;
	}

	bool ensure_plan(int n) {
		if (plan_size == n && forward_plan != 0 && inverse_plan != 0) {
			return true;
		}
		release_plan();
		if (!ensure_stream() ||
				cufftCreate(&forward_plan) != CUFFT_SUCCESS ||
				cufftCreate(&inverse_plan) != CUFFT_SUCCESS ||
				cufftSetAutoAllocation(forward_plan, 0) != CUFFT_SUCCESS ||
				cufftSetAutoAllocation(inverse_plan, 0) != CUFFT_SUCCESS) {
			release_plan();
			return false;
		}
		size_t forward_work = 0;
		size_t inverse_work = 0;
		if (cufftMakePlan1d(forward_plan, n, CUFFT_D2Z, 1, &forward_work) != CUFFT_SUCCESS ||
				cufftMakePlan1d(inverse_plan, n, CUFFT_Z2D, 1, &inverse_work) != CUFFT_SUCCESS) {
			release_plan();
			return false;
		}
		fft_work_bytes = std::max(forward_work, inverse_work);
		if (fft_work_bytes != 0 && cudaMalloc(&fft_work, fft_work_bytes) != cudaSuccess) {
			release_plan();
			return false;
		}
		if (cufftSetWorkArea(forward_plan, fft_work) != CUFFT_SUCCESS ||
				cufftSetWorkArea(inverse_plan, fft_work) != CUFFT_SUCCESS ||
				cufftSetStream(forward_plan, stream) != CUFFT_SUCCESS ||
				cufftSetStream(inverse_plan, stream) != CUFFT_SUCCESS) {
			release_plan();
			return false;
		}
		plan_size = n;
		return true;
	}

	// Allocate the D2H result staging buffer in page-locked (pinned) host memory
	// so the device->host copy runs at full PCIe bandwidth instead of going
	// through a pageable bounce buffer.
	bool ensure_host_digits(int n) {
		if (host_digits_capacity >= n) return true;
		cudaFreeHost(host_digits_a);
		cudaFreeHost(host_digits_b);
		host_digits_a = nullptr;
		host_digits_b = nullptr;
		host_digits_capacity = 0;
		if (cudaHostAlloc(
				reinterpret_cast<void **>(&host_digits_a),
				sizeof(uint16_t) * static_cast<size_t>(n),
				cudaHostAllocDefault
		) != cudaSuccess || cudaHostAlloc(
				reinterpret_cast<void **>(&host_digits_b),
				sizeof(uint16_t) * static_cast<size_t>(n),
				cudaHostAllocDefault
		) != cudaSuccess) {
			cudaFreeHost(host_digits_a);
			cudaFreeHost(host_digits_b);
			host_digits_a = nullptr;
			host_digits_b = nullptr;
			return false;
		}
		host_digits_capacity = n;
		return true;
	}

	bool ensure_host_result(size_t result_size) {
		if (host_result_capacity >= static_cast<int>(result_size)) {
			return true;
		}
		cudaFreeHost(host_result);
		host_result = nullptr;
		host_result_capacity = 0;
		if (cudaHostAlloc(reinterpret_cast<void **>(&host_result),
				sizeof(double) * result_size, cudaHostAllocDefault) != cudaSuccess) {
			host_result = nullptr;
			return false;
		}
		host_result_capacity = static_cast<int>(result_size);
		return true;
	}

};

class CudaWorkspacePool {
private:
	struct Slot {
		std::unique_ptr<CudaConvolutionWorkspace> workspace;
		int transform_size = 0;
		size_t device_bytes = 0;
		bool busy = false;
		uint64_t last_used = 0;
		std::chrono::steady_clock::time_point acquired_at{};
	};

public:
	class Lease {
	public:
		Lease() = default;
		Lease(CudaWorkspacePool *pool, Slot *slot) : pool_(pool), slot_(slot) {}
		~Lease() { release(); }

		Lease(const Lease &) = delete;
		Lease &operator=(const Lease &) = delete;

		Lease(Lease &&other) noexcept : pool_(other.pool_), slot_(other.slot_) {
			other.pool_ = nullptr;
		}

		Lease &operator=(Lease &&other) noexcept {
			if (this != &other) {
				release();
				pool_ = other.pool_;
				slot_ = other.slot_;
				other.pool_ = nullptr;
			}
			return *this;
		}

		explicit operator bool() const { return pool_ != nullptr; }

		CudaConvolutionWorkspace &workspace() {
			return *slot_->workspace;
		}

	private:
		void release() {
			if (pool_ != nullptr) {
				pool_->release(slot_);
				pool_ = nullptr;
			}
		}

		CudaWorkspacePool *pool_ = nullptr;
		Slot *slot_ = nullptr;
	};

	bool configure(int device, uint64_t budget_bytes) {
		std::lock_guard lock(mutex_);
		if (reserved_bytes_ != 0) return false;
		for (const auto &slot : slots_) {
			if (slot->busy) return false;
		}
		slots_.clear();
		allocated_bytes_ = 0;
		reserved_bytes_ = 0;
		device_ = device;
		budget_bytes_ = static_cast<size_t>(std::min<uint64_t>(budget_bytes, SIZE_MAX));
		configured_ = true;
		return true;
	}

	Lease acquire(int n, bool cache_spectra, uint64_t max_wait_nanos) {
		const auto deadline = std::chrono::steady_clock::now() +
			std::chrono::nanoseconds(max_wait_nanos);
		for (;;) {
			std::unique_lock lock(mutex_);
			if (!ensure_default_configuration(lock)) return Lease{};
			for (size_t index = 0; index < slots_.size(); index++) {
				Slot &slot = *slots_[index];
				if (!slot.busy && slot.transform_size == n &&
						(!cache_spectra || slot.workspace->spectrum_cache_capacity >= n)) {
					slot.busy = true;
					slot.acquired_at = std::chrono::steady_clock::now();
					slot.last_used = next_tick();
					if (cudaSetDevice(device_) != cudaSuccess) {
						slot.busy = false;
						return Lease{};
					}
					return Lease(this, &slot);
				}
			}

			const size_t estimate = estimate_device_bytes(n, cache_spectra);
			while (allocated_bytes_ + reserved_bytes_ > budget_bytes_ - std::min(estimate, budget_bytes_)) {
				if (!evict_idle_lru()) break;
			}
			if (estimate <= budget_bytes_ &&
					allocated_bytes_ + reserved_bytes_ <= budget_bytes_ - estimate) {
				reserved_bytes_ += estimate;
				const int device = device_;
				lock.unlock();
				auto workspace = std::make_unique<CudaConvolutionWorkspace>();
				const bool initialized = cudaSetDevice(device) == cudaSuccess &&
					workspace->ensure_capacity(n) &&
					workspace->ensure_plan(n) &&
					workspace->ensure_host_digits(n) &&
					workspace->ensure_host_result(static_cast<size_t>(n)) &&
					(!cache_spectra || workspace->ensure_spectrum_cache_capacity(n));
				const size_t actual = initialized ? workspace->device_bytes() : 0;
				lock.lock();
				reserved_bytes_ -= estimate;
				if (!initialized || actual > budget_bytes_ - allocated_bytes_) {
					lock.unlock();
					workspace.reset();
					lock.lock();
					condition_.notify_all();
					return Lease{};
				}
				auto slot = std::make_unique<Slot>();
				slot->workspace = std::move(workspace);
				slot->transform_size = n;
				slot->device_bytes = actual;
				slot->busy = true;
				slot->acquired_at = std::chrono::steady_clock::now();
				slot->last_used = next_tick();
				allocated_bytes_ += actual;
				Slot *acquired_slot = slot.get();
				slots_.push_back(std::move(slot));
				return Lease(this, acquired_slot);
			}

			if (max_wait_nanos == 0 ||
					condition_.wait_until(lock, deadline) == std::cv_status::timeout) {
				return Lease{};
			}
		}
	}

	uint64_t budget_bytes() const {
		std::lock_guard lock(mutex_);
		return budget_bytes_;
	}

	uint64_t in_use_bytes() const {
		std::lock_guard lock(mutex_);
		uint64_t bytes = 0;
		for (const auto &slot : slots_) {
			if (slot->busy) bytes += slot->device_bytes;
		}
		return bytes;
	}

	int capacity() const {
		std::lock_guard lock(mutex_);
		return static_cast<int>(slots_.size());
	}

	int in_use() const {
		std::lock_guard lock(mutex_);
		return static_cast<int>(std::count_if(slots_.begin(), slots_.end(), [](const auto &slot) {
			return slot->busy;
		}));
	}

	bool available(int n) const {
		std::lock_guard lock(mutex_);
		for (const auto &slot : slots_) {
			if (!slot->busy && slot->transform_size == n) return true;
		}
		const size_t estimate = estimate_device_bytes(n, true);
		return configured_ && estimate <= budget_bytes_ &&
			allocated_bytes_ + reserved_bytes_ <= budget_bytes_ - estimate;
	}

private:
	bool ensure_default_configuration(std::unique_lock<std::mutex> &) {
		if (configured_) return true;
		int device = 0;
		size_t free_bytes = 0;
		size_t total_bytes = 0;
		if (cudaGetDevice(&device) != cudaSuccess ||
				cudaMemGetInfo(&free_bytes, &total_bytes) != cudaSuccess) {
			return false;
		}
		device_ = device;
		budget_bytes_ = std::min<size_t>(free_bytes / 4, 512 * 1024 * 1024);
		configured_ = true;
		return true;
	}

	static size_t estimate_device_bytes(int n, bool cache_spectra) {
		const size_t count = static_cast<size_t>(n);
		const size_t spectrum = count / 2 + 1;
		const size_t base = sizeof(double) * count * 2 +
			sizeof(uint16_t) * count * 2 +
			sizeof(cufftDoubleComplex) * spectrum * 2;
		const size_t cached = cache_spectra
			? sizeof(cufftDoubleComplex) * spectrum * 2 * SPECTRUM_CACHE_WAYS
			: 0;
		const size_t conservative_fft_work = sizeof(cufftDoubleComplex) * count * 8;
		return base + cached + conservative_fft_work;
	}

	uint64_t next_tick() {
		if (++tick_ == 0) tick_ = 1;
		return tick_;
	}

	bool evict_idle_lru() {
		size_t target = slots_.size();
		for (size_t index = 0; index < slots_.size(); index++) {
			if (slots_[index]->busy) continue;
			if (target == slots_.size() || slots_[index]->last_used < slots_[target]->last_used) {
				target = index;
			}
		}
		if (target == slots_.size()) return false;
		allocated_bytes_ -= slots_[target]->device_bytes;
		slots_.erase(slots_.begin() + static_cast<std::ptrdiff_t>(target));
		return true;
	}

	void release(Slot *slot) {
		cudaError_t synchronized = cudaErrorInvalidResourceHandle;
		if (slot != nullptr && cudaSetDevice(device_) == cudaSuccess) {
			synchronized = cudaStreamSynchronize(slot->workspace->stream);
		}
		std::lock_guard lock(mutex_);
		if (slot != nullptr && synchronized == cudaSuccess) {
			slot->busy = false;
			slot->last_used = next_tick();
		} else if (slot != nullptr) {
			for (auto iterator = slots_.begin(); iterator != slots_.end(); ++iterator) {
				if (iterator->get() == slot) {
					allocated_bytes_ -= slot->device_bytes;
					slots_.erase(iterator);
					break;
				}
			}
		}
		condition_.notify_one();
	}

	mutable std::mutex mutex_;
	std::condition_variable condition_;
	std::vector<std::unique_ptr<Slot>> slots_;
	bool configured_ = false;
	int device_ = 0;
	size_t budget_bytes_ = 0;
	size_t allocated_bytes_ = 0;
	size_t reserved_bytes_ = 0;
	uint64_t tick_ = 0;
};

CudaWorkspacePool &workspace_pool() {
	static CudaWorkspacePool pool;
	return pool;
}

static bool prepare_u16_spectrum(const std::vector<uint16_t> &digits,
		uint16_t *host_digits,
		uint16_t *device_digits,
		double *device_values,
		cufftDoubleComplex *spectrum,
		cufftDoubleComplex *const *cached_spectra,
		CudaConvolutionWorkspace::CachedSpectrum *caches,
		uint64_t &cache_tick,
		cufftHandle forward_plan,
		cudaStream_t stream,
		int n,
		unsigned bits_per_digit,
		int block_size) {
	const int spectrum_size = n / 2 + 1;
	const size_t spectrum_bytes = sizeof(cufftDoubleComplex) * static_cast<size_t>(spectrum_size);
	uint64_t use_tick = 0;
	if (cached_spectra != nullptr && caches != nullptr) {
		cache_tick++;
		if (cache_tick == 0) {
			cache_tick = 1;
		}
		use_tick = cache_tick;
		for (int i = 0; i < SPECTRUM_CACHE_WAYS; i++) {
			if (cached_spectra[i] != nullptr && caches[i].matches(digits, n, bits_per_digit)) {
				caches[i].last_used = use_tick;
				return cudaMemcpyAsync(
					spectrum,
					cached_spectra[i],
					spectrum_bytes,
					cudaMemcpyDeviceToDevice,
					stream
				) == cudaSuccess;
			}
		}
	}
	std::memcpy(host_digits, digits.data(), sizeof(uint16_t) * digits.size());
	if (cudaMemcpyAsync(
			device_digits,
			host_digits,
			sizeof(uint16_t) * digits.size(),
			cudaMemcpyHostToDevice,
			stream
	) != cudaSuccess) {
		return false;
	}
	const int grid_size = (n + block_size - 1) / block_size;
	load_u16_digits<<<grid_size, block_size, 0, stream>>>(
		device_digits,
		static_cast<int>(digits.size()),
		device_values,
		n
	);
	if (cudaGetLastError() != cudaSuccess) {
		return false;
	}
	if (cufftExecD2Z(forward_plan, device_values, spectrum) != CUFFT_SUCCESS) {
		return false;
	}
	if (cached_spectra == nullptr || caches == nullptr) {
		return true;
	}
	int target = 0;
	for (int i = 0; i < SPECTRUM_CACHE_WAYS; i++) {
		if (!caches[i].ready) {
			target = i;
			break;
		}
		if (caches[i].last_used < caches[target].last_used) {
			target = i;
		}
	}
	if (cudaMemcpyAsync(
			cached_spectra[target],
			spectrum,
			spectrum_bytes,
			cudaMemcpyDeviceToDevice,
			stream
	) != cudaSuccess) {
		return false;
	}
	caches[target].store(digits, n, bits_per_digit, use_tick);
	return true;
}

static bool collect_u16_digits_result(const double *host_result,
		size_t result_size,
		int n,
		std::vector<uint16_t> &out,
		unsigned bits_per_digit) {
	out.clear();
	out.reserve(result_size + 1);
	const double scale = 1.0 / static_cast<double>(n);
	uint64_t carry = 0;
	const uint64_t base_mask = (uint64_t{1} << bits_per_digit) - 1;
	for (size_t i = 0; i < result_size; i++) {
		uint64_t rounded = 0;
		if (!round_nonnegative_to_u64(host_result[i] * scale, rounded)) {
			return false;
		}
		const uint64_t value = rounded + carry;
		out.push_back(static_cast<uint16_t>(value & base_mask));
		carry = value >> bits_per_digit;
	}
	while (carry != 0) {
		out.push_back(static_cast<uint16_t>(carry & base_mask));
		carry >>= bits_per_digit;
	}
	while (out.size() > 1 && out.back() == 0) {
		out.pop_back();
	}
	return true;
}

static bool collect_u16_limb_result(const double *host_result,
		size_t result_size,
		int n,
		std::vector<uint64_t> &out,
		unsigned bits_per_digit,
		unsigned limb_bits) {
	if (bits_per_digit == 0 || bits_per_digit >= 32 ||
			limb_bits == 0 || limb_bits > 64 ||
			limb_bits % bits_per_digit != 0) {
		return false;
	}
	out.clear();
	const int digits_per_limb = static_cast<int>(limb_bits / bits_per_digit);
	out.reserve((result_size + static_cast<size_t>(digits_per_limb) - 1) /
			static_cast<size_t>(digits_per_limb) + 1);
	const double scale = 1.0 / static_cast<double>(n);
	uint64_t carry = 0;
	uint64_t limb = 0;
	int limb_digit = 0;
	const uint64_t base_mask = (uint64_t{1} << bits_per_digit) - 1;
	auto append_digit = [&](uint64_t digit) {
		limb |= digit << (bits_per_digit * limb_digit);
		limb_digit++;
		if (limb_digit == digits_per_limb) {
			out.push_back(limb);
			limb = 0;
			limb_digit = 0;
		}
	};
	for (size_t i = 0; i < result_size; i++) {
		uint64_t rounded = 0;
		if (!round_nonnegative_to_u64(host_result[i] * scale, rounded)) {
			return false;
		}
		const uint64_t value = rounded + carry;
		append_digit(value & base_mask);
		carry = value >> bits_per_digit;
	}
	while (carry != 0) {
		append_digit(carry & base_mask);
		carry >>= bits_per_digit;
	}
	if (limb_digit != 0) {
		out.push_back(limb);
	}
	while (!out.empty() && out.back() == 0) {
		out.pop_back();
	}
	return true;
}

static bool convolve_u16_digits_impl(const std::vector<uint16_t> &a,
		const std::vector<uint16_t> &b,
		std::vector<uint16_t> *digit_out,
		std::vector<uint64_t> *limb_out,
		unsigned bits_per_digit,
		unsigned limb_bits,
		uint64_t max_queue_wait_nanos) {
	if (a.empty() || b.empty()) {
		if (digit_out != nullptr) {
			digit_out->clear();
		}
		if (limb_out != nullptr) {
			limb_out->clear();
		}
		return true;
	}

	const size_t result_size = a.size() + b.size() - 1;
	int n = 0;
	if (!coefficients_fit_double(result_size, bits_per_digit) ||
			!next_pow2(result_size, n)) {
		return false;
	}
	static constexpr int SPECTRUM_CACHE_MIN_SIZE = 32768;
	const bool use_spectrum_cache = n >= SPECTRUM_CACHE_MIN_SIZE;
	CudaWorkspacePool::Lease lease = workspace_pool().acquire(
		n,
		use_spectrum_cache,
		max_queue_wait_nanos
	);
	if (!lease) return false;
	CudaConvolutionWorkspace &workspace = lease.workspace();

	const int block_size = 256;
	if (!prepare_u16_spectrum(a, workspace.host_digits_a, workspace.digits_a, workspace.da, workspace.fa,
				use_spectrum_cache ? workspace.cached_fa : nullptr,
				use_spectrum_cache ? workspace.cache_a : nullptr,
				workspace.spectrum_cache_tick,
				workspace.forward_plan, workspace.stream, n, bits_per_digit, block_size)) {
		return false;
	}

	const int spectrum_size = n / 2 + 1;
	const size_t spectrum_bytes = sizeof(cufftDoubleComplex) * static_cast<size_t>(spectrum_size);
	if (&a == &b) {
		if (cudaMemcpyAsync(
				workspace.fb,
				workspace.fa,
				spectrum_bytes,
				cudaMemcpyDeviceToDevice,
				workspace.stream
		) != cudaSuccess) {
			return false;
		}
	} else if (!prepare_u16_spectrum(b, workspace.host_digits_b, workspace.digits_b, workspace.db, workspace.fb,
			use_spectrum_cache ? workspace.cached_fb : nullptr,
			use_spectrum_cache ? workspace.cache_b : nullptr,
			workspace.spectrum_cache_tick,
			workspace.forward_plan, workspace.stream, n, bits_per_digit, block_size)) {
		return false;
	}

	const int grid_size = (spectrum_size + block_size - 1) / block_size;
	pointwise_multiply<<<grid_size, block_size, 0, workspace.stream>>>(
		workspace.fa,
		workspace.fb,
		spectrum_size
	);
	if (cudaGetLastError() != cudaSuccess) {
		return false;
	}
	if (cufftExecZ2D(workspace.inverse_plan, workspace.fa, workspace.da) != CUFFT_SUCCESS) {
		return false;
	}
	if (!workspace.ensure_host_result(result_size)) {
		return false;
	}
	if (cudaMemcpyAsync(
			workspace.host_result,
			workspace.da,
			sizeof(double) * result_size,
			cudaMemcpyDeviceToHost,
			workspace.stream
	) != cudaSuccess || cudaStreamSynchronize(workspace.stream) != cudaSuccess) {
		return false;
	}

	if (limb_out != nullptr) {
		return collect_u16_limb_result(workspace.host_result, result_size, n, *limb_out,
				bits_per_digit, limb_bits);
	}
	return digit_out != nullptr &&
			collect_u16_digits_result(workspace.host_result, result_size, n, *digit_out, bits_per_digit);
}

bool convolve_u16_digits(const std::vector<uint16_t> &a,
		const std::vector<uint16_t> &b,
		std::vector<uint16_t> &out,
		unsigned bits_per_digit,
		uint64_t max_queue_wait_nanos) {
	return convolve_u16_digits_impl(
		a,
		b,
		&out,
		nullptr,
		bits_per_digit,
		0,
		max_queue_wait_nanos
	);
}

bool convolve_u16_digits_to_limbs(const std::vector<uint16_t> &a,
		const std::vector<uint16_t> &b,
		std::vector<uint64_t> &out,
		unsigned bits_per_digit,
		unsigned limb_bits,
		uint64_t max_queue_wait_nanos) {
	return convolve_u16_digits_impl(
		a,
		b,
		nullptr,
		&out,
		bits_per_digit,
		limb_bits,
		max_queue_wait_nanos
	);
}

bool configure_convolution_workspace_pool(int device, uint64_t budget_bytes) {
	if (device < 0 || cudaSetDevice(device) != cudaSuccess) return false;
	return workspace_pool().configure(device, budget_bytes);
}

uint64_t convolution_workspace_budget_bytes() {
	return workspace_pool().budget_bytes();
}

uint64_t convolution_workspace_in_use_bytes() {
	return workspace_pool().in_use_bytes();
}

int convolution_workspace_capacity() {
	return workspace_pool().capacity();
}

int convolution_workspace_in_use() {
	return workspace_pool().in_use();
}

bool convolution_workspace_available(int transform_size) {
	return workspace_pool().available(transform_size);
}

}
