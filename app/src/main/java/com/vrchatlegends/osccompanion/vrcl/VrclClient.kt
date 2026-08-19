package com.vrchatlegends.osccompanion.vrcl

import com.vrchatlegends.osccompanion.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
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

/**
 * VRChat camera metadata baked into a screenshot. VRChat writes this into the PNG and the
 * backend pulls it back out, so a post can say which world it came from without anyone typing it.
 */
data class VrclPhotoMeta(
    val worldName: String? = null,
    val worldId: String? = null,
    val instanceId: String? = null,
    val authorName: String? = null,
    val takenAt: String? = null,
    val players: List<String> = emptyList(),
) {
    val hasDetail: Boolean
        get() = !worldName.isNullOrBlank() || !authorName.isNullOrBlank() || players.isNotEmpty()
}

/** One attachment on a post. Images and video share this shape; [type] is the discriminator. */
data class VrclMedia(
    val url: String,
    val type: String,
    val width: Int? = null,
    val height: Int? = null,
    val spoiler: Boolean = false,
    val vrchat: VrclPhotoMeta? = null,
) {
    val isVideo: Boolean get() = type.equals("video", ignoreCase = true)

    /** Falls back to 16:9 so a media box never collapses before the real size is known. */
    val aspectRatio: Float
        get() {
            val w = width ?: 0
            val h = height ?: 0
            return if (w > 0 && h > 0) w.toFloat() / h.toFloat() else 16f / 9f
        }
}

/** The post a quote-post is pointing at. Rendered inline as a nested card. */
data class VrclQuote(
    val id: String,
    val playerId: String,
    val authorName: String,
    val body: String,
    val media: List<VrclMedia> = emptyList(),
)

/**
 * A comment, already flattened out of the server's nested tree. [depth] is how far to indent,
 * clamped by the parser so a deep thread cannot push text off the side of the panel.
 */
data class VrclComment(
    val id: String,
    val body: String,
    val authorName: String,
    val authorAvatarUrl: String?,
    val authorPlayerId: String?,
    val authorVerified: Boolean,
    val parentId: String?,
    val createdAt: String?,
    val likeCount: Int,
    val likedByMe: Boolean,
    val canDelete: Boolean,
    val depth: Int,
)

/** Upload and post-length ceilings for the signed-in account's patron tier. */
data class VrclSocialLimits(
    val tier: String? = null,
    val isAdmin: Boolean = false,
    val maxImageBytes: Long = 8L * 1024 * 1024,
    val maxVideoBytes: Long = 8L * 1024 * 1024,
    val canUploadVideo: Boolean = false,
    val maxBodyLen: Int = 100,
)

data class VrclFollowState(
    val following: Boolean,
    val followerCount: Int,
)

data class VrclCameraCaptureStatus(
    val configured: Boolean,
    val enabled: Boolean,
)

class CaptureUploadException(
    val statusCode: Int,
    message: String,
) : Exception(message)

/** One row of https://vrchatlegends.com/social */
data class VrclPost(
    val key: String,
    val id: String,
    val playerId: String,
    val authorName: String,
    val authorPlayerId: String?,
    val authorAvatarUrl: String?,
    val authorVerified: Boolean,
    val body: String,
    val createdAt: String?,
    val likeCount: Int,
    val commentCount: Int,
    val repostCount: Int,
    val viewerLiked: Boolean,
    val media: List<VrclMedia> = emptyList(),
    val repostedBy: String? = null,
    val hashtags: List<String> = emptyList(),
    val quote: VrclQuote? = null,
    val shareUrl: String? = null,
    val profilePath: String? = null,
) {
    /** Anything worth opening a detail view for. */
    val hasThread: Boolean get() = commentCount > 0
}

/**
 * A shared preset from /api/companion/scripts. Two kinds: "steps" presets that only
 * express chatbox lines, avatar parameters, and waits; and "lua" scripts whose source
 * runs inside the app's sealed Lua sandbox with those same three abilities.
 */
