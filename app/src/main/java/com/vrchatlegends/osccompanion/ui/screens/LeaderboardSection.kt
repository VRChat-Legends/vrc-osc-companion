package com.vrchatlegends.osccompanion.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vrchatlegends.osccompanion.ui.theme.Bad
import com.vrchatlegends.osccompanion.ui.theme.SignalCoral
import com.vrchatlegends.osccompanion.ui.theme.SignalCyan
import com.vrchatlegends.osccompanion.vrcl.LeaderboardRepository
import com.vrchatlegends.osccompanion.vrcl.LeaderboardState
import com.vrchatlegends.osccompanion.vrcl.UsageRange
import com.vrchatlegends.osccompanion.vrcl.VrclUsageEntry
import com.vrchatlegends.osccompanion.vrcl.formatUsage

/**
 * Time-in-app ranking, rendered inside the Community tab's list. The backend only tracks
 * accounts with a linked Legend profile, so the empty state has to say why.
 */
internal fun LazyListScope.leaderboardItems(state: LeaderboardState, repository: LeaderboardRepository) {
    item(key = "usage-ranges") {
        RangeTabs(state.range, repository::selectRange, repository::refresh)
    }

    state.viewer?.let { viewer ->
        item(key = "usage-viewer") { ViewerCard(viewer) }
    }

    if (state.loading && state.entries.isEmpty()) {
        item(key = "usage-loading") {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = SignalCyan)
        }
    }

    state.error?.let { message ->
        item(key = "usage-error") {
            Surface(shape = RoundedCornerShape(8.dp), color = Bad.copy(alpha = 0.10f)) {
                Text(
                    message,
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Bad,
                )
            }
        }
    }

    if (state.entries.isEmpty() && state.loaded && state.error == null) {
        item(key = "usage-empty") { LeaderboardEmpty() }
    }

    items(state.entries, key = { "usage-${it.playerId}" }) { entry ->
        LeaderboardRow(entry)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RangeTabs(selected: UsageRange, onSelect: (UsageRange) -> Unit, onRefresh: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            UsageRange.entries.forEach { range ->
                FilterChip(
                    selected = range == selected,
                    onClick = { onSelect(range) },
                    label = { Text(range.label) },
                )
            }
        }
        IconButton(onClick = onRefresh) {
            Icon(Icons.Filled.Refresh, contentDescription = "Refresh leaderboard")
        }
    }
}

@Composable
private fun ViewerCard(entry: VrclUsageEntry) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = SignalCoral.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, SignalCoral.copy(alpha = 0.30f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("#${entry.rank}", style = MaterialTheme.typography.headlineSmall, color = SignalCoral)
            Column(Modifier.weight(1f)) {
                Text("You are #${entry.rank}", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${formatUsage(entry.rangeSeconds)} in this range | ${formatUsage(entry.totalSeconds)} all time",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LeaderboardRow(entry: VrclUsageEntry) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (entry.isViewer) SignalCoral.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "#${entry.rank}",
                style = MaterialTheme.typography.titleMedium,
                color = if (entry.rank <= 3) SignalCoral else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
            )
            Avatar(entry.avatarUrl, entry.displayName, 40)
            Column(Modifier.weight(1f)) {
                Text(entry.displayName, style = MaterialTheme.typography.titleSmall)
                if (entry.streakDays > 1) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.LocalFireDepartment,
                            contentDescription = null,
                            tint = SignalCoral,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            " ${entry.streakDays} day streak",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Text(formatUsage(entry.rangeSeconds), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun LeaderboardEmpty() {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Nobody on the board yet", style = MaterialTheme.typography.titleMedium)
            Text(
                "Time is counted while the app is open, for accounts signed in to VRChat Legends " +
                    "that have a Legend profile linked. Sign in on the Account tab to join.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
