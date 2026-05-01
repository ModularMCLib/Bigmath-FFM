package com.modularmc.bigmath.ffm;

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
	void fromLongNegative() {
		try (BigInt bi = BigInt.fromLong(-42)) {
			assertEquals("-42", bi.toString());
		}
	}

	@Test
	void fromLongZero() {
		try (BigInt bi = BigInt.fromLong(0)) {
			assertEquals("0", bi.toString());
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
	void mod() {
		try (BigInt a = BigInt.fromLong(100); BigInt b = BigInt.fromLong(30)) {
			try (BigInt c = a.mod(b)) {
				assertEquals("10", c.toString());
			}
		}
	}

	@Test
	void modExactDivision() {
		try (BigInt a = BigInt.fromLong(100); BigInt b = BigInt.fromLong(25)) {
			try (BigInt c = a.mod(b)) {
				assertEquals("0", c.toString());
			}
		}
	}

	@Test
	void pow() {
		try (BigInt a = BigInt.fromLong(2)) {
			try (BigInt c = a.pow(10)) {
				assertEquals("1024", c.toString());
			}
		}
	}

	@Test
	void powZero() {
		try (BigInt a = BigInt.fromLong(5)) {
			try (BigInt c = a.pow(0)) {
				assertEquals("1", c.toString());
			}
		}
	}

	@Test
	void negate() {
		try (BigInt a = BigInt.fromLong(42)) {
			try (BigInt b = a.negate()) {
				assertEquals("-42", b.toString());
			}
		}
	}

	@Test
	void negateTwice() {
		try (BigInt a = BigInt.fromLong(42)) {
			try (BigInt b = a.negate(); BigInt c = b.negate()) {
				assertEquals("42", c.toString());
			}
		}
	}

	@Test
	void abs() {
		try (BigInt a = BigInt.fromLong(-42)) {
			try (BigInt b = a.abs()) {
				assertEquals("42", b.toString());
			}
		}
	}

	@Test
	void absOfPositive() {
		try (BigInt a = BigInt.fromLong(42)) {
			try (BigInt b = a.abs()) {
				assertEquals("42", b.toString());
			}
		}
	}

	@Test
	void compare() {
		try (BigInt a = BigInt.fromLong(10); BigInt b = BigInt.fromLong(20)) {
			assertTrue(a.compareTo(b) < 0);
			assertTrue(b.compareTo(a) > 0);
			assertEquals(0, a.compareTo(a));
		}
	}

	@Test
	void signum() {
		try (BigInt a = BigInt.fromLong(-5)) {
			assertEquals(-1, a.signum());
		}
		try (BigInt a = BigInt.fromLong(0)) {
			assertEquals(0, a.signum());
		}
		try (BigInt a = BigInt.fromLong(5)) {
			assertEquals(1, a.signum());
		}
	}

	@Test
	void gcd() {
		try (BigInt a = BigInt.fromLong(48); BigInt b = BigInt.fromLong(18)) {
			try (BigInt c = a.gcd(b)) {
				assertEquals("6", c.toString());
			}
		}
	}

	@Test
	void gcdOfSelf() {
		try (BigInt a = BigInt.fromLong(7)) {
			try (BigInt c = a.gcd(a)) {
				assertEquals("7", c.toString());
			}
		}
	}

	@Test
	void lcm() {
		try (BigInt a = BigInt.fromLong(12); BigInt b = BigInt.fromLong(18)) {
			try (BigInt c = a.lcm(b)) {
				assertEquals("36", c.toString());
			}
		}
	}

	@Test
	void sqrt() {
		try (BigInt a = BigInt.fromLong(100)) {
			try (BigInt c = a.sqrt()) {
				assertEquals("10", c.toString());
			}
		}
	}

	@Test
	void sqrtNonPerfect() {
		try (BigInt a = BigInt.fromLong(101)) {
			try (BigInt c = a.sqrt()) {
				assertEquals("10", c.toString());
			}
		}
	}

	@Test
	void bitwiseAnd() {
		try (BigInt a = BigInt.fromLong(0xFF0F); BigInt b = BigInt.fromLong(0xF0FF)) {
			try (BigInt c = a.and(b)) {
				assertEquals(Integer.toString(0xFF0F & 0xF0FF), c.toString());
			}
		}
	}

	@Test
	void bitwiseOr() {
		try (BigInt a = BigInt.fromLong(0xFF0F); BigInt b = BigInt.fromLong(0xF0FF)) {
			try (BigInt c = a.or(b)) {
				assertEquals(Integer.toString(0xFF0F | 0xF0FF), c.toString());
			}
		}
	}

	@Test
	void bitwiseXor() {
		try (BigInt a = BigInt.fromLong(0xFF0F); BigInt b = BigInt.fromLong(0xF0FF)) {
			try (BigInt c = a.xor(b)) {
				assertEquals(Integer.toString(0xFF0F ^ 0xF0FF), c.toString());
			}
		}
	}

	@Test
	void shiftLeft() {
		try (BigInt a = BigInt.fromLong(1)) {
			try (BigInt c = a.shiftLeft(10)) {
				assertEquals("1024", c.toString());
			}
		}
	}

	@Test
	void shiftRight() {
		try (BigInt a = BigInt.fromLong(1024)) {
			try (BigInt c = a.shiftRight(3)) {
				assertEquals("128", c.toString());
			}
		}
	}

	@Test
	void isProbablyPrime() {
		try (BigInt a = BigInt.fromLong(17)) {
			assertTrue(a.isProbablyPrime(10));
		}
		try (BigInt a = BigInt.fromLong(100)) {
			assertFalse(a.isProbablyPrime(10));
		}
	}

	@Test
	void factorial() {
		try (BigInt f = BigInt.factorial(10)) {
			assertEquals("3628800", f.toString());
		}
	}

	@Test
	void factorialZero() {
		try (BigInt f = BigInt.factorial(0)) {
			assertEquals("1", f.toString());
		}
	}

	@Test
	void nextPrime() {
		try (BigInt a = BigInt.fromLong(20)) {
			try (BigInt c = a.nextPrime()) {
				assertEquals("23", c.toString());
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
	void fromBigInteger() {
		var javaBi = new java.math.BigInteger("99999999999999999999");
		try (BigInt bi = BigInt.fromBigInteger(javaBi)) {
			assertEquals(javaBi.toString(), bi.toString());
		}
	}

	@Test
	void toBigInteger() {
		try (BigInt bi = BigInt.fromString("12345678901234567890", 10)) {
			assertEquals(new java.math.BigInteger("12345678901234567890"), bi.toBigInteger());
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

	@Test
	void formatting() {
		try (BigInt bi = BigInt.fromString("1234567", 10)) {
			assertEquals("1,234,567", bi.toFormattedString());
		}
	}

	@Test
	void formattingLarge() {
		try (BigInt bi = BigInt.fromString("12345678901234567890", 10)) {
			assertEquals("12,345,678,901,234,567,890", bi.toFormattedString());
		}
	}

	@Test
	void formattingCustom() {
		try (BigInt bi = BigInt.fromString("12345678", 10)) {
			assertEquals("1234 5678", bi.toFormattedString(4, " "));
		}
	}
}
