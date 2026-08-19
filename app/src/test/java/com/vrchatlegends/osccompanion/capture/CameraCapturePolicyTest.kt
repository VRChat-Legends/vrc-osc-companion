package com.vrchatlegends.osccompanion.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraCapturePolicyTest {

    @Test
    fun `baseline excludes every image that existed when forwarding was enabled`() {
        val existing = listOf(candidate("old-a", 100), candidate("old-b", 200))
        val baseline = CameraCapturePolicy.baselineModifiedAt(existing, enabledAtMs = 250)

        assertEquals(250, baseline)
        assertNull(CameraCapturePolicy.newestAfter(existing, baseline))
    }

    @Test
    fun `only the newest capture after the checkpoint is selected`() {
        val selected = CameraCapturePolicy.newestAfter(
            listOf(
                candidate("before", 100),
                candidate("newer", 400),
                candidate("new", 300),
            ),
            checkpointModifiedAtMs = 200,
        )

        assertEquals("newer", selected?.displayName)
    }

    @Test
    fun `timestamp ties at the checkpoint fail closed`() {
        assertNull(
            CameraCapturePolicy.newestAfter(
                listOf(candidate("different-file", 200)),
                checkpointModifiedAtMs = 200,
            ),
        )
    }

    @Test
    fun `newest oversized image is not replaced by an older uploadable image`() {
        val selected = CameraCapturePolicy.newestAfter(
            listOf(
                candidate("uploadable", 300, CameraCapturePolicy.MAX_UPLOAD_BYTES),
                candidate("oversized", 400, CameraCapturePolicy.MAX_UPLOAD_BYTES + 1),
            ),
            checkpointModifiedAtMs = 200,
        )

        assertEquals("oversized", selected?.displayName)
        assertFalse(CameraCapturePolicy.canUpload(requireNotNull(selected)))
        assertTrue(CameraCapturePolicy.canUpload(candidate("bounded", 500, 1)))
    }

    private fun candidate(name: String, modifiedAtMs: Long, sizeBytes: Long = 1024) =
        CameraCaptureCandidate(
            uri = "content://captures/$name",
            displayName = name,
            mimeType = "image/png",
            sizeBytes = sizeBytes,
            modifiedAtMs = modifiedAtMs,
            fingerprint = "$modifiedAtMs-$name",
        )
}