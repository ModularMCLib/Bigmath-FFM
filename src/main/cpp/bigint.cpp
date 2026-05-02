#include "bigmath_ffm.h"
#include "algos.h"
#include <cstdlib>
#include <cstring>

#ifndef BIGMATH_NO_GMP

void bigint_from_long(mpz_ptr *out, int64_t val) {
	*out = (mpz_ptr)malloc(sizeof(__mpz_struct));
	if (!*out) return;
	mpz_init(*out);
	uint64_t magnitude = val >= 0
		? static_cast<uint64_t>(val)
		: static_cast<uint64_t>(-(val + 1)) + 1;
	mpz_import(*out, 1, -1, sizeof(magnitude), 0, 0, &magnitude);
	if (val < 0) {
		mpz_neg(*out, *out);
	}
}

void bigint_from_string(mpz_ptr *out, const char *str, int radix) {
	*out = (mpz_ptr)malloc(sizeof(__mpz_struct));
	if (!*out) return;
	mpz_init(*out);
	mpz_set_str(*out, str, radix);
}

void bigint_add(mpz_ptr *out, mpz_ptr a, mpz_ptr b) {
	*out = (mpz_ptr)malloc(sizeof(__mpz_struct));
	if (!*out) return;
	mpz_init(*out);
	mpz_add(*out, a, b);
}

void bigint_sub(mpz_ptr *out, mpz_ptr a, mpz_ptr b) {
	*out = (mpz_ptr)malloc(sizeof(__mpz_struct));
	if (!*out) return;
	mpz_init(*out);
	mpz_sub(*out, a, b);
}

void bigint_mul(mpz_ptr *out, mpz_ptr a, mpz_ptr b) {
	*out = (mpz_ptr)malloc(sizeof(__mpz_struct));
	if (!*out) return;
	mpz_init(*out);
	int alen = mpz_size(a);
	int blen = mpz_size(b);
	if (alen + blen >= bigmath::NTT_THRESHOLD) {
		bigmath::fft_multiply(*out, a, b);
	} else {
		mpz_mul(*out, a, b);
	}
}

void bigint_div(mpz_ptr *out, mpz_ptr a, mpz_ptr b) {
	*out = (mpz_ptr)malloc(sizeof(__mpz_struct));
	if (!*out) return;
	mpz_init(*out);
	mpz_tdiv_q(*out, a, b);
}

void bigint_mod(mpz_ptr *out, mpz_ptr a, mpz_ptr b) {
	*out = (mpz_ptr)malloc(sizeof(__mpz_struct));
	if (!*out) return;
	mpz_init(*out);
	mpz_tdiv_r(*out, a, b);
}

void bigint_pow(mpz_ptr *out, mpz_ptr a, uint64_t exp) {
	*out = (mpz_ptr)malloc(sizeof(__mpz_struct));
	if (!*out) return;
	mpz_init(*out);
	bigmath::fast_pow(*out, a, exp);
}

void bigint_neg(mpz_ptr *out, mpz_ptr a) {
	*out = (mpz_ptr)malloc(sizeof(__mpz_struct));
	if (!*out) return;
	mpz_init(*out);
	mpz_neg(*out, a);
}

void bigint_abs(mpz_ptr *out, mpz_ptr a) {
	*out = (mpz_ptr)malloc(sizeof(__mpz_struct));
	if (!*out) return;
	mpz_init(*out);
	mpz_abs(*out, a);
}

int bigint_cmp(mpz_ptr a, mpz_ptr b) {
	return mpz_cmp(a, b);
}

int bigint_sign(mpz_ptr a) {
	return mpz_sgn(a);
}

int64_t bigint_to_long(mpz_ptr a) {
	if (mpz_sgn(a) == 0) {
		return 0;
	}
	mpz_t abs;
	mpz_init(abs);
	mpz_abs(abs, a);
	uint64_t magnitude = 0;
	mpz_export(&magnitude, nullptr, -1, sizeof(magnitude), 0, 0, abs);
	mpz_clear(abs);
	if (mpz_sgn(a) > 0) {
		return static_cast<int64_t>(magnitude);
	}
	if (magnitude == (uint64_t{1} << 63)) {
		return INT64_MIN;
	}
	return -static_cast<int64_t>(magnitude);
}

double bigint_to_double(mpz_ptr a) {
	return mpz_get_d(a);
}

char *bigint_to_string(mpz_ptr a, int radix) {
	return mpz_get_str(nullptr, radix, a);
}

