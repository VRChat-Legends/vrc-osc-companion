package com.vrchatlegends.osccompanion.diag

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps the last fatal stack trace on disk.
 *
 * A Quest panel closes instantly when it crashes and reading logcat needs a cable plus an
 * adb grant, so without this there is no way for a user to report what actually happened.
 */
object CrashReporter {

    private const val FILE_NAME = "last-crash.txt"
    private const val MAX_CHARS = 8_000

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val text = buildString {
                    appendLine("$stamp on thread ${thread.name}")
                    append(Log.getStackTraceString(error))
                }
                File(appContext.filesDir, FILE_NAME).writeText(text.take(MAX_CHARS))
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun lastCrash(context: Context): String? = runCatching {
        File(context.filesDir, FILE_NAME).takeIf { it.isFile }?.readText()?.takeIf { it.isNotBlank() }
    }.getOrNull()

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE_NAME).delete() }
    }
}
