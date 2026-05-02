#include "int128.h"

static constexpr char INT128_DIGITS[] = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

static int digit_value(const char c, const int radix) {
	if (c >= '0' && c <= '9') {
		return c - '0';
	}
	if (c >= 'a' && c <= 'z') {
		return c - 'a' + 10;
	}
	if (c >= 'A' && c <= 'Z') {
		if (radix <= 36) {
			return c - 'A' + 10;
		}
		return c - 'A' + 36;
	}
	return -1;
}

static char digit_char(const unsigned digit) {
	return INT128_DIGITS[digit];
}

#ifdef HAVE_INT128

static __int128 to_i128(const int128_box *a) {
	return static_cast<__int128>(a->hi) << 64 | static_cast<uint64_t>(a->lo);
}

static unsigned __int128 to_u128(const int128_box *a) {
	return (static_cast<unsigned __int128>(static_cast<uint64_t>(a->hi)) << 64)
		| static_cast<uint64_t>(a->lo);
}

static void from_i128(int128_box *out, __int128 v) {
	out->lo = static_cast<int64_t>(v);
	out->hi = static_cast<int64_t>(v >> 64);
}

static void from_u128(int128_box *out, unsigned __int128 v) {
	out->lo = static_cast<int64_t>(static_cast<uint64_t>(v));
	out->hi = static_cast<int64_t>(static_cast<uint64_t>(v >> 64));
}

static unsigned __int128 abs_u128(const int128_box *a) {
	unsigned __int128 bits = to_u128(a);
	return a->hi < 0 ? (~bits + 1) : bits;
}

void int128_from_i64(int128_box *out, int64_t val) {
	out->lo = val;
	out->hi = (val < 0) ? -1 : 0;
}

void int128_from_u64(int128_box *out, uint64_t val) {
	out->lo = static_cast<int64_t>(val);
	out->hi = 0;
}

void int128_from_string(int128_box *out, const char *str, int radix) {
	if (radix < 2 || radix > 62) {
		int128_from_i64(out, 0);
		return;
	}
	unsigned __int128 v = 0;
	bool neg = false;
	const char *p = str;
	if (*p == '-') { neg = true; p++; }
	while (*p) {
		const char c = *p++;
		const int d = digit_value(c, radix);
		if (d < 0 || d >= radix) break;
		v = v * radix + d;
	}
	if (neg) {
		v = ~v + 1;
	}
	from_u128(out, v);
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
	return (va < vb) ? -1 : (va > vb) ? 1 : 0;
}

int int128_sign(const int128_box *a) {
	__int128 v = to_i128(a);
	return (v < 0) ? -1 : (v > 0) ? 1 : 0;
}

char *int128_to_string(const int128_box *a, int radix) {
	if (radix < 2 || radix > 62) {
		return nullptr;
	}
	__int128 v = to_i128(a);
	if (v == 0) {
		const auto s = static_cast<char *>(malloc(2));
		s[0] = '0'; s[1] = '\0';
		return s;
	}
	const bool neg = (v < 0);
	unsigned __int128 magnitude = abs_u128(a);
	char buf[256];
	int pos = 255;
	buf[pos] = '\0';
	while (magnitude > 0) {
		const auto digit = static_cast<unsigned>(magnitude % static_cast<unsigned>(radix));
		buf[--pos] = digit_char(digit);
		magnitude /= static_cast<unsigned>(radix);
	}
	if (neg) buf[--pos] = '-';
	auto s = static_cast<char *>(malloc(255 - pos + 1));
	if (s) strcpy(s, &buf[pos]);
	return s;
}

