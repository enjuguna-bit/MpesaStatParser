package com.mpesaparser.utils

import com.mpesaparser.domain.model.AccountingInsights

data class BenchmarkComparison(
    val metric: String,
    val value: Double,
    val benchmark: Double,
    val status: String // Above, Below, Average
)

data class BenchmarkingResult(
    val comparisons: List<BenchmarkComparison>,
    val overallRating: String
)

class BorrowerBenchmarker {

    // Hardcoded benchmarks based on typical Kenyan M-Pesa users (adjustable)
    private val benchmarks = mapOf(
        "Credibility Score" to 65.0,
        "DTI Ratio" to 0.35,
        "Savings Rate" to 0.15,
        "Completion Rate" to 0.97,
        "Income Stability" to 0.7,
        "Low Balance Day Ratio" to 0.2
    )

    fun benchmarkBorrower(insights: AccountingInsights): BenchmarkingResult {
        val comparisons = mutableListOf<BenchmarkComparison>()

        // Credibility Score
        comparisons.add(compareMetric("Credibility Score", insights.credibilityScore.toDouble(), benchmarks["Credibility Score"]!!))

        // DTI (approximate using obligation burden)
        val dtiApprox = insights.chargeToOutflowRatio + insights.loanToOutflowRatio
        comparisons.add(compareMetric("DTI Ratio", dtiApprox, benchmarks["DTI Ratio"]!!))

        // Savings Rate
        comparisons.add(compareMetric("Savings Rate", insights.savingsRate, benchmarks["Savings Rate"]!!))

        // Completion Rate
        comparisons.add(compareMetric("Completion Rate", insights.completionRate, benchmarks["Completion Rate"]!!))

        // Income Stability
        comparisons.add(compareMetric("Income Stability", insights.incomeStability, benchmarks["Income Stability"]!!))

        // Low Balance Day Ratio
        comparisons.add(compareMetric("Low Balance Day Ratio", insights.lowBalanceDayRatio, benchmarks["Low Balance Day Ratio"]!!))

        val aboveAverage = comparisons.count { it.status == "Above" }
        val belowAverage = comparisons.count { it.status == "Below" }

        val overallRating = when {
            aboveAverage > belowAverage -> "Above Average"
            belowAverage > aboveAverage -> "Below Average"
            else -> "Average"
        }

        return BenchmarkingResult(comparisons, overallRating)
    }

    private fun compareMetric(metric: String, value: Double, benchmark: Double): BenchmarkComparison {
        val tolerance = benchmark * 0.1 // 10% tolerance for "average"
        val status = when {
            value > benchmark + tolerance -> "Above"
            value < benchmark - tolerance -> "Below"
            else -> "Average"
        }
        return BenchmarkComparison(metric, value, benchmark, status)
    }
}
