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

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 8, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class BigDeciBenchmark {
	private static final BigNumberFormat GROUPED_FORMAT = BigNumberFormat.ofPattern("#,##0.################");
	private static final int MULTIPLY_PAIRS = 32;

	@State(Scope.Thread)
	public static class PrecisionState {
		@Param({"64", "256", "1024"})
		public int precision;

		BigDeci left;
		BigDeci right;
		BigDeci result;

		@Setup(Level.Trial)
		public void setup() {
			left = BigDeci.fromString("1234567890.12345678901234567890", precision);
			right = BigDeci.fromString("9876543210.98765432109876543210", precision);
			result = BigDeci.fromDouble(0.0, precision);
		}

		@TearDown(Level.Trial)
		public void tearDown() {
			left.close();
			right.close();
			result.close();
		}
	}

	@State(Scope.Thread)
	public static class PowState {
		@Param({"64", "1024"})
		public int precision;

		@Param({"2", "128"})
		public long exponent;

		BigDeci base;
		BigDeci decimalExponent;

		@Setup(Level.Trial)
		public void setup() {
			base = BigDeci.fromString("1.000123456789", precision);
			decimalExponent = BigDeci.fromDouble((double) exponent, precision);
		}

		@TearDown(Level.Trial)
		public void tearDown() {
			base.close();
			decimalExponent.close();
		}
	}

	@State(Scope.Thread)
	public static class BigIntState {
		@Param({"64", "1024"})
		public int precision;

		@Param({"128", "2048"})
		public int digits;

		BigInt value;

		@Setup(Level.Trial)
		public void setup() {
			value = BigInt.fromString(repeatDigits("1234567890", digits), 10);
		}

		@TearDown(Level.Trial)
		public void tearDown() {
			value.close();
		}
	}

	@State(Scope.Thread)
	public static class HighPrecisionMultiplyState {
		@Param({"131072", "196608", "262144", "393216", "524288", "1048576"})
		public int precision;

		BigDeci[] left;
		BigDeci[] alternateLeft;
		BigDeci[] right;
		BigDeci result;
		int pair;
		int selectedPair;

		@Setup(Level.Trial)
		public void setup() {
			int digits = (int) (precision * 0.30103) - 8;
			left = new BigDeci[MULTIPLY_PAIRS];
			alternateLeft = new BigDeci[MULTIPLY_PAIRS];
			right = new BigDeci[MULTIPLY_PAIRS];
			for (int index = 0; index < MULTIPLY_PAIRS; index++) {
				String suffix = Integer.toString(10 + index);
				String leftDigits = repeatDigits("1234567890", digits - suffix.length()) + suffix;
				String alternateDigits = repeatDigits("2234567890", digits - suffix.length()) + suffix;
				String rightDigits = repeatDigits("9876543210", digits - suffix.length()) + suffix;
				try (BigInt leftInteger = BigInt.fromString(leftDigits, 10);
					BigInt alternateInteger = BigInt.fromString(alternateDigits, 10);
					BigInt rightInteger = BigInt.fromString(rightDigits, 10)) {
					left[index] = BigDeci.fromBigInt(leftInteger, precision);
					alternateLeft[index] = BigDeci.fromBigInt(alternateInteger, precision);
					right[index] = BigDeci.fromBigInt(rightInteger, precision);
				}
			}
			result = BigDeci.fromDouble(0.0, precision);
		}

		@Setup(Level.Invocation)
		public void prepareInvocation() {
			selectedPair = pair;
			pair = (pair + 1) % MULTIPLY_PAIRS;
			left[selectedPair].set(alternateLeft[selectedPair]);
		}

		@TearDown(Level.Trial)
		public void tearDown() {
			for (int index = 0; index < MULTIPLY_PAIRS; index++) {
				left[index].close();
				alternateLeft[index].close();
				right[index].close();
			}
			result.close();
		}
	}

	@Benchmark
	public void add(PrecisionState state, Blackhole blackhole) {
		try (BigDeci result = state.left.add(state.right)) {
			blackhole.consume(result);
		}
	}

	@Benchmark
	public void addInto(PrecisionState state, Blackhole blackhole) {
		blackhole.consume(state.result.addInto(state.left, state.right));
	}

	@Benchmark
	public void setValue(PrecisionState state, Blackhole blackhole) {
		blackhole.consume(state.result.set(state.left));
	}

	@Benchmark
	public void divide(PrecisionState state, Blackhole blackhole) {
		try (BigDeci result = state.left.divide(state.right)) {
			blackhole.consume(result);
		}
	}

	@Benchmark
	public void divideInto(PrecisionState state, Blackhole blackhole) {
		blackhole.consume(state.result.divideInto(state.left, state.right));
	}

	@Benchmark
	public void highPrecisionMultiply(HighPrecisionMultiplyState state, Blackhole blackhole) {
		int pair = state.selectedPair;
		try (BigDeci result = state.left[pair].multiply(state.right[pair])) {
			blackhole.consume(result);
		}
	}

	@Benchmark
	public void highPrecisionMultiplyInto(HighPrecisionMultiplyState state, Blackhole blackhole) {
		int pair = state.selectedPair;
		blackhole.consume(state.result.multiplyInto(state.left[pair], state.right[pair]));
	}

	@Benchmark
	public void sqrt(PrecisionState state, Blackhole blackhole) {
		try (BigDeci result = state.left.sqrt()) {
			blackhole.consume(result);
		}
	}

	@Benchmark
	public void sqrtInto(PrecisionState state, Blackhole blackhole) {
		blackhole.consume(state.result.sqrtInto(state.left));
	}

	@Benchmark
	public void pow(PowState state, Blackhole blackhole) {
		try (BigDeci result = state.base.pow(state.decimalExponent)) {
			blackhole.consume(result);
		}
	}

	@Benchmark
	public void powLong(PowState state, Blackhole blackhole) {
		try (BigDeci result = state.base.pow(state.exponent)) {
			blackhole.consume(result);
		}
	}

	@Benchmark
	public void fromBigInt(BigIntState state, Blackhole blackhole) {
		try (BigDeci result = BigDeci.fromBigInt(state.value, state.precision)) {
			blackhole.consume(result);
		}
	}

	@Benchmark
	public String toString(PrecisionState state) {
		return state.left.toString();
	}

	@Benchmark
	public String format(PrecisionState state) {
		return GROUPED_FORMAT.format(state.left);
	}

	private static String repeatDigits(String pattern, int digits) {
		StringBuilder sb = new StringBuilder(digits);
		while (sb.length() < digits) {
			sb.append(pattern);
		}
		sb.setLength(digits);
		return sb.toString();
	}
}
