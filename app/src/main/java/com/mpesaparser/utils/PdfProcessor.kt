package com.mpesaparser.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.mpesaparser.domain.model.MpesaTransaction
import com.mpesaparser.domain.model.ParseDiagnostics
import com.mpesaparser.domain.model.PdfParseResult
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale
import kotlin.math.abs

class PdfProcessor {

    companion object {
        const val TRANSACTION_REGEX = """(?i)(\d{2}/\d{2}/\d{4})\s+(\d{2}:\d{2}:\d{2})\s+(\w+(?:\s+\w+)*?)\s+KES\s+([\d,]+\.\d{2})(?:\s+(from|to)\s+(.+?))?\.\s+Ref:\s+(\w+)\s+Balance\s+KES\s+([\d,]+\.\d{2})"""
        private val MODERN_TRANSACTION_START_REGEX = Regex("""^([A-Z0-9]{8,})\s+(\d{4}-\d{2}-\d{2})\s+(\d{2}:\d{2}:\d{2})\s+(.+)$""")
        private val STATUS_LINE_REGEX = Regex("""^(Completed|Failed|Reversed|Pending|Cancelled)\s+(.+)$""", RegexOption.IGNORE_CASE)
        private val INLINE_STATUS_REGEX = Regex("""^(.*?)(Completed|Failed|Reversed|Pending|Cancelled)\s+(.+)$""", RegexOption.IGNORE_CASE)
        private val AMOUNT_REGEX = Regex("""[-+]?[\d,]+\.\d{2}""")
    }

