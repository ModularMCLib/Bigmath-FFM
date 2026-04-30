#include "int128.h"

#ifdef HAVE_INT128

static __int128 to_i128(const int128_box *a) {
	return (__int128)a->hi << 64 | (uint64_t)a->lo;
}

static void from_i128(int128_box *out, __int128 v) {
	out->lo = (int64_t)v;
	out->hi = (int64_t)(v >> 64);
}

void int128_from_i64(int128_box *out, int64_t val) {
	out->lo = val;
	out->hi = (val < 0) ? -1 : 0;
}

void int128_from_u64(int128_box *out, uint64_t val) {
	out->lo = (int64_t)val;
	out->hi = 0;
}

void int128_from_string(int128_box *out, const char *str, int radix) {
	__int128 v = 0;
	bool neg = false;
	const char *p = str;
	if (*p == '-') { neg = true; p++; }
	while (*p) {
		char c = *p++;
		int d;
		if (c >= '0' && c <= '9') d = c - '0';
		else if (c >= 'a' && c <= 'f') d = c - 'a' + 10;
		else if (c >= 'A' && c <= 'F') d = c - 'A' + 10;
		else break;
		v = v * radix + d;
	}
	from_i128(out, neg ? -v : v);
}

void int128_add(int128_box *out, const int128_box *a, const int128_box *b) {
	from_i128(out, to_i128(a) + to_i128(b));
}

void int128_sub(int128_box *out, const int128_box *a, const int128_box *b) {
	from_i128(out, to_i128(a) - to_i128(b));
}

void int128_mul(int128_box *out, const int128_box *a, const int128_box *b) {
	from_i128(out, to_i128(a) * to_i128(b));
}

void int128_div(int128_box *out, const int128_box *a, const int128_box *b) {
	from_i128(out, to_i128(a) / to_i128(b));
}

void int128_mod(int128_box *out, const int128_box *a, const int128_box *b) {
	from_i128(out, to_i128(a) % to_i128(b));
}

void int128_neg(int128_box *out, const int128_box *a) {
	from_i128(out, -to_i128(a));
}

void int128_abs(int128_box *out, const int128_box *a) {
	__int128 v = to_i128(a);
	from_i128(out, v < 0 ? -v : v);
}

int int128_cmp(const int128_box *a, const int128_box *b) {
	__int128 va = to_i128(a);
	__int128 vb = to_i128(b);
	if (va < vb) return -1;
	if (va > vb) return 1;
	return 0;
}

int int128_sign(const int128_box *a) {
	__int128 v = to_i128(a);
	if (v < 0) return -1;
	if (v > 0) return 1;
	return 0;
}

char *int128_to_string(const int128_box *a, int radix) {
	static const char digits[] = "0123456789abcdef";
	__int128 v = to_i128(a);
	if (v == 0) {
		char *s = (char *)malloc(2);
		s[0] = '0'; s[1] = '\0';
		return s;
	}
	bool neg = (v < 0);
	if (neg) v = -v;
	char buf[256];
	int pos = 255;
	buf[pos] = '\0';
	while (v > 0) {
		buf[--pos] = digits[(int)(v % radix)];
		v /= radix;
	}
	if (neg) buf[--pos] = '-';
	char *s = (char *)malloc(255 - pos + 1);
	if (s) strcpy(s, &buf[pos]);
	return s;
}

#else
// --- Portable fallback for MSVC ---

static bool is_zero(const int128_box *a) {
	return a->lo == 0 && a->hi == 0;
}

static bool is_neg(const int128_box *a) {
	return a->hi < 0;
}

static void u128_add(uint64_t *lo, uint64_t *hi, uint64_t a_lo, uint64_t a_hi, uint64_t b_lo, uint64_t b_hi) {
	*lo = a_lo + b_lo;
	*hi = a_hi + b_hi + (*lo < a_lo ? 1ULL : 0ULL);
}

static void neg_abs(int128_box *out, const int128_box *a) {
	// negate (two's complement)
	out->lo = ~a->lo + 1;
	out->hi = ~a->hi + (out->lo == 0 ? 1 : 0);
}

static void abs_value(int128_box *out, const int128_box *a) {
	if (is_neg(a)) {
		neg_abs(out, a);
	} else {
		out->lo = a->lo;
		out->hi = a->hi;
	}
}

void int128_from_i64(int128_box *out, int64_t val) {
	out->lo = val;
	out->hi = (val < 0) ? -1 : 0;
}

void int128_from_u64(int128_box *out, uint64_t val) {
	out->lo = (int64_t)val;
	out->hi = 0;
}

