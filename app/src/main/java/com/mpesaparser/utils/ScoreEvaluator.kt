package com.mpesaparser.utils

import com.mpesaparser.domain.model.ScoreComponent
import com.mpesaparser.domain.model.CredibilityBand
import com.mpesaparser.domain.model.ScoringConfig
import kotlin.math.roundToInt

data class ScoringResult(
    val scoreComponents: List<ScoreComponent>,
    val credibilityScore: Int,
    val credibilityBand: CredibilityBand
)

class ScoreEvaluator {

    fun evaluateScore(
        inflowOutflowRatio: Double,
        savingsRate: Double,
        averageBalance: Double,
        minimumBalance: Double,
        lowBalanceDayRatio: Double,
        completionRate: Double,
        parseRate: Double,
        incomeStability: Double,
        expenseStability: Double,
        obligationBurdenRatio: Double,
        top3CounterpartyShare: Double,
        highValueNet: Double,
        debitPressureRatio: Double,
        totalInflow: Double,
        config: ScoringConfig,
        transactions: Int,
        activeMonths: Int
    ): ScoringResult {
        val scoreComponents = buildScoreComponents(
            inflowOutflowRatio, savingsRate, averageBalance, minimumBalance, lowBalanceDayRatio,
            completionRate, parseRate, incomeStability, expenseStability, obligationBurdenRatio,
            top3CounterpartyShare, highValueNet, debitPressureRatio, totalInflow, config
        )
        val credibilityScore = scoreComponents.sumOf { it.points }.coerceIn(0, 100)
        val credibilityBand = credibilityBand(credibilityScore, transactions, activeMonths)
        return ScoringResult(scoreComponents, credibilityScore, credibilityBand)
    }

    private fun buildScoreComponents(
        inflowOutflowRatio: Double,
        savingsRate: Double,
        averageBalance: Double,
        minimumBalance: Double,
        lowBalanceDayRatio: Double,
        completionRate: Double,
        parseRate: Double,
        incomeStability: Double,
        expenseStability: Double,
        obligationBurdenRatio: Double,
        top3CounterpartyShare: Double,
        highValueNet: Double,
        debitPressureRatio: Double,
        totalInflow: Double,
        config: ScoringConfig
    ): List<ScoreComponent> {
        val cashflowNorm = (
            0.7 * scale(inflowOutflowRatio, min = 0.60, max = 1.60) +
            0.3 * scale(savingsRate, min = -0.25, max = 0.35)
        ).coerceIn(0.0, 1.0)
        val liquidityNorm = (
            0.4 * scale(averageBalance, min = 1000.0, max = 20000.0) +
            0.35 * scale(minimumBalance, min = -500.0, max = 5000.0) +
            0.25 * (1.0 - lowBalanceDayRatio).coerceIn(0.0, 1.0)
        ).coerceIn(0.0, 1.0)
        val reliabilityNorm = (
            0.8 * completionRate.coerceIn(0.0, 1.0) +
            0.2 * parseRate.coerceIn(0.0, 1.0)
        ).coerceIn(0.0, 1.0)
        val incomeNorm = incomeStability.coerceIn(0.0, 1.0)
        val expenseNorm = expenseStability.coerceIn(0.0, 1.0)
        val obligationNorm = (1.0 - scale(obligationBurdenRatio, min = 0.08, max = 0.45)).coerceIn(0.0, 1.0)
        val diversificationNorm = (1.0 - scale(top3CounterpartyShare, min = 0.35, max = 0.90)).coerceIn(0.0, 1.0)
        val highValueNetRatio = if (totalInflow > 0.0) highValueNet / totalInflow else 0.0
        val highValueNorm = (
            0.6 * scale(highValueNetRatio, min = -0.40, max = 0.30) +
            0.4 * (1.0 - scale(debitPressureRatio, min = 0.25, max = 0.80))
        ).coerceIn(0.0, 1.0)

        return listOf(
            scoreComponent("Cashflow Sustainability", config.cashflowWeight, cashflowNorm),
            scoreComponent("Liquidity Health", config.liquidityWeight, liquidityNorm),
            scoreComponent("Transaction Reliability", config.reliabilityWeight, reliabilityNorm),
            scoreComponent("Income Stability", config.incomeStabilityWeight, incomeNorm),
            scoreComponent("Expense Stability", config.expenseStabilityWeight, expenseNorm),
            scoreComponent("Obligation Burden", config.obligationWeight, obligationNorm),
            scoreComponent("Counterparty Diversification", config.diversificationWeight, diversificationNorm),
            scoreComponent("High-Value Behaviour", config.highValueBehaviorWeight, highValueNorm)
        )
    }

    private fun scoreComponent(name: String, maxPoints: Int, normalizedScore: Double): ScoreComponent {
        val points = (maxPoints * normalizedScore.coerceIn(0.0, 1.0)).roundToInt().coerceIn(0, maxPoints)
        return ScoreComponent(name = name, points = points, maxPoints = maxPoints)
    }

    private fun credibilityBand(score: Int, transactions: Int, activeMonths: Int): CredibilityBand {
        if (transactions < 10 || activeMonths == 0) {
            return CredibilityBand.INSUFFICIENT_DATA
        }
        return when {
            score >= 82 -> CredibilityBand.EXCELLENT
            score >= 68 -> CredibilityBand.GOOD
            score >= 54 -> CredibilityBand.MODERATE
            score >= 40 -> CredibilityBand.CAUTION
            else -> CredibilityBand.HIGH_RISK
        }
    }

    private fun scale(value: Double, min: Double, max: Double): Double {
        if (max <= min) return 0.0
        return ((value - min) / (max - min)).coerceIn(0.0, 1.0)
    }
}
