package com.vrchatlegends.osccompanion.logs

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile

/**
 * Reads VRChat's own Unity log off the headset.
 *
 * VRChat writes to its private external directory, and Android 11 upward walls that off
 * from other apps, so there are three routes and the reader tries them in order of
 * convenience:
 *
 *  1. Direct [File] access to a known path. Works on Horizon OS builds that still allow it
 *     once All files access is granted.
 *  2. A folder the user picked through the storage access framework, whose permission we
 *     persist so it survives a restart.
 *  3. Nothing, in which case the UI explains that Developer Mode plus `adb logcat` is the
 *     guaranteed fallback.
 *
 * VRChat's Android package id is probed rather than hardcoded because it has differed
 * between store builds.
 */
object VrcLogReader {

    /** Candidate VRChat Android application ids, most likely first. */
    val CANDIDATE_PACKAGES = listOf(
        "com.vrchat.mobile.playstore",
        "com.vrchat.mobile.oculus",
        "com.vrchat.mobile.quest",
        "com.vrchat.vrcquest",
    )

    private const val LOG_NAME_PREFIX = "output_log"
    private const val MAX_TAIL_BYTES = 512L * 1024L

    data class LogSource(
        val displayName: String,
        val sizeBytes: Long,
        val lastModifiedMs: Long,
        /** Exactly one of these is set. */
        val file: File? = null,
        val documentUri: Uri? = null,
    ) {
        val key: String get() = documentUri?.toString() ?: file?.absolutePath ?: displayName
    }

    enum class Level { LOG, WARNING, ERROR, EXCEPTION, OTHER }

    data class LogLine(
        val raw: String,
        val timestamp: String?,
        val level: Level,
        val message: String,
    )

    /** Notable things pulled out of the stream so the tab is useful without reading raw text. */
    data class SessionEvent(
        val timestamp: String?,
        val kind: Kind,
        val detail: String,
    ) {
        enum class Kind { WORLD, PLAYER_JOIN, PLAYER_LEAVE, OSC, ERROR }
    }

    // ── Discovery ───────────────────────────────────────────────────────────────

    fun hasAllFilesAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true