#else
// --- Portable fallback (MSVC, etc.) ---

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
	if (radix < 2 || radix > 62) {
		out->lo = 0;
		out->hi = 0;
		return;
	}
	out->lo = 0;
	out->hi = 0;
	bool neg = false;
	const char *p = str;
	if (*p == '-') { neg = true; p++; }
	const int128_box radix_box = {(int64_t)radix, 0};
	while (*p) {
		const int d = digit_value(*p++, radix);
		if (d < 0 || d >= radix) break;
		int128_box product;
		int128_mul(&product, out, &radix_box);
		const int128_box digit_box = {(int64_t)d, 0};
		int128_add(out, &product, &digit_box);
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

// 64x64 -> 128 multiplication using 32-bit decomposition
static void umul64(uint64_t *lo, uint64_t *hi, uint64_t a, uint64_t b) {
	uint32_t a_lo = (uint32_t)a;
	uint32_t a_hi = (uint32_t)(a >> 32);
	uint32_t b_lo = (uint32_t)b;
	uint32_t b_hi = (uint32_t)(b >> 32);
	uint64_t p00 = (uint64_t)a_lo * b_lo;
	uint64_t p01 = (uint64_t)a_lo * b_hi;
	uint64_t p10 = (uint64_t)a_hi * b_lo;
	uint64_t p11 = (uint64_t)a_hi * b_hi;
	uint64_t mid = p01 + p10;
	*lo = p00 + ((mid & 0xFFFFFFFFULL) << 32);
	*hi = p11 + (mid >> 32) + ((p00 >> 32) + (mid << 32 >> 32) > 0xFFFFFFFFULL ? 1 : 0);
	// better carry handling
	uint64_t mid_lo = (mid & 0xFFFFFFFFULL) << 32;
	uint64_t carry = (p00 + mid_lo < p00) ? 1ULL : 0ULL;
	*lo = p00 + mid_lo;
	*hi = p11 + (mid >> 32) + carry;
}

void int128_mul(int128_box *out, const int128_box *a, const int128_box *b) {
	int128_box aa, bb;
	abs_value(&aa, a);
	abs_value(&bb, b);
	bool result_neg = (is_neg(a) != is_neg(b));
	// 128x128 -> 256, keep low 128 bits
	uint64_t a0 = (uint64_t)aa.lo, a1 = (uint64_t)aa.hi;
	uint64_t b0 = (uint64_t)bb.lo, b1 = (uint64_t)bb.hi;
	uint64_t p00_lo, p00_hi, p01_lo, p01_hi, p10_lo, p10_hi;
	umul64(&p00_lo, &p00_hi, a0, b0);
	umul64(&p01_lo, &p01_hi, a0, b1);
	umul64(&p10_lo, &p10_hi, a1, b0);
	uint64_t r_lo = p00_lo;
	uint64_t r_hi = p00_hi + p01_lo + p10_lo;
	// handle overflow in addition chain
	uint64_t sum = p00_hi + p01_lo;
	uint64_t carry = (sum < p00_hi || sum < p01_lo) ? 1ULL : 0ULL;
	sum += p10_lo;
	if (sum < p10_lo) carry++;
	r_hi = sum;
	r_hi += (carry << 32);  // carry bits into higher part (p01_hi + p10_hi + overflow)
	r_hi += p01_hi + p10_hi;
	out->lo = (int64_t)r_lo;
	out->hi = (int64_t)r_hi;
	if (result_neg) {
		neg_abs(out, out);
	}
}

void int128_div(int128_box *out, const int128_box *a, const int128_box *b) {
	int128_box aa, bb;
	abs_value(&aa, a);
	abs_value(&bb, b);
	bool result_neg = (is_neg(a) != is_neg(b));
	if (is_zero(&bb)) {
		out->lo = 0; out->hi = 0;
		return;
	}
	// restore division: shift-left algorithm
	int128_box remainder = {0, 0};
	int128_box quotient = {0, 0};
	uint64_t a_lo = (uint64_t)aa.lo;
	uint64_t a_hi = (uint64_t)aa.hi;
	for (int i = 127; i >= 0; i--) {
		// shift remainder left by 1
		uint64_t r_lo = (uint64_t)remainder.lo;
		uint64_t r_hi = (uint64_t)remainder.hi;
		uint64_t new_lo = r_lo << 1;
		uint64_t new_hi = (r_hi << 1) | (r_lo >> 63);
		remainder.lo = (int64_t)new_lo;
		remainder.hi = (int64_t)new_hi;
		// bring down bit from dividend
		uint64_t bit = (i >= 64) ? ((a_hi >> (i - 64)) & 1) : ((a_lo >> i) & 1);
		remainder.lo |= bit;
		// if remainder >= bb, subtract and set quotient bit
		int128_box neg_bb;
		neg_abs(&neg_bb, &bb);
		int128_box diff;
		uint64_t d_lo, d_hi;
		u128_add(&d_lo, &d_hi,
			(uint64_t)remainder.lo, (uint64_t)remainder.hi,
			(uint64_t)neg_bb.lo, (uint64_t)neg_bb.hi);
		diff.lo = (int64_t)d_lo;
		diff.hi = (int64_t)d_hi;
		if (!is_neg(&diff) || is_zero(&diff)) {
			remainder = diff;
			if (i >= 64) {
				quotient.hi |= (1ULL << (i - 64));
			} else {
				quotient.lo |= (1ULL << i);
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
	int128_box q;
	int128_div(&q, a, b);
	int128_box prod;
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
	if (radix < 2 || radix > 62) {
		return nullptr;
	}
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
	const int128_box radix_box = {(int64_t)radix, 0};
	while (!is_zero(&val)) {
		int128_box remainder;
		int128_mod(&remainder, &val, &radix_box);
		buf[--pos] = digit_char((unsigned)(uint64_t)remainder.lo);
		int128_box quotient;
		int128_div(&quotient, &val, &radix_box);
		val = quotient;
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
	const bool neg = (raw[0] == '-');
	const char *digits = raw + (neg ? 1 : 0);
	const size_t len = strlen(digits);
	const size_t sep_len = strlen(group_sep);
	const size_t groups = (len + group_size - 1) / group_size;
	const size_t new_len = (neg ? 1 : 0) + len + (groups - 1) * sep_len;
	const auto out = static_cast<char *>(malloc(new_len + 1));
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
