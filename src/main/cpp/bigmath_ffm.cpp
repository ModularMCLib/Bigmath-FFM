#include "bigmath_ffm.h"
#include <cstdlib>
#include <cstring>

/* BigInt */
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
	mpz_pow_ui(*out, a, exp);
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

/* BigDecimal */
void bigdecimal_from_double(mpfr_t *out, double val, int precision) {
	mpfr_init2(*out, precision);
	mpfr_set_d(*out, val, MPFR_RNDN);
}

void bigdecimal_from_string(mpfr_t *out, const char *str, int precision) {
	mpfr_init2(*out, precision);
	mpfr_set_str(*out, str, 10, MPFR_RNDN);
}

void bigdecimal_add(mpfr_t *out, const mpfr_t a, const mpfr_t b) {
	mpfr_init2(*out, mpfr_get_prec(a));
	mpfr_add(*out, a, b, MPFR_RNDN);
}

void bigdecimal_sub(mpfr_t *out, const mpfr_t a, const mpfr_t b) {
	mpfr_init2(*out, mpfr_get_prec(a));
	mpfr_sub(*out, a, b, MPFR_RNDN);
}

void bigdecimal_mul(mpfr_t *out, const mpfr_t a, const mpfr_t b) {
	mpfr_init2(*out, mpfr_get_prec(a));
	mpfr_mul(*out, a, b, MPFR_RNDN);
}

void bigdecimal_div(mpfr_t *out, const mpfr_t a, const mpfr_t b) {
	mpfr_init2(*out, mpfr_get_prec(a));
	mpfr_div(*out, a, b, MPFR_RNDN);
}

void bigdecimal_neg(mpfr_t *out, const mpfr_t a) {
	mpfr_init2(*out, mpfr_get_prec(a));
	mpfr_neg(*out, a, MPFR_RNDN);
}

int bigdecimal_cmp(const mpfr_t a, const mpfr_t b) {
	return mpfr_cmp(a, b);
}

double bigdecimal_to_double(const mpfr_t a) {
	return mpfr_get_d(a, MPFR_RNDN);
}

char *bigdecimal_to_string(const mpfr_t a) {
	mpfr_exp_t exp;
	char *str = mpfr_get_str(nullptr, &exp, 10, 0, a, MPFR_RNDN);
	return str;
}

char *bigdecimal_format(const mpfr_t a, int scale, int group_size, const char *group_sep) {
	mpfr_exp_t exp;
	char *raw = mpfr_get_str(nullptr, &exp, 10, 0, a, MPFR_RNDN);
	if (!raw) return nullptr;
	bool neg = (raw[0] == '-');
	char *digits = raw + (neg ? 1 : 0);
	size_t digit_len = strlen(digits);

	// determine integer and fractional parts
	int int_digits = (int)exp;
	if (int_digits < 0) int_digits = 0;
	size_t frac_len = (scale >= 0) ? (size_t)scale : 0;
	if (frac_len > digit_len - int_digits) {
		frac_len = digit_len - int_digits;
	}

	// integer part
	char *int_part = (char *)malloc(int_digits + 1);
	if (!int_part) { free(raw); return nullptr; }
	if (int_digits > 0) {
		memcpy(int_part, digits, int_digits);
		int_part[int_digits] = '\0';
	} else {
		int_part[0] = '0';
		int_part[1] = '\0';
		int_digits = 1;
	}

	// format integer part with grouping
	size_t sep_len = (group_sep && group_size > 0) ? strlen(group_sep) : 0;
	size_t ig_len = strlen(int_part);
	size_t groups = (ig_len + group_size - 1) / group_size;
	char *int_fmt;
	if (sep_len > 0 && group_size > 0) {
		size_t fmt_len = ig_len + (groups - 1) * sep_len;
		int_fmt = (char *)malloc(fmt_len + 1);
		if (!int_fmt) { free(int_part); free(raw); return nullptr; }
		size_t first_group = ig_len % group_size;
		if (first_group == 0) first_group = group_size;
		size_t pos = 0;
		memcpy(int_fmt, int_part, first_group);
		pos += first_group;
		for (size_t i = first_group; i < ig_len; i += group_size) {
			memcpy(int_fmt + pos, group_sep, sep_len);
			pos += sep_len;
			memcpy(int_fmt + pos, int_part + i, group_size);
			pos += group_size;
		}
		int_fmt[pos] = '\0';
	} else {
		int_fmt = strdup(int_part);
	}
	free(int_part);

	// fractional part
	size_t frac_copy = frac_len;
	if (frac_copy > digit_len - int_digits) {
		frac_copy = digit_len - int_digits;
	}
	char *frac_str = (char *)malloc(frac_copy + 1);
	if (!frac_str) { free(int_fmt); free(raw); return nullptr; }
	if (frac_copy > 0) {
		memcpy(frac_str, digits + int_digits, frac_copy);
	}
	frac_str[frac_copy] = '\0';
	// trim trailing zeros if scale < 0
	if (scale < 0) {
		while (frac_copy > 0 && frac_str[frac_copy - 1] == '0') frac_copy--;
		frac_str[frac_copy] = '\0';
	} else {
		// pad with zeros
		while (frac_copy < (size_t)scale) {
			frac_str[frac_copy++] = '0';
			frac_str[frac_copy] = '\0';
		}
	}

	// build final string
	size_t sign_len = (neg ? 1 : 0);
	size_t int_fmt_len = strlen(int_fmt);
	size_t total = sign_len + int_fmt_len + (frac_copy > 0 ? 1 + frac_copy : 0);
	char *out = (char *)malloc(total + 1);
	if (!out) { free(int_fmt); free(frac_str); free(raw); return nullptr; }
	size_t pos = 0;
	if (neg) out[pos++] = '-';
	memcpy(out + pos, int_fmt, int_fmt_len);
	pos += int_fmt_len;
	if (frac_copy > 0) {
		out[pos++] = '.';
		memcpy(out + pos, frac_str, frac_copy);
		pos += frac_copy;
	}
	out[pos] = '\0';

	free(int_fmt);
	free(frac_str);
	free(raw);
	return out;
}

void bigdecimal_free_string(char *s) {
	free(s);
}

void bigdecimal_free(mpfr_t a) {
	mpfr_clear(a);
}
