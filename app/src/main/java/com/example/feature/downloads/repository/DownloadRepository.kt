package com.example.feature.downloads.repository

import android.content.Context
import android.net.Uri
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.core.database.dao.DownloadDao
import com.example.core.database.entity.DownloadEntity
import com.example.core.database.entity.DownloadStatus
import com.example.core.datastore.BrowserPreferences
import com.example.core.datastore.BrowserPreferencesDataStore
import com.example.core.network.NetworkClientFactory
import com.example.core.worker.FileDownloadWorker
import com.example.feature.downloads.engine.DownloadFolderHelper
import com.example.feature.downloads.model.DownloadProbeResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.UUID

class DownloadRepository(
    private val appContext: Context,
    private val downloadDao: DownloadDao,
    private val workManager: WorkManager,
    private val preferencesDataStore: BrowserPreferencesDataStore
) {
    private val probeClient by lazy { NetworkClientFactory.createDownloadOkHttpClient() }

    val preferencesFlow: Flow<BrowserPreferences> = preferencesDataStore.preferencesFlow

    fun observeDownloads(): Flow<List<DownloadEntity>> = downloadDao.observeDownloads()

    suspend fun probeRemoteFile(url: String, fallbackMime: String? = null): DownloadProbeResult =
        withContext(Dispatchers.IO) {
            DownloadFolderHelper.probeRemoteFile(probeClient, url, fallbackMime)
        }

    suspend fun enqueueDownload(
        url: String,
        suggestedFileName: String? = null,
        mimeType: String? = null,
        chunkCountOverride: Int? = null,
        targetFolderUriOverride: String? = null,
        targetFolderLabelOverride: String? = null,
        wifiOnlyOverride: Boolean? = null
    ): Long {
        val prefs = preferencesDataStore.preferencesFlow.first()
        val rawName = suggestedFileName?.takeIf { it.isNotBlank() }
            ?: Uri.parse(url).lastPathSegment?.takeIf { it.isNotBlank() }
            ?: "orbit_file_${System.currentTimeMillis()}.bin"
        val fileName = DownloadFolderHelper.sanitizeFileName(rawName)
        val chunks = (chunkCountOverride ?: prefs.downloadChunkCount).coerceIn(1, 8)
        val folderUri = targetFolderUriOverride ?: prefs.downloadFolderUri
        val folderLabel = targetFolderLabelOverride ?: prefs.downloadFolderLabel
        val wifiOnly = wifiOnlyOverride ?: prefs.downloadWifiOnly

        val id = downloadDao.insert(
            DownloadEntity(
                fileName = fileName,
                url = url,
                mimeType = mimeType?.substringBefore(';')?.trim()?.ifBlank { "application/octet-stream" }
                    ?: "application/octet-stream",
                chunkCount = chunks,
                targetFolderUri = folderUri,
                targetFolderLabel = folderLabel,
                wifiOnly = wifiOnly,
                status = DownloadStatus.QUEUED.name
            )
        )

        enqueueWorkForDownload(
            id = id,
            url = url,
            fileName = fileName,
            chunks = chunks,
            targetFolderUri = folderUri,
            wifiOnly = wifiOnly
        )
        return id
    }

    suspend fun pauseDownload(entity: DownloadEntity) {
        downloadDao.updateProgress(
            id = entity.id,
            status = DownloadStatus.PAUSED.name,
            bytesDownloaded = entity.bytesDownloaded,
            totalBytes = entity.totalBytes,
            speedBytesPerSec = 0L,
            chunkCount = entity.chunkCount,
            supportsRanges = entity.supportsRanges,
            localPath = "",
            stagingPath = entity.stagingPath,
            mimeType = entity.mimeType,
            errorMessage = null
        )
        if (entity.workRequestId.isNotBlank()) {
            runCatching {
                workManager.cancelWorkById(UUID.fromString(entity.workRequestId))
            }
        }
    }

    suspend fun resumeDownload(entity: DownloadEntity) {
        downloadDao.updateProgress(
            id = entity.id,
            status = DownloadStatus.QUEUED.name,
            bytesDownloaded = entity.bytesDownloaded,
            totalBytes = entity.totalBytes,
            speedBytesPerSec = 0L,
            chunkCount = entity.chunkCount,
            supportsRanges = entity.supportsRanges,
            localPath = "",
            stagingPath = entity.stagingPath,
            mimeType = entity.mimeType,
            errorMessage = null
        )
        enqueueWorkForDownload(
            id = entity.id,
            url = entity.url,
            fileName = entity.fileName,
            chunks = entity.chunkCount.coerceIn(1, 8),
            targetFolderUri = entity.targetFolderUri,
            wifiOnly = entity.wifiOnly
        )
    }

    suspend fun retryDownload(entity: DownloadEntity) {
        if (entity.stagingPath.isNotBlank() && entity.supportsRanges) {
            resumeDownload(entity)
        } else {
            DownloadFolderHelper.deleteDownloadedFiles(appContext, entity)
            downloadDao.deleteById(entity.id)
            enqueueDownload(
                url = entity.url,
                suggestedFileName = entity.fileName,
                mimeType = entity.mimeType,
                chunkCountOverride = entity.chunkCount,
                targetFolderUriOverride = entity.targetFolderUri,
                targetFolderLabelOverride = entity.targetFolderLabel,
                wifiOnlyOverride = entity.wifiOnly
            )
        }
    }

    suspend fun cancelDownload(entity: DownloadEntity) {
        downloadDao.updateProgress(
            id = entity.id,
            status = DownloadStatus.CANCELLED.name,
            bytesDownloaded = entity.bytesDownloaded,
            totalBytes = entity.totalBytes,
            speedBytesPerSec = 0L,
            chunkCount = entity.chunkCount,
            supportsRanges = entity.supportsRanges,
            localPath = entity.localPath,
            stagingPath = "",
            mimeType = entity.mimeType,
            errorMessage = "Cancelled by user"
        )
        if (entity.workRequestId.isNotBlank()) {
            runCatching {
                workManager.cancelWorkById(UUID.fromString(entity.workRequestId))
            }
        }
        DownloadFolderHelper.deleteDownloadedFiles(appContext, entity)
    }

    suspend fun deleteDownload(entity: DownloadEntity) {
        if (entity.workRequestId.isNotBlank()) {
            runCatching {
                workManager.cancelWorkById(UUID.fromString(entity.workRequestId))
            }
        }
        DownloadFolderHelper.deleteDownloadedFiles(appContext, entity)
        downloadDao.deleteById(entity.id)
    }

    suspend fun clearFinished() {
        downloadDao.clearFinished()
    }

    suspend fun setDownloadFolder(uri: Uri) {
        DownloadFolderHelper.persistTreePermission(appContext, uri)
        val label = DownloadFolderHelper.parseFolderDisplayName(uri)
        preferencesDataStore.setDownloadFolder(uri.toString(), label)
    }

    suspend fun resetDownloadFolder() {
        preferencesDataStore.resetDownloadFolder()
    }

    suspend fun setConfirmBeforeDownload(enabled: Boolean) {
        preferencesDataStore.setConfirmBeforeDownload(enabled)
    }

    suspend fun setDownloadChunkCount(chunks: Int) {
        preferencesDataStore.setDownloadChunkCount(chunks)
    }

    suspend fun setDownloadWifiOnly(wifiOnly: Boolean) {
        preferencesDataStore.setDownloadWifiOnly(wifiOnly)
    }

    private suspend fun enqueueWorkForDownload(
        id: Long,
        url: String,
        fileName: String,
        chunks: Int,
        targetFolderUri: String,
        wifiOnly: Boolean
    ) {
        val networkType = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
        val request = OneTimeWorkRequestBuilder<FileDownloadWorker>()
            .setInputData(
                FileDownloadWorker.buildInputData(
                    downloadId = id,
                    url = url,
                    fileName = fileName,
                    chunkCount = chunks,
                    targetFolderUri = targetFolderUri
                )
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(networkType)
                    .build()
            )
            .build()

        downloadDao.updateWorkRequestId(id, request.id.toString())
        workManager.enqueue(request)
    }
}
