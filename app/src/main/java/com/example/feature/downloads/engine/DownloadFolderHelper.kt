package com.example.feature.downloads.engine

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.webkit.URLUtil
import androidx.core.content.FileProvider
import com.example.core.database.entity.DownloadEntity
import com.example.feature.downloads.model.DownloadProbeResult
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream

object DownloadFolderHelper {

    private const val COPY_BUFFER_SIZE = 64 * 1024

    fun persistTreePermission(context: Context, treeUri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching {
            context.contentResolver.takePersistableUriPermission(treeUri, flags)
        }
    }

    fun parseFolderDisplayName(treeUri: Uri): String {
        return runCatching {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val volume = docId.substringBefore(':', "")
            val relPath = docId.substringAfter(':', docId).trim('/')
            when {
                volume.equals("primary", ignoreCase = true) && relPath.isBlank() -> "Internal Storage"
                volume.equals("primary", ignoreCase = true) -> "Internal / $relPath"
                relPath.isBlank() -> volume.ifBlank { "Selected Folder" }
                else -> "$volume / $relPath"
            }
        }.getOrElse { treeUri.lastPathSegment ?: "Selected Folder" }
    }

    fun sanitizeFileName(rawName: String): String {
        val cleaned = rawName.trim().replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
        return cleaned.ifBlank { "download_${System.currentTimeMillis()}.bin" }
    }

    fun probeRemoteFile(
        client: OkHttpClient,
        url: String,
        fallbackMime: String? = null
    ): DownloadProbeResult {
        val headAttempt = runCatching {
            val request = Request.Builder().url(url).head().build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val acceptRanges = resp.header("Accept-Ranges")
                    ?.contains("bytes", ignoreCase = true) == true
                val contentLength = resp.header("Content-Length")?.toLongOrNull() ?: -1L
                val disposition = resp.header("Content-Disposition")
                val mime = resp.header("Content-Type")
                    ?.substringBefore(';')
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: fallbackMime
                    ?: "application/octet-stream"
                val guessedName = if (!disposition.isNullOrBlank()) {
                    URLUtil.guessFileName(url, disposition, mime)
                } else null
                DownloadProbeResult(
                    url = url,
                    totalBytes = contentLength,
                    supportsRanges = acceptRanges && contentLength > 0L,
                    mimeType = mime,
                    suggestedFileName = guessedName,
                    eTag = resp.header("ETag"),
                    probedSuccessfully = true
                )
            }
        }.getOrNull()

        if (headAttempt != null && (headAttempt.totalBytes > 0L || headAttempt.supportsRanges)) {
            return headAttempt
        }

