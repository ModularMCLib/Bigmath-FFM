#include "bigmath_ffm.h"
#include "handles/native_backend.h"
#include "algos.h"
#include <cstdlib>
#include <cstring>
#include <limits>

#ifdef BIGMATH_HAS_CUDA
#include "cuda_runtime_state.h"
#endif

#ifndef BIGMATH_NO_GMP

// GPU-accelerated MPFR multiply for very high precision. The expensive part of
// mpfr_mul is the integer significand product; route that through the GPU bigint
// multiply (accelerated_mul) and let MPFR round exactly once via mpfr_set_z_2exp.
// Because a = za*2^ea and b = zb*2^eb exactly, the result is the single correct
// rounding of the exact product — bit-identical to mpfr_mul. Returns false (the
// caller falls back to mpfr_mul) for non-regular operands, when CUDA is
// unavailable, or below the precision where the GPU beats CPU GMP and the
// get/set_z overhead pays off.
static bool gpu_mpfr_mul(mpfr_ptr out, mpfr_ptr a, mpfr_ptr b) {
#ifndef BIGMATH_HAS_CUDA
	(void)out;
	(void)a;
	(void)b;
	return false;
#else
	static constexpr mpfr_prec_t GPU_MUL_PREC_THRESHOLD = 262144;  // ~79k decimal digits
	if (!bigmath::cuda::is_available() ||
			!mpfr_regular_p(a) || !mpfr_regular_p(b) ||
			mpfr_get_prec(a) < GPU_MUL_PREC_THRESHOLD ||
			mpfr_get_prec(b) < GPU_MUL_PREC_THRESHOLD) {
		return false;
	}
	mpz_t za, zb, zp;
	mpz_init(za);
	mpz_init(zb);
	mpz_init(zp);
	const mpfr_exp_t ea = mpfr_get_z_2exp(za, a);   // a = za * 2^ea
	const mpfr_exp_t eb = mpfr_get_z_2exp(zb, b);   // b = zb * 2^eb
	bigmath::accelerated_mul(zp, za, zb);           // exact significand product (GPU)
	mpfr_set_z_2exp(out, zp, ea + eb, MPFR_RNDN);   // single correct rounding to out's prec
	mpz_clear(za);
	mpz_clear(zb);
	mpz_clear(zp);
	return true;
#endif
}

void bigdecimal_backend_from_double(mpfr_ptr *out, double val, int precision) {
	*out = (mpfr_ptr)malloc(sizeof(__mpfr_struct));
	if (!*out) return;
	mpfr_init2(*out, precision);
	mpfr_set_d(*out, val, MPFR_RNDN);
}

void bigdecimal_backend_from_string(mpfr_ptr *out, const char *str, int precision) {
	*out = (mpfr_ptr)malloc(sizeof(__mpfr_struct));
	if (!*out) return;
	mpfr_init2(*out, precision);
	mpfr_set_str(*out, str, 10, MPFR_RNDN);
}

void bigdecimal_backend_from_bigint(mpfr_ptr *out, mpz_ptr val, int precision) {
	*out = (mpfr_ptr)malloc(sizeof(__mpfr_struct));
	if (!*out) return;
	mpfr_init2(*out, precision);
	mpfr_set_z(*out, val, MPFR_RNDN);
}

static void bigdecimal_backend_pow_si(mpfr_ptr out, mpfr_ptr a, long exp) {
	switch (exp) {
		case 0:
			mpfr_set_ui(out, 1, MPFR_RNDN);
			return;
		case 1:
			mpfr_set(out, a, MPFR_RNDN);
			return;
		case 2:
			mpfr_sqr(out, a, MPFR_RNDN);
			return;
		case -1:
			mpfr_ui_div(out, 1, a, MPFR_RNDN);
			return;
		default:
			mpfr_pow_si(out, a, exp, MPFR_RNDN);
			return;
	}
}

void bigdecimal_backend_init(mpfr_ptr *out, int precision) {
	*out = (mpfr_ptr)malloc(sizeof(__mpfr_struct));
	if (!*out) return;
	mpfr_init2(*out, precision);
}

void bigdecimal_backend_clear(mpfr_ptr a) {
	bigdecimal_backend_free(a);
}

