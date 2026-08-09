package com.vrchatlegends.osccompanion.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PcBridgeConfigTest {

    @Test
    fun `prefix parsing accepts commas and newlines and rejects junk`() {
        val parsed = PcBridge.parsePrefixes("/tracking/, /avatar/parameters/Vel\n/input/\nnot a path")
        assertEquals(listOf("/tracking/", "/avatar/parameters/Vel", "/input/"), parsed)
    }

    @Test
    fun `empty prefix text yields no filters`() {
        assertTrue(PcBridge.parsePrefixes("").isEmpty())
        assertTrue(PcBridge.parsePrefixes("   ").isEmpty())
    }

    @Test
    fun `bridge is off by default and the ports do not collide with vrchat`() {
        val config = PcBridge.Config()
        assertFalse(config.enabled)
        assertTrue(config.restrictToPcHost)
        // VRChat owns 9000 on the headset, so the downlink must not try to take it.
        assertTrue(config.listenPort != 9000)
        assertEquals(9001, config.pcPort)
    }

    @Test
    fun `stats report the pc as unseen until a downlink arrives`() {
        assertFalse(PcBridge.Stats().pcSeen)
        assertTrue(PcBridge.Stats(lastDownlinkMs = System.currentTimeMillis()).pcSeen)
        assertFalse(PcBridge.Stats(lastDownlinkMs = System.currentTimeMillis() - 60_000).pcSeen)
    }
}
