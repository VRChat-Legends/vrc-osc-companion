package com.vrchatlegends.osccompanion.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.filled.Headset
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vrchatlegends.osccompanion.ui.AppViewModel
import com.vrchatlegends.osccompanion.ui.theme.Good
import com.vrchatlegends.osccompanion.ui.theme.SignalCoral
import com.vrchatlegends.osccompanion.ui.theme.SignalCyan
import com.vrchatlegends.osccompanion.ui.theme.Warn

private const val ONBOARDING_STEPS = 3

@Composable
fun OnboardingScreen(
    viewModel: AppViewModel,
    onFinished: () -> Unit,
) {
    val connection by viewModel.osc.connection.collectAsStateWithLifecycle()
    var page by remember { mutableIntStateOf(0) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val wide = maxWidth >= 760.dp
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                OnboardingVisual(
                    connected = connection.vrchatSeen,
                    modifier = Modifier
                        .width(360.dp)
                        .fillMaxHeight(),
                )
                OnboardingBody(
                    page = page,
                    connected = connection.vrchatSeen,
                    running = connection.running,
                    onConnect = viewModel::connect,
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
                    connected = connection.vrchatSeen,
                    compact = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                )
                OnboardingBody(
                    page = page,
                    connected = connection.vrchatSeen,
                    running = connection.running,
                    onConnect = viewModel::connect,
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
    connected: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val infinite = rememberInfiniteTransition(label = "onboarding signal")
    val progress by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing)),
        label = "signal progress",
    )
    val pulse by infinite.animateFloat(
        initialValue = 0.86f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing)),
        label = "signal pulse",
    )
    val activeColor = if (connected) Good else SignalCyan

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surface),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val spacing = 28.dp.toPx()
            var x = 0f
            while (x < size.width) {
                drawLine(
                    color = Color.White.copy(alpha = 0.025f),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
                x += spacing
            }
            var y = 0f
            while (y < size.height) {
                drawLine(
                    color = Color.White.copy(alpha = 0.025f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                )
                y += spacing
            }

            val centerY = if (compact) size.height * 0.58f else size.height * 0.5f
            val start = Offset(size.width * 0.22f, centerY)
            val end = Offset(size.width * 0.78f, centerY)
            drawLine(
                color = activeColor.copy(alpha = 0.22f),
                start = start,
                end = end,
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )
            val signalX = start.x + (end.x - start.x) * progress
            drawCircle(
                color = activeColor.copy(alpha = 0.18f),
                radius = 16.dp.toPx() * pulse,
                center = Offset(signalX, centerY),
            )
            drawCircle(
                color = activeColor,
                radius = 5.dp.toPx(),
                center = Offset(signalX, centerY),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (compact) 24.dp else 32.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "VRCHAT LEGENDS",
                    color = SignalCoral,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "OSC Companion",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SignalNode(Icons.Filled.Headset, "Quest")
                SignalNode(
                    icon = if (connected) Icons.Filled.Check else Icons.Filled.SettingsInputAntenna,
                    label = if (connected) "Linked" else "Listening",
                    active = connected,
                    modifier = Modifier.scale(pulse.coerceAtMost(1f)),
                )
                SignalNode(Icons.Filled.Computer, "VRChat")
            }

            if (!compact) {
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
                        if (connected) "VRChat detected" else "Waiting for your first connection",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SignalNode(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            modifier = Modifier.size(54.dp),
            shape = CircleShape,
            color = if (active) Good.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (active) Good.copy(alpha = 0.65f) else MaterialTheme.colorScheme.outline,
            ),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (active) Good else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun OnboardingBody(
    page: Int,
    connected: Boolean,
    running: Boolean,
    onConnect: () -> Unit,
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
            Text(
                "Skip setup",
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onSkip)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AnimatedContent(
            targetState = page,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            transitionSpec = {
                (fadeIn(tween(280)) + slideInHorizontally(tween(320)) { it / 8 }) togetherWith
                    (fadeOut(tween(180)) + slideOutHorizontally(tween(240)) { -it / 10 })
            },
            label = "onboarding page",
        ) { target ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                when (target) {
                    0 -> WelcomeStep()
                    1 -> ConnectStep(
                        connected = connected,
                        running = running,
                        onConnect = onConnect,
                    )
                    else -> ReadyStep(connected = connected, running = running)
                }
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
                colors = ButtonDefaults.buttonColors(containerColor = SignalCoral),
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
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(ONBOARDING_STEPS) { index ->
            val width by animateFloatAsState(
                targetValue = if (index == current) 32f else 9f,
                animationSpec = tween(250),
                label = "step width",
            )
            Box(
                Modifier
                    .height(9.dp)
                    .width(width.dp)
                    .clip(CircleShape)
                    .background(
                        if (index <= current) SignalCoral
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
            color = SignalCoral,
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
