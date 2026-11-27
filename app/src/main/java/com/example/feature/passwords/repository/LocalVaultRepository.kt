package com.example.feature.passwords.repository

import android.content.Context
import com.example.core.database.dao.PasswordVaultDao
import com.example.core.database.entity.BackupMetadataEntity
import com.example.core.database.entity.CredentialEntity
import com.example.core.database.entity.CredentialFolderEntity
import com.example.core.database.entity.PasswordHistoryEntity
import com.example.core.database.entity.VaultMetadataEntity
import com.example.feature.passwords.crypto.Argon2Kdf
import com.example.feature.passwords.crypto.Argon2Params
import com.example.feature.passwords.crypto.VaultBiometricKeystoreHelper
import com.example.feature.passwords.crypto.VaultCryptoEngine
import com.example.feature.passwords.crypto.WrappedKey
import com.example.feature.passwords.crypto.wipe
import com.example.feature.passwords.domain.CredentialCsvCodec
import com.example.feature.passwords.domain.DomainMatchQuality
import com.example.feature.passwords.domain.DomainMatcher
import com.example.feature.passwords.model.AutofillMatchCandidate
import com.example.feature.passwords.model.BackupRecord
import com.example.feature.passwords.model.Credential
import com.example.feature.passwords.model.CredentialFolder
import com.example.feature.passwords.model.CredentialListItem
import com.example.feature.passwords.model.DecryptedPasswordHistoryItem
import com.example.feature.passwords.model.VaultLockState
import com.example.feature.passwords.model.VaultSecuritySettings
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.crypto.Cipher

/**
 * Offline-first, encrypted Local Vault repository owning:
 *  - Master password setup, Argon2id KDF + DEK envelope management, and lock/unlock lifecycle.
 *  - In-memory DEK caching while unlocked + inactivity timeout & app-background lock.
 *  - Encrypted CRUD, folder management, password history, portable backup/restore, and CSV I/O.
 */
