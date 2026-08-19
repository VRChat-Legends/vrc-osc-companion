package com.vrchatlegends.osccompanion.osc

import android.content.Context
import com.vrchatlegends.osccompanion.bridge.PcBridge
import com.vrchatlegends.osccompanion.data.AppSettings
import com.vrchatlegends.osccompanion.net.NetworkUtils
import com.vrchatlegends.osccompanion.oscquery.OscQueryDiscovery
import com.vrchatlegends.osccompanion.oscquery.OscQueryPeer
import com.vrchatlegends.osccompanion.oscquery.OscQueryServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ParameterState(
    val name: String,
    val address: String,
    val typeTag: Char?,
    val value: OscArg?,
    val writable: Boolean = true,
    val fromOscQuery: Boolean = false,
    val updatedAtMs: Long = System.currentTimeMillis(),
) {
    val isBool: Boolean get() = typeTag == 'T' || typeTag == 'F' || value is OscArg.OscBool
    val isInt: Boolean get() = typeTag == 'i' || (typeTag == null && value is OscArg.OscInt)
    val isFloat: Boolean get() = typeTag == 'f' || (typeTag == null && value is OscArg.OscFloat)
}

data class ConnectionState(
    val running: Boolean = false,
    val targetHost: String = "",
    val targetPort: Int = VrcOsc.DEFAULT_SEND_PORT,
    val listenPort: Int = 0,
    val autoHost: Boolean = true,
    val oscQueryHttpPort: Int = 0,
    val vrchatPeer: OscQueryPeer? = null,
    val lastInboundMs: Long = 0L,
    val sent: Long = 0,
    val received: Long = 0,
    val error: String? = null,
) {
    /** VRChat only sends when OSC is enabled in its Action Menu, so inbound traffic is the real handshake. */
    val vrchatSeen: Boolean
        get() = lastInboundMs > 0 && System.currentTimeMillis() - lastInboundMs < 30_000
}

internal class AvatarSchemaEpoch {
    var current: Long = 0L
        private set

    private var loaded: Long = -1L

    fun invalidate(): Long {
        current += 1L
        loaded = -1L
        return current
    }

    fun canLoad(epoch: Long): Boolean = epoch == current

    fun markLoaded(epoch: Long): Boolean {
        if (!canLoad(epoch)) return false
        loaded = epoch
        return true
    }

    fun isLoaded(): Boolean = loaded == current
}

internal fun revokeScriptOscQueryProvenance(
    parameters: Map<String, ParameterState>,
): Map<String, ParameterState> = parameters.mapValues { (_, parameter) ->
    parameter.copy(value = null, fromOscQuery = false)
}

/**
 * Owns the UDP socket, the OSCQuery peer and all VRChat-facing state.
 *
 * Single instance for the whole process so the foreground service and the UI operate on
 * the same socket. Never bind two sockets to 9001: the second one silently receives
 * nothing on Android.
 */
