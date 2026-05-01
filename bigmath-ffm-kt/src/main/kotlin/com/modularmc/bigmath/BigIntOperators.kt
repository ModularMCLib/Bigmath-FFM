package com.modularmc.bigmath

/** Arithmetic addition operator. */
operator fun BigInt.plus(other: BigInt): BigInt = add(other)
/** Arithmetic subtraction operator. */
operator fun BigInt.minus(other: BigInt): BigInt = subtract(other)
/** Arithmetic multiplication operator. */
operator fun BigInt.times(other: BigInt): BigInt = multiply(other)
/** Arithmetic division operator (integer division, truncating toward zero). */
operator fun BigInt.div(other: BigInt): BigInt = divide(other)
/** Remainder (modulus) operator. */
operator fun BigInt.rem(other: BigInt): BigInt = mod(other)
/** Unary negation operator. */
operator fun BigInt.unaryMinus(): BigInt = negate()
/** Unary plus operator (identity). */
operator fun BigInt.unaryPlus(): BigInt = this
/** Increment operator ({@code ++x}). */
operator fun BigInt.inc(): BigInt = add(BigInt.ONE)
/** Decrement operator ({@code --x}). */
operator fun BigInt.dec(): BigInt = subtract(BigInt.ONE)
/** Left bitwise shift operator ({@code shl}). */
infix fun BigInt.shl(bits: Int): BigInt = shiftLeft(bits.toLong())
/** Right bitwise shift operator ({@code shr}). */
infix fun BigInt.shr(bits: Int): BigInt = shiftRight(bits.toLong())
/** Range-to operator ({@code ..}) producing a closed range. */
operator fun BigInt.rangeTo(other: BigInt): ClosedRange<BigInt> {
    val self = this
    return object : ClosedRange<BigInt> {
        override val start: BigInt get() = self
        override val endInclusive: BigInt get() = other
    }
}
