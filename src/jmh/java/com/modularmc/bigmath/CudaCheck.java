package com.modularmc.bigmath;

import java.util.Locale;

/**
 * Diagnostic main for the configured Native multiplication backend.
 * <p>
 * It configures the requested backend through {@link BigmathRuntime}, prints the
 * public runtime diagnostics, then runs a handful of distinct large multiplies.
 * <p>
 * Run via {@code scripts/cuda-bench.sh} (which stages a CUDA-enabled native lib),
 * or manually:
 * <pre>
 *   java --enable-native-access=ALL-UNNAMED \
 *        -Dbigmath.native.path=build/native/lib/bigmath_ffm.dll \
 *        -cp build/classes/java/main;build/cudacheck \
 *        com.modularmc.bigmath.CudaCheck [AUTO|CPU|CUFFT|NTT] [digits]
 * </pre>
 */
public final class CudaCheck {
	public static void main(String[] args) {
		RuntimeOptions.CudaBackend backend = args.length > 0
			? RuntimeOptions.CudaBackend.valueOf(args[0].toUpperCase(Locale.ROOT))
			: RuntimeOptions.CudaBackend.AUTO;
		BigmathRuntime.configure(RuntimeOptions.builder()
			.cudaBackend(backend)
			.productCacheEnabled(false)
			.build());
		RuntimeDiagnostics before = BigmathRuntime.initializeAsync().join();
		System.out.println("requestedBackend = " + backend);
		System.out.println("activeBackend    = " + before.cuda().activeBackend());
		System.out.println("calibration      = " + before.cuda().calibrationStatus());
		System.out.println("deviceCount      = " + before.cuda().deviceCount());
		System.out.println("deviceName       = " + before.cuda().deviceName());
		System.out.println("status           = " + before.cuda().statusMessage());

		int n = args.length > 1 ? Integer.parseInt(args[1]) : 80000;
		StringBuilder sb = new StringBuilder(n);
		sb.append('9');
		for (int i = 1; i < n; i++) sb.append((char) ('0' + (i * 7 + 3) % 10));
		String s = sb.toString();

		try (BigInt a = BigInt.fromString(s, 10);
			BigInt b = BigInt.fromString(new StringBuilder(s).reverse().toString(), 10)) {
			for (int k = 0; k < 5; k++) {
				try (BigInt shifted = a.shiftLeft(k * 64L);
					BigInt r = shifted.multiply(b)) {
					blackhole ^= r.hashCode();
				}
			}
		}
		RuntimeDiagnostics after = BigmathRuntime.diagnostics();
		System.out.println("cpuFallbacks     before=" + before.cpuFallbackCount()
			+ " after=" + after.cpuFallbackCount()
			+ " (delta=" + (after.cpuFallbackCount() - before.cpuFallbackCount()) + ")");
	}

	private static long blackhole;
}
