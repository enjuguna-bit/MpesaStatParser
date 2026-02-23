@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mpesaparser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mpesaparser.ui.navigation.AppScreen
import com.mpesaparser.ui.screens.DashboardScreen
import com.mpesaparser.ui.screens.ReportsScreen
import com.mpesaparser.ui.screens.RulesScreen
import com.mpesaparser.ui.screens.TransactionsScreen
import com.mpesaparser.ui.theme.MpesaParserTheme
import com.mpesaparser.viewmodel.MainViewModel
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PDFBoxResourceLoader.init(applicationContext)
        setContent {
            val viewModel: MainViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsState()
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val snackbarHostState = remember { SnackbarHostState() }
            val navController = rememberNavController()

            LaunchedEffect(Unit) { viewModel.initialize(context) }
            LaunchedEffect(uiState.errorMessage) {
                uiState.errorMessage?.let {
                    scope.launch { snackbarHostState.showSnackbar(it) }
                    viewModel.clearError()
                }
            }
            LaunchedEffect(uiState.infoMessage) {
                uiState.infoMessage?.let {
                    scope.launch { snackbarHostState.showSnackbar(it) }
                    viewModel.clearInfo()
                }
            }

            MpesaParserTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                        MaterialTheme.colorScheme.background,
                                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f)
                                    )
                                )
                            )
                    ) {
                        Scaffold(
                            containerColor = Color.Transparent,
                            snackbarHost = { SnackbarHost(snackbarHostState) },
                            bottomBar = { BottomNavBar(navController) }
                        ) { padding ->
                            AppNavHost(
                                navController = navController,
                                padding = padding,
                                viewModel = viewModel,
                                scope = scope,
                                uiState = uiState
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomNavBar(navController: androidx.navigation.NavHostController) {
    val items = listOf(
        AppScreen.Dashboard,
        AppScreen.Transactions,
        AppScreen.Rules,
        AppScreen.Reports
    )
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar {
        items.forEach { screen ->
            NavigationBarItem(
                selected = currentRoute == screen.route,
                onClick = {
                    navController.navigate(screen.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { androidx.compose.material3.Icon(screen.icon, contentDescription = screen.label) },
                label = { Text(screen.label) }
            )
        }
    }
}

@Composable
private fun AppNavHost(
    navController: androidx.navigation.NavHostController,
    padding: PaddingValues,
    viewModel: MainViewModel,
    scope: kotlinx.coroutines.CoroutineScope,
    uiState: com.mpesaparser.ui.state.UiState
) {
    NavHost(
        navController = navController,
        startDestination = AppScreen.Dashboard.route,
        modifier = Modifier.padding(padding)
    ) {
        composable(AppScreen.Dashboard.route) { DashboardScreen(viewModel, uiState) }
        composable(AppScreen.Transactions.route) { TransactionsScreen(viewModel, uiState) }
        composable(AppScreen.Rules.route) { RulesScreen(viewModel, uiState) }
        composable(AppScreen.Reports.route) { ReportsScreen(viewModel, uiState, scope) }
    }
}
