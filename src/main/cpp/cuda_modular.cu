#include "cuda_modular.h"

#include "cuda_ntt_device.cuh"
#include "runtime/cuda_workspace_budget.h"

#include <algorithm>
#include <atomic>
#include <chrono>
#include <condition_variable>
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <limits>
#include <memory>
#include <mutex>
#include <utility>
#include <vector>

#include <cuda_runtime.h>

namespace bigmath::cuda {
namespace {

using namespace ntt_device;

inline constexpr u64 DIGIT_MASK = UINT64_C(0xffff);
inline constexpr u64 CRT_MODULUS = MOD1 * MOD3;
inline constexpr u64 STATUS_OK = 0;
inline constexpr u64 STATUS_CARRY_OVERFLOW = 1;
inline constexpr u64 STATUS_MONTGOMERY_INVARIANT = 2;
inline constexpr u64 STATUS_REDUCTION_RANGE = 3;
inline constexpr u64 STATUS_WINDOW_RANGE = 4;

std::atomic<uint64_t> resident_buffer_bytes{0};

__device__ bool greater_or_equal(const u64 *left, const u64 *right, int digits) {
	for (int index = digits - 1; index >= 0; index--) {
		if (left[index] != right[index]) return left[index] > right[index];
	}
	return true;
}

__device__ void subtract_digits(u64 *left, const u64 *right, int digits) {
	u64 borrow = 0;
	for (int index = 0; index < digits; index++) {
		const u64 subtrahend = right[index] + borrow;
		const u64 value = left[index];
		left[index] = (value - subtrahend) & DIGIT_MASK;
		borrow = value < subtrahend ? 1 : 0;
	}
}

static __global__ void normalize_coefficients(
		const u64 *coefficients,
		u64 *digits,
		int coefficient_count,
		int digit_capacity,
		u64 *status
) {
	if (blockIdx.x != 0 || threadIdx.x != 0 || *status != STATUS_OK) return;
	for (int index = 0; index < digit_capacity; index++) digits[index] = 0;
	u64 carry = 0;
	int output = 0;
	for (; output < coefficient_count && output < digit_capacity; output++) {
		if (~static_cast<u64>(0) - coefficients[output] < carry) {
			*status = STATUS_CARRY_OVERFLOW;
			return;
		}
		const u64 value = coefficients[output] + carry;
		digits[output] = value & DIGIT_MASK;
		carry = value >> 16;
	}
	while (carry != 0 && output < digit_capacity) {
		digits[output++] = carry & DIGIT_MASK;
		carry >>= 16;
	}
	if (carry != 0 || coefficient_count > digit_capacity) {
		*status = STATUS_CARRY_OVERFLOW;
	}
}

static __global__ void finish_montgomery_reduction(
		const u64 *product,
		const u64 *multiple,
		u64 *result,
		const u64 *modulus,
		int modulus_digits,
		u64 *status
) {
	if (blockIdx.x != 0 || threadIdx.x != 0 || *status != STATUS_OK) return;
	u64 carry = 0;
	for (int index = 0; index <= 2 * modulus_digits; index++) {
		const u64 sum = product[index] + multiple[index] + carry;
		const u64 digit = sum & DIGIT_MASK;
		carry = sum >> 16;
		if (index < modulus_digits) {
			if (digit != 0) {
				*status = STATUS_MONTGOMERY_INVARIANT;
				return;
			}
		} else {
			result[index - modulus_digits] = digit;
		}
	}
	if (carry != 0) {
		*status = STATUS_CARRY_OVERFLOW;
		return;
	}
	const int result_digits = modulus_digits + 1;
	if (greater_or_equal(result, modulus, result_digits)) {
		subtract_digits(result, modulus, result_digits);
	}
	if (greater_or_equal(result, modulus, result_digits)) {
		subtract_digits(result, modulus, result_digits);
	}
	if (result[modulus_digits] != 0 || greater_or_equal(result, modulus, result_digits)) {
		*status = STATUS_REDUCTION_RANGE;
	}
}

static __global__ void finish_barrett_reduction(
		const u64 *product,
		const u64 *reduction_product,
		u64 *result,
		const u64 *modulus,
		int modulus_digits,
		u64 *status
) {
	if (blockIdx.x != 0 || threadIdx.x != 0 || *status != STATUS_OK) return;
	const int result_digits = modulus_digits + 1;
	u64 borrow = 0;
	for (int index = 0; index < result_digits; index++) {
		const u64 subtrahend = reduction_product[index] + borrow;
		const u64 value = product[index];
		result[index] = (value - subtrahend) & DIGIT_MASK;
		borrow = value < subtrahend ? 1 : 0;
	}
	for (int correction = 0; correction < 3; correction++) {
		if (!greater_or_equal(result, modulus, result_digits)) break;
		subtract_digits(result, modulus, result_digits);
	}
	if (result[modulus_digits] != 0 || greater_or_equal(result, modulus, result_digits)) {
		*status = STATUS_REDUCTION_RANGE;
	}
}

static __global__ void set_one(u64 *value, int digits, u64 *status) {
	if (blockIdx.x != 0 || threadIdx.x != 0 || *status != STATUS_OK) return;
	for (int index = 0; index < digits; index++) value[index] = 0;
	value[0] = 1;
}

static __global__ void select_window_power(
		const u64 *exponent,
		int exponent_digits,
		int lower_bit,
		int bit_count,
		const u64 *window_table,
		int table_entries,
		int stride,
		u64 *selected,
		u64 *status
) {
	if (*status != STATUS_OK) return;
	u64 window = 0;
	for (int offset = 0; offset < bit_count; offset++) {
		const int bit = lower_bit + offset;
		const int digit = bit / 16;
		if (digit < exponent_digits) {
			window |= ((exponent[digit] >> (bit % 16)) & 1) << offset;
		}
	}
	const int table_index = static_cast<int>(window >> 1);
	if (window == 0 || (window & 1) == 0 || table_index >= table_entries) {
		if (blockIdx.x == 0 && threadIdx.x == 0) *status = STATUS_WINDOW_RANGE;
		return;
	}
	const int index = blockIdx.x * blockDim.x + threadIdx.x;
	if (index < stride) {
		selected[index] = window_table[static_cast<size_t>(table_index) * stride + index];
	}
}

struct ModularNttWorkspace {
	int capacity = 0;
	int twiddle_count = 0;
	cudaStream_t stream = nullptr;
	u64 *left = nullptr;
	u64 *right = nullptr;
	u64 *first_residues = nullptr;
	u64 *coefficients = nullptr;
	u64 *forward_first = nullptr;
	u64 *inverse_first = nullptr;
	u64 *forward_third = nullptr;
	u64 *inverse_third = nullptr;
	u64 *host_twiddles = nullptr;
	u64 *host_result = nullptr;
	u64 *host_status = nullptr;
	int host_twiddle_capacity = 0;
	int host_result_capacity = 0;
	uint64_t device_bytes = 0;

