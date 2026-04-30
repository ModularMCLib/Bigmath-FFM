#include "bigmath_ffm.h"
#include <cstdlib>
#include <cstring>

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
	return str;
}

char *bigdecimal_format(mpfr_ptr a, int scale, int group_size, const char *group_sep) {
	int max_frac;
	if (scale >= 0) {
		max_frac = scale;
	} else {
		int decimal_prec = (int)(mpfr_get_prec(a) * 0.30103) + 2;
		max_frac = decimal_prec > 40 ? 40 : decimal_prec;
	}

	char fmt[32];
	snprintf(fmt, sizeof(fmt), "%%.%dRf", max_frac);

	int buf_size = max_frac + 128;
	char *raw = (char *)malloc(buf_size);
	if (!raw) return nullptr;

	mpfr_sprintf(raw, fmt, a);
	if (!raw[0]) { free(raw); return nullptr; }

	bool neg = (raw[0] == '-');
	char *p = raw + (neg ? 1 : 0);

	char *dot = strchr(p, '.');
	if (!dot) {
		size_t len = strlen(p);
		size_t total = len + (neg ? 1 : 0);
		char *out = (char *)malloc(total + 1);
		if (!out) { free(raw); return nullptr; }
		size_t pos = 0;
		if (neg) out[pos++] = '-';
		memcpy(out + pos, p, len);
		out[pos + len] = '\0';
		free(raw);
		return out;
	}

	size_t int_len = dot - p;
	char *frac_raw = dot + 1;
	size_t frac_len = strlen(frac_raw);

	if (scale < 0) {
		while (frac_len > 0 && frac_raw[frac_len - 1] == '0') frac_len--;
	} else if ((size_t)scale > frac_len) {
		char *padded = (char *)malloc(scale + 1);
		if (!padded) { free(raw); return nullptr; }
		memcpy(padded, frac_raw, frac_len);
		memset(padded + frac_len, '0', scale - frac_len);
		padded[scale] = '\0';
		frac_raw = padded;
		frac_len = scale;
	} else if ((size_t)scale < frac_len) {
		frac_len = scale;
	}

	int pad_alloc = 0;
	if (scale < 0) {
		while (frac_len > 0 && frac_raw[frac_len - 1] == '0') frac_len--;
	} else if ((size_t)scale > frac_len) {
		char *padded = (char *)malloc(scale + 1);
		if (!padded) { free(raw); return nullptr; }
		memcpy(padded, frac_raw, frac_len);
		memset(padded + frac_len, '0', scale - frac_len);
		padded[scale] = '\0';
		frac_raw = padded;
		frac_len = scale;
		pad_alloc = 1;
	} else if ((size_t)scale < frac_len) {
		frac_len = scale;
	}

	char *int_only;
	int int_only_alloc;
	if (int_len > 0) {
		int_only = (char *)malloc(int_len + 1);
		if (!int_only) { if (pad_alloc) free((char*)frac_raw); free(raw); return nullptr; }
		memcpy(int_only, p, int_len);
		int_only[int_len] = '\0';
		int_only_alloc = 1;
	} else {
		int_only = (char*)"0";
		int_only_alloc = 0;
	}

	size_t sep_len = (group_sep && group_size > 0) ? strlen(group_sep) : 0;
	size_t ig_len = int_len > 0 ? int_len : 1;
	size_t groups = (ig_len + group_size - 1) / group_size;
	char *int_fmt;
	int int_fmt_alloc;
	if (sep_len > 0 && group_size > 0 && int_len > 0) {
		size_t fmt_len = ig_len + (groups - 1) * sep_len;
		int_fmt = (char *)malloc(fmt_len + 1);
		if (!int_fmt) {
			if (int_only_alloc) free(int_only);
			if (pad_alloc) free((char*)frac_raw);
			free(raw);
			return nullptr;
		}
		size_t first_group = ig_len % group_size;
		if (first_group == 0) first_group = group_size;
		size_t out_pos = 0;
		memcpy(int_fmt, int_only, first_group);
		out_pos += first_group;
		for (size_t i = first_group; i < ig_len; i += group_size) {
			memcpy(int_fmt + out_pos, group_sep, sep_len);
			out_pos += sep_len;
			memcpy(int_fmt + out_pos, int_only + i, group_size);
			out_pos += group_size;
		}
		int_fmt[out_pos] = '\0';
		int_fmt_alloc = 1;
	} else {
		int_fmt = int_only;
		int_fmt_alloc = 0;
	}

	size_t sign_len = (neg ? 1 : 0);
	size_t int_fmt_len = strlen(int_fmt);
	size_t total = sign_len + int_fmt_len + (frac_len > 0 ? 1 + frac_len : 0);
	char *out = (char *)malloc(total + 1);
	if (!out) {
		if (int_fmt_alloc) free(int_fmt);
		if (int_only_alloc) free(int_only);
		if (pad_alloc) free((char*)frac_raw);
		free(raw);
		return nullptr;
	}
	size_t pos = 0;
	if (neg) out[pos++] = '-';
	memcpy(out + pos, int_fmt, int_fmt_len);
	pos += int_fmt_len;
	if (frac_len > 0) {
		out[pos++] = '.';
		memcpy(out + pos, frac_raw, frac_len);
		pos += frac_len;
	}
	out[pos] = '\0';

	if (int_fmt_alloc) free(int_fmt);
	if (int_only_alloc) free(int_only);
	if (pad_alloc) free((char*)frac_raw);
	free(raw);
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
void bigdecimal_add(mpfr_ptr *out, mpfr_ptr, mpfr_ptr) { *out = nullptr; }
void bigdecimal_sub(mpfr_ptr *out, mpfr_ptr, mpfr_ptr) { *out = nullptr; }
void bigdecimal_mul(mpfr_ptr *out, mpfr_ptr, mpfr_ptr) { *out = nullptr; }
void bigdecimal_div(mpfr_ptr *out, mpfr_ptr, mpfr_ptr) { *out = nullptr; }
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
