package com.example.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "offline_pages",
    indices = [
        Index(value = ["originalUrl"], unique = true),
        Index(value = ["savedAt"])
    ]
)
data class OfflinePageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val originalUrl: String,
    val filePath: String,
    val fileSizeBytes: Long,
    val savedAt: Long = System.currentTimeMillis()
)
