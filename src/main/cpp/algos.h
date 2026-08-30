#ifndef BIGMATH_ALGOS_H
#define BIGMATH_ALGOS_H

#include "caching/product_cache.h"

#include <cstdint>

#if __has_include(<gmp.h>)
#include <gmp.h>
#elif !defined(BIGMATH_MPZ_STUB_DEFINED)
#define BIGMATH_MPZ_STUB_DEFINED
struct __bigmath_mpz { int _mp_alloc; int _mp_size; unsigned long *_mp_d; };
typedef struct __bigmath_mpz mpz_t[1];
typedef struct __bigmath_mpz *mpz_ptr;
#endif

namespace bigmath {

constexpr int NTT_THRESHOLD = 4096;

enum class DirectCudaBackend {
	CUFFT,
	NTT
};

void fast_pow(mpz_ptr out, mpz_ptr base, uint64_t exp);

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

bool try_cuda_multiply(
	mpz_ptr out,
	mpz_ptr a,
	mpz_ptr b,
	const caching::ProductCacheKey *cache_key = nullptr
);

bool cuda_dispatch_favorable(uint64_t left_bits, uint64_t right_bits, bool square);

void modpow(mpz_ptr out, mpz_ptr base, mpz_ptr exp, mpz_ptr mod);

}

#endif /* BIGMATH_ALGOS_H */
