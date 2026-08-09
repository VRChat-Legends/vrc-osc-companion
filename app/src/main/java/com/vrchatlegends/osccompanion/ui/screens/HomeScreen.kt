package com.vrchatlegends.osccompanion.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.vrchatlegends.osccompanion.ui.AppViewModel
import com.vrchatlegends.osccompanion.ui.Destination
import com.vrchatlegends.osccompanion.ui.LabelledValue
import com.vrchatlegends.osccompanion.ui.ScreenScaffold
import com.vrchatlegends.osccompanion.ui.SectionCard
import com.vrchatlegends.osccompanion.ui.StatusDot
import com.vrchatlegends.osccompanion.ui.StatusTone
import com.vrchatlegends.osccompanion.ui.theme.Bad
import com.vrchatlegends.osccompanion.ui.theme.Good

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(viewModel: AppViewModel, navController: NavController) {
    val connection by viewModel.osc.connection.collectAsStateWithLifecycle()
    val events by viewModel.osc.events.collectAsStateWithLifecycle()
    val parameters by viewModel.osc.parameters.collectAsStateWithLifecycle()
    val avatarId by viewModel.osc.avatarId.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()

    val tone = when {
        !connection.running -> StatusTone.IDLE
        connection.vrchatSeen -> StatusTone.GOOD
        else -> StatusTone.WARN
    }

    ScreenScaffold(
        title = "VRC OSC Companion",
        subtitle = profile?.let { "Signed in as ${it.displayName}" } ?: "Not signed in",
    ) {
        SectionCard(
            title = "OSC link",
            subtitle = when {
                !connection.running -> "Stopped"
                connection.vrchatSeen -> "VRChat is talking to us"
                else -> "Listening. Enable OSC in VRChat: Action Menu > Options > OSC > Enabled"
            },
            trailing = { StatusDot(tone) },
        ) {
            LabelledValue(
                "Sending to",
                "${connection.targetHost}:${connection.targetPort}" +
                    if (connection.autoHost) "  (Quest IP, auto)" else "",
            )
            LabelledValue("Listening on", if (connection.listenPort > 0) ":${connection.listenPort}" else "-")
            LabelledValue(
                "OSCQuery",
                if (connection.oscQueryHttpPort > 0) "advertising :${connection.oscQueryHttpPort}" else "off",
            )
            connection.vrchatPeer?.let { LabelledValue("VRChat peer", "${it.name} @ ${it.host}") }
            LabelledValue("Packets", "${connection.sent} out / ${connection.received} in")
            connection.error?.let { LabelledValue("Last error", it, valueColor = Bad) }

            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { viewModel.toggleConnection() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (connection.running) Bad else Good,
                    ),
                ) {
                    Text(if (connection.running) "Disconnect" else "Connect")
                }
                OutlinedButton(onClick = { viewModel.osc.releaseAllInputs() }) {
                    Text("Release all inputs")
                }
            }
        }

        SectionCard(title = "Quick actions") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AssistChip(
                    onClick = { navController.navigate(Destination.CHATBOX.route) },
                    label = { Text("Chatbox") },
                )
                AssistChip(
                    onClick = { navController.navigate(Destination.PARAMETERS.route) },
                    label = { Text("Parameters (${parameters.size})") },
                )
                AssistChip(
                    onClick = { navController.navigate(Destination.SCALE.route) },
                    label = { Text("Avatar scale") },
                )
                AssistChip(
                    onClick = { viewModel.clearChatbox() },
                    label = { Text("Clear chatbox") },
                )
                AssistChip(
                    onClick = { viewModel.osc.pulseButton("/input/Voice") },
                    label = { Text("Toggle voice") },
                )
            }
            avatarId?.let {
                Text(
                    "Current avatar: $it",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionCard(title = "Activity", subtitle = "Most recent first") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (events.isEmpty()) {
                    Text(
                        "Nothing yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                events.asReversed().take(10).forEach {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
