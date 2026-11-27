package com.example.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.core.database.entity.BackupMetadataEntity
import com.example.core.database.entity.CredentialEntity
import com.example.core.database.entity.CredentialFolderEntity
import com.example.core.database.entity.PasswordHistoryEntity
import com.example.core.database.entity.VaultMetadataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PasswordVaultDao {

    @Query("SELECT * FROM vault_metadata WHERE id = :id LIMIT 1")
    fun observeMetadata(id: String = VaultMetadataEntity.PRIMARY_ID): Flow<VaultMetadataEntity?>

    @Query("SELECT * FROM vault_metadata WHERE id = :id LIMIT 1")
    suspend fun getMetadata(id: String = VaultMetadataEntity.PRIMARY_ID): VaultMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMetadata(metadata: VaultMetadataEntity)

    @Query("SELECT * FROM credentials ORDER BY isFavorite DESC, updatedAt DESC")
    fun observeAllCredentials(): Flow<List<CredentialEntity>>

    @Query("SELECT * FROM credentials ORDER BY isFavorite DESC, updatedAt DESC")
    suspend fun getAllCredentialsSnapshot(): List<CredentialEntity>

    @Query("SELECT * FROM credentials WHERE id = :id LIMIT 1")
    suspend fun getCredentialById(id: String): CredentialEntity?

    @Query("SELECT COUNT(*) FROM credentials WHERE normalizedDomain = :domain")
    suspend fun countCredentialsForDomain(domain: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCredential(entity: CredentialEntity)

    @Query("UPDATE credentials SET isFavorite = :favorite, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean, updatedAt: Long)

    @Query("DELETE FROM credentials WHERE id = :id")
    suspend fun deleteCredentialById(id: String)

    @Query("SELECT * FROM credential_folders ORDER BY name COLLATE NOCASE ASC")
    fun observeFolders(): Flow<List<CredentialFolderEntity>>

    @Query("SELECT * FROM credential_folders ORDER BY name COLLATE NOCASE ASC")
    suspend fun getFoldersSnapshot(): List<CredentialFolderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFolder(folder: CredentialFolderEntity)

    @Query("UPDATE credentials SET folderId = NULL WHERE folderId = :folderId")
    suspend fun clearFolderFromCredentials(folderId: String)

    @Query("DELETE FROM credential_folders WHERE id = :folderId")
    suspend fun deleteFolderById(folderId: String)

    @Transaction
    suspend fun deleteFolderSafely(folderId: String) {
        clearFolderFromCredentials(folderId)
        deleteFolderById(folderId)
    }

    @Query("SELECT * FROM password_history WHERE credentialId = :credentialId ORDER BY changedAt DESC")
    fun observePasswordHistory(credentialId: String): Flow<List<PasswordHistoryEntity>>

    @Query("SELECT * FROM password_history WHERE credentialId = :credentialId ORDER BY changedAt DESC")
    suspend fun getPasswordHistorySnapshot(credentialId: String): List<PasswordHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPasswordHistory(entry: PasswordHistoryEntity)

    @Query(
        """
        DELETE FROM password_history
        WHERE credentialId = :credentialId
          AND id NOT IN (
            SELECT id FROM password_history
            WHERE credentialId = :credentialId
            ORDER BY changedAt DESC
            LIMIT :keepCount
          )
        """
    )
    suspend fun trimPasswordHistory(credentialId: String, keepCount: Int = 10)

    @Query("SELECT * FROM backup_metadata ORDER BY createdAt DESC LIMIT 10")
    fun observeBackupHistory(): Flow<List<BackupMetadataEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBackupMetadata(metadata: BackupMetadataEntity)

    @Query("DELETE FROM password_history")
    suspend fun deleteAllHistory()

    @Query("DELETE FROM credentials")
    suspend fun deleteAllCredentials()

    @Query("DELETE FROM credential_folders")
    suspend fun deleteAllFolders()

    @Query("DELETE FROM backup_metadata")
    suspend fun deleteAllBackupMetadata()

    @Query("DELETE FROM vault_metadata")
    suspend fun deleteMetadata()

    @Transaction
    suspend fun wipeAllVaultData() {
        deleteAllHistory()
        deleteAllCredentials()
        deleteAllFolders()
        deleteAllBackupMetadata()
        deleteMetadata()
    }
}
