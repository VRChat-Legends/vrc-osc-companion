package com.vrchatlegends.osccompanion.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vrchatlegends.osccompanion.osc.OscDirection
import com.vrchatlegends.osccompanion.ui.AppViewModel
import com.vrchatlegends.osccompanion.ui.ScreenScaffold
import com.vrchatlegends.osccompanion.ui.SectionCard
import com.vrchatlegends.osccompanion.ui.theme.BrandCyan
import com.vrchatlegends.osccompanion.ui.theme.Good
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MonitorScreen(viewModel: AppViewModel) {
    val log by viewModel.osc.log.collectAsStateWithLifecycle()
    val connection by viewModel.osc.connection.collectAsStateWithLifecycle()

    var filter by remember { mutableStateOf("") }
    var showOutbound by remember { mutableStateOf(true) }
    var showInbound by remember { mutableStateOf(true) }

    val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }

    val visible = log.asReversed().filter { entry ->
        val directionOk = when (entry.direction) {
            OscDirection.OUT -> showOutbound
            OscDirection.IN -> showInbound
        }
        directionOk && (filter.isBlank() || entry.message.address.contains(filter, ignoreCase = true))
    }

    ScreenScaffold(
        title = "Monitor",
        subtitle = "${connection.sent} sent, ${connection.received} received, newest first",
    ) {
        SectionCard(title = "Filters") {
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                label = { Text("Address contains") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Switch(checked = showOutbound, onCheckedChange = { showOutbound = it })
                    Text("Outgoing", style = MaterialTheme.typography.bodyMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Switch(checked = showInbound, onCheckedChange = { showInbound = it })
                    Text("Incoming", style = MaterialTheme.typography.bodyMedium)
                }
                Button(onClick = { viewModel.osc.clearLog() }) { Text("Clear") }
            }
        }

        SectionCard(title = "Traffic (${visible.size})") {
            if (visible.isEmpty()) {
                Text(
                    "Nothing matching. VRChat only sends once OSC is enabled in its Action Menu.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(visible.take(400)) { entry ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            timeFormat.format(Date(entry.timestampMs)),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            if (entry.direction == OscDirection.OUT) "OUT" else "IN ",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = if (entry.direction == OscDirection.OUT) BrandCyan else Good,
                        )
                        Column {
                            Text(
                                entry.message.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }
        }
    }
}
