#include "decimal_quantity.h"

#include <algorithm>
#include <bit>
#include <cstdio>
#include <cstring>
#include <limits>
#include <vector>

namespace bigmath::formatting {
namespace {

constexpr uint32_t BASE = 1000000000u;

class BigUnsigned {
public:
	BigUnsigned() : limbs_(1, 0) {}

	explicit BigUnsigned(uint64_t value) {
		do {
			limbs_.push_back(static_cast<uint32_t>(value % BASE));
			value /= BASE;
		} while (value != 0);
	}

	void multiply(uint32_t factor) {
		uint64_t carry = 0;
		for (uint32_t &limb : limbs_) {
			const uint64_t product = static_cast<uint64_t>(limb) * factor + carry;
			limb = static_cast<uint32_t>(product % BASE);
			carry = product / BASE;
		}
		while (carry != 0) {
			limbs_.push_back(static_cast<uint32_t>(carry % BASE));
			carry /= BASE;
		}
	}

	void add(uint32_t value) {
		uint64_t carry = value;
		for (size_t index = 0; carry != 0 && index < limbs_.size(); index++) {
			const uint64_t sum = static_cast<uint64_t>(limbs_[index]) + carry;
			limbs_[index] = static_cast<uint32_t>(sum % BASE);
			carry = sum / BASE;
		}
		if (carry != 0) limbs_.push_back(static_cast<uint32_t>(carry));
	}

	void multiply_power(uint32_t factor, uint64_t exponent) {
		for (uint64_t index = 0; index < exponent; index++) {
			multiply(factor);
		}
	}

