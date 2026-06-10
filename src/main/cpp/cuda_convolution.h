#ifndef BIGMATH_CUDA_CONVOLUTION_H
#define BIGMATH_CUDA_CONVOLUTION_H

#include <cstdint>
#include <vector>

namespace bigmath::cuda {

bool convolve_u16_digits(const std::vector<uint16_t> &a,
		const std::vector<uint16_t> &b,
		std::vector<uint16_t> &out,
		unsigned bits_per_digit);

}

#endif /* BIGMATH_CUDA_CONVOLUTION_H */
