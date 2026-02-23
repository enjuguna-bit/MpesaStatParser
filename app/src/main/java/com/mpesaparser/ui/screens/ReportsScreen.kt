@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mpesaparser.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mpesaparser.domain.model.ReportFormat
import com.mpesaparser.ui.state.UiState
import com.mpesaparser.viewmodel.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ReportsScreen(
    viewModel: MainViewModel,
    uiState: UiState,
    scope: CoroutineScope
) {
    val context = LocalContext.current
    var thresholdText by remember { mutableStateOf(uiState.scoringConfig.highValueThreshold.toInt().toString()) }
    var reliabilityText by remember { mutableStateOf(uiState.scoringConfig.reliabilityWeight.toString()) }
    var obligationText by remember { mutableStateOf(uiState.scoringConfig.obligationWeight.toString()) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Generate Report", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            scope.launch {
                                val uri = viewModel.generateReport(context, ReportFormat.CSV)
                                uri?.let {
                                    shareReport(context, it, ReportFormat.CSV)
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !uiState.isExporting
                    ) { Text("Download CSV") }
                    Button(
                        onClick = {
                            scope.launch {
                                val uri = viewModel.generateReport(context, ReportFormat.PDF)
                                uri?.let {
                                    shareReport(context, it, ReportFormat.PDF)
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !uiState.isExporting
                    ) { Text("Download PDF") }
                }
                if (uiState.isExporting) {
                    Text("Preparing report...", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Card {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Scoring Configuration", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = thresholdText,
                    onValueChange = { thresholdText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("High-Value Threshold (KES)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = reliabilityText,
                    onValueChange = { reliabilityText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Reliability Weight") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = obligationText,
                    onValueChange = { obligationText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Obligation Weight") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = {
                        viewModel.updateScoringConfig(
                            highValueThreshold = thresholdText.toDoubleOrNull() ?: 5000.0,
                            reliabilityWeight = reliabilityText.toIntOrNull() ?: uiState.scoringConfig.reliabilityWeight,
                            obligationWeight = obligationText.toIntOrNull() ?: uiState.scoringConfig.obligationWeight,
                            context = context
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Apply Settings") }
            }
        }

        Card {
            Column(
                Modifier.padding(12.dp).heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Report History", style = MaterialTheme.typography.titleMedium)
                if (uiState.reportHistory.isEmpty()) {
                    Text("No reports saved yet.")
                } else {
                    uiState.reportHistory.forEach { item ->
                        val date = Instant.ofEpochMilli(item.createdAtMillis)
                            .atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Text("${item.reportType} - ${item.credibilityScore}/100 (${item.credibilityBand})", style = MaterialTheme.typography.titleSmall)
                            Text("${item.sourceName} - $date - ${item.transactions} tx", style = MaterialTheme.typography.bodySmall)
                            if (item.strengths.isNotBlank()) {
                                Text("Strengths: ${item.strengths}", style = MaterialTheme.typography.bodySmall)
                            }
                            if (item.risks.isNotBlank()) {
                                Text("Risks: ${item.risks}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Divider()
                    }
                }
            }
        }
    }
}

private fun shareReport(context: android.content.Context, uri: android.net.Uri, format: ReportFormat) {
    try {
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = format.mimeType
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                "Share ${format.label}"
            )
        )
    } catch (_: ActivityNotFoundException) {
        // Silently ignore; snackbar is handled by caller.
    }
}
