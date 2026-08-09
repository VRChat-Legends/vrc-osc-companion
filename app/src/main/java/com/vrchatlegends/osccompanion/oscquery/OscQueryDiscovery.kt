package com.vrchatlegends.osccompanion.oscquery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Finds VRChat's OSCQuery service on the LAN and pulls its live parameter tree.
 *
 * VRChat publishes `_oscjson._tcp` with a name like `VRChat-Client-A1B2C3`. Its
 * `?HOST_INFO` document carries the UDP endpoint it listens on, which we then use as the
 * OSC send target instead of assuming port 9000.
 */
class OscQueryDiscovery(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onPeer: (OscQueryPeer) -> Unit,
    private val onEvent: (String) -> Unit,
) {
    private val nsd: NsdManager? by lazy {
        context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    }

    private var discoveryListener: NsdManager.DiscoveryListener? = null

    // NsdManager on older releases only tolerates one resolve in flight, so they are
    // serialised through this channel rather than fired off in parallel.
    private val resolveQueue = Channel<NsdServiceInfo>(Channel.UNLIMITED)
    private var resolveJob: kotlinx.coroutines.Job? = null

    private val http = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun start() {
        val manager = nsd ?: run {
            onEvent("mDNS service unavailable on this device")
            return
        }
        stop()
        resolveJob = scope.launch { resolveWorker(manager) }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                onEvent("Looking for OSCQuery peers")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                resolveQueue.trySend(serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                onEvent("Lost ${serviceInfo.serviceName}")
            }

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                onEvent("mDNS discovery failed (code $errorCode)")
                runCatching { manager.stopServiceDiscovery(this) }
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                runCatching { manager.stopServiceDiscovery(this) }
            }
        }
        discoveryListener = listener
        runCatching {
            manager.discoverServices(SERVICE_TYPE_OSCJSON, NsdManager.PROTOCOL_DNS_SD, listener)
        }.onFailure { onEvent("Could not start mDNS discovery: ${it.message}") }
    }

    fun stop() {
        val manager = nsd
        discoveryListener?.let { l -> runCatching { manager?.stopServiceDiscovery(l) } }
        discoveryListener = null
        resolveJob?.cancel()
        resolveJob = null
    }

    private suspend fun resolveWorker(manager: NsdManager) {
        for (info in resolveQueue) {
            val resolved = resolveOnce(manager, info) ?: continue
            val host = resolved.host?.hostAddress ?: continue
            val peer = fetchHostInfo(resolved.serviceName ?: "unknown", host, resolved.port)
            onPeer(peer)
        }
    }

    private suspend fun resolveOnce(manager: NsdManager, info: NsdServiceInfo): NsdServiceInfo? =
        withContext(Dispatchers.IO) {
            val result = Channel<NsdServiceInfo?>(1)
            val listener = object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    result.trySend(null)
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    result.trySend(serviceInfo)
                }
            }
            runCatching { manager.resolveService(info, listener) }
                .onFailure { return@withContext null }
            result.receive()
        }

    private suspend fun fetchHostInfo(name: String, host: String, port: Int): OscQueryPeer {
        val fallback = OscQueryPeer(name, host, port)
        val body = get(fallback.hostInfoUrl) ?: return fallback
        return runCatching {
            OscQueryJson.parseHostInfo(name, host, port, json.parseToJsonElement(body))
        }.getOrDefault(fallback)
    }

    /** Pulls and flattens a peer's whole node tree. */
    suspend fun fetchTree(peer: OscQueryPeer): List<OscQueryNode> {
        val body = get(peer.rootUrl) ?: return emptyList()
        return runCatching {
            OscQueryJson.parseTree(json.parseToJsonElement(body))
        }.getOrElse {
            onEvent("Could not parse OSCQuery tree from ${peer.name}: ${it.message}")
            emptyList()
        }
    }

    private suspend fun get(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.string()
            }
        }.getOrNull()
    }
}
