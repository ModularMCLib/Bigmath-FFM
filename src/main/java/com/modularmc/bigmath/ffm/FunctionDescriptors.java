package com.modularmc.bigmath.ffm;

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

	// --- BigInt ---

	/** {@code void bigint_from_long(void** out, long value)} */
	static final FunctionDescriptor BIGINT_FROM_LONG = FunctionDescriptor.ofVoid(
			ValueLayout.ADDRESS, ValueLayout.JAVA_LONG
	);

	/** {@code void bigint_from_string(void** out, const char* str, int radix)} */
	static final FunctionDescriptor BIGINT_FROM_STRING = FunctionDescriptor.ofVoid(
			ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT
	);

	/** {@code void bigint_op(void** out, void* a, void* b)} — add/sub/mul/div/mod/gcd/lcm/and/or/xor */
	static final FunctionDescriptor BIGINT_BINARY = FunctionDescriptor.ofVoid(
			ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS
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

	/** {@code char* bigint_format(void* a, int group_size, const char* group_sep)} */
	static final FunctionDescriptor BIGINT_FORMAT = FunctionDescriptor.of(
			ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS
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

	// --- BigDeci ---

	/** {@code void bigdecimal_from_double(void** out, double value, int precision)} */
	static final FunctionDescriptor BIGDECIMAL_FROM_DOUBLE = FunctionDescriptor.ofVoid(
			ValueLayout.ADDRESS, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_INT
	);

	/** {@code void bigdecimal_from_string(void** out, const char* str, int precision)} */
	static final FunctionDescriptor BIGDECIMAL_FROM_STRING = FunctionDescriptor.ofVoid(
			ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT
	);

	/** {@code void bigdecimal_op(void** out, void* a, void* b)} — add/sub/mul/div/pow */
	static final FunctionDescriptor BIGDECIMAL_BINARY = FunctionDescriptor.ofVoid(
			ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS
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

	/** {@code char* bigdecimal_format(void* a, int scale, int group_size, const char* group_sep)} */
	static final FunctionDescriptor BIGDECIMAL_FORMAT = FunctionDescriptor.of(
			ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS
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

	/** {@code void int128_from_string(int128_t* out, const char* str, int radix)} */
	static final FunctionDescriptor INT128_FROM_STRING = FunctionDescriptor.ofVoid(
			ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT
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

	/** {@code char* int128_format(int128_t* a, int group_size, const char* group_sep)} */
	static final FunctionDescriptor INT128_FORMAT = FunctionDescriptor.of(
			ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS
	);

	/** {@code void int128_free_string(char* s)} */
	static final FunctionDescriptor INT128_FREE_STRING = FunctionDescriptor.ofVoid(
			ValueLayout.ADDRESS
	);
}
