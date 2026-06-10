#include "cuda_runtime_state.h"
#include "bigmath_ffm.h"

#include <atomic>
#include <chrono>
#include <condition_variable>
#include <mutex>
#include <string>
#include <utility>

#ifdef BIGMATH_HAS_CUDA
#include <cuda_runtime.h>
#ifdef _WIN32
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#endif
#endif

namespace bigmath::cuda {

struct RuntimeState {
	bool initialized = false;
	bool available = false;
	int count = 0;
	int selected_device = -1;
	int probe_count = 0;
	std::string name = "";
	std::string message = "CUDA runtime has not been probed";
};

enum class ProbePhase : int {
	not_started = 0,
	running = 1,
	finished = 2
};

static RuntimeState state;
static std::mutex state_mutex;
static std::condition_variable state_cv;
static std::atomic<int> probe_phase{static_cast<int>(ProbePhase::not_started)};
static std::atomic<int> successful_multiply_count{0};

static RuntimeState unavailable_state(std::string message) {
	RuntimeState result;
	result.initialized = true;
	result.available = false;
	result.count = 0;
	result.selected_device = -1;
	result.probe_count = 1;
	result.name.clear();
	result.message = std::move(message);
	return result;
}

static RuntimeState unavailable_state(std::string message, int device_count) {
	RuntimeState result = unavailable_state(std::move(message));
	result.count = device_count;
	return result;
}

static RuntimeState probe_runtime() {
#ifndef BIGMATH_HAS_CUDA
	return unavailable_state("CUDA support was not compiled for this platform");
#else
	RuntimeState result;
	result.initialized = true;
	result.probe_count = 1;

	int count = 0;
	cudaError_t err = cudaGetDeviceCount(&count);
	if (err != cudaSuccess) {
		return unavailable_state(std::string("cudaGetDeviceCount failed: ") + cudaGetErrorString(err));
	}

	result.count = count;
	if (count <= 0) {
		return unavailable_state("No CUDA devices were detected");
	}

	cudaDeviceProp prop{};
	err = cudaGetDeviceProperties(&prop, 0);
	if (err != cudaSuccess) {
		return unavailable_state(std::string("cudaGetDeviceProperties failed: ") + cudaGetErrorString(err), count);
	}

	err = cudaSetDevice(0);
	if (err != cudaSuccess) {
		return unavailable_state(std::string("cudaSetDevice failed: ") + cudaGetErrorString(err), count);
	}

	result.available = true;
	result.selected_device = 0;
	result.name = prop.name;
	result.message = "CUDA device 0 is available: " + result.name;
	return result;
#endif
}

static bool begin_probe() {
	int expected = static_cast<int>(ProbePhase::not_started);
	return probe_phase.compare_exchange_strong(expected, static_cast<int>(ProbePhase::running),
			std::memory_order_acq_rel);
}

static bool finish_probe(RuntimeState result) {
	std::lock_guard<std::mutex> lock(state_mutex);
	if (probe_phase.load(std::memory_order_acquire) != static_cast<int>(ProbePhase::running)) {
		return false;
	}
	state = std::move(result);
	probe_phase.store(static_cast<int>(ProbePhase::finished), std::memory_order_release);
	state_cv.notify_all();
	return true;
}

static void run_probe_body() {
	finish_probe(probe_runtime());
}

static void wait_for_probe() {
	if (probe_phase.load(std::memory_order_acquire) == static_cast<int>(ProbePhase::finished)) {
		return;
	}
	std::unique_lock<std::mutex> lock(state_mutex);
	if (state_cv.wait_for(lock, std::chrono::seconds(10), [] {
		return probe_phase.load(std::memory_order_acquire) == static_cast<int>(ProbePhase::finished);
	})) {
		return;
	}
	if (probe_phase.load(std::memory_order_acquire) == static_cast<int>(ProbePhase::running)) {
		state = unavailable_state("CUDA runtime probe timed out");
		probe_phase.store(static_cast<int>(ProbePhase::finished), std::memory_order_release);
		state_cv.notify_all();
	}
}

void initialize_once() {
	if (begin_probe()) {
		run_probe_body();
	} else {
		wait_for_probe();
	}
}

void start_background_probe() {
	if (!begin_probe()) {
		return;
	}
#if defined(BIGMATH_HAS_CUDA) && defined(_WIN32)
	HANDLE thread = CreateThread(nullptr, 0, [](LPVOID) -> DWORD {
		run_probe_body();
		return 0;
	}, nullptr, 0, nullptr);
	if (thread != nullptr) {
		CloseHandle(thread);
		return;
	}
	finish_probe(unavailable_state("Failed to start CUDA runtime probe thread"));
#else
	run_probe_body();
#endif
}

static void ensure_initialized() {
	if (probe_phase.load(std::memory_order_acquire) == static_cast<int>(ProbePhase::not_started)) {
		initialize_once();
		return;
	}
	wait_for_probe();
}

bool is_available() {
	ensure_initialized();
	return state.available;
}

int device_count() {
	ensure_initialized();
	return state.count;
}

int device_id() {
	ensure_initialized();
	return state.selected_device;
}

int probe_count() {
	ensure_initialized();
	return state.probe_count;
}

void record_multiply() {
	successful_multiply_count.fetch_add(1, std::memory_order_relaxed);
}

int multiply_count() {
	return successful_multiply_count.load(std::memory_order_relaxed);
}

const char *device_name() {
	ensure_initialized();
	return state.name.c_str();
}

const char *status_message() {
	ensure_initialized();
	return state.message.c_str();
}

}

#ifdef BIGMATH_HAS_CUDA
#ifdef _WIN32
BOOL APIENTRY DllMain(HMODULE module, DWORD reason, LPVOID) {
	if (reason == DLL_PROCESS_ATTACH) {
		DisableThreadLibraryCalls(module);
		bigmath::cuda::start_background_probe();
	}
	return TRUE;
}
#else
__attribute__((constructor))
static void bigmath_cuda_constructor() {
	bigmath::cuda::start_background_probe();
}
#endif
#endif

extern "C" {

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
