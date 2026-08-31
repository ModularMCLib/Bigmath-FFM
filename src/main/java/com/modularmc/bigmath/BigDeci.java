package com.modularmc.bigmath;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Cleaner;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Arbitrary-precision decimal floating-point backed by the native bigmath
 * library (MPFR).
 * <p>
 * Supports arithmetic, trigonometric, logarithmic, exponential, and rounding
 * operations. Use {@link BigNumberFormat} for pattern- and locale-aware output.
 * Each instance wraps a native handle; call
 * {@link #close()} to free the underlying resource. A cleaner is retained only
 * as a fallback for abandoned owned values.
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
public final class BigDeci extends Number implements AutoCloseable, Comparable<BigDeci> {

	static final Cleaner CLEANER = Cleaner.create();
	static final int CONSTANT_PRECISION = 128;
	static final MethodHandle BIGDECIMAL_ADD_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_add",
			FunctionDescriptors.HANDLE_BINARY
	);
	static final MethodHandle BIGDECIMAL_CMP_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_cmp",
			FunctionDescriptors.HANDLE_INT_BINARY
	);
	static final MethodHandle BIGDECIMAL_DIV_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_div",
			FunctionDescriptors.HANDLE_BINARY
	);
	static final MethodHandle BIGDECIMAL_SUB_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_sub",
			FunctionDescriptors.HANDLE_BINARY
	);
	static final MethodHandle BIGDECIMAL_MUL_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_mul",
			FunctionDescriptors.HANDLE_BINARY
	);
	static final MethodHandle BIGDECIMAL_SQRT_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_sqrt",
			FunctionDescriptors.HANDLE_UNARY
	);
	static final MethodHandle BIGDECIMAL_NEG_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_neg",
			FunctionDescriptors.HANDLE_UNARY
	);
	static final MethodHandle BIGDECIMAL_ABS_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_abs",
			FunctionDescriptors.HANDLE_UNARY
	);
	static final MethodHandle BIGDECIMAL_POW_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_pow",
			FunctionDescriptors.HANDLE_BINARY
	);
	static final MethodHandle BIGDECIMAL_POW_LONG_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_pow_long",
			FunctionDescriptors.HANDLE_ADDRESS_LONG
	);
	static final MethodHandle BIGDECIMAL_LOG_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_log",
			FunctionDescriptors.HANDLE_UNARY
	);
	static final MethodHandle BIGDECIMAL_EXP_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_exp",
			FunctionDescriptors.HANDLE_UNARY
	);
	static final MethodHandle BIGDECIMAL_SIN_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_sin",
			FunctionDescriptors.HANDLE_UNARY
	);
	static final MethodHandle BIGDECIMAL_COS_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_cos",
			FunctionDescriptors.HANDLE_UNARY
	);
	static final MethodHandle BIGDECIMAL_TAN_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_tan",
			FunctionDescriptors.HANDLE_UNARY
	);
	static final MethodHandle BIGDECIMAL_CEIL_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_ceil",
			FunctionDescriptors.HANDLE_UNARY
	);
	static final MethodHandle BIGDECIMAL_FLOOR_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_floor",
			FunctionDescriptors.HANDLE_UNARY
	);
	static final MethodHandle BIGDECIMAL_ROUND_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_round",
			FunctionDescriptors.HANDLE_UNARY
	);
	static final MethodHandle BIGDECIMAL_FROM_DOUBLE_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_from_double",
			FunctionDescriptors.HANDLE_FROM_DOUBLE_INT
	);
	static final MethodHandle BIGDECIMAL_FROM_STRING_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_from_string",
			FunctionDescriptors.HANDLE_FROM_ADDRESS_INT
	);
	static final MethodHandle BIGDECIMAL_FROM_BIGINT_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_from_bigint",
			FunctionDescriptors.HANDLE_FROM_ADDRESS_INT
	);
	static final MethodHandle BIGDECIMAL_CONSTANT_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_constant",
			FunctionDescriptors.HANDLE_FROM_DOUBLE_INT
	);
	static final MethodHandle BIGDECIMAL_COPY_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_copy",
			FunctionDescriptors.HANDLE_UNARY
	);
	static final MethodHandle BIGDECIMAL_SET_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_set",
			FunctionDescriptors.HANDLE_MUTATE
	);
	static final MethodHandle BIGDECIMAL_SET_DOUBLE_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_set_double",
			FunctionDescriptors.HANDLE_MUTATE_DOUBLE
	);
	static final MethodHandle BIGDECIMAL_SET_STRING_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_set_string",
			FunctionDescriptors.HANDLE_MUTATE_ADDRESS_INT
	);
	static final MethodHandle BIGDECIMAL_ADD_INTO_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_add_into",
			FunctionDescriptors.HANDLE_MUTATE_BINARY
	);
	static final MethodHandle BIGDECIMAL_MUL_INTO_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_mul_into",
			FunctionDescriptors.HANDLE_MUTATE_BINARY
	);
	static final MethodHandle BIGDECIMAL_DIV_INTO_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_div_into",
			FunctionDescriptors.HANDLE_MUTATE_BINARY
	);
	static final MethodHandle BIGDECIMAL_SQRT_INTO_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_sqrt_into",
			FunctionDescriptors.HANDLE_MUTATE
	);
	static final MethodHandle BIGDECIMAL_TO_DOUBLE_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_to_double",
			FunctionDescriptors.HANDLE_DOUBLE_UNARY
	);
	static final MethodHandle BIGDECIMAL_TO_STRING_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_to_string",
			FunctionDescriptors.HANDLE_STRING
	);
	static final MethodHandle BIGDECIMAL_FREE_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_free",
			FunctionDescriptors.HANDLE_FREE
	);
	static final MethodHandle BIGDECIMAL_FREE_STRING_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_free_string",
			FunctionDescriptors.HANDLE_FREE
	);
	static final MethodHandle BIGDECIMAL_ID_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_id",
			FunctionDescriptors.HANDLE_LONG_UNARY
	);
	static final MethodHandle BIGDECIMAL_VERSION_HANDLE = BigmathFFM.getInstance().downcall(
			"bigdecimal_version",
			FunctionDescriptors.HANDLE_LONG_UNARY
	);

	public static final BigDeci ZERO = createConstant(0.0);
	public static final BigDeci ONE = createConstant(1.0);
	public static final BigDeci TWO = createConstant(2.0);
	public static final BigDeci TEN = createConstant(10.0);
	public static final BigDeci NEGATIVE_ONE = createConstant(-1.0);

	final MemorySegment nativePtr;
	final boolean permanent;
	final NativeState nativeState;
	final Cleaner.Cleanable cleanable;

	private BigDeci(MemorySegment nativePtr, boolean permanent) {
		this.nativePtr = nativePtr;
		this.permanent = permanent;
		this.nativeState = new NativeState(nativePtr, permanent);
		this.cleanable = permanent ? null : CLEANER.register(this, nativeState);
	}

	MemorySegment nativePtr() {
		BigmathFFM.requireBigDeciCapability();
		if (nativeState.closed.get()) {
			throw new IllegalStateException("BigDeci is closed");
		}
		return nativePtr;
	}

	static BigDeci copyOf(MemorySegment value) {
		return adoptOwnedResult(invokeUnary(BIGDECIMAL_COPY_HANDLE, value));
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
		BigmathFFM.requireBigDeciCapability();
		return adoptOwnedResult(invokeDoubleIntToHandle(BIGDECIMAL_FROM_DOUBLE_HANDLE, value, precision));
	}

	/**
	 * Parses a decimal string with the given precision (in bits).
	 *
	 * @param value     the string to parse
	 * @param precision the MPFR precision in bits
	 * @return a new {@code BigDeci}
	 */
	public static BigDeci fromString(String value, int precision) {
		BigmathFFM.requireBigDeciCapability();
		try (Arena tmp = Arena.ofConfined()) {
			MemorySegment str = tmp.allocateFrom(value, StandardCharsets.UTF_8);
			return adoptOwnedResult(invokeAddressIntToHandle(BIGDECIMAL_FROM_STRING_HANDLE, str, precision));
		}
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
		BigmathFFM.requireBigDeciCapability();
		return adoptOwnedResult(invokeAddressIntToHandle(
			BIGDECIMAL_FROM_BIGINT_HANDLE,
			value.nativePtr(),
			precision
		));
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
		invokeMutationDouble(BIGDECIMAL_SET_DOUBLE_HANDLE, nativePtr(), value);
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
			MemorySegment str = tmp.allocateFrom(value, StandardCharsets.UTF_8);
			invokeMutationAddressInt(BIGDECIMAL_SET_STRING_HANDLE, nativePtr(), str, precision);
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
			invokeMutation(BIGDECIMAL_SET_HANDLE, nativePtr(), value.nativePtr());
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
		invokeMutationBinary(BIGDECIMAL_ADD_INTO_HANDLE, nativePtr(), left.nativePtr(), right.nativePtr());
		return this;
	}

	/**
	 * Replaces this value with {@code left * right}.
	 *
	 * @return this instance
	 */
	public BigDeci multiplyInto(BigDeci left, BigDeci right) {
		ensureMutable();
		invokeMutationBinary(BIGDECIMAL_MUL_INTO_HANDLE, nativePtr(), left.nativePtr(), right.nativePtr());
		return this;
	}

	/**
	 * Replaces this value with {@code left / right}.
	 *
	 * @return this instance
	 */
	public BigDeci divideInto(BigDeci left, BigDeci right) {
		ensureMutable();
		invokeMutationBinary(BIGDECIMAL_DIV_INTO_HANDLE, nativePtr(), left.nativePtr(), right.nativePtr());
		return this;
	}

	/**
	 * Replaces this value with {@code sqrt(value)}.
	 *
	 * @return this instance
	 */
	public BigDeci sqrtInto(BigDeci value) {
		ensureMutable();
		invokeMutation(BIGDECIMAL_SQRT_INTO_HANDLE, nativePtr(), value.nativePtr());
		return this;
	}

	/**
	 * Returns {@code this + other}.
	 */
	public BigDeci add(BigDeci other) {
		return adoptOwnedResult(invokeBinary(BIGDECIMAL_ADD_HANDLE, nativePtr(), other.nativePtr()));
	}

	/**
	 * Returns {@code this - other}.
	 */
	public BigDeci subtract(BigDeci other) {
		return adoptOwnedResult(invokeBinary(BIGDECIMAL_SUB_HANDLE, nativePtr(), other.nativePtr()));
	}

	/**
	 * Returns {@code this * other}.
	 */
	public BigDeci multiply(BigDeci other) {
		return adoptOwnedResult(invokeBinary(BIGDECIMAL_MUL_HANDLE, nativePtr(), other.nativePtr()));
	}

	/**
	 * Returns {@code this / other}.
	 */
	public BigDeci divide(BigDeci other) {
		return adoptOwnedResult(invokeBinary(BIGDECIMAL_DIV_HANDLE, nativePtr(), other.nativePtr()));
	}

	/**
	 * Returns {@code -this}.
	 */
	public BigDeci negate() {
		return adoptOwnedResult(invokeUnary(BIGDECIMAL_NEG_HANDLE, nativePtr()));
	}

	/**
	 * Returns the absolute value.
	 */
	public BigDeci abs() {
		return adoptOwnedResult(invokeUnary(BIGDECIMAL_ABS_HANDLE, nativePtr()));
	}

	/**
	 * Returns the square root.
	 */
	public BigDeci sqrt() {
		return adoptOwnedResult(invokeUnary(BIGDECIMAL_SQRT_HANDLE, nativePtr()));
	}

	/**
	 * Returns {@code this}<sup>{@code exponent}</sup>.
	 *
	 * @param exponent the exponent as a {@code BigDeci}
	 */
	public BigDeci pow(BigDeci exponent) {
		return adoptOwnedResult(invokeBinary(BIGDECIMAL_POW_HANDLE, nativePtr(), exponent.nativePtr()));
	}

	/**
	 * Returns {@code this}<sup>{@code exponent}</sup>.
	 *
	 * @param exponent the integer exponent
	 */
	public BigDeci pow(long exponent) {
		return adoptOwnedResult(invokeAddressLongToHandle(BIGDECIMAL_POW_LONG_HANDLE, nativePtr(), exponent));
	}

	/**
	 * Returns the natural logarithm.
	 */
	public BigDeci log() {
		return adoptOwnedResult(invokeUnary(BIGDECIMAL_LOG_HANDLE, nativePtr()));
	}

	/**
	 * Returns <i>e</i><sup>this</sup>.
	 */
	public BigDeci exp() {
		return adoptOwnedResult(invokeUnary(BIGDECIMAL_EXP_HANDLE, nativePtr()));
	}

	/**
	 * Returns the sine.
	 */
	public BigDeci sin() {
		return adoptOwnedResult(invokeUnary(BIGDECIMAL_SIN_HANDLE, nativePtr()));
	}

	/**
	 * Returns the cosine.
	 */
	public BigDeci cos() {
		return adoptOwnedResult(invokeUnary(BIGDECIMAL_COS_HANDLE, nativePtr()));
	}

	/**
	 * Returns the tangent.
	 */
	public BigDeci tan() {
		return adoptOwnedResult(invokeUnary(BIGDECIMAL_TAN_HANDLE, nativePtr()));
	}

	/**
	 * Returns the smallest integer greater than or equal to this value.
	 */
	public BigDeci ceil() {
		return adoptOwnedResult(invokeUnary(BIGDECIMAL_CEIL_HANDLE, nativePtr()));
	}

	/**
	 * Returns the largest integer less than or equal to this value.
	 */
	public BigDeci floor() {
		return adoptOwnedResult(invokeUnary(BIGDECIMAL_FLOOR_HANDLE, nativePtr()));
	}

	/**
	 * Returns the nearest integer (rounds half away from zero).
	 */
	public BigDeci round() {
		return adoptOwnedResult(invokeUnary(BIGDECIMAL_ROUND_HANDLE, nativePtr()));
	}

	/**
	 * Compares this value with the specified value.
	 *
	 * @param other the value to compare
	 * @return 0 if equal, less than 0 if this is less, greater than 0 if this is greater
	 */
	@Override
	public int compareTo(BigDeci other) {
		return invokeIntBinary(nativePtr(), other.nativePtr());
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
		return invokeDoubleUnary(nativePtr());
	}

	/**
	 * Returns the full-precision decimal string representation.
	 */
	@Override
	public String toString() {
		MemorySegment result = invokeString(nativePtr());
		try {
			return result.reinterpret(Long.MAX_VALUE).getString(0);
		} finally {
			invokeVoidAddress(BIGDECIMAL_FREE_STRING_HANDLE, result);
		}
	}

	/**
	 * Frees the native MPFR memory backing this instance.
	 */
	@Override
	public void close() {
		if (permanent) {
			throw new UnsupportedOperationException("BigDeci constants are permanent and cannot be closed");
		}
		cleanable.clean();
	}

	/**
	 * Creates a constant {@code BigDeci} in the global arena.
	 *
	 * @param value the source value
	 * @return a new constant {@code BigDeci}
	 */
	static BigDeci createConstant(double value) {
		if (!BigmathFFM.hasCapability(BigmathFFM.CAPABILITY_BIGDECI)) {
			return new BigDeci(MemorySegment.NULL, true);
		}
		MemorySegment result = invokeDoubleIntToHandle(BIGDECIMAL_CONSTANT_HANDLE, value, CONSTANT_PRECISION);
		if (result.equals(MemorySegment.NULL)) {
			throw new LinkageError("Native BigDeci constant is unavailable: " + value);
		}
		return new BigDeci(result, true);
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

	static MemorySegment invokeUnary(MethodHandle handle, MemorySegment value) {
		try {
			return (MemorySegment) handle.invokeExact(value);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	static MemorySegment invokeString(MemorySegment value) {
		try {
			return (MemorySegment) BIGDECIMAL_TO_STRING_HANDLE.invokeExact(value);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	static int invokeIntBinary(MemorySegment left, MemorySegment right) {
		try {
			return (int) BIGDECIMAL_CMP_HANDLE.invokeExact(left, right);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	static MemorySegment invokeDoubleIntToHandle(MethodHandle handle, double value, int precision) {
		try {
			return (MemorySegment) handle.invokeExact(value, precision);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	static MemorySegment invokeAddressIntToHandle(MethodHandle handle, MemorySegment value, int precision) {
		try {
			return (MemorySegment) handle.invokeExact(value, precision);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	static double invokeDoubleUnary(MemorySegment value) {
		try {
			return (double) BIGDECIMAL_TO_DOUBLE_HANDLE.invokeExact(value);
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

	static void invokeVoidAddress(MethodHandle handle, MemorySegment value) {
		try {
			handle.invokeExact(value);
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

	static void invokeMutationDouble(MethodHandle handle, MemorySegment out, double value) {
		try {
			checkMutationStatus((int) handle.invokeExact(out, value));
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	static void invokeMutationAddressInt(MethodHandle handle, MemorySegment out, MemorySegment value, int precision) {
		try {
			checkMutationStatus((int) handle.invokeExact(out, value, precision));
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
			throw new IllegalStateException("Native BigDeci mutation was rejected with status " + status);
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

	static BigDeci adoptOwnedResult(MemorySegment result) {
		if (result == null || result.equals(MemorySegment.NULL)) {
			throw new OutOfMemoryError("Native BigDeci allocation returned null");
		}
		return new BigDeci(result, false);
	}

	long nativeId() {
		return invokeLong(BIGDECIMAL_ID_HANDLE, nativePtr());
	}

	long nativeVersion() {
		return invokeLong(BIGDECIMAL_VERSION_HANDLE, nativePtr());
	}

	boolean isClosed() {
		return nativeState.closed.get();
	}

	boolean isPermanent() {
		return permanent;
	}

	void ensureMutable() {
		nativePtr();
		if (permanent) {
			throw new UnsupportedOperationException("BigDeci constants are shared and cannot be mutated");
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
				invokeVoidAddress(BIGDECIMAL_FREE_HANDLE, nativePtr);
			}
		}
	}
}
