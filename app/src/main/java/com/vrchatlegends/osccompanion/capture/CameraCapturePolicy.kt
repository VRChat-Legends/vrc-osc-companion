package com.vrchatlegends.osccompanion.capture

data class CameraCaptureCandidate(
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val modifiedAtMs: Long,
    val fingerprint: String,
)

object CameraCapturePolicy {
    const val MAX_UPLOAD_BYTES = 8L * 1024 * 1024

    private val acceptedMimeTypes = setOf("image/jpeg", "image/png", "image/webp")

    fun isCameraImage(candidate: CameraCaptureCandidate): Boolean =
        candidate.mimeType.lowercase() in acceptedMimeTypes && candidate.modifiedAtMs > 0L

    fun newestAfter(
        candidates: Iterable<CameraCaptureCandidate>,
        checkpointModifiedAtMs: Long,
    ): CameraCaptureCandidate? = candidates
        .asSequence()
        .filter(::isCameraImage)
        .filter { it.modifiedAtMs > checkpointModifiedAtMs }
        .maxWithOrNull(compareBy<CameraCaptureCandidate> { it.modifiedAtMs }.thenBy { it.fingerprint })

    fun canUpload(candidate: CameraCaptureCandidate): Boolean =
        candidate.sizeBytes in 1..MAX_UPLOAD_BYTES

    fun baselineModifiedAt(
        candidates: Iterable<CameraCaptureCandidate>,
        enabledAtMs: Long,
    ): Long = maxOf(
        enabledAtMs,
        candidates.asSequence()
            .filter(::isCameraImage)
            .maxOfOrNull { it.modifiedAtMs }
            ?: 0L,
    )
}