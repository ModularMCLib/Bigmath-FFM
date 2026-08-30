#include "int128.h"

#include <bit>

#if defined(_MSC_VER)
#include <intrin.h>
#endif

static constexpr char INT128_DIGITS[] = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

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
	if (radix == 10) {
		constexpr unsigned __int128 chunk_base = 1000000000;
		while (magnitude > 0) {
			const unsigned __int128 quotient = magnitude / chunk_base;
			uint32_t chunk = static_cast<uint32_t>(magnitude - quotient * chunk_base);
			const int digits = quotient == 0 ? 1 : 9;
			int written = 0;
			do {
				buf[--pos] = static_cast<char>('0' + chunk % 10);
				chunk /= 10;
				written++;
			} while (chunk != 0);
			while (written++ < digits) {
				buf[--pos] = '0';
			}
			magnitude = quotient;
		}
	} else {
		const auto unsigned_radix = static_cast<unsigned>(radix);
		while (magnitude > 0) {
			const unsigned __int128 quotient = magnitude / unsigned_radix;
			const auto digit = static_cast<unsigned>(magnitude - quotient * unsigned_radix);
			buf[--pos] = digit_char(digit);
			magnitude = quotient;
		}
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
#if defined(_MSC_VER) && defined(_M_X64)
	*lo = _umul128(a, b, hi);
#elif defined(_MSC_VER) && defined(_M_ARM64)
	*lo = a * b;
	*hi = __umulh(a, b);
#else
	const uint64_t a_lo = static_cast<uint32_t>(a);
	const uint64_t a_hi = a >> 32;
	const uint64_t b_lo = static_cast<uint32_t>(b);
	const uint64_t b_hi = b >> 32;
	const uint64_t low_product = a_lo * b_lo;
	uint64_t middle = a_hi * b_lo + (low_product >> 32);
	const uint64_t middle_high = middle >> 32;
	middle = (middle & 0xffff'ffffULL) + a_lo * b_hi;
	*lo = (middle << 32) | (low_product & 0xffff'ffffULL);
	*hi = a_hi * b_hi + middle_high + (middle >> 32);
#endif
}

void int128_mul(int128_box *out, const int128_box *a, const int128_box *b) {
	const uint64_t a0 = static_cast<uint64_t>(a->lo);
	const uint64_t a1 = static_cast<uint64_t>(a->hi);
	const uint64_t b0 = static_cast<uint64_t>(b->lo);
	const uint64_t b1 = static_cast<uint64_t>(b->hi);
	uint64_t product_lo;
	uint64_t product_hi;
	umul64(&product_lo, &product_hi, a0, b0);
	out->lo = static_cast<int64_t>(product_lo);
	out->hi = static_cast<int64_t>(product_hi + a0 * b1 + a1 * b0);
}

static int u128_compare(const int128_box *a, const int128_box *b) {
	const uint64_t a_hi = static_cast<uint64_t>(a->hi);
	const uint64_t b_hi = static_cast<uint64_t>(b->hi);
	if (a_hi < b_hi) return -1;
	if (a_hi > b_hi) return 1;
	const uint64_t a_lo = static_cast<uint64_t>(a->lo);
	const uint64_t b_lo = static_cast<uint64_t>(b->lo);
	return a_lo < b_lo ? -1 : a_lo > b_lo ? 1 : 0;
}

static void u128_subtract(int128_box *out, const int128_box *a, const int128_box *b) {
	const uint64_t a_lo = static_cast<uint64_t>(a->lo);
	const uint64_t b_lo = static_cast<uint64_t>(b->lo);
	const uint64_t result_lo = a_lo - b_lo;
	const uint64_t borrow = a_lo < b_lo ? 1 : 0;
	out->lo = static_cast<int64_t>(result_lo);
	out->hi = static_cast<int64_t>(static_cast<uint64_t>(a->hi) - static_cast<uint64_t>(b->hi) - borrow);
}

static uint64_t udiv128_by_64_low(uint64_t lo, uint64_t hi, uint64_t divisor, uint64_t *remainder) {
#if defined(_MSC_VER) && defined(_M_X64)
	return _udiv128(hi, lo, divisor, remainder);
#else
	uint64_t quotient = 0;
	uint64_t current = hi;
	for (int bit = 63; bit >= 0; bit--) {
		const bool carry = (current >> 63) != 0;
		current = (current << 1) | ((lo >> bit) & 1ULL);
		if (carry || current >= divisor) {
			current -= divisor;
			quotient |= 1ULL << bit;
		}
	}
	*remainder = current;
	return quotient;
#endif
}

static void u128_divmod_restoring(
		const int128_box *dividend,
		const int128_box *divisor,
		int128_box *quotient,
		int128_box *remainder
) {
	quotient->lo = 0;
	quotient->hi = 0;
	remainder->lo = 0;
	remainder->hi = 0;
	const uint64_t dividend_lo = static_cast<uint64_t>(dividend->lo);
	const uint64_t dividend_hi = static_cast<uint64_t>(dividend->hi);
	for (int bit = 127; bit >= 0; bit--) {
		const uint64_t remainder_lo = static_cast<uint64_t>(remainder->lo);
		const uint64_t remainder_hi = static_cast<uint64_t>(remainder->hi);
		remainder->lo = static_cast<int64_t>(remainder_lo << 1);
		remainder->hi = static_cast<int64_t>((remainder_hi << 1) | (remainder_lo >> 63));
		const uint64_t incoming = bit >= 64
			? (dividend_hi >> (bit - 64)) & 1ULL
			: (dividend_lo >> bit) & 1ULL;
		remainder->lo = static_cast<int64_t>(static_cast<uint64_t>(remainder->lo) | incoming);
		if (u128_compare(remainder, divisor) >= 0) {
			u128_subtract(remainder, remainder, divisor);
			if (bit >= 64) {
				quotient->hi = static_cast<int64_t>(static_cast<uint64_t>(quotient->hi) | (1ULL << (bit - 64)));
			} else {
				quotient->lo = static_cast<int64_t>(static_cast<uint64_t>(quotient->lo) | (1ULL << bit));
			}
		}
	}
}

static void u128_divmod_unsigned(
		const int128_box *dividend,
		const int128_box *divisor,
		int128_box *quotient,
		int128_box *remainder
) {
	if (static_cast<uint64_t>(divisor->hi) == 0) {
		const uint64_t divisor_lo = static_cast<uint64_t>(divisor->lo);
		const uint64_t dividend_hi = static_cast<uint64_t>(dividend->hi);
		const uint64_t quotient_hi = dividend_hi / divisor_lo;
		const uint64_t high_remainder = dividend_hi % divisor_lo;
		uint64_t low_remainder;
		const uint64_t quotient_lo = udiv128_by_64_low(
			static_cast<uint64_t>(dividend->lo),
			high_remainder,
			divisor_lo,
			&low_remainder
		);
		quotient->lo = static_cast<int64_t>(quotient_lo);
		quotient->hi = static_cast<int64_t>(quotient_hi);
		remainder->lo = static_cast<int64_t>(low_remainder);
		remainder->hi = 0;
		return;
	}

	// A true two-limb divisor produces at most one quotient limb. Normalize the
	// top divisor word, estimate that quotient, then correct against the exact
	// low-128 product. The restoring path remains the bounded fallback.
	const uint64_t dividend_lo = static_cast<uint64_t>(dividend->lo);
	const uint64_t dividend_hi = static_cast<uint64_t>(dividend->hi);
	const uint64_t divisor_lo = static_cast<uint64_t>(divisor->lo);
	const uint64_t divisor_hi = static_cast<uint64_t>(divisor->hi);
	const int shift = std::countl_zero(divisor_hi);
	const uint64_t normalized_divisor_hi = shift == 0
		? divisor_hi
		: (divisor_hi << shift) | (divisor_lo >> (64 - shift));
	const uint64_t normalized_divisor_lo = divisor_lo << shift;
	const uint64_t numerator_top = shift == 0 ? 0 : dividend_hi >> (64 - shift);
	const uint64_t normalized_dividend_hi = shift == 0
		? dividend_hi
		: (dividend_hi << shift) | (dividend_lo >> (64 - shift));
	const uint64_t normalized_dividend_lo = dividend_lo << shift;
	uint64_t remainder_hat;
	uint64_t estimate = udiv128_by_64_low(
		normalized_dividend_hi,
		numerator_top,
		normalized_divisor_hi,
		&remainder_hat
	);
	for (int correction = 0; correction < 3; correction++) {
		uint64_t product_lo;
		uint64_t product_hi;
		umul64(&product_lo, &product_hi, estimate, normalized_divisor_lo);
		if (product_hi < remainder_hat ||
				(product_hi == remainder_hat && product_lo <= normalized_dividend_lo)) {
			break;
		}
		estimate--;
		const uint64_t previous = remainder_hat;
		remainder_hat += normalized_divisor_hi;
		if (remainder_hat < previous) break;
	}

	for (int correction = 0; correction <= 4; correction++) {
		uint64_t low_product_lo;
		uint64_t low_product_hi;
		uint64_t high_product_lo;
		uint64_t high_product_hi;
		umul64(&low_product_lo, &low_product_hi, estimate, divisor_lo);
		umul64(&high_product_lo, &high_product_hi, estimate, divisor_hi);
		const uint64_t product_hi = low_product_hi + high_product_lo;
		const bool overflow = high_product_hi != 0 || product_hi < low_product_hi;
		const int128_box product = {
			static_cast<int64_t>(low_product_lo),
			static_cast<int64_t>(product_hi)
		};
		if (!overflow && u128_compare(&product, dividend) <= 0) {
			u128_subtract(remainder, dividend, &product);
			while (u128_compare(remainder, divisor) >= 0) {
				u128_subtract(remainder, remainder, divisor);
				estimate++;
			}
			quotient->lo = static_cast<int64_t>(estimate);
			quotient->hi = 0;
			return;
		}
		estimate--;
	}
	u128_divmod_restoring(dividend, divisor, quotient, remainder);
}

void int128_div(int128_box *out, const int128_box *a, const int128_box *b) {
	int128_box dividend;
	int128_box divisor;
	abs_value(&dividend, a);
	abs_value(&divisor, b);
	if (is_zero(&divisor)) {
		out->lo = 0;
		out->hi = 0;
		return;
	}
	int128_box quotient;
	int128_box remainder;
	u128_divmod_unsigned(&dividend, &divisor, &quotient, &remainder);
	if (is_neg(a) != is_neg(b)) {
		neg_abs(out, &quotient);
	} else {
		*out = quotient;
	}
}

void int128_mod(int128_box *out, const int128_box *a, const int128_box *b) {
	int128_box dividend;
	int128_box divisor;
	abs_value(&dividend, a);
	abs_value(&divisor, b);
	if (is_zero(&divisor)) {
		out->lo = 0;
		out->hi = 0;
		return;
	}
	int128_box quotient;
	int128_box remainder;
	u128_divmod_unsigned(&dividend, &divisor, &quotient, &remainder);
	if (is_neg(a)) {
		neg_abs(out, &remainder);
	} else {
		*out = remainder;
	}
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
	if (radix == 10) {
		const int128_box chunk_base = {1000000000, 0};
		while (!is_zero(&val)) {
			int128_box quotient;
			int128_box remainder;
			u128_divmod_unsigned(&val, &chunk_base, &quotient, &remainder);
			uint32_t chunk = static_cast<uint32_t>(remainder.lo);
			const int digits = is_zero(&quotient) ? 1 : 9;
			int written = 0;
			do {
				buf[--pos] = static_cast<char>('0' + chunk % 10);
				chunk /= 10;
				written++;
			} while (chunk != 0);
			while (written++ < digits) {
				buf[--pos] = '0';
			}
			val = quotient;
		}
	} else {
		const int128_box radix_box = {static_cast<int64_t>(radix), 0};
		while (!is_zero(&val)) {
			int128_box quotient;
			int128_box remainder;
			u128_divmod_unsigned(&val, &radix_box, &quotient, &remainder);
			buf[--pos] = digit_char(static_cast<unsigned>(static_cast<uint64_t>(remainder.lo)));
			val = quotient;
		}
	}
	if (neg) buf[--pos] = '-';
	char *s = (char *)malloc(255 - pos + 1);
	if (s) strcpy(s, &buf[pos]);
	return s;
}
#endif // HAVE_INT128

void int128_free_string(char *s) {
	free(s);
}
