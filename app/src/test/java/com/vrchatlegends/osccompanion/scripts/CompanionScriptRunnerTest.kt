package com.vrchatlegends.osccompanion.scripts

import com.vrchatlegends.osccompanion.osc.OscArg
import com.vrchatlegends.osccompanion.osc.ParameterState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

class CompanionScriptRunnerTest {

    @Test
    fun `runs only the three allowed actions with bounded pacing`() = runBlocking {
        val events = mutableListOf<String>()
        val waits = mutableListOf<Long>()
        val runner = runner(
            script = installed(
                StoredScriptStep(type = "chatbox", text = "hello"),
                StoredScriptStep(type = "wait", ms = 250),
                StoredScriptStep(type = "parameter", address = "/avatar/parameters/AFK", value = "true"),
            ),
            onChatbox = {
                events += "chat:$it"
                true
            },
            onParameter = { _, name, value ->
                events += "parameter:$name:$value"
                true
            },
            onWait = { waits += it },
        )

        val result = runner.executeForTests("safe")

        assertTrue(result.isSuccess)
        assertEquals(listOf("chat:hello", "parameter:AFK:OscBool(value=true)"), events)
        assertEquals(
            listOf(
                CompanionScriptRunner.CHATBOX_ACTION_INTERVAL_MS,
                250L,
            ),
            waits,
        )
    }

    @Test
    fun `invalid later parameter prevents every earlier action`() = runBlocking {
        val sends = mutableListOf<String>()
        val script = installed(
            StoredScriptStep(type = "chatbox", text = "must not send"),
            StoredScriptStep(type = "parameter", address = "/avatar/parameters/Missing", value = "1"),
        )
        val runner = runner(script, onChatbox = {
            sends += it
            true
        })

        val result = runner.executeForTests("safe")

        assertTrue(result.isFailure)
        assertTrue(sends.isEmpty())
    }

    @Test
    fun `disconnected runtime sends nothing`() = runBlocking {
        val sends = mutableListOf<String>()
        val runner = runner(
            installed(StoredScriptStep(type = "chatbox", text = "must not send")),
            runtimeSnapshot = { snapshot(connected = false) },
            onChatbox = {
                sends += it
                true
            },
        )

        assertTrue(runner.executeForTests("safe").isFailure)
        assertTrue(sends.isEmpty())
    }

    @Test
    fun `avatar change aborts all remaining parameter steps`() = runBlocking {
        val sends = mutableListOf<String>()
        var avatar = "avtr_one"
        val runner = runner(
            script = installed(
                StoredScriptStep(type = "parameter", address = "/avatar/parameters/AFK", value = "true"),
                StoredScriptStep(type = "wait", ms = 1),
                StoredScriptStep(type = "parameter", address = "/avatar/parameters/AFK", value = "false"),
            ),
            runtimeSnapshot = { snapshot(avatarId = avatar) },
            onParameter = { _, name, value ->
                sends += "$name:$value"
                true
            },
            onWait = { if (it == 1L) avatar = "avtr_two" },
        )

        val result = runner.executeForTests("safe")

        assertTrue(result.isFailure)
        assertEquals(listOf("AFK:OscBool(value=true)"), sends)
    }

    @Test
    fun `manually widened runnable document is rejected before callbacks`() = runBlocking {
        val sends = mutableListOf<String>()
        val widened = installed(StoredScriptStep(type = "chatbox", text = "safe"))
            .copy(steps = listOf(StoredScriptStep(type = "shell", text = "id")))
        val runner = runner(widened, onChatbox = {
            sends += it
            true
        })

        assertTrue(runner.executeForTests("safe").isFailure)
        assertTrue(sends.isEmpty())
    }

    @Test
    fun `two chatbox lines cannot collapse into one burst`() = runBlocking {
        val sends = mutableListOf<String>()
        val waits = mutableListOf<Long>()
        val runner = runner(
            script = installed(
                StoredScriptStep(type = "chatbox", text = "first"),
                StoredScriptStep(type = "chatbox", text = "second"),
            ),
            onChatbox = {
                sends += it
                true
            },
            onWait = { waits += it },
        )

        assertTrue(runner.executeForTests("safe").isSuccess)
        assertEquals(listOf("first", "second"), sends)
        assertEquals(listOf(CompanionScriptRunner.CHATBOX_ACTION_INTERVAL_MS), waits)
    }

    @Test
    fun `simultaneous starts acquire only one runner slot`() = runBlocking {
        val runnerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val enteredWait = kotlinx.coroutines.CompletableDeferred<Unit>()
        val waitCount = AtomicInteger()
        val runner = runner(
            script = installed(StoredScriptStep(type = "wait", ms = 1)),
            scope = runnerScope,
            onWait = {
                waitCount.incrementAndGet()
                enteredWait.complete(Unit)
                awaitCancellation()
            },
        )
        val ready = CountDownLatch(2)
        val release = CountDownLatch(1)
        val starts = List(2) {
            async(Dispatchers.Default) {
                ready.countDown()
                release.await()
                runner.start("safe")
            }
        }

        ready.await()
        release.countDown()
        starts.forEach { it.await() }
        withTimeout(5_000) { enteredWait.await() }

        assertEquals(1, waitCount.get())
        runner.cancel()
        withTimeout(5_000) { runner.state.first { !it.running } }
        runnerScope.cancel()
    }

