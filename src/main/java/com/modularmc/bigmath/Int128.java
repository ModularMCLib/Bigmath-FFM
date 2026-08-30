package com.modularmc.bigmath;

/**
 * 128-bit signed integer value.
 * <p>
 * Hot-path small operations are implemented directly on two Java {@code long}
 * words. Native helpers remain for parsing.
 */
public final class Int128 extends Number implements AutoCloseable, Comparable<Int128> {

	static final long UNSIGNED_INT_MASK = 0xffff_ffffL;
	static final long UNSIGNED_INT_BASE = 1L << 32;
	static final long DECIMAL_CHUNK_BASE = 1_000_000_000L;
	static final int DECIMAL_CHUNK_DIGITS = 9;
	static final char[] DIGITS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
	static final char[] PADDED_THREE_DIGIT_TABLE = createPaddedThreeDigitTable();

	public static final Int128 ZERO = fromLong(0);
	public static final Int128 ONE = fromLong(1);
	public static final Int128 TWO = fromLong(2);
	public static final Int128 TEN = fromLong(10);
	public static final Int128 NEGATIVE_ONE = fromLong(-1);

	final long lo;
	final long hi;
	String cachedDecimalString;

	Int128(long lo, long hi) {
		this.lo = lo;
		this.hi = hi;
	}

	public static Int128 fromLong(long value) {
		return new Int128(value, value < 0 ? -1L : 0L);
	}

	public static Int128 fromString(String value, int radix) {
		if (radix < 2 || radix > DIGITS.length) {
			throw new IllegalArgumentException("radix must be between 2 and 62");
		}
		int index = 0;
		boolean negative = false;
		if (!value.isEmpty()) {
			char sign = value.charAt(0);
			if (sign == '-' || sign == '+') {
				negative = sign == '-';
				index = 1;
			}
		}
		long resultLo = 0;
		long resultHi = 0;
		for (; index < value.length(); index++) {
			int digit = digitValue(value.charAt(index), radix);
			if (digit < 0 || digit >= radix) {
				break;
			}
			long nextLo = resultLo * radix;
			long nextHi = Math.unsignedMultiplyHigh(resultLo, radix) + resultHi * radix;
			long withDigit = nextLo + digit;
			if (Long.compareUnsigned(withDigit, nextLo) < 0) {
				nextHi++;
			}
			resultLo = withDigit;
			resultHi = nextHi;
		}
		Int128 result = new Int128(resultLo, resultHi);
		return negative ? result.negate() : result;
	}

	static Int128 fromWords(long lo, long hi) {
		return new Int128(lo, hi);
	}

	public long lo() {
		return lo;
	}

	public long hi() {
		return hi;
	}

	public Int128 add(Int128 other) {
		long sumLo = lo + other.lo;
		long carry = Long.compareUnsigned(sumLo, lo) < 0 ? 1L : 0L;
		return new Int128(sumLo, hi + other.hi + carry);
	}

	public Int128 subtract(Int128 other) {
		long diffLo = lo - other.lo;
		long borrow = Long.compareUnsigned(lo, other.lo) < 0 ? 1L : 0L;
		return new Int128(diffLo, hi - other.hi - borrow);
	}

	public Int128 multiply(Int128 other) {
		long productLo = lo * other.lo;
		long productHi = Math.unsignedMultiplyHigh(lo, other.lo)
				+ lo * other.hi
				+ hi * other.lo;
		return new Int128(productLo, productHi);
	}

	public Int128 divide(Int128 other) {
		if (other.isZero()) {
			throw new ArithmeticException("/ by zero");
		}
		if (hi == 0 && other.hi == 0) {
			return fromWords(Long.divideUnsigned(lo, other.lo), 0);
		}
		if (fitsInLong() && other.fitsInLong() && !(lo == Long.MIN_VALUE && other.lo == -1L)) {
			return fromLong(lo / other.lo);
		}
		long dividendLo = absLo();
		long dividendHi = absHi();
		long divisorLo = other.absLo();
		long divisorHi = other.absHi();
		if (compareUnsigned(dividendLo, dividendHi, divisorLo, divisorHi) < 0) {
			return ZERO;
		}
		Int128 quotient;
		if (dividendHi == 0 && divisorHi == 0) {
			quotient = fromWords(Long.divideUnsigned(dividendLo, divisorLo), 0);
		} else if (divisorHi == 0 && Long.compareUnsigned(divisorLo, UNSIGNED_INT_MASK) <= 0) {
			quotient = unsignedDivideByUnsignedInt(dividendLo, dividendHi, divisorLo);
		} else if (divisorHi == 0) {
			quotient = unsignedDivideByUnsignedLong(dividendLo, dividendHi, divisorLo);
		} else {
			quotient = unsignedDivModNormalized(dividendLo, dividendHi, divisorLo, divisorHi, false);
		}
		return (hi < 0) != (other.hi < 0) ? quotient.negate() : quotient;
	}

