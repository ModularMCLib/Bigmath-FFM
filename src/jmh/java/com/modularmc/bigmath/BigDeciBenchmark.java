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
@Warmup(iterations = 8, time = 2)
@Measurement(iterations = 8, time = 2)
@Fork(1)
public class BigDeciBenchmark {

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
		return state.left.toFormattedString();
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
