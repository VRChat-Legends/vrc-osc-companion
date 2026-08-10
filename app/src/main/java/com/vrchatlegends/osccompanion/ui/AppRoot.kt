package com.vrchatlegends.osccompanion.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vrchatlegends.osccompanion.ui.screens.AccountScreen
import com.vrchatlegends.osccompanion.ui.screens.BridgeScreen
import com.vrchatlegends.osccompanion.ui.screens.ChatboxScreen
import com.vrchatlegends.osccompanion.ui.screens.CommunityScreen
import com.vrchatlegends.osccompanion.ui.screens.HomeScreen
import com.vrchatlegends.osccompanion.ui.screens.InputScreen
import com.vrchatlegends.osccompanion.ui.screens.LogsScreen
import com.vrchatlegends.osccompanion.ui.screens.MonitorScreen
import com.vrchatlegends.osccompanion.ui.screens.OnboardingScreen
import com.vrchatlegends.osccompanion.ui.screens.ParametersScreen
import com.vrchatlegends.osccompanion.ui.screens.ScaleScreen
import com.vrchatlegends.osccompanion.ui.screens.SettingsScreen
import com.vrchatlegends.osccompanion.ui.screens.StatusScreen
import com.vrchatlegends.osccompanion.ui.screens.VrchatToolsScreen
import com.vrchatlegends.osccompanion.ui.theme.Good
import com.vrchatlegends.osccompanion.ui.theme.SignalCoral
import com.vrchatlegends.osccompanion.ui.theme.Warn
import kotlinx.coroutines.launch

enum class NavSection(val label: String) {
    PLAY("PLAY"),
    CONNECT("CONNECT"),
    DISCOVER("DISCOVER"),
}

enum class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val section: NavSection? = null,
) {
    HOME("home", "Home", Icons.Filled.Dashboard),
    CHATBOX("chatbox", "Chatbox", Icons.Filled.Chat, NavSection.PLAY),
    PARAMETERS("parameters", "Parameters", Icons.Filled.Tune, NavSection.PLAY),
    INPUT("input", "Input", Icons.Filled.Gamepad, NavSection.PLAY),
    SCALE("scale", "Avatar scale", Icons.Filled.Height, NavSection.PLAY),
    STATUS("status", "Status line", Icons.Filled.Favorite, NavSection.PLAY),
    BRIDGE("bridge", "PC Link", Icons.Filled.Sync, NavSection.CONNECT),
    MONITOR("monitor", "OSC monitor", Icons.Filled.Terminal, NavSection.CONNECT),
    LOGS("logs", "VRChat logs", Icons.Filled.Description, NavSection.CONNECT),
    VRCHAT_TOOLS("vrchat", "VRChat tools", Icons.Filled.Extension, NavSection.DISCOVER),
    COMMUNITY("community", "Community", Icons.Filled.Groups, NavSection.DISCOVER),
    ACCOUNT("account", "Account", Icons.Filled.AccountCircle, NavSection.DISCOVER),
    SETTINGS("settings", "Settings", Icons.Filled.Settings),
}

@Composable
fun AppRoot(viewModel: AppViewModel) {
    val onboardingCompleted by viewModel.onboardingCompleted.collectAsStateWithLifecycle()

    Crossfade(
        targetState = onboardingCompleted,
        animationSpec = tween(320),
        label = "app start",
    ) { completed ->
        when (completed) {
            null -> StartupScreen()
            false -> OnboardingScreen(
                viewModel = viewModel,
                onFinished = { viewModel.setOnboardingCompleted(true) },
            )
            true -> CompanionWorkspace(viewModel)
        }
    }
}

