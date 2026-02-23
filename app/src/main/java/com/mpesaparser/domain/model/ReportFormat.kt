package com.mpesaparser.domain.model

enum class ReportFormat(val label: String, val mimeType: String) {
    CSV("Detailed CSV", "text/csv"),
    PDF("Executive PDF", "application/pdf")
}
