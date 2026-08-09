package com.vrchatlegends.osccompanion.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vrchatlegends.osccompanion.bridge.PcBridge
import com.vrchatlegends.osccompanion.net.NetworkUtils
import com.vrchatlegends.osccompanion.ui.AppViewModel
import com.vrchatlegends.osccompanion.ui.LabelledValue
import com.vrchatlegends.osccompanion.ui.ScreenScaffold
import com.vrchatlegends.osccompanion.ui.SectionCard
import com.vrchatlegends.osccompanion.ui.StatusDot
import com.vrchatlegends.osccompanion.ui.StatusTone
import com.vrchatlegends.osccompanion.ui.theme.Bad

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BridgeScreen(viewModel: AppViewModel) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val stats by viewModel.bridge.collectAsStateWithLifecycle()
    val connection by viewModel.osc.connection.collectAsStateWithLifecycle()

    val questIp = remember { NetworkUtils.localIpv4OrLoopback(context) }

    var hostDraft by remember { mutableStateOf(settings.bridgePcHost) }
    var pcPortDraft by remember { mutableStateOf(settings.bridgePcPort.toString()) }
    var listenPortDraft by remember { mutableStateOf(settings.bridgeListenPort.toString()) }
    var blockDraft by remember { mutableStateOf(settings.bridgeBlockPrefixes) }
    var rateDraft by remember { mutableStateOf(settings.bridgeRateLimitHz.toString()) }

    LaunchedEffect(settings.bridgePcHost) { hostDraft = settings.bridgePcHost }

    val hostInvalid = hostDraft.isNotBlank() && !NetworkUtils.isValidHost(hostDraft)
    val pcPortInvalid = pcPortDraft.toIntOrNull()?.let { !NetworkUtils.isValidPort(it) } ?: true
    val listenPortInvalid = listenPortDraft.toIntOrNull()?.let { !NetworkUtils.isValidPort(it) } ?: true

    ScreenScaffold(
        title = "PC Bridge",
        subtitle = "Gives a desktop app two way OSC with VRChat running on this headset.",
    ) {
        SectionCard(
            title = "How it works",
            subtitle = "VRChat on Quest only ever sends OSC to 127.0.0.1, so a PC can never be the " +
                "direct destination. This app runs on the headset, so it can be that local listener " +
                "and relay everything over Wi-Fi.",
        ) {
            Text(
                "VRChat  ->  127.0.0.1:${connection.listenPort.takeIf { it > 0 } ?: 9001} (this app)  ->  " +
                    "${settings.bridgePcHost.ifBlank { "your PC" }}:${settings.bridgePcPort}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "your PC  ->  $questIp:${settings.bridgeListenPort} (this app)  ->  VRChat",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "The uplink is byte identical to a local VRChat, so desktop tools that already speak " +
                    "OSC work unchanged. Point them at this headset instead of localhost.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(
            title = "Status",
            trailing = {
                StatusDot(
                    when {
                        !stats.running -> StatusTone.IDLE
                        stats.pcSeen -> StatusTone.GOOD
                        else -> StatusTone.WARN
                    }
                )
            },
        ) {
            LabelledValue("Bridge", if (stats.running) "Running" else "Stopped")
            LabelledValue("This Quest", "$questIp:${stats.listenPort.takeIf { it > 0 } ?: settings.bridgeListenPort}")
            LabelledValue("PC target", stats.pcTarget.ifBlank { "not set" })
            LabelledValue("Sent to PC", "${stats.uplinkSent}")
            LabelledValue("Filtered out", "${stats.uplinkDropped}")
            LabelledValue("Received from PC", "${stats.downlinkReceived}")
            LabelledValue("Rejected", "${stats.downlinkRejected}")
            stats.lastDownlinkAddress?.let { LabelledValue("Last from PC", it) }
            stats.lastRejectedFrom?.let {
                LabelledValue("Last rejected source", it, valueColor = Bad)
            }
            stats.error?.let { Text(it, color = Bad, style = MaterialTheme.typography.bodyMedium) }
        }

        SectionCard(title = "Desktop") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Enable bridge", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Off by default. Needs a PC address before it will start.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.bridgeEnabled,
                    onCheckedChange = { v -> viewModel.updateSettings { setBridgeEnabled(v) } },
                    enabled = settings.bridgePcHost.isNotBlank(),
                )
            }

            OutlinedTextField(
                value = hostDraft,
                onValueChange = { hostDraft = it },
                label = { Text("PC address") },
                placeholder = { Text("192.168.1.x") },
                singleLine = true,
                isError = hostInvalid,
                modifier = Modifier.fillMaxWidth(),
                supportingText = {
                    if (hostInvalid) Text("Not a valid host", color = Bad)
                    else Text("The desktop that should receive VRChat's OSC output")
                },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = pcPortDraft,
                    onValueChange = { pcPortDraft = it.filter { c -> c.isDigit() } },
                    label = { Text("PC port") },
                    singleLine = true,
                    isError = pcPortInvalid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(170.dp),
                )
                OutlinedTextField(
                    value = listenPortDraft,
                    onValueChange = { listenPortDraft = it.filter { c -> c.isDigit() } },
                    label = { Text("Quest listen port") },
                    singleLine = true,
                    isError = listenPortInvalid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(210.dp),
                )
                Button(
                    onClick = {
                        viewModel.updateSettings {
                            setBridgePcHost(hostDraft)
                            pcPortDraft.toIntOrNull()?.let { setBridgePcPort(it) }
                            listenPortDraft.toIntOrNull()?.let { setBridgeListenPort(it) }
                        }
                    },
                    enabled = !hostInvalid && !pcPortInvalid && !listenPortInvalid,
                ) { Text("Apply") }
            }

            Text(
                "Port ${PcBridge.DEFAULT_PC_PORT} makes this headset look exactly like a local VRChat to " +
                    "your PC tool. The listen port has to differ from 9000 because VRChat already owns " +
                    "that one on the headset.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(
            title = "Traffic",
            subtitle = "Wi-Fi is the weak link, so keep high rate addresses off it.",
        ) {
            OutlinedTextField(
                value = blockDraft,
                onValueChange = { blockDraft = it },
                label = { Text("Never forward these prefixes") },
                placeholder = { Text("/tracking/, /avatar/parameters/Vel") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Comma separated. Tracking data updates every frame.") },
            )

            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { blockDraft = "" }, label = { Text("Forward everything") })
                AssistChip(onClick = { blockDraft = "/tracking/" }, label = { Text("Block tracking") })
                AssistChip(
                    onClick = { blockDraft = "/tracking/,/avatar/parameters/Velocity,/avatar/parameters/Angular" },
                    label = { Text("Block tracking and velocity") },
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = rateDraft,
                    onValueChange = { rateDraft = it.filter { c -> c.isDigit() } },
                    label = { Text("Max per address (Hz)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(230.dp),
                    supportingText = { Text("0 means no limit") },
                )
                Button(onClick = {
                    viewModel.updateSettings {
                        setBridgeBlockPrefixes(blockDraft)
                        rateDraft.toIntOrNull()?.let { setBridgeRateLimitHz(it) }
                    }
                }) { Text("Apply") }
            }
        }

        SectionCard(
            title = "Security",
            subtitle = "The listen port accepts OSC that gets injected straight into your VRChat session.",
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Only accept from the PC above", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Drops datagrams from any other address on the network. Leave this on unless " +
                            "you are deliberately testing from a second machine.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.bridgeRestrictToPcHost,
                    onCheckedChange = { v -> viewModel.updateSettings { setBridgeRestrictToPcHost(v) } },
                )
            }
            Text(
                "UDP source addresses can be forged, so treat this as a convenience filter and not a " +
                    "guarantee. Only run the bridge on networks you trust.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(
            title = "Wiring up the PC",
            subtitle = "No extra software is needed on the headset beyond this app.",
        ) {
            Text(
                "1. Put the PC and the Quest on the same network.\n" +
                    "2. Enter the PC address above and turn the bridge on.\n" +
                    "3. On the PC, listen on UDP ${settings.bridgePcPort} to receive everything VRChat emits, " +
                    "including avatar contact receivers and parameter changes.\n" +
                    "4. Send OSC back to $questIp:${settings.bridgeListenPort} and it lands in VRChat.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "While running, the app repeats ${PcBridge.ANNOUNCE_ADDRESS} to the PC carrying the headset " +
                    "address and listen port, so a tool can configure itself without you typing anything.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
