package com.example.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.core.database.entity.FilterListEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FilterListDao {

    @Query("SELECT * FROM filter_lists ORDER BY isBuiltIn DESC, name ASC")
    fun observeAll(): Flow<List<FilterListEntity>>

    @Query("SELECT * FROM filter_lists ORDER BY isBuiltIn DESC, name ASC")
    suspend fun getAllSnapshot(): List<FilterListEntity>

    @Query("SELECT * FROM filter_lists WHERE enabled = 1")
    suspend fun getEnabledSnapshot(): List<FilterListEntity>

    @Query("SELECT COUNT(*) FROM filter_lists")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(lists: List<FilterListEntity>)

    @Query("UPDATE filter_lists SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)

    @Query(
        "UPDATE filter_lists SET ruleCount = :ruleCount, cosmeticRuleCount = :cosmeticRuleCount, lastUpdatedAt = :updatedAt WHERE id = :id"
    )
    suspend fun updateSyncStats(id: String, ruleCount: Int, cosmeticRuleCount: Int, updatedAt: Long)
}
