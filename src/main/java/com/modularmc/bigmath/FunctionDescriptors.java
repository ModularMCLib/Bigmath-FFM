package com.modularmc.bigmath;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.ValueLayout;

/**
 * FFM function descriptors for the native bigmath library API.
 * <p>
 * Package-private utility holding pre-built {@link FunctionDescriptor}
 * and {@link MemoryLayout} constants used by {@link BigmathFFM#downcall}.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class FunctionDescriptors {

	// --- Native ABI v2 ---

	static final FunctionDescriptor ABI_VERSION = FunctionDescriptor.of(ValueLayout.JAVA_INT);
	static final FunctionDescriptor ABI_STRING = FunctionDescriptor.of(ValueLayout.ADDRESS);
	static final FunctionDescriptor ABI_CAPABILITIES = FunctionDescriptor.of(ValueLayout.JAVA_LONG);

	static final FunctionDescriptor HANDLE_FROM_LONG = FunctionDescriptor.of(
			ValueLayout.ADDRESS, ValueLayout.JAVA_LONG
	);
	static final FunctionDescriptor HANDLE_FROM_DOUBLE_INT = FunctionDescriptor.of(
			ValueLayout.ADDRESS, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_INT
	);
	static final FunctionDescriptor HANDLE_FROM_ADDRESS_INT = FunctionDescriptor.of(
			ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT
	);
	static final FunctionDescriptor HANDLE_FROM_BYTES = FunctionDescriptor.of(
			ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG
	);
	static final FunctionDescriptor HANDLE_UNARY = FunctionDescriptor.of(
			ValueLayout.ADDRESS, ValueLayout.ADDRESS
	);
	static final FunctionDescriptor HANDLE_BINARY = FunctionDescriptor.of(
			ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS
	);
	static final FunctionDescriptor HANDLE_TERNARY = FunctionDescriptor.of(
			ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS
	);
	static final FunctionDescriptor HANDLE_ADDRESS_LONG = FunctionDescriptor.of(
			ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG
	);
	static final FunctionDescriptor HANDLE_MUTATE = FunctionDescriptor.of(
			ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS
	);
	static final FunctionDescriptor HANDLE_MUTATE_LONG = FunctionDescriptor.of(
			ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG
	);
	static final FunctionDescriptor HANDLE_MUTATE_DOUBLE = FunctionDescriptor.of(
			ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_DOUBLE
	);
	static final FunctionDescriptor HANDLE_MUTATE_ADDRESS_INT = FunctionDescriptor.of(
			ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT
	);
	static final FunctionDescriptor HANDLE_MUTATE_BINARY = FunctionDescriptor.of(
			ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS
	);
	static final FunctionDescriptor HANDLE_INT_UNARY = FunctionDescriptor.of(
			ValueLayout.JAVA_INT, ValueLayout.ADDRESS
	);
	static final FunctionDescriptor HANDLE_INT_BINARY = FunctionDescriptor.of(
			ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS
	);
	static final FunctionDescriptor HANDLE_INT_ADDRESS_INT = FunctionDescriptor.of(
			ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT
	);
	static final FunctionDescriptor HANDLE_LONG_UNARY = FunctionDescriptor.of(
			ValueLayout.JAVA_LONG, ValueLayout.ADDRESS
	);
	static final FunctionDescriptor HANDLE_DOUBLE_UNARY = FunctionDescriptor.of(
			ValueLayout.JAVA_DOUBLE, ValueLayout.ADDRESS
	);
	static final FunctionDescriptor HANDLE_STRING = FunctionDescriptor.of(
			ValueLayout.ADDRESS, ValueLayout.ADDRESS
	);
	static final FunctionDescriptor HANDLE_STRING_RADIX = FunctionDescriptor.of(
			ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT
	);
	static final FunctionDescriptor HANDLE_EXPORT_BYTES = FunctionDescriptor.of(
			ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG
	);
	static final FunctionDescriptor HANDLE_FREE = FunctionDescriptor.ofVoid(ValueLayout.ADDRESS);

	// --- BigInt ---

	/** {@code void bigint_from_long(void** out, long value)} */
	static final FunctionDescriptor BIGINT_FROM_LONG = FunctionDescriptor.ofVoid(
			ValueLayout.ADDRESS, ValueLayout.JAVA_LONG
	);

	/** {@code void bigint_from_string(void** out, const char* str, int radix)} */
	static final FunctionDescriptor BIGINT_FROM_STRING = FunctionDescriptor.ofVoid(
			ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT
	);

	/** {@code void bigint_init(void** out)} */
	static final FunctionDescriptor BIGINT_INIT = FunctionDescriptor.ofVoid(
			ValueLayout.ADDRESS
	);

	/** {@code void bigint_set(void* out, void* a)} */
	static final FunctionDescriptor BIGINT_SET = FunctionDescriptor.ofVoid(
			ValueLayout.ADDRESS, ValueLayout.ADDRESS
	);

	/** {@code void bigint_set_long(void* out, long value)} */
	static final FunctionDescriptor BIGINT_SET_LONG = FunctionDescriptor.ofVoid(
			ValueLayout.ADDRESS, ValueLayout.JAVA_LONG
	);

	/** {@code void bigint_set_string(void* out, const char* str, int radix)} */
	static final FunctionDescriptor BIGINT_SET_STRING = FunctionDescriptor.ofVoid(
			ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT
	);

	/** {@code void bigint_op(void** out, void* a, void* b)} — add/sub/mul/div/mod/gcd/lcm/and/or/xor */
	static final FunctionDescriptor BIGINT_BINARY = FunctionDescriptor.ofVoid(
			ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS
	);

	/** {@code void bigint_op(void** out, void* a, void* b, void* c)} — powm */
	static final FunctionDescriptor BIGINT_TERNARY = FunctionDescriptor.ofVoid(
			ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS
	);

	/** {@code void bigint_op_into(void* out, void* a, void* b)} — add/mul/div */
	static final FunctionDescriptor BIGINT_BINARY_INTO = FunctionDescriptor.ofVoid(
			ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS
	);

	/** {@code void bigint_op_into(void* out, void* a)} — sqrt */
	static final FunctionDescriptor BIGINT_UNARY_INTO = FunctionDescriptor.ofVoid(
			ValueLayout.ADDRESS, ValueLayout.ADDRESS
	);

	/** {@code void bigint_op(void** out, void* a)} — neg/abs/sqrt/next_prime */
	static final FunctionDescriptor BIGINT_UNARY = FunctionDescriptor.ofVoid(
			ValueLayout.ADDRESS, ValueLayout.ADDRESS
	);

	/** {@code void bigint_pow(void** out, void* a, long exp)} — pow/shl/shr */
	static final FunctionDescriptor BIGINT_POW = FunctionDescriptor.ofVoid(
			ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG
	);

	/** {@code int bigint_cmp(void* a, void* b)} */
	static final FunctionDescriptor BIGINT_CMP = FunctionDescriptor.of(
			ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS
	);

	/** {@code char* bigint_to_string(void* a, int radix)} */
	static final FunctionDescriptor BIGINT_TO_STRING = FunctionDescriptor.of(
			ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT
	);

	/** {@code void bigint_free(void* a)} */
	static final FunctionDescriptor BIGINT_FREE = FunctionDescriptor.ofVoid(
			ValueLayout.ADDRESS
	);

	/** {@code void bigint_free_string(char* s)} */
	static final FunctionDescriptor BIGINT_FREE_STRING = FunctionDescriptor.ofVoid(
			ValueLayout.ADDRESS
	);

	/** {@code int bigint_sign(void* a)} */
	static final FunctionDescriptor BIGINT_SIGN = FunctionDescriptor.of(
			ValueLayout.JAVA_INT, ValueLayout.ADDRESS
	);

	/** {@code long bigint_to_long(void* a)} */
	static final FunctionDescriptor BIGINT_TO_LONG = FunctionDescriptor.of(
			ValueLayout.JAVA_LONG, ValueLayout.ADDRESS
	);

	/** {@code double bigint_to_double(void* a)} */
	static final FunctionDescriptor BIGINT_TO_DOUBLE = FunctionDescriptor.of(
			ValueLayout.JAVA_DOUBLE, ValueLayout.ADDRESS
	);

	/** {@code int bigint_is_probably_prime(void* a, int certainty)} */
	static final FunctionDescriptor BIGINT_IS_PROBABLY_PRIME = FunctionDescriptor.of(
			ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT
	);

	// --- CUDA diagnostics ---

	/** {@code int bigmath_cuda_value(void)} */
	static final FunctionDescriptor CUDA_INT = FunctionDescriptor.of(
			ValueLayout.JAVA_INT
	);

	/** {@code const char* bigmath_cuda_string(void)} */
	static final FunctionDescriptor CUDA_STRING = FunctionDescriptor.of(
			ValueLayout.ADDRESS
	);

	/** {@code uint64_t bigmath_product_cache_metric(void)} */
	static final FunctionDescriptor PRODUCT_CACHE_LONG = FunctionDescriptor.of(
			ValueLayout.JAVA_LONG
	);

	/** Runtime configuration scalar entry point. */
	static final FunctionDescriptor RUNTIME_CONFIGURE = FunctionDescriptor.of(
			ValueLayout.JAVA_INT,
			ValueLayout.JAVA_INT,
			ValueLayout.JAVA_LONG,
			ValueLayout.JAVA_DOUBLE,
			ValueLayout.JAVA_LONG,
			ValueLayout.JAVA_LONG,
			ValueLayout.JAVA_INT,
			ValueLayout.JAVA_INT
	);

	static final FunctionDescriptor RUNTIME_INT = FunctionDescriptor.of(ValueLayout.JAVA_INT);
	static final FunctionDescriptor RUNTIME_SNAPSHOT = FunctionDescriptor.of(
			ValueLayout.JAVA_INT,
			ValueLayout.ADDRESS
	);

	// --- BigDeci ---

	/** {@code void bigdecimal_from_double(void** out, double value, int precision)} */
	static final FunctionDescriptor BIGDECIMAL_FROM_DOUBLE = FunctionDescriptor.ofVoid(
			ValueLayout.ADDRESS, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_INT
	);

	/** {@code void bigdecimal_from_string(void** out, const char* str, int precision)} */
	static final FunctionDescriptor BIGDECIMAL_FROM_STRING = FunctionDescriptor.ofVoid(
			ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT
	);

	/** {@code void bigdecimal_from_bigint(void** out, void* value, int precision)} */
	static final FunctionDescriptor BIGDECIMAL_FROM_BIGINT = FunctionDescriptor.ofVoid(
			ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT
	);

	/** {@code void bigdecimal_init(void** out, int precision)} */
	static final FunctionDescriptor BIGDECIMAL_INIT = FunctionDescriptor.ofVoid(
			ValueLayout.ADDRESS, ValueLayout.JAVA_INT
	);

	/** {@code void bigdecimal_set(void* out, void* a)} */
	static final FunctionDescriptor BIGDECIMAL_SET = FunctionDescriptor.ofVoid(
			ValueLayout.ADDRESS, ValueLayout.ADDRESS
	);

	/** {@code void bigdecimal_set_double(void* out, double value)} */
	static final FunctionDescriptor BIGDECIMAL_SET_DOUBLE = FunctionDescriptor.ofVoid(
			ValueLayout.ADDRESS, ValueLayout.JAVA_DOUBLE
	);

	/** {@code void bigdecimal_set_string(void* out, const char* str, int precision)} */
	static final FunctionDescriptor BIGDECIMAL_SET_STRING = FunctionDescriptor.ofVoid(
			ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT
	);

	/** {@code void bigdecimal_op(void** out, void* a, void* b)} — add/sub/mul/div/pow */
	static final FunctionDescriptor BIGDECIMAL_BINARY = FunctionDescriptor.ofVoid(
			ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS
	);

	/** {@code void bigdecimal_pow_long(void** out, void* a, long exp)} */
	static final FunctionDescriptor BIGDECIMAL_POW_LONG = FunctionDescriptor.ofVoid(
			ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG
	);

	/** {@code void bigdecimal_op_into(void* out, void* a, void* b)} — add/mul/div */
	static final FunctionDescriptor BIGDECIMAL_BINARY_INTO = FunctionDescriptor.ofVoid(
			ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS
	);

	/** {@code void bigdecimal_op_into(void* out, void* a)} — sqrt */
	static final FunctionDescriptor BIGDECIMAL_UNARY_INTO = FunctionDescriptor.ofVoid(
			ValueLayout.ADDRESS, ValueLayout.ADDRESS
	);

	/** {@code void bigdecimal_op(void** out, void* a)} — neg/abs/sqrt/log/exp/sin/cos/tan/ceil/floor/round */
	static final FunctionDescriptor BIGDECIMAL_UNARY = FunctionDescriptor.ofVoid(
			ValueLayout.ADDRESS, ValueLayout.ADDRESS
	);

	/** {@code double bigdecimal_to_double(void* a)} */
	static final FunctionDescriptor BIGDECIMAL_TO_DOUBLE = FunctionDescriptor.of(
			ValueLayout.JAVA_DOUBLE, ValueLayout.ADDRESS
	);

	/** {@code char* bigdecimal_to_string(void* a)} */
	static final FunctionDescriptor BIGDECIMAL_TO_STRING = FunctionDescriptor.of(
			ValueLayout.ADDRESS, ValueLayout.ADDRESS
	);

	/** {@code void bigdecimal_free(void* a)} */
	static final FunctionDescriptor BIGDECIMAL_FREE = FunctionDescriptor.ofVoid(
			ValueLayout.ADDRESS
	);

	/** {@code void bigdecimal_free_string(char* s)} */
	static final FunctionDescriptor BIGDECIMAL_FREE_STRING = FunctionDescriptor.ofVoid(
			ValueLayout.ADDRESS
	);

	/** {@code int bigdecimal_cmp(void* a, void* b)} */
	static final FunctionDescriptor BIGDECIMAL_CMP = FunctionDescriptor.of(
			ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS
	);

	// --- Int128 ---

	/** 16-byte struct: {@code { int64_t lo; int64_t hi; }} */
	static final MemoryLayout INT128 = MemoryLayout.structLayout(
			ValueLayout.JAVA_LONG.withName("lo"),
			ValueLayout.JAVA_LONG.withName("hi")
	);

	/** {@code void int128_from_i64(int128_t* out, int64_t value)} */
	static final FunctionDescriptor INT128_FROM_I64 = FunctionDescriptor.ofVoid(
			ValueLayout.ADDRESS, ValueLayout.JAVA_LONG
	);

	/** {@code void int128_op(int128_t* out, int128_t* a, int128_t* b)} — add/sub/mul/div/mod */
	static final FunctionDescriptor INT128_BINARY = FunctionDescriptor.ofVoid(
			ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS
	);

	/** {@code void int128_op(int128_t* out, int128_t* a)} — neg/abs */
	static final FunctionDescriptor INT128_UNARY = FunctionDescriptor.ofVoid(
			ValueLayout.ADDRESS, ValueLayout.ADDRESS
	);

	/** {@code int int128_cmp(int128_t* a, int128_t* b)} */
	static final FunctionDescriptor INT128_CMP = FunctionDescriptor.of(
			ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS
	);

	/** {@code int int128_sign(int128_t* a)} */
	static final FunctionDescriptor INT128_SIGN = FunctionDescriptor.of(
			ValueLayout.JAVA_INT, ValueLayout.ADDRESS
	);

	/** {@code char* int128_to_string(int128_t* a, int radix)} */
	static final FunctionDescriptor INT128_TO_STRING = FunctionDescriptor.of(
			ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT
	);

	/** {@code void int128_free_string(char* s)} */
	static final FunctionDescriptor INT128_FREE_STRING = FunctionDescriptor.ofVoid(
			ValueLayout.ADDRESS
	);
}
