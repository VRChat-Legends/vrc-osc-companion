package com.vrchatlegends.osccompanion.osc

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OscRepositorySafetyTest {

    @Test
    fun `avatar change revokes prior OSCQuery provenance`() {
        val parameters = mapOf(
            "AFK" to ParameterState(
                name = "AFK",
                address = "/avatar/parameters/AFK",
                typeTag = 'T',
                value = OscArg.OscBool(true),
                writable = true,
                fromOscQuery = true,
            ),
        )

        val revoked = revokeScriptOscQueryProvenance(parameters).getValue("AFK")

        assertFalse(revoked.fromOscQuery)
        assertNull(revoked.value)
    }

    @Test
    fun `schema epoch rejects stale loads even after avatar identity cycles`() {
        val epoch = AvatarSchemaEpoch()
        val firstAvatar = epoch.current

        assertTrue(epoch.markLoaded(firstAvatar))
        assertTrue(epoch.isLoaded())

        epoch.invalidate()
        epoch.invalidate()

        assertFalse(epoch.markLoaded(firstAvatar))
        assertFalse(epoch.isLoaded())
        assertTrue(epoch.markLoaded(epoch.current))
        assertTrue(epoch.isLoaded())
    }
}