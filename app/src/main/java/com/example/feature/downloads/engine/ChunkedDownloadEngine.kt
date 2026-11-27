package com.example.feature.downloads.engine

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

data class ChunkRange(
    val index: Int,
    val startByte: Long,
    val endByte: Long,
    val downloadedBytes: Long = 0L
) {
    val totalLength: Long
        get() = (endByte - startByte + 1L).coerceAtLeast(0L)

    val isComplete: Boolean
        get() = downloadedBytes >= totalLength
}

data class DownloadProgressSnapshot(
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val speedBytesPerSec: Long,
    val chunkCount: Int,
    val supportsRanges: Boolean,
    val mimeType: String,
    val stagingPath: String
)

data class CompletedStagingResult(
    val stagingFile: File,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val chunkCount: Int,
    val supportsRanges: Boolean,
    val mimeType: String
)

/**
 * Multi-chunk HTTP Range download engine with positional lock-free FileChannel writes,
 * rolling download speed calculation, and .meta.json pause/resume checkpointing.
 */
class ChunkedDownloadEngine(
    private val client: OkHttpClient
) {

    suspend fun downloadToStaging(
        context: Context,
        downloadId: Long,
        url: String,
        fileName: String,
        requestedChunks: Int,
        existingStagingPath: String,
        isStopped: () -> Boolean,
        onProgress: suspend (DownloadProgressSnapshot) -> Unit
    ): CompletedStagingResult = withContext(Dispatchers.IO) {
        val stagingDir = File(context.cacheDir, "download_staging").apply { mkdirs() }
        val safeName = DownloadFolderHelper.sanitizeFileName(fileName)
        val stagingFile = if (existingStagingPath.isNotBlank()) {
            File(existingStagingPath)
        } else {
            File(stagingDir, "orbit_${downloadId}_$safeName.part")
        }
        val metaFile = File("${stagingFile.absolutePath}.meta.json")

        val probe = DownloadFolderHelper.probeRemoteFile(client, url)
        val effectiveChunks = if (probe.supportsRanges && probe.totalBytes >= MIN_CHUNKED_SIZE_BYTES) {
            requestedChunks.coerceIn(1, 8)
        } else {
            1
        }

        if (probe.supportsRanges && probe.totalBytes > 0L && effectiveChunks > 1) {
            downloadChunkedParallel(
                url = url,
                totalBytes = probe.totalBytes,
                eTag = probe.eTag,
                mimeType = probe.mimeType,
                requestedChunks = effectiveChunks,
                stagingFile = stagingFile,
                metaFile = metaFile,
                isStopped = isStopped,
                onProgress = onProgress
            )
        } else {
            downloadSingleStream(
                url = url,
                expectedBytes = probe.totalBytes,
                fallbackMime = probe.mimeType,
                stagingFile = stagingFile,
                metaFile = metaFile,
                isStopped = isStopped,
                onProgress = onProgress
            )
        }
    }

    private suspend fun downloadChunkedParallel(
        url: String,
        totalBytes: Long,
        eTag: String?,
        mimeType: String,
        requestedChunks: Int,
        stagingFile: File,
        metaFile: File,
        isStopped: () -> Boolean,
        onProgress: suspend (DownloadProgressSnapshot) -> Unit
    ): CompletedStagingResult = coroutineScope {
        val chunks = loadOrCreateChunks(
            metaFile = metaFile,
            stagingFile = stagingFile,
            url = url,
            totalBytes = totalBytes,
            eTag = eTag,
            requestedChunks = requestedChunks
        )

        val raf = RandomAccessFile(stagingFile, "rw")
        raf.setLength(totalBytes)
        val channel = raf.channel

        val progressArray = Array(chunks.size) { idx -> AtomicLong(chunks[idx].downloadedBytes) }
        val totalDownloaded = AtomicLong(chunks.sumOf { it.downloadedBytes })
        val lastEmitMs = AtomicLong(System.currentTimeMillis())
        val lastWindowBytes = AtomicLong(totalDownloaded.get())
        val speedRef = AtomicLong(0L)
        val progressLock = Any()
        val firstFailure = AtomicReference<Throwable?>(null)

        onProgress(
            DownloadProgressSnapshot(
                bytesDownloaded = totalDownloaded.get(),
                totalBytes = totalBytes,
                speedBytesPerSec = 0L,
                chunkCount = chunks.size,
                supportsRanges = true,
                mimeType = mimeType,
                stagingPath = stagingFile.absolutePath
            )
        )

        try {
            val jobs = chunks.map { chunk ->
                async(Dispatchers.IO) {
                    if (chunk.isComplete) return@async
                    val startOffset = chunk.startByte + chunk.downloadedBytes
                    val request = Request.Builder()
                        .url(url)
                        .header("Range", "bytes=$startOffset-${chunk.endByte}")
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful && response.code != 206) {
                            error("HTTP ${response.code} for chunk ${chunk.index}")
                        }
                        val body = response.body ?: error("Empty response body")
                        val buffer = ByteArray(STREAM_BUFFER_BYTES)
                        var writePosition = startOffset
                        var chunkWritten = chunk.downloadedBytes

                        body.byteStream().use { input ->
                            while (true) {
                                ensureActive()
                                if (isStopped() || firstFailure.get() != null) {
                                    throw CancellationException("Stopped")
                                }
                                val read = input.read(buffer)
                                if (read == -1) break

                                val byteBuffer = ByteBuffer.wrap(buffer, 0, read)
                                while (byteBuffer.hasRemaining()) {
                                    writePosition += channel.write(byteBuffer, writePosition)
                                }
                                chunkWritten += read
                                progressArray[chunk.index].set(chunkWritten)
                                val currentTotal = totalDownloaded.addAndGet(read.toLong())

                                val now = System.currentTimeMillis()
                                val prevMs = lastEmitMs.get()
                                if (now - prevMs >= PROGRESS_INTERVAL_MS && lastEmitMs.compareAndSet(prevMs, now)) {
                                    val elapsedSec = ((now - prevMs).coerceAtLeast(1L)) / 1000.0
                                    val deltaBytes = (currentTotal - lastWindowBytes.getAndSet(currentTotal)).coerceAtLeast(0L)
                                    val instantSpeed = (deltaBytes / elapsedSec).toLong()
                                    speedRef.set(instantSpeed)

                                    val snapshot = synchronized(progressLock) {
                                        val updatedChunks = chunks.mapIndexed { i, c ->
                                            c.copy(downloadedBytes = progressArray[i].get())
                                        }
                                        saveMetadata(metaFile, url, totalBytes, eTag, updatedChunks)
                                        DownloadProgressSnapshot(
                                            bytesDownloaded = currentTotal,
                                            totalBytes = totalBytes,
                                            speedBytesPerSec = instantSpeed,
                                            chunkCount = chunks.size,
                                            supportsRanges = true,
                                            mimeType = mimeType,
                                            stagingPath = stagingFile.absolutePath
                                        )
                                    }
                                    onProgress(snapshot)
                                }
                            }
                        }
                    }
                }
            }
            jobs.awaitAll()
            metaFile.delete()
            CompletedStagingResult(
                stagingFile = stagingFile,
                bytesDownloaded = totalDownloaded.get(),
                totalBytes = totalBytes,
                chunkCount = chunks.size,
                supportsRanges = true,
                mimeType = mimeType
            )
        } catch (ce: CancellationException) {
            val savedChunks = chunks.mapIndexed { i, c -> c.copy(downloadedBytes = progressArray[i].get()) }
            saveMetadata(metaFile, url, totalBytes, eTag, savedChunks)
            throw ce
        } catch (t: Throwable) {
            firstFailure.compareAndSet(null, t)
            val savedChunks = chunks.mapIndexed { i, c -> c.copy(downloadedBytes = progressArray[i].get()) }
            saveMetadata(metaFile, url, totalBytes, eTag, savedChunks)
            throw t
        } finally {
            runCatching { channel.force(false) }
            runCatching { channel.close() }
            runCatching { raf.close() }
        }
    }

    private suspend fun downloadSingleStream(
        url: String,
        expectedBytes: Long,
        fallbackMime: String,
        stagingFile: File,
        metaFile: File,
        isStopped: () -> Boolean,
        onProgress: suspend (DownloadProgressSnapshot) -> Unit
    ): CompletedStagingResult {
        metaFile.delete()
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val body = response.body ?: error("Empty response body")
            val totalBytes = body.contentLength().takeIf { it > 0L } ?: expectedBytes
            val mimeType = body.contentType()?.toString()
                ?.substringBefore(';')
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: fallbackMime

            onProgress(
                DownloadProgressSnapshot(
                    bytesDownloaded = 0L,
                    totalBytes = totalBytes,
                    speedBytesPerSec = 0L,
                    chunkCount = 1,
                    supportsRanges = false,
                    mimeType = mimeType,
                    stagingPath = stagingFile.absolutePath
                )
            )

            val buffer = ByteArray(STREAM_BUFFER_BYTES)
            var downloaded = 0L
            var lastWindowBytes = 0L
            var lastEmitMs = System.currentTimeMillis()

            body.byteStream().use { input ->
                FileOutputStream(stagingFile).use { output ->
                    while (true) {
                        if (isStopped()) {
                            throw CancellationException("Stopped")
                        }
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read

                        val now = System.currentTimeMillis()
                        if (now - lastEmitMs >= PROGRESS_INTERVAL_MS) {
                            val elapsedSec = ((now - lastEmitMs).coerceAtLeast(1L)) / 1000.0
                            val deltaBytes = (downloaded - lastWindowBytes).coerceAtLeast(0L)
                            val speed = (deltaBytes / elapsedSec).toLong()
                            lastWindowBytes = downloaded
                            lastEmitMs = now
                            onProgress(
                                DownloadProgressSnapshot(
                                    bytesDownloaded = downloaded,
                                    totalBytes = totalBytes,
                                    speedBytesPerSec = speed,
                                    chunkCount = 1,
                                    supportsRanges = false,
                                    mimeType = mimeType,
                                    stagingPath = stagingFile.absolutePath
                                )
                            )
                        }
                    }
                    output.flush()
                }
            }

            return CompletedStagingResult(
                stagingFile = stagingFile,
                bytesDownloaded = downloaded,
                totalBytes = if (totalBytes > 0L) totalBytes else downloaded,
                chunkCount = 1,
                supportsRanges = false,
                mimeType = mimeType
            )
        }
    }

    private fun loadOrCreateChunks(
        metaFile: File,
        stagingFile: File,
        url: String,
        totalBytes: Long,
        eTag: String?,
        requestedChunks: Int
    ): List<ChunkRange> {
        if (metaFile.exists() && stagingFile.exists() && stagingFile.length() == totalBytes) {
            val restored = runCatching {
                val root = JSONObject(metaFile.readText())
                if (root.optString("url") != url || root.optLong("totalBytes") != totalBytes) {
                    return@runCatching null
                }
                val savedETag = root.optString("eTag")
                if (!eTag.isNullOrBlank() && savedETag.isNotBlank() && savedETag != eTag) {
                    return@runCatching null
                }
                val arr = root.getJSONArray("chunks")
                (0 until arr.length()).map { idx ->
                    val obj = arr.getJSONObject(idx)
                    ChunkRange(
                        index = obj.getInt("index"),
                        startByte = obj.getLong("start"),
                        endByte = obj.getLong("end"),
                        downloadedBytes = obj.getLong("written").coerceAtLeast(0L)
                    )
                }
            }.getOrNull()
            if (!restored.isNullOrEmpty()) return restored
        }
        return planChunks(totalBytes, requestedChunks)
    }

    private fun saveMetadata(
        metaFile: File,
        url: String,
        totalBytes: Long,
        eTag: String?,
        chunks: List<ChunkRange>
    ) {
        runCatching {
            val arr = JSONArray()
            chunks.forEach { c ->
                arr.put(
                    JSONObject().apply {
                        put("index", c.index)
                        put("start", c.startByte)
                        put("end", c.endByte)
                        put("written", c.downloadedBytes)
                    }
                )
            }
            val root = JSONObject().apply {
                put("url", url)
                put("totalBytes", totalBytes)
                put("eTag", eTag.orEmpty())
                put("chunks", arr)
            }
            metaFile.writeText(root.toString())
        }
    }

    companion object {
        const val MIN_CHUNKED_SIZE_BYTES = 512L * 1024L // 512 KB
        private const val STREAM_BUFFER_BYTES = 64 * 1024 // 64 KB I/O buffer
        private const val PROGRESS_INTERVAL_MS = 350L

        fun planChunks(totalBytes: Long, requestedChunks: Int): List<ChunkRange> {
            if (totalBytes <= 0L) return emptyList()
            val chunkCount = if (totalBytes < MIN_CHUNKED_SIZE_BYTES || requestedChunks <= 1) {
                1
            } else {
                requestedChunks.coerceIn(2, 8)
            }
            if (chunkCount == 1) {
                return listOf(ChunkRange(index = 0, startByte = 0L, endByte = totalBytes - 1L))
            }
            val baseSize = totalBytes / chunkCount
            var cursor = 0L
            return List(chunkCount) { index ->
                val end = if (index == chunkCount - 1) totalBytes - 1L else (cursor + baseSize - 1L)
                val range = ChunkRange(index = index, startByte = cursor, endByte = end)
                cursor = end + 1L
                range
            }
        }
    }
}
