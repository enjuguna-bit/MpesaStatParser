package com.mpesaparser.utils

import android.content.Context
import com.mpesaparser.domain.model.CategoryRule
import com.mpesaparser.domain.model.MpesaTransaction
import com.mpesaparser.domain.model.TransactionCategory
import java.util.Locale

class TransactionClassifier {

    companion object {
        private const val PREFS_NAME = "mpesa_rules"
        private const val RULES_KEY = "custom_rules_v1"
    }

    private val defaultRules = listOf(
        CategoryRule("funds received", TransactionCategory.RECEIVED_MONEY, priority = 90),
        CategoryRule("business payment from", TransactionCategory.BUSINESS, priority = 80),
        CategoryRule("customer transfer to", TransactionCategory.SEND_MONEY, priority = 80),
        CategoryRule("customer transfer fuliza", TransactionCategory.OVERDRAFT, priority = 95),
        CategoryRule("pay bill", TransactionCategory.PAY_BILL, priority = 85),
        CategoryRule("buy goods", TransactionCategory.BUY_GOODS, priority = 85),
        CategoryRule("merchant payment", TransactionCategory.BUY_GOODS, priority = 85),
        CategoryRule("bundle purchase", TransactionCategory.BUNDLE, priority = 75),
        CategoryRule("data bundle", TransactionCategory.BUNDLE, priority = 75),
        CategoryRule("airtime", TransactionCategory.AIRTIME, priority = 70),
        CategoryRule("withdraw", TransactionCategory.AGENT_WITHDRAWAL, priority = 85),
        CategoryRule("deposit of funds at agent", TransactionCategory.AGENT_DEPOSIT, priority = 85),
        CategoryRule("loan repayment", TransactionCategory.LOAN, priority = 95),
        CategoryRule("fuliza", TransactionCategory.OVERDRAFT, priority = 95),
        CategoryRule("charge", TransactionCategory.CHARGE, priority = 80),
        CategoryRule("m-pesa overdraw", TransactionCategory.OVERDRAFT, priority = 95)
    )

    fun applyCategories(transactions: List<MpesaTransaction>, customRules: List<CategoryRule>): List<MpesaTransaction> {
        val rules = (customRules + defaultRules)
            .sortedWith(
                compareByDescending<CategoryRule> { it.priority }
                    .thenByDescending { it.keyword.length }
            )
        return transactions.map { tx ->
            tx.copy(category = classify(tx, rules))
        }
    }

    fun loadCustomRules(context: Context): List<CategoryRule> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(RULES_KEY, "").orEmpty()
        if (raw.isBlank()) {
            return emptyList()
        }
        return raw.lines().mapNotNull { line ->
            val parts = line.split("|")
            if (parts.size < 2) {
                return@mapNotNull null
            }
            val keyword = parts[0].trim()
            val category = parts[1].trim()
            val enumCategory = runCatching { TransactionCategory.valueOf(category) }.getOrNull()
            val priority = parts.getOrNull(2)?.trim()?.toIntOrNull() ?: 100
            val isRegex = parts.getOrNull(3)?.trim()?.equals("true", ignoreCase = true) ?: false
            val excludeKeyword = parts.getOrNull(4)?.trim().orEmpty()
            if (keyword.isBlank() || enumCategory == null) {
                null
            } else {
                CategoryRule(
                    keyword = keyword,
                    category = enumCategory,
                    priority = priority,
                    isRegex = isRegex,
                    excludeKeyword = excludeKeyword
                )
            }
        }
    }

    fun saveCustomRules(context: Context, rules: List<CategoryRule>) {
        val serialized = rules.joinToString("\n") {
            "${sanitizeKeyword(it.keyword)}|${it.category.name}|${it.priority}|${it.isRegex}|${sanitizeKeyword(it.excludeKeyword)}"
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(RULES_KEY, serialized)
            .apply()
    }

    private fun classify(transaction: MpesaTransaction, rules: List<CategoryRule>): TransactionCategory {
        val haystack = buildString {
            append(transaction.transactionType)
            append(" ")
            append(transaction.counterparty)
            append(" ")
            append(transaction.details)
        }.lowercase(Locale.ROOT)

        val keywordMatch = rules.firstOrNull { rule ->
            matchesRule(haystack, rule)
        }
        if (keywordMatch != null) {
            return keywordMatch.category
        }

        return when {
            transaction.amount > 0 -> TransactionCategory.RECEIVED_MONEY
            transaction.amount < 0 -> TransactionCategory.SEND_MONEY
            else -> TransactionCategory.OTHER
        }
    }

    private fun sanitizeKeyword(keyword: String): String {
        return keyword.replace("|", " ").replace("\n", " ").trim()
    }

    private fun matchesRule(haystack: String, rule: CategoryRule): Boolean {
        val includeMatch = if (rule.isRegex) {
            runCatching { Regex(rule.keyword, RegexOption.IGNORE_CASE).containsMatchIn(haystack) }.getOrDefault(false)
        } else {
            haystack.contains(rule.keyword.lowercase(Locale.ROOT))
        }
        if (!includeMatch) {
            return false
        }
        if (rule.excludeKeyword.isBlank()) {
            return true
        }
        val exclude = rule.excludeKeyword.lowercase(Locale.ROOT)
        return !haystack.contains(exclude)
    }
}
