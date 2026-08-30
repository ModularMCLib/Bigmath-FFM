#ifndef BIGMATH_CUDA_MODULAR_H
#define BIGMATH_CUDA_MODULAR_H

#include <cstdint>
#include <vector>

namespace bigmath::cuda {

// Selects the device-resident reduction for one fixed modulus. Montgomery is
// valid only for odd moduli; Barrett accepts any positive modulus.
enum class ResidentReduction {
	MONTGOMERY,
	BARRETT
};

// Configures the process-wide pool for the selected CUDA device. Leases are
// exclusive, share the global CUDA workspace budget, and may be used by any
// caller thread after runtime initialization.
bool configure_modular_workspace_pool(int device);
uint64_t modular_workspace_in_use_bytes();
int modular_workspace_capacity();
int modular_workspace_in_use();

// Computes base^exponent mod modulus while all intermediate values remain on
// one device and stream. Inputs are little-endian base-2^16 digits. The caller
// must provide the matching precomputed reduction constant and identity. On
// any allocation, launch, synchronization, normalization, or range failure the
// function returns false and leaves result unchanged so the caller can safely
// fall back to mpz_powm.
bool resident_modpow_u16(
	const std::vector<uint16_t> &base,
	const std::vector<uint16_t> &exponent,
	const std::vector<uint16_t> &modulus,
	const std::vector<uint16_t> &reduction_constant,
	const std::vector<uint16_t> &identity,
	ResidentReduction reduction,
	uint64_t max_queue_wait_nanos,
	std::vector<uint16_t> &result
);

}

#endif
