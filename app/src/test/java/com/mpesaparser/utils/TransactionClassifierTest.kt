package com.mpesaparser.utils

import com.mpesaparser.domain.model.CategoryRule
import com.mpesaparser.domain.model.MpesaTransaction
import com.mpesaparser.domain.model.TransactionCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionClassifierTest {

    @Test
    fun appliesDefaultRules() {
        val classifier = TransactionClassifier()
        val tx = MpesaTransaction(
            date = "2026-02-10",
            time = "12:15:35",
            transactionType = "Pay Bill Online",
            amount = -300.0,
            counterparty = "Utility Company",
            reference = "ABC123",
            balance = 1000.0
        )
        val result = classifier.applyCategories(listOf(tx), emptyList())
        assertEquals(TransactionCategory.PAY_BILL, result.first().category)
    }

    @Test
    fun customRuleOverridesFallback() {
        val classifier = TransactionClassifier()
        val tx = MpesaTransaction(
            date = "2026-02-10",
            time = "12:15:35",
            transactionType = "Custom Transfer",
            amount = -100.0,
            counterparty = "School Vendor",
            reference = "ABC123",
            balance = 1000.0
        )
        val rules = listOf(CategoryRule("school", TransactionCategory.BUSINESS))
        val result = classifier.applyCategories(listOf(tx), rules)
        assertEquals(TransactionCategory.BUSINESS, result.first().category)
    }
}
