#ifndef BIGMATH_FORMAT_DESCRIPTOR_H
#define BIGMATH_FORMAT_DESCRIPTOR_H

#include <cstddef>
#include <cstdint>
#include <string_view>

namespace bigmath::formatting {

enum class RoundingMode : uint32_t {
	UP,
	DOWN,
	CEILING,
	FLOOR,
	HALF_UP,
	HALF_DOWN,
	HALF_EVEN,
	UNNECESSARY
};

struct FormatDescriptor {
	uint32_t flags;
	RoundingMode rounding;
	int32_t multiplier;
	int32_t minimum_integer_digits;
	int32_t maximum_integer_digits;
	int32_t minimum_fraction_digits;
	int32_t maximum_fraction_digits;
	int32_t grouping_size;
	int32_t minimum_exponent_digits;
	char32_t zero_digit;
	std::string_view positive_prefix;
	std::string_view positive_suffix;
	std::string_view negative_prefix;
	std::string_view negative_suffix;
	std::string_view nan;
	std::string_view infinity;
	std::string_view decimal_separator;
	std::string_view grouping_separator;
	std::string_view exponent_separator;
	std::string_view minus_sign;
	std::string_view unit;
	const uint8_t *fallback_data;
	size_t fallback_size;

	bool grouping() const { return (flags & 1u) != 0; }
	bool decimal_always() const { return (flags & (1u << 1)) != 0; }
	bool scientific() const { return (flags & (1u << 2)) != 0; }
	bool compact() const { return (flags & (1u << 3)) != 0; }
	bool milli() const { return (flags & (1u << 4)) != 0; }
};

bool decode_descriptor(const uint8_t *data, size_t size, FormatDescriptor &out);

}

#endif
