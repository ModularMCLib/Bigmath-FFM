#ifndef BIGMATH_ALGOS_H
#define BIGMATH_ALGOS_H

#include "caching/product_cache.h"

#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <algorithm>

#if __has_include(<gmp.h>)
#include <gmp.h>
#elif !defined(BIGMATH_MPZ_STUB_DEFINED)
#define BIGMATH_MPZ_STUB_DEFINED
struct __bigmath_mpz { int _mp_alloc; int _mp_size; unsigned long *_mp_d; };
typedef struct __bigmath_mpz mpz_t[1];
typedef struct __bigmath_mpz *mpz_ptr;
#endif

namespace bigmath {

constexpr int KARATSUBA_THRESHOLD = 32;
constexpr int ALGO_THRESHOLD     = 64;
constexpr int NTT_THRESHOLD      = 4096;

using limb_t = uint64_t;

enum class DirectCudaBackend {
	CUFFT,
	NTT
};

void karatsuba_mul(limb_t *out, const limb_t *a, int alen, const limb_t *b, int blen);

void binary_gcd(mpz_ptr out, mpz_ptr a, mpz_ptr b);

void fast_pow(mpz_ptr out, mpz_ptr base, uint64_t exp);

void product_tree_factorial(mpz_ptr out, uint64_t n);

void fft_multiply(
	mpz_ptr out,
	mpz_ptr a,
	mpz_ptr b,
	const caching::ProductCacheKey *cache_key = nullptr
);

void fft_multiply_into(
	mpz_ptr out,
	mpz_ptr a,
	mpz_ptr b,
	const caching::ProductCacheKey *cache_key = nullptr
);

void accelerated_mul(
	mpz_ptr out,
	mpz_ptr a,
	mpz_ptr b,
	const caching::ProductCacheKey *cache_key = nullptr
);

bool cuda_multiply_direct(
	mpz_ptr out,
	mpz_ptr a,
	mpz_ptr b,
	DirectCudaBackend backend,
	uint64_t max_queue_wait_nanos,
	bool cache_spectra,
	bool reset_host_cache
);

bool cuda_dispatch_favorable(uint64_t left_bits, uint64_t right_bits, bool square);

void modpow(mpz_ptr out, mpz_ptr base, mpz_ptr exp, mpz_ptr mod);

inline limb_t *limb_alloc(int n) {
	return static_cast<limb_t *>(calloc(n, sizeof(limb_t)));
}

}

#endif /* BIGMATH_ALGOS_H */
