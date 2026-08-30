#ifndef BIGMATH_CUDA_CONVOLUTION_H
#define BIGMATH_CUDA_CONVOLUTION_H

#include <cstdint>
#include <vector>

namespace bigmath::cuda {

bool convolve_u16_digits(const std::vector<uint16_t> &a,
		const std::vector<uint16_t> &b,
		std::vector<uint16_t> &out,
		unsigned bits_per_digit,
		uint64_t max_queue_wait_nanos = 0);

bool convolve_u16_digits_to_limbs(const std::vector<uint16_t> &a,
		const std::vector<uint16_t> &b,
		std::vector<uint64_t> &out,
		unsigned bits_per_digit,
		unsigned limb_bits,
		uint64_t max_queue_wait_nanos = 0);

bool configure_convolution_workspace_pool(int device, uint64_t budget_bytes);
uint64_t convolution_workspace_budget_bytes();
uint64_t convolution_workspace_in_use_bytes();
int convolution_workspace_capacity();
int convolution_workspace_in_use();
bool convolution_workspace_available(int transform_size);

}

#endif /* BIGMATH_CUDA_CONVOLUTION_H */
