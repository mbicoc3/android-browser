package com.example.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sandboxes",
    indices = [
        Index(value = ["profileName"], unique = true),
        Index(value = ["lastUsedAt"])
    ]
)
data class SandboxEntity(
    @PrimaryKey val id: String,
    val name: String,
    val profileName: String,
    val pinnedDomain: String = "",
    val startUrl: String = "",
    val colorHex: Long = 0xFF38BDF8L,
    val iconKey: String = "work",
    val keepLongTerm: Boolean = true,
    val preferredCredentialId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = System.currentTimeMillis()
)
