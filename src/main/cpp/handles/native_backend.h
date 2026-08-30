#ifndef BIGMATH_NATIVE_BACKEND_H
#define BIGMATH_NATIVE_BACKEND_H

#include "../bigmath_ffm.h"

extern "C" {

void bigint_backend_from_long(mpz_ptr *out, int64_t val);
void bigint_backend_from_string(mpz_ptr *out, const char *str, int radix);
void bigint_backend_init(mpz_ptr *out);
void bigint_backend_clear(mpz_ptr a);
void bigint_backend_set(mpz_ptr out, mpz_ptr a);
void bigint_backend_set_long(mpz_ptr out, int64_t val);
void bigint_backend_set_string(mpz_ptr out, const char *str, int radix);
void bigint_backend_add(mpz_ptr *out, mpz_ptr a, mpz_ptr b);
void bigint_backend_sub(mpz_ptr *out, mpz_ptr a, mpz_ptr b);
void bigint_backend_mul(mpz_ptr *out, mpz_ptr a, mpz_ptr b);
void bigint_backend_mul_cpu(mpz_ptr *out, mpz_ptr a, mpz_ptr b);
void bigint_backend_mul_gmp(mpz_ptr *out, mpz_ptr a, mpz_ptr b);
void bigint_backend_div(mpz_ptr *out, mpz_ptr a, mpz_ptr b);
void bigint_backend_mod(mpz_ptr *out, mpz_ptr a, mpz_ptr b);
void bigint_backend_add_into(mpz_ptr out, mpz_ptr a, mpz_ptr b);
void bigint_backend_mul_into(mpz_ptr out, mpz_ptr a, mpz_ptr b);
void bigint_backend_div_into(mpz_ptr out, mpz_ptr a, mpz_ptr b);
void bigint_backend_sqrt_into(mpz_ptr out, mpz_ptr a);
void bigint_backend_pow(mpz_ptr *out, mpz_ptr a, uint64_t exp);
void bigint_backend_powm(mpz_ptr *out, mpz_ptr base, mpz_ptr exp, mpz_ptr mod);
void bigint_backend_powm_gmp(mpz_ptr *out, mpz_ptr base, mpz_ptr exp, mpz_ptr mod);
void bigint_backend_neg(mpz_ptr *out, mpz_ptr a);
void bigint_backend_abs(mpz_ptr *out, mpz_ptr a);
int bigint_backend_cmp(mpz_ptr a, mpz_ptr b);
char *bigint_backend_to_string(mpz_ptr a, int radix);
char *bigint_backend_format(mpz_ptr a, int group_size, const char *group_sep);
void bigint_backend_free(mpz_ptr a);
void bigint_backend_free_string(char *s);
void bigint_backend_gcd(mpz_ptr *out, mpz_ptr a, mpz_ptr b);
void bigint_backend_lcm(mpz_ptr *out, mpz_ptr a, mpz_ptr b);
void bigint_backend_sqrt(mpz_ptr *out, mpz_ptr a);
void bigint_backend_and(mpz_ptr *out, mpz_ptr a, mpz_ptr b);
void bigint_backend_or(mpz_ptr *out, mpz_ptr a, mpz_ptr b);
void bigint_backend_xor(mpz_ptr *out, mpz_ptr a, mpz_ptr b);
void bigint_backend_shl(mpz_ptr *out, mpz_ptr a, uint64_t bits);
void bigint_backend_shr(mpz_ptr *out, mpz_ptr a, uint64_t bits);
int bigint_backend_is_probably_prime(mpz_ptr a, int reps);
void bigint_backend_factorial(mpz_ptr *out, uint64_t n);
void bigint_backend_next_prime(mpz_ptr *out, mpz_ptr a);
int bigint_backend_sign(mpz_ptr a);
int64_t bigint_backend_to_long(mpz_ptr a);
double bigint_backend_to_double(mpz_ptr a);

void bigdecimal_backend_from_double(mpfr_ptr *out, double val, int precision);
void bigdecimal_backend_from_string(mpfr_ptr *out, const char *str, int precision);
void bigdecimal_backend_from_bigint(mpfr_ptr *out, mpz_ptr val, int precision);
void bigdecimal_backend_init(mpfr_ptr *out, int precision);
void bigdecimal_backend_clear(mpfr_ptr a);
void bigdecimal_backend_set(mpfr_ptr out, mpfr_ptr a);
void bigdecimal_backend_set_double(mpfr_ptr out, double val);
void bigdecimal_backend_set_string(mpfr_ptr out, const char *str, int precision);
void bigdecimal_backend_add(mpfr_ptr *out, mpfr_ptr a, mpfr_ptr b);
void bigdecimal_backend_sub(mpfr_ptr *out, mpfr_ptr a, mpfr_ptr b);
void bigdecimal_backend_mul(mpfr_ptr *out, mpfr_ptr a, mpfr_ptr b);
void bigdecimal_backend_mul_mpfr(mpfr_ptr *out, mpfr_ptr a, mpfr_ptr b);
void bigdecimal_backend_div(mpfr_ptr *out, mpfr_ptr a, mpfr_ptr b);
void bigdecimal_backend_add_into(mpfr_ptr out, mpfr_ptr a, mpfr_ptr b);
void bigdecimal_backend_mul_into(mpfr_ptr out, mpfr_ptr a, mpfr_ptr b);
void bigdecimal_backend_div_into(mpfr_ptr out, mpfr_ptr a, mpfr_ptr b);
void bigdecimal_backend_sqrt_into(mpfr_ptr out, mpfr_ptr a);
void bigdecimal_backend_neg(mpfr_ptr *out, mpfr_ptr a);
void bigdecimal_backend_abs(mpfr_ptr *out, mpfr_ptr a);
int bigdecimal_backend_cmp(mpfr_ptr a, mpfr_ptr b);
double bigdecimal_backend_to_double(mpfr_ptr a);
char *bigdecimal_backend_to_string(mpfr_ptr a);
char *bigdecimal_backend_format(mpfr_ptr a, int scale, int group_size, const char *group_sep);
void bigdecimal_backend_free(mpfr_ptr a);
void bigdecimal_backend_free_string(char *s);
void bigdecimal_backend_sqrt(mpfr_ptr *out, mpfr_ptr a);
void bigdecimal_backend_pow(mpfr_ptr *out, mpfr_ptr a, mpfr_ptr b);
void bigdecimal_backend_pow_long(mpfr_ptr *out, mpfr_ptr a, int64_t exp);
void bigdecimal_backend_log(mpfr_ptr *out, mpfr_ptr a);
void bigdecimal_backend_exp(mpfr_ptr *out, mpfr_ptr a);
void bigdecimal_backend_sin(mpfr_ptr *out, mpfr_ptr a);
void bigdecimal_backend_cos(mpfr_ptr *out, mpfr_ptr a);
void bigdecimal_backend_tan(mpfr_ptr *out, mpfr_ptr a);
void bigdecimal_backend_ceil(mpfr_ptr *out, mpfr_ptr a);
void bigdecimal_backend_floor(mpfr_ptr *out, mpfr_ptr a);
void bigdecimal_backend_round(mpfr_ptr *out, mpfr_ptr a);
void bigdecimal_backend_atan(mpfr_ptr *out, mpfr_ptr a);
void bigdecimal_backend_asin(mpfr_ptr *out, mpfr_ptr a);
void bigdecimal_backend_acos(mpfr_ptr *out, mpfr_ptr a);
void bigdecimal_backend_sinh(mpfr_ptr *out, mpfr_ptr a);
void bigdecimal_backend_cosh(mpfr_ptr *out, mpfr_ptr a);
void bigdecimal_backend_tanh(mpfr_ptr *out, mpfr_ptr a);

}

#endif
