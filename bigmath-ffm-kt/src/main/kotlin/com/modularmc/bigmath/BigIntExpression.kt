package com.modularmc.bigmath

/**
 * Receiver for allocation-safe BigInt expressions.
 *
 * Every value produced by an operator is owned by the backing
 * [NativeCalculationScope]. The expression result is detached automatically by
 * [bigIntExpression]; all other intermediate values are closed.
 */
class BigIntExpressionScope internal constructor(
    private val scope: NativeCalculationScope,
) {
    operator fun BigInt.plus(other: BigInt): BigInt = scope.own(add(other))

    operator fun BigInt.minus(other: BigInt): BigInt = scope.own(subtract(other))

    operator fun BigInt.times(other: BigInt): BigInt = scope.own(multiply(other))

    operator fun BigInt.div(other: BigInt): BigInt = scope.own(divide(other))

    operator fun BigInt.rem(other: BigInt): BigInt = scope.own(mod(other))

    operator fun BigInt.unaryMinus(): BigInt = scope.own(negate())

    operator fun BigInt.unaryPlus(): BigInt = this

    operator fun BigInt.inc(): BigInt = scope.own(add(BigInt.ONE))

    operator fun BigInt.dec(): BigInt = scope.own(subtract(BigInt.ONE))

    infix fun BigInt.shl(bits: Int): BigInt = scope.own(shiftLeft(bits.toLong()))

    infix fun BigInt.shr(bits: Int): BigInt = scope.own(shiftRight(bits.toLong()))
}

/**
 * Evaluates a BigInt expression and closes every intermediate native value.
 * The returned value is detached and must be closed by the caller.
 */
fun bigIntExpression(expression: BigIntExpressionScope.() -> BigInt): BigInt =
    NativeCalculationScope.open().use { scope ->
        scope.detach(BigIntExpressionScope(scope).expression())
    }