    suspend fun processPdf(
        uri: Uri,
        password: String,
        context: Context,
        onProgress: (Float) -> Unit = {}
    ): PdfParseResult = withContext(Dispatchers.IO) {
        val cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
        val size = cursor?.use {
            if (it.moveToFirst()) it.getLong(it.getColumnIndexOrThrow(OpenableColumns.SIZE)) else 0L
        } ?: 0L
        if (size > 50L * 1024 * 1024) {
            throw IOException("PDF file is too large. Maximum size is 50MB.")
        }
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            PDDocument.load(inputStream, password).use { document ->
                val transactions = mutableListOf<MpesaTransaction>()
                var candidateRows = 0
                var parsedRows = 0
                val unmatchedSamples = mutableListOf<String>()
                var modernCandidates = 0
                var legacyCandidates = 0
                val stripper = PDFTextStripper()
                val totalPages = document.numberOfPages.coerceAtLeast(1)

                for (page in 1..totalPages) {
                    stripper.startPage = page
                    stripper.endPage = page
                    val pageText = stripper.getText(document)
                    val outcome = parseTransactionsWithDiagnostics(pageText)
                    transactions.addAll(outcome.transactions)
                    candidateRows += outcome.candidateRows
                    parsedRows += outcome.parsedRows
                    if (outcome.parserMode == "Modern") {
                        modernCandidates += outcome.candidateRows
                    } else if (outcome.parserMode == "Legacy") {
                        legacyCandidates += outcome.candidateRows
                    }
                    if (unmatchedSamples.size < 25 && outcome.unmatchedSamples.isNotEmpty()) {
                        val remaining = 25 - unmatchedSamples.size
                        unmatchedSamples.addAll(outcome.unmatchedSamples.take(remaining))
                    }
                    onProgress(page.toFloat() / totalPages.toFloat())
                }
                val deduplicated = deduplicateTransactions(transactions)
                val duplicatesRemoved = transactions.size - deduplicated.size
                val parserMode = when {
                    modernCandidates > legacyCandidates -> "Modern"
                    legacyCandidates > modernCandidates -> "Legacy"
                    modernCandidates > 0 -> "Modern"
                    else -> "Unknown"
                }

                PdfParseResult(
                    transactions = deduplicated,
                    diagnostics = ParseDiagnostics(
                        candidateRows = candidateRows,
                        parsedRows = deduplicated.size.takeIf { it > 0 } ?: parsedRows,
                        unmatchedSamples = unmatchedSamples,
                        duplicatesRemoved = duplicatesRemoved,
                        parserMode = parserMode
                    )
                )
            }
        } ?: PdfParseResult(emptyList(), ParseDiagnostics())
    }

    fun parseTransactions(text: String): List<MpesaTransaction> {
        return parseTransactionsWithDiagnostics(text).transactions
    }

    private fun parseTransactionsWithDiagnostics(text: String): ParseOutcome {
        val modernTransactions = parseModernStatementTransactions(text)
        if (modernTransactions.parsedRows > 0 || modernTransactions.candidateRows > 0) {
            return modernTransactions
        }
        return parseLegacyStatementTransactions(text)
    }

    private fun parseModernStatementTransactions(text: String): ParseOutcome {
        val transactions = mutableListOf<MpesaTransaction>()
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val unmatched = mutableListOf<String>()
        var index = 0
        var candidateRows = 0
        var parsedRows = 0

        while (index < lines.size) {
            val startMatch = MODERN_TRANSACTION_START_REGEX.matchEntire(lines[index])
            if (startMatch == null) {
                index++
                continue
            }

            candidateRows++
            val reference = startMatch.groupValues[1]
            val date = startMatch.groupValues[2]
            val time = startMatch.groupValues[3]
            var firstDetails = startMatch.groupValues[4]
            var statusLine: String? = null
            val detailsParts = mutableListOf<String>()
            var detailLines = 0

            extractInlineStatus(firstDetails)?.let { inline ->
                firstDetails = inline.first.trim()
                statusLine = inline.second.trim()
            }
            if (firstDetails.isNotBlank()) {
                detailsParts.add(firstDetails)
            }
            index++

            while (index < lines.size) {
                detailLines++
                if (detailLines > 20) break
                val currentLine = lines[index]
                if (MODERN_TRANSACTION_START_REGEX.matches(currentLine) || isTerminatorLine(currentLine)) {
                    break
                }
                if (STATUS_LINE_REGEX.matches(currentLine)) {
                    statusLine = currentLine
                    index++
                    break
                }
                detailsParts.add(currentLine)
                index++
            }

            val resolvedStatusLine = statusLine
            if (resolvedStatusLine == null) {
                recordUnmatched(unmatched, "$reference $date $time ${detailsParts.joinToString(" ")}")
                continue
            }

            val amountAndBalance = parseAmountAndBalance(resolvedStatusLine) ?: continue
            val details = detailsParts.joinToString(" ")
                .replace(Regex("""\s+"""), " ")
                .trim()
            val transactionType = extractTransactionType(details)
            val counterparty = extractCounterparty(details)
            val status = extractStatus(resolvedStatusLine)
            val transaction = MpesaTransaction(
                date = date,
                time = time,
                transactionType = transactionType,
                amount = amountAndBalance.first,
                counterparty = counterparty,
                reference = reference,
                balance = amountAndBalance.second,
                status = status,
                details = details
            )
            if (transaction.amount != 0.0 && transaction.reference.isNotBlank() && transaction.date.isNotBlank()) {
                transactions.add(transaction)
                parsedRows++
            }
        }

        return ParseOutcome(
            transactions = transactions,
            candidateRows = candidateRows,
            parsedRows = parsedRows,
            unmatchedSamples = unmatched,
            parserMode = "Modern"
        )
    }

    private fun parseLegacyStatementTransactions(text: String): ParseOutcome {
        val transactions = mutableListOf<MpesaTransaction>()
        val lines = text.lines()
        val regex = Regex(TRANSACTION_REGEX)
        for (line in lines) {
            val match = regex.find(line.trim())
            if (match != null) {
                val groupValues = match.groupValues
                val date = groupValues[1]
                val time = groupValues[2]
                val transactionType = groupValues[3]
                val amountStr = groupValues[4]
                val counterparty = groupValues[6].takeIf { it.isNotEmpty() } ?: ""
                val ref = groupValues[7]
                val balanceStr = groupValues[8]
                try {
                    val amount = amountStr.replace(",", "").toDouble()
                    val balance = balanceStr.replace(",", "").toDouble()
                    if (amount != 0.0 && ref.isNotBlank() && date.isNotBlank()) {
                        transactions.add(
                            MpesaTransaction(
                                date = date,
                                time = time,
                                transactionType = transactionType,
                                amount = amount,
                                counterparty = counterparty,
                                reference = ref,
                                balance = balance,
                                details = line.trim()
                            )
                        )
                    }
                } catch (e: NumberFormatException) {
                    // Skip invalid amounts
                }
            }
        }
        return ParseOutcome(
            transactions = transactions,
            candidateRows = transactions.size,
            parsedRows = transactions.size,
            parserMode = "Legacy"
        )
    }

    private fun parseAmountAndBalance(statusLine: String): Pair<Double, Double>? {
        val numbers = AMOUNT_REGEX.findAll(statusLine)
            .mapNotNull { parseAmount(it.value) }
            .toList()
        if (numbers.size < 2) {
            return null
        }

        val balance = numbers.last()
        val amount = if (numbers.size == 2) {
            numbers.first()
        } else {
            val paidIn = numbers[numbers.size - 3]
            val withdrawn = numbers[numbers.size - 2]
            when {
                paidIn == 0.0 -> -abs(withdrawn)
                withdrawn == 0.0 -> paidIn
                else -> paidIn - withdrawn
            }
        }
        return amount to balance
    }

    private fun parseAmount(value: String): Double? {
        return value.replace(",", "").toDoubleOrNull()
    }

    private fun extractStatus(statusLine: String): String {
        return STATUS_LINE_REGEX.matchEntire(statusLine)?.groupValues?.get(1) ?: "Completed"
    }

    private fun extractTransactionType(details: String): String {
        val separators = listOf(" to ", " from ", " by - ", " via ")
        for (separator in separators) {
            val index = details.indexOf(separator, ignoreCase = true)
            if (index > 0) {
                return details.substring(0, index).trim()
            }
        }
        return details
    }

    private fun extractCounterparty(details: String): String {
        val markers = listOf(" by - ", " - ", " from ", " to ")
        for (marker in markers) {
            val index = details.indexOf(marker, ignoreCase = true)
            if (index >= 0) {
                val value = details.substring(index + marker.length).trim()
                if (value.isNotEmpty()) {
                    return value
                }
            }
        }
        return ""
    }

    private fun extractInlineStatus(line: String): Pair<String, String>? {
        val match = INLINE_STATUS_REGEX.matchEntire(line) ?: return null
        val details = match.groupValues[1].trim()
        val statusLine = "${match.groupValues[2]} ${match.groupValues[3]}".trim()
        return details to statusLine
    }

    private fun recordUnmatched(unmatched: MutableList<String>, value: String) {
        if (unmatched.size >= 25) {
            return
        }
        unmatched.add(value.take(160))
    }

    private fun isTerminatorLine(line: String): Boolean {
        return line.startsWith("Receipt No.", ignoreCase = true) ||
            line.startsWith("Disclaimer:", ignoreCase = true) ||
            line.startsWith("Statement Verification Code", ignoreCase = true) ||
            line.startsWith("For self-help dial", ignoreCase = true) ||
            line.startsWith("Page ", ignoreCase = true) ||
            line.matches(Regex("""^MVRNET\d+$""", RegexOption.IGNORE_CASE))
    }

    private fun deduplicateTransactions(items: List<MpesaTransaction>): List<MpesaTransaction> {
        if (items.isEmpty()) {
            return items
        }
        val map = LinkedHashMap<String, MpesaTransaction>()
        items.forEach { tx ->
            val key = buildString {
                append(tx.reference.trim())
                append("|")
                append(tx.date.trim())
                append("|")
                append(tx.time.trim())
                append("|")
                append(String.format(Locale.US, "%.2f", tx.amount))
                append("|")
                append(String.format(Locale.US, "%.2f", tx.balance))
                append("|")
                append(tx.transactionType.trim())
            }
            if (!map.containsKey(key)) {
                map[key] = tx
            }
        }
        return map.values.toList()
    }

    private data class ParseOutcome(
        val transactions: List<MpesaTransaction>,
        val candidateRows: Int,
        val parsedRows: Int,
        val unmatchedSamples: List<String> = emptyList(),
        val parserMode: String = "Unknown"
    )
}
