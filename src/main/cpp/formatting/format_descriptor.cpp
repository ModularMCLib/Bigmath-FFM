#include "format_descriptor.h"

#include <array>
#include <limits>

namespace bigmath::formatting {
namespace {

constexpr uint32_t MAGIC = 0x32464e42u;
constexpr uint32_t VERSION = 1;
constexpr size_t STRING_COUNT = 12;
constexpr size_t HEADER_SIZE = 56 + STRING_COUNT * 8;

uint32_t read_u32(const uint8_t *data) {
	return static_cast<uint32_t>(data[0])
		| static_cast<uint32_t>(data[1]) << 8
		| static_cast<uint32_t>(data[2]) << 16
		| static_cast<uint32_t>(data[3]) << 24;
}

int32_t read_i32(const uint8_t *data) {
	return static_cast<int32_t>(read_u32(data));
}

}

bool decode_descriptor(const uint8_t *data, size_t size, FormatDescriptor &out) {
	if (data == nullptr || size < HEADER_SIZE ||
			read_u32(data) != MAGIC || read_u32(data + 4) != VERSION ||
			read_u32(data + 8) != size || read_u32(data + 52) != STRING_COUNT) {
		return false;
	}
	const uint32_t rounding = read_u32(data + 16);
	if (rounding > static_cast<uint32_t>(RoundingMode::UNNECESSARY)) {
		return false;
	}

	std::array<std::string_view, STRING_COUNT - 1> strings;
	const uint8_t *fallback_data = nullptr;
	size_t fallback_size = 0;
	for (size_t index = 0; index < STRING_COUNT; index++) {
		const size_t pair = 56 + index * 8;
		const uint32_t offset = read_u32(data + pair);
		const uint32_t length = read_u32(data + pair + 4);
		if (offset < HEADER_SIZE || offset > size || length > size - offset) {
			return false;
		}
		if (index + 1 == STRING_COUNT) {
			fallback_data = data + offset;
			fallback_size = length;
		} else {
			strings[index] = std::string_view(
				reinterpret_cast<const char *>(data + offset),
				length
			);
		}
	}

	out.flags = read_u32(data + 12);
	out.rounding = static_cast<RoundingMode>(rounding);
	out.multiplier = read_i32(data + 20);
	out.minimum_integer_digits = read_i32(data + 24);
	out.maximum_integer_digits = read_i32(data + 28);
	out.minimum_fraction_digits = read_i32(data + 32);
	out.maximum_fraction_digits = read_i32(data + 36);
	out.grouping_size = read_i32(data + 40);
	out.minimum_exponent_digits = read_i32(data + 44);
	out.zero_digit = static_cast<char32_t>(read_u32(data + 48));
	if (out.multiplier <= 0 || out.minimum_integer_digits < 0 ||
			out.maximum_integer_digits < out.minimum_integer_digits ||
			out.minimum_fraction_digits < 0 ||
			out.maximum_fraction_digits < out.minimum_fraction_digits ||
			out.grouping_size < 0 || out.minimum_exponent_digits < 0 ||
			out.zero_digit > 0x10ffff) {
		return false;
	}
	out.positive_prefix = strings[0];
	out.positive_suffix = strings[1];
	out.negative_prefix = strings[2];
	out.negative_suffix = strings[3];
	out.nan = strings[4];
	out.infinity = strings[5];
	out.decimal_separator = strings[6];
	out.grouping_separator = strings[7];
	out.exponent_separator = strings[8];
	out.minus_sign = strings[9];
	out.unit = strings[10];
	out.fallback_data = fallback_data;
	out.fallback_size = fallback_size;
	return true;
}

}
