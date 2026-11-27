package com.example.feature.passwords.model

import com.example.core.database.entity.VaultMetadataEntity
import com.example.feature.passwords.domain.DomainMatchQuality

enum class CredentialSource(val label: String) {
    LOCAL_VAULT("Local Vault"),
    PROTON_PASS("Proton Pass")
}

data class ProviderCapabilities(
    val canListCredentials: Boolean,
    val canReadCredential: Boolean,
    val canCreateCredential: Boolean,
    val canUpdateCredential: Boolean,
    val canDeleteCredential: Boolean,
    val canGeneratePassword: Boolean
)

/**
 * Decrypted in-memory credential model. Keep lifetime short and never log or persist plaintext.
 */
data class Credential(
    val id: String,
    val title: String,
    val username: String?,
    val password: String,
    val websiteUrl: String,
    val notes: String?,
    val folderId: String?,
    val isFavorite: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val source: CredentialSource = CredentialSource.LOCAL_VAULT
)

data class CredentialListItem(
    val id: String,
    val title: String,
    val normalizedDomain: String,
    val usernameHint: String?,
    val folderId: String?,
    val isFavorite: Boolean,
    val updatedAt: Long,
    val source: CredentialSource = CredentialSource.LOCAL_VAULT
)

data class CredentialFolder(
    val id: String,
    val name: String,
    val createdAt: Long
)

data class DecryptedPasswordHistoryItem(
    val id: String,
    val password: String,
    val changedAt: Long
)

data class VaultSecuritySettings(
    val biometricEnabled: Boolean = false,
    val biometricAvailable: Boolean = false,
    val autoLockTimeoutMs: Long = VaultMetadataEntity.DEFAULT_AUTO_LOCK_MS,
    val lockOnBackground: Boolean = true,
    val clipboardClearSeconds: Int = VaultMetadataEntity.DEFAULT_CLIPBOARD_CLEAR_SEC,
    val allowHttpAutofill: Boolean = false,
    val neverSaveDomains: List<String> = emptyList()
)

enum class VaultLockState {
    LOADING,
    UNINITIALIZED,
    LOCKED,
    UNLOCKED
}

data class AutofillPromptRequest(
    val tabId: String,
    val pageUrl: String,
    val host: String,
    val formType: WebFormType,
    val hasUsernameField: Boolean,
    val hasPasswordField: Boolean,
    val hasBlockedHttpMatches: Boolean,
    val vaultLockedWithMatches: Boolean,
    val matches: List<AutofillMatchCandidate>
)

data class AutofillMatchCandidate(
    val credential: Credential,
    val matchQuality: DomainMatchQuality
)

enum class WebFormType {
    LOGIN,
    REGISTRATION,
    PASSWORD_CHANGE
}

data class PendingCredentialSaveRequest(
    val tabId: String,
    val pageUrl: String,
    val domain: String,
    val username: String,
    val password: String,
    val formType: WebFormType,
    val existingCredentialId: String?,
    val existingTitle: String?
)

data class ProtonPassStatus(
    val isInstalled: Boolean,
    val isSystemAutofillSupported: Boolean,
    val hasSystemAutofillEnabled: Boolean,
    val capabilities: ProviderCapabilities
)

data class BackupRecord(
    val id: String,
    val fileName: String,
    val credentialCount: Int,
    val backupVersion: Int,
    val createdAt: Long
)
