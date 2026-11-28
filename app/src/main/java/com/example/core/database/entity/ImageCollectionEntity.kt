package com.example.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "image_collections",
    indices = [
        Index(value = ["imageUrl"], unique = true),
        Index(value = ["collectionName"]),
        Index(value = ["createdAt"])
    ]
)
data class ImageCollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val imageUrl: String,
    val sourcePageUrl: String = "",
    val collectionName: String = "General",
    val createdAt: Long = System.currentTimeMillis()
)
