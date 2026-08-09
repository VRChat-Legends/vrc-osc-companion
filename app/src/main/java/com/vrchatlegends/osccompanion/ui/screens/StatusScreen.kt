package com.vrchatlegends.osccompanion.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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

@Composable
fun StatusScreen(viewModel: AppViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val heartRate by viewModel.heartRate.collectAsStateWithLifecycle()
    val rotation by viewModel.rotationLines.collectAsStateWithLifecycle()

    var newLine by remember { mutableStateOf("") }
    var tokenDraft by remember { mutableStateOf("") }

    ScreenScaffold(
        title = "Status",
        subtitle = "Composes one rotating chatbox line out of the modules you enable",
    ) {
        SectionCard(
            title = "Auto chatbox",
            subtitle = "Sends every ${settings.statusIntervalSec}s, silently",
            trailing = {
                Switch(
                    checked = settings.statusEnabled,
                    onCheckedChange = { value -> viewModel.updateSettings { setStatusEnabled(value) } },
                )
            },
        ) {
            ToggleRow("Clock", settings.statusShowClock) { v -> viewModel.updateSettings { setStatusClock(v) } }
            ToggleRow("Headset battery", settings.statusShowBattery) { v -> viewModel.updateSettings { setStatusBattery(v) } }
            ToggleRow("Heart rate", settings.statusShowHeartRate) { v -> viewModel.updateSettings { setStatusHeartRate(v) } }
            ToggleRow("VRChat Legends name", settings.statusShowVrcl) { v -> viewModel.updateSettings { setStatusVrcl(v) } }

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
                Text("${settings.statusIntervalSec}s", style = MaterialTheme.typography.bodyMedium)
            }

            Text(
                "VRChat throttles the chatbox, so anything under about 1.5 seconds gets dropped.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(title = "Rotating lines", subtitle = "One is shown per cycle, in order") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rotation.forEach { line ->
                    Row(
                        Modifier.fillMaxWidth(),
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
                        Button(onClick = {
                            viewModel.saveRotationLines(rotation.filterNot { it.id == line.id })
                        }) { Text("Remove") }
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
                ) { Text("Add") }
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
            LabelledValue("BPM", if (heartRate.bpm > 0) heartRate.bpm.toString() else "-")
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
                ) { Text("Save and connect") }

                Button(
                    onClick = { viewModel.updateSettings { setPulsoidToken("") } },
                    enabled = settings.pulsoidToken.isNotBlank(),
                ) { Text("Disconnect") }
            }

            ToggleRow("Write HR avatar parameters", settings.heartRateToParameters) { v ->
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
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
