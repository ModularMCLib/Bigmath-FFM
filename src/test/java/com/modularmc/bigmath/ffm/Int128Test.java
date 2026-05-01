package com.modularmc.bigmath.ffm;

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
	void mod() {
		try (Int128 a = Int128.fromLong(100); Int128 b = Int128.fromLong(30)) {
			try (Int128 c = a.mod(b)) {
				assertEquals("10", c.toString());
			}
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
	void hexString() {
		try (Int128 i = Int128.fromString("ff", 16)) {
			assertEquals("255", i.toString());
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
			// hi should be 0 for positive numbers that fit in 64 bits
			assertEquals(0, i.hi());
		}
	}

	@Test
	void intValuePositive() {
		try (Int128 i = Int128.fromLong(42)) {
			assertEquals(42, i.intValue());
		}
	}

	@Test
	void intValueNegative() {
		try (Int128 i = Int128.fromLong(-127)) {
			assertEquals(-127, i.intValue());
		}
	}

	@Test
	void intValueZero() {
		assertEquals(0, Int128.ZERO.intValue());
	}

	@Test
	void longValuePositive() {
		try (Int128 i = Int128.fromLong(42)) {
			assertEquals(42L, i.longValue());
		}
	}

	@Test
	void longValueNegative() {
		try (Int128 i = Int128.fromLong(-1000)) {
			assertEquals(-1000L, i.longValue());
		}
	}

	@Test
	void longValueMax() {
		try (Int128 i = Int128.fromLong(Long.MAX_VALUE)) {
			assertEquals(Long.MAX_VALUE, i.longValue());
		}
	}

	@Test
	void longValueMin() {
		try (Int128 i = Int128.fromLong(Long.MIN_VALUE)) {
			assertEquals(Long.MIN_VALUE, i.longValue());
		}
	}

	@Test
	void longValueZero() {
		assertEquals(0L, Int128.ZERO.longValue());
	}

	@Test
	void floatValuePositive() {
		try (Int128 i = Int128.fromLong(42)) {
			assertEquals(42.0f, i.floatValue(), 0.0f);
		}
	}

	@Test
	void floatValueNegative() {
		try (Int128 i = Int128.fromLong(-42)) {
			assertEquals(-42.0f, i.floatValue(), 0.0f);
		}
	}

	@Test
	void floatValueZero() {
		assertEquals(0.0f, Int128.ZERO.floatValue(), 0.0f);
	}

	@Test
	void doubleValuePositive() {
		try (Int128 i = Int128.fromLong(42)) {
			assertEquals(42.0, i.doubleValue(), 0.0);
		}
	}

	@Test
	void doubleValueNegative() {
		try (Int128 i = Int128.fromLong(-42)) {
			assertEquals(-42.0, i.doubleValue(), 0.0);
		}
	}

	@Test
	void doubleValueZero() {
		assertEquals(0.0, Int128.ZERO.doubleValue(), 0.0);
	}

	@Test
	void doubleValueLarge() {
		try (Int128 i = Int128.fromString("170141183460469231731687303715884105727", 10)) {
			assertTrue(i.doubleValue() > 1e38);
		}
	}
}
