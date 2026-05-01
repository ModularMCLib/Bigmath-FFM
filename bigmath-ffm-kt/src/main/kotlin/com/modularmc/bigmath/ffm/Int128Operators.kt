package com.modularmc.bigmath.ffm

/** Arithmetic addition operator. */
operator fun Int128.plus(other: Int128): Int128 = add(other)
/** Arithmetic subtraction operator. */
operator fun Int128.minus(other: Int128): Int128 = subtract(other)
/** Arithmetic multiplication operator. */
operator fun Int128.times(other: Int128): Int128 = multiply(other)
/** Arithmetic division operator (truncating toward zero). */
operator fun Int128.div(other: Int128): Int128 = divide(other)
/** Remainder (modulus) operator. */
operator fun Int128.rem(other: Int128): Int128 = mod(other)
/** Unary negation operator. */
operator fun Int128.unaryMinus(): Int128 = negate()
/** Unary plus operator (identity). */
operator fun Int128.unaryPlus(): Int128 = this
/** Increment operator ({@code ++x}). */
operator fun Int128.inc(): Int128 = add(Int128.ONE)
/** Decrement operator ({@code --x}). */
operator fun Int128.dec(): Int128 = subtract(Int128.ONE)
/** Range-to operator ({@code ..}) producing a closed range. */
operator fun Int128.rangeTo(other: Int128): ClosedRange<Int128> {
    val self = this
    return object : ClosedRange<Int128> {
        override val start: Int128 get() = self
        override val endInclusive: Int128 get() = other
    }
}
