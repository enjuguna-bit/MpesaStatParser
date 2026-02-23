package com.mpesaparser.utils

import com.mpesaparser.domain.model.ParseDiagnostics
import com.mpesaparser.domain.model.ScoringConfig

data class RiskSignals(
    val strengths: List<String>,
    val riskSignals: List<String>
)

class RiskSignalAnalyzer {

    fun analyzeRisks(
        inflowOutflowRatio: Double,
        savingsRate: Double,
        netCashflow: Double,
        minimumBalance: Double,
        lowBalanceDayRatio: Double,
        completionRate: Double,
        obligationBurdenRatio: Double,
        debitPressureRatio: Double,
        incomeStability: Double,
        expenseStability: Double,
        topCounterpartyShare: Double,
        parseRate: Float
    ): RiskSignals {
        val strengths = buildStrengths(
            inflowOutflowRatio, savingsRate, netCashflow, completionRate,
            minimumBalance, lowBalanceDayRatio, obligationBurdenRatio, topCounterpartyShare, incomeStability
        )
        val risks = buildRiskSignals(
            inflowOutflowRatio, savingsRate, minimumBalance, lowBalanceDayRatio, completionRate,
            obligationBurdenRatio, debitPressureRatio, incomeStability, expenseStability, topCounterpartyShare, parseRate
        )
        return RiskSignals(strengths, risks)
    }

    private fun buildStrengths(
        inflowOutflowRatio: Double,
        savingsRate: Double,
        netCashflow: Double,
        completionRate: Double,
        minimumBalance: Double,
        lowBalanceDayRatio: Double,
        obligationBurdenRatio: Double,
        topCounterpartyShare: Double,
        incomeStability: Double
    ): List<String> {
        val strengths = mutableListOf<String>()
        if (inflowOutflowRatio >= 1.10) {
            strengths.add("Inflow-to-outflow ratio is strong, indicating positive coverage of obligations")
        }
        if (savingsRate >= 0.12) {
            strengths.add("Healthy savings rate from transaction inflows")
        }
        if (netCashflow > 0.0) {
            strengths.add("Positive net cashflow across the statement period")
        }
        if (completionRate >= 0.97) {
            strengths.add("Very high completion rate with minimal failed or reversed transactions")
        }
        if (minimumBalance >= 500.0 && lowBalanceDayRatio <= 0.20) {
            strengths.add("Stable liquidity with low exposure to low-balance days")
        }
        if (obligationBurdenRatio <= 0.15) {
            strengths.add("Charge and loan burden remains controlled")
        }
        if (topCounterpartyShare <= 0.35) {
            strengths.add("Transaction movement is reasonably diversified across counterparties")
        }
        if (incomeStability >= 0.65) {
            strengths.add("Income pattern is stable across months")
        }
        return strengths.distinct().take(6)
    }

    private fun buildRiskSignals(
        inflowOutflowRatio: Double,
        savingsRate: Double,
        minimumBalance: Double,
        lowBalanceDayRatio: Double,
        completionRate: Double,
        obligationBurdenRatio: Double,
        debitPressureRatio: Double,
        incomeStability: Double,
        expenseStability: Double,
        topCounterpartyShare: Double,
        parseRate: Float
    ): List<String> {
        val risks = mutableListOf<String>()
        if (inflowOutflowRatio < 0.95) {
            risks.add("Inflow-to-outflow ratio is below sustainable range")
        }
        if (savingsRate < 0.0) {
            risks.add("Negative savings rate: outflows exceed inflows")
        }
        if (minimumBalance < 500.0) {
            risks.add("Low liquidity buffer: minimum running balance dropped below KES 500")
        }
        if (lowBalanceDayRatio >= 0.35) {
            risks.add("Frequent low-balance days increase payment-failure risk")
        }
        if (completionRate < 0.95) {
            risks.add("Elevated failed or reversed transaction rate")
        }
        if (obligationBurdenRatio >= 0.25) {
            risks.add("Charge and loan burden is high relative to total outflows")
        }
        if (debitPressureRatio >= 0.50) {
            risks.add("High-value debits consume at least half of cash outflows")
        }
        if (incomeStability < 0.40) {
            risks.add("Monthly inflows are volatile")
        }
        if (expenseStability < 0.40) {
            risks.add("Monthly outflows are volatile")
        }
        if (topCounterpartyShare >= 0.45) {
            risks.add("Counterparty concentration is high, increasing dependency risk")
        }
        if (parseRate < 0.92f) {
            risks.add("Parse quality below 92%; verify source statement for completeness")
        }
        return risks.distinct().take(6)
    }
}
