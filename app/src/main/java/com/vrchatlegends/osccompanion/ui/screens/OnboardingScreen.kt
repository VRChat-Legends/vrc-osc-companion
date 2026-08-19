package com.vrchatlegends.osccompanion.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vrchatlegends.osccompanion.data.AppTheme
import com.vrchatlegends.osccompanion.ui.AppViewModel
import com.vrchatlegends.osccompanion.ui.ThemeModeSelector
import com.vrchatlegends.osccompanion.ui.theme.AccentChoices
import com.vrchatlegends.osccompanion.ui.theme.Good
import com.vrchatlegends.osccompanion.ui.theme.SignalCyan
import com.vrchatlegends.osccompanion.ui.theme.Warn

private const val ONBOARDING_STEPS = 4

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
    viewModel: AppViewModel,
    onFinished: () -> Unit,
) {
    val connection by viewModel.osc.connection.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var page by remember { mutableIntStateOf(0) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val wide = maxWidth >= 900.dp
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                OnboardingVisual(
                    page = page,
                    connected = connection.vrchatSeen,
                    modifier = Modifier
                        .width(360.dp)
                        .fillMaxHeight(),
                )
                OnboardingBody(
                    page = page,
                    connected = connection.vrchatSeen,
                    running = connection.running,
                    appTheme = settings.appTheme,
                    accentArgb = settings.accentColor,
                    onConnect = viewModel::connect,
                    onThemeSelect = viewModel::setAppTheme,
                    onAccentSelect = viewModel::setAccentColor,
                    onBack = { page = (page - 1).coerceAtLeast(0) },
                    onNext = {
                        if (page == ONBOARDING_STEPS - 1) onFinished()
                        else page += 1
                    },
                    onSkip = onFinished,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                OnboardingVisual(
                    page = page,
                    connected = connection.vrchatSeen,
                    compact = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp),
                )
                OnboardingBody(
                    page = page,
                    connected = connection.vrchatSeen,
                    running = connection.running,
                    appTheme = settings.appTheme,
                    accentArgb = settings.accentColor,
                    onConnect = viewModel::connect,
                    onThemeSelect = viewModel::setAppTheme,
                    onAccentSelect = viewModel::setAccentColor,
                    onBack = { page = (page - 1).coerceAtLeast(0) },
                    onNext = {
                        if (page == ONBOARDING_STEPS - 1) onFinished()
                        else page += 1
                    },
                    onSkip = onFinished,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun OnboardingVisual(
    page: Int,
    connected: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        border = androidx.compose.foundation.BorderStroke(
            0.dp,
            MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (compact) 20.dp else 28.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "VRCHAT LEGENDS",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "OSC Companion",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (compact) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CompactSetupStage(Icons.Filled.AutoAwesome, "Welcome", page, 0, Modifier.weight(1f))
                    CompactSetupStage(Icons.Filled.SettingsInputAntenna, "Connect", page, 1, Modifier.weight(1f))
                    CompactSetupStage(Icons.Filled.Palette, "Style", page, 2, Modifier.weight(1f))
                    CompactSetupStage(Icons.Filled.Check, "Ready", page, 3, Modifier.weight(1f))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SetupStageRow(Icons.Filled.AutoAwesome, "Welcome", "What the companion controls", page, 0)
                    SetupStageRow(
                        Icons.Filled.SettingsInputAntenna,
                        "Connect VRChat",
                        if (connected) "VRChat detected" else "Enable OSC and start listening",
                        page,
                        1,
                        forceComplete = connected,
                    )
                    SetupStageRow(Icons.Filled.Palette, "Personalize", "Theme and accent", page, 2)
                    SetupStageRow(Icons.Filled.Check, "Ready", "Open your workspace", page, 3)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (connected) Good else Warn),
                    )
                    Text(
                        when (page) {
                            0 -> "Built for your Quest workspace"
                            1 -> if (connected) "VRChat detected" else "Waiting for your first connection"
                            2 -> "Theme changes apply instantly"
                            else -> if (connected) "Connection verified" else "Setup ready"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactSetupStage(
    icon: ImageVector,
    label: String,
    current: Int,
    index: Int,
    modifier: Modifier = Modifier,
) {
    val active = index == current
    val complete = index < current
    val accent = if (complete) Good else MaterialTheme.colorScheme.primary
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            modifier = Modifier.size(38.dp),
            shape = CircleShape,
            color = if (active || complete) accent.copy(alpha = 0.16f) else Color.Transparent,
            border = androidx.compose.foundation.BorderStroke(
                if (active) 2.dp else 1.dp,
                if (active || complete) accent.copy(alpha = 0.72f) else MaterialTheme.colorScheme.outline,
            ),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (complete) Icons.Filled.Check else icon,
                    contentDescription = if (active) "Current step" else if (complete) "Complete" else null,
                    tint = if (active || complete) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(19.dp),
                )
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun SetupStageRow(
    icon: ImageVector,
    title: String,
    detail: String,
    current: Int,
    index: Int,
    forceComplete: Boolean = false,
) {
    val active = index == current
    val complete = index < current || forceComplete
    val accent = if (complete) Good else MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = CircleShape,
            color = if (active || complete) accent.copy(alpha = 0.14f) else Color.Transparent,
            border = androidx.compose.foundation.BorderStroke(
                if (active) 2.dp else 1.dp,
                if (active || complete) accent.copy(alpha = 0.68f) else MaterialTheme.colorScheme.outline,
            ),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (complete) Icons.Filled.Check else icon,
                    contentDescription = if (active) "Current step" else if (complete) "Complete" else null,
                    tint = if (active || complete) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(21.dp),
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            )
            Text(
                if (complete && !active) "Complete" else detail,
                style = MaterialTheme.typography.labelSmall,
                color = if (complete) Good else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OnboardingBody(
    page: Int,
    connected: Boolean,
    running: Boolean,
    appTheme: AppTheme,
    accentArgb: Long,
    onConnect: () -> Unit,
    onThemeSelect: (AppTheme) -> Unit,
    onAccentSelect: (Long) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(horizontal = 40.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepIndicator(page)
            TextButton(onClick = onSkip, modifier = Modifier.height(48.dp)) {
                Text("Skip setup")
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            when (page) {
                0 -> WelcomeStep()
                1 -> ConnectStep(
                    connected = connected,
                    running = running,
                    onConnect = onConnect,
                )
                2 -> AppearanceStep(
                    appTheme = appTheme,
                    accentArgb = accentArgb,
                    onThemeSelect = onThemeSelect,
                    onAccentSelect = onAccentSelect,
                )
                else -> ReadyStep(connected = connected, running = running)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (page > 0) {
                OutlinedButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Back")
                }
            } else {
                Spacer(Modifier.width(1.dp))
            }

            Button(
                onClick = onNext,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text(if (page == ONBOARDING_STEPS - 1) "Open companion" else "Continue")
                Spacer(Modifier.width(8.dp))
                Icon(
                    if (page == ONBOARDING_STEPS - 1) Icons.Filled.PlayArrow else Icons.Filled.ArrowForward,
                    contentDescription = null,
                )
            }
        }
    }
}

@Composable
private fun StepIndicator(current: Int) {
    Row(
        modifier = Modifier.semantics {
            contentDescription = "Step ${current + 1} of $ONBOARDING_STEPS"
        },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(ONBOARDING_STEPS) { index ->
            Box(
                Modifier
                    .height(9.dp)
                    .width(if (index == current) 32.dp else 9.dp)
                    .clip(CircleShape)
                    .background(
                        if (index <= current) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline,
                    ),
            )
        }
    }
}

@Composable
private fun WelcomeStep() {
    StepColumn(
        eyebrow = "WELCOME",
        title = "VRC OSC Companion",
        body = "Control your avatar, send chatbox messages, and bridge live OSC data to your PC while you stay inside VRChat.",
    ) {
        FeatureRow(Icons.Filled.Chat, "Chat without leaving VR", "Send, save, and rotate chatbox messages.")
        FeatureRow(Icons.Filled.Speed, "Live avatar controls", "Parameters, input, scale, and status in one place.")
        FeatureRow(Icons.Filled.Security, "Local by default", "OSC stays on your headset and home network.")
    }
}

@Composable
private fun ConnectStep(
    connected: Boolean,
    running: Boolean,
    onConnect: () -> Unit,
) {
    StepColumn(
        eyebrow = "CONNECT",
        title = if (connected) "VRChat found you" else "Turn on OSC in VRChat",
        body = if (connected) {
            "The companion is receiving VRChat traffic. Your background link is ready."
        } else {
            "In VRChat, open Action Menu > Options > OSC, then switch Enabled on."
        },
    ) {
        SetupRow(number = "1", text = "Start the companion link", done = running)
        SetupRow(number = "2", text = "Enable OSC in VRChat", done = connected)
        SetupRow(number = "3", text = "Return here to confirm", done = connected)

        AnimatedVisibility(
            visible = !running,
            enter = fadeIn() + slideInHorizontally { it / 8 },
            exit = fadeOut(),
        ) {
            Button(onClick = onConnect, colors = ButtonDefaults.buttonColors(containerColor = SignalCyan)) {
                Icon(Icons.Filled.PowerSettingsNew, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Start background link")
            }
        }

        AnimatedVisibility(visible = running) {
            ConnectionBadge(connected = connected)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearanceStep(
    appTheme: AppTheme,
    accentArgb: Long,
    onThemeSelect: (AppTheme) -> Unit,
    onAccentSelect: (Long) -> Unit,
) {
    StepColumn(
        eyebrow = "PERSONALIZE",
        title = "Make it feel like yours",
        body = "Choose how the companion looks. You can change this again from Settings.",
    ) {
        ThemeModeSelector(selected = appTheme, onSelect = onThemeSelect)
        Text("Accent", style = MaterialTheme.typography.titleMedium)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            maxItemsInEachRow = 4,
        ) {
            AccentChoices.forEachIndexed { index, (name, color) ->
                val stored = if (index == 0) 0L else color.toArgb().toLong()
                val selected = stored == accentArgb
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .clickable { onAccentSelect(stored) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Surface(
                        modifier = Modifier.size(38.dp),
                        shape = CircleShape,
                        color = color,
                        border = androidx.compose.foundation.BorderStroke(
                            if (selected) 3.dp else 1.dp,
                            if (selected) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.outlineVariant,
                        ),
                    ) {
                        if (selected) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = "Selected",
                                    tint = Color.Black.copy(alpha = 0.72f),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                    Text(name, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun ReadyStep(connected: Boolean, running: Boolean) {
    StepColumn(
        eyebrow = "READY",
        title = "Your headset, your tools",
        body = "The Home workspace keeps the important state visible. Everything else is grouped by what you want to do.",
    ) {
        FeatureRow(
            Icons.Filled.AutoAwesome,
            if (connected) "Connection verified" else "Setup saved",
            if (connected) "VRChat is already sending data." else "You can connect from Home whenever VRChat is open.",
            accent = if (connected) Good else SignalCyan,
        )
        FeatureRow(Icons.Filled.Chat, "Play", "Chatbox, avatar controls, and status.")
        FeatureRow(Icons.Filled.Computer, "Connect", "PC Link, monitor, and VRChat logs.")
        if (running) ConnectionBadge(connected = connected)
    }
}

@Composable
private fun StepColumn(
    eyebrow: String,
    title: String,
    body: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            eyebrow,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            title,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(0.9f),
        )
        Spacer(Modifier.height(2.dp))
        content()
    }
}

@Composable
private fun FeatureRow(
    icon: ImageVector,
    title: String,
    body: String,
    accent: Color = SignalCyan,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = RoundedCornerShape(8.dp),
            color = accent.copy(alpha = 0.13f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SetupRow(number: String, text: String, done: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(34.dp),
            shape = CircleShape,
            color = if (done) Good else MaterialTheme.colorScheme.surfaceVariant,
            border = if (done) null else androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline,
            ),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (done) {
                    Icon(Icons.Filled.Check, contentDescription = "Complete", modifier = Modifier.size(18.dp))
                } else {
                    Text(number, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ConnectionBadge(connected: Boolean) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (connected) Good.copy(alpha = 0.12f) else Warn.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (connected) Good.copy(alpha = 0.5f) else Warn.copy(alpha = 0.5f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (connected) Good else Warn),
            )
            Text(
                if (connected) "Live OSC traffic detected" else "Listening for VRChat",
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
