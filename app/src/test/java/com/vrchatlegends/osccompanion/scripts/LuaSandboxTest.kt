package com.vrchatlegends.osccompanion.scripts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LuaSandboxTest {

    private class RecordingHost : LuaHostApi {
        val chatboxes = mutableListOf<String>()
        val parameters = mutableListOf<Pair<String, String>>()
        val waits = mutableListOf<Long>()
        val logs = mutableListOf<String>()
        var failChatbox: String? = null

        override fun chatbox(text: String) {
            failChatbox?.let { throw IllegalStateException(it) }
            chatboxes += text
        }

        override fun setParameter(name: String, rawValue: String) {
            parameters += name to rawValue
        }

        override fun sleep(ms: Long) {
            waits += ms
        }

        override fun log(line: String) {
            logs += line
        }

        override fun elapsedMs(): Long = 42L
    }

    private fun sandbox(
        host: RecordingHost = RecordingHost(),
        cancelled: () -> Boolean = { false },
        maxInstructions: Long = LuaSandbox.MAX_INSTRUCTIONS,
    ) = LuaSandbox(host, cancelled, maxInstructions)

    @Test
    fun `api calls reach the host with clean values`() {
        val host = RecordingHost()
        val result = sandbox(host).run(
            """
            vrc.chatbox("hello world")
            vrc.param("Mood", 3)
            vrc.param("Blend", 0.5)
            vrc.param("Flag", true)
            vrc.wait(500)
            wait(250)
            print("elapsed", vrc.elapsed())
            """.trimIndent(),
        )
        assertTrue(result.exceptionOrNull()?.message.orEmpty(), result.isSuccess)
        assertEquals(listOf("hello world"), host.chatboxes)
        assertEquals(listOf("Mood" to "3", "Blend" to "0.5", "Flag" to "true"), host.parameters)
        assertEquals(listOf(500L, 250L), host.waits)
        assertEquals(listOf("elapsed\t42"), host.logs)
    }

    @Test
    fun `whole numbers do not grow a decimal suffix`() {
        val host = RecordingHost()
        val result = sandbox(host).run("""vrc.param("Level", 1 + 2)""")
        assertTrue(result.isSuccess)
        assertEquals(listOf("Level" to "3"), host.parameters)
    }

    @Test
    fun `every escape hatch is gone`() {
        val result = sandbox().run(
            """
            assert(os == nil, "os leaked")
            assert(io == nil, "io leaked")
            assert(require == nil, "require leaked")
            assert(package == nil, "package leaked")
            assert(luajava == nil, "luajava leaked")
            assert(dofile == nil, "dofile leaked")
            assert(loadfile == nil, "loadfile leaked")
            assert(debug == nil, "debug leaked")
            assert(coroutine == nil, "coroutine leaked")
            assert(collectgarbage == nil, "collectgarbage leaked")
            """.trimIndent(),
        )
        assertTrue(result.exceptionOrNull()?.message.orEmpty(), result.isSuccess)
    }

    @Test
    fun `infinite loops are stopped by the instruction budget`() {
        val result = sandbox(maxInstructions = 10_000).run("while true do end")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("too much work"))
    }

    @Test
    fun `pcall cannot outlive the instruction budget`() {
        val result = sandbox(maxInstructions = 10_000).run(
            "while true do pcall(function() while true do end end) end",
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun `the cancel flag stops the script`() {
        val result = sandbox(cancelled = { true }).run("local x = 0\nwhile true do x = x + 1 end")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ScriptStopped)
    }

    @Test
    fun `syntax errors fail cleanly`() {
        val result = sandbox().run("this is not lua(")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().startsWith("The script failed"))
    }

    @Test
    fun `host validation failures stop the script`() {
        val host = RecordingHost().apply { failChatbox = "chatbox rejected" }
        val result = sandbox(host).run("""vrc.chatbox("nope") vrc.wait(1)""")
        assertTrue(result.isFailure)
        assertEquals(0, host.waits.size)
    }
}