        // Fallback Range: bytes=0-0 probe when servers disable HEAD or omit Accept-Ranges on HEAD
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .header("Range", "bytes=0-0")
                .get()
                .build()
            client.newCall(request).execute().use { resp ->
                val isPartial = resp.code == 206
                val contentRange = resp.header("Content-Range")
                val parsedTotal = contentRange
                    ?.substringAfter('/', "")
                    ?.trim()
                    ?.toLongOrNull()
                    ?: resp.header("Content-Length")?.toLongOrNull()
                    ?: headAttempt?.totalBytes
                    ?: -1L
                val mime = resp.header("Content-Type")
                    ?.substringBefore(';')
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: headAttempt?.mimeType
                    ?: fallbackMime
                    ?: "application/octet-stream"
                val disposition = resp.header("Content-Disposition")
                val guessedName = if (!disposition.isNullOrBlank()) {
                    URLUtil.guessFileName(url, disposition, mime)
                } else headAttempt?.suggestedFileName

                DownloadProbeResult(
                    url = url,
                    totalBytes = parsedTotal,
                    supportsRanges = isPartial && parsedTotal > 0L,
                    mimeType = mime,
                    suggestedFileName = guessedName,
                    eTag = resp.header("ETag") ?: headAttempt?.eTag,
                    probedSuccessfully = resp.isSuccessful
                )
            }
        }.getOrElse {
            headAttempt ?: DownloadProbeResult(
                url = url,
                mimeType = fallbackMime ?: "application/octet-stream",
                probedSuccessfully = false
            )
        }
    }

    fun publishStagedFileToDestination(
        context: Context,
        stagingFile: File,
        fileName: String,
        mimeType: String,
        targetFolderUri: String
    ): String {
        val safeName = sanitizeFileName(fileName)
        val cleanMime = mimeType.substringBefore(';').trim().ifBlank { "application/octet-stream" }

        if (targetFolderUri.isNotBlank()) {
            val treeUri = Uri.parse(targetFolderUri)
            val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)
            val createdUri = DocumentsContract.createDocument(
                context.contentResolver,
                parentDocUri,
                cleanMime,
                safeName
            ) ?: error("Unable to create file in selected download folder")

            context.contentResolver.openOutputStream(createdUri)?.use { output ->
                FileInputStream(stagingFile).use { input ->
                    input.copyTo(output, COPY_BUFFER_SIZE)
                }
                output.flush()
            } ?: error("Unable to open output stream for selected folder")

            stagingFile.delete()
            return createdUri.toString()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                put(MediaStore.Downloads.MIME_TYPE, cleanMime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val itemUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (itemUri != null) {
                return runCatching {
                    resolver.openOutputStream(itemUri)?.use { output ->
                        FileInputStream(stagingFile).use { input ->
                            input.copyTo(output, COPY_BUFFER_SIZE)
                        }
                        output.flush()
                    } ?: error("Failed to write MediaStore download")
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(itemUri, values, null, null)
                    stagingFile.delete()
                    itemUri.toString()
                }.getOrElse { err ->
                    runCatching { resolver.delete(itemUri, null, null) }
                    throw err
                }
            }
        }

        val targetDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "downloads")
        if (!targetDir.exists()) targetDir.mkdirs()
        val finalFile = resolveUniqueFile(targetDir, safeName)
        if (!stagingFile.renameTo(finalFile)) {
            FileInputStream(stagingFile).use { input ->
                finalFile.outputStream().use { output ->
                    input.copyTo(output, COPY_BUFFER_SIZE)
                }
            }
            stagingFile.delete()
        }
        return finalFile.absolutePath
    }

    fun openDownloadedItem(context: Context, entity: DownloadEntity): Boolean {
        val uri = resolvePlayableUri(context, entity) ?: return false
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, entity.mimeType.substringBefore(';').ifBlank { "*/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(Intent.createChooser(intent, entity.fileName))
            true
        }.getOrDefault(false)
    }

    fun shareDownloadedItem(context: Context, entity: DownloadEntity): Boolean {
        val uri = resolvePlayableUri(context, entity) ?: return false
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = entity.mimeType.substringBefore(';').ifBlank { "*/*" }
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return runCatching {
            context.startActivity(Intent.createChooser(intent, entity.fileName))
            true
        }.getOrDefault(false)
    }

    fun deleteDownloadedFiles(context: Context, entity: DownloadEntity) {
        if (entity.localPath.isNotBlank()) {
            if (entity.localPath.startsWith("content://")) {
                val uri = Uri.parse(entity.localPath)
                runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
                    .onFailure { runCatching { context.contentResolver.delete(uri, null, null) } }
            } else {
                runCatching { File(entity.localPath).delete() }
            }
        }
        if (entity.stagingPath.isNotBlank()) {
            runCatching { File(entity.stagingPath).delete() }
            runCatching { File("${entity.stagingPath}.meta.json").delete() }
        }
    }

    private fun resolvePlayableUri(context: Context, entity: DownloadEntity): Uri? {
        if (entity.localPath.isBlank()) return null
        if (entity.localPath.startsWith("content://")) {
            return Uri.parse(entity.localPath)
        }
        val file = File(entity.localPath)
        if (!file.exists()) return null
        return runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull()
    }

    private fun resolveUniqueFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val dotIdx = name.lastIndexOf('.')
        val base = if (dotIdx > 0) name.substring(0, dotIdx) else name
        val ext = if (dotIdx > 0) name.substring(dotIdx) else ""
        var count = 1
        while (candidate.exists()) {
            candidate = File(dir, "${base}_$count$ext")
            count++
        }
        return candidate
    }
}
