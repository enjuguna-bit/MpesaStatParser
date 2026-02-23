package com.mpesaparser.data.local

import com.mpesaparser.domain.model.AccountingInsights
import com.mpesaparser.domain.model.ParseDiagnostics
import com.mpesaparser.domain.model.ReportHistoryItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ReportHistoryRepository(
    private val dao: ReportHistoryDao
) {
    fun observeRecent(): Flow<List<ReportHistoryItem>> {
        return dao.observeRecent().map { rows ->
            rows.map { row ->
                ReportHistoryItem(
                    id = row.id,
                    createdAtMillis = row.createdAtMillis,
                    sourceName = row.sourceName,
                    transactions = row.transactions,
                    credibilityScore = row.credibilityScore,
                    credibilityBand = row.credibilityBand,
                    netCashflow = row.netCashflow,
                    parseQuality = row.parseQuality,
                    reportType = row.reportType,
                    strengths = row.strengths,
                    risks = row.risks
                )
            }
        }
    }

    suspend fun addEntry(
        sourceName: String,
        reportType: String,
        insights: AccountingInsights,
        diagnostics: ParseDiagnostics
    ) {
        val entity = ReportHistoryEntity(
            createdAtMillis = System.currentTimeMillis(),
            sourceName = sourceName,
            transactions = insights.totalTransactions,
            credibilityScore = insights.credibilityScore,
            credibilityBand = insights.credibilityBand.label,
            netCashflow = insights.netCashflow,
            parseQuality = diagnostics.parseRate.toDouble(),
            reportType = reportType,
            strengths = insights.strengths.joinToString(" | "),
            risks = insights.riskSignals.joinToString(" | ")
        )
        dao.insert(entity)
        dao.trimToRecent()
    }
}
