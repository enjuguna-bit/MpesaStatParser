package com.mpesaparser.domain.model

data class AccountingInsights(
    val periodStart: String = "",
    val periodEnd: String = "",
    val totalTransactions: Int = 0,
    val totalInflow: Double = 0.0,
    val totalOutflow: Double = 0.0,
    val netCashflow: Double = 0.0,
    val inflowOutflowRatio: Double = 0.0,
    val savingsRate: Double = 0.0,
    val averageCredit: Double = 0.0,
    val averageDebit: Double = 0.0,
    val averageMovement: Double = 0.0,
    val highValueThreshold: Double = 5000.0,
    val highValueCreditCount: Int = 0,
    val highValueDebitCount: Int = 0,
    val highValueCreditTotal: Double = 0.0,
    val highValueDebitTotal: Double = 0.0,
    val highValueNet: Double = 0.0,
    val chargeToOutflowRatio: Double = 0.0,
    val loanToOutflowRatio: Double = 0.0,
    val obligationBurdenRatio: Double = 0.0,
    val debitPressureRatio: Double = 0.0,
    val creditStrengthRatio: Double = 0.0,
    val averageBalance: Double = 0.0,
    val minimumBalance: Double = 0.0,
    val medianBalance: Double = 0.0,
    val balanceVolatility: Double = 0.0,
    val negativeBalanceCount: Int = 0,
    val lowBalanceDays: Int = 0,
    val lowBalanceDayRatio: Double = 0.0,
    val completionRate: Double = 0.0,
    val failedOrReversedCount: Int = 0,
    val activeDays: Int = 0,
    val activeMonths: Int = 0,
    val monthlyConsistency: Double = 0.0,
    val incomeStability: Double = 0.0,
    val expenseStability: Double = 0.0,
    val averageDailyTransactions: Double = 0.0,
    val maxSingleCredit: Double = 0.0,
    val maxSingleDebit: Double = 0.0,
    val topCounterparty: String = "N/A",
    val topCounterpartyShare: Double = 0.0,
    val top3CounterpartyShare: Double = 0.0,
    val counterpartyConcentrationIndex: Double = 0.0,
    val loanSection: TransactionSection = TransactionSection(name = "Loans"),
    val above5kSection: TransactionSection = TransactionSection(name = "Above 5K"),
    val otherSection: TransactionSection = TransactionSection(name = "Other"),
    val credibilityScore: Int = 0,
    val credibilityBand: CredibilityBand = CredibilityBand.INSUFFICIENT_DATA,
    val scoreComponents: List<ScoreComponent> = emptyList(),
    val strengths: List<String> = emptyList(),
    val riskSignals: List<String> = emptyList(),
    val monthlyBreakdown: List<MonthlyCashflow> = emptyList()
)

data class MonthlyCashflow(
    val month: String,
    val transactions: Int,
    val inflow: Double,
    val outflow: Double,
    val net: Double
)

data class ScoreComponent(
    val name: String,
    val points: Int,
    val maxPoints: Int
)

data class TransactionSection(
    val name: String,
    val transactionCount: Int = 0,
    val inflow: Double = 0.0,
    val outflow: Double = 0.0,
    val net: Double = 0.0
)

enum class CredibilityBand(val label: String) {
    EXCELLENT("Excellent"),
    GOOD("Good"),
    MODERATE("Moderate"),
    CAUTION("Caution"),
    HIGH_RISK("High Risk"),
    INSUFFICIENT_DATA("Insufficient Data")
}
