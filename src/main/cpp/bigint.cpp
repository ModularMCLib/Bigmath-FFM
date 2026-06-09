#include "bigmath_ffm.h"
#include "algos.h"
#include <cstdlib>
#include <cstring>
#include <limits>

#ifndef BIGMATH_NO_GMP

static void mpz_set_int64(mpz_ptr out, int64_t val) {
	if (val >= static_cast<int64_t>(std::numeric_limits<long>::min())
			&& val <= static_cast<int64_t>(std::numeric_limits<long>::max())) {
		mpz_set_si(out, static_cast<long>(val));
		return;
	}
	uint64_t magnitude = val >= 0
		? static_cast<uint64_t>(val)
		: static_cast<uint64_t>(-(val + 1)) + 1;
	if (magnitude <= static_cast<uint64_t>(std::numeric_limits<unsigned long>::max())) {
		mpz_set_ui(out, static_cast<unsigned long>(magnitude));
	} else {
		mpz_import(out, 1, -1, sizeof(magnitude), 0, 0, &magnitude);
	}
	if (val < 0) {
		mpz_neg(out, out);
	}
}

void bigint_from_long(mpz_ptr *out, int64_t val) {
	*out = (mpz_ptr)malloc(sizeof(__mpz_struct));
	if (!*out) return;
	if (val >= static_cast<int64_t>(std::numeric_limits<long>::min())
			&& val <= static_cast<int64_t>(std::numeric_limits<long>::max())) {
		mpz_init_set_si(*out, static_cast<long>(val));
		return;
	}
	mpz_init(*out);
	mpz_set_int64(*out, val);
}

void bigint_from_string(mpz_ptr *out, const char *str, int radix) {
	*out = (mpz_ptr)malloc(sizeof(__mpz_struct));
	if (!*out) return;
	mpz_init_set_str(*out, str, radix);
}

void bigint_init(mpz_ptr *out) {
	*out = (mpz_ptr)malloc(sizeof(__mpz_struct));
	if (!*out) return;
	mpz_init(*out);
}

void bigint_clear(mpz_ptr a) {
	bigint_free(a);
}

void bigint_set(mpz_ptr out, mpz_ptr a) {
	mpz_set(out, a);
}

void bigint_set_long(mpz_ptr out, int64_t val) {
	mpz_set_int64(out, val);
}

