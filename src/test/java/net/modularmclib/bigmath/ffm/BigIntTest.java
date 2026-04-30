package net.modularmclib.bigmath.ffm;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BigIntTest {

	@Test
	void fromLong() {
		try (BigInt bi = BigInt.fromLong(42)) {
			assertEquals("42", bi.toString());
		}
	}

	@Test
	void add() {
		try (BigInt a = BigInt.fromLong(10); BigInt b = BigInt.fromLong(32)) {
			try (BigInt c = a.add(b)) {
				assertEquals("42", c.toString());
			}
		}
	}

	@Test
	void subtract() {
		try (BigInt a = BigInt.fromLong(100); BigInt b = BigInt.fromLong(58)) {
			try (BigInt c = a.subtract(b)) {
				assertEquals("42", c.toString());
			}
		}
	}

	@Test
	void multiply() {
		try (BigInt a = BigInt.fromLong(6); BigInt b = BigInt.fromLong(7)) {
			try (BigInt c = a.multiply(b)) {
				assertEquals("42", c.toString());
			}
		}
	}

	@Test
	void divide() {
		try (BigInt a = BigInt.fromLong(84); BigInt b = BigInt.fromLong(2)) {
			try (BigInt c = a.divide(b)) {
				assertEquals("42", c.toString());
			}
		}
	}

	@Test
	void fromString() {
		try (BigInt bi = BigInt.fromString("12345678901234567890", 10)) {
			assertEquals("12345678901234567890", bi.toString());
		}
	}

	@Test
	void hexString() {
		try (BigInt bi = BigInt.fromString("ff", 16)) {
			assertEquals("255", bi.toString());
		}
	}

	@Test
	void largeMultiplication() {
		try (BigInt a = BigInt.fromString("99999999999999999999", 10);
			BigInt b = BigInt.fromString("99999999999999999999", 10)) {
			try (BigInt c = a.multiply(b)) {
				assertEquals("9999999999999999999800000000000000000001", c.toString());
			}
		}
	}
}