class OscRepository private constructor(private val appContext: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _connection = MutableStateFlow(ConnectionState())
    val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    private val _parameters = MutableStateFlow<Map<String, ParameterState>>(emptyMap())
    val parameters: StateFlow<Map<String, ParameterState>> = _parameters.asStateFlow()

    private val _log = MutableStateFlow<List<OscLogEntry>>(emptyList())
    val log: StateFlow<List<OscLogEntry>> = _log.asStateFlow()

    private val _events = MutableStateFlow<List<String>>(emptyList())
    val events: StateFlow<List<String>> = _events.asStateFlow()

    private val _avatarId = MutableStateFlow<String?>(null)
    val avatarId: StateFlow<String?> = _avatarId.asStateFlow()

    private val _eyeHeight = MutableStateFlow(1.7f)
    val eyeHeight: StateFlow<Float> = _eyeHeight.asStateFlow()

    private val _eyeHeightMin = MutableStateFlow(0.2f)
    val eyeHeightMin: StateFlow<Float> = _eyeHeightMin.asStateFlow()

    private val _eyeHeightMax = MutableStateFlow(5.0f)
    val eyeHeightMax: StateFlow<Float> = _eyeHeightMax.asStateFlow()

    private val _scalingAllowed = MutableStateFlow(true)
    val scalingAllowed: StateFlow<Boolean> = _scalingAllowed.asStateFlow()

    private val transport = OscTransport(
        scope = scope,
        onReceive = ::handleInbound,
        onError = { pushError(it) },
    )

    /**
     * Relays VRChat traffic to and from a desktop. Lives here rather than in the UI layer
     * because it has to see every inbound message and be able to inject outbound ones.
     */
    val bridge = PcBridge(
        scope = scope,
        onEvent = ::pushEvent,
        onError = { pushError(it) },
    ).also { it.onDownlink = { message -> sendFromBridge(message) } }

    private var queryServer: OscQueryServer? = null
    private var discovery: OscQueryDiscovery? = null
    private var discoveryWatchdog: Job? = null
    private var chatboxJob: Job? = null

    private val lifecycleMutex = Mutex()
    private val chatboxMutex = Mutex()
    private val chatboxSendMutex = Mutex()
    private val scriptDispatchLock = Any()
    private val avatarSchemaEpoch = AvatarSchemaEpoch()
    private var pendingChatbox: String? = null
    private var lastChatboxSendMs = 0L

    @Volatile private var settings: AppSettings = AppSettings()
    @Volatile private var runningRequested = false

    // ── Lifecycle ───────────────────────────────────────────────────────────────

    fun applySettings(newSettings: AppSettings) {
        val previous = settings
        settings = newSettings
        scope.launch {
            bridge.apply(newSettings.bridgeConfig(), NetworkUtils.localIpv4OrLoopback(appContext))
        }
        if (!_connection.value.running) return
        val hostChanged = previous.oscHost != newSettings.oscHost ||
            previous.oscSendPort != newSettings.oscSendPort ||
            previous.useBroadcast != newSettings.useBroadcast
        val listenChanged = previous.oscReceivePort != newSettings.oscReceivePort
        val queryChanged = previous.useOscQuery != newSettings.useOscQuery
        when {
            listenChanged || queryChanged -> scope.launch { restart() }
            hostChanged -> scope.launch { retarget() }
        }
    }

    fun start() {
        runningRequested = true
        scope.launch {
            lifecycleMutex.withLock {
                if (runningRequested && !_connection.value.running) restartLocked()
            }
        }
    }

    private suspend fun restart() {
        lifecycleMutex.withLock {
            if (runningRequested) restartLocked()
        }
    }

    private suspend fun restartLocked() {
        // The bridge is deliberately left alone here: it owns its own socket and has no
        // reason to drop the PC link just because the VRChat side re-bound.
        stopInternalLocked(stopBridge = false)
        transport.start(settings.oscReceivePort)
        retargetLocked()

        _connection.update {
            it.copy(
                running = transport.isRunning,
                listenPort = transport.boundPort,
                error = if (transport.isRunning) null else it.error,
            )
        }

        if (settings.useOscQuery) startOscQuery()
        bridge.apply(settings.bridgeConfig(), NetworkUtils.localIpv4OrLoopback(appContext))
        pushEvent("OSC listening on :${transport.boundPort}")
    }

    private suspend fun retarget() {
        lifecycleMutex.withLock {
            if (runningRequested && _connection.value.running) retargetLocked()
        }
    }

    private suspend fun retargetLocked() {
        val host = settings.resolvedHost(appContext)
        transport.setTarget(host, settings.oscSendPort, settings.useBroadcast)
        _connection.update {
            it.copy(
                targetHost = host,
                targetPort = settings.oscSendPort,
                autoHost = settings.isAutoHost,
            )
        }
    }

    fun stop() {
        runningRequested = false
        scope.launch {
            lifecycleMutex.withLock {
                if (!runningRequested) stopInternalLocked(stopBridge = true)
            }
        }
    }

    private suspend fun stopInternalLocked(stopBridge: Boolean) {
        chatboxJob?.cancel()
        chatboxJob = null
        discovery?.stop()
        discovery = null
        discoveryWatchdog?.cancel()
        discoveryWatchdog = null
        queryServer?.stop()
        queryServer = null
        if (stopBridge) bridge.stop()
        transport.stop()
        synchronized(scriptDispatchLock) {
            _avatarId.value = null
            avatarSchemaEpoch.invalidate()
            _parameters.update(::revokeScriptOscQueryProvenance)
        }
        _connection.update {
            it.copy(
                running = false,
                listenPort = 0,
                oscQueryHttpPort = 0,
                vrchatPeer = null,
                lastInboundMs = 0L,
            )
        }
    }

    // ── OSCQuery ────────────────────────────────────────────────────────────────

    private fun startOscQuery() {
        val localIp = NetworkUtils.localIpv4OrLoopback(appContext)
        val server = OscQueryServer(
            context = appContext,
            scope = scope,
            serviceName = SERVICE_NAME,
            onEvent = ::pushEvent,
        )
        server.start(localIp, transport.boundPort, subscribedPaths())
        queryServer = server
        _connection.update { it.copy(oscQueryHttpPort = server.httpPort) }

        val disc = OscQueryDiscovery(
            context = appContext,
            scope = scope,
            onPeer = ::onPeerFound,
            onEvent = ::pushEvent,
        )
        disc.start()
        discovery = disc
        discoveryWatchdog?.cancel()
        discoveryWatchdog = scope.launch {
            while (isActive) {
                delay(DISCOVERY_RETRY_MS)
                val state = _connection.value
                if (!state.running || !settings.useOscQuery) break
                if (!state.vrchatSeen) {
                    pushEvent("Refreshing OSCQuery discovery")
                    disc.start()
                }
            }
        }
    }

    private fun subscribedPaths(): List<String> =
        VrcOsc.COMMON_PARAMETERS.map { VrcOsc.parameter(it) } +
            _parameters.value.values.map { it.address }

    private fun onPeerFound(peer: OscQueryPeer) {
        if (!peer.isVrChat || !runningRequested || !_connection.value.running || !settings.useOscQuery) return
        _connection.update { it.copy(vrchatPeer = peer) }
        pushEvent("Found ${peer.name} at ${peer.host}:${peer.httpPort}")

        // Prefer VRChat's advertised UDP port over the 9000 default.
        val negotiated = peer.oscPort
        if (negotiated != null && negotiated != _connection.value.targetPort && settings.useOscQuery) {
            scope.launch {
                val host = peer.oscIp?.takeIf { it.isNotBlank() && it != "0.0.0.0" } ?: peer.host
                transport.setTarget(host, negotiated, settings.useBroadcast)
                _connection.update { it.copy(targetHost = host, targetPort = negotiated) }
                pushEvent("OSCQuery negotiated $host:$negotiated")
            }
        }

        val expectedEpoch = synchronized(scriptDispatchLock) { avatarSchemaEpoch.current }
        scope.launch { loadAvatarSchema(peer, expectedEpoch) }
    }

    private suspend fun loadAvatarSchema(peer: OscQueryPeer, expectedEpoch: Long) {
        val nodes = discovery?.fetchTree(peer).orEmpty()
        val avatarNodes = nodes.filter { it.isAvatarParameter }
        if (avatarNodes.isEmpty()) return

        var applied = false
        synchronized(scriptDispatchLock) {
            if (!avatarSchemaEpoch.canLoad(expectedEpoch) ||
                _connection.value.vrchatPeer != peer ||
                !_connection.value.running ||
                !settings.useOscQuery
            ) {
                return@synchronized
            }
            val now = System.currentTimeMillis()
            _parameters.update { current ->
                val merged = current.mapValues { (_, parameter) ->
                    parameter.copy(fromOscQuery = false)
                }.toMutableMap()
                for (node in avatarNodes) {
                    val existing = merged[node.name]
                    merged[node.name] = ParameterState(
                        name = node.name,
                        address = node.fullPath,
                        typeTag = node.primaryType,
                        value = existing?.value ?: node.toOscArg(),
                        writable = node.writable,
                        fromOscQuery = true,
                        updatedAtMs = existing?.updatedAtMs ?: now,
                    )
                }
                merged
            }
            applied = avatarSchemaEpoch.markLoaded(expectedEpoch)
        }
        if (!applied) return
        queryServer?.updatePaths(subscribedPaths())
        pushEvent("Loaded ${avatarNodes.size} parameters from OSCQuery")
    }

    // ── Inbound ─────────────────────────────────────────────────────────────────

    private fun handleInbound(message: OscMessage, peer: String) {
        appendLog(OscLogEntry(OscDirection.IN, message, peer = peer))
        // VRChat on Quest can only reach localhost, so this is the only chance a PC gets
        // to see the message.
        bridge.uplink(message)
        _connection.update {
            it.copy(
                lastInboundMs = System.currentTimeMillis(),
                received = transport.receivedCount.get(),
            )
        }

        when {
            message.address == VrcOsc.AVATAR_CHANGE -> {
                val id = (message.args.firstOrNull() as? OscArg.OscString)?.value
                val reload = synchronized(scriptDispatchLock) {
                    _avatarId.value = id
                    val epoch = avatarSchemaEpoch.invalidate()
                    _parameters.update(::revokeScriptOscQueryProvenance)
                    _connection.value.vrchatPeer?.let { it to epoch }
                }
                reload?.let { (peer, epoch) -> scope.launch { loadAvatarSchema(peer, epoch) } }
                pushEvent("Avatar changed${id?.let { " -> $it" } ?: ""}")
            }

            message.address == VrcOsc.AVATAR_EYE_HEIGHT ->
                message.args.firstOrNull()?.asFloatOrNull()?.let { _eyeHeight.value = it }

            message.address == VrcOsc.AVATAR_EYE_HEIGHT_MIN ->
                message.args.firstOrNull()?.asFloatOrNull()?.let { _eyeHeightMin.value = it }

            message.address == VrcOsc.AVATAR_EYE_HEIGHT_MAX ->
                message.args.firstOrNull()?.asFloatOrNull()?.let { _eyeHeightMax.value = it }

            message.address == VrcOsc.AVATAR_EYE_HEIGHT_SCALING_ALLOWED ->
                _scalingAllowed.value =
                    (message.args.firstOrNull() as? OscArg.OscBool)?.value
                        ?: (message.args.firstOrNull()?.asFloatOrNull()?.let { it != 0f } ?: true)

            message.address.startsWith(VrcOsc.AVATAR_PARAMETER_PREFIX) -> {
                val name = message.address.removePrefix(VrcOsc.AVATAR_PARAMETER_PREFIX)
                val arg = message.args.firstOrNull() ?: return
                _parameters.update { current ->
                    val existing = current[name]
                    current + (name to ParameterState(
                        name = name,
                        address = message.address,
                        typeTag = existing?.typeTag ?: arg.typeTag(),
                        value = arg,
                        writable = existing?.writable ?: true,
                        fromOscQuery = existing?.fromOscQuery ?: false,
                    ))
                }
            }
        }
    }

    // ── Outbound ────────────────────────────────────────────────────────────────

    fun send(message: OscMessage) {
        transport.send(message)
        appendLog(OscLogEntry(OscDirection.OUT, message))
        _connection.update { it.copy(sent = transport.sentCount.get()) }
    }

    /**
     * Delivers a message the PC asked us to forward. Parameter writes are mirrored into
     * local state so the Params screen stays truthful about what the avatar is doing.
     */
    private fun sendFromBridge(message: OscMessage) {
        if (message.address.startsWith(VrcOsc.AVATAR_PARAMETER_PREFIX)) {
            val name = message.address.removePrefix(VrcOsc.AVATAR_PARAMETER_PREFIX)
            val arg = message.args.firstOrNull()
            if (arg != null) {
                setParameter(name, arg)
                return
            }
        }
        send(message)
    }

    fun setParameter(name: String, value: OscArg) {
        send(OscMessage(VrcOsc.parameter(name), listOf(value)))
        _parameters.update { current ->
            val existing = current[name]
            current + (name to (existing?.copy(value = value, updatedAtMs = System.currentTimeMillis())
                ?: ParameterState(name, VrcOsc.parameter(name), value.typeTag(), value)))
        }
    }

    suspend fun sendScriptChatbox(text: String): Boolean =
        sendChatboxNow(
            text = clipChatbox(text),
            immediate = true,
            silent = true,
            requireVrchatSeen = true,
        )

    fun setScriptParameter(expectedAvatarId: String, name: String, value: OscArg): Boolean =
        synchronized(scriptDispatchLock) {
            val current = _connection.value
            if (!current.running || !current.vrchatSeen || _avatarId.value != expectedAvatarId) {
                return@synchronized false
            }
            if (!avatarSchemaEpoch.isLoaded()) return@synchronized false
            val parameter = _parameters.value[name] ?: return@synchronized false
            if (parameter.address != VrcOsc.parameter(name) ||
                !parameter.fromOscQuery ||
                !parameter.writable
            ) {
                return@synchronized false
            }
            val compatible = when (value) {
                is OscArg.OscBool -> parameter.isBool
                is OscArg.OscInt -> parameter.isInt
                is OscArg.OscFloat -> parameter.isFloat
                else -> false
            }
            if (!compatible) return@synchronized false
            setParameter(name, value)
            true
        }

    fun setEyeHeight(metres: Float) {
        val clamped = metres.coerceIn(0.01f, 10_000f)
        send(OscMessage.of(VrcOsc.AVATAR_EYE_HEIGHT, clamped))
        _eyeHeight.value = clamped
    }

    fun setTyping(typing: Boolean) = send(OscMessage.of(VrcOsc.CHATBOX_TYPING, typing))

    /**
     * Sends text to the chatbox, coalescing bursts.
     *
     * VRChat throttles the chatbox, so updates are spaced out and only the most recent
     * text survives a burst. `silent` maps to the third `/chatbox/input` argument, which
     * suppresses the notification SFX.
     */
    fun sendChatbox(text: String, immediate: Boolean = true, silent: Boolean = settings.chatboxSilent) {
        val clipped = clipChatbox(text)
        scope.launch {
            chatboxMutex.withLock { pendingChatbox = clipped }
            if (chatboxJob?.isActive == true) return@launch
            chatboxJob = scope.launch {
                while (true) {
                    val next = chatboxMutex.withLock {
                        val value = pendingChatbox
                        pendingChatbox = null
                        value
                    } ?: break
                    sendChatboxNow(next, immediate, silent, requireVrchatSeen = false)
                }
            }
        }
    }

    private suspend fun sendChatboxNow(
        text: String,
        immediate: Boolean,
        silent: Boolean,
        requireVrchatSeen: Boolean,
    ): Boolean = chatboxSendMutex.withLock {
        if (requireVrchatSeen) {
            val beforeWait = _connection.value
            if (!beforeWait.running || !beforeWait.vrchatSeen) return@withLock false
        }
        val remaining = CHATBOX_MIN_INTERVAL_MS -
            (System.currentTimeMillis() - lastChatboxSendMs)
        if (remaining > 0) delay(remaining)
        if (requireVrchatSeen) {
            val beforeSend = _connection.value
            if (!beforeSend.running || !beforeSend.vrchatSeen) return@withLock false
        }
        lastChatboxSendMs = System.currentTimeMillis()
        send(
            OscMessage(
                VrcOsc.CHATBOX_INPUT,
                listOf(
                    OscArg.OscString(text),
                    OscArg.OscBool(immediate),
                    OscArg.OscBool(!silent),
                ),
            ),
        )
        true
    }

    fun clearChatbox() = sendChatbox("", immediate = true, silent = true)

    /** Axes reset to 0, buttons need an explicit release, otherwise VRChat latches. */
    fun pulseButton(address: String, holdMs: Long = 120) {
        scope.launch {
            send(OscMessage.of(address, 1))
            delay(holdMs)
            send(OscMessage.of(address, 0))
        }
    }

    fun setButton(address: String, pressed: Boolean) =
        send(OscMessage.of(address, if (pressed) 1 else 0))

    fun setAxis(address: String, value: Float) =
        send(OscMessage.of(address, value.coerceIn(-1f, 1f)))

    fun releaseAllInputs() {
        val messages = VrcOsc.BUTTONS.map { OscMessage.of(it.address, 0) } +
            VrcOsc.AXES.map { OscMessage.of(it.address, 0f) }
        messages.forEach { send(it) }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    fun clearLog() {
        _log.value = emptyList()
    }

    private fun appendLog(entry: OscLogEntry) {
        _log.update { current ->
            val next = current + entry
            if (next.size > LOG_CAPACITY) next.takeLast(LOG_CAPACITY) else next
        }
    }

    private fun pushEvent(text: String) {
        _events.update { current ->
            val next = current + text
            if (next.size > EVENT_CAPACITY) next.takeLast(EVENT_CAPACITY) else next
        }
    }

    private fun pushError(text: String) {
        pushEvent(text)
        _connection.update { it.copy(error = text) }
    }

    companion object {
        const val SERVICE_NAME = "VRChat Legends OSC Companion"
        const val LOG_CAPACITY = 500
        const val EVENT_CAPACITY = 100

        /** VRChat throttles the chatbox; anything faster than this gets dropped. */
        const val CHATBOX_MIN_INTERVAL_MS = 1_500L
        const val DISCOVERY_RETRY_MS = 20_000L

        @Volatile private var instance: OscRepository? = null

        fun get(context: Context): OscRepository =
            instance ?: synchronized(this) {
                instance ?: OscRepository(context.applicationContext).also { instance = it }
            }
    }
}

/**
 * VRChat's chatbox counts characters, not bytes, and caps at 144 across at most 9 lines
 * including wrapped ones. Trimming here keeps the client from silently discarding text.
 */
fun clipChatbox(text: String): String {
    val lines = text.split('\n')
    val limited = if (lines.size > VrcOsc.CHATBOX_MAX_LINES) {
        lines.take(VrcOsc.CHATBOX_MAX_LINES).joinToString("\n")
    } else {
        text
    }
    return if (limited.length > VrcOsc.CHATBOX_MAX_CHARS) {
        limited.substring(0, VrcOsc.CHATBOX_MAX_CHARS)
    } else {
        limited
    }
}

private fun OscArg.typeTag(): Char? = when (this) {
    is OscArg.OscInt -> 'i'
    is OscArg.OscFloat -> 'f'
    is OscArg.OscString -> 's'
    is OscArg.OscBool -> if (value) 'T' else 'F'
    else -> null
}

private fun com.vrchatlegends.osccompanion.oscquery.OscQueryNode.toOscArg(): OscArg? = when (primaryType) {
    'i' -> currentFloat()?.let { OscArg.OscInt(it.toInt()) }
    'f' -> currentFloat()?.let { OscArg.OscFloat(it) }
    'T', 'F' -> currentBool()?.let { OscArg.OscBool(it) }
    else -> null
}
