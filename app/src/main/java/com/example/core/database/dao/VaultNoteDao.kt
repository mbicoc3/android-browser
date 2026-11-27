package com.example.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.core.database.entity.VaultNoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultNoteDao {
    @Query("SELECT * FROM vault_notes ORDER BY updatedAt DESC")
    fun observeAllNotes(): Flow<List<VaultNoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNote(note: VaultNoteEntity): Long

    @Query("DELETE FROM vault_notes WHERE id = :id")
    suspend fun deleteById(id: Long)
}
