package com.vrchatlegends.osccompanion.vrchat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VrchatApiJsonTest {

    @Test
    fun `login response selects two factor before parsing a user`() {
        val result = VrchatApiJson.loginResult(
            """{"requiresTwoFactorAuth":["totp","otp"]}""",
        )

        assertEquals(
            listOf("totp", "otp"),
            (result as VrchatLoginResult.TwoFactorRequired).methods,
        )
    }

    @Test
    fun `two factor route prefers authenticator then email then recovery`() {
        assertEquals(
            "/auth/twofactorauth/totp/verify",
            VrchatApiJson.twoFactorEndpoint(listOf("otp", "totp")),
        )
        assertEquals(
            "/auth/twofactorauth/emailotp/verify",
            VrchatApiJson.twoFactorEndpoint(listOf("emailOtp")),
        )
        assertEquals(
            "/auth/twofactorauth/otp/verify",
            VrchatApiJson.twoFactorEndpoint(listOf("otp")),
        )
        assertTrue(runCatching { VrchatApiJson.twoFactorEndpoint(listOf("futureMethod")) }.isFailure)
    }

    @Test
    fun `two factor verification fails closed`() {
        assertTrue(VrchatApiJson.verified("""{"verified":true}"""))
        assertFalse(VrchatApiJson.verified("""{"verified":false}"""))
        assertFalse(VrchatApiJson.verified("{}"))
    }

    @Test
    fun `friend parser normalizes location and Quest platform`() {
        val friends = VrchatApiJson.friends(
            """[{"id":"usr_1","displayName":"Cadyn","location":"private","last_platform":"android"}]""",
        )

        assertEquals("Private world", friends.single().locationLabel)
        assertEquals("Quest", friends.single().platform)
        assertTrue(friends.single().canRequestInvite)
    }

    @Test
    fun `notification parser keeps invite instance details`() {
        val notification = VrchatApiJson.notifications(
            """[{"id":"not_1","type":"invite","senderUsername":"Friend","details":{"worldId":"wrld_1","instanceId":"123~private"}}]""",
        ).single()

        assertTrue(notification.isInvite)
        assertEquals("wrld_1", notification.worldId)
        assertEquals("123~private", notification.instanceId)
    }

    @Test
    fun `api error prefers the nested VRChat message`() {
        assertEquals(
            "Invalid Username or Password",
            VrchatApiJson.errorMessage(
                """{"error":{"message":"Invalid Username or Password"}}""",
                401,
            ),
        )
    }
}