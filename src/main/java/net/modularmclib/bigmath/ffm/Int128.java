package net.modularmclib.bigmath.ffm;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

import static net.modularmclib.bigmath.ffm.BigmathFFM.invoke;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class Int128 implements AutoCloseable {

	private static final long STRUCT_SIZE = 16L;

	private final MemorySegment nativePtr;
	private final Arena arena;

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

	public long lo() {
		return nativePtr.get(ValueLayout.JAVA_LONG, 0);
	}

	public long hi() {
		return nativePtr.get(ValueLayout.JAVA_LONG, 8);
	}

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

	public int compareTo(Int128 other) {
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"int128_cmp",
				FunctionDescriptors.INT128_CMP
		);
		return (int) invoke(handle, nativePtr, other.nativePtr);
	}

	public int signum() {
		MethodHandle handle = BigmathFFM.getInstance().downcall(
				"int128_sign",
				FunctionDescriptors.INT128_SIGN
		);
		return (int) invoke(handle, nativePtr);
	}

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

	public String toFormattedString() {
		return toFormattedString(3, ",");
	}

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

	@Override
	public String toString() {
		return toString(10);
	}

	@Override
	public void close() {
		arena.close();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof Int128 other)) return false;
		return lo() == other.lo() && hi() == other.hi();
	}

	@Override
	public int hashCode() {
		return Long.hashCode(lo()) ^ Long.hashCode(hi());
	}
}
