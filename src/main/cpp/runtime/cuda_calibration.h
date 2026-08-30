#ifndef BIGMATH_CUDA_CALIBRATION_H
#define BIGMATH_CUDA_CALIBRATION_H

#include <array>
#include <cstddef>
#include <cstdint>

namespace bigmath::runtime {

inline constexpr size_t CUDA_DISPATCH_SHAPES = 5;
inline constexpr size_t CUDA_TRANSFORM_BUCKETS = 24;

enum class CalibratedBackend : uint8_t {
	CPU,
	CUFFT,
	NTT
};

struct DispatchProfileCell {
	bool measured = false;
	CalibratedBackend backend = CalibratedBackend::CPU;
	uint64_t cpu_nanos = 0;
	uint64_t cufft_cold_nanos = 0;
	uint64_t cufft_nanos = 0;
	uint64_t ntt_cold_nanos = 0;
	uint64_t ntt_nanos = 0;
};

struct CudaCalibrationProfile {
	bool completed = false;
	std::array<std::array<DispatchProfileCell, CUDA_TRANSFORM_BUCKETS>, CUDA_DISPATCH_SHAPES> cells{};
	std::array<uint64_t, 4> threshold_bits = {UINT64_MAX, UINT64_MAX, UINT64_MAX, UINT64_MAX};
	uint64_t square_threshold_bits = UINT64_MAX;
	uint32_t ntt_transform_mask = 0;
};

CudaCalibrationProfile calibrate_cuda(uint64_t budget_millis);

}

#endif
