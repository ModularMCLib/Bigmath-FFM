#include "bigmath_ffm.h"
#include <cstdlib>
#include <cstring>
#include <limits>

#ifndef BIGMATH_NO_GMP

void bigdecimal_from_double(mpfr_ptr *out, double val, int precision) {
	*out = (mpfr_ptr)malloc(sizeof(__mpfr_struct));
	if (!*out) return;
	mpfr_init2(*out, precision);
	mpfr_set_d(*out, val, MPFR_RNDN);
}

void bigdecimal_from_string(mpfr_ptr *out, const char *str, int precision) {
	*out = (mpfr_ptr)malloc(sizeof(__mpfr_struct));
	if (!*out) return;
	mpfr_init2(*out, precision);
	mpfr_set_str(*out, str, 10, MPFR_RNDN);
}

void bigdecimal_init(mpfr_ptr *out, int precision) {
	*out = (mpfr_ptr)malloc(sizeof(__mpfr_struct));
	if (!*out) return;
	mpfr_init2(*out, precision);
}

void bigdecimal_clear(mpfr_ptr a) {
	bigdecimal_free(a);
}

void bigdecimal_set(mpfr_ptr out, mpfr_ptr a) {
	mpfr_set_prec(out, mpfr_get_prec(a));
	mpfr_set(out, a, MPFR_RNDN);
}

void bigdecimal_set_double(mpfr_ptr out, double val) {
	mpfr_set_d(out, val, MPFR_RNDN);
}

void bigdecimal_set_string(mpfr_ptr out, const char *str, int precision) {
	mpfr_set_prec(out, precision);
	mpfr_set_str(out, str, 10, MPFR_RNDN);
}

void bigdecimal_add(mpfr_ptr *out, mpfr_ptr a, mpfr_ptr b) {
	*out = (mpfr_ptr)malloc(sizeof(__mpfr_struct));
	if (!*out) return;
	mpfr_init2(*out, mpfr_get_prec(a));
	mpfr_add(*out, a, b, MPFR_RNDN);
}

void bigdecimal_sub(mpfr_ptr *out, mpfr_ptr a, mpfr_ptr b) {
	*out = (mpfr_ptr)malloc(sizeof(__mpfr_struct));
	if (!*out) return;
	mpfr_init2(*out, mpfr_get_prec(a));
	mpfr_sub(*out, a, b, MPFR_RNDN);
}

void bigdecimal_mul(mpfr_ptr *out, mpfr_ptr a, mpfr_ptr b) {
	*out = (mpfr_ptr)malloc(sizeof(__mpfr_struct));
	if (!*out) return;
	mpfr_init2(*out, mpfr_get_prec(a));
	mpfr_mul(*out, a, b, MPFR_RNDN);
}

void bigdecimal_div(mpfr_ptr *out, mpfr_ptr a, mpfr_ptr b) {
	*out = (mpfr_ptr)malloc(sizeof(__mpfr_struct));
	if (!*out) return;
	mpfr_init2(*out, mpfr_get_prec(a));
	mpfr_div(*out, a, b, MPFR_RNDN);
}

void bigdecimal_add_into(mpfr_ptr out, mpfr_ptr a, mpfr_ptr b) {
	if (mpfr_get_prec(out) != mpfr_get_prec(a)) {
		mpfr_set_prec(out, mpfr_get_prec(a));
	}
	mpfr_add(out, a, b, MPFR_RNDN);
}

void bigdecimal_mul_into(mpfr_ptr out, mpfr_ptr a, mpfr_ptr b) {
	if (mpfr_get_prec(out) != mpfr_get_prec(a)) {
		mpfr_set_prec(out, mpfr_get_prec(a));
	}
	mpfr_mul(out, a, b, MPFR_RNDN);
}

void bigdecimal_div_into(mpfr_ptr out, mpfr_ptr a, mpfr_ptr b) {
	if (mpfr_get_prec(out) != mpfr_get_prec(a)) {
		mpfr_set_prec(out, mpfr_get_prec(a));
	}
	mpfr_div(out, a, b, MPFR_RNDN);
}

void bigdecimal_sqrt_into(mpfr_ptr out, mpfr_ptr a) {
	if (mpfr_get_prec(out) != mpfr_get_prec(a)) {
		mpfr_set_prec(out, mpfr_get_prec(a));
	}
	mpfr_sqrt(out, a, MPFR_RNDN);
}

void bigdecimal_neg(mpfr_ptr *out, mpfr_ptr a) {
	*out = (mpfr_ptr)malloc(sizeof(__mpfr_struct));
	if (!*out) return;
	mpfr_init2(*out, mpfr_get_prec(a));
	mpfr_neg(*out, a, MPFR_RNDN);
}