	~ModularNttWorkspace() {
		release();
	}

	void release() {
		cudaFree(left);
		cudaFree(right);
		cudaFree(first_residues);
		cudaFree(coefficients);
		cudaFree(forward_first);
		cudaFree(inverse_first);
		cudaFree(forward_third);
		cudaFree(inverse_third);
		left = right = first_residues = coefficients = nullptr;
		forward_first = inverse_first = forward_third = inverse_third = nullptr;
		cudaFreeHost(host_twiddles);
		cudaFreeHost(host_result);
		cudaFreeHost(host_status);
		host_twiddles = host_result = host_status = nullptr;
		host_twiddle_capacity = 0;
		host_result_capacity = 0;
		if (stream != nullptr) {
			cudaStreamDestroy(stream);
			stream = nullptr;
		}
		capacity = 0;
		twiddle_count = 0;
		device_bytes = 0;
	}

	bool ensure(int transform_size) {
		if (capacity == transform_size && stream != nullptr) return true;
		release();
		if (cudaStreamCreateWithFlags(&stream, cudaStreamNonBlocking) != cudaSuccess) return false;
		const size_t full_bytes = sizeof(u64) * static_cast<size_t>(transform_size);
		const size_t half_bytes = sizeof(u64) * static_cast<size_t>(transform_size / 2);
		if (cudaMalloc(&left, full_bytes) != cudaSuccess ||
				cudaMalloc(&right, full_bytes) != cudaSuccess ||
				cudaMalloc(&first_residues, full_bytes) != cudaSuccess ||
				cudaMalloc(&coefficients, full_bytes) != cudaSuccess ||
				cudaMalloc(&forward_first, half_bytes) != cudaSuccess ||
				cudaMalloc(&inverse_first, half_bytes) != cudaSuccess ||
				cudaMalloc(&forward_third, half_bytes) != cudaSuccess ||
				cudaMalloc(&inverse_third, half_bytes) != cudaSuccess) {
			release();
			return false;
		}
		if (cudaHostAlloc(
				reinterpret_cast<void **>(&host_twiddles),
				half_bytes,
				cudaHostAllocDefault
		) != cudaSuccess || cudaHostAlloc(
				reinterpret_cast<void **>(&host_status),
				sizeof(u64),
				cudaHostAllocDefault
		) != cudaSuccess) {
			release();
			return false;
		}
		capacity = transform_size;
		host_twiddle_capacity = transform_size / 2;
		device_bytes = static_cast<uint64_t>(full_bytes) * 4 +
			static_cast<uint64_t>(half_bytes) * 4;
		return true;
	}

