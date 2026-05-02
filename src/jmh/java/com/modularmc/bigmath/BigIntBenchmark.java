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

		@Setup(Level.Trial)
		public void setup() {
			left = BigInt.fromLong(123456789L);
			right = BigInt.fromLong(987654321L);
		}

		@TearDown(Level.Trial)
		public void tearDown() {
			left.close();
			right.close();
		}
	}

	@State(Scope.Thread)
	public static class LargeState {
		@Param({"128", "512", "2048"})
		public int digits;

		BigInt left;
		BigInt right;

		@Setup(Level.Trial)
		public void setup() {
			left = BigInt.fromString(repeatDigits("1234567890", digits), 10);
			right = BigInt.fromString(repeatDigits("9876543210", digits), 10);
		}

		@TearDown(Level.Trial)
		public void tearDown() {
			left.close();
			right.close();
		}
	}

	@Benchmark
	public void addSmall(SmallState state, Blackhole blackhole) {
		try (BigInt result = state.left.add(state.right)) {
			blackhole.consume(result);
		}
	}

	@Benchmark
	public void multiplyLarge(LargeState state, Blackhole blackhole) {
		try (BigInt result = state.left.multiply(state.right)) {
			blackhole.consume(result);
		}
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
