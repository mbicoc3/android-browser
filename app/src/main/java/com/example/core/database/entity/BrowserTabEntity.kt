package com.example.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "browser_tabs")
data class BrowserTabEntity(
    @PrimaryKey val id: String,
    val title: String,
    val url: String,
    val isIncognito: Boolean = false,
    val isDesktopMode: Boolean = false,
    val isActive: Boolean = false,
    val position: Int = 0,
    val sandboxId: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
