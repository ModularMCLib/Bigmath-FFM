#include "bigmath_ffm.h"
#include "handles/native_backend.h"
#include "algos.h"
#include <algorithm>
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

static bool mpz_ior_nonnegative_fast(mpz_ptr out, mpz_ptr a, mpz_ptr b) {
	const mp_size_t asize = a->_mp_size;
	const mp_size_t bsize = b->_mp_size;
	if (asize < 0 || bsize < 0) {
		return false;
	}
	if (asize == 0) {
		mpz_init_set(out, b);
		return true;
	}
	if (bsize == 0) {
		mpz_init_set(out, a);
		return true;
	}
	if (asize >= bsize) {
		mpz_init2(out, static_cast<mp_bitcnt_t>(asize) * GMP_NUMB_BITS);
		mpn_ior_n(out->_mp_d, a->_mp_d, b->_mp_d, bsize);
		if (asize > bsize) {
			mpn_copyi(out->_mp_d + bsize, a->_mp_d + bsize, asize - bsize);
		}
		out->_mp_size = asize;
		return true;
	}
	mpz_init2(out, static_cast<mp_bitcnt_t>(bsize) * GMP_NUMB_BITS);
	mpn_ior_n(out->_mp_d, a->_mp_d, b->_mp_d, asize);
	mpn_copyi(out->_mp_d + asize, b->_mp_d + asize, bsize - asize);
	out->_mp_size = bsize;
	return true;
}

static void mpz_normalize_nonnegative(mpz_ptr value, mp_size_t size) {
	while (size > 0 && value->_mp_d[size - 1] == 0) {
		size--;
	}
	value->_mp_size = size;
}

static bool mpz_and_nonnegative_fast(mpz_ptr out, mpz_ptr a, mpz_ptr b) {
	const mp_size_t asize = a->_mp_size;
	const mp_size_t bsize = b->_mp_size;
	if (asize < 0 || bsize < 0) {
		return false;
	}
	const mp_size_t rsize = std::min(asize, bsize);
	if (rsize == 0) {
		mpz_init(out);
		return true;
	}
	mpz_init2(out, static_cast<mp_bitcnt_t>(rsize) * GMP_NUMB_BITS);
	mpn_and_n(out->_mp_d, a->_mp_d, b->_mp_d, rsize);
	mpz_normalize_nonnegative(out, rsize);
	return true;
}

static bool mpz_xor_nonnegative_fast(mpz_ptr out, mpz_ptr a, mpz_ptr b) {
	const mp_size_t asize = a->_mp_size;
	const mp_size_t bsize = b->_mp_size;
	if (asize < 0 || bsize < 0) {
		return false;
	}
	if (asize == 0) {
		mpz_init_set(out, b);
		return true;
	}
	if (bsize == 0) {
		mpz_init_set(out, a);
		return true;
	}
	const mp_size_t min_size = std::min(asize, bsize);
	const mp_size_t max_size = std::max(asize, bsize);
	mpz_init2(out, static_cast<mp_bitcnt_t>(max_size) * GMP_NUMB_BITS);
	mpn_xor_n(out->_mp_d, a->_mp_d, b->_mp_d, min_size);
	if (asize > bsize) {
		mpn_copyi(out->_mp_d + min_size, a->_mp_d + min_size, asize - min_size);
	} else if (bsize > asize) {
		mpn_copyi(out->_mp_d + min_size, b->_mp_d + min_size, bsize - min_size);
	}
	mpz_normalize_nonnegative(out, max_size);
	return true;
}

static bool should_use_gmp_pow_ui(mpz_ptr base, uint64_t exp) {
	if (exp > static_cast<uint64_t>(std::numeric_limits<unsigned long>::max())) {
		return false;
	}
	static constexpr mp_bitcnt_t GMP_POW_BIT_THRESHOLD = 4096;
	const mp_bitcnt_t base_bits = mpz_sizeinbase(base, 2);
	return base_bits <= GMP_POW_BIT_THRESHOLD / static_cast<mp_bitcnt_t>(exp);
}

