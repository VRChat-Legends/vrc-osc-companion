package com.vrchatlegends.osccompanion.scripts

import com.vrchatlegends.osccompanion.osc.OscArg
import com.vrchatlegends.osccompanion.osc.ParameterState
import com.vrchatlegends.osccompanion.osc.VrcOsc
import com.vrchatlegends.osccompanion.vrcl.VrclScript
import com.vrchatlegends.osccompanion.vrcl.VrclScriptStep
import kotlinx.serialization.Serializable

@Serializable
data class InstalledCompanionScript(
    val schemaVersion: Int = CompanionScriptPolicy.SCHEMA_VERSION,
    val sourceId: String,
    val title: String,
    val summary: String? = null,
    val tags: List<String> = emptyList(),
    val kind: String = CompanionScriptPolicy.KIND_STEPS,
    val luaSource: String? = null,
    val steps: List<StoredScriptStep> = emptyList(),
    val authorName: String,
    val installedAtMs: Long,
) {
    val isLua: Boolean get() = kind == CompanionScriptPolicy.KIND_LUA
}

@Serializable
data class StoredScriptStep(
    val type: String,
    val text: String? = null,
    val address: String? = null,
    val value: String? = null,
    val ms: Int? = null,
) {
    val describe: String
        get() = when (type) {
            CompanionScriptPolicy.TYPE_CHATBOX -> "Chatbox: ${text.orEmpty()}"
            CompanionScriptPolicy.TYPE_WAIT -> "Wait ${(ms ?: 0)} ms"
            else -> "${address?.removePrefix(VrcOsc.AVATAR_PARAMETER_PREFIX).orEmpty()} = ${value.orEmpty()}"
        }
}

object CompanionScriptPolicy {
    const val SCHEMA_VERSION = 1
    const val MAX_INSTALLED_SCRIPTS = 100
    const val MAX_FILE_BYTES = 64 * 1024L
    const val MAX_STEPS = 20
    const val MAX_TOTAL_WAIT_MS = 60_000
    const val MAX_TITLE_CHARS = 80
    const val MAX_SUMMARY_CHARS = 300
    const val MAX_TAGS = 6
    const val MAX_TAG_CHARS = 24
    const val MAX_AUTHOR_CHARS = 80

    const val TYPE_CHATBOX = "chatbox"
    const val TYPE_PARAMETER = "parameter"
    const val TYPE_WAIT = "wait"

    const val KIND_STEPS = "steps"
    const val KIND_LUA = "lua"
    const val MAX_LUA_BYTES = 32 * 1024
    const val MAX_LUA_LINES = 2_000

    private val safeId = Regex("^[A-Za-z0-9_-]{1,128}$")
    private val parameterAddress = Regex("^/avatar/parameters/[A-Za-z0-9_-]{1,64}$")

    fun fromRemote(
        script: VrclScript,
        installedAtMs: Long = System.currentTimeMillis(),
    ): Result<InstalledCompanionScript> = runCatching {
        validateDocument(
            InstalledCompanionScript(
                sourceId = script.id,
                title = script.title,
                summary = script.summary,
                tags = script.tags,
                kind = script.kind,
                luaSource = script.luaSource,
                steps = script.steps.map(::fromRemoteStep),
                authorName = script.authorName,
                installedAtMs = installedAtMs,
            ),
        )
    }

    fun validateStored(script: InstalledCompanionScript): Result<InstalledCompanionScript> =
        runCatching { validateDocument(script) }

