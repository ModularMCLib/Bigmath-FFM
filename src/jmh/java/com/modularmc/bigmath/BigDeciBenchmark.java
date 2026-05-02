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
public class BigDeciBenchmark {

	@State(Scope.Thread)
	public static class PrecisionState {
		@Param({"64", "256", "1024"})
		public int precision;

		BigDeci left;
		BigDeci right;

		@Setup(Level.Trial)
		public void setup() {
			left = BigDeci.fromString("1234567890.12345678901234567890", precision);
			right = BigDeci.fromString("9876543210.98765432109876543210", precision);
		}

		@TearDown(Level.Trial)
		public void tearDown() {
			left.close();
			right.close();
		}
	}

	@Benchmark
	public void add(PrecisionState state, Blackhole blackhole) {
		try (BigDeci result = state.left.add(state.right)) {
			blackhole.consume(result);
		}
	}

	@Benchmark
	public void divide(PrecisionState state, Blackhole blackhole) {
		try (BigDeci result = state.left.divide(state.right)) {
			blackhole.consume(result);
		}
	}

	@Benchmark
	public void sqrt(PrecisionState state, Blackhole blackhole) {
		try (BigDeci result = state.left.sqrt()) {
			blackhole.consume(result);
		}
	}

	@Benchmark
	public String toString(PrecisionState state) {
		return state.left.toString();
	}
}
