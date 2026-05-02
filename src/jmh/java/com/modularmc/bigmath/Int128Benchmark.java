package com.modularmc.bigmath;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class Int128Benchmark {

	@State(Scope.Thread)
	public static class ArithmeticState {
		Int128 left;
		Int128 right;

		@Setup(Level.Trial)
		public void setup() {
			left = Int128.fromString("12345678901234567890", 10);
			right = Int128.fromString("987654321", 10);
		}

		@TearDown(Level.Trial)
		public void tearDown() {
			left.close();
			right.close();
		}
	}

	@Benchmark
	public void add(ArithmeticState state, Blackhole blackhole) {
		try (Int128 result = state.left.add(state.right)) {
			blackhole.consume(result);
		}
	}

	@Benchmark
	public void divide(ArithmeticState state, Blackhole blackhole) {
		try (Int128 result = state.left.divide(state.right)) {
			blackhole.consume(result);
		}
	}

	@Benchmark
	public String toStringBase10(ArithmeticState state) {
		return state.left.toString();
	}

	@Benchmark
	public String formatBase10(ArithmeticState state) {
		return state.left.toFormattedString();
	}
}
