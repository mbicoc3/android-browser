package com.example.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val fileName: String,
    val url: String,
    val localPath: String = "",
    val mimeType: String = "application/octet-stream",
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = -1L,
    val speedBytesPerSec: Long = 0L,
    val chunkCount: Int = 1,
    val supportsRanges: Boolean = false,
    val targetFolderUri: String = "",
    val targetFolderLabel: String = "Downloads (Default)",
    val stagingPath: String = "",
    val wifiOnly: Boolean = false,
    val status: String = DownloadStatus.QUEUED.name,
    val workRequestId: String = "",
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