	public Int128 mod(Int128 other) {
		if (other.isZero()) {
			throw new ArithmeticException("/ by zero");
		}
		if (hi == 0 && other.hi == 0) {
			return fromLong(Long.remainderUnsigned(lo, other.lo));
		}
		if (fitsInLong() && other.fitsInLong()) {
			return fromLong(lo % other.lo);
		}
		long dividendLo = absLo();
		long dividendHi = absHi();
		long divisorLo = other.absLo();
		long divisorHi = other.absHi();
		if (compareUnsigned(dividendLo, dividendHi, divisorLo, divisorHi) < 0) {
			return this;
		}
		Int128 remainder;
		if (dividendHi == 0 && divisorHi == 0) {
			remainder = fromLong(Long.remainderUnsigned(dividendLo, divisorLo));
		} else if (divisorHi == 0 && Long.compareUnsigned(divisorLo, UNSIGNED_INT_MASK) <= 0) {
			remainder = fromLong(unsignedRemainderByUnsignedInt(dividendLo, dividendHi, divisorLo));
		} else if (divisorHi == 0) {
			remainder = fromLong(unsignedRemainderByUnsignedLong(dividendLo, dividendHi, divisorLo));
		} else {
			remainder = unsignedDivModNormalized(dividendLo, dividendHi, divisorLo, divisorHi, true);
		}
		return hi < 0 ? remainder.negate() : remainder;
	}

	public Int128 negate() {
		long negLo = ~lo + 1;
		long negHi = ~hi + (negLo == 0 ? 1 : 0);
		return new Int128(negLo, negHi);
	}

	public Int128 abs() {
		return hi < 0 ? negate() : this;
	}

	@Override
	public int compareTo(Int128 other) {
		int hiCompare = Long.compare(hi, other.hi);
		if (hiCompare != 0) return hiCompare;
		return Long.compareUnsigned(lo, other.lo);
	}

	@Override
	public int intValue() {
		return (int) lo;
	}

	@Override
	public long longValue() {
		return lo;
	}

	@Override
	public float floatValue() {
		return (float) doubleValue();
	}

	@Override
	public double doubleValue() {
		if (hi == 0 && lo == 0) {
			return 0.0;
		}
		boolean negative = hi < 0;
		long magnitudeLo = negative ? ~lo + 1L : lo;
		long magnitudeHi = negative ? ~hi + (magnitudeLo == 0 ? 1L : 0L) : hi;
		double magnitude = Math.scalb(unsignedLongToDouble(magnitudeHi), 64)
				+ unsignedLongToDouble(magnitudeLo);
		return negative ? -magnitude : magnitude;
	}

	public int signum() {
		if (hi == 0 && lo == 0) return 0;
		return hi < 0 ? -1 : 1;
	}

	public String toString(int radix) {
		if (radix < 2 || radix > DIGITS.length) {
			throw new IllegalArgumentException("radix must be between 2 and 62");
		}
		if (radix == 10) {
			String cached = cachedDecimalString;
			if (cached != null) {
				return cached;
			}
			String value;
			if (fitsInLong()) {
				value = Long.toString(lo);
			} else if (hi == 0) {
				value = Long.toUnsignedString(lo);
			} else {
				value = toDecimalString();
			}
			cachedDecimalString = value;
			return value;
		}
		if (fitsInLong() && radix <= Character.MAX_RADIX) {
			return Long.toString(lo, radix);
		}
		return toStringJava(radix);
	}

	@Override
	public String toString() {
		String cached = cachedDecimalString;
		if (cached != null) {
			return cached;
		}
		String value;
		if (fitsInLong()) {
			value = Long.toString(lo);
		} else if (hi == 0) {
			value = Long.toUnsignedString(lo);
		} else {
			value = toDecimalString();
		}
		cachedDecimalString = value;
		return value;
	}

	@Override
	public void close() {
		// Value object; kept for source compatibility with previous AutoCloseable API.
	}

	boolean fitsInLong() {
		return hi == (lo < 0 ? -1L : 0L);
	}

