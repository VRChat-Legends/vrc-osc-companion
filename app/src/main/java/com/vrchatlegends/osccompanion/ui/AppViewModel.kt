package com.vrchatlegends.osccompanion.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vrchatlegends.osccompanion.data.AppSettings
import com.vrchatlegends.osccompanion.data.ChatboxPreset
import com.vrchatlegends.osccompanion.data.PresetStore
import com.vrchatlegends.osccompanion.data.SettingsStore
import com.vrchatlegends.osccompanion.data.StatusLine
import com.vrchatlegends.osccompanion.osc.OscArg
import com.vrchatlegends.osccompanion.osc.OscRepository
import com.vrchatlegends.osccompanion.osc.VrcOsc
import com.vrchatlegends.osccompanion.pulsoid.HeartRateState
import com.vrchatlegends.osccompanion.pulsoid.PulsoidClient
import com.vrchatlegends.osccompanion.service.OscForegroundService
import com.vrchatlegends.osccompanion.status.DeviceStats
import com.vrchatlegends.osccompanion.status.StatusComposer
import com.vrchatlegends.osccompanion.vrcl.VrclAuth
import com.vrchatlegends.osccompanion.vrcl.VrclClient
import com.vrchatlegends.osccompanion.vrcl.VrclEvent
import com.vrchatlegends.osccompanion.vrcl.VrclProfile
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsStore = SettingsStore(application)
    private val presetStore = PresetStore(application)

    val osc: OscRepository = OscRepository.get(application)

    val settings: StateFlow<AppSettings> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val chatboxPresets: StateFlow<List<ChatboxPreset>> = presetStore.chatboxPresets
        .stateIn(viewModelScope, SharingStarted.Eagerly, PresetStore.DEFAULT_PRESETS)

    val rotationLines: StateFlow<List<StatusLine>> = presetStore.rotationLines
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val pulsoid = PulsoidClient(viewModelScope) { bpm -> onHeartRate(bpm) }
    val heartRate: StateFlow<HeartRateState> get() = pulsoid.state

    private val vrclClient = VrclClient { settings.value.vrclToken.takeIf { it.isNotBlank() } }

    private val _profile = MutableStateFlow<VrclProfile?>(null)
    val profile: StateFlow<VrclProfile?> = _profile.asStateFlow()

    private val _vrclEvents = MutableStateFlow<List<VrclEvent>>(emptyList())
    val vrclEvents: StateFlow<List<VrclEvent>> = _vrclEvents.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val composer = StatusComposer()
    private var statusJob: Job? = null
    private var lastPulsoidToken: String? = null

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
    }

    // ── Connection ──────────────────────────────────────────────────────────────

    fun connect() {
        osc.start()
        if (settings.value.keepAliveInBackground) {
            OscForegroundService.start(getApplication())
        }
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

    fun signInWithApiKey(key: String) {
        viewModelScope.launch {
            settingsStore.setVrclSession(key.trim(), "")
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
                }
                .onFailure {
                    _profile.value = null
                    _authError.value = it.message ?: "Could not load profile"
                }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            vrclClient.logout()
            settingsStore.clearVrclSession()
            _profile.value = null
            _vrclEvents.value = emptyList()
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

    override fun onCleared() {
        super.onCleared()
        statusJob?.cancel()
        pulsoid.disconnect()
    }
}
