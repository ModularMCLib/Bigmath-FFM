package net.modularmclib.bigmath.ffm;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.math.BigInteger;

import static net.modularmclib.bigmath.ffm.BigmathFFM.invoke;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class BigInt implements AutoCloseable {

	private final MemorySegment nativePtr;
	private final Arena arena;

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

	public static BigInt fromBigInteger(BigInteger val) {
		return fromString(val.toString(), 10);
	}

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

	public int compareTo(BigInt other) {
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigint_cmp",
				FunctionDescriptors.BIGINT_CMP
		);
		return (int) invoke(handle, nativePtr, other.nativePtr);
	}

	public int signum() {
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigint_sign",
				FunctionDescriptors.BIGINT_SIGN
		);
		return (int) invoke(handle, nativePtr);
	}

	public boolean isProbablyPrime(int certainty) {
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigint_is_probably_prime",
				FunctionDescriptors.BIGINT_IS_PROBABLY_PRIME
		);
		int result = (int) invoke(handle, nativePtr, certainty);
		return result != 0;
	}

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

	public BigInteger toBigInteger() {
		return new BigInteger(toString(10));
	}

	public String toFormattedString() {
		return toFormattedString(3, ",");
	}

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

	@Override
	public String toString() {
		return toString(10);
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
}