void bigdecimal_abs(mpfr_ptr *out, mpfr_ptr a) {
	*out = (mpfr_ptr)malloc(sizeof(__mpfr_struct));
	if (!*out) return;
	mpfr_init2(*out, mpfr_get_prec(a));
	mpfr_abs(*out, a, MPFR_RNDN);
}

int bigdecimal_cmp(mpfr_ptr a, mpfr_ptr b) {
	return mpfr_cmp(a, b);
}

double bigdecimal_to_double(mpfr_ptr a) {
	return mpfr_get_d(a, MPFR_RNDN);
}

char *bigdecimal_to_string(mpfr_ptr a) {
	mpfr_exp_t exp;
	char *str = mpfr_get_str(nullptr, &exp, 10, 0, a, MPFR_RNDN);
	if (!str) return nullptr;
	size_t len = strlen(str);
	char *out = (char *)malloc(len + 1);
	if (!out) {
		mpfr_free_str(str);
		return nullptr;
	}
	memcpy(out, str, len + 1);
	mpfr_free_str(str);
	return out;
}

char *bigdecimal_format(mpfr_ptr a, int scale, int group_size, const char *group_sep) {
	int max_sig = (int)(mpfr_get_prec(a) * 0.30103);
	if (max_sig < 8) max_sig = 8;
	if (max_sig > 100) max_sig = 100;

	mpfr_exp_t exp;
	char *digits = mpfr_get_str(nullptr, &exp, 10, (size_t)max_sig, a, MPFR_RNDN);
	if (!digits) return nullptr;

	bool neg = (digits[0] == '-');
	char *p = digits + (neg ? 1 : 0);
	size_t digit_len = strlen(p);

	auto frac_digit = [&](size_t index) -> char {
		if (exp > 0) {
			size_t source = (size_t)exp + index;
			return source < digit_len ? p[source] : '0';
		}
		size_t leading_zeros = (size_t)(-exp);
		if (index < leading_zeros) {
			return '0';
		}
		size_t source = index - leading_zeros;
		return source < digit_len ? p[source] : '0';
	};

	size_t int_len = exp > 0 ? (size_t)exp : 1;
	size_t frac_len;
	if (scale >= 0) {
		frac_len = (size_t)scale;
	} else if (exp > 0) {
		frac_len = (size_t)exp < digit_len ? digit_len - (size_t)exp : 0;
	} else {
		frac_len = (size_t)(-exp) + digit_len;
	}
	if (scale < 0) {
		while (frac_len > 0 && frac_digit(frac_len - 1) == '0') {
			frac_len--;
		}
	}

	size_t sep_len = (group_sep && group_size > 0) ? strlen(group_sep) : 0;
	size_t sep_count = sep_len > 0 && int_len > 0 ? (int_len - 1) / (size_t)group_size : 0;
	size_t int_fmt_len = int_len + sep_count * sep_len;
	size_t total = (neg ? 1 : 0) + int_fmt_len + (frac_len > 0 ? 1 + frac_len : 0);
	char *out = (char *)malloc(total + 1);
	if (!out) { mpfr_free_str(digits); return nullptr; }
	size_t pos = 0;
	if (neg) out[pos++] = '-';

	size_t first_group = sep_len > 0 ? int_len % (size_t)group_size : int_len;
	if (first_group == 0) first_group = (size_t)group_size;
	for (size_t i = 0; i < int_len; i++) {
		if (sep_len > 0 && i > 0 && (i == first_group || (i > first_group && (i - first_group) % (size_t)group_size == 0))) {
			memcpy(out + pos, group_sep, sep_len);
			pos += sep_len;
		}
		out[pos++] = (exp > 0 && i < digit_len) ? p[i] : '0';
	}

	if (frac_len > 0) {
		out[pos++] = '.';
		for (size_t i = 0; i < frac_len; i++) {
			out[pos++] = frac_digit(i);
		}
	}
	out[pos] = '\0';

	mpfr_free_str(digits);
	return out;
}

void bigdecimal_free_string(char *s) {
	free(s);
}

void bigdecimal_free(mpfr_ptr a) {
	if (a) {
		mpfr_clear(a);
		free(a);
	}
}

void bigdecimal_sqrt(mpfr_ptr *out, mpfr_ptr a) {
	*out = (mpfr_ptr)malloc(sizeof(__mpfr_struct));
	if (!*out) return;
	mpfr_init2(*out, mpfr_get_prec(a));
	mpfr_sqrt(*out, a, MPFR_RNDN);
}

void bigdecimal_pow(mpfr_ptr *out, mpfr_ptr a, mpfr_ptr b) {
	*out = (mpfr_ptr)malloc(sizeof(__mpfr_struct));
	if (!*out) return;
	mpfr_init2(*out, mpfr_get_prec(a));
	mpfr_pow(*out, a, b, MPFR_RNDN);
}

