package com.vrchatlegends.osccompanion.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vrchatlegends.osccompanion.ui.ScreenScaffold
import com.vrchatlegends.osccompanion.ui.SectionCard
import com.vrchatlegends.osccompanion.ui.theme.SignalCoral
import com.vrchatlegends.osccompanion.ui.theme.SignalCyan

/** A planned feature with a one line explanation of what it will do. */
data class PlannedFeature(val title: String, val detail: String)

/** A useful destination that remains available while the larger feature is being built. */
data class AvailableAction(val label: String, val url: String, val primary: Boolean = false)

/**
 * Shared placeholder for tabs that are announced but not built yet. Keeping them visible
 * sets expectations rather than shipping a surprise later.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ComingSoonScreen(
    title: String,
    subtitle: String,
    intro: String,
    features: List<PlannedFeature>,
    footnote: String? = null,
    availableActions: List<AvailableAction> = emptyList(),
) {
    val uriHandler = LocalUriHandler.current

    ScreenScaffold(title = title, subtitle = subtitle) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = SignalCyan.copy(alpha = 0.08f),
            border = BorderStroke(1.dp, SignalCyan.copy(alpha = 0.28f)),
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(shape = CircleShape, color = SignalCyan.copy(alpha = 0.14f)) {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Construction, contentDescription = null, tint = SignalCyan)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("In development", style = MaterialTheme.typography.titleMedium)
                        AssistChip(
                            onClick = {},
                            label = { Text("PLANNED") },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Schedule,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                        )
                    }
                    Text(
                        intro,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        SectionCard(title = "Roadmap", subtitle = "The first version is scoped around these workflows") {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                features.forEachIndexed { index, feature ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            modifier = Modifier.size(32.dp),
                            shape = CircleShape,
                            color = if (index == 0) SignalCoral.copy(alpha = 0.14f)
                            else MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    (index + 1).toString().padStart(2, '0'),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (index == 0) SignalCoral
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                feature.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                feature.detail,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        if (availableActions.isNotEmpty()) {
            SectionCard(
                title = "Available now",
                subtitle = "These open in the Quest browser",
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    availableActions.forEach { action ->
                        if (action.primary) {
                            Button(onClick = { uriHandler.openUri(action.url) }) {
                                Icon(Icons.Filled.OpenInNew, contentDescription = null)
                                Text(action.label)
                            }
                        } else {
                            OutlinedButton(onClick = { uriHandler.openUri(action.url) }) {
                                Icon(Icons.Filled.OpenInNew, contentDescription = null)
                                Text(action.label)
                            }
                        }
                    }
                }
            }
        }

        footnote?.let {
            SectionCard(title = "Note") {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