void bigdecimal_backend_set(mpfr_ptr out, mpfr_ptr a) {
	if (mpfr_get_prec(out) != mpfr_get_prec(a)) {
		mpfr_set_prec(out, mpfr_get_prec(a));
	}
	mpfr_set(out, a, MPFR_RNDN);
}

void bigdecimal_backend_set_double(mpfr_ptr out, double val) {
	mpfr_set_d(out, val, MPFR_RNDN);
}

void bigdecimal_backend_set_string(mpfr_ptr out, const char *str, int precision) {
	mpfr_set_prec(out, precision);
	mpfr_set_str(out, str, 10, MPFR_RNDN);
}

void bigdecimal_backend_add(mpfr_ptr *out, mpfr_ptr a, mpfr_ptr b) {
	*out = (mpfr_ptr)malloc(sizeof(__mpfr_struct));
	if (!*out) return;
	mpfr_init2(*out, mpfr_get_prec(a));
	mpfr_add(*out, a, b, MPFR_RNDN);
}

void bigdecimal_backend_sub(mpfr_ptr *out, mpfr_ptr a, mpfr_ptr b) {
	*out = (mpfr_ptr)malloc(sizeof(__mpfr_struct));
	if (!*out) return;
	mpfr_init2(*out, mpfr_get_prec(a));
	mpfr_sub(*out, a, b, MPFR_RNDN);
}

void bigdecimal_backend_mul(mpfr_ptr *out, mpfr_ptr a, mpfr_ptr b) {
	*out = (mpfr_ptr)malloc(sizeof(__mpfr_struct));
	if (!*out) return;
	mpfr_init2(*out, mpfr_get_prec(a));
	if (!gpu_mpfr_mul(*out, a, b)) {
		mpfr_mul(*out, a, b, MPFR_RNDN);
	}
}

// Pure-MPFR reference multiply (never uses the GPU); correctness oracle for the
// gpu_mpfr_mul path, analogous to bigint_mul_gmp.
void bigdecimal_backend_mul_mpfr(mpfr_ptr *out, mpfr_ptr a, mpfr_ptr b) {
	*out = (mpfr_ptr)malloc(sizeof(__mpfr_struct));
	if (!*out) return;
	mpfr_init2(*out, mpfr_get_prec(a));
	mpfr_mul(*out, a, b, MPFR_RNDN);
}

void bigdecimal_backend_div(mpfr_ptr *out, mpfr_ptr a, mpfr_ptr b) {
	*out = (mpfr_ptr)malloc(sizeof(__mpfr_struct));
	if (!*out) return;
	mpfr_init2(*out, mpfr_get_prec(a));
	mpfr_div(*out, a, b, MPFR_RNDN);
}

void bigdecimal_backend_add_into(mpfr_ptr out, mpfr_ptr a, mpfr_ptr b) {
	if (mpfr_get_prec(out) != mpfr_get_prec(a)) {
		mpfr_set_prec(out, mpfr_get_prec(a));
	}
	mpfr_add(out, a, b, MPFR_RNDN);
}

void bigdecimal_backend_mul_into(mpfr_ptr out, mpfr_ptr a, mpfr_ptr b) {
	if (mpfr_get_prec(out) != mpfr_get_prec(a)) {
		mpfr_set_prec(out, mpfr_get_prec(a));
	}
	if (!gpu_mpfr_mul(out, a, b)) {
		mpfr_mul(out, a, b, MPFR_RNDN);
	}
}

void bigdecimal_backend_div_into(mpfr_ptr out, mpfr_ptr a, mpfr_ptr b) {
	if (mpfr_get_prec(out) != mpfr_get_prec(a)) {
		mpfr_set_prec(out, mpfr_get_prec(a));
	}
	mpfr_div(out, a, b, MPFR_RNDN);
}

void bigdecimal_backend_sqrt_into(mpfr_ptr out, mpfr_ptr a) {
	if (mpfr_get_prec(out) != mpfr_get_prec(a)) {
		mpfr_set_prec(out, mpfr_get_prec(a));
	}
	mpfr_sqrt(out, a, MPFR_RNDN);
}

void bigdecimal_backend_neg(mpfr_ptr *out, mpfr_ptr a) {
	*out = (mpfr_ptr)malloc(sizeof(__mpfr_struct));
	if (!*out) return;
	mpfr_init2(*out, mpfr_get_prec(a));
	mpfr_neg(*out, a, MPFR_RNDN);
}

