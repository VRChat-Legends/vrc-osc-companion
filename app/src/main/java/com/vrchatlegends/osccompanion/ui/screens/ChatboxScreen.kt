package com.vrchatlegends.osccompanion.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vrchatlegends.osccompanion.data.ChatboxPreset
import com.vrchatlegends.osccompanion.osc.VrcOsc
import com.vrchatlegends.osccompanion.ui.AppViewModel
import com.vrchatlegends.osccompanion.ui.ScreenScaffold
import com.vrchatlegends.osccompanion.ui.SectionCard
import com.vrchatlegends.osccompanion.ui.theme.Bad
import com.vrchatlegends.osccompanion.ui.theme.Warn

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatboxScreen(viewModel: AppViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val presets by viewModel.chatboxPresets.collectAsStateWithLifecycle()

    var text by remember { mutableStateOf("") }
    var newPresetLabel by remember { mutableStateOf("") }

    val lines = text.count { it == '\n' } + 1
    val overCharacters = text.length > VrcOsc.CHATBOX_MAX_CHARS
    val overLines = lines > VrcOsc.CHATBOX_MAX_LINES

    // The typing indicator has to be turned back off, otherwise VRChat leaves the bubble up.
    LaunchedEffect(text) {
        viewModel.setTyping(text.isNotEmpty())
    }

    ScreenScaffold(
        title = "Chatbox",
        subtitle = "144 characters and 9 lines maximum, word wrap counts toward both",
    ) {
        SectionCard(
            title = "Compose",
            subtitle = "${text.length}/${VrcOsc.CHATBOX_MAX_CHARS} characters, $lines/${VrcOsc.CHATBOX_MAX_LINES} lines",
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                placeholder = { Text("Say something...") },
                isError = overCharacters || overLines,
                supportingText = {
                    when {
                        overCharacters -> Text("Too long, VRChat will cut it", color = Bad)
                        overLines -> Text("Too many lines", color = Warn)
                        else -> Text("Sends immediately without opening the in-game keyboard")
                    }
                },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = {
                        viewModel.sendChatbox(text)
                        viewModel.setTyping(false)
                    },
                    enabled = text.isNotBlank(),
                ) { Text("Send") }

                OutlinedButton(
                    onClick = { viewModel.openKeyboardWith(text) },
                    enabled = text.isNotBlank(),
                ) { Text("Open VRChat keyboard") }

                OutlinedButton(onClick = {
                    text = ""
                    viewModel.clearChatbox()
                    viewModel.setTyping(false)
                }) { Text("Clear") }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Switch(
                        checked = settings.chatboxSilent,
                        onCheckedChange = { value -> viewModel.updateSettings { setChatboxSilent(value) } },
                    )
                    Text("Silent (no notification SFX)", style = MaterialTheme.typography.bodyMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Switch(
                        checked = settings.chatboxShowTyping,
                        onCheckedChange = { value -> viewModel.updateSettings { setChatboxTyping(value) } },
                    )
                    Text("Typing indicator", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        SectionCard(title = "Presets", subtitle = "Tap to send, long labels wrap") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { preset ->
                    InputChip(
                        selected = false,
                        onClick = { viewModel.sendChatbox(preset.text) },
                        label = { Text(preset.label) },
                        trailingIcon = {
                            IconButton(
                                onClick = { viewModel.deletePreset(preset.id) },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Delete ${preset.label}",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newPresetLabel,
                    onValueChange = { newPresetLabel = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Preset name") },
                    singleLine = true,
                )
                Button(
                    onClick = {
                        viewModel.savePreset(
                            ChatboxPreset(
                                id = System.currentTimeMillis().toString(),
                                label = newPresetLabel.trim(),
                                text = text,
                            )
                        )
                        newPresetLabel = ""
                    },
                    enabled = newPresetLabel.isNotBlank() && text.isNotBlank(),
                ) { Text("Save current text") }
            }
        }

        SectionCard(title = "Quick inserts") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                QUICK_INSERTS.forEach { insert ->
                    FilterChip(
                        selected = false,
                        onClick = { text = (text + insert).take(VrcOsc.CHATBOX_MAX_CHARS) },
                        label = { Text(insert) },
                    )
                }
            }
        }
    }
}

private val QUICK_INSERTS = listOf("\u2665", "\u2606", "\u266A", "\u2600", "\u25B6", "o/", "^^", "\n")
