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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
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
import com.vrchatlegends.osccompanion.ui.theme.Good
import com.vrchatlegends.osccompanion.ui.theme.SignalCoral
import com.vrchatlegends.osccompanion.ui.theme.SignalCyan
import com.vrchatlegends.osccompanion.ui.theme.Warn

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
        title = "PC Link",
        subtitle = "Two-way OSC between VRChat on this Quest and tools on your PC",
    ) {
        SectionCard(
            title = "Network path",
            subtitle = "The companion relays VRChat's headset-only OSC over your Wi-Fi",
        ) {
            BridgePath(
                questIp = questIp,
                pcHost = settings.bridgePcHost.ifBlank { "Your PC" },
                pcPort = settings.bridgePcPort,
            )
            Text(
                "Desktop tools listen on $pcPortDraft. To send back into VRChat, target " +
                    "$questIp:$listenPortDraft.",
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
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BridgeMetric("TO PC", stats.uplinkSent, SignalCyan)
                BridgeMetric("FROM PC", stats.downlinkReceived, Good)
                BridgeMetric("FILTERED", stats.uplinkDropped, Warn)
                BridgeMetric("REJECTED", stats.downlinkRejected, Bad)
            }
            stats.lastDownlinkAddress?.let { LabelledValue("Last from PC", it) }
            stats.lastRejectedFrom?.let {
                LabelledValue("Last rejected source", it, valueColor = Bad)
            }
            stats.error?.let { Text(it, color = Bad, style = MaterialTheme.typography.bodyMedium) }
        }

        SectionCard(
            title = "Connect your PC",
            subtitle = if (settings.bridgePcHost.isBlank()) {
                "Enter your PC's local network address first"
            } else if (settings.bridgeEnabled) {
                "Bridge enabled"
            } else {
                "Ready to enable"
            },
            trailing = {
                Switch(
                    checked = settings.bridgeEnabled,
                    onCheckedChange = { v -> viewModel.updateSettings { setBridgeEnabled(v) } },
                    enabled = settings.bridgePcHost.isNotBlank(),
                )
            },
        ) {

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
                ) {
                    Icon(Icons.Filled.Save, contentDescription = null)
                    Text("Save connection")
                }
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
            trailing = { Icon(Icons.Filled.Security, contentDescription = null, tint = SignalCoral) },
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

@Composable
private fun BridgePath(questIp: String, pcHost: String, pcPort: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BridgeNode(Icons.Filled.Headset, "VRChat", "Quest")
        Icon(
            Icons.Filled.ArrowForward,
            contentDescription = null,
            tint = SignalCyan,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        BridgeNode(Icons.Filled.Headset, "Companion", questIp)
        Icon(
            Icons.Filled.ArrowForward,
            contentDescription = null,
            tint = SignalCyan,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        BridgeNode(Icons.Filled.Computer, "PC tool", "$pcHost:$pcPort")
    }
}

@Composable
private fun BridgeNode(icon: ImageVector, title: String, detail: String) {
    Surface(
        modifier = Modifier.width(180.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(shape = RoundedCornerShape(7.dp), color = SignalCyan.copy(alpha = 0.12f)) {
                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = SignalCyan, modifier = Modifier.size(20.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun BridgeMetric(label: String, value: Long, color: Color) {
    Surface(
        shape = RoundedCornerShape(7.dp),
        color = color.copy(alpha = 0.10f),
    ) {
        Column(
            modifier = Modifier.width(130.dp).padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
            Text(value.toString(), style = MaterialTheme.typography.titleMedium)
        }
    }
}
