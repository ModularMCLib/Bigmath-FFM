#include "cuda_convolution.h"

#include <cstdint>
#include <limits>
#include <vector>

#include <cuda_runtime.h>
#include <cufft.h>

namespace bigmath::cuda {

__global__ static void pointwise_multiply(cufftDoubleComplex *left,
		const cufftDoubleComplex *right,
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

__global__ static void load_u16_digits(const uint16_t *digits,
		size_t digit_count,
		double *out,
		int n) {
	const int i = blockIdx.x * blockDim.x + threadIdx.x;
	if (i >= n) {
		return;
	}
	out[i] = static_cast<size_t>(i) < digit_count ? static_cast<double>(digits[i]) : 0.0;
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

static bool has_enough_device_memory(size_t element_count) {
	size_t free_bytes = 0;
	size_t total_bytes = 0;
	if (cudaMemGetInfo(&free_bytes, &total_bytes) != cudaSuccess) {
		return false;
	}
	const size_t spectrum_size = element_count / 2 + 1;
	const size_t required = sizeof(double) * element_count * 2 +
			sizeof(cufftDoubleComplex) * spectrum_size * 2 +
			sizeof(uint16_t) * element_count * 2;
	// cuFFT may reserve temporary workspace in addition to both input arrays.
	return required < free_bytes / 2;
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
	if (value <= -0.5) {
		return false;
	}
	out = static_cast<uint64_t>(value + 0.5);
	return true;
}

static bool clear_device_tail(double *values, size_t used, int n) {
	const size_t count = static_cast<size_t>(n);
	if (used >= count) {
		return true;
	}
	return cudaMemset(values + used, 0, sizeof(double) * (count - used)) == cudaSuccess;
}

struct CudaConvolutionWorkspace {
	double *da = nullptr;
	double *db = nullptr;
	uint16_t *digits_a = nullptr;
	uint16_t *digits_b = nullptr;
	cufftDoubleComplex *fa = nullptr;
	cufftDoubleComplex *fb = nullptr;
	cufftHandle forward_plan = 0;
	cufftHandle inverse_plan = 0;
	int plan_size = 0;
	int capacity = 0;
	std::vector<double> host_a;
	std::vector<double> host_b;
	std::vector<double> host_result;

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
		plan_size = 0;
	}

	void release() {
		release_plan();
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
		host_a.clear();
		host_b.clear();
		host_result.clear();
	}

	bool ensure_capacity(int n) {
		if (capacity >= n) {
			return true;
		}
		release();
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
		return true;
	}

	bool ensure_plan(int n) {
		if (plan_size == n && forward_plan != 0 && inverse_plan != 0) {
			return true;
		}
		release_plan();
		if (cufftPlan1d(&forward_plan, n, CUFFT_D2Z, 1) != CUFFT_SUCCESS) {
			forward_plan = 0;
			plan_size = 0;
			return false;
		}
		if (cufftPlan1d(&inverse_plan, n, CUFFT_Z2D, 1) != CUFFT_SUCCESS) {
			release_plan();
			plan_size = 0;
			return false;
		}
		plan_size = n;
		return true;
	}

	void prepare_host_result(size_t result_size) {
		host_result.resize(result_size);
	}

	void prepare_host_inputs(size_t a_size, size_t b_size) {
		host_a.resize(a_size);
		host_b.resize(b_size);
	}
};

bool convolve_u16_digits(const std::vector<uint16_t> &a,
		const std::vector<uint16_t> &b,
		std::vector<uint16_t> &out,
		unsigned bits_per_digit) {
	if (a.empty() || b.empty()) {
		out.clear();
		return true;
	}

	const size_t result_size = a.size() + b.size() - 1;
	int n = 0;
	if (!coefficients_fit_double(result_size, bits_per_digit) ||
			!next_pow2(result_size, n)) {
		return false;
	}
	thread_local CudaConvolutionWorkspace workspace;
	if (workspace.capacity < n && !has_enough_device_memory(static_cast<size_t>(n))) {
		return false;
	}
	if (!workspace.ensure_capacity(n) || !workspace.ensure_plan(n)) {
		return false;
	}

	if (cudaMemcpy(workspace.digits_a, a.data(), sizeof(uint16_t) * a.size(), cudaMemcpyHostToDevice) != cudaSuccess ||
			cudaMemcpy(workspace.digits_b, b.data(), sizeof(uint16_t) * b.size(), cudaMemcpyHostToDevice) != cudaSuccess) {
		return false;
	}
	const int block_size = 256;
	const int init_grid_size = (n + block_size - 1) / block_size;
	load_u16_digits<<<init_grid_size, block_size>>>(workspace.digits_a, a.size(), workspace.da, n);
	load_u16_digits<<<init_grid_size, block_size>>>(workspace.digits_b, b.size(), workspace.db, n);
	if (cudaGetLastError() != cudaSuccess) {
		return false;
	}
	if (cufftExecD2Z(workspace.forward_plan, workspace.da, workspace.fa) != CUFFT_SUCCESS ||
			cufftExecD2Z(workspace.forward_plan, workspace.db, workspace.fb) != CUFFT_SUCCESS) {
		return false;
	}

	const int spectrum_size = n / 2 + 1;
	const int grid_size = (spectrum_size + block_size - 1) / block_size;
	pointwise_multiply<<<grid_size, block_size>>>(workspace.fa, workspace.fb, spectrum_size);
	if (cudaGetLastError() != cudaSuccess) {
		return false;
	}
	if (cufftExecZ2D(workspace.inverse_plan, workspace.fa, workspace.da) != CUFFT_SUCCESS) {
		return false;
	}
	workspace.prepare_host_result(result_size);
	if (cudaMemcpy(workspace.host_result.data(), workspace.da, sizeof(double) * result_size, cudaMemcpyDeviceToHost) != cudaSuccess) {
		return false;
	}

	out.reserve(result_size + 1);
	out.resize(result_size);
	const double scale = 1.0 / static_cast<double>(n);
	uint64_t carry = 0;
	const uint64_t base_mask = (uint64_t{1} << bits_per_digit) - 1;
	for (size_t i = 0; i < result_size; i++) {
		uint64_t rounded = 0;
		if (!round_nonnegative_to_u64(workspace.host_result[i] * scale, rounded)) {
			return false;
		}
		const uint64_t value = rounded + carry;
		out[i] = static_cast<uint16_t>(value & base_mask);
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

bool convolve_digits(const std::vector<uint64_t> &a,
		const std::vector<uint64_t> &b,
		std::vector<uint64_t> &out,
		unsigned bits_per_digit) {
	if (a.empty() || b.empty()) {
		out.clear();
		return true;
	}

	const size_t result_size = a.size() + b.size() - 1;
	int n = 0;
	if (!coefficients_fit_double(result_size, bits_per_digit) ||
			!next_pow2(result_size, n)) {
		return false;
	}
	thread_local CudaConvolutionWorkspace workspace;
	if (workspace.capacity < n && !has_enough_device_memory(static_cast<size_t>(n))) {
		return false;
	}
	if (!workspace.ensure_capacity(n) || !workspace.ensure_plan(n)) {
		return false;
	}

	workspace.prepare_host_inputs(a.size(), b.size());
	for (size_t i = 0; i < a.size(); i++) {
		workspace.host_a[i] = static_cast<double>(a[i]);
	}
	for (size_t i = 0; i < b.size(); i++) {
		workspace.host_b[i] = static_cast<double>(b[i]);
	}

	if (cudaMemcpy(workspace.da, workspace.host_a.data(), sizeof(double) * a.size(), cudaMemcpyHostToDevice) != cudaSuccess ||
			cudaMemcpy(workspace.db, workspace.host_b.data(), sizeof(double) * b.size(), cudaMemcpyHostToDevice) != cudaSuccess ||
			!clear_device_tail(workspace.da, a.size(), n) ||
			!clear_device_tail(workspace.db, b.size(), n)) {
		return false;
	}
	if (cufftExecD2Z(workspace.forward_plan, workspace.da, workspace.fa) != CUFFT_SUCCESS ||
			cufftExecD2Z(workspace.forward_plan, workspace.db, workspace.fb) != CUFFT_SUCCESS) {
		return false;
	}

	const int block_size = 256;
	const int spectrum_size = n / 2 + 1;
	const int grid_size = (spectrum_size + block_size - 1) / block_size;
	pointwise_multiply<<<grid_size, block_size>>>(workspace.fa, workspace.fb, spectrum_size);
	if (cudaGetLastError() != cudaSuccess) {
		return false;
	}
	if (cufftExecZ2D(workspace.inverse_plan, workspace.fa, workspace.da) != CUFFT_SUCCESS) {
		return false;
	}
	workspace.prepare_host_result(result_size);
	if (cudaMemcpy(workspace.host_result.data(), workspace.da, sizeof(double) * result_size, cudaMemcpyDeviceToHost) != cudaSuccess) {
		return false;
	}

	out.resize(result_size);
	const double scale = 1.0 / static_cast<double>(n);
	for (size_t i = 0; i < result_size; i++) {
		uint64_t rounded = 0;
		if (!round_nonnegative_to_u64(workspace.host_result[i] * scale, rounded)) {
			return false;
		}
		out[i] = rounded;
	}
	return true;
}

}