void int128_from_string(int128_box *out, const char *str, int radix) {
	out->lo = 0;
	out->hi = 0;
	bool neg = false;
	const char *p = str;
	if (*p == '-') { neg = true; p++; }
	while (*p) {
		char c = *p++;
		int d;
		if (c >= '0' && c <= '9') d = c - '0';
		else if (c >= 'a' && c <= 'f') d = c - 'a' + 10;
		else if (c >= 'A' && c <= 'F') d = c - 'A' + 10;
		else break;
		// multiply out by radix then add d
		uint64_t old_lo = (uint64_t)out->lo;
		uint64_t old_hi = (uint64_t)out->hi;
		// out *= radix
		uint64_t r = (uint64_t)radix;
		uint64_t lo0 = old_lo * r;
		uint64_t hi0 = old_hi * r + (uint64_t)((__uint128_t)old_lo * r >> 64);
		// out += d
		uint64_t lo1 = lo0 + (uint64_t)d;
		uint64_t hi1 = hi0 + (lo1 < lo0 ? 1ULL : 0ULL);
		out->lo = (int64_t)lo1;
		out->hi = (int64_t)hi1;
	}
	if (neg) {
		neg_abs(out, out);
	}
}

void int128_add(int128_box *out, const int128_box *a, const int128_box *b) {
	u128_add((uint64_t*)&out->lo, (uint64_t*)&out->hi,
		(uint64_t)a->lo, (uint64_t)a->hi,
		(uint64_t)b->lo, (uint64_t)b->hi);
}

void int128_sub(int128_box *out, const int128_box *a, const int128_box *b) {
	int128_box neg_b;
	neg_abs(&neg_b, b);
	int128_add(out, a, &neg_b);
}

void int128_mul(int128_box *out, const int128_box *a, const int128_box *b) {
	// signed multiplication using absolute values
	int128_box aa, bb;
	abs_value(&aa, a);
	abs_value(&bb, b);
	bool result_neg = (is_neg(a) != is_neg(b));

	uint64_t a_lo = (uint64_t)aa.lo, a_hi = (uint64_t)aa.hi;
	uint64_t b_lo = (uint64_t)bb.lo, b_hi = (uint64_t)bb.hi;

	// 64x64 -> 128 partial products
	uint64_t p00_lo, p00_hi;
	uint64_t p01_lo, p01_hi;
	uint64_t p10_lo, p10_hi;

	// p00 = a_lo * b_lo (128-bit)
	__uint128_t p00 = (__uint128_t)a_lo * b_lo;
	p00_lo = (uint64_t)p00;
	p00_hi = (uint64_t)(p00 >> 64);

	// p01 = a_lo * b_hi (128-bit)
	__uint128_t p01 = (__uint128_t)a_lo * b_hi;
	p01_lo = (uint64_t)p01;
	p01_hi = (uint64_t)(p01 >> 64);

	// p10 = a_hi * b_lo (128-bit)
	__uint128_t p10 = (__uint128_t)a_hi * b_lo;
	p10_lo = (uint64_t)p10;
	p10_hi = (uint64_t)(p10 >> 64);

	// sum with proper carries
	uint64_t r_lo = p00_lo;
	uint64_t r_hi = p00_hi + p01_lo + p10_lo;
	// handle carry from low addition: p00_hi + p01_lo may overflow
	uint64_t carry = 0;
	uint64_t tmp = p00_hi + p01_lo;
	if (tmp < p00_hi) carry++;
	r_hi = tmp + p10_lo;
	if (r_hi < tmp) carry++;
	// carry could be 0, 1, or 2
	r_hi += carry << 63; // carry goes to high bit

	out->lo = (int64_t)r_lo;
	out->hi = (int64_t)r_hi;

	if (result_neg) {
		neg_abs(out, out);
	}
}

