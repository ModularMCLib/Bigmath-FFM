package com.modularmc.bigmath;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Cleaner;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Arbitrary-precision integer backed by the native bigmath library (GMP).
 * <p>
 * Supports arithmetic, bitwise, comparison, primality testing, and formatted
 * string conversion. Each instance wraps an ABI-v2 native handle; call
 * {@link #close()} to free the underlying resource, or use try-with-resources.
 * A cleaner is retained only as a fallback for abandoned owned values.
 * <p>
 * The ordinary arithmetic methods allocate and return a new native-backed
 * value, leaving the original operands unchanged. For hot loops, {@code set}
 * and {@code *Into} methods mutate the current native value so callers can
 * reuse an existing result object and avoid repeated native allocations.
 * <p>
 * Constants {@link #ZERO}, {@link #ONE}, {@link #TWO}, {@link #TEN}, and
 * {@link #NEGATIVE_ONE} are permanent read-only handles and reject both
 * mutation and {@link #close()}.
 * <p>
 * Owned values may be read from different threads while open. Concurrent
 * mutation, mutation concurrent with reads, and concurrent close are not
 * supported.
 */
public final class BigInt extends Number implements AutoCloseable, Comparable<BigInt> {

	static final Cleaner CLEANER = Cleaner.create();

	static final MethodHandle BIGINT_ADD_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_add",
			FunctionDescriptors.HANDLE_BINARY
	);
	static final MethodHandle BIGINT_CMP_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_cmp",
			FunctionDescriptors.HANDLE_INT_BINARY
	);
	static final MethodHandle BIGINT_DIV_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_div",
			FunctionDescriptors.HANDLE_BINARY
	);
	static final MethodHandle BIGINT_MOD_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_mod",
			FunctionDescriptors.HANDLE_BINARY
	);
	static final MethodHandle BIGINT_MUL_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_mul",
			FunctionDescriptors.HANDLE_BINARY
	);
	static final MethodHandle BIGINT_POW_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_pow",
			FunctionDescriptors.HANDLE_ADDRESS_LONG
	);
	static final MethodHandle BIGINT_POWM_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_powm",
			FunctionDescriptors.HANDLE_TERNARY
	);
	static final MethodHandle BIGINT_SUB_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_sub",
			FunctionDescriptors.HANDLE_BINARY
	);
	static final MethodHandle BIGINT_NEG_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_neg",
			FunctionDescriptors.HANDLE_UNARY
	);
	static final MethodHandle BIGINT_ABS_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_abs",
			FunctionDescriptors.HANDLE_UNARY
	);
	static final MethodHandle BIGINT_GCD_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_gcd",
			FunctionDescriptors.HANDLE_BINARY
	);
	static final MethodHandle BIGINT_LCM_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_lcm",
			FunctionDescriptors.HANDLE_BINARY
	);
	static final MethodHandle BIGINT_SQRT_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_sqrt",
			FunctionDescriptors.HANDLE_UNARY
	);
	static final MethodHandle BIGINT_AND_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_and",
			FunctionDescriptors.HANDLE_BINARY
	);
	static final MethodHandle BIGINT_OR_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_or",
			FunctionDescriptors.HANDLE_BINARY
	);
	static final MethodHandle BIGINT_XOR_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_xor",
			FunctionDescriptors.HANDLE_BINARY
	);
	static final MethodHandle BIGINT_SHL_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_shl",
			FunctionDescriptors.HANDLE_ADDRESS_LONG
	);
	static final MethodHandle BIGINT_SHR_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_shr",
			FunctionDescriptors.HANDLE_ADDRESS_LONG
	);
	static final MethodHandle BIGINT_FACTORIAL_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_factorial",
			FunctionDescriptors.HANDLE_FROM_LONG
	);
	static final MethodHandle BIGINT_NEXT_PRIME_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_next_prime",
			FunctionDescriptors.HANDLE_UNARY
	);
	static final MethodHandle BIGINT_IS_PROBABLY_PRIME_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_is_probably_prime",
			FunctionDescriptors.HANDLE_INT_ADDRESS_INT
	);
	static final MethodHandle BIGINT_FROM_LONG_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_from_long",
			FunctionDescriptors.HANDLE_FROM_LONG
	);
	static final MethodHandle BIGINT_FROM_STRING_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_from_string",
			FunctionDescriptors.HANDLE_FROM_ADDRESS_INT
	);
	static final MethodHandle BIGINT_FROM_BYTES_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_from_twos_complement",
			FunctionDescriptors.HANDLE_FROM_BYTES
	);
	static final MethodHandle BIGINT_CONSTANT_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_constant",
			FunctionDescriptors.HANDLE_FROM_LONG
	);
	static final MethodHandle BIGINT_COPY_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_copy",
			FunctionDescriptors.HANDLE_UNARY
	);
	static final MethodHandle BIGINT_SET_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_set",
			FunctionDescriptors.HANDLE_MUTATE
	);
	static final MethodHandle BIGINT_SET_LONG_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_set_long",
			FunctionDescriptors.HANDLE_MUTATE_LONG
	);
	static final MethodHandle BIGINT_SET_STRING_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_set_string",
			FunctionDescriptors.HANDLE_MUTATE_ADDRESS_INT
	);
	static final MethodHandle BIGINT_ADD_INTO_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_add_into",
			FunctionDescriptors.HANDLE_MUTATE_BINARY
	);
	static final MethodHandle BIGINT_MUL_INTO_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_mul_into",
			FunctionDescriptors.HANDLE_MUTATE_BINARY
	);
	static final MethodHandle BIGINT_DIV_INTO_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_div_into",
			FunctionDescriptors.HANDLE_MUTATE_BINARY
	);
	static final MethodHandle BIGINT_SQRT_INTO_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_sqrt_into",
			FunctionDescriptors.HANDLE_MUTATE
	);
	static final MethodHandle BIGINT_SIGN_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_sign",
			FunctionDescriptors.HANDLE_INT_UNARY
	);
	static final MethodHandle BIGINT_TO_LONG_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_to_long",
			FunctionDescriptors.HANDLE_LONG_UNARY
	);
	static final MethodHandle BIGINT_TO_DOUBLE_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_to_double",
			FunctionDescriptors.HANDLE_DOUBLE_UNARY
	);
	static final MethodHandle BIGINT_TO_STRING_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_to_string",
			FunctionDescriptors.HANDLE_STRING_RADIX
	);
	static final MethodHandle BIGINT_FORMAT_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_format",
			FunctionDescriptors.HANDLE_FORMAT
	);
	static final MethodHandle BIGINT_FREE_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_free",
			FunctionDescriptors.HANDLE_FREE
	);
	static final MethodHandle BIGINT_FREE_STRING_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_free_string",
			FunctionDescriptors.HANDLE_FREE
	);
	static final MethodHandle BIGINT_BYTES_SIZE_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_twos_complement_size",
			FunctionDescriptors.HANDLE_LONG_UNARY
	);
	static final MethodHandle BIGINT_TO_BYTES_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_to_twos_complement",
			FunctionDescriptors.HANDLE_EXPORT_BYTES
	);
	static final MethodHandle BIGINT_ID_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_id",
			FunctionDescriptors.HANDLE_LONG_UNARY
	);
	static final MethodHandle BIGINT_VERSION_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_version",
			FunctionDescriptors.HANDLE_LONG_UNARY
	);
	static final MemorySegment BIGINT_COMMA_SEPARATOR = Arena.global()
			.allocateFrom(",", java.nio.charset.StandardCharsets.UTF_8);

	public static final BigInt ZERO = createConstant(0);
	public static final BigInt ONE = createConstant(1);
	public static final BigInt TWO = createConstant(2);
	public static final BigInt TEN = createConstant(10);
	public static final BigInt NEGATIVE_ONE = createConstant(-1);

	final MemorySegment nativePtr;
	final boolean permanent;
	final NativeState nativeState;
	final Cleaner.Cleanable cleanable;

	private BigInt(MemorySegment nativePtr, boolean permanent) {
		this.nativePtr = nativePtr;
		this.permanent = permanent;
		this.nativeState = new NativeState(nativePtr, permanent);
		this.cleanable = permanent ? null : CLEANER.register(this, nativeState);
	}

	MemorySegment nativePtr() {
		BigmathFFM.requireBigIntCapability();
		if (nativeState.closed.get()) {
			throw new IllegalStateException("BigInt is closed");
		}
		return nativePtr;
	}

	static BigInt copyOf(MemorySegment value) {
		return adoptOwnedResult(invokeUnary(BIGINT_COPY_HANDLE, value));
	}

	/**
	 * Creates a {@code BigInt} from a primitive {@code long}.
	 *
	 * @param value the source value
	 * @return a new {@code BigInt}
	 */
	public static BigInt fromLong(long value) {
		BigmathFFM.requireBigIntCapability();
		return adoptOwnedResult(invokeLongToHandle(BIGINT_FROM_LONG_HANDLE, value));
	}

	/**
	 * Parses a string representation in the given radix.
	 *
	 * @param value the string to parse
	 * @param radix the base, between 2 and 62 inclusive
	 * @return a new {@code BigInt}
	 */
	public static BigInt fromString(String value, int radix) {
		BigmathFFM.requireBigIntCapability();
		try (Arena tmp = Arena.ofConfined()) {
			MemorySegment str = tmp.allocateFrom(value, StandardCharsets.UTF_8);
			return adoptOwnedResult(invokeAddressIntToHandle(BIGINT_FROM_STRING_HANDLE, str, radix));
		}
	}

	/**
	 * Converts a {@link BigInteger} to a {@code BigInt}.
	 *
	 * @param val the source value
	 * @return a new {@code BigInt}
	 */
	public static BigInt fromBigInteger(BigInteger val) {
		BigmathFFM.requireBigIntCapability();
		byte[] bytes = val.toByteArray();
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment source = arena.allocateFrom(ValueLayout.JAVA_BYTE, bytes);
			return adoptOwnedResult(invokeBytesToHandle(source, bytes.length));
		}
	}

	/**
	 * Replaces this value with {@code value}.
	 *
	 * @param value the source value
	 * @return this instance
	 */
	public BigInt set(long value) {
		ensureMutable();
		invokeMutationLong(BIGINT_SET_LONG_HANDLE, nativePtr(), value);
		return this;
	}

	/**
	 * Parses {@code value} and replaces this instance's native value.
	 *
	 * @param value the string to parse
	 * @param radix the base, between 2 and 62 inclusive
	 * @return this instance
	 */
	public BigInt set(String value, int radix) {
		ensureMutable();
		try (Arena tmp = Arena.ofConfined()) {
			MemorySegment str = tmp.allocateFrom(value, StandardCharsets.UTF_8);
			invokeMutationAddressInt(BIGINT_SET_STRING_HANDLE, nativePtr(), str, radix);
		}
		return this;
	}

	/**
	 * Copies {@code value} into this instance.
	 *
	 * @param value the source value
	 * @return this instance
	 */
	public BigInt set(BigInt value) {
		ensureMutable();
		if (this != value) {
			invokeMutation(BIGINT_SET_HANDLE, nativePtr(), value.nativePtr());
		}
		return this;
	}

	/**
	 * Replaces this value with {@code left + right}.
	 *
	 * @return this instance
	 */
	public BigInt addInto(BigInt left, BigInt right) {
		ensureMutable();
		invokeMutationBinary(BIGINT_ADD_INTO_HANDLE, nativePtr(), left.nativePtr(), right.nativePtr());
		return this;
	}

	/**
	 * Replaces this value with {@code left * right}.
	 *
	 * @return this instance
	 */
	public BigInt multiplyInto(BigInt left, BigInt right) {
		ensureMutable();
		invokeMutationBinary(BIGINT_MUL_INTO_HANDLE, nativePtr(), left.nativePtr(), right.nativePtr());
		return this;
	}

	/**
	 * Replaces this value with {@code left / right}.
	 *
	 * @return this instance
	 */
	public BigInt divideInto(BigInt left, BigInt right) {
		ensureMutable();
		invokeMutationBinary(BIGINT_DIV_INTO_HANDLE, nativePtr(), left.nativePtr(), right.nativePtr());
		return this;
	}

	/**
	 * Replaces this value with {@code floor(sqrt(value))}.
	 *
	 * @return this instance
	 */
	public BigInt sqrtInto(BigInt value) {
		ensureMutable();
		invokeMutation(BIGINT_SQRT_INTO_HANDLE, nativePtr(), value.nativePtr());
		return this;
	}

	/**
	 * Returns {@code this + other}.
	 *
	 * @param other the value to add
	 * @return the sum
	 */
	public BigInt add(BigInt other) {
		return adoptOwnedResult(invokeBinary(BIGINT_ADD_HANDLE, nativePtr(), other.nativePtr()));
	}

	/**
	 * Returns {@code this - other}.
	 *
	 * @param other the value to subtract
	 * @return the difference
	 */
	public BigInt subtract(BigInt other) {
		return adoptOwnedResult(invokeBinary(BIGINT_SUB_HANDLE, nativePtr(), other.nativePtr()));
	}

	/**
	 * Returns {@code this * other}.
	 *
	 * @param other the value to multiply by
	 * @return the product
	 */
	public BigInt multiply(BigInt other) {
		return adoptOwnedResult(invokeBinary(BIGINT_MUL_HANDLE, nativePtr(), other.nativePtr()));
	}

	/**
	 * Returns {@code this^exp mod modulus}. For very large moduli the modular
	 * multiplications are GPU-accelerated (Barrett reduction); otherwise this
	 * matches GMP's {@code mpz_powm}.
	 *
	 * @param exp the exponent (must be non-negative)
	 * @param modulus the modulus (must be positive)
	 * @return {@code this^exp mod modulus}
	 */
	public BigInt modPow(BigInt exp, BigInt modulus) {
		return adoptOwnedResult(invokeTernary(
			BIGINT_POWM_HANDLE,
			nativePtr(),
			exp.nativePtr(),
			modulus.nativePtr()
		));
	}

	/**
	 * Returns {@code this / other} (integer division, truncating toward zero).
	 *
	 * @param other the divisor
	 * @return the quotient
	 */
	public BigInt divide(BigInt other) {
		return adoptOwnedResult(invokeBinary(BIGINT_DIV_HANDLE, nativePtr(), other.nativePtr()));
	}

	/**
	 * Returns {@code this % other} (non-negative remainder).
	 *
	 * @param other the modulus
	 * @return the remainder
	 */
	public BigInt mod(BigInt other) {
		return adoptOwnedResult(invokeBinary(BIGINT_MOD_HANDLE, nativePtr(), other.nativePtr()));
	}

	/**
	 * Returns {@code this}<sup>{@code exp}</sup>.
	 *
	 * @param exp the exponent
	 * @return the power
	 */
	public BigInt pow(long exp) {
		return adoptOwnedResult(invokeAddressLongToHandle(BIGINT_POW_HANDLE, nativePtr(), exp));
	}

	/**
	 * Returns {@code -this}.
	 *
	 * @return the negated value
	 */
	public BigInt negate() {
		return adoptOwnedResult(invokeUnary(BIGINT_NEG_HANDLE, nativePtr()));
	}

	/**
	 * Returns the absolute value.
	 *
	 * @return {@code |this|}
	 */
	public BigInt abs() {
		return adoptOwnedResult(invokeUnary(BIGINT_ABS_HANDLE, nativePtr()));
	}

	/**
	 * Returns the greatest common divisor of {@code this} and {@code other}.
	 *
	 * @param other the other value
	 * @return the GCD
	 */
	public BigInt gcd(BigInt other) {
		return adoptOwnedResult(invokeBinary(BIGINT_GCD_HANDLE, nativePtr(), other.nativePtr()));
	}

	/**
	 * Returns the least common multiple of {@code this} and {@code other}.
	 *
	 * @param other the other value
	 * @return the LCM
	 */
	public BigInt lcm(BigInt other) {
		return adoptOwnedResult(invokeBinary(BIGINT_LCM_HANDLE, nativePtr(), other.nativePtr()));
	}

	/**
	 * Returns the integer square root (truncated).
	 *
	 * @return floor(sqrt(this))
	 */
	public BigInt sqrt() {
		return adoptOwnedResult(invokeUnary(BIGINT_SQRT_HANDLE, nativePtr()));
	}

	/**
	 * Returns bitwise AND of {@code this} and {@code other}.
	 */
	public BigInt and(BigInt other) {
		return adoptOwnedResult(invokeBinary(BIGINT_AND_HANDLE, nativePtr(), other.nativePtr()));
	}

	/**
	 * Returns bitwise OR of {@code this} and {@code other}.
	 */
	public BigInt or(BigInt other) {
		return adoptOwnedResult(invokeBinary(BIGINT_OR_HANDLE, nativePtr(), other.nativePtr()));
	}

	/**
	 * Returns bitwise XOR of {@code this} and {@code other}.
	 */
	public BigInt xor(BigInt other) {
		return adoptOwnedResult(invokeBinary(BIGINT_XOR_HANDLE, nativePtr(), other.nativePtr()));
	}

	/**
	 * Returns {@code this << bits}.
	 *
	 * @param bits number of bits to shift left
	 */
	public BigInt shiftLeft(long bits) {
		return adoptOwnedResult(invokeAddressLongToHandle(BIGINT_SHL_HANDLE, nativePtr(), bits));
	}

	/**
	 * Returns {@code this >> bits} (arithmetic right shift).
	 *
	 * @param bits number of bits to shift right
	 */
	public BigInt shiftRight(long bits) {
		return adoptOwnedResult(invokeAddressLongToHandle(BIGINT_SHR_HANDLE, nativePtr(), bits));
	}

	/**
	 * Returns the factorial of {@code n}.
	 *
	 * @param n non-negative integer
	 * @return {@code n!}
	 */
	public static BigInt factorial(long n) {
		BigmathFFM.requireBigIntCapability();
		return adoptOwnedResult(invokeLongToHandle(BIGINT_FACTORIAL_HANDLE, n));
	}

	/**
	 * Returns the smallest prime greater than {@code this}.
	 *
	 * @return the next prime
	 */
	public BigInt nextPrime() {
		return adoptOwnedResult(invokeUnary(BIGINT_NEXT_PRIME_HANDLE, nativePtr()));
	}

	/**
	 * Compares this value with the specified value.
	 *
	 * @param other the value to compare
	 * @return 0 if equal, less than 0 if this is less, greater than 0 if this is greater
	 */
	@Override
	public int compareTo(BigInt other) {
		return invokeIntBinary(nativePtr(), other.nativePtr());
	}

	/**
	 * Returns the signum: {@code -1} (negative), {@code 0} (zero), or
	 * {@code 1} (positive).
	 */
	public int signum() {
		return invokeIntUnary(nativePtr());
	}

	/**
	 * Miller-Rabin probabilistic primality test.
	 *
	 * @param certainty number of iterations
	 * @return {@code true} if probably prime
	 */
	public boolean isProbablyPrime(int certainty) {
		return invokeIntAddressInt(nativePtr(), certainty) != 0;
	}

	/**
	 * Returns the string representation in the given radix.
	 *
	 * @param radix base, between 2 and 62 inclusive
	 */
	public String toString(int radix) {
		MemorySegment result = invokeStringWithRadix(nativePtr(), radix);
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
		MemorySegment handle = nativePtr();
		long byteCount = invokeLong(BIGINT_BYTES_SIZE_HANDLE, handle);
		if (byteCount <= 0 || byteCount > Integer.MAX_VALUE) {
			throw new ArithmeticException("BigInt two's-complement export is too large: " + byteCount);
		}
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment bytes = arena.allocate(byteCount);
			invokeExportBytes(handle, bytes, byteCount);
			return new BigInteger(bytes.toArray(ValueLayout.JAVA_BYTE));
		}
	}

	/**
	 * Returns the formatted string with default grouping (group size 3,
	 * comma separator).
	 */
	public String toFormattedString() {
		MemorySegment result = invokeFormat(nativePtr(), 3, BIGINT_COMMA_SEPARATOR);
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
			MemorySegment result = invokeFormat(nativePtr(), groupSize, BIGINT_COMMA_SEPARATOR);
			try {
				return result.reinterpret(Long.MAX_VALUE).getString(0);
			} finally {
				invokeVoidAddress(BIGINT_FREE_STRING_HANDLE, result);
			}
		}
		try (Arena tmp = Arena.ofConfined()) {
			MemorySegment separator = tmp.allocateFrom(groupSep, java.nio.charset.StandardCharsets.UTF_8);
			MemorySegment result = invokeFormat(nativePtr(), groupSize, separator);
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
		return invokeLongUnary(nativePtr());
	}

	@Override
	public float floatValue() {
		return (float) doubleValue();
	}

	@Override
	public double doubleValue() {
		return invokeDoubleUnary(nativePtr());
	}

	@Override
	public void close() {
		if (permanent) {
			throw new UnsupportedOperationException("BigInt constants are permanent and cannot be closed");
		}
		cleanable.clean();
	}

	static MemorySegment invokeBinary(MethodHandle handle, MemorySegment left, MemorySegment right) {
		try {
			return (MemorySegment) handle.invokeExact(left, right);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	static MemorySegment invokeTernary(MethodHandle handle, MemorySegment a, MemorySegment b, MemorySegment c) {
		try {
			return (MemorySegment) handle.invokeExact(a, b, c);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	static MemorySegment invokeUnary(MethodHandle handle, MemorySegment value) {
		try {
			return (MemorySegment) handle.invokeExact(value);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	static MemorySegment invokeLongToHandle(MethodHandle handle, long value) {
		try {
			return (MemorySegment) handle.invokeExact(value);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	static MemorySegment invokeAddressLongToHandle(MethodHandle handle, MemorySegment value, long argument) {
		try {
			return (MemorySegment) handle.invokeExact(value, argument);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	static MemorySegment invokeAddressIntToHandle(MethodHandle handle, MemorySegment value, int argument) {
		try {
			return (MemorySegment) handle.invokeExact(value, argument);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	static MemorySegment invokeBytesToHandle(MemorySegment value, long byteCount) {
		try {
			return (MemorySegment) BIGINT_FROM_BYTES_HANDLE.invokeExact(value, byteCount);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	static void invokeMutation(MethodHandle handle, MemorySegment out, MemorySegment value) {
		try {
			checkMutationStatus((int) handle.invokeExact(out, value));
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	static void invokeMutationLong(MethodHandle handle, MemorySegment out, long value) {
		try {
			checkMutationStatus((int) handle.invokeExact(out, value));
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	static void invokeMutationAddressInt(MethodHandle handle, MemorySegment out, MemorySegment value, int argument) {
		try {
			checkMutationStatus((int) handle.invokeExact(out, value, argument));
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	static void invokeMutationBinary(MethodHandle handle, MemorySegment out, MemorySegment left, MemorySegment right) {
		try {
			checkMutationStatus((int) handle.invokeExact(out, left, right));
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	static void checkMutationStatus(int status) {
		if (status != 0) {
			throw new IllegalStateException("Native BigInt mutation was rejected with status " + status);
		}
	}

	static MemorySegment invokeStringWithRadix(MemorySegment value, int radix) {
		try {
			return (MemorySegment) BIGINT_TO_STRING_HANDLE.invokeExact(value, radix);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	static MemorySegment invokeFormat(MemorySegment value, int groupSize, MemorySegment separator) {
		try {
			return (MemorySegment) BIGINT_FORMAT_HANDLE.invokeExact(value, groupSize, separator);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	static int invokeIntBinary(MemorySegment left, MemorySegment right) {
		try {
			return (int) BIGINT_CMP_HANDLE.invokeExact(left, right);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	static int invokeIntUnary(MemorySegment value) {
		try {
			return (int) BIGINT_SIGN_HANDLE.invokeExact(value);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	static int invokeIntAddressInt(MemorySegment value, int argument) {
		try {
			return (int) BIGINT_IS_PROBABLY_PRIME_HANDLE.invokeExact(value, argument);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	static long invokeLongUnary(MemorySegment value) {
		try {
			return (long) BIGINT_TO_LONG_HANDLE.invokeExact(value);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	static long invokeLong(MethodHandle handle, MemorySegment value) {
		try {
			return (long) handle.invokeExact(value);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	static double invokeDoubleUnary(MemorySegment value) {
		try {
			return (double) BIGINT_TO_DOUBLE_HANDLE.invokeExact(value);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	static void invokeVoidAddress(MethodHandle handle, MemorySegment value) {
		try {
			handle.invokeExact(value);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	static void invokeExportBytes(MemorySegment value, MemorySegment out, long byteCount) {
		try {
			int status = (int) BIGINT_TO_BYTES_HANDLE.invokeExact(value, out, byteCount);
			if (status != 0) {
				throw new IllegalStateException("Native BigInt export failed with status " + status);
			}
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	static BigInt adoptOwnedResult(MemorySegment result) {
		if (result == null || result.equals(MemorySegment.NULL)) {
			throw new OutOfMemoryError("Native BigInt allocation returned null");
		}
		return new BigInt(result, false);
	}

	/**
	 * Creates a constant {@code BigInt} in the global arena.
	 *
	 * @param value the source value
	 * @return a new constant {@code BigInt}
	 */
	static BigInt createConstant(long value) {
		if (!BigmathFFM.hasCapability(BigmathFFM.CAPABILITY_BIGINT)) {
			return new BigInt(MemorySegment.NULL, true);
		}
		MemorySegment result = invokeLongToHandle(BIGINT_CONSTANT_HANDLE, value);
		if (result.equals(MemorySegment.NULL)) {
			throw new LinkageError("Native BigInt constant is unavailable: " + value);
		}
		return new BigInt(result, true);
	}

	long nativeId() {
		return invokeLong(BIGINT_ID_HANDLE, nativePtr());
	}

	long nativeVersion() {
		return invokeLong(BIGINT_VERSION_HANDLE, nativePtr());
	}

	void ensureMutable() {
		nativePtr();
		if (permanent) {
			throw new UnsupportedOperationException("BigInt constants are shared and cannot be mutated");
		}
	}

	static final class NativeState implements Runnable {
		final MemorySegment nativePtr;
		final boolean permanent;
		final AtomicBoolean closed = new AtomicBoolean();

		NativeState(MemorySegment nativePtr, boolean permanent) {
			this.nativePtr = nativePtr;
			this.permanent = permanent;
		}

		@Override
		public void run() {
			if (!permanent && closed.compareAndSet(false, true) && !nativePtr.equals(MemorySegment.NULL)) {
				invokeVoidAddress(BIGINT_FREE_HANDLE, nativePtr);
			}
		}
	}
}
