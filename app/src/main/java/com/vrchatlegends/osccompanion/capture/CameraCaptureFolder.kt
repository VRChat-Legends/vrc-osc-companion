package com.vrchatlegends.osccompanion.capture

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class CameraCaptureFolder(private val context: Context) {

    suspend fun scan(treeUri: String): Result<List<CameraCaptureCandidate>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val root = DocumentFile.fromTreeUri(context, Uri.parse(treeUri))
                    ?: error("The selected capture folder is no longer available.")
                check(root.canRead()) { "The selected capture folder cannot be read." }

                val found = mutableListOf<CameraCaptureCandidate>()
                val pending = ArrayDeque<Pair<DocumentFile, Int>>()
                pending.add(root to 0)
                var visited = 0

                while (pending.isNotEmpty()) {
                    val (directory, depth) = pending.removeFirst()
                    for (entry in directory.listFiles()) {
                        visited += 1
                        check(visited <= MAX_ENTRIES) {
                            "The selected folder is too large. Choose the VRChat Captures folder directly."
                        }
                        if (entry.isDirectory && depth < MAX_DEPTH) {
                            pending.add(entry to depth + 1)
                            continue
                        }
                        if (!entry.isFile) continue
                        val modified = entry.lastModified()
                        val size = entry.length()
                        val mime = entry.type.orEmpty().lowercase()
                        val candidate = CameraCaptureCandidate(
                            uri = entry.uri.toString(),
                            displayName = entry.name ?: "VRChat capture",
                            mimeType = mime,
                            sizeBytes = size,
                            modifiedAtMs = modified,
                            fingerprint = "$modified:$size:${entry.uri}",
                        )
                        if (CameraCapturePolicy.isCameraImage(candidate)) found += candidate
                    }
                }
                found
            }
        }

    suspend fun read(candidate: CameraCaptureCandidate): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            runCatching {
                check(CameraCapturePolicy.canUpload(candidate)) {
                    "Camera captures must be 8 MB or smaller."
                }
                val output = ByteArrayOutputStream(candidate.sizeBytes.toInt().coerceAtLeast(1024))
                context.contentResolver.openInputStream(Uri.parse(candidate.uri))?.use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        check(total <= CameraCapturePolicy.MAX_UPLOAD_BYTES) {
                            "Camera capture grew beyond the 8 MB limit while it was being read."
                        }
                        output.write(buffer, 0, read)
                    }
                } ?: error("The newest camera capture could not be opened.")
                output.toByteArray().also { bytes ->
                    check(bytes.size.toLong() == candidate.sizeBytes) {
                        "The camera capture is still being written."
                    }
                }
            }
        }

    private companion object {
        const val MAX_ENTRIES = 20_000
        const val MAX_DEPTH = 8
    }
}