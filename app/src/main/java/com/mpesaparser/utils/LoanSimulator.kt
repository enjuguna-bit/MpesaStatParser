package com.mpesaparser.utils

import com.mpesaparser.domain.model.AccountingInsights
import com.mpesaparser.domain.model.MpesaTransaction
import kotlin.math.abs
import kotlin.math.pow

data class LoanSimulationResult(
    val simulatedInsights: AccountingInsights,
    val loanPayment: Double,
    val impactSummary: List<String>
)

class LoanSimulator {

    fun simulateLoanImpact(
        originalInsights: AccountingInsights,
        originalTransactions: List<MpesaTransaction>,
        loanAmount: Double,
        termMonths: Int,
        interestRate: Double = 0.12
    ): LoanSimulationResult {
        // Calculate monthly loan payment
        val monthlyRate = interestRate / 12
        val loanPayment = if (monthlyRate > 0) {
            (loanAmount * monthlyRate * (1 + monthlyRate).pow(termMonths)) / ((1 + monthlyRate).pow(termMonths) - 1)
        } else {
            loanAmount / termMonths
        }

        // Simulate additional outflows (loan payments)
        val simulatedOutflow = originalInsights.totalOutflow + (loanPayment * (originalInsights.activeMonths.coerceAtLeast(1)))

        // Recalculate key metrics
        val simulatedNetCashflow = originalInsights.totalInflow - simulatedOutflow
        val simulatedSavingsRate = if (originalInsights.totalInflow > 0) simulatedNetCashflow / originalInsights.totalInflow else 0.0
        val simulatedInflowOutflowRatio = if (simulatedOutflow > 0) originalInsights.totalInflow / simulatedOutflow else 0.0

        // Create simulated insights (simplified, copying most values)
        val simulatedInsights = originalInsights.copy(
            totalOutflow = simulatedOutflow,
            netCashflow = simulatedNetCashflow,
            savingsRate = simulatedSavingsRate,
            inflowOutflowRatio = simulatedInflowOutflowRatio,
            // Could adjust other ratios, but keeping simple
            chargeToOutflowRatio = originalInsights.chargeToOutflowRatio * (originalInsights.totalOutflow / simulatedOutflow),
            loanToOutflowRatio = (originalInsights.loanToOutflowRatio * originalInsights.totalOutflow + loanPayment) / simulatedOutflow,
            obligationBurdenRatio = (originalInsights.obligationBurdenRatio * originalInsights.totalOutflow + loanPayment) / simulatedOutflow
        )

        // Impact summary
        val impactSummary = mutableListOf<String>()
        impactSummary.add("Monthly loan payment: KES ${String.format("%,.0f", loanPayment)}")
        impactSummary.add("New net cashflow: KES ${String.format("%,.0f", simulatedNetCashflow)} (was ${String.format("%,.0f", originalInsights.netCashflow)})")
        if (simulatedNetCashflow < 0) {
            impactSummary.add("WARNING: Negative cashflow after loan")
        }
        impactSummary.add("New savings rate: ${String.format("%.2f", simulatedSavingsRate)} (was ${String.format("%.2f", originalInsights.savingsRate)})")
        if (simulatedSavingsRate < 0) {
            impactSummary.add("WARNING: Negative savings rate")
        }

        return LoanSimulationResult(simulatedInsights, loanPayment, impactSummary)
    }
}
