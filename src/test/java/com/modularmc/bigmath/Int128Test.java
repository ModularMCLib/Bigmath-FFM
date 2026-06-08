package com.modularmc.bigmath;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Int128Test {

	@Test
	void fromLong() {
		try (Int128 i = Int128.fromLong(42)) {
			assertEquals(42, i.lo());
			assertEquals(0, i.hi());
			assertEquals("42", i.toString());
		}
	}

	@Test
	void fromNegativeLong() {
		try (Int128 i = Int128.fromLong(-42)) {
			assertEquals(-42, i.lo());
			assertEquals(-1, i.hi());
			assertEquals("-42", i.toString());
		}
	}

	@Test
	void add() {
		try (Int128 a = Int128.fromLong(10); Int128 b = Int128.fromLong(32)) {
			try (Int128 c = a.add(b)) {
				assertEquals("42", c.toString());
			}
		}
	}

	@Test
	void subtract() {
		try (Int128 a = Int128.fromLong(100); Int128 b = Int128.fromLong(58)) {
			try (Int128 c = a.subtract(b)) {
				assertEquals("42", c.toString());
			}
		}
	}

	@Test
	void multiply() {
		try (Int128 a = Int128.fromLong(6); Int128 b = Int128.fromLong(7)) {
			try (Int128 c = a.multiply(b)) {
				assertEquals("42", c.toString());
			}
		}
	}

	@Test
	void divide() {
		try (Int128 a = Int128.fromLong(84); Int128 b = Int128.fromLong(2)) {
			try (Int128 c = a.divide(b)) {
				assertEquals("42", c.toString());
			}
		}
	}

	@Test
	void divideLongMinByNegativeOneUsesWideResult() {
		try (Int128 a = Int128.fromLong(Long.MIN_VALUE); Int128 b = Int128.fromLong(-1)) {
			try (Int128 c = a.divide(b)) {
				assertEquals("9223372036854775808", c.toString());
			}
		}
	}

	@Test
	void divideWidePositiveValue() {
		try (Int128 a = Int128.fromString("123456789012345678901234567890", 10);
			Int128 b = Int128.fromLong(123456789)) {
			try (Int128 c = a.divide(b)) {
				assertEquals("1000000000100000000010", c.toString());
			}
		}
	}

	@Test
	void divideWideNegativeValue() {
		try (Int128 a = Int128.fromString("-123456789012345678901234567890", 10);
			Int128 b = Int128.fromLong(123456789)) {
			try (Int128 c = a.divide(b)) {
				assertEquals("-1000000000100000000010", c.toString());
			}
		}
	}

	@Test
	void divideWideByWideValue() {
		try (Int128 a = Int128.fromString("170141183460469231731687303715884105727", 10);
			Int128 b = Int128.fromString("18446744073709551616", 10)) {
			try (Int128 c = a.divide(b)) {
				assertEquals("9223372036854775807", c.toString());
			}
		}
	}

	@Test
	void divideWideByUnsignedLongValue() {
		try (Int128 a = Int128.fromString("170141183460469231731687303715884105727", 10);
			Int128 b = Int128.fromString("18446744073709551615", 10)) {
			try (Int128 c = a.divide(b)) {
				assertEquals("9223372036854775808", c.toString());
			}
		}
	}

	@Test
	void divideUnsignedLongBenchmarkCase() {
		try (Int128 a = Int128.fromString("12345678901234567890", 10);
			Int128 b = Int128.fromLong(987654321)) {
			try (Int128 c = a.divide(b)) {
				assertEquals("12499999887", c.toString());
			}
		}
	}

	@Test
	void divideMinNegativeInt128ByNegativeOneKeepsTwosComplementValue() {
		try (Int128 a = Int128.fromString("-170141183460469231731687303715884105728", 10);
			Int128 b = Int128.fromLong(-1)) {
			try (Int128 c = a.divide(b)) {
				assertEquals("-170141183460469231731687303715884105728", c.toString());
			}
		}
	}

	@Test
	void divideByZeroThrows() {
		try (Int128 a = Int128.fromLong(42)) {
			assertThrows(ArithmeticException.class, () -> a.divide(Int128.ZERO));
		}
	}

	@Test
	void mod() {
		try (Int128 a = Int128.fromLong(100); Int128 b = Int128.fromLong(30)) {
			try (Int128 c = a.mod(b)) {
				assertEquals("10", c.toString());
			}
		}
	}

	@Test
	void modWidePositiveValue() {
		try (Int128 a = Int128.fromString("123456789012345678901234567890", 10);
			Int128 b = Int128.fromLong(123456789)) {
			try (Int128 c = a.mod(b)) {
				assertEquals("0", c.toString());
			}
		}
	}

	@Test
	void modUnsignedLongBenchmarkCase() {
		try (Int128 a = Int128.fromString("12345678901234567890", 10);
			Int128 b = Int128.fromLong(987654321)) {
			try (Int128 c = a.mod(b)) {
				assertEquals("339506163", c.toString());
			}
		}
	}

	@Test
	void divideWideByUnsignedIntFastPath() {
		try (Int128 a = Int128.fromString("170141183460469231731687303715884105727", 10);
			Int128 b = Int128.fromLong(4294967295L)) {
			try (Int128 c = a.divide(b)) {
				assertEquals("39614081266355540835774234624", c.toString());
			}
		}
	}

	@Test
	void modWideByUnsignedIntFastPath() {
		try (Int128 a = Int128.fromString("170141183460469231731687303715884105727", 10);
			Int128 b = Int128.fromLong(4294967295L)) {
			try (Int128 c = a.mod(b)) {
				assertEquals("2147483647", c.toString());
			}
		}
	}

	@Test
	void modWideByUnsignedLongValue() {
		try (Int128 a = Int128.fromString("170141183460469231731687303715884105727", 10);
			Int128 b = Int128.fromString("18446744073709551615", 10)) {
			try (Int128 c = a.mod(b)) {
				assertEquals("9223372036854775807", c.toString());
			}
		}
	}

	@Test
	void modWideNegativeValueKeepsDividendSign() {
		try (Int128 a = Int128.fromString("-123456789012345678901234567891", 10);
			Int128 b = Int128.fromLong(123456789)) {
			try (Int128 c = a.mod(b)) {
				assertEquals("-1", c.toString());
			}
		}
	}

	@Test
	void modWidePositiveValueIgnoresDivisorSign() {
		try (Int128 a = Int128.fromString("12345678901234567890", 10);
			Int128 b = Int128.fromLong(-987654321)) {
			try (Int128 c = a.mod(b)) {
				assertEquals("339506163", c.toString());
			}
		}
	}

	@Test
	void modMinNegativeInt128ByNegativeOneIsZero() {
		try (Int128 a = Int128.fromString("-170141183460469231731687303715884105728", 10);
			Int128 b = Int128.fromLong(-1)) {
			try (Int128 c = a.mod(b)) {
				assertEquals("0", c.toString());
			}
		}
	}

	@Test
	void modByZeroThrows() {
		try (Int128 a = Int128.fromLong(42)) {
			assertThrows(ArithmeticException.class, () -> a.mod(Int128.ZERO));
		}
	}

	@Test
	void negate() {
		try (Int128 a = Int128.fromLong(42)) {
			try (Int128 b = a.negate()) {
				assertEquals("-42", b.toString());
			}
		}
	}

	@Test
	void abs() {
		try (Int128 a = Int128.fromLong(-42)) {
			try (Int128 b = a.abs()) {
				assertEquals("42", b.toString());
			}
		}
	}

	@Test
	void compare() {
		try (Int128 a = Int128.fromLong(10); Int128 b = Int128.fromLong(20)) {
			assertTrue(a.compareTo(b) < 0);
			assertTrue(b.compareTo(a) > 0);
			assertEquals(0, a.compareTo(a));
		}
	}

	@Test
	void signum() {
		try (Int128 a = Int128.fromLong(-5)) {
			assertEquals(-1, a.signum());
		}
		try (Int128 a = Int128.fromLong(0)) {
			assertEquals(0, a.signum());
		}
		try (Int128 a = Int128.fromLong(5)) {
			assertEquals(1, a.signum());
		}
	}

	@Test
	void fromString() {
		try (Int128 i = Int128.fromString("12345678901234567890", 10)) {
			assertEquals("12345678901234567890", i.toString());
		}
	}

	@Test
	void unsignedLongDecimalStringFastPath() {
		try (Int128 i = Int128.fromString("12345678901234567890", 10)) {
			assertEquals(0, i.hi());
			assertEquals("12345678901234567890", i.toString());
		}
	}

	@Test
	void hexString() {
		try (Int128 i = Int128.fromString("ff", 16)) {
			assertEquals("255", i.toString());
		}
	}

	@Test
	void uppercaseHexString() {
		try (Int128 i = Int128.fromString("FF", 16)) {
			assertEquals("255", i.toString());
		}
	}

	@Test
	void radix62RoundTrip() {
		try (Int128 i = Int128.fromString("Zz", 62)) {
			assertEquals("Zz", i.toString(62));
		}
	}

	@Test
	void maxPositiveInt128StringRoundTrip() {
		String value = "170141183460469231731687303715884105727";
		try (Int128 i = Int128.fromString(value, 10)) {
			assertEquals(value, i.toString());
			assertEquals("170,141,183,460,469,231,731,687,303,715,884,105,727", i.toFormattedString());
		}
	}

	@Test
	void minNegativeInt128StringRoundTrip() {
		String value = "-170141183460469231731687303715884105728";
		try (Int128 i = Int128.fromString(value, 10)) {
			assertEquals(value, i.toString());
			assertEquals("-170,141,183,460,469,231,731,687,303,715,884,105,728", i.toFormattedString());
		}
	}

	@Test
	void wideDecimalFormattingWithCustomGrouping() {
		try (Int128 i = Int128.fromString("170141183460469231731687303715884105727", 10)) {
			assertEquals("1701_41183_46046_92317_31687_30371_58841_05727", i.toFormattedString(5, "_"));
		}
	}

	@Test
	void largeMultiplication() {
		try (Int128 a = Int128.fromLong(1000000); Int128 b = Int128.fromLong(1000000)) {
			try (Int128 c = a.multiply(b)) {
				assertEquals("1000000000000", c.toString());
			}
		}
	}

	@Test
	void multiplyCarriesIntoHighWord() {
		try (Int128 a = Int128.fromLong(Long.MAX_VALUE); Int128 b = Int128.fromLong(2)) {
			try (Int128 c = a.multiply(b)) {
				assertEquals("18446744073709551614", c.toString());
			}
		}
	}

	@Test
	void multiplyNegativeSmallValues() {
		try (Int128 a = Int128.fromLong(-123456789); Int128 b = Int128.fromLong(987654321)) {
			try (Int128 c = a.multiply(b)) {
				assertEquals("-121932631112635269", c.toString());
			}
		}
	}

	@Test
	void formatSmallValueInJava() {
		try (Int128 i = Int128.fromLong(1234567890)) {
			assertEquals("1,234,567,890", i.toFormattedString());
			assertEquals("12_3456_7890", i.toFormattedString(4, "_"));
		}
	}

	@Test
	void formatUnsignedLongValue() {
		try (Int128 i = Int128.fromString("12345678901234567890", 10)) {
			assertEquals("12,345,678,901,234,567,890", i.toFormattedString());
			assertEquals("1234_5678_9012_3456_7890", i.toFormattedString(4, "_"));
		}
	}

	@Test
	void equalsAndHashCode() {
		try (Int128 a = Int128.fromLong(42); Int128 b = Int128.fromLong(42); Int128 c = Int128.fromLong(43)) {
			assertEquals(a, b);
			assertNotEquals(a, c);
			assertEquals(a.hashCode(), b.hashCode());
		}
	}

	@Test
	void loHi() {
		try (Int128 i = Int128.fromLong(0x1234567890abcdefL)) {
			assertEquals(0x1234567890abcdefL, i.lo());
			assertEquals(0, i.hi());
		}
	}

	@Test
	void intValue() {
		try (Int128 i = Int128.fromLong(42)) {
			assertEquals(42, i.intValue());
		}
	}

	@Test
	void longValue() {
		try (Int128 i = Int128.fromLong(Long.MAX_VALUE)) {
			assertEquals(Long.MAX_VALUE, i.longValue());
		}
	}

	@Test
	void doubleValue() {
		try (Int128 i = Int128.fromLong(42)) {
			assertEquals(42.0, i.doubleValue(), 0.0);
		}
	}

	@Test
	void floatValue() {
		try (Int128 i = Int128.fromLong(42)) {
			assertEquals(42.0f, i.floatValue(), 0.0f);
		}
	}
}