@Composable
private fun StartupScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text(
                "VRC OSC Companion",
                modifier = Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CompanionWorkspace(viewModel: AppViewModel) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val connection by viewModel.osc.connection.collectAsStateWithLifecycle()
    val currentRoute = currentDestination?.route ?: Destination.HOME.route
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    fun navigate(destination: Destination) {
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 760.dp) {
            Row(Modifier.fillMaxSize()) {
                WorkspaceSidebar(
                    currentRoute = currentRoute,
                    running = connection.running,
                    vrchatSeen = connection.vrchatSeen,
                    onNavigate = ::navigate,
                    modifier = Modifier
                        .width(224.dp)
                        .fillMaxHeight(),
                )
                WorkspaceNavHost(navController, viewModel, Modifier.weight(1f))
            }
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet {
                        WorkspaceSidebar(
                            currentRoute = currentRoute,
                            running = connection.running,
                            vrchatSeen = connection.vrchatSeen,
                            onNavigate = { destination ->
                                navigate(destination)
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier
                                .width(286.dp)
                                .fillMaxHeight(),
                        )
                    }
                },
            ) {
                Column(Modifier.fillMaxSize()) {
                    TopAppBar(
                        title = {
                            Text(
                                Destination.entries.firstOrNull { it.route == currentRoute }?.label
                                    ?: "VRC OSC Companion",
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Filled.Menu, contentDescription = "Open navigation")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                    )
                    WorkspaceNavHost(navController, viewModel, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun WorkspaceSidebar(
    currentRoute: String,
    running: Boolean,
    vrchatSeen: Boolean,
    onNavigate: (Destination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 16.dp)) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    modifier = Modifier.size(38.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = SignalCoral.copy(alpha = 0.15f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.SettingsInputAntenna,
                            contentDescription = null,
                            tint = SignalCoral,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Column {
                    Text("VRC OSC", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "COMPANION",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            WorkspaceStatus(running = running, vrchatSeen = vrchatSeen)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 14.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                SidebarDestination(
                    destination = Destination.HOME,
                    selected = currentRoute == Destination.HOME.route,
                    onClick = { onNavigate(Destination.HOME) },
                )

                NavSection.entries.forEach { section ->
                    Text(
                        section.label,
                        modifier = Modifier.padding(start = 12.dp, top = 14.dp, bottom = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                    )
                    Destination.entries.filter { it.section == section }.forEach { destination ->
                        SidebarDestination(
                            destination = destination,
                            selected = currentRoute == destination.route,
                            onClick = { onNavigate(destination) },
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 10.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                SidebarDestination(
                    destination = Destination.SETTINGS,
                    selected = currentRoute == Destination.SETTINGS.route,
                    onClick = { onNavigate(Destination.SETTINGS) },
                )
            }
        }
    }
}

@Composable
private fun WorkspaceStatus(running: Boolean, vrchatSeen: Boolean) {
    val color = when {
        vrchatSeen -> Good
        running -> Warn
        else -> MaterialTheme.colorScheme.outline
    }
    val label = when {
        vrchatSeen -> "VRChat linked"
        running -> "Waiting for VRChat"
        else -> "Link stopped"
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.10f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).background(color, CircleShape))
            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}

@Composable
private fun SidebarDestination(
    destination: Destination,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(120),
        label = "navigation press",
    )
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        shape = RoundedCornerShape(7.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(destination.icon, contentDescription = null, modifier = Modifier.size(21.dp))
            Text(destination.label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun WorkspaceNavHost(
    navController: NavHostController,
    viewModel: AppViewModel,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Destination.HOME.route,
        modifier = modifier,
        enterTransition = { fadeIn(tween(180)) + slideInHorizontally(tween(220)) { it / 24 } },
        exitTransition = { fadeOut(tween(120)) + slideOutHorizontally(tween(180)) { -it / 30 } },
        popEnterTransition = { fadeIn(tween(180)) + slideInHorizontally(tween(220)) { -it / 24 } },
        popExitTransition = { fadeOut(tween(120)) + slideOutHorizontally(tween(180)) { it / 30 } },
    ) {
        composable(Destination.HOME.route) { HomeScreen(viewModel, navController) }
        composable(Destination.CHATBOX.route) { ChatboxScreen(viewModel) }
        composable(Destination.PARAMETERS.route) { ParametersScreen(viewModel) }
        composable(Destination.INPUT.route) { InputScreen(viewModel) }
        composable(Destination.SCALE.route) { ScaleScreen(viewModel) }
        composable(Destination.STATUS.route) { StatusScreen(viewModel) }
        composable(Destination.BRIDGE.route) { BridgeScreen(viewModel) }
        composable(Destination.LOGS.route) { LogsScreen(viewModel) }
        composable(Destination.MONITOR.route) { MonitorScreen(viewModel) }
        composable(Destination.VRCHAT_TOOLS.route) { VrchatToolsScreen(viewModel) }
        composable(Destination.COMMUNITY.route) { CommunityScreen(viewModel) }
        composable(Destination.ACCOUNT.route) { AccountScreen(viewModel) }
        composable(Destination.SETTINGS.route) { SettingsScreen(viewModel) }
    }
}

/** Shared page frame: scrolling column with consistent Quest-friendly padding. */
@Composable
fun ScreenScaffold(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = ScreenMaxWidth)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ScreenHeader(title, subtitle)
            content()
        }
    }
}

/** Widest the content column is allowed to get before it stops being readable. */
val ScreenMaxWidth = 1680.dp

/** The page title block, split out so screens with their own scroll container can reuse it. */
@Composable
fun ScreenHeader(title: String, subtitle: String? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(
            Modifier
                .width(4.dp)
                .height(38.dp)
                .background(SignalCoral, RoundedCornerShape(2.dp)),
        )
        Column {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
