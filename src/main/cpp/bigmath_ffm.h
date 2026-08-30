#ifndef BIGMATH_FFM_H
#define BIGMATH_FFM_H

#include "export.h"
#include <cstdint>

#if __has_include(<gmp.h>) && __has_include(<mpfr.h>)
#include <gmp.h>
#include <mpfr.h>
#elif !defined(BIGMATH_MPZ_STUB_DEFINED)
#define BIGMATH_MPZ_STUB_DEFINED
struct __bigmath_mpz { int _mp_alloc; int _mp_size; unsigned long *_mp_d; };
struct __bigmath_mpfr { unsigned long _mpfr_prec; unsigned long _mpfr_sign; unsigned long _mpfr_exp; unsigned long *_mpfr_d; };
typedef struct __bigmath_mpz mpz_t[1];
typedef struct __bigmath_mpfr mpfr_t[1];
typedef struct __bigmath_mpz *mpz_ptr;
typedef struct __bigmath_mpfr *mpfr_ptr;
#endif

struct BigIntHandle;
struct BigDeciHandle;

extern "C" {

BIGMATH_EXPORT uint32_t    bigmath_abi_version();
BIGMATH_EXPORT const char *bigmath_build_id();
BIGMATH_EXPORT uint64_t    bigmath_capabilities();

BIGMATH_EXPORT BigIntHandle *bigint_from_long(int64_t val);
BIGMATH_EXPORT BigIntHandle *bigint_from_string(const char *str, int radix);
BIGMATH_EXPORT BigIntHandle *bigint_from_twos_complement(const uint8_t *bytes, uint64_t size);
BIGMATH_EXPORT BigIntHandle *bigint_constant(int64_t val);
BIGMATH_EXPORT BigIntHandle *bigint_copy(BigIntHandle *value);
BIGMATH_EXPORT int bigint_set(BigIntHandle *out, BigIntHandle *value);
BIGMATH_EXPORT int bigint_set_long(BigIntHandle *out, int64_t val);
BIGMATH_EXPORT int bigint_set_string(BigIntHandle *out, const char *str, int radix);
BIGMATH_EXPORT BigIntHandle *bigint_add(BigIntHandle *a, BigIntHandle *b);
BIGMATH_EXPORT BigIntHandle *bigint_sub(BigIntHandle *a, BigIntHandle *b);
BIGMATH_EXPORT BigIntHandle *bigint_mul(BigIntHandle *a, BigIntHandle *b);
BIGMATH_EXPORT BigIntHandle *bigint_mul_cpu(BigIntHandle *a, BigIntHandle *b);
BIGMATH_EXPORT BigIntHandle *bigint_mul_gmp(BigIntHandle *a, BigIntHandle *b);
BIGMATH_EXPORT BigIntHandle *bigint_div(BigIntHandle *a, BigIntHandle *b);
BIGMATH_EXPORT BigIntHandle *bigint_mod(BigIntHandle *a, BigIntHandle *b);
BIGMATH_EXPORT BigIntHandle *bigint_pow(BigIntHandle *a, uint64_t exp);
BIGMATH_EXPORT BigIntHandle *bigint_powm(BigIntHandle *base, BigIntHandle *exp, BigIntHandle *mod);
BIGMATH_EXPORT BigIntHandle *bigint_powm_gmp(BigIntHandle *base, BigIntHandle *exp, BigIntHandle *mod);
BIGMATH_EXPORT BigIntHandle *bigint_neg(BigIntHandle *a);
BIGMATH_EXPORT BigIntHandle *bigint_abs(BigIntHandle *a);
BIGMATH_EXPORT BigIntHandle *bigint_gcd(BigIntHandle *a, BigIntHandle *b);
BIGMATH_EXPORT BigIntHandle *bigint_lcm(BigIntHandle *a, BigIntHandle *b);
BIGMATH_EXPORT BigIntHandle *bigint_sqrt(BigIntHandle *a);
BIGMATH_EXPORT BigIntHandle *bigint_and(BigIntHandle *a, BigIntHandle *b);
BIGMATH_EXPORT BigIntHandle *bigint_or(BigIntHandle *a, BigIntHandle *b);
BIGMATH_EXPORT BigIntHandle *bigint_xor(BigIntHandle *a, BigIntHandle *b);
BIGMATH_EXPORT BigIntHandle *bigint_shl(BigIntHandle *a, uint64_t bits);
BIGMATH_EXPORT BigIntHandle *bigint_shr(BigIntHandle *a, uint64_t bits);
BIGMATH_EXPORT BigIntHandle *bigint_factorial(uint64_t n);
BIGMATH_EXPORT BigIntHandle *bigint_next_prime(BigIntHandle *a);
BIGMATH_EXPORT int bigint_add_into(BigIntHandle *out, BigIntHandle *a, BigIntHandle *b);
BIGMATH_EXPORT int bigint_mul_into(BigIntHandle *out, BigIntHandle *a, BigIntHandle *b);
BIGMATH_EXPORT int bigint_div_into(BigIntHandle *out, BigIntHandle *a, BigIntHandle *b);
BIGMATH_EXPORT int bigint_sqrt_into(BigIntHandle *out, BigIntHandle *a);
BIGMATH_EXPORT int bigint_cmp(BigIntHandle *a, BigIntHandle *b);
BIGMATH_EXPORT int bigint_sign(BigIntHandle *a);
BIGMATH_EXPORT int bigint_is_probably_prime(BigIntHandle *a, int reps);
BIGMATH_EXPORT int64_t bigint_to_long(BigIntHandle *a);
BIGMATH_EXPORT double bigint_to_double(BigIntHandle *a);
BIGMATH_EXPORT char *bigint_to_string(BigIntHandle *a, int radix);
BIGMATH_EXPORT char *bigint_format(BigIntHandle *a, int group_size, const char *group_sep);
BIGMATH_EXPORT uint64_t bigint_twos_complement_size(BigIntHandle *a);
BIGMATH_EXPORT int bigint_to_twos_complement(BigIntHandle *a, uint8_t *out, uint64_t size);
BIGMATH_EXPORT uint64_t bigint_id(BigIntHandle *a);
BIGMATH_EXPORT uint64_t bigint_version(BigIntHandle *a);
BIGMATH_EXPORT void bigint_free(BigIntHandle *a);
BIGMATH_EXPORT void bigint_free_string(char *value);

BIGMATH_EXPORT BigDeciHandle *bigdecimal_from_double(double val, int precision);
BIGMATH_EXPORT BigDeciHandle *bigdecimal_from_string(const char *str, int precision);
BIGMATH_EXPORT BigDeciHandle *bigdecimal_from_bigint(BigIntHandle *val, int precision);
BIGMATH_EXPORT BigDeciHandle *bigdecimal_constant(double val, int precision);
BIGMATH_EXPORT BigDeciHandle *bigdecimal_copy(BigDeciHandle *value);
BIGMATH_EXPORT int bigdecimal_set(BigDeciHandle *out, BigDeciHandle *value);
BIGMATH_EXPORT int bigdecimal_set_double(BigDeciHandle *out, double val);
BIGMATH_EXPORT int bigdecimal_set_string(BigDeciHandle *out, const char *str, int precision);
BIGMATH_EXPORT BigDeciHandle *bigdecimal_add(BigDeciHandle *a, BigDeciHandle *b);
BIGMATH_EXPORT BigDeciHandle *bigdecimal_sub(BigDeciHandle *a, BigDeciHandle *b);
BIGMATH_EXPORT BigDeciHandle *bigdecimal_mul(BigDeciHandle *a, BigDeciHandle *b);
BIGMATH_EXPORT BigDeciHandle *bigdecimal_mul_mpfr(BigDeciHandle *a, BigDeciHandle *b);
BIGMATH_EXPORT BigDeciHandle *bigdecimal_div(BigDeciHandle *a, BigDeciHandle *b);
BIGMATH_EXPORT BigDeciHandle *bigdecimal_neg(BigDeciHandle *a);
BIGMATH_EXPORT BigDeciHandle *bigdecimal_abs(BigDeciHandle *a);
BIGMATH_EXPORT BigDeciHandle *bigdecimal_sqrt(BigDeciHandle *a);
BIGMATH_EXPORT BigDeciHandle *bigdecimal_pow(BigDeciHandle *a, BigDeciHandle *b);
BIGMATH_EXPORT BigDeciHandle *bigdecimal_pow_long(BigDeciHandle *a, int64_t exp);
BIGMATH_EXPORT BigDeciHandle *bigdecimal_log(BigDeciHandle *a);
BIGMATH_EXPORT BigDeciHandle *bigdecimal_exp(BigDeciHandle *a);
BIGMATH_EXPORT BigDeciHandle *bigdecimal_sin(BigDeciHandle *a);
BIGMATH_EXPORT BigDeciHandle *bigdecimal_cos(BigDeciHandle *a);
BIGMATH_EXPORT BigDeciHandle *bigdecimal_tan(BigDeciHandle *a);
BIGMATH_EXPORT BigDeciHandle *bigdecimal_ceil(BigDeciHandle *a);
BIGMATH_EXPORT BigDeciHandle *bigdecimal_floor(BigDeciHandle *a);
BIGMATH_EXPORT BigDeciHandle *bigdecimal_round(BigDeciHandle *a);
BIGMATH_EXPORT int bigdecimal_add_into(BigDeciHandle *out, BigDeciHandle *a, BigDeciHandle *b);
BIGMATH_EXPORT int bigdecimal_mul_into(BigDeciHandle *out, BigDeciHandle *a, BigDeciHandle *b);
BIGMATH_EXPORT int bigdecimal_div_into(BigDeciHandle *out, BigDeciHandle *a, BigDeciHandle *b);
BIGMATH_EXPORT int bigdecimal_sqrt_into(BigDeciHandle *out, BigDeciHandle *a);
BIGMATH_EXPORT int bigdecimal_cmp(BigDeciHandle *a, BigDeciHandle *b);
BIGMATH_EXPORT double bigdecimal_to_double(BigDeciHandle *a);
BIGMATH_EXPORT char *bigdecimal_to_string(BigDeciHandle *a);
BIGMATH_EXPORT char *bigdecimal_format(BigDeciHandle *a, int scale, int group_size, const char *group_sep);
BIGMATH_EXPORT uint64_t bigdecimal_id(BigDeciHandle *a);
BIGMATH_EXPORT uint64_t bigdecimal_version(BigDeciHandle *a);
BIGMATH_EXPORT void bigdecimal_free(BigDeciHandle *a);
BIGMATH_EXPORT void bigdecimal_free_string(char *value);

BIGMATH_EXPORT int         bigmath_cuda_available();
BIGMATH_EXPORT int         bigmath_cuda_device_count();
BIGMATH_EXPORT int         bigmath_cuda_probe_count();
BIGMATH_EXPORT int         bigmath_cuda_multiply_count();
BIGMATH_EXPORT const char *bigmath_cuda_device_name();
BIGMATH_EXPORT const char *bigmath_cuda_status_message();

}

#endif
