#ifndef BIGMATH_FFM_H
#define BIGMATH_FFM_H

#include <gmp.h>
#include <mpfr.h>

#ifdef _WIN32
#define EXPORT __declspec(dllexport)
#else
#define EXPORT __attribute__((visibility("default")))
#endif

extern "C" {

/* BigInt */
EXPORT void bigint_from_long(mpz_t *out, long val);
EXPORT void bigint_from_string(mpz_t *out, const char *str, int radix);
EXPORT void bigint_add(mpz_t *out, const mpz_t a, const mpz_t b);
EXPORT void bigint_sub(mpz_t *out, const mpz_t a, const mpz_t b);
EXPORT void bigint_mul(mpz_t *out, const mpz_t a, const mpz_t b);
EXPORT void bigint_div(mpz_t *out, const mpz_t a, const mpz_t b);
EXPORT void bigint_mod(mpz_t *out, const mpz_t a, const mpz_t b);
EXPORT void bigint_pow(mpz_t *out, const mpz_t a, unsigned long exp);
EXPORT void bigint_neg(mpz_t *out, const mpz_t a);
EXPORT void bigint_abs(mpz_t *out, const mpz_t a);
EXPORT int  bigint_cmp(const mpz_t a, const mpz_t b);
EXPORT char *bigint_to_string(const mpz_t a, int radix);
EXPORT char *bigint_format(const mpz_t a, int group_size, const char *group_sep);
EXPORT void bigint_free(mpz_t a);
EXPORT void bigint_free_string(char *s);

/* BigDecimal */
EXPORT void bigdecimal_from_double(mpfr_t *out, double val, int precision);
EXPORT void bigdecimal_from_string(mpfr_t *out, const char *str, int precision);
EXPORT void bigdecimal_add(mpfr_t *out, const mpfr_t a, const mpfr_t b);
EXPORT void bigdecimal_sub(mpfr_t *out, const mpfr_t a, const mpfr_t b);
EXPORT void bigdecimal_mul(mpfr_t *out, const mpfr_t a, const mpfr_t b);
EXPORT void bigdecimal_div(mpfr_t *out, const mpfr_t a, const mpfr_t b);
EXPORT void bigdecimal_neg(mpfr_t *out, const mpfr_t a);
EXPORT int  bigdecimal_cmp(const mpfr_t a, const mpfr_t b);
EXPORT double bigdecimal_to_double(const mpfr_t a);
EXPORT char *bigdecimal_to_string(const mpfr_t a);
EXPORT char *bigdecimal_format(const mpfr_t a, int scale, int group_size, const char *group_sep);
EXPORT void bigdecimal_free(mpfr_t a);
EXPORT void bigdecimal_free_string(char *s);

}

#endif /* BIGMATH_FFM_H */
