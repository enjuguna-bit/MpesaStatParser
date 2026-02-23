@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mpesaparser.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mpesaparser.domain.model.TransactionCategory
import com.mpesaparser.ui.components.TransactionItem
import com.mpesaparser.ui.state.TransactionSortOrder
import com.mpesaparser.ui.state.UiState
import com.mpesaparser.viewmodel.MainViewModel

@Composable
fun TransactionsScreen(viewModel: MainViewModel, uiState: UiState) {
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = viewModel::setSearchQuery,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search transactions") },
            singleLine = true
        )

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = uiState.selectedCategory == null,
                onClick = { viewModel.setCategoryFilter(null) },
                label = { Text("All") }
            )
            TransactionCategory.entries.forEach { category ->
                FilterChip(
                    selected = uiState.selectedCategory == category,
                    onClick = { viewModel.setCategoryFilter(category) },
                    label = { Text(category.label) }
                )
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                OutlinedButton(onClick = { sortMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Sort: ${uiState.sortOrder.label}")
                }
                DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                    TransactionSortOrder.entries.forEach { order ->
                        DropdownMenuItem(
                            text = { Text(order.label) },
                            onClick = {
                                viewModel.setSortOrder(order)
                                sortMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth().weight(1f, fill = false)) {
            if (uiState.visibleTransactions.isEmpty()) {
                Box(Modifier.fillMaxWidth().heightIn(min = 180.dp), contentAlignment = Alignment.Center) {
                    Text("No transactions match filters.", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(
                        uiState.visibleTransactions,
                        key = { index, tx ->
                            "${tx.reference}_${tx.date}_${tx.time}_${tx.amount}_${tx.balance}_${tx.details.hashCode()}_$index"
                        }
                    ) { _, tx ->
                        TransactionItem(tx)
                    }
                }
            }
        }
    }
}
