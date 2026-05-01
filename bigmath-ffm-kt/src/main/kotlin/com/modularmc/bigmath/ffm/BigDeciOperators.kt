package com.modularmc.bigmath.ffm

operator fun BigDeci.plus(other: BigDeci): BigDeci = add(other)
operator fun BigDeci.minus(other: BigDeci): BigDeci = subtract(other)
operator fun BigDeci.times(other: BigDeci): BigDeci = multiply(other)
operator fun BigDeci.div(other: BigDeci): BigDeci = divide(other)
operator fun BigDeci.unaryMinus(): BigDeci = negate()
operator fun BigDeci.unaryPlus(): BigDeci = this
operator fun BigDeci.inc(): BigDeci = add(BigDeci.ONE)
operator fun BigDeci.dec(): BigDeci = subtract(BigDeci.ONE)
operator fun BigDeci.rangeTo(other: BigDeci): ClosedRange<BigDeci> {
    val self = this
    return object : ClosedRange<BigDeci> {
        override val start: BigDeci get() = self
        override val endInclusive: BigDeci get() = other
    }
}