	boolean isZero() {
		return hi == 0 && lo == 0;
	}

	long absLo() {
		return hi < 0 ? ~lo + 1L : lo;
	}

	long absHi() {
		if (hi >= 0) {
			return hi;
		}
		long magnitudeLo = ~lo + 1L;
		return ~hi + (magnitudeLo == 0 ? 1L : 0L);
	}

	String toStringJava(int radix) {
		char[] buffer = new char[130];
		int pos = writeDigits(buffer, radix);
		return new String(buffer, pos, buffer.length - pos);
	}

	String toDecimalString() {
		long magnitudeLo = lo;
		long magnitudeHi = hi;
		boolean negative = magnitudeHi < 0;
		if (negative) {
			magnitudeLo = ~magnitudeLo + 1L;
			magnitudeHi = ~magnitudeHi + (magnitudeLo == 0 ? 1L : 0L);
		}

		int[] chunks = new int[5];
		int chunkCount = 0;
		while (magnitudeHi != 0 || magnitudeLo != 0) {
			long remainder = 0;

			long part = magnitudeHi >>> 32;
			long qHiHigh = part / DECIMAL_CHUNK_BASE;
			remainder = part - qHiHigh * DECIMAL_CHUNK_BASE;

			part = (remainder << 32) | (magnitudeHi & UNSIGNED_INT_MASK);
			long qHiLow = part / DECIMAL_CHUNK_BASE;
			remainder = part - qHiLow * DECIMAL_CHUNK_BASE;

			part = (remainder << 32) | (magnitudeLo >>> 32);
			long qLoHigh = part / DECIMAL_CHUNK_BASE;
			remainder = part - qLoHigh * DECIMAL_CHUNK_BASE;

			part = (remainder << 32) | (magnitudeLo & UNSIGNED_INT_MASK);
			long qLoLow = part / DECIMAL_CHUNK_BASE;
			remainder = part - qLoLow * DECIMAL_CHUNK_BASE;

			chunks[chunkCount++] = (int) remainder;
			magnitudeHi = (qHiHigh << 32) | qHiLow;
			magnitudeLo = (qLoHigh << 32) | qLoLow;
		}

		int length = negative ? 1 : 0;
		int firstChunk = chunks[chunkCount - 1];
		length += decimalDigits(firstChunk) + (chunkCount - 1) * DECIMAL_CHUNK_DIGITS;
		char[] buffer = new char[length];
		int pos = 0;
		if (negative) {
			buffer[pos++] = '-';
		}
		pos = writeUnsignedInt(firstChunk, buffer, pos);
		for (int index = chunkCount - 2; index >= 0; index--) {
			writePaddedNineDigits(chunks[index], buffer, pos);
			pos += DECIMAL_CHUNK_DIGITS;
		}
		return new String(buffer);
	}

	int writeDigits(char[] buffer, int radix) {
		if (hi == 0 && lo == 0) {
			buffer[buffer.length - 1] = '0';
			return buffer.length - 1;
		}
		boolean negative = hi < 0;
		long magnitudeLo = lo;
		long magnitudeHi = hi;
		if (negative) {
			magnitudeLo = ~magnitudeLo + 1L;
			magnitudeHi = ~magnitudeHi + (magnitudeLo == 0 ? 1L : 0L);
		}

		int pos = buffer.length;
		while (magnitudeHi != 0 || magnitudeLo != 0) {
			long remainder = 0;

			long part = (remainder << 32) + (magnitudeHi >>> 32);
			long qHiHigh = part / radix;
			remainder = part - qHiHigh * radix;

			part = (remainder << 32) + (magnitudeHi & 0xffff_ffffL);
			long qHiLow = part / radix;
			remainder = part - qHiLow * radix;

			part = (remainder << 32) + (magnitudeLo >>> 32);
			long qLoHigh = part / radix;
			remainder = part - qLoHigh * radix;

			part = (remainder << 32) + (magnitudeLo & 0xffff_ffffL);
			long qLoLow = part / radix;
			remainder = part - qLoLow * radix;

			buffer[--pos] = DIGITS[(int) remainder];
			magnitudeHi = (qHiHigh << 32) | qHiLow;
			magnitudeLo = (qLoHigh << 32) | qLoLow;
		}
		if (negative) {
			buffer[--pos] = '-';
		}
		return pos;
	}

