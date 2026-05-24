package com.modularmc.bigmath;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * 128-bit signed integer value.
 * <p>
 * Hot-path small operations are implemented directly on two Java {@code long}
 * words. Native helpers remain for parsing, wide arithmetic, and formatting.
 */
public final class Int128 extends Number implements AutoCloseable, Comparable<Int128> {

	private static final long STRUCT_SIZE = 16L;
	private static final MethodHandle INT128_FROM_STRING_HANDLE = BigmathFFM.getInstance().downcall(
			"int128_from_string",
			FunctionDescriptors.INT128_FROM_STRING
	);
	private static final MethodHandle INT128_MUL_HANDLE = BigmathFFM.getInstance().downcall(
			"int128_mul",
			FunctionDescriptors.INT128_BINARY
	);
	private static final MethodHandle INT128_DIV_HANDLE = BigmathFFM.getInstance().downcall(
			"int128_div",
			FunctionDescriptors.INT128_BINARY
	);
	private static final MethodHandle INT128_MOD_HANDLE = BigmathFFM.getInstance().downcall(
			"int128_mod",
			FunctionDescriptors.INT128_BINARY
	);
	private static final MethodHandle INT128_TO_STRING_HANDLE = BigmathFFM.getInstance().downcall(
			"int128_to_string",
			FunctionDescriptors.INT128_TO_STRING
	);
	private static final MethodHandle INT128_FORMAT_HANDLE = BigmathFFM.getInstance().downcall(
			"int128_format",
			FunctionDescriptors.INT128_FORMAT
	);
	private static final MethodHandle INT128_FREE_STRING_HANDLE = BigmathFFM.getInstance().downcall(
			"int128_free_string",
			FunctionDescriptors.INT128_FREE_STRING
	);
	private static final MemorySegment INT128_COMMA_SEPARATOR = Arena.global()
			.allocateFrom(",", java.nio.charset.StandardCharsets.UTF_8);

	public static final Int128 ZERO = fromLong(0);
	public static final Int128 ONE = fromLong(1);
	public static final Int128 TWO = fromLong(2);
	public static final Int128 TEN = fromLong(10);
	public static final Int128 NEGATIVE_ONE = fromLong(-1);

	private final long lo;
	private final long hi;

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

	MemorySegment toSegment(Arena arena) {
		MemorySegment ptr = arena.allocate(STRUCT_SIZE);
		ptr.set(ValueLayout.JAVA_LONG, 0, lo);
		ptr.set(ValueLayout.JAVA_LONG, 8, hi);
		return ptr;
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
		return invokeNativeBinary(INT128_MUL_HANDLE, this, other);
	}

	public Int128 divide(Int128 other) {
		if (fitsInLong() && other.fitsInLong() && !(lo == Long.MIN_VALUE && other.lo == -1L)) {
			return fromLong(lo / other.lo);
		}
		return invokeNativeBinary(INT128_DIV_HANDLE, this, other);
	}

	public Int128 mod(Int128 other) {
		if (fitsInLong() && other.fitsInLong()) {
			return fromLong(lo % other.lo);
		}
		return invokeNativeBinary(INT128_MOD_HANDLE, this, other);
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
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment result = invokeStringWithRadix(toSegment(arena), radix);
			try {
				return result.reinterpret(Long.MAX_VALUE).getString(0);
			} finally {
				invokeVoidAddress(result);
			}
		}
	}

	public String toFormattedString() {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment result = invokeFormat(toSegment(arena), 3, INT128_COMMA_SEPARATOR);
			try {
				return result.reinterpret(Long.MAX_VALUE).getString(0);
			} finally {
				invokeVoidAddress(result);
			}
		}
	}

	public String toFormattedString(int groupSize, String groupSep) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment sep = ",".equals(groupSep)
					? INT128_COMMA_SEPARATOR
					: arena.allocateFrom(groupSep, java.nio.charset.StandardCharsets.UTF_8);
			MemorySegment result = invokeFormat(toSegment(arena), groupSize, sep);
			try {
				return result.reinterpret(Long.MAX_VALUE).getString(0);
			} finally {
				invokeVoidAddress(result);
			}
		}
	}

	@Override
	public String toString() {
		return toString(10);
	}

	@Override
	public void close() {
		// Value object; kept for source compatibility with previous AutoCloseable API.
	}

	private boolean fitsInLong() {
		return hi == (lo < 0 ? -1L : 0L);
	}

	private static Int128 invokeNativeBinary(MethodHandle handle, Int128 left, Int128 right) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment result = arena.allocate(STRUCT_SIZE);
			handle.invokeExact(result, left.toSegment(arena), right.toSegment(arena));
			return fromSegment(result);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
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

	private static MemorySegment invokeStringWithRadix(MemorySegment value, int radix) {
		try {
			return (MemorySegment) INT128_TO_STRING_HANDLE.invokeExact(value, radix);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	private static MemorySegment invokeFormat(MemorySegment value, int groupSize, MemorySegment separator) {
		try {
			return (MemorySegment) INT128_FORMAT_HANDLE.invokeExact(value, groupSize, separator);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	private static void invokeVoidAddress(MemorySegment value) {
		try {
			INT128_FREE_STRING_HANDLE.invokeExact(value);
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
