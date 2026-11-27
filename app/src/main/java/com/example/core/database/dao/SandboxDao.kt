package com.example.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.core.database.entity.SandboxEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SandboxDao {
    @Query("SELECT * FROM sandboxes ORDER BY lastUsedAt DESC, createdAt DESC")
    fun observeAll(): Flow<List<SandboxEntity>>

    @Query("SELECT * FROM sandboxes ORDER BY lastUsedAt DESC, createdAt DESC")
    suspend fun getAllSnapshot(): List<SandboxEntity>

    @Query("SELECT * FROM sandboxes WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SandboxEntity?

    @Query("SELECT COUNT(*) FROM sandboxes")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(sandbox: SandboxEntity): Long

    @Query("UPDATE sandboxes SET lastUsedAt = :timestamp WHERE id = :id")
    suspend fun touchLastUsed(id: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE sandboxes SET preferredCredentialId = :credentialId WHERE id = :id")
    suspend fun updatePreferredCredential(id: String, credentialId: String?)

    @Query("DELETE FROM sandboxes WHERE id = :id")
    suspend fun deleteById(id: String)
}
