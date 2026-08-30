package com.modularmc.bigmath.formatting;

import com.modularmc.bigmath.BigmathFFM;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;

/** FFM boundary for the Native number renderer. */
public final class NativeNumberRenderer {

	private static final MemoryLayout RESULT_LAYOUT = MemoryLayout.structLayout(
			ValueLayout.ADDRESS.withName("data"),
			ValueLayout.JAVA_LONG.withName("size")
	);
	private static final long DATA_OFFSET = RESULT_LAYOUT.byteOffset(
			MemoryLayout.PathElement.groupElement("data")
	);
	private static final long SIZE_OFFSET = RESULT_LAYOUT.byteOffset(
			MemoryLayout.PathElement.groupElement("size")
	);
	private static final MethodHandle FORMAT_BIGINT = downcall(
			"bigmath_format_bigint",
			FunctionDescriptor.of(
				ValueLayout.JAVA_INT,
				ValueLayout.ADDRESS,
				ValueLayout.JAVA_INT,
				ValueLayout.ADDRESS,
				ValueLayout.ADDRESS
			)
	);
	private static final MethodHandle FORMAT_BIGDECI = downcall(
			"bigmath_format_bigdeci",
			FunctionDescriptor.of(
				ValueLayout.JAVA_INT,
				ValueLayout.ADDRESS,
				ValueLayout.JAVA_INT,
				ValueLayout.ADDRESS,
				ValueLayout.ADDRESS
			)
	);
	private static final MethodHandle FORMAT_INT128 = downcall(
			"bigmath_format_int128",
			FunctionDescriptor.of(
				ValueLayout.JAVA_INT,
				ValueLayout.ADDRESS,
				ValueLayout.JAVA_INT,
				ValueLayout.JAVA_LONG,
				ValueLayout.JAVA_LONG,
				ValueLayout.ADDRESS
			)
	);
	private static final MethodHandle FORMAT_I64 = downcall(
			"bigmath_format_i64",
			FunctionDescriptor.of(
				ValueLayout.JAVA_INT,
				ValueLayout.ADDRESS,
				ValueLayout.JAVA_INT,
				ValueLayout.JAVA_LONG,
				ValueLayout.ADDRESS
			)
	);
	private static final MethodHandle FORMAT_F64 = downcall(
			"bigmath_format_f64",
			FunctionDescriptor.of(
				ValueLayout.JAVA_INT,
				ValueLayout.ADDRESS,
				ValueLayout.JAVA_INT,
				ValueLayout.JAVA_DOUBLE,
				ValueLayout.ADDRESS
			)
	);
	private static final MethodHandle FORMAT_DECIMAL = downcall(
			"bigmath_format_decimal",
			FunctionDescriptor.of(
				ValueLayout.JAVA_INT,
				ValueLayout.ADDRESS,
				ValueLayout.JAVA_INT,
				ValueLayout.ADDRESS,
				ValueLayout.JAVA_LONG,
				ValueLayout.JAVA_LONG,
				ValueLayout.ADDRESS
			)
	);
	private static final MethodHandle FREE_RESULT = downcall(
			"bigmath_format_free",
			FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
	);

	private NativeNumberRenderer() {
	}

	public static String formatBigInt(FormatDescriptor descriptor, MemorySegment handle) {
		return invoke(descriptor, result -> (int) FORMAT_BIGINT.invokeExact(
				descriptor.segment(), descriptor.size(), handle, result
		));
	}

	public static String formatBigDeci(FormatDescriptor descriptor, MemorySegment handle) {
		return invoke(descriptor, result -> (int) FORMAT_BIGDECI.invokeExact(
				descriptor.segment(), descriptor.size(), handle, result
		));
	}

	public static String formatInt128(FormatDescriptor descriptor, long lo, long hi) {
		return invoke(descriptor, result -> (int) FORMAT_INT128.invokeExact(
				descriptor.segment(), descriptor.size(), lo, hi, result
		));
	}

	public static String formatLong(FormatDescriptor descriptor, long value) {
		return invoke(descriptor, result -> (int) FORMAT_I64.invokeExact(
				descriptor.segment(), descriptor.size(), value, result
		));
	}

	public static String formatDouble(FormatDescriptor descriptor, double value) {
		return invoke(descriptor, result -> (int) FORMAT_F64.invokeExact(
				descriptor.segment(), descriptor.size(), value, result
		));
	}

	public static String formatDecimal(FormatDescriptor descriptor, byte[] unscaled, long scale) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment bytes = arena.allocateFrom(ValueLayout.JAVA_BYTE, unscaled);
			return invoke(descriptor, result -> (int) FORMAT_DECIMAL.invokeExact(
					descriptor.segment(),
					descriptor.size(),
					bytes,
					(long) unscaled.length,
					scale,
					result
			));
		}
	}

	private static String invoke(FormatDescriptor descriptor, NativeCall call) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment result = arena.allocate(RESULT_LAYOUT);
			result.fill((byte) 0);
			int status;
			try {
				status = call.invoke(result);
			} catch (RuntimeException | Error exception) {
				throw exception;
			} catch (Throwable throwable) {
				throw new IllegalStateException("Native formatting call failed", throwable);
			}
			MemorySegment data = result.get(ValueLayout.ADDRESS, DATA_OFFSET);
			long size = result.get(ValueLayout.JAVA_LONG, SIZE_OFFSET);
			if (status != 0) {
				free(data);
				throw statusException(status);
			}
			if (data.equals(MemorySegment.NULL) || size < 0 || size > Integer.MAX_VALUE) {
				free(data);
				throw new IllegalStateException("Native formatter returned an invalid result buffer");
			}
			try {
				byte[] utf8 = data.reinterpret(size).toArray(ValueLayout.JAVA_BYTE);
				return new String(utf8, StandardCharsets.UTF_8);
			} finally {
				free(data);
			}
		}
	}

	private static RuntimeException statusException(int status) {
		return switch (status) {
			case 1 -> new IllegalArgumentException("Invalid number-format descriptor");
			case 2 -> new IllegalArgumentException("Invalid value for number formatting");
			case 3 -> new IllegalStateException("Requested Native number backend is unavailable");
			case 4 -> new ArithmeticException("Rounding was required with RoundingMode.UNNECESSARY");
			case 5 -> throw new OutOfMemoryError("Native number formatter ran out of memory");
			case 6 -> new IllegalArgumentException("Formatted result is too large");
			default -> new IllegalStateException("Native number formatter failed with status " + status);
		};
	}

	private static void free(MemorySegment data) {
		if (data == null || data.equals(MemorySegment.NULL)) {
			return;
		}
		try {
			FREE_RESULT.invokeExact(data);
		} catch (RuntimeException | Error exception) {
			throw exception;
		} catch (Throwable throwable) {
			throw new IllegalStateException("Failed to free Native formatting result", throwable);
		}
	}

	private static MethodHandle downcall(String name, FunctionDescriptor descriptor) {
		return BigmathFFM.getInstance().downcall(name, descriptor);
	}

	@FunctionalInterface
	private interface NativeCall {
		int invoke(MemorySegment result) throws Throwable;
	}
}
