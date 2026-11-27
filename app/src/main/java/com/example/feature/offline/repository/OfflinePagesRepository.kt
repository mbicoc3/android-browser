package com.example.feature.offline.repository

import android.content.Context
import android.os.Environment
import android.webkit.WebView
import com.example.core.common.UrlUtils
import com.example.core.database.dao.OfflinePageDao
import com.example.core.database.entity.OfflinePageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume

class OfflinePagesRepository(
    private val appContext: Context,
    private val offlinePageDao: OfflinePageDao
) {

    fun observeAll(): Flow<List<OfflinePageEntity>> = offlinePageDao.observeAll()

    suspend fun saveCurrentWebPage(
        webView: WebView,
        title: String,
        url: String
    ): Result<OfflinePageEntity> {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank() || UrlUtils.isStartPage(cleanUrl) || UrlUtils.isOfflineArchiveUrl(cleanUrl)) {
            return Result.failure(IllegalArgumentException("Only live web pages can be saved offline"))
        }

        val archiveDir = withContext(Dispatchers.IO) { ensureArchiveDirectory() }
        val existing = withContext(Dispatchers.IO) { offlinePageDao.getByOriginalUrl(cleanUrl) }
        val safeTitle = title.trim().ifBlank { UrlUtils.extractHost(cleanUrl) }
        val targetFile = File(archiveDir, buildArchiveFileName(safeTitle, cleanUrl))

        val savedPath = withContext(Dispatchers.Main) {
            suspendCancellableCoroutine<String?> { continuation ->
                webView.saveWebArchive(targetFile.absolutePath, false) { resultPath ->
                    if (continuation.isActive) {
                        continuation.resume(resultPath)
                    }
                }
            }
        }

        return withContext(Dispatchers.IO) {
            if (savedPath.isNullOrBlank()) {
                targetFile.delete()
                return@withContext Result.failure(IllegalStateException("WebView did not produce an archive"))
            }
            val savedFile = File(savedPath)
            if (!savedFile.exists() || savedFile.length() <= 0L) {
                savedFile.delete()
                return@withContext Result.failure(IllegalStateException("Saved offline file is empty"))
            }
            if (existing != null && existing.filePath != savedFile.absolutePath) {
                runCatching { File(existing.filePath).delete() }
            }
            val entity = OfflinePageEntity(
                id = existing?.id ?: 0L,
                title = safeTitle,
                originalUrl = cleanUrl,
                filePath = savedFile.absolutePath,
                fileSizeBytes = savedFile.length(),
                savedAt = System.currentTimeMillis()
            )
            val rowId = offlinePageDao.insertOrReplace(entity)
            Result.success(entity.copy(id = if (entity.id != 0L) entity.id else rowId))
        }
    }

    suspend fun deletePage(page: OfflinePageEntity) = withContext(Dispatchers.IO) {
        runCatching { File(page.filePath).delete() }
        offlinePageDao.deleteById(page.id)
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        val dir = ensureArchiveDirectory()
        dir.listFiles()?.forEach { file -> runCatching { file.delete() } }
        offlinePageDao.clearAll()
    }

    private fun ensureArchiveDirectory(): File {
        val base = appContext.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: appContext.filesDir
        return File(base, OFFLINE_DIR).apply { mkdirs() }
    }

    companion object {
        const val OFFLINE_DIR = "orbit_offline_pages"

        fun buildArchiveFileName(
            title: String,
            url: String,
            timestampMs: Long = System.currentTimeMillis()
        ): String {
            val base = title.ifBlank { url }
                .lowercase(Locale.US)
                .replace(Regex("[^a-z0-9]+"), "_")
                .trim('_')
                .take(40)
                .ifBlank { "orbit_page" }
            val hash = (url.hashCode().toUInt()).toString(16)
            return "${base}_${hash}_${timestampMs}.mht"
        }
    }
}
