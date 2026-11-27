package com.example.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.core.database.entity.SiteRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SiteRecordDao {
    @Query("SELECT * FROM site_records ORDER BY lastVisitedAt DESC")
    fun observeAll(): Flow<List<SiteRecordEntity>>

    @Query("SELECT * FROM site_records ORDER BY lastVisitedAt DESC")
    suspend fun getAllSnapshot(): List<SiteRecordEntity>

    @Query("SELECT * FROM site_records WHERE origin = :origin LIMIT 1")
    suspend fun getByOrigin(origin: String): SiteRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: SiteRecordEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(records: List<SiteRecordEntity>)

    @Query(
        """
        UPDATE site_records SET
            cookieCount = 0,
            localStorageCount = 0,
            localStorageBytes = 0,
            sessionStorageCount = 0,
            sessionStorageBytes = 0,
            indexedDbCount = 0,
            cacheStorageCount = 0,
            serviceWorkerCount = 0,
            webStorageUsageBytes = 0,
            lastInspectedAt = :inspectedAt
        """
    )
    suspend fun resetAllStorageTelemetry(inspectedAt: Long)

    @Query("DELETE FROM site_records WHERE origin = :origin")
    suspend fun deleteByOrigin(origin: String)

    @Query("DELETE FROM site_records")
    suspend fun clearAll()
}
