#include "ntt.h"

namespace bigmath::ntt {

// ---- NTT transform (in-place, Cooley-Tukey) ----
void ntt_transform(u64 *a, int n, u64 mod, u64 root, bool invert) {
	// Bit-reversal permutation
	for (int i = 1, j = 0; i < n; i++) {
		int bit = n >> 1;
		while (j & bit) {
			j ^= bit;
			bit >>= 1;
		}
		j ^= bit;
		if (i < j) std::swap(a[i], a[j]);
	}

	for (int len = 2; len <= n; len <<= 1) {
		u64 wlen = mod_pow(root, (mod - 1) / len, mod);
		if (invert) wlen = mod_pow(wlen, mod - 2, mod);
		for (int i = 0; i < n; i += len) {
			u64 w = 1;
			for (int j = 0; j < len / 2; j++) {
				u64 u_val = a[i + j];
				u64 v_val = mod_mul(a[i + j + len / 2], w, mod);
				a[i + j] = (u_val + v_val) % mod;
				a[i + j + len / 2] = (u_val + mod - v_val) % mod;
				w = mod_mul(w, wlen, mod);
			}
		}
	}

	if (invert) {
		u64 inv_n = mod_pow(n, mod - 2, mod);
		for (int i = 0; i < n; i++) {
			a[i] = mod_mul(a[i], inv_n, mod);
		}
	}
}

// ---- Single-modulus convolution ----
std::vector<u64> convolve_mod(const std::vector<u64> &a, const std::vector<u64> &b, u64 mod) {
	int n = next_pow2((int)(a.size() + b.size() - 1));
	std::vector<u64> fa(n), fb(n);
	for (size_t i = 0; i < a.size(); i++) fa[i] = a[i] % mod;
	for (size_t i = 0; i < b.size(); i++) fb[i] = b[i] % mod;

	u64 root = mod_pow(PRIMITIVE_ROOT, (mod - 1) / n, mod);
	// Need to find proper primitive root for the padded size
	// For NTT primes where mod-1 has factor n, this root works
	while (mod_pow(root, n, mod) != 1) {
		root = mod_pow(root, 2, mod);
	}

	ntt_transform(fa.data(), n, mod, root, false);
	ntt_transform(fb.data(), n, mod, root, false);
	for (int i = 0; i < n; i++) fa[i] = mod_mul(fa[i], fb[i], mod);
	ntt_transform(fa.data(), n, mod, root, true);

	std::vector<u64> res(a.size() + b.size() - 1);
	for (size_t i = 0; i < res.size(); i++) res[i] = fa[i];
	return res;
}

// ---- CRT reconstruction from 3 moduli ----
// Given x ≡ r1 (mod m1), x ≡ r2 (mod m2), x ≡ r3 (mod m3)
// Returns x (which is < m1*m2*m3)
static u64 crt3(u64 r1, u64 m1, u64 r2, u64 m2, u64 r3, u64 m3) {
	// Combine first two moduli
	// x = r1 + k * m1 ≡ r2 (mod m2)  =>  k ≡ (r2 - r1) * m1^-1 (mod m2)
	u64 m1_inv_m2 = mod_pow(m1 % m2, m2 - 2, m2);
	u64 k1 = mod_mul((r2 + m2 - r1 % m2) % m2, m1_inv_m2, m2);
	u64 x12 = r1 + k1 * m1;  // x mod (m1*m2), exact value
	u64 m12 = m1 * m2;

	// Combine with third modulus
	u64 m12_inv_m3 = mod_pow(m12 % m3, m3 - 2, m3);
	u64 k2 = mod_mul((r3 + m3 - x12 % m3) % m3, m12_inv_m3, m3);
	return x12 + k2 * m12;
}

// ---- Multi-modulus convolution with CRT ----
std::vector<u64> convolve(const std::vector<u64> &a, const std::vector<u64> &b, u64 digit_base) {
	auto c1 = convolve_mod(a, b, MOD1);
	auto c2 = convolve_mod(a, b, MOD2);
	auto c3 = convolve_mod(a, b, MOD3);

	size_t n = c1.size();
	std::vector<u64> res(n);
	for (size_t i = 0; i < n; i++) {
		res[i] = crt3(c1[i], MOD1, c2[i], MOD2, c3[i], MOD3);
	}
	return res;
}

}
