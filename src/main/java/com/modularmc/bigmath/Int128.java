package com.modularmc.bigmath;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

import static com.modularmc.bigmath.BigmathFFM.invoke;

/**
 * 128-bit signed integer backed by the native bigmath library.
 * <p>
 * Unlike {@link BigInt}, this type is stack-allocated (16-byte struct) and
 * does not use dynamic heap memory. Supports full arithmetic and comparison
 * operations within the 128-bit range.
 * <p>
 * Constants {@link #ZERO}, {@link #ONE}, {@link #TWO}, {@link #TEN}, and
 * {@link #NEGATIVE_ONE} use a global arena and should not be closed.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class Int128 extends Number implements AutoCloseable, Comparable<Int128> {

	private static final long STRUCT_SIZE = 16L;
	private static final MethodHandle INT128_FROM_I64_HANDLE = BigmathFFM.getInstance().downcall(
			"int128_from_i64",
			FunctionDescriptors.INT128_FROM_I64
	);
	private static final MethodHandle INT128_FROM_STRING_HANDLE = BigmathFFM.getInstance().downcall(
			"int128_from_string",
			FunctionDescriptors.INT128_FROM_STRING
	);
	private static final MethodHandle INT128_ADD_HANDLE = BigmathFFM.getInstance().downcall(
			"int128_add",
			FunctionDescriptors.INT128_BINARY
	);
	private static final MethodHandle INT128_SUB_HANDLE = BigmathFFM.getInstance().downcall(
			"int128_sub",
			FunctionDescriptors.INT128_BINARY
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
	private static final MethodHandle INT128_NEG_HANDLE = BigmathFFM.getInstance().downcall(
			"int128_neg",
			FunctionDescriptors.INT128_UNARY
	);
	private static final MethodHandle INT128_ABS_HANDLE = BigmathFFM.getInstance().downcall(
			"int128_abs",
			FunctionDescriptors.INT128_UNARY
	);
	private static final MethodHandle INT128_CMP_HANDLE = BigmathFFM.getInstance().downcall(
			"int128_cmp",
			FunctionDescriptors.INT128_CMP
	);
	private static final MethodHandle INT128_SIGN_HANDLE = BigmathFFM.getInstance().downcall(
			"int128_sign",
			FunctionDescriptors.INT128_SIGN
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

	public static final Int128 ZERO = createConstant(0);
	public static final Int128 ONE = createConstant(1);
	public static final Int128 TWO = createConstant(2);
	public static final Int128 TEN = createConstant(10);
	public static final Int128 NEGATIVE_ONE = createConstant(-1);

	private final MemorySegment nativePtr;
	private final Arena arena;

	/**
	 * Creates an {@code Int128} from a primitive {@code long}.
	 *
	 * @param value the source value
	 * @return a new {@code Int128}
	 */
	public static Int128 fromLong(long value) {
		Arena arena = Arena.ofConfined();
		MemorySegment ptr = arena.allocate(STRUCT_SIZE);
		invokeOutWithLong(INT128_FROM_I64_HANDLE, ptr, value);
		return new Int128(ptr, arena);
	}

	/**
	 * Parses a string representation in the given radix.
	 *
	 * @param value the string to parse
	 * @param radix the base, between 2 and 62 inclusive
	 * @return a new {@code Int128}
	 */
	public static Int128 fromString(String value, int radix) {
		Arena arena = Arena.ofConfined();
		MemorySegment ptr = arena.allocate(STRUCT_SIZE);
		try (Arena tmp = Arena.ofConfined()) {
			MemorySegment str = tmp.allocateFrom(value, java.nio.charset.StandardCharsets.UTF_8);
			invokeOutAddressInt(INT128_FROM_STRING_HANDLE, ptr, str, radix);
		}
		return new Int128(ptr, arena);
	}

	/**
	 * Returns the low 64 bits.
	 */
	public long lo() {
		return nativePtr.get(ValueLayout.JAVA_LONG, 0);
	}

	/**
	 * Returns the high 64 bits.
	 */
	public long hi() {
		return nativePtr.get(ValueLayout.JAVA_LONG, 8);
	}

	/**
	 * Returns {@code this + other}.
	 */
	public Int128 add(Int128 other) {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(STRUCT_SIZE);
		invokeBinaryOut(INT128_ADD_HANDLE, result, nativePtr, other.nativePtr);
		return new Int128(result, arena);
	}

	/**
	 * Returns {@code this - other}.
	 */
	public Int128 subtract(Int128 other) {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(STRUCT_SIZE);
		invokeBinaryOut(INT128_SUB_HANDLE, result, nativePtr, other.nativePtr);
		return new Int128(result, arena);
	}

	/**
	 * Returns {@code this * other}.
	 */
	public Int128 multiply(Int128 other) {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(STRUCT_SIZE);
		invokeBinaryOut(INT128_MUL_HANDLE, result, nativePtr, other.nativePtr);
		return new Int128(result, arena);
	}

	/**
	 * Returns {@code this / other} (truncating toward zero).
	 */
	public Int128 divide(Int128 other) {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(STRUCT_SIZE);
		invokeBinaryOut(INT128_DIV_HANDLE, result, nativePtr, other.nativePtr);
		return new Int128(result, arena);
	}

	/**
	 * Returns {@code this % other}.
	 */
	public Int128 mod(Int128 other) {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(STRUCT_SIZE);
		invokeBinaryOut(INT128_MOD_HANDLE, result, nativePtr, other.nativePtr);
		return new Int128(result, arena);
	}

	/**
	 * Returns {@code -this}.
	 */
	public Int128 negate() {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(STRUCT_SIZE);
		invokeUnaryOut(INT128_NEG_HANDLE, result, nativePtr);
		return new Int128(result, arena);
	}

	/**
	 * Returns the absolute value.
	 */
	public Int128 abs() {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(STRUCT_SIZE);
		invokeUnaryOut(INT128_ABS_HANDLE, result, nativePtr);
		return new Int128(result, arena);
	}

	/**
	 * Compares this value with the specified value.
	 *
	 * @param other the value to compare
	 * @return 0 if equal, less than 0 if this is less, greater than 0 if this is greater
	 */
	@Override
	public int compareTo(Int128 other) {
		return invokeIntBinary(INT128_CMP_HANDLE, nativePtr, other.nativePtr);
	}

	@Override
	public int intValue() {
		return (int) lo();
	}

	@Override
	public long longValue() {
		return lo();
	}

	@Override
	public float floatValue() {
		return (float) doubleValue();
	}

	@Override
	public double doubleValue() {
		return Double.parseDouble(toString());
	}

	/**
	 * Returns the signum: {@code -1} (negative), {@code 0} (zero), or
	 * {@code 1} (positive).
	 */
	public int signum() {
		return invokeIntUnary(INT128_SIGN_HANDLE, nativePtr);
	}

	/**
	 * Returns the string representation in the given radix.
	 *
	 * @param radix base, between 2 and 62 inclusive
	 */
	public String toString(int radix) {
		MemorySegment result = invokeStringWithRadix(INT128_TO_STRING_HANDLE, nativePtr, radix);
		try {
			return result.reinterpret(Long.MAX_VALUE).getString(0);
		} finally {
			invokeVoidAddress(INT128_FREE_STRING_HANDLE, result);
		}
	}

	/**
	 * Returns the formatted string with default grouping (group size 3,
	 * comma separator).
	 */
	public String toFormattedString() {
		MemorySegment result = invokeFormat(INT128_FORMAT_HANDLE, nativePtr, 3, INT128_COMMA_SEPARATOR);
		try {
			return result.reinterpret(Long.MAX_VALUE).getString(0);
		} finally {
			invokeVoidAddress(INT128_FREE_STRING_HANDLE, result);
		}
	}

	/**
	 * Returns the formatted string with custom digit grouping.
	 *
	 * @param groupSize number of digits per group
	 * @param groupSep  the separator string
	 */
	public String toFormattedString(int groupSize, String groupSep) {
		if (",".equals(groupSep)) {
			MemorySegment result = invokeFormat(INT128_FORMAT_HANDLE, nativePtr, groupSize, INT128_COMMA_SEPARATOR);
			try {
				return result.reinterpret(Long.MAX_VALUE).getString(0);
			} finally {
				invokeVoidAddress(INT128_FREE_STRING_HANDLE, result);
			}
		}
		try (Arena tmp = Arena.ofConfined()) {
			MemorySegment sep = tmp.allocateFrom(groupSep, java.nio.charset.StandardCharsets.UTF_8);
			MemorySegment result = invokeFormat(INT128_FORMAT_HANDLE, nativePtr, groupSize, sep);
			try {
				return result.reinterpret(Long.MAX_VALUE).getString(0);
			} finally {
				invokeVoidAddress(INT128_FREE_STRING_HANDLE, result);
			}
		}
	}

	/**
	 * Returns the base-10 string representation.
	 */
	@Override
	public String toString() {
		return toString(10);
	}

	/**
	 * Releases the arena backing this instance.
	 */
	@Override
	public void close() {
		arena.close();
	}

	/**
	 * Creates a constant {@code Int128} in the global arena.
	 *
	 * @param value the source value
	 * @return a new constant {@code Int128}
	 */
	private static Int128 createConstant(long value) {
		Arena arena = Arena.global();
		MemorySegment ptr = arena.allocate(STRUCT_SIZE);
		invokeOutWithLong(INT128_FROM_I64_HANDLE, ptr, value);
		return new Int128(ptr, arena);
	}

	private static void invokeBinaryOut(MethodHandle handle, MemorySegment out, MemorySegment left, MemorySegment right) {
		try {
			handle.invokeExact(out, left, right);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	private static void invokeUnaryOut(MethodHandle handle, MemorySegment out, MemorySegment value) {
		try {
			handle.invokeExact(out, value);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	private static void invokeOutWithLong(MethodHandle handle, MemorySegment out, long value) {
		try {
			handle.invokeExact(out, value);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	private static void invokeOutAddressInt(MethodHandle handle, MemorySegment out, MemorySegment value, int radix) {
		try {
			handle.invokeExact(out, value, radix);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	private static int invokeIntBinary(MethodHandle handle, MemorySegment left, MemorySegment right) {
		try {
			return (int) handle.invokeExact(left, right);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	private static int invokeIntUnary(MethodHandle handle, MemorySegment value) {
		try {
			return (int) handle.invokeExact(value);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	private static MemorySegment invokeStringWithRadix(MethodHandle handle, MemorySegment value, int radix) {
		try {
			return (MemorySegment) handle.invokeExact(value, radix);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	private static MemorySegment invokeFormat(MethodHandle handle, MemorySegment value, int groupSize, MemorySegment separator) {
		try {
			return (MemorySegment) handle.invokeExact(value, groupSize, separator);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	private static void invokeVoidAddress(MethodHandle handle, MemorySegment value) {
		try {
			handle.invokeExact(value);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	/**
	 * Checks equality by comparing lo and hi words.
	 *
	 * @param o the object to compare
	 * @return {@code true} if both lo and hi are equal
	 */
	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof Int128 other)) return false;
		return lo() == other.lo() && hi() == other.hi();
	}

	/**
	 * Returns a hash code based on the lo and hi words.
	 *
	 * @return the hash code
	 */
	@Override
	public int hashCode() {
		return Long.hashCode(lo()) ^ Long.hashCode(hi());
	}
}
