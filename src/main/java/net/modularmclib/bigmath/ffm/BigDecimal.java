package net.modularmclib.bigmath.ffm;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

import static net.modularmclib.bigmath.ffm.BigmathFFM.invoke;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class BigDecimal implements AutoCloseable {

	private final MemorySegment nativePtr;
	private final Arena arena;

	public static BigDecimal fromDouble(double value, int precision) {
		Arena arena = Arena.ofConfined();
		MemorySegment ptr = arena.allocate(ValueLayout.ADDRESS);
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigdecimal_from_double",
				FunctionDescriptors.BIGDECIMAL_FROM_DOUBLE
		);
		invoke(handle, ptr, value, precision);
		return new BigDecimal(ptr.get(ValueLayout.ADDRESS, 0), arena);
	}

	public static BigDecimal fromString(String value, int precision) {
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
		return new BigDecimal(ptr.get(ValueLayout.ADDRESS, 0), arena);
	}

	public static BigDecimal fromBigInt(BigInt value, int precision) {
		return fromString(value.toString(), precision);
	}

	public BigDecimal add(BigDecimal other) {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigdecimal_add",
				FunctionDescriptors.BIGDECIMAL_BINARY
		);
		invoke(handle, result, nativePtr, other.nativePtr);
		return new BigDecimal(result.get(ValueLayout.ADDRESS, 0), arena);
	}

	public BigDecimal subtract(BigDecimal other) {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigdecimal_sub",
				FunctionDescriptors.BIGDECIMAL_BINARY
		);
		invoke(handle, result, nativePtr, other.nativePtr);
		return new BigDecimal(result.get(ValueLayout.ADDRESS, 0), arena);
	}

	public BigDecimal multiply(BigDecimal other) {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigdecimal_mul",
				FunctionDescriptors.BIGDECIMAL_BINARY
		);
		invoke(handle, result, nativePtr, other.nativePtr);
		return new BigDecimal(result.get(ValueLayout.ADDRESS, 0), arena);
	}

	public BigDecimal divide(BigDecimal other) {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigdecimal_div",
				FunctionDescriptors.BIGDECIMAL_BINARY
		);
		invoke(handle, result, nativePtr, other.nativePtr);
		return new BigDecimal(result.get(ValueLayout.ADDRESS, 0), arena);
	}

	public BigDecimal negate() {
		Arena arena = Arena.ofConfined();
		MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigdecimal_neg",
				FunctionDescriptors.BIGDECIMAL_BINARY
		);
		invoke(handle, result, nativePtr);
		return new BigDecimal(result.get(ValueLayout.ADDRESS, 0), arena);
	}

	public int compareTo(BigDecimal other) {
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigdecimal_cmp",
				FunctionDescriptors.BIGDECIMAL_CMP
		);
		return (int) invoke(handle, nativePtr, other.nativePtr);
	}

	public double toDouble() {
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigdecimal_to_double",
				FunctionDescriptors.BIGDECIMAL_TO_DOUBLE
		);
		return (double) invoke(handle, nativePtr);
	}

	public String toString() {
		try (Arena tmp = Arena.ofConfined()) {
			MethodHandle handle = BigmathFFM.getInstance().downcall(
					"bigdecimal_to_string",
					FunctionDescriptors.BIGDECIMAL_TO_STRING
			);
			MemorySegment result = (MemorySegment) invoke(handle, nativePtr);
			return result.getString(0);
		}
	}

	public String toFormattedString() {
		return toFormattedString(-1, 3, ",");
	}

	public String toFormattedString(int scale) {
		return toFormattedString(scale, 3, ",");
	}

	public String toFormattedString(int scale, int groupSize, String groupSep) {
		try (Arena tmp = Arena.ofConfined()) {
			MemorySegment sep = tmp.allocateFrom(groupSep, java.nio.charset.StandardCharsets.UTF_8);
			MethodHandle handle = BigmathFFM.getInstance().downcall(
					"bigdecimal_format",
					FunctionDescriptors.BIGDECIMAL_FORMAT
			);
			MemorySegment result = (MemorySegment) invoke(handle, nativePtr, scale, groupSize, sep);
			String str = result.getString(0);
			MethodHandle freeHandle = BigmathFFM.getInstance().downcall(
					"bigdecimal_free_string",
					FunctionDescriptors.BIGDECIMAL_FREE_STRING
			);
			invoke(freeHandle, result);
			return str;
		}
	}

	@Override
	public void close() {
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"bigdecimal_free",
				FunctionDescriptors.BIGDECIMAL_FREE
		);
		invoke(handle, nativePtr);
		arena.close();
	}
}
