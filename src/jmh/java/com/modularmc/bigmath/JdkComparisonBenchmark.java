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

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 8, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class JdkComparisonBenchmark {

	private static final long RANDOM_SEED = 0x5eed_f00d_cafe_babeL;
	private static final int SMALL_INT_DIGITS = 9;
	private static final int DECIMAL_INTEGER_DIGITS = 10;
	private static final int RANDOM_PAIRS = 32;
	private static final MethodHandle BIGINT_MUL_GMP_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_mul_gmp",
			FunctionDescriptors.BIGINT_BINARY
	);
	private static final MethodHandle BIGINT_FREE_HANDLE = BigmathFFM.getInstance().downcall(
			"bigint_free",
			FunctionDescriptors.BIGINT_FREE
	);

	@State(Scope.Thread)
	public static class SmallIntState {
		BigInt[] nativeLeftValues;
		BigInt[] nativeRightValues;
		BigInteger[] jdkLeftValues;
		BigInteger[] jdkRightValues;
		int index;

		@Setup(Level.Trial)
		public void setup() {
			nativeLeftValues = new BigInt[RANDOM_PAIRS];
			nativeRightValues = new BigInt[RANDOM_PAIRS];
			jdkLeftValues = new BigInteger[RANDOM_PAIRS];
			jdkRightValues = new BigInteger[RANDOM_PAIRS];
			for (int i = 0; i < RANDOM_PAIRS; i++) {
				long left = randomPositiveLong(SMALL_INT_DIGITS, "small-left", i);
				long right = randomPositiveLong(SMALL_INT_DIGITS, "small-right", i);
				nativeLeftValues[i] = BigInt.fromLong(left);
				nativeRightValues[i] = BigInt.fromLong(right);
				jdkLeftValues[i] = BigInteger.valueOf(left);
				jdkRightValues[i] = BigInteger.valueOf(right);
			}
			index = 0;
		}

		@TearDown(Level.Trial)
		public void tearDown() {
			for (int i = 0; i < RANDOM_PAIRS; i++) {
				nativeLeftValues[i].close();
				nativeRightValues[i].close();
			}
		}

		int nextPair() {
			int pair = index;
			index++;
			if (index == RANDOM_PAIRS) {
				index = 0;
			}
			return pair;
		}
	}

	@State(Scope.Thread)
	public static class Int128State {
		Int128[] nativeLeftValues;
		Int128[] nativeRightValues;
		long[] primitiveLeftValues;
		long[] primitiveRightValues;
		Long[] boxedLeftValues;
		Long[] boxedRightValues;
		int index;

		@Setup(Level.Trial)
		public void setup() {
			nativeLeftValues = new Int128[RANDOM_PAIRS];
			nativeRightValues = new Int128[RANDOM_PAIRS];
			primitiveLeftValues = new long[RANDOM_PAIRS];
			primitiveRightValues = new long[RANDOM_PAIRS];
			boxedLeftValues = new Long[RANDOM_PAIRS];
			boxedRightValues = new Long[RANDOM_PAIRS];
			for (int i = 0; i < RANDOM_PAIRS; i++) {
				long left = randomPositiveLong(SMALL_INT_DIGITS, "int128-left", i);
				long right = randomPositiveLong(SMALL_INT_DIGITS, "int128-right", i);
				primitiveLeftValues[i] = left;
				primitiveRightValues[i] = right;
				boxedLeftValues[i] = left;
				boxedRightValues[i] = right;
				nativeLeftValues[i] = Int128.fromLong(left);
				nativeRightValues[i] = Int128.fromLong(right);
			}
			index = 0;
		}

		@TearDown(Level.Trial)
		public void tearDown() {
			for (int i = 0; i < RANDOM_PAIRS; i++) {
				nativeLeftValues[i].close();
				nativeRightValues[i].close();
			}
		}

		int nextPair() {
			int pair = index;
			index++;
			if (index == RANDOM_PAIRS) {
				index = 0;
			}
			return pair;
		}
	}

	@State(Scope.Thread)
	public static class LargeIntState {
		@Param({"128", "512", "2048", "80000"})
		public int digits;

		BigInt[] nativeLeftValues;
		BigInt[] nativeRightValues;
		BigInteger[] jdkLeftValues;
		BigInteger[] jdkRightValues;
		int index;

		@Setup(Level.Trial)
		public void setup() {
			nativeLeftValues = new BigInt[RANDOM_PAIRS];
			nativeRightValues = new BigInt[RANDOM_PAIRS];
			jdkLeftValues = new BigInteger[RANDOM_PAIRS];
			jdkRightValues = new BigInteger[RANDOM_PAIRS];
			for (int i = 0; i < RANDOM_PAIRS; i++) {
				String left = randomDecimalDigits(digits, seedFor("large-left", digits, i));
				String right = randomDecimalDigits(digits, seedFor("large-right", digits, i));
				nativeLeftValues[i] = BigInt.fromString(left, 10);
				nativeRightValues[i] = BigInt.fromString(right, 10);
				jdkLeftValues[i] = new BigInteger(left);
				jdkRightValues[i] = new BigInteger(right);
			}
			index = 0;
		}

		@TearDown(Level.Trial)
		public void tearDown() {
			for (int i = 0; i < RANDOM_PAIRS; i++) {
				nativeLeftValues[i].close();
				nativeRightValues[i].close();
			}
		}

		int nextPair() {
			int pair = index;
			index++;
			if (index == RANDOM_PAIRS) {
				index = 0;
			}
			return pair;
		}
	}

	@State(Scope.Thread)
	public static class RotatingLargeIntState {
		@Param({"40000", "80000"})
		public int digits;

		@Param({"32"})
		public int pairs;

		BigInt[] nativeValues;
		int index;

		@Setup(Level.Trial)
		public void setup() {
			nativeValues = new BigInt[pairs * 2];
			for (int i = 0; i < pairs; i++) {
				String left = randomDecimalDigits(digits, seedFor("rotating-left", digits, i));
				String right = randomDecimalDigits(digits, seedFor("rotating-right", digits, i));
				nativeValues[i * 2] = BigInt.fromString(left, 10);
				nativeValues[i * 2 + 1] = BigInt.fromString(right, 10);
			}
			index = 0;
		}

		@TearDown(Level.Trial)
		public void tearDown() {
			for (BigInt value : nativeValues) {
				value.close();
			}
		}

		int nextPair() {
			int pair = index;
			index++;
			if (index == pairs) {
				index = 0;
			}
			return pair;
		}
	}

	@State(Scope.Thread)
	public static class CpuNttDispatchState {
		@Param({"4096", "8192", "32768", "80000"})
		public int digits;

		BigInt[] nativeLeftValues;
		BigInt[] nativeRightValues;
		BigInt nativeResult;
		BigInteger[] jdkLeftValues;
		BigInteger[] jdkRightValues;
		int index;

		@Setup(Level.Trial)
		public void setup() {
			nativeLeftValues = new BigInt[RANDOM_PAIRS];
			nativeRightValues = new BigInt[RANDOM_PAIRS];
			jdkLeftValues = new BigInteger[RANDOM_PAIRS];
			jdkRightValues = new BigInteger[RANDOM_PAIRS];
			for (int i = 0; i < RANDOM_PAIRS; i++) {
				String left = randomDecimalDigits(digits, seedFor("cpu-dispatch-left", digits, i));
				String right = randomDecimalDigits(digits, seedFor("cpu-dispatch-right", digits, i));
				nativeLeftValues[i] = BigInt.fromString(left, 10);
				nativeRightValues[i] = BigInt.fromString(right, 10);
				jdkLeftValues[i] = new BigInteger(left);
				jdkRightValues[i] = new BigInteger(right);
			}
			nativeResult = BigInt.fromLong(0);
			index = 0;
		}

		@TearDown(Level.Trial)
		public void tearDown() {
			for (int i = 0; i < RANDOM_PAIRS; i++) {
				nativeLeftValues[i].close();
				nativeRightValues[i].close();
			}
			nativeResult.close();
		}

		int nextPair() {
			int pair = index;
			index++;
			if (index == RANDOM_PAIRS) {
				index = 0;
			}
			return pair;
		}
	}

	@State(Scope.Benchmark)
	public static class CudaState {
		String status;
		boolean available;

		@Setup(Level.Trial)
		public void setup() {
			status = BigmathFFM.cudaStatusMessage();
			available = BigmathFFM.cudaAvailable();
		}
	}

	@State(Scope.Thread)
	public static class DecimalState {
		@Param({"64", "256", "1024"})
		public int precision;

		BigDeci[] nativeLeftValues;
		BigDeci[] nativeRightValues;
		BigDecimal[] jdkLeftValues;
		BigDecimal[] jdkRightValues;
		MathContext mathContext;
		int index;

		@Setup(Level.Trial)
		public void setup() {
			int fractionalDigits = bitsToDecimalDigits(precision);
			nativeLeftValues = new BigDeci[RANDOM_PAIRS];
			nativeRightValues = new BigDeci[RANDOM_PAIRS];
			jdkLeftValues = new BigDecimal[RANDOM_PAIRS];
			jdkRightValues = new BigDecimal[RANDOM_PAIRS];
			for (int i = 0; i < RANDOM_PAIRS; i++) {
				String left = randomDecimalValue(
						DECIMAL_INTEGER_DIGITS,
						fractionalDigits,
						seedFor("decimal-left", precision, i)
				);
				String right = randomDecimalValue(
						DECIMAL_INTEGER_DIGITS,
						fractionalDigits,
						seedFor("decimal-right", precision, i)
				);
				nativeLeftValues[i] = BigDeci.fromString(left, precision);
				nativeRightValues[i] = BigDeci.fromString(right, precision);
				jdkLeftValues[i] = new BigDecimal(left);
				jdkRightValues[i] = new BigDecimal(right);
			}
			mathContext = new MathContext(bitsToDecimalDigits(precision), RoundingMode.HALF_EVEN);
			index = 0;
		}

		@TearDown(Level.Trial)
		public void tearDown() {
			for (int i = 0; i < RANDOM_PAIRS; i++) {
				nativeLeftValues[i].close();
				nativeRightValues[i].close();
			}
		}

		int nextPair() {
			int pair = index;
			index++;
			if (index == RANDOM_PAIRS) {
				index = 0;
			}
			return pair;
		}
	}

	@Benchmark
	public void nativeBigIntAddSmall(SmallIntState state, Blackhole blackhole) {
		int pair = state.nextPair();
		try (BigInt result = state.nativeLeftValues[pair].add(state.nativeRightValues[pair])) {
			blackhole.consume(result);
		}
	}

	@Benchmark
	public void jdkBigIntegerAddSmall(SmallIntState state, Blackhole blackhole) {
		int pair = state.nextPair();
		blackhole.consume(state.jdkLeftValues[pair].add(state.jdkRightValues[pair]));
	}

	@Benchmark
	public void nativeInt128FromLong(Int128State state, Blackhole blackhole) {
		int pair = state.nextPair();
		try (Int128 result = Int128.fromLong(state.primitiveLeftValues[pair])) {
			blackhole.consume(result);
		}
	}

	@Benchmark
	public void boxedLongFromLong(Int128State state, Blackhole blackhole) {
		int pair = state.nextPair();
		blackhole.consume(Long.valueOf(state.primitiveLeftValues[pair]));
	}

	@Benchmark
	public void nativeInt128Add(Int128State state, Blackhole blackhole) {
		int pair = state.nextPair();
		try (Int128 result = state.nativeLeftValues[pair].add(state.nativeRightValues[pair])) {
			blackhole.consume(result);
		}
	}

	@Benchmark
	public void primitiveLongAdd(Int128State state, Blackhole blackhole) {
		int pair = state.nextPair();
		blackhole.consume(state.primitiveLeftValues[pair] + state.primitiveRightValues[pair]);
	}

	@Benchmark
	public void boxedLongAdd(Int128State state, Blackhole blackhole) {
		int pair = state.nextPair();
		blackhole.consume(state.boxedLeftValues[pair] + state.boxedRightValues[pair]);
	}

	@Benchmark
	public void nativeInt128Subtract(Int128State state, Blackhole blackhole) {
		int pair = state.nextPair();
		try (Int128 result = state.nativeRightValues[pair].subtract(state.nativeLeftValues[pair])) {
			blackhole.consume(result);
		}
	}

	@Benchmark
	public void primitiveLongSubtract(Int128State state, Blackhole blackhole) {
		int pair = state.nextPair();
		blackhole.consume(state.primitiveRightValues[pair] - state.primitiveLeftValues[pair]);
	}

	@Benchmark
	public void nativeInt128Multiply(Int128State state, Blackhole blackhole) {
		int pair = state.nextPair();
		try (Int128 result = state.nativeLeftValues[pair].multiply(state.nativeRightValues[pair])) {
			blackhole.consume(result);
		}
	}

	@Benchmark
	public void primitiveLongMultiply(Int128State state, Blackhole blackhole) {
		int pair = state.nextPair();
		blackhole.consume(state.primitiveLeftValues[pair] * state.primitiveRightValues[pair]);
	}

	@Benchmark
	public void nativeInt128Divide(Int128State state, Blackhole blackhole) {
		int pair = state.nextPair();
		try (Int128 result = state.nativeRightValues[pair].divide(state.nativeLeftValues[pair])) {
			blackhole.consume(result);
		}
	}

	@Benchmark
	public void primitiveLongDivide(Int128State state, Blackhole blackhole) {
		int pair = state.nextPair();
		blackhole.consume(state.primitiveRightValues[pair] / state.primitiveLeftValues[pair]);
	}

	@Benchmark
	public void nativeInt128Mod(Int128State state, Blackhole blackhole) {
		int pair = state.nextPair();
		try (Int128 result = state.nativeRightValues[pair].mod(state.nativeLeftValues[pair])) {
			blackhole.consume(result);
		}
	}

	@Benchmark
	public void primitiveLongMod(Int128State state, Blackhole blackhole) {
		int pair = state.nextPair();
		blackhole.consume(state.primitiveRightValues[pair] % state.primitiveLeftValues[pair]);
	}

	@Benchmark
	public String nativeInt128ToString(Int128State state) {
		return state.nativeLeftValues[state.nextPair()].toString();
	}

	@Benchmark
	public String primitiveLongToString(Int128State state) {
		return Long.toString(state.primitiveLeftValues[state.nextPair()]);
	}

	@Benchmark
	public String boxedLongToString(Int128State state) {
		return state.boxedLeftValues[state.nextPair()].toString();
	}

	@Benchmark
	public String nativeInt128Format(Int128State state) {
		return state.nativeRightValues[state.nextPair()].toFormattedString();
	}

	@Benchmark
	public String boxedLongFormat(Int128State state) {
		return formatGroupedDecimal(state.boxedRightValues[state.nextPair()].toString());
	}

	@Benchmark
	public void nativeBigIntMultiplyLargeCpuNtt(LargeIntState state, Blackhole blackhole) {
		int pair = state.nextPair();
		try (BigInt result = BigInt.multiplyCpu(state.nativeLeftValues[pair], state.nativeRightValues[pair])) {
			blackhole.consume(result);
		}
	}

	@Benchmark
	public void cpuDispatchNativeMultiplyAuto(CpuNttDispatchState state, CudaState cudaState, Blackhole blackhole) {
		blackhole.consume(cudaState.status);
		blackhole.consume(cudaState.available);
		int pair = state.nextPair();
		try (BigInt result = state.nativeLeftValues[pair].multiply(state.nativeRightValues[pair])) {
			blackhole.consume(BigmathFFM.cudaMultiplyCount());
			blackhole.consume(result);
		}
	}

	@Benchmark
	public void cpuDispatchNativeMultiplyIntoAuto(CpuNttDispatchState state, CudaState cudaState, Blackhole blackhole) {
		blackhole.consume(cudaState.status);
		blackhole.consume(cudaState.available);
		int pair = state.nextPair();
		blackhole.consume(state.nativeResult.multiplyInto(state.nativeLeftValues[pair], state.nativeRightValues[pair]));
		blackhole.consume(BigmathFFM.cudaMultiplyCount());
	}

	@Benchmark
	public void cpuDispatchNativeMultiplyCpuNtt(CpuNttDispatchState state, Blackhole blackhole) {
		int pair = state.nextPair();
		try (BigInt result = BigInt.multiplyCpu(state.nativeLeftValues[pair], state.nativeRightValues[pair])) {
			blackhole.consume(result);
		}
	}

	@Benchmark
	public void cpuDispatchNativeMultiplyGmp(CpuNttDispatchState state, Blackhole blackhole) {
		int pair = state.nextPair();
		MemorySegment result = multiplyGmp(state.nativeLeftValues[pair], state.nativeRightValues[pair]);
		try {
			blackhole.consume(result);
		} finally {
			freeBigInt(result);
		}
	}

	@Benchmark
	public void cpuDispatchJdkBigIntegerMultiply(CpuNttDispatchState state, Blackhole blackhole) {
		int pair = state.nextPair();
		blackhole.consume(state.jdkLeftValues[pair].multiply(state.jdkRightValues[pair]));
	}

	@Benchmark
	public void nativeBigIntMultiplyLargeAutoBackend(LargeIntState state, CudaState cudaState, Blackhole blackhole) {
		blackhole.consume(cudaState.status);
		blackhole.consume(cudaState.available);
		int pair = state.nextPair();
		try (BigInt result = state.nativeLeftValues[pair].multiply(state.nativeRightValues[pair])) {
			blackhole.consume(BigmathFFM.cudaMultiplyCount());
			blackhole.consume(result);
		}
	}

	@Benchmark
	public void nativeBigIntMultiplyLargeRotatingAutoBackend(
			RotatingLargeIntState state,
			CudaState cudaState,
			Blackhole blackhole) {
		blackhole.consume(cudaState.status);
		blackhole.consume(cudaState.available);
		int pair = state.nextPair();
		try (BigInt result = state.nativeValues[pair * 2].multiply(state.nativeValues[pair * 2 + 1])) {
			blackhole.consume(BigmathFFM.cudaMultiplyCount());
			blackhole.consume(result);
		}
	}

	@Benchmark
	public void jdkBigIntegerMultiplyLarge(LargeIntState state, Blackhole blackhole) {
		int pair = state.nextPair();
		blackhole.consume(state.jdkLeftValues[pair].multiply(state.jdkRightValues[pair]));
	}

	@Benchmark
	public String nativeBigIntToStringLarge(LargeIntState state) {
		return state.nativeLeftValues[state.nextPair()].toString();
	}

	@Benchmark
	public String jdkBigIntegerToStringLarge(LargeIntState state) {
		return state.jdkLeftValues[state.nextPair()].toString();
	}

	@Benchmark
	public String nativeBigIntFormatLarge(LargeIntState state) {
		return state.nativeLeftValues[state.nextPair()].toFormattedString();
	}

	@Benchmark
	public String jdkBigIntegerFormatLarge(LargeIntState state) {
		return formatGroupedDecimal(state.jdkLeftValues[state.nextPair()].toString());
	}

	@Benchmark
	public void nativeBigDeciAdd(DecimalState state, Blackhole blackhole) {
		int pair = state.nextPair();
		try (BigDeci result = state.nativeLeftValues[pair].add(state.nativeRightValues[pair])) {
			blackhole.consume(result);
		}
	}

	@Benchmark
	public void jdkBigDecimalAdd(DecimalState state, Blackhole blackhole) {
		int pair = state.nextPair();
		blackhole.consume(state.jdkLeftValues[pair].add(state.jdkRightValues[pair], state.mathContext));
	}

	@Benchmark
	public void nativeBigDeciDivide(DecimalState state, Blackhole blackhole) {
		int pair = state.nextPair();
		try (BigDeci result = state.nativeLeftValues[pair].divide(state.nativeRightValues[pair])) {
			blackhole.consume(result);
		}
	}

	@Benchmark
	public void jdkBigDecimalDivide(DecimalState state, Blackhole blackhole) {
		int pair = state.nextPair();
		blackhole.consume(state.jdkLeftValues[pair].divide(state.jdkRightValues[pair], state.mathContext));
	}

	@Benchmark
	public void nativeBigDeciSqrt(DecimalState state, Blackhole blackhole) {
		try (BigDeci result = state.nativeLeftValues[state.nextPair()].sqrt()) {
			blackhole.consume(result);
		}
	}

	@Benchmark
	public void jdkBigDecimalSqrt(DecimalState state, Blackhole blackhole) {
		blackhole.consume(state.jdkLeftValues[state.nextPair()].sqrt(state.mathContext));
	}

	@Benchmark
	public String nativeBigDeciToString(DecimalState state) {
		return state.nativeLeftValues[state.nextPair()].toString();
	}

	@Benchmark
	public String jdkBigDecimalToString(DecimalState state) {
		return state.jdkLeftValues[state.nextPair()].round(state.mathContext).toString();
	}

	private static long randomPositiveLong(int digits, String scope, int index) {
		return Long.parseLong(randomDecimalDigits(digits, seedFor(scope, digits, index)));
	}

	private static String randomDecimalValue(int integerDigits, int fractionalDigits, long seed) {
		return randomDecimalDigits(integerDigits, seed) + "."
				+ randomDecimalDigits(fractionalDigits, mixSeed(seed, fractionalDigits));
	}

	private static String randomDecimalDigits(int digits, long seed) {
		SplittableRandom random = new SplittableRandom(seed);
		StringBuilder sb = new StringBuilder(digits);
		sb.append((char) ('1' + random.nextInt(9)));
		while (sb.length() < digits) {
			sb.append((char) ('0' + random.nextInt(10)));
		}
		return sb.toString();
	}

	private static long seedFor(String scope, int digits, int index) {
		long seed = RANDOM_SEED;
		for (int i = 0; i < scope.length(); i++) {
			seed = mixSeed(seed, scope.charAt(i));
		}
		seed = mixSeed(seed, digits);
		return mixSeed(seed, index);
	}

	private static long mixSeed(long seed, long value) {
		long mixed = seed ^ (value + 0x9e37_79b9_7f4a_7c15L + (seed << 6) + (seed >>> 2));
		mixed ^= mixed >>> 30;
		mixed *= 0xbf58_476d_1ce4_e5b9L;
		mixed ^= mixed >>> 27;
		mixed *= 0x94d0_49bb_1331_11ebL;
		return mixed ^ (mixed >>> 31);
	}

	private static MemorySegment multiplyGmp(BigInt left, BigInt right) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
			invokeBinaryOut(BIGINT_MUL_GMP_HANDLE, result, left.nativePtr(), right.nativePtr());
			return result.get(ValueLayout.ADDRESS, 0);
		}
	}

	private static void invokeBinaryOut(MethodHandle handle, MemorySegment out, MemorySegment left, MemorySegment right) {
		try {
			handle.invokeExact(out, left, right);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable e) {
			throw new IllegalStateException("Native BigInt benchmark operation failed", e);
		}
	}

	private static void freeBigInt(MemorySegment value) {
		if (MemorySegment.NULL.equals(value)) {
			return;
		}
		try {
			BIGINT_FREE_HANDLE.invokeExact(value);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable e) {
			throw new IllegalStateException("Native BigInt benchmark cleanup failed", e);
		}
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