void bigdecimal_pow_long(mpfr_ptr *out, mpfr_ptr a, int64_t exp) {
	*out = (mpfr_ptr)malloc(sizeof(__mpfr_struct));
	if (!*out) return;
	mpfr_init2(*out, mpfr_get_prec(a));
	if (exp >= static_cast<int64_t>(std::numeric_limits<long>::min())
			&& exp <= static_cast<int64_t>(std::numeric_limits<long>::max())) {
		mpfr_pow_si(*out, a, static_cast<long>(exp), MPFR_RNDN);
		return;
	}
	mpfr_t exponent;
	mpfr_init2(exponent, 64);
	mpfr_set_sj(exponent, exp, MPFR_RNDN);
	mpfr_pow(*out, a, exponent, MPFR_RNDN);
	mpfr_clear(exponent);
}

void bigdecimal_log(mpfr_ptr *out, mpfr_ptr a) {
	*out = (mpfr_ptr)malloc(sizeof(__mpfr_struct));
	if (!*out) return;
	mpfr_init2(*out, mpfr_get_prec(a));
	mpfr_log(*out, a, MPFR_RNDN);
}

void bigdecimal_exp(mpfr_ptr *out, mpfr_ptr a) {
	*out = (mpfr_ptr)malloc(sizeof(__mpfr_struct));
	if (!*out) return;
	mpfr_init2(*out, mpfr_get_prec(a));
	mpfr_exp(*out, a, MPFR_RNDN);
}

void bigdecimal_sin(mpfr_ptr *out, mpfr_ptr a) {
	*out = (mpfr_ptr)malloc(sizeof(__mpfr_struct));
	if (!*out) return;
	mpfr_init2(*out, mpfr_get_prec(a));
	mpfr_sin(*out, a, MPFR_RNDN);
}

void bigdecimal_cos(mpfr_ptr *out, mpfr_ptr a) {
	*out = (mpfr_ptr)malloc(sizeof(__mpfr_struct));
	if (!*out) return;
	mpfr_init2(*out, mpfr_get_prec(a));
	mpfr_cos(*out, a, MPFR_RNDN);
}

void bigdecimal_tan(mpfr_ptr *out, mpfr_ptr a) {
	*out = (mpfr_ptr)malloc(sizeof(__mpfr_struct));
	if (!*out) return;
	mpfr_init2(*out, mpfr_get_prec(a));
	mpfr_tan(*out, a, MPFR_RNDN);
}

void bigdecimal_ceil(mpfr_ptr *out, mpfr_ptr a) {
	*out = (mpfr_ptr)malloc(sizeof(__mpfr_struct));
	if (!*out) return;
	mpfr_init2(*out, mpfr_get_prec(a));
	mpfr_ceil(*out, a);
}

void bigdecimal_floor(mpfr_ptr *out, mpfr_ptr a) {
	*out = (mpfr_ptr)malloc(sizeof(__mpfr_struct));
	if (!*out) return;
	mpfr_init2(*out, mpfr_get_prec(a));
	mpfr_floor(*out, a);
}

void bigdecimal_round(mpfr_ptr *out, mpfr_ptr a) {
	*out = (mpfr_ptr)malloc(sizeof(__mpfr_struct));
	if (!*out) return;
	mpfr_init2(*out, mpfr_get_prec(a));
	mpfr_round(*out, a);
}

void bigdecimal_atan(mpfr_ptr *out, mpfr_ptr a) {
	*out = (mpfr_ptr)malloc(sizeof(__mpfr_struct));
	if (!*out) return;
	mpfr_init2(*out, mpfr_get_prec(a));
	mpfr_atan(*out, a, MPFR_RNDN);
}

void bigdecimal_asin(mpfr_ptr *out, mpfr_ptr a) {
	*out = (mpfr_ptr)malloc(sizeof(__mpfr_struct));
	if (!*out) return;
	mpfr_init2(*out, mpfr_get_prec(a));
	mpfr_asin(*out, a, MPFR_RNDN);
}

void bigdecimal_acos(mpfr_ptr *out, mpfr_ptr a) {
	*out = (mpfr_ptr)malloc(sizeof(__mpfr_struct));
	if (!*out) return;
	mpfr_init2(*out, mpfr_get_prec(a));
	mpfr_acos(*out, a, MPFR_RNDN);
}

void bigdecimal_sinh(mpfr_ptr *out, mpfr_ptr a) {
	*out = (mpfr_ptr)malloc(sizeof(__mpfr_struct));
	if (!*out) return;
	mpfr_init2(*out, mpfr_get_prec(a));
	mpfr_sinh(*out, a, MPFR_RNDN);
}

