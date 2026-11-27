package com.example.feature.passwords.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.core.ui.components.OrbitConfirmDialog
import com.example.core.ui.components.OrbitDialog
import com.example.core.ui.components.OrbitPrimaryButton
import com.example.core.ui.components.OrbitSecondaryButton
import com.example.core.ui.components.OrbitSectionHeader
import com.example.core.ui.components.OrbitSegmentPill
import com.example.core.ui.components.OrbitSettingSwitchRow
import com.example.core.ui.components.OrbitSurfaceCard
import com.example.core.ui.components.OrbitTextField
import com.example.core.ui.theme.OrbitSpacing
import com.example.feature.passwords.crypto.VaultBiometricKeystoreHelper
import com.example.feature.passwords.viewmodel.PasswordsUiState
import com.example.feature.passwords.viewmodel.PasswordsViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VaultSettingsTab(
    uiState: PasswordsUiState,
    viewModel: PasswordsViewModel,
    biometricHelper: VaultBiometricKeystoreHelper
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    val settings = uiState.settings
    var showChangeMaster by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var resetAcknowledge by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(OrbitSpacing.md),
        verticalArrangement = Arrangement.spacedBy(OrbitSpacing.sm)
    ) {
        if (settings.biometricAvailable && activity != null) {
            item {
                OrbitSurfaceCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(OrbitSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Phone Lock & Biometrics", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Unlock with Fingerprint, Face, PIN, Pattern, or Password",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = settings.biometricEnabled,
                            onCheckedChange = { enabled ->
                                if (!enabled) {
                                    viewModel.disableBiometric()
                                } else {
                                    val cipher = if (biometricHelper.canUseCryptoObjectAuth(activity)) {
                                        biometricHelper.prepareEncryptCipher()
                                    } else null
                                    biometricHelper.authenticate(
                                        activity = activity,
                                        title = "Enable Phone Lock & Biometric Unlock",
                                        subtitle = "Verify with fingerprint, face, PIN, pattern, or password",
                                        negativeButtonText = "Cancel",
                                        cipher = cipher,
                                        onSuccess = { unlocked ->
                                            if (unlocked != null) {
                                                viewModel.enableBiometricWithCipher(unlocked)
                                            } else {
                                                viewModel.enableDeviceGateBiometric()
                                            }
                                        },
                                        onError = { msg -> viewModel.reportError(msg) }
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }

        item {
            OrbitSettingSwitchRow(
                title = "Lock on App Background",
                subtitle = "Automatically lock decrypted vault & clear memory when leaving Orbit",
                icon = Icons.Rounded.Lock,
                checked = settings.lockOnBackground,
                onCheckedChange = { checked -> viewModel.updateSecurityPolicies(lockOnBackground = checked) },
                testTag = "vault_setting_bg_lock"
            )
        }

        item {
            OrbitSettingSwitchRow(
                title = "Allow Autofill on HTTP Sites",
                subtitle = "Keep OFF to block password autofill on unencrypted http:// pages",
                icon = Icons.Rounded.Warning,
                checked = settings.allowHttpAutofill,
                onCheckedChange = { checked -> viewModel.updateSecurityPolicies(allowHttpAutofill = checked) },
                testTag = "vault_setting_http_autofill"
            )
        }

        item {
            OrbitSurfaceCard {
                Column(
                    modifier = Modifier.padding(OrbitSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Auto-Lock Inactivity Timeout", style = MaterialTheme.typography.titleSmall)
                    val timeouts = listOf(
                        60_000L to "1 min",
                        300_000L to "5 min",
                        900_000L to "15 min",
                        0L to "Never"
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        timeouts.forEach { (ms, label) ->
                            OrbitSegmentPill(
                                label = label,
                                selected = settings.autoLockTimeoutMs == ms,
                                onClick = { viewModel.updateSecurityPolicies(autoLockTimeoutMs = ms) }
                            )
                        }
                    }

                    Text("Clipboard Auto-Clear Delay", style = MaterialTheme.typography.titleSmall)
                    val delays = listOf(15 to "15s", 30 to "30s", 60 to "60s", 0 to "Off")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        delays.forEach { (sec, label) ->
                            OrbitSegmentPill(
                                label = label,
                                selected = settings.clipboardClearSeconds == sec,
                                onClick = { viewModel.updateSecurityPolicies(clipboardClearSeconds = sec) }
                            )
                        }
                    }
                }
            }
        }

        if (settings.neverSaveDomains.isNotEmpty()) {
            item { OrbitSectionHeader(title = "Never-Save Sites") }
            item {
                OrbitSurfaceCard {
                    Column(modifier = Modifier.padding(OrbitSpacing.md)) {
                        settings.neverSaveDomains.forEach { domain ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(domain, style = MaterialTheme.typography.bodyMedium)
                                IconButton(onClick = { viewModel.removeNeverSaveDomain(domain) }) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove domain")
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            OrbitSurfaceCard {
                Column(
                    modifier = Modifier.padding(OrbitSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OrbitSecondaryButton(
                        text = "Change Master Password",
                        icon = Icons.Rounded.Password,
                        onClick = { showChangeMaster = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OrbitSecondaryButton(
                        text = "Wipe & Reset Local Vault",
                        icon = Icons.Rounded.DeleteOutline,
                        isDestructive = true,
                        onClick = {
                            resetAcknowledge = false
                            showResetConfirm = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    if (showChangeMaster) {
        ChangeMasterPasswordDialog(
            onConfirm = { oldPw, newPw ->
                showChangeMaster = false
                viewModel.changeMasterPassword(oldPw, newPw)
            },
            onDismiss = { showChangeMaster = false }
        )
    }

    if (showResetConfirm) {
        OrbitConfirmDialog(
            title = "Wipe Local Vault?",
            message = "This permanently erases all stored credentials and folders in the Local Vault.",
            confirmText = "Wipe Vault",
            dismissText = "Cancel",
            acknowledgeText = "Confirm permanent deletion",
            acknowledged = resetAcknowledge,
            onAcknowledgedChange = { resetAcknowledge = it },
            onConfirm = {
                showResetConfirm = false
                viewModel.resetVault()
            },
            onDismiss = { showResetConfirm = false }
        )
    }
}

@Composable
internal fun BackupPassphraseDialog(
    title: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var passphrase by remember { mutableStateOf("") }
    OrbitDialog(
        onDismissRequest = onDismiss,
        title = title,
        subtitle = "Argon2id + AES-256-GCM encrypted backup file",
        icon = Icons.Rounded.Key,
        actions = {
            OrbitSecondaryButton(
                text = "Cancel",
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            )
            OrbitPrimaryButton(
                text = "Continue",
                onClick = { onConfirm(passphrase) },
                enabled = passphrase.length >= 8,
                modifier = Modifier.weight(1f)
            )
        }
    ) {
        OrbitTextField(
            value = passphrase,
            onValueChange = { passphrase = it },
            label = "Backup Passphrase (min 8 chars)",
            visualTransformation = PasswordVisualTransformation()
        )
    }
}

@Composable
private fun ChangeMasterPasswordDialog(
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var currentPw by remember { mutableStateOf("") }
    var newPw by remember { mutableStateOf("") }
    var confirmPw by remember { mutableStateOf("") }

    OrbitDialog(
        onDismissRequest = onDismiss,
        title = "Change Master Password",
        subtitle = "Re-encrypts the local vault key with your new password",
        icon = Icons.Rounded.Password,
        actions = {
            OrbitSecondaryButton(
                text = "Cancel",
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            )
            OrbitPrimaryButton(
                text = "Update",
                onClick = { onConfirm(currentPw, newPw) },
                enabled = currentPw.isNotEmpty() && newPw.length >= 8 && newPw == confirmPw,
                modifier = Modifier.weight(1f)
            )
        }
    ) {
        OrbitTextField(
            value = currentPw,
            onValueChange = { currentPw = it },
            label = "Current Master Password",
            visualTransformation = PasswordVisualTransformation()
        )
        OrbitTextField(
            value = newPw,
            onValueChange = { newPw = it },
            label = "New Master Password (min 8 chars)",
            visualTransformation = PasswordVisualTransformation()
        )
        OrbitTextField(
            value = confirmPw,
            onValueChange = { confirmPw = it },
            label = "Confirm New Password",
            isError = confirmPw.isNotEmpty() && newPw != confirmPw,
            visualTransformation = PasswordVisualTransformation()
        )
    }
}