    @Test
    fun `cancelled job keeps ownership until its callbacks finish`() = runBlocking {
        val runnerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val firstEntered = kotlinx.coroutines.CompletableDeferred<Unit>()
        val releaseFirst = kotlinx.coroutines.CompletableDeferred<Unit>()
        val secondEntered = kotlinx.coroutines.CompletableDeferred<Unit>()
        val waitCount = AtomicInteger()
        val runner = runner(
            script = installed(StoredScriptStep(type = "wait", ms = 1)),
            scope = runnerScope,
            onWait = {
                if (waitCount.incrementAndGet() == 1) {
                    firstEntered.complete(Unit)
                    withContext(NonCancellable) { releaseFirst.await() }
                } else {
                    secondEntered.complete(Unit)
                    awaitCancellation()
                }
            },
        )

        runner.start("safe")
        withTimeout(5_000) { firstEntered.await() }
        runner.cancel()
        runner.start("safe")

        assertEquals(1, waitCount.get())
        assertTrue(runner.state.value.running)
        releaseFirst.complete(Unit)
        withTimeout(5_000) { runner.state.first { !it.running } }

        runner.start("safe")
        withTimeout(5_000) { secondEntered.await() }
        assertEquals(2, waitCount.get())
        runner.cancel()
        withTimeout(5_000) { runner.state.first { !it.running } }
        runnerScope.cancel()
    }

    @Test
    fun `parameter callback rejects an avatar change at dispatch`() = runBlocking {
        var avatar = "avtr_one"
        var snapshotCount = 0
        val sends = mutableListOf<String>()
        val runner = runner(
            script = installed(
                StoredScriptStep(
                    type = "parameter",
                    address = "/avatar/parameters/AFK",
                    value = "true",
                ),
            ),
            runtimeSnapshot = {
                snapshotCount += 1
                val result = snapshot(avatarId = avatar)
                if (snapshotCount == 3) avatar = "avtr_two"
                result
            },
            onParameter = { expectedAvatarId, name, _ ->
                if (avatar != expectedAvatarId) return@runner false
                sends += name
                true
            },
        )

        val result = runner.executeForTests("safe")

        assertTrue(result.isFailure)
        assertTrue(sends.isEmpty())
    }

    @Test
    fun `callback failure prevents every later step`() = runBlocking {
        val sends = mutableListOf<String>()
        val runner = runner(
            script = installed(
                StoredScriptStep(type = "chatbox", text = "first"),
                StoredScriptStep(type = "chatbox", text = "second"),
            ),
            onChatbox = {
                sends += it
                error("dispatch failed")
            },
        )

        val result = runner.executeForTests("safe")

        assertTrue(result.isFailure)
        assertEquals(listOf("first"), sends)
    }

    @Test
    fun `parameter callback refusal prevents later actions`() = runBlocking {
        val chatboxSends = mutableListOf<String>()
        val runner = runner(
            script = installed(
                StoredScriptStep(
                    type = "parameter",
                    address = "/avatar/parameters/AFK",
                    value = "true",
                ),
                StoredScriptStep(type = "chatbox", text = "must not send"),
            ),
            onChatbox = {
                chatboxSends += it
                true
            },
            onParameter = { _, _, _ -> false },
        )

        val result = runner.executeForTests("safe")

        assertTrue(result.isFailure)
        assertFalse(chatboxSends.isNotEmpty())
    }

    @Test
    fun `absolute timeout stops a stalled script`() = runBlocking {
        val runner = runner(
            script = installed(StoredScriptStep(type = "wait", ms = 1)),
            onWait = { awaitCancellation() },
            maxRunDurationMs = 50L,
        )

        val result = runner.executeForTests("safe")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("safety limit"))
    }

    private fun runner(
        script: InstalledCompanionScript,
        scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        runtimeSnapshot: () -> ScriptRuntimeSnapshot = { snapshot() },
        onChatbox: (String) -> Boolean = { true },
        onParameter: (String, String, OscArg) -> Boolean = { _, _, _ -> true },
        onWait: suspend (Long) -> Unit = {},
        maxRunDurationMs: Long = CompanionScriptRunner.MAX_RUN_DURATION_MS,
    ) = CompanionScriptRunner(
        scope = scope,
        loadScript = { Result.success(script) },
        runtimeSnapshot = runtimeSnapshot,
        sendSilentChatbox = onChatbox,
        setAvatarParameter = onParameter,
        wait = onWait,
        maxRunDurationMs = maxRunDurationMs,
    )

    private fun snapshot(
        connected: Boolean = true,
        avatarId: String? = "avtr_one",
    ) = ScriptRuntimeSnapshot(
        connected = connected,
        avatarId = avatarId,
        parameters = listOf(
            ParameterState(
                name = "AFK",
                address = "/avatar/parameters/AFK",
                typeTag = 'T',
                value = OscArg.OscBool(false),
                writable = true,
                fromOscQuery = true,
            ),
        ),
    )

    private fun installed(vararg steps: StoredScriptStep) = InstalledCompanionScript(
        sourceId = "safe",
        title = "Safe preset",
        steps = steps.toList(),
        authorName = "Legend",
        installedAtMs = 1L,
    )
}