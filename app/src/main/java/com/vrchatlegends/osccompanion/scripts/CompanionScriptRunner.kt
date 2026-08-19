package com.vrchatlegends.osccompanion.scripts

import com.vrchatlegends.osccompanion.osc.OscArg
import com.vrchatlegends.osccompanion.osc.ParameterState
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

data class ScriptRuntimeSnapshot(
    val connected: Boolean,
    val avatarId: String?,
    val parameters: List<ParameterState>,
)

data class ScriptRunnerState(
    val runningScriptId: String? = null,
    val runningTitle: String? = null,
    val currentStep: Int = 0,
    val totalSteps: Int = 0,
    val message: String? = null,
    val error: String? = null,
) {
    val running: Boolean get() = runningScriptId != null
}

class CompanionScriptRunner(
    private val scope: CoroutineScope,
    private val loadScript: suspend (String) -> Result<InstalledCompanionScript>,
    private val runtimeSnapshot: () -> ScriptRuntimeSnapshot,
    private val sendSilentChatbox: suspend (String) -> Boolean,
    private val setAvatarParameter: (String, String, OscArg) -> Boolean,
    private val wait: suspend (Long) -> Unit = { delay(it) },
    private val maxRunDurationMs: Long = MAX_RUN_DURATION_MS,
) {
    private val _state = MutableStateFlow(ScriptRunnerState())
    val state: StateFlow<ScriptRunnerState> = _state.asStateFlow()

    private val runLock = Any()
    private var runJob: Job? = null

    fun start(sourceId: String) {
        lateinit var newJob: Job
        newJob = scope.launch(start = CoroutineStart.LAZY) {
            val result = execute(sourceId)
            synchronized(runLock) {
                if (runJob !== newJob) return@synchronized
                _state.value = result.fold(
                    onSuccess = { script ->
                        ScriptRunnerState(message = "Finished ${script.title}")
                    },
                    onFailure = { error ->
                        ScriptRunnerState(error = error.message ?: "The script was stopped safely.")
                    },
                )
            }
        }

        synchronized(runLock) {
            if (runJob != null) {
                _state.value = _state.value.copy(
                    error = "Stop the current script before starting another.",
                )
                newJob.cancel()
                return
            }
            runJob = newJob
        }

        newJob.invokeOnCompletion { cause ->
            synchronized(runLock) {
                if (runJob !== newJob) return@synchronized
                runJob = null
                if (cause is CancellationException) {
                    _state.value = ScriptRunnerState(message = "Script stopped")
                } else if (cause != null) {
                    _state.value = ScriptRunnerState(
                        error = cause.message ?: "The script was stopped safely.",
                    )
                }
            }
        }
        newJob.start()
    }

    fun cancel() {
        synchronized(runLock) {
            val currentJob = runJob ?: return
            if (currentJob.isCompleted) return
            _state.value = _state.value.copy(message = "Stopping script...", error = null)
            currentJob.cancel()
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null, error = null)
    }

    internal suspend fun executeForTests(sourceId: String): Result<InstalledCompanionScript> = execute(sourceId)

    private suspend fun execute(sourceId: String): Result<InstalledCompanionScript> = try {
        Result.success(withTimeout(maxRunDurationMs) { executeValidated(sourceId) })
    } catch (timeout: TimeoutCancellationException) {
        Result.failure(IllegalStateException("The script exceeded the two-minute safety limit."))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }

    private suspend fun executeValidated(sourceId: String): InstalledCompanionScript {
        val script = loadScript(sourceId).getOrThrow()
        val validated = CompanionScriptPolicy.validateStored(script).getOrThrow()
        val initial = runtimeSnapshot()
        require(initial.connected) { "VRChat must be connected before a script can run." }

        if (validated.isLua) {
            executeLua(validated)
            return validated
        }

        val hasParameters = validated.steps.any { it.type == CompanionScriptPolicy.TYPE_PARAMETER }
        if (hasParameters) {
            require(!initial.avatarId.isNullOrBlank()) { "Wait for VRChat to report the current avatar." }
        }
        val expectedAvatarId = initial.avatarId

        // Resolve every parameter before step one. A bad later step must not allow partial execution.
        validated.steps.filter { it.type == CompanionScriptPolicy.TYPE_PARAMETER }.forEach { step ->
            CompanionScriptPolicy.resolveParameter(step, initial.parameters).getOrThrow()
        }

        _state.value = ScriptRunnerState(
            runningScriptId = validated.sourceId,
            runningTitle = validated.title,
            totalSteps = validated.steps.size,
        )

        validated.steps.forEachIndexed { index, step ->
            val current = runtimeSnapshot()
            require(current.connected) { "VRChat disconnected, so the script was stopped." }
            if (hasParameters) {
                require(current.avatarId == initial.avatarId) {
                    "The avatar changed, so the script was stopped before sending more values."
                }
            }

            _state.value = _state.value.copy(currentStep = index + 1)
            when (step.type) {
                CompanionScriptPolicy.TYPE_CHATBOX -> {
                    require(sendSilentChatbox(requireNotNull(step.text))) {
                        "VRChat disconnected before the chatbox line could be sent."
                    }
                    if (index != validated.steps.lastIndex) wait(CHATBOX_ACTION_INTERVAL_MS)
                }

                CompanionScriptPolicy.TYPE_PARAMETER -> {
                    val (name, value) = CompanionScriptPolicy.resolveParameter(
                        step,
                        current.parameters,
                    ).getOrThrow()
                    val beforeDispatch = runtimeSnapshot()
                    require(beforeDispatch.connected && beforeDispatch.avatarId == expectedAvatarId) {
                        "The avatar changed, so the script was stopped before sending more values."
                    }
                    require(setAvatarParameter(requireNotNull(expectedAvatarId), name, value)) {
                        "The avatar or connection changed before the parameter could be sent."
                    }
                    if (index != validated.steps.lastIndex) wait(PARAMETER_ACTION_INTERVAL_MS)
                }

                CompanionScriptPolicy.TYPE_WAIT -> wait(requireNotNull(step.ms).toLong())
                else -> error("The installed script contains an unsupported action.")
            }
        }
        return validated
    }

    /**
     * Runs Lua inside [LuaSandbox] on a daemon thread. The sandbox has no
     * file, network, or Java access; the only reachable effects are the host
     * callbacks below, which apply the same validation and pacing as steps.
     */
    private suspend fun executeLua(script: InstalledCompanionScript) {
        val source = requireNotNull(script.luaSource) { "The Lua script is empty." }
        _state.value = ScriptRunnerState(
            runningScriptId = script.sourceId,
            runningTitle = script.title,
            message = "Running Lua script",
        )

        val cancelFlag = AtomicBoolean(false)
        val startedAt = System.currentTimeMillis()
        val outcome = suspendCancellableCoroutine<Result<Unit>> { continuation ->
            continuation.invokeOnCancellation { cancelFlag.set(true) }

            val host = object : LuaHostApi {
                private var pinnedAvatarId: String? = null
                private var nextChatboxAt = 0L
                private var nextParameterAt = 0L

                private fun checkCancelled() {
                    if (cancelFlag.get()) throw ScriptStopped("Script stopped")
                }

                private fun sleepUntil(target: Long) {
                    while (System.currentTimeMillis() < target) {
                        checkCancelled()
                        Thread.sleep(SLEEP_SLICE_MS)
                    }
                }

                private fun connectedSnapshot(): ScriptRuntimeSnapshot {
                    checkCancelled()
                    val current = runtimeSnapshot()
                    if (!current.connected) {
                        throw ScriptStopped("VRChat disconnected, so the script was stopped.")
                    }
                    return current
                }

                override fun chatbox(text: String) {
                    val clean = CompanionScriptPolicy.validateLuaChatbox(text).getOrThrow()
                    sleepUntil(nextChatboxAt)
                    connectedSnapshot()
                    val sent = runBlockingHost { sendSilentChatbox(clean) }
                    check(sent) { "VRChat disconnected before the chatbox line could be sent." }
                    nextChatboxAt = System.currentTimeMillis() + CHATBOX_ACTION_INTERVAL_MS
                }

                override fun setParameter(name: String, rawValue: String) {
                    val step = CompanionScriptPolicy.validateLuaParameter(name, rawValue).getOrThrow()
                    sleepUntil(nextParameterAt)
                    val current = connectedSnapshot()
                    val avatarId = current.avatarId
                    check(!avatarId.isNullOrBlank()) { "Wait for VRChat to report the current avatar." }
                    val pinned = pinnedAvatarId ?: avatarId.also { pinnedAvatarId = it }
                    if (avatarId != pinned) {
                        throw ScriptStopped("The avatar changed, so the script was stopped before sending more values.")
                    }
                    val (resolvedName, value) = CompanionScriptPolicy
                        .resolveParameter(step, current.parameters)
                        .getOrThrow()
                    check(setAvatarParameter(pinned, resolvedName, value)) {
                        "The avatar or connection changed before the parameter could be sent."
                    }
                    nextParameterAt = System.currentTimeMillis() + PARAMETER_ACTION_INTERVAL_MS
                }

                override fun sleep(ms: Long) {
                    check(ms in 0..MAX_LUA_WAIT_MS) { "vrc.wait can pause for at most $MAX_LUA_WAIT_MS ms per call." }
                    sleepUntil(System.currentTimeMillis() + ms)
                }

                override fun log(line: String) {
                    checkCancelled()
                    val clean = line.replace('\n', ' ').take(MAX_LOG_CHARS)
                    _state.value = _state.value.copy(message = clean.ifBlank { "Running Lua script" })
                }

                override fun elapsedMs(): Long = System.currentTimeMillis() - startedAt
            }

            val worker = Thread({
                val result = LuaSandbox(host, cancelled = { cancelFlag.get() }).run(source)
                if (continuation.isActive) continuation.resume(result)
            }, "companion-lua-script")
            worker.isDaemon = true
            worker.start()
        }
        outcome.getOrThrow()
    }

    /** Bridges the Lua worker thread into the app's suspend send functions. */
    private fun <T> runBlockingHost(block: suspend () -> T): T =
        kotlinx.coroutines.runBlocking { block() }

    companion object {
        const val CHATBOX_ACTION_INTERVAL_MS = 1_600L
        const val PARAMETER_ACTION_INTERVAL_MS = 100L
        const val MAX_RUN_DURATION_MS = 120_000L
        const val MAX_LUA_WAIT_MS = 10_000L
        private const val SLEEP_SLICE_MS = 25L
        private const val MAX_LOG_CHARS = 200
    }
}