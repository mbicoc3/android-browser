package com.example.feature.passwords.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.feature.passwords.domain.PasswordGenerator
import com.example.feature.passwords.domain.PasswordGeneratorOptions
import com.example.feature.passwords.domain.PasswordHealthAnalyzer
import com.example.feature.passwords.domain.PasswordHealthReport
import com.example.feature.passwords.domain.PasswordStrengthReport
import com.example.feature.passwords.model.BackupRecord
import com.example.feature.passwords.model.Credential
import com.example.feature.passwords.model.CredentialFolder
import com.example.feature.passwords.model.DecryptedPasswordHistoryItem
import com.example.feature.passwords.model.ProtonPassStatus
import com.example.feature.passwords.model.VaultLockState
import com.example.feature.passwords.model.VaultSecuritySettings
import com.example.feature.passwords.provider.ProtonPassIntegration
import com.example.feature.passwords.repository.LocalVaultRepository
import com.example.feature.passwords.util.ClipboardAutoClearHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher

enum class PasswordsSection(val label: String) {
    ALL("All"),
    LOCAL_VAULT("Local Vault"),
    PROTON_PASS("Proton Pass"),
    GENERATOR("Generator"),
    SECURITY("Security"),
    SETTINGS("Settings")
}

data class PasswordsUiState(
    val lockState: VaultLockState = VaultLockState.LOADING,
    val settings: VaultSecuritySettings = VaultSecuritySettings(),
    val credentials: List<Credential> = emptyList(),
    val folders: List<CredentialFolder> = emptyList(),
    val backupHistory: List<BackupRecord> = emptyList(),
    val protonStatus: ProtonPassStatus? = null,
    val activeSection: PasswordsSection = PasswordsSection.ALL,
    val searchQuery: String = "",
    val selectedFolderId: String? = null,
    val favoritesOnly: Boolean = false,
    val selectedCredential: Credential? = null,
    val selectedHistory: List<DecryptedPasswordHistoryItem> = emptyList(),
    val isHistoryLoading: Boolean = false,
    val healthReport: PasswordHealthReport = PasswordHealthReport(0, emptySet(), emptyList(), 100),
    val breachCountsById: Map<String, Int> = emptyMap(),
    val isCheckingBreaches: Boolean = false,
    val hasCompletedBreachCheck: Boolean = false,
    val generatorOptions: PasswordGeneratorOptions = PasswordGeneratorOptions(),
    val generatedPassword: String = PasswordGenerator.generate(),
    val generatedStrength: PasswordStrengthReport = PasswordGenerator.evaluateStrength(""),
    val isBusy: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val clipboardClearRemainingSec: Int? = null
) {
    val filteredCredentials: List<Credential>
        get() = credentials.filter { cred ->
            val matchesFolder = selectedFolderId == null || cred.folderId == selectedFolderId
            val matchesFav = !favoritesOnly || cred.isFavorite
            val query = searchQuery.trim().lowercase()
            val matchesQuery = query.isEmpty() ||
                cred.title.lowercase().contains(query) ||
                cred.websiteUrl.lowercase().contains(query) ||
                cred.username.orEmpty().lowercase().contains(query)
            matchesFolder && matchesFav && matchesQuery
        }.sortedWith(compareByDescending<Credential> { it.isFavorite }.thenByDescending { it.updatedAt })
}

