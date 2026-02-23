package com.mpesaparser.utils

import com.mpesaparser.domain.model.CredibilityBand
import com.mpesaparser.domain.model.MpesaTransaction
import com.mpesaparser.domain.model.ParseDiagnostics
import com.mpesaparser.domain.model.ScoringConfig
import com.mpesaparser.domain.model.TransactionCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountingAnalyzerTest {

    private val analyzer = AccountingAnalyzer()

    @Test
    fun computesHighValueAndAccountingRatios() {
        val transactions = listOf(
            tx("2026-01-05", "10:00:00", 12000.0, 15000.0, TransactionCategory.RECEIVED_MONEY, counterparty = "Client A"),
            tx("2026-01-06", "11:20:00", -6500.0, 8500.0, TransactionCategory.PAY_BILL, counterparty = "Utility"),
            tx("2026-01-07", "13:10:00", -120.0, 8380.0, TransactionCategory.CHARGE, counterparty = "Safaricom"),
            tx("2026-01-10", "07:25:00", -2500.0, 5880.0, TransactionCategory.LOAN, counterparty = "Fuliza"),
            tx("2026-02-03", "09:00:00", 9000.0, 14880.0, TransactionCategory.RECEIVED_MONEY, counterparty = "Client B"),
            tx("2026-02-04", "10:15:00", -5200.0, 9680.0, TransactionCategory.SEND_MONEY, counterparty = "Supplier"),
            tx("2026-02-05", "12:45:00", -1800.0, 7880.0, TransactionCategory.BUY_GOODS, counterparty = "Merchant"),
            tx("2026-02-06", "14:05:00", -100.0, 7780.0, TransactionCategory.CHARGE, counterparty = "Safaricom"),
            tx("2026-02-07", "15:00:00", 7000.0, 14780.0, TransactionCategory.RECEIVED_MONEY, counterparty = "Client C"),
            tx("2026-02-08", "16:30:00", -700.0, 14080.0, TransactionCategory.AIRTIME, counterparty = "Safaricom"),
            tx("2026-02-10", "08:00:00", -6300.0, 7780.0, TransactionCategory.PAY_BILL, status = "Completed", counterparty = "Landlord"),
            tx("2026-02-10", "08:30:00", -400.0, 7380.0, TransactionCategory.CHARGE, status = "Reversed", counterparty = "Safaricom")
        )

        val insights = analyzer.analyze(
            transactions = transactions,
            diagnostics = ParseDiagnostics(candidateRows = 12, parsedRows = 12),
            config = ScoringConfig()
        )

        assertEquals(3, insights.highValueCreditCount)
        assertEquals(3, insights.highValueDebitCount)
        assertEquals(1, insights.loanSection.transactionCount)
        assertEquals(6, insights.above5kSection.transactionCount)
        assertEquals(5, insights.otherSection.transactionCount)
        assertEquals(12, insights.loanSection.transactionCount + insights.above5kSection.transactionCount + insights.otherSection.transactionCount)
        assertTrue(insights.chargeToOutflowRatio > 0.0)
        assertTrue(insights.loanToOutflowRatio > 0.0)
        assertTrue(insights.inflowOutflowRatio > 0.0)
        assertTrue(insights.obligationBurdenRatio > 0.0)
        assertTrue(insights.averageDailyTransactions > 0.0)
        assertTrue(insights.completionRate < 1.0)
        assertTrue(insights.credibilityScore in 0..100)
        assertTrue(insights.scoreComponents.isNotEmpty())
        assertEquals(100, insights.scoreComponents.sumOf { it.maxPoints })
        assertTrue(insights.activeMonths >= 2)
        assertTrue(insights.monthlyBreakdown.isNotEmpty())
        assertTrue(insights.topCounterparty.isNotBlank())
    }

    @Test
    fun marksInsufficientDataForVerySmallSamples() {
        val insights = analyzer.analyze(
            transactions = listOf(
                tx("2026-02-01", "08:00:00", 2000.0, 2000.0, TransactionCategory.RECEIVED_MONEY),
                tx("2026-02-02", "09:00:00", -500.0, 1500.0, TransactionCategory.SEND_MONEY)
            ),
            diagnostics = ParseDiagnostics(candidateRows = 2, parsedRows = 2),
            config = ScoringConfig()
        )

        assertEquals(CredibilityBand.INSUFFICIENT_DATA, insights.credibilityBand)
    }

    private fun tx(
        date: String,
        time: String,
        amount: Double,
        balance: Double,
        category: TransactionCategory,
        status: String = "Completed",
        counterparty: String = "N/A"
    ): MpesaTransaction {
        return MpesaTransaction(
            date = date,
            time = time,
            transactionType = "Tx",
            amount = amount,
            counterparty = counterparty,
            reference = "${date.replace("-", "")}${time.replace(":", "")}",
            balance = balance,
            status = status,
            category = category,
            details = "Details"
        )
    }
}