	bool ensure_host_result(int digits) {
		if (host_result_capacity >= digits) return true;
		cudaFreeHost(host_result);
		host_result = nullptr;
		host_result_capacity = 0;
		if (cudaHostAlloc(
				reinterpret_cast<void **>(&host_result),
				sizeof(u64) * static_cast<size_t>(digits),
				cudaHostAllocDefault
		) != cudaSuccess) {
			return false;
		}
		host_result_capacity = digits;
		return true;
	}
};

bool upload_twiddles(
		ModularNttWorkspace &workspace,
		u64 *destination,
		int transform_size,
		u64 modulus,
		bool inverse_transform
) {
	const u64 root = host_powmod(
		PRIMITIVE_ROOT,
		(modulus - 1) / static_cast<u64>(transform_size),
		modulus
	);
	const u64 step = inverse_transform ? host_powmod(root, modulus - 2, modulus) : root;
	u64 current = 1;
	for (int index = 0; index < transform_size / 2; index++) {
		workspace.host_twiddles[index] = current;
		current = host_mulmod(current, step, modulus);
	}
	return cudaMemcpyAsync(
		destination,
		workspace.host_twiddles,
		sizeof(u64) * static_cast<size_t>(transform_size / 2),
		cudaMemcpyHostToDevice,
		workspace.stream
	) == cudaSuccess && cudaStreamSynchronize(workspace.stream) == cudaSuccess;
}

bool ensure_twiddles(ModularNttWorkspace &workspace, int transform_size) {
	if (workspace.twiddle_count == transform_size) return true;
	if (!upload_twiddles(workspace, workspace.forward_first, transform_size, MOD1, false) ||
			!upload_twiddles(workspace, workspace.inverse_first, transform_size, MOD1, true) ||
			!upload_twiddles(workspace, workspace.forward_third, transform_size, MOD3, false) ||
			!upload_twiddles(workspace, workspace.inverse_third, transform_size, MOD3, true) ||
			cudaStreamSynchronize(workspace.stream) != cudaSuccess) {
		workspace.twiddle_count = 0;
		return false;
	}
	workspace.twiddle_count = transform_size;
	return true;
}

class ModularWorkspacePool {
private:
	struct Slot {
		std::unique_ptr<ModularNttWorkspace> workspace;
		int transform_size = 0;
		uint64_t device_bytes = 0;
		bool busy = false;
		uint64_t last_used = 0;
	};

public:
	class Lease {
	public:
		Lease() = default;
		Lease(ModularWorkspacePool *pool, Slot *slot) : pool_(pool), slot_(slot) {
		}
		~Lease() {
			release();
		}

		Lease(const Lease &) = delete;
		Lease &operator=(const Lease &) = delete;

		Lease(Lease &&other) noexcept :
				pool_(other.pool_), slot_(other.slot_), discard_(other.discard_) {
			other.pool_ = nullptr;
		}

		Lease &operator=(Lease &&other) noexcept {
			if (this != &other) {
				release();
				pool_ = other.pool_;
				slot_ = other.slot_;
				discard_ = other.discard_;
				other.pool_ = nullptr;
			}
			return *this;
		}

		explicit operator bool() const {
			return pool_ != nullptr;
		}

		ModularNttWorkspace &workspace() {
			return *slot_->workspace;
		}

		void discard() {
			discard_ = true;
		}

	private:
		void release() {
			if (pool_ == nullptr) return;
			pool_->release(slot_, discard_);
			pool_ = nullptr;
		}

		ModularWorkspacePool *pool_ = nullptr;
		Slot *slot_ = nullptr;
		bool discard_ = false;
	};

	bool configure(int device) {
		std::lock_guard lock(mutex_);
		if (reserved_bytes_ != 0) return false;
		for (const auto &slot : slots_) if (slot->busy) return false;
		for (const auto &slot : slots_) {
			bigmath::runtime::release_cuda_workspace_bytes(device_, slot->device_bytes);
		}
		slots_.clear();
		allocated_bytes_ = 0;
		device_ = device;
		configured_ = true;
		return true;
	}

