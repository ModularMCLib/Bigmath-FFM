#ifndef BIGMATH_ALGOS_H
#define BIGMATH_ALGOS_H

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
#endif

namespace bigmath {

constexpr int KARATSUBA_THRESHOLD = 32;
constexpr int ALGO_THRESHOLD     = 64;
constexpr int NTT_THRESHOLD      = 512;

using limb_t = unsigned long;

void karatsuba_mul(limb_t *out, const limb_t *a, int alen, const limb_t *b, int blen);

void binary_gcd(mpz_t out, const mpz_t a, const mpz_t b);

void fast_pow(mpz_t out, const mpz_t base, unsigned long exp);

void product_tree_factorial(mpz_t out, unsigned long n);

void fft_multiply(mpz_t out, const mpz_t a, const mpz_t b);

inline limb_t *limb_alloc(int n) {
	return static_cast<limb_t *>(calloc(n, sizeof(limb_t)));
}

}

#endif /* BIGMATH_ALGOS_H */
