@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mpesaparser.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mpesaparser.domain.model.TransactionCategory
import com.mpesaparser.ui.components.RuleItem
import com.mpesaparser.ui.state.UiState
import com.mpesaparser.viewmodel.MainViewModel

@Composable
fun RulesScreen(viewModel: MainViewModel, uiState: UiState) {
    val context = LocalContext.current
    var keyword by remember { mutableStateOf("") }
    var excludeKeyword by remember { mutableStateOf("") }
    var priorityText by remember { mutableStateOf("100") }
    var selectedCategory by remember { mutableStateOf(TransactionCategory.OTHER) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var useRegex by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = keyword,
            onValueChange = { keyword = it },
            label = { Text("Keyword / Regex") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = excludeKeyword,
            onValueChange = { excludeKeyword = it },
            label = { Text("Exclude keyword (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = priorityText,
            onValueChange = { priorityText = it.filter { ch -> ch.isDigit() } },
            label = { Text("Priority (1-200)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Box {
                OutlinedButton(onClick = { categoryMenuExpanded = true }) {
                    Text("Category: ${selectedCategory.label}")
                }
                DropdownMenu(expanded = categoryMenuExpanded, onDismissRequest = { categoryMenuExpanded = false }) {
                    TransactionCategory.entries.forEach { category ->
                        DropdownMenuItem(text = { Text(category.label) }, onClick = {
                            selectedCategory = category
                            categoryMenuExpanded = false
                        })
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Regex")
                Switch(checked = useRegex, onCheckedChange = { useRegex = it })
            }
        }

        Button(
            onClick = {
                val priority = priorityText.toIntOrNull() ?: 100
                viewModel.addCategoryRule(
                    keyword = keyword,
                    category = selectedCategory,
                    context = context,
                    priority = priority,
                    isRegex = useRegex,
                    excludeKeyword = excludeKeyword
                )
                keyword = ""
                excludeKeyword = ""
                priorityText = "100"
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Add Rule") }

        Column(
            Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (uiState.customRules.isEmpty()) {
                Text("No custom rules.")
            } else {
                uiState.customRules.forEach { rule ->
                    RuleItem(rule = rule, onRemove = { viewModel.removeCategoryRule(it, context) })
                }
            }
        }
    }
}