	Lease acquire(int transform_size, uint64_t max_wait_nanos) {
		const auto deadline = std::chrono::steady_clock::now() +
			std::chrono::nanoseconds(max_wait_nanos);
		const uint64_t required = static_cast<uint64_t>(sizeof(u64)) *
			static_cast<uint64_t>(transform_size) * 6;
		for (;;) {
			std::unique_lock lock(mutex_);
			if (!configured_) return Lease{};
			for (const auto &slot : slots_) {
				if (!slot->busy && slot->transform_size == transform_size) {
					slot->busy = true;
					slot->last_used = next_tick();
					if (cudaSetDevice(device_) != cudaSuccess) {
						slot->busy = false;
						return Lease{};
					}
					return Lease(this, slot.get());
				}
			}

			if (bigmath::runtime::reserve_cuda_workspace_bytes(device_, required)) {
				reserved_bytes_ += required;
				const int device = device_;
				lock.unlock();
				auto workspace = std::make_unique<ModularNttWorkspace>();
				const bool initialized = cudaSetDevice(device) == cudaSuccess &&
					workspace->ensure(transform_size) && ensure_twiddles(*workspace, transform_size);
				lock.lock();
				reserved_bytes_ -= required;
				if (!initialized) {
					bigmath::runtime::release_cuda_workspace_bytes(device_, required);
					lock.unlock();
					workspace.reset();
					lock.lock();
					condition_.notify_all();
					return Lease{};
				}
				auto slot = std::make_unique<Slot>();
				slot->device_bytes = workspace->device_bytes;
				slot->transform_size = transform_size;
				slot->workspace = std::move(workspace);
				slot->busy = true;
				slot->last_used = next_tick();
				allocated_bytes_ += required;
				Slot *acquired = slot.get();
				slots_.push_back(std::move(slot));
				return Lease(this, acquired);
			}

			if (evict_idle_lru()) continue;
			if (max_wait_nanos == 0 ||
					condition_.wait_until(lock, deadline) == std::cv_status::timeout) {
				return Lease{};
			}
		}
	}

	uint64_t in_use_bytes() const {
		std::lock_guard lock(mutex_);
		uint64_t bytes = 0;
		for (const auto &slot : slots_) if (slot->busy) bytes += slot->device_bytes;
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

private:
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
		bigmath::runtime::release_cuda_workspace_bytes(device_, slots_[target]->device_bytes);
		slots_.erase(slots_.begin() + static_cast<std::ptrdiff_t>(target));
		return true;
	}

