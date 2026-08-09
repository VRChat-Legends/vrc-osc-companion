package com.vrchatlegends.osccompanion.bridge

import com.vrchatlegends.osccompanion.osc.OscArg
import com.vrchatlegends.osccompanion.osc.OscCodec
import com.vrchatlegends.osccompanion.osc.OscMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Two way OSC relay between VRChat running on this headset and a PC on the same LAN.
 *
 * Why this exists: VRChat on Quest always emits its OSC output to 127.0.0.1:9001 and there
 * is no practical way to pass the `--osc=` launch argument on a standalone headset, so a PC
 * can never be the direct destination. The companion app sidesteps that by living on the
 * headset itself: it is the localhost listener VRChat is willing to talk to, and it then
 * re-sends every message over Wi-Fi.
 *
 *   uplink   VRChat -> 127.0.0.1:9001 (this app) -> pcHost:pcPort
 *   downlink PC -> questIp:listenPort (this app) -> VRChat on 127.0.0.1:9000
 *
 * From the PC side the uplink stream is byte identical to a local VRChat, so existing
 * desktop OSC tools work unmodified as long as they are pointed at this headset.
 *
 * The downlink is an inbound network surface, so by default it only accepts datagrams whose
 * source address matches the configured PC. Anything else is counted and dropped.
 */
