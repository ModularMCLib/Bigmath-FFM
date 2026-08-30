#include "decimal_quantity.h"

#include <algorithm>
#include <bit>
#include <cstdio>
#include <cstring>
#include <limits>
#include <memory>
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

#ifdef BIGMATH_HAS_GMP

class ScopedMpz {
public:
	ScopedMpz() { mpz_init(value_); }
	~ScopedMpz() { mpz_clear(value_); }

	ScopedMpz(const ScopedMpz &) = delete;
	ScopedMpz &operator=(const ScopedMpz &) = delete;

	mpz_ptr get() { return value_; }
	mpz_srcptr get() const { return value_; }

private:
	mpz_t value_;
};

int descriptor_multiplier_power10(int32_t multiplier) {
	int power = 0;
	while (multiplier > 1 && multiplier % 10 == 0) {
		multiplier /= 10;
		power++;
	}
	return multiplier == 1 ? power : 0;
}

bool add_exact(int64_t left, int64_t right, int64_t &result) {
	if ((right > 0 && left > std::numeric_limits<int64_t>::max() - right) ||
			(right < 0 && left < std::numeric_limits<int64_t>::min() - right)) {
		return false;
	}
	result = left + right;
	return true;
}

QuantityStatus required_significant_digits(
		const FormatDescriptor &descriptor,
		int64_t source_exponent,
		int64_t decimal_shift,
		size_t &required
) {
	int64_t digit_count;
	if (descriptor.scientific()) {
		digit_count = std::max<int64_t>(
			1,
			static_cast<int64_t>(descriptor.maximum_integer_digits) +
				descriptor.maximum_fraction_digits
		);
	} else {
		int64_t displayed_exponent;
		if (!add_exact(source_exponent, -decimal_shift, displayed_exponent) ||
				!add_exact(
					displayed_exponent,
					descriptor_multiplier_power10(descriptor.multiplier),
					displayed_exponent
				)) {
			return QuantityStatus::RESULT_TOO_LARGE;
		}
		const int64_t integer_digits = displayed_exponent < 0 ? 0 : displayed_exponent + 1;
		if (!add_exact(integer_digits, descriptor.maximum_fraction_digits, digit_count)) {
			return QuantityStatus::RESULT_TOO_LARGE;
		}
		digit_count = std::max<int64_t>(1, digit_count);
	}
	if (digit_count > static_cast<int64_t>(MAX_FORMAT_RESULT_BYTES)) {
		return QuantityStatus::RESULT_TOO_LARGE;
	}
	required = static_cast<size_t>(digit_count);
	return QuantityStatus::OK;
}

QuantityStatus required_bigdeci_digits(
		const FormatDescriptor &descriptor,
		int64_t source_exponent,
		size_t &required
) {
	const int64_t milli_shift = descriptor.milli() && source_exponent >= 3 ? 3 : 0;
	int64_t compact_exponent;
	if (!add_exact(source_exponent, -milli_shift, compact_exponent)) {
		return QuantityStatus::RESULT_TOO_LARGE;
	}
	if (!descriptor.compact() || compact_exponent < 3) {
		return required_significant_digits(descriptor, source_exponent, milli_shift, required);
	}

	const int64_t compact_index = compact_exponent / 3;
	if (compact_index >= static_cast<int64_t>(COMPACT_SUFFIX_COUNT)) {
		FormatDescriptor fallback{};
		if (!decode_descriptor(descriptor.fallback_data, descriptor.fallback_size, fallback)) {
			return QuantityStatus::INVALID_DESCRIPTOR;
		}
		return required_significant_digits(fallback, source_exponent, milli_shift, required);
	}

	const int64_t compact_shift = milli_shift + compact_index * 3;
	QuantityStatus status = required_significant_digits(
		descriptor,
		source_exponent,
		compact_shift,
		required
	);
	if (status != QuantityStatus::OK ||
			compact_index + 1 < static_cast<int64_t>(COMPACT_SUFFIX_COUNT) ||
			descriptor.fallback_size == 0) {
		return status;
	}

	FormatDescriptor fallback{};
	if (!decode_descriptor(descriptor.fallback_data, descriptor.fallback_size, fallback)) {
		return QuantityStatus::INVALID_DESCRIPTOR;
	}
	size_t fallback_required;
	status = required_significant_digits(
		fallback,
		source_exponent,
		milli_shift,
		fallback_required
	);
	if (status == QuantityStatus::OK) required = std::max(required, fallback_required);
	return status;
}

uint64_t unsigned_magnitude(int64_t value) {
	return value < 0
		? static_cast<uint64_t>(-(value + 1)) + 1
		: static_cast<uint64_t>(value);
}

bool sum_greater_than_size(int64_t signed_value, uint64_t unsigned_value, size_t limit) {
	const uint64_t unsigned_limit = static_cast<uint64_t>(limit);
	if (signed_value >= 0) {
		const uint64_t positive = static_cast<uint64_t>(signed_value);
		return positive > unsigned_limit || unsigned_value > unsigned_limit - positive;
	}
	const uint64_t negative = unsigned_magnitude(signed_value);
	if (negative > std::numeric_limits<uint64_t>::max() - unsigned_limit) return false;
	return unsigned_value > unsigned_limit + negative;
}

