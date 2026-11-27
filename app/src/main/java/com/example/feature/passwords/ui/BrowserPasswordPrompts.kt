package com.example.feature.passwords.ui

import android.webkit.WebView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.ui.components.OrbitDialog
import com.example.core.ui.components.OrbitGhostButton
import com.example.core.ui.components.OrbitModalBottomSheet
import com.example.core.ui.components.OrbitPrimaryButton
import com.example.core.ui.components.OrbitSecondaryButton
import com.example.core.ui.components.OrbitSheetActionRow
import com.example.core.ui.components.OrbitSheetHairlineDivider
import com.example.core.ui.components.OrbitSheetHeader
import com.example.core.ui.components.OrbitSheetPanel
import com.example.core.ui.components.OrbitSheetPrimaryButton
import com.example.core.ui.components.OrbitSheetSecondaryButton
import com.example.core.ui.components.OrbitSheetStatusPill
import com.example.core.ui.components.OrbitTextField
import com.example.core.ui.theme.OrbitMonoStyle
import com.example.feature.passwords.autofill.BrowserPasswordCoordinator
import com.example.feature.passwords.crypto.VaultBiometricKeystoreHelper
import com.example.feature.passwords.domain.PasswordGenerator
import com.example.feature.passwords.model.AutofillPromptRequest
import com.example.feature.passwords.model.CredentialSource
import com.example.feature.passwords.model.PendingCredentialSaveRequest
import com.example.feature.passwords.model.ProtonPassStatus
import com.example.feature.passwords.model.WebFormType
import com.example.feature.passwords.provider.ProtonPassIntegration
import com.example.feature.passwords.repository.LocalVaultRepository
import com.example.feature.sandbox.model.SandboxModel
import kotlinx.coroutines.launch

