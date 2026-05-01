package com.modularmc.bigmath.ffm

operator fun BigDecimal.plus(other: BigDecimal): BigDecimal = add(other)
operator fun BigDecimal.minus(other: BigDecimal): BigDecimal = subtract(other)
operator fun BigDecimal.times(other: BigDecimal): BigDecimal = multiply(other)
operator fun BigDecimal.div(other: BigDecimal): BigDecimal = divide(other)
operator fun BigDecimal.unaryMinus(): BigDecimal = negate()
operator fun BigDecimal.unaryPlus(): BigDecimal = this
operator fun BigDecimal.inc(): BigDecimal = add(BigDecimal.ONE)
operator fun BigDecimal.dec(): BigDecimal = subtract(BigDecimal.ONE)
operator fun BigDecimal.rangeTo(other: BigDecimal): ClosedRange<BigDecimal> {
    val self = this
    return object : ClosedRange<BigDecimal> {
        override val start: BigDecimal get() = self
        override val endInclusive: BigDecimal get() = other
    }
}
