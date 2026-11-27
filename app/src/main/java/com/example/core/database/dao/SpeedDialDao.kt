package com.example.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.core.database.entity.SpeedDialEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SpeedDialDao {
    @Query("SELECT * FROM speed_dial ORDER BY collectionIndex ASC, position ASC, id ASC")
    fun observeAll(): Flow<List<SpeedDialEntity>>

    @Query("SELECT * FROM speed_dial ORDER BY collectionIndex ASC, position ASC, id ASC")
    suspend fun getAllSnapshot(): List<SpeedDialEntity>

    @Query("SELECT COUNT(*) FROM speed_dial")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<SpeedDialEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: SpeedDialEntity): Long

    @Query("UPDATE speed_dial SET iconPath = :iconPath WHERE id = :id")
    suspend fun updateIconPath(id: Long, iconPath: String)

    @Query("UPDATE speed_dial SET collectionIndex = :collectionIndex WHERE id = :id")
    suspend fun updateCollection(id: Long, collectionIndex: Int)

    @Query("DELETE FROM speed_dial WHERE id = :id")
    suspend fun deleteById(id: Long)
}
