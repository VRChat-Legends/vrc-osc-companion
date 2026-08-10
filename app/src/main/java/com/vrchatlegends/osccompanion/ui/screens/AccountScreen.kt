package com.vrchatlegends.osccompanion.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vrchatlegends.osccompanion.ui.AppViewModel
import com.vrchatlegends.osccompanion.ui.LabelledValue
import com.vrchatlegends.osccompanion.ui.ScreenScaffold
import com.vrchatlegends.osccompanion.ui.SectionCard
import com.vrchatlegends.osccompanion.ui.theme.Bad
import com.vrchatlegends.osccompanion.ui.theme.Good
import com.vrchatlegends.osccompanion.ui.theme.SignalCoral
import com.vrchatlegends.osccompanion.ui.theme.SignalCyan
import com.vrchatlegends.osccompanion.vrcl.VrclAuth

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountScreen(viewModel: AppViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val events by viewModel.vrclEvents.collectAsStateWithLifecycle()
    val error by viewModel.authError.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    var apiKey by remember { mutableStateOf("") }
    var showApiKey by remember { mutableStateOf(false) }

    ScreenScaffold(
        title = "Account",
        subtitle = "VRChat Legends events and community features, optional for OSC",
    ) {
        if (profile == null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = SignalCyan.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, SignalCyan.copy(alpha = 0.28f)),
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(shape = CircleShape, color = SignalCyan.copy(alpha = 0.14f)) {
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Security, contentDescription = null, tint = SignalCyan)
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Sign in without sharing a password", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Authentication happens in your browser and returns a session to this app.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            SectionCard(
                title = "Continue with",
                subtitle = "Choose the account already linked to VRChat Legends",
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    VrclAuth.providers.forEachIndexed { index, provider ->
                        if (index == 0) {
                            Button(
                                onClick = { viewModel.signIn(provider.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = SignalCoral),
                            ) {
                                Icon(Icons.Filled.Login, contentDescription = null)
                                Text(provider.label)
                            }
                        } else {
                            OutlinedButton(onClick = { viewModel.signIn(provider.id) }) {
                                Icon(Icons.Filled.Login, contentDescription = null)
                                Text(provider.label)
                            }
                        }
                    }
                }
                error?.let {
                    Surface(shape = RoundedCornerShape(7.dp), color = Bad.copy(alpha = 0.10f)) {
                        Text(
                            it,
                            modifier = Modifier.fillMaxWidth().padding(11.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Bad,
                        )
                    }
                }
                Text(
                    "If your account has 2FA, finish it in the browser tab and it will return here automatically.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedButton(onClick = { showApiKey = !showApiKey }) {
                Icon(Icons.Filled.Api, contentDescription = null)
                Text(if (showApiKey) "Hide API key sign-in" else "Advanced: use an API key")
            }

            AnimatedVisibility(visible = showApiKey) {
                SectionCard(
                    title = "API key",
                    subtitle = "Create one in Account settings on vrchatlegends.com. It starts with vrcl_",
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = { Text("vrcl_...") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.weight(1f),
                        )
                        Button(
                            onClick = {
                                viewModel.signInWithApiKey(apiKey)
                                apiKey = ""
                            },
                            enabled = apiKey.isNotBlank(),
                        ) {
                            Icon(Icons.Filled.ArrowForward, contentDescription = null)
                            Text("Use key")
                        }
                    }
                }
            }
        } else {
            SectionCard(
                title = "Signed in",
                subtitle = "VRChat Legends account",
                trailing = { ProfileInitial(profile?.displayName.orEmpty()) },
            ) {
                LabelledValue("Display name", profile?.displayName.orEmpty())
                profile?.roles?.takeIf { it.isNotEmpty() }?.let {
                    LabelledValue("Roles", it.joinToString(", "))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { viewModel.refreshProfile() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Text("Refresh")
                    }
                    OutlinedButton(onClick = { viewModel.signOut() }) {
                        Icon(Icons.Filled.Logout, contentDescription = null)
                        Text("Sign out")
                    }
                }
            }

            SectionCard(title = "Events", subtitle = "Tap to push to your chatbox") {
                if (events.isEmpty()) {
                    Text(
                        "No upcoming events.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    events.forEach { event ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(7.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Event, contentDescription = null, tint = SignalCyan)
                            Column(Modifier.weight(1f)) {
                                Text(event.title, style = MaterialTheme.typography.bodyLarge)
                                listOfNotNull(event.startsAt, event.location)
                                    .takeIf { it.isNotEmpty() }
                                    ?.let {
                                        Text(
                                            it.joinToString(" | "),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                            }
                            OutlinedButton(onClick = { viewModel.sendEventToChatbox(event) }) {
                                Text("Send to chatbox")
                            }
                        }
                        }
                    }
                }
            }
        }

        SectionCard(title = "Community", subtitle = "Open in the Quest browser") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { uriHandler.openUri("https://discord.gg/6xPkZ7Dxp9") }) {
                    Icon(Icons.Filled.OpenInNew, contentDescription = null)
                    Text("Discord")
                }
                OutlinedButton(onClick = { uriHandler.openUri("https://vrchatlegends.com") }) {
                    Icon(Icons.Filled.OpenInNew, contentDescription = null)
                    Text("Website")
                }
            }
            if (settings.vrclToken.isNotBlank() && profile == null) {
                Text(
                    "A saved session exists but could not be validated. Try Refresh or sign in again.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Bad,
                )
            }
        }
    }
}

@Composable
private fun ProfileInitial(displayName: String) {
    Surface(
        modifier = Modifier.size(46.dp),
        shape = CircleShape,
        color = Good.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, Good.copy(alpha = 0.42f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                displayName.trim().firstOrNull()?.uppercase() ?: "L",
                style = MaterialTheme.typography.titleLarge,
                color = Good,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
