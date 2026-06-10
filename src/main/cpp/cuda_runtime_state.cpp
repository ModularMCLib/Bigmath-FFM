#include "cuda_runtime_state.h"
#include "bigmath_ffm.h"

#include <atomic>
#include <mutex>
#include <string>

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

static RuntimeState state;
static std::once_flag init_flag;
static std::atomic<int> successful_multiply_count{0};

static void probe_runtime() {
	state.initialized = true;
	state.probe_count++;

#ifndef BIGMATH_HAS_CUDA
	state.available = false;
	state.count = 0;
	state.selected_device = -1;
	state.name.clear();
	state.message = "CUDA support was not compiled for this platform";
#else
	int count = 0;
	cudaError_t err = cudaGetDeviceCount(&count);
	if (err != cudaSuccess) {
		state.available = false;
		state.count = 0;
		state.selected_device = -1;
		state.name.clear();
		state.message = std::string("cudaGetDeviceCount failed: ") + cudaGetErrorString(err);
		return;
	}

	state.count = count;
	if (count <= 0) {
		state.available = false;
		state.selected_device = -1;
		state.name.clear();
		state.message = "No CUDA devices were detected";
		return;
	}

	cudaDeviceProp prop{};
	err = cudaGetDeviceProperties(&prop, 0);
	if (err != cudaSuccess) {
		state.available = false;
		state.selected_device = -1;
		state.name.clear();
		state.message = std::string("cudaGetDeviceProperties failed: ") + cudaGetErrorString(err);
		return;
	}

	err = cudaSetDevice(0);
	if (err != cudaSuccess) {
		state.available = false;
		state.selected_device = -1;
		state.name.clear();
		state.message = std::string("cudaSetDevice failed: ") + cudaGetErrorString(err);
		return;
	}

	state.available = true;
	state.selected_device = 0;
	state.name = prop.name;
	state.message = "CUDA device 0 is available: " + state.name;
#endif
}

void initialize_once() {
	std::call_once(init_flag, probe_runtime);
}

bool is_available() {
	initialize_once();
	return state.available;
}

int device_count() {
	initialize_once();
	return state.count;
}

int device_id() {
	initialize_once();
	return state.selected_device;
}

int probe_count() {
	initialize_once();
	return state.probe_count;
}

void record_multiply() {
	successful_multiply_count.fetch_add(1, std::memory_order_relaxed);
}

int multiply_count() {
	return successful_multiply_count.load(std::memory_order_relaxed);
}

const char *device_name() {
	initialize_once();
	return state.name.c_str();
}

const char *status_message() {
	initialize_once();
	return state.message.c_str();
}

}

#ifdef BIGMATH_HAS_CUDA
#ifdef _WIN32
BOOL APIENTRY DllMain(HMODULE, DWORD reason, LPVOID) {
	if (reason == DLL_PROCESS_ATTACH) {
		bigmath::cuda::initialize_once();
	}
	return TRUE;
}
#else
__attribute__((constructor))
static void bigmath_cuda_constructor() {
	bigmath::cuda::initialize_once();
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