bool has_omitted_nonzero_digits(
		mpz_srcptr significand,
		int64_t binary_exponent,
		int64_t decimal_exponent,
		size_t retained_digits
) {
	const uint64_t factors_of_two = static_cast<uint64_t>(mpz_scan1(significand, 0));
	if (binary_exponent < 0) {
		const uint64_t denominator_power = unsigned_magnitude(binary_exponent);
		if (denominator_power > factors_of_two) {
			return sum_greater_than_size(
				decimal_exponent,
				denominator_power - factors_of_two,
				retained_digits
			);
		}
	}

	uint64_t integer_factors_of_two;
	if (binary_exponent >= 0) {
		const uint64_t exponent = static_cast<uint64_t>(binary_exponent);
		integer_factors_of_two = factors_of_two > std::numeric_limits<uint64_t>::max() - exponent
			? std::numeric_limits<uint64_t>::max()
			: factors_of_two + exponent;
	} else {
		integer_factors_of_two = factors_of_two - unsigned_magnitude(binary_exponent);
	}

	ScopedMpz factor;
	ScopedMpz quotient;
	mpz_set_ui(factor.get(), 5);
	const uint64_t factors_of_five = static_cast<uint64_t>(
		mpz_remove(quotient.get(), significand, factor.get())
	);
	const uint64_t trailing_zeros = std::min(integer_factors_of_two, factors_of_five);
	if (decimal_exponent <= 0) return false;
	const uint64_t decimal_digits = static_cast<uint64_t>(decimal_exponent);
	if (trailing_zeros >= decimal_digits) return false;
	const uint64_t significant_span = decimal_digits - trailing_zeros;
	return significant_span > retained_digits;
}

#endif

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

QuantityStatus quantity_from_bigdeci(
		BigDeciHandle *value,
		const FormatDescriptor &descriptor,
		DecimalQuantity &out
) {
#ifndef BIGMATH_HAS_GMP
	(void)value;
	(void)descriptor;
	(void)out;
	return QuantityStatus::BACKEND_UNAVAILABLE;
#else
	if (value == nullptr || value->value == nullptr) return QuantityStatus::INVALID_VALUE;
	out.negative = mpfr_signbit(value->value) != 0;
	if (mpfr_nan_p(value->value)) {
		out.number_class = NumberClass::NAN_VALUE;
		return QuantityStatus::OK;
	}
	if (mpfr_inf_p(value->value)) {
		out.number_class = NumberClass::INFINITY_VALUE;
		return QuantityStatus::OK;
	}
	out.number_class = NumberClass::FINITE;
	if (mpfr_zero_p(value->value)) {
		out.digits = "0";
		out.scale = 0;
		return QuantityStatus::OK;
	}

	mpfr_exp_t decimal_exponent_value;
	std::unique_ptr<char, void (*)(char *)> exponent_probe(
		mpfr_get_str(nullptr, &decimal_exponent_value, 10, 2, value->value, MPFR_RNDZ),
		mpfr_free_str
	);
	if (!exponent_probe) return QuantityStatus::INVALID_VALUE;
	const int64_t decimal_exponent = static_cast<int64_t>(decimal_exponent_value);
	int64_t source_exponent;
	if (!add_exact(decimal_exponent, -1, source_exponent)) {
		return QuantityStatus::RESULT_TOO_LARGE;
	}

	size_t required_digits;
	QuantityStatus status = required_bigdeci_digits(descriptor, source_exponent, required_digits);
	if (status != QuantityStatus::OK) return status;
	const size_t retained_digits = std::max<size_t>(2, required_digits + 1);

	ScopedMpz significand;
	const mpfr_exp_t binary_exponent_value = mpfr_get_z_2exp(significand.get(), value->value);
	if (mpz_sgn(significand.get()) < 0) mpz_neg(significand.get(), significand.get());
	const int64_t binary_exponent = static_cast<int64_t>(binary_exponent_value);
	const bool sticky = has_omitted_nonzero_digits(
		significand.get(),
		binary_exponent,
		decimal_exponent,
		retained_digits
	);

	std::unique_ptr<char, void (*)(char *)> raw_digits(
		mpfr_get_str(
			nullptr,
			&decimal_exponent_value,
			10,
			retained_digits,
			value->value,
			MPFR_RNDZ
		),
		mpfr_free_str
	);
	if (!raw_digits) return QuantityStatus::INVALID_VALUE;
	out.digits.assign(raw_digits.get());
	if (!out.digits.empty() && out.digits.front() == '-') out.digits.erase(0, 1);
	if (out.digits.empty()) return QuantityStatus::INVALID_VALUE;

	const int64_t returned_exponent = static_cast<int64_t>(decimal_exponent_value);
	if (retained_digits > static_cast<size_t>(std::numeric_limits<int64_t>::max()) ||
			!add_exact(static_cast<int64_t>(retained_digits), -returned_exponent, out.scale)) {
		return QuantityStatus::RESULT_TOO_LARGE;
	}
	if (sticky) {
		out.digits.push_back('1');
		if (!add_exact(out.scale, 1, out.scale)) return QuantityStatus::RESULT_TOO_LARGE;
	}
	return QuantityStatus::OK;
#endif
}

}
