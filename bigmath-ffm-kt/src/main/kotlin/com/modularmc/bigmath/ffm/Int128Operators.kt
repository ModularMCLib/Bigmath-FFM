package com.modularmc.bigmath.ffm

operator fun Int128.plus(other: Int128): Int128 = add(other)
operator fun Int128.minus(other: Int128): Int128 = subtract(other)
operator fun Int128.times(other: Int128): Int128 = multiply(other)
operator fun Int128.div(other: Int128): Int128 = divide(other)
operator fun Int128.rem(other: Int128): Int128 = mod(other)
operator fun Int128.unaryMinus(): Int128 = negate()
operator fun Int128.unaryPlus(): Int128 = this
operator fun Int128.inc(): Int128 = add(Int128.ONE)
operator fun Int128.dec(): Int128 = subtract(Int128.ONE)
operator fun Int128.rangeTo(other: Int128): ClosedRange<Int128> {
    val self = this
    return object : ClosedRange<Int128> {
        override val start: Int128 get() = self
        override val endInclusive: Int128 get() = other
    }
}
