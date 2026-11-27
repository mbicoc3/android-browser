package com.example.feature.vault.repository

import com.example.core.database.dao.VaultNoteDao
import com.example.core.database.entity.VaultNoteEntity
import com.example.core.security.KeystoreCryptoManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class DecryptedVaultEntry(
    val id: Long,
    val label: String,
    val secretContent: String,
    val updatedAt: Long,
    val decryptionFailed: Boolean = false
)

class VaultRepository(
    private val vaultNoteDao: VaultNoteDao,
    private val cryptoManager: KeystoreCryptoManager
) {
    fun observeDecryptedEntries(): Flow<List<DecryptedVaultEntry>> =
        vaultNoteDao.observeAllNotes().map { list ->
            list.map { entity ->
                val decrypted = runCatching {
                    cryptoManager.decrypt(entity.encryptedPayload)
                }
                DecryptedVaultEntry(
                    id = entity.id,
                    label = entity.label,
                    secretContent = decrypted.getOrElse { "Unable to decrypt on this keystore" },
                    updatedAt = entity.updatedAt,
                    decryptionFailed = decrypted.isFailure
                )
            }
        }

    suspend fun saveEntry(label: String, secretContent: String) {
        val encrypted = cryptoManager.encrypt(secretContent)
        val now = System.currentTimeMillis()
        vaultNoteDao.upsertNote(
            VaultNoteEntity(
                label = label.trim(),
                encryptedPayload = encrypted,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun deleteEntry(id: Long) {
        vaultNoteDao.deleteById(id)
    }
}
