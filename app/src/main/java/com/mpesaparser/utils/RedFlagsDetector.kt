package com.mpesaparser.utils

import com.mpesaparser.domain.model.AccountingInsights

data class LoanRedFlags(
    val flags: List<String>,
    val severity: String // Low, Medium, High
)

class RedFlagsDetector {

    fun detectLoanRedFlags(insights: AccountingInsights): LoanRedFlags {
        val flags = mutableListOf<String>()

        // High debt-to-income ratio (assuming DTI > 0.43)
        if (insights.chargeToOutflowRatio + insights.loanToOutflowRatio > 0.43) {
            flags.add("High debt obligations relative to outflows")
        }

        // Frequent low balance days
        if (insights.lowBalanceDayRatio > 0.35) {
            flags.add("Frequent low-balance days indicate liquidity issues")
        }

        // Negative balance occurrences
        if (insights.negativeBalanceCount > 5) {
            flags.add("Multiple negative balance events")
        }

        // Low income stability
        if (insights.incomeStability < 0.4) {
            flags.add("Unstable income patterns")
        }

        // High expense volatility
        if (insights.expenseStability < 0.4) {
            flags.add("Volatile expense patterns")
        }

        // Low savings rate
        if (insights.savingsRate < 0.0) {
            flags.add("Negative savings rate")
        }

        // High counterparty concentration
        if (insights.topCounterpartyShare > 0.45) {
            flags.add("High dependency on single counterparty")
        }

        // Failed transactions
        if (insights.failedOrReversedCount > 10) {
            flags.add("High number of failed or reversed transactions")
        }

        // Low completion rate
        if (insights.completionRate < 0.95) {
            flags.add("Low transaction completion rate")
        }

        val severity = when {
            flags.size >= 5 -> "High"
            flags.size >= 3 -> "Medium"
            flags.isNotEmpty() -> "Low"
            else -> "None"
        }

        return LoanRedFlags(flags, severity)
    }
}
