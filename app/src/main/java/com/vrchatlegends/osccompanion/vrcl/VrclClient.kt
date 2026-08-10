package com.vrchatlegends.osccompanion.vrcl

import com.vrchatlegends.osccompanion.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class VrclProfile(
    val id: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val roles: List<String> = emptyList(),
)

data class VrclEvent(
    val id: String,
    val title: String,
    val startsAt: String? = null,
    val location: String? = null,
)

/** One row of https://vrchatlegends.com/social */
data class VrclPost(
    val key: String,
    val id: String,
    val playerId: String,
    val authorName: String,
    val authorAvatarUrl: String?,
    val authorVerified: Boolean,
    val body: String,
    val createdAt: String?,
    val likeCount: Int,
    val commentCount: Int,
    val repostCount: Int,
    val viewerLiked: Boolean,
    val imageUrl: String?,
    val repostedBy: String?,
)

/**
 * A shared preset from /api/companion/scripts. Deliberately not code: the backend only
 * lets a script express chatbox lines, the user's own avatar parameters, and waits.
 */
data class VrclScript(
    val id: String,
    val title: String,
    val summary: String?,
    val tags: List<String>,
    val steps: List<VrclScriptStep>,
    val authorName: String,
    val authorAvatarUrl: String?,
    val installs: Int,
    val likeCount: Int,
    val viewerLiked: Boolean,
    val canEdit: Boolean,
)

data class VrclScriptStep(
    val type: String,
    val text: String? = null,
    val address: String? = null,
    val value: String? = null,
    val ms: Int? = null,
) {
    /** One line the UI can show without knowing the step vocabulary. */
    val describe: String
        get() = when (type) {
            "chatbox" -> "Chatbox: ${text.orEmpty()}"
            "wait" -> "Wait ${(ms ?: 0)} ms"
            else -> "${address?.removePrefix("/avatar/parameters/").orEmpty()} = ${value.orEmpty()}"
        }
}

/** One row of the in-app usage leaderboard. */
data class VrclUsageEntry(
    val playerId: String,
    val rank: Int,
    val displayName: String,
    val avatarUrl: String?,
    val rangeSeconds: Long,
    val totalSeconds: Long,
    val streakDays: Int,
    val isViewer: Boolean,
)

/**
 * Who the signed-in account is on the social side. Posting goes to a Legend profile, not
 * the account, so [playerId] is what decides whether the composer can be shown at all.
 */
data class VrclSocialIdentity(
    val playerId: String?,
    val displayName: String,
    val avatarUrl: String?,
) {
    val canPost: Boolean get() = !playerId.isNullOrBlank()
}

/**
 * Thin read-only client for the VRChat Legends API.
 *
 * Sessions are `Authorization: Bearer <jwt>` obtained from the OAuth deep link. Calls go
 * straight to the API host: the public site only proxies /api through a Next.js rewrite
 * that sits behind Cloudflare's cache.
 */
