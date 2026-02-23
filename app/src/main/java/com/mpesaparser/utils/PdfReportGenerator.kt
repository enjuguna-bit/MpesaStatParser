package com.mpesaparser.utils

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.mpesaparser.domain.model.AccountingInsights
import com.mpesaparser.domain.model.ParseDiagnostics
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class PdfReportGenerator {

    fun generateSummaryPdf(
        insights: AccountingInsights,
        diagnostics: ParseDiagnostics,
        loanEligibility: LoanEligibilityResult?,
        dtiAnalysis: DTIAnalysis?,
        loanRedFlags: LoanRedFlags?,
        benchmarking: BenchmarkingResult?,
        loanRecommendation: LoanRecommendation?,
        repaymentCapacity: RepaymentCapacityResult?,
        context: Context
    ): android.net.Uri? {
        val fileName = "mpesa_executive_report_${System.currentTimeMillis()}.pdf"
        val outputDir = context.getExternalFilesDir(null) ?: context.filesDir
        cleanupOldPdfReports(outputDir)
        val file = File(outputDir, fileName)

        val document = PdfDocument()
        try {
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            val titlePaint = Paint().apply {
                textSize = 20f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val headingPaint = Paint().apply {
                textSize = 14f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val bodyPaint = Paint().apply {
                textSize = 11f
            }

            var y = 50f
            canvas.drawText("M-Pesa Executive Credit Report", 40f, y, titlePaint)
            y += 28f
            canvas.drawText(
                "Credibility Score: ${insights.credibilityScore}/100 (${insights.credibilityBand.label})",
                40f,
                y,
                headingPaint
            )
            y += 20f
            canvas.drawText("Generated: ${java.time.LocalDateTime.now()}", 40f, y, bodyPaint)
            y += 28f

            canvas.drawText("Core Metrics", 40f, y, headingPaint)
            y += 18f
            val metricRows = listOf(
                "Transactions: ${insights.totalTransactions}",
                "Inflow: ${currency(insights.totalInflow)}",
                "Outflow: ${currency(insights.totalOutflow)}",
                "Net Cashflow: ${currency(insights.netCashflow)}",
                "Coverage Ratio: ${ratio(insights.inflowOutflowRatio)}",
                "Savings Rate: ${percent(insights.savingsRate)}",
                "Completion Rate: ${percent(insights.completionRate)}",
                "Parse Quality: ${String.format(Locale.US, "%.2f%%", diagnostics.parseRate * 100f)}",
                "Parse Confidence: ${String.format(Locale.US, "%.2f%%", diagnostics.confidenceScore * 100f)}",
                "Parser Mode: ${diagnostics.parserMode}"
            )
            metricRows.forEach { row ->
                canvas.drawText("- $row", 50f, y, bodyPaint)
                y += 15f
            }
            y += 10f

            canvas.drawText("Section Analysis", 40f, y, headingPaint)
            y += 18f
            val sections = listOf(insights.loanSection, insights.above5kSection, insights.otherSection)
            sections.forEach { section ->
                val line = "${section.name}: ${section.transactionCount} tx | In ${currency(section.inflow)} | Out ${currency(section.outflow)} | Net ${currency(section.net)}"
                canvas.drawText("- $line", 50f, y, bodyPaint)
                y += 15f
            }
            y += 10f

            if (insights.strengths.isNotEmpty()) {
                canvas.drawText("Strengths", 40f, y, headingPaint)
                y += 18f
                insights.strengths.take(5).forEach { strength ->
                    canvas.drawText("- $strength", 50f, y, bodyPaint)
                    y += 15f
                }
            }
            y += 8f
            if (insights.riskSignals.isNotEmpty()) {
                canvas.drawText("Risk Signals", 40f, y, headingPaint)
                y += 18f
                insights.riskSignals.take(6).forEach { risk ->
                    canvas.drawText("- $risk", 50f, y, bodyPaint)
                    y += 15f
                }
            }
            y += 8f

            canvas.drawText("Decision Guidance", 40f, y, headingPaint)
            y += 18f
            canvas.drawText(
                recommendation(insights.credibilityBand.label),
                50f,
                y,
                bodyPaint
            )
            y += 24f
            canvas.drawText("Loan Decision Snapshot", 40f, y, headingPaint)
            y += 18f
            loanEligibility?.let {
                canvas.drawText("- Eligibility: ${if (it.isEligible) "Eligible" else "Not Eligible"}", 50f, y, bodyPaint)
                y += 15f
            }
            dtiAnalysis?.let {
                canvas.drawText("- DTI: ${String.format(Locale.US, "%.2f", it.dtiRatio)} (${it.riskLevel})", 50f, y, bodyPaint)
                y += 15f
            }
            loanRecommendation?.let {
                canvas.drawText("- Recommended: ${currency(it.recommendedAmount)} over ${it.recommendedTerm}m", 50f, y, bodyPaint)
                y += 15f
            }
            repaymentCapacity?.let {
                canvas.drawText("- Repayment: ${if (it.canRepay) "Adequate" else "Risky"} (${String.format(Locale.US, "%.2f", it.coverageRatio)}x)", 50f, y, bodyPaint)
                y += 15f
            }
            loanRedFlags?.let {
                canvas.drawText("- Red flags: ${it.flags.size} (${it.severity})", 50f, y, bodyPaint)
                y += 15f
            }
            benchmarking?.let {
                canvas.drawText("- Benchmark: ${it.overallRating}", 50f, y, bodyPaint)
                y += 15f
            }
            y += 10f
            canvas.drawText(
                "Model version: CreditModel v2.1 | For internal decision support only.",
                40f,
                y,
                bodyPaint
            )

            document.finishPage(page)
            FileOutputStream(file).use { output ->
                document.writeTo(output)
            }
        } finally {
            document.close()
        }

        return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    }

    private fun recommendation(band: String): String {
        return when (band) {
            "Excellent" -> "Strong profile. Suitable for higher credit limits with standard controls."
            "Good" -> "Healthy profile. Suitable for standard lending products."
            "Moderate" -> "Apply moderate limits and verify supporting income/cashflow documents."
            "Caution" -> "Use conservative limits and stricter repayment conditions."
            "High Risk" -> "High risk profile. Require collateral or additional guarantees."
            else -> "Insufficient data for final credit determination."
        }
    }

    private fun cleanupOldPdfReports(outputDir: File) {
        val cutoff = System.currentTimeMillis() - (7L * 24L * 60L * 60L * 1000L)
        outputDir.listFiles { file ->
            file.isFile && file.name.startsWith("mpesa_executive_report_") && file.name.endsWith(".pdf")
        }?.forEach { file ->
            if (file.lastModified() < cutoff) {
                file.delete()
            }
        }
    }

    private fun currency(value: Double): String = "KES ${String.format(Locale.US, "%,.2f", value)}"
    private fun percent(value: Double): String = "${String.format(Locale.US, "%.1f", value * 100.0)}%"
    private fun ratio(value: Double): String = "${String.format(Locale.US, "%.2f", value)}x"
}

