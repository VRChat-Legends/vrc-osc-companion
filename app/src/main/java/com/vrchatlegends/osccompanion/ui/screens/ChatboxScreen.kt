package com.vrchatlegends.osccompanion.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vrchatlegends.osccompanion.data.ChatboxPreset
import com.vrchatlegends.osccompanion.osc.VrcOsc
import com.vrchatlegends.osccompanion.ui.AppViewModel
import com.vrchatlegends.osccompanion.ui.ScreenScaffold
import com.vrchatlegends.osccompanion.ui.SectionCard
import com.vrchatlegends.osccompanion.ui.theme.Bad
import com.vrchatlegends.osccompanion.ui.theme.SignalCoral
import com.vrchatlegends.osccompanion.ui.theme.SignalCyan
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
    val invalid = overCharacters || overLines
    val budget = maxOf(
        text.length.toFloat() / VrcOsc.CHATBOX_MAX_CHARS,
        lines.toFloat() / VrcOsc.CHATBOX_MAX_LINES,
    ).coerceIn(0f, 1f)
    val budgetColor = when {
        invalid -> Bad
        budget >= 0.8f -> Warn
        else -> SignalCyan
    }

    // The typing indicator has to be turned back off, otherwise VRChat leaves the bubble up.
    LaunchedEffect(text) {
        viewModel.setTyping(text.isNotEmpty())
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.setTyping(false) }
    }

    ScreenScaffold(
        title = "Chatbox",
        subtitle = "Send directly to VRChat without opening the in-game keyboard",
    ) {
        SectionCard(
            title = "Message",
            subtitle = "VRChat allows ${VrcOsc.CHATBOX_MAX_CHARS} characters across ${VrcOsc.CHATBOX_MAX_LINES} lines",
            trailing = {
                BudgetBadge(
                    text = "${text.length}/${VrcOsc.CHATBOX_MAX_CHARS}  •  $lines/${VrcOsc.CHATBOX_MAX_LINES}",
                    color = budgetColor,
                )
            },
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 170.dp),
                label = { Text("Chatbox message") },
                placeholder = { Text("What do you want VRChat to say?") },
                isError = invalid,
                supportingText = {
                    when {
                        overCharacters -> Text("Remove ${text.length - VrcOsc.CHATBOX_MAX_CHARS} characters before sending", color = Bad)
                        overLines -> Text("Remove ${lines - VrcOsc.CHATBOX_MAX_LINES} lines before sending", color = Bad)
                        else -> Text("Word wrap also counts toward the line limit")
                    }
                },
            )

            LinearProgressIndicator(
                progress = budget,
                modifier = Modifier.fillMaxWidth(),
                color = budgetColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        viewModel.sendChatbox(text)
                        viewModel.setTyping(false)
                    },
                    enabled = text.isNotBlank() && !invalid,
                    colors = ButtonDefaults.buttonColors(containerColor = SignalCoral),
                ) {
                    Icon(Icons.Filled.Send, contentDescription = null)
                    Text("Send now")
                }

                OutlinedButton(
                    onClick = { viewModel.openKeyboardWith(text) },
                    enabled = text.isNotBlank() && !invalid,
                ) {
                    Icon(Icons.Filled.Keyboard, contentDescription = null)
                    Text("Open keyboard")
                }

                OutlinedButton(onClick = {
                    text = ""
                    viewModel.clearChatbox()
                    viewModel.setTyping(false)
                }) {
                    Icon(Icons.Filled.Backspace, contentDescription = null)
                    Text("Clear")
                }
            }

            Text("Quick inserts", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                QUICK_INSERTS.forEach { (label, insert) ->
                    FilterChip(
                        selected = false,
                        onClick = { text = (text + insert).take(VrcOsc.CHATBOX_MAX_CHARS) },
                        label = { Text(label) },
                    )
                }
            }
        }

        SectionCard(title = "Presets", subtitle = "One tap sends the saved message") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { preset ->
                    PresetButton(
                        preset = preset,
                        onSend = { viewModel.sendChatbox(preset.text) },
                        onDelete = { viewModel.deletePreset(preset.id) },
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
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("Save message")
                }
            }
        }

        SectionCard(
            title = "Delivery",
            subtitle = "These apply to every message you send",
            trailing = { Icon(Icons.Filled.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        ) {
            DeliveryToggle(
                title = "Silent delivery",
                description = "Do not play VRChat's notification sound",
                checked = settings.chatboxSilent,
                onCheckedChange = { value -> viewModel.updateSettings { setChatboxSilent(value) } },
            )
            DeliveryToggle(
                title = "Typing indicator",
                description = "Show the typing bubble while this message is being edited",
                checked = settings.chatboxShowTyping,
                onCheckedChange = { value -> viewModel.updateSettings { setChatboxTyping(value) } },
            )
        }
    }
}

@Composable
private fun BudgetBadge(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(7.dp),
        color = color.copy(alpha = 0.12f),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun PresetButton(
    preset: ChatboxPreset,
    onSend: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier.width(220.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        onClick = onSend,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(preset.label, style = MaterialTheme.typography.titleMedium)
                Text(
                    preset.text.replace('\n', ' ').take(32),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Delete ${preset.label}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DeliveryToggle(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private val QUICK_INSERTS = listOf(
    "Heart" to "\u2665",
    "Star" to "\u2606",
    "Music" to "\u266A",
    "Sun" to "\u2600",
    "Play" to "\u25B6",
    "Wave" to "o/",
    "Happy" to "^^",
    "New line" to "\n",
)
