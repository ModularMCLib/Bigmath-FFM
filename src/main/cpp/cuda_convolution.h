#ifndef BIGMATH_CUDA_CONVOLUTION_H
#define BIGMATH_CUDA_CONVOLUTION_H

#include <cstdint>
#include <vector>

namespace bigmath::cuda {

bool convolve_base256(const std::vector<uint64_t> &a,
		const std::vector<uint64_t> &b,
		std::vector<uint64_t> &out);

}

#endif /* BIGMATH_CUDA_CONVOLUTION_H */
