package com.example.core.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.example.core.database.OrbitDatabase
import com.example.core.database.entity.DownloadStatus
import com.example.core.datastore.BrowserPreferencesDataStore
import com.example.core.network.NetworkClientFactory
import com.example.feature.downloads.engine.ChunkedDownloadEngine
import com.example.feature.downloads.engine.DownloadFolderHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/**
 * WorkManager background worker that executes high-speed multi-chunk downloads via
 * [ChunkedDownloadEngine] and publishes completed files into SAF/MediaStore/App storage.
 */
class FileDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val downloadId = inputData.getLong(KEY_DOWNLOAD_ID, -1L)
        val url = inputData.getString(KEY_URL).orEmpty()
        val fileName = inputData.getString(KEY_FILE_NAME).orEmpty().ifBlank { "download.bin" }
        val requestedChunks = inputData.getInt(KEY_CHUNK_COUNT, 4).coerceIn(1, 8)
        val targetFolderUri = inputData.getString(KEY_TARGET_FOLDER_URI).orEmpty()

        if (downloadId <= 0L || url.isBlank()) {
            return Result.failure()
        }

        val downloadDao = OrbitDatabase.getInstance(applicationContext).downloadDao()
        val existingEntity = downloadDao.getById(downloadId)
        val existingStaging = existingEntity?.stagingPath.orEmpty()
        runCatching {
            val prefs = BrowserPreferencesDataStore(applicationContext).preferencesFlow.first()
            NetworkClientFactory.setDohProvider(prefs.dohProvider)
        }
        val engine = ChunkedDownloadEngine(NetworkClientFactory.createDownloadOkHttpClient())

        return try {
            val stagedResult = engine.downloadToStaging(
                context = applicationContext,
                downloadId = downloadId,
                url = url,
                fileName = fileName,
                requestedChunks = requestedChunks,
                existingStagingPath = existingStaging,
                isStopped = { isStopped }
            ) { snapshot ->
                downloadDao.updateProgress(
                    id = downloadId,
                    status = DownloadStatus.DOWNLOADING.name,
                    bytesDownloaded = snapshot.bytesDownloaded,
                    totalBytes = snapshot.totalBytes,
                    speedBytesPerSec = snapshot.speedBytesPerSec,
                    chunkCount = snapshot.chunkCount,
                    supportsRanges = snapshot.supportsRanges,
                    localPath = "",
                    stagingPath = snapshot.stagingPath,
                    mimeType = snapshot.mimeType,
                    errorMessage = null
                )
            }

            val publishedLocation = DownloadFolderHelper.publishStagedFileToDestination(
                context = applicationContext,
                stagingFile = stagedResult.stagingFile,
                fileName = fileName,
                mimeType = stagedResult.mimeType,
                targetFolderUri = targetFolderUri
            )

            downloadDao.updateProgress(
                id = downloadId,
                status = DownloadStatus.COMPLETED.name,
                bytesDownloaded = stagedResult.bytesDownloaded,
                totalBytes = stagedResult.totalBytes,
                speedBytesPerSec = 0L,
                chunkCount = stagedResult.chunkCount,
                supportsRanges = stagedResult.supportsRanges,
                localPath = publishedLocation,
                stagingPath = "",
                mimeType = stagedResult.mimeType,
                errorMessage = null
            )
            Result.success()
        } catch (_: CancellationException) {
            withContext(NonCancellable) {
                val latest = downloadDao.getById(downloadId)
                if (latest != null && latest.status == DownloadStatus.PAUSED.name) {
                    downloadDao.updateProgress(
                        id = downloadId,
                        status = DownloadStatus.PAUSED.name,
                        bytesDownloaded = latest.bytesDownloaded,
                        totalBytes = latest.totalBytes,
                        speedBytesPerSec = 0L,
                        chunkCount = latest.chunkCount,
                        supportsRanges = latest.supportsRanges,
                        localPath = "",
                        stagingPath = latest.stagingPath,
                        mimeType = latest.mimeType,
                        errorMessage = null
                    )
                } else {
                    latest?.stagingPath?.takeIf { it.isNotBlank() }?.let { path ->
                        runCatching { File(path).delete() }
                        runCatching { File("$path.meta.json").delete() }
                    }
                    downloadDao.updateProgress(
                        id = downloadId,
                        status = DownloadStatus.CANCELLED.name,
                        bytesDownloaded = latest?.bytesDownloaded ?: 0L,
                        totalBytes = latest?.totalBytes ?: -1L,
                        speedBytesPerSec = 0L,
                        chunkCount = latest?.chunkCount ?: 1,
                        supportsRanges = latest?.supportsRanges ?: false,
                        localPath = "",
                        stagingPath = "",
                        mimeType = latest?.mimeType ?: "application/octet-stream",
                        errorMessage = "Cancelled"
                    )
                }
            }
            Result.failure()
        } catch (error: Throwable) {
            val latest = downloadDao.getById(downloadId)
            downloadDao.updateProgress(
                id = downloadId,
                status = DownloadStatus.FAILED.name,
                bytesDownloaded = latest?.bytesDownloaded ?: 0L,
                totalBytes = latest?.totalBytes ?: -1L,
                speedBytesPerSec = 0L,
                chunkCount = latest?.chunkCount ?: requestedChunks,
                supportsRanges = latest?.supportsRanges ?: false,
                localPath = "",
                stagingPath = latest?.stagingPath.orEmpty(),
                mimeType = latest?.mimeType ?: "application/octet-stream",
                errorMessage = error.localizedMessage ?: "Download failed"
            )
            Result.failure()
        }
    }

    companion object {
        const val KEY_DOWNLOAD_ID = "download_id"
        const val KEY_URL = "download_url"
        const val KEY_FILE_NAME = "download_file_name"
        const val KEY_CHUNK_COUNT = "download_chunk_count"
        const val KEY_TARGET_FOLDER_URI = "download_target_folder_uri"

        fun buildInputData(
            downloadId: Long,
            url: String,
            fileName: String,
            chunkCount: Int,
            targetFolderUri: String
        ): Data =
            Data.Builder()
                .putLong(KEY_DOWNLOAD_ID, downloadId)
                .putString(KEY_URL, url)
                .putString(KEY_FILE_NAME, fileName)
                .putInt(KEY_CHUNK_COUNT, chunkCount.coerceIn(1, 8))
                .putString(KEY_TARGET_FOLDER_URI, targetFolderUri)
                .build()
    }
}
