package com.mpesaparser.domain.model

data class ReportHistoryItem(
    val id: Long = 0L,
    val createdAtMillis: Long,
    val sourceName: String,
    val transactions: Int,
    val credibilityScore: Int,
    val credibilityBand: String,
    val netCashflow: Double,
    val parseQuality: Double,
    val reportType: String,
    val strengths: String,
    val risks: String
)
