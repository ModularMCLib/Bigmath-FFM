#ifndef BIGMATH_CUDA_WORKSPACE_BUDGET_H
#define BIGMATH_CUDA_WORKSPACE_BUDGET_H

#include <cstdint>

namespace bigmath::runtime {

bool configure_cuda_workspace_budget(int device, uint64_t budget_bytes);
bool reserve_cuda_workspace_bytes(int device, uint64_t bytes);
void release_cuda_workspace_bytes(int device, uint64_t bytes);
uint64_t cuda_workspace_budget_bytes(int device);
uint64_t cuda_workspace_allocated_bytes(int device);

}

#endif
