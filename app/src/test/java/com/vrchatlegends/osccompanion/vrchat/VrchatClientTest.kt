package com.vrchatlegends.osccompanion.vrchat

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VrchatClientTest {
    private lateinit var server: MockWebServer
    private lateinit var store: MemoryCookieStore
    private lateinit var client: VrchatClient

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        store = MemoryCookieStore()
        client = VrchatClient(store, server.url("api/1/").toString())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `login sends basic auth once and persists only session cookies`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "auth=session-one; Path=/; HttpOnly")
                .addHeader("Set-Cookie", "unrelated=ignored; Path=/")
                .setBody("""{"requiresTwoFactorAuth":["totp"]}"""),
        )

        val result = client.login("name@example.com", "secret password").getOrThrow()
        val request = server.takeRequest()

        assertTrue(result is VrchatLoginResult.TwoFactorRequired)
        assertNotNull(request.getHeader("Authorization"))
        assertNull(request.getHeader("Cookie"))
        assertEquals(mapOf("auth" to "session-one"), store.cookies)
        assertFalse(store.cookies.toString().contains("secret"))
    }

    @Test
    fun `login never follows a redirect with credentials`() = runBlocking {
        val redirectTarget = MockWebServer().apply { start() }
        try {
            server.enqueue(
                MockResponse()
                    .setResponseCode(302)
                    .addHeader("Location", redirectTarget.url("capture")),
            )

            val result = client.login("name@example.com", "secret password")

            assertTrue(result.isFailure)
            assertEquals(1, server.requestCount)
            assertEquals(0, redirectTarget.requestCount)
        } finally {
            redirectTarget.shutdown()
        }
    }

    @Test
    fun `two factor uses saved cookie and never repeats basic auth`() = runBlocking {
        store.cookies = mutableMapOf("auth" to "session-one")
        client = VrchatClient(store, server.url("api/1/").toString())
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "twoFactorAuth=second-factor; Path=/; HttpOnly")
                .setBody("""{"verified":true}"""),
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody(USER_JSON))

        val user = client.verifyTwoFactor("123456", listOf("totp")).getOrThrow()
        val verify = server.takeRequest()
        val profile = server.takeRequest()

        assertEquals("/api/1/auth/twofactorauth/totp/verify", verify.path)
        assertEquals("auth=session-one", verify.getHeader("Cookie"))
        assertNull(verify.getHeader("Authorization"))
        assertTrue(profile.getHeader("Cookie")!!.contains("twoFactorAuth=second-factor"))
        assertEquals("Cadyn", user.displayName)
    }

    @Test
    fun `two factor rejects a response without explicit verification`() = runBlocking {
        store.cookies = mutableMapOf("auth" to "session-one")
        client = VrchatClient(store, server.url("api/1/").toString())
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val result = client.verifyTwoFactor("123456", listOf("totp"))

        assertTrue(result.isFailure)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `new session cookie replaces the saved value`() = runBlocking {
        store.cookies = mutableMapOf("auth" to "session-one")
        client = VrchatClient(store, server.url("api/1/").toString())
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "auth=session-two; Path=/; HttpOnly")
                .setBody(USER_JSON),
        )

        client.restoreSession().getOrThrow()

        assertEquals("session-two", store.cookies["auth"])
    }

    @Test
    fun `expired session clears encrypted storage`() = runBlocking {
        store.cookies = mutableMapOf("auth" to "expired")
        client = VrchatClient(store, server.url("api/1/").toString())
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"Missing Credentials"}}"""))

        assertNull(client.restoreSession().getOrThrow())
        assertTrue(store.cookies.isEmpty())
    }

    @Test
    fun `join action creates an invite to the current account`() = runBlocking {
        store.cookies = mutableMapOf("auth" to "session-one")
        client = VrchatClient(store, server.url("api/1/").toString())
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        client.inviteMyselfTo("wrld_123:456~region(us)").getOrThrow()
        val request = server.takeRequest()

        assertEquals("POST", request.method)
        assertEquals("/api/1/invite/myself/to/wrld_123:456%7Eregion%28us%29", request.path)
    }

    @Test
    fun `invite action targets the selected friend`() = runBlocking {
        store.cookies = mutableMapOf("auth" to "session-one")
        client = VrchatClient(store, server.url("api/1/").toString())
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        client.invite("usr_friend").getOrThrow()
        val request = server.takeRequest()

        assertEquals("POST", request.method)
        assertEquals("/api/1/invite/usr_friend", request.path)
    }

    @Test
    fun `social requests use cookies and no authorization header`() = runBlocking {
        store.cookies = mutableMapOf("auth" to "session-one", "twoFactorAuth" to "factor")
        client = VrchatClient(store, server.url("api/1/").toString())
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))

        client.friends(offline = false).getOrThrow()
        val request = server.takeRequest()

        assertEquals("/api/1/auth/user/friends?offline=false&n=100&offset=0", request.path)
        assertTrue(request.getHeader("Cookie")!!.contains("auth=session-one"))
        assertNull(request.getHeader("Authorization"))
    }

    private class MemoryCookieStore : VrchatCookieStore {
        var cookies = mutableMapOf<String, String>()

        override fun load(): Map<String, String> = cookies.toMap()

        override fun save(cookies: Map<String, String>) {
            this.cookies = cookies.toMutableMap()
        }

        override fun clear() {
            cookies.clear()
        }
    }

    private companion object {
        const val USER_JSON = """{"id":"usr_1","displayName":"Cadyn","last_platform":"android"}"""
    }
}