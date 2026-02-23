package com.mpesaparser.domain.model

data class CategoryRule(
    val keyword: String,
    val category: TransactionCategory,
    val priority: Int = 100,
    val isRegex: Boolean = false,
    val excludeKeyword: String = ""
)
