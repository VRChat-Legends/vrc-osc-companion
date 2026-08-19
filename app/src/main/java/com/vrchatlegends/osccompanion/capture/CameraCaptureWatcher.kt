package com.vrchatlegends.osccompanion.capture

import android.content.Context
import com.vrchatlegends.osccompanion.data.SettingsStore
import com.vrchatlegends.osccompanion.vrcl.CaptureUploadException
import com.vrchatlegends.osccompanion.vrcl.VrclClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.min

data class CameraCaptureState(
    val watching: Boolean = false,
    val uploading: Boolean = false,
    val lastSentName: String? = null,
    val lastSentAtMs: Long? = null,
    val message: String? = null,
)

class CameraCaptureWatcher private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val settingsStore = SettingsStore(appContext)
    private val folder = CameraCaptureFolder(appContext)
    private var currentToken = ""
    private val client = VrclClient { currentToken.takeIf(String::isNotBlank) }

    private val _state = MutableStateFlow(CameraCaptureState())
    val state: StateFlow<CameraCaptureState> = _state.asStateFlow()

    private var job: Job? = null

    @Synchronized
    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch { watch() }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
        _state.value = _state.value.copy(watching = false, uploading = false)
    }

    private suspend fun watch() {
        var observedFingerprint: String? = null
        var retryDelayMs = POLL_INTERVAL_MS

        while (kotlin.coroutines.coroutineContext.isActive) {
            val settings = settingsStore.settings.first()
            currentToken = settings.vrclToken
            if (!settings.captureAutoSend ||
                settings.captureFolderUri.isBlank() ||
                settings.vrclToken.isBlank()
            ) {
                observedFingerprint = null
                _state.value = _state.value.copy(watching = false, uploading = false)
                delay(POLL_INTERVAL_MS)
                continue
            }

            _state.value = _state.value.copy(watching = true, uploading = false)
            val scanResult = folder.scan(settings.captureFolderUri)
            if (scanResult.isFailure) {
                val error = scanResult.exceptionOrNull()
                _state.value = _state.value.copy(message = error?.message ?: "Could not scan the capture folder.")
                delay(RETRY_MIN_MS)
                continue
            }
            val candidates = scanResult.getOrThrow()
            val newest = CameraCapturePolicy.newestAfter(
                candidates,
                settings.captureCheckpointModifiedAtMs,
            )
            if (newest == null) {
                observedFingerprint = null
                retryDelayMs = POLL_INTERVAL_MS
                delay(POLL_INTERVAL_MS)
                continue
            }

            if (observedFingerprint != newest.fingerprint) {
                observedFingerprint = newest.fingerprint
                delay(POLL_INTERVAL_MS)
                continue
            }

            if (!CameraCapturePolicy.canUpload(newest)) {
                settingsStore.markCaptureProcessed(newest.modifiedAtMs, newest.fingerprint)
                observedFingerprint = null
                _state.value = _state.value.copy(
                    message = "${newest.displayName} was not sent because it is larger than 8 MB.",
                )
                continue
            }

            val readResult = folder.read(newest)
            if (readResult.isFailure) {
                val error = readResult.exceptionOrNull()
                observedFingerprint = null
                _state.value = _state.value.copy(message = error?.message ?: "Could not read the newest capture.")
                delay(POLL_INTERVAL_MS)
                continue
            }
            val bytes = readResult.getOrThrow()

            _state.value = _state.value.copy(uploading = true, message = null)
            client.uploadCameraCapture(bytes, newest.mimeType)
                .onSuccess {
                    settingsStore.markCaptureProcessed(newest.modifiedAtMs, newest.fingerprint)
                    observedFingerprint = null
                    retryDelayMs = POLL_INTERVAL_MS
                    _state.value = CameraCaptureState(
                        watching = true,
                        lastSentName = newest.displayName,
                        lastSentAtMs = System.currentTimeMillis(),
                        message = "Sent ${newest.displayName} to Discord.",
                    )
                }
                .onFailure { error ->
                    val http = error as? CaptureUploadException
                    val permanent = http != null && http.statusCode in 400..499 &&
                        http.statusCode !in setOf(408, 409, 429)
                    if (permanent) {
                        settingsStore.markCaptureProcessed(newest.modifiedAtMs, newest.fingerprint)
                        observedFingerprint = null
                    }
                    retryDelayMs = if (permanent) POLL_INTERVAL_MS
                    else min((retryDelayMs * 2).coerceAtLeast(RETRY_MIN_MS), RETRY_MAX_MS)
                    _state.value = _state.value.copy(
                        uploading = false,
                        message = error.message ?: "Discord delivery failed. The app will retry.",
                    )
                    delay(retryDelayMs)
                }
        }
    }

    companion object {
        private const val POLL_INTERVAL_MS = 5_000L
        private const val RETRY_MIN_MS = 15_000L
        private const val RETRY_MAX_MS = 5 * 60_000L

        @Volatile
        private var instance: CameraCaptureWatcher? = null

        fun get(context: Context): CameraCaptureWatcher = instance ?: synchronized(this) {
            instance ?: CameraCaptureWatcher(context).also { instance = it }
        }
    }
}