#ifndef BIGMATH_NTT_H
#define BIGMATH_NTT_H

#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <vector>
#include <algorithm>
#include <utility>

namespace bigmath::ntt {

// NTT-friendly primes = k * 2^b + 1 with primitive root 3
// convolve() reconstructs exact coefficients via CRT over MOD1 * MOD3
// (~2^58.8), which also bounds the transform size at MOD1's 2^23 root order.
// MOD2 stays available for the generic transform/convolve_mod API.
inline constexpr uint64_t MOD1 = 998244353;   //  119 * 2^23 + 1
inline constexpr uint64_t MOD2 = 1004535809;  //  479 * 2^21 + 1
inline constexpr uint64_t MOD3 = 469762049;   //    7 * 2^26 + 1
inline constexpr uint64_t PRIMITIVE_ROOT = 3;

using u64 = uint64_t;

// The configured NTT primes keep residue products below uint64_t overflow.
constexpr u64 mod_mul(u64 a, u64 b, u64 mod) {
	return (a % mod) * (b % mod) % mod;
}

constexpr u64 mod_pow(u64 a, u64 e, u64 mod) {
	u64 r = 1;
	while (e) {
		if (e & 1) r = mod_mul(r, a, mod);
		a = mod_mul(a, a, mod);
		e >>= 1;
	}
	return r;
}

// NTT transform (in-place, Cooley-Tukey)
void ntt_transform(u64 *a, int n, u64 mod, u64 root, bool invert);

// Convolution via NTT: c = a * b
std::vector<u64> convolve_mod(const std::vector<u64> &a, const std::vector<u64> &b, u64 mod);

// Multi-modulus convolution with CRT reconstruction to exact u64
std::vector<u64> convolve(const std::vector<u64> &a, const std::vector<u64> &b, u64 digit_base);

// Compute nearest power of 2 >= n
inline int next_pow2(int n) {
	int p = 1;
	while (p < n) p <<= 1;
	return p;
}

}

#endif
