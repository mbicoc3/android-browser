package com.example.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.core.database.entity.DownloadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(download: DownloadEntity): Long

    @Query(
        """
        UPDATE downloads
        SET status = :status,
            bytesDownloaded = :bytesDownloaded,
            totalBytes = :totalBytes,
            speedBytesPerSec = :speedBytesPerSec,
            chunkCount = :chunkCount,
            supportsRanges = :supportsRanges,
            localPath = :localPath,
            stagingPath = :stagingPath,
            mimeType = :mimeType,
            errorMessage = :errorMessage
        WHERE id = :id
        """
    )
    suspend fun updateProgress(
        id: Long,
        status: String,
        bytesDownloaded: Long,
        totalBytes: Long,
        speedBytesPerSec: Long,
        chunkCount: Int,
        supportsRanges: Boolean,
        localPath: String,
        stagingPath: String,
        mimeType: String,
        errorMessage: String?
    )

    @Query("UPDATE downloads SET workRequestId = :workId WHERE id = :id")
    suspend fun updateWorkRequestId(id: Long, workId: String)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM downloads WHERE status IN ('COMPLETED', 'FAILED', 'CANCELLED')")
    suspend fun clearFinished()
}
