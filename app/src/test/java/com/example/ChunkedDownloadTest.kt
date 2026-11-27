package com.example

import com.example.feature.downloads.engine.ChunkedDownloadEngine
import com.example.feature.downloads.engine.DownloadFolderHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChunkedDownloadTest {

    @Test
    fun `planChunks splits large file into contiguous non-overlapping ranges`() {
        val total = 10L * 1024L * 1024L + 137L // ~10 MB non-aligned
        val chunks = ChunkedDownloadEngine.planChunks(total, requestedChunks = 4)

        assertEquals(4, chunks.size)
        assertEquals(0L, chunks.first().startByte)
        assertEquals(total - 1L, chunks.last().endByte)

        for (i in 0 until chunks.lastIndex) {
            assertEquals(chunks[i].endByte + 1L, chunks[i + 1].startByte)
        }
        assertEquals(total, chunks.sumOf { it.totalLength })
    }

    @Test
    fun `planChunks uses single chunk below threshold or for 1 chunk`() {
        val small = 128L * 1024L
        val singleSmall = ChunkedDownloadEngine.planChunks(small, requestedChunks = 8)
        assertEquals(1, singleSmall.size)
        assertEquals(small, singleSmall.first().totalLength)

        val large = 4L * 1024L * 1024L
        val forcedSingle = ChunkedDownloadEngine.planChunks(large, requestedChunks = 1)
        assertEquals(1, forcedSingle.size)
        assertEquals(large, forcedSingle.first().totalLength)
    }

    @Test
    fun `planChunks caps requested chunks to 8`() {
        val large = 20L * 1024L * 1024L
        val capped = ChunkedDownloadEngine.planChunks(large, requestedChunks = 32)
        assertEquals(8, capped.size)
        assertEquals(large, capped.sumOf { it.totalLength })
    }

    @Test
    fun `sanitizeFileName strips illegal path characters cleanly`() {
        val sanitized = DownloadFolderHelper.sanitizeFileName("report:2026/final*?name.pdf")
        assertTrue(!sanitized.contains("/"))
        assertTrue(!sanitized.contains(":"))
        assertTrue(!sanitized.contains("*"))
        assertTrue(!sanitized.contains("?"))
        assertTrue(sanitized.endsWith(".pdf"))
    }
}
