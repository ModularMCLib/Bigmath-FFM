package com.modularmc.bigmath;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Arrays;
import java.util.SplittableRandom;

/**
 * Correctness gate for the GPU multiply paths.
 * <p>
 * The {@link CudaSweep} harness only measures wall-clock time; it never checks
 * that the GPU result is correct. A bug in the (hand-written) integer-NTT kernel
 * would therefore produce silently-wrong products. This harness multiplies via
 * the auto-dispatch path — which routes to the GPU integer-NTT kernel when
 * launched with {@code BIGMATH_CUDA_NTT=1} and the operands exceed the CUDA bit
 * threshold — and compares the product bit-exactly against the pure-GMP
 * reference {@code bigint_mul_gmp}. It covers distinct-operand multiplies and the
 * squaring fast path ({@code a*a}), across digit sizes chosen to bracket the NTT
 * transform-size (power-of-two) boundaries.
 * <p>
 * Exits non-zero on any mismatch so {@code scripts/cuda-bench.sh} / CI can gate
 * a kernel rewrite on correctness before trusting its timings.
 */
public final class CudaVerify {

	static final MethodHandle MUL_GMP = BigmathFFM.getInstance()
			.downcall("bigint_mul_gmp", FunctionDescriptors.BIGINT_BINARY);
	static final MethodHandle CMP = BigmathFFM.getInstance()
			.downcall("bigint_cmp", FunctionDescriptors.BIGINT_CMP);
	static final MethodHandle FREE = BigmathFFM.getInstance()
			.downcall("bigint_free", FunctionDescriptors.BIGINT_FREE);
	// BigDeci reference handles (same FFM layout as the bigint binary/cmp/free).
	static final MethodHandle DEC_MUL_MPFR = BigmathFFM.getInstance()
			.downcall("bigdecimal_mul_mpfr", FunctionDescriptors.BIGINT_BINARY);
	static final MethodHandle DEC_CMP = BigmathFFM.getInstance()
			.downcall("bigdecimal_cmp", FunctionDescriptors.BIGINT_CMP);
	static final MethodHandle DEC_FREE = BigmathFFM.getInstance()
			.downcall("bigdecimal_free", FunctionDescriptors.BIGINT_FREE);

	public static void main(String[] args) throws Throwable {
		System.err.println("CUDA: " + BigmathFFM.cudaAvailable() + " / " + BigmathFFM.cudaDeviceName()
				+ "   BIGMATH_CUDA_NTT=" + System.getenv("BIGMATH_CUDA_NTT"));

		// Sizes >= the ~79k-digit CUDA threshold so the GPU path engages, chosen to
		// straddle NTT transform-size boundaries: the u16 digit count is ~0.2075x
		// the decimal length, so result_size = next_pow2 lands on 2^16..2^19 across
		// this range, plus a few just-over/just-under-a-boundary points.
		int[] sizes = args.length > 0
				? Arrays.stream(args).mapToInt(Integer::parseInt).toArray()
				: new int[]{90000, 100000, 158000, 162000, 200000, 316000, 320000, 400000, 640000, 800000};

		SplittableRandom rnd = new SplittableRandom(0x5EEDC0DEL);
		System.out.printf("%-9s %-6s %8s   %s%n", "digits", "case", "cudaOps", "result");
		int failures = 0;
		for (int n : sizes) {
			failures += check(n, false, rnd) ? 0 : 1;
			failures += check(n, true, rnd) ? 0 : 1;
		}
		System.out.printf("%nRESULT: %s  (%d cases checked)%n",
				failures == 0 ? "ALL PASS" : (failures + " FAILED"), sizes.length * 2);

		// BigDeci high-precision multiply: bigdecimal_mul routes the significand
		// product through the GPU (gpu_mpfr_mul) above ~262144-bit precision.
		// Verify bit-exactly against the pure-MPFR reference bigdecimal_mul_mpfr.
		int decFail = 0;
		for (int precBits : new int[]{300000, 524288}) {
			decFail += checkBigDeci(precBits, rnd) ? 0 : 1;
		}
		System.out.printf("RESULT(bigdeci): %s%n",
				decFail == 0 ? "ALL PASS" : (decFail + " FAILED"));

		if (failures + decFail != 0) {
			System.exit(1);
		}
	}

	/** High-precision BigDeci multiply (GPU significand path) vs pure-MPFR reference. */
	static boolean checkBigDeci(int precBits, SplittableRandom rnd) throws Throwable {
		int decDigits = (int) (precBits * 0.3011) + 8;   // fill the significand
		BigDeci a = BigDeci.fromString(randDecimal(decDigits, rnd), precBits);
		BigDeci b = BigDeci.fromString(randDecimal(decDigits, rnd), precBits);
		MemorySegment ref = null;
		try (BigDeci prod = a.multiply(b)) {
			ref = mulMpfr(a, b);
			boolean ok = (int) DEC_CMP.invokeExact(prod.nativePtr(), ref) == 0;
			System.out.printf("%-9d %-6s %8s   %s%n", precBits, "decmul", "-", ok ? "PASS" : "*** MISMATCH ***");
			return ok;
		} finally {
			if (ref != null) {
				DEC_FREE.invokeExact(ref);
			}
			a.close();
			b.close();
		}
	}

	static MemorySegment mulMpfr(BigDeci l, BigDeci r) throws Throwable {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment out = arena.allocate(ValueLayout.ADDRESS);
			DEC_MUL_MPFR.invokeExact(out, l.nativePtr(), r.nativePtr());
			return out.get(ValueLayout.ADDRESS, 0);
		}
	}

	static String randDecimal(int digits, SplittableRandom rnd) {
		StringBuilder sb = new StringBuilder(digits + 2);
		sb.append((char) ('1' + rnd.nextInt(9))).append('.');
		for (int i = 0; i < digits; i++) {
			sb.append((char) ('0' + rnd.nextInt(10)));
		}
		return sb.toString();
	}

	/** Multiply via the auto/GPU path and compare bit-exactly to the GMP reference. */
	static boolean check(int n, boolean square, SplittableRandom rnd) throws Throwable {
		BigInt a = BigInt.fromString(randDigits(n, rnd), 10);
		BigInt b = square ? a : BigInt.fromString(randDigits(n, rnd), 10);
		long before = BigmathFFM.cudaMultiplyCount();
		MemorySegment refPtr = null;
		try (BigInt prod = a.multiply(b)) {
			long ops = BigmathFFM.cudaMultiplyCount() - before;
			refPtr = mulGmp(a, b);
			int cmp = (int) CMP.invokeExact(prod.nativePtr(), refPtr);
			boolean ok = cmp == 0;
			System.out.printf("%-9d %-6s %8d   %s%n",
					n, square ? "a*a" : "a*b", ops, ok ? "PASS" : "*** MISMATCH ***");
			return ok;
		} finally {
			if (refPtr != null) {
				FREE.invokeExact(refPtr);
			}
			if (!square) {
				b.close();
			}
			a.close();
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
		for (int i = 1; i < n; i++) {
			sb.append((char) ('0' + rnd.nextInt(10)));
		}
		return sb.toString();
	}

	private CudaVerify() {}
}
