package com.example.feature.passwords.autofill

import android.webkit.WebView
import com.example.feature.passwords.domain.DomainMatchQuality
import com.example.feature.passwords.domain.DomainMatcher
import com.example.feature.passwords.model.AutofillPromptRequest
import com.example.feature.passwords.model.Credential
import com.example.feature.passwords.model.PendingCredentialSaveRequest
import com.example.feature.passwords.model.VaultLockState
import com.example.feature.passwords.model.WebFormType
import com.example.feature.passwords.provider.CredentialCoordinator
import com.example.feature.passwords.repository.LocalVaultRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.crypto.Cipher

/**
 * Coordinates in-browser WebView autofill suggestions and Save/Update credential prompts
 * without leaking secrets into UI navigation state or auto-submitting forms.
 */
class BrowserPasswordCoordinator(
    private val localVaultRepository: LocalVaultRepository,
    private val coordinator: CredentialCoordinator
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _autofillPrompt = MutableStateFlow<AutofillPromptRequest?>(null)
    val autofillPrompt: StateFlow<AutofillPromptRequest?> = _autofillPrompt.asStateFlow()

    private val _pendingSavePrompt = MutableStateFlow<PendingCredentialSaveRequest?>(null)
    val pendingSavePrompt: StateFlow<PendingCredentialSaveRequest?> = _pendingSavePrompt.asStateFlow()

    private var queuedSubmission: PendingCredentialSaveRequest? = null
    private var spaFallbackJob: Job? = null

    fun onLoginFieldFocused(
        tabId: String,
        pageUrl: String,
        formType: WebFormType,
        hasUsernameField: Boolean,
        hasPasswordField: Boolean
    ) {
        val originInfo = DomainMatcher.parseOrigin(pageUrl) ?: return
        scope.launch {
            val lockState = localVaultRepository.lockState.value
            if (lockState == VaultLockState.UNINITIALIZED) return@launch

            if (lockState == VaultLockState.LOCKED) {
                val hasLocked = localVaultRepository.hasLockedCredentialsForUrl(pageUrl)
                if (hasLocked || formType != WebFormType.LOGIN) {
                    _autofillPrompt.value = AutofillPromptRequest(
                        tabId = tabId,
                        pageUrl = pageUrl,
                        host = originInfo.host,
                        formType = formType,
                        hasUsernameField = hasUsernameField,
                        hasPasswordField = hasPasswordField,
                        hasBlockedHttpMatches = false,
                        vaultLockedWithMatches = hasLocked,
                        matches = emptyList()
                    )
                }
                return@launch
            }

            val allMatches = coordinator.findMatchesForPage(pageUrl)
            val usableMatches = allMatches.filter {
                it.matchQuality != DomainMatchQuality.BLOCKED_INSECURE_HTTP
            }
            val blockedHttp = allMatches.any {
                it.matchQuality == DomainMatchQuality.BLOCKED_INSECURE_HTTP
            }

            if (usableMatches.isNotEmpty() || blockedHttp || formType != WebFormType.LOGIN) {
                _autofillPrompt.value = AutofillPromptRequest(
                    tabId = tabId,
                    pageUrl = pageUrl,
                    host = originInfo.host,
                    formType = formType,
                    hasUsernameField = hasUsernameField,
                    hasPasswordField = hasPasswordField,
                    hasBlockedHttpMatches = blockedHttp,
                    vaultLockedWithMatches = false,
                    matches = usableMatches
                )
            }
        }
    }

    fun refreshAutofillAfterUnlock() {
        val current = _autofillPrompt.value ?: return
        onLoginFieldFocused(
            tabId = current.tabId,
            pageUrl = current.pageUrl,
            formType = current.formType,
            hasUsernameField = current.hasUsernameField,
            hasPasswordField = current.hasPasswordField
        )
    }

    suspend fun unlockWithMasterPassword(password: CharArray): Result<Unit> {
        val res = localVaultRepository.unlockWithMasterPassword(password)
        if (res.isSuccess) refreshAutofillAfterUnlock()
        return res
    }

    suspend fun unlockWithBiometricCipher(cipher: Cipher): Result<Unit> {
        val res = localVaultRepository.unlockWithBiometricCipher(cipher)
        if (res.isSuccess) refreshAutofillAfterUnlock()
        return res
    }

    suspend fun unlockWithDeviceGate(): Result<Unit> {
        val res = localVaultRepository.unlockWithDeviceGate()
        if (res.isSuccess) refreshAutofillAfterUnlock()
        return res
    }

    fun fillSelectedCredential(webView: WebView?, credential: Credential) {
        val prompt = _autofillPrompt.value ?: return
        if (webView != null) {
            WebFormInspector.fillCredentials(
                webView = webView,
                expectedPageUrl = prompt.pageUrl,
                username = credential.username.orEmpty(),
                password = credential.password
            )
        }
        localVaultRepository.notifyUserActivity()
        _autofillPrompt.value = null
    }

    fun fillGeneratedPassword(webView: WebView?, generatedPassword: String) {
        val prompt = _autofillPrompt.value ?: return
        if (webView != null) {
            WebFormInspector.fillGeneratedPassword(
                webView = webView,
                expectedPageUrl = prompt.pageUrl,
                generatedPassword = generatedPassword
            )
        }
        _autofillPrompt.value = null
    }

    fun allowHttpOnceAndRefresh() {
        val prompt = _autofillPrompt.value ?: return
        scope.launch {
            localVaultRepository.updateSecurityPolicies(allowHttpAutofill = true)
            refreshAutofillAfterUnlock()
        }
    }

    fun onCredentialFormSubmitted(
        tabId: String,
        pageUrl: String,
        formType: WebFormType,
        username: String,
        password: String
    ) {
        if (password.isBlank()) return
        if (localVaultRepository.lockState.value == VaultLockState.UNINITIALIZED) return
        if (localVaultRepository.isNeverSaveDomain(pageUrl)) return
        val domain = DomainMatcher.normalizeDomain(pageUrl)
        if (domain.isBlank()) return

        val unlockedMatches = coordinator.findMatchesForPage(pageUrl)
            .filter { it.matchQuality != DomainMatchQuality.BLOCKED_INSECURE_HTTP }
            .map { it.credential }

        val matchingAccount = unlockedMatches.firstOrNull { cred ->
            if (username.isNotBlank()) {
                cred.username.orEmpty().equals(username, ignoreCase = true)
            } else {
                unlockedMatches.size == 1
            }
        }

        if (matchingAccount != null && matchingAccount.password == password) {
            return
        }

        val candidate = PendingCredentialSaveRequest(
            tabId = tabId,
            pageUrl = pageUrl,
            domain = domain,
            username = username,
            password = password,
            formType = formType,
            existingCredentialId = matchingAccount?.id,
            existingTitle = matchingAccount?.title
        )
        queuedSubmission = candidate
        _autofillPrompt.value = null

        spaFallbackJob?.cancel()
        spaFallbackJob = scope.launch {
            delay(1_600)
            promoteQueuedSubmissionIfValid(tabId, currentUrl = null)
        }
    }

    fun onPageNavigated(tabId: String, newUrl: String) {
        if (_autofillPrompt.value?.tabId == tabId && _autofillPrompt.value?.pageUrl != newUrl) {
            _autofillPrompt.value = null
        }
        promoteQueuedSubmissionIfValid(tabId, currentUrl = newUrl)
    }

    private fun promoteQueuedSubmissionIfValid(tabId: String, currentUrl: String?) {
        val pending = queuedSubmission ?: return
        if (pending.tabId != tabId) return
        if (currentUrl != null && DomainMatcher.normalizeDomain(currentUrl) != pending.domain) {
            // Cross-domain SSO redirect or external hop: keep prompt only if same site
            return
        }
        if (localVaultRepository.lockState.value != VaultLockState.UNLOCKED) {
            // Do not silently persist or show plaintext if vault is locked
            return
        }
        spaFallbackJob?.cancel()
        queuedSubmission = null
        _pendingSavePrompt.value = pending
    }

    fun confirmSave(title: String, saveAsNew: Boolean) {
        val pending = _pendingSavePrompt.value ?: return
        _pendingSavePrompt.value = null
        scope.launch {
            localVaultRepository.saveOrUpdateCredential(
                existingId = if (saveAsNew) null else pending.existingCredentialId,
                title = title.ifBlank { pending.domain },
                websiteUrl = pending.pageUrl,
                username = pending.username,
                password = pending.password,
                notes = null,
                folderId = null,
                isFavorite = false
            )
        }
    }

    fun neverSaveForPendingSite() {
        val pending = _pendingSavePrompt.value ?: return
        _pendingSavePrompt.value = null
        scope.launch {
            localVaultRepository.addNeverSaveDomain(pending.pageUrl)
        }
    }

    fun dismissAutofillPrompt() {
        _autofillPrompt.value = null
    }

    fun dismissSavePrompt() {
        _pendingSavePrompt.value = null
        queuedSubmission = null
    }

    fun clearTabState(tabId: String) {
        if (_autofillPrompt.value?.tabId == tabId) _autofillPrompt.value = null
        if (_pendingSavePrompt.value?.tabId == tabId) _pendingSavePrompt.value = null
        if (queuedSubmission?.tabId == tabId) queuedSubmission = null
    }
}
