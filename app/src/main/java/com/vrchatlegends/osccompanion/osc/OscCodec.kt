package com.vrchatlegends.osccompanion.osc

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * OSC 1.0 encoder/decoder.
 *
 * Everything is big-endian and 4-byte aligned. Strings and blobs are null terminated and
 * padded up to the next multiple of four. Bundles are `#bundle` + a 64-bit timetag +
 * length-prefixed elements, and may nest.
 */
object OscCodec {

    private const val BUNDLE_TAG = "#bundle"

    // ── Encoding ────────────────────────────────────────────────────────────────

    fun encode(message: OscMessage): ByteArray {
        val body = ByteArrayBuilder()
        body.putOscString(message.address)

        val tags = StringBuilder(",")
        for (arg in message.args) {
            tags.append(
                when (arg) {
                    is OscArg.OscInt -> 'i'
                    is OscArg.OscFloat -> 'f'
                    is OscArg.OscString -> 's'
                    is OscArg.OscBool -> if (arg.value) 'T' else 'F'
                    is OscArg.OscLong -> 'h'
                    is OscArg.OscDouble -> 'd'
                    is OscArg.OscBlob -> 'b'
                    OscArg.OscNil -> 'N'
                }
            )
        }
        body.putOscString(tags.toString())

        for (arg in message.args) {
            when (arg) {
                is OscArg.OscInt -> body.putInt(arg.value)
                is OscArg.OscFloat -> body.putFloat(arg.value)
                is OscArg.OscString -> body.putOscString(arg.value)
                is OscArg.OscLong -> body.putLong(arg.value)
                is OscArg.OscDouble -> body.putDouble(arg.value)
                is OscArg.OscBlob -> body.putBlob(arg.value)
                // T / F / N carry no payload.
                is OscArg.OscBool, OscArg.OscNil -> Unit
            }
        }
        return body.toByteArray()
    }

    fun encodeBundle(messages: List<OscMessage>): ByteArray {
        val out = ByteArrayBuilder()
        out.putOscString(BUNDLE_TAG)
        out.putLong(1L) // "immediately" timetag
        for (m in messages) {
            val bytes = encode(m)
            out.putInt(bytes.size)
            out.putRaw(bytes)
        }
        return out.toByteArray()
    }

    // ── Decoding ────────────────────────────────────────────────────────────────

    /** Returns every message in the packet; a bundle yields its (possibly nested) contents. */
    fun decode(packet: ByteArray, length: Int = packet.size): List<OscMessage> {
        return try {
            val buf = ByteBuffer.wrap(packet, 0, length).order(ByteOrder.BIG_ENDIAN)
            val out = mutableListOf<OscMessage>()
            decodeInto(buf, out)
            out
        } catch (_: Exception) {
            // A malformed packet from the network must never take the receive loop down.
            emptyList()
        }
    }

