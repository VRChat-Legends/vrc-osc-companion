package com.vrchatlegends.osccompanion.vrcl

import com.vrchatlegends.osccompanion.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
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

/**
 * Thin read-only client for the VRChat Legends API.
 *
 * Sessions are `Authorization: Bearer <jwt>`; the same header also accepts a
 * `vrcl_`-prefixed personal API key, which is the fallback when the OAuth deep link is
 * blocked by a locked-down browser.
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
                .url(BuildConfig.VRCL_BASE_URL + "/api/auth/logout")
                .post("{}".toRequestBody("application/json".toMediaType()))
                .authorized()
                .build()
            http.newCall(request).execute().close()
        }
    }

    private suspend fun <T> request(path: String, parse: (String) -> T): Result<T> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(BuildConfig.VRCL_BASE_URL + path)
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

    private fun JsonObject.str(key: String): String? =
        (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    @Suppress("unused")
    private fun JsonObject.int(key: String): Int? =
        (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.intOrNull
}
