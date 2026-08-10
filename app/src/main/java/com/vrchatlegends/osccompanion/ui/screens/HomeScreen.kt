package com.vrchatlegends.osccompanion.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.vrchatlegends.osccompanion.ui.AppViewModel
import com.vrchatlegends.osccompanion.ui.Destination
import com.vrchatlegends.osccompanion.ui.ScreenScaffold
import com.vrchatlegends.osccompanion.ui.theme.Bad
import com.vrchatlegends.osccompanion.ui.theme.Good
import com.vrchatlegends.osccompanion.ui.theme.SignalCoral
import com.vrchatlegends.osccompanion.ui.theme.SignalCyan
import com.vrchatlegends.osccompanion.ui.theme.Warn

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(viewModel: AppViewModel, navController: NavController) {
    val connection by viewModel.osc.connection.collectAsStateWithLifecycle()
    val events by viewModel.osc.events.collectAsStateWithLifecycle()
    val parameters by viewModel.osc.parameters.collectAsStateWithLifecycle()
    val avatarId by viewModel.osc.avatarId.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val bridge by viewModel.bridge.collectAsStateWithLifecycle()

    ScreenScaffold(
        title = profile?.let { "Welcome, ${it.displayName}" } ?: "Home",
        subtitle = when {
            connection.vrchatSeen -> "Your VRChat link is healthy"
            connection.running -> "The companion is ready for VRChat"
            else -> "Start here before opening VRChat"
        },
    ) {
        ConnectionPanel(
            running = connection.running,
            vrchatSeen = connection.vrchatSeen,
            target = "${connection.targetHost}:${connection.targetPort}",
            sent = connection.sent,
            received = connection.received,
            error = connection.error,
            onToggle = viewModel::toggleConnection,
            onReleaseInputs = viewModel.osc::releaseAllInputs,
        )

        AnimatedVisibility(
            visible = !connection.vrchatSeen,
            enter = fadeIn() + slideInVertically { it / 4 },
            exit = fadeOut() + slideOutVertically { -it / 4 },
        ) {
            SetupPrompt(running = connection.running, onConnect = viewModel::connect)
        }

        StatusOverview(
            running = connection.running,
            vrchatSeen = connection.vrchatSeen,
            bridgeRunning = bridge.running,
            pcSeen = bridge.pcSeen,
            parameterCount = parameters.size,
        )

        Text("Quick tools", style = MaterialTheme.typography.titleLarge)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            QuickTool(Icons.Filled.Chat, "Chatbox", "Send a message") {
                navController.navigate(Destination.CHATBOX.route)
            }
            QuickTool(Icons.Filled.Tune, "Parameters", "${parameters.size} available") {
                navController.navigate(Destination.PARAMETERS.route)
            }
            QuickTool(Icons.Filled.Sync, "PC Link", if (bridge.running) "Running" else "Set up bridge") {
                navController.navigate(Destination.BRIDGE.route)
            }
            QuickTool(Icons.Filled.Height, "Avatar scale", "Adjust eye height") {
                navController.navigate(Destination.SCALE.route)
            }
            QuickTool(Icons.Filled.Terminal, "OSC monitor", "Inspect live traffic") {
                navController.navigate(Destination.MONITOR.route)
            }
        }

        avatarId?.let {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Current avatar", style = MaterialTheme.typography.labelSmall)
                    Text(
                        it,
                        modifier = Modifier.widthIn(max = 520.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        ActivityPanel(events = events)
    }
}

@Composable
private fun ConnectionPanel(
    running: Boolean,
    vrchatSeen: Boolean,
    target: String,
    sent: Long,
    received: Long,
    error: String?,
    onToggle: () -> Unit,
    onReleaseInputs: () -> Unit,
) {
    val color = when {
        vrchatSeen -> Good
        running -> Warn
        else -> MaterialTheme.colorScheme.outline
    }
    val state = when {
        vrchatSeen -> "live"
        running -> "waiting"
        else -> "stopped"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, color.copy(alpha = 0.38f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(9.dp).background(color, CircleShape))
                    Text(
                        when (state) {
                            "live" -> "LIVE OSC"
                            "waiting" -> "LISTENING"
                            else -> "OFFLINE"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                        fontWeight = FontWeight.Bold,
                    )
                }

                AnimatedContent(
                    targetState = state,
                    transitionSpec = {
                        (fadeIn(tween(220)) + slideInVertically { it / 4 }) togetherWith
                            (fadeOut(tween(140)) + slideOutVertically { -it / 4 })
                    },
                    label = "connection state",
                ) { current ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            when (current) {
                                "live" -> "VRChat is connected"
                                "waiting" -> "Waiting for VRChat"
                                else -> "Start your headset link"
                            },
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            when (current) {
                                "live" -> "Avatar data is moving through the companion."
                                "waiting" -> "Open VRChat and enable OSC in the Action Menu."
                                else -> "The background service keeps OSC alive while the panel is closed."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (running) {
                        OutlinedButton(onClick = onToggle) {
                            Icon(Icons.Filled.StopCircle, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Stop link")
                        }
                        OutlinedButton(onClick = onReleaseInputs) {
                            Text("Release inputs")
                        }
                    } else {
                        Button(
                            onClick = onToggle,
                            colors = ButtonDefaults.buttonColors(containerColor = SignalCoral),
                        ) {
                            Icon(Icons.Filled.PowerSettingsNew, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Start link")
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text("Target $target", style = MaterialTheme.typography.labelSmall)
                    Text("$sent out", style = MaterialTheme.typography.labelSmall)
                    Text("$received in", style = MaterialTheme.typography.labelSmall)
                }
                error?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Bad) }
            }

            SignalPulse(active = running, color = color)
        }
    }
}

@Composable
private fun SignalPulse(active: Boolean, color: Color) {
    val infinite = rememberInfiniteTransition(label = "live signal")
    val pulse by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (active) 1300 else 2600),
            repeatMode = RepeatMode.Restart,
        ),
        label = "live signal pulse",
    )
    Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().height(112.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            if (active) {
                drawCircle(
                    color = color.copy(alpha = (1f - pulse) * 0.24f),
                    radius = 24.dp.toPx() + 24.dp.toPx() * pulse,
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
                )
            }
            drawCircle(color = color.copy(alpha = 0.13f), radius = 31.dp.toPx(), center = center)
            drawCircle(color = color.copy(alpha = 0.52f), radius = 31.dp.toPx(), center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()))
        }
        Icon(Icons.Filled.Link, contentDescription = null, tint = color, modifier = Modifier.size(30.dp))
    }
}

