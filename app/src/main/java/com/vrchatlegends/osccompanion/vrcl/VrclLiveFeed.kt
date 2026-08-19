package com.vrchatlegends.osccompanion.vrcl

import com.vrchatlegends.osccompanion.BuildConfig
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * Live social events, same transport the website uses.
 *
 * `wss://<api>/api/live/ws`, subscribe with `{"op":"subscribe","topics":["feed:global"]}`,
 * then every push arrives as `{"op":"event","type":...,"payload":...}`. Matching the site
 * exactly is the point: a post made in a browser shows up in the headset without a refresh.
 */
class VrclLiveFeed(private val tokenProvider: () -> String?) {

    sealed interface Event {
        data class NewPost(val post: VrclPost) : Event
        data class Deleted(val playerId: String, val postId: String) : Event
        data class Like(val playerId: String, val postId: String, val likeCount: Int) : Event
        data class NewComment(val postId: String) : Event
        data class CommentDeleted(val postId: String, val commentId: String) : Event
        data class Connected(val connected: Boolean) : Event
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val http = OkHttpClient.Builder()
        // The server sends no traffic on a quiet feed, so ping to keep Cloudflare from
        // dropping the socket at its idle timeout.
        .pingInterval(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    fun events(topic: String = "feed:global"): Flow<Event> = callbackFlow {
        var socket: WebSocket? = null
        var closed = false

        fun connect() {
            if (closed) return
            val base = BuildConfig.VRCL_API_BASE_URL
                .replace("https://", "wss://")
                .replace("http://", "ws://")
            val token = tokenProvider()
            val url = buildString {
                append(base).append("/api/live/ws")
                if (!token.isNullOrBlank()) {
                    append("?token=").append(java.net.URLEncoder.encode(token, "UTF-8"))
                }
            }

            socket = http.newWebSocket(
                Request.Builder().url(url).build(),
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        webSocket.send("""{"op":"subscribe","topics":["$topic"]}""")
                        trySend(Event.Connected(true))
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        val event = runCatching { parse(text) }.getOrNull() ?: return
                        trySend(event)
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        trySend(Event.Connected(false))
                        // The headset sleeps and wakes constantly, so a drop is normal. The
                        // flow stays alive and the collector keeps whatever it already has.
                        if (!closed) {
                            socket = null
                            reconnectLater(::connect)
                        }
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        trySend(Event.Connected(false))
                        if (!closed) {
                            socket = null
                            reconnectLater(::connect)
                        }
                    }
                },
            )
        }

        connect()

        awaitClose {
            closed = true
            runCatching { socket?.close(1000, null) }
        }
    }

    private fun reconnectLater(block: () -> Unit) {
        Thread {
            Thread.sleep(RECONNECT_DELAY_MS)
            runCatching { block() }
        }.apply { isDaemon = true }.start()
    }

    private fun parse(text: String): Event? {
        val msg = json.parseToJsonElement(text).jsonObject
        if (msg.str("op") != "event") return null
        val payload = msg["payload"] as? JsonObject ?: return null
        return when (msg.str("type")) {
            "post.new" -> {
                val postObj = payload["post"] as? JsonObject ?: return null
                val playerId = payload.str("playerId") ?: postObj.str("playerId") ?: return null
                // The summary on the wire can leave playerId at the envelope level, so graft it
                // on before handing the object to the parser the REST feed also uses.
                val merged = JsonObject(postObj + ("playerId" to JsonPrimitive(playerId)))
                Event.NewPost(merged.toPost() ?: return null)
            }

            "comment.new" -> {
                val postId = payload.str("postId") ?: return null
                Event.NewComment(postId)
            }

            "comment.deleted" -> {
                val postId = payload.str("postId") ?: return null
                val commentId = payload.str("commentId") ?: return null
                Event.CommentDeleted(postId, commentId)
            }

            "post.deleted" -> {
                val playerId = payload.str("playerId") ?: return null
                val postId = payload.str("postId") ?: return null
                Event.Deleted(playerId, postId)
            }

            "post.like" -> {
                val playerId = payload.str("playerId") ?: return null
                val postId = payload.str("postId") ?: return null
                Event.Like(playerId, postId, payload.int("likeCount") ?: 0)
            }

            else -> null
        }
    }

    private companion object {
        const val RECONNECT_DELAY_MS = 4_000L
    }
}
