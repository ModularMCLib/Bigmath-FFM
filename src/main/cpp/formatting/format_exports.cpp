#include "../bigmath_ffm.h"
#include "decimal_quantity.h"
#include "format_descriptor.h"
#include "number_renderer.h"

#include <cstdlib>
#include <cstring>
#include <limits>
#include <new>
#include <string>

namespace {

using bigmath::formatting::DecimalQuantity;
using bigmath::formatting::FormatDescriptor;
using bigmath::formatting::FormatStatus;
using bigmath::formatting::QuantityStatus;

void reset_result(BigmathFormatResult *result) {
	if (result != nullptr) {
		result->data = nullptr;
		result->size = 0;
	}
}

FormatStatus write_result(const std::string &value, BigmathFormatResult *result) {
	if (result == nullptr) return FormatStatus::INVALID_VALUE;
	auto *data = static_cast<char *>(std::malloc(value.size()));
	if (data == nullptr && !value.empty()) return FormatStatus::OUT_OF_MEMORY;
	if (!value.empty()) std::memcpy(data, value.data(), value.size());
	result->data = data;
	result->size = value.size();
	return FormatStatus::OK;
}

FormatStatus format_status(QuantityStatus status) {
	switch (status) {
		case QuantityStatus::OK:
			return FormatStatus::OK;
		case QuantityStatus::INVALID_DESCRIPTOR:
			return FormatStatus::INVALID_DESCRIPTOR;
		case QuantityStatus::INVALID_VALUE:
			return FormatStatus::INVALID_VALUE;
		case QuantityStatus::BACKEND_UNAVAILABLE:
			return FormatStatus::BACKEND_UNAVAILABLE;
		case QuantityStatus::RESULT_TOO_LARGE:
			return FormatStatus::RESULT_TOO_LARGE;
	}
	return FormatStatus::INTERNAL_ERROR;
}

template<typename Adapter>
int32_t format_value(
		const uint8_t *descriptor_data,
		uint32_t descriptor_size,
		BigmathFormatResult *result,
		Adapter adapter
) {
	reset_result(result);
	try {
		FormatDescriptor descriptor{};
		if (!bigmath::formatting::decode_descriptor(descriptor_data, descriptor_size, descriptor)) {
			return static_cast<int32_t>(FormatStatus::INVALID_DESCRIPTOR);
		}
		DecimalQuantity quantity;
		const FormatStatus adapter_status = adapter(descriptor, quantity);
		if (adapter_status != FormatStatus::OK) return static_cast<int32_t>(adapter_status);
		std::string rendered;
		const FormatStatus status = bigmath::formatting::render_number(descriptor, quantity, rendered);
		if (status != FormatStatus::OK) return static_cast<int32_t>(status);
		return static_cast<int32_t>(write_result(rendered, result));
	} catch (const std::bad_alloc &) {
		return static_cast<int32_t>(FormatStatus::OUT_OF_MEMORY);
	} catch (...) {
		return static_cast<int32_t>(FormatStatus::INTERNAL_ERROR);
	}
}

}

int32_t bigmath_format_bigint(
		const uint8_t *descriptor,
		uint32_t descriptor_size,
		BigIntHandle *value,
		BigmathFormatResult *result
) {
	return format_value(descriptor, descriptor_size, result, [value](
			const FormatDescriptor &,
			DecimalQuantity &quantity
	) {
		return bigmath::formatting::quantity_from_bigint(value, quantity)
			? FormatStatus::OK
			: FormatStatus::BACKEND_UNAVAILABLE;
	});
}

int32_t bigmath_format_bigdeci(
		const uint8_t *descriptor,
		uint32_t descriptor_size,
		BigDeciHandle *value,
		BigmathFormatResult *result
) {
	return format_value(descriptor, descriptor_size, result, [value](
			const FormatDescriptor &format,
			DecimalQuantity &quantity
	) {
		return format_status(bigmath::formatting::quantity_from_bigdeci(value, format, quantity));
	});
}

int32_t bigmath_format_int128(
		const uint8_t *descriptor,
		uint32_t descriptor_size,
		int64_t lo,
		int64_t hi,
		BigmathFormatResult *result
) {
	return format_value(descriptor, descriptor_size, result, [lo, hi](
			const FormatDescriptor &,
			DecimalQuantity &quantity
	) {
		return bigmath::formatting::quantity_from_int128(lo, hi, quantity)
			? FormatStatus::OK
			: FormatStatus::INVALID_VALUE;
	});
}

int32_t bigmath_format_i64(
		const uint8_t *descriptor,
		uint32_t descriptor_size,
		int64_t value,
		BigmathFormatResult *result
) {
	return format_value(descriptor, descriptor_size, result, [value](
			const FormatDescriptor &,
			DecimalQuantity &quantity
	) {
		return bigmath::formatting::quantity_from_i64(value, quantity)
			? FormatStatus::OK
			: FormatStatus::INVALID_VALUE;
	});
}

int32_t bigmath_format_f64(
		const uint8_t *descriptor,
		uint32_t descriptor_size,
		double value,
		BigmathFormatResult *result
) {
	return format_value(descriptor, descriptor_size, result, [value](
			const FormatDescriptor &,
			DecimalQuantity &quantity
	) {
		return bigmath::formatting::quantity_from_f64(value, quantity)
			? FormatStatus::OK
			: FormatStatus::INVALID_VALUE;
	});
}

int32_t bigmath_format_decimal(
		const uint8_t *descriptor,
		uint32_t descriptor_size,
		const uint8_t *unscaled,
		uint64_t unscaled_size,
		int64_t scale,
		BigmathFormatResult *result
) {
	if (unscaled_size > static_cast<uint64_t>(std::numeric_limits<size_t>::max())) {
		reset_result(result);
		return static_cast<int32_t>(FormatStatus::INVALID_VALUE);
	}
	return format_value(descriptor, descriptor_size, result, [=](
			const FormatDescriptor &,
			DecimalQuantity &quantity
	) {
		return bigmath::formatting::quantity_from_twos_complement(
			unscaled,
			static_cast<size_t>(unscaled_size),
			scale,
			quantity
		) ? FormatStatus::OK : FormatStatus::INVALID_VALUE;
	});
}

void bigmath_format_free(char *data) {
	std::free(data);
}
