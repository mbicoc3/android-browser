package com.example.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.core.database.entity.BrowserTabEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BrowserTabDao {
    @Query("SELECT * FROM browser_tabs ORDER BY position ASC, updatedAt ASC")
    fun observeStandardTabs(): Flow<List<BrowserTabEntity>>

    @Query("SELECT * FROM browser_tabs ORDER BY position ASC, updatedAt ASC")
    suspend fun getStandardTabsSnapshot(): List<BrowserTabEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTab(tab: BrowserTabEntity)

    @Query("UPDATE browser_tabs SET isActive = (id = :activeTabId)")
    suspend fun setActiveTab(activeTabId: String)

    @Query("DELETE FROM browser_tabs WHERE id = :tabId")
    suspend fun deleteTab(tabId: String)

    @Query("DELETE FROM browser_tabs WHERE sandboxId = :sandboxId")
    suspend fun deleteTabsBySandbox(sandboxId: String)

    @Query("DELETE FROM browser_tabs")
    suspend fun clearAllStandardTabs()
}
