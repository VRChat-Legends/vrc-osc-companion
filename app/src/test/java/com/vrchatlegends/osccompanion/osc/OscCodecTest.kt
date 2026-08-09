package com.vrchatlegends.osccompanion.osc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OscCodecTest {

    @Test
    fun `encodes and decodes a chatbox message`() {
        val original = OscMessage(
            VrcOsc.CHATBOX_INPUT,
            listOf(
                OscArg.OscString("hello world"),
                OscArg.OscBool(true),
                OscArg.OscBool(false),
            ),
        )
        val decoded = OscCodec.decode(OscCodec.encode(original)).single()
        assertEquals(original.address, decoded.address)
        assertEquals(original.args, decoded.args)
    }

    @Test
    fun `pads addresses to a four byte boundary`() {
        // "/a" is 2 bytes plus a terminator, so the encoded address block must be 4 bytes.
        val bytes = OscCodec.encode(OscMessage("/a"))
        assertEquals(0, bytes.size % 4)
    }

    @Test
    fun `round trips int float and bool arguments`() {
        val original = OscMessage(
            "/avatar/parameters/Test",
            listOf(OscArg.OscInt(42), OscArg.OscFloat(0.5f), OscArg.OscBool(true)),
        )
        val decoded = OscCodec.decode(OscCodec.encode(original)).single()
        assertEquals(OscArg.OscInt(42), decoded.args[0])
        assertEquals(0.5f, (decoded.args[1] as OscArg.OscFloat).value, 0.0001f)
        assertEquals(OscArg.OscBool(true), decoded.args[2])
    }

    @Test
    fun `decodes every message in a bundle`() {
        val messages = listOf(
            OscMessage.of("/input/Jump", 1),
            OscMessage.of("/input/Jump", 0),
            OscMessage.of("/avatar/eyeheight", 1.7f),
        )
        val decoded = OscCodec.decode(OscCodec.encodeBundle(messages))
        assertEquals(3, decoded.size)
        assertEquals("/avatar/eyeheight", decoded[2].address)
    }

    @Test
    fun `malformed packets decode to nothing instead of throwing`() {
        assertTrue(OscCodec.decode(byteArrayOf(1, 2, 3)).isEmpty())
        assertTrue(OscCodec.decode(ByteArray(0)).isEmpty())
    }

    @Test
    fun `clips chatbox text to VRChat limits`() {
        val long = "x".repeat(200)
        assertEquals(VrcOsc.CHATBOX_MAX_CHARS, clipChatbox(long).length)

        val manyLines = (1..20).joinToString("\n") { "l$it" }
        assertEquals(VrcOsc.CHATBOX_MAX_LINES, clipChatbox(manyLines).count { it == '\n' } + 1)
    }
}