	static int digitValue(char value, int radix) {
		if (value >= '0' && value <= '9') {
			return value - '0';
		}
		if (value >= 'a' && value <= 'z') {
			return value - 'a' + 10;
		}
		if (value >= 'A' && value <= 'Z') {
			return radix <= 36 ? value - 'A' + 10 : value - 'A' + 36;
		}
		return -1;
	}

	static double unsignedLongToDouble(long value) {
		if (value >= 0) {
			return value;
		}
		return Math.scalb((double) (value >>> 1), 1) + (value & 1L);
	}

	static Int128 unsignedDivideByUnsignedInt(long dividendLo, long dividendHi, long divisor) {
		long remainder = 0;

		long part = dividendHi >>> 32;
		long qHiHigh = Long.divideUnsigned(part, divisor);
		remainder = part - qHiHigh * divisor;

		part = (remainder << 32) | (dividendHi & UNSIGNED_INT_MASK);
		long qHiLow = Long.divideUnsigned(part, divisor);
		remainder = part - qHiLow * divisor;

		part = (remainder << 32) | (dividendLo >>> 32);
		long qLoHigh = Long.divideUnsigned(part, divisor);
		remainder = part - qLoHigh * divisor;

		part = (remainder << 32) | (dividendLo & UNSIGNED_INT_MASK);
		long qLoLow = Long.divideUnsigned(part, divisor);

		return new Int128((qLoHigh << 32) | qLoLow, (qHiHigh << 32) | qHiLow);
	}

	static Int128 unsignedDivideByUnsignedLong(long dividendLo, long dividendHi, long divisor) {
		if (Long.compareUnsigned(dividendHi, divisor) < 0) {
			return new Int128(unsignedDivideByUnsignedLongLow(dividendLo, dividendHi, divisor), 0);
		}
		long quotientHi = Long.divideUnsigned(dividendHi, divisor);
		long remainderHi = dividendHi - quotientHi * divisor;
		long quotientLo = unsignedDivideByUnsignedLongLow(dividendLo, remainderHi, divisor);
		return new Int128(quotientLo, quotientHi);
	}

	static long unsignedDivideByUnsignedLongLow(long dividendLo, long dividendHi, long divisor) {
		return unsignedDivideAndRemainderByUnsignedLongLow(dividendLo, dividendHi, divisor, false);
	}

	static long unsignedRemainderByUnsignedInt(long dividendLo, long dividendHi, long divisor) {
		long remainder = 0;

		long part = dividendHi >>> 32;
		remainder = Long.remainderUnsigned(part, divisor);

		part = (remainder << 32) | (dividendHi & UNSIGNED_INT_MASK);
		remainder = Long.remainderUnsigned(part, divisor);

		part = (remainder << 32) | (dividendLo >>> 32);
		remainder = Long.remainderUnsigned(part, divisor);

		part = (remainder << 32) | (dividendLo & UNSIGNED_INT_MASK);
		return Long.remainderUnsigned(part, divisor);
	}

	static long unsignedRemainderByUnsignedLong(long dividendLo, long dividendHi, long divisor) {
		if (Long.compareUnsigned(dividendHi, divisor) < 0) {
			return unsignedRemainderByUnsignedLongLow(dividendLo, dividendHi, divisor);
		}
		long remainderHi = Long.remainderUnsigned(dividendHi, divisor);
		return unsignedRemainderByUnsignedLongLow(dividendLo, remainderHi, divisor);
	}

	static long unsignedRemainderByUnsignedLongLow(long dividendLo, long dividendHi, long divisor) {
		return unsignedDivideAndRemainderByUnsignedLongLow(dividendLo, dividendHi, divisor, true);
	}

