package com.vrchatlegends.osccompanion.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vrchatlegends.osccompanion.osc.OscArg
import com.vrchatlegends.osccompanion.osc.ParameterState
import com.vrchatlegends.osccompanion.osc.VrcOsc
import com.vrchatlegends.osccompanion.ui.AppViewModel
import com.vrchatlegends.osccompanion.ui.ScreenScaffold
import com.vrchatlegends.osccompanion.ui.SectionCard

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ParametersScreen(viewModel: AppViewModel) {
    val parameters by viewModel.osc.parameters.collectAsStateWithLifecycle()
    val connection by viewModel.osc.connection.collectAsStateWithLifecycle()

    var filter by remember { mutableStateOf("") }
    var manualName by remember { mutableStateOf("") }
    var manualValue by remember { mutableStateOf("") }

    val visible = parameters.values
        .filter { filter.isBlank() || it.name.contains(filter, ignoreCase = true) }
        .sortedWith(compareByDescending<ParameterState> { it.name in VrcOsc.COMMON_PARAMETERS }.thenBy { it.name })

    ScreenScaffold(
        title = "Avatar parameters",
        subtitle = if (connection.vrchatPeer != null) {
            "Discovered over OSCQuery from ${connection.vrchatPeer?.name}"
        } else {
            "Learned from inbound OSC. Connect and move around to populate the list."
        },
    ) {
        SectionCard(title = "Emote wheel", subtitle = "Writes the stock VRCEmote parameter") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                VrcOsc.EMOTES.forEach { (value, label) ->
                    AssistChip(
                        onClick = { viewModel.osc.setParameter("VRCEmote", OscArg.OscInt(value)) },
                        label = { Text(label) },
                    )
                }
            }
        }

        SectionCard(title = "Send a parameter by hand") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = manualName,
                    onValueChange = { manualName = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = manualValue,
                    onValueChange = { manualValue = it },
                    label = { Text("Value") },
                    singleLine = true,
                    modifier = Modifier.width(160.dp),
                )
                Button(
                    onClick = {
                        viewModel.osc.setParameter(manualName.trim(), parseArg(manualValue))
                    },
                    enabled = manualName.isNotBlank() && manualValue.isNotBlank(),
                ) { Text("Send") }
            }
            Text(
                "true/false send a bool, whole numbers send an int, anything else sends a float.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(title = "Live parameters (${parameters.size})") {
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                label = { Text("Filter") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (visible.isEmpty()) {
                Text(
                    "Nothing yet. VRChat only streams parameters once OSC is enabled in its Action Menu.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                visible.take(200).forEach { param ->
                    ParameterRow(param) { value -> viewModel.osc.setParameter(param.name, value) }
                }
            }
        }
    }
}

@Composable
private fun ParameterRow(param: ParameterState, onSet: (OscArg) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(param.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                param.value?.display() ?: "-",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        when {
            param.isBool -> Switch(
                checked = (param.value as? OscArg.OscBool)?.value == true,
                enabled = param.writable,
                onCheckedChange = { onSet(OscArg.OscBool(it)) },
            )

            param.isFloat -> Slider(
                value = param.value?.asFloatOrNull() ?: 0f,
                valueRange = -1f..1f,
                enabled = param.writable,
                onValueChange = { onSet(OscArg.OscFloat(it)) },
                modifier = Modifier.width(220.dp),
            )

            param.isInt -> Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val current = (param.value?.asFloatOrNull() ?: 0f).toInt()
                OutlinedButton(onClick = { onSet(OscArg.OscInt(current - 1)) }, enabled = param.writable) { Text("-") }
                Text("$current", style = MaterialTheme.typography.bodyLarge)
                OutlinedButton(onClick = { onSet(OscArg.OscInt(current + 1)) }, enabled = param.writable) { Text("+") }
            }

            else -> Text(
                param.typeTag?.toString() ?: "?",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun parseArg(raw: String): OscArg {
    val trimmed = raw.trim()
    trimmed.toBooleanStrictOrNull()?.let { return OscArg.OscBool(it) }
    trimmed.toIntOrNull()?.let { return OscArg.OscInt(it) }
    trimmed.toFloatOrNull()?.let { return OscArg.OscFloat(it) }
    return OscArg.OscString(trimmed)
}
