package com.vrchatlegends.osccompanion.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vrchatlegends.osccompanion.ui.AppViewModel
import com.vrchatlegends.osccompanion.ui.LabelledValue
import com.vrchatlegends.osccompanion.ui.ScreenScaffold
import com.vrchatlegends.osccompanion.ui.SectionCard
import com.vrchatlegends.osccompanion.ui.theme.Bad
import com.vrchatlegends.osccompanion.vrcl.VrclAuth

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountScreen(viewModel: AppViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val events by viewModel.vrclEvents.collectAsStateWithLifecycle()
    val error by viewModel.authError.collectAsStateWithLifecycle()

    var apiKey by remember { mutableStateOf("") }

    ScreenScaffold(
        title = "VRChat Legends",
        subtitle = "Optional. Every OSC feature works signed out.",
    ) {
        if (profile == null) {
            SectionCard(
                title = "Sign in",
                subtitle = "Opens the VRChat Legends login in a browser tab. No password is typed into this app.",
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    VrclAuth.providers.forEach { provider ->
                        Button(onClick = { viewModel.signIn(provider.id) }) {
                            Text("Continue with ${provider.label}")
                        }
                    }
                }
                error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Bad) }
                Text(
                    "If your account has 2FA, finish it in the browser tab and it will return here automatically.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionCard(
                title = "Or paste an API key",
                subtitle = "Account settings > API keys on vrchatlegends.com. Starts with vrcl_",
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API key") },
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
                    ) { Text("Use key") }
                }
            }
        } else {
            SectionCard(title = "Signed in") {
                LabelledValue("Display name", profile?.displayName.orEmpty())
                profile?.roles?.takeIf { it.isNotEmpty() }?.let {
                    LabelledValue("Roles", it.joinToString(", "))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { viewModel.refreshProfile() }) { Text("Refresh") }
                    Button(onClick = { viewModel.signOut() }) { Text("Sign out") }
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
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
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
                            OutlinedButton(onClick = { viewModel.sendEventToChatbox(event) }) { Text("Chatbox") }
                        }
                    }
                }
            }
        }

        SectionCard(title = "Community") {
            Text("Discord: https://discord.gg/6xPkZ7Dxp9", style = MaterialTheme.typography.bodyMedium)
            Text("Website: https://vrchatlegends.com", style = MaterialTheme.typography.bodyMedium)
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
