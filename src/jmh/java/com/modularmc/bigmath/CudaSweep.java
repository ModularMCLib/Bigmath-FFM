package com.modularmc.bigmath;

import java.util.Arrays;
import java.util.Locale;
import java.util.SplittableRandom;

/**
 * Standalone public-API multiply sweep across large operand sizes.
 * <p>
 * The backend is selected through {@link RuntimeOptions}; the product cache is
 * disabled so each row measures multiplication rather than result reuse. Run
 * separate CPU, cuFFT, NTT, and AUTO processes to compare the same public API.
 * <p>
 * Usage: {@code CudaSweep [AUTO|CPU|CUFFT|NTT] [digit sizes...]}. Defaults to
 * AUTO and digit sizes 40k..640k. Intended to be launched by
 * {@code scripts/cuda-bench.sh} against a CUDA-enabled native library.
 */
public final class CudaSweep {

	static final int PAIRS = 24;
	static final int REPS = 40;    // measured multiplies per pair

	public static void main(String[] args) {
		RuntimeOptions.CudaBackend backend = args.length > 0
			? RuntimeOptions.CudaBackend.valueOf(args[0].toUpperCase(Locale.ROOT))
			: RuntimeOptions.CudaBackend.AUTO;
		BigmathRuntime.configure(RuntimeOptions.builder()
			.cudaBackend(backend)
			.productCacheEnabled(false)
			.build());
		RuntimeDiagnostics diagnostics = BigmathRuntime.initializeAsync().join();
		if (backend != RuntimeOptions.CudaBackend.CPU &&
				diagnostics.cuda().calibrationStatus() != RuntimeDiagnostics.CalibrationStatus.READY) {
			throw new IllegalStateException(diagnostics.cuda().statusMessage());
		}
		System.err.println("Backend: " + backend + " -> " + diagnostics.cuda().activeBackend()
			+ " / " + diagnostics.cuda().deviceName());
		int[] sizes = args.length > 1
				? Arrays.stream(args, 1, args.length).mapToInt(Integer::parseInt).toArray()
				: new int[]{40000, 80000, 160000, 320000, 640000};

		System.out.printf("%-9s %-8s %12s %14s%n",
				"digits", "backend", "us/op", "cpuFallbacks");
		for (int n : sizes) {
			BigInt[] a = new BigInt[PAIRS], b = new BigInt[PAIRS];
			SplittableRandom rnd = new SplittableRandom(0xC0FFEEL ^ n);
			try {
				for (int i = 0; i < PAIRS; i++) {
					a[i] = BigInt.fromString(randDigits(n, rnd), 10);
					b[i] = BigInt.fromString(randDigits(n, rnd), 10);
				}

				for (int i = 0; i < PAIRS; i++) {
					try (BigInt result = a[i].multiply(b[i])) {
						sink(result);
					}
				}

				long fallbackBefore = BigmathRuntime.diagnostics().cpuFallbackCount();
				long elapsed = 0;
				long operations = 0;
				for (int repetition = 0; repetition < REPS; repetition++) {
					for (int i = 0; i < PAIRS; i++) {
						long started = System.nanoTime();
						try (BigInt result = a[i].multiply(b[i])) {
							sink(result);
						}
						elapsed += System.nanoTime() - started;
						operations++;
					}
				}
				long fallbacks = BigmathRuntime.diagnostics().cpuFallbackCount() - fallbackBefore;
				System.out.printf("%-9d %-8s %12.1f %14d%n",
					n, backend, elapsed / 1000.0 / operations, fallbacks);
			} finally {
				for (int i = 0; i < PAIRS; i++) {
					if (a[i] != null) a[i].close();
					if (b[i] != null) b[i].close();
				}
			}
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
