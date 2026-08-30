#ifndef BIGMATH_CUDA_CALIBRATION_CACHE_H
#define BIGMATH_CUDA_CALIBRATION_CACHE_H

#include "cuda_calibration.h"

#include <filesystem>
#include <string>

namespace bigmath::runtime {

class CudaCalibrationCache final {
public:
	explicit CudaCalibrationCache(std::string key);
	~CudaCalibrationCache();

	CudaCalibrationCache(const CudaCalibrationCache &) = delete;
	CudaCalibrationCache &operator=(const CudaCalibrationCache &) = delete;

	bool load(CudaCalibrationProfile &profile) const;
	void store(const CudaCalibrationProfile &profile) const;

private:
	std::string key_;
	std::filesystem::path data_path_;
#ifdef _WIN32
	void *lock_handle_ = nullptr;
#else
	int lock_fd_ = -1;
#endif
	bool locked_ = false;
};

}

#endif
