package com.vrchatlegends.osccompanion.vrchat

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

data class VrchatUser(
    val id: String,
    val displayName: String,
    val status: String = "",
    val statusDescription: String = "",
    val imageUrl: String? = null,
    val platform: String = "",
)

data class VrchatFriend(
    val id: String,
    val displayName: String,
    val status: String,
    val statusDescription: String,
    val location: String,
    val locationLabel: String,
    val imageUrl: String?,
    val platform: String,
) {
    val isOnline: Boolean get() = location != "offline"
    val canRequestInvite: Boolean get() = location == "private"
    val canJoin: Boolean get() = location.startsWith("wrld_") && ':' in location
}

data class VrchatNotification(
    val id: String,
    val type: String,
    val senderUserId: String?,
    val senderDisplayName: String,
    val message: String,
    val worldId: String? = null,
    val instanceId: String? = null,
) {
    val isFriendRequest: Boolean get() = type.equals("friendRequest", ignoreCase = true)
    val isInvite: Boolean get() = type.equals("invite", ignoreCase = true)
    val isRequestInvite: Boolean get() = type.equals("requestInvite", ignoreCase = true)
}

data class VrchatAvatar(
    val id: String,
    val name: String,
    val authorName: String,
    val imageUrl: String?,
    val description: String,
    val platforms: List<String>,
)

data class VrchatWorld(
    val id: String,
    val name: String,
    val authorName: String,
    val imageUrl: String?,
    val capacity: Int,
    val favorites: Int,
    val platforms: List<String>,
)

sealed interface VrchatLoginResult {
    data class SignedIn(val user: VrchatUser) : VrchatLoginResult
    data class TwoFactorRequired(val methods: List<String>) : VrchatLoginResult
}

object VrchatApiJson {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun loginResult(body: String): VrchatLoginResult {
        val root = parseObject(body)
        val methods = root.array("requiresTwoFactorAuth")
            .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        return if (methods.isNotEmpty()) {
            VrchatLoginResult.TwoFactorRequired(methods)
        } else {
            VrchatLoginResult.SignedIn(user(root))
        }
    }

    fun user(body: String): VrchatUser = user(parseObject(body))

    fun friends(body: String): List<VrchatFriend> = parseArray(body).mapNotNull { element ->
        val obj = element as? JsonObject ?: return@mapNotNull null
        val id = obj.str("id") ?: return@mapNotNull null
        val location = obj.str("location").orEmpty().ifBlank { "offline" }
        VrchatFriend(
            id = id,
            displayName = obj.str("displayName") ?: "Unknown",
            status = obj.str("status").orEmpty(),
            statusDescription = obj.str("statusDescription").orEmpty(),
            location = location,
            locationLabel = locationLabel(location, obj.str("worldName")),
            imageUrl = obj.imageUrl(),
            platform = platformLabel(obj.str("last_platform")),
        )
    }

    fun notifications(body: String): List<VrchatNotification> = parseArray(body).mapNotNull { element ->
        val obj = element as? JsonObject ?: return@mapNotNull null
        val id = obj.str("id") ?: return@mapNotNull null
        val details = obj.details()
        VrchatNotification(
            id = id,
            type = obj.str("type").orEmpty().ifBlank { "notification" },
            senderUserId = obj.str("senderUserId"),
            senderDisplayName = obj.str("senderUsername")
                ?: obj.str("senderDisplayName")
                ?: obj.str("username")
                ?: "VRChat",
            message = obj.str("message")
                ?: details?.str("inviteMessage")
                ?: obj.str("type")
                ?: "Notification",
            worldId = details?.str("worldId") ?: obj.str("worldId"),
            instanceId = details?.str("instanceId") ?: obj.str("instanceId"),
        )
    }

