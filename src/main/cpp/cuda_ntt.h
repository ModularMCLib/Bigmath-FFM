#ifndef BIGMATH_CUDA_NTT_H
#define BIGMATH_CUDA_NTT_H

#include <cstdint>
#include <vector>

namespace bigmath::cuda {

inline constexpr int NTT_MAX_TRANSFORM_SIZE = 1 << 23;

// GPU integer NTT convolution with dual-modulus CRT reconstruction.
//
// Unlike the cuFFT (FP64) path, coefficients are computed in exact modular
// arithmetic over two NTT-friendly primes and recombined via CRT, so the
// digit base never has to shrink to keep products inside a double's 2^50
// exact-integer range. Inputs are little-endian base-2^bits_per_digit digits
// (bits_per_digit must be 16); the product is packed into limb_bits-wide limbs.
//
// Returns false (caller should fall back) when CUDA is unavailable, the size is
// out of range for the configured primes, the CRT bound would be exceeded, or a
// device operation fails.
bool convolve_ntt_u16_to_limbs(const std::vector<uint16_t> &a,
		const std::vector<uint16_t> &b,
		std::vector<uint64_t> &out,
		unsigned bits_per_digit,
		unsigned limb_bits);

}

#endif /* BIGMATH_CUDA_NTT_H */
