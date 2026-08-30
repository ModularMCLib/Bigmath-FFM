#include "cuda_workspace_budget.h"

#include <mutex>
#include <unordered_map>

namespace bigmath::runtime {
namespace {

struct DeviceBudget {
	uint64_t budget = 0;
	uint64_t allocated = 0;
};

std::mutex budget_mutex;
std::unordered_map<int, DeviceBudget> device_budgets;

}

bool configure_cuda_workspace_budget(int device, uint64_t budget_bytes) {
	if (device < 0) return false;
	std::lock_guard lock(budget_mutex);
	DeviceBudget &budget = device_budgets[device];
	if (budget.allocated != 0) return false;
	budget.budget = budget_bytes;
	return true;
}

bool reserve_cuda_workspace_bytes(int device, uint64_t bytes) {
	std::lock_guard lock(budget_mutex);
	auto iterator = device_budgets.find(device);
	if (iterator == device_budgets.end()) return false;
	DeviceBudget &budget = iterator->second;
	if (bytes > budget.budget || budget.allocated > budget.budget - bytes) return false;
	budget.allocated += bytes;
	return true;
}

void release_cuda_workspace_bytes(int device, uint64_t bytes) {
	std::lock_guard lock(budget_mutex);
	auto iterator = device_budgets.find(device);
	if (iterator == device_budgets.end()) return;
	DeviceBudget &budget = iterator->second;
	budget.allocated = bytes >= budget.allocated ? 0 : budget.allocated - bytes;
}

uint64_t cuda_workspace_budget_bytes(int device) {
	std::lock_guard lock(budget_mutex);
	auto iterator = device_budgets.find(device);
	return iterator == device_budgets.end() ? 0 : iterator->second.budget;
}

uint64_t cuda_workspace_allocated_bytes(int device) {
	std::lock_guard lock(budget_mutex);
	auto iterator = device_budgets.find(device);
	return iterator == device_budgets.end() ? 0 : iterator->second.allocated;
}

}
