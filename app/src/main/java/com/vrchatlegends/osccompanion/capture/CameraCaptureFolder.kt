package com.vrchatlegends.osccompanion.capture

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

class CameraCaptureFolder(private val context: Context) {

    /**
     * Lists every camera image under the source. [source] is either [AUTO_SOURCE]
     * (direct file access to Pictures/VRChat) or a persisted SAF tree URI.
     */
    suspend fun scan(source: String): Result<List<CameraCaptureCandidate>> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (source == AUTO_SOURCE) scanFiles() else scanTree(source)
            }
        }

    /** Walks the public VRChat pictures folder with plain file IO. */
    private fun scanFiles(): List<CameraCaptureCandidate> {
        val root = autoFolder()
            ?: error(
                "No Pictures/VRChat folder was found on this headset yet. " +
                    "Take one photo with the VRChat camera, then try again.",
            )
        val found = mutableListOf<CameraCaptureCandidate>()
        val pending = ArrayDeque<Pair<File, Int>>()
        pending.add(root to 0)
        var visited = 0

        while (pending.isNotEmpty()) {
            val (directory, depth) = pending.removeFirst()
            for (entry in directory.listFiles().orEmpty()) {
                visited += 1
                check(visited <= MAX_ENTRIES) { TOO_LARGE_MESSAGE }
                if (entry.isDirectory) {
                    if (depth < MAX_DEPTH) pending.add(entry to depth + 1)
                    continue
                }
                if (!entry.isFile) continue
                val modified = entry.lastModified()
                val size = entry.length()
                val candidate = CameraCaptureCandidate(
                    uri = Uri.fromFile(entry).toString(),
                    displayName = entry.name,
                    mimeType = mimeFromName(entry.name),
                    sizeBytes = size,
                    modifiedAtMs = modified,
                    fingerprint = "$modified:$size:${entry.absolutePath}",
                )
                if (CameraCapturePolicy.isCameraImage(candidate)) found += candidate
            }
        }
        return found
    }

    /**
     * Walks a SAF tree with one ContentResolver query per directory. The old
     * DocumentFile walk made about five binder calls per file, which turned a
     * folder of a few hundred photos into minutes of "Checking...".
     */
    private fun scanTree(treeUri: String): List<CameraCaptureCandidate> {
        val tree = Uri.parse(treeUri)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        val found = mutableListOf<CameraCaptureCandidate>()
        val pending = ArrayDeque<Pair<String, Int>>()
        pending.add(DocumentsContract.getTreeDocumentId(tree) to 0)
        var visited = 0

        while (pending.isNotEmpty()) {
            val (documentId, depth) = pending.removeFirst()
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId)
            val cursor = context.contentResolver.query(children, projection, null, null, null)
                ?: error("The selected capture folder is no longer available.")
            cursor.use {
                while (it.moveToNext()) {
                    visited += 1
                    check(visited <= MAX_ENTRIES) { TOO_LARGE_MESSAGE }
                    val childId = it.getString(0) ?: continue
                    val mime = it.getString(2).orEmpty().lowercase()
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (depth < MAX_DEPTH) pending.add(childId to depth + 1)
                        continue
                    }
                    val modified = if (it.isNull(4)) 0L else it.getLong(4)
                    val size = if (it.isNull(3)) 0L else it.getLong(3)
                    val childUri = DocumentsContract.buildDocumentUriUsingTree(tree, childId)
                    val candidate = CameraCaptureCandidate(
                        uri = childUri.toString(),
                        displayName = it.getString(1) ?: "VRChat capture",
                        mimeType = mime,
                        sizeBytes = size,
                        modifiedAtMs = modified,
                        fingerprint = "$modified:$size:$childUri",
                    )
                    if (CameraCapturePolicy.isCameraImage(candidate)) found += candidate
                }
            }
        }
        return found
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

    companion object {
        /** Stored in place of a tree URI when the folder was found automatically. */
        const val AUTO_SOURCE = "auto"

        private const val MAX_ENTRIES = 20_000
        private const val MAX_DEPTH = 8
        private const val TOO_LARGE_MESSAGE =
            "The selected folder is too large. Choose the VRChat photos folder directly."

        /** The permission direct file access needs on this Android version. */
        fun readPermission(): String =
            if (Build.VERSION.SDK_INT >= 33) {
                "android.permission.READ_MEDIA_IMAGES"
            } else {
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            }

        /** Finds the VRChat photos folder on shared storage, or null when absent. */
        fun autoFolder(): File? = runCatching {
            sequenceOf(
                File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "VRChat",
                ),
                @Suppress("DEPRECATION")
                File(Environment.getExternalStorageDirectory(), "Pictures/VRChat"),
            ).firstOrNull { it.isDirectory }
        }.getOrNull()

        private fun mimeFromName(name: String): String =
            when (name.substringAfterLast('.', "").lowercase()) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "webp" -> "image/webp"
                else -> ""
            }
    }
}