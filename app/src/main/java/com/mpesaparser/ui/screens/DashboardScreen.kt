package com.mpesaparser.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mpesaparser.ui.components.AccountingInsightsCard
import com.mpesaparser.ui.components.LoanOfficerDashboard
import com.mpesaparser.ui.components.Metric
import com.mpesaparser.ui.components.PasswordDialog
import com.mpesaparser.ui.components.UnparsedSamplesDialog
import com.mpesaparser.ui.state.UiState
import com.mpesaparser.viewmodel.MainViewModel
import kotlin.math.abs

@Composable
fun HeaderSection() {
    Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.primary.copy(alpha = 0.95f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("M-Pesa Parser", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimary)
            Text("Credit-grade analytics, exportable reports, and rule-driven classification.", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f))
        }
    }
}

@Composable
fun FileSelectionSection(selectedUri: Uri?, picker: () -> Unit) {
    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { picker() }, modifier = Modifier.fillMaxWidth()) {
                Text("Select M-Pesa Statement")
            }
            selectedUri?.let {
                Text("Selected: ${it.lastPathSegment ?: "statement.pdf"}")
            }
        }
    }
}

@Composable
fun ProcessingSection(uiState: UiState, onCancel: () -> Unit) {
    if (uiState.isProcessing) {
        Card {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Processing statement...", style = MaterialTheme.typography.titleMedium)
                LinearProgressIndicator(progress = uiState.progress, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${(uiState.progress * 100).toInt()}%")
                    OutlinedButton(onClick = onCancel) { Text("Cancel") }
                }
            }
        }
    }
}

@Composable
fun MetricsSection(incoming: Double, outgoing: Double, net: Double, transactionCount: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Metric("Transactions", transactionCount.toString(), Modifier.weight(1f))
        Metric("Inflow", "KES ${"%.2f".format(incoming)}", Modifier.weight(1f))
        Metric("Outflow", "KES ${"%.2f".format(outgoing)}", Modifier.weight(1f))
        Metric("Net", "KES ${"%.2f".format(net)}", Modifier.weight(1f))
    }
}

@Composable
fun ParseQualitySection(uiState: UiState, onShowUnparsed: () -> Unit) {
    if (uiState.diagnostics.candidateRows > 0) {
        Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))) {
            Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                val rate = uiState.diagnostics.parseRate * 100f
                Text("Parse quality ${String.format("%.2f", rate)}%")
                if (uiState.diagnostics.unmatchedSamples.isNotEmpty()) {
                    OutlinedButton(onClick = onShowUnparsed) {
                        Text("Unparsed ${uiState.diagnostics.unmatchedSamples.size}")
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Mode: ${uiState.diagnostics.parserMode}", style = MaterialTheme.typography.bodySmall)
                Text(
                    "Dupes: ${uiState.diagnostics.duplicatesRemoved} | Confidence ${String.format("%.0f", uiState.diagnostics.confidenceScore * 100f)}%",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun DashboardScreen(viewModel: MainViewModel, uiState: UiState) {
    val context = LocalContext.current
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showUnparsedDialog by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedUri = uri
            showPasswordDialog = true
        }
    }

    val incoming = remember(uiState.transactions) { uiState.transactions.filter { it.amount > 0 }.sumOf { it.amount } }
    val outgoing = remember(uiState.transactions) { uiState.transactions.filter { it.amount < 0 }.sumOf { abs(it.amount) } }
    val net = incoming - outgoing

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HeaderSection()

        FileSelectionSection(selectedUri, { picker.launch(arrayOf("application/pdf")) })

        ProcessingSection(uiState, { viewModel.cancelProcessing() })

        if (uiState.showPreview) {
            MetricsSection(incoming, outgoing, net, uiState.transactions.size)

            ParseQualitySection(uiState, { showUnparsedDialog = true })

            AccountingInsightsCard(uiState.insights)

            LoanOfficerDashboard(
                insights = uiState.insights,
                eligibility = uiState.loanEligibility,
                dti = uiState.dtiAnalysis,
                redFlags = uiState.loanRedFlags,
                benchmarking = uiState.benchmarking
            )

            uiState.loanRecommendation?.let { rec ->
                Card {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Loan Recommendation", style = MaterialTheme.typography.titleMedium)
                        Text("Recommended Amount: KES ${String.format("%,.0f", rec.recommendedAmount)}")
                        Text("Recommended Term: ${rec.recommendedTerm} months")
                        Text("Estimated Monthly Payment: KES ${String.format("%,.0f", rec.monthlyPayment)}")
                    }
                }
            }
            uiState.repaymentCapacity?.let { capacity ->
                Card(
                    colors = CardDefaults.cardColors(
                        if (capacity.canRepay) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            if (capacity.canRepay) "Repayment Capacity: Adequate" else "Repayment Capacity: Risky",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text("Coverage Ratio: ${String.format("%.2f", capacity.coverageRatio)}x")
                        Text("Monthly Surplus: KES ${String.format("%,.0f", capacity.monthlySurplus)}")
                        Text("Loan Payment: KES ${String.format("%,.0f", capacity.monthlyPayment)}")
                    }
                }
            }
        }
    }

    if (showPasswordDialog) {
        PasswordDialog(
            onDismiss = { showPasswordDialog = false },
            onConfirm = { password ->
                selectedUri?.let { viewModel.processPdf(it, password, context) }
                showPasswordDialog = false
            }
        )
    }

    if (showUnparsedDialog) {
        UnparsedSamplesDialog(uiState.diagnostics.unmatchedSamples) { showUnparsedDialog = false }
    }
}
