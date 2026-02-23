package com.mpesaparser.domain.model

data class ScoringConfig(
    val highValueThreshold: Double = 5000.0,
    val cashflowWeight: Int = 22,
    val liquidityWeight: Int = 20,
    val reliabilityWeight: Int = 15,
    val incomeStabilityWeight: Int = 10,
    val expenseStabilityWeight: Int = 8,
    val obligationWeight: Int = 10,
    val diversificationWeight: Int = 7,
    val highValueBehaviorWeight: Int = 8
) {
    val totalWeight: Int
        get() = cashflowWeight +
            liquidityWeight +
            reliabilityWeight +
            incomeStabilityWeight +
            expenseStabilityWeight +
            obligationWeight +
            diversificationWeight +
            highValueBehaviorWeight

    fun normalized(): ScoringConfig {
        val weights = listOf(
            cashflowWeight,
            liquidityWeight,
            reliabilityWeight,
            incomeStabilityWeight,
            expenseStabilityWeight,
            obligationWeight,
            diversificationWeight,
            highValueBehaviorWeight
        ).map { it.coerceAtLeast(0) }
        val sum = weights.sum().coerceAtLeast(1)
        val normalized = weights.map { ((it.toDouble() / sum.toDouble()) * 100.0).toInt().coerceAtLeast(1) }.toMutableList()
        val diff = 100 - normalized.sum()
        normalized[0] = (normalized[0] + diff).coerceAtLeast(1)
        return copy(
            cashflowWeight = normalized[0],
            liquidityWeight = normalized[1],
            reliabilityWeight = normalized[2],
            incomeStabilityWeight = normalized[3],
            expenseStabilityWeight = normalized[4],
            obligationWeight = normalized[5],
            diversificationWeight = normalized[6],
            highValueBehaviorWeight = normalized[7]
        )
    }
}
