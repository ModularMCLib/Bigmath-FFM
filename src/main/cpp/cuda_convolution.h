#ifndef BIGMATH_CUDA_CONVOLUTION_H
#define BIGMATH_CUDA_CONVOLUTION_H

#include <cstdint>
#include <vector>

namespace bigmath::cuda {

bool convolve_u16_digits(const std::vector<uint16_t> &a,
		const std::vector<uint16_t> &b,
		std::vector<uint16_t> &out,
		unsigned bits_per_digit);

bool convolve_u16_digits_to_limbs(const std::vector<uint16_t> &a,
		const std::vector<uint16_t> &b,
		std::vector<uint64_t> &out,
		unsigned bits_per_digit,
		unsigned limb_bits);

bool convolve_digits(const std::vector<uint64_t> &a,
		const std::vector<uint64_t> &b,
		std::vector<uint64_t> &out,
		unsigned bits_per_digit);

}

#endif /* BIGMATH_CUDA_CONVOLUTION_H */
