package com.example.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.core.database.entity.OfflinePageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OfflinePageDao {
    @Query("SELECT * FROM offline_pages ORDER BY savedAt DESC")
    fun observeAll(): Flow<List<OfflinePageEntity>>

    @Query("SELECT * FROM offline_pages WHERE originalUrl = :originalUrl LIMIT 1")
    suspend fun getByOriginalUrl(originalUrl: String): OfflinePageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(page: OfflinePageEntity): Long

    @Query("DELETE FROM offline_pages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM offline_pages")
    suspend fun clearAll()
}