void int128_div(int128_box *out, const int128_box *a, const int128_box *b) {
	// Simple schoolbook division
	int128_box aa, bb;
	abs_value(&aa, a);
	abs_value(&bb, b);
	bool result_neg = (is_neg(a) != is_neg(b));

	if (is_zero(&bb)) {
		out->lo = 0; out->hi = 0;
		return;
	}

	// Compare a and b as unsigned
	int128_box remainder = aa;
	int128_box quotient;
	quotient.lo = 0; quotient.hi = 0;

	// Use bit-by-bit algorithm
	for (int i = 127; i >= 0; i--) {
		// shift remainder left by 1
		uint64_t r_lo = (uint64_t)remainder.lo;
		uint64_t r_hi = (uint64_t)remainder.hi;
		uint64_t new_lo = r_lo << 1;
		uint64_t new_hi = (r_hi << 1) | (r_lo >> 63);
		remainder.lo = (int64_t)new_lo;
		remainder.hi = (int64_t)new_hi;

		// bring down bit from quotient
		// (aa has the dividend bits shifted into remainder)
		if (!is_zero(&aa)) {
			uint128_t aa_val = (__uint128_t)(uint64_t)aa.hi << 64 | (uint64_t)aa.lo;
			if (aa_val & ((__uint128_t)1 << i)) {
				remainder.lo |= 1;
			}
		}

		// if remainder >= bb, remainder -= bb and set quotient bit
		int128_box neg_bb;
		neg_abs(&neg_bb, &bb);
		int128_box diff;
		int128_add(&diff, &remainder, &neg_bb);
		if (!is_neg(&diff) || is_zero(&diff)) {
			remainder = diff;
			if (i < 64) {
				quotient.lo |= (1ULL << i);
			} else {
				quotient.hi |= (1ULL << (i - 64));
			}
		}
	}

	if (result_neg) {
		neg_abs(out, &quotient);
	} else {
		*out = quotient;
	}
}

void int128_mod(int128_box *out, const int128_box *a, const int128_box *b) {
	int128_box q, prod;
	int128_div(&q, a, b);
	int128_mul(&prod, &q, b);
	int128_sub(out, a, &prod);
}

void int128_neg(int128_box *out, const int128_box *a) {
	neg_abs(out, a);
}

void int128_abs(int128_box *out, const int128_box *a) {
	abs_value(out, a);
}

int int128_cmp(const int128_box *a, const int128_box *b) {
	if (a->hi < b->hi) return -1;
	if (a->hi > b->hi) return 1;
	if ((uint64_t)a->lo < (uint64_t)b->lo) return -1;
	if ((uint64_t)a->lo > (uint64_t)b->lo) return 1;
	return 0;
}

int int128_sign(const int128_box *a) {
	if (a->hi < 0) return -1;
	if (a->hi > 0 || (uint64_t)a->lo > 0) return 1;
	return 0;
}

char *int128_to_string(const int128_box *a, int radix) {
	static const char digits[] = "0123456789abcdef";
	if (is_zero(a)) {
		char *s = (char *)malloc(2);
		s[0] = '0'; s[1] = '\0';
		return s;
	}
	bool neg = is_neg(a);
	int128_box val;
	abs_value(&val, a);

	char buf[256];
	int pos = 255;
	buf[pos] = '\0';

	// Manual division for string conversion
	uint64_t radix_u64 = (uint64_t)radix;
	while (!is_zero(&val)) {
		uint64_t r_lo = (uint64_t)val.lo;
		uint64_t r_hi = (uint64_t)val.hi;
		// val % radix
		__uint128_t v = (__uint128_t)r_hi << 64 | r_lo;
		int digit = (int)(v % radix_u64);
		v /= radix_u64;
		val.lo = (int64_t)v;
		val.hi = (int64_t)(v >> 64);
		buf[--pos] = digits[digit];
	}
	if (neg) buf[--pos] = '-';
	char *s = (char *)malloc(255 - pos + 1);
	if (s) strcpy(s, &buf[pos]);
	return s;
}

#endif // HAVE_INT128

char *int128_format(const int128_box *a, int group_size, const char *group_sep) {
	char *raw = int128_to_string(a, 10);
	if (!raw || group_size <= 0 || !group_sep || !*group_sep) {
		return raw;
	}
	bool neg = (raw[0] == '-');
	const char *digits = raw + (neg ? 1 : 0);
	size_t len = strlen(digits);
	size_t sep_len = strlen(group_sep);
	size_t groups = (len + group_size - 1) / group_size;
	size_t new_len = (neg ? 1 : 0) + len + (groups - 1) * sep_len;
	char *out = (char *)malloc(new_len + 1);
	if (!out) { free(raw); return nullptr; }
	size_t pos = 0;
	if (neg) out[pos++] = '-';
	size_t first_group = len % group_size;
	if (first_group == 0) first_group = group_size;
	memcpy(out + pos, digits, first_group);
	pos += first_group;
	for (size_t i = first_group; i < len; i += group_size) {
		memcpy(out + pos, group_sep, sep_len);
		pos += sep_len;
		memcpy(out + pos, digits + i, group_size);
		pos += group_size;
	}
	out[pos] = '\0';
	free(raw);
	return out;
}

void int128_free_string(char *s) {
	free(s);
}
