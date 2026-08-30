package com.modularmc.bigmath

/**
 * Receiver for allocation-safe BigDeci expressions.
 *
 * Every value produced by an operator is owned by the backing
 * [NativeCalculationScope]. The expression result is detached automatically by
 * [bigDeciExpression]; all other intermediate values are closed.
 */
class BigDeciExpressionScope internal constructor(
    private val scope: NativeCalculationScope,
) {
    operator fun BigDeci.plus(other: BigDeci): BigDeci = scope.own(add(other))

    operator fun BigDeci.minus(other: BigDeci): BigDeci = scope.own(subtract(other))

    operator fun BigDeci.times(other: BigDeci): BigDeci = scope.own(multiply(other))

    operator fun BigDeci.div(other: BigDeci): BigDeci = scope.own(divide(other))

    operator fun BigDeci.unaryMinus(): BigDeci = scope.own(negate())

    operator fun BigDeci.unaryPlus(): BigDeci = this

    operator fun BigDeci.inc(): BigDeci = scope.own(add(BigDeci.ONE))

    operator fun BigDeci.dec(): BigDeci = scope.own(subtract(BigDeci.ONE))
}

/**
 * Evaluates a BigDeci expression and closes every intermediate native value.
 * The returned value is detached and must be closed by the caller.
 */
fun bigDeciExpression(expression: BigDeciExpressionScope.() -> BigDeci): BigDeci =
    NativeCalculationScope.open().use { scope ->
        scope.detach(BigDeciExpressionScope(scope).expression())
    }
