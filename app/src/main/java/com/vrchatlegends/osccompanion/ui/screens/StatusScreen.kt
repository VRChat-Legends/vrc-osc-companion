package com.vrchatlegends.osccompanion.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vrchatlegends.osccompanion.data.StatusLine
import com.vrchatlegends.osccompanion.ui.AppViewModel
import com.vrchatlegends.osccompanion.ui.LabelledValue
import com.vrchatlegends.osccompanion.ui.ScreenScaffold
import com.vrchatlegends.osccompanion.ui.SectionCard
import com.vrchatlegends.osccompanion.ui.StatusDot
import com.vrchatlegends.osccompanion.ui.StatusTone
import com.vrchatlegends.osccompanion.ui.theme.Bad
import com.vrchatlegends.osccompanion.ui.theme.Good
import com.vrchatlegends.osccompanion.ui.theme.SignalCoral
import com.vrchatlegends.osccompanion.ui.theme.SignalCyan
import com.vrchatlegends.osccompanion.ui.theme.Warn

@Composable
fun StatusScreen(viewModel: AppViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val heartRate by viewModel.heartRate.collectAsStateWithLifecycle()
    val rotation by viewModel.rotationLines.collectAsStateWithLifecycle()

    var newLine by remember { mutableStateOf("") }
    var tokenDraft by remember { mutableStateOf("") }

    ScreenScaffold(
        title = "Status line",
        subtitle = "Build and rotate an automatic VRChat chatbox status",
    ) {
        SectionCard(
            title = "Automatic status",
            subtitle = if (settings.statusEnabled) {
                "Live, sending every ${settings.statusIntervalSec} seconds"
            } else {
                "Preview and configure before turning it on"
            },
            trailing = {
                Switch(
                    checked = settings.statusEnabled,
                    onCheckedChange = { value -> viewModel.updateSettings { setStatusEnabled(value) } },
                )
            },
        ) {
            StatusPreview(
                prefix = settings.statusPrefix,
                clock = settings.statusShowClock,
                battery = settings.statusShowBattery,
                heartRate = settings.statusShowHeartRate,
                vrcl = settings.statusShowVrcl,
                rotationLine = rotation.firstOrNull { it.enabled }?.text,
                bpm = heartRate.bpm.takeIf { heartRate.isFresh },
            )

            ToggleRow("Clock", "Current local time", settings.statusShowClock) { v -> viewModel.updateSettings { setStatusClock(v) } }
            ToggleRow("Headset battery", "Quest battery percentage", settings.statusShowBattery) { v -> viewModel.updateSettings { setStatusBattery(v) } }
            ToggleRow("Heart rate", "Live Pulsoid BPM", settings.statusShowHeartRate) { v -> viewModel.updateSettings { setStatusHeartRate(v) } }
            ToggleRow("VRChat Legends name", "Your signed-in display name", settings.statusShowVrcl) { v -> viewModel.updateSettings { setStatusVrcl(v) } }

            OutlinedTextField(
                value = settings.statusPrefix,
                onValueChange = { value -> viewModel.updateSettings { setStatusPrefix(value) } },
                label = { Text("Prefix") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Interval", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(90.dp))
                Slider(
                    value = settings.statusIntervalSec.toFloat(),
                    valueRange = 2f..60f,
                    onValueChange = { value ->
                        viewModel.updateSettings { setStatusInterval(value.toInt()) }
                    },
                    modifier = Modifier.weight(1f),
                )
                Surface(shape = RoundedCornerShape(6.dp), color = SignalCyan.copy(alpha = 0.11f)) {
                    Text(
                        "${settings.statusIntervalSec}s",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = SignalCyan,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Text(
                "VRChat throttles the chatbox, so anything under about 1.5 seconds gets dropped.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(title = "Rotating lines", subtitle = "Optional custom lines, one per cycle in order") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rotation.forEach { line ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(7.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 10.dp, top = 4.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Switch(
                            checked = line.enabled,
                            onCheckedChange = { enabled ->
                                viewModel.saveRotationLines(
                                    rotation.map { if (it.id == line.id) it.copy(enabled = enabled) else it }
                                )
                            },
                        )
                        Text(line.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = {
                            viewModel.saveRotationLines(rotation.filterNot { it.id == line.id })
                        }) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove ${line.text}")
                        }
                    }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newLine,
                    onValueChange = { newLine = it },
                    label = { Text("New line") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        viewModel.saveRotationLines(
                            rotation + StatusLine(System.currentTimeMillis().toString(), newLine.trim())
                        )
                        newLine = ""
                    },
                    enabled = newLine.isNotBlank(),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("Add line")
                }
            }
        }

        SectionCard(
            title = "Heart rate",
            subtitle = "Pulsoid real-time websocket",
            trailing = {
                StatusDot(
                    when {
                        heartRate.connected && heartRate.isFresh -> StatusTone.GOOD
                        heartRate.connected -> StatusTone.WARN
                        else -> StatusTone.IDLE
                    }
                )
            },
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = if (heartRate.isFresh) Good.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("LIVE HEART RATE", style = MaterialTheme.typography.labelSmall, color = if (heartRate.isFresh) Good else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            if (heartRate.connected) "Pulsoid connected" else "No Pulsoid connection",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Text(
                        if (heartRate.bpm > 0) "${heartRate.bpm} BPM" else "-- BPM",
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (heartRate.isFresh) Good else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            heartRate.error?.let { LabelledValue("Error", it, valueColor = Bad) }

            OutlinedTextField(
                value = tokenDraft,
                onValueChange = { tokenDraft = it },
                label = { Text("Pulsoid access token") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                supportingText = {
                    Text(
                        if (settings.pulsoidToken.isBlank()) {
                            "Get one at pulsoid.net. Needs an access token, not a widget URL."
                        } else {
                            "A token is saved. Type a new one to replace it."
                        }
                    )
                },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = {
                        viewModel.updateSettings { setPulsoidToken(tokenDraft) }
                        tokenDraft = ""
                    },
                    enabled = tokenDraft.isNotBlank(),
                ) {
                    Icon(Icons.Filled.Save, contentDescription = null)
                    Text("Save and connect")
                }

                OutlinedButton(
                    onClick = { viewModel.updateSettings { setPulsoidToken("") } },
                    enabled = settings.pulsoidToken.isNotBlank(),
                ) {
                    Icon(Icons.Filled.LinkOff, contentDescription = null)
                    Text("Disconnect")
                }
            }

            ToggleRow(
                "Write HR avatar parameters",
                "Drive compatible avatar menus with the current BPM",
                settings.heartRateToParameters,
            ) { v ->
                viewModel.updateSettings { setHeartRateToParameters(v) }
            }
            Text(
                "Writes HR, HRPercent, isHRConnected, isHRActive, onesHR, tensHR and hundredsHR.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("HRPercent max", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(140.dp))
                Slider(
                    value = settings.heartRateMax.toFloat(),
                    valueRange = 80f..260f,
                    onValueChange = { value -> viewModel.updateSettings { setHeartRateMax(value.toInt()) } },
                    modifier = Modifier.weight(1f),
                )
                Text("${settings.heartRateMax}", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun StatusPreview(
    prefix: String,
    clock: Boolean,
    battery: Boolean,
    heartRate: Boolean,
    vrcl: Boolean,
    rotationLine: String?,
    bpm: Int?,
) {
    val modules = buildList {
        if (prefix.isNotBlank()) add(prefix)
        if (clock) add("12:34")
        if (battery) add("Quest 82%")
        if (heartRate) add("${bpm ?: 72} BPM")
        if (vrcl) add("Legend")
        rotationLine?.takeIf { it.isNotBlank() }?.let(::add)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = SignalCoral.copy(alpha = 0.09f),
        border = BorderStroke(1.dp, SignalCoral.copy(alpha = 0.28f)),
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("CHATBOX PREVIEW", style = MaterialTheme.typography.labelSmall, color = SignalCoral, fontWeight = FontWeight.Bold)
            Text(
                modules.joinToString("  •  ").ifBlank { "Enable a module below" },
                style = MaterialTheme.typography.bodyLarge,
                color = if (modules.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ToggleRow(label: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
