package com.modularmc.bigmath.ffm

/** Arithmetic addition operator. */
operator fun BigDeci.plus(other: BigDeci): BigDeci = add(other)
/** Arithmetic subtraction operator. */
operator fun BigDeci.minus(other: BigDeci): BigDeci = subtract(other)
/** Arithmetic multiplication operator. */
operator fun BigDeci.times(other: BigDeci): BigDeci = multiply(other)
/** Arithmetic division operator. */
operator fun BigDeci.div(other: BigDeci): BigDeci = divide(other)
/** Unary negation operator. */
operator fun BigDeci.unaryMinus(): BigDeci = negate()
/** Unary plus operator (identity). */
operator fun BigDeci.unaryPlus(): BigDeci = this
/** Increment operator ({@code ++x}). */
operator fun BigDeci.inc(): BigDeci = add(BigDeci.ONE)
/** Decrement operator ({@code --x}). */
operator fun BigDeci.dec(): BigDeci = subtract(BigDeci.ONE)
/** Range-to operator ({@code ..}) producing a closed range. */
operator fun BigDeci.rangeTo(other: BigDeci): ClosedRange<BigDeci> {
    val self = this
    return object : ClosedRange<BigDeci> {
        override val start: BigDeci get() = self
        override val endInclusive: BigDeci get() = other
    }
}
