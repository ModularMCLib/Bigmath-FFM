package net.modularmclib.bigmath.ffm;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BigDecimalTest {

	@Test
	void fromDouble() {
		try (BigDecimal bd = BigDecimal.fromDouble(3.14, 64)) {
			assertEquals(3.14, bd.toDouble(), 1e-10);
		}
	}

	@Test
	void fromString() {
		try (BigDecimal bd = BigDecimal.fromString("3.14", 64)) {
			assertEquals(3.14, bd.toDouble(), 1e-10);
		}
	}

	@Test
	void add() {
		try (BigDecimal a = BigDecimal.fromDouble(1.5, 64);
			BigDecimal b = BigDecimal.fromDouble(2.5, 64)) {
			try (BigDecimal c = a.add(b)) {
				assertEquals(4.0, c.toDouble(), 1e-10);
			}
		}
	}

	@Test
	void subtract() {
		try (BigDecimal a = BigDecimal.fromDouble(5.0, 64);
			BigDecimal b = BigDecimal.fromDouble(3.5, 64)) {
			try (BigDecimal c = a.subtract(b)) {
				assertEquals(1.5, c.toDouble(), 1e-10);
			}
		}
	}

	@Test
	void multiply() {
		try (BigDecimal a = BigDecimal.fromDouble(3.0, 64);
			BigDecimal b = BigDecimal.fromDouble(1.5, 64)) {
			try (BigDecimal c = a.multiply(b)) {
				assertEquals(4.5, c.toDouble(), 1e-10);
			}
		}
	}

	@Test
	void divide() {
		try (BigDecimal a = BigDecimal.fromDouble(10.0, 64);
			BigDecimal b = BigDecimal.fromDouble(3.0, 64)) {
			try (BigDecimal c = a.divide(b)) {
				assertEquals(10.0 / 3.0, c.toDouble(), 1e-10);
			}
		}
	}

	@Test
	void negate() {
		try (BigDecimal a = BigDecimal.fromDouble(3.14, 64)) {
			try (BigDecimal b = a.negate()) {
				assertEquals(-3.14, b.toDouble(), 1e-8);
			}
		}
	}

	@Test
	void abs() {
		try (BigDecimal a = BigDecimal.fromDouble(-3.14, 64)) {
			try (BigDecimal b = a.abs()) {
				assertEquals(3.14, b.toDouble(), 1e-8);
			}
		}
	}

	@Test
	void absOfPositive() {
		try (BigDecimal a = BigDecimal.fromDouble(3.14, 64)) {
			try (BigDecimal b = a.abs()) {
				assertEquals(3.14, b.toDouble(), 1e-8);
			}
		}
	}

	@Test
	void sqrt() {
		try (BigDecimal a = BigDecimal.fromDouble(100.0, 64)) {
			try (BigDecimal c = a.sqrt()) {
				assertEquals(10.0, c.toDouble(), 1e-10);
			}
		}
	}

	@Test
	void pow() {
		try (BigDecimal base = BigDecimal.fromDouble(2.0, 64);
			BigDecimal exp = BigDecimal.fromDouble(10.0, 64)) {
			try (BigDecimal c = base.pow(exp)) {
				assertEquals(Math.pow(2.0, 10.0), c.toDouble(), 1e-6);
			}
		}
	}

	@Test
	void log() {
		try (BigDecimal a = BigDecimal.fromDouble(Math.E, 64)) {
			try (BigDecimal c = a.log()) {
				assertEquals(1.0, c.toDouble(), 1e-10);
			}
		}
	}

	@Test
	void exp() {
		try (BigDecimal a = BigDecimal.fromDouble(1.0, 64)) {
			try (BigDecimal c = a.exp()) {
				assertEquals(Math.E, c.toDouble(), 1e-10);
			}
		}
	}

	@Test
	void sin() {
		try (BigDecimal a = BigDecimal.fromDouble(Math.PI / 2, 64)) {
			try (BigDecimal c = a.sin()) {
				assertEquals(1.0, c.toDouble(), 1e-8);
			}
		}
	}

	@Test
	void cos() {
		try (BigDecimal a = BigDecimal.fromDouble(0.0, 64)) {
			try (BigDecimal c = a.cos()) {
				assertEquals(1.0, c.toDouble(), 1e-8);
			}
		}
	}

	@Test
	void tan() {
		try (BigDecimal a = BigDecimal.fromDouble(0.0, 64)) {
			try (BigDecimal c = a.tan()) {
				assertEquals(0.0, c.toDouble(), 1e-8);
			}
		}
	}

	@Test
	void ceil() {
		try (BigDecimal a = BigDecimal.fromDouble(3.14, 64)) {
			try (BigDecimal c = a.ceil()) {
				assertEquals(4.0, c.toDouble(), 1e-10);
			}
		}
	}

	@Test
	void floor() {
		try (BigDecimal a = BigDecimal.fromDouble(3.14, 64)) {
			try (BigDecimal c = a.floor()) {
				assertEquals(3.0, c.toDouble(), 1e-10);
			}
		}
	}

	@Test
	void round() {
		try (BigDecimal a = BigDecimal.fromDouble(3.6, 64)) {
			try (BigDecimal c = a.round()) {
				assertEquals(4.0, c.toDouble(), 1e-10);
			}
		}
	}

	@Test
	void compare() {
		try (BigDecimal a = BigDecimal.fromDouble(1.0, 64);
			BigDecimal b = BigDecimal.fromDouble(2.0, 64)) {
			assertTrue(a.compareTo(b) < 0);
			assertTrue(b.compareTo(a) > 0);
		}
	}

	@Test
	void fromBigInt() {
		try (BigInt bi = BigInt.fromLong(12345)) {
			try (BigDecimal bd = BigDecimal.fromBigInt(bi, 64)) {
				assertEquals(12345.0, bd.toDouble(), 1e-6);
			}
		}
	}

	@Test
	void formatting() {
		try (BigDecimal bd = BigDecimal.fromString("1234567.89", 64)) {
			assertEquals("1,234,567.89", bd.toFormattedString());
		}
	}

	@Test
	void formattingWithScale() {
		try (BigDecimal bd = BigDecimal.fromString("3.14159", 64)) {
			assertEquals("3.14", bd.toFormattedString(2));
		}
	}
}
