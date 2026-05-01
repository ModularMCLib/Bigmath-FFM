package com.modularmc.bigmath;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.math.BigInteger;

import static com.modularmc.bigmath.BigmathFFM.invoke;

/**
 * Arbitrary-precision integer backed by the native bigmath library (GMP).
 * <p>
 * Supports arithmetic, bitwise, comparison, primality testing, and formatted
 * string conversion. Each instance wraps a native heap pointer; call
 * {@link #close()} to free the underlying resource, or use
 * try-with-resources.
 * <p>
 * Constants {@link #ZERO}, {@link #ONE}, {@link #TWO}, {@link #TEN}, and
 * {@link #NEGATIVE_ONE} use a global arena and should not be closed.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class BigInt extends Number implements AutoCloseable, Comparable<BigInt> {

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
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigint_from_long",
				FunctionDescriptors.BIGINT_FROM_LONG
		);
		invoke(handle, ptr, value);
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
			MethodHandle handle = BigmathFFM.getInstance().downcall(
					"bigint_from_string",
					FunctionDescriptors.BIGINT_FROM_STRING
			);
			invoke(handle, ptr, str, radix);
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
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigint_add",
				FunctionDescriptors.BIGINT_BINARY
		);
		invoke(handle, result, nativePtr, other.nativePtr);
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
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigint_sub",
				FunctionDescriptors.BIGINT_BINARY
		);
		invoke(handle, result, nativePtr, other.nativePtr);
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
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigint_mul",
				FunctionDescriptors.BIGINT_BINARY
		);
		invoke(handle, result, nativePtr, other.nativePtr);
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
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigint_div",
				FunctionDescriptors.BIGINT_BINARY
		);
		invoke(handle, result, nativePtr, other.nativePtr);
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
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigint_mod",
				FunctionDescriptors.BIGINT_BINARY
		);
		invoke(handle, result, nativePtr, other.nativePtr);
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
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigint_pow",
				FunctionDescriptors.BIGINT_POW
		);
		invoke(handle, result, nativePtr, exp);
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
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigint_neg",
				FunctionDescriptors.BIGINT_UNARY
		);
		invoke(handle, result, nativePtr);
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
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigint_abs",
				FunctionDescriptors.BIGINT_UNARY
		);
		invoke(handle, result, nativePtr);
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
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigint_gcd",
				FunctionDescriptors.BIGINT_BINARY
		);
		invoke(handle, result, nativePtr, other.nativePtr);
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
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigint_lcm",
				FunctionDescriptors.BIGINT_BINARY
		);
		invoke(handle, result, nativePtr, other.nativePtr);
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
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigint_sqrt",
				FunctionDescriptors.BIGINT_UNARY
		);
		invoke(handle, result, nativePtr);
		return new BigInt(result.get(ValueLayout.ADDRESS, 0).reinterpret(arena, null), arena);
	}

	/**
	 * Returns bitwise AND of {@code this} and {@code other}.
	 */
	public BigInt and(BigInt other) {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigint_and",
				FunctionDescriptors.BIGINT_BINARY
		);
		invoke(handle, result, nativePtr, other.nativePtr);
		return new BigInt(result.get(ValueLayout.ADDRESS, 0).reinterpret(arena, null), arena);
	}

	/**
	 * Returns bitwise OR of {@code this} and {@code other}.
	 */
	public BigInt or(BigInt other) {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigint_or",
				FunctionDescriptors.BIGINT_BINARY
		);
		invoke(handle, result, nativePtr, other.nativePtr);
		return new BigInt(result.get(ValueLayout.ADDRESS, 0).reinterpret(arena, null), arena);
	}

	/**
	 * Returns bitwise XOR of {@code this} and {@code other}.
	 */
	public BigInt xor(BigInt other) {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigint_xor",
				FunctionDescriptors.BIGINT_BINARY
		);
		invoke(handle, result, nativePtr, other.nativePtr);
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
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigint_shl",
				FunctionDescriptors.BIGINT_POW
		);
		invoke(handle, result, nativePtr, bits);
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
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigint_shr",
				FunctionDescriptors.BIGINT_POW
		);
		invoke(handle, result, nativePtr, bits);
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
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigint_factorial",
				FunctionDescriptors.BIGINT_FROM_LONG
		);
		invoke(handle, result, n);
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
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigint_next_prime",
				FunctionDescriptors.BIGINT_UNARY
		);
		invoke(handle, result, nativePtr);
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
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigint_cmp",
				FunctionDescriptors.BIGINT_CMP
		);
		return (int) invoke(handle, nativePtr, other.nativePtr);
	}

	/**
	 * Returns the signum: {@code -1} (negative), {@code 0} (zero), or
	 * {@code 1} (positive).
	 */
	public int signum() {
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigint_sign",
				FunctionDescriptors.BIGINT_SIGN
		);
		return (int) invoke(handle, nativePtr);
	}

	/**
	 * Miller-Rabin probabilistic primality test.
	 *
	 * @param certainty number of iterations
	 * @return {@code true} if probably prime
	 */
	public boolean isProbablyPrime(int certainty) {
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigint_is_probably_prime",
				FunctionDescriptors.BIGINT_IS_PROBABLY_PRIME
		);
		int result = (int) invoke(handle, nativePtr, certainty);
		return result != 0;
	}

	/**
	 * Returns the string representation in the given radix.
	 *
	 * @param radix base, between 2 and 62 inclusive
	 */
	public String toString(int radix) {
		try (Arena tmp = Arena.ofConfined()) {
			MethodHandle handle = BigmathFFM.getInstance().downcall(
					"bigint_to_string",
					FunctionDescriptors.BIGINT_TO_STRING
			);
			MemorySegment result = (MemorySegment) invoke(handle, nativePtr, radix);
			String str = result.reinterpret(tmp, null).reinterpret(Long.MAX_VALUE).getString(0);
			MethodHandle freeHandle = BigmathFFM.getInstance().downcall(
					"bigint_free_string",
					FunctionDescriptors.BIGINT_FREE_STRING
			);
			invoke(freeHandle, result);
			return str;
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
					"bigint_format",
					FunctionDescriptors.BIGINT_FORMAT
			);
			MemorySegment result = (MemorySegment) invoke(handle, nativePtr, groupSize, sep);
			String str = result.reinterpret(tmp, null).reinterpret(Long.MAX_VALUE).getString(0);
			MethodHandle freeHandle = BigmathFFM.getInstance().downcall(
					"bigint_free_string",
					FunctionDescriptors.BIGINT_FREE_STRING
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
	 * Frees the native memory backing this instance.
	 */
	@Override
	public int intValue() {
		return (int) longValue();
	}

	@Override
	public long longValue() {
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigint_to_long",
				FunctionDescriptors.BIGINT_TO_LONG
		);
		return (long) invoke(handle, nativePtr);
	}

	@Override
	public float floatValue() {
		return (float) doubleValue();
	}

	@Override
	public double doubleValue() {
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigint_to_double",
				FunctionDescriptors.BIGINT_TO_DOUBLE
		);
		return (double) invoke(handle, nativePtr);
	}

	@Override
	public void close() {
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigint_free",
				FunctionDescriptors.BIGINT_FREE
		);
		invoke(handle, nativePtr);
		arena.close();
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
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigint_from_long",
				FunctionDescriptors.BIGINT_FROM_LONG
		);
		invoke(handle, ptr, value);
		long rawAddr = ptr.get(ValueLayout.JAVA_LONG, 0);
		if (rawAddr == 0) throw new RuntimeException("null pointer from bigint_from_long");
		MemorySegment nativePtr = MemorySegment.ofAddress(rawAddr)
				.reinterpret(arena, null)
				.reinterpret(Long.MAX_VALUE);
		return new BigInt(nativePtr, arena);
	}
}
