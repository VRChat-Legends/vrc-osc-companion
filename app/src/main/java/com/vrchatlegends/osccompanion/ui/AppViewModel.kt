package com.vrchatlegends.osccompanion.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vrchatlegends.osccompanion.bridge.PcBridge
import com.vrchatlegends.osccompanion.data.AppSettings
import com.vrchatlegends.osccompanion.data.ChatboxPreset
import com.vrchatlegends.osccompanion.data.PresetStore
import com.vrchatlegends.osccompanion.data.SettingsStore
import com.vrchatlegends.osccompanion.data.StatusLine
import com.vrchatlegends.osccompanion.logs.VrcLogReader
import com.vrchatlegends.osccompanion.osc.OscArg
import com.vrchatlegends.osccompanion.osc.OscRepository
import com.vrchatlegends.osccompanion.osc.VrcOsc
import com.vrchatlegends.osccompanion.pulsoid.HeartRateState
import com.vrchatlegends.osccompanion.pulsoid.PulsoidClient
import com.vrchatlegends.osccompanion.service.OscForegroundService
import com.vrchatlegends.osccompanion.status.DeviceStats
import com.vrchatlegends.osccompanion.status.StatusComposer
import com.vrchatlegends.osccompanion.vrcl.CommunityRepository
import com.vrchatlegends.osccompanion.vrcl.LeaderboardRepository
import com.vrchatlegends.osccompanion.vrcl.VrclAuth
import com.vrchatlegends.osccompanion.vrcl.VrclClient
import com.vrchatlegends.osccompanion.vrcl.VrclEvent
import com.vrchatlegends.osccompanion.vrcl.VrclLiveFeed
import com.vrchatlegends.osccompanion.vrcl.VrclProfile
import com.vrchatlegends.osccompanion.vrchat.VrchatClient
import com.vrchatlegends.osccompanion.vrchat.VrchatSessionStore
import com.vrchatlegends.osccompanion.vrchat.VrchatToolsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsStore = SettingsStore(application)
    private val presetStore = PresetStore(application)

    val osc: OscRepository = OscRepository.get(application)

    val settings: StateFlow<AppSettings> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    /** Null until DataStore has emitted, so an existing user never sees first-run UI flash. */
    val onboardingCompleted: StateFlow<Boolean?> = settingsStore.settings
        .map { it.onboardingCompleted as Boolean? }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val chatboxPresets: StateFlow<List<ChatboxPreset>> = presetStore.chatboxPresets
        .stateIn(viewModelScope, SharingStarted.Eagerly, PresetStore.DEFAULT_PRESETS)

    val rotationLines: StateFlow<List<StatusLine>> = presetStore.rotationLines
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val pulsoid = PulsoidClient(viewModelScope) { bpm -> onHeartRate(bpm) }
    val heartRate: StateFlow<HeartRateState> get() = pulsoid.state

    private val vrclClient = VrclClient { settings.value.vrclToken.takeIf { it.isNotBlank() } }
    private val vrclLive = VrclLiveFeed { settings.value.vrclToken.takeIf { it.isNotBlank() } }

    val vrchatTools = VrchatToolsRepository(
        scope = viewModelScope,
        client = VrchatClient(VrchatSessionStore(application)),
    )

    val community = CommunityRepository(
        scope = viewModelScope,
        client = vrclClient,
        live = vrclLive,
        isSignedIn = { settings.value.vrclToken.isNotBlank() },
    )

    val leaderboard = LeaderboardRepository(
        scope = viewModelScope,
        client = vrclClient,
    )

    private val _profile = MutableStateFlow<VrclProfile?>(null)
    val profile: StateFlow<VrclProfile?> = _profile.asStateFlow()

    private val _vrclEvents = MutableStateFlow<List<VrclEvent>>(emptyList())
    val vrclEvents: StateFlow<List<VrclEvent>> = _vrclEvents.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val composer = StatusComposer()
    private var statusJob: Job? = null
    private var lastPulsoidToken: String? = null

    val bridge: StateFlow<PcBridge.Stats> get() = osc.bridge.stats

    private val _logSources = MutableStateFlow<List<VrcLogReader.LogSource>>(emptyList())
    val logSources: StateFlow<List<VrcLogReader.LogSource>> = _logSources.asStateFlow()

    private val _logLines = MutableStateFlow<List<VrcLogReader.LogLine>>(emptyList())
    val logLines: StateFlow<List<VrcLogReader.LogLine>> = _logLines.asStateFlow()

    private val _logStatus = MutableStateFlow("Not scanned yet")
    val logStatus: StateFlow<String> = _logStatus.asStateFlow()

    private var selectedLog: VrcLogReader.LogSource? = null
    private var logOffset = -1L
    private var logJob: Job? = null

    init {
        viewModelScope.launch {
            settings.collect { current ->
                osc.applySettings(current)
                syncPulsoid(current)
                syncStatusLoop(current)
            }
        }
        viewModelScope.launch {
            val initial = settingsStore.settings.first()
            osc.applySettings(initial)
            if (initial.autoConnect) connect()
            if (initial.vrclToken.isNotBlank()) refreshProfile()
        }
        startHeartbeat()
        vrchatTools.restoreSession()
    }

    // ── Connection ──────────────────────────────────────────────────────────────

    fun connect() {
        osc.start()
        // Horizon OS freezes a 2D panel the moment the user drops into VRChat, which kills
        // the socket. The service is what keeps OSC alive, so it is not optional.
        OscForegroundService.start(getApplication())
    }

    fun disconnect() {
        osc.stop()
        OscForegroundService.stop(getApplication())
    }

    fun toggleConnection() {
        if (osc.connection.value.running) disconnect() else connect()
    }

    // ── Settings ────────────────────────────────────────────────────────────────

    fun updateSettings(block: suspend SettingsStore.() -> Unit) {
        viewModelScope.launch { settingsStore.block() }
    }

    fun setOnboardingCompleted(completed: Boolean) {
        updateSettings { setOnboardingCompleted(completed) }
    }

    // ── Chatbox ─────────────────────────────────────────────────────────────────

    fun sendChatbox(text: String) = osc.sendChatbox(text, immediate = true)

    fun openKeyboardWith(text: String) = osc.sendChatbox(text, immediate = false)

    fun setTyping(typing: Boolean) {
        if (settings.value.chatboxShowTyping) osc.setTyping(typing)
    }

    fun clearChatbox() = osc.clearChatbox()

    fun savePreset(preset: ChatboxPreset) {
        viewModelScope.launch {
            val current = chatboxPresets.value.filterNot { it.id == preset.id }
            presetStore.saveChatboxPresets(current + preset)
        }
    }

    fun deletePreset(id: String) {
        viewModelScope.launch {
            presetStore.saveChatboxPresets(chatboxPresets.value.filterNot { it.id == id })
        }
    }

    fun saveRotationLines(lines: List<StatusLine>) {
        viewModelScope.launch { presetStore.saveRotationLines(lines) }
    }

    // ── Status loop ─────────────────────────────────────────────────────────────

    private fun syncStatusLoop(current: AppSettings) {
        if (!current.statusEnabled) {
            statusJob?.cancel()
            statusJob = null
            return
        }
        if (statusJob?.isActive == true) return
        statusJob = viewModelScope.launch {
            while (true) {
                val now = settings.value
                if (!now.statusEnabled) break
                val text = composer.compose(
                    StatusComposer.Input(
                        settings = now,
                        device = DeviceStats.read(getApplication()),
                        heartRate = heartRate.value.bpm.takeIf { heartRate.value.isFresh },
                        vrclDisplayName = _profile.value?.displayName ?: now.vrclDisplayName,
                        rotationLines = rotationLines.value,
                    )
                )
                if (text.isNotBlank()) osc.sendChatbox(text, immediate = true, silent = true)
                delay(now.statusIntervalSec.coerceAtLeast(2) * 1000L)
            }
        }
    }

    // ── Heart rate ──────────────────────────────────────────────────────────────

    private fun syncPulsoid(current: AppSettings) {
        if (current.pulsoidToken == lastPulsoidToken) return
        lastPulsoidToken = current.pulsoidToken
        if (current.pulsoidToken.isBlank()) pulsoid.disconnect() else pulsoid.connect(current.pulsoidToken)
    }

    private fun onHeartRate(bpm: Int) {
        val current = settings.value
        if (!current.heartRateToParameters || !osc.connection.value.running) return
        val max = current.heartRateMax.coerceAtLeast(1)
        osc.setParameter(VrcOsc.HeartRateParams.CONNECTED, OscArg.OscBool(true))
        osc.setParameter(VrcOsc.HeartRateParams.ACTIVE, OscArg.OscBool(bpm > 0))
        osc.setParameter(VrcOsc.HeartRateParams.RAW, OscArg.OscInt(bpm))
        osc.setParameter(VrcOsc.HeartRateParams.PERCENT, OscArg.OscFloat((bpm.toFloat() / max).coerceIn(0f, 1f)))
        osc.setParameter(VrcOsc.HeartRateParams.ONES, OscArg.OscInt(bpm % 10))
        osc.setParameter(VrcOsc.HeartRateParams.TENS, OscArg.OscInt((bpm / 10) % 10))
        osc.setParameter(VrcOsc.HeartRateParams.HUNDREDS, OscArg.OscInt((bpm / 100) % 10))
    }

    // ── VRChat Legends ──────────────────────────────────────────────────────────

    fun signIn(providerId: String) {
        _authError.value = null
        VrclAuth.launch(getApplication(), providerId)
    }

    /** Called from MainActivity when the `vrcoscc://auth` deep link comes back. */
    fun handleAuthCallback(uri: Uri?) {
        val token = VrclAuth.extractToken(uri)
        if (token == null) {
            if (uri?.scheme.equals(com.vrchatlegends.osccompanion.BuildConfig.AUTH_REDIRECT_SCHEME, true)) {
                _authError.value = "Sign-in was cancelled or returned no token"
            }
            return
        }
        viewModelScope.launch {
            settingsStore.setVrclSession(token, "")
            refreshProfile()
        }
    }

    fun refreshProfile() {
        viewModelScope.launch {
            vrclClient.me()
                .onSuccess { p ->
                    _profile.value = p
                    _authError.value = null
                    settingsStore.setVrclSession(settings.value.vrclToken, p.displayName)
                    vrclClient.events().onSuccess { _vrclEvents.value = it }
                    community.refresh()
                }
                .onFailure {
                    _profile.value = null
                    _authError.value = it.message ?: "Could not load profile"
                }
        }
    }

    /**
     * Keeps the device row fresh and is what credits time to the usage leaderboard. The
     * backend clamps the gap between beats, so this interval is the unit of credited time.
     */
    private fun startHeartbeat() {
        viewModelScope.launch {
            while (isActive) {
                if (settings.value.vrclToken.isNotBlank()) {
                    vrclClient.heartbeat(installId, Build.MODEL ?: "Headset")
                }
                delay(HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    /** Stable per-install id so the backend can tell devices apart without identifying hardware. */
    private val installId: String by lazy {
        val prefs = getApplication<Application>()
            .getSharedPreferences("companion-install", Context.MODE_PRIVATE)
        prefs.getString("installId", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("installId", it).apply()
        }
    }

    fun signOut() {
        viewModelScope.launch {
            vrclClient.logout()
            settingsStore.clearVrclSession()
            _profile.value = null
            _vrclEvents.value = emptyList()
            community.onSignedOut()
        }
    }

    fun sendEventToChatbox(event: VrclEvent) {
        val line = buildString {
            append(event.title)
            event.startsAt?.let { append(" @ $it") }
            event.location?.let { append(" | $it") }
        }
        sendChatbox(line)
    }

    // ── VRChat logs ─────────────────────────────────────────────────────────────

    /**
     * Builds the list of readable log sources. Logcat comes first because on Quest that is
     * where VRChat actually logs, then any file we can reach.
     */
    fun scanLogs() {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val direct = VrcLogReader.findDirectLogs()
            val granted = settings.value.logFolderUri
                .takeIf { it.isNotBlank() }
                ?.let { Uri.parse(it) }
                ?.takeIf { VrcLogReader.hasTreePermission(app, it) }
            val fromTree = granted?.let { VrcLogReader.findTreeLogs(app, it) }.orEmpty()

            val files = (direct + fromTree).distinctBy { it.key }.sortedByDescending { it.lastModifiedMs }
            val all = listOf(VrcLogReader.logcatSource()) + files
            _logSources.value = all
            _logStatus.value = when {
                VrcLogReader.canReadOtherAppLogs(app) ->
                    "Log access granted. Reading VRChat from logcat." +
                        if (files.isNotEmpty()) " Plus ${files.size} log file(s)." else ""
                files.isNotEmpty() -> "${files.size} log file(s). Logcat needs the adb grant below."
                else -> "Logcat is not granted yet, and no log files are visible."
            }
            if (selectedLog == null) selectLog(all.first())
        }
    }

    fun onLogFolderPicked(uri: Uri) {
        val app = getApplication<Application>()
        VrcLogReader.persistTreePermission(app, uri)
        viewModelScope.launch {
            settingsStore.setLogFolderUri(uri.toString())
            scanLogs()
        }
    }

    fun selectLog(source: VrcLogReader.LogSource) {
        selectedLog = source
        logOffset = -1L
        _logLines.value = emptyList()
        refreshLog()
        if (settings.value.logAutoRefresh) startLogTail() else logJob?.cancel()
    }

    fun refreshLog() {
        val source = selectedLog ?: return
        viewModelScope.launch {
            val (lines, offset) = VrcLogReader.readTail(getApplication(), source, logOffset)
            logOffset = offset
            if (lines.isEmpty()) return@launch
            if (source.logcat) {
                // logcat has no byte cursor, so each read is a fresh snapshot of the tail.
                _logLines.value = lines.takeLast(LOG_LINE_CAPACITY)
                return@launch
            }
            _logLines.update { current ->
                val next = current + lines
                if (next.size > LOG_LINE_CAPACITY) next.takeLast(LOG_LINE_CAPACITY) else next
            }
        }
    }

    private fun startLogTail() {
        logJob?.cancel()
        logJob = viewModelScope.launch {
            while (true) {
                delay(LOG_TAIL_INTERVAL_MS)
                refreshLog()
            }
        }
    }

    fun setLogAutoRefresh(enabled: Boolean) {
        updateSettings { setLogAutoRefresh(enabled) }
        if (enabled) startLogTail() else logJob?.cancel()
    }

    override fun onCleared() {
        super.onCleared()
        statusJob?.cancel()
        logJob?.cancel()
        pulsoid.disconnect()
    }

    private companion object {
        const val LOG_LINE_CAPACITY = 1_000
        const val LOG_TAIL_INTERVAL_MS = 2_000L
        const val HEARTBEAT_INTERVAL_MS = 60_000L
    }
}
