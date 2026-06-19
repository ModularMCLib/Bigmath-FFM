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
	static final MethodHandle FROM_LONG = BigmathFFM.getInstance()
			.downcall("bigint_from_long", FunctionDescriptors.BIGINT_FROM_LONG);

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

		// Factorial: product_tree now routes its large top nodes through the GPU
		// (accelerated_mul). Verify against an independent CPU product tree built
		// with bigint_mul_gmp. 40000! is ~166k digits so the top two levels exceed
		// the CUDA threshold.
		int facFail = 0;
		for (long fn : new long[]{40000L, 60000L}) {
			facFail += checkFactorial(fn) ? 0 : 1;
		}
		System.out.printf("RESULT(factorial): %s%n",
				facFail == 0 ? "ALL PASS" : (facFail + " FAILED"));

		if (failures + facFail != 0) {
			System.exit(1);
		}
	}

	/** lib factorial (GPU product tree) vs an independent CPU product tree (bigint_mul_gmp). */
	static boolean checkFactorial(long n) throws Throwable {
		MemorySegment ref = null;
		try (BigInt fac = BigInt.factorial(n)) {
			ref = treeRefGmp(2, n);
			boolean ok = (int) CMP.invokeExact(fac.nativePtr(), ref) == 0;
			System.out.printf("%-9d %-6s %8s   %s%n", n, "fac", "-", ok ? "PASS" : "*** MISMATCH ***");
			return ok;
		} finally {
			if (ref != null) {
				FREE.invokeExact(ref);
			}
		}
	}

	/** Balanced product of [lo..hi] using only the pure-GMP multiply; caller frees the result. */
	static MemorySegment treeRefGmp(long lo, long hi) throws Throwable {
		if (lo == hi) {
			return fromLongPtr(lo);
		}
		long mid = lo + (hi - lo) / 2;
		MemorySegment left = treeRefGmp(lo, mid);
		MemorySegment right = treeRefGmp(mid + 1, hi);
		MemorySegment prod = mulGmpPtr(left, right);
		FREE.invokeExact(left);
		FREE.invokeExact(right);
		return prod;
	}

	static MemorySegment fromLongPtr(long v) throws Throwable {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment out = arena.allocate(ValueLayout.ADDRESS);
			FROM_LONG.invokeExact(out, v);
			return out.get(ValueLayout.ADDRESS, 0);
		}
	}

	static MemorySegment mulGmpPtr(MemorySegment l, MemorySegment r) throws Throwable {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment out = arena.allocate(ValueLayout.ADDRESS);
			MUL_GMP.invokeExact(out, l, r);
			return out.get(ValueLayout.ADDRESS, 0);
		}
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