	std::string decimal() const {
		std::string result = std::to_string(limbs_.back());
		char buffer[10];
		for (size_t index = limbs_.size() - 1; index-- > 0;) {
			std::snprintf(buffer, sizeof(buffer), "%09u", limbs_[index]);
			result.append(buffer, 9);
		}
		return result;
	}

private:
	std::vector<uint32_t> limbs_;
};

BigUnsigned unsigned_from_bytes(const uint8_t *bytes, size_t size) {
	BigUnsigned value;
	for (size_t index = 0; index < size; index++) {
		value.multiply(256);
		value.add(bytes[index]);
	}
	return value;
}

BigUnsigned magnitude_from_twos_complement(const uint8_t *bytes, size_t size, bool negative) {
	if (!negative) return unsigned_from_bytes(bytes, size);
	std::vector<uint8_t> magnitude(size);
	uint16_t carry = 1;
	for (size_t index = size; index-- > 0;) {
		const uint16_t value = static_cast<uint16_t>(static_cast<uint8_t>(~bytes[index])) + carry;
		magnitude[index] = static_cast<uint8_t>(value);
		carry = value >> 8;
	}
	return unsigned_from_bytes(magnitude.data(), magnitude.size());
}

void apply_binary_exponent(BigUnsigned &magnitude, int64_t exponent, DecimalQuantity &out) {
	if (exponent >= 0) {
		magnitude.multiply_power(2, static_cast<uint64_t>(exponent));
		out.scale = 0;
	} else {
		const uint64_t denominator_power = static_cast<uint64_t>(-(exponent + 1)) + 1;
		magnitude.multiply_power(5, denominator_power);
		out.scale = static_cast<int64_t>(denominator_power);
	}
	out.digits = magnitude.decimal();
}

BigUnsigned unsigned_from_words(uint64_t lo, uint64_t hi) {
	uint8_t bytes[16];
	uint64_t high = hi;
	uint64_t low = lo;
	for (int index = 7; index >= 0; index--) {
		bytes[index] = static_cast<uint8_t>(high);
		high >>= 8;
	}
	for (int index = 15; index >= 8; index--) {
		bytes[index] = static_cast<uint8_t>(low);
		low >>= 8;
	}
	return unsigned_from_bytes(bytes, 16);
}

}

bool quantity_from_twos_complement(
		const uint8_t *bytes,
		size_t size,
		int64_t scale,
		DecimalQuantity &out
) {
	if (bytes == nullptr || size == 0) return false;
	out.number_class = NumberClass::FINITE;
	out.negative = (bytes[0] & 0x80u) != 0;
	out.scale = scale;
	out.digits = magnitude_from_twos_complement(bytes, size, out.negative).decimal();
	if (out.digits == "0") {
		out.negative = false;
		out.scale = 0;
	}
	return true;
}

bool quantity_from_i64(int64_t value, DecimalQuantity &out) {
	out.number_class = NumberClass::FINITE;
	out.negative = value < 0;
	const uint64_t magnitude = value < 0
		? static_cast<uint64_t>(-(value + 1)) + 1
		: static_cast<uint64_t>(value);
	out.digits = std::to_string(magnitude);
	out.scale = 0;
	return true;
}

bool quantity_from_int128(int64_t lo, int64_t hi, DecimalQuantity &out) {
	out.number_class = NumberClass::FINITE;
	out.negative = hi < 0;
	uint64_t magnitude_lo = static_cast<uint64_t>(lo);
	uint64_t magnitude_hi = static_cast<uint64_t>(hi);
	if (out.negative) {
		magnitude_lo = ~magnitude_lo + 1;
		magnitude_hi = ~magnitude_hi + (magnitude_lo == 0 ? 1 : 0);
	}
	out.digits = unsigned_from_words(magnitude_lo, magnitude_hi).decimal();
	out.scale = 0;
	if (out.digits == "0") out.negative = false;
	return true;
}

bool quantity_from_f64(double value, DecimalQuantity &out) {
	const uint64_t bits = std::bit_cast<uint64_t>(value);
	out.negative = (bits >> 63) != 0;
	const uint64_t exponent_bits = (bits >> 52) & 0x7ffu;
	const uint64_t fraction = bits & ((UINT64_C(1) << 52) - 1);
	if (exponent_bits == 0x7ffu) {
		out.number_class = fraction == 0 ? NumberClass::INFINITY_VALUE : NumberClass::NAN_VALUE;
		return true;
	}
	out.number_class = NumberClass::FINITE;
	if (exponent_bits == 0 && fraction == 0) {
		out.digits = "0";
		out.scale = 0;
		return true;
	}
	const uint64_t significand = exponent_bits == 0 ? fraction : fraction | (UINT64_C(1) << 52);
	const int64_t exponent = exponent_bits == 0
		? -1074
		: static_cast<int64_t>(exponent_bits) - 1023 - 52;
	BigUnsigned magnitude(significand);
	apply_binary_exponent(magnitude, exponent, out);
	return true;
}

bool quantity_from_bigint(BigIntHandle *value, DecimalQuantity &out) {
#ifndef BIGMATH_HAS_GMP
	(void)value;
	(void)out;
	return false;
#else
	if (value == nullptr || value->value == nullptr) return false;
	const size_t capacity = mpz_sizeinbase(value->value, 10) + 3;
	std::string decimal(capacity, '\0');
	mpz_get_str(decimal.data(), 10, value->value);
	decimal.resize(std::strlen(decimal.c_str()));
	out.number_class = NumberClass::FINITE;
	out.negative = !decimal.empty() && decimal[0] == '-';
	out.digits = out.negative ? decimal.substr(1) : decimal;
	out.scale = 0;
	return true;
#endif
}

bool quantity_from_bigdeci(BigDeciHandle *value, DecimalQuantity &out) {
#ifndef BIGMATH_HAS_GMP
	(void)value;
	(void)out;
	return false;
#else
	if (value == nullptr || value->value == nullptr) return false;
	out.negative = mpfr_signbit(value->value) != 0;
	if (mpfr_nan_p(value->value)) {
		out.number_class = NumberClass::NAN_VALUE;
		return true;
	}
	if (mpfr_inf_p(value->value)) {
		out.number_class = NumberClass::INFINITY_VALUE;
		return true;
	}
	out.number_class = NumberClass::FINITE;
	if (mpfr_zero_p(value->value)) {
		out.digits = "0";
		out.scale = 0;
		return true;
	}
	mpz_t significand;
	mpz_init(significand);
	const mpfr_exp_t exponent = mpfr_get_z_2exp(significand, value->value);
	if (mpz_sgn(significand) < 0) mpz_neg(significand, significand);
	size_t byte_count = 0;
	const size_t capacity = (mpz_sizeinbase(significand, 2) + 7) / 8;
	std::vector<uint8_t> bytes(std::max<size_t>(1, capacity));
	mpz_export(bytes.data(), &byte_count, 1, 1, 1, 0, significand);
	mpz_clear(significand);
	BigUnsigned magnitude = unsigned_from_bytes(bytes.data(), byte_count);
	apply_binary_exponent(magnitude, static_cast<int64_t>(exponent), out);
	return true;
#endif
}

}
