package com.modularmc.bigmath;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.math.BigInteger;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 8, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class LoopMultiplyBenchmark {

	static final int MULTIPLICATIONS_PER_INVOCATION = 256;
	private static final int OPERAND_COUNT = 8;

	@State(Scope.Thread)
	public static class MultiplyState {
		@Param({"128", "512", "2048"})
		public int digits;

		BigInt[] nativeLefts;
		BigInt[] nativeRights;
		BigInt nativeResult;
		BigInteger[] jdkLefts;
		BigInteger[] jdkRights;

		@Setup(Level.Trial)
		public void setup() {
			nativeLefts = new BigInt[OPERAND_COUNT];
			nativeRights = new BigInt[OPERAND_COUNT];
			jdkLefts = new BigInteger[OPERAND_COUNT];
			jdkRights = new BigInteger[OPERAND_COUNT];
			for (int i = 0; i < OPERAND_COUNT; i++) {
				String left = repeatDigits(Integer.toString(1234567890 + i), digits);
				String right = repeatDigits(Integer.toString(987654321 - i), digits);
				nativeLefts[i] = BigInt.fromString(left, 10);
				nativeRights[i] = BigInt.fromString(right, 10);
				jdkLefts[i] = new BigInteger(left);
				jdkRights[i] = new BigInteger(right);
			}
			nativeResult = BigInt.fromLong(0);
		}

		@TearDown(Level.Trial)
		public void tearDown() {
			closeAll(nativeLefts);
			closeAll(nativeRights);
			nativeResult.close();
		}
	}

	@Benchmark
	@OperationsPerInvocation(MULTIPLICATIONS_PER_INVOCATION)
	public void nativeBigIntMultiplyLoop(MultiplyState state, Blackhole blackhole) {
		BigInt result = null;
		try {
			for (int i = 0; i < MULTIPLICATIONS_PER_INVOCATION; i++) {
				if (result != null) {
					result.close();
				}
				int operandIndex = i & (OPERAND_COUNT - 1);
				result = state.nativeLefts[operandIndex].multiply(state.nativeRights[operandIndex]);
			}
			blackhole.consume(result);
		} finally {
			if (result != null) {
				result.close();
			}
		}
	}

	@Benchmark
	@OperationsPerInvocation(MULTIPLICATIONS_PER_INVOCATION)
	public void nativeBigIntMultiplyIntoLoop(MultiplyState state, Blackhole blackhole) {
		for (int i = 0; i < MULTIPLICATIONS_PER_INVOCATION; i++) {
			int operandIndex = i & (OPERAND_COUNT - 1);
			state.nativeResult.multiplyInto(state.nativeLefts[operandIndex], state.nativeRights[operandIndex]);
		}
		blackhole.consume(state.nativeResult);
	}

	@Benchmark
	@OperationsPerInvocation(MULTIPLICATIONS_PER_INVOCATION)
	public void jdkBigIntegerMultiplyLoop(MultiplyState state, Blackhole blackhole) {
		BigInteger result = BigInteger.ZERO;
		for (int i = 0; i < MULTIPLICATIONS_PER_INVOCATION; i++) {
			int operandIndex = i & (OPERAND_COUNT - 1);
			result = state.jdkLefts[operandIndex].multiply(state.jdkRights[operandIndex]);
		}
		blackhole.consume(result);
	}

	private static void closeAll(BigInt[] values) {
		if (values == null) {
			return;
		}
		for (BigInt value : values) {
			if (value != null) {
				value.close();
			}
		}
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
