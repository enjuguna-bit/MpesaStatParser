package com.mpesaparser.domain.model

data class ParseDiagnostics(
    val candidateRows: Int = 0,
    val parsedRows: Int = 0,
    val unmatchedSamples: List<String> = emptyList(),
    val duplicatesRemoved: Int = 0,
    val parserMode: String = "Unknown"
) {
    val parseRate: Float
        get() = if (candidateRows == 0) 0f else parsedRows.toFloat() / candidateRows.toFloat()

    val confidenceScore: Float
        get() {
            val quality = parseRate.coerceIn(0f, 1f)
            val duplicatePenalty = (duplicatesRemoved.toFloat() / (candidateRows.coerceAtLeast(1))).coerceIn(0f, 0.3f)
            return (quality - duplicatePenalty).coerceIn(0f, 1f)
        }
}
