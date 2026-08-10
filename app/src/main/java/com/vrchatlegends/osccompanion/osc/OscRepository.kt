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
    private var chatboxJob: Job? = null

    private val chatboxMutex = Mutex()
    private var pendingChatbox: String? = null
    private var lastChatboxSendMs = 0L

    @Volatile private var settings: AppSettings = AppSettings()

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
        scope.launch { restart() }
    }

    private suspend fun restart() {
        // The bridge is deliberately left alone here: it owns its own socket and has no
        // reason to drop the PC link just because the VRChat side re-bound.
        stopInternal(stopBridge = false)
        transport.start(settings.oscReceivePort)
        retarget()

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
        scope.launch { stopInternal(stopBridge = true) }
    }

    private suspend fun stopInternal(stopBridge: Boolean) {
        chatboxJob?.cancel()
        chatboxJob = null
        discovery?.stop()
        discovery = null
        queryServer?.stop()
        queryServer = null
        if (stopBridge) bridge.stop()
        transport.stop()
        _connection.update { it.copy(running = false, listenPort = 0, oscQueryHttpPort = 0) }
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
    }

    private fun subscribedPaths(): List<String> =
        VrcOsc.COMMON_PARAMETERS.map { VrcOsc.parameter(it) } +
            _parameters.value.values.map { it.address }

    private fun onPeerFound(peer: OscQueryPeer) {
        if (!peer.isVrChat) return
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

        scope.launch {
            val nodes = discovery?.fetchTree(peer).orEmpty()
            if (nodes.isEmpty()) return@launch
            val now = System.currentTimeMillis()
            _parameters.update { current ->
                val merged = current.toMutableMap()
                for (node in nodes.filter { it.isAvatarParameter }) {
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
            queryServer?.updatePaths(subscribedPaths())
            pushEvent("Loaded ${nodes.count { it.isAvatarParameter }} parameters from OSCQuery")
        }
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
                _avatarId.value = id
                // Parameter values are per-avatar; drop stale ones but keep the schema.
                _parameters.update { current ->
                    current.mapValues { (_, p) -> p.copy(value = null) }
                }
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
                    val wait = CHATBOX_MIN_INTERVAL_MS - (System.currentTimeMillis() - lastChatboxSendMs)
                    if (wait > 0) delay(wait)
                    lastChatboxSendMs = System.currentTimeMillis()
                    send(
                        OscMessage(
                            VrcOsc.CHATBOX_INPUT,
                            listOf(
                                OscArg.OscString(next),
                                OscArg.OscBool(immediate),
                                OscArg.OscBool(!silent),
                            ),
                        )
                    )
                }
            }
        }
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