data class VrclScript(
    val id: String,
    val title: String,
    val summary: String?,
    val tags: List<String>,
    val kind: String = "steps",
    val luaSource: String? = null,
    val steps: List<VrclScriptStep>,
    val authorName: String,
    val authorAvatarUrl: String?,
    val installs: Int,
    val likeCount: Int,
    val viewerLiked: Boolean,
    val canEdit: Boolean,
) {
    val isLua: Boolean get() = kind == "lua"
}

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
 *
 * [avatarUrl] is the Legend page picture when one exists. [discordAvatarUrl] is kept separate
 * because the two are genuinely different images and the app should prefer the website one.
 */
data class VrclSocialIdentity(
    val playerId: String?,
    val displayName: String,
    val avatarUrl: String?,
    val discordAvatarUrl: String? = null,
    val profilePath: String? = null,
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

    // Uploads ride a slow headset Wi-Fi link, so they get their own generous timeouts rather
    // than dragging the 15 s read timeout out for every ordinary call.
    private val uploads = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
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
            (root["posts"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.toPost() }
        }

    /** Upload and body-length ceilings for this account's tier. Drives the composer's guard rails. */
    suspend fun socialLimits(): Result<VrclSocialLimits> = request("/api/social/limits") { body ->
        val limits = json.parseToJsonElement(body).jsonObject["limits"]?.jsonObject
            ?: return@request VrclSocialLimits()
        VrclSocialLimits(
            tier = limits.str("tier"),
            isAdmin = limits.bool("isAdmin"),
            maxImageBytes = limits.long("maxImageBytes") ?: (8L * 1024 * 1024),
            maxVideoBytes = limits.long("maxVideoBytes") ?: (8L * 1024 * 1024),
            canUploadVideo = limits.bool("canUploadVideo"),
            maxBodyLen = limits.int("maxBodyLen") ?: 100,
        )
    }

    /**
     * Whole comment thread for one post, flattened depth-first so a LazyColumn can render it
     * without recursive composables.
     */
    suspend fun comments(playerId: String, postId: String): Result<List<VrclComment>> =
        request("/api/players/${encode(playerId)}/feed/${encode(postId)}/comments?limit=100") { body ->
            val root = json.parseToJsonElement(body).jsonObject
            val nodes = (root["replies"] as? JsonArray) ?: (root["comments"] as? JsonArray)
            buildList { flattenComments(nodes, 0, this) }
        }

    suspend fun createComment(
        playerId: String,
        postId: String,
        body: String,
        parentId: String? = null,
    ): Result<Unit> {
        val payload = buildJsonObject {
            put("body", JsonPrimitive(body))
            if (!parentId.isNullOrBlank()) put("parentId", JsonPrimitive(parentId))
        }.toString()
        return post("/api/players/${encode(playerId)}/feed/${encode(postId)}/comments", payload)
    }

    suspend fun likeComment(playerId: String, postId: String, commentId: String): Result<Unit> =
        post("/api/players/${encode(playerId)}/feed/${encode(postId)}/comments/${encode(commentId)}/like", "{}")

    suspend fun deleteComment(playerId: String, postId: String, commentId: String): Result<Unit> =
        delete("/api/players/${encode(playerId)}/feed/${encode(postId)}/comments/${encode(commentId)}")

    suspend fun followStatus(playerId: String): Result<VrclFollowState> =
        request("/api/players/${encode(playerId)}/follow-status") { body ->
            val obj = json.parseToJsonElement(body).jsonObject
            VrclFollowState(obj.bool("following"), obj.int("followerCount") ?: 0)
        }

    suspend fun follow(playerId: String): Result<Unit> =
        post("/api/players/${encode(playerId)}/follow", "{}")

    suspend fun unfollow(playerId: String): Result<Unit> =
        delete("/api/players/${encode(playerId)}/follow")


    /** Purpose-built endpoint for this app; the only place that reports the Legend profile id. */
    suspend fun socialIdentity(): Result<VrclSocialIdentity> = request("/api/companion/session") { body ->
        val root = json.parseToJsonElement(body).jsonObject
        val account = root["account"]?.jsonObject ?: error("No account in session response")
        val legend = root["legend"] as? JsonObject
        VrclSocialIdentity(
            playerId = account.str("playerId"),
            displayName = legend?.str("displayName") ?: account.str("displayName") ?: "Legend",
            avatarUrl = legend?.str("avatarUrl") ?: account.str("avatar"),
            discordAvatarUrl = account.str("avatar"),
            profilePath = legend?.str("profilePath"),
        )
    }

    suspend fun createPost(
        playerId: String,
        body: String,
        media: List<VrclMedia> = emptyList(),
    ): Result<Unit> {
        val payload = buildJsonObject {
            put("body", JsonPrimitive(body))
            if (media.isNotEmpty()) {
                put("media", buildJsonArray {
                    media.forEach { item ->
                        add(buildJsonObject {
                            put("url", JsonPrimitive(item.url))
                            put("type", JsonPrimitive(item.type))
                            item.width?.let { put("width", JsonPrimitive(it)) }
                            item.height?.let { put("height", JsonPrimitive(it)) }
                        })
                    }
                })
            }
        }.toString()
        return post("/api/players/${encode(playerId)}/feed", payload)
    }

    /**
     * Uploads one attachment and returns the stored media descriptor to hand to [createPost].
     *
     * Anything over one chunk goes through the chunked route. The server insists chunks arrive
     * strictly in order, so this is deliberately sequential rather than parallel.
     */
    suspend fun uploadMedia(
        playerId: String,
        bytes: ByteArray,
        mimeType: String,
        onProgress: (Float) -> Unit = {},
    ): Result<VrclMedia> = withContext(Dispatchers.IO) {
        runCatching {
            val base = "/api/players/${encode(playerId)}/upload-feed-media"
            val fileName = "upload.${mimeType.uploadExtension()}"
            if (bytes.size <= CHUNK_BYTES) {
                onProgress(0.05f)
                val part = MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("media", fileName, bytes.toRequestBody(mimeType.toMediaType()))
                    .build()
                val media = uploadCall(base, part) ?: error("Upload did not return a file.")
                onProgress(1f)
                media
            } else {
                val total = (bytes.size + CHUNK_BYTES - 1) / CHUNK_BYTES
                if (total > CHUNK_MAX_TOTAL) {
                    error("That file is too large to upload from the headset.")
                }
                val uploadId = UUID.randomUUID().toString()
                var assembled: VrclMedia? = null
                for (index in 0 until total) {
                    val from = index * CHUNK_BYTES
                    val to = minOf(from + CHUNK_BYTES, bytes.size)
                    val part = MultipartBody.Builder().setType(MultipartBody.FORM)
                        .addFormDataPart("uploadId", uploadId)
                        .addFormDataPart("chunkIndex", index.toString())
                        .addFormDataPart("totalChunks", total.toString())
                        .addFormDataPart("mimetype", mimeType)
                        .addFormDataPart(
                            "chunk",
                            fileName,
                            bytes.copyOfRange(from, to).toRequestBody(mimeType.toMediaType()),
                        )
                        .build()
                    val media = uploadCall("$base/chunk", part)
                    onProgress((index + 1).toFloat() / total)
                    if (media != null) assembled = media
                }
                assembled ?: error("Upload finished without a file.")
            }
        }
    }

    /** Intermediate chunks answer `{ ok, received }` with no url, so this returns null for those. */
    private fun uploadCall(path: String, part: RequestBody): VrclMedia? {
        val request = Request.Builder()
            .url(BuildConfig.VRCL_API_BASE_URL + path)
            .post(part)
            .authorized()
            .build()
        uploads.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) error(readError(body, response.code))
            val obj = json.parseToJsonElement(body).jsonObject
            if (obj.str("url") == null) return null
            return obj.toMedia()
        }
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
                    kind = obj.str("kind") ?: "steps",
                    luaSource = obj.str("luaSource"),
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

    suspend fun recordScriptInstall(id: String): Result<Unit> =
        post("/api/companion/scripts/${encode(id)}/install", "{}")

    suspend fun cameraCaptureStatus(): Result<VrclCameraCaptureStatus> =
        request("/api/companion/camera-captures/status") { body ->
            val obj = json.parseToJsonElement(body).jsonObject
            VrclCameraCaptureStatus(
                configured = obj.bool("configured"),
                enabled = obj.bool("enabled"),
            )
        }

    suspend fun uploadCameraCapture(bytes: ByteArray, mimeType: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val extension = when (mimeType.lowercase()) {
                    "image/png" -> "png"
                    "image/webp" -> "webp"
                    else -> "jpg"
                }
                val part = MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "image",
                        "vrchat-capture.$extension",
                        bytes.toRequestBody(mimeType.toMediaType()),
                    )
                    .build()
                val request = Request.Builder()
                    .url(BuildConfig.VRCL_API_BASE_URL + "/api/companion/camera-captures")
                    .post(part)
                    .authorized()
                    .build()
                uploads.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw CaptureUploadException(response.code, readError(body, response.code))
                    }
                }
            }
        }

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
    suspend fun heartbeat(installId: String, label: String): Result<VrclAnnouncement?> {
        val payload = buildJsonObject {
            put("installId", JsonPrimitive(installId))
            put("deviceLabel", JsonPrimitive(label))
        }.toString()
        // The heartbeat is already a once-a-minute round trip, so the staff announcement rides
        // along on it instead of holding open a second socket.
        return postFor("/api/companion/heartbeat", payload) { body ->
            (json.parseToJsonElement(body).jsonObject["announcement"] as? JsonObject)?.toAnnouncement()
        }
    }

    /** Whether this account may ask for a group invite right now, without spending an attempt. */
    suspend fun groupInviteStatus(): Result<VrclInviteStatus> =
        request("/api/companion/group/invite") { body ->
            val obj = json.parseToJsonElement(body).jsonObject
            VrclInviteStatus(
                allowed = obj.bool("allowed"),
                reason = obj.str("reason"),
                retryAfterSeconds = obj.long("retryAfterSeconds") ?: 0L,
                dailyLimit = obj.int("dailyLimit") ?: 0,
            )
        }

    /** Asks the site's VRChat account to invite the signed in headset user to the group. */
    suspend fun requestGroupInvite(vrchatUserId: String): Result<String> {
        val payload = buildJsonObject { put("vrchatUserId", JsonPrimitive(vrchatUserId)) }.toString()
        return postFor("/api/companion/group/invite", payload) { body ->
            val obj = json.parseToJsonElement(body).jsonObject
            obj.str("message") ?: "Invite sent. Check your VRChat notifications."
        }
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

    private suspend fun delete(path: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(BuildConfig.VRCL_API_BASE_URL + path)
                    .delete()
                    .authorized()
                    .build()
                http.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) error(readError(body, response.code))
                }
            }
        }

    /** A POST whose reply body matters. Uses the same friendly error copy as [post]. */
    private suspend fun <T> postFor(path: String, payload: String, parse: (String) -> T): Result<T> =
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
                    parse(body)
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

    private companion object {
        // The server rejects any chunk over 3 MB and caps a non-admin upload at 12 chunks,
        // so 2.5 MB keeps a safety margin while still clearing the 25 MB Gold ceiling.
        const val CHUNK_BYTES = 2_500_000
        const val CHUNK_MAX_TOTAL = 12
    }
}

