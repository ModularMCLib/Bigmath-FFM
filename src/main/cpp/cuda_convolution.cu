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
	const size_t required = sizeof(cufftDoubleComplex) * element_count * 2;
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

static bool cleanup(cufftHandle plan, cufftDoubleComplex *fa, cufftDoubleComplex *fb) {
	if (plan != 0) {
		cufftDestroy(plan);
	}
	cudaFree(fa);
	cudaFree(fb);
	return false;
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
			!next_pow2(result_size, n) ||
			!has_enough_device_memory(static_cast<size_t>(n))) {
		return false;
	}
	cufftDoubleComplex *fa = nullptr;
	cufftDoubleComplex *fb = nullptr;
	cufftHandle plan = 0;

	if (cudaMalloc(&fa, sizeof(cufftDoubleComplex) * n) != cudaSuccess) {
		return false;
	}
	if (cudaMalloc(&fb, sizeof(cufftDoubleComplex) * n) != cudaSuccess) {
		return cleanup(plan, fa, fb);
	}

	std::vector<cufftDoubleComplex> host_a(n);
	std::vector<cufftDoubleComplex> host_b(n);
	for (size_t i = 0; i < a.size(); i++) {
		host_a[i].x = static_cast<double>(a[i]);
		host_a[i].y = 0.0;
	}
	for (size_t i = 0; i < b.size(); i++) {
		host_b[i].x = static_cast<double>(b[i]);
		host_b[i].y = 0.0;
	}

	if (cudaMemcpy(fa, host_a.data(), sizeof(cufftDoubleComplex) * n, cudaMemcpyHostToDevice) != cudaSuccess ||
			cudaMemcpy(fb, host_b.data(), sizeof(cufftDoubleComplex) * n, cudaMemcpyHostToDevice) != cudaSuccess) {
		return cleanup(plan, fa, fb);
	}
	if (cufftPlan1d(&plan, n, CUFFT_Z2Z, 1) != CUFFT_SUCCESS) {
		return cleanup(plan, fa, fb);
	}
	if (cufftExecZ2Z(plan, fa, fa, CUFFT_FORWARD) != CUFFT_SUCCESS ||
			cufftExecZ2Z(plan, fb, fb, CUFFT_FORWARD) != CUFFT_SUCCESS) {
		return cleanup(plan, fa, fb);
	}

	const int block_size = 256;
	const int grid_size = (n + block_size - 1) / block_size;
	pointwise_multiply<<<grid_size, block_size>>>(fa, fb, n);
	if (cudaGetLastError() != cudaSuccess || cudaDeviceSynchronize() != cudaSuccess) {
		return cleanup(plan, fa, fb);
	}
	if (cufftExecZ2Z(plan, fa, fa, CUFFT_INVERSE) != CUFFT_SUCCESS) {
		return cleanup(plan, fa, fb);
	}
	if (cudaMemcpy(host_a.data(), fa, sizeof(cufftDoubleComplex) * n, cudaMemcpyDeviceToHost) != cudaSuccess) {
		return cleanup(plan, fa, fb);
	}

	out.resize(result_size);
	const double scale = 1.0 / static_cast<double>(n);
	for (size_t i = 0; i < result_size; i++) {
		double rounded = std::round(host_a[i].x * scale);
		if (rounded < 0.0) {
			return cleanup(plan, fa, fb);
		}
		out[i] = static_cast<uint64_t>(rounded);
	}

	cufftDestroy(plan);
	cudaFree(fa);
	cudaFree(fb);
	return true;
}

}
