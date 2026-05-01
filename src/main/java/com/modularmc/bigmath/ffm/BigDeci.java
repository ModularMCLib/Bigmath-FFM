package com.modularmc.bigmath.ffm;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

import static com.modularmc.bigmath.ffm.BigmathFFM.invoke;

/**
 * Arbitrary-precision decimal floating-point backed by the native bigmath
 * library (MPFR).
 * <p>
 * Supports arithmetic, trigonometric, logarithmic, exponential, and rounding
 * operations. Each instance wraps a native heap pointer; call
 * {@link #close()} to free the underlying resource.
 * <p>
 * Constants {@link #ZERO}, {@link #ONE}, {@link #TWO}, {@link #TEN}, and
 * {@link #NEGATIVE_ONE} use a global arena and should not be closed.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class BigDeci implements AutoCloseable, Comparable<BigDeci> {

	private static final int CONSTANT_PRECISION = 128;
	public static final BigDeci ZERO = createConstant(0.0, CONSTANT_PRECISION);
	public static final BigDeci ONE = createConstant(1.0, CONSTANT_PRECISION);
	public static final BigDeci TWO = createConstant(2.0, CONSTANT_PRECISION);
	public static final BigDeci TEN = createConstant(10.0, CONSTANT_PRECISION);
	public static final BigDeci NEGATIVE_ONE = createConstant(-1.0, CONSTANT_PRECISION);

	private final MemorySegment nativePtr;
	private final Arena arena;

	/**
	 * Creates a {@code BigDeci} from a {@code double} with the given
	 * precision (in bits).
	 *
	 * @param value     the source value
	 * @param precision the MPFR precision in bits
	 * @return a new {@code BigDeci}
	 */
	public static BigDeci fromDouble(double value, int precision) {
		Arena arena = Arena.ofConfined();
		MemorySegment ptr = arena.allocate(ValueLayout.ADDRESS);
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigdecimal_from_double",
				FunctionDescriptors.BIGDECIMAL_FROM_DOUBLE
		);
		invoke(handle, ptr, value, precision);
		return new BigDeci(ptr.get(ValueLayout.ADDRESS, 0).reinterpret(arena, null), arena);
	}

	/**
	 * Parses a decimal string with the given precision (in bits).
	 *
	 * @param value     the string to parse
	 * @param precision the MPFR precision in bits
	 * @return a new {@code BigDeci}
	 */
	public static BigDeci fromString(String value, int precision) {
		Arena arena = Arena.ofConfined();
		MemorySegment ptr = arena.allocate(ValueLayout.ADDRESS);
		try (Arena tmp = Arena.ofConfined()) {
			MemorySegment str = tmp.allocateFrom(value, java.nio.charset.StandardCharsets.UTF_8);
			MethodHandle handle = BigmathFFM.getInstance().downcall(
					"bigdecimal_from_string",
					FunctionDescriptors.BIGDECIMAL_FROM_STRING
			);
			invoke(handle, ptr, str, precision);
		}
		return new BigDeci(ptr.get(ValueLayout.ADDRESS, 0).reinterpret(arena, null), arena);
	}

	/**
	 * Creates a {@code BigDeci} from a {@link BigInt} with the given
	 * precision.
	 *
	 * @param value     the source integer
	 * @param precision the MPFR precision in bits
	 * @return a new {@code BigDeci}
	 */
	public static BigDeci fromBigInt(BigInt value, int precision) {
		return fromString(value.toString(), precision);
	}

	/**
	 * Returns {@code this + other}.
	 */
	public BigDeci add(BigDeci other) {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigdecimal_add",
				FunctionDescriptors.BIGDECIMAL_BINARY
		);
		invoke(handle, result, nativePtr, other.nativePtr);
		return new BigDeci(result.get(ValueLayout.ADDRESS, 0).reinterpret(arena, null), arena);
	}

	/**
	 * Returns {@code this - other}.
	 */
	public BigDeci subtract(BigDeci other) {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigdecimal_sub",
				FunctionDescriptors.BIGDECIMAL_BINARY
		);
		invoke(handle, result, nativePtr, other.nativePtr);
		return new BigDeci(result.get(ValueLayout.ADDRESS, 0).reinterpret(arena, null), arena);
	}

	/**
	 * Returns {@code this * other}.
	 */
	public BigDeci multiply(BigDeci other) {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigdecimal_mul",
				FunctionDescriptors.BIGDECIMAL_BINARY
		);
		invoke(handle, result, nativePtr, other.nativePtr);
		return new BigDeci(result.get(ValueLayout.ADDRESS, 0).reinterpret(arena, null), arena);
	}

	/**
	 * Returns {@code this / other}.
	 */
	public BigDeci divide(BigDeci other) {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigdecimal_div",
				FunctionDescriptors.BIGDECIMAL_BINARY
		);
		invoke(handle, result, nativePtr, other.nativePtr);
		return new BigDeci(result.get(ValueLayout.ADDRESS, 0).reinterpret(arena, null), arena);
	}

	/**
	 * Returns {@code -this}.
	 */
	public BigDeci negate() {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigdecimal_neg",
				FunctionDescriptors.BIGDECIMAL_UNARY
		);
		invoke(handle, result, nativePtr);
		return new BigDeci(result.get(ValueLayout.ADDRESS, 0).reinterpret(arena, null), arena);
	}

	/**
	 * Returns the absolute value.
	 */
	public BigDeci abs() {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigdecimal_abs",
				FunctionDescriptors.BIGDECIMAL_UNARY
		);
		invoke(handle, result, nativePtr);
		return new BigDeci(result.get(ValueLayout.ADDRESS, 0).reinterpret(arena, null), arena);
	}

	/**
	 * Returns the square root.
	 */
	public BigDeci sqrt() {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigdecimal_sqrt",
				FunctionDescriptors.BIGDECIMAL_UNARY
		);
		invoke(handle, result, nativePtr);
		return new BigDeci(result.get(ValueLayout.ADDRESS, 0).reinterpret(arena, null), arena);
	}

	/**
	 * Returns {@code this}<sup>{@code exponent}</sup>.
	 *
	 * @param exponent the exponent as a {@code BigDeci}
	 */
	public BigDeci pow(BigDeci exponent) {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigdecimal_pow",
				FunctionDescriptors.BIGDECIMAL_BINARY
		);
		invoke(handle, result, nativePtr, exponent.nativePtr);
		return new BigDeci(result.get(ValueLayout.ADDRESS, 0).reinterpret(arena, null), arena);
	}

	/**
	 * Returns the natural logarithm.
	 */
	public BigDeci log() {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigdecimal_log",
				FunctionDescriptors.BIGDECIMAL_UNARY
		);
		invoke(handle, result, nativePtr);
		return new BigDeci(result.get(ValueLayout.ADDRESS, 0).reinterpret(arena, null), arena);
	}

	/**
	 * Returns <i>e</i><sup>this</sup>.
	 */
	public BigDeci exp() {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigdecimal_exp",
				FunctionDescriptors.BIGDECIMAL_UNARY
		);
		invoke(handle, result, nativePtr);
		return new BigDeci(result.get(ValueLayout.ADDRESS, 0).reinterpret(arena, null), arena);
	}

	/**
	 * Returns the sine.
	 */
	public BigDeci sin() {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigdecimal_sin",
				FunctionDescriptors.BIGDECIMAL_UNARY
		);
		invoke(handle, result, nativePtr);
		return new BigDeci(result.get(ValueLayout.ADDRESS, 0).reinterpret(arena, null), arena);
	}

	/**
	 * Returns the cosine.
	 */
	public BigDeci cos() {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigdecimal_cos",
				FunctionDescriptors.BIGDECIMAL_UNARY
		);
		invoke(handle, result, nativePtr);
		return new BigDeci(result.get(ValueLayout.ADDRESS, 0).reinterpret(arena, null), arena);
	}

	/**
	 * Returns the tangent.
	 */
	public BigDeci tan() {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigdecimal_tan",
				FunctionDescriptors.BIGDECIMAL_UNARY
		);
		invoke(handle, result, nativePtr);
		return new BigDeci(result.get(ValueLayout.ADDRESS, 0).reinterpret(arena, null), arena);
	}

	/**
	 * Returns the smallest integer greater than or equal to this value.
	 */
	public BigDeci ceil() {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigdecimal_ceil",
				FunctionDescriptors.BIGDECIMAL_UNARY
		);
		invoke(handle, result, nativePtr);
		return new BigDeci(result.get(ValueLayout.ADDRESS, 0).reinterpret(arena, null), arena);
	}

	/**
	 * Returns the largest integer less than or equal to this value.
	 */
	public BigDeci floor() {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigdecimal_floor",
				FunctionDescriptors.BIGDECIMAL_UNARY
		);
		invoke(handle, result, nativePtr);
		return new BigDeci(result.get(ValueLayout.ADDRESS, 0).reinterpret(arena, null), arena);
	}

	/**
	 * Returns the nearest integer (rounds half away from zero).
	 */
	public BigDeci round() {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigdecimal_round",
				FunctionDescriptors.BIGDECIMAL_UNARY
		);
		invoke(handle, result, nativePtr);
		return new BigDeci(result.get(ValueLayout.ADDRESS, 0).reinterpret(arena, null), arena);
	}

	/**
	 * Compares this value with the specified value.
	 *
	 * @param other the value to compare
	 * @return 0 if equal, less than 0 if this is less, greater than 0 if this is greater
	 */
	@Override
	public int compareTo(BigDeci other) {
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigdecimal_cmp",
				FunctionDescriptors.BIGDECIMAL_CMP
		);
		return (int) invoke(handle, nativePtr, other.nativePtr);
	}

	/**
	 * Converts to a primitive {@code double}, possibly with loss of
	 * precision.
	 */
	public double toDouble() {
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigdecimal_to_double",
				FunctionDescriptors.BIGDECIMAL_TO_DOUBLE
		);
		return (double) invoke(handle, nativePtr);
	}

	/**
	 * Returns the full-precision decimal string representation.
	 */
	@Override
	public String toString() {
		try (Arena tmp = Arena.ofConfined()) {
			MethodHandle handle = BigmathFFM.getInstance().downcall(
					"bigdecimal_to_string",
					FunctionDescriptors.BIGDECIMAL_TO_STRING
			);
			MemorySegment result = (MemorySegment) invoke(handle, nativePtr);
			String str = result.reinterpret(tmp, null).reinterpret(Long.MAX_VALUE).getString(0);
			MethodHandle freeHandle = BigmathFFM.getInstance().downcall(
					"bigdecimal_free_string",
					FunctionDescriptors.BIGDECIMAL_FREE_STRING
			);
			invoke(freeHandle, result);
			return str;
		}
	}

	/**
	 * Returns the formatted string with auto-scaled fractional part, default
	 * group size 3, and comma separator.
	 */
	public String toFormattedString() {
		return toFormattedString(-1, 3, ",");
	}

	/**
	 * Returns the formatted string with a fixed scale.
	 *
	 * @param scale number of fractional digits, or {@code -1} for auto-scale
	 */
	public String toFormattedString(int scale) {
		return toFormattedString(scale, 3, ",");
	}

	/**
	 * Returns the formatted string with custom scale and digit grouping.
	 *
	 * @param scale     number of fractional digits, or {@code -1} for auto-scale
	 * @param groupSize number of integer digits per group
	 * @param groupSep  the group separator string
	 */
	public String toFormattedString(int scale, int groupSize, String groupSep) {
		try (Arena tmp = Arena.ofConfined()) {
			MemorySegment sep = tmp.allocateFrom(groupSep, java.nio.charset.StandardCharsets.UTF_8);
			MethodHandle handle = BigmathFFM.getInstance().downcall(
					"bigdecimal_format",
					FunctionDescriptors.BIGDECIMAL_FORMAT
			);
			MemorySegment result = (MemorySegment) invoke(handle, nativePtr, scale, groupSize, sep);
			String str = result.reinterpret(tmp, null).reinterpret(Long.MAX_VALUE).getString(0);
			MethodHandle freeHandle = BigmathFFM.getInstance().downcall(
					"bigdecimal_free_string",
					FunctionDescriptors.BIGDECIMAL_FREE_STRING
			);
			invoke(freeHandle, result);
			return str;
		}
	}

	/**
	 * Frees the native MPFR memory backing this instance.
	 */
	@Override
	public void close() {
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigdecimal_free",
				FunctionDescriptors.BIGDECIMAL_FREE
		);
		invoke(handle, nativePtr);
		arena.close();
	}

	/**
	 * Creates a constant {@code BigDeci} in the global arena.
	 *
	 * @param value     the source value
	 * @param precision the MPFR precision in bits
	 * @return a new constant {@code BigDeci}
	 */
	private static BigDeci createConstant(double value, int precision) {
		Arena arena = Arena.global();
		MemorySegment ptr = arena.allocate(ValueLayout.ADDRESS);
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigdecimal_from_double",
				FunctionDescriptors.BIGDECIMAL_FROM_DOUBLE
		);
		invoke(handle, ptr, value, precision);
		return new BigDeci(ptr.get(ValueLayout.ADDRESS, 0).reinterpret(arena, null), arena);
	}
}