private fun String.uploadExtension(): String = when (lowercase()) {
    "image/gif" -> "gif"
    "image/png" -> "png"
    "image/webp" -> "webp"
    "image/avif" -> "avif"
    "video/webm" -> "webm"
    "video/quicktime" -> "mov"
    "video/mp4" -> "mp4"
    else -> if (startsWith("video/")) "mp4" else "jpg"
}

// File level so VrclLiveFeed can parse the same post shape off the WebSocket.

/** A staff push, sent from the moderation bot's `/announce` command. */
data class VrclAnnouncement(
    val id: String,
    val title: String,
    val body: String,
    val from: String,
    val level: String,
    val chatbox: Boolean,
    val expiresAt: String,
) {
    val isUrgent: Boolean get() = level == "warn" || level == "alert"
}

/** Whether a group invite may be requested, and why not if it may not. */
data class VrclInviteStatus(
    val allowed: Boolean,
    val reason: String?,
    val retryAfterSeconds: Long,
    val dailyLimit: Int,
)

internal fun JsonObject.toAnnouncement(): VrclAnnouncement? {
    val body = str("body") ?: return null
    return VrclAnnouncement(
        id = str("id").orEmpty(),
        title = str("title") ?: "Announcement",
        body = body,
        from = str("from") ?: "VRChat Legends",
        level = str("level") ?: "info",
        chatbox = bool("chatbox"),
        expiresAt = str("expiresAt").orEmpty(),
    )
}