class LocalVaultRepository(
    private val appContext: Context,
    private val dao: PasswordVaultDao,
    private val biometricHelper: VaultBiometricKeystoreHelper
) {
    private val exceptionHandler = CoroutineExceptionHandler { _, _ ->
        if (_lockState.value == VaultLockState.LOADING) {
            _lockState.value = if (cachedMetadata == null) VaultLockState.UNINITIALIZED else VaultLockState.LOCKED
        }
    }
    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)
    private val lockMutex = Mutex()

    @Volatile
    private var activeDek: ByteArray? = null

    @Volatile
    private var cachedUnlockedCredentials: List<Credential> = emptyList()

    @Volatile
    private var cachedMetadata: VaultMetadataEntity? = null

    private val _lockState = MutableStateFlow(VaultLockState.LOADING)
    val lockState: StateFlow<VaultLockState> = _lockState.asStateFlow()

    private val _unlockedCredentials = MutableStateFlow<List<Credential>>(emptyList())
    val unlockedCredentials: StateFlow<List<Credential>> = _unlockedCredentials.asStateFlow()

    private val _securitySettings = MutableStateFlow(VaultSecuritySettings())
    val securitySettings: StateFlow<VaultSecuritySettings> = _securitySettings.asStateFlow()

    private var inactivityTimerJob: Job? = null

    private val moshi: Moshi by lazy { Moshi.Builder().build() }
    private val stringMapAdapter by lazy {
        val type = Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
        moshi.adapter<Map<String, String>>(type)
    }
    private val stringListAdapter by lazy {
        val type = Types.newParameterizedType(List::class.java, String::class.java)
        moshi.adapter<List<String>>(type)
    }

    init {
        repoScope.launch {
            combine(dao.observeMetadata(), dao.observeAllCredentials()) { metadata, entities ->
                metadata to entities
            }.catch {
                lockMutex.withLock {
                    clearSessionSecretsLocked()
                    _lockState.value = if (cachedMetadata == null) {
                        VaultLockState.UNINITIALIZED
                    } else {
                        VaultLockState.LOCKED
                    }
                }
            }.collectLatest { (metadata, entities) ->
                lockMutex.withLock {
                    cachedMetadata = metadata
                    val biometricAvailable = runCatching {
                        biometricHelper.isBiometricHardwareAvailable(appContext)
                    }.getOrDefault(false)
                    if (metadata == null) {
                        clearSessionSecretsLocked()
                        _lockState.value = VaultLockState.UNINITIALIZED
                        _securitySettings.value = VaultSecuritySettings(biometricAvailable = biometricAvailable)
                    } else {
                        _securitySettings.value = VaultSecuritySettings(
                            biometricEnabled = metadata.biometricEnabled &&
                                metadata.keystoreWrappedDek != null &&
                                metadata.keystoreDekNonce != null,
                            biometricAvailable = biometricAvailable,
                            autoLockTimeoutMs = metadata.autoLockTimeoutMs,
                            lockOnBackground = metadata.lockOnBackground,
                            clipboardClearSeconds = metadata.clipboardClearSeconds,
                            allowHttpAutofill = metadata.allowHttpAutofill,
                            neverSaveDomains = parseNeverSaveDomains(metadata.neverSaveDomainsJson)
                        )
                        val dek = activeDek
                        if (dek != null) {
                            val decrypted = decryptAllEntities(dek, entities)
                            cachedUnlockedCredentials = decrypted
                            _unlockedCredentials.value = decrypted
                            _lockState.value = VaultLockState.UNLOCKED
                        } else {
                            _lockState.value = VaultLockState.LOCKED
                        }
                    }
                }
            }
        }
    }

    fun observeCredentialSummaries(): Flow<List<CredentialListItem>> =
        dao.observeAllCredentials().map { list ->
            list.map {
                CredentialListItem(
                    id = it.id,
                    title = it.title,
                    normalizedDomain = it.normalizedDomain,
                    usernameHint = it.usernameHint,
                    folderId = it.folderId,
                    isFavorite = it.isFavorite,
                    updatedAt = it.updatedAt
                )
            }
        }

    fun observeFolders(): Flow<List<CredentialFolder>> =
        dao.observeFolders().map { list ->
            list.map { CredentialFolder(id = it.id, name = it.name, createdAt = it.createdAt) }
        }

    fun observeBackupHistory(): Flow<List<BackupRecord>> =
        dao.observeBackupHistory().map { list ->
            list.map {
                BackupRecord(
                    id = it.id,
                    fileName = it.fileName,
                    credentialCount = it.credentialCount,
                    backupVersion = it.backupVersion,
                    createdAt = it.createdAt
                )
            }
        }

    suspend fun createVault(masterPassword: CharArray): Result<Unit> = withContext(Dispatchers.IO) {
        lockMutex.withLock {
            runCatching {
                require(masterPassword.size >= 8) { "Master password must be at least 8 characters" }
                val existing = dao.getMetadata()
                require(existing == null) { "Local Vault already initialized" }

                val salt = Argon2Kdf.generateSalt()
                val params = Argon2Params()
                val kek = Argon2Kdf.deriveKey(masterPassword, salt, params)
                val dek = VaultCryptoEngine.generateDek()
                try {
                    val wrappedDek = VaultCryptoEngine.wrapDekWithKek(dek, kek)
                    val verifier = VaultCryptoEngine.createVerifierBlob(dek)
                    val now = System.currentTimeMillis()

                    val metadata = VaultMetadataEntity(
                        id = VaultMetadataEntity.PRIMARY_ID,
                        encryptedDek = wrappedDek.encryptedKey,
                        dekNonce = wrappedDek.nonce,
                        salt = salt,
                        argon2MemoryKb = params.memoryKb,
                        argon2Iterations = params.iterations,
                        argon2Parallelism = params.parallelism,
                        verifierBlob = verifier,
                        encryptionVersion = VaultCryptoEngine.ENVELOPE_VERSION,
                        createdAt = now,
                        updatedAt = now
                    )
                    dao.upsertMetadata(metadata)
                    activeDek = dek.copyOf()
                    cachedMetadata = metadata
                    cachedUnlockedCredentials = emptyList()
                    _unlockedCredentials.value = emptyList()
                    _lockState.value = VaultLockState.UNLOCKED
                    touchActivityLocked()
                } finally {
                    kek.wipe()
                    dek.wipe()
                    masterPassword.wipe()
                }
            }
        }
    }

    suspend fun createVaultWithDeviceAuth(cipher: Cipher?): Result<Unit> = withContext(Dispatchers.IO) {
        lockMutex.withLock {
            runCatching {
                val existing = dao.getMetadata()
                require(existing == null) { "Local Vault already initialized" }

                val randomSecret = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
                val randomChars = CharArray(randomSecret.size) { i ->
                    ((randomSecret[i].toInt() and 0x3F) + 33).toChar()
                }
                randomSecret.wipe()

                val salt = Argon2Kdf.generateSalt()
                val params = Argon2Params(memoryKb = 4096, iterations = 1, parallelism = 1)
                val kek = Argon2Kdf.deriveKey(randomChars, salt, params)
                val dek = VaultCryptoEngine.generateDek()
                try {
                    val wrappedDek = VaultCryptoEngine.wrapDekWithKek(dek, kek)
                    val bioWrapped = if (cipher != null) {
                        biometricHelper.wrapDekWithCipher(dek, cipher)
                    } else {
                        biometricHelper.wrapDekWithDeviceGate(dek)
                            ?: error("Device Keystore is unavailable on this phone")
                    }
                    val verifier = VaultCryptoEngine.createVerifierBlob(dek)
                    val now = System.currentTimeMillis()

                    val metadata = VaultMetadataEntity(
                        id = VaultMetadataEntity.PRIMARY_ID,
                        encryptedDek = wrappedDek.encryptedKey,
                        dekNonce = wrappedDek.nonce,
                        salt = salt,
                        argon2MemoryKb = params.memoryKb,
                        argon2Iterations = params.iterations,
                        argon2Parallelism = params.parallelism,
                        verifierBlob = verifier,
                        keystoreWrappedDek = bioWrapped.encryptedKey,
                        keystoreDekNonce = bioWrapped.nonce,
                        biometricEnabled = true,
                        encryptionVersion = VaultCryptoEngine.ENVELOPE_VERSION,
                        createdAt = now,
                        updatedAt = now
                    )
                    dao.upsertMetadata(metadata)
                    activeDek = dek.copyOf()
                    cachedMetadata = metadata
                    cachedUnlockedCredentials = emptyList()
                    _unlockedCredentials.value = emptyList()
                    _lockState.value = VaultLockState.UNLOCKED
                    touchActivityLocked()
                } finally {
                    kek.wipe()
                    dek.wipe()
                    randomChars.wipe()
                }
            }
        }
    }

    suspend fun unlockWithMasterPassword(masterPassword: CharArray): Result<Unit> = withContext(Dispatchers.IO) {
        lockMutex.withLock {
            runCatching {
                val metadata = dao.getMetadata() ?: error("Vault is not set up")
                val params = Argon2Params(
                    memoryKb = metadata.argon2MemoryKb,
                    iterations = metadata.argon2Iterations,
                    parallelism = metadata.argon2Parallelism
                )
                val kek = Argon2Kdf.deriveKey(masterPassword, metadata.salt, params)
                try {
                    val unwrappedDek = VaultCryptoEngine.unwrapDekWithKek(
                        wrapped = WrappedKey(metadata.encryptedDek, metadata.dekNonce),
                        kek = kek
                    )
                    if (!VaultCryptoEngine.verifyDekWithCanary(unwrappedDek, metadata.verifierBlob)) {
                        unwrappedDek.wipe()
                        error("Invalid master password")
                    }
                    activateSessionWithDek(unwrappedDek)
                } finally {
                    kek.wipe()
                    masterPassword.wipe()
                }
            }
        }
    }

    fun prepareBiometricUnlockCipher(): Cipher? {
        val metadata = cachedMetadata ?: return null
        val nonce = metadata.keystoreDekNonce ?: return null
        return biometricHelper.prepareDecryptCipher(nonce)
    }

    suspend fun unlockWithBiometricCipher(cipher: Cipher): Result<Unit> = withContext(Dispatchers.IO) {
        lockMutex.withLock {
            runCatching {
                val metadata = dao.getMetadata() ?: error("Vault is not set up")
                val wrapped = metadata.keystoreWrappedDek ?: error("Phone lock / biometric unlock not configured")
                val nonce = metadata.keystoreDekNonce ?: error("Phone lock / biometric unlock nonce missing")
                val dek = biometricHelper.unwrapDekWithCipher(WrappedKey(wrapped, nonce), cipher)
                if (!VaultCryptoEngine.verifyDekWithCanary(dek, metadata.verifierBlob)) {
                    dek.wipe()
                    error("Biometric key verification failed")
                }
                activateSessionWithDek(dek)
            }
        }
    }

    suspend fun unlockWithDeviceGate(): Result<Unit> = withContext(Dispatchers.IO) {
        lockMutex.withLock {
            runCatching {
                val metadata = dao.getMetadata() ?: error("Vault is not set up")
                val wrapped = metadata.keystoreWrappedDek ?: error("Phone lock unlock not configured")
                val nonce = metadata.keystoreDekNonce ?: error("Phone lock unlock nonce missing")
                val dek = biometricHelper.unwrapDekWithDeviceGate(WrappedKey(wrapped, nonce))
                    ?: error("Phone lock key invalidated. Unlock with Master Password or reset vault.")
                if (!VaultCryptoEngine.verifyDekWithCanary(dek, metadata.verifierBlob)) {
                    dek.wipe()
                    error("Phone lock key verification failed")
                }
                activateSessionWithDek(dek)
            }
        }
    }

    suspend fun enableBiometricWithCipher(cipher: Cipher): Result<Unit> = withContext(Dispatchers.IO) {
        lockMutex.withLock {
            runCatching {
                val dek = activeDek ?: error("Unlock vault first")
                val metadata = dao.getMetadata() ?: error("Vault not initialized")
                val wrapped = biometricHelper.wrapDekWithCipher(dek, cipher)
                dao.upsertMetadata(
                    metadata.copy(
                        keystoreWrappedDek = wrapped.encryptedKey,
                        keystoreDekNonce = wrapped.nonce,
                        biometricEnabled = true,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    suspend fun enableDeviceGateBiometric(): Result<Unit> = withContext(Dispatchers.IO) {
        lockMutex.withLock {
            runCatching {
                val dek = activeDek ?: error("Unlock vault first")
                val metadata = dao.getMetadata() ?: error("Vault not initialized")
                val wrapped = biometricHelper.wrapDekWithDeviceGate(dek)
                    ?: error("Phone lock Keystore unavailable")
                dao.upsertMetadata(
                    metadata.copy(
                        keystoreWrappedDek = wrapped.encryptedKey,
                        keystoreDekNonce = wrapped.nonce,
                        biometricEnabled = true,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    suspend fun disableBiometricUnlock() = withContext(Dispatchers.IO) {
        lockMutex.withLock {
            val metadata = dao.getMetadata() ?: return@withLock
            biometricHelper.deleteKey()
            dao.upsertMetadata(
                metadata.copy(
                    keystoreWrappedDek = null,
                    keystoreDekNonce = null,
                    biometricEnabled = false,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun changeMasterPassword(
        currentPassword: CharArray,
        newPassword: CharArray
    ): Result<Unit> = withContext(Dispatchers.IO) {
        lockMutex.withLock {
            runCatching {
                require(newPassword.size >= 8) { "New master password must be at least 8 characters" }
                val metadata = dao.getMetadata() ?: error("Vault is not set up")
                val oldParams = Argon2Params(
                    metadata.argon2MemoryKb,
                    metadata.argon2Iterations,
                    metadata.argon2Parallelism
                )
                val oldKek = Argon2Kdf.deriveKey(currentPassword, metadata.salt, oldParams)
                val dek = try {
                    VaultCryptoEngine.unwrapDekWithKek(
                        WrappedKey(metadata.encryptedDek, metadata.dekNonce),
                        oldKek
                    ).also { candidate ->
                        if (!VaultCryptoEngine.verifyDekWithCanary(candidate, metadata.verifierBlob)) {
                            candidate.wipe()
                            error("Current master password is incorrect")
                        }
                    }
                } finally {
                    oldKek.wipe()
                    currentPassword.wipe()
                }

                val newSalt = Argon2Kdf.generateSalt()
                val newParams = Argon2Params()
                val newKek = Argon2Kdf.deriveKey(newPassword, newSalt, newParams)
                try {
                    val rewrapped = VaultCryptoEngine.wrapDekWithKek(dek, newKek)
                    dao.upsertMetadata(
                        metadata.copy(
                            encryptedDek = rewrapped.encryptedKey,
                            dekNonce = rewrapped.nonce,
                            salt = newSalt,
                            argon2MemoryKb = newParams.memoryKb,
                            argon2Iterations = newParams.iterations,
                            argon2Parallelism = newParams.parallelism,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    activateSessionWithDek(dek)
                } finally {
                    newKek.wipe()
                    newPassword.wipe()
                }
            }
        }
    }

    suspend fun lock() {
        lockMutex.withLock {
            clearSessionSecretsLocked()
            if (dao.getMetadata() != null) {
                _lockState.value = VaultLockState.LOCKED
            }
        }
    }

    fun onAppBackgrounded() {
        if (_securitySettings.value.lockOnBackground && _lockState.value == VaultLockState.UNLOCKED) {
            repoScope.launch { lock() }
        }
    }

    fun notifyUserActivity() {
        if (_lockState.value == VaultLockState.UNLOCKED) {
            touchActivityLocked()
        }
    }

    suspend fun resetVaultCompletely() = withContext(Dispatchers.IO) {
        lockMutex.withLock {
            clearSessionSecretsLocked()
            biometricHelper.deleteKey()
            dao.wipeAllVaultData()
            _lockState.value = VaultLockState.UNINITIALIZED
        }
    }

    suspend fun saveOrUpdateCredential(
        existingId: String?,
        title: String,
        websiteUrl: String,
        username: String?,
        password: String,
        notes: String?,
        folderId: String?,
        isFavorite: Boolean
    ): Result<String> = withContext(Dispatchers.IO) {
        lockMutex.withLock {
            runCatching {
                val dek = activeDek ?: error("Vault is locked")
                require(password.isNotEmpty()) { "Password cannot be empty" }
                val cleanUrl = websiteUrl.trim().let {
                    if (it.contains("://")) it else "https://$it"
                }
                val normalizedDomain = DomainMatcher.normalizeDomain(cleanUrl)
                    .ifBlank { cleanUrl.lowercase() }
                val id = existingId ?: UUID.randomUUID().toString()
                val now = System.currentTimeMillis()

                val previousEntity = if (existingId != null) dao.getCredentialById(existingId) else null
                if (previousEntity != null) {
                    val prevDecrypted = decryptEntity(dek, previousEntity)
                    if (prevDecrypted != null && prevDecrypted.password != password) {
                        val histId = UUID.randomUUID().toString()
                        val histBlob = VaultCryptoEngine.encryptSecretPayload(
                            dek = dek,
                            plaintext = prevDecrypted.password.toByteArray(StandardCharsets.UTF_8),
                            aad = VaultCryptoEngine.buildHistoryAad(histId, id, VaultCryptoEngine.ENVELOPE_VERSION)
                        )
                        dao.insertPasswordHistory(
                            PasswordHistoryEntity(
                                id = histId,
                                credentialId = id,
                                encryptedPassword = histBlob,
                                encryptionVersion = VaultCryptoEngine.ENVELOPE_VERSION,
                                changedAt = now
                            )
                        )
                        dao.trimPasswordHistory(id, keepCount = 10)
                    }
                }

                val payloadJson = stringMapAdapter.toJson(
                    mapOf(
                        "username" to username.orEmpty(),
                        "password" to password,
                        "websiteUrl" to cleanUrl,
                        "notes" to notes.orEmpty()
                    )
                )
                val encryptedBlob = VaultCryptoEngine.encryptSecretPayload(
                    dek = dek,
                    plaintext = payloadJson.toByteArray(StandardCharsets.UTF_8),
                    aad = VaultCryptoEngine.buildCredentialAad(id, normalizedDomain, VaultCryptoEngine.ENVELOPE_VERSION),
                    version = VaultCryptoEngine.ENVELOPE_VERSION
                )

                val entity = CredentialEntity(
                    id = id,
                    title = title.trim().ifBlank { normalizedDomain },
                    normalizedDomain = normalizedDomain,
                    usernameHint = maskUsernameHint(username),
                    folderId = folderId,
                    isFavorite = isFavorite,
                    encryptedPayload = encryptedBlob,
                    encryptionVersion = VaultCryptoEngine.ENVELOPE_VERSION,
                    createdAt = previousEntity?.createdAt ?: now,
                    updatedAt = now
                )
                dao.upsertCredential(entity)
                touchActivityLocked()
                id
            }
        }
    }

    suspend fun toggleFavorite(id: String, favorite: Boolean) = withContext(Dispatchers.IO) {
        dao.setFavorite(id, favorite, System.currentTimeMillis())
        notifyUserActivity()
    }

    suspend fun deleteCredential(id: String) = withContext(Dispatchers.IO) {
        dao.deleteCredentialById(id)
        notifyUserActivity()
    }

    suspend fun getPasswordHistory(credentialId: String): List<DecryptedPasswordHistoryItem> =
        withContext(Dispatchers.IO) {
            val dek = activeDek ?: return@withContext emptyList()
            dao.getPasswordHistorySnapshot(credentialId).mapNotNull { item ->
                runCatching {
                    val plain = VaultCryptoEngine.decryptSecretPayload(
                        dek = dek,
                        blob = item.encryptedPassword,
                        aad = VaultCryptoEngine.buildHistoryAad(item.id, item.credentialId, item.encryptionVersion)
                    )
                    val password = String(plain, StandardCharsets.UTF_8)
                    plain.wipe()
                    DecryptedPasswordHistoryItem(
                        id = item.id,
                        password = password,
                        changedAt = item.changedAt
                    )
                }.getOrNull()
            }
        }

    suspend fun createFolder(name: String) = withContext(Dispatchers.IO) {
        val clean = name.trim()
        if (clean.isBlank()) return@withContext
        dao.upsertFolder(
            CredentialFolderEntity(
                id = UUID.randomUUID().toString(),
                name = clean,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteFolder(folderId: String) = withContext(Dispatchers.IO) {
        dao.deleteFolderSafely(folderId)
    }

    suspend fun updateSecurityPolicies(
        autoLockTimeoutMs: Long? = null,
        lockOnBackground: Boolean? = null,
        clipboardClearSeconds: Int? = null,
        allowHttpAutofill: Boolean? = null
    ) = withContext(Dispatchers.IO) {
        val metadata = dao.getMetadata() ?: return@withContext
        dao.upsertMetadata(
            metadata.copy(
                autoLockTimeoutMs = autoLockTimeoutMs ?: metadata.autoLockTimeoutMs,
                lockOnBackground = lockOnBackground ?: metadata.lockOnBackground,
                clipboardClearSeconds = clipboardClearSeconds ?: metadata.clipboardClearSeconds,
                allowHttpAutofill = allowHttpAutofill ?: metadata.allowHttpAutofill,
                updatedAt = System.currentTimeMillis()
            )
        )
        touchActivityLocked()
    }

    suspend fun addNeverSaveDomain(rawUrlOrDomain: String) = withContext(Dispatchers.IO) {
        val domain = DomainMatcher.normalizeDomain(rawUrlOrDomain)
        if (domain.isBlank()) return@withContext
        val metadata = dao.getMetadata() ?: return@withContext
        val updated = (parseNeverSaveDomains(metadata.neverSaveDomainsJson) + domain).distinct().sorted()
        dao.upsertMetadata(
            metadata.copy(
                neverSaveDomainsJson = stringListAdapter.toJson(updated),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun removeNeverSaveDomain(domain: String) = withContext(Dispatchers.IO) {
        val metadata = dao.getMetadata() ?: return@withContext
        val updated = parseNeverSaveDomains(metadata.neverSaveDomainsJson).filterNot { it == domain }
        dao.upsertMetadata(
            metadata.copy(
                neverSaveDomainsJson = stringListAdapter.toJson(updated),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    fun isNeverSaveDomain(url: String): Boolean {
        val domain = DomainMatcher.normalizeDomain(url)
        if (domain.isBlank()) return false
        return domain in _securitySettings.value.neverSaveDomains
    }

    suspend fun hasLockedCredentialsForUrl(pageUrl: String): Boolean = withContext(Dispatchers.IO) {
        val domain = DomainMatcher.normalizeDomain(pageUrl)
        if (domain.isBlank()) return@withContext false
        dao.countCredentialsForDomain(domain) > 0
    }

    fun findAutofillMatches(pageUrl: String): List<AutofillMatchCandidate> {
        if (_lockState.value != VaultLockState.UNLOCKED) return emptyList()
        val allowHttp = _securitySettings.value.allowHttpAutofill
        return cachedUnlockedCredentials.mapNotNull { cred ->
            val quality = DomainMatcher.match(
                pageUrl = pageUrl,
                storedUrlOrDomain = cred.websiteUrl,
                allowSubdomains = true,
                allowInsecureHttp = allowHttp
            )
            if (quality == DomainMatchQuality.NONE) null
            else AutofillMatchCandidate(cred, quality)
        }.sortedByDescending { it.matchQuality.rank }
    }

    suspend fun exportEncryptedBackup(passphrase: CharArray, fileName: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(_lockState.value == VaultLockState.UNLOCKED) { "Unlock vault before exporting backup" }
                require(passphrase.size >= 8) { "Backup passphrase must be at least 8 characters" }
                val folders = dao.getFoldersSnapshot().map { CredentialFolder(it.id, it.name, it.createdAt) }
                val creds = cachedUnlockedCredentials
                val backupText = VaultBackupHelper.buildEncryptedBackup(passphrase, folders, creds)
                dao.insertBackupMetadata(
                    BackupMetadataEntity(
                        id = UUID.randomUUID().toString(),
                        fileName = fileName,
                        credentialCount = creds.size,
                        backupVersion = 1,
                        createdAt = System.currentTimeMillis()
                    )
                )
                backupText
            }.also {
                passphrase.wipe()
            }
        }

    suspend fun restoreEncryptedBackup(passphrase: CharArray, backupContent: String): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(_lockState.value == VaultLockState.UNLOCKED) { "Unlock Local Vault first to merge restored backup" }
                val decoded = VaultBackupHelper.parseEncryptedBackup(passphrase, backupContent)
                val existingFolderIds = dao.getFoldersSnapshot().map { it.id }.toHashSet()
                decoded.folders.forEach { folder ->
                    if (folder.id !in existingFolderIds) {
                        dao.upsertFolder(CredentialFolderEntity(folder.id, folder.name, folder.createdAt))
                    }
                }
                val existingKeys = cachedUnlockedCredentials.map {
                    Triple(
                        DomainMatcher.normalizeDomain(it.websiteUrl),
                        it.username.orEmpty().trim().lowercase(),
                        it.password
                    )
                }.toHashSet()

                var imported = 0
                decoded.credentials.forEach { cred ->
                    val key = Triple(
                        DomainMatcher.normalizeDomain(cred.websiteUrl),
                        cred.username.orEmpty().trim().lowercase(),
                        cred.password
                    )
                    if (key !in existingKeys) {
                        saveOrUpdateCredential(
                            existingId = null,
                            title = cred.title,
                            websiteUrl = cred.websiteUrl,
                            username = cred.username,
                            password = cred.password,
                            notes = cred.notes,
                            folderId = cred.folderId,
                            isFavorite = cred.isFavorite
                        ).getOrThrow()
                        existingKeys += key
                        imported++
                    }
                }
                imported
            }.also {
                passphrase.wipe()
            }
        }

    suspend fun importCsv(csvText: String): Result<Pair<Int, Int>> = withContext(Dispatchers.IO) {
        runCatching {
            require(_lockState.value == VaultLockState.UNLOCKED) { "Unlock vault before importing CSV" }
            val parsed = CredentialCsvCodec.parseCsv(csvText, cachedUnlockedCredentials)
            var count = 0
            parsed.uniqueToImport.forEach { item ->
                saveOrUpdateCredential(
                    existingId = null,
                    title = item.title,
                    websiteUrl = item.websiteUrl,
                    username = item.username,
                    password = item.password,
                    notes = item.notes,
                    folderId = null,
                    isFavorite = false
                ).getOrThrow()
                count++
            }
            count to parsed.skippedDuplicates
        }
    }

    fun exportPlaintextCsv(): Result<String> {
        if (_lockState.value != VaultLockState.UNLOCKED) {
            return Result.failure(IllegalStateException("Unlock vault before exporting"))
        }
        return Result.success(CredentialCsvCodec.exportCsv(cachedUnlockedCredentials))
    }

    private suspend fun activateSessionWithDek(dek: ByteArray) {
        activeDek?.wipe()
        activeDek = dek
        val entities = dao.getAllCredentialsSnapshot()
        val decrypted = decryptAllEntities(dek, entities)
        cachedUnlockedCredentials = decrypted
        _unlockedCredentials.value = decrypted
        _lockState.value = VaultLockState.UNLOCKED
        touchActivityLocked()
    }

    private fun clearSessionSecretsLocked() {
        inactivityTimerJob?.cancel()
        inactivityTimerJob = null
        activeDek?.wipe()
        activeDek = null
        cachedUnlockedCredentials = emptyList()
        _unlockedCredentials.value = emptyList()
    }

    private fun touchActivityLocked() {
        inactivityTimerJob?.cancel()
        val timeout = _securitySettings.value.autoLockTimeoutMs
        if (timeout <= 0L) return
        inactivityTimerJob = repoScope.launch {
            delay(timeout)
            lock()
        }
    }

    private fun decryptAllEntities(dek: ByteArray, entities: List<CredentialEntity>): List<Credential> =
        entities.mapNotNull { decryptEntity(dek, it) }

    private fun decryptEntity(dek: ByteArray, entity: CredentialEntity): Credential? {
        return runCatching {
            val plain = VaultCryptoEngine.decryptSecretPayload(
                dek = dek,
                blob = entity.encryptedPayload,
                aad = VaultCryptoEngine.buildCredentialAad(
                    entity.id,
                    entity.normalizedDomain,
                    entity.encryptionVersion
                )
            )
            val json = String(plain, StandardCharsets.UTF_8)
            plain.wipe()
            val payload = stringMapAdapter.fromJson(json).orEmpty()
            Credential(
                id = entity.id,
                title = entity.title,
                username = payload["username"]?.takeIf { it.isNotBlank() },
                password = payload["password"].orEmpty(),
                websiteUrl = payload["websiteUrl"]?.takeIf { it.isNotBlank() } ?: "https://${entity.normalizedDomain}",
                notes = payload["notes"]?.takeIf { it.isNotBlank() },
                folderId = entity.folderId,
                isFavorite = entity.isFavorite,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt
            )
        }.getOrNull()
    }

    private fun parseNeverSaveDomains(json: String): List<String> {
        return runCatching { stringListAdapter.fromJson(json).orEmpty() }.getOrDefault(emptyList())
    }

    private fun maskUsernameHint(username: String?): String? {
        val clean = username?.trim().orEmpty()
        if (clean.isEmpty()) return null
        val atIdx = clean.indexOf('@')
        if (atIdx > 1) {
            val local = clean.substring(0, atIdx)
            val domain = clean.substring(atIdx)
            val prefix = local.take(2)
            return "$prefix***$domain"
        }
        return if (clean.length <= 2) "***" else "${clean.take(2)}***"
    }
}