char *bigint_format(mpz_ptr a, int group_size, const char *group_sep) {
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

void bigint_free(mpz_ptr a) {
	if (a) {
		mpz_clear(a);
		free(a);
	}
}

void bigint_gcd(mpz_ptr *out, mpz_ptr a, mpz_ptr b) {
	*out = (mpz_ptr)malloc(sizeof(__mpz_struct));
	if (!*out) return;
	mpz_init(*out);
	int abits = mpz_sizeinbase(a, 2);
	int bbits = mpz_sizeinbase(b, 2);
	if (abits + bbits <= bigmath::ALGO_THRESHOLD) {
		bigmath::binary_gcd(*out, a, b);
	} else {
		mpz_gcd(*out, a, b);
	}
}

void bigint_lcm(mpz_ptr *out, mpz_ptr a, mpz_ptr b) {
	*out = (mpz_ptr)malloc(sizeof(__mpz_struct));
	if (!*out) return;
	mpz_init(*out);
	mpz_lcm(*out, a, b);
}

void bigint_sqrt(mpz_ptr *out, mpz_ptr a) {
	*out = (mpz_ptr)malloc(sizeof(__mpz_struct));
	if (!*out) return;
	mpz_init(*out);
	mpz_sqrt(*out, a);
}

void bigint_and(mpz_ptr *out, mpz_ptr a, mpz_ptr b) {
	*out = (mpz_ptr)malloc(sizeof(__mpz_struct));
	if (!*out) return;
	mpz_init(*out);
	mpz_and(*out, a, b);
}

void bigint_or(mpz_ptr *out, mpz_ptr a, mpz_ptr b) {
	*out = (mpz_ptr)malloc(sizeof(__mpz_struct));
	if (!*out) return;
	mpz_init(*out);
	mpz_ior(*out, a, b);
}

void bigint_xor(mpz_ptr *out, mpz_ptr a, mpz_ptr b) {
	*out = (mpz_ptr)malloc(sizeof(__mpz_struct));
	if (!*out) return;
	mpz_init(*out);
	mpz_xor(*out, a, b);
}

void bigint_shl(mpz_ptr *out, mpz_ptr a, uint64_t bits) {
	*out = (mpz_ptr)malloc(sizeof(__mpz_struct));
	if (!*out) return;
	mpz_init(*out);
	mpz_mul_2exp(*out, a, static_cast<mp_bitcnt_t>(bits));
}

void bigint_shr(mpz_ptr *out, mpz_ptr a, uint64_t bits) {
	*out = (mpz_ptr)malloc(sizeof(__mpz_struct));
	if (!*out) return;
	mpz_init(*out);
	mpz_tdiv_q_2exp(*out, a, static_cast<mp_bitcnt_t>(bits));
}

int bigint_is_probably_prime(mpz_ptr a, int reps) {
	return mpz_probab_prime_p(a, reps);
}

void bigint_factorial(mpz_ptr *out, uint64_t n) {
	*out = (mpz_ptr)malloc(sizeof(__mpz_struct));
	if (!*out) return;
	mpz_init(*out);
	bigmath::product_tree_factorial(*out, n);
}

void bigint_next_prime(mpz_ptr *out, mpz_ptr a) {
	*out = (mpz_ptr)malloc(sizeof(__mpz_struct));
	if (!*out) return;
	mpz_init(*out);
	mpz_nextprime(*out, a);
}

#else

void bigint_from_long(mpz_ptr *out, int64_t) { *out = nullptr; }
void bigint_from_string(mpz_ptr *out, const char *, int) { *out = nullptr; }
void bigint_add(mpz_ptr *out, mpz_ptr, mpz_ptr) { *out = nullptr; }
void bigint_sub(mpz_ptr *out, mpz_ptr, mpz_ptr) { *out = nullptr; }
void bigint_mul(mpz_ptr *out, mpz_ptr, mpz_ptr) { *out = nullptr; }
void bigint_div(mpz_ptr *out, mpz_ptr, mpz_ptr) { *out = nullptr; }
void bigint_mod(mpz_ptr *out, mpz_ptr, mpz_ptr) { *out = nullptr; }
void bigint_pow(mpz_ptr *out, mpz_ptr, uint64_t) { *out = nullptr; }
void bigint_neg(mpz_ptr *out, mpz_ptr) { *out = nullptr; }
void bigint_abs(mpz_ptr *out, mpz_ptr) { *out = nullptr; }
int  bigint_cmp(mpz_ptr, mpz_ptr) { return 0; }
int  bigint_sign(mpz_ptr) { return 0; }
int64_t bigint_to_long(mpz_ptr) { return 0; }
double bigint_to_double(mpz_ptr) { return 0.0; }

void bigint_free_string(char *) { }
void bigint_free(mpz_ptr) { }
void bigint_gcd(mpz_ptr *out, mpz_ptr, mpz_ptr) { *out = nullptr; }
void bigint_lcm(mpz_ptr *out, mpz_ptr, mpz_ptr) { *out = nullptr; }
void bigint_sqrt(mpz_ptr *out, mpz_ptr) { *out = nullptr; }
void bigint_and(mpz_ptr *out, mpz_ptr, mpz_ptr) { *out = nullptr; }
void bigint_or(mpz_ptr *out, mpz_ptr, mpz_ptr) { *out = nullptr; }
void bigint_xor(mpz_ptr *out, mpz_ptr, mpz_ptr) { *out = nullptr; }
void bigint_shl(mpz_ptr *out, mpz_ptr, uint64_t) { *out = nullptr; }
void bigint_shr(mpz_ptr *out, mpz_ptr, uint64_t) { *out = nullptr; }
int  bigint_is_probably_prime(mpz_ptr, int) { return 0; }
void bigint_factorial(mpz_ptr *out, uint64_t) { *out = nullptr; }
void bigint_next_prime(mpz_ptr *out, mpz_ptr) { *out = nullptr; }

#endif /* BIGMATH_NO_GMP */
