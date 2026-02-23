@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mpesaparser.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mpesaparser.domain.model.AccountingInsights
import com.mpesaparser.domain.model.CategoryRule
import com.mpesaparser.domain.model.MpesaTransaction
import java.util.Locale
import kotlin.math.abs

@Composable
fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
fun AccountingInsightsCard(insights: AccountingInsights) {
    val scoreProgress = (insights.credibilityScore / 100f).coerceIn(0f, 1f)
    val scoreDrivers = insights.scoreComponents
        .sortedByDescending { it.points.toDouble() / it.maxPoints.toDouble() }
        .take(2)
        .joinToString(" | ") { "${it.name}: ${it.points}/${it.maxPoints}" }

    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f))) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Credibility ${insights.credibilityScore}/100 (${insights.credibilityBand.label})",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            LinearProgressIndicator(progress = { scoreProgress }, modifier = Modifier.fillMaxWidth())
            Text(
                "High-value activity (> KES ${String.format(Locale.US, "%,.0f", insights.highValueThreshold)}): In ${insights.highValueCreditCount} | Out ${insights.highValueDebitCount}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Coverage ${ratio(insights.inflowOutflowRatio)} | Savings ${percent(insights.savingsRate)} | Completion ${percent(insights.completionRate)}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Sections: Loans ${insights.loanSection.transactionCount} | Above 5K ${insights.above5kSection.transactionCount} | Other ${insights.otherSection.transactionCount}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Low-balance days ${insights.lowBalanceDays} (${percent(insights.lowBalanceDayRatio)}) | Obligation burden ${percent(insights.obligationBurdenRatio)}",
                style = MaterialTheme.typography.bodySmall
            )
            if (scoreDrivers.isNotBlank()) {
                Text("Score drivers: $scoreDrivers", style = MaterialTheme.typography.bodySmall)
            }
            if (insights.strengths.isNotEmpty()) {
                Text(
                    "Strengths: ${insights.strengths.take(2).joinToString(" | ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF18794E)
                )
            }
            if (insights.riskSignals.isNotEmpty()) {
                Text(
                    "Watchouts: ${insights.riskSignals.take(2).joinToString(" | ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB42318)
                )
            }
        }
    }
}

@Composable
fun TransactionItem(tx: MpesaTransaction) {
    val amountColor = if (tx.amount >= 0) Color(0xFF18794E) else Color(0xFFC1121F)
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(tx.transactionType, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Text(if (tx.amount >= 0) "+${currency(tx.amount)}" else "-${currency(abs(tx.amount))}", color = amountColor, fontWeight = FontWeight.Bold)
            }
            Text("${tx.date} ${tx.time}", style = MaterialTheme.typography.bodySmall)
            Text("Category: ${tx.category.label} | Status: ${tx.status}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (tx.counterparty.isBlank()) "Counterparty: N/A" else "Counterparty: ${tx.counterparty}", maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("Ref: ${tx.reference} | Balance: ${currency(tx.balance)}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun UnparsedSamplesDialog(samples: List<String>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Unparsed Samples") },
        text = {
            Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (samples.isEmpty()) {
                    Text("No samples.")
                } else {
                    samples.forEachIndexed { index, sample ->
                        Text("${index + 1}. $sample", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
fun PasswordDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter PDF Password") },
        text = {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true
            )
        },
        confirmButton = { Button(onClick = { onConfirm(password) }) { Text("Process") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun RuleItem(rule: CategoryRule, onRemove: (CategoryRule) -> Unit) {
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                "\"${rule.keyword}\" -> ${rule.category.label} (p${rule.priority}${if (rule.isRegex) ", regex" else ""})",
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            OutlinedButton(onClick = { onRemove(rule) }) { Text("Delete") }
        }
    }
}

private fun currency(value: Double): String = "KES ${String.format(Locale.US, "%,.2f", value)}"
private fun percent(value: Double): String = "${String.format(Locale.US, "%.1f", value * 100)}%"
private fun ratio(value: Double): String = "${String.format(Locale.US, "%.2f", value)}x"
