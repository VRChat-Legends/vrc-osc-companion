package com.vrchatlegends.osccompanion.logs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VrcLogReaderTest {

    @Test
    fun `parses a standard vrchat log header`() {
        val line = VrcLogReader.parseLine("2024.06.01 21:03:11 Log        -  [Behaviour] OnPlayerJoined Cadyn")
        assertEquals("2024.06.01 21:03:11", line.timestamp)
        assertEquals(VrcLogReader.Level.LOG, line.level)
        assertEquals("[Behaviour] OnPlayerJoined Cadyn", line.message)
    }

    @Test
    fun `recognises warning and error levels`() {
        assertEquals(
            VrcLogReader.Level.WARNING,
            VrcLogReader.parseLine("2024.06.01 21:03:11 Warning    -  something").level,
        )
        assertEquals(
            VrcLogReader.Level.ERROR,
            VrcLogReader.parseLine("2024.06.01 21:03:11 Error      -  something").level,
        )
    }

    @Test
    fun `stack trace continuation lines survive without a header`() {
        val raw = "  at SomeClass.SomeMethod () [0x00000]"
        val line = VrcLogReader.parseLine(raw)
        assertNull(line.timestamp)
        assertEquals(VrcLogReader.Level.OTHER, line.level)
        assertEquals(raw.trim(), line.message)
    }

    @Test
    fun `pulls player and world events out of the stream`() {
        val join = VrcLogReader.extractEvent(
            VrcLogReader.parseLine("2024.06.01 21:03:11 Log        -  [Behaviour] OnPlayerJoined Cadyn")
        )
        assertEquals(VrcLogReader.SessionEvent.Kind.PLAYER_JOIN, join?.kind)
        assertEquals("Cadyn", join?.detail)

        val world = VrcLogReader.extractEvent(
            VrcLogReader.parseLine("2024.06.01 21:03:11 Log        -  [Behaviour] Joining or Creating Room: The Great Pug")
        )
        assertEquals(VrcLogReader.SessionEvent.Kind.WORLD, world?.kind)
        assertEquals("The Great Pug", world?.detail)
    }

    @Test
    fun `ordinary lines produce no event`() {
        val line = VrcLogReader.parseLine("2024.06.01 21:03:11 Log        -  [Behaviour] nothing interesting")
        assertNull(VrcLogReader.extractEvent(line))
    }

    @Test
    fun `known vrchat packages are distinct and look like application ids`() {
        val packages = VrcLogReader.CANDIDATE_PACKAGES
        assertEquals(packages.size, packages.distinct().size)
        assertTrue(packages.all { it.startsWith("com.vrchat.") })
    }
}
