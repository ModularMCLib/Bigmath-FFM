package com.modularmc.bigmath;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class JdkComparisonBenchmark {

	private static final String DECIMAL_LEFT = "1234567890.12345678901234567890";
	private static final String DECIMAL_RIGHT = "9876543210.98765432109876543210";

	@State(Scope.Thread)
	public static class SmallIntState {
		BigInt nativeLeft;
		BigInt nativeRight;
		BigInteger jdkLeft;
		BigInteger jdkRight;

		@Setup(Level.Trial)
		public void setup() {
			nativeLeft = BigInt.fromLong(123456789L);
			nativeRight = BigInt.fromLong(987654321L);
			jdkLeft = BigInteger.valueOf(123456789L);
			jdkRight = BigInteger.valueOf(987654321L);
		}

		@TearDown(Level.Trial)
		public void tearDown() {
			nativeLeft.close();
			nativeRight.close();
		}
	}

	@State(Scope.Thread)
	public static class Int128State {
		Int128 nativeLeft;
		Int128 nativeRight;
		long primitiveLeft;
		long primitiveRight;
		Long boxedLeft;
		Long boxedRight;

		@Setup(Level.Trial)
		public void setup() {
			primitiveLeft = 123_456_789L;
			primitiveRight = 987_654_321L;
			boxedLeft = primitiveLeft;
			boxedRight = primitiveRight;
			nativeLeft = Int128.fromLong(primitiveLeft);
			nativeRight = Int128.fromLong(primitiveRight);
		}

		@TearDown(Level.Trial)
		public void tearDown() {
			nativeLeft.close();
			nativeRight.close();
		}
	}

	@State(Scope.Thread)
	public static class LargeIntState {
		@Param({"128", "512", "2048"})
		public int digits;

		BigInt nativeLeft;
		BigInt nativeRight;
		BigInteger jdkLeft;
		BigInteger jdkRight;

		@Setup(Level.Trial)
		public void setup() {
			String left = repeatDigits("1234567890", digits);
			String right = repeatDigits("9876543210", digits);
			nativeLeft = BigInt.fromString(left, 10);
			nativeRight = BigInt.fromString(right, 10);
			jdkLeft = new BigInteger(left);
			jdkRight = new BigInteger(right);
		}

		@TearDown(Level.Trial)
		public void tearDown() {
			nativeLeft.close();
			nativeRight.close();
		}
	}

	@State(Scope.Thread)
	public static class DecimalState {
		@Param({"64", "256", "1024"})
		public int precision;

		BigDeci nativeLeft;
		BigDeci nativeRight;
		BigDecimal jdkLeft;
		BigDecimal jdkRight;
		MathContext mathContext;

		@Setup(Level.Trial)
		public void setup() {
			nativeLeft = BigDeci.fromString(DECIMAL_LEFT, precision);
			nativeRight = BigDeci.fromString(DECIMAL_RIGHT, precision);
			jdkLeft = new BigDecimal(DECIMAL_LEFT);
			jdkRight = new BigDecimal(DECIMAL_RIGHT);
			mathContext = new MathContext(bitsToDecimalDigits(precision), RoundingMode.HALF_EVEN);
		}

		@TearDown(Level.Trial)
		public void tearDown() {
			nativeLeft.close();
			nativeRight.close();
		}
	}

	@Benchmark
	public void nativeBigIntAddSmall(SmallIntState state, Blackhole blackhole) {
		try (BigInt result = state.nativeLeft.add(state.nativeRight)) {
			blackhole.consume(result);
		}
	}

	@Benchmark
	public void jdkBigIntegerAddSmall(SmallIntState state, Blackhole blackhole) {
		blackhole.consume(state.jdkLeft.add(state.jdkRight));
	}

	@Benchmark
	public void nativeInt128FromLong(Int128State state, Blackhole blackhole) {
		try (Int128 result = Int128.fromLong(state.primitiveLeft)) {
			blackhole.consume(result);
		}
	}

	@Benchmark
	public void boxedLongFromLong(Int128State state, Blackhole blackhole) {
		blackhole.consume(Long.valueOf(state.primitiveLeft));
	}

	@Benchmark
	public void nativeInt128Add(Int128State state, Blackhole blackhole) {
		try (Int128 result = state.nativeLeft.add(state.nativeRight)) {
			blackhole.consume(result);
		}
	}

	@Benchmark
	public void primitiveLongAdd(Int128State state, Blackhole blackhole) {
		blackhole.consume(state.primitiveLeft + state.primitiveRight);
	}

	@Benchmark
	public void boxedLongAdd(Int128State state, Blackhole blackhole) {
		blackhole.consume(state.boxedLeft + state.boxedRight);
	}

	@Benchmark
	public void nativeInt128Subtract(Int128State state, Blackhole blackhole) {
		try (Int128 result = state.nativeRight.subtract(state.nativeLeft)) {
			blackhole.consume(result);
		}
	}

	@Benchmark
	public void primitiveLongSubtract(Int128State state, Blackhole blackhole) {
		blackhole.consume(state.primitiveRight - state.primitiveLeft);
	}

	@Benchmark
	public void nativeInt128Multiply(Int128State state, Blackhole blackhole) {
		try (Int128 result = state.nativeLeft.multiply(state.nativeRight)) {
			blackhole.consume(result);
		}
	}

	@Benchmark
	public void primitiveLongMultiply(Int128State state, Blackhole blackhole) {
		blackhole.consume(state.primitiveLeft * state.primitiveRight);
	}

	@Benchmark
	public void nativeInt128Divide(Int128State state, Blackhole blackhole) {
		try (Int128 result = state.nativeRight.divide(state.nativeLeft)) {
			blackhole.consume(result);
		}
	}

	@Benchmark
	public void primitiveLongDivide(Int128State state, Blackhole blackhole) {
		blackhole.consume(state.primitiveRight / state.primitiveLeft);
	}

	@Benchmark
	public void nativeInt128Mod(Int128State state, Blackhole blackhole) {
		try (Int128 result = state.nativeRight.mod(state.nativeLeft)) {
			blackhole.consume(result);
		}
	}

	@Benchmark
	public void primitiveLongMod(Int128State state, Blackhole blackhole) {
		blackhole.consume(state.primitiveRight % state.primitiveLeft);
	}

	@Benchmark
	public String nativeInt128ToString(Int128State state) {
		return state.nativeLeft.toString();
	}

	@Benchmark
	public String primitiveLongToString(Int128State state) {
		return Long.toString(state.primitiveLeft);
	}

	@Benchmark
	public String boxedLongToString(Int128State state) {
		return state.boxedLeft.toString();
	}

	@Benchmark
	public String nativeInt128Format(Int128State state) {
		return state.nativeRight.toFormattedString();
	}

	@Benchmark
	public String boxedLongFormat(Int128State state) {
		return formatGroupedDecimal(state.boxedRight.toString());
	}

	@Benchmark
	public void nativeBigIntMultiplyLarge(LargeIntState state, Blackhole blackhole) {
		try (BigInt result = state.nativeLeft.multiply(state.nativeRight)) {
			blackhole.consume(result);
		}
	}

	@Benchmark
	public void jdkBigIntegerMultiplyLarge(LargeIntState state, Blackhole blackhole) {
		blackhole.consume(state.jdkLeft.multiply(state.jdkRight));
	}

	@Benchmark
	public String nativeBigIntToStringLarge(LargeIntState state) {
		return state.nativeLeft.toString();
	}

	@Benchmark
	public String jdkBigIntegerToStringLarge(LargeIntState state) {
		return state.jdkLeft.toString();
	}

	@Benchmark
	public String nativeBigIntFormatLarge(LargeIntState state) {
		return state.nativeLeft.toFormattedString();
	}

	@Benchmark
	public String jdkBigIntegerFormatLarge(LargeIntState state) {
		return formatGroupedDecimal(state.jdkLeft.toString());
	}

	@Benchmark
	public void nativeBigDeciAdd(DecimalState state, Blackhole blackhole) {
		try (BigDeci result = state.nativeLeft.add(state.nativeRight)) {
			blackhole.consume(result);
		}
	}

	@Benchmark
	public void jdkBigDecimalAdd(DecimalState state, Blackhole blackhole) {
		blackhole.consume(state.jdkLeft.add(state.jdkRight, state.mathContext));
	}

	@Benchmark
	public void nativeBigDeciDivide(DecimalState state, Blackhole blackhole) {
		try (BigDeci result = state.nativeLeft.divide(state.nativeRight)) {
			blackhole.consume(result);
		}
	}

	@Benchmark
	public void jdkBigDecimalDivide(DecimalState state, Blackhole blackhole) {
		blackhole.consume(state.jdkLeft.divide(state.jdkRight, state.mathContext));
	}

	@Benchmark
	public void nativeBigDeciSqrt(DecimalState state, Blackhole blackhole) {
		try (BigDeci result = state.nativeLeft.sqrt()) {
			blackhole.consume(result);
		}
	}

	@Benchmark
	public void jdkBigDecimalSqrt(DecimalState state, Blackhole blackhole) {
		blackhole.consume(state.jdkLeft.sqrt(state.mathContext));
	}

	@Benchmark
	public String nativeBigDeciToString(DecimalState state) {
		return state.nativeLeft.toString();
	}

	@Benchmark
	public String jdkBigDecimalToString(DecimalState state) {
		return state.jdkLeft.round(state.mathContext).toString();
	}

	private static String repeatDigits(String pattern, int digits) {
		StringBuilder sb = new StringBuilder(digits);
		while (sb.length() < digits) {
			sb.append(pattern);
		}
		sb.setLength(digits);
		return sb.toString();
	}

	private static int bitsToDecimalDigits(int bits) {
		return (int) Math.ceil(bits * Math.log10(2));
	}

	private static String formatGroupedDecimal(String value) {
		int signOffset = value.startsWith("-") ? 1 : 0;
		StringBuilder sb = new StringBuilder(value.length() + value.length() / 3);
		for (int i = 0; i < value.length(); i++) {
			if (i > signOffset && (value.length() - i) % 3 == 0) {
				sb.append(',');
			}
			sb.append(value.charAt(i));
		}
		return sb.toString();
	}
}
