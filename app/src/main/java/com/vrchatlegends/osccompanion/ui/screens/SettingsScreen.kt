package com.vrchatlegends.osccompanion.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vrchatlegends.osccompanion.BuildConfig
import com.vrchatlegends.osccompanion.net.NetworkUtils
import com.vrchatlegends.osccompanion.osc.VrcOsc
import com.vrchatlegends.osccompanion.ui.AppViewModel
import com.vrchatlegends.osccompanion.ui.LabelledValue
import com.vrchatlegends.osccompanion.ui.ScreenScaffold
import com.vrchatlegends.osccompanion.ui.SectionCard
import com.vrchatlegends.osccompanion.ui.theme.Bad

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(viewModel: AppViewModel) {
    val context: Context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val connection by viewModel.osc.connection.collectAsStateWithLifecycle()

    val questIp = remember { NetworkUtils.localIpv4OrLoopback(context) }
    val allIps = remember { NetworkUtils.enumerateIpv4() }

    var hostDraft by remember { mutableStateOf(settings.oscHost) }
    var sendPortDraft by remember { mutableStateOf(settings.oscSendPort.toString()) }
    var receivePortDraft by remember { mutableStateOf(settings.oscReceivePort.toString()) }

    LaunchedEffect(settings.oscHost) { hostDraft = settings.oscHost }

    val hostInvalid = hostDraft.isNotBlank() && !NetworkUtils.isValidHost(hostDraft)
    val sendPortInvalid = sendPortDraft.toIntOrNull()?.let { !NetworkUtils.isValidPort(it) } ?: true
    val receivePortInvalid = receivePortDraft.toIntOrNull()?.let { !NetworkUtils.isValidPort(it) } ?: true

    ScreenScaffold(
        title = "Settings",
        subtitle = "VRC OSC Companion ${BuildConfig.VERSION_NAME}",
    ) {
        SectionCard(
            title = "OSC target",
            subtitle = "Defaults to this headset's own address, which is where VRChat is listening " +
                "when it runs on the same Quest.",
        ) {
            LabelledValue("This Quest", questIp)
            LabelledValue("Currently sending to", "${connection.targetHost}:${connection.targetPort}")

            OutlinedTextField(
                value = hostDraft,
                onValueChange = { hostDraft = it },
                label = { Text("Host") },
                placeholder = { Text("Leave empty to follow the Quest IP") },
                singleLine = true,
                isError = hostInvalid,
                modifier = Modifier.fillMaxWidth(),
                supportingText = {
                    if (hostInvalid) {
                        Text("Not a valid host", color = Bad)
                    } else if (settings.isAutoHost) {
                        Text("Auto: re-resolved on every connect, so DHCP changes cannot break it")
                    }
                },
            )

            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { hostDraft = "" }, label = { Text("Auto (Quest IP)") })
                AssistChip(onClick = { hostDraft = NetworkUtils.LOOPBACK }, label = { Text("Loopback") })
                allIps.forEach { ip -> AssistChip(onClick = { hostDraft = ip }, label = { Text(ip) }) }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = sendPortDraft,
                    onValueChange = { sendPortDraft = it.filter { c -> c.isDigit() } },
                    label = { Text("Send port") },
                    singleLine = true,
                    isError = sendPortInvalid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(180.dp),
                )
                OutlinedTextField(
                    value = receivePortDraft,
                    onValueChange = { receivePortDraft = it.filter { c -> c.isDigit() } },
                    label = { Text("Listen port") },
                    singleLine = true,
                    isError = receivePortInvalid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(180.dp),
                )
                Button(
                    onClick = {
                        viewModel.updateSettings {
                            setOscHost(hostDraft)
                            sendPortDraft.toIntOrNull()?.let { setOscSendPort(it) }
                            receivePortDraft.toIntOrNull()?.let { setOscReceivePort(it) }
                        }
                    },
                    enabled = !hostInvalid && !sendPortInvalid && !receivePortInvalid,
                ) { Text("Apply") }
            }

            Text(
                "VRChat defaults to receiving on ${VrcOsc.DEFAULT_SEND_PORT} and sending on " +
                    "${VrcOsc.DEFAULT_RECEIVE_PORT}. Only change these if you launched VRChat with --osc=.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(
            title = "Discovery",
            subtitle = if (connection.oscQueryHttpPort > 0) {
                "Advertising on :${connection.oscQueryHttpPort}"
            } else {
                "Not advertising"
            },
        ) {
            ToggleSetting(
                "OSCQuery",
                "Finds VRChat over mDNS and negotiates ports automatically. Turn off only if mDNS is blocked.",
                settings.useOscQuery,
            ) { v -> viewModel.updateSettings { setUseOscQuery(v) } }

            ToggleSetting(
                "Broadcast",
                "Sends to the subnet broadcast address instead of one host. Useful when a second " +
                    "headset keeps changing IP, wasteful otherwise.",
                settings.useBroadcast,
            ) { v -> viewModel.updateSettings { setUseBroadcast(v) } }
        }

        SectionCard(title = "Behaviour") {
            ToggleSetting(
                "Connect on launch",
                "Opens the socket as soon as the panel starts.",
                settings.autoConnect,
            ) { v -> viewModel.updateSettings { setAutoConnect(v) } }

            ToggleSetting(
                "Stay alive in the background",
                "Runs a foreground service so the link survives while you are inside VRChat. " +
                    "Without it Horizon OS freezes the panel and OSC stops.",
                settings.keepAliveInBackground,
            ) { v -> viewModel.updateSettings { setKeepAlive(v) } }
        }

        SectionCard(title = "About") {
            LabelledValue("Version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            LabelledValue("Package", BuildConfig.APPLICATION_ID)
            Text(
                "Enable OSC in VRChat: Action Menu > Options > OSC > Enabled. " +
                    "The OSC Debug view in the same menu shows what VRChat is receiving.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ToggleSetting(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