internal fun JsonObject.str(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

internal fun JsonObject.bool(key: String): Boolean =
    (this[key] as? JsonPrimitive)?.booleanOrNull ?: false

internal fun JsonObject.int(key: String): Int? =
    (this[key] as? JsonPrimitive)?.intOrNull

internal fun JsonObject.long(key: String): Long? =
    (this[key] as? JsonPrimitive)?.longOrNull

internal fun JsonObject.strings(key: String): List<String> =
    (this[key] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }

internal fun JsonObject.toMedia(): VrclMedia? {
    val url = str("url") ?: str("src") ?: return null
    // The server only ever says "image" or "video"; infer from the extension when it is absent.
    val type = str("type")
        ?: if (url.substringBefore('?').endsWith(".mp4", true) ||
            url.substringBefore('?').endsWith(".webm", true) ||
            url.substringBefore('?').endsWith(".mov", true)
        ) "video" else "image"
    val meta = (this["vrchat"] as? JsonObject)?.let { vrc ->
        val world = vrc["world"] as? JsonObject
        val author = vrc["author"] as? JsonObject
        VrclPhotoMeta(
            worldName = world?.str("name"),
            worldId = world?.str("id"),
            instanceId = world?.str("instanceId"),
            authorName = author?.str("displayName"),
            takenAt = vrc.str("createdAt"),
            players = (vrc["players"] as? JsonArray).orEmpty()
                .mapNotNull { (it as? JsonObject)?.str("displayName") },
        ).takeIf { it.hasDetail }
    }
    return VrclMedia(
        url = url,
        type = type,
        width = int("width"),
        height = int("height"),
        spoiler = bool("spoiler"),
        vrchat = meta,
    )
}

internal fun JsonObject.mediaList(): List<VrclMedia> =
    (this["media"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.toMedia() }

internal fun JsonObject.toPost(): VrclPost? {
    val id = str("id") ?: return null
    val playerId = str("playerId") ?: return null
    val quoted = (this["quote"] as? JsonObject)?.let { q ->
        val quoteId = q.str("id") ?: return@let null
        VrclQuote(
            id = quoteId,
            playerId = q.str("playerId").orEmpty(),
            authorName = q.str("authorName") ?: "Legend",
            body = q.str("body").orEmpty(),
            media = q.mediaList(),
        )
    }
    return VrclPost(
        key = str("feedKey") ?: "$playerId-$id",
        id = id,
        playerId = playerId,
        authorName = str("authorName") ?: "Legend",
        authorPlayerId = str("authorPlayerId") ?: playerId,
        authorAvatarUrl = str("authorAvatarUrl"),
        authorVerified = bool("authorVerified"),
        body = str("body").orEmpty(),
        createdAt = str("createdAt"),
        likeCount = int("likeCount") ?: 0,
        commentCount = int("commentCount") ?: 0,
        repostCount = int("repostCount") ?: 0,
        viewerLiked = bool("viewerLiked"),
        media = mediaList(),
        repostedBy = (this["repostedBy"] as? JsonObject)?.str("name"),
        hashtags = strings("hashtags"),
        quote = quoted,
        shareUrl = str("shareUrl"),
        profilePath = str("profilePath"),
    )
}

/** Walks the server's nested reply tree into a flat list, capping indent so text stays readable. */
internal fun flattenComments(nodes: JsonArray?, depth: Int, into: MutableList<VrclComment>) {
    nodes.orEmpty().forEach { element ->
        val obj = element as? JsonObject ?: return@forEach
        val id = obj.str("id") ?: return@forEach
        into += VrclComment(
            id = id,
            body = obj.str("body").orEmpty(),
            authorName = obj.str("authorName") ?: "Legend",
            authorAvatarUrl = obj.str("authorAvatarUrl"),
            authorPlayerId = obj.str("authorPlayerId"),
            authorVerified = obj.bool("authorVerified"),
            parentId = obj.str("parentId"),
            createdAt = obj.str("createdAt"),
            likeCount = obj.int("likeCount") ?: 0,
            likedByMe = obj.bool("likedByMe"),
            canDelete = obj.bool("canDelete"),
            depth = depth.coerceAtMost(3),
        )
        flattenComments(obj["replies"] as? JsonArray, depth + 1, into)
    }
}
