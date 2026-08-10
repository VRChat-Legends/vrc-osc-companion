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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vrchatlegends.osccompanion.osc.OscArg
import com.vrchatlegends.osccompanion.osc.ParameterState
import com.vrchatlegends.osccompanion.osc.VrcOsc
import com.vrchatlegends.osccompanion.ui.AppViewModel
import com.vrchatlegends.osccompanion.ui.ScreenScaffold
import com.vrchatlegends.osccompanion.ui.SectionCard
import com.vrchatlegends.osccompanion.ui.theme.SignalCoral
import com.vrchatlegends.osccompanion.ui.theme.SignalCyan

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ParametersScreen(viewModel: AppViewModel) {
    val parameters by viewModel.osc.parameters.collectAsStateWithLifecycle()
    val connection by viewModel.osc.connection.collectAsStateWithLifecycle()

    var filter by remember { mutableStateOf("") }
    var manualName by remember { mutableStateOf("") }
    var manualValue by remember { mutableStateOf("") }
    var listFilter by remember { mutableStateOf(ParameterListFilter.ALL) }
    var showManual by remember { mutableStateOf(false) }

    val visible = parameters.values
        .filter { filter.isBlank() || it.name.contains(filter, ignoreCase = true) }
        .filter {
            when (listFilter) {
                ParameterListFilter.ALL -> true
                ParameterListFilter.COMMON -> it.name in VrcOsc.COMMON_PARAMETERS
                ParameterListFilter.WRITABLE -> it.writable
            }
        }
        .sortedWith(compareByDescending<ParameterState> { it.name in VrcOsc.COMMON_PARAMETERS }.thenBy { it.name })

    ScreenScaffold(
        title = "Avatar parameters",
        subtitle = if (connection.vrchatPeer != null) {
            "Discovered over OSCQuery from ${connection.vrchatPeer?.name}"
        } else {
            "Learned from inbound OSC. Connect and move around to populate the list."
        },
    ) {
        SectionCard(title = "Emote wheel", subtitle = "Trigger a stock VRChat emote") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                VrcOsc.EMOTES.forEach { (value, label) ->
                    AssistChip(
                        onClick = { viewModel.osc.setParameter("VRCEmote", OscArg.OscInt(value)) },
                        label = { Text(label) },
                    )
                }
            }
        }

        SectionCard(
            title = "Live parameters",
            subtitle = "${visible.size} shown from ${parameters.size} discovered",
            trailing = { ParameterCountBadge(parameters.size) },
        ) {
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                label = { Text("Search parameters") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (filter.isNotEmpty()) {
                        IconButton(onClick = { filter = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search")
                        }
                    }
                },
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ParameterListFilter.entries.forEach { mode ->
                    FilterChip(
                        selected = listFilter == mode,
                        onClick = { listFilter = mode },
                        label = { Text(mode.label) },
                    )
                }
            }

            if (visible.isEmpty()) {
                Text(
                    if (parameters.isEmpty()) {
                        "Nothing yet. Enable OSC in VRChat, then move or change an avatar control."
                    } else {
                        "No parameters match this search and filter."
                    },
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

        OutlinedButton(onClick = { showManual = !showManual }) {
            Icon(
                if (showManual) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
            )
            Text(if (showManual) "Hide manual sender" else "Advanced: send by name")
        }

        AnimatedVisibility(visible = showManual) {
            SectionCard(
                title = "Manual parameter",
                subtitle = "Use this when a parameter is not in the discovered list",
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = manualName,
                        onValueChange = { manualName = it },
                        label = { Text("Parameter name") },
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
                    ) {
                        Icon(Icons.Filled.Send, contentDescription = null)
                        Text("Send")
                    }
                }
                Text(
                    "true or false sends a bool; whole numbers send an int; decimals send a float.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ParameterRow(param: ParameterState, onSet: (OscArg) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(7.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(
                        param.name,
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!param.writable) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = "Read only",
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TypeBadge(parameterType(param))
                    Text(
                        param.value?.display() ?: "No value",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
                    modifier = Modifier.width(190.dp),
                )

                param.isInt -> Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val current = (param.value?.asFloatOrNull() ?: 0f).toInt()
                    IconButton(onClick = { onSet(OscArg.OscInt(current - 1)) }, enabled = param.writable) {
                        Icon(Icons.Filled.Remove, contentDescription = "Decrease ${param.name}")
                    }
                    Text("$current", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { onSet(OscArg.OscInt(current + 1)) }, enabled = param.writable) {
                        Icon(Icons.Filled.Add, contentDescription = "Increase ${param.name}")
                    }
                }

                else -> Text(
                    param.typeTag?.toString() ?: "?",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ParameterCountBadge(count: Int) {
    Surface(shape = RoundedCornerShape(7.dp), color = SignalCyan.copy(alpha = 0.12f)) {
        Text(
            count.toString(),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            color = SignalCyan,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun TypeBadge(type: String) {
    val color = when (type) {
        "BOOL" -> SignalCoral
        "FLOAT" -> SignalCyan
        else -> MaterialTheme.colorScheme.tertiary
    }
    Surface(shape = RoundedCornerShape(4.dp), color = color.copy(alpha = 0.12f)) {
        Text(
            type,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun parameterType(param: ParameterState): String = when {
    param.isBool -> "BOOL"
    param.isFloat -> "FLOAT"
    param.isInt -> "INT"
    else -> param.typeTag?.toString()?.uppercase() ?: "OTHER"
}

private enum class ParameterListFilter(val label: String) {
    ALL("All"),
    COMMON("Common"),
    WRITABLE("Writable"),
}

private fun parseArg(raw: String): OscArg {
    val trimmed = raw.trim()
    trimmed.toBooleanStrictOrNull()?.let { return OscArg.OscBool(it) }
    trimmed.toIntOrNull()?.let { return OscArg.OscInt(it) }
    trimmed.toFloatOrNull()?.let { return OscArg.OscFloat(it) }
    return OscArg.OscString(trimmed)
}
