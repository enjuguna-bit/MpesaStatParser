package com.mpesaparser.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mpesaparser.data.local.AppDatabase
import com.mpesaparser.data.local.ReportHistoryRepository
import com.mpesaparser.domain.model.CategoryRule
import com.mpesaparser.domain.model.MpesaTransaction
import com.mpesaparser.domain.model.ReportFormat
import com.mpesaparser.domain.model.TransactionCategory
import com.mpesaparser.ui.state.UiState
import com.mpesaparser.ui.state.TransactionSortOrder
import com.mpesaparser.utils.AccountingAnalyzer
import com.mpesaparser.utils.ExcelGenerator
import com.mpesaparser.utils.BorrowerBenchmarker
import com.mpesaparser.utils.DTICalculator
import com.mpesaparser.utils.LoanEligibilityChecker
import com.mpesaparser.utils.LoanRecommender
import com.mpesaparser.utils.PdfReportGenerator
import com.mpesaparser.utils.PdfProcessor
import com.mpesaparser.utils.RedFlagsDetector
import com.mpesaparser.utils.RepaymentCapacityPredictor
import com.mpesaparser.utils.ScoringConfigStore
import com.mpesaparser.utils.TransactionClassifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.io.IOException
import android.util.Log
import kotlin.math.abs

class MainViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val pdfProcessor = PdfProcessor()
    private val excelGenerator = ExcelGenerator()
    private val pdfReportGenerator = PdfReportGenerator()
    private val classifier = TransactionClassifier()
    private val accountingAnalyzer = AccountingAnalyzer()
    private val scoringConfigStore = ScoringConfigStore()
    private val loanEligibilityChecker = LoanEligibilityChecker()
    private val dtiCalculator = DTICalculator()
    private val redFlagsDetector = RedFlagsDetector()
    private val borrowerBenchmarker = BorrowerBenchmarker()
    private val loanRecommender = LoanRecommender()
    private val repaymentCapacityPredictor = RepaymentCapacityPredictor()
    private var processJob: Job? = null
    private var initializedRules = false
    private var reportHistoryRepository: ReportHistoryRepository? = null

    private val dateTimeFormats = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
    )

    fun initialize(context: Context) {
        if (initializedRules) {
            return
        }
        val rules = classifier.loadCustomRules(context)
        val config = scoringConfigStore.load(context)
        val state = _uiState.value.copy(customRules = rules, scoringConfig = config)
        _uiState.value = state.copy(visibleTransactions = filterAndSort(state))
        if (reportHistoryRepository == null) {
            reportHistoryRepository = ReportHistoryRepository(AppDatabase.get(context).reportHistoryDao())
            viewModelScope.launch {
                reportHistoryRepository?.observeRecent()?.collect { history ->
                    _uiState.value = _uiState.value.copy(reportHistory = history)
                }
            }
        }
        initializedRules = true
    }

    fun processPdf(uri: Uri, password: String, context: Context) {
        if (password.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Password cannot be empty")
            return
        }
        if (uri.scheme != "content") {
            _uiState.value = _uiState.value.copy(errorMessage = "Invalid file selected")
            return
        }
        processJob?.cancel()
        processJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isProcessing = true,
                errorMessage = null,
                infoMessage = null,
                progress = 0f,
                showPreview = false,
                sourceName = uri.lastPathSegment ?: "statement.pdf"
            )
            try {
                val parseResult = pdfProcessor.processPdf(uri, password, context) { progress ->
                    _uiState.value = _uiState.value.copy(progress = progress.coerceIn(0f, 1f))
                }
                val categorized = classifier.applyCategories(parseResult.transactions, _uiState.value.customRules)
                val insights = withContext(Dispatchers.Default) {
                    accountingAnalyzer.analyze(
                        transactions = categorized,
                        diagnostics = parseResult.diagnostics,
                        config = _uiState.value.scoringConfig
                    )
                }
                val baseState = _uiState.value.copy(
                    isProcessing = false,
                    progress = 1f,
                    transactions = categorized,
                    diagnostics = parseResult.diagnostics,
                    insights = insights,
                    showPreview = true,
                    infoMessage = if (categorized.isEmpty()) "No transactions found in the PDF" else "Parsed ${categorized.size} transactions"
                )
                _uiState.value = enrichLoanDecisionState(baseState).copy(visibleTransactions = filterAndSort(baseState))
                Log.d("MainViewModel", "Successfully parsed ${categorized.size} transactions from ${baseState.sourceName}")
            } catch (e: IOException) {
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    errorMessage = "Failed to load PDF: Invalid password, corrupted file, or unsupported format."
                )
            } catch (e: SecurityException) {
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    errorMessage = "Permission denied to access the file."
                )
            } catch (t: Throwable) {
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    errorMessage = "Processing failed: ${t.localizedMessage ?: t.message ?: "Unknown error"}"
                )
            }
        }
    }

    suspend fun generateExcel(context: Context): Uri? = withContext(Dispatchers.IO) {
        generateReport(context, ReportFormat.CSV)
    }

    suspend fun generateReport(context: Context, format: ReportFormat): Uri? = withContext(Dispatchers.IO) {
        val snapshot = _uiState.value
        if (snapshot.transactions.isEmpty()) {
            _uiState.value = snapshot.copy(errorMessage = "No transactions to export")
            return@withContext null
        }
        _uiState.value = snapshot.copy(isExporting = true, errorMessage = null)
        try {
            val uri = when (format) {
                ReportFormat.CSV -> excelGenerator.generateExcel(
                    transactions = snapshot.transactions,
                    diagnostics = snapshot.diagnostics,
                    insights = snapshot.insights,
                    loanEligibility = snapshot.loanEligibility,
                    dtiAnalysis = snapshot.dtiAnalysis,
                    loanRedFlags = snapshot.loanRedFlags,
                    benchmarking = snapshot.benchmarking,
                    loanRecommendation = snapshot.loanRecommendation,
                    repaymentCapacity = snapshot.repaymentCapacity,
                    context = context
                )
                ReportFormat.PDF -> pdfReportGenerator.generateSummaryPdf(
                    insights = snapshot.insights,
                    diagnostics = snapshot.diagnostics,
                    loanEligibility = snapshot.loanEligibility,
                    dtiAnalysis = snapshot.dtiAnalysis,
                    loanRedFlags = snapshot.loanRedFlags,
                    benchmarking = snapshot.benchmarking,
                    loanRecommendation = snapshot.loanRecommendation,
                    repaymentCapacity = snapshot.repaymentCapacity,
                    context = context
                )
            }
            if (uri != null) {
                reportHistoryRepository?.addEntry(
                    sourceName = snapshot.sourceName,
                    reportType = format.label,
                    insights = snapshot.insights,
                    diagnostics = snapshot.diagnostics
                )
            }
            if (uri != null) {
                _uiState.value = _uiState.value.copy(isExporting = false, infoMessage = "${format.label} report ready")
            } else {
                _uiState.value = _uiState.value.copy(isExporting = false, errorMessage = "${format.label} report could not be generated")
            }
            uri
        } catch (t: Throwable) {
            _uiState.value = _uiState.value.copy(
                isExporting = false,
                errorMessage = "Report generation failed: ${t.localizedMessage ?: t.message ?: "Unknown error"}"
            )
            null
        }
    }

    fun cancelProcessing() {
        processJob?.cancel()
        _uiState.value = _uiState.value.copy(isProcessing = false, progress = 0f, errorMessage = "Processing cancelled")
    }

    fun setSearchQuery(query: String) {
        val state = _uiState.value.copy(searchQuery = query)
        _uiState.value = state.copy(visibleTransactions = filterAndSort(state))
    }

    fun setCategoryFilter(category: TransactionCategory?) {
        val state = _uiState.value.copy(selectedCategory = category)
        _uiState.value = state.copy(visibleTransactions = filterAndSort(state))
    }

    fun setSortOrder(sortOrder: TransactionSortOrder) {
        val state = _uiState.value.copy(sortOrder = sortOrder)
        _uiState.value = state.copy(visibleTransactions = filterAndSort(state))
    }

    fun addCategoryRule(
        keyword: String,
        category: TransactionCategory,
        context: Context,
        priority: Int = 100,
        isRegex: Boolean = false,
        excludeKeyword: String = ""
    ) {
        val cleaned = keyword.trim()
        if (cleaned.length < 2) {
            _uiState.value = _uiState.value.copy(errorMessage = "Keyword must be at least 2 characters")
            return
        }
        val rule = CategoryRule(
            keyword = cleaned,
            category = category,
            priority = priority.coerceIn(1, 200),
            isRegex = isRegex,
            excludeKeyword = excludeKeyword.trim()
        )
        val rules = (_uiState.value.customRules + rule)
            .distinctBy {
                "${it.keyword.lowercase(Locale.ROOT)}|${it.category.name}|${it.priority}|${it.isRegex}|${it.excludeKeyword.lowercase(Locale.ROOT)}"
            }
        classifier.saveCustomRules(context, rules)
        recategorizeWithRules(rules, "Rule added")
    }

    fun removeCategoryRule(rule: CategoryRule, context: Context) {
        val rules = _uiState.value.customRules.filterNot {
            it.keyword.equals(rule.keyword, ignoreCase = true) &&
                it.category == rule.category &&
                it.priority == rule.priority &&
                it.isRegex == rule.isRegex &&
                it.excludeKeyword.equals(rule.excludeKeyword, ignoreCase = true)
        }
        classifier.saveCustomRules(context, rules)
        recategorizeWithRules(rules, "Rule removed")
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun clearInfo() {
        _uiState.value = _uiState.value.copy(infoMessage = null)
    }

    private fun recategorizeWithRules(rules: List<CategoryRule>, info: String) {
        val recategorized = classifier.applyCategories(_uiState.value.transactions, rules)
        val recalculated = accountingAnalyzer.analyze(
            transactions = recategorized,
            diagnostics = _uiState.value.diagnostics,
            config = _uiState.value.scoringConfig
        )
        val state = _uiState.value.copy(
            customRules = rules,
            transactions = recategorized,
            insights = recalculated,
            infoMessage = info
        )
        _uiState.value = enrichLoanDecisionState(state).copy(visibleTransactions = filterAndSort(state))
    }

    private fun filterAndSort(state: UiState): List<MpesaTransaction> {
        val query = state.searchQuery.trim().lowercase(Locale.ROOT)
        val filtered = state.transactions.filter { tx ->
            val categoryMatch = state.selectedCategory == null || tx.category == state.selectedCategory
            val queryMatch = query.isBlank() || buildString {
                append(tx.transactionType)
                append(" ")
                append(tx.counterparty)
                append(" ")
                append(tx.reference)
                append(" ")
                append(tx.details)
                append(" ")
                append(tx.status)
            }.lowercase(Locale.ROOT).contains(query)
            categoryMatch && queryMatch
        }

        return when (state.sortOrder) {
            TransactionSortOrder.DATE_DESC -> filtered.sortedByDescending { toEpochMillis(it) }
            TransactionSortOrder.DATE_ASC -> filtered.sortedBy { toEpochMillis(it) }
            TransactionSortOrder.AMOUNT_DESC -> filtered.sortedByDescending { abs(it.amount) }
            TransactionSortOrder.AMOUNT_ASC -> filtered.sortedBy { abs(it.amount) }
        }
    }

    fun updateScoringConfig(
        highValueThreshold: Double,
        reliabilityWeight: Int,
        obligationWeight: Int,
        context: Context
    ) {
        val next = _uiState.value.scoringConfig.copy(
            highValueThreshold = highValueThreshold.coerceAtLeast(1000.0),
            reliabilityWeight = reliabilityWeight.coerceIn(5, 40),
            obligationWeight = obligationWeight.coerceIn(5, 40)
        ).normalized()
        try {
            scoringConfigStore.save(context, next)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(errorMessage = "Failed to save scoring settings")
            return
        }
        val recalculated = accountingAnalyzer.analyze(
            transactions = _uiState.value.transactions,
            diagnostics = _uiState.value.diagnostics,
            config = next
        )
        _uiState.value = _uiState.value.copy(
            scoringConfig = next,
            insights = recalculated,
            infoMessage = "Scoring settings updated"
        ).let { enrichLoanDecisionState(it) }
    }

    private fun enrichLoanDecisionState(state: UiState): UiState {
        if (state.transactions.isEmpty()) {
            return state.copy(
                loanEligibility = null,
                dtiAnalysis = null,
                loanRedFlags = null,
                benchmarking = null,
                loanRecommendation = null,
                repaymentCapacity = null
            )
        }
        val recommendation = loanRecommender.recommendLoan(state.insights)
        val recommendedAmount = recommendation.recommendedAmount.takeIf { it > 0.0 } ?: 0.0
        val recommendedTerm = recommendation.recommendedTerm.takeIf { it > 0 } ?: 12
        return state.copy(
            loanEligibility = loanEligibilityChecker.checkEligibility(state.insights),
            dtiAnalysis = dtiCalculator.calculateDTI(state.transactions, state.insights.activeMonths.coerceAtLeast(1)),
            loanRedFlags = redFlagsDetector.detectLoanRedFlags(state.insights),
            benchmarking = borrowerBenchmarker.benchmarkBorrower(state.insights),
            loanRecommendation = recommendation,
            repaymentCapacity = repaymentCapacityPredictor.predictCapacity(
                insights = state.insights,
                loanAmount = recommendedAmount,
                termMonths = recommendedTerm
            )
        )
    }

    private fun toEpochMillis(transaction: MpesaTransaction): Long {
        val raw = "${transaction.date} ${transaction.time}"
        for (formatter in dateTimeFormats) {
            val value = runCatching { LocalDateTime.parse(raw, formatter) }.getOrNull()
            if (value != null) {
                return value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
        }
        return Long.MIN_VALUE
    }
}

