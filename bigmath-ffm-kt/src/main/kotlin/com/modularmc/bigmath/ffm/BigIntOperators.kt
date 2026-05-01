package com.modularmc.bigmath.ffm

operator fun BigInt.plus(other: BigInt): BigInt = add(other)
operator fun BigInt.minus(other: BigInt): BigInt = subtract(other)
operator fun BigInt.times(other: BigInt): BigInt = multiply(other)
operator fun BigInt.div(other: BigInt): BigInt = divide(other)
operator fun BigInt.rem(other: BigInt): BigInt = mod(other)
operator fun BigInt.unaryMinus(): BigInt = negate()
operator fun BigInt.unaryPlus(): BigInt = this
operator fun BigInt.inc(): BigInt = add(BigInt.ONE)
operator fun BigInt.dec(): BigInt = subtract(BigInt.ONE)
infix fun BigInt.shl(bits: Int): BigInt = shiftLeft(bits.toLong())
infix fun BigInt.shr(bits: Int): BigInt = shiftRight(bits.toLong())
operator fun BigInt.rangeTo(other: BigInt): ClosedRange<BigInt> {
    val self = this
    return object : ClosedRange<BigInt> {
        override val start: BigInt get() = self
        override val endInclusive: BigInt get() = other
    }
}
