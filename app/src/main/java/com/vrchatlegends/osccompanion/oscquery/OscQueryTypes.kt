package com.vrchatlegends.osccompanion.oscquery

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Minimal model of the OSCQuery proposal (github.com/Vidvox/OSCQueryProposal) as VRChat
 * implements it since 2023.3.1.
 *
 * A node tree is served over HTTP at `/`. Leaves carry a `TYPE` (an OSC type-tag string),
 * a `VALUE` array and an `ACCESS` bitmask. `GET /?HOST_INFO` returns the UDP endpoint the
 * peer wants OSC on, which is how the port negotiation avoids hardcoded 9000/9001.
 */
object OscQueryAccess {
    const val NONE = 0
    const val READ = 1
    const val WRITE = 2
    const val READ_WRITE = 3
}

/** A resolved OSCQuery peer on the network (VRChat itself, or another OSC tool). */
data class OscQueryPeer(
    val name: String,
    val host: String,
    val httpPort: Int,
    val oscIp: String? = null,
    val oscPort: Int? = null,
) {
    val isVrChat: Boolean get() = name.startsWith("VRChat-Client", ignoreCase = true)
    val rootUrl: String get() = "http://$host:$httpPort/"
    val hostInfoUrl: String get() = "http://$host:$httpPort/?HOST_INFO"
}

/** One addressable endpoint discovered in a peer's tree. */
data class OscQueryNode(
    val fullPath: String,
    val typeTags: String,
    val access: Int,
    val value: List<JsonElement> = emptyList(),
    val description: String? = null,
) {
    val name: String get() = fullPath.substringAfterLast('/')
    val isAvatarParameter: Boolean get() = fullPath.startsWith("/avatar/parameters/")
    val writable: Boolean get() = access and OscQueryAccess.WRITE != 0
    val readable: Boolean get() = access and OscQueryAccess.READ != 0

    /** Single-argument shorthand: 'i', 'f', 'T'/'F' (bool), 's'. */
    val primaryType: Char? get() = typeTags.firstOrNull()

    fun currentFloat(): Float? {
        val primitive = value.firstOrNull() as? JsonPrimitive ?: return null
        primitive.booleanOrNull?.let { return if (it) 1f else 0f }
        return primitive.doubleOrNull?.toFloat()
    }

    fun currentBool(): Boolean? {
        val primitive = value.firstOrNull() as? JsonPrimitive ?: return null
        return primitive.booleanOrNull ?: primitive.intOrNull?.let { it != 0 }
    }
}

object OscQueryJson {

    /** Flattens a peer's node tree into every leaf that has a TYPE. */
    fun parseTree(root: JsonElement): List<OscQueryNode> {
        val out = mutableListOf<OscQueryNode>()
        walk(root, out)
        return out
    }

    private fun walk(element: JsonElement, out: MutableList<OscQueryNode>) {
        val obj = element as? JsonObject ?: return
        val fullPath = obj["FULL_PATH"]?.jsonPrimitive?.contentOrNullSafe()
        val type = obj["TYPE"]?.jsonPrimitive?.contentOrNullSafe()

        if (fullPath != null && type != null) {
            out += OscQueryNode(
                fullPath = fullPath,
                typeTags = type,
                access = obj["ACCESS"]?.jsonPrimitive?.intOrNull ?: OscQueryAccess.READ_WRITE,
                value = obj["VALUE"]?.let { runCatching { it.jsonArray.toList() }.getOrDefault(emptyList()) }
                    ?: emptyList(),
                description = obj["DESCRIPTION"]?.jsonPrimitive?.contentOrNullSafe(),
            )
        }

        val contents = obj["CONTENTS"] as? JsonObject ?: return
        for ((_, child) in contents) walk(child, out)
    }

    fun parseHostInfo(name: String, host: String, httpPort: Int, element: JsonElement): OscQueryPeer {
        val obj = element as? JsonObject
        return OscQueryPeer(
            name = obj?.get("NAME")?.jsonPrimitive?.contentOrNullSafe() ?: name,
            host = host,
            httpPort = httpPort,
            oscIp = obj?.get("OSC_IP")?.jsonPrimitive?.contentOrNullSafe(),
            oscPort = obj?.get("OSC_PORT")?.jsonPrimitive?.intOrNull,
        )
    }

    /** The HOST_INFO document we serve so VRChat learns which UDP port to send us. */
    fun buildHostInfo(name: String, oscIp: String, oscPort: Int): JsonObject = buildJsonObject {
        put("NAME", name)
        put("OSC_IP", oscIp)
        put("OSC_PORT", oscPort)
        put("OSC_TRANSPORT", "UDP")
        put("EXTENSIONS", buildJsonObject {
            put("ACCESS", true)
            put("VALUE", true)
            put("RANGE", false)
            put("DESCRIPTION", true)
            put("CLIPMODE", false)
            put("UNIT", false)
            put("CRITICAL", false)
            put("TAGS", false)
            put("EXTENDED_TYPE", false)
            put("HTML", false)
            put("LISTEN", false)
            put("PATH_CHANGED", false)
            put("PATH_ADDED", false)
            put("PATH_REMOVED", false)
            put("PATH_RENAMED", false)
        })
    }

    /**
     * Builds the node tree we advertise. Listing `/avatar/change` and
     * `/avatar/parameters` is what makes VRChat start streaming avatar state to us.
     */
    fun buildTree(subscribedPaths: List<String>): JsonObject {
        val tree = MutableNode("/")
        tree.ensure("/avatar/change", "s", OscQueryAccess.WRITE)
        tree.ensure("/avatar/parameters", null, OscQueryAccess.NONE)
        for (path in subscribedPaths.distinct()) {
            tree.ensure(path, "f", OscQueryAccess.WRITE)
        }
        return tree.toJson()
    }

    private class MutableNode(val fullPath: String) {
        var type: String? = null
        var access: Int = OscQueryAccess.NONE
        val children = LinkedHashMap<String, MutableNode>()

        fun ensure(path: String, type: String?, access: Int) {
            val segments = path.trim('/').split('/').filter { it.isNotEmpty() }
            var node = this
            var acc = ""
            for (segment in segments) {
                acc += "/$segment"
                node = node.children.getOrPut(segment) { MutableNode(acc) }
            }
            if (type != null) {
                node.type = type
                node.access = access
            }
        }

        fun toJson(): JsonObject = buildJsonObject {
            put("FULL_PATH", fullPath)
            put("ACCESS", access)
            type?.let {
                put("TYPE", it)
                put("VALUE", buildJsonArray { })
            }
            if (children.isNotEmpty()) {
                put("CONTENTS", buildJsonObject {
                    for ((name, child) in children) put(name, child.toJson())
                })
            }
        }
    }

    private fun JsonPrimitive.contentOrNullSafe(): String? = content.takeIf { it.isNotEmpty() }
}
