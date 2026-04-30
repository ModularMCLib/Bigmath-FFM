#include "algos.h"
#include "ntt.h"
#include <cstring>
#include <vector>

namespace bigmath {

// ---- Helper: add/sub limb arrays (no GMP dependency) ----
static void add_limbs(limb_t *out, const limb_t *a, int n) {
	limb_t carry = 0;
	for (int i = 0; i < n; i++) {
		limb_t sum = out[i] + a[i] + carry;
		carry = (sum < a[i] || (carry && sum == a[i])) ? 1 : 0;
		out[i] = sum;
	}
	for (int i = n; carry; i++) {
		limb_t sum = out[i] + carry;
		carry = (sum < carry) ? 1 : 0;
		out[i] = sum;
	}
}

static void sub_limbs(limb_t *out, const limb_t *a, int n) {
	limb_t borrow = 0;
	for (int i = 0; i < n; i++) {
		limb_t diff = out[i] - a[i] - borrow;
		borrow = (out[i] < a[i] + borrow) ? 1 : 0;
		out[i] = diff;
	}
	for (int i = n; borrow; i++) {
		limb_t diff = out[i] - borrow;
		borrow = (out[i] < borrow) ? 1 : 0;
		out[i] = diff;
	}
}

// ---- Schoolbook multiplication (base case for Karatsuba) ----
static void schoolbook_mul(limb_t *out, const limb_t *a, int alen, const limb_t *b, int blen) {
	for (int i = 0; i < alen + blen; i++) out[i] = 0;
	for (int i = 0; i < alen; i++) {
		limb_t carry = 0;
		for (int j = 0; j < blen; j++) {
			limb_t value = out[i + j];
			limb_t a_lo = a[i] & 0xFFFFFFFF;
			limb_t a_hi = a[i] >> 32;
			limb_t b_lo = b[j] & 0xFFFFFFFF;
			limb_t b_hi = b[j] >> 32;

			limb_t p00 = a_lo * b_lo;
			limb_t p01 = a_lo * b_hi;
			limb_t p10 = a_hi * b_lo;
			limb_t p11 = a_hi * b_hi;

			limb_t mid = p01 + p10;
			limb_t mid_lo = (mid & 0xFFFFFFFF) << 32;
			limb_t mid_hi = mid >> 32;
			limb_t p00_hi = p00 >> 32;

			limb_t lo = p00 + mid_lo + carry + value;
			carry = p11 + mid_hi + p00_hi;
			if (lo < value || lo < carry) carry++;
			out[i + j] = lo;
		}
		out[i + blen] += carry;
	}
}

// ---- Karatsuba Multiplication (O(n^1.585), no GMP dependency) ----
void karatsuba_mul(limb_t *out, const limb_t *a, int alen, const limb_t *b, int blen) {
	if (alen == 0 || blen == 0) return;
	if (alen == 1 && blen == 1) {
		out[0] = a[0] * b[0];
		out[1] = 0;
		return;
	}
	if (alen + blen <= KARATSUBA_THRESHOLD) {
		schoolbook_mul(out, a, alen, b, blen);
		return;
	}

	int n = (std::max(alen, blen) + 1) / 2;
	int a_lo_len = std::min(n, alen);
	int a_hi_len = std::max(0, alen - n);
	int b_lo_len = std::min(n, blen);
	int b_hi_len = std::max(0, blen - n);

	const limb_t *a_lo = a;
	const limb_t *a_hi = a + n;
	const limb_t *b_lo = b;
	const limb_t *b_hi = b + n;

	int sum_a_len = std::max(a_lo_len, a_hi_len) + 1;
	int sum_b_len = std::max(b_lo_len, b_hi_len) + 1;
	int max_size = 2 * n * 2;
	auto sum_a = limb_alloc(sum_a_len);
	auto sum_b = limb_alloc(sum_b_len);
	auto prod_lo = limb_alloc(max_size);
	auto prod_sum = limb_alloc(max_size);

	for (int i = 0; i < sum_a_len; i++) sum_a[i] = 0;
	if (a_lo_len > 0) memcpy(sum_a, a_lo, a_lo_len * sizeof(limb_t));
	if (a_hi_len > 0) add_limbs(sum_a, a_hi, a_hi_len);

	for (int i = 0; i < sum_b_len; i++) sum_b[i] = 0;
	if (b_lo_len > 0) memcpy(sum_b, b_lo, b_lo_len * sizeof(limb_t));
	if (b_hi_len > 0) add_limbs(sum_b, b_hi, b_hi_len);

	// prod_lo = a_lo * b_lo (in low half)
	karatsuba_mul(prod_lo, a_lo, a_lo_len, b_lo, b_lo_len);

	// prod_hi = a_hi * b_hi (in high half)
	karatsuba_mul(prod_lo + 2 * n, a_hi, a_hi_len, b_hi, b_hi_len);

	// prod_sum = sum_a * sum_b
	int sa_actual = sum_a_len;
	while (sa_actual > 0 && sum_a[sa_actual - 1] == 0) sa_actual--;
	if (sa_actual == 0) sa_actual = 1;
	int sb_actual = sum_b_len;
	while (sb_actual > 0 && sum_b[sb_actual - 1] == 0) sb_actual--;
	if (sb_actual == 0) sb_actual = 1;
	karatsuba_mul(prod_sum, sum_a, sa_actual, sum_b, sb_actual);

	// middle = prod_sum - prod_lo - prod_hi_shifts
	sub_limbs(prod_sum, prod_lo, max_size);
	sub_limbs(prod_sum, prod_lo + 2 * n, max_size - 2 * n);

	// out = prod_lo + middle << n + prod_hi << 2n
	for (int i = 0; i < max_size; i++) out[i] = 0;
	memcpy(out, prod_lo, (a_lo_len + b_lo_len) * sizeof(limb_t));
	add_limbs(out + n, prod_sum, max_size - n);

	free(sum_a);
	free(sum_b);
	free(prod_lo);
	free(prod_sum);
}

#ifndef BIGMATH_NO_GMP

// ---- Binary GCD (Stein's algorithm) ----
void binary_gcd(mpz_t out, const mpz_t a, const mpz_t b) {
	if (mpz_sgn(a) == 0) { mpz_set(out, b); mpz_abs(out, out); return; }
	if (mpz_sgn(b) == 0) { mpz_set(out, a); mpz_abs(out, out); return; }

	mpz_t u, v;
	mpz_init_set(u, a);
	mpz_init_set(v, b);
	mpz_abs(u, u);
	mpz_abs(v, v);

	int shift = 0;
	while (mpz_even_p(u) && mpz_even_p(v)) {
		mpz_tdiv_q_2exp(u, u, 1);
		mpz_tdiv_q_2exp(v, v, 1);
		shift++;
	}
	while (mpz_sgn(u) != 0) {
		while (mpz_even_p(u)) mpz_tdiv_q_2exp(u, u, 1);
		while (mpz_even_p(v)) mpz_tdiv_q_2exp(v, v, 1);
		if (mpz_cmp(u, v) >= 0) {
			mpz_sub(u, u, v);
			mpz_tdiv_q_2exp(u, u, 1);
		} else {
			mpz_sub(v, v, u);
			mpz_tdiv_q_2exp(v, v, 1);
		}
	}
	mpz_mul_2exp(out, v, shift);
	mpz_clear(u);
	mpz_clear(v);
}

// ---- Exponentiation by Squaring ----
void fast_pow(mpz_t out, const mpz_t base, unsigned long exp) {
	mpz_set_ui(out, 1);
	if (exp == 0) return;
	mpz_t b;
	mpz_init_set(b, base);
	unsigned long e = exp;
	while (e > 0) {
		if (e & 1) mpz_mul(out, out, b);
		e >>= 1;
		if (e > 0) mpz_mul(b, b, b);
	}
	mpz_clear(b);
}

// ---- Product Tree Factorial ----
static void product_tree(mpz_t out, unsigned long a, unsigned long b) {
	if (a == b) {
		mpz_set_ui(out, a);
		return;
	}
	if (a + 1 == b) {
		mpz_set_ui(out, a);
		mpz_mul_ui(out, out, b);
		return;
	}
	unsigned long mid = a + (b - a) / 2;
	mpz_t left, right;
	mpz_init(left);
	mpz_init(right);
	product_tree(left, a, mid);
	product_tree(right, mid + 1, b);
	mpz_mul(out, left, right);
	mpz_clear(left);
	mpz_clear(right);
}

void product_tree_factorial(mpz_t out, unsigned long n) {
	if (n <= 1) { mpz_set_ui(out, 1); return; }
	if (n < 128) {
		mpz_fac_ui(out, n);
		return;
	}
	product_tree(out, 2, n);
}

// ---- FFT/NTT-based multiplication ----
void fft_multiply(mpz_t out, const mpz_t a, const mpz_t b) {
	int alen = mpz_size(a);
	int blen = mpz_size(b);
	if (alen == 0 || blen == 0) { mpz_set_ui(out, 0); return; }

	bool a_neg = (mpz_sgn(a) < 0);
	bool b_neg = (mpz_sgn(b) < 0);

	mpz_t abs_a, abs_b;
	mpz_init(abs_a); mpz_abs(abs_a, a);
	mpz_init(abs_b); mpz_abs(abs_b, b);

	static constexpr uint64_t BASE = 1ULL << 16;
	static constexpr uint64_t BASE_MASK = BASE - 1;

	// Convert to base-2^16 digits
	std::vector<uint64_t> ad, bd;
	mpz_t tmp, digit;
	mpz_init(tmp);
	mpz_init(digit);
	mpz_set(tmp, abs_a);
	while (mpz_sgn(tmp) > 0) {
		mpz_tdiv_r_2exp(digit, tmp, 16);
		ad.push_back(mpz_get_ui(digit));
		mpz_tdiv_q_2exp(tmp, tmp, 16);
	}
	if (ad.empty()) ad.push_back(0);

	mpz_set(tmp, abs_b);
	while (mpz_sgn(tmp) > 0) {
		mpz_tdiv_r_2exp(digit, tmp, 16);
		bd.push_back(mpz_get_ui(digit));
		mpz_tdiv_q_2exp(tmp, tmp, 16);
	}
	if (bd.empty()) bd.push_back(0);

	mpz_clear(tmp);
	mpz_clear(digit);
	mpz_clear(abs_a);
	mpz_clear(abs_b);

	// NTT convolution
	auto conv = ntt::convolve(ad, bd, BASE);

	// Carry propagation
	uint64_t carry = 0;
	for (size_t i = 0; i < conv.size(); i++) {
		uint64_t val = conv[i] + carry;
		conv[i] = val & BASE_MASK;
		carry = val >> 16;
	}
	conv.push_back(carry);

	// Build result: most significant digit first
	mpz_set_ui(out, 0);
	for (int i = (int)conv.size() - 1; i >= 0; i--) {
		mpz_mul_2exp(out, out, 16);
		mpz_add_ui(out, out, conv[i]);
	}

	if (a_neg != b_neg) mpz_neg(out, out);
}

#else
// Stubs when GMP not available
void binary_gcd(mpz_t, const mpz_t, const mpz_t) {}
void fast_pow(mpz_t, const mpz_t, unsigned long) {}
void product_tree_factorial(mpz_t, unsigned long) {}
void fft_multiply(mpz_t, const mpz_t, const mpz_t) {}
#endif // BIGMATH_NO_GMP

}
