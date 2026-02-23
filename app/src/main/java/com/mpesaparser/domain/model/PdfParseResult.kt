package com.mpesaparser.domain.model

data class PdfParseResult(
    val transactions: List<MpesaTransaction>,
    val diagnostics: ParseDiagnostics
)
