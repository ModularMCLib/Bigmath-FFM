package com.modularmc.bigmath;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * 128-bit signed integer value.
 * <p>
 * Hot-path small operations are implemented directly on two Java {@code long}
 * words. Native helpers remain for parsing.
 */
public final class Int128 extends Number implements AutoCloseable, Comparable<Int128> {

	private static final long STRUCT_SIZE = 16L;
	private static final long UNSIGNED_INT_MASK = 0xffff_ffffL;
	private static final long DECIMAL_CHUNK_BASE = 1_000_000_000L;
	private static final int DECIMAL_CHUNK_DIGITS = 9;
	private static final char[] DIGITS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
	private static final MethodHandle INT128_FROM_STRING_HANDLE = BigmathFFM.getInstance().downcall(
			"int128_from_string",
			FunctionDescriptors.INT128_FROM_STRING
	);

	public static final Int128 ZERO = fromLong(0);
	public static final Int128 ONE = fromLong(1);
	public static final Int128 TWO = fromLong(2);
	public static final Int128 TEN = fromLong(10);
	public static final Int128 NEGATIVE_ONE = fromLong(-1);

	private final long lo;
	private final long hi;
	private String cachedDecimalString;
	private String cachedFormattedDecimalString;

	private Int128(long lo, long hi) {
		this.lo = lo;
		this.hi = hi;
	}

	public static Int128 fromLong(long value) {
		return new Int128(value, value < 0 ? -1L : 0L);
	}

