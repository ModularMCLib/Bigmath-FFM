package com.modularmc.bigmath;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.math.BigInteger;

/**
 * Arbitrary-precision integer backed by the native bigmath library (GMP).
 * <p>
 * Supports arithmetic, bitwise, comparison, primality testing, and formatted
 * string conversion. Each instance wraps a native heap pointer; call
 * {@link #close()} to free the underlying resource, or use
 * try-with-resources.
 * <p>
 * Instances are immutable from the Java caller's perspective: every arithmetic
 * method allocates and returns a new native-backed value, leaving the original
 * operand unchanged. This mirrors the public API contract of
 * {@link java.math.BigInteger}, while delegating the underlying heavy-lifting
 * to GMP through the FFM bridge.
 * <p>
 * Constants {@link #ZERO}, {@link #ONE}, {@link #TWO}, {@link #TEN}, and
 * {@link #NEGATIVE_ONE} use a global arena and should not be closed.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class BigInt extends Number implements AutoCloseable, Comparable<BigInt> {

	private static final MethodHandle BIGINT_ADD_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_add",
			FunctionDescriptors.BIGINT_BINARY
	);
	private static final MethodHandle BIGINT_CMP_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_cmp",
			FunctionDescriptors.BIGINT_CMP
	);
	private static final MethodHandle BIGINT_DIV_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_div",
			FunctionDescriptors.BIGINT_BINARY
	);
	private static final MethodHandle BIGINT_MOD_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_mod",
			FunctionDescriptors.BIGINT_BINARY
	);
	private static final MethodHandle BIGINT_MUL_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_mul",
			FunctionDescriptors.BIGINT_BINARY
	);
	private static final MethodHandle BIGINT_POW_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_pow",
			FunctionDescriptors.BIGINT_POW
	);
	private static final MethodHandle BIGINT_SUB_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_sub",
			FunctionDescriptors.BIGINT_BINARY
	);
	private static final MethodHandle BIGINT_NEG_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_neg",
			FunctionDescriptors.BIGINT_UNARY
	);
	private static final MethodHandle BIGINT_ABS_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_abs",
			FunctionDescriptors.BIGINT_UNARY
	);
	private static final MethodHandle BIGINT_GCD_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_gcd",
			FunctionDescriptors.BIGINT_BINARY
	);
	private static final MethodHandle BIGINT_LCM_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_lcm",
			FunctionDescriptors.BIGINT_BINARY
	);
	private static final MethodHandle BIGINT_SQRT_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_sqrt",
			FunctionDescriptors.BIGINT_UNARY
	);
	private static final MethodHandle BIGINT_AND_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_and",
			FunctionDescriptors.BIGINT_BINARY
	);
	private static final MethodHandle BIGINT_OR_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_or",
			FunctionDescriptors.BIGINT_BINARY
	);
	private static final MethodHandle BIGINT_XOR_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_xor",
			FunctionDescriptors.BIGINT_BINARY
	);
	private static final MethodHandle BIGINT_SHL_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_shl",
			FunctionDescriptors.BIGINT_POW
	);
	private static final MethodHandle BIGINT_SHR_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_shr",
			FunctionDescriptors.BIGINT_POW
	);
	private static final MethodHandle BIGINT_FACTORIAL_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_factorial",
			FunctionDescriptors.BIGINT_FROM_LONG
	);
	private static final MethodHandle BIGINT_NEXT_PRIME_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_next_prime",
			FunctionDescriptors.BIGINT_UNARY
	);
	private static final MethodHandle BIGINT_IS_PROBABLY_PRIME_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_is_probably_prime",
			FunctionDescriptors.BIGINT_IS_PROBABLY_PRIME
	);
	private static final MethodHandle BIGINT_FROM_LONG_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_from_long",
			FunctionDescriptors.BIGINT_FROM_LONG
	);
	private static final MethodHandle BIGINT_FROM_STRING_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_from_string",
			FunctionDescriptors.BIGINT_FROM_STRING
	);
	private static final MethodHandle BIGINT_SIGN_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_sign",
			FunctionDescriptors.BIGINT_SIGN
	);
	private static final MethodHandle BIGINT_TO_LONG_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_to_long",
			FunctionDescriptors.BIGINT_TO_LONG
	);
	private static final MethodHandle BIGINT_TO_DOUBLE_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_to_double",
			FunctionDescriptors.BIGINT_TO_DOUBLE
	);
	private static final MethodHandle BIGINT_TO_STRING_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_to_string",
			FunctionDescriptors.BIGINT_TO_STRING
	);
	private static final MethodHandle BIGINT_FORMAT_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_format",
			FunctionDescriptors.BIGINT_FORMAT
	);
	private static final MethodHandle BIGINT_FREE_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_free",
			FunctionDescriptors.BIGINT_FREE
	);
	private static final MethodHandle BIGINT_FREE_STRING_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_free_string",
			FunctionDescriptors.BIGINT_FREE_STRING
	);
	private static final MemorySegment BIGINT_COMMA_SEPARATOR = Arena.global()
			.allocateFrom(",", java.nio.charset.StandardCharsets.UTF_8);

	public static final BigInt ZERO = createConstant(0);
	public static final BigInt ONE = createConstant(1);
	public static final BigInt TWO = createConstant(2);
	public static final BigInt TEN = createConstant(10);
	public static final BigInt NEGATIVE_ONE = createConstant(-1);

	private final MemorySegment nativePtr;
	private final Arena arena;

	/**
	 * Creates a {@code BigInt} from a primitive {@code long}.
	 *
	 * @param value the source value
	 * @return a new {@code BigInt}
	 */
	public static BigInt fromLong(long value) {
		Arena arena = Arena.ofConfined();
		MemorySegment ptr = arena.allocate(ValueLayout.ADDRESS);
		invokeOutWithLong(BIGINT_FROM_LONG_HANDLE, ptr, value);
		long rawAddr = ptr.get(ValueLayout.JAVA_LONG, 0);
		if (rawAddr == 0) throw new RuntimeException("null pointer from bigint_from_long");
		MemorySegment nativePtr = MemorySegment.ofAddress(rawAddr)
			.reinterpret(arena, null)
			.reinterpret(Long.MAX_VALUE);
		return new BigInt(nativePtr, arena);
	}

	/**
	 * Parses a string representation in the given radix.
	 *
	 * @param value the string to parse
	 * @param radix the base, between 2 and 62 inclusive
	 * @return a new {@code BigInt}
	 */
	public static BigInt fromString(String value, int radix) {
		Arena arena = Arena.ofConfined();
		MemorySegment ptr = arena.allocate(ValueLayout.ADDRESS);
		try (Arena tmp = Arena.ofConfined()) {
			MemorySegment str = tmp.allocateFrom(value, java.nio.charset.StandardCharsets.UTF_8);
			invokeOutAddressInt(BIGINT_FROM_STRING_HANDLE, ptr, str, radix);
		}
		return new BigInt(ptr.get(ValueLayout.ADDRESS, 0).reinterpret(arena, null), arena);
	}

	/**
	 * Converts a {@link BigInteger} to a {@code BigInt}.
	 *
	 * @param val the source value
	 * @return a new {@code BigInt}
	 */
	public static BigInt fromBigInteger(BigInteger val) {
		return fromString(val.toString(), 10);
	}

	/**
	 * Returns {@code this + other}.
	 *
	 * @param other the value to add
	 * @return the sum
	 */
	public BigInt add(BigInt other) {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		invokeBinaryOut(BIGINT_ADD_HANDLE, result, nativePtr, other.nativePtr);
		return new BigInt(result.get(ValueLayout.ADDRESS, 0).reinterpret(arena, null), arena);
	}

	/**
	 * Returns {@code this - other}.
	 *
	 * @param other the value to subtract
	 * @return the difference
	 */
	public BigInt subtract(BigInt other) {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		invokeBinaryOut(BIGINT_SUB_HANDLE, result, nativePtr, other.nativePtr);
		return new BigInt(result.get(ValueLayout.ADDRESS, 0).reinterpret(arena, null), arena);
	}

	/**
	 * Returns {@code this * other}.
	 *
	 * @param other the value to multiply by
	 * @return the product
	 */
	public BigInt multiply(BigInt other) {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		invokeBinaryOut(BIGINT_MUL_HANDLE, result, nativePtr, other.nativePtr);
		return new BigInt(result.get(ValueLayout.ADDRESS, 0).reinterpret(arena, null), arena);
	}

	/**
	 * Returns {@code this / other} (integer division, truncating toward zero).
	 *
	 * @param other the divisor
	 * @return the quotient
	 */
	public BigInt divide(BigInt other) {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		invokeBinaryOut(BIGINT_DIV_HANDLE, result, nativePtr, other.nativePtr);
		return new BigInt(result.get(ValueLayout.ADDRESS, 0).reinterpret(arena, null), arena);
	}

	/**
	 * Returns {@code this % other} (non-negative remainder).
	 *
	 * @param other the modulus
	 * @return the remainder
	 */
	public BigInt mod(BigInt other) {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		invokeBinaryOut(BIGINT_MOD_HANDLE, result, nativePtr, other.nativePtr);
		return new BigInt(result.get(ValueLayout.ADDRESS, 0).reinterpret(arena, null), arena);
	}

	/**
	 * Returns {@code this}<sup>{@code exp}</sup>.
	 *
	 * @param exp the exponent
	 * @return the power
	 */
	public BigInt pow(long exp) {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		invokeOutAddressLong(BIGINT_POW_HANDLE, result, nativePtr, exp);
		return new BigInt(result.get(ValueLayout.ADDRESS, 0).reinterpret(arena, null), arena);
	}

	/**
	 * Returns {@code -this}.
	 *
	 * @return the negated value
	 */
	public BigInt negate() {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		invokeUnaryOut(BIGINT_NEG_HANDLE, result, nativePtr);
		return new BigInt(result.get(ValueLayout.ADDRESS, 0).reinterpret(arena, null), arena);
	}

	/**
	 * Returns the absolute value.
	 *
	 * @return {@code |this|}
	 */
	public BigInt abs() {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		invokeUnaryOut(BIGINT_ABS_HANDLE, result, nativePtr);
		return new BigInt(result.get(ValueLayout.ADDRESS, 0).reinterpret(arena, null), arena);
	}

	/**
	 * Returns the greatest common divisor of {@code this} and {@code other}.
	 *
	 * @param other the other value
	 * @return the GCD
	 */
	public BigInt gcd(BigInt other) {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		invokeBinaryOut(BIGINT_GCD_HANDLE, result, nativePtr, other.nativePtr);
		return new BigInt(result.get(ValueLayout.ADDRESS, 0).reinterpret(arena, null), arena);
	}

	/**
	 * Returns the least common multiple of {@code this} and {@code other}.
	 *
	 * @param other the other value
	 * @return the LCM
	 */
	public BigInt lcm(BigInt other) {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		invokeBinaryOut(BIGINT_LCM_HANDLE, result, nativePtr, other.nativePtr);
		return new BigInt(result.get(ValueLayout.ADDRESS, 0).reinterpret(arena, null), arena);
	}

	/**
	 * Returns the integer square root (truncated).
	 *
	 * @return floor(sqrt(this))
	 */
	public BigInt sqrt() {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		invokeUnaryOut(BIGINT_SQRT_HANDLE, result, nativePtr);
		return new BigInt(result.get(ValueLayout.ADDRESS, 0).reinterpret(arena, null), arena);
	}

	/**
	 * Returns bitwise AND of {@code this} and {@code other}.
	 */
	public BigInt and(BigInt other) {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		invokeBinaryOut(BIGINT_AND_HANDLE, result, nativePtr, other.nativePtr);
		return new BigInt(result.get(ValueLayout.ADDRESS, 0).reinterpret(arena, null), arena);
	}

	/**
	 * Returns bitwise OR of {@code this} and {@code other}.
	 */
	public BigInt or(BigInt other) {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		invokeBinaryOut(BIGINT_OR_HANDLE, result, nativePtr, other.nativePtr);
		return new BigInt(result.get(ValueLayout.ADDRESS, 0).reinterpret(arena, null), arena);
	}

	/**
	 * Returns bitwise XOR of {@code this} and {@code other}.
	 */
	public BigInt xor(BigInt other) {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		invokeBinaryOut(BIGINT_XOR_HANDLE, result, nativePtr, other.nativePtr);
		return new BigInt(result.get(ValueLayout.ADDRESS, 0).reinterpret(arena, null), arena);
	}

	/**
	 * Returns {@code this << bits}.
	 *
	 * @param bits number of bits to shift left
	 */
	public BigInt shiftLeft(long bits) {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		invokeOutAddressLong(BIGINT_SHL_HANDLE, result, nativePtr, bits);
		return new BigInt(result.get(ValueLayout.ADDRESS, 0).reinterpret(arena, null), arena);
	}

	/**
	 * Returns {@code this >> bits} (arithmetic right shift).
	 *
	 * @param bits number of bits to shift right
	 */
	public BigInt shiftRight(long bits) {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		invokeOutAddressLong(BIGINT_SHR_HANDLE, result, nativePtr, bits);
		return new BigInt(result.get(ValueLayout.ADDRESS, 0).reinterpret(arena, null), arena);
	}

	/**
	 * Returns the factorial of {@code n}.
	 *
	 * @param n non-negative integer
	 * @return {@code n!}
	 */
	public static BigInt factorial(long n) {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		invokeOutWithLong(BIGINT_FACTORIAL_HANDLE, result, n);
		return new BigInt(result.get(ValueLayout.ADDRESS, 0).reinterpret(arena, null), arena);
	}

	/**
	 * Returns the smallest prime greater than {@code this}.
	 *
	 * @return the next prime
	 */
	public BigInt nextPrime() {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		invokeUnaryOut(BIGINT_NEXT_PRIME_HANDLE, result, nativePtr);
		return new BigInt(result.get(ValueLayout.ADDRESS, 0).reinterpret(arena, null), arena);
	}

	/**
	 * Compares this value with the specified value.
	 *
	 * @param other the value to compare
	 * @return 0 if equal, less than 0 if this is less, greater than 0 if this is greater
	 */
	@Override
	public int compareTo(BigInt other) {
		return invokeIntBinary(BIGINT_CMP_HANDLE, nativePtr, other.nativePtr);
	}

	/**
	 * Returns the signum: {@code -1} (negative), {@code 0} (zero), or
	 * {@code 1} (positive).
	 */
	public int signum() {
		return invokeIntUnary(BIGINT_SIGN_HANDLE, nativePtr);
	}

	/**
	 * Miller-Rabin probabilistic primality test.
	 *
	 * @param certainty number of iterations
	 * @return {@code true} if probably prime
	 */
	public boolean isProbablyPrime(int certainty) {
		return invokeIntAddressInt(BIGINT_IS_PROBABLY_PRIME_HANDLE, nativePtr, certainty) != 0;
	}

	/**
	 * Returns the string representation in the given radix.
	 *
	 * @param radix base, between 2 and 62 inclusive
	 */
	public String toString(int radix) {
		MemorySegment result = invokeStringWithRadix(BIGINT_TO_STRING_HANDLE, nativePtr, radix);
		try {
			return result.reinterpret(Long.MAX_VALUE).getString(0);
		} finally {
			invokeVoidAddress(BIGINT_FREE_STRING_HANDLE, result);
		}
	}

	/**
	 * Converts to {@link BigInteger}.
	 */
	public BigInteger toBigInteger() {
		return new BigInteger(toString(10));
	}

	/**
	 * Returns the formatted string with default grouping (group size 3,
	 * comma separator).
	 */
	public String toFormattedString() {
		MemorySegment result = invokeFormat(BIGINT_FORMAT_HANDLE, nativePtr, 3, BIGINT_COMMA_SEPARATOR);
		try {
			return result.reinterpret(Long.MAX_VALUE).getString(0);
		} finally {
			invokeVoidAddress(BIGINT_FREE_STRING_HANDLE, result);
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
			MemorySegment result = invokeFormat(BIGINT_FORMAT_HANDLE, nativePtr, groupSize, BIGINT_COMMA_SEPARATOR);
			try {
				return result.reinterpret(Long.MAX_VALUE).getString(0);
			} finally {
				invokeVoidAddress(BIGINT_FREE_STRING_HANDLE, result);
			}
		}
		try (Arena tmp = Arena.ofConfined()) {
			MemorySegment separator = tmp.allocateFrom(groupSep, java.nio.charset.StandardCharsets.UTF_8);
			MemorySegment result = invokeFormat(BIGINT_FORMAT_HANDLE, nativePtr, groupSize, separator);
			try {
				return result.reinterpret(Long.MAX_VALUE).getString(0);
			} finally {
				invokeVoidAddress(BIGINT_FREE_STRING_HANDLE, result);
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
	 * Frees the native memory backing this instance.
	 */
	@Override
	public int intValue() {
		return (int) longValue();
	}

	@Override
	public long longValue() {
		return invokeLongUnary(BIGINT_TO_LONG_HANDLE, nativePtr);
	}

	@Override
	public float floatValue() {
		return (float) doubleValue();
	}

	@Override
	public double doubleValue() {
		return invokeDoubleUnary(BIGINT_TO_DOUBLE_HANDLE, nativePtr);
	}

	@Override
	public void close() {
		invokeVoidAddress(BIGINT_FREE_HANDLE, nativePtr);
		arena.close();
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

	private static void invokeOutAddressLong(MethodHandle handle, MemorySegment out, MemorySegment value, long argument) {
		try {
			handle.invokeExact(out, value, argument);
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

	private static void invokeOutAddressInt(MethodHandle handle, MemorySegment out, MemorySegment value, int argument) {
		try {
			handle.invokeExact(out, value, argument);
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

	private static int invokeIntAddressInt(MethodHandle handle, MemorySegment value, int argument) {
		try {
			return (int) handle.invokeExact(value, argument);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	private static long invokeLongUnary(MethodHandle handle, MemorySegment value) {
		try {
			return (long) handle.invokeExact(value);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	private static double invokeDoubleUnary(MethodHandle handle, MemorySegment value) {
		try {
			return (double) handle.invokeExact(value);
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
	 * Creates a constant {@code BigInt} in the global arena.
	 *
	 * @param value the source value
	 * @return a new constant {@code BigInt}
	 */
	private static BigInt createConstant(long value) {
		Arena arena = Arena.global();
		MemorySegment ptr = arena.allocate(ValueLayout.ADDRESS);
		invokeOutWithLong(BIGINT_FROM_LONG_HANDLE, ptr, value);
		long rawAddr = ptr.get(ValueLayout.JAVA_LONG, 0);
		if (rawAddr == 0) throw new RuntimeException("null pointer from bigint_from_long");
		MemorySegment nativePtr = MemorySegment.ofAddress(rawAddr)
				.reinterpret(arena, null)
				.reinterpret(Long.MAX_VALUE);
		return new BigInt(nativePtr, arena);
	}
}
