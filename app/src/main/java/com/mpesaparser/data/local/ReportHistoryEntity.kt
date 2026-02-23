package com.mpesaparser.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "report_history")
data class ReportHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
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
