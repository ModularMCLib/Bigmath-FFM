#ifndef BIGMATH_INT128_H
#define BIGMATH_INT128_H

#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <climits>

#ifdef _WIN32
#define EXPORT __declspec(dllexport)
#else
#define EXPORT __attribute__((visibility("default")))
#endif

struct int128_box {
	int64_t lo;
	int64_t hi;
};

#ifdef __SIZEOF_INT128__
#define HAVE_INT128 1
#endif

extern "C" {

EXPORT void int128_from_i64(int128_box *out, int64_t val);
EXPORT void int128_from_u64(int128_box *out, uint64_t val);
EXPORT void int128_from_string(int128_box *out, const char *str, int radix);
EXPORT void int128_add(int128_box *out, const int128_box *a, const int128_box *b);
EXPORT void int128_sub(int128_box *out, const int128_box *a, const int128_box *b);
EXPORT void int128_mul(int128_box *out, const int128_box *a, const int128_box *b);
EXPORT void int128_div(int128_box *out, const int128_box *a, const int128_box *b);
EXPORT void int128_mod(int128_box *out, const int128_box *a, const int128_box *b);
EXPORT void int128_neg(int128_box *out, const int128_box *a);
EXPORT void int128_abs(int128_box *out, const int128_box *a);
EXPORT int  int128_cmp(const int128_box *a, const int128_box *b);
EXPORT int  int128_sign(const int128_box *a);
EXPORT char *int128_to_string(const int128_box *a, int radix);
EXPORT char *int128_format(const int128_box *a, int group_size, const char *group_sep);
EXPORT void int128_free_string(char *s);

}

#endif /* BIGMATH_INT128_H */
