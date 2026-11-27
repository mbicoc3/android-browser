package com.example.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Encrypted entry stored in Room after AES-GCM encryption via KeystoreCryptoManager.
 */
@Entity(tableName = "vault_notes")
data class VaultNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val label: String,
    val encryptedPayload: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
