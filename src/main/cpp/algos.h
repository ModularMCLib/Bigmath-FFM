#ifndef BIGMATH_ALGOS_H
#define BIGMATH_ALGOS_H

#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <algorithm>

#if __has_include(<gmp.h>)
#include <gmp.h>
#endif

namespace bigmath {

constexpr int KARATSUBA_THRESHOLD = 32;
constexpr int ALGO_THRESHOLD     = 64;
constexpr int NTT_THRESHOLD      = 512;

using limb_t = unsigned long;

inline int limb_count(const mpz_t n) {
	return mpz_size(n);
}
inline const limb_t *limb_data(const mpz_t n) {
	return mpz_limbs_read(n);
}
inline limb_t *limb_mutable(mpz_t n) {
	return mpz_limbs_modify(n, mpz_size(n));
}
inline int limb_bits(const mpz_t n) {
	return mpz_sizeinbase(n, 2);
}
inline int limb_sign(const mpz_t n) {
	return mpz_sgn(n);
}

// ---- Karatsuba multiplication (in-place on raw limb arrays) ----
void karatsuba_mul(limb_t *out, const limb_t *a, int alen, const limb_t *b, int blen);

// ---- Binary GCD (Stein's algorithm) ----
void binary_gcd(mpz_t out, const mpz_t a, const mpz_t b);

// ---- Exponentiation by squaring ----
void fast_pow(mpz_t out, const mpz_t base, unsigned long exp);

// ---- Product tree factorial for large n ----
void product_tree_factorial(mpz_t out, unsigned long n);

// ---- FFT/NTT-based multiplication for very large integers ----
void fft_multiply(mpz_t out, const mpz_t a, const mpz_t b);

// ---- Helper: allocate limb buffer ----
inline limb_t *limb_alloc(int n) {
	return static_cast<limb_t *>(calloc(n, sizeof(limb_t)));
}

}

#endif /* BIGMATH_ALGOS_H */