@Composable
fun BrowserAutofillPromptSheet(
    request: AutofillPromptRequest,
    webViewProvider: () -> WebView?,
    coordinator: BrowserPasswordCoordinator,
    localVaultRepository: LocalVaultRepository,
    biometricHelper: VaultBiometricKeystoreHelper,
    protonPassIntegration: ProtonPassIntegration,
    protonStatus: ProtonPassStatus,
    biometricEnabled: Boolean,
    activeSandbox: SandboxModel? = null,
    onCredentialSelectedInSandbox: ((sandboxId: String, credentialId: String) -> Unit)? = null
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    val scope = rememberCoroutineScope()
    var masterPassword by remember { mutableStateOf("") }
    var unlockError by remember { mutableStateOf<String?>(null) }
    var generatedPassword by remember(request.pageUrl) { mutableStateOf(PasswordGenerator.generate()) }

    OrbitModalBottomSheet(onDismissRequest = coordinator::dismissAutofillPrompt) {
        OrbitSheetHeader(
            title = when (request.formType) {
                WebFormType.REGISTRATION -> "Sign Up · ${request.host}"
                WebFormType.PASSWORD_CHANGE -> "Password Change · ${request.host}"
                WebFormType.LOGIN -> "Orbit Autofill · ${request.host}"
            },
            subtitle = "Tap a saved login — Orbit never auto-submits forms",
            icon = Icons.Rounded.Key
        )

        if (request.hasBlockedHttpMatches) {
            OrbitSheetPanel {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Rounded.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Text(
                            text = "Autofill blocked on insecure HTTP page (${request.host}). Only enable if you trust this local/private network.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    OrbitSheetSecondaryButton(
                        text = "Allow HTTP Autofill",
                        onClick = coordinator::allowHttpOnceAndRefresh,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        if (request.vaultLockedWithMatches) {
            OrbitSheetPanel {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "Local Vault Locked",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                    OrbitTextField(
                        value = masterPassword,
                        onValueChange = { masterPassword = it },
                        label = "Master Password",
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    unlockError?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OrbitSheetPrimaryButton(
                            text = "Unlock",
                            enabled = masterPassword.isNotEmpty(),
                            onClick = {
                                scope.launch {
                                    val res = coordinator.unlockWithMasterPassword(masterPassword.toCharArray())
                                    masterPassword = ""
                                    unlockError = res.exceptionOrNull()?.message
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        if (biometricEnabled && activity != null) {
                            OrbitSheetSecondaryButton(
                                text = "Phone Lock",
                                icon = Icons.Rounded.Fingerprint,
                                onClick = {
                                    val useDeviceGate = biometricHelper.hasDeviceGateKey()
                                    val cipher = if (useDeviceGate) null else localVaultRepository.prepareBiometricUnlockCipher()
                                    if (cipher == null && !useDeviceGate) {
                                        unlockError = "Use Master Password"
                                        return@OrbitSheetSecondaryButton
                                    }
                                    biometricHelper.authenticate(
                                        activity = activity,
                                        title = "Unlock Orbit Autofill",
                                        subtitle = "${request.host} · Fingerprint, Face, or Phone Lock",
                                        negativeButtonText = "Password",
                                        cipher = cipher,
                                        onSuccess = { unlocked ->
                                            scope.launch {
                                                val res = if (unlocked != null) {
                                                    coordinator.unlockWithBiometricCipher(unlocked)
                                                } else {
                                                    coordinator.unlockWithDeviceGate()
                                                }
                                                unlockError = res.exceptionOrNull()?.message
                                            }
                                        },
                                        onError = { msg -> unlockError = msg }
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        if (request.matches.isNotEmpty()) {
            val preferredId = activeSandbox?.preferredCredentialId
            val orderedMatches = remember(request.matches, preferredId) {
                if (preferredId == null) request.matches
                else request.matches.sortedByDescending { it.credential.id == preferredId }
            }
            OrbitSheetPanel {
                orderedMatches.forEachIndexed { index, candidate ->
                    if (index > 0) OrbitSheetHairlineDivider()
                    val cred = candidate.credential
                    val isSandboxPreferred = activeSandbox != null && cred.id == preferredId
                    OrbitSheetActionRow(
                        title = cred.title,
                        subtitle = cred.username?.takeIf { it.isNotBlank() } ?: "No username",
                        icon = Icons.Rounded.Key,
                        onClick = {
                            if (activeSandbox != null) {
                                onCredentialSelectedInSandbox?.invoke(activeSandbox.id, cred.id)
                            }
                            coordinator.fillSelectedCredential(webViewProvider(), cred)
                        },
                        trailing = {
                            OrbitSheetStatusPill(
                                text = if (isSandboxPreferred) {
                                    activeSandbox!!.name
                                } else {
                                    CredentialSource.LOCAL_VAULT.label
                                }
                            )
                        },
                        modifier = Modifier.testTag("autofill_candidate_${cred.id}")
                    )
                }
            }
        }

        if (request.formType != WebFormType.LOGIN && request.hasPasswordField) {
            OrbitSheetPanel {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "Generate Strong Password",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                        IconButton(onClick = { generatedPassword = PasswordGenerator.generate() }) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Regenerate password")
                        }
                    }
                    Text(
                        text = generatedPassword,
                        style = OrbitMonoStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    OrbitSheetPrimaryButton(
                        text = "Fill Generated Password",
                        onClick = { coordinator.fillGeneratedPassword(webViewProvider(), generatedPassword) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        if (protonStatus.isInstalled) {
            OrbitSheetSecondaryButton(
                text = "Open Proton Pass",
                icon = Icons.AutoMirrored.Rounded.OpenInNew,
                onClick = {
                    protonPassIntegration.buildOpenProtonPassIntent()?.let { intent ->
                        runCatching { context.startActivity(intent) }
                            .onFailure { unlockError = "Unable to open Proton Pass" }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun SaveOrUpdateCredentialDialog(
    request: PendingCredentialSaveRequest,
    onConfirmSave: (title: String, saveAsNew: Boolean) -> Unit,
    onNeverForSite: () -> Unit,
    onDismiss: () -> Unit
) {
    val isUpdate = request.existingCredentialId != null
    var title by remember(request) { mutableStateOf(request.existingTitle ?: request.domain) }
    var showPass by remember { mutableStateOf(false) }

    OrbitDialog(
        onDismissRequest = onDismiss,
        title = if (isUpdate) "Update Saved Password?" else "Save Password to Local Vault?",
        subtitle = request.domain,
        icon = Icons.Rounded.Key,
        actions = {
            OrbitSecondaryButton(
                text = "Not now",
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            )
            OrbitPrimaryButton(
                text = if (isUpdate) "Update" else "Save",
                onClick = { onConfirmSave(title, false) },
                modifier = Modifier.weight(1f)
            )
        }
    ) {
        OrbitTextField(
            value = title,
            onValueChange = { title = it },
            label = "Account Title"
        )
        if (request.username.isNotBlank()) {
            Text(
                text = "Username: ${request.username}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OrbitTextField(
            value = request.password,
            onValueChange = {},
            readOnly = true,
            label = "Password",
            visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPass = !showPass }) {
                    Icon(
                        imageVector = if (showPass) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = "Toggle password"
                    )
                }
            }
        )
        if (isUpdate) {
            OrbitGhostButton(
                text = "Save as a separate new entry",
                onClick = { onConfirmSave(title, true) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        OrbitGhostButton(
            text = "Never save for ${request.domain}",
            isDestructive = true,
            onClick = onNeverForSite,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
