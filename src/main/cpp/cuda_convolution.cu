#include "cuda_convolution.h"

#include <cmath>
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
			sizeof(cufftDoubleComplex) * spectrum_size * 2;
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

struct CudaConvolutionWorkspace {
	double *da = nullptr;
	double *db = nullptr;
	cufftDoubleComplex *fa = nullptr;
	cufftDoubleComplex *fb = nullptr;
	cufftHandle forward_plan = 0;
	cufftHandle inverse_plan = 0;
	int plan_size = 0;
	int capacity = 0;
	std::vector<double> host_a;
	std::vector<double> host_b;

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
		cudaFree(fa);
		cudaFree(fb);
		da = nullptr;
		db = nullptr;
		fa = nullptr;
		fb = nullptr;
		capacity = 0;
		host_a.clear();
		host_b.clear();
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

	void prepare_host_buffers(int n) {
		host_a.assign(static_cast<size_t>(n), 0.0);
		host_b.assign(static_cast<size_t>(n), 0.0);
	}
};

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
			!next_pow2(result_size, n) ||
			!has_enough_device_memory(static_cast<size_t>(n))) {
		return false;
	}
	thread_local CudaConvolutionWorkspace workspace;
	if (!workspace.ensure_capacity(n) || !workspace.ensure_plan(n)) {
		return false;
	}

	workspace.prepare_host_buffers(n);
	for (size_t i = 0; i < a.size(); i++) {
		workspace.host_a[i] = static_cast<double>(a[i]);
	}
	for (size_t i = 0; i < b.size(); i++) {
		workspace.host_b[i] = static_cast<double>(b[i]);
	}

	if (cudaMemcpy(workspace.da, workspace.host_a.data(), sizeof(double) * n, cudaMemcpyHostToDevice) != cudaSuccess ||
			cudaMemcpy(workspace.db, workspace.host_b.data(), sizeof(double) * n, cudaMemcpyHostToDevice) != cudaSuccess) {
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
	if (cudaMemcpy(workspace.host_a.data(), workspace.da, sizeof(double) * result_size, cudaMemcpyDeviceToHost) != cudaSuccess) {
		return false;
	}

	out.resize(result_size);
	const double scale = 1.0 / static_cast<double>(n);
	for (size_t i = 0; i < result_size; i++) {
		double rounded = std::round(workspace.host_a[i] * scale);
		if (rounded < 0.0) {
			return false;
		}
		out[i] = static_cast<uint64_t>(rounded);
	}
	return true;
}

}
