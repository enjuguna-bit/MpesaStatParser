package com.mpesaparser.utils

import com.mpesaparser.domain.model.MpesaTransaction
import com.mpesaparser.domain.model.TransactionCategory
import kotlin.math.abs

data class DTIAnalysis(
    val dtiRatio: Double,
    val monthlyDebtPayments: Double,
    val monthlyIncome: Double,
    val riskLevel: String
)

class DTICalculator {

    fun calculateDTI(transactions: List<MpesaTransaction>, periodMonths: Int = 1): DTIAnalysis {
        if (transactions.isEmpty() || periodMonths <= 0) {
            return DTIAnalysis(0.0, 0.0, 0.0, "Insufficient Data")
        }

        // Approximate monthly income (total inflow / period)
        val totalInflow = transactions.filter { it.amount > 0 }.sumOf { it.amount }
        val monthlyIncome = totalInflow / periodMonths

        // Debt payments: outflows from loans, charges, etc.
        val debtCategories = setOf(TransactionCategory.LOAN, TransactionCategory.OVERDRAFT, TransactionCategory.CHARGE)
        val totalDebtPayments = transactions.filter { it.amount < 0 && it.category in debtCategories }.sumOf { abs(it.amount) }
        val monthlyDebtPayments = totalDebtPayments / periodMonths

        val dtiRatio = if (monthlyIncome > 0) monthlyDebtPayments / monthlyIncome else 0.0

        val riskLevel = when {
            dtiRatio <= 0.36 -> "Low Risk"
            dtiRatio <= 0.43 -> "Moderate Risk"
            else -> "High Risk"
        }

        return DTIAnalysis(dtiRatio, monthlyDebtPayments, monthlyIncome, riskLevel)
    }
}
