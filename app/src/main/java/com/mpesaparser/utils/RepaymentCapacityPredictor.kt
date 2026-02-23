package com.mpesaparser.utils

import com.mpesaparser.domain.model.AccountingInsights
import kotlin.math.pow

data class RepaymentCapacityResult(
    val canRepay: Boolean,
    val monthlyPayment: Double,
    val monthlySurplus: Double,
    val coverageRatio: Double,
    val reasons: List<String>
)

class RepaymentCapacityPredictor {

    fun predictCapacity(
        insights: AccountingInsights,
        loanAmount: Double,
        termMonths: Int,
        interestRate: Double = 0.12, // 12% annual
        periodMonths: Int = insights.activeMonths.coerceAtLeast(1)
    ): RepaymentCapacityResult {
        val reasons = mutableListOf<String>()

        // Calculate monthly payment using loan formula
        val monthlyRate = interestRate / 12
        val monthlyPayment = if (monthlyRate > 0) {
            (loanAmount * monthlyRate * (1 + monthlyRate).pow(termMonths)) / ((1 + monthlyRate).pow(termMonths) - 1)
        } else {
            loanAmount / termMonths
        }

        // Estimate monthly surplus from data
        val totalSurplus = insights.totalInflow - insights.totalOutflow
        val monthlySurplus = totalSurplus / periodMonths

        val coverageRatio = if (monthlyPayment > 0) monthlySurplus / monthlyPayment else 0.0

        val canRepay = coverageRatio >= 1.2 // At least 20% buffer

        if (canRepay) {
            reasons.add("Monthly surplus (${String.format("%.2f", monthlySurplus)}) covers payment (${String.format("%.2f", monthlyPayment)}) with ${String.format("%.1f", coverageRatio * 100)}% coverage")
        } else {
            reasons.add("Insufficient surplus: monthly payment (${String.format("%.2f", monthlyPayment)}) exceeds surplus (${String.format("%.2f", monthlySurplus)})")
        }

        // Additional checks
        if (insights.netCashflow < 0) {
            reasons.add("Negative net cashflow indicates repayment risk")
            // Override if surplus is negative
        }

        if (insights.lowBalanceDayRatio > 0.3) {
            reasons.add("High low-balance days suggest liquidity issues")
        }

        return RepaymentCapacityResult(canRepay, monthlyPayment, monthlySurplus, coverageRatio, reasons)
    }
}
