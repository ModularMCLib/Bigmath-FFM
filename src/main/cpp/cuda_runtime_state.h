#ifndef BIGMATH_CUDA_RUNTIME_STATE_H
#define BIGMATH_CUDA_RUNTIME_STATE_H

namespace bigmath::cuda {

void initialize_once();
bool is_available();
int device_count();
int device_id();
int probe_count();
void record_multiply();
int multiply_count();
const char *device_name();
const char *status_message();

}

#endif /* BIGMATH_CUDA_RUNTIME_STATE_H */
