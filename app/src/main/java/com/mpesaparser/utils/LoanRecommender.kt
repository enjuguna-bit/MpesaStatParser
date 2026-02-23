package com.mpesaparser.utils

import com.mpesaparser.domain.model.AccountingInsights
import kotlin.math.pow

data class LoanRecommendation(
    val recommendedAmount: Double,
    val recommendedTerm: Int,
    val monthlyPayment: Double,
    val reasoning: List<String>
)

class LoanRecommender {

    fun recommendLoan(insights: AccountingInsights): LoanRecommendation {
        val reasoning = mutableListOf<String>()

        // Base on net cashflow surplus
        val periodMonths = insights.activeMonths.coerceAtLeast(1)
        val monthlySurplus = insights.netCashflow / periodMonths

        if (monthlySurplus <= 0) {
            return LoanRecommendation(0.0, 0, 0.0, listOf("No positive surplus for loan repayment"))
        }

        // Assume 30% of surplus for loan payment (conservative)
        val affordableMonthlyPayment = monthlySurplus * 0.3
        reasoning.add("Based on 30% of monthly surplus (KES ${String.format("%,.0f", monthlySurplus)})")

        // For a standard term, say 12 months, calculate amount
        val term = 12
        val interestRate = 0.12 // 12%
        val monthlyRate = interestRate / 12
        val maxAmount = affordableMonthlyPayment * ((1 + monthlyRate).pow(term) - 1) / (monthlyRate * (1 + monthlyRate).pow(term))

        val recommendedAmount = maxAmount.coerceAtMost(500000.0).coerceAtLeast(0.0) // Cap at 500K

        // Recalculate payment for recommended amount
        val monthlyPayment = if (monthlyRate > 0) {
            (recommendedAmount * monthlyRate * (1 + monthlyRate).pow(term)) / ((1 + monthlyRate).pow(term) - 1)
        } else {
            recommendedAmount / term
        }

        reasoning.add("Recommended amount: KES ${String.format("%,.0f", recommendedAmount)} for $term months")
        reasoning.add("Monthly payment: KES ${String.format("%,.0f", monthlyPayment)}")
        reasoning.add("Affordability check: ${String.format("%.1f", (monthlySurplus / monthlyPayment) * 100)}% of surplus")

        if (insights.credibilityScore < 60) {
            reasoning.add("WARNING: Low credibility score may require higher scrutiny")
        }

        return LoanRecommendation(recommendedAmount, term, monthlyPayment, reasoning)
    }
}
