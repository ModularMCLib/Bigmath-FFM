#ifndef BIGMATH_NUMBER_RENDERER_H
#define BIGMATH_NUMBER_RENDERER_H

#include "decimal_quantity.h"
#include "format_descriptor.h"

#include <string>

namespace bigmath::formatting {

enum class FormatStatus : int32_t {
	OK = 0,
	INVALID_DESCRIPTOR = 1,
	INVALID_VALUE = 2,
	BACKEND_UNAVAILABLE = 3,
	ROUNDING_NECESSARY = 4,
	OUT_OF_MEMORY = 5,
	RESULT_TOO_LARGE = 6,
	INTERNAL_ERROR = 7
};

FormatStatus render_number(
	const FormatDescriptor &descriptor,
	const DecimalQuantity &quantity,
	std::string &out
);

}

#endif
