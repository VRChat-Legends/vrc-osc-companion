package com.vrchatlegends.osccompanion.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
        title = "OSC monitor",
        subtitle = "${connection.sent} sent, ${connection.received} received, newest first",
    ) {
        SectionCard(
            title = "Traffic filters",
            subtitle = "${visible.size} matching packets",
            trailing = {
                IconButton(onClick = { viewModel.osc.clearLog() }) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear traffic log")
                }
            },
        ) {
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                label = { Text("Address contains") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (filter.isNotEmpty()) {
                        IconButton(onClick = { filter = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear address filter")
                        }
                    }
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = showInbound,
                    onClick = { showInbound = !showInbound },
                    label = { Text("Incoming") },
                    leadingIcon = { DirectionBadge(OscDirection.IN, compact = true) },
                )
                FilterChip(
                    selected = showOutbound,
                    onClick = { showOutbound = !showOutbound },
                    label = { Text("Outgoing") },
                    leadingIcon = { DirectionBadge(OscDirection.OUT, compact = true) },
                )
            }
        }

        SectionCard(title = "Live traffic", subtitle = "Up to 400 recent packets") {
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
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                        Text(
                            timeFormat.format(Date(entry.timestampMs)),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        DirectionBadge(entry.direction)
                        Column(Modifier.weight(1f)) {
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
}

@Composable
private fun DirectionBadge(direction: OscDirection, compact: Boolean = false) {
    val color = if (direction == OscDirection.OUT) BrandCyan else Good
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.12f),
    ) {
        Text(
            if (direction == OscDirection.OUT) "OUT" else "IN",
            modifier = Modifier.padding(
                horizontal = if (compact) 5.dp else 7.dp,
                vertical = if (compact) 1.dp else 2.dp,
            ),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
    }
}
