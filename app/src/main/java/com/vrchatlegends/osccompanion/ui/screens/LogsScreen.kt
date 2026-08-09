package com.vrchatlegends.osccompanion.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vrchatlegends.osccompanion.logs.VrcLogReader
import com.vrchatlegends.osccompanion.ui.AppViewModel
import com.vrchatlegends.osccompanion.ui.LabelledValue
import com.vrchatlegends.osccompanion.ui.ScreenScaffold
import com.vrchatlegends.osccompanion.ui.SectionCard
import com.vrchatlegends.osccompanion.ui.theme.Bad
import com.vrchatlegends.osccompanion.ui.theme.Good
import com.vrchatlegends.osccompanion.ui.theme.Warn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LogsScreen(viewModel: AppViewModel) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val sources by viewModel.logSources.collectAsStateWithLifecycle()
    val lines by viewModel.logLines.collectAsStateWithLifecycle()
    val status by viewModel.logStatus.collectAsStateWithLifecycle()

    var filter by remember { mutableStateOf("") }
    var showErrorsOnly by remember { mutableStateOf(false) }
    var showEvents by remember { mutableStateOf(true) }
    var selectedKey by remember { mutableStateOf<String?>(null) }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        if (result.resultCode == android.app.Activity.RESULT_OK && uri != null) {
            viewModel.onLogFolderPicked(uri)
        }
    }

    val allFilesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.scanLogs() }

    LaunchedEffect(Unit) { viewModel.scanLogs() }
    LaunchedEffect(sources) { selectedKey = selectedKey ?: sources.firstOrNull()?.key }

    val visible = remember(lines, filter, showErrorsOnly) {
        lines.asReversed().filter { line ->
            (!showErrorsOnly || line.level == VrcLogReader.Level.ERROR || line.level == VrcLogReader.Level.EXCEPTION) &&
                (filter.isBlank() || line.raw.contains(filter, ignoreCase = true))
        }
    }

    val events = remember(lines) {
        lines.asReversed().mapNotNull(VrcLogReader::extractEvent).take(40)
    }

    ScreenScaffold(
        title = "VRChat Logs",
        subtitle = "Reads VRChat's own log off this headset, no PC required.",
    ) {
        SectionCard(
            title = "Access",
            subtitle = status,
        ) {
            val logcatGranted = remember { VrcLogReader.canReadOtherAppLogs(context) }
            LabelledValue("Logcat access", if (logcatGranted) "Granted" else "Not granted")
            LabelledValue("All files access", if (VrcLogReader.hasAllFilesAccess()) "Granted" else "Not granted")
            LabelledValue(
                "Picked folder",
                if (settings.logFolderUri.isBlank()) "None" else "Set",
            )

            if (!logcatGranted) {
                Text(
                    "VRChat on Quest does not write a log file. Its output goes to logcat, and " +
                        "Android only lets one app read another's logcat with a permission that " +
                        "cannot be granted from inside an app. Run this once from a PC with the " +
                        "headset plugged in and Developer Mode on:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    VrcLogReader.GRANT_COMMAND,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = Warn,
                )
                Text(
                    "It survives reboots but not a reinstall. Add .debug to the package name for " +
                        "sideloaded debug builds.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.scanLogs() }) { Text("Scan") }
                OutlinedButton(onClick = { folderPicker.launch(VrcLogReader.openTreeIntent()) }) {
                    Text("Pick a log folder")
                }
                VrcLogReader.allFilesAccessIntent(context)?.let { intent ->
                    OutlinedButton(onClick = { allFilesLauncher.launch(intent) }) {
                        Text("Grant all files access")
                    }
                }
            }

            Text(
                "The folder picker is only useful for log files copied onto the headset, or for " +
                    "desktop VRChat logs. These are the paths that get probed:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.horizontalScroll(rememberScrollState())) {
                VrcLogReader.CANDIDATE_PACKAGES.forEach { pkg ->
                    Text(
                        "Android/data/$pkg/files",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }

        if (sources.isNotEmpty()) {
            SectionCard(title = "Log files", subtitle = "Newest first") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    sources.forEach { source ->
                        FilterChip(
                            selected = selectedKey == source.key,
                            onClick = {
                                selectedKey = source.key
                                viewModel.selectLog(source)
                            },
                            label = { Text("${source.displayName}  ${formatSize(source.sizeBytes)}") },
                        )
                    }
                }
                sources.firstOrNull { it.key == selectedKey }?.let { current ->
                    LabelledValue("Modified", formatTime(current.lastModifiedMs))
                    LabelledValue("Size", formatSize(current.sizeBytes))
                }
            }
        }

        SectionCard(
            title = "Session",
            subtitle = "World changes, players and errors pulled out of the stream",
            trailing = {
                Switch(checked = showEvents, onCheckedChange = { showEvents = it })
            },
        ) {
            if (!showEvents) {
                Text("Hidden", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (events.isEmpty()) {
                Text(
                    "Nothing yet. Events appear as VRChat writes them.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(events) { event ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                event.kind.name.replace('_', ' '),
                                style = MaterialTheme.typography.labelSmall,
                                color = when (event.kind) {
                                    VrcLogReader.SessionEvent.Kind.ERROR -> Bad
                                    VrcLogReader.SessionEvent.Kind.PLAYER_JOIN -> Good
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                            Text(event.detail, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        SectionCard(
            title = "Raw log",
            subtitle = "${visible.size} of ${lines.size} lines",
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    label = { Text("Filter") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                AssistChip(
                    onClick = { showErrorsOnly = !showErrorsOnly },
                    label = { Text(if (showErrorsOnly) "Errors only" else "All levels") },
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Follow live", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Re-reads the tail every couple of seconds.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.logAutoRefresh,
                    onCheckedChange = { viewModel.setLogAutoRefresh(it) },
                )
                OutlinedButton(onClick = { viewModel.refreshLog() }) { Text("Refresh") }
            }

            if (visible.isEmpty()) {
                Text(
                    "No lines to show.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(visible) { line ->
                        Text(
                            line.raw,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = when (line.level) {
                                VrcLogReader.Level.ERROR, VrcLogReader.Level.EXCEPTION -> Bad
                                VrcLogReader.Level.WARNING -> Warn
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "${bytes / (1024L * 1024L)} MB"
    bytes >= 1024L -> "${bytes / 1024L} KB"
    else -> "$bytes B"
}

private fun formatTime(millis: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(millis))