	void release(Slot *slot, bool discard) {
		cudaError_t synchronized = cudaErrorInvalidResourceHandle;
		if (slot != nullptr && cudaSetDevice(device_) == cudaSuccess) {
			synchronized = cudaStreamSynchronize(slot->workspace->stream);
		}
		std::lock_guard lock(mutex_);
		if (slot != nullptr && !discard && synchronized == cudaSuccess) {
			slot->busy = false;
			slot->last_used = next_tick();
		} else if (slot != nullptr) {
			for (auto iterator = slots_.begin(); iterator != slots_.end(); ++iterator) {
				if (iterator->get() == slot) {
					allocated_bytes_ -= slot->device_bytes;
					bigmath::runtime::release_cuda_workspace_bytes(device_, slot->device_bytes);
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
	uint64_t allocated_bytes_ = 0;
	uint64_t reserved_bytes_ = 0;
	uint64_t tick_ = 0;
};

ModularWorkspacePool &workspace_pool() {
	static ModularWorkspacePool pool;
	return pool;
}

struct ResidentBuffers {
	int device = 0;
	int modulus_digits = 0;
	int stride = 0;
	int full_digits = 0;
	int exponent_digits = 0;
	int window_entries = 0;
	u64 *block = nullptr;
	u64 *host_initial = nullptr;
	u64 *modulus = nullptr;
	u64 *constant = nullptr;
	u64 *accumulator = nullptr;
	u64 *power = nullptr;
	u64 *spare = nullptr;
	u64 *exponent = nullptr;
	u64 *full_first = nullptr;
	u64 *full_second = nullptr;
	u64 *quotient = nullptr;
	u64 *status = nullptr;
	u64 *window_table = nullptr;
	uint64_t bytes = 0;

	~ResidentBuffers() {
		release();
	}

	void release() {
		cudaFreeHost(host_initial);
		host_initial = nullptr;
		cudaFree(block);
		block = nullptr;
		if (bytes != 0) {
			resident_buffer_bytes.fetch_sub(bytes, std::memory_order_relaxed);
			bigmath::runtime::release_cuda_workspace_bytes(device, bytes);
			bytes = 0;
		}
	}

	bool allocate(
			int selected_device,
			int digits,
			int selected_exponent_digits,
			int selected_window_entries
	) {
		device = selected_device;
		modulus_digits = digits;
		stride = digits + 1;
		full_digits = 2 * digits + 4;
		exponent_digits = selected_exponent_digits;
		window_entries = selected_window_entries;
		const size_t total_digits = static_cast<size_t>(stride) * 5 +
			static_cast<size_t>(exponent_digits) +
			static_cast<size_t>(full_digits) * 2 + static_cast<size_t>(digits + 2) + 1 +
			static_cast<size_t>(window_entries) * stride;
		if (total_digits > std::numeric_limits<size_t>::max() / sizeof(u64)) return false;
		bytes = static_cast<uint64_t>(total_digits * sizeof(u64));
		if (!bigmath::runtime::reserve_cuda_workspace_bytes(device, bytes)) {
			bytes = 0;
			return false;
		}
		resident_buffer_bytes.fetch_add(bytes, std::memory_order_relaxed);
		if (cudaMalloc(&block, static_cast<size_t>(bytes)) != cudaSuccess ||
				cudaHostAlloc(
					reinterpret_cast<void **>(&host_initial),
					(static_cast<size_t>(stride) * 5 + exponent_digits) * sizeof(u64),
					cudaHostAllocDefault
				) != cudaSuccess) {
			release();
			return false;
		}
		u64 *cursor = block;
		modulus = cursor; cursor += stride;
		constant = cursor; cursor += stride;
		accumulator = cursor; cursor += stride;
		power = cursor; cursor += stride;
		spare = cursor; cursor += stride;
		exponent = cursor; cursor += exponent_digits;
		full_first = cursor; cursor += full_digits;
		full_second = cursor; cursor += full_digits;
		quotient = cursor; cursor += digits + 2;
		status = cursor; cursor++;
		window_table = cursor;
		return true;
	}

	static void copy_to_padded(
			u64 *destination,
			int capacity,
			const std::vector<uint16_t> &source
	) {
		for (int index = 0; index < capacity; index++) destination[index] = 0;
		const size_t count = std::min(source.size(), static_cast<size_t>(capacity));
		for (size_t index = 0; index < count; index++) destination[index] = source[index];
	}

	bool initialize(
			const std::vector<uint16_t> &base,
			const std::vector<uint16_t> &exponent_value,
			const std::vector<uint16_t> &modulus_value,
			const std::vector<uint16_t> &reduction_constant,
			const std::vector<uint16_t> &identity,
			cudaStream_t stream
	) {
		copy_to_padded(host_initial, stride, modulus_value);
		copy_to_padded(host_initial + stride, stride, reduction_constant);
		copy_to_padded(host_initial + stride * 2, stride, identity);
		copy_to_padded(host_initial + stride * 3, stride, base);
		for (int index = 0; index < stride; index++) host_initial[stride * 4 + index] = 0;
		copy_to_padded(
			host_initial + static_cast<size_t>(stride) * 5,
			exponent_digits,
			exponent_value
		);
		const size_t initial_bytes =
			(static_cast<size_t>(stride) * 5 + exponent_digits) * sizeof(u64);
		return cudaMemcpyAsync(
			block,
			host_initial,
			initial_bytes,
			cudaMemcpyHostToDevice,
			stream
		) == cudaSuccess && cudaMemsetAsync(
			block + static_cast<size_t>(stride) * 5,
			0,
			static_cast<size_t>(bytes) - initial_bytes,
			stream
		) == cudaSuccess;
	}
};

bool load_device_digits(
		ModularNttWorkspace &workspace,
		u64 *destination,
		const u64 *source,
		int source_digits,
		int transform_size
) {
	if (cudaMemcpyAsync(
			destination,
			source,
			sizeof(u64) * static_cast<size_t>(source_digits),
			cudaMemcpyDeviceToDevice,
			workspace.stream
	) != cudaSuccess) {
		return false;
	}
	return source_digits >= transform_size || cudaMemsetAsync(
		destination + source_digits,
		0,
		sizeof(u64) * static_cast<size_t>(transform_size - source_digits),
		workspace.stream
	) == cudaSuccess;
}

bool convolve_prime(
		ModularNttWorkspace &workspace,
		const u64 *left_digits,
		int left_count,
		const u64 *right_digits,
		int right_count,
		int transform_size,
		int logarithm,
		u64 modulus,
		u64 mu,
		const u64 *forward_twiddles,
		const u64 *inverse_twiddles
) {
	const bool square = left_digits == right_digits && left_count == right_count;
	if (!load_device_digits(workspace, workspace.left, left_digits, left_count, transform_size) ||
			(!square && !load_device_digits(
				workspace,
				workspace.right,
				right_digits,
				right_count,
				transform_size
			))) {
		return false;
	}
	if (!forward(
			workspace.left,
			forward_twiddles,
			transform_size,
			logarithm,
			modulus,
			mu,
			workspace.stream
	) || (!square && !forward(
			workspace.right,
			forward_twiddles,
			transform_size,
			logarithm,
			modulus,
			mu,
			workspace.stream
	))) {
		return false;
	}
	const u64 *right_transform = square ? workspace.left : workspace.right;
	pointwise_multiply<<<
		(transform_size + BLOCK_SIZE - 1) / BLOCK_SIZE,
		BLOCK_SIZE,
		0,
		workspace.stream
	>>>(workspace.left, right_transform, transform_size, modulus, mu);
	return cudaGetLastError() == cudaSuccess && inverse(
		workspace.left,
		inverse_twiddles,
		transform_size,
		logarithm,
		modulus,
		mu,
		workspace.stream
	);
}

bool convolve_device(
		ModularNttWorkspace &workspace,
		const u64 *left,
		int left_digits,
		const u64 *right,
		int right_digits,
		u64 *output,
		int output_capacity,
		int transform_size,
		int logarithm,
		u64 *status
) {
	if (left_digits <= 0 || right_digits <= 0) return false;
	const int coefficient_count = left_digits + right_digits - 1;
	if (coefficient_count > transform_size || coefficient_count > output_capacity) return false;
	const u64 minimum = static_cast<u64>(std::min(left_digits, right_digits));
	if (UINT64_C(65535) * UINT64_C(65535) > CRT_MODULUS / minimum) return false;
	if (!convolve_prime(
			workspace,
			left,
			left_digits,
			right,
			right_digits,
			transform_size,
			logarithm,
			MOD1,
			MU1,
			workspace.forward_first,
			workspace.inverse_first
	)) {
		return false;
	}
	if (cudaMemcpyAsync(
			workspace.first_residues,
			workspace.left,
			sizeof(u64) * static_cast<size_t>(transform_size),
			cudaMemcpyDeviceToDevice,
			workspace.stream
	) != cudaSuccess || !convolve_prime(
			workspace,
			left,
			left_digits,
			right,
			right_digits,
			transform_size,
			logarithm,
			MOD3,
			MU3,
			workspace.forward_third,
			workspace.inverse_third
	)) {
		return false;
	}
	reconstruct_crt<<<
		(coefficient_count + BLOCK_SIZE - 1) / BLOCK_SIZE,
		BLOCK_SIZE,
		0,
		workspace.stream
	>>>(
		workspace.first_residues,
		workspace.left,
		workspace.coefficients,
		coefficient_count
	);
	normalize_coefficients<<<1, 1, 0, workspace.stream>>>(
		workspace.coefficients,
		output,
		coefficient_count,
		output_capacity,
		status
	);
	return cudaGetLastError() == cudaSuccess;
}

bool montgomery_multiply(
		ModularNttWorkspace &workspace,
		ResidentBuffers &buffers,
		const u64 *left,
		const u64 *right,
		u64 *output,
		int transform_size,
		int logarithm
) {
	const int digits = buffers.modulus_digits;
	if (!convolve_device(
			workspace,
			left,
			digits,
			right,
			digits,
			buffers.full_first,
			buffers.full_digits,
			transform_size,
			logarithm,
			buffers.status
	) || !convolve_device(
			workspace,
			buffers.full_first,
			digits,
			buffers.constant,
			digits,
			buffers.full_second,
			buffers.full_digits,
			transform_size,
			logarithm,
			buffers.status
	) || cudaMemcpyAsync(
			buffers.quotient,
			buffers.full_second,
			sizeof(u64) * static_cast<size_t>(digits),
			cudaMemcpyDeviceToDevice,
			workspace.stream
	) != cudaSuccess || !convolve_device(
			workspace,
			buffers.quotient,
			digits,
			buffers.modulus,
			digits,
			buffers.full_second,
			buffers.full_digits,
			transform_size,
			logarithm,
			buffers.status
	)) {
		return false;
	}
	finish_montgomery_reduction<<<1, 1, 0, workspace.stream>>>(
		buffers.full_first,
		buffers.full_second,
		output,
		buffers.modulus,
		digits,
		buffers.status
	);
	return cudaGetLastError() == cudaSuccess;
}

bool barrett_multiply(
		ModularNttWorkspace &workspace,
		ResidentBuffers &buffers,
		const u64 *left,
		const u64 *right,
		u64 *output,
		int transform_size,
		int logarithm
) {
	const int digits = buffers.modulus_digits;
	if (!convolve_device(
			workspace,
			left,
			digits,
			right,
			digits,
			buffers.full_first,
			buffers.full_digits,
			transform_size,
			logarithm,
			buffers.status
	) || !convolve_device(
			workspace,
			buffers.full_first + digits - 1,
			digits + 1,
			buffers.constant,
			digits + 1,
			buffers.full_second,
			buffers.full_digits,
			transform_size,
			logarithm,
			buffers.status
	) || !convolve_device(
			workspace,
			buffers.full_second + digits + 1,
			digits + 1,
			buffers.modulus,
			digits,
			buffers.full_second,
			buffers.full_digits,
			transform_size,
			logarithm,
			buffers.status
	)) {
		return false;
	}
	finish_barrett_reduction<<<1, 1, 0, workspace.stream>>>(
		buffers.full_first,
		buffers.full_second,
		output,
		buffers.modulus,
		digits,
		buffers.status
	);
	return cudaGetLastError() == cudaSuccess;
}

size_t exponent_bit_length(const std::vector<uint16_t> &exponent) {
	size_t digits = exponent.size();
	while (digits > 0 && exponent[digits - 1] == 0) digits--;
	if (digits == 0) return 0;
	uint16_t top = exponent[digits - 1];
	unsigned high_bits = 0;
	while (top != 0) {
		high_bits++;
		top >>= 1;
	}
	return (digits - 1) * 16 + high_bits;
}

bool exponent_bit(const std::vector<uint16_t> &exponent, size_t bit) {
	const size_t digit = bit / 16;
	return digit < exponent.size() && ((exponent[digit] >> (bit % 16)) & 1) != 0;
}

size_t window_start(
		const std::vector<uint16_t> &exponent,
		size_t position,
		unsigned width
) {
	const size_t minimum = position > width ? position - width : 0;
	size_t start = minimum;
	while (!exponent_bit(exponent, start)) start++;
	return start;
}

uint64_t window_operation_count(
		const std::vector<uint16_t> &exponent,
		size_t bits,
		unsigned width
) {
	const uint64_t entries = UINT64_C(1) << (width - 1);
	uint64_t operations = width == 1 ? 0 : entries;
	size_t position = bits;
	while (position > 0) {
		if (!exponent_bit(exponent, position - 1)) {
			position--;
			continue;
		}
		position = window_start(exponent, position, width);
		operations++;
	}
	return operations;
}

unsigned select_window_width(
		const std::vector<uint16_t> &exponent,
		size_t bits
) {
	unsigned selected = 1;
	uint64_t selected_operations = window_operation_count(exponent, bits, selected);
	for (unsigned width = 2; width <= 6; width++) {
		const uint64_t operations = window_operation_count(exponent, bits, width);
		if (operations < selected_operations) {
			selected = width;
			selected_operations = operations;
		}
	}
	return selected;
}

bool less_than_modulus(
		const std::vector<uint16_t> &value,
		const std::vector<uint16_t> &modulus
) {
	for (size_t index = modulus.size(); index > 0; index--) {
		const uint16_t left = index <= value.size() ? value[index - 1] : 0;
		const uint16_t right = modulus[index - 1];
		if (left != right) return left < right;
	}
	return false;
}

}

bool configure_modular_workspace_pool(int device) {
	return workspace_pool().configure(device);
}

uint64_t modular_workspace_in_use_bytes() {
	return workspace_pool().in_use_bytes() +
		resident_buffer_bytes.load(std::memory_order_relaxed);
}

int modular_workspace_capacity() {
	return workspace_pool().capacity();
}

int modular_workspace_in_use() {
	return workspace_pool().in_use();
}

bool resident_modpow_u16(
		const std::vector<uint16_t> &base,
		const std::vector<uint16_t> &exponent,
		const std::vector<uint16_t> &modulus,
		const std::vector<uint16_t> &reduction_constant,
		const std::vector<uint16_t> &identity,
		ResidentReduction reduction,
		uint64_t max_queue_wait_nanos,
		std::vector<uint16_t> &result
) {
	if (base.empty() || modulus.empty() || reduction_constant.empty() || identity.empty()) {
		return false;
	}
	if (exponent.empty() ||
			exponent.size() > static_cast<size_t>(std::numeric_limits<int>::max() / 16)) {
		return false;
	}
	if (modulus.size() > static_cast<size_t>((NTT_MAX_TRANSFORM_SIZE - 4) / 2)) {
		return false;
	}
	const int digits = static_cast<int>(modulus.size());
	int transform_size = 0;
	int logarithm = 0;
	if (!next_power_of_two(static_cast<size_t>(2 * digits + 3), transform_size, logarithm)) {
		return false;
	}
	const size_t bits = exponent_bit_length(exponent);
	const unsigned window_width = select_window_width(exponent, bits);
	const int window_entries = 1 << (window_width - 1);

	ModularWorkspacePool::Lease lease = workspace_pool().acquire(
		transform_size,
		max_queue_wait_nanos
	);
	if (!lease) return false;
	ModularNttWorkspace &workspace = lease.workspace();
	if (!workspace.ensure_host_result(digits)) {
		lease.discard();
		return false;
	}
	int device = 0;
	if (cudaGetDevice(&device) != cudaSuccess) {
		lease.discard();
		return false;
	}
	ResidentBuffers buffers;
	if (!buffers.allocate(
			device,
			digits,
			static_cast<int>(exponent.size()),
			window_entries
	) || !buffers.initialize(
			base,
			exponent,
			modulus,
			reduction_constant,
			identity,
			workspace.stream
	)) {
		lease.discard();
		return false;
	}

	auto modular_multiply = [&](const u64 *left, const u64 *right, u64 *output) {
		return reduction == ResidentReduction::MONTGOMERY
			? montgomery_multiply(
				workspace,
				buffers,
				left,
				right,
				output,
				transform_size,
				logarithm
			)
			: barrett_multiply(
				workspace,
				buffers,
				left,
				right,
				output,
				transform_size,
				logarithm
			);
	};

	if (cudaMemcpyAsync(
			buffers.window_table,
			buffers.power,
			sizeof(u64) * static_cast<size_t>(buffers.stride),
			cudaMemcpyDeviceToDevice,
			workspace.stream
	) != cudaSuccess) {
		lease.discard();
		return false;
	}
	if (window_entries > 1) {
		if (!modular_multiply(buffers.power, buffers.power, buffers.spare)) {
			lease.discard();
			return false;
		}
		for (int entry = 1; entry < window_entries; entry++) {
			if (!modular_multiply(
				buffers.window_table + static_cast<size_t>(entry - 1) * buffers.stride,
				buffers.spare,
				buffers.window_table + static_cast<size_t>(entry) * buffers.stride
			)) {
				lease.discard();
				return false;
			}
		}
	}

	size_t position = bits;
	while (position > 0) {
		if (!exponent_bit(exponent, position - 1)) {
			if (!modular_multiply(buffers.accumulator, buffers.accumulator, buffers.spare)) {
				lease.discard();
				return false;
			}
			std::swap(buffers.accumulator, buffers.spare);
			position--;
			continue;
		}

		const size_t lower = window_start(exponent, position, window_width);
		const int window_bits = static_cast<int>(position - lower);
		for (int square = 0; square < window_bits; square++) {
			if (!modular_multiply(buffers.accumulator, buffers.accumulator, buffers.spare)) {
				lease.discard();
				return false;
			}
			std::swap(buffers.accumulator, buffers.spare);
		}
		select_window_power<<<
			(buffers.stride + BLOCK_SIZE - 1) / BLOCK_SIZE,
			BLOCK_SIZE,
			0,
			workspace.stream
		>>>(
			buffers.exponent,
			buffers.exponent_digits,
			static_cast<int>(lower),
			window_bits,
			buffers.window_table,
			window_entries,
			buffers.stride,
			buffers.power,
			buffers.status
		);
		if (cudaGetLastError() != cudaSuccess ||
				!modular_multiply(buffers.accumulator, buffers.power, buffers.spare)) {
			lease.discard();
			return false;
		}
		std::swap(buffers.accumulator, buffers.spare);
		position = lower;
	}

	u64 *final_value = buffers.accumulator;
	if (reduction == ResidentReduction::MONTGOMERY) {
		set_one<<<1, 1, 0, workspace.stream>>>(
			buffers.quotient,
			digits,
			buffers.status
		);
		if (cudaGetLastError() != cudaSuccess ||
				!modular_multiply(buffers.accumulator, buffers.quotient, buffers.spare)) {
			lease.discard();
			return false;
		}
		final_value = buffers.spare;
	}

	if (cudaMemcpyAsync(
			workspace.host_result,
			final_value,
			sizeof(u64) * static_cast<size_t>(digits),
			cudaMemcpyDeviceToHost,
			workspace.stream
	) != cudaSuccess || cudaMemcpyAsync(
			workspace.host_status,
			buffers.status,
			sizeof(u64),
			cudaMemcpyDeviceToHost,
			workspace.stream
	) != cudaSuccess || cudaStreamSynchronize(workspace.stream) != cudaSuccess ||
			*workspace.host_status != STATUS_OK) {
		lease.discard();
		return false;
	}

	std::vector<uint16_t> completed_result(static_cast<size_t>(digits));
	for (int index = 0; index < digits; index++) {
		if (workspace.host_result[index] > DIGIT_MASK) {
			lease.discard();
			return false;
		}
		completed_result[static_cast<size_t>(index)] =
			static_cast<uint16_t>(workspace.host_result[index]);
	}
	while (completed_result.size() > 1 && completed_result.back() == 0) {
		completed_result.pop_back();
	}
	if (!less_than_modulus(completed_result, modulus)) {
		lease.discard();
		return false;
	}
	result = std::move(completed_result);
	return true;
}

}
