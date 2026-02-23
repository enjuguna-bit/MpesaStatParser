package com.mpesaparser.utils

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class PdfProcessorTest {

    @Test
    fun testParseTransactions() {
        val text = """
            15/01/2024 14:30:22 Received KES 1,500.00 from JOHN DOE. Ref: ABC123 Balance KES 2,500.00
            16/01/2024 15:45:10 Sent KES 500.00 to JANE SMITH. Ref: DEF456 Balance KES 2,000.00
        """.trimIndent()
        val processor = PdfProcessor()
        val transactions = processor.parseTransactions(text)
        assertEquals(2, transactions.size)
        val tx1 = transactions[0]
        assertEquals("15/01/2024", tx1.date)
        assertEquals("14:30:22", tx1.time)
        assertEquals("Received", tx1.transactionType)
        assertEquals(1500.0, tx1.amount, 0.001)
        assertEquals("JOHN DOE", tx1.counterparty)
        assertEquals("ABC123", tx1.reference)
        assertEquals(2500.0, tx1.balance, 0.001)
    }

    @Test
    fun testParseTransactionsWithInvalidData() {
        val text = "Invalid line without match"
        val processor = PdfProcessor()
        val transactions = processor.parseTransactions(text)
        assertEquals(0, transactions.size)
    }

    @Test
    fun testParseModernMpesaStatementFormat() {
        val text = """
            Receipt No. Completion Time Details Transaction Status Paid In Withdrawn Balance
            UBBOY66TYM 2026-02-11 11:38:41 Pay Bill Online to 3575329 -
            LIPANA TECHNOLOGIES  LIMITED
            Acc. Bulksms
            Completed -60.00 835.10
            UBB016DUAM 2026-02-11 06:13:14 Funds received from -
            07******833 FAITH NGANGA
            Completed 600.00 1,359.10
        """.trimIndent()

        val processor = PdfProcessor()
        val transactions = processor.parseTransactions(text)

        assertEquals(2, transactions.size)

        val first = transactions[0]
        assertEquals("2026-02-11", first.date)
        assertEquals("11:38:41", first.time)
        assertEquals("Pay Bill Online", first.transactionType)
        assertEquals(-60.0, first.amount, 0.001)
        assertEquals("LIPANA TECHNOLOGIES LIMITED Acc. Bulksms", first.counterparty)
        assertEquals("UBBOY66TYM", first.reference)
        assertEquals(835.10, first.balance, 0.001)

        val second = transactions[1]
        assertEquals("Funds received", second.transactionType)
        assertEquals(600.0, second.amount, 0.001)
        assertEquals("07******833 FAITH NGANGA", second.counterparty)
        assertEquals(1359.10, second.balance, 0.001)
    }

    @Test
    fun testParseModernStatementInlineStatus() {
        val text = """
            UBAOY63FLW 2026-02-10 12:15:35 Pay Bill Charge Completed -25.00 0.00
            UBAOY63FLW 2026-02-10 12:15:35 OverDraft of Credit Party Completed 1,281.08 3,088.00
        """.trimIndent()

        val processor = PdfProcessor()
        val transactions = processor.parseTransactions(text)
        assertEquals(2, transactions.size)
        assertEquals("Completed", transactions[0].status)
        assertEquals(-25.0, transactions[0].amount, 0.001)
        assertEquals(1281.08, transactions[1].amount, 0.001)
    }

    @Test
    fun testParseEmptyText() {
        val processor = PdfProcessor()
        val transactions = processor.parseTransactions("")
        assertEquals(0, transactions.size)
    }

    @Test
    fun testParseWithZeroAmount() {
        val text = "15/01/2024 14:30:22 Received KES 0.00 from JOHN DOE. Ref: ABC123 Balance KES 2500.00"
        val processor = PdfProcessor()
        val transactions = processor.parseTransactions(text)
        assertEquals(0, transactions.size)
    }
}