void bigdecimal_cosh(mpfr_ptr *out, mpfr_ptr a) {
	*out = (mpfr_ptr)malloc(sizeof(__mpfr_struct));
	if (!*out) return;
	mpfr_init2(*out, mpfr_get_prec(a));
	mpfr_cosh(*out, a, MPFR_RNDN);
}

void bigdecimal_tanh(mpfr_ptr *out, mpfr_ptr a) {
	*out = (mpfr_ptr)malloc(sizeof(__mpfr_struct));
	if (!*out) return;
	mpfr_init2(*out, mpfr_get_prec(a));
	mpfr_tanh(*out, a, MPFR_RNDN);
}

#else

void bigdecimal_from_double(mpfr_ptr *out, double, int) { *out = nullptr; }
void bigdecimal_from_string(mpfr_ptr *out, const char *, int) { *out = nullptr; }
void bigdecimal_init(mpfr_ptr *out, int) { *out = nullptr; }
void bigdecimal_clear(mpfr_ptr) { }
void bigdecimal_set(mpfr_ptr, mpfr_ptr) { }
void bigdecimal_set_double(mpfr_ptr, double) { }
void bigdecimal_set_string(mpfr_ptr, const char *, int) { }
void bigdecimal_add(mpfr_ptr *out, mpfr_ptr, mpfr_ptr) { *out = nullptr; }
void bigdecimal_sub(mpfr_ptr *out, mpfr_ptr, mpfr_ptr) { *out = nullptr; }
void bigdecimal_mul(mpfr_ptr *out, mpfr_ptr, mpfr_ptr) { *out = nullptr; }
void bigdecimal_div(mpfr_ptr *out, mpfr_ptr, mpfr_ptr) { *out = nullptr; }
void bigdecimal_add_into(mpfr_ptr, mpfr_ptr, mpfr_ptr) { }
void bigdecimal_mul_into(mpfr_ptr, mpfr_ptr, mpfr_ptr) { }
void bigdecimal_div_into(mpfr_ptr, mpfr_ptr, mpfr_ptr) { }
void bigdecimal_sqrt_into(mpfr_ptr, mpfr_ptr) { }
void bigdecimal_neg(mpfr_ptr *out, mpfr_ptr) { *out = nullptr; }
void bigdecimal_abs(mpfr_ptr *out, mpfr_ptr) { *out = nullptr; }
int  bigdecimal_cmp(mpfr_ptr, mpfr_ptr) { return 0; }
double bigdecimal_to_double(mpfr_ptr) { return 0.0; }
char *bigdecimal_to_string(mpfr_ptr) { return nullptr; }
char *bigdecimal_format(mpfr_ptr, int, int, const char *) { return nullptr; }
void bigdecimal_free_string(char *) { }
void bigdecimal_free(mpfr_ptr) { }
void bigdecimal_sqrt(mpfr_ptr *out, mpfr_ptr) { *out = nullptr; }
void bigdecimal_pow(mpfr_ptr *out, mpfr_ptr, mpfr_ptr) { *out = nullptr; }
void bigdecimal_pow_long(mpfr_ptr *out, mpfr_ptr, int64_t) { *out = nullptr; }
void bigdecimal_log(mpfr_ptr *out, mpfr_ptr) { *out = nullptr; }
void bigdecimal_exp(mpfr_ptr *out, mpfr_ptr) { *out = nullptr; }
void bigdecimal_sin(mpfr_ptr *out, mpfr_ptr) { *out = nullptr; }
void bigdecimal_cos(mpfr_ptr *out, mpfr_ptr) { *out = nullptr; }
void bigdecimal_tan(mpfr_ptr *out, mpfr_ptr) { *out = nullptr; }
void bigdecimal_ceil(mpfr_ptr *out, mpfr_ptr) { *out = nullptr; }
void bigdecimal_floor(mpfr_ptr *out, mpfr_ptr) { *out = nullptr; }
void bigdecimal_round(mpfr_ptr *out, mpfr_ptr) { *out = nullptr; }
void bigdecimal_atan(mpfr_ptr *out, mpfr_ptr) { *out = nullptr; }
void bigdecimal_asin(mpfr_ptr *out, mpfr_ptr) { *out = nullptr; }
void bigdecimal_acos(mpfr_ptr *out, mpfr_ptr) { *out = nullptr; }
void bigdecimal_sinh(mpfr_ptr *out, mpfr_ptr) { *out = nullptr; }
void bigdecimal_cosh(mpfr_ptr *out, mpfr_ptr) { *out = nullptr; }
void bigdecimal_tanh(mpfr_ptr *out, mpfr_ptr) { *out = nullptr; }

#endif /* BIGMATH_NO_GMP */