void bigint_backend_from_long(mpz_ptr *out, int64_t val) {
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

void bigint_backend_from_string(mpz_ptr *out, const char *str, int radix) {
	*out = (mpz_ptr)malloc(sizeof(__mpz_struct));
	if (!*out) return;
	mpz_init_set_str(*out, str, radix);
}

void bigint_backend_init(mpz_ptr *out) {
	*out = (mpz_ptr)malloc(sizeof(__mpz_struct));
	if (!*out) return;
	mpz_init(*out);
}

void bigint_backend_clear(mpz_ptr a) {
	bigint_backend_free(a);
}

void bigint_backend_set(mpz_ptr out, mpz_ptr a) {
	mpz_set(out, a);
}

void bigint_backend_set_long(mpz_ptr out, int64_t val) {
	mpz_set_int64(out, val);
}

void bigint_backend_set_string(mpz_ptr out, const char *str, int radix) {
	mpz_set_str(out, str, radix);
}

void bigint_backend_add(mpz_ptr *out, mpz_ptr a, mpz_ptr b) {
	*out = (mpz_ptr)malloc(sizeof(__mpz_struct));
	if (!*out) return;
	mpz_init(*out);
	mpz_add(*out, a, b);
}

void bigint_backend_sub(mpz_ptr *out, mpz_ptr a, mpz_ptr b) {
	*out = (mpz_ptr)malloc(sizeof(__mpz_struct));
	if (!*out) return;
	mpz_init(*out);
	mpz_sub(*out, a, b);
}

void bigint_backend_mul(mpz_ptr *out, mpz_ptr a, mpz_ptr b) {
	*out = (mpz_ptr)malloc(sizeof(__mpz_struct));
	if (!*out) return;
	int alen = mpz_size(a);
	int blen = mpz_size(b);
	if (alen == 0 || blen == 0) {
		mpz_init(*out);
	} else {
		mpz_init2(*out, static_cast<mp_bitcnt_t>(alen + blen + 1) * GMP_NUMB_BITS);
	}
	bigmath::accelerated_mul(*out, a, b);
}

void bigint_backend_mul_gmp(mpz_ptr *out, mpz_ptr a, mpz_ptr b) {
	*out = (mpz_ptr)malloc(sizeof(__mpz_struct));
	if (!*out) return;
	int alen = mpz_size(a);
	int blen = mpz_size(b);
	if (alen == 0 || blen == 0) {
		mpz_init(*out);
	} else {
		mpz_init2(*out, static_cast<mp_bitcnt_t>(alen + blen + 1) * GMP_NUMB_BITS);
	}
	mpz_mul(*out, a, b);
}

void bigint_backend_mul_cpu(mpz_ptr *out, mpz_ptr a, mpz_ptr b) {
	bigint_backend_mul_gmp(out, a, b);
}

void bigint_backend_div(mpz_ptr *out, mpz_ptr a, mpz_ptr b) {
	*out = (mpz_ptr)malloc(sizeof(__mpz_struct));
	if (!*out) return;
	mpz_init(*out);
	mpz_tdiv_q(*out, a, b);
}

void bigint_backend_mod(mpz_ptr *out, mpz_ptr a, mpz_ptr b) {
	*out = (mpz_ptr)malloc(sizeof(__mpz_struct));
	if (!*out) return;
	mpz_init(*out);
	mpz_tdiv_r(*out, a, b);
}

void bigint_backend_add_into(mpz_ptr out, mpz_ptr a, mpz_ptr b) {
	mpz_add(out, a, b);
}

void bigint_backend_mul_into(mpz_ptr out, mpz_ptr a, mpz_ptr b) {
	bigmath::accelerated_mul(out, a, b);
}

void bigint_backend_div_into(mpz_ptr out, mpz_ptr a, mpz_ptr b) {
	mpz_tdiv_q(out, a, b);
}

void bigint_backend_sqrt_into(mpz_ptr out, mpz_ptr a) {
	mpz_sqrt(out, a);
}

void bigint_backend_pow(mpz_ptr *out, mpz_ptr a, uint64_t exp) {
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
			bigmath::accelerated_mul(*out, a, a);
			return;
		default:
			break;
	}
	if (should_use_gmp_pow_ui(a, exp)) {
		mpz_pow_ui(*out, a, static_cast<unsigned long>(exp));
		return;
	}
	bigmath::fast_pow(*out, a, exp);
}

void bigint_backend_powm(mpz_ptr *out, mpz_ptr base, mpz_ptr exp, mpz_ptr mod) {
	*out = (mpz_ptr)malloc(sizeof(__mpz_struct));
	if (!*out) return;
	mpz_init(*out);
	bigmath::modpow(*out, base, exp, mod);
}