class PcBridge(
    private val scope: CoroutineScope,
    private val onEvent: (String) -> Unit,
    private val onError: (String) -> Unit,
) {

    data class Config(
        val enabled: Boolean = false,
        val pcHost: String = "",
        /** Where VRChat output is re-sent. 9001 makes this app look like a local VRChat. */
        val pcPort: Int = DEFAULT_PC_PORT,
        /** VRChat already owns 9000 on the headset, so the downlink needs its own port. */
        val listenPort: Int = DEFAULT_LISTEN_PORT,
        /** Empty means forward every address. Otherwise only these prefixes go to the PC. */
        val uplinkAllowPrefixes: List<String> = emptyList(),
        val uplinkBlockPrefixes: List<String> = emptyList(),
        /** 0 disables the limiter. Otherwise each address is capped to this many sends a second. */
        val uplinkRateLimitHz: Int = 0,
        /** Drops downlink datagrams that did not come from [pcHost]. Leave on. */
        val restrictToPcHost: Boolean = true,
        /** Announces the headset to the PC so desktop tools can auto configure. */
        val announce: Boolean = true,
    )

    data class Stats(
        val running: Boolean = false,
        val listenPort: Int = 0,
        val pcTarget: String = "",
        val uplinkSent: Long = 0,
        val uplinkDropped: Long = 0,
        val downlinkReceived: Long = 0,
        val downlinkRejected: Long = 0,
        val lastUplinkMs: Long = 0,
        val lastDownlinkMs: Long = 0,
        val lastDownlinkAddress: String? = null,
        val error: String? = null,
    ) {
        val pcSeen: Boolean
            get() = lastDownlinkMs > 0 && System.currentTimeMillis() - lastDownlinkMs < 30_000
    }

    private val _stats = MutableStateFlow(Stats())
    val stats: StateFlow<Stats> = _stats.asStateFlow()

    @Volatile private var config = Config()
    @Volatile private var socket: DatagramSocket? = null
    @Volatile private var pcTarget: InetSocketAddress? = null
    @Volatile private var pcAddressLiteral: String? = null

    private var receiveJob: Job? = null
    private var announceJob: Job? = null

    private val uplinkSent = AtomicLong(0)
    private val uplinkDropped = AtomicLong(0)
    private val downlinkReceived = AtomicLong(0)
    private val downlinkRejected = AtomicLong(0)

    /** Last send time per address, used only by the optional rate limiter. */
    private val lastSentPerAddress = ConcurrentHashMap<String, Long>()

    val isRunning: Boolean get() = socket?.isClosed == false

    /**
     * Applies a new configuration, restarting the socket only when something that affects
     * it actually changed. Callers may invoke this on every settings emission.
     */
    suspend fun apply(newConfig: Config, localIp: String) {
        val previous = config
        config = newConfig

        if (!newConfig.enabled || newConfig.pcHost.isBlank()) {
            if (isRunning) stop()
            return
        }

        val needsRestart = !isRunning ||
            previous.listenPort != newConfig.listenPort ||
            previous.pcHost != newConfig.pcHost ||
            previous.pcPort != newConfig.pcPort ||
            previous.announce != newConfig.announce

        if (needsRestart) start(localIp)
    }

    suspend fun start(localIp: String) = withContext(Dispatchers.IO) {
        stop()
        val cfg = config
        if (!cfg.enabled || cfg.pcHost.isBlank()) return@withContext

        runCatching {
            val resolved = InetAddress.getByName(cfg.pcHost)
            pcAddressLiteral = resolved.hostAddress
            pcTarget = InetSocketAddress(resolved, cfg.pcPort)

            val s = DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(cfg.listenPort))
            }
            socket = s
            receiveJob = scope.launch(Dispatchers.IO) { receiveLoop(s) }
            if (cfg.announce) {
                announceJob = scope.launch(Dispatchers.IO) { announceLoop(localIp) }
            }
            _stats.update {
                it.copy(
                    running = true,
                    listenPort = s.localPort,
                    pcTarget = "${cfg.pcHost}:${cfg.pcPort}",
                    error = null,
                )
            }
            onEvent("Bridge up: VRChat -> ${cfg.pcHost}:${cfg.pcPort}, PC -> :${s.localPort}")
        }.onFailure {
            val message = "Bridge could not bind :${cfg.listenPort}: ${it.message}"
            _stats.update { s -> s.copy(running = false, error = message) }
            onError(message)
        }
        Unit
    }

    fun stop() {
        receiveJob?.cancel()
        receiveJob = null
        announceJob?.cancel()
        announceJob = null
        socket?.close()
        socket = null
        pcTarget = null
        pcAddressLiteral = null
        lastSentPerAddress.clear()
        _stats.update { it.copy(running = false, listenPort = 0) }
    }

    // ── Uplink: VRChat on this headset out to the PC ─────────────────────────────

    /**
     * Called for every message VRChat sends us. Cheap and non blocking: the actual write
     * happens on the IO dispatcher.
     */
    fun uplink(message: OscMessage) {
        val s = socket ?: return
        val dest = pcTarget ?: return
        if (!config.enabled) return

        if (!passesUplinkFilter(message.address)) {
            uplinkDropped.incrementAndGet()
            return
        }
        if (!passesRateLimit(message.address)) {
            uplinkDropped.incrementAndGet()
            return
        }

        val bytes = OscCodec.encode(message)
        scope.launch(Dispatchers.IO) {
            runCatching {
                s.send(DatagramPacket(bytes, bytes.size, dest))
                uplinkSent.incrementAndGet()
                _stats.update {
                    it.copy(
                        uplinkSent = uplinkSent.get(),
                        uplinkDropped = uplinkDropped.get(),
                        lastUplinkMs = System.currentTimeMillis(),
                    )
                }
            }.onFailure {
                // A missing PC is the normal case, so this must not spam the error banner.
                uplinkDropped.incrementAndGet()
            }
        }
    }

    private fun passesUplinkFilter(address: String): Boolean {
        val cfg = config
        if (cfg.uplinkBlockPrefixes.any { address.startsWith(it) }) return false
        if (cfg.uplinkAllowPrefixes.isEmpty()) return true
        return cfg.uplinkAllowPrefixes.any { address.startsWith(it) }
    }

    private fun passesRateLimit(address: String): Boolean {
        val hz = config.uplinkRateLimitHz
        if (hz <= 0) return true
        val minInterval = 1_000L / hz
        val now = System.currentTimeMillis()
        val previous = lastSentPerAddress[address]
        if (previous != null && now - previous < minInterval) return false
        lastSentPerAddress[address] = now
        return true
    }

    // ── Downlink: PC back into VRChat on this headset ────────────────────────────

    /**
     * Set by [OscRepository]. Receives messages the PC wants delivered to VRChat, which is
     * only reachable from inside the headset.
     */
    @Volatile var onDownlink: ((OscMessage) -> Unit)? = null

    private suspend fun receiveLoop(s: DatagramSocket) {
        val buffer = ByteArray(65_507)
        val packet = DatagramPacket(buffer, buffer.size)
        while (scope.isActive && !s.isClosed) {
            try {
                packet.length = buffer.size
                s.receive(packet)
                val source = packet.address?.hostAddress ?: ""

                if (config.restrictToPcHost && source != pcAddressLiteral) {
                    downlinkRejected.incrementAndGet()
                    _stats.update { it.copy(downlinkRejected = downlinkRejected.get()) }
                    continue
                }

                val messages = OscCodec.decode(packet.data, packet.length)
                for (msg in messages) {
                    // A malformed or hostile packet must never be handed to VRChat.
                    if (!msg.address.startsWith("/")) {
                        downlinkRejected.incrementAndGet()
                        continue
                    }
                    downlinkReceived.incrementAndGet()
                    _stats.update {
                        it.copy(
                            downlinkReceived = downlinkReceived.get(),
                            downlinkRejected = downlinkRejected.get(),
                            lastDownlinkMs = System.currentTimeMillis(),
                            lastDownlinkAddress = msg.address,
                        )
                    }
                    onDownlink?.invoke(msg)
                }
            } catch (e: Exception) {
                if (s.isClosed) return
                onError("Bridge receive error: ${e.message}")
            }
        }
    }

    // ── Announce ────────────────────────────────────────────────────────────────

    /**
     * Repeats a small hello so a desktop tool can learn the headset address and the port to
     * reply on without the user typing anything. Sent to the same place as the uplink.
     */
    private suspend fun announceLoop(localIp: String) {
        while (scope.isActive) {
            val s = socket
            val dest = pcTarget
            if (s != null && dest != null && !s.isClosed) {
                val hello = OscMessage(
                    ANNOUNCE_ADDRESS,
                    listOf(
                        OscArg.OscString(localIp),
                        OscArg.OscInt(s.localPort),
                        OscArg.OscString(PROTOCOL_VERSION),
                    ),
                )
                val bytes = OscCodec.encode(hello)
                runCatching { s.send(DatagramPacket(bytes, bytes.size, dest)) }
            }
            delay(ANNOUNCE_INTERVAL_MS)
        }
    }

    companion object {
        const val DEFAULT_PC_PORT = 9001
        const val DEFAULT_LISTEN_PORT = 9100
        const val ANNOUNCE_ADDRESS = "/vrcosc/bridge/hello"
        const val ANNOUNCE_INTERVAL_MS = 3_000L
        const val PROTOCOL_VERSION = "1"

        /** Sensible defaults that keep 60 Hz tracking noise off the Wi-Fi link. */
        val SUGGESTED_BLOCK_PREFIXES = listOf(
            "/tracking/",
        )

        fun parsePrefixes(raw: String): List<String> =
            raw.split(',', '\n')
                .map { it.trim() }
                .filter { it.startsWith("/") }
    }
}
