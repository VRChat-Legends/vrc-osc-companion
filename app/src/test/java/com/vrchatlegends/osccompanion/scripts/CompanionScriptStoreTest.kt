package com.vrchatlegends.osccompanion.scripts

import com.vrchatlegends.osccompanion.vrcl.VrclScript
import com.vrchatlegends.osccompanion.vrcl.VrclScriptStep
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CompanionScriptStoreTest {
    private lateinit var root: java.io.File
    private lateinit var store: CompanionScriptStore

    @Before
    fun setUp() {
        root = Files.createTempDirectory("companion-script-store").toFile()
        store = CompanionScriptStore.createForTests(java.io.File(root, "scripts"))
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `creates a private folder and atomically installs valid JSON`() = runBlocking {
        val result = store.install(script("safe_one"))

        assertTrue(result.isSuccess)
        assertTrue(java.io.File(root, "scripts/safe_one.json").isFile)
        assertFalse(java.io.File(root, "scripts").listFiles().orEmpty().any { it.extension == "tmp" })
        assertEquals(listOf("safe_one"), store.reload().scripts.map { it.sourceId })
    }

    @Test
    fun `corrupt unknown and oversized files fail closed`() = runBlocking {
        val folder = java.io.File(root, "scripts")
        java.io.File(folder, "corrupt.json").writeText("not json")
        java.io.File(folder, "unknown.json").writeText(
            """{"schemaVersion":1,"sourceId":"unknown","title":"x","steps":[],"authorName":"x","installedAtMs":1,"extra":"blocked"}""",
        )
        java.io.File(folder, "oversized.json").writeBytes(
            ByteArray((CompanionScriptPolicy.MAX_FILE_BYTES + 1).toInt()) { 'x'.code.toByte() },
        )

        val state = store.reload()

        assertTrue(state.scripts.isEmpty())
        assertEquals(3, state.rejectedFiles)
    }

    @Test
    fun `file changed after install is rejected before run`() = runBlocking {
        assertTrue(store.install(script("changed")).isSuccess)
        java.io.File(root, "scripts/changed.json").writeText(
            """{"schemaVersion":1,"sourceId":"changed","title":"bad","steps":[{"type":"shell","text":"id"}],"authorName":"x","installedAtMs":1}""",
        )

        assertTrue(store.readForRun("changed").isFailure)
    }

    @Test
    fun `path traversal IDs never leave the folder`() = runBlocking {
        val result = store.install(script("../escape"))

        assertTrue(result.isFailure)
        assertFalse(java.io.File(root, "escape.json").exists())
    }

    @Test
    fun `symbolic links are not loaded when the filesystem supports them`() = runBlocking {
        val outside = java.io.File(root, "outside.json")
        outside.writeText("{}")
        val link = java.io.File(root, "scripts/link.json").toPath()
        val linked = runCatching { Files.createSymbolicLink(link, outside.toPath()) }.isSuccess

        if (linked) {
            val state = store.reload()
            assertTrue(state.scripts.isEmpty())
            assertEquals(1, state.rejectedFiles)
        }
    }

    @Test
    fun `junk JSON names cannot consume install capacity`() = runBlocking {
        val folder = java.io.File(root, "scripts")
        repeat(CompanionScriptPolicy.MAX_INSTALLED_SCRIPTS) { index ->
            java.io.File(folder, "junk.$index.json").writeText("not json")
        }

        assertTrue(store.install(script("still_safe")).isSuccess)
    }

    @Test
    fun `symlinked root folder is rejected when the filesystem supports it`() {
        val target = java.io.File(root, "target").apply { mkdirs() }
        val link = java.io.File(root, "linked-scripts").toPath()
        val linked = runCatching { Files.createSymbolicLink(link, target.toPath()) }.isSuccess

        if (linked) {
            assertTrue(runCatching { CompanionScriptStore.createForTests(link.toFile()) }.isFailure)
        }
    }

    private fun script(id: String) = VrclScript(
        id = id,
        title = "Safe preset",
        summary = null,
        tags = emptyList(),
        steps = listOf(VrclScriptStep(type = "chatbox", text = "Hello")),
        authorName = "Legend",
        authorAvatarUrl = "https://example.com/avatar.png",
        installs = 0,
        likeCount = 0,
        viewerLiked = false,
        canEdit = false,
    )
}