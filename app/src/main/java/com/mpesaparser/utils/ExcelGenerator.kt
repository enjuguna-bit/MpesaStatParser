package com.mpesaparser.utils

import android.content.Context
import androidx.core.content.FileProvider
import com.mpesaparser.domain.model.AccountingInsights
import com.mpesaparser.domain.model.MpesaTransaction
import com.mpesaparser.domain.model.ParseDiagnostics
import com.mpesaparser.utils.BenchmarkingResult
import com.mpesaparser.utils.DTIAnalysis
import com.mpesaparser.utils.LoanEligibilityResult
import com.mpesaparser.utils.LoanRedFlags
import com.mpesaparser.utils.LoanRecommendation
import com.mpesaparser.utils.RepaymentCapacityResult
import java.io.BufferedWriter
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

class ExcelGenerator {

    private val dateFormatLegacy = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    fun generateExcel(
        transactions: List<MpesaTransaction>,
        diagnostics: ParseDiagnostics,
        insights: AccountingInsights,
        loanEligibility: LoanEligibilityResult?,
        dtiAnalysis: DTIAnalysis?,
        loanRedFlags: LoanRedFlags?,
        benchmarking: BenchmarkingResult?,
        loanRecommendation: LoanRecommendation?,
        repaymentCapacity: RepaymentCapacityResult?,
        context: Context
    ): android.net.Uri? {
        val fileName = "mpesa_analysis_${System.currentTimeMillis()}.csv"
        val outputDir = context.getExternalFilesDir(null) ?: context.filesDir
        cleanupOldExports(outputDir)
        val file = File(outputDir, fileName)

        file.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write('\uFEFF'.code)
            writeReportHeader(writer, insights)
            writer.newLine()
            writeTransactionsSection(writer, transactions)
            writer.newLine()
            writeSummarySection(writer, transactions, diagnostics)
            writer.newLine()
            writeAccountingSection(writer, insights)
            writer.newLine()
            writeTransactionSections(writer, insights)
            writer.newLine()
            writeCategorySummarySection(writer, transactions)
            writer.newLine()
            writeMonthlySummarySection(writer, transactions)
            writer.newLine()
            writeTopCounterpartiesSection(writer, transactions)
            if (
                loanEligibility != null || dtiAnalysis != null || loanRedFlags != null ||
                benchmarking != null || loanRecommendation != null || repaymentCapacity != null
            ) {
                writer.newLine()
                writeLoanDecisionSection(
                    writer = writer,
                    loanEligibility = loanEligibility,
                    dtiAnalysis = dtiAnalysis,
                    loanRedFlags = loanRedFlags,
                    benchmarking = benchmarking,
                    loanRecommendation = loanRecommendation,
                    repaymentCapacity = repaymentCapacity
                )
            }
            if (diagnostics.unmatchedSamples.isNotEmpty()) {
                writer.newLine()
                writeUnparsedSection(writer, diagnostics)
            }
        }

