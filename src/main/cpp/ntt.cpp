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

	u64 root_base = invert ? mod_pow(root, mod - 2, mod) : root;
	for (int len = 2; len <= n; len <<= 1) {
		u64 wlen = mod_pow(root_base, (mod - 1) / len, mod);
		for (int i = 0; i < n; i += len) {
			u64 w = 1;
			for (int j = 0; j < len / 2; j++) {
				u64 u_val = a[i + j];
				u64 v_val = mod_mul(a[i + j + len / 2], w, mod);
				u64 sum = u_val + v_val;
				if (sum >= mod) sum -= mod;
				a[i + j] = sum;
				a[i + j + len / 2] = u_val >= v_val ? u_val - v_val : u_val + mod - v_val;
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
	if ((mod - 1) % (u64)n != 0) {
		return {};
	}
	std::vector<u64> fa(n), fb(n);
	for (size_t i = 0; i < a.size(); i++) fa[i] = a[i] % mod;
	for (size_t i = 0; i < b.size(); i++) fb[i] = b[i] % mod;

	ntt_transform(fa.data(), n, mod, PRIMITIVE_ROOT, false);
	ntt_transform(fb.data(), n, mod, PRIMITIVE_ROOT, false);
	for (int i = 0; i < n; i++) fa[i] = mod_mul(fa[i], fb[i], mod);
	ntt_transform(fa.data(), n, mod, PRIMITIVE_ROOT, true);

	std::vector<u64> res(a.size() + b.size() - 1);
	for (size_t i = 0; i < res.size(); i++) res[i] = fa[i];
	return res;
}

// ---- CRT reconstruction from 3 moduli ----
// Given x ≡ r1 (mod m1), x ≡ r2 (mod m2), x ≡ r3 (mod m3)
// Returns x (which is < m1*m2*m3)
static u64 crt3(u64 r1, u64 r2, u64 r3, u64 m1_inv_m2, u64 m12_inv_m3) {
	// Combine first two moduli
	// x = r1 + k * m1 ≡ r2 (mod m2)  =>  k ≡ (r2 - r1) * m1^-1 (mod m2)
	u64 r1_mod_m2 = r1 % MOD2;
	u64 delta12 = r2 >= r1_mod_m2 ? r2 - r1_mod_m2 : r2 + MOD2 - r1_mod_m2;
	u64 k1 = mod_mul(delta12, m1_inv_m2, MOD2);
	u64 x12 = r1 + k1 * MOD1;  // x mod (m1*m2), exact value
	u64 m12 = MOD1 * MOD2;

	// Combine with third modulus
	u64 x12_mod_m3 = x12 % MOD3;
	u64 delta3 = r3 >= x12_mod_m3 ? r3 - x12_mod_m3 : r3 + MOD3 - x12_mod_m3;
	u64 k2 = mod_mul(delta3, m12_inv_m3, MOD3);
	return x12 + k2 * m12;
}

// ---- Multi-modulus convolution with CRT ----
std::vector<u64> convolve(const std::vector<u64> &a, const std::vector<u64> &b, u64 digit_base) {
	auto c1 = convolve_mod(a, b, MOD1);
	auto c2 = convolve_mod(a, b, MOD2);
	auto c3 = convolve_mod(a, b, MOD3);
	if (c1.empty() || c2.empty() || c3.empty()) {
		return {};
	}

	size_t n = c1.size();
	std::vector<u64> res(n);
	u64 m1_inv_m2 = mod_pow(MOD1 % MOD2, MOD2 - 2, MOD2);
	u64 m12_inv_m3 = mod_pow((MOD1 * MOD2) % MOD3, MOD3 - 2, MOD3);
	for (size_t i = 0; i < n; i++) {
		res[i] = crt3(c1[i], c2[i], c3[i], m1_inv_m2, m12_inv_m3);
	}
	return res;
}

}
