package com.vrchatlegends.osccompanion.pulsoid

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

data class HeartRateState(
    val connected: Boolean = false,
    val bpm: Int = 0,
    val updatedAtMs: Long = 0L,
    val error: String? = null,
) {
    val isFresh: Boolean get() = updatedAtMs > 0 && System.currentTimeMillis() - updatedAtMs < 15_000
}

/**
 * Pulsoid real-time heart rate over websocket.
 *
 * Payload shape: `{"measured_at":1700000000000,"data":{"heart_rate":72}}`.
 * The token is a Pulsoid access token, not a Pulsoid widget URL; a widget id will just
 * produce a 401 on connect.
 */
class PulsoidClient(
    private val scope: CoroutineScope,
    private val onBpm: (Int) -> Unit,
) {
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private var socket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var token: String = ""

    private val _state = MutableStateFlow(HeartRateState())
    val state: StateFlow<HeartRateState> = _state.asStateFlow()

    fun connect(accessToken: String) {
        if (accessToken.isBlank()) {
            disconnect()
            return
        }
        token = accessToken
        openSocket()
    }

    fun disconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
        socket?.close(1000, "client disconnect")
        socket = null
        _state.value = HeartRateState()
    }

    private fun openSocket() {
        socket?.close(1000, "reconnect")
        val request = Request.Builder()
            .url("wss://dev.pulsoid.net/api/v1/data/real_time?access_token=$token")
            .build()

        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _state.value = _state.value.copy(connected = true, error = null)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val bpm = runCatching {
                    json.parseToJsonElement(text)
                        .jsonObject["data"]?.jsonObject
                        ?.get("heart_rate")?.jsonPrimitive?.intOrNull
                }.getOrNull() ?: return
                _state.value = HeartRateState(
                    connected = true,
                    bpm = bpm,
                    updatedAtMs = System.currentTimeMillis(),
                )
                onBpm(bpm)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _state.value = _state.value.copy(
                    connected = false,
                    error = response?.let { "HTTP ${it.code}" } ?: t.message,
                )
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _state.value = _state.value.copy(connected = false)
                if (code != 1000) scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        if (token.isBlank() || reconnectJob?.isActive == true) return
        reconnectJob = scope.launch(Dispatchers.IO) {
            delay(RECONNECT_DELAY_MS)
            if (token.isNotBlank()) openSocket()
        }
    }

    private companion object {
        const val RECONNECT_DELAY_MS = 10_000L
    }
}
