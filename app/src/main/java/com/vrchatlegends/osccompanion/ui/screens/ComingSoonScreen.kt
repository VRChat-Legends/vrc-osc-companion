package com.vrchatlegends.osccompanion.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vrchatlegends.osccompanion.ui.ScreenScaffold
import com.vrchatlegends.osccompanion.ui.SectionCard
import com.vrchatlegends.osccompanion.ui.theme.BrandCyan

/** A planned feature with a one line explanation of what it will do. */
data class PlannedFeature(val title: String, val detail: String)

/**
 * Shared placeholder for tabs that are announced but not built yet. Keeping them visible
 * sets expectations rather than shipping a surprise later.
 */
@Composable
fun ComingSoonScreen(
    title: String,
    subtitle: String,
    intro: String,
    features: List<PlannedFeature>,
    footnote: String? = null,
) {
    ScreenScaffold(title = title, subtitle = subtitle) {
        SectionCard(
            title = "Coming soon",
            trailing = {
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text("In development") },
                    colors = AssistChipDefaults.assistChipColors(disabledLabelColor = BrandCyan),
                )
            },
        ) {
            Text(intro, style = MaterialTheme.typography.bodyMedium)
        }

        SectionCard(title = "What is planned") {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                features.forEach { feature ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text("-", style = MaterialTheme.typography.bodyLarge, color = BrandCyan)
                        Column(Modifier.weight(1f)) {
                            Text(feature.title, style = MaterialTheme.typography.bodyLarge)
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

@Composable
fun VrchatToolsScreen() = ComingSoonScreen(
    title = "VRChat Tools",
    subtitle = "Signing in to your VRChat account, straight from the headset.",
    intro = "A second sign in, separate from VRChat Legends, that talks to the VRChat API on your " +
        "behalf. Nothing here is live yet and no VRChat credentials are collected by this build.",
    features = listOf(
        PlannedFeature(
            "Sign in to VRChat",
            "Username and password with two factor support. The session stays on this headset and " +
                "is never sent to VRChat Legends.",
        ),
        PlannedFeature(
            "Friends and instances",
            "See who is online and where, without leaving the panel.",
        ),
        PlannedFeature(
            "Invites and requests",
            "Accept an invite or fire one off while you are still in world.",
        ),
        PlannedFeature(
            "Avatar and world favourites",
            "Browse your favourites and jump to them.",
        ),
        PlannedFeature(
            "Notifications to the chatbox",
            "Push a friend request or invite straight into your VRChat chatbox using the OSC link " +
                "this app already holds open.",
        ),
    ),
    footnote = "The VRChat API is not officially documented for third party use, so this tab will " +
        "stay conservative: read mostly, rate limited, and easy to turn off. Your VRChat login will " +
        "be stored on the device only.",
)

@Composable
fun CommunityScreen() = ComingSoonScreen(
    title = "Community",
    subtitle = "VRChat Legends, in the headset.",
    intro = "The community side of VRChat Legends brought into the panel so you do not have to take " +
        "the headset off to see what is happening.",
    features = listOf(
        PlannedFeature(
            "Events",
            "Upcoming VRChat Legends events with a one tap push to your chatbox.",
        ),
        PlannedFeature(
            "Leaderboards",
            "Where you sit, updated live.",
        ),
        PlannedFeature(
            "Announcements",
            "Site and Discord announcements without alt tabbing.",
        ),
        PlannedFeature(
            "Shared presets",
            "Chatbox and status presets other people have published.",
        ),
    ),
    footnote = "Questions and suggestions go to the VRChat Legends Discord: https://discord.gg/6xPkZ7Dxp9",
)
