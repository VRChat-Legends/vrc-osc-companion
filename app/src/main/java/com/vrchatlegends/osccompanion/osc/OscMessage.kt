package com.vrchatlegends.osccompanion.osc

/**
 * The argument types VRChat actually round-trips. The codec understands a wider set on
 * the wire (see [OscCodec]) but everything we originate is one of these.
 */
sealed class OscArg {
    data class OscInt(val value: Int) : OscArg()
    data class OscFloat(val value: Float) : OscArg()
    data class OscString(val value: String) : OscArg()
    data class OscBool(val value: Boolean) : OscArg()
    data class OscLong(val value: Long) : OscArg()
    data class OscDouble(val value: Double) : OscArg()
    data class OscBlob(val value: ByteArray) : OscArg() {
        override fun equals(other: Any?) = other is OscBlob && value.contentEquals(other.value)
        override fun hashCode() = value.contentHashCode()
    }

    object OscNil : OscArg()

    /** Human-readable form used by the monitor screen. */
    fun display(): String = when (this) {
        is OscInt -> value.toString()
        is OscFloat -> formatFloat(value)
        is OscString -> "\"$value\""
        is OscBool -> value.toString()
        is OscLong -> value.toString()
        is OscDouble -> value.toString()
        is OscBlob -> "<blob ${value.size}B>"
        OscNil -> "nil"
    }

    /** Best-effort numeric view, used to drive parameter sliders regardless of wire type. */
    fun asFloatOrNull(): Float? = when (this) {
        is OscInt -> value.toFloat()
        is OscFloat -> value
        is OscBool -> if (value) 1f else 0f
        is OscLong -> value.toFloat()
        is OscDouble -> value.toFloat()
        else -> null
    }

    private fun formatFloat(v: Float): String {
        val rounded = Math.round(v * 1000f) / 1000f
        return if (rounded == rounded.toInt().toFloat()) "${rounded.toInt()}.0" else rounded.toString()
    }
}

data class OscMessage(
    val address: String,
    val args: List<OscArg> = emptyList(),
) {
    override fun toString(): String =
        if (args.isEmpty()) address else "$address ${args.joinToString(" ") { it.display() }}"

    companion object {
        fun of(address: String, value: Int) = OscMessage(address, listOf(OscArg.OscInt(value)))
        fun of(address: String, value: Float) = OscMessage(address, listOf(OscArg.OscFloat(value)))
        fun of(address: String, value: Boolean) = OscMessage(address, listOf(OscArg.OscBool(value)))
        fun of(address: String, value: String) = OscMessage(address, listOf(OscArg.OscString(value)))
    }
}

enum class OscDirection { OUT, IN }

data class OscLogEntry(
    val direction: OscDirection,
    val message: OscMessage,
    val timestampMs: Long = System.currentTimeMillis(),
    val peer: String? = null,
)