    private fun decodeInto(buf: ByteBuffer, out: MutableList<OscMessage>) {
        if (buf.remaining() < 4) return
        val mark = buf.position()
        val head = readOscString(buf)
        if (head == BUNDLE_TAG) {
            if (buf.remaining() < 8) return
            buf.long // timetag, ignored: we act on everything immediately
            while (buf.remaining() >= 4) {
                val size = buf.int
                if (size <= 0 || size > buf.remaining()) return
                val slice = buf.slice().order(ByteOrder.BIG_ENDIAN)
                slice.limit(size)
                decodeInto(slice, out)
                buf.position(buf.position() + size)
            }
            return
        }

        buf.position(mark)
        val address = readOscString(buf)
        if (!address.startsWith("/")) return
        if (buf.remaining() <= 0) {
            out += OscMessage(address)
            return
        }

        val typeTags = readOscString(buf)
        if (!typeTags.startsWith(",")) {
            out += OscMessage(address)
            return
        }

        val args = mutableListOf<OscArg>()
        for (tag in typeTags.drop(1)) {
            when (tag) {
                'i' -> if (buf.remaining() >= 4) args += OscArg.OscInt(buf.int)
                'f' -> if (buf.remaining() >= 4) args += OscArg.OscFloat(buf.float)
                's', 'S' -> args += OscArg.OscString(readOscString(buf))
                'h' -> if (buf.remaining() >= 8) args += OscArg.OscLong(buf.long)
                't' -> if (buf.remaining() >= 8) args += OscArg.OscLong(buf.long)
                'd' -> if (buf.remaining() >= 8) args += OscArg.OscDouble(buf.double)
                'c' -> if (buf.remaining() >= 4) args += OscArg.OscString(buf.int.toChar().toString())
                'r', 'm' -> if (buf.remaining() >= 4) args += OscArg.OscInt(buf.int)
                'T' -> args += OscArg.OscBool(true)
                'F' -> args += OscArg.OscBool(false)
                'N' -> args += OscArg.OscNil
                'I' -> args += OscArg.OscFloat(Float.POSITIVE_INFINITY)
                'b' -> {
                    if (buf.remaining() < 4) break
                    val size = buf.int
                    if (size < 0 || size > buf.remaining()) break
                    val bytes = ByteArray(size)
                    buf.get(bytes)
                    skipPadding(buf, size)
                    args += OscArg.OscBlob(bytes)
                }
                else -> Unit // unknown tag, nothing to consume
            }
        }
        out += OscMessage(address, args)
    }

    private fun readOscString(buf: ByteBuffer): String {
        val start = buf.position()
        var end = start
        while (end < buf.limit() && buf.get(end) != 0.toByte()) end++
        val bytes = ByteArray(end - start)
        buf.position(start)
        buf.get(bytes)
        // Consume the terminator plus alignment padding.
        val consumed = bytes.size + 1
        val padded = ((consumed + 3) / 4) * 4
        val next = (start + padded).coerceAtMost(buf.limit())
        buf.position(next)
        return String(bytes, Charsets.UTF_8)
    }

    private fun skipPadding(buf: ByteBuffer, written: Int) {
        val pad = (4 - (written % 4)) % 4
        buf.position((buf.position() + pad).coerceAtMost(buf.limit()))
    }

    // ── Growable big-endian writer ──────────────────────────────────────────────

    private class ByteArrayBuilder {
        private var buf = ByteArray(256)
        private var size = 0

        private fun ensure(extra: Int) {
            if (size + extra <= buf.size) return
            var cap = buf.size
            while (cap < size + extra) cap *= 2
            buf = buf.copyOf(cap)
        }

        fun putRaw(bytes: ByteArray) {
            ensure(bytes.size)
            System.arraycopy(bytes, 0, buf, size, bytes.size)
            size += bytes.size
        }

        fun putInt(v: Int) {
            ensure(4)
            buf[size++] = (v ushr 24).toByte()
            buf[size++] = (v ushr 16).toByte()
            buf[size++] = (v ushr 8).toByte()
            buf[size++] = v.toByte()
        }

        fun putLong(v: Long) {
            putInt((v ushr 32).toInt())
            putInt(v.toInt())
        }

        fun putFloat(v: Float) = putInt(java.lang.Float.floatToIntBits(v))

        fun putDouble(v: Double) = putLong(java.lang.Double.doubleToLongBits(v))

        fun putOscString(s: String) {
            val bytes = s.toByteArray(Charsets.UTF_8)
            val total = ((bytes.size + 1 + 3) / 4) * 4
            ensure(total)
            System.arraycopy(bytes, 0, buf, size, bytes.size)
            java.util.Arrays.fill(buf, size + bytes.size, size + total, 0.toByte())
            size += total
        }

        fun putBlob(bytes: ByteArray) {
            putInt(bytes.size)
            val total = ((bytes.size + 3) / 4) * 4
            ensure(total)
            System.arraycopy(bytes, 0, buf, size, bytes.size)
            java.util.Arrays.fill(buf, size + bytes.size, size + total, 0.toByte())
            size += total
        }

        fun toByteArray(): ByteArray = buf.copyOf(size)
    }
}