void bigint_set_string(mpz_ptr out, const char *str, int radix) {
	mpz_set_str(out, str, radix);
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
	int alen = mpz_size(a);
	int blen = mpz_size(b);
	if (alen == 0 || blen == 0) {
		mpz_init(*out);
	} else {
		mpz_init2(*out, static_cast<mp_bitcnt_t>(alen + blen + 1) * GMP_NUMB_BITS);
	}
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

void bigint_add_into(mpz_ptr out, mpz_ptr a, mpz_ptr b) {
	mpz_add(out, a, b);
}

void bigint_mul_into(mpz_ptr out, mpz_ptr a, mpz_ptr b) {
	int alen = mpz_size(a);
	int blen = mpz_size(b);
	if (out != a && out != b && alen + blen >= bigmath::NTT_THRESHOLD) {
		bigmath::fft_multiply(out, a, b);
	} else {
		mpz_mul(out, a, b);
	}
}

void bigint_div_into(mpz_ptr out, mpz_ptr a, mpz_ptr b) {
	mpz_tdiv_q(out, a, b);
}

void bigint_sqrt_into(mpz_ptr out, mpz_ptr a) {
	mpz_sqrt(out, a);
}

void bigint_pow(mpz_ptr *out, mpz_ptr a, uint64_t exp) {
	*out = (mpz_ptr)malloc(sizeof(__mpz_struct));
	if (!*out) return;
	mpz_init(*out);
	switch (exp) {
		case 0:
			mpz_set_ui(*out, 1);
			return;
		case 1:
			mpz_set(*out, a);
			return;
		case 2:
			mpz_mul(*out, a, a);
			return;
		default:
			break;
	}
	if (exp <= static_cast<uint64_t>(std::numeric_limits<unsigned long>::max())) {
		mpz_pow_ui(*out, a, static_cast<unsigned long>(exp));
	} else {
		bigmath::fast_pow(*out, a, exp);
	}
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
	if (mpz_fits_slong_p(a)) {
		return static_cast<int64_t>(mpz_get_si(a));
	}
	uint64_t magnitude = 0;
	mpz_export(&magnitude, nullptr, -1, sizeof(magnitude), 0, 0, a);
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
	if (group_size <= 0 || group_sep == nullptr || *group_sep == '\0') {
		return mpz_get_str(nullptr, 10, a);
	}
	size_t digit_cap = mpz_sizeinbase(a, 10);
	bool value_neg = mpz_sgn(a) < 0;
	size_t sep_len = group_sep[1] == '\0' ? 1 : strlen(group_sep);
	size_t estimated_sep_count = digit_cap > 0 ? (digit_cap - 1) / static_cast<size_t>(group_size) : 0;
	size_t capacity = (value_neg ? 1 : 0) + digit_cap + estimated_sep_count * sep_len + 1;
	char *raw = (char *)malloc(capacity);
	if (!raw) return nullptr;
	if (!mpz_get_str(raw, 10, a)) {
		free(raw);
		return nullptr;
	}
	bool neg = (raw[0] == '-');
	size_t sign_offset = neg ? 1 : 0;
	char *digits = raw + sign_offset;
	size_t len = strlen(digits);
	if (len <= static_cast<size_t>(group_size)) {
		return raw;
	}
	size_t sep_count = (len - 1) / static_cast<size_t>(group_size);
	size_t new_len = sign_offset + len + sep_count * sep_len;
	char *out = raw;
	size_t read = sign_offset + len;
	size_t write = new_len;
	out[write--] = '\0';
	if (group_size == 3 && sep_len == 1) {
		char sep = group_sep[0];
		while (read - sign_offset > 3) {
			out[write--] = out[--read];
			out[write--] = out[--read];
			out[write--] = out[--read];
			out[write--] = sep;
		}
		while (read > sign_offset) {
			out[write--] = out[--read];
		}
		return out;
	}
	size_t group_digits = 0;
	while (read > sign_offset) {
		out[write--] = out[--read];
		group_digits++;
		if (group_digits == static_cast<size_t>(group_size) && read > sign_offset) {
			if (sep_len == 1) {
				out[write--] = group_sep[0];
			} else {
				write -= sep_len - 1;
				memcpy(out + write, group_sep, sep_len);
				write--;
			}
			group_digits = 0;
		}
	}
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
	mpz_gcd(*out, a, b);
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
	mpz_fac_ui(*out, n);
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
void bigint_init(mpz_ptr *out) { *out = nullptr; }
void bigint_clear(mpz_ptr) { }
void bigint_set(mpz_ptr, mpz_ptr) { }
void bigint_set_long(mpz_ptr, int64_t) { }
void bigint_set_string(mpz_ptr, const char *, int) { }
void bigint_add(mpz_ptr *out, mpz_ptr, mpz_ptr) { *out = nullptr; }
void bigint_sub(mpz_ptr *out, mpz_ptr, mpz_ptr) { *out = nullptr; }
void bigint_mul(mpz_ptr *out, mpz_ptr, mpz_ptr) { *out = nullptr; }
void bigint_div(mpz_ptr *out, mpz_ptr, mpz_ptr) { *out = nullptr; }
void bigint_mod(mpz_ptr *out, mpz_ptr, mpz_ptr) { *out = nullptr; }
void bigint_add_into(mpz_ptr, mpz_ptr, mpz_ptr) { }
void bigint_mul_into(mpz_ptr, mpz_ptr, mpz_ptr) { }
void bigint_div_into(mpz_ptr, mpz_ptr, mpz_ptr) { }
void bigint_sqrt_into(mpz_ptr, mpz_ptr) { }
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
