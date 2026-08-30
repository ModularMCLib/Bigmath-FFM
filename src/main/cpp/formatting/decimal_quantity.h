#ifndef BIGMATH_DECIMAL_QUANTITY_H
#define BIGMATH_DECIMAL_QUANTITY_H

#include "../handles/native_backend.h"

#include <cstddef>
#include <cstdint>
#include <string>

namespace bigmath::formatting {

enum class NumberClass {
	FINITE,
	NAN_VALUE,
	INFINITY_VALUE
};

struct DecimalQuantity {
	NumberClass number_class = NumberClass::FINITE;
	bool negative = false;
	std::string digits = "0";
	int64_t scale = 0;

	bool zero() const { return number_class == NumberClass::FINITE && digits == "0"; }
};

bool quantity_from_bigint(BigIntHandle *value, DecimalQuantity &out);
bool quantity_from_bigdeci(BigDeciHandle *value, DecimalQuantity &out);
bool quantity_from_int128(int64_t lo, int64_t hi, DecimalQuantity &out);
bool quantity_from_i64(int64_t value, DecimalQuantity &out);
bool quantity_from_f64(double value, DecimalQuantity &out);
bool quantity_from_twos_complement(
	const uint8_t *bytes,
	size_t size,
	int64_t scale,
	DecimalQuantity &out
);

}

#endif
