package com.mpesaparser.utils

import com.mpesaparser.domain.model.MpesaTransaction
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class BasicMetrics(
    val incoming: List<MpesaTransaction>,
    val outgoing: List<MpesaTransaction>,
    val totalInflow: Double,
    val totalOutflow: Double,
    val netCashflow: Double,
    val inflowOutflowRatio: Double,
    val savingsRate: Double,
    val averageCredit: Double,
    val averageDebit: Double,
    val averageMovement: Double,
    val balances: List<Double>,
    val averageBalance: Double,
    val minimumBalance: Double,
    val medianBalance: Double,
    val balanceVolatility: Double,
    val negativeBalanceCount: Int,
    val completedTransactions: List<MpesaTransaction>,
    val failedTransactions: List<MpesaTransaction>,
    val completionRate: Double,
    val maxSingleCredit: Double,
    val maxSingleDebit: Double
)

class FinancialMetricsCalculator {

    fun computeBasicMetrics(transactions: List<MpesaTransaction>): BasicMetrics {
        if (transactions.isEmpty()) {
            return BasicMetrics(
                emptyList(), emptyList(), 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, emptyList(),
                0.0, 0.0, 0.0, 0.0, 0, emptyList(), emptyList(), 0.0, 0.0, 0.0
            )
        }

        val incoming = transactions.filter { it.amount > 0 }
        val outgoing = transactions.filter { it.amount < 0 }
        val totalInflow = incoming.sumOf { it.amount }
        val totalOutflow = outgoing.sumOf { abs(it.amount) }
        val netCashflow = totalInflow - totalOutflow
        val inflowOutflowRatio = when {
            totalOutflow <= 0.0 && totalInflow > 0.0 -> 2.0
            totalOutflow <= 0.0 -> 0.0
            else -> totalInflow / totalOutflow
        }
        val savingsRate = if (totalInflow > 0.0) netCashflow / totalInflow else 0.0

        val averageCredit = incoming.map { it.amount }.averageOrZero()
        val averageDebit = outgoing.map { abs(it.amount) }.averageOrZero()
        val averageMovement = transactions.map { abs(it.amount) }.averageOrZero()

        val balances = transactions.map { it.balance }.sorted()
        val averageBalance = balances.averageOrZero()
        val minimumBalance = balances.minOrNull() ?: 0.0
        val medianBalance = median(balances)
        val balanceVolatility = standardDeviation(balances, averageBalance)
        val negativeBalanceCount = transactions.count { it.balance < 0.0 }

        val completedTransactions = transactions.filter { it.status.lowercase().contains("completed") }
        val failedTransactions = transactions.filter { it.status.lowercase().let { s -> s.contains("failed") || s.contains("reversed") || s.contains("cancel") } }
        val completionRate = if (transactions.isNotEmpty()) 1.0 - (failedTransactions.size.toDouble() / transactions.size.toDouble()) else 0.0

        val maxSingleCredit = incoming.maxOfOrNull { it.amount } ?: 0.0
        val maxSingleDebit = outgoing.maxOfOrNull { abs(it.amount) } ?: 0.0

        return BasicMetrics(
            incoming, outgoing, totalInflow, totalOutflow, netCashflow, inflowOutflowRatio, savingsRate,
            averageCredit, averageDebit, averageMovement, balances, averageBalance,
            minimumBalance, medianBalance, balanceVolatility, negativeBalanceCount,
            completedTransactions, failedTransactions, completionRate, maxSingleCredit, maxSingleDebit
        )
    }

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val center = values.size / 2
        return if (values.size % 2 == 0) (values[center - 1] + values[center]) / 2.0 else values[center]
    }

    private fun standardDeviation(values: List<Double>, mean: Double): Double {
        if (values.isEmpty()) return 0.0
        val variance = values.sumOf { (it - mean).pow(2.0) } / values.size.toDouble()
        return sqrt(variance)
    }

    private fun List<Double>.averageOrZero(): Double = if (isEmpty()) 0.0 else average()
}
