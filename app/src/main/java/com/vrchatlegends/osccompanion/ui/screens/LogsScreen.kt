package com.vrchatlegends.osccompanion.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vrchatlegends.osccompanion.logs.VrcLogReader
import com.vrchatlegends.osccompanion.ui.AppViewModel
import com.vrchatlegends.osccompanion.ui.LabelledValue
import com.vrchatlegends.osccompanion.ui.ScreenScaffold
import com.vrchatlegends.osccompanion.ui.SectionCard
import com.vrchatlegends.osccompanion.ui.theme.Bad
import com.vrchatlegends.osccompanion.ui.theme.Good
import com.vrchatlegends.osccompanion.ui.theme.SignalCyan
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
        title = "VRChat logs",
        subtitle = "Inspect worlds, players, errors, and Unity output from this headset",
    ) {
        SectionCard(
            title = "Access",
            subtitle = status,
        ) {
            val logcatGranted = remember { VrcLogReader.canReadOtherAppLogs(context) }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AccessTile("LOGCAT", logcatGranted, if (logcatGranted) "Ready" else "ADB grant needed")
                AccessTile("FILES", VrcLogReader.hasAllFilesAccess(), if (VrcLogReader.hasAllFilesAccess()) "Ready" else "Optional")
                AccessTile("FOLDER", settings.logFolderUri.isNotBlank(), if (settings.logFolderUri.isBlank()) "Not picked" else "Selected")
            }

            if (!logcatGranted) {
                Text(
                    "VRChat on Quest does not write a log file. Its output goes to logcat, and " +
                        "Android only lets one app read another's logcat with a permission that " +
                        "cannot be granted from inside an app. Run this once from a PC with the " +
                        "headset plugged in and Developer Mode on:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(7.dp),
                    color = Warn.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, Warn.copy(alpha = 0.35f)),
                ) {
                    Text(
                        VrcLogReader.GRANT_COMMAND,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = Warn,
                    )
                }
                Text(
                    "It survives reboots but not a reinstall. Add .debug to the package name for " +
                        "sideloaded debug builds.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.scanLogs() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Text("Refresh sources")
                }
                OutlinedButton(onClick = { folderPicker.launch(VrcLogReader.openTreeIntent()) }) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = null)
                    Text("Pick a log folder")
                }
                VrcLogReader.allFilesAccessIntent(context)?.let { intent ->
                    OutlinedButton(onClick = { allFilesLauncher.launch(intent) }) {
                        Icon(Icons.Filled.AdminPanelSettings, contentDescription = null)
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
                            EventBadge(event.kind)
                            Text(event.detail, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
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
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (filter.isNotEmpty()) {
                            IconButton(onClick = { filter = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear log filter")
                            }
                        }
                    },
                )
                FilterChip(
                    selected = showErrorsOnly,
                    onClick = { showErrorsOnly = !showErrorsOnly },
                    label = { Text("Errors only") },
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
                IconButton(onClick = { viewModel.refreshLog() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh current log")
                }
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
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(4.dp),
                            color = when (line.level) {
                                VrcLogReader.Level.ERROR, VrcLogReader.Level.EXCEPTION -> Bad.copy(alpha = 0.08f)
                                VrcLogReader.Level.WARNING -> Warn.copy(alpha = 0.06f)
                                else -> Color.Transparent
                            },
                        ) {
                            Text(
                                line.raw,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
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
}

@Composable
private fun AccessTile(label: String, granted: Boolean, detail: String) {
    val color = if (granted) Good else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = Modifier.width(160.dp),
        shape = RoundedCornerShape(7.dp),
        color = color.copy(alpha = 0.09f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.28f)),
    ) {
        Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(Modifier.size(7.dp).background(color, RoundedCornerShape(50)))
                Text(label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
            }
            Text(detail, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun EventBadge(kind: VrcLogReader.SessionEvent.Kind) {
    val color = when (kind) {
        VrcLogReader.SessionEvent.Kind.ERROR -> Bad
        VrcLogReader.SessionEvent.Kind.PLAYER_JOIN -> Good
        VrcLogReader.SessionEvent.Kind.PLAYER_LEAVE -> Warn
        else -> SignalCyan
    }
    Surface(shape = RoundedCornerShape(4.dp), color = color.copy(alpha = 0.11f)) {
        Text(
            kind.name.replace('_', ' '),
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "${bytes / (1024L * 1024L)} MB"
    bytes >= 1024L -> "${bytes / 1024L} KB"
    else -> "$bytes B"
}

private fun formatTime(millis: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(millis))
