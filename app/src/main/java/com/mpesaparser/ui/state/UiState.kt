package com.mpesaparser.ui.state

import com.mpesaparser.domain.model.CategoryRule
import com.mpesaparser.domain.model.AccountingInsights
import com.mpesaparser.domain.model.MpesaTransaction
import com.mpesaparser.domain.model.ParseDiagnostics
import com.mpesaparser.domain.model.ReportHistoryItem
import com.mpesaparser.domain.model.ScoringConfig
import com.mpesaparser.domain.model.TransactionCategory
import com.mpesaparser.utils.BenchmarkingResult
import com.mpesaparser.utils.DTIAnalysis
import com.mpesaparser.utils.LoanEligibilityResult
import com.mpesaparser.utils.LoanRedFlags
import com.mpesaparser.utils.LoanRecommendation
import com.mpesaparser.utils.RepaymentCapacityResult

data class UiState(
    val isProcessing: Boolean = false,
    val isExporting: Boolean = false,
    val transactions: List<MpesaTransaction> = emptyList(),
    val visibleTransactions: List<MpesaTransaction> = emptyList(),
    val diagnostics: ParseDiagnostics = ParseDiagnostics(),
    val insights: AccountingInsights = AccountingInsights(),
    val scoringConfig: ScoringConfig = ScoringConfig(),
    val reportHistory: List<ReportHistoryItem> = emptyList(),
    val customRules: List<CategoryRule> = emptyList(),
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val progress: Float = 0f,
    val showPreview: Boolean = false,
    val sourceName: String = "statement.pdf",
    val searchQuery: String = "",
    val selectedCategory: TransactionCategory? = null,
    val sortOrder: TransactionSortOrder = TransactionSortOrder.DATE_DESC,
    val loanEligibility: LoanEligibilityResult? = null,
    val dtiAnalysis: DTIAnalysis? = null,
    val loanRedFlags: LoanRedFlags? = null,
    val benchmarking: BenchmarkingResult? = null,
    val loanRecommendation: LoanRecommendation? = null,
    val repaymentCapacity: RepaymentCapacityResult? = null
)
