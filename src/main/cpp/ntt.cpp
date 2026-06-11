#include "ntt.h"

namespace bigmath::ntt {

namespace {

// Modular helpers for compile-time moduli. With MOD as a template constant the
// compiler strength-reduces % into multiply+shift, so the butterfly loops run
// without div instructions. Operands must already be reduced; products fit in
// u64 because every configured modulus is below 2^30.
template <u64 MOD>
inline u64 mul_mod(u64 a, u64 b) {
	return a * b % MOD;
}

template <u64 MOD>
inline u64 add_mod(u64 a, u64 b) {
	const u64 sum = a + b;
	return sum >= MOD ? sum - MOD : sum;
}

template <u64 MOD>
inline u64 sub_mod(u64 a, u64 b) {
	return a >= b ? a - b : a + MOD - b;
}

void bit_reverse_permute(u64 *a, int n) {
	for (int i = 1, j = 0; i < n; i++) {
		int bit = n >> 1;
		while (j & bit) {
			j ^= bit;
			bit >>= 1;
		}
		j ^= bit;
		if (i < j) std::swap(a[i], a[j]);
	}
}

// In-place Cooley-Tukey transform with the modulus as a compile-time constant.
// Each level expands its twiddle factors into tw once and reuses the row for
// every block, replacing the serial w *= wlen chain inside the inner loops.
template <u64 MOD>
void transform_fixed(u64 *a, int n, bool invert, std::vector<u64> &tw) {
	bit_reverse_permute(a, n);
	if ((int)tw.size() < n / 2) {
		tw.resize(n / 2);
	}
	const u64 root_base = invert ? mod_pow(PRIMITIVE_ROOT, MOD - 2, MOD) : PRIMITIVE_ROOT;
	for (int len = 2; len <= n; len <<= 1) {
		const int half = len >> 1;
		const u64 wlen = mod_pow(root_base, (MOD - 1) / (u64)len, MOD);
		tw[0] = 1;
		for (int j = 1; j < half; j++) {
			tw[j] = mul_mod<MOD>(tw[j - 1], wlen);
		}
		for (int i = 0; i < n; i += len) {
			u64 *lo = a + i;
			u64 *hi = lo + half;
			for (int j = 0; j < half; j++) {
				const u64 u_val = lo[j];
				const u64 v_val = mul_mod<MOD>(hi[j], tw[j]);
				lo[j] = add_mod<MOD>(u_val, v_val);
				hi[j] = sub_mod<MOD>(u_val, v_val);
			}
		}
	}
	if (invert) {
		const u64 inv_n = mod_pow((u64)n, MOD - 2, MOD);
		for (int i = 0; i < n; i++) {
			a[i] = mul_mod<MOD>(a[i], inv_n);
		}
	}
}

// Fallback for runtime moduli below 2^32 with reduced inputs; same structure,
// one division per multiply instead of three.
void transform_any(u64 *a, int n, u64 mod, u64 root, bool invert, std::vector<u64> &tw) {
	bit_reverse_permute(a, n);
	if ((int)tw.size() < n / 2) {
		tw.resize(n / 2);
	}
	const u64 root_base = invert ? mod_pow(root, mod - 2, mod) : root;
	for (int len = 2; len <= n; len <<= 1) {
		const int half = len >> 1;
		const u64 wlen = mod_pow(root_base, (mod - 1) / (u64)len, mod);
		tw[0] = 1;
		for (int j = 1; j < half; j++) {
			tw[j] = tw[j - 1] * wlen % mod;
		}
		for (int i = 0; i < n; i += len) {
			u64 *lo = a + i;
			u64 *hi = lo + half;
			for (int j = 0; j < half; j++) {
				const u64 u_val = lo[j];
				const u64 v_val = hi[j] * tw[j] % mod;
				const u64 sum = u_val + v_val;
				lo[j] = sum >= mod ? sum - mod : sum;
				hi[j] = u_val >= v_val ? u_val - v_val : u_val + mod - v_val;
			}
		}
	}
	if (invert) {
		const u64 inv_n = mod_pow((u64)n, mod - 2, mod);
		for (int i = 0; i < n; i++) {
			a[i] = a[i] * inv_n % mod;
		}
	}
}

template <u64 MOD>
std::vector<u64> convolve_mod_fixed(const std::vector<u64> &a, const std::vector<u64> &b) {
	const size_t result_size = a.size() + b.size() - 1;
	const int n = next_pow2((int)result_size);
	if ((MOD - 1) % (u64)n != 0) {
		return {};
	}
	std::vector<u64> fa(n), fb(n), tw;
	for (size_t i = 0; i < a.size(); i++) fa[i] = a[i] % MOD;
	for (size_t i = 0; i < b.size(); i++) fb[i] = b[i] % MOD;

	transform_fixed<MOD>(fa.data(), n, false, tw);
	transform_fixed<MOD>(fb.data(), n, false, tw);
	for (int i = 0; i < n; i++) {
		fa[i] = mul_mod<MOD>(fa[i], fb[i]);
	}
	transform_fixed<MOD>(fa.data(), n, true, tw);

	fa.resize(result_size);
	return fa;
}

}

// ---- NTT transform (in-place, Cooley-Tukey) ----
void ntt_transform(u64 *a, int n, u64 mod, u64 root, bool invert) {
	std::vector<u64> tw;
	if (root == PRIMITIVE_ROOT) {
		switch (mod) {
			case MOD1: transform_fixed<MOD1>(a, n, invert, tw); return;
			case MOD2: transform_fixed<MOD2>(a, n, invert, tw); return;
			case MOD3: transform_fixed<MOD3>(a, n, invert, tw); return;
			default: break;
		}
	}
	transform_any(a, n, mod, root, invert, tw);
}

// ---- Single-modulus convolution ----
std::vector<u64> convolve_mod(const std::vector<u64> &a, const std::vector<u64> &b, u64 mod) {
	if (a.empty() || b.empty()) {
		return {};
	}
	switch (mod) {
		case MOD1: return convolve_mod_fixed<MOD1>(a, b);
		case MOD2: return convolve_mod_fixed<MOD2>(a, b);
		case MOD3: return convolve_mod_fixed<MOD3>(a, b);
		default: break;
	}
	const size_t result_size = a.size() + b.size() - 1;
	const int n = next_pow2((int)result_size);
	if ((mod - 1) % (u64)n != 0) {
		return {};
	}
	std::vector<u64> fa(n), fb(n), tw;
	for (size_t i = 0; i < a.size(); i++) fa[i] = a[i] % mod;
	for (size_t i = 0; i < b.size(); i++) fb[i] = b[i] % mod;

	transform_any(fa.data(), n, mod, PRIMITIVE_ROOT, false, tw);
	transform_any(fb.data(), n, mod, PRIMITIVE_ROOT, false, tw);
	for (int i = 0; i < n; i++) {
		fa[i] = fa[i] * fb[i] % mod;
	}
	transform_any(fa.data(), n, mod, PRIMITIVE_ROOT, true, tw);

	fa.resize(result_size);
	return fa;
}

// ---- Dual-modulus convolution with CRT ----
// Coefficients are bounded by min(|a|,|b|) * (base-1)^2, so reconstruction
// over MOD1 * MOD3 (~2^58.8) is exact whenever that bound stays below it.
std::vector<u64> convolve(const std::vector<u64> &a, const std::vector<u64> &b, u64 digit_base) {
	if (a.empty() || b.empty() || digit_base < 2) {
		return {};
	}
	static constexpr u64 CRT_MODULUS = MOD1 * MOD3;
	const u64 max_digit = digit_base - 1;
	if ((max_digit >> 32) != 0) {
		return {};
	}
	const u64 min_len = (u64)std::min(a.size(), b.size());
	if (max_digit * max_digit > CRT_MODULUS / min_len) {
		return {};
	}

	auto c1 = convolve_mod_fixed<MOD1>(a, b);
	auto c3 = convolve_mod_fixed<MOD3>(a, b);
	if (c1.empty() || c3.empty()) {
		return {};
	}

	// x = r1 + k * MOD1 with k = (r3 - r1) * MOD1^-1 (mod MOD3), x < MOD1*MOD3
	static constexpr u64 MOD1_INV_MOD3 = mod_pow(MOD1, MOD3 - 2, MOD3);
	for (size_t i = 0; i < c1.size(); i++) {
		const u64 r1 = c1[i];
		const u64 delta = sub_mod<MOD3>(c3[i], r1 % MOD3);
		c1[i] = r1 + mul_mod<MOD3>(delta, MOD1_INV_MOD3) * MOD1;
	}
	return c1;
}

}
