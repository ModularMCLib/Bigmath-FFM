package com.modularmc.bigmath.ffm;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.ValueLayout;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class FunctionDescriptors {

	static final FunctionDescriptor BIGINT_FROM_LONG = FunctionDescriptor.ofVoid(
			ValueLayout.ADDRESS, ValueLayout.JAVA_LONG
	);

	static final FunctionDescriptor BIGINT_FROM_STRING = FunctionDescriptor.ofVoid(
			ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT
	);

	static final FunctionDescriptor BIGINT_BINARY = FunctionDescriptor.ofVoid(
			ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS
	);

	static final FunctionDescriptor BIGINT_UNARY = FunctionDescriptor.ofVoid(
			ValueLayout.ADDRESS, ValueLayout.ADDRESS
	);

	static final FunctionDescriptor BIGINT_POW = FunctionDescriptor.ofVoid(
			ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG
	);

	static final FunctionDescriptor BIGINT_CMP = FunctionDescriptor.of(
			ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS
	);

	static final FunctionDescriptor BIGINT_TO_STRING = FunctionDescriptor.of(
			ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT
	);

	static final FunctionDescriptor BIGINT_FORMAT = FunctionDescriptor.of(
			ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS
	);

	static final FunctionDescriptor BIGINT_FREE = FunctionDescriptor.ofVoid(
			ValueLayout.ADDRESS
	);

	static final FunctionDescriptor BIGINT_FREE_STRING = FunctionDescriptor.ofVoid(
			ValueLayout.ADDRESS
	);

	static final FunctionDescriptor BIGINT_SIGN = FunctionDescriptor.of(
			ValueLayout.JAVA_INT, ValueLayout.ADDRESS
	);

	static final FunctionDescriptor BIGINT_IS_PROBABLY_PRIME = FunctionDescriptor.of(
			ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT
	);

	static final FunctionDescriptor BIGDECIMAL_FROM_DOUBLE = FunctionDescriptor.ofVoid(
			ValueLayout.ADDRESS, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_INT
	);

	static final FunctionDescriptor BIGDECIMAL_FROM_STRING = FunctionDescriptor.ofVoid(
			ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT
	);

	static final FunctionDescriptor BIGDECIMAL_BINARY = FunctionDescriptor.ofVoid(
			ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS
	);

	static final FunctionDescriptor BIGDECIMAL_UNARY = FunctionDescriptor.ofVoid(
			ValueLayout.ADDRESS, ValueLayout.ADDRESS
	);

	static final FunctionDescriptor BIGDECIMAL_TO_DOUBLE = FunctionDescriptor.of(
			ValueLayout.JAVA_DOUBLE, ValueLayout.ADDRESS
	);

	static final FunctionDescriptor BIGDECIMAL_TO_STRING = FunctionDescriptor.of(
			ValueLayout.ADDRESS, ValueLayout.ADDRESS
	);

	static final FunctionDescriptor BIGDECIMAL_FORMAT = FunctionDescriptor.of(
			ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS
	);

	static final FunctionDescriptor BIGDECIMAL_FREE = FunctionDescriptor.ofVoid(
			ValueLayout.ADDRESS
	);

	static final FunctionDescriptor BIGDECIMAL_FREE_STRING = FunctionDescriptor.ofVoid(
			ValueLayout.ADDRESS
	);

	static final FunctionDescriptor BIGDECIMAL_CMP = FunctionDescriptor.of(
			ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS
	);

	static final MemoryLayout INT128 = MemoryLayout.structLayout(
			ValueLayout.JAVA_LONG.withName("lo"),
			ValueLayout.JAVA_LONG.withName("hi")
	);

	static final FunctionDescriptor INT128_FROM_I64 = FunctionDescriptor.ofVoid(
			ValueLayout.ADDRESS, ValueLayout.JAVA_LONG
	);

	static final FunctionDescriptor INT128_FROM_STRING = FunctionDescriptor.ofVoid(
			ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT
	);

	static final FunctionDescriptor INT128_BINARY = FunctionDescriptor.ofVoid(
			ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS
	);

	static final FunctionDescriptor INT128_UNARY = FunctionDescriptor.ofVoid(
			ValueLayout.ADDRESS, ValueLayout.ADDRESS
	);

	static final FunctionDescriptor INT128_CMP = FunctionDescriptor.of(
			ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS
	);

	static final FunctionDescriptor INT128_SIGN = FunctionDescriptor.of(
			ValueLayout.JAVA_INT, ValueLayout.ADDRESS
	);

	static final FunctionDescriptor INT128_TO_STRING = FunctionDescriptor.of(
			ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT
	);

	static final FunctionDescriptor INT128_FORMAT = FunctionDescriptor.of(
			ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS
	);

	static final FunctionDescriptor INT128_FREE_STRING = FunctionDescriptor.ofVoid(
			ValueLayout.ADDRESS
	);
}
