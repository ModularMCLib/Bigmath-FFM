#include "bigmath_ffm.h"
#include "algos.h"
#include <cstdlib>
#include <cstring>

#ifndef BIGMATH_NO_GMP

void bigint_from_long(mpz_t *out, long val) {
	mpz_init(*out);
	mpz_set_si(*out, val);
}

void bigint_from_string(mpz_t *out, const char *str, int radix) {
	mpz_init(*out);
	mpz_set_str(*out, str, radix);
}

void bigint_add(mpz_t *out, const mpz_t a, const mpz_t b) {
	mpz_init(*out);
	mpz_add(*out, a, b);
}

void bigint_sub(mpz_t *out, const mpz_t a, const mpz_t b) {
	mpz_init(*out);
	mpz_sub(*out, a, b);
}

void bigint_mul(mpz_t *out, const mpz_t a, const mpz_t b) {
	mpz_init(*out);
	// GMP internally uses tiered algorithms (schoolbook -> Karatsuba -> Toom-Cook -> FFT)
	// based on operand size, automatically selecting the optimal algorithm
	mpz_mul(*out, a, b);
}

void bigint_div(mpz_t *out, const mpz_t a, const mpz_t b) {
	mpz_init(*out);
	mpz_tdiv_q(*out, a, b);
}

void bigint_mod(mpz_t *out, const mpz_t a, const mpz_t b) {
	mpz_init(*out);
	mpz_tdiv_r(*out, a, b);
}

void bigint_pow(mpz_t *out, const mpz_t a, unsigned long exp) {
	mpz_init(*out);
	bigmath::fast_pow(*out, a, exp);
}

void bigint_neg(mpz_t *out, const mpz_t a) {
	mpz_init(*out);
	mpz_neg(*out, a);
}

void bigint_abs(mpz_t *out, const mpz_t a) {
	mpz_init(*out);
	mpz_abs(*out, a);
}

int bigint_cmp(const mpz_t a, const mpz_t b) {
	return mpz_cmp(a, b);
}

int bigint_sign(const mpz_t a) {
	return mpz_sgn(a);
}

char *bigint_to_string(const mpz_t a, int radix) {
	return mpz_get_str(nullptr, radix, a);
}

char *bigint_format(const mpz_t a, int group_size, const char *group_sep) {
	char *raw = mpz_get_str(nullptr, 10, a);
	if (group_size <= 0 || group_sep == nullptr || *group_sep == '\0') {
		return raw;
	}
	bool neg = (raw[0] == '-');
	const char *digits = raw + (neg ? 1 : 0);
	size_t len = strlen(digits);
	size_t sep_len = strlen(group_sep);
	size_t groups = (len + group_size - 1) / group_size;
	size_t new_len = (neg ? 1 : 0) + len + (groups - 1) * sep_len;
	char *out = (char *)malloc(new_len + 1);
	if (!out) { free(raw); return nullptr; }
	size_t pos = 0;
	if (neg) out[pos++] = '-';
	size_t first_group = len % group_size;
	if (first_group == 0) first_group = group_size;
	memcpy(out + pos, digits, first_group);
	pos += first_group;
	for (size_t i = first_group; i < len; i += group_size) {
		memcpy(out + pos, group_sep, sep_len);
		pos += sep_len;
		memcpy(out + pos, digits + i, group_size);
		pos += group_size;
	}
	out[pos] = '\0';
	free(raw);
	return out;
}

void bigint_free_string(char *s) {
	free(s);
}

void bigint_free(mpz_t a) {
	mpz_clear(a);
}

void bigint_gcd(mpz_t *out, const mpz_t a, const mpz_t b) {
	mpz_init(*out);
	int abits = bigmath::limb_bits(a);
	int bbits = bigmath::limb_bits(b);
	if (abits + bbits <= bigmath::ALGO_THRESHOLD) {
		bigmath::binary_gcd(*out, a, b);
	} else {
		mpz_gcd(*out, a, b);
	}
}

void bigint_lcm(mpz_t *out, const mpz_t a, const mpz_t b) {
	mpz_init(*out);
	mpz_lcm(*out, a, b);
}

void bigint_sqrt(mpz_t *out, const mpz_t a) {
	mpz_init(*out);
	mpz_sqrt(*out, a);
}

void bigint_and(mpz_t *out, const mpz_t a, const mpz_t b) {
	mpz_init(*out);
	mpz_and(*out, a, b);
}

void bigint_or(mpz_t *out, const mpz_t a, const mpz_t b) {
	mpz_init(*out);
	mpz_ior(*out, a, b);
}

void bigint_xor(mpz_t *out, const mpz_t a, const mpz_t b) {
	mpz_init(*out);
	mpz_xor(*out, a, b);
}

void bigint_shl(mpz_t *out, const mpz_t a, unsigned long bits) {
	mpz_init(*out);
	mpz_mul_2exp(*out, a, bits);
}

void bigint_shr(mpz_t *out, const mpz_t a, unsigned long bits) {
	mpz_init(*out);
	mpz_tdiv_q_2exp(*out, a, bits);
}

int bigint_is_probably_prime(const mpz_t a, int reps) {
	return mpz_probab_prime_p(a, reps);
}

void bigint_factorial(mpz_t *out, unsigned long n) {
	mpz_init(*out);
	bigmath::product_tree_factorial(*out, n);
}

void bigint_next_prime(mpz_t *out, const mpz_t a) {
	mpz_init(*out);
	mpz_nextprime(*out, a);
}

#else

void bigint_from_long(mpz_t *out, long) { }
void bigint_from_string(mpz_t *out, const char *, int) { }
void bigint_add(mpz_t *out, const mpz_t, const mpz_t) { }
void bigint_sub(mpz_t *out, const mpz_t, const mpz_t) { }
void bigint_mul(mpz_t *out, const mpz_t, const mpz_t) { }
void bigint_div(mpz_t *out, const mpz_t, const mpz_t) { }
void bigint_mod(mpz_t *out, const mpz_t, const mpz_t) { }
void bigint_pow(mpz_t *out, const mpz_t, unsigned long) { }
void bigint_neg(mpz_t *out, const mpz_t) { }
void bigint_abs(mpz_t *out, const mpz_t) { }
int  bigint_cmp(const mpz_t, const mpz_t) { return 0; }
int  bigint_sign(const mpz_t) { return 0; }
char *bigint_to_string(const mpz_t, int) { return nullptr; }
char *bigint_format(const mpz_t, int, const char *) { return nullptr; }
void bigint_free_string(char *) { }
void bigint_free(mpz_t) { }
void bigint_gcd(mpz_t *out, const mpz_t, const mpz_t) { }
void bigint_lcm(mpz_t *out, const mpz_t, const mpz_t) { }
void bigint_sqrt(mpz_t *out, const mpz_t) { }
void bigint_and(mpz_t *out, const mpz_t, const mpz_t) { }
void bigint_or(mpz_t *out, const mpz_t, const mpz_t) { }
void bigint_xor(mpz_t *out, const mpz_t, const mpz_t) { }
void bigint_shl(mpz_t *out, const mpz_t, unsigned long) { }
void bigint_shr(mpz_t *out, const mpz_t, unsigned long) { }
int  bigint_is_probably_prime(const mpz_t, int) { return 0; }
void bigint_factorial(mpz_t *out, unsigned long) { }
void bigint_next_prime(mpz_t *out, const mpz_t) { }

#endif /* BIGMATH_NO_GMP */
