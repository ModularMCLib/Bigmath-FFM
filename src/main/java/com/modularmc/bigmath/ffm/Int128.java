package com.modularmc.bigmath.ffm;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

import static com.modularmc.bigmath.ffm.BigmathFFM.invoke;

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
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"int128_from_i64",
				FunctionDescriptors.INT128_FROM_I64
		);
		invoke(handle, ptr, value);
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
			MethodHandle handle = BigmathFFM.getInstance().downcall(
					"int128_from_string",
					FunctionDescriptors.INT128_FROM_STRING
			);
			invoke(handle, ptr, str, radix);
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
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"int128_add",
				FunctionDescriptors.INT128_BINARY
		);
		invoke(handle, result, nativePtr, other.nativePtr);
		return new Int128(result, arena);
	}

	/**
	 * Returns {@code this - other}.
	 */
	public Int128 subtract(Int128 other) {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(STRUCT_SIZE);
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"int128_sub",
				FunctionDescriptors.INT128_BINARY
		);
		invoke(handle, result, nativePtr, other.nativePtr);
		return new Int128(result, arena);
	}

	/**
	 * Returns {@code this * other}.
	 */
	public Int128 multiply(Int128 other) {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(STRUCT_SIZE);
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"int128_mul",
				FunctionDescriptors.INT128_BINARY
		);
		invoke(handle, result, nativePtr, other.nativePtr);
		return new Int128(result, arena);
	}

	/**
	 * Returns {@code this / other} (truncating toward zero).
	 */
	public Int128 divide(Int128 other) {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(STRUCT_SIZE);
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"int128_div",
				FunctionDescriptors.INT128_BINARY
		);
		invoke(handle, result, nativePtr, other.nativePtr);
		return new Int128(result, arena);
	}

	/**
	 * Returns {@code this % other}.
	 */
	public Int128 mod(Int128 other) {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(STRUCT_SIZE);
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"int128_mod",
				FunctionDescriptors.INT128_BINARY
		);
		invoke(handle, result, nativePtr, other.nativePtr);
		return new Int128(result, arena);
	}

	/**
	 * Returns {@code -this}.
	 */
	public Int128 negate() {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(STRUCT_SIZE);
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"int128_neg",
				FunctionDescriptors.INT128_UNARY
		);
		invoke(handle, result, nativePtr);
		return new Int128(result, arena);
	}

	/**
	 * Returns the absolute value.
	 */
	public Int128 abs() {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(STRUCT_SIZE);
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"int128_abs",
				FunctionDescriptors.INT128_UNARY
		);
		invoke(handle, result, nativePtr);
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
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"int128_cmp",
				FunctionDescriptors.INT128_CMP
		);
		return (int) invoke(handle, nativePtr, other.nativePtr);
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
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"int128_sign",
				FunctionDescriptors.INT128_SIGN
		);
		return (int) invoke(handle, nativePtr);
	}

	/**
	 * Returns the string representation in the given radix.
	 *
	 * @param radix base, between 2 and 62 inclusive
	 */
	public String toString(int radix) {
		try (Arena tmp = Arena.ofConfined()) {
			MethodHandle handle = BigmathFFM.getInstance().downcall(
					"int128_to_string",
					FunctionDescriptors.INT128_TO_STRING
			);
			MemorySegment result = (MemorySegment) invoke(handle, nativePtr, radix);
			String str = result.reinterpret(tmp, null).reinterpret(Long.MAX_VALUE).getString(0);
			MethodHandle freeHandle = BigmathFFM.getInstance().downcall(
					"int128_free_string",
					FunctionDescriptors.INT128_FREE_STRING
			);
			invoke(freeHandle, result);
			return str;
		}
	}

	/**
	 * Returns the formatted string with default grouping (group size 3,
	 * comma separator).
	 */
	public String toFormattedString() {
		return toFormattedString(3, ",");
	}

	/**
	 * Returns the formatted string with custom digit grouping.
	 *
	 * @param groupSize number of digits per group
	 * @param groupSep  the separator string
	 */
	public String toFormattedString(int groupSize, String groupSep) {
		try (Arena tmp = Arena.ofConfined()) {
			MemorySegment sep = tmp.allocateFrom(groupSep, java.nio.charset.StandardCharsets.UTF_8);
			MethodHandle handle = BigmathFFM.getInstance().downcall(
					"int128_format",
					FunctionDescriptors.INT128_FORMAT
			);
			MemorySegment result = (MemorySegment) invoke(handle, nativePtr, groupSize, sep);
			String str = result.reinterpret(tmp, null).reinterpret(Long.MAX_VALUE).getString(0);
			MethodHandle freeHandle = BigmathFFM.getInstance().downcall(
					"int128_free_string",
					FunctionDescriptors.INT128_FREE_STRING
			);
			invoke(freeHandle, result);
			return str;
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
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"int128_from_i64",
				FunctionDescriptors.INT128_FROM_I64
		);
		invoke(handle, ptr, value);
		return new Int128(ptr, arena);
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
