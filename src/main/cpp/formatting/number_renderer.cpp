#include "number_renderer.h"

#include <algorithm>
#include <array>
#include <cstdint>
#include <limits>

namespace bigmath::formatting {
namespace {

constexpr size_t MAX_RESULT_BYTES = 16 * 1024 * 1024;
constexpr std::array<std::string_view, 39> COMPACT_SUFFIXES = {
	"", "K", "M", "G", "T", "P", "E", "Z", "Y", "B",
	"N", "D", "C", "S", "O", "Q", "X", "W", "V", "U",
	"Tt", "Gt", "Mt", "St", "Ot", "Nt", "Dt", "Ct", "Lt", "Kt",
	"Jt", "It", "Ht", "Gtt", "Ett", "Dtt", "Ctt", "Btt", "Att"
};

void strip_leading_zeros(std::string &digits) {
	const size_t first = digits.find_first_not_of('0');
	if (first == std::string::npos) {
		digits = "0";
	} else if (first != 0) {
		digits.erase(0, first);
	}
}

void increment_decimal(std::string &digits) {
	for (size_t index = digits.size(); index-- > 0;) {
		if (digits[index] != '9') {
			digits[index]++;
			return;
		}
		digits[index] = '0';
	}
	digits.insert(digits.begin(), '1');
}

bool has_nonzero(std::string_view value) {
	return value.find_first_not_of('0') != std::string_view::npos;
}

FormatStatus round_to_scale(DecimalQuantity &value, int64_t target_scale, RoundingMode mode) {
	if (value.number_class != NumberClass::FINITE || value.scale <= target_scale) {
		return FormatStatus::OK;
	}
	const uint64_t drop = static_cast<uint64_t>(value.scale) - static_cast<uint64_t>(target_scale);
	const size_t length = value.digits.size();
	const size_t kept_count = drop >= length ? 0 : length - static_cast<size_t>(drop);
	const std::string_view discarded = kept_count < length
		? std::string_view(value.digits).substr(kept_count)
		: std::string_view();
	const bool discarded_nonzero = has_nonzero(discarded);
	if (!discarded_nonzero) {
		value.digits = kept_count == 0 ? "0" : value.digits.substr(0, kept_count);
		value.scale = target_scale;
		strip_leading_zeros(value.digits);
		return FormatStatus::OK;
	}
	if (mode == RoundingMode::UNNECESSARY) {
		return FormatStatus::ROUNDING_NECESSARY;
	}

	bool increment = false;
	switch (mode) {
		case RoundingMode::UP:
			increment = true;
			break;
		case RoundingMode::DOWN:
			break;
		case RoundingMode::CEILING:
			increment = !value.negative;
			break;
		case RoundingMode::FLOOR:
			increment = value.negative;
			break;
		case RoundingMode::HALF_UP:
		case RoundingMode::HALF_DOWN:
		case RoundingMode::HALF_EVEN: {
			int half_comparison = -1;
			if (drop <= length) {
				const char first = value.digits[kept_count];
				if (first > '5') {
					half_comparison = 1;
				} else if (first == '5') {
					half_comparison = has_nonzero(discarded.substr(1)) ? 1 : 0;
				}
			}
			if (half_comparison > 0) {
				increment = true;
			} else if (half_comparison == 0) {
				if (mode == RoundingMode::HALF_UP) {
					increment = true;
				} else if (mode == RoundingMode::HALF_EVEN) {
					const int last = kept_count == 0 ? 0 : value.digits[kept_count - 1] - '0';
					increment = (last & 1) != 0;
				}
			}
			break;
		}
		case RoundingMode::UNNECESSARY:
			break;
	}

	value.digits = kept_count == 0 ? "0" : value.digits.substr(0, kept_count);
	if (increment) increment_decimal(value.digits);
	value.scale = target_scale;
	strip_leading_zeros(value.digits);
	return FormatStatus::OK;
}

void multiply_digits(std::string &digits, uint32_t factor) {
	uint64_t carry = 0;
	for (size_t index = digits.size(); index-- > 0;) {
		const uint64_t product = static_cast<uint64_t>(digits[index] - '0') * factor + carry;
		digits[index] = static_cast<char>('0' + product % 10);
		carry = product / 10;
	}
	while (carry != 0) {
		digits.insert(digits.begin(), static_cast<char>('0' + carry % 10));
		carry /= 10;
	}
}

int multiplier_power10(int32_t multiplier) {
	int power = 0;
	while (multiplier > 1 && multiplier % 10 == 0) {
		multiplier /= 10;
		power++;
	}
	return multiplier == 1 ? power : -1;
}

bool add_scale(int64_t &scale, int64_t delta) {
	if ((delta > 0 && scale > std::numeric_limits<int64_t>::max() - delta) ||
			(delta < 0 && scale < std::numeric_limits<int64_t>::min() - delta)) {
		return false;
	}
	scale += delta;
	return true;
}

int64_t decimal_exponent(const DecimalQuantity &value) {
	const uint64_t base = value.digits.size() - 1;
	if (value.scale >= 0) {
		const uint64_t scale = static_cast<uint64_t>(value.scale);
		if (scale <= base) return static_cast<int64_t>(base - scale);
		return -static_cast<int64_t>(scale - base);
	}
	const uint64_t magnitude = static_cast<uint64_t>(-(value.scale + 1)) + 1;
	if (magnitude > static_cast<uint64_t>(std::numeric_limits<int64_t>::max()) - base) {
		return std::numeric_limits<int64_t>::max();
	}
	return static_cast<int64_t>(base + magnitude);
}

void append_utf8_codepoint(std::string &out, char32_t codepoint) {
	if (codepoint <= 0x7f) {
		out.push_back(static_cast<char>(codepoint));
	} else if (codepoint <= 0x7ff) {
		out.push_back(static_cast<char>(0xc0 | (codepoint >> 6)));
		out.push_back(static_cast<char>(0x80 | (codepoint & 0x3f)));
	} else if (codepoint <= 0xffff) {
		out.push_back(static_cast<char>(0xe0 | (codepoint >> 12)));
		out.push_back(static_cast<char>(0x80 | ((codepoint >> 6) & 0x3f)));
		out.push_back(static_cast<char>(0x80 | (codepoint & 0x3f)));
	} else {
		out.push_back(static_cast<char>(0xf0 | (codepoint >> 18)));
		out.push_back(static_cast<char>(0x80 | ((codepoint >> 12) & 0x3f)));
		out.push_back(static_cast<char>(0x80 | ((codepoint >> 6) & 0x3f)));
		out.push_back(static_cast<char>(0x80 | (codepoint & 0x3f)));
	}
}

std::string localize_digits(std::string_view ascii, char32_t zero_digit) {
	if (zero_digit == U'0') return std::string(ascii);
	std::string localized;
	localized.reserve(ascii.size());
	for (char value : ascii) {
		if (value >= '0' && value <= '9') {
			append_utf8_codepoint(localized, zero_digit + (value - '0'));
		} else {
			localized.push_back(value);
		}
	}
	return localized;
}

bool append_zeros(std::string &out, uint64_t count) {
	if (count > MAX_RESULT_BYTES || out.size() > MAX_RESULT_BYTES - static_cast<size_t>(count)) {
		return false;
	}
	out.append(static_cast<size_t>(count), '0');
	return true;
}

FormatStatus render_plain_body(
		const FormatDescriptor &descriptor,
		DecimalQuantity value,
		std::string &body
) {
	if (value.digits.size() > MAX_RESULT_BYTES) return FormatStatus::RESULT_TOO_LARGE;
	FormatStatus status = round_to_scale(value, descriptor.maximum_fraction_digits, descriptor.rounding);
	if (status != FormatStatus::OK) return status;
	const int64_t exponent = decimal_exponent(value);
	if (exponent == std::numeric_limits<int64_t>::max()) return FormatStatus::RESULT_TOO_LARGE;
	const int64_t integer_count = exponent + 1;
	std::string integer;
	std::string fraction;
	if (integer_count <= 0) {
		integer = "0";
		const uint64_t leading_fraction_zeros = uint64_t{0} - static_cast<uint64_t>(integer_count);
		if (!append_zeros(fraction, leading_fraction_zeros)) return FormatStatus::RESULT_TOO_LARGE;
		if (fraction.size() > MAX_RESULT_BYTES - value.digits.size()) return FormatStatus::RESULT_TOO_LARGE;
		fraction += value.digits;
	} else if (integer_count >= static_cast<int64_t>(value.digits.size())) {
		integer = value.digits;
		if (!append_zeros(integer, static_cast<uint64_t>(integer_count - value.digits.size()))) {
			return FormatStatus::RESULT_TOO_LARGE;
		}
	} else {
		integer = value.digits.substr(0, static_cast<size_t>(integer_count));
		fraction = value.digits.substr(static_cast<size_t>(integer_count));
	}

	while (fraction.size() > static_cast<size_t>(descriptor.minimum_fraction_digits) &&
			!fraction.empty() && fraction.back() == '0') {
		fraction.pop_back();
	}
	if (fraction.size() < static_cast<size_t>(descriptor.minimum_fraction_digits) &&
			!append_zeros(fraction, descriptor.minimum_fraction_digits - fraction.size())) {
		return FormatStatus::RESULT_TOO_LARGE;
	}
	if (integer.size() < static_cast<size_t>(descriptor.minimum_integer_digits)) {
		std::string padding(
			static_cast<size_t>(descriptor.minimum_integer_digits) - integer.size(),
			'0'
		);
		integer.insert(0, padding);
	}
	if (descriptor.minimum_integer_digits == 0 && integer == "0" && !fraction.empty()) {
		integer.clear();
	}

	std::string grouped;
	if (descriptor.grouping() && descriptor.grouping_size > 0 &&
			integer.size() > static_cast<size_t>(descriptor.grouping_size)) {
		const size_t grouping = static_cast<size_t>(descriptor.grouping_size);
		const size_t first = integer.size() % grouping == 0 ? grouping : integer.size() % grouping;
		grouped.append(integer, 0, first);
		for (size_t index = first; index < integer.size(); index += grouping) {
			grouped += descriptor.grouping_separator;
			grouped.append(integer, index, grouping);
		}
	} else {
		grouped = integer;
	}
	body = localize_digits(grouped, descriptor.zero_digit);
	if (!fraction.empty() || descriptor.decimal_always()) {
		body += descriptor.decimal_separator;
		body += localize_digits(fraction, descriptor.zero_digit);
	}
	return body.size() <= MAX_RESULT_BYTES ? FormatStatus::OK : FormatStatus::RESULT_TOO_LARGE;
}

int64_t floor_multiple(int64_t value, int32_t divisor) {
	const int64_t quotient = value / divisor;
	const int64_t remainder = value % divisor;
	return (remainder < 0 ? quotient - 1 : quotient) * divisor;
}

bool subtract_exact(int64_t left, int64_t right, int64_t &result) {
	if ((right > 0 && left < std::numeric_limits<int64_t>::min() + right) ||
			(right < 0 && left > std::numeric_limits<int64_t>::max() + right)) {
		return false;
	}
	result = left - right;
	return true;
}

bool scientific_displayed_exponent(
		const FormatDescriptor &descriptor,
		int64_t exponent,
		int64_t &displayed_exponent
) {
	if (descriptor.maximum_integer_digits > descriptor.minimum_integer_digits &&
			descriptor.maximum_integer_digits > 1) {
		displayed_exponent = floor_multiple(exponent, descriptor.maximum_integer_digits);
		return true;
	}
	return subtract_exact(
		exponent,
		static_cast<int64_t>(descriptor.minimum_integer_digits) - 1,
		displayed_exponent
	);
}

FormatStatus render_scientific_body(
		const FormatDescriptor &descriptor,
		DecimalQuantity value,
		std::string &body
) {
	const int64_t minimum_significant_digits =
		static_cast<int64_t>(descriptor.minimum_integer_digits) + descriptor.minimum_fraction_digits;
	const int64_t maximum_significant_digits = std::max<int64_t>(
		1,
		static_cast<int64_t>(descriptor.maximum_integer_digits) + descriptor.maximum_fraction_digits
	);
	int64_t exponent = value.zero() ? 0 : decimal_exponent(value);
	int64_t displayed_exponent = 0;
	if (!value.zero() && !scientific_displayed_exponent(descriptor, exponent, displayed_exponent)) {
		return FormatStatus::RESULT_TOO_LARGE;
	}
	for (int attempt = 0; attempt < 2; attempt++) {
		int64_t target_scale;
		if (!subtract_exact(maximum_significant_digits - 1, exponent, target_scale)) {
			return FormatStatus::RESULT_TOO_LARGE;
		}
		FormatStatus status = round_to_scale(value, target_scale, descriptor.rounding);
		if (status != FormatStatus::OK) return status;
		const int64_t rounded_exponent = value.zero() ? 0 : decimal_exponent(value);
		int64_t next_displayed = 0;
		if (!value.zero() && !scientific_displayed_exponent(
				descriptor,
				rounded_exponent,
				next_displayed
		)) {
			return FormatStatus::RESULT_TOO_LARGE;
		}
		if (next_displayed == displayed_exponent) break;
		exponent = rounded_exponent;
		displayed_exponent = next_displayed;
	}

	DecimalQuantity mantissa = value;
	if (!add_scale(mantissa.scale, displayed_exponent)) return FormatStatus::RESULT_TOO_LARGE;
	FormatDescriptor mantissa_descriptor = descriptor;
	mantissa_descriptor.flags &= ~1u;
	const int64_t integer_digits = value.zero()
		? descriptor.minimum_integer_digits
		: decimal_exponent(value) - displayed_exponent + 1;
	mantissa_descriptor.minimum_fraction_digits = static_cast<int32_t>(std::max<int64_t>(
		0,
		minimum_significant_digits - integer_digits
	));
	mantissa_descriptor.maximum_fraction_digits = static_cast<int32_t>(std::max<int64_t>(
		0,
		maximum_significant_digits - integer_digits
	));
	FormatStatus status = render_plain_body(mantissa_descriptor, mantissa, body);
	if (status != FormatStatus::OK) return status;
	if (value.zero() && minimum_significant_digits == 0) body.clear();
	body += descriptor.exponent_separator;
	uint64_t exponent_magnitude = displayed_exponent < 0
		? static_cast<uint64_t>(-(displayed_exponent + 1)) + 1
		: static_cast<uint64_t>(displayed_exponent);
	if (displayed_exponent < 0) body += descriptor.minus_sign;
	std::string exponent_digits = std::to_string(exponent_magnitude);
	if (exponent_digits.size() < static_cast<size_t>(descriptor.minimum_exponent_digits)) {
		exponent_digits.insert(
			0,
			static_cast<size_t>(descriptor.minimum_exponent_digits) - exponent_digits.size(),
			'0'
		);
	}
	body += localize_digits(exponent_digits, descriptor.zero_digit);
	return body.size() <= MAX_RESULT_BYTES ? FormatStatus::OK : FormatStatus::RESULT_TOO_LARGE;
}

FormatStatus render_finite(
		const FormatDescriptor &descriptor,
		const DecimalQuantity &source,
		std::string &out
);

FormatStatus render_fallback(
		const FormatDescriptor &descriptor,
		const DecimalQuantity &value,
		std::string &out
) {
	if (descriptor.fallback_size == 0) return FormatStatus::RESULT_TOO_LARGE;
	FormatDescriptor fallback{};
	if (!decode_descriptor(descriptor.fallback_data, descriptor.fallback_size, fallback)) {
		return FormatStatus::INVALID_DESCRIPTOR;
	}
	return render_finite(fallback, value, out);
}

FormatStatus render_finite(
		const FormatDescriptor &descriptor,
		const DecimalQuantity &source,
		std::string &out
) {
	DecimalQuantity working = source;
	bool milli_suffix = false;
	int compact_index = 0;
	if (descriptor.milli() && !working.zero()) {
		if (decimal_exponent(working) < 3) {
			milli_suffix = true;
		} else if (!add_scale(working.scale, 3)) {
			return FormatStatus::RESULT_TOO_LARGE;
		}
	}
	DecimalQuantity compact_base = working;
	if (descriptor.compact() && !working.zero()) {
		const int64_t exponent = decimal_exponent(working);
		if (exponent >= 3) {
			const int64_t candidate = exponent / 3;
			if (candidate >= static_cast<int64_t>(COMPACT_SUFFIXES.size())) {
				return render_fallback(descriptor, compact_base, out);
			}
			compact_index = static_cast<int>(candidate);
			if (!add_scale(working.scale, static_cast<int64_t>(compact_index) * 3)) {
				return FormatStatus::RESULT_TOO_LARGE;
			}
		}

		if (!milli_suffix) {
			DecimalQuantity promotion = working;
			const int multiplier_digits = std::max(0, multiplier_power10(descriptor.multiplier));
			FormatStatus status = round_to_scale(
				promotion,
				static_cast<int64_t>(descriptor.maximum_fraction_digits) + multiplier_digits,
				descriptor.rounding
			);
			if (status != FormatStatus::OK) return status;
			if (decimal_exponent(promotion) >= 3) {
				compact_index++;
				if (compact_index >= static_cast<int>(COMPACT_SUFFIXES.size())) {
					return render_fallback(descriptor, compact_base, out);
				}
				working = compact_base;
				if (!add_scale(working.scale, static_cast<int64_t>(compact_index) * 3)) {
					return FormatStatus::RESULT_TOO_LARGE;
				}
			}
		}
	}

	const int power = multiplier_power10(descriptor.multiplier);
	if (power >= 0) {
		if (!add_scale(working.scale, -power)) return FormatStatus::RESULT_TOO_LARGE;
	} else {
		multiply_digits(working.digits, static_cast<uint32_t>(descriptor.multiplier));
	}

	std::string body;
	FormatStatus status = descriptor.scientific()
		? render_scientific_body(descriptor, working, body)
		: render_plain_body(descriptor, working, body);
	if (status != FormatStatus::OK) return status;
	const std::string_view prefix = working.negative ? descriptor.negative_prefix : descriptor.positive_prefix;
	const std::string_view suffix = working.negative ? descriptor.negative_suffix : descriptor.positive_suffix;
	out.clear();
	out.reserve(prefix.size() + body.size() + descriptor.unit.size() + suffix.size() + 4);
	out += prefix;
	out += body;
	if (milli_suffix) {
		out += "m";
	} else if (compact_index > 0) {
		out += COMPACT_SUFFIXES[static_cast<size_t>(compact_index)];
	}
	out += descriptor.unit;
	out += suffix;
	return out.size() <= MAX_RESULT_BYTES ? FormatStatus::OK : FormatStatus::RESULT_TOO_LARGE;
}

}

FormatStatus render_number(
		const FormatDescriptor &descriptor,
		const DecimalQuantity &quantity,
		std::string &out
) {
	if (quantity.number_class == NumberClass::NAN_VALUE) {
		out.assign(descriptor.nan);
		return FormatStatus::OK;
	}
	if (quantity.number_class == NumberClass::INFINITY_VALUE) {
		out.clear();
		out += quantity.negative ? descriptor.negative_prefix : descriptor.positive_prefix;
		out += descriptor.infinity;
		out += descriptor.unit;
		out += quantity.negative ? descriptor.negative_suffix : descriptor.positive_suffix;
		return out.size() <= MAX_RESULT_BYTES ? FormatStatus::OK : FormatStatus::RESULT_TOO_LARGE;
	}
	return render_finite(descriptor, quantity, out);
}

}
