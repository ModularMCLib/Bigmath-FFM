#ifndef BIGMATH_FFM_H
#define BIGMATH_FFM_H

#include "export.h"

#if __has_include(<gmp.h>) && __has_include(<mpfr.h>)
#include <gmp.h>
#include <mpfr.h>
#elif !defined(BIGMATH_MPZ_STUB_DEFINED)
#define BIGMATH_MPZ_STUB_DEFINED
#include <cstdint>
struct __bigmath_mpz { int _mp_alloc; int _mp_size; unsigned long *_mp_d; };
struct __bigmath_mpfr { unsigned long _mpfr_prec; unsigned long _mpfr_sign; unsigned long _mpfr_exp; unsigned long *_mpfr_d; };
typedef struct __bigmath_mpz mpz_t[1];
typedef struct __bigmath_mpfr mpfr_t[1];
typedef struct __bigmath_mpz *mpz_ptr;
typedef struct __bigmath_mpfr *mpfr_ptr;
#endif

extern "C" {

/* BigInt */
BIGMATH_EXPORT void  bigint_from_long(mpz_ptr *out, long val);
BIGMATH_EXPORT void  bigint_from_string(mpz_ptr *out, const char *str, int radix);
BIGMATH_EXPORT void  bigint_add(mpz_ptr *out, mpz_ptr a, mpz_ptr b);
BIGMATH_EXPORT void  bigint_sub(mpz_ptr *out, mpz_ptr a, mpz_ptr b);
BIGMATH_EXPORT void  bigint_mul(mpz_ptr *out, mpz_ptr a, mpz_ptr b);
BIGMATH_EXPORT void  bigint_div(mpz_ptr *out, mpz_ptr a, mpz_ptr b);
BIGMATH_EXPORT void  bigint_mod(mpz_ptr *out, mpz_ptr a, mpz_ptr b);
BIGMATH_EXPORT void  bigint_pow(mpz_ptr *out, mpz_ptr a, unsigned long exp);
BIGMATH_EXPORT void  bigint_neg(mpz_ptr *out, mpz_ptr a);
BIGMATH_EXPORT void  bigint_abs(mpz_ptr *out, mpz_ptr a);
BIGMATH_EXPORT int   bigint_cmp(mpz_ptr a, mpz_ptr b);
BIGMATH_EXPORT char *bigint_to_string(mpz_ptr a, int radix);
BIGMATH_EXPORT char *bigint_format(mpz_ptr a, int group_size, const char *group_sep);
BIGMATH_EXPORT void  bigint_free(mpz_ptr a);
BIGMATH_EXPORT void  bigint_free_string(char *s);

BIGMATH_EXPORT void  bigint_gcd(mpz_ptr *out, mpz_ptr a, mpz_ptr b);
BIGMATH_EXPORT void  bigint_lcm(mpz_ptr *out, mpz_ptr a, mpz_ptr b);
BIGMATH_EXPORT void  bigint_sqrt(mpz_ptr *out, mpz_ptr a);
BIGMATH_EXPORT void  bigint_and(mpz_ptr *out, mpz_ptr a, mpz_ptr b);
BIGMATH_EXPORT void  bigint_or(mpz_ptr *out, mpz_ptr a, mpz_ptr b);
BIGMATH_EXPORT void  bigint_xor(mpz_ptr *out, mpz_ptr a, mpz_ptr b);
BIGMATH_EXPORT void  bigint_shl(mpz_ptr *out, mpz_ptr a, unsigned long bits);
BIGMATH_EXPORT void  bigint_shr(mpz_ptr *out, mpz_ptr a, unsigned long bits);
BIGMATH_EXPORT int   bigint_is_probably_prime(mpz_ptr a, int reps);
BIGMATH_EXPORT void  bigint_factorial(mpz_ptr *out, unsigned long n);
BIGMATH_EXPORT void  bigint_next_prime(mpz_ptr *out, mpz_ptr a);
BIGMATH_EXPORT int   bigint_sign(mpz_ptr a);

/* BigDecimal */
BIGMATH_EXPORT void   bigdecimal_from_double(mpfr_ptr *out, double val, int precision);
BIGMATH_EXPORT void   bigdecimal_from_string(mpfr_ptr *out, const char *str, int precision);
BIGMATH_EXPORT void   bigdecimal_add(mpfr_ptr *out, mpfr_ptr a, mpfr_ptr b);
BIGMATH_EXPORT void   bigdecimal_sub(mpfr_ptr *out, mpfr_ptr a, mpfr_ptr b);
BIGMATH_EXPORT void   bigdecimal_mul(mpfr_ptr *out, mpfr_ptr a, mpfr_ptr b);
BIGMATH_EXPORT void   bigdecimal_div(mpfr_ptr *out, mpfr_ptr a, mpfr_ptr b);
BIGMATH_EXPORT void   bigdecimal_neg(mpfr_ptr *out, mpfr_ptr a);
BIGMATH_EXPORT void   bigdecimal_abs(mpfr_ptr *out, mpfr_ptr a);
BIGMATH_EXPORT int    bigdecimal_cmp(mpfr_ptr a, mpfr_ptr b);
BIGMATH_EXPORT double bigdecimal_to_double(mpfr_ptr a);
BIGMATH_EXPORT char  *bigdecimal_to_string(mpfr_ptr a);
BIGMATH_EXPORT char  *bigdecimal_format(mpfr_ptr a, int scale, int group_size, const char *group_sep);
BIGMATH_EXPORT void   bigdecimal_free(mpfr_ptr a);
BIGMATH_EXPORT void   bigdecimal_free_string(char *s);

BIGMATH_EXPORT void   bigdecimal_sqrt(mpfr_ptr *out, mpfr_ptr a);
BIGMATH_EXPORT void   bigdecimal_pow(mpfr_ptr *out, mpfr_ptr a, mpfr_ptr b);
BIGMATH_EXPORT void   bigdecimal_log(mpfr_ptr *out, mpfr_ptr a);
BIGMATH_EXPORT void   bigdecimal_exp(mpfr_ptr *out, mpfr_ptr a);
BIGMATH_EXPORT void   bigdecimal_sin(mpfr_ptr *out, mpfr_ptr a);
BIGMATH_EXPORT void   bigdecimal_cos(mpfr_ptr *out, mpfr_ptr a);
BIGMATH_EXPORT void   bigdecimal_tan(mpfr_ptr *out, mpfr_ptr a);
BIGMATH_EXPORT void   bigdecimal_ceil(mpfr_ptr *out, mpfr_ptr a);
BIGMATH_EXPORT void   bigdecimal_floor(mpfr_ptr *out, mpfr_ptr a);
BIGMATH_EXPORT void   bigdecimal_round(mpfr_ptr *out, mpfr_ptr a);
BIGMATH_EXPORT void   bigdecimal_atan(mpfr_ptr *out, mpfr_ptr a);
BIGMATH_EXPORT void   bigdecimal_asin(mpfr_ptr *out, mpfr_ptr a);
BIGMATH_EXPORT void   bigdecimal_acos(mpfr_ptr *out, mpfr_ptr a);
BIGMATH_EXPORT void   bigdecimal_sinh(mpfr_ptr *out, mpfr_ptr a);
BIGMATH_EXPORT void   bigdecimal_cosh(mpfr_ptr *out, mpfr_ptr a);
BIGMATH_EXPORT void   bigdecimal_tanh(mpfr_ptr *out, mpfr_ptr a);

}

#endif /* BIGMATH_FFM_H */
