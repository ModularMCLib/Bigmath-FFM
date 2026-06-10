#ifndef BIGMATH_CUDA_CONVOLUTION_H
#define BIGMATH_CUDA_CONVOLUTION_H

#include <cstdint>
#include <vector>

namespace bigmath::cuda {

struct U16DigitsCacheToken {
	const void *owner = nullptr;
	uint64_t version = 0;

	bool valid() const {
		return owner != nullptr && version != 0;
	}
};

bool convolve_u16_digits(const std::vector<uint16_t> &a,
		const std::vector<uint16_t> &b,
		std::vector<uint16_t> &out,
		unsigned bits_per_digit,
		U16DigitsCacheToken a_token = {},
		U16DigitsCacheToken b_token = {});

bool convolve_digits(const std::vector<uint64_t> &a,
		const std::vector<uint64_t> &b,
		std::vector<uint64_t> &out,
		unsigned bits_per_digit);

}

#endif /* BIGMATH_CUDA_CONVOLUTION_H */