	static long unsignedDivideAndRemainderByUnsignedLongLow(
			long dividendLo,
			long dividendHi,
			long divisor,
			boolean returnRemainder
	) {
		int shift = Long.numberOfLeadingZeros(divisor);
		long normalizedDivisor = divisor << shift;
		long divisorHigh = normalizedDivisor >>> 32;
		long divisorLow = normalizedDivisor & UNSIGNED_INT_MASK;
		long carry = shift == 0 ? 0 : dividendLo >>> (64 - shift);
		long numeratorHigh = (dividendHi << shift) | carry;
		long numeratorLow = dividendLo << shift;
		long numeratorMid = numeratorLow >>> 32;
		long numeratorLowWord = numeratorLow & UNSIGNED_INT_MASK;

		long quotientHigh = Long.divideUnsigned(numeratorHigh, divisorHigh);
		long remainderHat = numeratorHigh - quotientHigh * divisorHigh;
		while (Long.compareUnsigned(quotientHigh, UNSIGNED_INT_BASE) >= 0
				|| Long.compareUnsigned(quotientHigh * divisorLow, (remainderHat << 32) | numeratorMid) > 0) {
			quotientHigh--;
			remainderHat += divisorHigh;
			if (Long.compareUnsigned(remainderHat, UNSIGNED_INT_BASE) >= 0) {
				break;
			}
		}

		long numeratorCombined = (numeratorHigh << 32) + numeratorMid - quotientHigh * normalizedDivisor;
		long quotientLow = Long.divideUnsigned(numeratorCombined, divisorHigh);
		remainderHat = numeratorCombined - quotientLow * divisorHigh;
		while (Long.compareUnsigned(quotientLow, UNSIGNED_INT_BASE) >= 0
				|| Long.compareUnsigned(quotientLow * divisorLow, (remainderHat << 32) | numeratorLowWord) > 0) {
			quotientLow--;
			remainderHat += divisorHigh;
			if (Long.compareUnsigned(remainderHat, UNSIGNED_INT_BASE) >= 0) {
				break;
			}
		}

		if (!returnRemainder) {
			return (quotientHigh << 32) | (quotientLow & UNSIGNED_INT_MASK);
		}

		long normalizedRemainder = (numeratorCombined << 32) + numeratorLowWord - quotientLow * normalizedDivisor;
		return shift == 0 ? normalizedRemainder : normalizedRemainder >>> shift;
	}

	static int decimalDigits(int value) {
		if (value >= 100_000_000) return 9;
		if (value >= 10_000_000) return 8;
		if (value >= 1_000_000) return 7;
		if (value >= 100_000) return 6;
		if (value >= 10_000) return 5;
		if (value >= 1_000) return 4;
		if (value >= 100) return 3;
		if (value >= 10) return 2;
		return 1;
	}

	static int writeUnsignedInt(int value, char[] buffer, int pos) {
		int digits = decimalDigits(value);
		int end = pos + digits;
		int cursor = end;
		int current = value;
		do {
			int quotient = current / 10;
			buffer[--cursor] = (char) ('0' + (current - quotient * 10));
			current = quotient;
		} while (current != 0);
		return end;
	}

	static void writePaddedNineDigits(int value, char[] buffer, int pos) {
		int high = value / 1_000_000;
		int remainder = value - high * 1_000_000;
		int middle = remainder / 1_000;
		int low = remainder - middle * 1_000;
		copyPaddedThreeDigits(high, buffer, pos);
		copyPaddedThreeDigits(middle, buffer, pos + 3);
		copyPaddedThreeDigits(low, buffer, pos + 6);
	}

