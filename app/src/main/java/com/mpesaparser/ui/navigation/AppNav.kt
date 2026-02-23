package com.mpesaparser.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Rule
import androidx.compose.ui.graphics.vector.ImageVector

sealed class AppScreen(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    data object Dashboard : AppScreen("dashboard", "Dashboard", Icons.Filled.Assessment)
    data object Transactions : AppScreen("transactions", "Transactions", Icons.Filled.ListAlt)
    data object Rules : AppScreen("rules", "Rules", Icons.Filled.Rule)
    data object Reports : AppScreen("reports", "Reports", Icons.Filled.History)
}
