package com.vrchatlegends.osccompanion.scripts

import android.content.Context
import com.vrchatlegends.osccompanion.vrcl.VrclScript
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

data class ScriptLibraryState(
    val scripts: List<InstalledCompanionScript> = emptyList(),
    val rejectedFiles: Int = 0,
    val loaded: Boolean = false,
)

@OptIn(ExperimentalSerializationApi::class)
class CompanionScriptStore private constructor(private val directory: File) {
    private val mutex = Mutex()
    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = false
        isLenient = false
        coerceInputValues = false
        allowSpecialFloatingPointValues = false
    }

    private val _state = MutableStateFlow(ScriptLibraryState())
    val state: StateFlow<ScriptLibraryState> = _state.asStateFlow()

    init {
        require(directory.exists() || directory.mkdirs()) { "Could not create the private Scripts folder." }
        require(directory.isDirectory) { "The private Scripts path is not a folder." }
        require(!Files.isSymbolicLink(directory.toPath())) { "The private Scripts folder cannot be a link." }
    }

    suspend fun reload(): ScriptLibraryState = withContext(Dispatchers.IO) {
        mutex.withLock { loadLocked().also { _state.value = it } }
    }

    suspend fun install(remote: VrclScript): Result<InstalledCompanionScript> = withContext(Dispatchers.IO) {
        runCatching {
            mutex.withLock {
                val installed = CompanionScriptPolicy.fromRemote(remote).getOrThrow()
                val target = fileFor(installed.sourceId)
                val existingCount = safeFiles().count(::hasCanonicalScriptName)
                require(target.exists() || existingCount < CompanionScriptPolicy.MAX_INSTALLED_SCRIPTS) {
                    "The private Scripts folder is full. Remove a script before installing another."
                }

                val encoded = json.encodeToString(installed).toByteArray(Charsets.UTF_8)
                require(encoded.size <= CompanionScriptPolicy.MAX_FILE_BYTES) {
                    "That script is too large to store safely."
                }
                atomicWrite(target, encoded)
                _state.value = loadLocked()
                installed
            }
        }
    }

    suspend fun remove(sourceId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            mutex.withLock {
                val target = fileFor(sourceId)
                if (target.exists()) {
                    require(target.isFile && !Files.isSymbolicLink(target.toPath())) {
                        "The installed script path is not a regular file."
                    }
                    require(target.delete()) { "Could not remove that script." }
                }
                _state.value = loadLocked()
            }
        }
    }

    /** Reads and validates the file again immediately before each run. */
    suspend fun readForRun(sourceId: String): Result<InstalledCompanionScript> = withContext(Dispatchers.IO) {
        runCatching {
            mutex.withLock {
                readValidated(fileFor(sourceId)) ?: error("That installed script is missing or no longer safe.")
            }
        }
    }

    private fun loadLocked(): ScriptLibraryState {
        var rejected = 0
        val scripts = safeFiles()
            .filter { it.extension == FILE_EXTENSION }
            .mapNotNull { file ->
                readValidated(file).also { if (it == null) rejected += 1 }
            }
            .sortedByDescending { it.installedAtMs }
            .take(CompanionScriptPolicy.MAX_INSTALLED_SCRIPTS)
        return ScriptLibraryState(scripts = scripts, rejectedFiles = rejected, loaded = true)
    }

    private fun readValidated(file: File): InstalledCompanionScript? = runCatching {
        require(file.isFile && !Files.isSymbolicLink(file.toPath()))
        require(file.length() in 1..CompanionScriptPolicy.MAX_FILE_BYTES)
        require(file.canonicalFile.parentFile == directory.canonicalFile)
        val raw = file.inputStream().buffered().use { input ->
            val limit = CompanionScriptPolicy.MAX_FILE_BYTES.toInt()
            val buffer = ByteArray(limit + 1)
            var count = 0
            while (count < buffer.size) {
                val read = input.read(buffer, count, buffer.size - count)
                if (read < 0) break
                count += read
            }
            require(count <= limit)
            buffer.copyOf(count).toString(Charsets.UTF_8)
        }
        val decoded = json.decodeFromString<InstalledCompanionScript>(raw)
        require(file.name == fileName(decoded.sourceId))
        CompanionScriptPolicy.validateStored(decoded).getOrThrow()
    }.getOrNull()

    private fun safeFiles(): List<File> = directory.listFiles()?.toList().orEmpty()

    private fun hasCanonicalScriptName(file: File): Boolean =
        file.isFile && !Files.isSymbolicLink(file.toPath()) &&
            file.extension == FILE_EXTENSION && SAFE_ID.matches(file.nameWithoutExtension)

    private fun fileFor(sourceId: String): File {
        val validatedId = sourceId.takeIf { SAFE_ID.matches(it) } ?: error("The script ID is invalid.")
        val file = File(directory, fileName(validatedId))
        require(file.canonicalFile.parentFile == directory.canonicalFile) { "The script path is invalid." }
        return file
    }

    private fun atomicWrite(target: File, bytes: ByteArray) {
        val temporary = File(directory, ".${target.name}.${System.nanoTime()}.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            runCatching {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }.getOrElse {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
        } finally {
            temporary.delete()
        }
    }

    companion object {
        const val FOLDER_NAME = "scripts"
        private const val FILE_EXTENSION = "json"
        private val SAFE_ID = Regex("^[A-Za-z0-9_-]{1,128}$")

        fun create(context: Context): CompanionScriptStore =
            CompanionScriptStore(File(context.filesDir, FOLDER_NAME))

        internal fun createForTests(directory: File): CompanionScriptStore = CompanionScriptStore(directory)

        private fun fileName(sourceId: String) = "$sourceId.$FILE_EXTENSION"
    }
}