    fun avatars(body: String): List<VrchatAvatar> = parseArray(body).mapNotNull { element ->
        val obj = element as? JsonObject ?: return@mapNotNull null
        val id = obj.str("id") ?: return@mapNotNull null
        VrchatAvatar(
            id = id,
            name = obj.str("name") ?: "Unknown avatar",
            authorName = obj.str("authorName") ?: obj.str("author") ?: "Unknown creator",
            imageUrl = obj.str("thumbnailImageUrl") ?: obj.str("imageUrl"),
            description = obj.str("description").orEmpty(),
            platforms = obj.platforms(),
        )
    }

    fun worlds(body: String): List<VrchatWorld> = parseArray(body).mapNotNull { element ->
        val obj = element as? JsonObject ?: return@mapNotNull null
        val id = obj.str("id") ?: return@mapNotNull null
        VrchatWorld(
            id = id,
            name = obj.str("name") ?: "Unknown world",
            authorName = obj.str("authorName") ?: "Unknown creator",
            imageUrl = obj.str("thumbnailImageUrl") ?: obj.str("imageUrl"),
            capacity = obj.int("capacity") ?: 0,
            favorites = obj.int("favorites") ?: 0,
            platforms = obj.platforms(),
        )
    }

    fun errorMessage(body: String, statusCode: Int): String {
        val root = runCatching { parseObject(body) }.getOrNull()
        val nested = root?.get("error") as? JsonObject
        return nested?.str("message")
            ?: root?.str("message")
            ?: "VRChat request failed (HTTP $statusCode)"
    }

    fun verified(body: String): Boolean =
        (parseObject(body)["verified"] as? JsonPrimitive)?.booleanOrNull == true

    fun twoFactorEndpoint(methods: List<String>): String {
        val normalized = methods.map { it.lowercase() }
        return when {
            "totp" in normalized -> "/auth/twofactorauth/totp/verify"
            "emailotp" in normalized -> "/auth/twofactorauth/emailotp/verify"
            "otp" in normalized -> "/auth/twofactorauth/otp/verify"
            else -> error("VRChat requested an unsupported two-factor method")
        }
    }

    private fun user(obj: JsonObject): VrchatUser {
        val id = obj.str("id") ?: error("VRChat returned no user ID")
        return VrchatUser(
            id = id,
            displayName = obj.str("displayName") ?: obj.str("username") ?: "VRChat user",
            status = obj.str("status").orEmpty(),
            statusDescription = obj.str("statusDescription").orEmpty(),
            imageUrl = obj.imageUrl(),
            platform = platformLabel(obj.str("last_platform")),
        )
    }

    private fun parseObject(body: String): JsonObject =
        json.parseToJsonElement(body) as? JsonObject ?: error("VRChat returned an invalid object")

    private fun parseArray(body: String): JsonArray =
        json.parseToJsonElement(body) as? JsonArray ?: error("VRChat returned an invalid list")

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.int(key: String): Int? =
        (this[key] as? JsonPrimitive)?.intOrNull

    private fun JsonObject.array(key: String): JsonArray =
        this[key] as? JsonArray ?: JsonArray(emptyList())

    private fun JsonObject.imageUrl(): String? =
        str("currentAvatarThumbnailImageUrl") ?: str("userIcon") ?: str("profilePicOverride")

    private fun JsonObject.details(): JsonObject? = when (val details = this["details"]) {
        is JsonObject -> details
        is JsonPrimitive -> runCatching { json.parseToJsonElement(details.content) as? JsonObject }.getOrNull()
        else -> null
    }

    private fun JsonObject.platforms(): List<String> {
        val packages = this["unityPackages"] as? JsonArray ?: return emptyList()
        return packages.mapNotNull { (it as? JsonObject)?.str("platform") }
            .map(::platformLabel)
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun locationLabel(location: String, worldName: String?): String = when (location) {
        "offline" -> "Offline"
        "private" -> "Private world"
        "traveling" -> "Traveling"
        else -> worldName ?: if (location.startsWith("wrld_")) "Joinable world" else "In a world"
    }

    private fun platformLabel(platform: String?): String = when (platform?.lowercase()) {
        "android" -> "Quest"
        "standalonewindows" -> "PC"
        else -> platform.orEmpty()
    }
}