    fun resolveParameter(
        step: StoredScriptStep,
        parameters: Collection<ParameterState>,
    ): Result<Pair<String, OscArg>> = runCatching {
        require(step.type == TYPE_PARAMETER) { "That step is not an avatar parameter." }
        val address = requireNotNull(step.address) { "The avatar parameter address is missing." }
        val parameter = parameters.firstOrNull {
            it.address == address && it.fromOscQuery && it.writable
        } ?: error("${address.removePrefix(VrcOsc.AVATAR_PARAMETER_PREFIX)} is not writable on the current avatar.")
        val raw = requireNotNull(step.value) { "The avatar parameter value is missing." }

        val argument = when {
            parameter.isBool -> when (raw.lowercase()) {
                "true", "1" -> OscArg.OscBool(true)
                "false", "0" -> OscArg.OscBool(false)
                else -> error("${parameter.name} needs a true or false value.")
            }

            parameter.isInt -> {
                val number = raw.toDoubleOrNull()
                    ?.takeIf { it.isFinite() && it % 1.0 == 0.0 }
                    ?: error("${parameter.name} needs a whole number.")
                require(number in 0.0..255.0) { "${parameter.name} must be between 0 and 255." }
                OscArg.OscInt(number.toInt())
            }

            parameter.isFloat -> {
                val number = raw.toFloatOrNull()?.takeIf { it.isFinite() }
                    ?: error("${parameter.name} needs a number.")
                require(number in -1f..1f) { "${parameter.name} must be between -1 and 1." }
                OscArg.OscFloat(number)
            }

            else -> error("${parameter.name} has an unsupported OSC type.")
        }
        parameter.name to argument
    }

    private fun fromRemoteStep(step: VrclScriptStep): StoredScriptStep = StoredScriptStep(
        type = step.type.lowercase(),
        text = step.text,
        address = step.address,
        value = step.value,
        ms = step.ms,
    )

    private fun validateDocument(script: InstalledCompanionScript): InstalledCompanionScript {
        require(script.schemaVersion == SCHEMA_VERSION) { "This script format is not supported." }
        require(safeId.matches(script.sourceId)) { "The script ID is invalid." }
        val title = requiredDisplayText(script.title, MAX_TITLE_CHARS, "title")
        val summary = script.summary?.let { optionalDisplayText(it, MAX_SUMMARY_CHARS, "summary") }
        require(script.tags.size <= MAX_TAGS) { "A script can have at most $MAX_TAGS tags." }
        val tags = script.tags.map { optionalDisplayText(it, MAX_TAG_CHARS, "tag") }
        val author = requiredDisplayText(script.authorName, MAX_AUTHOR_CHARS, "author")
        require(script.installedAtMs >= 0L) { "The install time is invalid." }

        if (script.kind == KIND_LUA) {
            val source = validateLuaSource(script.luaSource)
            require(script.steps.isEmpty()) { "A Lua script cannot also contain steps." }
            return script.copy(
                title = title,
                summary = summary,
                tags = tags,
                luaSource = source,
                authorName = author,
            )
        }

        require(script.kind == KIND_STEPS) { "This script type is not supported." }
        require(script.luaSource == null) { "A step script cannot contain Lua code." }
        require(script.steps.isNotEmpty()) { "A script needs at least one step." }
        require(script.steps.size <= MAX_STEPS) { "A script can have at most $MAX_STEPS steps." }

        var totalWaitMs = 0
        val steps = script.steps.mapIndexed { index, step ->
            validateStep(step, index).also {
                if (it.type == TYPE_WAIT) totalWaitMs += it.ms ?: 0
            }
        }
        require(totalWaitMs <= MAX_TOTAL_WAIT_MS) {
            "A script can wait for at most ${MAX_TOTAL_WAIT_MS / 1000} seconds in total."
        }

        return script.copy(
            title = title,
            summary = summary,
            tags = tags,
            steps = steps,
            authorName = author,
        )
    }

    /** Validates a chatbox line coming from a running Lua script. */
    fun validateLuaChatbox(text: String): Result<String> = runCatching {
        requireNotNull(validateStep(StoredScriptStep(type = TYPE_CHATBOX, text = text), "vrc.chatbox").text)
    }

    /** Validates a parameter write coming from a running Lua script. */
    fun validateLuaParameter(name: String, value: String): Result<StoredScriptStep> = runCatching {
        validateStep(
            StoredScriptStep(
                type = TYPE_PARAMETER,
                address = VrcOsc.AVATAR_PARAMETER_PREFIX + name.trim(),
                value = value,
            ),
            "vrc.param",
        )
    }

    private fun validateStep(step: StoredScriptStep, index: Int): StoredScriptStep =
        validateStep(step, "Step ${index + 1}")

