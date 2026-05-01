package com.modularmc.bigmath;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BigDeciTest {

	@Test
	void fromDouble() {
		try (BigDeci bd = BigDeci.fromDouble(3.14, 64)) {
			assertEquals(3.14, bd.toDouble(), 1e-10);
		}
	}

	@Test
	void fromString() {
		try (BigDeci bd = BigDeci.fromString("3.14", 64)) {
			assertEquals(3.14, bd.toDouble(), 1e-10);
		}
	}

	@Test
	void add() {
		try (BigDeci a = BigDeci.fromDouble(1.5, 64);
			BigDeci b = BigDeci.fromDouble(2.5, 64)) {
			try (BigDeci c = a.add(b)) {
				assertEquals(4.0, c.toDouble(), 1e-10);
			}
		}
	}

	@Test
	void subtract() {
		try (BigDeci a = BigDeci.fromDouble(5.0, 64);
			BigDeci b = BigDeci.fromDouble(3.5, 64)) {
			try (BigDeci c = a.subtract(b)) {
				assertEquals(1.5, c.toDouble(), 1e-10);
			}
		}
	}

	@Test
	void multiply() {
		try (BigDeci a = BigDeci.fromDouble(3.0, 64);
			BigDeci b = BigDeci.fromDouble(1.5, 64)) {
			try (BigDeci c = a.multiply(b)) {
				assertEquals(4.5, c.toDouble(), 1e-10);
			}
		}
	}

	@Test
	void divide() {
		try (BigDeci a = BigDeci.fromDouble(10.0, 64);
			BigDeci b = BigDeci.fromDouble(3.0, 64)) {
			try (BigDeci c = a.divide(b)) {
				assertEquals(10.0 / 3.0, c.toDouble(), 1e-10);
			}
		}
	}

	@Test
	void negate() {
		try (BigDeci a = BigDeci.fromDouble(3.14, 64)) {
			try (BigDeci b = a.negate()) {
				assertEquals(-3.14, b.toDouble(), 1e-8);
			}
		}
	}

	@Test
	void abs() {
		try (BigDeci a = BigDeci.fromDouble(-3.14, 64)) {
			try (BigDeci b = a.abs()) {
				assertEquals(3.14, b.toDouble(), 1e-8);
			}
		}
	}

	@Test
	void absOfPositive() {
		try (BigDeci a = BigDeci.fromDouble(3.14, 64)) {
			try (BigDeci b = a.abs()) {
				assertEquals(3.14, b.toDouble(), 1e-8);
			}
		}
	}

	@Test
	void sqrt() {
		try (BigDeci a = BigDeci.fromDouble(100.0, 64)) {
			try (BigDeci c = a.sqrt()) {
				assertEquals(10.0, c.toDouble(), 1e-10);
			}
		}
	}

	@Test
	void pow() {
		try (BigDeci base = BigDeci.fromDouble(2.0, 64);
			BigDeci exp = BigDeci.fromDouble(10.0, 64)) {
			try (BigDeci c = base.pow(exp)) {
				assertEquals(Math.pow(2.0, 10.0), c.toDouble(), 1e-6);
			}
		}
	}

	@Test
	void log() {
		try (BigDeci a = BigDeci.fromDouble(Math.E, 64)) {
			try (BigDeci c = a.log()) {
				assertEquals(1.0, c.toDouble(), 1e-10);
			}
		}
	}

	@Test
	void exp() {
		try (BigDeci a = BigDeci.fromDouble(1.0, 64)) {
			try (BigDeci c = a.exp()) {
				assertEquals(Math.E, c.toDouble(), 1e-10);
			}
		}
	}

	@Test
	void sin() {
		try (BigDeci a = BigDeci.fromDouble(Math.PI / 2, 64)) {
			try (BigDeci c = a.sin()) {
				assertEquals(1.0, c.toDouble(), 1e-8);
			}
		}
	}

	@Test
	void cos() {
		try (BigDeci a = BigDeci.fromDouble(0.0, 64)) {
			try (BigDeci c = a.cos()) {
				assertEquals(1.0, c.toDouble(), 1e-8);
			}
		}
	}

	@Test
	void tan() {
		try (BigDeci a = BigDeci.fromDouble(0.0, 64)) {
			try (BigDeci c = a.tan()) {
				assertEquals(0.0, c.toDouble(), 1e-8);
			}
		}
	}

	@Test
	void ceil() {
		try (BigDeci a = BigDeci.fromDouble(3.14, 64)) {
			try (BigDeci c = a.ceil()) {
				assertEquals(4.0, c.toDouble(), 1e-10);
			}
		}
	}

	@Test
	void floor() {
		try (BigDeci a = BigDeci.fromDouble(3.14, 64)) {
			try (BigDeci c = a.floor()) {
				assertEquals(3.0, c.toDouble(), 1e-10);
			}
		}
	}

	@Test
	void round() {
		try (BigDeci a = BigDeci.fromDouble(3.6, 64)) {
			try (BigDeci c = a.round()) {
				assertEquals(4.0, c.toDouble(), 1e-10);
			}
		}
	}

	@Test
	void compare() {
		try (BigDeci a = BigDeci.fromDouble(1.0, 64);
			BigDeci b = BigDeci.fromDouble(2.0, 64)) {
			assertTrue(a.compareTo(b) < 0);
			assertTrue(b.compareTo(a) > 0);
		}
	}

	@Test
	void fromBigInt() {
		try (BigInt bi = BigInt.fromLong(12345)) {
			try (BigDeci bd = BigDeci.fromBigInt(bi, 64)) {
				assertEquals(12345.0, bd.toDouble(), 1e-6);
			}
		}
	}

	@Test
	void formatting() {
		try (BigDeci bd = BigDeci.fromString("1234567.89", 64)) {
			assertEquals("1,234,567.89", bd.toFormattedString());
		}
	}

	@Test
	void formattingWithScale() {
		try (BigDeci bd = BigDeci.fromString("3.14159", 64)) {
			assertEquals("3.14", bd.toFormattedString(2));
		}
	}

	@Test
	void intValueTruncatesFraction() {
		try (BigDeci bd = BigDeci.fromDouble(42.7, 64)) {
			assertEquals(42, bd.intValue());
		}
	}

	@Test
	void intValueNegative() {
		try (BigDeci bd = BigDeci.fromDouble(-3.14, 64)) {
			assertEquals(-3, bd.intValue());
		}
	}

	@Test
	void intValueZero() {
		assertEquals(0, BigDeci.ZERO.intValue());
	}

	@Test
	void longValueLarge() {
		try (BigDeci bd = BigDeci.fromDouble(1e12, 64)) {
			assertEquals(1000000000000L, bd.longValue());
		}
	}

	@Test
	void longValueNegative() {
		try (BigDeci bd = BigDeci.fromDouble(-99.9, 64)) {
			assertEquals(-99L, bd.longValue());
		}
	}

	@Test
	void longValueZero() {
		assertEquals(0L, BigDeci.ZERO.longValue());
	}

	@Test
	void floatValuePrecise() {
		try (BigDeci bd = BigDeci.fromDouble(3.14, 64)) {
			assertEquals(3.14f, bd.floatValue(), 1e-6f);
		}
	}

	@Test
	void floatValueNegative() {
		try (BigDeci bd = BigDeci.fromDouble(-2.5, 64)) {
			assertEquals(-2.5f, bd.floatValue(), 1e-6f);
		}
	}

	@Test
	void floatValueZero() {
		assertEquals(0.0f, BigDeci.ZERO.floatValue(), 0.0f);
	}

	@Test
	void doubleValueHighPrecision() {
		try (BigDeci bd = BigDeci.fromDouble(3.1415926535, 128)) {
			assertEquals(3.1415926535, bd.doubleValue(), 1e-12);
		}
	}

	@Test
	void doubleValueNegative() {
		try (BigDeci bd = BigDeci.fromDouble(-1.5, 64)) {
			assertEquals(-1.5, bd.doubleValue(), 1e-10);
		}
	}

	@Test
	void doubleValueZero() {
		assertEquals(0.0, BigDeci.ZERO.doubleValue(), 0.0);
	}

	@Test
	void longValueFractionTruncation() {
		try (BigDeci bd = BigDeci.fromString("123.999", 64)) {
			assertEquals(123L, bd.longValue());
		}
	}
}