// Pure-GMP reference (mpz_powm); correctness oracle for the Barrett/GPU modpow.
void bigint_backend_powm_gmp(mpz_ptr *out, mpz_ptr base, mpz_ptr exp, mpz_ptr mod) {
	*out = (mpz_ptr)malloc(sizeof(__mpz_struct));
	if (!*out) return;
	mpz_init(*out);
	mpz_powm(*out, base, exp, mod);
}

void bigint_backend_neg(mpz_ptr *out, mpz_ptr a) {
	*out = (mpz_ptr)malloc(sizeof(__mpz_struct));
	if (!*out) return;
	mpz_init(*out);
	mpz_neg(*out, a);
}

void bigint_backend_abs(mpz_ptr *out, mpz_ptr a) {
	*out = (mpz_ptr)malloc(sizeof(__mpz_struct));
	if (!*out) return;
	mpz_init(*out);
	mpz_abs(*out, a);
}

int bigint_backend_cmp(mpz_ptr a, mpz_ptr b) {
	return mpz_cmp(a, b);
}

int bigint_backend_sign(mpz_ptr a) {
	return mpz_sgn(a);
}

int64_t bigint_backend_to_long(mpz_ptr a) {
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

double bigint_backend_to_double(mpz_ptr a) {
	return mpz_get_d(a);
}

char *bigint_backend_to_string(mpz_ptr a, int radix) {
	return mpz_get_str(nullptr, radix, a);
}

void bigint_backend_free_string(char *s) {
	free(s);
}

void bigint_backend_free(mpz_ptr a) {
	if (a) {
		mpz_clear(a);
		free(a);
	}
}

void bigint_backend_gcd(mpz_ptr *out, mpz_ptr a, mpz_ptr b) {
	*out = (mpz_ptr)malloc(sizeof(__mpz_struct));
	if (!*out) return;
	mpz_init(*out);
	mpz_gcd(*out, a, b);
}

void bigint_backend_lcm(mpz_ptr *out, mpz_ptr a, mpz_ptr b) {
	*out = (mpz_ptr)malloc(sizeof(__mpz_struct));
	if (!*out) return;
	mpz_init(*out);
	mpz_lcm(*out, a, b);
}

void bigint_backend_sqrt(mpz_ptr *out, mpz_ptr a) {
	*out = (mpz_ptr)malloc(sizeof(__mpz_struct));
	if (!*out) return;
	mpz_init(*out);
	mpz_sqrt(*out, a);
}

void bigint_backend_and(mpz_ptr *out, mpz_ptr a, mpz_ptr b) {
	*out = (mpz_ptr)malloc(sizeof(__mpz_struct));
	if (!*out) return;
	if (!mpz_and_nonnegative_fast(*out, a, b)) {
		mpz_init(*out);
		mpz_and(*out, a, b);
	}
}

void bigint_backend_or(mpz_ptr *out, mpz_ptr a, mpz_ptr b) {
	*out = (mpz_ptr)malloc(sizeof(__mpz_struct));
	if (!*out) return;
	if (!mpz_ior_nonnegative_fast(*out, a, b)) {
		mpz_init(*out);
		mpz_ior(*out, a, b);
	}
}

void bigint_backend_xor(mpz_ptr *out, mpz_ptr a, mpz_ptr b) {
	*out = (mpz_ptr)malloc(sizeof(__mpz_struct));
	if (!*out) return;
	if (!mpz_xor_nonnegative_fast(*out, a, b)) {
		mpz_init(*out);
		mpz_xor(*out, a, b);
	}
}

void bigint_backend_shl(mpz_ptr *out, mpz_ptr a, uint64_t bits) {
	*out = (mpz_ptr)malloc(sizeof(__mpz_struct));
	if (!*out) return;
	mpz_init(*out);
	mpz_mul_2exp(*out, a, static_cast<mp_bitcnt_t>(bits));
}

void bigint_backend_shr(mpz_ptr *out, mpz_ptr a, uint64_t bits) {
	*out = (mpz_ptr)malloc(sizeof(__mpz_struct));
	if (!*out) return;
	mpz_init(*out);
	mpz_tdiv_q_2exp(*out, a, static_cast<mp_bitcnt_t>(bits));
}

int bigint_backend_is_probably_prime(mpz_ptr a, int reps) {
	return mpz_probab_prime_p(a, reps);
}

void bigint_backend_factorial(mpz_ptr *out, uint64_t n) {
	*out = (mpz_ptr)malloc(sizeof(__mpz_struct));
	if (!*out) return;
	mpz_init(*out);
	mpz_fac_ui(*out, n);
}

void bigint_backend_next_prime(mpz_ptr *out, mpz_ptr a) {
	*out = (mpz_ptr)malloc(sizeof(__mpz_struct));
	if (!*out) return;
	mpz_init(*out);
	mpz_nextprime(*out, a);
}

#else

void bigint_backend_from_long(mpz_ptr *out, int64_t) { *out = nullptr; }
void bigint_backend_from_string(mpz_ptr *out, const char *, int) { *out = nullptr; }
void bigint_backend_init(mpz_ptr *out) { *out = nullptr; }
void bigint_backend_clear(mpz_ptr) { }
void bigint_backend_set(mpz_ptr, mpz_ptr) { }
void bigint_backend_set_long(mpz_ptr, int64_t) { }
void bigint_backend_set_string(mpz_ptr, const char *, int) { }
void bigint_backend_add(mpz_ptr *out, mpz_ptr, mpz_ptr) { *out = nullptr; }
void bigint_backend_sub(mpz_ptr *out, mpz_ptr, mpz_ptr) { *out = nullptr; }
void bigint_backend_mul(mpz_ptr *out, mpz_ptr, mpz_ptr) { *out = nullptr; }
void bigint_backend_mul_cpu(mpz_ptr *out, mpz_ptr, mpz_ptr) { *out = nullptr; }
void bigint_backend_mul_gmp(mpz_ptr *out, mpz_ptr, mpz_ptr) { *out = nullptr; }
void bigint_backend_div(mpz_ptr *out, mpz_ptr, mpz_ptr) { *out = nullptr; }
void bigint_backend_mod(mpz_ptr *out, mpz_ptr, mpz_ptr) { *out = nullptr; }
void bigint_backend_add_into(mpz_ptr, mpz_ptr, mpz_ptr) { }
void bigint_backend_mul_into(mpz_ptr, mpz_ptr, mpz_ptr) { }
void bigint_backend_div_into(mpz_ptr, mpz_ptr, mpz_ptr) { }
void bigint_backend_sqrt_into(mpz_ptr, mpz_ptr) { }
void bigint_backend_pow(mpz_ptr *out, mpz_ptr, uint64_t) { *out = nullptr; }
void bigint_backend_powm(mpz_ptr *out, mpz_ptr, mpz_ptr, mpz_ptr) { *out = nullptr; }
void bigint_backend_powm_gmp(mpz_ptr *out, mpz_ptr, mpz_ptr, mpz_ptr) { *out = nullptr; }
void bigint_backend_neg(mpz_ptr *out, mpz_ptr) { *out = nullptr; }
void bigint_backend_abs(mpz_ptr *out, mpz_ptr) { *out = nullptr; }
int  bigint_backend_cmp(mpz_ptr, mpz_ptr) { return 0; }
int  bigint_backend_sign(mpz_ptr) { return 0; }
int64_t bigint_backend_to_long(mpz_ptr) { return 0; }
double bigint_backend_to_double(mpz_ptr) { return 0.0; }

void bigint_backend_free_string(char *) { }
void bigint_backend_free(mpz_ptr) { }
void bigint_backend_gcd(mpz_ptr *out, mpz_ptr, mpz_ptr) { *out = nullptr; }
void bigint_backend_lcm(mpz_ptr *out, mpz_ptr, mpz_ptr) { *out = nullptr; }
void bigint_backend_sqrt(mpz_ptr *out, mpz_ptr) { *out = nullptr; }
void bigint_backend_and(mpz_ptr *out, mpz_ptr, mpz_ptr) { *out = nullptr; }
void bigint_backend_or(mpz_ptr *out, mpz_ptr, mpz_ptr) { *out = nullptr; }
void bigint_backend_xor(mpz_ptr *out, mpz_ptr, mpz_ptr) { *out = nullptr; }
void bigint_backend_shl(mpz_ptr *out, mpz_ptr, uint64_t) { *out = nullptr; }
void bigint_backend_shr(mpz_ptr *out, mpz_ptr, uint64_t) { *out = nullptr; }
int  bigint_backend_is_probably_prime(mpz_ptr, int) { return 0; }
void bigint_backend_factorial(mpz_ptr *out, uint64_t) { *out = nullptr; }
void bigint_backend_next_prime(mpz_ptr *out, mpz_ptr) { *out = nullptr; }

#endif /* BIGMATH_NO_GMP */