class VrclClient(private val tokenProvider: () -> String?) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun me(): Result<VrclProfile> = request("/api/auth/me") { body ->
        val root = json.parseToJsonElement(body).jsonObject
        val account = root["account"]?.jsonObject ?: root["user"]?.jsonObject ?: root
        VrclProfile(
            id = account.str("id") ?: account.str("_id") ?: "",
            displayName = account.str("displayName")
                ?: account.str("username")
                ?: account.str("name")
                ?: "Legend",
            avatarUrl = account.str("avatarUrl") ?: account.str("avatar"),
            roles = (account["roles"] as? JsonArray)
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                .orEmpty(),
        )
    }

    suspend fun events(limit: Int = 5): Result<List<VrclEvent>> = request("/api/events?limit=$limit") { body ->
        val root = json.parseToJsonElement(body)
        val array = when {
            root is JsonArray -> root
            root is JsonObject && root["events"] is JsonArray -> root["events"]!!.jsonArray
            root is JsonObject && root["data"] is JsonArray -> root["data"]!!.jsonArray
            else -> JsonArray(emptyList())
        }
        array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val title = obj.str("title") ?: obj.str("name") ?: return@mapNotNull null
            VrclEvent(
                id = obj.str("id") ?: obj.str("_id") ?: title,
                title = title,
                startsAt = obj.str("startsAt") ?: obj.str("startTime") ?: obj.str("date"),
                location = obj.str("location") ?: obj.str("world"),
            )
        }
    }

    suspend fun logout(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(BuildConfig.VRCL_API_BASE_URL + "/api/auth/logout")
                .post("{}".toRequestBody("application/json".toMediaType()))
                .authorized()
                .build()
            http.newCall(request).execute().close()
        }
    }

    /** Public timeline. Works signed out, which is why the Community tab is never empty. */
    suspend fun socialFeed(limit: Int = 25, offset: Int = 0, mode: String = "latest"): Result<List<VrclPost>> =
        request("/api/social/feed?feed=$mode&limit=$limit&offset=$offset") { body ->
            val root = json.parseToJsonElement(body).jsonObject
            (root["posts"] as? JsonArray).orEmpty().mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val id = obj.str("id") ?: return@mapNotNull null
                val playerId = obj.str("playerId") ?: return@mapNotNull null
                VrclPost(
                    key = obj.str("feedKey") ?: "$playerId-$id",
                    id = id,
                    playerId = playerId,
                    authorName = obj.str("authorName") ?: "Legend",
                    authorAvatarUrl = obj.str("authorAvatarUrl"),
                    authorVerified = obj.bool("authorVerified"),
                    body = obj.str("body").orEmpty(),
                    createdAt = obj.str("createdAt"),
                    likeCount = obj.int("likeCount") ?: 0,
                    commentCount = obj.int("commentCount") ?: 0,
                    repostCount = obj.int("repostCount") ?: 0,
                    viewerLiked = obj.bool("viewerLiked"),
                    imageUrl = obj.firstMediaUrl(),
                    repostedBy = (obj["repostedBy"] as? JsonObject)?.str("name"),
                )
            }
        }

    /** Purpose-built endpoint for this app; the only place that reports the Legend profile id. */
    suspend fun socialIdentity(): Result<VrclSocialIdentity> = request("/api/companion/session") { body ->
        val account = json.parseToJsonElement(body).jsonObject["account"]?.jsonObject
            ?: error("No account in session response")
        VrclSocialIdentity(
            playerId = account.str("playerId"),
            displayName = account.str("displayName") ?: "Legend",
            avatarUrl = account.str("avatar"),
        )
    }

    suspend fun createPost(playerId: String, body: String): Result<Unit> {
        val payload = buildJsonObject { put("body", JsonPrimitive(body)) }.toString()
        return post("/api/players/${encode(playerId)}/feed", payload)
    }

    /** Community scripts. Open to signed-out users so the tab is never empty. */
    suspend fun communityScripts(sort: String = "recent", query: String = ""): Result<List<VrclScript>> {
        val q = if (query.isBlank()) "" else "&q=${encode(query)}"
        return request("/api/companion/scripts?sort=$sort&limit=50$q") { body ->
            val root = json.parseToJsonElement(body).jsonObject
            (root["scripts"] as? JsonArray).orEmpty().mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val author = obj["author"] as? JsonObject
                VrclScript(
                    id = obj.str("id") ?: return@mapNotNull null,
                    title = obj.str("title") ?: "Untitled",
                    summary = obj.str("summary"),
                    tags = (obj["tags"] as? JsonArray).orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull },
                    steps = (obj["steps"] as? JsonArray).orEmpty().mapNotNull { step ->
                        val s = step as? JsonObject ?: return@mapNotNull null
                        VrclScriptStep(
                            type = s.str("type") ?: return@mapNotNull null,
                            text = s.str("text"),
                            address = s.str("address"),
                            value = (s["value"] as? JsonPrimitive)?.contentOrNull,
                            ms = s.int("ms"),
                        )
                    },
                    authorName = author?.str("name") ?: "Unknown Legend",
                    authorAvatarUrl = author?.str("avatarUrl"),
                    installs = obj.int("installs") ?: 0,
                    likeCount = obj.int("likeCount") ?: 0,
                    viewerLiked = obj.bool("viewerLiked"),
                    canEdit = obj.bool("canEdit"),
                )
            }
        }
    }

    suspend fun likeScript(id: String): Result<Unit> = post("/api/companion/scripts/${encode(id)}/like", "{}")

    /** Time-in-app ranking. Only Legends with a linked profile appear. */
    suspend fun usageLeaderboard(range: String = "all"): Result<List<VrclUsageEntry>> =
        request("/api/companion/usage/leaderboard?range=$range&limit=50") { body ->
            val root = json.parseToJsonElement(body).jsonObject
            (root["entries"] as? JsonArray).orEmpty().mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                VrclUsageEntry(
                    playerId = obj.str("playerId") ?: return@mapNotNull null,
                    rank = obj.int("rank") ?: 0,
                    displayName = obj.str("displayName") ?: obj.str("playerId").orEmpty(),
                    avatarUrl = obj.str("avatarUrl"),
                    rangeSeconds = (obj.int("rangeSeconds") ?: 0).toLong(),
                    totalSeconds = (obj.int("totalSeconds") ?: 0).toLong(),
                    streakDays = obj.int("streakDays") ?: 0,
                    isViewer = obj.bool("isViewer"),
                )
            }
        }

    /** Keeps the device row fresh and is what credits time to the leaderboard. */
    suspend fun heartbeat(installId: String, label: String): Result<Unit> {
        val payload = buildJsonObject {
            put("installId", JsonPrimitive(installId))
            put("deviceLabel", JsonPrimitive(label))
        }.toString()
        return post("/api/companion/heartbeat", payload)
    }

    suspend fun likePost(playerId: String, postId: String): Result<Unit> =
        post("/api/players/${encode(playerId)}/feed/${encode(postId)}/like", "{}")

    private suspend fun post(path: String, payload: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(BuildConfig.VRCL_API_BASE_URL + path)
                    .post(payload.toRequestBody("application/json".toMediaType()))
                    .authorized()
                    .build()
                http.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) error(readError(body, response.code))
                }
            }
        }

    /** The site returns friendly `{ "error": "..." }` copy for rate limits and setup problems. */
    private fun readError(body: String, code: Int): String =
        runCatching { json.parseToJsonElement(body).jsonObject.str("error") }.getOrNull()
            ?: "Request failed (HTTP $code)"

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private suspend fun <T> request(path: String, parse: (String) -> T): Result<T> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(BuildConfig.VRCL_API_BASE_URL + path)
                    .get()
                    .authorized()
                    .build()
                http.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        error("HTTP ${response.code}${if (body.isNotBlank()) ": ${body.take(200)}" else ""}")
                    }
                    parse(body)
                }
            }
        }

    private fun Request.Builder.authorized(): Request.Builder = apply {
        header("Accept", "application/json")
        header("User-Agent", "VRC-OSC-Companion/${BuildConfig.VERSION_NAME} (Quest)")
        tokenProvider()?.takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") }
    }
}

// File level so VrclLiveFeed can parse the same post shape off the WebSocket.

internal fun JsonObject.str(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

internal fun JsonObject.bool(key: String): Boolean =
    (this[key] as? JsonPrimitive)?.booleanOrNull ?: false

internal fun JsonObject.firstMediaUrl(): String? =
    (this["media"] as? JsonArray)
        ?.firstNotNullOfOrNull { (it as? JsonObject)?.let { m -> m.str("url") ?: m.str("src") } }

internal fun JsonObject.int(key: String): Int? =
    (this[key] as? JsonPrimitive)?.intOrNull
