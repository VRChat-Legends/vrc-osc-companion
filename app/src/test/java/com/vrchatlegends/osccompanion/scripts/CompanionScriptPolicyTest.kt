package com.vrchatlegends.osccompanion.scripts

import com.vrchatlegends.osccompanion.osc.OscArg
import com.vrchatlegends.osccompanion.osc.ParameterState
import com.vrchatlegends.osccompanion.vrcl.VrclScript
import com.vrchatlegends.osccompanion.vrcl.VrclScriptStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionScriptPolicyTest {

    @Test
    fun `accepts only the declarative script vocabulary`() {
        val result = CompanionScriptPolicy.fromRemote(
            script(
                VrclScriptStep(type = "chatbox", text = "Hello VRChat"),
                VrclScriptStep(type = "wait", ms = 500),
                VrclScriptStep(type = "parameter", address = "/avatar/parameters/AFK", value = "true"),
            ),
            installedAtMs = 100L,
        )

        assertTrue(result.isSuccess)
        assertEquals(3, result.getOrThrow().steps.size)
    }

    @Test
    fun `rejects code shell network and input actions`() {
        listOf("shell", "http", "intent", "javascript", "input").forEach { type ->
            val result = CompanionScriptPolicy.fromRemote(script(VrclScriptStep(type = type, text = "x")))
            assertTrue("Expected $type to be rejected", result.isFailure)
        }

        val movement = CompanionScriptPolicy.fromRemote(
            script(VrclScriptStep(type = "parameter", address = "/input/MoveForward", value = "1")),
        )
        assertTrue(movement.isFailure)
    }

    @Test
    fun `rejects scripts that can consume excessive time or memory`() {
        val tooMany = CompanionScriptPolicy.fromRemote(
            script(*Array(CompanionScriptPolicy.MAX_STEPS + 1) { VrclScriptStep(type = "wait", ms = 1) }),
        )
        val tooLong = CompanionScriptPolicy.fromRemote(
            script(*Array(7) { VrclScriptStep(type = "wait", ms = 10_000) }),
        )
        val tooMuchText = CompanionScriptPolicy.fromRemote(
            script(VrclScriptStep(type = "chatbox", text = "x".repeat(145))),
        )

        assertTrue(tooMany.isFailure)
        assertTrue(tooLong.isFailure)
        assertTrue(tooMuchText.isFailure)
    }

    @Test
    fun `rejects control characters and manually widened stored files`() {
        val controls = CompanionScriptPolicy.fromRemote(
            script(VrclScriptStep(type = "chatbox", text = "hello\u0000world")),
        )
        val bidiOverride = CompanionScriptPolicy.fromRemote(
            script(VrclScriptStep(type = "chatbox", text = "safe\u202Etxt.exe")),
        )
        val hiddenLineBreak = CompanionScriptPolicy.fromRemote(
            script(VrclScriptStep(type = "chatbox", text = "line one\u2028line two")),
        )
        val widened = validInstalled().copy(
            steps = listOf(StoredScriptStep(type = "launch", text = "com.android.settings")),
        )

        assertTrue(controls.isFailure)
        assertTrue(bidiOverride.isFailure)
        assertTrue(hiddenLineBreak.isFailure)
        assertTrue(CompanionScriptPolicy.validateStored(widened).isFailure)
    }

    @Test
    fun `runnable copy does not retain a remote avatar URL`() {
        val installed = validInstalled()
        val fieldNames = installed::class.java.declaredFields.map { it.name }

        assertTrue("authorAvatarUrl" !in fieldNames)
    }

    @Test
    fun `parameter writes require an exact writable OSCQuery node`() {
        val step = StoredScriptStep(
            type = "parameter",
            address = "/avatar/parameters/AFK",
            value = "true",
        )
        val inboundOnly = parameter("AFK", 'T', writable = true, fromOscQuery = false)
        val outputOnly = parameter("AFK", 'T', writable = false, fromOscQuery = true)
        val writable = parameter("AFK", 'T', writable = true, fromOscQuery = true)

        assertTrue(CompanionScriptPolicy.resolveParameter(step, listOf(inboundOnly)).isFailure)
        assertTrue(CompanionScriptPolicy.resolveParameter(step, listOf(outputOnly)).isFailure)
        assertEquals(
            "AFK" to OscArg.OscBool(true),
            CompanionScriptPolicy.resolveParameter(step, listOf(writable)).getOrThrow(),
        )
    }

    @Test
    fun `parameter values are constrained by the live avatar type`() {
        val intParameter = parameter("Mode", 'i')
        val floatParameter = parameter("Blend", 'f')

        assertTrue(resolve("Mode", "256", intParameter).isFailure)
        assertTrue(resolve("Mode", "1.5", intParameter).isFailure)
        assertEquals(OscArg.OscInt(255), resolve("Mode", "255", intParameter).getOrThrow().second)
        assertTrue(resolve("Blend", "1.1", floatParameter).isFailure)
        assertEquals(OscArg.OscFloat(-0.5f), resolve("Blend", "-0.5", floatParameter).getOrThrow().second)
    }

    @Test
    fun `rejects numeric values outside every supported parameter range`() {
        val tooHigh = CompanionScriptPolicy.fromRemote(
            script(
                VrclScriptStep(
                    type = "parameter",
                    address = "/avatar/parameters/Mode",
                    value = "256",
                ),
            ),
        )
        val tooLow = CompanionScriptPolicy.fromRemote(
            script(
                VrclScriptStep(
                    type = "parameter",
                    address = "/avatar/parameters/Blend",
                    value = "-1.01",
                ),
            ),
        )
        val impossibleFraction = CompanionScriptPolicy.fromRemote(
            script(
                VrclScriptStep(
                    type = "parameter",
                    address = "/avatar/parameters/Mode",
                    value = "1.5",
                ),
            ),
        )

        assertTrue(tooHigh.isFailure)
        assertTrue(tooLow.isFailure)
        assertTrue(impossibleFraction.isFailure)
    }

    private fun resolve(name: String, value: String, parameter: ParameterState) =
        CompanionScriptPolicy.resolveParameter(
            StoredScriptStep(
                type = "parameter",
                address = "/avatar/parameters/$name",
                value = value,
            ),
            listOf(parameter),
        )

    private fun parameter(
        name: String,
        type: Char,
        writable: Boolean = true,
        fromOscQuery: Boolean = true,
    ) = ParameterState(
        name = name,
        address = "/avatar/parameters/$name",
        typeTag = type,
        value = null,
        writable = writable,
        fromOscQuery = fromOscQuery,
    )

    private fun validInstalled() = CompanionScriptPolicy.fromRemote(
        script(VrclScriptStep(type = "chatbox", text = "Hello")),
        installedAtMs = 100L,
    ).getOrThrow()

    @Test
    fun `accepts a lua script and rejects mixed empty or oversized ones`() {
        val lua = CompanionScriptPolicy.fromRemote(luaScript("""vrc.chatbox("hi")"""))
        assertTrue(lua.exceptionOrNull()?.message.orEmpty(), lua.isSuccess)
        assertTrue(lua.getOrThrow().isLua)

        val tooBig = CompanionScriptPolicy.fromRemote(luaScript("-- padding\n".repeat(10_000)))
        assertTrue(tooBig.isFailure)

        val mixed = CompanionScriptPolicy.fromRemote(
            luaScript("vrc.wait(1)").copy(steps = listOf(VrclScriptStep(type = "wait", ms = 1))),
        )
        assertTrue(mixed.isFailure)

        val empty = CompanionScriptPolicy.fromRemote(luaScript("   "))
        assertTrue(empty.isFailure)

        val unknownKind = CompanionScriptPolicy.fromRemote(luaScript("vrc.wait(1)").copy(kind = "exe"))
        assertTrue(unknownKind.isFailure)
    }

    private fun luaScript(source: String) = script().copy(
        kind = CompanionScriptPolicy.KIND_LUA,
        luaSource = source,
    )

    private fun script(vararg steps: VrclScriptStep) = VrclScript(
        id = "script_123",
        title = "Safe preset",
        summary = "A test preset",
        tags = listOf("test"),
        steps = steps.toList(),
        authorName = "Legend",
        authorAvatarUrl = "https://example.com/avatar.png",
        installs = 0,
        likeCount = 0,
        viewerLiked = false,
        canEdit = false,
    )
}