	static Int128 unsignedDivModNormalized(
			long dividendLo,
			long dividendHi,
			long divisorLo,
			long divisorHi,
			boolean returnRemainder
	) {
		int shift = Long.numberOfLeadingZeros(divisorHi);
		long normalizedDivisorHi;
		long normalizedDivisorLo;
		long numeratorTop;
		long normalizedDividendHi;
		long normalizedDividendLo;
		if (shift == 0) {
			normalizedDivisorHi = divisorHi;
			normalizedDivisorLo = divisorLo;
			numeratorTop = 0;
			normalizedDividendHi = dividendHi;
			normalizedDividendLo = dividendLo;
		} else {
			normalizedDivisorHi = (divisorHi << shift) | (divisorLo >>> (64 - shift));
			normalizedDivisorLo = divisorLo << shift;
			numeratorTop = dividendHi >>> (64 - shift);
			normalizedDividendHi = (dividendHi << shift) | (dividendLo >>> (64 - shift));
			normalizedDividendLo = dividendLo << shift;
		}

		long quotient = unsignedDivideByUnsignedLongLow(
				normalizedDividendHi,
				numeratorTop,
				normalizedDivisorHi
		);
		long remainderHat = unsignedRemainderByUnsignedLongLow(
				normalizedDividendHi,
				numeratorTop,
				normalizedDivisorHi
		);
		for (int correction = 0; correction < 3; correction++) {
			long productLo = quotient * normalizedDivisorLo;
			long productHi = Math.unsignedMultiplyHigh(quotient, normalizedDivisorLo);
			if (Long.compareUnsigned(productHi, remainderHat) < 0 ||
					(productHi == remainderHat &&
						Long.compareUnsigned(productLo, normalizedDividendLo) <= 0)) {
				break;
			}
			quotient--;
			long previous = remainderHat;
			remainderHat += normalizedDivisorHi;
			if (Long.compareUnsigned(remainderHat, previous) < 0) {
				break;
			}
		}

		int corrections = 0;
		long productLo;
		long productHi;
		while (true) {
			productLo = quotient * divisorLo;
			long lowProductHi = Math.unsignedMultiplyHigh(quotient, divisorLo);
			long highProductLo = quotient * divisorHi;
			productHi = lowProductHi + highProductLo;
			boolean overflow = Math.unsignedMultiplyHigh(quotient, divisorHi) != 0 ||
					Long.compareUnsigned(productHi, lowProductHi) < 0;
			if (!overflow && compareUnsigned(productLo, productHi, dividendLo, dividendHi) <= 0) {
				break;
			}
			if (++corrections > 3) {
				return unsignedDivMod(dividendLo, dividendHi, divisorLo, divisorHi, returnRemainder);
			}
			quotient--;
		}

		long remainderLo = dividendLo - productLo;
		long borrow = Long.compareUnsigned(dividendLo, productLo) < 0 ? 1L : 0L;
		long remainderHi = dividendHi - productHi - borrow;
		while (compareUnsigned(remainderLo, remainderHi, divisorLo, divisorHi) >= 0) {
			long nextLo = remainderLo - divisorLo;
			borrow = Long.compareUnsigned(remainderLo, divisorLo) < 0 ? 1L : 0L;
			remainderHi = remainderHi - divisorHi - borrow;
			remainderLo = nextLo;
			quotient++;
			if (++corrections > 4) {
				return unsignedDivMod(dividendLo, dividendHi, divisorLo, divisorHi, returnRemainder);
			}
		}
		return returnRemainder
				? new Int128(remainderLo, remainderHi)
				: new Int128(quotient, 0);
	}

	static Int128 unsignedDivMod(
			long dividendLo,
			long dividendHi,
			long divisorLo,
			long divisorHi,
			boolean returnRemainder
	) {
		long quotientLo = 0;
		long quotientHi = 0;
		long remainderLo = 0;
		long remainderHi = 0;

		for (int bitIndex = 127; bitIndex >= 0; bitIndex--) {
			long incomingBit = bitIndex >= 64
					? (dividendHi >>> (bitIndex - 64)) & 1L
					: (dividendLo >>> bitIndex) & 1L;
			remainderHi = (remainderHi << 1) | (remainderLo >>> 63);
			remainderLo = (remainderLo << 1) | incomingBit;

			if (compareUnsigned(remainderLo, remainderHi, divisorLo, divisorHi) >= 0) {
				long diffLo = remainderLo - divisorLo;
				long borrow = Long.compareUnsigned(remainderLo, divisorLo) < 0 ? 1L : 0L;
				remainderHi = remainderHi - divisorHi - borrow;
				remainderLo = diffLo;
				if (bitIndex >= 64) {
					quotientHi |= 1L << (bitIndex - 64);
				} else {
					quotientLo |= 1L << bitIndex;
				}
			}
		}
		return returnRemainder
				? new Int128(remainderLo, remainderHi)
				: new Int128(quotientLo, quotientHi);
	}

	static int compareUnsigned(long leftLo, long leftHi, long rightLo, long rightHi) {
		int high = Long.compareUnsigned(leftHi, rightHi);
		return high != 0 ? high : Long.compareUnsigned(leftLo, rightLo);
	}

	static void copyPaddedThreeDigits(int value, char[] buffer, int pos) {
		int source = value * 3;
		buffer[pos] = PADDED_THREE_DIGIT_TABLE[source];
		buffer[pos + 1] = PADDED_THREE_DIGIT_TABLE[source + 1];
		buffer[pos + 2] = PADDED_THREE_DIGIT_TABLE[source + 2];
	}

	static char[] createPaddedThreeDigitTable() {
		char[] table = new char[1_000 * 3];
		for (int value = 0; value < 1_000; value++) {
			int pos = value * 3;
			table[pos] = (char) ('0' + value / 100);
			table[pos + 1] = (char) ('0' + (value / 10) % 10);
			table[pos + 2] = (char) ('0' + value % 10);
		}
		return table;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof Int128 other)) return false;
		return lo == other.lo && hi == other.hi;
	}

	@Override
	public int hashCode() {
		return Long.hashCode(lo) ^ Long.hashCode(hi);
	}
}
