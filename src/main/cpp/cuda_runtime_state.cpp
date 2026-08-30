#include "cuda_runtime_state.h"

#include "caching/product_cache.h"
#include "runtime/cuda_calibration.h"

#include <algorithm>
#include <atomic>
#include <cmath>
#include <condition_variable>
#include <exception>
#include <cstdio>
#include <cstring>
#include <limits>
#include <mutex>
#include <thread>

#ifdef BIGMATH_HAS_CUDA
#include "cuda_convolution.h"
#include "cuda_ntt.h"
#include <cuda_runtime.h>
#endif

static_assert(sizeof(BigmathRuntimeSnapshot) == 920);

namespace bigmath::cuda {
namespace {

enum class InitializationPhase : int {
	NOT_STARTED = 0,
	PROBING = 1,
	CALIBRATING = 2,
	FINISHED = 3
};

struct RuntimeState {
	bool available = false;
	int count = 0;
	int selected_device = -1;
	int probe_count = 0;
	RuntimeBackend active_backend = RuntimeBackend::CPU;
	uint64_t threshold_bits[4] = {UINT64_MAX, UINT64_MAX, UINT64_MAX, UINT64_MAX};
	uint64_t square_threshold_bits = UINT64_MAX;
	uint32_t ntt_transform_mask = 0;
	uint64_t workspace_budget_bytes = 0;
	uint64_t workspace_in_use_bytes = 0;
	uint32_t workspace_capacity = 0;
	uint32_t workspace_in_use = 0;
	bigmath::runtime::CudaCalibrationProfile calibration;
	char name[256] = "";
	char message[512] = "Native runtime initialization has not started";
};

std::mutex state_mutex;
std::condition_variable state_changed;
RuntimeConfiguration configured_options;
RuntimeState state;
std::atomic<int> initialization_phase{static_cast<int>(InitializationPhase::NOT_STARTED)};
std::atomic<uint32_t> calibration_status{
	static_cast<uint32_t>(CalibrationStatus::NOT_STARTED)
};
std::atomic<int> successful_multiply_count{0};
std::atomic<uint64_t> cpu_fallbacks{0};

void copy_text(char *target, size_t target_size, const char *value) {
	if (target_size == 0) return;
	std::snprintf(target, target_size, "%s", value == nullptr ? "" : value);
}

RuntimeState unavailable_state(const char *message, CalibrationStatus status) {
	RuntimeState result;
	result.probe_count = 1;
	copy_text(result.message, sizeof(result.message), message);
	calibration_status.store(static_cast<uint32_t>(status), std::memory_order_release);
	return result;
}

#ifdef BIGMATH_HAS_CUDA
RuntimeState cuda_error_state(const char *operation, cudaError_t error, int count = 0) {
	RuntimeState result = unavailable_state("", CalibrationStatus::FAILED);
	result.count = count;
	std::snprintf(
		result.message,
		sizeof(result.message),
		"%s failed: %s",
		operation,
		cudaGetErrorString(error)
	);
	return result;
}
#endif

RuntimeState probe_runtime(const RuntimeConfiguration &configuration) {
	if (configuration.backend == RuntimeBackend::CPU) {
		RuntimeState result = unavailable_state(
			"CPU backend was selected by RuntimeOptions",
			CalibrationStatus::READY
		);
		result.active_backend = RuntimeBackend::CPU;
		return result;
	}
#ifndef BIGMATH_HAS_CUDA
	return unavailable_state(
		"CUDA support was not compiled for this platform",
		CalibrationStatus::UNAVAILABLE
	);
#else
	RuntimeState result;
	result.probe_count = 1;
	int count = 0;
	cudaError_t error = cudaGetDeviceCount(&count);
	if (error != cudaSuccess) return cuda_error_state("cudaGetDeviceCount", error);
	result.count = count;
	if (count <= 0) {
		return unavailable_state("No CUDA devices were detected", CalibrationStatus::UNAVAILABLE);
	}

	const int selected = configuration.selected_device < 0 ? 0 : configuration.selected_device;
	if (selected >= count) {
		return unavailable_state("Configured CUDA device index is unavailable", CalibrationStatus::FAILED);
	}
	error = cudaSetDevice(selected);
	if (error != cudaSuccess) return cuda_error_state("cudaSetDevice", error, count);
	cudaDeviceProp properties{};
	error = cudaGetDeviceProperties(&properties, selected);
	if (error != cudaSuccess) return cuda_error_state("cudaGetDeviceProperties", error, count);

	size_t free_bytes = 0;
	size_t total_bytes = 0;
	error = cudaMemGetInfo(&free_bytes, &total_bytes);
	if (error != cudaSuccess) return cuda_error_state("cudaMemGetInfo", error, count);
	const long double fraction_budget =
		static_cast<long double>(free_bytes) * configuration.workspace_fraction;
	const uint64_t fraction_bytes = fraction_budget >= static_cast<long double>(UINT64_MAX)
		? UINT64_MAX
		: static_cast<uint64_t>(fraction_budget);

	result.available = true;
	result.selected_device = selected;
	result.workspace_budget_bytes = std::min(
		configuration.workspace_max_bytes,
		fraction_bytes
	);
	if (!configure_convolution_workspace_pool(selected, result.workspace_budget_bytes) ||
			!configure_ntt_workspace_pool(selected)) {
		result.available = false;
		result.active_backend = RuntimeBackend::CPU;
		calibration_status.store(
			static_cast<uint32_t>(CalibrationStatus::FAILED),
			std::memory_order_release
		);
		copy_text(
			result.message,
			sizeof(result.message),
			"Failed to configure the CUDA workspace pool"
		);
		return result;
	}
	copy_text(result.name, sizeof(result.name), properties.name);
	if (configuration.backend == RuntimeBackend::AUTO) {
		result.active_backend = RuntimeBackend::CPU;
		calibration_status.store(
			static_cast<uint32_t>(CalibrationStatus::RUNNING),
			std::memory_order_release
		);
		std::snprintf(
			result.message,
			sizeof(result.message),
			"CUDA device %d is available; calibration is pending: %s",
			selected,
			result.name
		);
	} else {
		result.active_backend = configuration.backend;
		calibration_status.store(
			static_cast<uint32_t>(CalibrationStatus::READY),
			std::memory_order_release
		);
		std::snprintf(
			result.message,
			sizeof(result.message),
			"CUDA device %d is ready with an explicit backend: %s",
			selected,
			result.name
		);
	}
	return result;
#endif
}

void run_initialization() {
	RuntimeConfiguration configuration;
	{
		std::lock_guard lock(state_mutex);
		configuration = configured_options;
	}
	RuntimeState initialized = probe_runtime(configuration);
	{
		std::lock_guard lock(state_mutex);
		state = initialized;
	}
	if (initialized.available && configuration.backend == RuntimeBackend::AUTO) {
		initialization_phase.store(
			static_cast<int>(InitializationPhase::CALIBRATING),
			std::memory_order_release
		);
		state_changed.notify_all();
		try {
			bigmath::runtime::CudaCalibrationProfile profile =
				bigmath::runtime::calibrate_cuda(
					configuration.calibration_millis,
					initialized.workspace_budget_bytes
				);
			{
				std::lock_guard lock(state_mutex);
				state.calibration = profile;
				state.active_backend = profile.completed ? RuntimeBackend::AUTO : RuntimeBackend::CPU;
				for (size_t index = 0; index < 4; index++) {
					state.threshold_bits[index] = profile.threshold_bits[index];
				}
				state.square_threshold_bits = profile.square_threshold_bits;
				state.ntt_transform_mask = profile.ntt_transform_mask;
				if (profile.completed) {
					calibration_status.store(
						static_cast<uint32_t>(CalibrationStatus::READY),
						std::memory_order_release
					);
					std::snprintf(
						state.message,
						sizeof(state.message),
						profile.loaded_from_cache
							? "CUDA calibration loaded from cache for device %d: %s"
							: "CUDA calibration completed for device %d: %s",
						state.selected_device,
						state.name
					);
				} else {
					calibration_status.store(
						static_cast<uint32_t>(CalibrationStatus::FAILED),
						std::memory_order_release
					);
					copy_text(
						state.message,
						sizeof(state.message),
						"CUDA calibration failed; CPU dispatch remains active"
					);
				}
			}
		} catch (const std::exception &exception) {
			std::lock_guard lock(state_mutex);
			state.active_backend = RuntimeBackend::CPU;
			calibration_status.store(
				static_cast<uint32_t>(CalibrationStatus::FAILED),
				std::memory_order_release
			);
			std::snprintf(
				state.message,
				sizeof(state.message),
				"CUDA calibration failed: %s",
				exception.what()
			);
		}
	}
	initialization_phase.store(
		static_cast<int>(InitializationPhase::FINISHED),
		std::memory_order_release
	);
	state_changed.notify_all();
}

void wait_for_initialization() {
	if (initialization_phase.load(std::memory_order_acquire) ==
			static_cast<int>(InitializationPhase::FINISHED)) {
		return;
	}
	std::unique_lock lock(state_mutex);
	state_changed.wait(lock, [] {
		return initialization_phase.load(std::memory_order_acquire) ==
			static_cast<int>(InitializationPhase::FINISHED);
	});
}

void wait_for_probe() {
	const int phase = initialization_phase.load(std::memory_order_acquire);
	if (phase >= static_cast<int>(InitializationPhase::CALIBRATING)) return;
	std::unique_lock lock(state_mutex);
	state_changed.wait(lock, [] {
		return initialization_phase.load(std::memory_order_acquire) >=
			static_cast<int>(InitializationPhase::CALIBRATING);
	});
}

RuntimeState state_snapshot() {
	wait_for_initialization();
	std::lock_guard lock(state_mutex);
	return state;
}

RuntimeState probe_state_snapshot() {
	wait_for_probe();
	std::lock_guard lock(state_mutex);
	return state;
}

}

bool configure(const RuntimeConfiguration &configuration) {
	if (initialization_phase.load(std::memory_order_acquire) !=
			static_cast<int>(InitializationPhase::NOT_STARTED)) {
		return false;
	}
	std::lock_guard lock(state_mutex);
	if (initialization_phase.load(std::memory_order_relaxed) !=
			static_cast<int>(InitializationPhase::NOT_STARTED)) {
		return false;
	}
	configured_options = configuration;
	bigmath::caching::configure_product_cache(
		configuration.product_cache_enabled,
		16,
		static_cast<size_t>(configuration.product_cache_bytes)
	);
	return true;
}

int initialize_async() {
	int expected = static_cast<int>(InitializationPhase::NOT_STARTED);
	if (!initialization_phase.compare_exchange_strong(
			expected,
			static_cast<int>(InitializationPhase::PROBING),
			std::memory_order_acq_rel
	)) {
		return 0;
	}
	try {
		std::thread(run_initialization).detach();
		return 0;
	} catch (const std::exception &exception) {
		{
			std::lock_guard lock(state_mutex);
			state = unavailable_state("", CalibrationStatus::FAILED);
			std::snprintf(
				state.message,
				sizeof(state.message),
				"Failed to start the Native runtime initialization thread: %s",
				exception.what()
			);
		}
		initialization_phase.store(
			static_cast<int>(InitializationPhase::FINISHED),
			std::memory_order_release
		);
		state_changed.notify_all();
		return -1;
	}
}

bool is_available() {
	return probe_state_snapshot().available;
}

bool calibration_ready() {
	return calibration_status.load(std::memory_order_acquire) ==
		static_cast<uint32_t>(CalibrationStatus::READY);
}

RuntimeBackend active_backend() {
	if (!calibration_ready()) return RuntimeBackend::CPU;
	std::lock_guard lock(state_mutex);
	return state.active_backend;
}

DispatchDecision choose_dispatch(const DispatchRequest &request) {
	DispatchDecision decision;
	if (!calibration_ready()) {
		if (calibration_status.load(std::memory_order_acquire) ==
				static_cast<uint32_t>(CalibrationStatus::RUNNING)) {
			cpu_fallbacks.fetch_add(1, std::memory_order_relaxed);
		}
		return decision;
	}
	if (request.left_bits == 0 || request.right_bits == 0 ||
			request.transform_size <= 0) {
		return decision;
	}

	RuntimeState current;
	{
		std::lock_guard lock(state_mutex);
		current = state;
	}
	if (!current.available || current.active_backend == RuntimeBackend::CPU) return decision;
	if (current.active_backend == RuntimeBackend::CUFFT) {
		decision.backend = RuntimeBackend::CUFFT;
		return decision;
	}
	if (current.active_backend == RuntimeBackend::NTT) {
#ifdef BIGMATH_HAS_CUDA
		if (request.transform_size <= bigmath::cuda::NTT_MAX_TRANSFORM_SIZE) {
			decision.backend = RuntimeBackend::NTT;
		}
#endif
		return decision;
	}

	const uint64_t maximum = std::max(request.left_bits, request.right_bits);
	const uint64_t minimum = std::min(request.left_bits, request.right_bits);
	size_t shape = 4;
	if (!request.square) {
		const uint64_t ratio = maximum / minimum + (maximum % minimum == 0 ? 0 : 1);
		if (ratio <= 1) shape = 0;
		else if (ratio <= 2) shape = 1;
		else if (ratio <= 8) shape = 2;
		else if (ratio <= 64) shape = 3;
		else return decision;
	}

	int transform_log = 0;
	int transform = request.transform_size;
	while (transform > 1 && (transform & 1) == 0) {
		transform >>= 1;
		transform_log++;
	}
	if (transform != 1 || transform_log >= static_cast<int>(bigmath::runtime::CUDA_TRANSFORM_BUCKETS)) {
		return decision;
	}
	const bigmath::runtime::DispatchProfileCell &cell =
		current.calibration.cells[shape][static_cast<size_t>(transform_log)];
	if (!cell.measured || cell.backend == bigmath::runtime::CalibratedBackend::CPU) return decision;

	bool workspace_available = false;
	uint64_t gpu_nanos = 0;
#ifdef BIGMATH_HAS_CUDA
	if (cell.backend == bigmath::runtime::CalibratedBackend::NTT) {
		if ((current.ntt_transform_mask & (UINT32_C(1) << transform_log)) == 0) return decision;
		workspace_available = ntt_workspace_available(request.transform_size);
		gpu_nanos = workspace_available ? cell.ntt_nanos : cell.ntt_cold_nanos;
		decision.backend = RuntimeBackend::NTT;
	} else {
		workspace_available = convolution_workspace_available(request.transform_size);
		gpu_nanos = request.spectrum_cached ? cell.cufft_nanos : cell.cufft_cold_nanos;
		decision.backend = RuntimeBackend::CUFFT;
	}
#else
	return DispatchDecision{};
#endif
	if (gpu_nanos == 0 || gpu_nanos >= cell.cpu_nanos) return DispatchDecision{};
	if (!workspace_available) decision.max_queue_wait_nanos = cell.cpu_nanos - gpu_nanos;
	return decision;
}

int device_count() {
	return probe_state_snapshot().count;
}

int device_id() {
	return probe_state_snapshot().selected_device;
}

int probe_count() {
	return probe_state_snapshot().probe_count;
}

void record_multiply() {
	successful_multiply_count.fetch_add(1, std::memory_order_relaxed);
}

int multiply_count() {
	return successful_multiply_count.load(std::memory_order_relaxed);
}

void record_cpu_fallback() {
	cpu_fallbacks.fetch_add(1, std::memory_order_relaxed);
}

uint64_t cpu_fallback_count() {
	return cpu_fallbacks.load(std::memory_order_relaxed);
}

const char *device_name() {
	thread_local char copy[256];
	RuntimeState current = state_snapshot();
	copy_text(copy, sizeof(copy), current.name);
	return copy;
}

const char *status_message() {
	thread_local char copy[512];
	RuntimeState current = state_snapshot();
	copy_text(copy, sizeof(copy), current.message);
	return copy;
}

int snapshot(BigmathRuntimeSnapshot *out) {
	if (out == nullptr) return -1;
	RuntimeState current = state_snapshot();
	std::memset(out, 0, sizeof(*out));
	for (size_t index = 0; index < 4; index++) out->threshold_bits[index] = current.threshold_bits[index];
	out->square_threshold_bits = current.square_threshold_bits;
	out->workspace_budget_bytes = current.workspace_budget_bytes;
	out->workspace_in_use_bytes = current.workspace_in_use_bytes;
	out->cpu_fallback_count = cpu_fallback_count();
	const bigmath::caching::ProductCacheMetrics cache_metrics =
		bigmath::caching::product_cache_metrics();
	out->product_cache_hits = cache_metrics.hits;
	out->product_cache_misses = cache_metrics.misses;
	out->product_cache_admissions = cache_metrics.admissions;
	out->product_cache_evictions = cache_metrics.evictions;
	out->product_cache_bytes = cache_metrics.bytes;
	out->schema_version = 1;
	out->calibration_status = calibration_status.load(std::memory_order_acquire);
	out->active_backend = static_cast<uint32_t>(current.active_backend);
	out->configured_backend = static_cast<uint32_t>(configured_options.backend);
	out->cuda_available = current.available ? 1 : 0;
	out->device_count = static_cast<uint32_t>(current.count);
	out->selected_device = current.selected_device;
	out->ntt_enabled = current.ntt_transform_mask != 0 ? 1 : 0;
	out->ntt_transform_mask = current.ntt_transform_mask;
	out->workspace_capacity = current.workspace_capacity;
	out->workspace_in_use = current.workspace_in_use;
#ifdef BIGMATH_HAS_CUDA
	if (current.available) {
		out->workspace_budget_bytes = convolution_workspace_budget_bytes();
		out->workspace_in_use_bytes =
			convolution_workspace_in_use_bytes() + ntt_workspace_in_use_bytes();
		out->workspace_capacity = static_cast<uint32_t>(
			convolution_workspace_capacity() + ntt_workspace_capacity()
		);
		out->workspace_in_use = static_cast<uint32_t>(
			convolution_workspace_in_use() + ntt_workspace_in_use()
		);
	}
#endif
	out->probe_count = static_cast<uint32_t>(current.probe_count);
	copy_text(out->device_name, sizeof(out->device_name), current.name);
	copy_text(out->status_message, sizeof(out->status_message), current.message);
	return 0;
}

}

