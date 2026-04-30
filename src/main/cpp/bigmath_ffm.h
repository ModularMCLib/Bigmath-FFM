#ifndef BIGMATH_FFM_H
#define BIGMATH_FFM_H

#include "export.h"

#if __has_include(<gmp.h>)
#include <gmp.h>
#include <mpfr.h>
#else
#include <cstdint>
struct __bigmath_mpz { int _mp_alloc; int _mp_size; unsigned long *_mp_d; };
struct __bigmath_mpfr { unsigned long _mpfr_prec; unsigned long _mpfr_sign; unsigned long _mpfr_exp; unsigned long *_mpfr_d; };
typedef struct __bigmath_mpz mpz_t[1];
typedef struct __bigmath_mpfr mpfr_t[1];
#endif

extern "C" {

BIGMATH_EXPORT void bigint_from_long(mpz_t *out, long val);
BIGMATH_EXPORT void bigint_from_string(mpz_t *out, const char *str, int radix);
BIGMATH_EXPORT void bigint_add(mpz_t *out, const mpz_t a, const mpz_t b);
BIGMATH_EXPORT void bigint_sub(mpz_t *out, const mpz_t a, const mpz_t b);
BIGMATH_EXPORT void bigint_mul(mpz_t *out, const mpz_t a, const mpz_t b);
BIGMATH_EXPORT void bigint_div(mpz_t *out, const mpz_t a, const mpz_t b);
BIGMATH_EXPORT void bigint_mod(mpz_t *out, const mpz_t a, const mpz_t b);
BIGMATH_EXPORT void bigint_pow(mpz_t *out, const mpz_t a, unsigned long exp);
BIGMATH_EXPORT void bigint_neg(mpz_t *out, const mpz_t a);
BIGMATH_EXPORT void bigint_abs(mpz_t *out, const mpz_t a);
BIGMATH_EXPORT int  bigint_cmp(const mpz_t a, const mpz_t b);
BIGMATH_EXPORT char *bigint_to_string(const mpz_t a, int radix);
BIGMATH_EXPORT char *bigint_format(const mpz_t a, int group_size, const char *group_sep);
BIGMATH_EXPORT void bigint_free(mpz_t a);
BIGMATH_EXPORT void bigint_free_string(char *s);

BIGMATH_EXPORT void bigdecimal_from_double(mpfr_t *out, double val, int precision);
BIGMATH_EXPORT void bigdecimal_from_string(mpfr_t *out, const char *str, int precision);
BIGMATH_EXPORT void bigdecimal_add(mpfr_t *out, const mpfr_t a, const mpfr_t b);
BIGMATH_EXPORT void bigdecimal_sub(mpfr_t *out, const mpfr_t a, const mpfr_t b);
BIGMATH_EXPORT void bigdecimal_mul(mpfr_t *out, const mpfr_t a, const mpfr_t b);
BIGMATH_EXPORT void bigdecimal_div(mpfr_t *out, const mpfr_t a, const mpfr_t b);
BIGMATH_EXPORT void bigdecimal_neg(mpfr_t *out, const mpfr_t a);
BIGMATH_EXPORT int  bigdecimal_cmp(const mpfr_t a, const mpfr_t b);
BIGMATH_EXPORT double bigdecimal_to_double(const mpfr_t a);
BIGMATH_EXPORT char *bigdecimal_to_string(const mpfr_t a);
BIGMATH_EXPORT char *bigdecimal_format(const mpfr_t a, int scale, int group_size, const char *group_sep);
BIGMATH_EXPORT void bigdecimal_free(mpfr_t a);
BIGMATH_EXPORT void bigdecimal_free_string(char *s);

}

#endif /* BIGMATH_FFM_H */
