package com.vrchatlegends.osccompanion.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vrchatlegends.osccompanion.bridge.PcBridge
import com.vrchatlegends.osccompanion.capture.CameraCaptureFolder
import com.vrchatlegends.osccompanion.capture.CameraCapturePolicy
import com.vrchatlegends.osccompanion.capture.CameraCaptureState
import com.vrchatlegends.osccompanion.capture.CameraCaptureWatcher
import com.vrchatlegends.osccompanion.data.AppSettings
import com.vrchatlegends.osccompanion.data.AppTheme
import com.vrchatlegends.osccompanion.data.ChatboxPreset
import com.vrchatlegends.osccompanion.data.MAX_BACKGROUND_DIM
import com.vrchatlegends.osccompanion.data.MIN_BACKGROUND_DIM
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
import com.vrchatlegends.osccompanion.scripts.CompanionScriptRunner
import com.vrchatlegends.osccompanion.scripts.CompanionScriptStore
import com.vrchatlegends.osccompanion.scripts.ScriptRuntimeSnapshot
import com.vrchatlegends.osccompanion.status.DeviceStats
import com.vrchatlegends.osccompanion.status.StatusComposer
import com.vrchatlegends.osccompanion.vrcl.CommunityRepository
import com.vrchatlegends.osccompanion.vrcl.LeaderboardRepository
import com.vrchatlegends.osccompanion.vrcl.PickedMedia
import com.vrchatlegends.osccompanion.vrcl.VrclAuth
import com.vrchatlegends.osccompanion.vrcl.VrclAnnouncement
import com.vrchatlegends.osccompanion.vrcl.VrclClient
import com.vrchatlegends.osccompanion.vrcl.VrclEvent
import com.vrchatlegends.osccompanion.vrcl.VrclLiveFeed
import com.vrchatlegends.osccompanion.vrcl.VrclProfile
import com.vrchatlegends.osccompanion.vrcl.VrclSocialIdentity
import com.vrchatlegends.osccompanion.vrchat.VrchatClient
import com.vrchatlegends.osccompanion.vrchat.VrchatSessionStore
import com.vrchatlegends.osccompanion.vrchat.VrchatToolsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
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

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    /** Null until DataStore has emitted, so an existing user never sees first-run UI flash. */
    private val _onboardingCompleted = MutableStateFlow<Boolean?>(null)
    val onboardingCompleted: StateFlow<Boolean?> = _onboardingCompleted.asStateFlow()

    val chatboxPresets: StateFlow<List<ChatboxPreset>> = presetStore.chatboxPresets
        .stateIn(viewModelScope, SharingStarted.Eagerly, PresetStore.DEFAULT_PRESETS)

    val rotationLines: StateFlow<List<StatusLine>> = presetStore.rotationLines
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val pulsoid = PulsoidClient(viewModelScope) { bpm -> onHeartRate(bpm) }
    val heartRate: StateFlow<HeartRateState> get() = pulsoid.state

    private val vrclClient = VrclClient { settings.value.vrclToken.takeIf { it.isNotBlank() } }
    private val vrclLive = VrclLiveFeed { settings.value.vrclToken.takeIf { it.isNotBlank() } }
    private val scriptStore = CompanionScriptStore.create(application)
    private val scriptRunner = CompanionScriptRunner(
        scope = viewModelScope,
        loadScript = scriptStore::readForRun,
        runtimeSnapshot = {
            val connection = osc.connection.value
            ScriptRuntimeSnapshot(
                connected = connection.running && connection.vrchatSeen,
                avatarId = osc.avatarId.value,
                parameters = osc.parameters.value.values.toList(),
            )
        },
        sendSilentChatbox = osc::sendScriptChatbox,
        setAvatarParameter = osc::setScriptParameter,
    )

    val vrchatTools = VrchatToolsRepository(
        scope = viewModelScope,
        client = VrchatClient(VrchatSessionStore(application)),
    )

    val community = CommunityRepository(
        scope = viewModelScope,
        client = vrclClient,
        live = vrclLive,
        isSignedIn = { settings.value.vrclToken.isNotBlank() },
        readMedia = { uri -> readPickedMedia(uri) },
        scriptStore = scriptStore,
        scriptRunner = scriptRunner,
    )

    val leaderboard = LeaderboardRepository(
        scope = viewModelScope,
        client = vrclClient,
    )

    private val _profile = MutableStateFlow<VrclProfile?>(null)
    val profile: StateFlow<VrclProfile?> = _profile.asStateFlow()

    /** The newest staff push that has not been dismissed on this device. */
    private val _announcement = MutableStateFlow<VrclAnnouncement?>(null)
    val announcement: StateFlow<VrclAnnouncement?> = _announcement.asStateFlow()

    private var lastAnnouncementId: String? = null

    private val _groupInvite = MutableStateFlow(GroupInviteState())
    val groupInvite: StateFlow<GroupInviteState> = _groupInvite.asStateFlow()

    private var appResumed = false
    private var foregroundServiceRequested = false

    fun dismissAnnouncement() {
        _announcement.value = null
    }

    /**
     * Asks the site's VRChat account to invite this headset's signed in user to the group.
     * The backend owns the cooldown and the audit trail, so the app only reports what it says.
     */
    fun requestGroupInvite() {
        if (_groupInvite.value.busy) return
        val userId = vrchatTools.state.value.user?.id
        when {
            settings.value.vrclToken.isBlank() ->
                _groupInvite.value = GroupInviteState(error = "Sign in to VRChat Legends first.")

            userId.isNullOrBlank() ->
                _groupInvite.value = GroupInviteState(error = "Sign in to VRChat first.")

            else -> {
                _groupInvite.value = GroupInviteState(busy = true)
                viewModelScope.launch {
                    vrclClient.requestGroupInvite(userId)
                        .onSuccess { _groupInvite.value = GroupInviteState(message = it) }
                        .onFailure {
                            _groupInvite.value =
                                GroupInviteState(error = it.message ?: "Could not send the invite.")
                        }
                }
            }
        }
    }

    fun clearGroupInvite() {
        _groupInvite.value = GroupInviteState()
    }

    // ── Appearance ──────────────────────────────────────────────────────────────

    /** 0 restores the built in coral. Stored as ARGB so it round trips through DataStore. */
    fun setAccentColor(argb: Long) {
        _settings.update { it.copy(accentColor = argb) }
        updateSettings { setAccentColor(argb) }
    }

    fun setAppTheme(theme: AppTheme) {
        _settings.update { it.copy(appTheme = theme) }
        updateSettings { setAppTheme(theme) }
    }

    fun setBackgroundDim(value: Float) {
        val clamped = value.coerceIn(MIN_BACKGROUND_DIM, MAX_BACKGROUND_DIM)
        _settings.update { it.copy(backgroundDim = clamped) }
        updateSettings { setBackgroundDim(clamped) }
    }

    /**
     * Takes a persistable read grant before storing the uri, otherwise the wallpaper works
     * until the next launch and then silently disappears.
     */
    fun setBackgroundUri(uri: Uri) {
        runCatching {
            getApplication<Application>().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        val stored = uri.toString()
        _settings.update { it.copy(backgroundUri = stored) }
        updateSettings { setBackgroundUri(stored) }
    }

    fun clearBackground() {
        _settings.update { it.copy(backgroundUri = "") }
        updateSettings { setBackgroundUri("") }
    }

    fun isVideoUri(uri: String): Boolean {
        if (uri.isBlank()) return false
        return runCatching {
            getApplication<Application>().contentResolver.getType(Uri.parse(uri))
                ?.startsWith("video/") == true
        }.getOrDefault(false)
    }

    /** The Legend page identity, which owns the profile picture the app should display. */
    private val _legend = MutableStateFlow<VrclSocialIdentity?>(null)
    val legend: StateFlow<VrclSocialIdentity?> = _legend.asStateFlow()

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

    /** Who is in the VRChat instance right now, folded out of the log stream. */
    private val _instance = MutableStateFlow(VrcLogReader.InstanceState())
    val instance: StateFlow<VrcLogReader.InstanceState> = _instance.asStateFlow()
    val logStatus: StateFlow<String> = _logStatus.asStateFlow()

    private var selectedLog: VrcLogReader.LogSource? = null
    private var logOffset = -1L
    private var logJob: Job? = null

    init {
        viewModelScope.launch {
            settingsStore.settings.collect { current ->
                _settings.value = current
                _onboardingCompleted.value = current.onboardingCompleted
                osc.applySettings(current)
                syncPulsoid(current)
                syncStatusLoop(current)
            }
        }
        viewModelScope.launch {
            val initial = settingsStore.settings.first()
            osc.applySettings(initial)
            if (initial.autoConnect) {
                foregroundServiceRequested = true
                osc.start()
                startForegroundServiceIfAllowed()
            }
            if (initial.vrclToken.isNotBlank()) refreshProfile()
        }
        startHeartbeat()
        vrchatTools.restoreSession()
        // Picks the best readable source and starts tailing, so the instance roster is live
        // from launch instead of only after the user opens the Logs tab.
        scanLogs()
    }

    // ── Connection ──────────────────────────────────────────────────────────────

    fun connect() {
        foregroundServiceRequested = true
        osc.start()
        startForegroundServiceIfAllowed()
    }

    fun disconnect() {
        foregroundServiceRequested = false
        osc.stop()
        OscForegroundService.stopOsc(getApplication())
    }

    fun onAppResumed() {
        appResumed = true
        startForegroundServiceIfAllowed()
        // Revives the capture watcher after process death while the panel is resumed.
        if (settings.value.captureAutoSend) OscForegroundService.refresh(getApplication())
    }

    fun onAppPaused() {
        appResumed = false
    }

    private fun startForegroundServiceIfAllowed() {
        if (!appResumed || !foregroundServiceRequested) return
        // Horizon OS freezes a 2D panel when the user returns to VRChat. Starting while this
        // activity is resumed satisfies Android's foreground-service launch restriction.
        OscForegroundService.start(getApplication(), startOsc = true)
    }

    fun toggleConnection() {
        if (osc.connection.value.running) disconnect() else connect()
    }

    // ── Camera captures ─────────────────────────────────────────────────────────

    data class CaptureSetupState(
        val busy: Boolean = false,
        val error: String? = null,
        val notice: String? = null,
    )

    private val captureFolder = CameraCaptureFolder(application)

    val cameraCapture: StateFlow<CameraCaptureState> = CameraCaptureWatcher.get(application).state

    private val _captureSetup = MutableStateFlow(CaptureSetupState())
    val captureSetup: StateFlow<CaptureSetupState> = _captureSetup.asStateFlow()

    fun setCaptureFolder(uri: Uri) {
        runCatching {
            getApplication<Application>().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        adoptCaptureSource(uri.toString())
    }

    /** Finds Pictures/VRChat on shared storage without the folder picker. */
    fun setCaptureFolderAuto() {
        val folder = CameraCaptureFolder.autoFolder()
        if (folder == null) {
            _captureSetup.value = CaptureSetupState(
                error = "No Pictures/VRChat folder was found yet. Take one photo with the " +
                    "VRChat camera, or pick the folder by hand.",
            )
            return
        }
        adoptCaptureSource(CameraCaptureFolder.AUTO_SOURCE)
    }

    fun reportCapturePermissionDenied() {
        _captureSetup.value = CaptureSetupState(
            error = "Storage permission was denied, so the folder cannot be found automatically. " +
                "Pick it by hand instead.",
        )
    }

    private fun adoptCaptureSource(source: String) {
        viewModelScope.launch {
            _captureSetup.value = CaptureSetupState(busy = true)
            // Baseline before saving, so nothing already in the folder can ever be sent.
            val scan = captureFolder.scan(source)
            val existing = scan.getOrElse { emptyList() }
            val baseline = CameraCapturePolicy.baselineModifiedAt(existing, System.currentTimeMillis())
            settingsStore.setCaptureFolder(source, baseline)
            _captureSetup.value = when {
                scan.isFailure -> CaptureSetupState(
                    error = scan.exceptionOrNull()?.message ?: "Could not read that folder.",
                )
                existing.isEmpty() -> CaptureSetupState(
                    notice = "Folder saved, but no photos are in it yet. Make sure you picked " +
                        "Pictures/VRChat, then turn on auto-send.",
                )
                else -> CaptureSetupState(
                    notice = "Folder saved. ${existing.size} existing photos stay private; " +
                        "only pictures taken after you enable auto-send go to Discord.",
                )
            }
        }
    }

    fun setCaptureAutoSend(enabled: Boolean) {
        if (!enabled) {
            _captureSetup.value = CaptureSetupState()
            updateSettings { setCaptureAutoSend(false) }
            OscForegroundService.refresh(getApplication())
            return
        }
        val current = settings.value
        when {
            current.vrclToken.isBlank() ->
                _captureSetup.value = CaptureSetupState(error = "Sign in on the Account tab first.")

            current.captureFolderUri.isBlank() ->
                _captureSetup.value = CaptureSetupState(error = "Choose your VRChat Captures folder first.")

            else -> {
                _captureSetup.value = CaptureSetupState(busy = true)
                viewModelScope.launch {
                    val status = vrclClient.cameraCaptureStatus().getOrNull()
                    if (status == null) {
                        _captureSetup.value =
                            CaptureSetupState(error = "Could not reach VRChat Legends. Try again.")
                        return@launch
                    }
                    if (!status.enabled) {
                        _captureSetup.value = CaptureSetupState(
                            error = if (status.configured) {
                                "Turn on the Camera captures event for your webhook in the website settings."
                            } else {
                                "Add a Discord webhook in your website account settings first."
                            },
                        )
                        return@launch
                    }
                    val existing = captureFolder.scan(current.captureFolderUri).getOrElse { emptyList() }
                    val baseline =
                        CameraCapturePolicy.baselineModifiedAt(existing, System.currentTimeMillis())
                    settingsStore.enableCaptureAutoSend(baseline)
                    _captureSetup.value = CaptureSetupState()
                    OscForegroundService.refresh(getApplication())
                }
            }
        }
    }

    // ── Settings ────────────────────────────────────────────────────────────────

    fun updateSettings(block: suspend SettingsStore.() -> Unit) {
        viewModelScope.launch { settingsStore.block() }
    }

    fun setOnboardingCompleted(completed: Boolean) {
        _onboardingCompleted.value = completed
        _settings.update { it.copy(onboardingCompleted = completed) }
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
                    vrclClient.socialIdentity().onSuccess { _legend.value = it }
                    vrclClient.events().onSuccess { _vrclEvents.value = it }
                    community.refresh()
                }
                .onFailure {
                    _profile.value = null
                    _legend.value = null
                    _authError.value = it.message ?: "Could not load profile"
                }
        }
    }

    /** Reads a picked content:// URI into memory so the repository never touches Android APIs. */
    private suspend fun readPickedMedia(uri: String): PickedMedia? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = getApplication<Application>().contentResolver
            val parsed = Uri.parse(uri)
            val mime = resolver.getType(parsed) ?: return@runCatching null
            val bytes = resolver.openInputStream(parsed)?.use { it.readBytes() }
                ?: return@runCatching null
            PickedMedia(bytes, mime)
        }.getOrNull()
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
                        .onSuccess(::onAnnouncement)
                }
                delay(HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    /**
     * Surfaces a staff push once. The heartbeat keeps returning the same announcement until it
     * expires, so the id is what stops it reappearing after the user dismisses it.
     */
    private fun onAnnouncement(announcement: VrclAnnouncement?) {
        if (announcement == null || announcement.id == lastAnnouncementId) return
        lastAnnouncementId = announcement.id
        _announcement.value = announcement
        // sendChatbox already clips to VRChat's 144 character ceiling.
        if (announcement.chatbox) osc.sendChatbox("${announcement.title}: ${announcement.body}")
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
            _legend.value = null
            _vrclEvents.value = emptyList()
            _announcement.value = null
            _groupInvite.value = GroupInviteState()
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
            val logcatUsable = VrcLogReader.canReadOtherAppLogs(app)
            _logStatus.value = when {
                logcatUsable ->
                    "Log access granted. Reading VRChat from logcat." +
                        if (files.isNotEmpty()) " Plus ${files.size} log file(s)." else ""
                files.isNotEmpty() -> "${files.size} log file(s). Logcat needs the adb grant below."
                else -> "Logcat is not granted yet, and no log files are visible."
            }
            // Logcat is the real source on Quest, but it returns only our own lines without the
            // adb grant, so fall back to the newest readable file rather than a dead stream.
            if (selectedLog == null) {
                selectLog(if (logcatUsable || files.isEmpty()) all.first() else files.first())
            }
        }
    }

    fun onLogFolderPicked(uri: Uri) {
        val app = getApplication<Application>()
        VrcLogReader.persistTreePermission(app, uri)
        viewModelScope.launch {
            settingsStore.setLogFolderUri(uri.toString())
            // A freshly granted folder is almost always the one the user wants read, so switch
            // to its newest log instead of leaving them on whatever was selected before.
            val found = VrcLogReader.findTreeLogs(app, uri)
            if (found.isNotEmpty()) selectedLog = null
            scanLogs()
        }
    }

    fun selectLog(source: VrcLogReader.LogSource) {
        selectedLog = source
        logOffset = -1L
        _logLines.value = emptyList()
        _instance.value = VrcLogReader.InstanceState()
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
            } else {
                _logLines.update { current ->
                    val next = current + lines
                    if (next.size > LOG_LINE_CAPACITY) next.takeLast(LOG_LINE_CAPACITY) else next
                }
            }
            _instance.update {
                VrcLogReader.trackInstance(
                    previous = it,
                    lines = if (source.logcat) _logLines.value else lines,
                    replaceRoster = source.logcat,
                )
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

/** One shot result of asking for a VRChat Legends group invite. */
data class GroupInviteState(
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)
