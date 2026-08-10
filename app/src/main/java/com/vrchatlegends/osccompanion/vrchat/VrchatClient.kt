package com.vrchatlegends.osccompanion.vrchat

import com.vrchatlegends.osccompanion.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.TimeUnit

class VrchatClient(
    private val cookieStore: VrchatCookieStore,
    baseUrl: String = BASE_URL,
    private val http: OkHttpClient = defaultHttpClient(),
) {
    private val base = baseUrl.ensureTrailingSlash().toHttpUrl()
    private val cookieLock = Any()
    private val cookies = cookieStore.load().toMutableMap()

    suspend fun login(username: String, password: String): Result<VrchatLoginResult> = apiCall {
        clearSession()
        val encodedCredentials = "${encodeCredential(username.trim())}:${encodeCredential(password)}"
        val basic = Base64.getEncoder().encodeToString(encodedCredentials.toByteArray(StandardCharsets.UTF_8))
        val response = request("GET", "/auth/user", authorization = "Basic $basic")
        requireSuccess(response)
        VrchatApiJson.loginResult(response.body)
    }

    suspend fun verifyTwoFactor(code: String, methods: List<String>): Result<VrchatUser> = apiCall {
        val response = request(
            "POST",
            VrchatApiJson.twoFactorEndpoint(methods),
            body = """{"code":"${jsonEscape(code.trim())}"}""",
        )
        requireSuccess(response)
        if (!VrchatApiJson.verified(response.body)) error("That 2FA code was rejected")
        currentUserOrThrow()
    }

    suspend fun restoreSession(): Result<VrchatLoginResult?> = apiCall {
        if (cookies["auth"].isNullOrBlank()) return@apiCall null
        val response = request("GET", "/auth/user")
        if (response.code == 401) {
            clearSession()
            return@apiCall null
        }
        requireSuccess(response)
        VrchatApiJson.loginResult(response.body)
    }

    suspend fun logout(): Result<Unit> = apiCall {
        runCatching { request("PUT", "/logout") }
        clearSession()
    }

    suspend fun friends(offline: Boolean): Result<List<VrchatFriend>> = apiCall {
        val response = request("GET", "/auth/user/friends?offline=$offline&n=100&offset=0")
        requireSuccess(response)
        VrchatApiJson.friends(response.body)
    }

    suspend fun notifications(): Result<List<VrchatNotification>> = apiCall {
        val response = request("GET", "/auth/user/notifications?type=all&n=100")
        requireSuccess(response)
        VrchatApiJson.notifications(response.body)
    }

    suspend fun favoriteAvatars(): Result<List<VrchatAvatar>> = apiCall {
        val response = request("GET", "/avatars/favorites?n=100&offset=0")
        requireSuccess(response)
        VrchatApiJson.avatars(response.body)
    }

    suspend fun favoriteWorlds(): Result<List<VrchatWorld>> = apiCall {
        val response = request("GET", "/worlds/favorites?n=100&offset=0")
        requireSuccess(response)
        VrchatApiJson.worlds(response.body)
    }

    suspend fun selectAvatar(avatarId: String): Result<Unit> = emptySuccess(
        method = "PUT",
        path = "/avatars/${pathSegment(avatarId)}/select",
    )

    suspend fun acceptFriendRequest(notificationId: String): Result<Unit> = emptySuccess(
        method = "PUT",
        path = "/auth/user/notifications/${pathSegment(notificationId)}/accept",
    )

    suspend fun hideNotification(notificationId: String): Result<Unit> = emptySuccess(
        method = "PUT",
        path = "/auth/user/notifications/${pathSegment(notificationId)}/hide",
    )

    suspend fun requestInvite(userId: String): Result<Unit> = emptySuccess(
        method = "POST",
        path = "/requestInvite/${pathSegment(userId)}",
    )

    suspend fun invite(userId: String): Result<Unit> = emptySuccess(
        method = "POST",
        path = "/invite/${pathSegment(userId)}",
    )

    suspend fun inviteMyselfTo(location: String): Result<Unit> {
        val worldId = location.substringBefore(':')
        val instanceId = location.substringAfter(':', "")
        if (!worldId.startsWith("wrld_") || instanceId.isBlank()) {
            return Result.failure(IllegalArgumentException("This friend is not in a joinable instance"))
        }
        return emptySuccess(
            method = "POST",
            path = "/invite/myself/to/${pathSegment(worldId)}:${pathSegment(instanceId)}",
        )
    }

    suspend fun joinInvite(worldId: String?, instanceId: String?): Result<Unit> {
        if (worldId.isNullOrBlank() || instanceId.isNullOrBlank()) {
            return Result.failure(IllegalArgumentException("This invite has no joinable instance"))
        }
        return inviteMyselfTo("$worldId:$instanceId")
    }

    private suspend fun emptySuccess(method: String, path: String): Result<Unit> = apiCall {
        val response = request(method, path)
        requireSuccess(response)
    }

    private suspend fun currentUserOrThrow(): VrchatUser {
        val response = request("GET", "/auth/user")
        requireSuccess(response)
        return VrchatApiJson.user(response.body)
    }

    private suspend fun request(
        method: String,
        path: String,
        body: String? = null,
        authorization: String? = null,
    ): ApiResponse = withContext(Dispatchers.IO) {
        val url = base.resolve(path.removePrefix("/")) ?: error("Invalid VRChat API path")
        val requestBody = when {
            body != null -> body.toRequestBody(JSON_MEDIA_TYPE)
            method == "POST" || method == "PUT" -> EMPTY_JSON_BODY
            else -> null
        }
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "VRChat-Legends-OSC-Companion/${BuildConfig.VERSION_NAME} (Quest; https://vrchatlegends.com)")
            .method(method, requestBody)
        authorization?.let { builder.header("Authorization", it) }
        cookieHeader()?.let { builder.header("Cookie", it) }

        http.newCall(builder.build()).execute().use { response ->
            captureCookies(url, response.headers("Set-Cookie"))
            ApiResponse(response.code, response.body?.string().orEmpty())
        }
    }

    private fun captureCookies(url: HttpUrl, headers: List<String>) {
        if (headers.isEmpty()) return
        synchronized(cookieLock) {
            var changed = false
            headers.forEach { raw ->
                val cookie = Cookie.parse(url, raw) ?: return@forEach
                if (cookie.name !in COOKIE_NAMES) return@forEach
                if (cookie.value.isBlank() || cookie.expiresAt <= System.currentTimeMillis()) {
                    changed = cookies.remove(cookie.name) != null || changed
                } else {
                    changed = cookies.put(cookie.name, cookie.value) != cookie.value || changed
                }
            }
            if (changed) cookieStore.save(cookies)
        }
    }

    private fun cookieHeader(): String? = synchronized(cookieLock) {
        cookies.entries
            .filter { it.key in COOKIE_NAMES && it.value.isNotBlank() }
            .joinToString("; ") { "${it.key}=${it.value}" }
            .takeIf { it.isNotBlank() }
    }

    private fun clearSession() {
        synchronized(cookieLock) {
            cookies.clear()
            cookieStore.clear()
        }
    }

    private fun requireSuccess(response: ApiResponse) {
        if (response.code !in 200..299) {
            throw VrchatApiException(response.code, VrchatApiJson.errorMessage(response.body, response.code))
        }
    }

    private suspend fun <T> apiCall(block: suspend () -> T): Result<T> = runCatching { block() }

    private data class ApiResponse(val code: Int, val body: String)

    companion object {
        const val BASE_URL = "https://api.vrchat.cloud/api/1/"
        private val COOKIE_NAMES = setOf("auth", "twoFactorAuth")
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val EMPTY_JSON_BODY = "{}".toRequestBody(JSON_MEDIA_TYPE)

        private fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

        private fun encodeCredential(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
            .replace("+", "%20")
            .replace("%21", "!")
            .replace("%27", "'")
            .replace("%28", "(")
            .replace("%29", ")")
            .replace("%7E", "~")

        private fun pathSegment(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
            .replace("+", "%20")

        private fun jsonEscape(value: String): String = buildString(value.length) {
            value.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
        }

        private fun String.ensureTrailingSlash(): String = if (endsWith('/')) this else "$this/"
    }
}

class VrchatApiException(val statusCode: Int, message: String) : Exception(message)