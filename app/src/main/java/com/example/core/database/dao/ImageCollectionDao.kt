package com.example.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.core.database.entity.ImageCollectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ImageCollectionDao {
    @Query("SELECT * FROM image_collections ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ImageCollectionEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM image_collections WHERE imageUrl = :imageUrl)")
    fun observeIsCollected(imageUrl: String): Flow<Boolean>

    @Query("SELECT * FROM image_collections WHERE imageUrl = :imageUrl LIMIT 1")
    suspend fun getByImageUrl(imageUrl: String): ImageCollectionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ImageCollectionEntity): Long

    @Query("UPDATE image_collections SET collectionName = :collectionName WHERE id = :id")
    suspend fun updateCollectionName(id: Long, collectionName: String)

    @Query("DELETE FROM image_collections WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM image_collections")
    suspend fun deleteAll()
}