    private fun validateStep(step: StoredScriptStep, where: String): StoredScriptStep {
        return when (step.type.lowercase()) {
            TYPE_CHATBOX -> {
                val text = requireNotNull(step.text) { "$where needs chatbox text." }.trim()
                require(text.isNotEmpty()) { "$where needs chatbox text." }
                require(text.length <= VrcOsc.CHATBOX_MAX_CHARS) {
                    "$where exceeds the ${VrcOsc.CHATBOX_MAX_CHARS} character chatbox limit."
                }
                require(text.count { it == '\n' } + 1 <= VrcOsc.CHATBOX_MAX_LINES) {
                    "$where exceeds the ${VrcOsc.CHATBOX_MAX_LINES} line chatbox limit."
                }
                require(!hasUnsafeControls(text, allowNewlines = true)) {
                    "$where contains unsupported control characters."
                }
                StoredScriptStep(type = TYPE_CHATBOX, text = text)
            }

            TYPE_PARAMETER -> {
                val address = requireNotNull(step.address) { "$where needs an avatar parameter." }.trim()
                require(parameterAddress.matches(address)) {
                    "$where can only target a named avatar parameter."
                }
                val value = requireNotNull(step.value) { "$where needs a parameter value." }.trim()
                require(value.length in 1..32 && !hasUnsafeControls(value, allowNewlines = false)) {
                    "$where has an invalid parameter value."
                }
                val boolValue = value.equals("true", ignoreCase = true) ||
                    value.equals("false", ignoreCase = true)
                val number = value.toDoubleOrNull()
                require(boolValue || number?.isFinite() == true) {
                    "$where needs a true, false, or numeric value."
                }
                val fitsFloat = number != null && number in -1.0..1.0
                val fitsInt = number != null && number in 0.0..255.0 && number % 1.0 == 0.0
                require(number == null || fitsFloat || fitsInt) {
                    "$where has an out of range value."
                }
                StoredScriptStep(
                    type = TYPE_PARAMETER,
                    address = address,
                    value = if (boolValue) value.lowercase() else value,
                )
            }

            TYPE_WAIT -> {
                val ms = requireNotNull(step.ms) { "$where needs a wait duration." }
                require(ms in 0..10_000) { "$where can wait for at most 10000 ms." }
                StoredScriptStep(type = TYPE_WAIT, ms = ms)
            }

            else -> error("$where uses an unsupported action.")
        }
    }

    /**
     * Lua source is data here, never trusted: it may only run inside
     * [LuaSandbox], which has no file, network, or Java access. This check
     * keeps the payload a reasonable size and plain text.
     */
    fun validateLuaSource(raw: String?): String {
        val source = requireNotNull(raw?.replace("\r\n", "\n")?.trim()) { "The script has no Lua code." }
        require(source.isNotEmpty()) { "The script has no Lua code." }
        require(source.toByteArray(Charsets.UTF_8).size <= MAX_LUA_BYTES) {
            "Lua code can be at most ${MAX_LUA_BYTES / 1024} KB."
        }
        require(source.count { it == '\n' } + 1 <= MAX_LUA_LINES) {
            "Lua code can be at most $MAX_LUA_LINES lines."
        }
        source.forEach { ch ->
            require(ch == '\n' || ch == '\t' || !Character.isISOControl(ch)) {
                "Lua code contains unsupported control characters."
            }
        }
        return source
    }

    private fun requiredDisplayText(value: String, max: Int, field: String): String {
        val text = optionalDisplayText(value, max, field)
        require(text.isNotEmpty()) { "The script $field is required." }
        return text
    }

    private fun optionalDisplayText(value: String, max: Int, field: String): String {
        val text = value.trim()
        require(text.length <= max) { "The script $field is too long." }
        require(!hasUnsafeControls(text, allowNewlines = false)) {
            "The script $field contains unsupported control characters."
        }
        return text
    }

    private fun hasUnsafeControls(value: String, allowNewlines: Boolean): Boolean {
        var index = 0
        while (index < value.length) {
            val codePoint = Character.codePointAt(value, index)
            val type = Character.getType(codePoint)
            val allowedNewline = allowNewlines && codePoint == '\n'.code
            if (!allowedNewline && (
                    type == Character.CONTROL.toInt() ||
                        type == Character.FORMAT.toInt() ||
                        type == Character.LINE_SEPARATOR.toInt() ||
                        type == Character.PARAGRAPH_SEPARATOR.toInt() ||
                        type == Character.SURROGATE.toInt()
                    )
            ) {
                return true
            }
            index += Character.charCount(codePoint)
        }
        return false
    }
}