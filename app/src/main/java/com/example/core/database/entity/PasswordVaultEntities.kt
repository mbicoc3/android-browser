package com.example.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "credentials",
    indices = [
        Index(value = ["normalizedDomain"]),
        Index(value = ["folderId"]),
        Index(value = ["updatedAt"])
    ]
)
data class CredentialEntity(
    @PrimaryKey val id: String,
    val title: String,
    val normalizedDomain: String,
    val usernameHint: String?,
    val folderId: String?,
    val isFavorite: Boolean,
    val encryptedPayload: ByteArray,
    val encryptionVersion: Int,
    val createdAt: Long,
    val updatedAt: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CredentialEntity) return false
        return id == other.id &&
            title == other.title &&
            normalizedDomain == other.normalizedDomain &&
            usernameHint == other.usernameHint &&
            folderId == other.folderId &&
            isFavorite == other.isFavorite &&
            encryptedPayload.contentEquals(other.encryptedPayload) &&
            encryptionVersion == other.encryptionVersion &&
            createdAt == other.createdAt &&
            updatedAt == other.updatedAt
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + normalizedDomain.hashCode()
        result = 31 * result + (usernameHint?.hashCode() ?: 0)
        result = 31 * result + (folderId?.hashCode() ?: 0)
        result = 31 * result + isFavorite.hashCode()
        result = 31 * result + encryptedPayload.contentHashCode()
        result = 31 * result + encryptionVersion
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + updatedAt.hashCode()
        return result
    }
}

@Entity(tableName = "vault_metadata")
data class VaultMetadataEntity(
    @PrimaryKey val id: String = PRIMARY_ID,
    val encryptedDek: ByteArray,
    val dekNonce: ByteArray,
    val salt: ByteArray,
    val argon2MemoryKb: Int,
    val argon2Iterations: Int,
    val argon2Parallelism: Int,
    val verifierBlob: ByteArray,
    val keystoreWrappedDek: ByteArray? = null,
    val keystoreDekNonce: ByteArray? = null,
    val biometricEnabled: Boolean = false,
    val autoLockTimeoutMs: Long = DEFAULT_AUTO_LOCK_MS,
    val lockOnBackground: Boolean = true,
    val clipboardClearSeconds: Int = DEFAULT_CLIPBOARD_CLEAR_SEC,
    val allowHttpAutofill: Boolean = false,
    val neverSaveDomainsJson: String = "[]",
    val encryptionVersion: Int = CURRENT_ENCRYPTION_VERSION,
    val createdAt: Long,
    val updatedAt: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VaultMetadataEntity) return false
        return id == other.id &&
            encryptedDek.contentEquals(other.encryptedDek) &&
            dekNonce.contentEquals(other.dekNonce) &&
            salt.contentEquals(other.salt) &&
            argon2MemoryKb == other.argon2MemoryKb &&
            argon2Iterations == other.argon2Iterations &&
            argon2Parallelism == other.argon2Parallelism &&
            verifierBlob.contentEquals(other.verifierBlob) &&
            (keystoreWrappedDek?.contentEquals(other.keystoreWrappedDek) ?: (other.keystoreWrappedDek == null)) &&
            (keystoreDekNonce?.contentEquals(other.keystoreDekNonce) ?: (other.keystoreDekNonce == null)) &&
            biometricEnabled == other.biometricEnabled &&
            autoLockTimeoutMs == other.autoLockTimeoutMs &&
            lockOnBackground == other.lockOnBackground &&
            clipboardClearSeconds == other.clipboardClearSeconds &&
            allowHttpAutofill == other.allowHttpAutofill &&
            neverSaveDomainsJson == other.neverSaveDomainsJson &&
            encryptionVersion == other.encryptionVersion &&
            createdAt == other.createdAt &&
            updatedAt == other.updatedAt
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + encryptedDek.contentHashCode()
        result = 31 * result + dekNonce.contentHashCode()
        result = 31 * result + salt.contentHashCode()
        result = 31 * result + argon2MemoryKb
        result = 31 * result + argon2Iterations
        result = 31 * result + argon2Parallelism
        result = 31 * result + verifierBlob.contentHashCode()
        result = 31 * result + (keystoreWrappedDek?.contentHashCode() ?: 0)
        result = 31 * result + (keystoreDekNonce?.contentHashCode() ?: 0)
        result = 31 * result + biometricEnabled.hashCode()
        result = 31 * result + autoLockTimeoutMs.hashCode()
        result = 31 * result + lockOnBackground.hashCode()
        result = 31 * result + clipboardClearSeconds
        result = 31 * result + allowHttpAutofill.hashCode()
        result = 31 * result + neverSaveDomainsJson.hashCode()
        result = 31 * result + encryptionVersion
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + updatedAt.hashCode()
        return result
    }

    companion object {
        const val PRIMARY_ID = "primary"
        const val CURRENT_ENCRYPTION_VERSION = 1
        const val DEFAULT_AUTO_LOCK_MS = 300_000L
        const val DEFAULT_CLIPBOARD_CLEAR_SEC = 30
    }
}

@Entity(tableName = "credential_folders")
data class CredentialFolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long
)

@Entity(
    tableName = "password_history",
    foreignKeys = [
        ForeignKey(
            entity = CredentialEntity::class,
            parentColumns = ["id"],
            childColumns = ["credentialId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["credentialId"]),
        Index(value = ["changedAt"])
    ]
)
data class PasswordHistoryEntity(
    @PrimaryKey val id: String,
    val credentialId: String,
    val encryptedPassword: ByteArray,
    val encryptionVersion: Int,
    val changedAt: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PasswordHistoryEntity) return false
        return id == other.id &&
            credentialId == other.credentialId &&
            encryptedPassword.contentEquals(other.encryptedPassword) &&
            encryptionVersion == other.encryptionVersion &&
            changedAt == other.changedAt
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + credentialId.hashCode()
        result = 31 * result + encryptedPassword.contentHashCode()
        result = 31 * result + encryptionVersion
        result = 31 * result + changedAt.hashCode()
        return result
    }
}

@Entity(tableName = "backup_metadata")
data class BackupMetadataEntity(
    @PrimaryKey val id: String,
    val fileName: String,
    val credentialCount: Int,
    val backupVersion: Int,
    val createdAt: Long
)
