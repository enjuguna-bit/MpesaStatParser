package com.mpesaparser.utils

import com.mpesaparser.domain.model.AccountingInsights
import com.mpesaparser.domain.model.CredibilityBand
import com.mpesaparser.domain.model.MonthlyCashflow
import com.mpesaparser.domain.model.MpesaTransaction
import com.mpesaparser.domain.model.ParseDiagnostics
import com.mpesaparser.domain.model.ScoringConfig
import com.mpesaparser.domain.model.ScoreComponent
import com.mpesaparser.domain.model.TransactionSection
import com.mpesaparser.domain.model.TransactionCategory
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import android.util.Log

class AccountingAnalyzer {

    companion object {
        private const val DEFAULT_HIGH_VALUE_THRESHOLD = 5000.0
        private const val LOW_BALANCE_THRESHOLD = 500.0
        private val DATE_TIME_FORMATS = listOf(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
        )
    }

    fun analyze(
        transactions: List<MpesaTransaction>,
        diagnostics: ParseDiagnostics,
        config: ScoringConfig = ScoringConfig()
    ): AccountingInsights {
        if (transactions.isEmpty()) {
            return AccountingInsights()
        }
        val normalizedConfig = config.normalized()
        val highValueThreshold = normalizedConfig.highValueThreshold.takeIf { it > 0.0 } ?: DEFAULT_HIGH_VALUE_THRESHOLD

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

        val highValueCredit = incoming.filter { it.amount >= highValueThreshold }
        val highValueDebit = outgoing.filter { abs(it.amount) >= highValueThreshold }
        val highValueCreditTotal = highValueCredit.sumOf { it.amount }
        val highValueDebitTotal = highValueDebit.sumOf { abs(it.amount) }
        val highValueNet = highValueCreditTotal - highValueDebitTotal

        val chargeTotal = transactions
            .filter { it.category == TransactionCategory.CHARGE && it.amount < 0 }
            .sumOf { abs(it.amount) }
        val loanRepaymentTotal = transactions
            .filter { it.category == TransactionCategory.LOAN && it.amount < 0 }
            .sumOf { abs(it.amount) }
        val chargeRatio = if (totalOutflow > 0.0) chargeTotal / totalOutflow else 0.0
        val loanRatio = if (totalOutflow > 0.0) loanRepaymentTotal / totalOutflow else 0.0
        val obligationBurdenRatio = if (totalOutflow > 0.0) (chargeTotal + loanRepaymentTotal) / totalOutflow else 0.0
        val debitPressureRatio = if (totalOutflow > 0.0) highValueDebitTotal / totalOutflow else 0.0
        val creditStrengthRatio = if (totalInflow > 0.0) highValueCreditTotal / totalInflow else 0.0

        val failedOrReversedCount = transactions.count {
            val status = it.status.lowercase()
            status.contains("failed") || status.contains("reversed") || status.contains("cancel")
        }

        val timeEntries = transactions.mapNotNull { tx -> parseDateTime(tx.date, tx.time)?.let { tx to it } }
        val orderedByTime = if (timeEntries.isNotEmpty()) {
            timeEntries.sortedBy { it.second }.map { it.first }
        } else {
            transactions
        }
        val loanTransactions = orderedByTime.filter { isLoanTransaction(it) }
        val above5kTransactions = orderedByTime.filter { tx ->
            !isLoanTransaction(tx) && abs(tx.amount) >= highValueThreshold
        }
        val otherTransactions = orderedByTime.filter { tx ->
            !isLoanTransaction(tx) && abs(tx.amount) < highValueThreshold
        }
        val loanSection = buildSection("Loans", loanTransactions)
        val above5kSection = buildSection("Above 5K", above5kTransactions)
        val otherSection = buildSection("Other", otherTransactions)

        val activeDays = timeEntries.map { it.second.toLocalDate() }.distinct().size
            .takeIf { it > 0 } ?: transactions.mapNotNull { parseDateOnly(it.date) }.distinct().size
        val monthlyBreakdown = if (timeEntries.isNotEmpty()) {
            buildMonthlyBreakdownFromEntries(timeEntries)
        } else {
            buildMonthlyBreakdownFromTransactions(transactions)
        }
        val activeMonths = monthlyBreakdown.size
        val monthlyRatios = monthlyBreakdown.map { month ->
            if (month.outflow <= 0.0) 2.0 else month.inflow / month.outflow
        }
        val incomeStability = stabilityFromSeries(monthlyBreakdown.map { it.inflow })
        val expenseStability = stabilityFromSeries(monthlyBreakdown.map { it.outflow })
        val monthlyConsistency = if (monthlyRatios.isEmpty()) {
            0.0
        } else {
            ((consistencyFromRatios(monthlyRatios) + incomeStability + expenseStability) / 3.0).coerceIn(0.0, 1.0)
        }
        val averageDailyTransactions = if (activeDays > 0) transactions.size.toDouble() / activeDays.toDouble() else 0.0

        val dailyMinimumBalance = if (timeEntries.isNotEmpty()) {
            timeEntries
                .groupBy { it.second.toLocalDate() }
                .mapValues { (_, items) -> items.minOf { it.first.balance } }
        } else {
            transactions
                .mapNotNull { tx -> parseDateOnly(tx.date)?.let { it to tx.balance } }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, values) -> values.minOrNull() ?: 0.0 }
        }
        val lowBalanceDays = dailyMinimumBalance.count { (_, balance) -> balance < LOW_BALANCE_THRESHOLD }
        val lowBalanceDayRatio = if (activeDays > 0) lowBalanceDays.toDouble() / activeDays.toDouble() else 0.0

        val periodStart = timeEntries.minByOrNull { it.second }?.second?.toString().orEmpty()
        val periodEnd = timeEntries.maxByOrNull { it.second }?.second?.toString().orEmpty()

        val movementTotal = orderedByTime.sumOf { abs(it.amount) }
        val movementByCounterparty = orderedByTime
            .groupBy { if (it.counterparty.isBlank()) "N/A" else it.counterparty }
            .mapValues { (_, list) -> list.sumOf { abs(it.amount) } }
        val topCounterpartyPair = movementByCounterparty
            .maxByOrNull { it.value }
        val topCounterparty = topCounterpartyPair?.key ?: "N/A"
        val topCounterpartyShare = if (movementTotal > 0.0) {
            (topCounterpartyPair?.value ?: 0.0) / movementTotal
        } else {
            0.0
        }
        val top3CounterpartyShare = if (movementTotal > 0.0) {
            movementByCounterparty.values.sortedDescending().take(3).sum() / movementTotal
        } else {
            0.0
        }
        val counterpartyConcentrationIndex = if (movementTotal > 0.0) {
            movementByCounterparty.values.sumOf { value ->
                val share = value / movementTotal
                share * share
            }
        } else {
            0.0
        }

        val maxSingleCredit = incoming.maxOfOrNull { it.amount } ?: 0.0
        val maxSingleDebit = outgoing.maxOfOrNull { abs(it.amount) } ?: 0.0
        val balances = orderedByTime.map { it.balance }.sorted()
        val averageBalance = balances.averageOrZero()
        val minimumBalance = balances.minOrNull() ?: 0.0
        val medianBalance = median(balances)
        val balanceVolatility = standardDeviation(balances, averageBalance)
        val negativeBalanceCount = orderedByTime.count { it.balance < 0.0 }
        val completionRate = if (orderedByTime.isNotEmpty()) {
            1.0 - (failedOrReversedCount.toDouble() / orderedByTime.size.toDouble())
        } else {
            0.0
        }

        val scoreEvaluator = ScoreEvaluator()
        val scoringResult = scoreEvaluator.evaluateScore(
            inflowOutflowRatio, savingsRate, averageBalance, minimumBalance, lowBalanceDayRatio, completionRate,
            diagnostics.parseRate.toDouble(), incomeStability, expenseStability, obligationBurdenRatio, top3CounterpartyShare,
            highValueNet, debitPressureRatio, totalInflow, normalizedConfig, transactions.size, activeMonths
        )
        val scoreComponents = scoringResult.scoreComponents
        val credibilityScore = scoringResult.credibilityScore
        val credibilityBand = scoringResult.credibilityBand
        Log.d("AccountingAnalyzer", "Computed score: $credibilityScore, band: $credibilityBand")
        val riskAnalyzer = RiskSignalAnalyzer()
        val riskSignals = riskAnalyzer.analyzeRisks(
            inflowOutflowRatio, savingsRate, netCashflow, minimumBalance, lowBalanceDayRatio, completionRate,
            obligationBurdenRatio, debitPressureRatio, incomeStability, expenseStability, topCounterpartyShare, diagnostics.parseRate.toFloat()
        )
        val strengths = riskSignals.strengths
        val risks = riskSignals.riskSignals
        Log.d("AccountingAnalyzer", "Analyzed risks: ${strengths.size} strengths, ${risks.size} risks")

        return AccountingInsights(
            periodStart = periodStart,
            periodEnd = periodEnd,
            totalTransactions = transactions.size,
            totalInflow = totalInflow,
            totalOutflow = totalOutflow,
            netCashflow = netCashflow,
            inflowOutflowRatio = inflowOutflowRatio,
            savingsRate = savingsRate,
            averageCredit = incoming.map { it.amount }.averageOrZero(),
            averageDebit = outgoing.map { abs(it.amount) }.averageOrZero(),
            averageMovement = transactions.map { abs(it.amount) }.averageOrZero(),
            highValueThreshold = highValueThreshold,
            highValueCreditCount = highValueCredit.size,
            highValueDebitCount = highValueDebit.size,
            highValueCreditTotal = highValueCreditTotal,
            highValueDebitTotal = highValueDebitTotal,
            highValueNet = highValueNet,
            chargeToOutflowRatio = chargeRatio,
            loanToOutflowRatio = loanRatio,
            obligationBurdenRatio = obligationBurdenRatio,
            debitPressureRatio = debitPressureRatio,
            creditStrengthRatio = creditStrengthRatio,
            averageBalance = averageBalance,
            minimumBalance = minimumBalance,
            medianBalance = medianBalance,
            balanceVolatility = balanceVolatility,
            negativeBalanceCount = negativeBalanceCount,
            lowBalanceDays = lowBalanceDays,
            lowBalanceDayRatio = lowBalanceDayRatio,
            completionRate = completionRate,
            failedOrReversedCount = failedOrReversedCount,
            activeDays = activeDays,
            activeMonths = activeMonths,
            monthlyConsistency = monthlyConsistency,
            incomeStability = incomeStability,
            expenseStability = expenseStability,
            averageDailyTransactions = averageDailyTransactions,
            maxSingleCredit = maxSingleCredit,
            maxSingleDebit = maxSingleDebit,
            topCounterparty = topCounterparty,
            topCounterpartyShare = topCounterpartyShare,
            top3CounterpartyShare = top3CounterpartyShare,
            counterpartyConcentrationIndex = counterpartyConcentrationIndex,
            loanSection = loanSection,
            above5kSection = above5kSection,
            otherSection = otherSection,
            credibilityScore = credibilityScore,
            credibilityBand = credibilityBand,
            scoreComponents = scoreComponents,
            strengths = strengths,
            riskSignals = risks,
            monthlyBreakdown = monthlyBreakdown
        )
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

    private fun buildStrengths(
        inflowOutflowRatio: Double,
        savingsRate: Double,
        netCashflow: Double,
        completionRate: Double,
        averageBalance: Double,
        monthlyConsistency: Double,
        highValueCreditTotal: Double,
        highValueDebitTotal: Double,
        topCounterpartyShare: Double,
        obligationBurdenRatio: Double,
        lowBalanceDayRatio: Double,
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
        if (averageBalance >= 5000.0 && lowBalanceDayRatio <= 0.20) {
            strengths.add("Stable liquidity with low exposure to low-balance days")
        }
        if (monthlyConsistency >= 0.65) {
            strengths.add("Cashflow behavior is relatively consistent month to month")
        }
        if (incomeStability >= 0.65) {
            strengths.add("Income pattern is stable across months")
        }
        if (obligationBurdenRatio <= 0.15) {
            strengths.add("Charge and loan burden remains controlled")
        }
        if (highValueCreditTotal > highValueDebitTotal && highValueCreditTotal > 0.0) {
            strengths.add("Large transactions are net-positive")
        }
        if (topCounterpartyShare <= 0.35) {
            strengths.add("Transaction movement is reasonably diversified across counterparties")
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

    private fun buildMonthlyBreakdownFromEntries(entries: List<Pair<MpesaTransaction, LocalDateTime>>): List<MonthlyCashflow> {
        if (entries.isEmpty()) {
            return emptyList()
        }
        return entries
            .groupBy { YearMonth.from(it.second) }
            .toList()
            .sortedBy { it.first }
            .map { (month, rows) ->
                val tx = rows.map { it.first }
                val inflow = tx.filter { it.amount > 0 }.sumOf { it.amount }
                val outflow = tx.filter { it.amount < 0 }.sumOf { abs(it.amount) }
                MonthlyCashflow(
                    month = month.toString(),
                    transactions = tx.size,
                    inflow = inflow,
                    outflow = outflow,
                    net = inflow - outflow
                )
            }
    }

    private fun buildMonthlyBreakdownFromTransactions(transactions: List<MpesaTransaction>): List<MonthlyCashflow> {
        if (transactions.isEmpty()) {
            return emptyList()
        }
        return transactions
            .mapNotNull { tx -> parseYearMonth(tx.date)?.let { it to tx } }
            .groupBy({ it.first }, { it.second })
            .toList()
            .sortedBy { it.first }
            .map { (month, tx) ->
                val inflow = tx.filter { it.amount > 0 }.sumOf { it.amount }
                val outflow = tx.filter { it.amount < 0 }.sumOf { abs(it.amount) }
                MonthlyCashflow(
                    month = month.toString(),
                    transactions = tx.size,
                    inflow = inflow,
                    outflow = outflow,
                    net = inflow - outflow
                )
            }
    }

    private fun consistencyFromRatios(ratios: List<Double>): Double {
        if (ratios.isEmpty()) {
            return 0.0
        }
        if (ratios.size == 1) {
            return 0.6
        }
        val mean = ratios.averageOrZero()
        if (mean <= 0.0) {
            return 0.0
        }
        val cv = standardDeviation(ratios, mean) / mean
        return (1.0 - (cv / 1.6)).coerceIn(0.0, 1.0)
    }

    private fun stabilityFromSeries(values: List<Double>): Double {
        if (values.isEmpty()) {
            return 0.0
        }
        if (values.size == 1) {
            return 0.6
        }
        val mean = values.averageOrZero()
        if (mean <= 0.0) {
            return 0.0
        }
        val cv = standardDeviation(values, mean) / mean
        return (1.0 - (cv / 1.5)).coerceIn(0.0, 1.0)
    }

    private fun parseDateTime(date: String, time: String): LocalDateTime? {
        val raw = "$date $time"
        for (format in DATE_TIME_FORMATS) {
            val parsed = runCatching { LocalDateTime.parse(raw, format) }.getOrNull()
            if (parsed != null) {
                return parsed
            }
        }
        if (Regex("""\d{4}-\d{2}-\d{2}""").matches(date) && Regex("""\d{2}:\d{2}:\d{2}""").matches(time)) {
            val day = runCatching { LocalDate.parse(date) }.getOrNull()
            if (day != null) {
                return runCatching { LocalDateTime.parse("${day}T$time") }.getOrNull()
            }
        }
        return null
    }

    private fun parseDateOnly(date: String): LocalDate? {
        if (Regex("""\d{4}-\d{2}-\d{2}""").matches(date)) {
            return runCatching { LocalDate.parse(date) }.getOrNull()
        }
        if (Regex("""\d{2}/\d{2}/\d{4}""").matches(date)) {
            return runCatching { LocalDate.parse(date, DateTimeFormatter.ofPattern("dd/MM/yyyy")) }.getOrNull()
        }
        return null
    }

    private fun parseYearMonth(date: String): YearMonth? {
        val parsed = parseDateOnly(date) ?: return null
        return YearMonth.from(parsed)
    }

    private fun buildSection(name: String, tx: List<MpesaTransaction>): TransactionSection {
        val inflow = tx.filter { it.amount > 0 }.sumOf { it.amount }
        val outflow = tx.filter { it.amount < 0 }.sumOf { abs(it.amount) }
        return TransactionSection(
            name = name,
            transactionCount = tx.size,
            inflow = inflow,
            outflow = outflow,
            net = inflow - outflow
        )
    }

    private fun isLoanTransaction(tx: MpesaTransaction): Boolean {
        if (tx.category == TransactionCategory.LOAN || tx.category == TransactionCategory.OVERDRAFT) {
            return true
        }
        val text = "${tx.transactionType} ${tx.details}".lowercase()
        val markers = listOf("loan", "fuliza", "overdraft", "over draft", "credit party", "m-shwari", "mshwari")
        return markers.any { text.contains(it) }
    }

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) {
            return 0.0
        }
        val center = values.size / 2
        return if (values.size % 2 == 0) {
            (values[center - 1] + values[center]) / 2.0
        } else {
            values[center]
        }
    }

    private fun standardDeviation(values: List<Double>, mean: Double): Double {
        if (values.isEmpty()) {
            return 0.0
        }
        val variance = values.sumOf { (it - mean).pow(2.0) } / values.size.toDouble()
        return sqrt(variance)
    }

    private fun scale(value: Double, min: Double, max: Double): Double {
        if (max <= min) {
            return 0.0
        }
        return ((value - min) / (max - min)).coerceIn(0.0, 1.0)
    }

    private fun List<Double>.averageOrZero(): Double {
        return if (isEmpty()) 0.0 else average()
    }
}