class PasswordsViewModel(
    private val appContext: Context,
    private val localVaultRepository: LocalVaultRepository,
    val protonPassIntegration: ProtonPassIntegration,
    private val okHttpClient: OkHttpClient
) : ViewModel() {

    private val clipboardHelper = ClipboardAutoClearHelper(appContext, viewModelScope)

    private val _uiState = MutableStateFlow(
        PasswordsUiState(
            protonStatus = runCatching { protonPassIntegration.getStatus() }.getOrNull()
        ).let { initial ->
            initial.copy(generatedStrength = PasswordGenerator.evaluateStrength(initial.generatedPassword))
        }
    )
    val uiState: StateFlow<PasswordsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            localVaultRepository.lockState.collectLatest { lock ->
                _uiState.update { state ->
                    if (lock != VaultLockState.UNLOCKED) {
                        state.copy(
                            lockState = lock,
                            selectedCredential = null,
                            selectedHistory = emptyList(),
                            breachCountsById = emptyMap(),
                            hasCompletedBreachCheck = false
                        )
                    } else {
                        state.copy(lockState = lock)
                    }
                }
            }
        }
        viewModelScope.launch {
            localVaultRepository.securitySettings.collectLatest { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
        viewModelScope.launch {
            localVaultRepository.unlockedCredentials.collectLatest { creds ->
                val health = PasswordHealthAnalyzer.analyzeLocalHealth(creds)
                _uiState.update { state ->
                    val refreshedSelected = state.selectedCredential?.let { prev ->
                        creds.firstOrNull { it.id == prev.id }
                    }
                    state.copy(
                        credentials = creds,
                        healthReport = health,
                        selectedCredential = refreshedSelected
                    )
                }
            }
        }
        viewModelScope.launch {
            localVaultRepository.observeFolders()
                .catch { }
                .collectLatest { folders ->
                    _uiState.update { it.copy(folders = folders) }
                }
        }
        viewModelScope.launch {
            localVaultRepository.observeBackupHistory()
                .catch { }
                .collectLatest { backups ->
                    _uiState.update { it.copy(backupHistory = backups) }
                }
        }
    }

    fun selectSection(section: PasswordsSection) {
        localVaultRepository.notifyUserActivity()
        if (section == PasswordsSection.PROTON_PASS || section == PasswordsSection.ALL) {
            refreshProtonStatus()
        }
        _uiState.update { it.copy(activeSection = section) }
    }

    fun refreshProtonStatus() {
        _uiState.update {
            it.copy(protonStatus = runCatching { protonPassIntegration.getStatus() }.getOrNull())
        }
    }

    fun updateSearchQuery(query: String) {
        localVaultRepository.notifyUserActivity()
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun selectFolder(folderId: String?) {
        localVaultRepository.notifyUserActivity()
        _uiState.update { it.copy(selectedFolderId = folderId) }
    }

    fun toggleFavoritesOnly() {
        localVaultRepository.notifyUserActivity()
        _uiState.update { it.copy(favoritesOnly = !it.favoritesOnly) }
    }

    fun createVault(masterPassword: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null) }
            val result = localVaultRepository.createVault(masterPassword.toCharArray())
            _uiState.update {
                it.copy(
                    isBusy = false,
                    errorMessage = result.exceptionOrNull()?.message,
                    statusMessage = if (result.isSuccess) "Local Vault created & encrypted" else null
                )
            }
        }
    }

    fun createVaultWithDeviceAuth(cipher: Cipher?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null) }
            val result = localVaultRepository.createVaultWithDeviceAuth(cipher)
            _uiState.update {
                it.copy(
                    isBusy = false,
                    errorMessage = result.exceptionOrNull()?.message,
                    statusMessage = if (result.isSuccess) "Vault protected with Phone Lock & Biometrics" else null
                )
            }
        }
    }

    fun unlockWithPassword(masterPassword: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null) }
            val result = localVaultRepository.unlockWithMasterPassword(masterPassword.toCharArray())
            _uiState.update {
                it.copy(
                    isBusy = false,
                    errorMessage = result.exceptionOrNull()?.message
                )
            }
        }
    }

    fun prepareBiometricUnlockCipher(): Cipher? = localVaultRepository.prepareBiometricUnlockCipher()

    fun unlockWithBiometricCipher(cipher: Cipher) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null) }
            val result = localVaultRepository.unlockWithBiometricCipher(cipher)
            _uiState.update {
                it.copy(
                    isBusy = false,
                    errorMessage = result.exceptionOrNull()?.message
                )
            }
        }
    }

    fun unlockWithDeviceGate() {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null) }
            val result = localVaultRepository.unlockWithDeviceGate()
            _uiState.update {
                it.copy(
                    isBusy = false,
                    errorMessage = result.exceptionOrNull()?.message
                )
            }
        }
    }

    fun enableBiometricWithCipher(cipher: Cipher) {
        viewModelScope.launch {
            val result = localVaultRepository.enableBiometricWithCipher(cipher)
            _uiState.update {
                it.copy(
                    errorMessage = result.exceptionOrNull()?.message,
                    statusMessage = if (result.isSuccess) "Phone Lock & Biometric unlock enabled" else null
                )
            }
        }
    }

    fun enableDeviceGateBiometric() {
        viewModelScope.launch {
            val result = localVaultRepository.enableDeviceGateBiometric()
            _uiState.update {
                it.copy(
                    errorMessage = result.exceptionOrNull()?.message,
                    statusMessage = if (result.isSuccess) "Phone Lock & Biometric unlock enabled" else null
                )
            }
        }
    }

    fun disableBiometric() {
        viewModelScope.launch {
            localVaultRepository.disableBiometricUnlock()
            _uiState.update { it.copy(statusMessage = "Phone Lock & Biometric unlock disabled") }
        }
    }

    fun changeMasterPassword(currentPassword: String, newPassword: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null) }
            val result = localVaultRepository.changeMasterPassword(
                currentPassword = currentPassword.toCharArray(),
                newPassword = newPassword.toCharArray()
            )
            _uiState.update {
                it.copy(
                    isBusy = false,
                    errorMessage = result.exceptionOrNull()?.message,
                    statusMessage = if (result.isSuccess) "Master password updated" else null
                )
            }
        }
    }

    fun lockVault() {
        viewModelScope.launch {
            clipboardHelper.clearNow { }
            localVaultRepository.lock()
            _uiState.update { it.copy(clipboardClearRemainingSec = null) }
        }
    }

    fun resetVault() {
        viewModelScope.launch {
            clipboardHelper.clearNow { }
            localVaultRepository.resetVaultCompletely()
            _uiState.update {
                it.copy(
                    clipboardClearRemainingSec = null,
                    statusMessage = "Local Vault wiped and reset"
                )
            }
        }
    }

    fun selectCredential(credential: Credential?) {
        localVaultRepository.notifyUserActivity()
        if (credential == null) {
            _uiState.update { it.copy(selectedCredential = null, selectedHistory = emptyList()) }
            return
        }
        _uiState.update {
            it.copy(
                selectedCredential = credential,
                selectedHistory = emptyList(),
                isHistoryLoading = true
            )
        }
        viewModelScope.launch {
            val history = localVaultRepository.getPasswordHistory(credential.id)
            _uiState.update { it.copy(selectedHistory = history, isHistoryLoading = false) }
        }
    }

    fun saveCredential(
        existingId: String?,
        title: String,
        websiteUrl: String,
        username: String?,
        password: String,
        notes: String?,
        folderId: String?,
        isFavorite: Boolean
    ) {
        viewModelScope.launch {
            val res = localVaultRepository.saveOrUpdateCredential(
                existingId = existingId,
                title = title,
                websiteUrl = websiteUrl,
                username = username,
                password = password,
                notes = notes,
                folderId = folderId,
                isFavorite = isFavorite
            )
            _uiState.update {
                it.copy(
                    errorMessage = res.exceptionOrNull()?.message,
                    statusMessage = if (res.isSuccess) {
                        if (existingId == null) "Credential saved to Local Vault" else "Credential updated"
                    } else null
                )
            }
        }
    }

    fun toggleFavorite(credential: Credential) {
        viewModelScope.launch {
            localVaultRepository.toggleFavorite(credential.id, !credential.isFavorite)
        }
    }

    fun deleteCredential(credentialId: String) {
        viewModelScope.launch {
            localVaultRepository.deleteCredential(credentialId)
            _uiState.update {
                it.copy(
                    selectedCredential = if (it.selectedCredential?.id == credentialId) null else it.selectedCredential,
                    statusMessage = "Credential deleted"
                )
            }
        }
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            localVaultRepository.createFolder(name)
            _uiState.update { it.copy(statusMessage = "Folder saved") }
        }
    }

    fun deleteFolder(folderId: String) {
        viewModelScope.launch {
            localVaultRepository.deleteFolder(folderId)
            _uiState.update { state ->
                state.copy(
                    selectedFolderId = if (state.selectedFolderId == folderId) null else state.selectedFolderId,
                    statusMessage = "Folder deleted"
                )
            }
        }
    }

    fun copySensitiveSecret(label: String, value: String, autoClear: Boolean = true) {
        localVaultRepository.notifyUserActivity()
        val delaySec = if (autoClear) _uiState.value.settings.clipboardClearSeconds else 0
        clipboardHelper.copySensitive(
            label = label,
            value = value,
            clearAfterSeconds = delaySec
        ) { remaining ->
            _uiState.update { it.copy(clipboardClearRemainingSec = remaining) }
        }
        _uiState.update {
            it.copy(
                statusMessage = if (delaySec > 0) "$label copied · auto-clears in ${delaySec}s" else "$label copied"
            )
        }
    }

    fun clearClipboardNow() {
        clipboardHelper.clearNow {
            _uiState.update { it.copy(clipboardClearRemainingSec = null, statusMessage = "Clipboard cleared") }
        }
    }

    fun updateGeneratorOptions(transform: (PasswordGeneratorOptions) -> PasswordGeneratorOptions) {
        localVaultRepository.notifyUserActivity()
        val updated = transform(_uiState.value.generatorOptions)
        val generated = PasswordGenerator.generate(updated)
        _uiState.update {
            it.copy(
                generatorOptions = updated,
                generatedPassword = generated,
                generatedStrength = PasswordGenerator.evaluateStrength(generated)
            )
        }
    }

    fun regeneratePassword() {
        localVaultRepository.notifyUserActivity()
        val generated = PasswordGenerator.generate(_uiState.value.generatorOptions)
        _uiState.update {
            it.copy(
                generatedPassword = generated,
                generatedStrength = PasswordGenerator.evaluateStrength(generated)
            )
        }
    }

    fun runBreachCheck() {
        val creds = _uiState.value.credentials
        if (creds.isEmpty()) {
            _uiState.update { it.copy(statusMessage = "No credentials to check") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingBreaches = true, errorMessage = null) }
            val result = PasswordHealthAnalyzer.checkPwnedBreaches(creds, okHttpClient)
            _uiState.update { state ->
                if (result.isSuccess) {
                    val map = result.getOrDefault(emptyMap())
                    state.copy(
                        isCheckingBreaches = false,
                        hasCompletedBreachCheck = true,
                        breachCountsById = map,
                        statusMessage = if (map.isEmpty()) "No breached passwords found" else "${map.size} password(s) appeared in known breaches"
                    )
                } else {
                    state.copy(
                        isCheckingBreaches = false,
                        errorMessage = "Breach check failed: ${result.exceptionOrNull()?.message ?: "Network error"}"
                    )
                }
            }
        }
    }

    fun updateSecurityPolicies(
        autoLockTimeoutMs: Long? = null,
        lockOnBackground: Boolean? = null,
        clipboardClearSeconds: Int? = null,
        allowHttpAutofill: Boolean? = null
    ) {
        viewModelScope.launch {
            localVaultRepository.updateSecurityPolicies(
                autoLockTimeoutMs = autoLockTimeoutMs,
                lockOnBackground = lockOnBackground,
                clipboardClearSeconds = clipboardClearSeconds,
                allowHttpAutofill = allowHttpAutofill
            )
        }
    }

    fun addNeverSaveDomain(domain: String) {
        viewModelScope.launch {
            localVaultRepository.addNeverSaveDomain(domain)
        }
    }

    fun removeNeverSaveDomain(domain: String) {
        viewModelScope.launch {
            localVaultRepository.removeNeverSaveDomain(domain)
        }
    }

    fun exportEncryptedBackup(passphrase: String, outputUri: Uri, fileName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null) }
            val res = localVaultRepository.exportEncryptedBackup(passphrase.toCharArray(), fileName)
            val writeResult = res.mapCatching { jsonContent ->
                withContext(Dispatchers.IO) {
                    appContext.contentResolver.openOutputStream(outputUri)?.use { stream ->
                        stream.write(jsonContent.toByteArray(StandardCharsets.UTF_8))
                    } ?: error("Unable to open destination file")
                }
            }
            _uiState.update {
                it.copy(
                    isBusy = false,
                    errorMessage = writeResult.exceptionOrNull()?.message,
                    statusMessage = if (writeResult.isSuccess) "Encrypted backup exported" else null
                )
            }
        }
    }

    fun restoreEncryptedBackup(passphrase: String, inputUri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null) }
            val readResult = withContext(Dispatchers.IO) {
                runCatching {
                    appContext.contentResolver.openInputStream(inputUri)?.bufferedReader()?.use {
                        it.readText()
                    } ?: error("Unable to open backup file")
                }
            }
            val restoreResult = readResult.fold(
                onSuccess = { text ->
                    localVaultRepository.restoreEncryptedBackup(passphrase.toCharArray(), text)
                },
                onFailure = { Result.failure(it) }
            )
            _uiState.update {
                it.copy(
                    isBusy = false,
                    errorMessage = restoreResult.exceptionOrNull()?.message,
                    statusMessage = restoreResult.getOrNull()?.let { count ->
                        "Restored $count new credential(s) from encrypted backup"
                    }
                )
            }
        }
    }

    fun exportCsvToUri(outputUri: Uri) {
        viewModelScope.launch {
            val csvRes = localVaultRepository.exportPlaintextCsv()
            val writeRes = csvRes.mapCatching { csvText ->
                withContext(Dispatchers.IO) {
                    appContext.contentResolver.openOutputStream(outputUri)?.use { stream ->
                        stream.write(csvText.toByteArray(StandardCharsets.UTF_8))
                    } ?: error("Unable to write CSV file")
                }
            }
            _uiState.update {
                it.copy(
                    errorMessage = writeRes.exceptionOrNull()?.message,
                    statusMessage = if (writeRes.isSuccess) "Plaintext CSV exported" else null
                )
            }
        }
    }

    fun importCsvFromUri(inputUri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null) }
            val textRes = withContext(Dispatchers.IO) {
                runCatching {
                    appContext.contentResolver.openInputStream(inputUri)?.bufferedReader()?.use {
                        it.readText()
                    } ?: error("Unable to read CSV file")
                }
            }
            val importRes = textRes.fold(
                onSuccess = { localVaultRepository.importCsv(it) },
                onFailure = { Result.failure(it) }
            )
            _uiState.update {
                it.copy(
                    isBusy = false,
                    errorMessage = importRes.exceptionOrNull()?.message,
                    statusMessage = importRes.getOrNull()?.let { (added, skipped) ->
                        "Imported $added login(s) ($skipped duplicate(s) skipped)"
                    }
                )
            }
        }
    }

    fun reportError(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
    }

    fun clearMessages() {
        _uiState.update { it.copy(statusMessage = null, errorMessage = null) }
    }

    override fun onCleared() {
        clipboardHelper.clearNow { }
        super.onCleared()
    }
}
