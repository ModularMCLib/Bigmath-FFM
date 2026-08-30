package com.modularmc.bigmath;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Locale;
import java.util.SplittableRandom;

/**
 * Correctness gate for configured multiply, BigDeci multiply, and modular
 * exponentiation paths through the public Bigmath API.
 * <p>
 * Usage: {@code CudaVerify [AUTO|CPU|CUFFT|NTT] [digit sizes...]}. The selected
 * backend is configured before Native initialization, and all results are
 * compared with public JDK or Bigmath values rather than private Native exports.
 */
public final class CudaVerify {

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
			: new int[]{90000, 100000, 158000, 162000, 200000, 316000, 320000, 400000, 640000, 800000};

		SplittableRandom random = new SplittableRandom(0x5EEDC0DEL);
		System.out.printf("%-9s %-8s %14s   %s%n", "size", "case", "cpuFallbacks", "result");
		int failures = 0;
		for (int digits : sizes) {
			failures += checkMultiply(digits, false, random) ? 0 : 1;
			failures += checkMultiply(digits, true, random) ? 0 : 1;
		}

		for (int precisionBits : new int[]{300000, 524288}) {
			failures += checkBigDeci(precisionBits, random) ? 0 : 1;
		}

		for (int modulusDigits : new int[]{200000, 250000}) {
			failures += checkModPow(modulusDigits, random) ? 0 : 1;
		}

		System.out.printf("%nRESULT: %s%n", failures == 0 ? "ALL PASS" : failures + " FAILED");
		if (failures != 0) {
			throw new AssertionError(failures + " public API CUDA verification cases failed");
		}
	}

	static boolean checkMultiply(int digits, boolean square, SplittableRandom random) {
		BigInteger leftValue = new BigInteger(randDigits(digits, random));
		BigInteger rightValue = square ? leftValue : new BigInteger(randDigits(digits, random));
		BigInteger expected = leftValue.multiply(rightValue);
		long fallbackBefore = BigmathRuntime.diagnostics().cpuFallbackCount();
		try (BigInt left = BigInt.fromBigInteger(leftValue);
			BigInt right = BigInt.fromBigInteger(rightValue);
			BigInt product = left.multiply(square ? left : right)) {
			long fallbacks = BigmathRuntime.diagnostics().cpuFallbackCount() - fallbackBefore;
			boolean matches = expected.equals(product.toBigInteger());
			System.out.printf("%-9d %-8s %14d   %s%n",
				digits, square ? "square" : "multiply", fallbacks, matches ? "PASS" : "MISMATCH");
			return matches;
		}
	}

	static boolean checkBigDeci(int precisionBits, SplittableRandom random) {
		int decimalDigits = (int) (precisionBits * 0.30103) - 8;
		BigInteger leftValue = new BigInteger(randDigits(decimalDigits, random));
		BigInteger rightValue = new BigInteger(randDigits(decimalDigits, random));
		BigInteger expectedValue = leftValue.multiply(rightValue);
		long fallbackBefore = BigmathRuntime.diagnostics().cpuFallbackCount();
		try (BigInt leftInteger = BigInt.fromBigInteger(leftValue);
			BigInt rightInteger = BigInt.fromBigInteger(rightValue);
			BigInt expectedInteger = BigInt.fromBigInteger(expectedValue);
			BigDeci left = BigDeci.fromBigInt(leftInteger, precisionBits);
			BigDeci right = BigDeci.fromBigInt(rightInteger, precisionBits);
			BigDeci expected = BigDeci.fromBigInt(expectedInteger, precisionBits);
			BigDeci product = left.multiply(right)) {
			long fallbacks = BigmathRuntime.diagnostics().cpuFallbackCount() - fallbackBefore;
			boolean matches = product.compareTo(expected) == 0;
			System.out.printf("%-9d %-8s %14d   %s%n",
				precisionBits, "BigDeci", fallbacks, matches ? "PASS" : "MISMATCH");
			return matches;
		}
	}

	static boolean checkModPow(int modulusDigits, SplittableRandom random) {
		BigInteger baseValue = new BigInteger(randDigits(modulusDigits, random));
		BigInteger modulusValue = new BigInteger(randDigits(modulusDigits, random));
		BigInteger exponentValue = BigInteger.valueOf(random.nextLong(1L << 40, 1L << 46));
		BigInteger expected = baseValue.modPow(exponentValue, modulusValue);
		long fallbackBefore = BigmathRuntime.diagnostics().cpuFallbackCount();
		try (BigInt base = BigInt.fromBigInteger(baseValue);
			BigInt exponent = BigInt.fromBigInteger(exponentValue);
			BigInt modulus = BigInt.fromBigInteger(modulusValue);
			BigInt result = base.modPow(exponent, modulus)) {
			long fallbacks = BigmathRuntime.diagnostics().cpuFallbackCount() - fallbackBefore;
			boolean matches = expected.equals(result.toBigInteger());
			System.out.printf("%-9d %-8s %14d   %s%n",
				modulusDigits, "modPow", fallbacks, matches ? "PASS" : "MISMATCH");
			return matches;
		}
	}

	static String randDigits(int digits, SplittableRandom random) {
		StringBuilder result = new StringBuilder(digits);
		result.append((char) ('1' + random.nextInt(9)));
		for (int index = 1; index < digits; index++) {
			result.append((char) ('0' + random.nextInt(10)));
		}
		return result.toString();
	}

	private CudaVerify() {
	}
}
