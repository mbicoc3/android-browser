package com.example.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "speed_dial")
data class SpeedDialEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val url: String,
    val shortBadge: String,
    val accentColorHex: Long,
    val position: Int = 0,
    val iconPath: String = "",
    val collectionIndex: Int = 0
)
