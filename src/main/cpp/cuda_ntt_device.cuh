#ifndef BIGMATH_CUDA_NTT_DEVICE_CUH
#define BIGMATH_CUDA_NTT_DEVICE_CUH

#include "cuda_ntt.h"

#include <cstddef>
#include <cstdint>

#include <cuda_runtime.h>

namespace bigmath::cuda::ntt_device {

using u64 = uint64_t;

inline constexpr u64 MOD1 = 998244353;
inline constexpr u64 MOD3 = 469762049;
inline constexpr u64 PRIMITIVE_ROOT = 3;
inline constexpr int BLOCK_SIZE = 256;
inline constexpr int SHARED_TILE_SIZE = 2048;

constexpr u64 host_mulmod(u64 a, u64 b, u64 modulus) {
	return (a % modulus) * (b % modulus) % modulus;
}

constexpr u64 host_powmod(u64 a, u64 exponent, u64 modulus) {
	u64 result = 1;
	while (exponent != 0) {
		if ((exponent & 1) != 0) result = host_mulmod(result, a, modulus);
		a = host_mulmod(a, a, modulus);
		exponent >>= 1;
	}
	return result;
}

inline constexpr u64 MOD1_INV_MOD3 = host_powmod(MOD1, MOD3 - 2, MOD3);

constexpr u64 barrett_mu(u64 modulus) {
	return (~static_cast<u64>(0)) / modulus;
}

inline constexpr u64 MU1 = barrett_mu(MOD1);
inline constexpr u64 MU3 = barrett_mu(MOD3);

__device__ __forceinline__ u64 multiply_mod(u64 left, u64 right, u64 modulus, u64 mu) {
	const u64 product = left * right;
	const u64 quotient = __umul64hi(product, mu);
	u64 result = product - quotient * modulus;
	if (result >= modulus) result -= modulus;
	if (result >= modulus) result -= modulus;
	return result;
}

static __global__ void bit_reverse(u64 *values, int count, int logarithm) {
	const int index = blockIdx.x * blockDim.x + threadIdx.x;
	if (index >= count) return;
	unsigned reversed = static_cast<unsigned>(index);
	reversed = (reversed >> 16) | (reversed << 16);
	reversed = ((reversed & 0x00ff00ffu) << 8) | ((reversed & 0xff00ff00u) >> 8);
	reversed = ((reversed & 0x0f0f0f0fu) << 4) | ((reversed & 0xf0f0f0f0u) >> 4);
	reversed = ((reversed & 0x33333333u) << 2) | ((reversed & 0xccccccccu) >> 2);
	reversed = ((reversed & 0x55555555u) << 1) | ((reversed & 0xaaaaaaaau) >> 1);
	const int target = static_cast<int>(reversed >> (32 - logarithm));
	if (index < target) {
		const u64 temporary = values[index];
		values[index] = values[target];
		values[target] = temporary;
	}
}

static __global__ void butterfly(
		u64 *values,
		const u64 *twiddles,
		int count,
		int length,
		u64 modulus,
		u64 mu
) {
	const int thread = blockIdx.x * blockDim.x + threadIdx.x;
	const int half = length >> 1;
	if (thread >= (count >> 1)) return;
	const int block = thread / half;
	const int offset = thread - block * half;
	const int index = block * length + offset;
	const int step = count / length;
	const u64 left = values[index];
	const u64 right = multiply_mod(values[index + half], twiddles[offset * step], modulus, mu);
	u64 sum = left + right;
	if (sum >= modulus) sum -= modulus;
	values[index] = sum;
	values[index + half] = left >= right ? left - right : left + modulus - right;
}

static __global__ void fused_stages(
		u64 *values,
		const u64 *twiddles,
		int count,
		int tile,
		u64 modulus,
		u64 mu
) {
	extern __shared__ u64 shared[];
	const int base = blockIdx.x * tile;
	const int thread = threadIdx.x;
	const int half_tile = tile >> 1;
	shared[thread] = values[base + thread];
	shared[thread + half_tile] = values[base + thread + half_tile];
	__syncthreads();
	for (int length = 2; length <= tile; length <<= 1) {
		const int half = length >> 1;
		const int step = count / length;
		const int block = thread / half;
		const int offset = thread - block * half;
		const int index = block * length + offset;
		const u64 left = shared[index];
		const u64 right = multiply_mod(
			shared[index + half],
			twiddles[offset * step],
			modulus,
			mu
		);
		u64 sum = left + right;
		if (sum >= modulus) sum -= modulus;
		shared[index] = sum;
		shared[index + half] = left >= right ? left - right : left + modulus - right;
		__syncthreads();
	}
	values[base + thread] = shared[thread];
	values[base + thread + half_tile] = shared[thread + half_tile];
}

static __global__ void pointwise_multiply(
		u64 *left,
		const u64 *right,
		int count,
		u64 modulus,
		u64 mu
) {
	const int index = blockIdx.x * blockDim.x + threadIdx.x;
	if (index >= count) return;
	left[index] = multiply_mod(left[index], right[index], modulus, mu);
}

static __global__ void scale(
		u64 *values,
		int count,
		u64 inverse_count,
		u64 modulus,
		u64 mu
) {
	const int index = blockIdx.x * blockDim.x + threadIdx.x;
	if (index >= count) return;
	values[index] = multiply_mod(values[index], inverse_count, modulus, mu);
}

static __global__ void reconstruct_crt(
		const u64 *first,
		const u64 *third,
		u64 *output,
		int count
) {
	const int index = blockIdx.x * blockDim.x + threadIdx.x;
	if (index >= count) return;
	const u64 first_residue = first[index];
	const u64 third_residue = third[index];
	const u64 first_mod_third = first_residue % MOD3;
	const u64 delta = third_residue >= first_mod_third
		? third_residue - first_mod_third
		: third_residue + MOD3 - first_mod_third;
	output[index] = first_residue + multiply_mod(delta, MOD1_INV_MOD3, MOD3, MU3) * MOD1;
}

inline bool next_power_of_two(size_t value, int &count, int &logarithm) {
	if (value > static_cast<size_t>(NTT_MAX_TRANSFORM_SIZE)) return false;
	int candidate = 1;
	int candidate_logarithm = 0;
	while (static_cast<size_t>(candidate) < value) {
		candidate <<= 1;
		candidate_logarithm++;
	}
	count = candidate;
	logarithm = candidate_logarithm;
	return true;
}

inline void run_stages(
		u64 *values,
		const u64 *twiddles,
		int count,
		u64 modulus,
		u64 mu,
		cudaStream_t stream
) {
	const int tile = count < SHARED_TILE_SIZE ? count : SHARED_TILE_SIZE;
	fused_stages<<<count / tile, tile / 2, sizeof(u64) * static_cast<size_t>(tile), stream>>>(
		values,
		twiddles,
		count,
		tile,
		modulus,
		mu
	);
	const int butterflies = (count / 2 + BLOCK_SIZE - 1) / BLOCK_SIZE;
	for (int length = 2 * tile; length <= count; length <<= 1) {
		butterfly<<<butterflies, BLOCK_SIZE, 0, stream>>>(
			values,
			twiddles,
			count,
			length,
			modulus,
			mu
		);
	}
}

inline bool forward(
		u64 *values,
		const u64 *twiddles,
		int count,
		int logarithm,
		u64 modulus,
		u64 mu,
		cudaStream_t stream
) {
	bit_reverse<<<(count + BLOCK_SIZE - 1) / BLOCK_SIZE, BLOCK_SIZE, 0, stream>>>(
		values,
		count,
		logarithm
	);
	run_stages(values, twiddles, count, modulus, mu, stream);
	return cudaGetLastError() == cudaSuccess;
}

inline bool inverse(
		u64 *values,
		const u64 *twiddles,
		int count,
		int logarithm,
		u64 modulus,
		u64 mu,
		cudaStream_t stream
) {
	bit_reverse<<<(count + BLOCK_SIZE - 1) / BLOCK_SIZE, BLOCK_SIZE, 0, stream>>>(
		values,
		count,
		logarithm
	);
	run_stages(values, twiddles, count, modulus, mu, stream);
	const u64 inverse_count = host_powmod(static_cast<u64>(count), modulus - 2, modulus);
	scale<<<(count + BLOCK_SIZE - 1) / BLOCK_SIZE, BLOCK_SIZE, 0, stream>>>(
		values,
		count,
		inverse_count,
		modulus,
		mu
	);
	return cudaGetLastError() == cudaSuccess;
}

}

#endif