@Composable
private fun SetupPrompt(running: Boolean, onConnect: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = SignalCyan.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, SignalCyan.copy(alpha = 0.28f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = CircleShape, color = SignalCyan.copy(alpha = 0.16f)) {
                Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        if (running) Icons.Filled.ArrowForward else Icons.Filled.PowerSettingsNew,
                        contentDescription = null,
                        tint = SignalCyan,
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (running) "One step left" else "Start the companion first",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    if (running) {
                        "In VRChat: Action Menu > Options > OSC > Enabled"
                    } else {
                        "Start the background link, then enable OSC inside VRChat."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!running) {
                Button(onClick = onConnect) { Text("Start") }
            }
        }
    }
}

@Composable
private fun StatusOverview(
    running: Boolean,
    vrchatSeen: Boolean,
    bridgeRunning: Boolean,
    pcSeen: Boolean,
    parameterCount: Int,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wide = maxWidth >= 660.dp
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusMetric(
                    "Headset link",
                    if (vrchatSeen) "VRChat live" else if (running) "Listening" else "Stopped",
                    if (vrchatSeen) Good else if (running) Warn else MaterialTheme.colorScheme.outline,
                    Modifier.weight(1f),
                )
                StatusMetric(
                    "PC Link",
                    if (pcSeen) "PC online" else if (bridgeRunning) "Waiting for PC" else "Off",
                    if (pcSeen) Good else if (bridgeRunning) Warn else MaterialTheme.colorScheme.outline,
                    Modifier.weight(1f),
                )
                StatusMetric(
                    "Avatar",
                    "$parameterCount parameters",
                    if (parameterCount > 0) SignalCyan else MaterialTheme.colorScheme.outline,
                    Modifier.weight(1f),
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusMetric("Headset link", if (vrchatSeen) "VRChat live" else "Not linked", if (vrchatSeen) Good else Warn)
                StatusMetric("PC Link", if (pcSeen) "PC online" else "Off", if (pcSeen) Good else MaterialTheme.colorScheme.outline)
                StatusMetric("Avatar", "$parameterCount parameters", SignalCyan)
            }
        }
    }
}

@Composable
private fun StatusMetric(title: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(8.dp).background(color, CircleShape))
                Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun QuickTool(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(120),
        label = "tool press",
    )
    Surface(
        modifier = Modifier
            .width(205.dp)
            .height(92.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = RoundedCornerShape(7.dp), color = SignalCoral.copy(alpha = 0.12f)) {
                Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = SignalCoral, modifier = Modifier.size(22.dp))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ActivityPanel(events: List<String>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Recent activity", style = MaterialTheme.typography.titleMedium)
                Text("Newest first", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (events.isEmpty()) {
                Text(
                    "Live events will appear here after VRChat connects.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                events.asReversed().take(6).forEach { event ->
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).background(Good, CircleShape))
                        Text(event, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
