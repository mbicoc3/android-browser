package com.example.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.core.database.entity.FilterRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FilterRuleDao {

    @Query("SELECT * FROM filter_rules ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<FilterRuleEntity>>

    @Query("SELECT * FROM filter_rules WHERE enabled = 1")
    suspend fun getEnabledSnapshot(): List<FilterRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: FilterRuleEntity): Long

    @Query("UPDATE filter_rules SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM filter_rules WHERE id = :id")
    suspend fun deleteById(id: Long)
}