        return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    }

    private fun writeReportHeader(writer: BufferedWriter, insights: AccountingInsights) {
        writeCsvRow(
            writer,
            listOf(
                "M-Pesa Accounting Report",
                "Credibility Score ${insights.credibilityScore}/100 (${insights.credibilityBand.label})"
            )
        )
        writeCsvRow(writer, listOf("Generated At", java.time.LocalDateTime.now().toString()))
    }

    private fun writeTransactionsSection(writer: BufferedWriter, transactions: List<MpesaTransaction>) {
        writeSectionTitle(writer, "Transactions")
        writeCsvRow(
            writer, listOf(
            "Date",
            "Time",
            "Type",
            "Category",
            "Counterparty",
            "Status",
            "Amount",
            "Balance",
            "Reference",
            "Details"
        )
        )

        transactions.forEach { tx ->
            writeCsvRow(
                writer, listOf(
                tx.date,
                tx.time,
                tx.transactionType,
                tx.category.label,
                tx.counterparty.ifBlank { "N/A" },
                tx.status,
                String.format(Locale.US, "%.2f", tx.amount),
                String.format(Locale.US, "%.2f", tx.balance),
                tx.reference,
                tx.details
            )
            )
        }
    }

    private fun writeSummarySection(
        writer: BufferedWriter,
        transactions: List<MpesaTransaction>,
        diagnostics: ParseDiagnostics
    ) {
        val incoming = transactions.filter { it.amount > 0 }.sumOf { it.amount }
        val outgoing = transactions.filter { it.amount < 0 }.sumOf { abs(it.amount) }
        val net = incoming - outgoing
        val parsedRate = diagnostics.parseRate * 100f

        writeSectionTitle(writer, "Summary")
        writeCsvRow(writer, listOf("Metric", "Value"))
        val rows = listOf(
            "Generated At" to java.time.LocalDateTime.now().toString(),
            "Transactions Parsed" to transactions.size.toString(),
            "Candidate Rows" to diagnostics.candidateRows.toString(),
            "Parsed Rows" to diagnostics.parsedRows.toString(),
            "Parse Quality" to String.format(Locale.US, "%.2f%%", parsedRate),
            "Parser Mode" to diagnostics.parserMode,
            "Duplicates Removed" to diagnostics.duplicatesRemoved.toString(),
            "Parse Confidence" to String.format(Locale.US, "%.2f%%", diagnostics.confidenceScore * 100f),
            "Total Paid In" to formatCurrency(incoming),
            "Total Paid Out" to formatCurrency(outgoing),
            "Net Cashflow" to formatCurrency(net),
            "Custom Rule Count" to "Applied during parsing and classification"
        )

        rows.forEach { (metric, value) ->
            writeCsvRow(writer, listOf(metric, value))
        }
    }

    private fun writeAccountingSection(writer: BufferedWriter, insights: AccountingInsights) {
        writeSectionTitle(writer, "Accounting Analysis")
        writeCsvRow(writer, listOf("Metric", "Value"))
        val rows = listOf(
            "Credibility Score" to "${insights.credibilityScore}/100",
            "Credibility Band" to insights.credibilityBand.label,
            "Assessment Note" to recommendation(insights.credibilityBand.label),
            "Period Start" to insights.periodStart.ifBlank { "N/A" },
            "Period End" to insights.periodEnd.ifBlank { "N/A" },
            "Active Days" to insights.activeDays.toString(),
            "Active Months" to insights.activeMonths.toString(),
            "Average Daily Transactions" to String.format(Locale.US, "%.2f", insights.averageDailyTransactions),
            "Average Credit" to formatCurrency(insights.averageCredit),
            "Average Debit" to formatCurrency(insights.averageDebit),
            "Average Movement" to formatCurrency(insights.averageMovement),
            "Inflow / Outflow Ratio" to formatRatio(insights.inflowOutflowRatio),
            "Savings Rate" to formatPercent(insights.savingsRate),
            "Average Balance" to formatCurrency(insights.averageBalance),
            "Minimum Balance" to formatCurrency(insights.minimumBalance),
            "Median Balance" to formatCurrency(insights.medianBalance),
            "Balance Volatility (Std Dev)" to formatCurrency(insights.balanceVolatility),
            "Negative Balance Entries" to insights.negativeBalanceCount.toString(),
            "Low Balance Days (< KES 500)" to insights.lowBalanceDays.toString(),
            "Low Balance Day Ratio" to formatPercent(insights.lowBalanceDayRatio),
            "Completion Rate" to formatPercent(insights.completionRate),
            "Failed or Reversed Tx" to insights.failedOrReversedCount.toString(),
            "Monthly Consistency" to formatPercent(insights.monthlyConsistency),
            "Income Stability" to formatPercent(insights.incomeStability),
            "Expense Stability" to formatPercent(insights.expenseStability),
            "High-Value Threshold" to formatCurrency(insights.highValueThreshold),
            "High-Value Credit Count" to insights.highValueCreditCount.toString(),
            "High-Value Debit Count" to insights.highValueDebitCount.toString(),
            "High-Value Credit Total" to formatCurrency(insights.highValueCreditTotal),
            "High-Value Debit Total" to formatCurrency(insights.highValueDebitTotal),
            "High-Value Net" to formatCurrency(insights.highValueNet),
            "Charge to Outflow Ratio" to formatPercent(insights.chargeToOutflowRatio),
            "Loan to Outflow Ratio" to formatPercent(insights.loanToOutflowRatio),
            "Obligation Burden Ratio" to formatPercent(insights.obligationBurdenRatio),
            "Debit Pressure Ratio" to formatPercent(insights.debitPressureRatio),
            "Credit Strength Ratio" to formatPercent(insights.creditStrengthRatio),
            "Max Single Credit" to formatCurrency(insights.maxSingleCredit),
            "Max Single Debit" to formatCurrency(insights.maxSingleDebit),
            "Top Counterparty" to insights.topCounterparty,
            "Top Counterparty Share" to formatPercent(insights.topCounterpartyShare),
            "Top 3 Counterparty Share" to formatPercent(insights.top3CounterpartyShare),
            "Counterparty Concentration Index (HHI)" to String.format(Locale.US, "%.3f", insights.counterpartyConcentrationIndex)
        )
        rows.forEach { (metric, value) ->
            writeCsvRow(writer, listOf(metric, value))
        }

        if (insights.scoreComponents.isNotEmpty()) {
            writer.newLine()
            writeCsvRow(writer, listOf("Score Breakdown"))
            writeCsvRow(writer, listOf("Component", "Points", "Max"))
            insights.scoreComponents.forEach { component ->
                writeCsvRow(
                    writer,
                    listOf(component.name, component.points.toString(), component.maxPoints.toString())
                )
            }
        }

        if (insights.monthlyBreakdown.isNotEmpty()) {
            writer.newLine()
            writeCsvRow(writer, listOf("Monthly Accounting Breakdown"))
            writeCsvRow(writer, listOf("Month", "Transactions", "Inflow", "Outflow", "Net", "Coverage"))
            insights.monthlyBreakdown.forEach { month ->
                val coverage = if (month.outflow <= 0.0) "N/A" else formatRatio(month.inflow / month.outflow)
                writeCsvRow(
                    writer,
                    listOf(
                        month.month,
                        month.transactions.toString(),
                        formatCurrency(month.inflow),
                        formatCurrency(month.outflow),
                        formatCurrency(month.net),
                        coverage
                    )
                )
            }
        }

        if (insights.strengths.isNotEmpty()) {
            writer.newLine()
            writeCsvRow(writer, listOf("Strengths"))
            insights.strengths.forEach { strength ->
                writeCsvRow(writer, listOf(strength))
            }
        }
        if (insights.riskSignals.isNotEmpty()) {
            writer.newLine()
            writeCsvRow(writer, listOf("Risk Signals"))
            insights.riskSignals.forEach { risk ->
                writeCsvRow(writer, listOf(risk))
            }
        }
    }

    private fun writeTransactionSections(writer: BufferedWriter, insights: AccountingInsights) {
        writeSectionTitle(writer, "Transaction Sections")
        writeCsvRow(writer, listOf("Section", "Count", "Inflow", "Outflow", "Net", "Share of Total"))
        val total = insights.totalTransactions.coerceAtLeast(1)
        val sections = listOf(insights.loanSection, insights.above5kSection, insights.otherSection)
        sections.forEach { section ->
            val share = section.transactionCount.toDouble() / total.toDouble()
            writeCsvRow(
                writer,
                listOf(
                    section.name,
                    section.transactionCount.toString(),
                    formatCurrency(section.inflow),
                    formatCurrency(section.outflow),
                    formatCurrency(section.net),
                    formatPercent(share)
                )
            )
        }
    }

    private fun writeCategorySummarySection(writer: BufferedWriter, transactions: List<MpesaTransaction>) {
        writeSectionTitle(writer, "By Category")
        writeCsvRow(writer, listOf("Category", "Count", "Total Amount"))
        val grouped = transactions.groupBy { it.category }
            .toList()
            .sortedByDescending { (_, list) -> list.sumOf { abs(it.amount) } }

        grouped.forEach { (category, items) ->
            val total = items.sumOf { it.amount }
            writeCsvRow(
                writer, listOf(
                category.label,
                items.size.toString(),
                String.format(Locale.US, "%.2f", total)
            )
            )
        }
    }

    private fun writeMonthlySummarySection(writer: BufferedWriter, transactions: List<MpesaTransaction>) {
        writeSectionTitle(writer, "Monthly Summary")
        writeCsvRow(writer, listOf("Month", "Count", "Paid In", "Paid Out", "Net"))
        val grouped = transactions.groupBy { monthKey(it.date) }
            .toList()
            .sortedByDescending { it.first }

        grouped.forEach { (month, list) ->
            val incoming = list.filter { it.amount > 0 }.sumOf { it.amount }
            val outgoing = list.filter { it.amount < 0 }.sumOf { abs(it.amount) }
            val net = incoming - outgoing
            writeCsvRow(
                writer, listOf(
                month,
                list.size.toString(),
                String.format(Locale.US, "%.2f", incoming),
                String.format(Locale.US, "%.2f", outgoing),
                String.format(Locale.US, "%.2f", net)
            )
            )
        }
    }

    private fun writeTopCounterpartiesSection(writer: BufferedWriter, transactions: List<MpesaTransaction>) {
        writeSectionTitle(writer, "Top Counterparties")
        writeCsvRow(writer, listOf("Counterparty", "Count", "Total Movement"))
        val grouped = transactions
            .groupBy { if (it.counterparty.isBlank()) "N/A" else it.counterparty }
            .mapValues { (_, list) -> list.size to list.sumOf { abs(it.amount) } }
            .toList()
            .sortedByDescending { it.second.second }
            .take(25)

        grouped.forEach { (counterparty, data) ->
            writeCsvRow(
                writer, listOf(
                counterparty,
                data.first.toString(),
                String.format(Locale.US, "%.2f", data.second)
            )
            )
        }
    }

    private fun writeUnparsedSection(writer: BufferedWriter, diagnostics: ParseDiagnostics) {
        writeSectionTitle(writer, "Unparsed Rows")
        writeCsvRow(writer, listOf("Sample Unparsed Rows"))
        diagnostics.unmatchedSamples.forEach { sample ->
            writeCsvRow(writer, listOf(sample))
        }
    }

    private fun writeLoanDecisionSection(
        writer: BufferedWriter,
        loanEligibility: LoanEligibilityResult?,
        dtiAnalysis: DTIAnalysis?,
        loanRedFlags: LoanRedFlags?,
        benchmarking: BenchmarkingResult?,
        loanRecommendation: LoanRecommendation?,
        repaymentCapacity: RepaymentCapacityResult?
    ) {
        writeSectionTitle(writer, "Loan Decision Support")
        writeCsvRow(writer, listOf("Metric", "Value"))
        loanEligibility?.let {
            writeCsvRow(writer, listOf("Eligible", if (it.isEligible) "Yes" else "No"))
            writeCsvRow(
                writer,
                listOf("Eligibility Recommended Amount", it.recommendedAmount?.let { amount -> formatCurrency(amount) } ?: "N/A")
            )
            it.reasons.forEach { reason -> writeCsvRow(writer, listOf("Eligibility Note", reason)) }
        }
        dtiAnalysis?.let {
            writeCsvRow(writer, listOf("DTI Ratio", String.format(Locale.US, "%.2f", it.dtiRatio)))
            writeCsvRow(writer, listOf("DTI Risk", it.riskLevel))
            writeCsvRow(writer, listOf("Monthly Debt", formatCurrency(it.monthlyDebtPayments)))
            writeCsvRow(writer, listOf("Monthly Income", formatCurrency(it.monthlyIncome)))
        }
        loanRedFlags?.let {
            writeCsvRow(writer, listOf("Red Flag Severity", it.severity))
            it.flags.forEach { flag -> writeCsvRow(writer, listOf("Red Flag", flag)) }
        }
        benchmarking?.let {
            writeCsvRow(writer, listOf("Benchmark Rating", it.overallRating))
            it.comparisons.forEach { comp ->
                writeCsvRow(
                    writer,
                    listOf("Benchmark ${comp.metric}", "${String.format(Locale.US, "%.2f", comp.value)} vs ${String.format(Locale.US, "%.2f", comp.benchmark)} (${comp.status})")
                )
            }
        }
        loanRecommendation?.let {
            writeCsvRow(writer, listOf("Recommended Loan Amount", formatCurrency(it.recommendedAmount)))
            writeCsvRow(writer, listOf("Recommended Term", "${it.recommendedTerm} months"))
            writeCsvRow(writer, listOf("Estimated Payment", formatCurrency(it.monthlyPayment)))
            it.reasoning.forEach { reason -> writeCsvRow(writer, listOf("Recommendation Note", reason)) }
        }
        repaymentCapacity?.let {
            writeCsvRow(writer, listOf("Repayment Capacity", if (it.canRepay) "Adequate" else "Risky"))
            writeCsvRow(writer, listOf("Coverage Ratio", String.format(Locale.US, "%.2fx", it.coverageRatio)))
            writeCsvRow(writer, listOf("Monthly Surplus", formatCurrency(it.monthlySurplus)))
            writeCsvRow(writer, listOf("Monthly Payment", formatCurrency(it.monthlyPayment)))
            it.reasons.forEach { reason -> writeCsvRow(writer, listOf("Repayment Note", reason)) }
        }
    }

    private fun writeSectionTitle(writer: BufferedWriter, title: String) {
        writeCsvRow(writer, listOf(title))
    }

    private fun writeCsvRow(writer: BufferedWriter, values: List<String>) {
        writer.write(values.joinToString(",") { escapeCsv(it) })
        writer.newLine()
    }

    private fun escapeCsv(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return if (escaped.contains(',') || escaped.contains('"') || escaped.contains('\n') || escaped.contains('\r')) {
            "\"$escaped\""
        } else {
            escaped
        }
    }

    private fun monthKey(date: String): String {
        if (Regex("""\d{4}-\d{2}-\d{2}""").matches(date)) {
            return date.substring(0, 7)
        }
        if (Regex("""\d{2}/\d{2}/\d{4}""").matches(date)) {
            val parsed = runCatching { LocalDate.parse(date, dateFormatLegacy) }.getOrNull()
            if (parsed != null) {
                return String.format(Locale.US, "%04d-%02d", parsed.year, parsed.monthValue)
            }
        }
        return "Unknown"
    }

    private fun formatCurrency(value: Double): String {
        return "KES ${String.format(Locale.US, "%,.2f", value)}"
    }

    private fun formatPercent(value: Double): String {
        return String.format(Locale.US, "%.2f%%", value * 100.0)
    }

    private fun formatRatio(value: Double): String {
        return String.format(Locale.US, "%.2fx", value)
    }

    private fun recommendation(band: String): String {
        return when (band) {
            "Excellent" -> "Strong profile. Suitable for higher credit limits with normal monitoring."
            "Good" -> "Healthy profile. Suitable for standard credit exposure."
            "Moderate" -> "Usable profile. Apply moderate limits and verify supporting documents."
            "Caution" -> "Elevated risk. Use conservative limits and tighter repayment terms."
            "High Risk" -> "High default risk signals. Require additional collateral or guarantors."
            else -> "Limited data. Request longer statement history before final credit decision."
        }
    }

    private fun cleanupOldExports(outputDir: File) {
        val cutoff = System.currentTimeMillis() - (7L * 24L * 60L * 60L * 1000L)
        outputDir.listFiles { file ->
            file.isFile && file.name.startsWith("mpesa_analysis_") && file.name.endsWith(".csv")
        }?.forEach { file ->
            if (file.lastModified() < cutoff) {
                file.delete()
            }
        }
    }
}
