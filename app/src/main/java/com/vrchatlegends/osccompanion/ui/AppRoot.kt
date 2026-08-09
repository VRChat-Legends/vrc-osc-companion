package com.vrchatlegends.osccompanion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vrchatlegends.osccompanion.ui.screens.AccountScreen
import com.vrchatlegends.osccompanion.ui.screens.ChatboxScreen
import com.vrchatlegends.osccompanion.ui.screens.HomeScreen
import com.vrchatlegends.osccompanion.ui.screens.InputScreen
import com.vrchatlegends.osccompanion.ui.screens.MonitorScreen
import com.vrchatlegends.osccompanion.ui.screens.ParametersScreen
import com.vrchatlegends.osccompanion.ui.screens.ScaleScreen
import com.vrchatlegends.osccompanion.ui.screens.SettingsScreen
import com.vrchatlegends.osccompanion.ui.screens.StatusScreen

enum class Destination(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Home", Icons.Filled.Dashboard),
    CHATBOX("chatbox", "Chatbox", Icons.Filled.Chat),
    PARAMETERS("parameters", "Params", Icons.Filled.Tune),
    INPUT("input", "Input", Icons.Filled.Gamepad),
    SCALE("scale", "Scale", Icons.Filled.Height),
    STATUS("status", "Status", Icons.Filled.Favorite),
    MONITOR("monitor", "Monitor", Icons.Filled.Terminal),
    ACCOUNT("account", "Account", Icons.Filled.AccountCircle),
    SETTINGS("settings", "Settings", Icons.Filled.Settings),
}

/**
 * A vertical rail suits the Quest panel far better than a bottom bar: the panel is wide,
 * and a bottom bar sits at the very edge of comfortable controller aim.
 */
@Composable
fun AppRoot(viewModel: AppViewModel) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Row(Modifier.fillMaxSize()) {
        NavigationRail(Modifier.fillMaxHeight()) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Destination.entries.forEach { destination ->
                    val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                    NavigationRailItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                    )
                }
            }
        }

        Box(Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = Destination.HOME.route,
                modifier = Modifier.fillMaxSize(),
            ) {
                composable(Destination.HOME.route) { HomeScreen(viewModel, navController) }
                composable(Destination.CHATBOX.route) { ChatboxScreen(viewModel) }
                composable(Destination.PARAMETERS.route) { ParametersScreen(viewModel) }
                composable(Destination.INPUT.route) { InputScreen(viewModel) }
                composable(Destination.SCALE.route) { ScaleScreen(viewModel) }
                composable(Destination.STATUS.route) { StatusScreen(viewModel) }
                composable(Destination.MONITOR.route) { MonitorScreen(viewModel) }
                composable(Destination.ACCOUNT.route) { AccountScreen(viewModel) }
                composable(Destination.SETTINGS.route) { SettingsScreen(viewModel) }
            }
        }
    }
}

/** Shared page frame: scrolling column with consistent Quest-friendly padding. */
@Composable
fun ScreenScaffold(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column {
            Text(title, style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
            subtitle?.let {
                Text(
                    it,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        content()
    }
}
