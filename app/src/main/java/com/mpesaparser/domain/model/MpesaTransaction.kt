package com.mpesaparser.domain.model

data class MpesaTransaction(
    val date: String,
    val time: String,
    val transactionType: String,
    val amount: Double,
    val counterparty: String,
    val reference: String,
    val balance: Double,
    val status: String = "Completed",
    val category: TransactionCategory = TransactionCategory.OTHER,
    val details: String = ""
)