extern "C" {

int32_t bigmath_runtime_configure(
		int32_t product_cache_enabled,
		uint64_t product_cache_bytes,
		double gpu_workspace_fraction,
		uint64_t gpu_workspace_max_bytes,
		uint64_t calibration_millis,
		int32_t cuda_device,
		int32_t cuda_backend
) {
	if (!std::isfinite(gpu_workspace_fraction) || gpu_workspace_fraction <= 0.0 ||
			gpu_workspace_fraction > 1.0 || gpu_workspace_max_bytes == 0 ||
			calibration_millis == 0 || cuda_device < -1 || cuda_backend < 0 || cuda_backend > 3 ||
			product_cache_bytes > static_cast<uint64_t>(SIZE_MAX)) {
		return -1;
	}
	bigmath::cuda::RuntimeConfiguration configuration;
	configuration.product_cache_enabled = product_cache_enabled != 0;
	configuration.product_cache_bytes = product_cache_bytes;
	configuration.workspace_fraction = gpu_workspace_fraction;
	configuration.workspace_max_bytes = gpu_workspace_max_bytes;
	configuration.calibration_millis = calibration_millis;
	configuration.selected_device = cuda_device;
	configuration.backend = static_cast<bigmath::cuda::RuntimeBackend>(cuda_backend);
	return bigmath::cuda::configure(configuration) ? 0 : -2;
}

int32_t bigmath_runtime_initialize() {
	return bigmath::cuda::initialize_async();
}

int32_t bigmath_runtime_snapshot(BigmathRuntimeSnapshot *snapshot) {
	return bigmath::cuda::snapshot(snapshot);
}

int bigmath_cuda_available() {
	return bigmath::cuda::is_available() ? 1 : 0;
}

int bigmath_cuda_device_count() {
	return bigmath::cuda::device_count();
}

int bigmath_cuda_probe_count() {
	return bigmath::cuda::probe_count();
}

int bigmath_cuda_multiply_count() {
	return bigmath::cuda::multiply_count();
}

const char *bigmath_cuda_device_name() {
	return bigmath::cuda::device_name();
}

const char *bigmath_cuda_status_message() {
	return bigmath::cuda::status_message();
}

}
