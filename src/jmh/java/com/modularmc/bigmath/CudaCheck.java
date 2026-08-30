package com.modularmc.bigmath;

/**
 * Diagnostic main that proves whether the loaded native library actually
 * executes multiplications on the GPU.
 * <p>
 * It prints CUDA availability/device info, then runs a handful of distinct
 * large multiplies and reports the delta of the native {@code cudaMultiplyCount}
 * counter. A non-zero delta means the GPU convolution path ran; zero means the
 * library fell back to CPU/GMP.
 * <p>
 * Run via {@code scripts/cuda-bench.sh} (which stages a CUDA-enabled native lib),
 * or manually:
 * <pre>
 *   java --enable-native-access=ALL-UNNAMED \
 *        -Dbigmath.native.path=build/native/lib/bigmath_ffm.dll \
 *        -cp build/classes/java/main;build/cudacheck \
 *        com.modularmc.bigmath.CudaCheck [digits]
 * </pre>
 */
public final class CudaCheck {
	public static void main(String[] args) {
		System.out.println("cudaAvailable   = " + BigmathFFM.cudaAvailable());
		System.out.println("deviceCount     = " + BigmathFFM.cudaDeviceCount());
		System.out.println("deviceName      = " + BigmathFFM.cudaDeviceName());
		System.out.println("status          = " + BigmathFFM.cudaStatusMessage());

		int n = args.length > 0 ? Integer.parseInt(args[0]) : 80000;
		StringBuilder sb = new StringBuilder(n);
		sb.append('9');
		for (int i = 1; i < n; i++) sb.append((char) ('0' + (i * 7 + 3) % 10));
		String s = sb.toString();

		long before = BigmathFFM.cudaMultiplyCount();
		try (BigInt a = BigInt.fromString(s, 10);
			BigInt b = BigInt.fromString(new StringBuilder(s).reverse().toString(), 10)) {
			for (int k = 0; k < 5; k++) {
				try (BigInt shifted = a.shiftLeft(k * 64L);
					BigInt r = shifted.multiply(b)) {
					if (r == null) System.out.println("null result");
				}
			}
		}
		long after = BigmathFFM.cudaMultiplyCount();
		System.out.println("multiplyCount   before=" + before + " after=" + after
				+ "  (delta=" + (after - before) + ")");
		System.out.println(after > before
				? ">>> CUDA path EXECUTED the multiplications"
				: ">>> CUDA did NOT run (CPU/GMP fallback)");
	}
}
