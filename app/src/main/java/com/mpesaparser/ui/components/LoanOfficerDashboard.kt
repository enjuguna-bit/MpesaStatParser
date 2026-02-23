package com.mpesaparser.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mpesaparser.domain.model.AccountingInsights
import com.mpesaparser.utils.BenchmarkingResult
import com.mpesaparser.utils.DTIAnalysis
import com.mpesaparser.utils.LoanEligibilityResult
import com.mpesaparser.utils.LoanRedFlags

@Composable
fun LoanOfficerDashboard(
    insights: AccountingInsights,
    eligibility: LoanEligibilityResult?,
    dti: DTIAnalysis?,
    redFlags: LoanRedFlags?,
    benchmarking: BenchmarkingResult?
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Loan Officer Dashboard", style = MaterialTheme.typography.headlineSmall)

        // Eligibility Status
        eligibility?.let {
            Card(colors = CardDefaults.cardColors(if (it.isEligible) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Eligibility: ${if (it.isEligible) "Eligible" else "Not Eligible"}", style = MaterialTheme.typography.titleMedium)
                    it.reasons.forEach { reason ->
                        Text("• $reason", style = MaterialTheme.typography.bodySmall)
                    }
                    it.recommendedAmount?.let { amount ->
                        Text("Recommended Loan Amount: KES ${String.format("%,.0f", amount)}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        // DTI Analysis
        dti?.let {
            Card {
                Column(Modifier.padding(12.dp)) {
                    Text("Debt-to-Income Ratio", style = MaterialTheme.typography.titleMedium)
                    Text("DTI: ${String.format("%.2f", it.dtiRatio)} (${it.riskLevel})")
                    Text("Monthly Debt: KES ${String.format("%,.0f", it.monthlyDebtPayments)}")
                    Text("Monthly Income: KES ${String.format("%,.0f", it.monthlyIncome)}")
                }
            }
        }

        // Red Flags
        redFlags?.let {
            if (it.flags.isNotEmpty()) {
                Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Red Flags (Severity: ${it.severity})", style = MaterialTheme.typography.titleMedium)
                        it.flags.forEach { flag ->
                            Text("• $flag", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        // Benchmarking
        benchmarking?.let {
            Card {
                Column(Modifier.padding(12.dp)) {
                    Text("Benchmarking: ${it.overallRating}", style = MaterialTheme.typography.titleMedium)
                    it.comparisons.forEach { comp ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${comp.metric}: ${String.format("%.2f", comp.value)}")
                            Text(comp.status, color = when (comp.status) {
                                "Above" -> MaterialTheme.colorScheme.primary
                                "Below" -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurface
                            })
                        }
                    }
                }
            }
        }

        // Key Metrics
        Card {
            Column(Modifier.padding(12.dp)) {
                Text("Key Metrics", style = MaterialTheme.typography.titleMedium)
                Text("Credibility Score: ${insights.credibilityScore}")
                Text("Net Cashflow: KES ${String.format("%,.0f", insights.netCashflow)}")
                Text("Savings Rate: ${String.format("%.2f", insights.savingsRate)}")
                Text("Low Balance Days: ${insights.lowBalanceDays} (${String.format("%.1f", insights.lowBalanceDayRatio * 100)}%)")
            }
        }
    }
}
