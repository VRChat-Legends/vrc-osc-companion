package com.vrchatlegends.osccompanion.osc

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicLong

/**
 * UDP transport. One socket is used for both directions so the source port of our
 * outgoing packets matches the port we advertise over OSCQuery.
 */
class OscTransport(
    private val scope: CoroutineScope,
    private val onReceive: (OscMessage, String) -> Unit,
    private val onError: (String) -> Unit,
) {
    @Volatile private var socket: DatagramSocket? = null
    private var receiveJob: Job? = null

    @Volatile private var target: InetSocketAddress? = null
    @Volatile private var allowBroadcast = false

    val sentCount = AtomicLong(0)
    val receivedCount = AtomicLong(0)

    /** The port we are actually bound to, which OSCQuery advertises. 0 when stopped. */
    @Volatile var boundPort: Int = 0
        private set

    val isRunning: Boolean get() = socket?.isClosed == false

    suspend fun start(receivePort: Int) = withContext(Dispatchers.IO) {
        stop()
        runCatching {
            val s = DatagramSocket(null).apply {
                reuseAddress = true
                broadcast = true
                bind(InetSocketAddress(receivePort))
            }
            socket = s
            boundPort = s.localPort
            receiveJob = scope.launch(Dispatchers.IO) { receiveLoop(s) }
        }.onFailure {
            onError("Could not bind UDP port $receivePort: ${it.message}")
        }
        Unit
    }

    fun stop() {
        receiveJob?.cancel()
        receiveJob = null
        socket?.close()
        socket = null
        boundPort = 0
    }

    /**
     * Resolving the target once and caching it keeps [send] off the DNS path. Callers
     * pass a literal IPv4 in practice, but a hostname must not block the UI thread.
     */
    suspend fun setTarget(host: String, port: Int, broadcast: Boolean = false) = withContext(Dispatchers.IO) {
        runCatching {
            target = InetSocketAddress(InetAddress.getByName(host), port)
            allowBroadcast = broadcast
        }.onFailure {
            onError("Could not resolve $host: ${it.message}")
        }
        Unit
    }

    fun send(message: OscMessage) = sendRaw(OscCodec.encode(message))

    fun sendBundle(messages: List<OscMessage>) {
        if (messages.isEmpty()) return
        sendRaw(OscCodec.encodeBundle(messages))
    }

    private fun sendRaw(bytes: ByteArray) {
        val s = socket ?: return
        val dest = target ?: return
        scope.launch(Dispatchers.IO) {
            runCatching {
                s.broadcast = allowBroadcast
                s.send(DatagramPacket(bytes, bytes.size, dest))
                sentCount.incrementAndGet()
            }.onFailure {
                onError("Send failed: ${it.message}")
            }
        }
    }

    private suspend fun receiveLoop(s: DatagramSocket) {
        // 64 KB covers the largest practical OSC bundle VRChat emits.
        val buffer = ByteArray(65_507)
        val packet = DatagramPacket(buffer, buffer.size)
        while (scope.isActive && !s.isClosed) {
            try {
                packet.length = buffer.size
                s.receive(packet)
                val peer = packet.address?.hostAddress ?: "?"
                for (msg in OscCodec.decode(packet.data, packet.length)) {
                    receivedCount.incrementAndGet()
                    onReceive(msg, peer)
                }
            } catch (e: Exception) {
                if (s.isClosed) return
                onError("Receive error: ${e.message}")
            }
        }
    }
}
