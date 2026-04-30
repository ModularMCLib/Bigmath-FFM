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
		return new BigInt(ptr.get(ValueLayout.ADDRESS, 0), arena);
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
		return new BigInt(ptr.get(ValueLayout.ADDRESS, 0), arena);
	}

	public BigInt add(BigInt other) {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigint_add",
				FunctionDescriptors.BIGINT_BINARY
		);
		invoke(handle, result, nativePtr, other.nativePtr);
		return new BigInt(result.get(ValueLayout.ADDRESS, 0), arena);
	}

	public BigInt subtract(BigInt other) {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigint_sub",
				FunctionDescriptors.BIGINT_BINARY
		);
		invoke(handle, result, nativePtr, other.nativePtr);
		return new BigInt(result.get(ValueLayout.ADDRESS, 0), arena);
	}

	public BigInt multiply(BigInt other) {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigint_mul",
				FunctionDescriptors.BIGINT_BINARY
		);
		invoke(handle, result, nativePtr, other.nativePtr);
		return new BigInt(result.get(ValueLayout.ADDRESS, 0), arena);
	}

	public BigInt divide(BigInt other) {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigint_div",
				FunctionDescriptors.BIGINT_BINARY
		);
		invoke(handle, result, nativePtr, other.nativePtr);
		return new BigInt(result.get(ValueLayout.ADDRESS, 0), arena);
	}

	public BigInt mod(BigInt other) {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigint_mod",
				FunctionDescriptors.BIGINT_BINARY
		);
		invoke(handle, result, nativePtr, other.nativePtr);
		return new BigInt(result.get(ValueLayout.ADDRESS, 0), arena);
	}

	public BigInt pow(long exp) {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigint_pow",
				FunctionDescriptors.BIGINT_POW
		);
		invoke(handle, result, nativePtr, exp);
		return new BigInt(result.get(ValueLayout.ADDRESS, 0), arena);
	}

	public BigInt negate() {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigint_neg",
				FunctionDescriptors.BIGINT_UNARY
		);
		invoke(handle, result, nativePtr);
		return new BigInt(result.get(ValueLayout.ADDRESS, 0), arena);
	}

	public BigInt abs() {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigint_abs",
				FunctionDescriptors.BIGINT_UNARY
		);
		invoke(handle, result, nativePtr);
		return new BigInt(result.get(ValueLayout.ADDRESS, 0), arena);
	}

	public int compareTo(BigInt other) {
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigint_cmp",
				FunctionDescriptors.BIGINT_CMP
		);
		return (int) invoke(handle, nativePtr, other.nativePtr);
	}

	public String toString(int radix) {
		try (Arena tmp = Arena.ofConfined()) {
			MethodHandle handle = BigmathFFM.getInstance().downcall(
					"bigint_to_string",
					FunctionDescriptors.BIGINT_TO_STRING
			);
			MemorySegment result = (MemorySegment) invoke(handle, nativePtr, radix);
			return result.getString(0);
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
			String str = result.getString(0);
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
