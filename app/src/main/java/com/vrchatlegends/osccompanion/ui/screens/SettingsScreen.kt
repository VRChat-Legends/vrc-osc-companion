package com.vrchatlegends.osccompanion.ui.screens

import android.content.Context
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.WarningAmber
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vrchatlegends.osccompanion.BuildConfig
import com.vrchatlegends.osccompanion.data.AppSettings
import com.vrchatlegends.osccompanion.data.AppTheme
import com.vrchatlegends.osccompanion.data.MAX_BACKGROUND_DIM
import com.vrchatlegends.osccompanion.data.MIN_BACKGROUND_DIM
import com.vrchatlegends.osccompanion.diag.CrashReporter
import com.vrchatlegends.osccompanion.logs.VrcLogReader
import com.vrchatlegends.osccompanion.net.NetworkUtils
import com.vrchatlegends.osccompanion.osc.VrcOsc
import com.vrchatlegends.osccompanion.ui.AppViewModel
import com.vrchatlegends.osccompanion.ui.LabelledValue
import com.vrchatlegends.osccompanion.ui.ScreenScaffold
import com.vrchatlegends.osccompanion.ui.SectionCard
import com.vrchatlegends.osccompanion.ui.ThemeModeSelector
import com.vrchatlegends.osccompanion.ui.theme.AccentChoices
import com.vrchatlegends.osccompanion.ui.theme.Bad
import com.vrchatlegends.osccompanion.ui.theme.Good
import com.vrchatlegends.osccompanion.ui.theme.SignalCyan
import com.vrchatlegends.osccompanion.ui.theme.Warn

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
    val targetChanged = hostDraft != settings.oscHost ||
        sendPortDraft != settings.oscSendPort.toString() ||
        receivePortDraft != settings.oscReceivePort.toString()

    ScreenScaffold(
        title = "Settings",
        subtitle = "VRC OSC Companion ${BuildConfig.VERSION_NAME}",
    ) {
        var showAdvanced by rememberSaveable { mutableStateOf(false) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !showAdvanced,
                onClick = { showAdvanced = false },
                label = { Text("General") },
            )
            FilterChip(
                selected = showAdvanced,
                onClick = { showAdvanced = true },
                label = { Text("Advanced") },
            )
        }

        if (!showAdvanced) {
            AppearanceSection(
                accentArgb = settings.accentColor,
                appTheme = settings.appTheme,
                backgroundUri = settings.backgroundUri,
                backgroundDim = settings.backgroundDim,
                viewModel = viewModel,
            )
        }

        if (showAdvanced) SectionCard(
            title = "OSC target",
            subtitle = "Where this app sends OSC messages",
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = if (connection.vrchatSeen) Good.copy(alpha = 0.10f)
                else SignalCyan.copy(alpha = 0.08f),
                border = BorderStroke(
                    1.dp,
                    if (connection.vrchatSeen) Good.copy(alpha = 0.32f)
                    else SignalCyan.copy(alpha = 0.24f),
                ),
            ) {
                Row(
                    modifier = Modifier.padding(15.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier.size(42.dp),
                        shape = CircleShape,
                        color = if (connection.vrchatSeen) Good.copy(alpha = 0.14f)
                        else SignalCyan.copy(alpha = 0.12f),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.Router,
                                contentDescription = null,
                                tint = if (connection.vrchatSeen) Good else SignalCyan,
                            )
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (connection.vrchatSeen) "VRChat discovered" else "Current OSC target",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (connection.vrchatSeen) Good else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "${connection.targetHost}:${connection.targetPort}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Text(
                        if (settings.isAutoHost) "AUTO" else "MANUAL",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (settings.isAutoHost) SignalCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            LabelledValue("This Quest", questIp)

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

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
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
                    enabled = targetChanged && !hostInvalid && !sendPortInvalid && !receivePortInvalid,
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    Text(if (targetChanged) "Apply target" else "Applied")
                }
            }

            Text(
                "VRChat defaults to receiving on ${VrcOsc.DEFAULT_SEND_PORT} and sending on " +
                    "${VrcOsc.DEFAULT_RECEIVE_PORT}. Only change these if you launched VRChat with --osc=.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (showAdvanced) SectionCard(
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

        if (!showAdvanced) SectionCard(title = "Behaviour") {
            ToggleSetting(
                "Connect on launch",
                "Opens the socket as soon as the panel starts.",
                settings.autoConnect,
            ) { v -> viewModel.updateSettings { setAutoConnect(v) } }

            Text(
                "Background running is always on. Horizon OS freezes a 2D panel as soon as you drop " +
                    "into VRChat, so the app runs a foreground service whenever it is connected. " +
                    "That notification is what keeps OSC alive and cannot be turned off.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (!showAdvanced) CameraCaptureSection(viewModel, settings)

        // Nothing to explain once the user already has Developer Mode on.
        if (showAdvanced && !remember { VrcLogReader.isDeveloperModeOn(context) }) {
            SectionCard(
                title = "Meta Developer Mode",
                subtitle = "Most OSC tools do not need it",
            ) {
                Text(
                    "Meta Horizon phone app > Menu > Devices > your headset > Headset settings > " +
                        "Developer Mode. A free verified developer organisation on your Meta account is " +
                        "required before the switch appears.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                DeveloperModeRequirement(
                    required = true,
                    title = "Developer Mode required",
                    detail = "Now playing, VRChat log access, and sideloaded builds.",
                )
                DeveloperModeRequirement(
                    required = false,
                    title = "Works without Developer Mode",
                    detail = "Chatbox, parameters, input, avatar scale, status, heart rate, and PC Link.",
                )
            }
        }

        if (!showAdvanced) SectionCard(title = "About") {
            LabelledValue("Version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            LabelledValue("Package", BuildConfig.APPLICATION_ID)
            Text(
                "Enable OSC in VRChat: Action Menu > Options > OSC > Enabled. " +
                    "The OSC Debug view in the same menu shows what VRChat is receiving.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = { viewModel.setOnboardingCompleted(false) }) {
                Icon(Icons.Filled.RestartAlt, contentDescription = null)
                Text("Run setup again")
            }
        }

        if (showAdvanced) {
            var lastCrash by remember { mutableStateOf(CrashReporter.lastCrash(context)) }
            lastCrash?.let { trace ->
            SectionCard(
                title = "Last crash",
                subtitle = "Captured on this headset, send this to support",
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(7.dp),
                    color = Bad.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, Bad.copy(alpha = 0.30f)),
                ) {
                    Text(
                        trace.lineSequence().take(14).joinToString("\n"),
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                OutlinedButton(
                    onClick = {
                        CrashReporter.clear(context)
                        lastCrash = null
                    },
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    Text("Clear")
                }
            }
        }
        }
    }
}

/**
 * Accent colour and a custom wallpaper.
 *
 * The picker is [ActivityResultContracts.OpenDocument] rather than the photo picker: Horizon OS
 * ships the storage access framework but not Google's media picker.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearanceSection(
    accentArgb: Long,
    appTheme: AppTheme,
    backgroundUri: String,
    backgroundDim: Float,
    viewModel: AppViewModel,
) {
    val pickBackground = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::setBackgroundUri) }

    var dimDraft by remember(backgroundDim) {
        mutableStateOf(backgroundDim.coerceIn(MIN_BACKGROUND_DIM, MAX_BACKGROUND_DIM))
    }

    SectionCard(
        title = "Appearance",
        subtitle = "Theme, colour, and your own background",
    ) {
        Text("Theme", style = MaterialTheme.typography.titleMedium)
        ThemeModeSelector(
            selected = appTheme,
            onSelect = viewModel::setAppTheme,
        )

        Text("Accent", style = MaterialTheme.typography.titleMedium)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AccentChoices.forEachIndexed { index, (name, color) ->
                // Index 0 is the built in look, which is stored as 0 rather than its argb so a
                // future palette change still lands for anyone who never picked a colour.
                val stored = if (index == 0) 0L else color.toArgb().toLong()
                AccentSwatch(
                    name = name,
                    color = color,
                    selected = accentArgb == stored,
                    onClick = { viewModel.setAccentColor(stored) },
                )
            }
        }

        Text("Background", style = MaterialTheme.typography.titleMedium)
        Text(
            if (backgroundUri.isBlank()) "Pick an image or a video and it loops behind the app."
            else "Custom background active. Videos loop muted.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { pickBackground.launch(arrayOf("image/*", "video/*")) }) {
                Text(if (backgroundUri.isBlank()) "Choose background" else "Replace")
            }
            if (backgroundUri.isNotBlank()) {
                OutlinedButton(onClick = viewModel::clearBackground) { Text("Remove") }
            }
        }

        if (backgroundUri.isNotBlank()) {
            Text(
                "Dim ${(dimDraft * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = dimDraft,
                onValueChange = { dimDraft = it },
                onValueChangeFinished = { viewModel.setBackgroundDim(dimDraft) },
                valueRange = MIN_BACKGROUND_DIM..MAX_BACKGROUND_DIM,
            )
        }
    }
}

@Composable
private fun AccentSwatch(name: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(color, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = Color.Black.copy(alpha = 0.75f),
                )
            }
        }
        Text(name, style = MaterialTheme.typography.labelSmall)
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

@Composable
private fun DeveloperModeRequirement(required: Boolean, title: String, detail: String) {
    val color = if (required) Warn else Good
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            if (required) Icons.Filled.WarningAmber else Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(22.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = color)
            Text(
                detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CameraCaptureSection(viewModel: AppViewModel, settings: AppSettings) {
    val setup by viewModel.captureSetup.collectAsStateWithLifecycle()
    val watcher by viewModel.cameraCapture.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val pickFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> uri?.let(viewModel::setCaptureFolder) }

    SectionCard(
        title = "Camera to Discord",
        subtitle = "Off until you opt in, and never sends old photos",
    ) {
        Text(
            "Sends each new VRChat camera picture to the Discord webhook saved in your " +
                "VRChat Legends account settings. Uploads go through your signed-in account; " +
                "the webhook URL itself never reaches this headset.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { pickFolder.launch(vrchatPhotosInitialUri()) }) {
                Text(if (settings.captureFolderUri.isBlank()) "Choose VRChat photos folder" else "Change folder")
            }
            OutlinedButton(
                onClick = { uriHandler.openUri("${BuildConfig.VRCL_BASE_URL}/account/settings?tab=notifications") },
            ) {
                Text("Webhook settings")
            }
        }

        if (settings.captureFolderUri.isNotBlank()) {
            LabelledValue("Watched folder", captureFolderLabel(settings.captureFolderUri))
        }

        ToggleSetting(
            "Auto-send newest capture",
            "JPEG, PNG, or WebP up to 8 MB. Pictures taken before enabling are never sent.",
            settings.captureAutoSend,
        ) { viewModel.setCaptureAutoSend(it) }

        if (setup.busy) {
            Text(
                "Checking...",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        setup.error?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = Bad)
        }
        setup.notice?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = Good)
        }
        if (settings.captureAutoSend) {
            watcher.message?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Drops the system picker straight onto the VRChat photo folder. */
private fun vrchatPhotosInitialUri() = DocumentsContract.buildDocumentUri(
    "com.android.externalstorage.documents",
    "primary:Pictures/VRChat",
)

private fun captureFolderLabel(uri: String): String = runCatching {
    java.net.URLDecoder.decode(uri.substringAfterLast('/'), "UTF-8").substringAfterLast(':')
}.getOrDefault("Selected folder")
