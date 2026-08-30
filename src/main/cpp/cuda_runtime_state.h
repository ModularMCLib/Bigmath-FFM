#ifndef BIGMATH_CUDA_RUNTIME_STATE_H
#define BIGMATH_CUDA_RUNTIME_STATE_H

#include "bigmath_ffm.h"

#include <cstdint>

namespace bigmath::cuda {

enum class RuntimeBackend : uint32_t {
	AUTO = 0,
	CPU = 1,
	CUFFT = 2,
	NTT = 3
};

enum class CalibrationStatus : uint32_t {
	NOT_STARTED = 0,
	RUNNING = 1,
	READY = 2,
	FAILED = 3,
	UNAVAILABLE = 4
};

struct RuntimeConfiguration {
	bool product_cache_enabled = true;
	uint64_t product_cache_bytes = UINT64_C(64) * 1024 * 1024;
	double workspace_fraction = 0.25;
	uint64_t workspace_max_bytes = UINT64_C(512) * 1024 * 1024;
	uint64_t calibration_millis = 10000;
	int selected_device = -1;
	RuntimeBackend backend = RuntimeBackend::AUTO;
};

struct DispatchRequest {
	uint64_t left_bits;
	uint64_t right_bits;
	int transform_size;
	bool square;
	bool spectrum_cached;
};

struct DispatchDecision {
	RuntimeBackend backend = RuntimeBackend::CPU;
	uint64_t max_queue_wait_nanos = 0;
};

bool configure(const RuntimeConfiguration &configuration);
int initialize_async();
bool is_available();
bool calibration_ready();
RuntimeBackend active_backend();
DispatchDecision choose_dispatch(const DispatchRequest &request);
int device_count();
int device_id();
int probe_count();
void record_multiply();
int multiply_count();
void record_cpu_fallback();
uint64_t cpu_fallback_count();
const char *device_name();
const char *status_message();
int snapshot(BigmathRuntimeSnapshot *out);

}

#endif
