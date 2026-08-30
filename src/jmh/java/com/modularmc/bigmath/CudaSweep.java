package com.modularmc.bigmath;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Arrays;
import java.util.SplittableRandom;

/**
 * Standalone CUDA-vs-GMP multiply sweep across large operand sizes.
 * <p>
 * Uses {@value #PAIRS} distinct operand pairs per size to defeat the native
 * 16-way product cache, verifies CUDA actually executed via the native multiply
 * counter ({@code cudaOps} column), and reports wall-clock us/op for the GMP
 * (CPU) path and the auto-dispatch path (which selects CUDA above its bit
 * threshold). The {@code speedup} column is GMP/CUDA — values above 1.0 mean CUDA wins.
 * <p>
 * Defaults to digit sizes 40k..640k. Pass explicit sizes as args. Intended to be
 * launched by {@code scripts/cuda-bench.sh} against a CUDA-enabled native lib.
 */
public final class CudaSweep {

	static final MethodHandle MUL_GMP = BigmathFFM.getInstance()
			.downcall("bigint_mul_gmp", FunctionDescriptors.BIGINT_BINARY);
	static final MethodHandle FREE = BigmathFFM.getInstance()
			.downcall("bigint_free", FunctionDescriptors.BIGINT_FREE);

	static final int PAIRS = 24;   // > 16-way product cache so every multiply is a miss
	static final int REPS = 40;    // measured multiplies per pair per path

	public static void main(String[] args) throws Throwable {
		System.err.println("CUDA: " + BigmathFFM.cudaAvailable() + " / " + BigmathFFM.cudaDeviceName());
		int[] sizes = args.length > 0
				? Arrays.stream(args).mapToInt(Integer::parseInt).toArray()
				: new int[]{40000, 80000, 160000, 320000, 640000};

		System.out.printf("%-9s %12s %12s %10s %8s%n",
				"digits", "GMP us/op", "CUDA us/op", "speedup", "cudaOps");
		for (int n : sizes) {
			BigInt[] a = new BigInt[PAIRS], b = new BigInt[PAIRS];
			SplittableRandom rnd = new SplittableRandom(0xC0FFEEL ^ n);
			for (int i = 0; i < PAIRS; i++) {
				a[i] = BigInt.fromString(randDigits(n, rnd), 10);
				b[i] = BigInt.fromString(randDigits(n, rnd), 10);
			}

			for (int w = 0; w < PAIRS; w++) {              // warmup both paths
				try (BigInt r = a[w].multiply(b[w])) { sink(r); }
				FREE.invokeExact(mulGmp(a[w], b[w]));
			}

			long cudaBefore = BigmathFFM.cudaMultiplyCount();
			long tC = 0, tG = 0, ops = 0;
			for (int r = 0; r < REPS; r++) {
				for (int i = 0; i < PAIRS; i++) {
					long s = System.nanoTime();
					try (BigInt res = a[i].multiply(b[i])) { sink(res); }
					tC += System.nanoTime() - s;

					s = System.nanoTime();
					MemorySegment g = mulGmp(a[i], b[i]);
					tG += System.nanoTime() - s;
					FREE.invokeExact(g);
					ops++;
				}
			}
			long cudaOps = BigmathFFM.cudaMultiplyCount() - cudaBefore;
			double gmpUs = tG / 1000.0 / ops, cudaUs = tC / 1000.0 / ops;
			System.out.printf("%-9d %12.1f %12.1f %9.2fx %8d%n",
					n, gmpUs, cudaUs, gmpUs / cudaUs, cudaOps);

			for (int i = 0; i < PAIRS; i++) { a[i].close(); b[i].close(); }
		}
	}

	static MemorySegment mulGmp(BigInt l, BigInt r) throws Throwable {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment out = arena.allocate(ValueLayout.ADDRESS);
			MUL_GMP.invokeExact(out, l.nativePtr(), r.nativePtr());
			return out.get(ValueLayout.ADDRESS, 0);
		}
	}

	static String randDigits(int n, SplittableRandom rnd) {
		StringBuilder sb = new StringBuilder(n);
		sb.append((char) ('1' + rnd.nextInt(9)));
		for (int i = 1; i < n; i++) sb.append((char) ('0' + rnd.nextInt(10)));
		return sb.toString();
	}

	static long blackhole;
	static void sink(BigInt x) { blackhole ^= x.hashCode(); }
}
