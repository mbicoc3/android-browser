package com.example.feature.passwords.provider

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.autofill.AutofillManager
import com.example.feature.passwords.model.AutofillMatchCandidate
import com.example.feature.passwords.model.CredentialSource
import com.example.feature.passwords.model.ProtonPassStatus
import com.example.feature.passwords.model.ProviderCapabilities
import com.example.feature.passwords.repository.LocalVaultRepository

/**
 * Explicit credential provider contract so Local Vault and external providers
 * never conflate ownership or fabricate unsupported capabilities.
 */
interface CredentialProvider {
    val source: CredentialSource
    val capabilities: ProviderCapabilities
}

/**
 * Capability-scoped integration for Proton Pass on Android:
 *  - Detects whether Proton Pass is installed and checks Android Autofill framework state.
 *  - Never attempts to read private Proton storage, cookies, or reverse-engineer private APIs.
 *  - Exposes honest capability flags and system Autofill/app hand-off intents.
 */
class ProtonPassIntegration(
    private val context: Context
) : CredentialProvider {

    override val source: CredentialSource = CredentialSource.PROTON_PASS

    override val capabilities: ProviderCapabilities = ProviderCapabilities(
        canListCredentials = false,
        canReadCredential = false,
        canCreateCredential = false,
        canUpdateCredential = false,
        canDeleteCredential = false,
        canGeneratePassword = false
    )

    fun getStatus(): ProtonPassStatus {
        val installed = isPackageInstalled(PROTON_PASS_PACKAGE)
        val autofillSupported: Boolean
        val hasEnabledAutofill: Boolean
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val afm = runCatching { context.getSystemService(AutofillManager::class.java) }.getOrNull()
            autofillSupported = runCatching { afm?.isAutofillSupported == true }.getOrDefault(false)
            hasEnabledAutofill = runCatching { afm?.hasEnabledAutofillServices() == true }.getOrDefault(false)
        } else {
            autofillSupported = false
            hasEnabledAutofill = false
        }
        return ProtonPassStatus(
            isInstalled = installed,
            isSystemAutofillSupported = autofillSupported,
            hasSystemAutofillEnabled = hasEnabledAutofill,
            capabilities = capabilities
        )
    }

    fun buildOpenProtonPassIntent(): Intent? {
        return context.packageManager.getLaunchIntentForPackage(PROTON_PASS_PACKAGE)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun buildInstallProtonPassIntent(): Intent {
        return Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=$PROTON_PASS_PACKAGE")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun buildAutofillSettingsIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(packageName, 0)
            true
        }.getOrDefault(false)
    }

    companion object {
        const val PROTON_PASS_PACKAGE = "proton.android.pass"
    }
}

/**
 * Coordinates browser-side credential matching and provider metadata without merging
 * Proton Pass data into Local Vault storage.
 */
class CredentialCoordinator(
    private val localVaultRepository: LocalVaultRepository,
    private val protonPassIntegration: ProtonPassIntegration
) {
    val localProviderCapabilities: ProviderCapabilities = ProviderCapabilities(
        canListCredentials = true,
        canReadCredential = true,
        canCreateCredential = true,
        canUpdateCredential = true,
        canDeleteCredential = true,
        canGeneratePassword = true
    )

    fun findMatchesForPage(pageUrl: String): List<AutofillMatchCandidate> {
        return localVaultRepository.findAutofillMatches(pageUrl)
    }

    fun getProtonPassStatus(): ProtonPassStatus = protonPassIntegration.getStatus()
}
