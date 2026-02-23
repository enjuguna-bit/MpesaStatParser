package com.mpesaparser.utils

import com.mpesaparser.domain.model.AccountingInsights

data class LoanEligibilityResult(
    val isEligible: Boolean,
    val reasons: List<String>,
    val recommendedAmount: Double? = null
)

data class EligibilityCriteria(
    val minScore: Int = 60,
    val minInflowOutflowRatio: Double = 1.0,
    val maxLowBalanceDayRatio: Double = 0.2,
    val minCompletionRate: Double = 0.95,
    val maxObligationBurdenRatio: Double = 0.25
)

class LoanEligibilityChecker {

    fun checkEligibility(
        insights: AccountingInsights,
        criteria: EligibilityCriteria = EligibilityCriteria()
    ): LoanEligibilityResult {
        val reasons = mutableListOf<String>()

        // Check credibility score
        if (insights.credibilityScore < criteria.minScore) {
            reasons.add("Credibility score (${insights.credibilityScore}) below minimum (${criteria.minScore})")
        } else {
            reasons.add("Credibility score (${insights.credibilityScore}) meets requirement")
        }

        // Check inflow-outflow ratio
        if (insights.inflowOutflowRatio < criteria.minInflowOutflowRatio) {
            reasons.add("Inflow-outflow ratio (${String.format("%.2f", insights.inflowOutflowRatio)}) below minimum (${criteria.minInflowOutflowRatio})")
        } else {
            reasons.add("Inflow-outflow ratio (${String.format("%.2f", insights.inflowOutflowRatio)}) is healthy")
        }

        // Check low balance days
        if (insights.lowBalanceDayRatio > criteria.maxLowBalanceDayRatio) {
            reasons.add("Low balance day ratio (${String.format("%.2f", insights.lowBalanceDayRatio)}) exceeds maximum (${criteria.maxLowBalanceDayRatio})")
        } else {
            reasons.add("Low balance day ratio (${String.format("%.2f", insights.lowBalanceDayRatio)}) is acceptable")
        }

        // Check completion rate
        if (insights.completionRate < criteria.minCompletionRate) {
            reasons.add("Completion rate (${String.format("%.2f", insights.completionRate)}) below minimum (${criteria.minCompletionRate})")
        } else {
            reasons.add("Completion rate (${String.format("%.2f", insights.completionRate)}) is high")
        }

        // Check obligation burden
        if (insights.obligationBurdenRatio > criteria.maxObligationBurdenRatio) {
            reasons.add("Obligation burden ratio (${String.format("%.2f", insights.obligationBurdenRatio)}) exceeds maximum (${criteria.maxObligationBurdenRatio})")
        } else {
            reasons.add("Obligation burden ratio (${String.format("%.2f", insights.obligationBurdenRatio)}) is manageable")
        }

        // Check for red flags
        if (insights.riskSignals.any { it.contains("Negative savings") || it.contains("Low liquidity") }) {
            reasons.add("Risk signals detected: ${insights.riskSignals.take(2).joinToString(", ")}")
        } else {
            reasons.add("No major risk signals detected")
        }

        val isEligible = reasons.none { it.contains("below") || it.contains("exceeds") || it.contains("detected") && it.contains("Risk") }

        // Simple recommendation based on net cashflow
        val recommendedAmount = if (isEligible && insights.netCashflow > 0) {
            (insights.netCashflow * 3).coerceAtMost(500000.0) // Up to 3 months surplus, max 500K
        } else null

        return LoanEligibilityResult(isEligible, reasons, recommendedAmount)
    }
}