void bigdecimal_backend_abs(mpfr_ptr *out, mpfr_ptr a) {
	*out = (mpfr_ptr)malloc(sizeof(__mpfr_struct));
	if (!*out) return;
	mpfr_init2(*out, mpfr_get_prec(a));
	mpfr_abs(*out, a, MPFR_RNDN);
}

int bigdecimal_backend_cmp(mpfr_ptr a, mpfr_ptr b) {
	return mpfr_cmp(a, b);
}

double bigdecimal_backend_to_double(mpfr_ptr a) {
	return mpfr_get_d(a, MPFR_RNDN);
}

char *bigdecimal_backend_to_string(mpfr_ptr a) {
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

char *bigdecimal_backend_format(mpfr_ptr a, int scale, int group_size, const char *group_sep) {
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
	size_t next_group = first_group;
	for (size_t i = 0; i < int_len; i++) {
		if (sep_len > 0 && i == next_group) {
			memcpy(out + pos, group_sep, sep_len);
			pos += sep_len;
			next_group += (size_t)group_size;
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

void bigdecimal_backend_free_string(char *s) {
	free(s);
}

void bigdecimal_backend_free(mpfr_ptr a) {
	if (a) {
		mpfr_clear(a);
		free(a);
	}
}

void bigdecimal_backend_sqrt(mpfr_ptr *out, mpfr_ptr a) {
	*out = (mpfr_ptr)malloc(sizeof(__mpfr_struct));
	if (!*out) return;
	mpfr_init2(*out, mpfr_get_prec(a));
	mpfr_sqrt(*out, a, MPFR_RNDN);
}

void bigdecimal_backend_pow(mpfr_ptr *out, mpfr_ptr a, mpfr_ptr b) {
	*out = (mpfr_ptr)malloc(sizeof(__mpfr_struct));
	if (!*out) return;
	mpfr_init2(*out, mpfr_get_prec(a));
	if (mpfr_cmp_ui(b, 2) == 0) {
		mpfr_sqr(*out, a, MPFR_RNDN);
		return;
	}
	if (mpfr_integer_p(b)) {
		if (mpfr_fits_ulong_p(b, MPFR_RNDN)) {
			mpfr_pow_ui(*out, a, mpfr_get_ui(b, MPFR_RNDN), MPFR_RNDN);
			return;
		}
		if (mpfr_fits_slong_p(b, MPFR_RNDN)) {
			bigdecimal_backend_pow_si(*out, a, mpfr_get_si(b, MPFR_RNDN));
			return;
		}
	}
	mpfr_pow(*out, a, b, MPFR_RNDN);
}

void bigdecimal_backend_pow_long(mpfr_ptr *out, mpfr_ptr a, int64_t exp) {
	*out = (mpfr_ptr)malloc(sizeof(__mpfr_struct));
	if (!*out) return;
	mpfr_init2(*out, mpfr_get_prec(a));
	if (exp >= -1 && exp <= 2) {
		bigdecimal_backend_pow_si(*out, a, static_cast<long>(exp));
		return;
	}
	if (exp >= 0 && static_cast<uint64_t>(exp) <= static_cast<uint64_t>(std::numeric_limits<unsigned long>::max())) {
		mpfr_pow_ui(*out, a, static_cast<unsigned long>(exp), MPFR_RNDN);
		return;
	}
	if (exp >= static_cast<int64_t>(std::numeric_limits<long>::min())
			&& exp <= static_cast<int64_t>(std::numeric_limits<long>::max())) {
		bigdecimal_backend_pow_si(*out, a, static_cast<long>(exp));
		return;
	}
	mpfr_t exponent;
	mpfr_init2(exponent, 64);
	mpfr_set_sj(exponent, exp, MPFR_RNDN);
	mpfr_pow(*out, a, exponent, MPFR_RNDN);
	mpfr_clear(exponent);
}

void bigdecimal_backend_log(mpfr_ptr *out, mpfr_ptr a) {
	*out = (mpfr_ptr)malloc(sizeof(__mpfr_struct));
	if (!*out) return;
	mpfr_init2(*out, mpfr_get_prec(a));
	mpfr_log(*out, a, MPFR_RNDN);
}

void bigdecimal_backend_exp(mpfr_ptr *out, mpfr_ptr a) {
	*out = (mpfr_ptr)malloc(sizeof(__mpfr_struct));
	if (!*out) return;
	mpfr_init2(*out, mpfr_get_prec(a));
	mpfr_exp(*out, a, MPFR_RNDN);
}

void bigdecimal_backend_sin(mpfr_ptr *out, mpfr_ptr a) {
	*out = (mpfr_ptr)malloc(sizeof(__mpfr_struct));
	if (!*out) return;
	mpfr_init2(*out, mpfr_get_prec(a));
	mpfr_sin(*out, a, MPFR_RNDN);
}

void bigdecimal_backend_cos(mpfr_ptr *out, mpfr_ptr a) {
	*out = (mpfr_ptr)malloc(sizeof(__mpfr_struct));
	if (!*out) return;
	mpfr_init2(*out, mpfr_get_prec(a));
	mpfr_cos(*out, a, MPFR_RNDN);
}

void bigdecimal_backend_tan(mpfr_ptr *out, mpfr_ptr a) {
	*out = (mpfr_ptr)malloc(sizeof(__mpfr_struct));
	if (!*out) return;
	mpfr_init2(*out, mpfr_get_prec(a));
	mpfr_tan(*out, a, MPFR_RNDN);
}

void bigdecimal_backend_ceil(mpfr_ptr *out, mpfr_ptr a) {
	*out = (mpfr_ptr)malloc(sizeof(__mpfr_struct));
	if (!*out) return;
	mpfr_init2(*out, mpfr_get_prec(a));
	mpfr_ceil(*out, a);
}

void bigdecimal_backend_floor(mpfr_ptr *out, mpfr_ptr a) {
	*out = (mpfr_ptr)malloc(sizeof(__mpfr_struct));
	if (!*out) return;
	mpfr_init2(*out, mpfr_get_prec(a));
	mpfr_floor(*out, a);
}

void bigdecimal_backend_round(mpfr_ptr *out, mpfr_ptr a) {
	*out = (mpfr_ptr)malloc(sizeof(__mpfr_struct));
	if (!*out) return;
	mpfr_init2(*out, mpfr_get_prec(a));
	mpfr_round(*out, a);
}

void bigdecimal_backend_atan(mpfr_ptr *out, mpfr_ptr a) {
	*out = (mpfr_ptr)malloc(sizeof(__mpfr_struct));
	if (!*out) return;
	mpfr_init2(*out, mpfr_get_prec(a));
	mpfr_atan(*out, a, MPFR_RNDN);
}

void bigdecimal_backend_asin(mpfr_ptr *out, mpfr_ptr a) {
	*out = (mpfr_ptr)malloc(sizeof(__mpfr_struct));
	if (!*out) return;
	mpfr_init2(*out, mpfr_get_prec(a));
	mpfr_asin(*out, a, MPFR_RNDN);
}

void bigdecimal_backend_acos(mpfr_ptr *out, mpfr_ptr a) {
	*out = (mpfr_ptr)malloc(sizeof(__mpfr_struct));
	if (!*out) return;
	mpfr_init2(*out, mpfr_get_prec(a));
	mpfr_acos(*out, a, MPFR_RNDN);
}

void bigdecimal_backend_sinh(mpfr_ptr *out, mpfr_ptr a) {
	*out = (mpfr_ptr)malloc(sizeof(__mpfr_struct));
	if (!*out) return;
	mpfr_init2(*out, mpfr_get_prec(a));
	mpfr_sinh(*out, a, MPFR_RNDN);
}

void bigdecimal_backend_cosh(mpfr_ptr *out, mpfr_ptr a) {
	*out = (mpfr_ptr)malloc(sizeof(__mpfr_struct));
	if (!*out) return;
	mpfr_init2(*out, mpfr_get_prec(a));
	mpfr_cosh(*out, a, MPFR_RNDN);
}

void bigdecimal_backend_tanh(mpfr_ptr *out, mpfr_ptr a) {
	*out = (mpfr_ptr)malloc(sizeof(__mpfr_struct));
	if (!*out) return;
	mpfr_init2(*out, mpfr_get_prec(a));
	mpfr_tanh(*out, a, MPFR_RNDN);
}

#else

void bigdecimal_backend_from_double(mpfr_ptr *out, double, int) { *out = nullptr; }
void bigdecimal_backend_from_string(mpfr_ptr *out, const char *, int) { *out = nullptr; }
void bigdecimal_backend_from_bigint(mpfr_ptr *out, mpz_ptr, int) { *out = nullptr; }
void bigdecimal_backend_init(mpfr_ptr *out, int) { *out = nullptr; }
void bigdecimal_backend_clear(mpfr_ptr) { }
void bigdecimal_backend_set(mpfr_ptr, mpfr_ptr) { }
void bigdecimal_backend_set_double(mpfr_ptr, double) { }
void bigdecimal_backend_set_string(mpfr_ptr, const char *, int) { }
void bigdecimal_backend_add(mpfr_ptr *out, mpfr_ptr, mpfr_ptr) { *out = nullptr; }
void bigdecimal_backend_sub(mpfr_ptr *out, mpfr_ptr, mpfr_ptr) { *out = nullptr; }
void bigdecimal_backend_mul(mpfr_ptr *out, mpfr_ptr, mpfr_ptr) { *out = nullptr; }
void bigdecimal_backend_mul_mpfr(mpfr_ptr *out, mpfr_ptr, mpfr_ptr) { *out = nullptr; }
void bigdecimal_backend_div(mpfr_ptr *out, mpfr_ptr, mpfr_ptr) { *out = nullptr; }
void bigdecimal_backend_add_into(mpfr_ptr, mpfr_ptr, mpfr_ptr) { }
void bigdecimal_backend_mul_into(mpfr_ptr, mpfr_ptr, mpfr_ptr) { }
void bigdecimal_backend_div_into(mpfr_ptr, mpfr_ptr, mpfr_ptr) { }
void bigdecimal_backend_sqrt_into(mpfr_ptr, mpfr_ptr) { }
void bigdecimal_backend_neg(mpfr_ptr *out, mpfr_ptr) { *out = nullptr; }
void bigdecimal_backend_abs(mpfr_ptr *out, mpfr_ptr) { *out = nullptr; }
int  bigdecimal_backend_cmp(mpfr_ptr, mpfr_ptr) { return 0; }
double bigdecimal_backend_to_double(mpfr_ptr) { return 0.0; }
char *bigdecimal_backend_to_string(mpfr_ptr) { return nullptr; }
char *bigdecimal_backend_format(mpfr_ptr, int, int, const char *) { return nullptr; }
void bigdecimal_backend_free_string(char *) { }
void bigdecimal_backend_free(mpfr_ptr) { }
void bigdecimal_backend_sqrt(mpfr_ptr *out, mpfr_ptr) { *out = nullptr; }
void bigdecimal_backend_pow(mpfr_ptr *out, mpfr_ptr, mpfr_ptr) { *out = nullptr; }
void bigdecimal_backend_pow_long(mpfr_ptr *out, mpfr_ptr, int64_t) { *out = nullptr; }
void bigdecimal_backend_log(mpfr_ptr *out, mpfr_ptr) { *out = nullptr; }
void bigdecimal_backend_exp(mpfr_ptr *out, mpfr_ptr) { *out = nullptr; }
void bigdecimal_backend_sin(mpfr_ptr *out, mpfr_ptr) { *out = nullptr; }
void bigdecimal_backend_cos(mpfr_ptr *out, mpfr_ptr) { *out = nullptr; }
void bigdecimal_backend_tan(mpfr_ptr *out, mpfr_ptr) { *out = nullptr; }
void bigdecimal_backend_ceil(mpfr_ptr *out, mpfr_ptr) { *out = nullptr; }
void bigdecimal_backend_floor(mpfr_ptr *out, mpfr_ptr) { *out = nullptr; }
void bigdecimal_backend_round(mpfr_ptr *out, mpfr_ptr) { *out = nullptr; }
void bigdecimal_backend_atan(mpfr_ptr *out, mpfr_ptr) { *out = nullptr; }
void bigdecimal_backend_asin(mpfr_ptr *out, mpfr_ptr) { *out = nullptr; }
void bigdecimal_backend_acos(mpfr_ptr *out, mpfr_ptr) { *out = nullptr; }
void bigdecimal_backend_sinh(mpfr_ptr *out, mpfr_ptr) { *out = nullptr; }
void bigdecimal_backend_cosh(mpfr_ptr *out, mpfr_ptr) { *out = nullptr; }
void bigdecimal_backend_tanh(mpfr_ptr *out, mpfr_ptr) { *out = nullptr; }

#endif /* BIGMATH_NO_GMP */
