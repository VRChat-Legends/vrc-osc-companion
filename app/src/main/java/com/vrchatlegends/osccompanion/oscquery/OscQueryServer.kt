package com.vrchatlegends.osccompanion.oscquery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

const val SERVICE_TYPE_OSCJSON = "_oscjson._tcp."
const val SERVICE_TYPE_OSC = "_osc._udp."

/**
 * Serves our OSCQuery document and advertises it over mDNS.
 *
 * Advertising `_oscjson._tcp` plus `_osc._udp` is what lets VRChat find this app and
 * start streaming avatar parameters to it without the user typing a port anywhere. It is
 * also the only way to receive output when VRChat has negotiated a non-default port.
 */
class OscQueryServer(
    private val context: Context,
    private val scope: CoroutineScope,
    private val serviceName: String,
    private val onEvent: (String) -> Unit,
) {
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private val nsd: NsdManager? by lazy {
        context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    }

    private var jsonListener: NsdManager.RegistrationListener? = null
    private var oscListener: NsdManager.RegistrationListener? = null

    @Volatile private var oscIp: String = "127.0.0.1"
    @Volatile private var oscPort: Int = 0
    @Volatile private var subscribedPaths: List<String> = emptyList()

    @Volatile var httpPort: Int = 0
        private set

    val isRunning: Boolean get() = serverSocket?.isClosed == false

    fun start(oscIp: String, oscUdpPort: Int, paths: List<String>) {
        stop()
        this.oscIp = oscIp
        this.oscPort = oscUdpPort
        this.subscribedPaths = paths

        val socket = runCatching { ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(0)) } }
            .getOrElse {
                onEvent("OSCQuery HTTP bind failed: ${it.message}")
                return
            }
        serverSocket = socket
        httpPort = socket.localPort
        acceptJob = scope.launch(Dispatchers.IO) { acceptLoop(socket) }
        registerServices()
        onEvent("OSCQuery serving on :$httpPort, OSC on $oscIp:$oscUdpPort")
    }

    fun updatePaths(paths: List<String>) {
        subscribedPaths = paths
    }

    fun stop() {
        acceptJob?.cancel()
        acceptJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        httpPort = 0
        unregisterServices()
    }

    // ── HTTP ────────────────────────────────────────────────────────────────────

    private suspend fun acceptLoop(socket: ServerSocket) {
        while (scope.isActive && !socket.isClosed) {
            val client = try {
                socket.accept()
            } catch (e: Exception) {
                if (socket.isClosed) return
                continue
            }
            scope.launch(Dispatchers.IO) { handleClient(client) }
        }
    }

    private fun handleClient(client: Socket) {
        client.use { sock ->
            runCatching {
                sock.soTimeout = 5_000
                val reader = BufferedReader(InputStreamReader(sock.getInputStream()))
                val requestLine = reader.readLine() ?: return@runCatching
                // Drain headers so the client sees a clean close.
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                }

                val parts = requestLine.split(" ")
                if (parts.size < 2 || !parts[0].equals("GET", ignoreCase = true)) {
                    respond(sock, 405, "{\"error\":\"method not allowed\"}")
                    return@runCatching
                }

                val target = parts[1]
                val path = target.substringBefore('?')
                val query = target.substringAfter('?', "")
                    .let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }

                val body: JsonObject = when {
                    query.equals("HOST_INFO", ignoreCase = true) ->
                        OscQueryJson.buildHostInfo(serviceName, oscIp, oscPort)
                    path == "/" || path.isEmpty() ->
                        OscQueryJson.buildTree(subscribedPaths)
                    else -> {
                        // Sub-path query: return the matching branch if we advertise it.
                        val tree = OscQueryJson.buildTree(subscribedPaths)
                        subTree(tree, path) ?: run {
                            respond(sock, 404, "{\"error\":\"no such node\"}")
                            return@runCatching
                        }
                    }
                }
                respond(sock, 200, Json.encodeToString(JsonObject.serializer(), body))
            }
        }
    }

    private fun subTree(root: JsonObject, path: String): JsonObject? {
        var node: JsonObject = root
        for (segment in path.trim('/').split('/').filter { it.isNotEmpty() }) {
            val contents = node["CONTENTS"] as? JsonObject ?: return null
            node = contents[segment] as? JsonObject ?: return null
        }
        return node
    }

    private fun respond(socket: Socket, status: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val reason = when (status) {
            200 -> "OK"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            else -> "Error"
        }
        val header = buildString {
            append("HTTP/1.1 $status $reason\r\n")
            append("Content-Type: application/json\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Connection: close\r\n\r\n")
        }
        socket.getOutputStream().apply {
            write(header.toByteArray(Charsets.US_ASCII))
            write(bytes)
            flush()
        }
    }

    // ── mDNS ────────────────────────────────────────────────────────────────────

    private fun registerServices() {
        val manager = nsd ?: return
        jsonListener = register(manager, SERVICE_TYPE_OSCJSON, httpPort)
        oscListener = register(manager, SERVICE_TYPE_OSC, oscPort)
    }

    private fun register(manager: NsdManager, type: String, port: Int): NsdManager.RegistrationListener? {
        if (port <= 0) return null
        val info = NsdServiceInfo().apply {
            serviceName = this@OscQueryServer.serviceName
            serviceType = type
            this.port = port
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                onEvent("Advertised ${info.serviceName} $type:$port")
            }

            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                onEvent("mDNS registration failed for $type (code $errorCode)")
            }

            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
        }
        return runCatching {
            manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
            listener
        }.getOrElse {
            onEvent("mDNS unavailable: ${it.message}")
            null
        }
    }

    private fun unregisterServices() {
        val manager = nsd ?: return
        jsonListener?.let { runCatching { manager.unregisterService(it) } }
        oscListener?.let { runCatching { manager.unregisterService(it) } }
        jsonListener = null
        oscListener = null
    }
}