	public static Int128 fromString(String value, int radix) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment ptr = arena.allocate(STRUCT_SIZE);
			MemorySegment str = arena.allocateFrom(value, java.nio.charset.StandardCharsets.UTF_8);
			invokeOutAddressInt(ptr, str, radix);
			return fromSegment(ptr);
		}
	}

	static Int128 fromWords(long lo, long hi) {
		return new Int128(lo, hi);
	}

	static Int128 fromSegment(MemorySegment value) {
		return new Int128(
				value.get(ValueLayout.JAVA_LONG, 0),
				value.get(ValueLayout.JAVA_LONG, 8)
		);
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
		Int128 quotient;
		if (dividendHi == 0 && divisorHi == 0) {
			quotient = fromWords(Long.divideUnsigned(dividendLo, divisorLo), 0);
		} else if (divisorHi == 0 && Long.compareUnsigned(divisorLo, UNSIGNED_INT_MASK) <= 0) {
			quotient = unsignedDivideByUnsignedInt(dividendLo, dividendHi, divisorLo);
		} else {
			quotient = unsignedDivMod(dividendLo, dividendHi, divisorLo, divisorHi, false);
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
		Int128 remainder;
		if (dividendHi == 0 && divisorHi == 0) {
			remainder = fromLong(Long.remainderUnsigned(dividendLo, divisorLo));
		} else if (divisorHi == 0 && Long.compareUnsigned(divisorLo, UNSIGNED_INT_MASK) <= 0) {
			remainder = fromLong(unsignedRemainderByUnsignedInt(dividendLo, dividendHi, divisorLo));
		} else {
			remainder = unsignedDivMod(dividendLo, dividendHi, divisorLo, divisorHi, true);
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
		return Double.parseDouble(toString());
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

	public String toFormattedString() {
		String cached = cachedFormattedDecimalString;
		if (cached != null) {
			return cached;
		}
		String value = formatGroupedDecimalDefault(toString());
		cachedFormattedDecimalString = value;
		return value;
	}

	public String toFormattedString(int groupSize, String groupSep) {
		if (groupSize <= 0 || groupSep == null || groupSep.isEmpty()) {
			return toString();
		}
		if (groupSize == 3 && groupSep.equals(",")) {
			return toFormattedString();
		}
		if (fitsInLong()) {
			return formatGroupedDecimal(Long.toString(lo), groupSize, groupSep);
		}
		if (hi == 0) {
			return formatGroupedDecimal(Long.toUnsignedString(lo), groupSize, groupSep);
		}
		if (groupSep.equals(",") && groupSize == 3) {
			return toFormattedDecimalString(groupSize, groupSep);
		}
		return toFormattedStringJava(groupSize, groupSep);
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

	private boolean fitsInLong() {
		return hi == (lo < 0 ? -1L : 0L);
	}

	private boolean isZero() {
		return hi == 0 && lo == 0;
	}

	private long absLo() {
		return hi < 0 ? ~lo + 1L : lo;
	}

	private long absHi() {
		if (hi >= 0) {
			return hi;
		}
		long magnitudeLo = ~lo + 1L;
		return ~hi + (magnitudeLo == 0 ? 1L : 0L);
	}

	private String toStringJava(int radix) {
		char[] buffer = new char[130];
		int pos = writeDigits(buffer, radix);
		return new String(buffer, pos, buffer.length - pos);
	}

	private String toDecimalString() {
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
			long qHiHigh = Long.divideUnsigned(part, DECIMAL_CHUNK_BASE);
			remainder = Long.remainderUnsigned(part, DECIMAL_CHUNK_BASE);

			part = (remainder << 32) | (magnitudeHi & UNSIGNED_INT_MASK);
			long qHiLow = Long.divideUnsigned(part, DECIMAL_CHUNK_BASE);
			remainder = Long.remainderUnsigned(part, DECIMAL_CHUNK_BASE);

			part = (remainder << 32) | (magnitudeLo >>> 32);
			long qLoHigh = Long.divideUnsigned(part, DECIMAL_CHUNK_BASE);
			remainder = Long.remainderUnsigned(part, DECIMAL_CHUNK_BASE);

			part = (remainder << 32) | (magnitudeLo & UNSIGNED_INT_MASK);
			long qLoLow = Long.divideUnsigned(part, DECIMAL_CHUNK_BASE);
			remainder = Long.remainderUnsigned(part, DECIMAL_CHUNK_BASE);

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

	private String toFormattedDecimalString(int groupSize, String groupSep) {
		return formatGroupedDecimal(toDecimalString(), groupSize, groupSep);
	}

	private String toFormattedStringJava(int groupSize, String groupSep) {
		char[] raw = new char[130];
		int pos = writeDigits(raw, 10);
		boolean negative = raw[pos] == '-';
		int digitStart = negative ? pos + 1 : pos;
		int digits = raw.length - digitStart;
		if (digits <= groupSize) {
			return new String(raw, pos, raw.length - pos);
		}

		int sepLength = groupSep.length();
		int sepCount = (digits - 1) / groupSize;
		char[] formatted = new char[(negative ? 1 : 0) + digits + sepCount * sepLength];
		int out = 0;
		if (negative) {
			formatted[out++] = '-';
		}
		int firstGroup = digits % groupSize;
		if (firstGroup == 0) {
			firstGroup = groupSize;
		}
		System.arraycopy(raw, digitStart, formatted, out, firstGroup);
		out += firstGroup;
		int in = digitStart + firstGroup;
		while (in < raw.length) {
			groupSep.getChars(0, sepLength, formatted, out);
			out += sepLength;
			System.arraycopy(raw, in, formatted, out, groupSize);
			out += groupSize;
			in += groupSize;
		}
		return new String(formatted);
	}

	private int writeDigits(char[] buffer, int radix) {
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

	private static String formatGroupedDecimal(String value, int groupSize, String groupSep) {
		int signOffset = value.startsWith("-") ? 1 : 0;
		int digits = value.length() - signOffset;
		if (digits <= groupSize) {
			return value;
		}
		StringBuilder sb = new StringBuilder(value.length() + (digits - 1) / groupSize * groupSep.length());
		if (signOffset != 0) {
			sb.append('-');
		}
		int firstGroup = digits % groupSize;
		if (firstGroup == 0) {
			firstGroup = groupSize;
		}
		int index = signOffset;
		sb.append(value, index, index + firstGroup);
		index += firstGroup;
		while (index < value.length()) {
			sb.append(groupSep);
			sb.append(value, index, index + groupSize);
			index += groupSize;
		}
		return sb.toString();
	}

	private static String formatGroupedDecimalDefault(String value) {
		int signOffset = value.startsWith("-") ? 1 : 0;
		int digits = value.length() - signOffset;
		if (digits <= 3) {
			return value;
		}
		int sepCount = (digits - 1) / 3;
		char[] formatted = new char[value.length() + sepCount];
		int out = 0;
		if (signOffset != 0) {
			formatted[out++] = '-';
		}
		int firstGroup = digits % 3;
		if (firstGroup == 0) {
			firstGroup = 3;
		}
		value.getChars(signOffset, signOffset + firstGroup, formatted, out);
		out += firstGroup;
		int in = signOffset + firstGroup;
		while (in < value.length()) {
			formatted[out++] = ',';
			value.getChars(in, in + 3, formatted, out);
			out += 3;
			in += 3;
		}
		return new String(formatted);
	}

	private static Int128 unsignedDivideByUnsignedInt(long dividendLo, long dividendHi, long divisor) {
		long remainder = 0;

		long part = dividendHi >>> 32;
		long qHiHigh = Long.divideUnsigned(part, divisor);
		remainder = Long.remainderUnsigned(part, divisor);

		part = (remainder << 32) | (dividendHi & UNSIGNED_INT_MASK);
		long qHiLow = Long.divideUnsigned(part, divisor);
		remainder = Long.remainderUnsigned(part, divisor);

		part = (remainder << 32) | (dividendLo >>> 32);
		long qLoHigh = Long.divideUnsigned(part, divisor);
		remainder = Long.remainderUnsigned(part, divisor);

		part = (remainder << 32) | (dividendLo & UNSIGNED_INT_MASK);
		long qLoLow = Long.divideUnsigned(part, divisor);

		return new Int128((qLoHigh << 32) | qLoLow, (qHiHigh << 32) | qHiLow);
	}

	private static long unsignedRemainderByUnsignedInt(long dividendLo, long dividendHi, long divisor) {
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

	private static int decimalDigits(int value) {
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

	private static int writeUnsignedInt(int value, char[] buffer, int pos) {
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

	private static void writePaddedNineDigits(int value, char[] buffer, int pos) {
		int current = value;
		for (int index = pos + DECIMAL_CHUNK_DIGITS - 1; index >= pos; index--) {
			int quotient = current / 10;
			buffer[index] = (char) ('0' + (current - quotient * 10));
			current = quotient;
		}
	}

	private static Int128 unsignedDivMod(
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

	private static int compareUnsigned(long leftLo, long leftHi, long rightLo, long rightHi) {
		int high = Long.compareUnsigned(leftHi, rightHi);
		return high != 0 ? high : Long.compareUnsigned(leftLo, rightLo);
	}

	private static void invokeOutAddressInt(MemorySegment out, MemorySegment value, int radix) {
		try {
			INT128_FROM_STRING_HANDLE.invokeExact(out, value, radix);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
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
