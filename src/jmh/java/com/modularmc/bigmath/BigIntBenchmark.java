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
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class BigIntBenchmark {

	@State(Scope.Thread)
	public static class SmallState {
		BigInt left;
		BigInt right;
		BigInt result;

		@Setup(Level.Trial)
		public void setup() {
			left = BigInt.fromLong(123456789L);
			right = BigInt.fromLong(987654321L);
			result = BigInt.fromLong(0);
		}

		@TearDown(Level.Trial)
		public void tearDown() {
			left.close();
			right.close();
			result.close();
		}
	}

	@State(Scope.Thread)
	public static class LargeState {
		@Param({"128", "512", "2048"})
		public int digits;

		BigInt left;
		BigInt right;
		BigInt result;

		@Setup(Level.Trial)
		public void setup() {
			left = BigInt.fromString(repeatDigits("1234567890", digits), 10);
			right = BigInt.fromString(repeatDigits("9876543210", digits), 10);
			result = BigInt.fromLong(0);
		}

		@TearDown(Level.Trial)
		public void tearDown() {
			left.close();
			right.close();
			result.close();
		}
	}

	@State(Scope.Thread)
	public static class VeryLargeState {
		@Param({"4096", "8192"})
		public int digits;

		BigInt left;
		BigInt right;
		BigInt result;

		@Setup(Level.Trial)
		public void setup() {
			left = BigInt.fromString(repeatDigits("1234567890", digits), 10);
			right = BigInt.fromString(repeatDigits("9876543210", digits), 10);
			result = BigInt.fromLong(0);
		}

		@TearDown(Level.Trial)
		public void tearDown() {
			left.close();
			right.close();
			result.close();
		}
	}

	@State(Scope.Thread)
	public static class FactorialState {
		@Param({"10", "128", "512", "5000"})
		public long n;
	}

	@State(Scope.Thread)
	public static class PowState {
		@Param({"2", "32", "512"})
		public long exponent;

		BigInt base;

		@Setup(Level.Trial)
		public void setup() {
			base = BigInt.fromString("12345678901234567890", 10);
		}

		@TearDown(Level.Trial)
		public void tearDown() {
			base.close();
		}
	}

	@State(Scope.Thread)
	public static class LongState {
		@Param({"42", "-9223372036854775808"})
		public long value;

		BigInt bigint;
		BigInt result;

		@Setup(Level.Trial)
		public void setup() {
			bigint = BigInt.fromLong(value);
			result = BigInt.fromLong(0);
		}

		@TearDown(Level.Trial)
		public void tearDown() {
			bigint.close();
			result.close();
		}
	}

	@State(Scope.Thread)
	public static class StringState {
		@Param({"128", "2048"})
		public int digits;

		String value;
		BigInt result;

		@Setup(Level.Trial)
		public void setup() {
			value = repeatDigits("1234567890", digits);
			result = BigInt.fromLong(0);
		}

		@TearDown(Level.Trial)
		public void tearDown() {
			result.close();
		}
	}

	@Benchmark
	public void addSmall(SmallState state, Blackhole blackhole) {
		try (BigInt result = state.left.add(state.right)) {
			blackhole.consume(result);
		}
	}

	@Benchmark
	public void addSmallInto(SmallState state, Blackhole blackhole) {
		blackhole.consume(state.result.addInto(state.left, state.right));
	}

	@Benchmark
	public void multiplyLarge(LargeState state, Blackhole blackhole) {
		try (BigInt result = state.left.multiply(state.right)) {
			blackhole.consume(result);
		}
	}

	@Benchmark
	public void multiplyLargeInto(LargeState state, Blackhole blackhole) {
		blackhole.consume(state.result.multiplyInto(state.left, state.right));
	}

	@Benchmark
	public void gcdLarge(LargeState state, Blackhole blackhole) {
		try (BigInt result = state.left.gcd(state.right)) {
			blackhole.consume(result);
		}
	}

	@Benchmark
	public void factorial(FactorialState state, Blackhole blackhole) {
		try (BigInt result = BigInt.factorial(state.n)) {
			blackhole.consume(result);
		}
	}

	@Benchmark
	public void pow(PowState state, Blackhole blackhole) {
		try (BigInt result = state.base.pow(state.exponent)) {
			blackhole.consume(result);
		}
	}

	@Benchmark
	public void fromLong(LongState state, Blackhole blackhole) {
		try (BigInt result = BigInt.fromLong(state.value)) {
			blackhole.consume(result);
		}
	}

	@Benchmark
	public void fromString(StringState state, Blackhole blackhole) {
		try (BigInt result = BigInt.fromString(state.value, 10)) {
			blackhole.consume(result);
		}
	}

	@Benchmark
	public void setLong(LongState state, Blackhole blackhole) {
		blackhole.consume(state.result.set(state.value));
	}

	@Benchmark
	public void setString(StringState state, Blackhole blackhole) {
		blackhole.consume(state.result.set(state.value, 10));
	}

	@Benchmark
	public void longValue(LongState state, Blackhole blackhole) {
		blackhole.consume(state.bigint.longValue());
	}

	@Benchmark
	public void multiplyVeryLarge(VeryLargeState state, Blackhole blackhole) {
		try (BigInt result = state.left.multiply(state.right)) {
			blackhole.consume(result);
		}
	}

	@Benchmark
	public void multiplyVeryLargeInto(VeryLargeState state, Blackhole blackhole) {
		blackhole.consume(state.result.multiplyInto(state.left, state.right));
	}

	@Benchmark
	public String toStringLarge(LargeState state) {
		return state.left.toString();
	}

	@Benchmark
	public String formatLarge(LargeState state) {
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
