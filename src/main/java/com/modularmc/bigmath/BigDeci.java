package com.modularmc.bigmath;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

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
public final class BigDeci extends Number implements AutoCloseable, Comparable<BigDeci> {

	private static final int CONSTANT_PRECISION = 128;
	private static final MethodHandle BIGDECIMAL_ADD_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_add",
			FunctionDescriptors.BIGDECIMAL_BINARY
	);
	private static final MethodHandle BIGDECIMAL_CMP_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_cmp",
			FunctionDescriptors.BIGDECIMAL_CMP
	);
	private static final MethodHandle BIGDECIMAL_DIV_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_div",
			FunctionDescriptors.BIGDECIMAL_BINARY
	);
	private static final MethodHandle BIGDECIMAL_SUB_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_sub",
			FunctionDescriptors.BIGDECIMAL_BINARY
	);
	private static final MethodHandle BIGDECIMAL_MUL_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_mul",
			FunctionDescriptors.BIGDECIMAL_BINARY
	);
	private static final MethodHandle BIGDECIMAL_SQRT_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_sqrt",
			FunctionDescriptors.BIGDECIMAL_UNARY
	);
	private static final MethodHandle BIGDECIMAL_NEG_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_neg",
			FunctionDescriptors.BIGDECIMAL_UNARY
	);
	private static final MethodHandle BIGDECIMAL_ABS_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_abs",
			FunctionDescriptors.BIGDECIMAL_UNARY
	);
	private static final MethodHandle BIGDECIMAL_POW_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_pow",
			FunctionDescriptors.BIGDECIMAL_BINARY
	);
	private static final MethodHandle BIGDECIMAL_POW_LONG_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_pow_long",
			FunctionDescriptors.BIGDECIMAL_POW_LONG
	);
	private static final MethodHandle BIGDECIMAL_LOG_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_log",
			FunctionDescriptors.BIGDECIMAL_UNARY
	);
	private static final MethodHandle BIGDECIMAL_EXP_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_exp",
			FunctionDescriptors.BIGDECIMAL_UNARY
	);
	private static final MethodHandle BIGDECIMAL_SIN_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_sin",
			FunctionDescriptors.BIGDECIMAL_UNARY
	);
	private static final MethodHandle BIGDECIMAL_COS_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_cos",
			FunctionDescriptors.BIGDECIMAL_UNARY
	);
	private static final MethodHandle BIGDECIMAL_TAN_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_tan",
			FunctionDescriptors.BIGDECIMAL_UNARY
	);
	private static final MethodHandle BIGDECIMAL_CEIL_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_ceil",
			FunctionDescriptors.BIGDECIMAL_UNARY
	);
	private static final MethodHandle BIGDECIMAL_FLOOR_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_floor",
			FunctionDescriptors.BIGDECIMAL_UNARY
	);
	private static final MethodHandle BIGDECIMAL_ROUND_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_round",
			FunctionDescriptors.BIGDECIMAL_UNARY
	);
	private static final MethodHandle BIGDECIMAL_FROM_DOUBLE_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_from_double",
			FunctionDescriptors.BIGDECIMAL_FROM_DOUBLE
	);
	private static final MethodHandle BIGDECIMAL_FROM_STRING_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_from_string",
			FunctionDescriptors.BIGDECIMAL_FROM_STRING
	);
	private static final MethodHandle BIGDECIMAL_SET_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_set",
			FunctionDescriptors.BIGDECIMAL_SET
	);
	private static final MethodHandle BIGDECIMAL_SET_DOUBLE_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_set_double",
			FunctionDescriptors.BIGDECIMAL_SET_DOUBLE
	);
	private static final MethodHandle BIGDECIMAL_SET_STRING_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_set_string",
			FunctionDescriptors.BIGDECIMAL_SET_STRING
	);
	private static final MethodHandle BIGDECIMAL_ADD_INTO_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_add_into",
			FunctionDescriptors.BIGDECIMAL_BINARY_INTO
	);
	private static final MethodHandle BIGDECIMAL_MUL_INTO_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_mul_into",
			FunctionDescriptors.BIGDECIMAL_BINARY_INTO
	);
	private static final MethodHandle BIGDECIMAL_DIV_INTO_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_div_into",
			FunctionDescriptors.BIGDECIMAL_BINARY_INTO
	);
	private static final MethodHandle BIGDECIMAL_SQRT_INTO_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_sqrt_into",
			FunctionDescriptors.BIGDECIMAL_UNARY_INTO
	);
	private static final MethodHandle BIGDECIMAL_TO_DOUBLE_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_to_double",
			FunctionDescriptors.BIGDECIMAL_TO_DOUBLE
	);
	private static final MethodHandle BIGDECIMAL_TO_STRING_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_to_string",
			FunctionDescriptors.BIGDECIMAL_TO_STRING
	);
	private static final MethodHandle BIGDECIMAL_FORMAT_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_format",
			FunctionDescriptors.BIGDECIMAL_FORMAT
	);
	private static final MethodHandle BIGDECIMAL_FREE_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_free",
			FunctionDescriptors.BIGDECIMAL_FREE
	);
	private static final MethodHandle BIGDECIMAL_FREE_STRING_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_free_string",
			FunctionDescriptors.BIGDECIMAL_FREE_STRING
	);
	private static final MemorySegment BIGDECIMAL_COMMA_SEPARATOR = Arena.global()
			.allocateFrom(",", java.nio.charset.StandardCharsets.UTF_8);

	public static final BigDeci ZERO = createConstant(0.0);
	public static final BigDeci ONE = createConstant(1.0);
	public static final BigDeci TWO = createConstant(2.0);
	public static final BigDeci TEN = createConstant(10.0);
	public static final BigDeci NEGATIVE_ONE = createConstant(-1.0);

	private final MemorySegment nativePtr;
	private final Arena arena;

	MemorySegment nativePtr() {
		return nativePtr;
	}

	static BigDeci copyOf(MemorySegment value) {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		invokeOutDoubleInt(result, 0.0, CONSTANT_PRECISION);
		MemorySegment ptr = result.get(ValueLayout.ADDRESS, 0).reinterpret(arena, null);
		invokeSet(ptr, value);
		return new BigDeci(ptr, arena);
	}

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
		invokeOutDoubleInt(ptr, value, precision);
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
			invokeOutAddressInt(ptr, str, precision);
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
	 * Replaces this value with {@code value}, preserving this instance's
	 * current MPFR precision.
	 *
	 * @param value the source value
	 * @return this instance
	 */
	public BigDeci set(double value) {
		ensureMutable();
		invokeSetDouble(nativePtr, value);
		return this;
	}

	/**
	 * Parses {@code value}, resets this instance to {@code precision}, and
	 * stores the parsed decimal.
	 *
	 * @param value     the string to parse
	 * @param precision the MPFR precision in bits
	 * @return this instance
	 */
	public BigDeci set(String value, int precision) {
		ensureMutable();
		try (Arena tmp = Arena.ofConfined()) {
			MemorySegment str = tmp.allocateFrom(value, java.nio.charset.StandardCharsets.UTF_8);
			invokeSetString(nativePtr, str, precision);
		}
		return this;
	}

	/**
	 * Copies {@code value} into this instance.
	 *
	 * @param value the source value
	 * @return this instance
	 */
	public BigDeci set(BigDeci value) {
		ensureMutable();
		if (this != value) {
			invokeSet(nativePtr, value.nativePtr);
		}
		return this;
	}

	/**
	 * Replaces this value with {@code left + right}.
	 *
	 * @return this instance
	 */
	public BigDeci addInto(BigDeci left, BigDeci right) {
		ensureMutable();
		invokeBinaryOut(BIGDECIMAL_ADD_INTO_HANDLE, nativePtr, left.nativePtr, right.nativePtr);
		return this;
	}

	/**
	 * Replaces this value with {@code left * right}.
	 *
	 * @return this instance
	 */
	public BigDeci multiplyInto(BigDeci left, BigDeci right) {
		ensureMutable();
		invokeBinaryOut(BIGDECIMAL_MUL_INTO_HANDLE, nativePtr, left.nativePtr, right.nativePtr);
		return this;
	}

	/**
	 * Replaces this value with {@code left / right}.
	 *
	 * @return this instance
	 */
	public BigDeci divideInto(BigDeci left, BigDeci right) {
		ensureMutable();
		invokeBinaryOut(BIGDECIMAL_DIV_INTO_HANDLE, nativePtr, left.nativePtr, right.nativePtr);
		return this;
	}

	/**
	 * Replaces this value with {@code sqrt(value)}.
	 *
	 * @return this instance
	 */
	public BigDeci sqrtInto(BigDeci value) {
		ensureMutable();
		invokeUnaryOut(BIGDECIMAL_SQRT_INTO_HANDLE, nativePtr, value.nativePtr);
		return this;
	}

	/**
	 * Returns {@code this + other}.
	 */
	public BigDeci add(BigDeci other) {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		invokeBinaryOut(BIGDECIMAL_ADD_HANDLE, result, nativePtr, other.nativePtr);
		return new BigDeci(result.get(ValueLayout.ADDRESS, 0).reinterpret(arena, null), arena);
	}

	/**
	 * Returns {@code this - other}.
	 */
	public BigDeci subtract(BigDeci other) {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		invokeBinaryOut(BIGDECIMAL_SUB_HANDLE, result, nativePtr, other.nativePtr);
		return new BigDeci(result.get(ValueLayout.ADDRESS, 0).reinterpret(arena, null), arena);
	}

	/**
	 * Returns {@code this * other}.
	 */
	public BigDeci multiply(BigDeci other) {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		invokeBinaryOut(BIGDECIMAL_MUL_HANDLE, result, nativePtr, other.nativePtr);
		return new BigDeci(result.get(ValueLayout.ADDRESS, 0).reinterpret(arena, null), arena);
	}

	/**
	 * Returns {@code this / other}.
	 */
	public BigDeci divide(BigDeci other) {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		invokeBinaryOut(BIGDECIMAL_DIV_HANDLE, result, nativePtr, other.nativePtr);
		return new BigDeci(result.get(ValueLayout.ADDRESS, 0).reinterpret(arena, null), arena);
	}

	/**
	 * Returns {@code -this}.
	 */
	public BigDeci negate() {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		invokeUnaryOut(BIGDECIMAL_NEG_HANDLE, result, nativePtr);
		return new BigDeci(result.get(ValueLayout.ADDRESS, 0).reinterpret(arena, null), arena);
	}

	/**
	 * Returns the absolute value.
	 */
	public BigDeci abs() {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		invokeUnaryOut(BIGDECIMAL_ABS_HANDLE, result, nativePtr);
		return new BigDeci(result.get(ValueLayout.ADDRESS, 0).reinterpret(arena, null), arena);
	}

	/**
	 * Returns the square root.
	 */
	public BigDeci sqrt() {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		invokeUnaryOut(BIGDECIMAL_SQRT_HANDLE, result, nativePtr);
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
		invokeBinaryOut(BIGDECIMAL_POW_HANDLE, result, nativePtr, exponent.nativePtr);
		return new BigDeci(result.get(ValueLayout.ADDRESS, 0).reinterpret(arena, null), arena);
	}

	/**
	 * Returns {@code this}<sup>{@code exponent}</sup>.
	 *
	 * @param exponent the integer exponent
	 */
	public BigDeci pow(long exponent) {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		invokeOutAddressLong(BIGDECIMAL_POW_LONG_HANDLE, result, nativePtr, exponent);
		return new BigDeci(result.get(ValueLayout.ADDRESS, 0).reinterpret(arena, null), arena);
	}

	/**
	 * Returns the natural logarithm.
	 */
	public BigDeci log() {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		invokeUnaryOut(BIGDECIMAL_LOG_HANDLE, result, nativePtr);
		return new BigDeci(result.get(ValueLayout.ADDRESS, 0).reinterpret(arena, null), arena);
	}

	/**
	 * Returns <i>e</i><sup>this</sup>.
	 */
	public BigDeci exp() {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		invokeUnaryOut(BIGDECIMAL_EXP_HANDLE, result, nativePtr);
		return new BigDeci(result.get(ValueLayout.ADDRESS, 0).reinterpret(arena, null), arena);
	}

	/**
	 * Returns the sine.
	 */
	public BigDeci sin() {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		invokeUnaryOut(BIGDECIMAL_SIN_HANDLE, result, nativePtr);
		return new BigDeci(result.get(ValueLayout.ADDRESS, 0).reinterpret(arena, null), arena);
	}

	/**
	 * Returns the cosine.
	 */
	public BigDeci cos() {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		invokeUnaryOut(BIGDECIMAL_COS_HANDLE, result, nativePtr);
		return new BigDeci(result.get(ValueLayout.ADDRESS, 0).reinterpret(arena, null), arena);
	}

	/**
	 * Returns the tangent.
	 */
	public BigDeci tan() {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		invokeUnaryOut(BIGDECIMAL_TAN_HANDLE, result, nativePtr);
		return new BigDeci(result.get(ValueLayout.ADDRESS, 0).reinterpret(arena, null), arena);
	}

	/**
	 * Returns the smallest integer greater than or equal to this value.
	 */
	public BigDeci ceil() {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		invokeUnaryOut(BIGDECIMAL_CEIL_HANDLE, result, nativePtr);
		return new BigDeci(result.get(ValueLayout.ADDRESS, 0).reinterpret(arena, null), arena);
	}

	/**
	 * Returns the largest integer less than or equal to this value.
	 */
	public BigDeci floor() {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		invokeUnaryOut(BIGDECIMAL_FLOOR_HANDLE, result, nativePtr);
		return new BigDeci(result.get(ValueLayout.ADDRESS, 0).reinterpret(arena, null), arena);
	}

	/**
	 * Returns the nearest integer (rounds half away from zero).
	 */
	public BigDeci round() {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		invokeUnaryOut(BIGDECIMAL_ROUND_HANDLE, result, nativePtr);
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
		return invokeIntBinary(nativePtr, other.nativePtr);
	}

	@Override
	public int intValue() {
		return (int) longValue();
	}

	@Override
	public long longValue() {
		return (long) doubleValue();
	}

	@Override
	public float floatValue() {
		return (float) doubleValue();
	}

	/**
	 * Converts to a primitive {@code double}, possibly with loss of
	 * precision.
	 */
	@Override
	public double doubleValue() {
		return toDouble();
	}

	/**
	 * Converts to a primitive {@code double}, possibly with loss of
	 * precision.
	 */
	public double toDouble() {
		return invokeDoubleUnary(nativePtr);
	}

	/**
	 * Returns the full-precision decimal string representation.
	 */
	@Override
	public String toString() {
		MemorySegment result = invokeString(nativePtr);
		try {
			return result.reinterpret(Long.MAX_VALUE).getString(0);
		} finally {
			invokeVoidAddress(BIGDECIMAL_FREE_STRING_HANDLE, result);
		}
	}

	/**
	 * Returns the formatted string with auto-scaled fractional part, default
	 * group size 3, and comma separator.
	 */
	public String toFormattedString() {
		MemorySegment result = invokeFormat(nativePtr, -1, 3, BIGDECIMAL_COMMA_SEPARATOR);
		try {
			return result.reinterpret(Long.MAX_VALUE).getString(0);
		} finally {
			invokeVoidAddress(BIGDECIMAL_FREE_STRING_HANDLE, result);
		}
	}

	/**
	 * Returns the formatted string with a fixed scale.
	 *
	 * @param scale number of fractional digits, or {@code -1} for auto-scale
	 */
	public String toFormattedString(int scale) {
		MemorySegment result = invokeFormat(nativePtr, scale, 3, BIGDECIMAL_COMMA_SEPARATOR);
		try {
			return result.reinterpret(Long.MAX_VALUE).getString(0);
		} finally {
			invokeVoidAddress(BIGDECIMAL_FREE_STRING_HANDLE, result);
		}
	}

	/**
	 * Returns the formatted string with custom scale and digit grouping.
	 *
	 * @param scale     number of fractional digits, or {@code -1} for auto-scale
	 * @param groupSize number of integer digits per group
	 * @param groupSep  the group separator string
	 */
	public String toFormattedString(int scale, int groupSize, String groupSep) {
		if (",".equals(groupSep)) {
			MemorySegment result = invokeFormat(nativePtr, scale, groupSize, BIGDECIMAL_COMMA_SEPARATOR);
			try {
				return result.reinterpret(Long.MAX_VALUE).getString(0);
			} finally {
				invokeVoidAddress(BIGDECIMAL_FREE_STRING_HANDLE, result);
			}
		}
		try (Arena tmp = Arena.ofConfined()) {
			MemorySegment sep = tmp.allocateFrom(groupSep, java.nio.charset.StandardCharsets.UTF_8);
			MemorySegment result = invokeFormat(nativePtr, scale, groupSize, sep);
			try {
				return result.reinterpret(Long.MAX_VALUE).getString(0);
			} finally {
				invokeVoidAddress(BIGDECIMAL_FREE_STRING_HANDLE, result);
			}
		}
	}

	/**
	 * Frees the native MPFR memory backing this instance.
	 */
	@Override
	public void close() {
		invokeVoidAddress(BIGDECIMAL_FREE_HANDLE, nativePtr);
		arena.close();
	}

	/**
	 * Creates a constant {@code BigDeci} in the global arena.
	 *
	 * @param value the source value
	 * @return a new constant {@code BigDeci}
	 */
	private static BigDeci createConstant(double value) {
		Arena arena = Arena.global();
		MemorySegment ptr = arena.allocate(ValueLayout.ADDRESS);
		invokeOutDoubleInt(ptr, value, BigDeci.CONSTANT_PRECISION);
		return new BigDeci(ptr.get(ValueLayout.ADDRESS, 0).reinterpret(arena, null), arena);
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

	private static MemorySegment invokeString(MemorySegment value) {
		try {
			return (MemorySegment) BIGDECIMAL_TO_STRING_HANDLE.invokeExact(value);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	private static MemorySegment invokeFormat(MemorySegment value, int scale, int groupSize, MemorySegment separator) {
		try {
			return (MemorySegment) BIGDECIMAL_FORMAT_HANDLE.invokeExact(value, scale, groupSize, separator);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	private static int invokeIntBinary(MemorySegment left, MemorySegment right) {
		try {
			return (int) BIGDECIMAL_CMP_HANDLE.invokeExact(left, right);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	private static void invokeOutDoubleInt(MemorySegment out, double value, int precision) {
		try {
			BIGDECIMAL_FROM_DOUBLE_HANDLE.invokeExact(out, value, precision);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	private static void invokeOutAddressInt(MemorySegment out, MemorySegment value, int precision) {
		try {
			BIGDECIMAL_FROM_STRING_HANDLE.invokeExact(out, value, precision);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	private static double invokeDoubleUnary(MemorySegment value) {
		try {
			return (double) BIGDECIMAL_TO_DOUBLE_HANDLE.invokeExact(value);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	private static void invokeOutAddressLong(MethodHandle handle, MemorySegment out, MemorySegment value, long argument) {
		try {
			handle.invokeExact(out, value, argument);
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

	private static void invokeSet(MemorySegment out, MemorySegment value) {
		try {
			BIGDECIMAL_SET_HANDLE.invokeExact(out, value);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	private static void invokeSetDouble(MemorySegment out, double value) {
		try {
			BIGDECIMAL_SET_DOUBLE_HANDLE.invokeExact(out, value);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	private static void invokeSetString(MemorySegment out, MemorySegment value, int precision) {
		try {
			BIGDECIMAL_SET_STRING_HANDLE.invokeExact(out, value, precision);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	private void ensureMutable() {
		if (this == ZERO || this == ONE || this == TWO || this == TEN || this == NEGATIVE_ONE) {
			throw new UnsupportedOperationException("BigDeci constants are shared and cannot be mutated");
		}
	}
}
