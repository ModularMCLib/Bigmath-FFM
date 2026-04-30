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
	void add() {
		try (BigDecimal a = BigDecimal.fromDouble(1.5, 64);
			BigDecimal b = BigDecimal.fromDouble(2.5, 64)) {
			try (BigDecimal c = a.add(b)) {
				assertEquals(4.0, c.toDouble(), 1e-10);
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
}