    fun allFilesAccessIntent(context: Context): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(
                android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            )
        } else {
            null
        }

    /** The directories VRChat is known to log into, whether or not they currently exist. */
    fun candidateDirectories(): List<File> {
        val external = Environment.getExternalStorageDirectory()
        return CANDIDATE_PACKAGES.map { File(external, "Android/data/$it/files") }
    }

    suspend fun findDirectLogs(): List<LogSource> = withContext(Dispatchers.IO) {
        candidateDirectories()
            .filter { runCatching { it.isDirectory }.getOrDefault(false) }
            .flatMap { dir ->
                runCatching { dir.listFiles()?.toList().orEmpty() }.getOrDefault(emptyList())
            }
            .filter { it.isFile && it.name.startsWith(LOG_NAME_PREFIX) }
            .map { LogSource(it.name, it.length(), it.lastModified(), file = it) }
            .sortedByDescending { it.lastModifiedMs }
    }

    fun openTreeIntent(): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }

    fun persistTreePermission(context: Context, treeUri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    /** True when the persisted grant is still valid, so we do not prompt on every launch. */
    fun hasTreePermission(context: Context, treeUri: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any {
            it.uri == treeUri && it.isReadPermission
        }

    suspend fun findTreeLogs(context: Context, treeUri: Uri): List<LogSource> =
        withContext(Dispatchers.IO) {
            runCatching {
                val rootId = DocumentsContract.getTreeDocumentId(treeUri)
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, rootId)
                val projection = arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                    DocumentsContract.Document.COLUMN_SIZE,
                )
                val found = mutableListOf<LogSource>()
                context.contentResolver.query(childrenUri, projection, null, null, null)?.use { c ->
                    while (c.moveToNext()) {
                        val name = c.getString(1) ?: continue
                        if (!name.startsWith(LOG_NAME_PREFIX) && !name.endsWith(".txt")) continue
                        val docId = c.getString(0) ?: continue
                        found += LogSource(
                            displayName = name,
                            sizeBytes = c.getLong(3),
                            lastModifiedMs = c.getLong(2),
                            documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId),
                        )
                    }
                }
                found.sortedByDescending { it.lastModifiedMs }
            }.getOrElse { emptyList() }
        }

    // ── Reading ─────────────────────────────────────────────────────────────────

    /**
     * Reads at most the trailing [maxBytes] of a source. Seeking past the start means the
     * first line is usually a fragment, so it is discarded.
     *
     * Returns the parsed lines plus the byte offset that was read up to, so a follow up
     * call can fetch only what is new.
     */
    suspend fun readTail(
        context: Context,
        source: LogSource,
        fromOffset: Long = -1L,
        maxBytes: Long = MAX_TAIL_BYTES,
    ): Pair<List<LogLine>, Long> = withContext(Dispatchers.IO) {
        runCatching {
            val length = currentLength(context, source)
            if (length <= 0L) return@runCatching emptyList<LogLine>() to 0L

            // A rotated or truncated file resets the cursor rather than reading garbage.
            val start = when {
                fromOffset in 0..length -> fromOffset
                else -> (length - maxBytes).coerceAtLeast(0L)
            }
            if (start >= length) return@runCatching emptyList<LogLine>() to length

            val bytes = readRange(context, source, start, length - start)
            var text = String(bytes, Charsets.UTF_8)
            if (start > 0) text = text.substringAfter('\n', "")

            val lines = text.lineSequence()
                .filter { it.isNotBlank() }
                .map(::parseLine)
                .toList()
            lines to length
        }.getOrElse { emptyList<LogLine>() to fromOffset.coerceAtLeast(0L) }
    }

    private fun currentLength(context: Context, source: LogSource): Long {
        source.file?.let { return it.length() }
        val uri = source.documentUri ?: return 0L
        return context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
    }

    private fun readRange(context: Context, source: LogSource, start: Long, count: Long): ByteArray {
        val size = count.coerceAtMost(MAX_TAIL_BYTES * 8).toInt()
        source.file?.let { f ->
            RandomAccessFile(f, "r").use { raf ->
                raf.seek(start)
                val buffer = ByteArray(size)
                val read = raf.read(buffer)
                return if (read <= 0) ByteArray(0) else buffer.copyOf(read)
            }
        }
        val uri = source.documentUri ?: return ByteArray(0)
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            FileInputStream(pfd.fileDescriptor).use { stream ->
                stream.channel.position(start)
                val buffer = ByteArray(size)
                var total = 0
                while (total < size) {
                    val read = stream.read(buffer, total, size - total)
                    if (read <= 0) break
                    total += read
                }
                return buffer.copyOf(total)
            }
        }
        return ByteArray(0)
    }

    // ── Parsing ─────────────────────────────────────────────────────────────────

    /**
     * VRChat lines look like `2024.01.01 12:00:00 Log        -  [Behaviour] text`.
     * Continuation lines such as stack traces have no header and are kept verbatim.
     */
    fun parseLine(raw: String): LogLine {
        val match = HEADER.find(raw)
            ?: return LogLine(raw, null, Level.OTHER, raw.trim())
        val timestamp = match.groupValues[1]
        val level = when (match.groupValues[2].trim().lowercase()) {
            "log", "debug" -> Level.LOG
            "warning" -> Level.WARNING
            "error" -> Level.ERROR
            "exception" -> Level.EXCEPTION
            else -> Level.OTHER
        }
        return LogLine(raw, timestamp, level, raw.substring(match.range.last + 1).trim())
    }

    fun extractEvent(line: LogLine): SessionEvent? {
        val text = line.message
        return when {
            text.contains("Joining or Creating Room:") ->
                SessionEvent(line.timestamp, SessionEvent.Kind.WORLD, text.substringAfter("Room:").trim())

            text.contains("Joining wrld_") ->
                SessionEvent(line.timestamp, SessionEvent.Kind.WORLD, text.substringAfter("Joining").trim())

            text.contains("OnPlayerJoined") ->
                SessionEvent(line.timestamp, SessionEvent.Kind.PLAYER_JOIN, text.substringAfter("OnPlayerJoined").trim())

            text.contains("OnPlayerLeft") ->
                SessionEvent(line.timestamp, SessionEvent.Kind.PLAYER_LEAVE, text.substringAfter("OnPlayerLeft").trim())

            text.contains("OSC", ignoreCase = false) && text.contains("port", ignoreCase = true) ->
                SessionEvent(line.timestamp, SessionEvent.Kind.OSC, text)

            line.level == Level.ERROR || line.level == Level.EXCEPTION ->
                SessionEvent(line.timestamp, SessionEvent.Kind.ERROR, text.take(160))

            else -> null
        }
    }

    private val HEADER = Regex("""^(\d{4}\.\d{2}\.\d{2} \d{2}:\d{2}:\d{2})\s+(\w+)\s+-\s+""")
}
