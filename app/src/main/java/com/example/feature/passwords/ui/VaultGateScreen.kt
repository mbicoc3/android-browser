package com.example.feature.passwords.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.core.ui.components.OrbitBadgeChip
import com.example.core.ui.components.OrbitConfirmDialog
import com.example.core.ui.components.OrbitGhostButton
import com.example.core.ui.components.OrbitPrimaryButton
import com.example.core.ui.components.OrbitSecondaryButton
import com.example.core.ui.components.OrbitSurfaceCard
import com.example.core.ui.components.OrbitTextField
import com.example.core.ui.components.OrbitTopBar
import com.example.core.ui.theme.OrbitEmerald
import com.example.core.ui.theme.OrbitSpacing
import com.example.feature.passwords.crypto.VaultBiometricKeystoreHelper
import com.example.feature.passwords.domain.PasswordGenerator
import com.example.feature.passwords.domain.PasswordStrength
import com.example.feature.passwords.model.VaultLockState
import com.example.feature.passwords.viewmodel.PasswordsUiState
import com.example.feature.passwords.viewmodel.PasswordsViewModel

internal tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}

@Composable
fun VaultGateScreen(
    uiState: PasswordsUiState,
    viewModel: PasswordsViewModel,
    biometricHelper: VaultBiometricKeystoreHelper,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }

    Scaffold(
        topBar = {
            OrbitTopBar(
                title = "Password Manager",
                subtitle = "Argon2id + AES-256-GCM Local Vault",
                onBackClick = onBack
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(OrbitSpacing.md),
            contentAlignment = Alignment.Center
        ) {
            when (uiState.lockState) {
                VaultLockState.LOADING -> {
                    CircularProgressIndicator()
                }
                VaultLockState.UNINITIALIZED -> {
                    VaultOnboardingCard(
                        isBusy = uiState.isBusy,
                        errorMessage = uiState.errorMessage,
                        canUseDeviceAuth = uiState.settings.biometricAvailable && activity != null,
                        onSetupWithDeviceAuth = {
                            val act = activity ?: return@VaultOnboardingCard
                            val cipher = if (biometricHelper.canUseCryptoObjectAuth(act)) {
                                biometricHelper.prepareEncryptCipher()
                            } else null
                            biometricHelper.authenticate(
                                activity = act,
                                title = "Protect Vault with Phone Lock",
                                subtitle = "Use your phone PIN, pattern, password, fingerprint, or face",
                                negativeButtonText = "Cancel",
                                cipher = cipher,
                                onSuccess = { unlockedCipher ->
                                    viewModel.createVaultWithDeviceAuth(unlockedCipher)
                                },
                                onError = { msg -> viewModel.reportError(msg) }
                            )
                        },
                        onCreateVault = { master -> viewModel.createVault(master) }
                    )
                }
                VaultLockState.LOCKED -> {
                    VaultLockedCard(
                        uiState = uiState,
                        canUseBiometric = uiState.settings.biometricEnabled && activity != null,
                        onUnlockPassword = { pw -> viewModel.unlockWithPassword(pw) },
                        onUnlockBiometric = {
                            val act = activity ?: return@VaultLockedCard
                            val useDeviceGate = biometricHelper.hasDeviceGateKey()
                            val cipher = if (useDeviceGate) null else viewModel.prepareBiometricUnlockCipher()
                            if (cipher == null && !useDeviceGate) {
                                viewModel.reportError("Phone lock / biometric key invalidated. Unlock using Master Password or reset vault.")
                                return@VaultLockedCard
                            }
                            biometricHelper.authenticate(
                                activity = act,
                                title = "Unlock Orbit Password Vault",
                                subtitle = "Use your fingerprint, face, PIN, pattern, or phone password",
                                negativeButtonText = "Use Master Password",
                                cipher = cipher,
                                onSuccess = { unlockedCipher ->
                                    if (unlockedCipher != null) {
                                        viewModel.unlockWithBiometricCipher(unlockedCipher)
                                    } else {
                                        viewModel.unlockWithDeviceGate()
                                    }
                                },
                                onError = { msg -> viewModel.reportError(msg) }
                            )
                        },
                        onResetVault = { viewModel.resetVault() }
                    )
                }
                VaultLockState.UNLOCKED -> Unit
            }
        }
    }
}

@Composable
private fun VaultOnboardingCard(
    isBusy: Boolean,
    errorMessage: String?,
    canUseDeviceAuth: Boolean,
    onSetupWithDeviceAuth: () -> Unit,
    onCreateVault: (String) -> Unit
) {
    var masterPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    val strength = remember(masterPassword) { PasswordGenerator.evaluateStrength(masterPassword) }
    val mismatch = confirmPassword.isNotEmpty() && masterPassword != confirmPassword
    val canCreate = masterPassword.length >= 8 && masterPassword == confirmPassword && !isBusy

    OrbitSurfaceCard(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 520.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(OrbitSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(OrbitSpacing.sm)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Column {
                    Text("Set Up Local Vault", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Zero-cloud · Offline-first encrypted storage",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (canUseDeviceAuth) {
                OrbitSurfaceCard {
                    Column(
                        modifier = Modifier.padding(OrbitSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Quick Phone Lock Setup", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "Use your phone's screen lock (Fingerprint, Face, PIN, Pattern, or Password) to lock and unlock the vault.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OrbitPrimaryButton(
                            text = "Use Phone Lock & Biometrics",
                            icon = Icons.Rounded.Fingerprint,
                            enabled = !isBusy,
                            onClick = onSetupWithDeviceAuth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("vault_setup_device_auth_button")
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                    Text(
                        "OR MASTER PASSWORD",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                }
            } else {
                OrbitSurfaceCard {
                    Row(
                        modifier = Modifier.padding(OrbitSpacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Your Master Password is never stored or uploaded anywhere. If you forget it without an encrypted backup file, vault data cannot be recovered.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            OrbitTextField(
                value = masterPassword,
                onValueChange = { masterPassword = it },
                label = "Master Password (min 8 characters)",
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            imageVector = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = "Toggle visibility"
                        )
                    }
                },
                modifier = Modifier.testTag("vault_setup_master_input")
            )

            if (masterPassword.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LinearProgressIndicator(
                        progress = { strength.strength.scorePercent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = when (strength.strength) {
                            PasswordStrength.WEAK -> MaterialTheme.colorScheme.error
                            PasswordStrength.FAIR -> MaterialTheme.colorScheme.tertiary
                            PasswordStrength.GOOD -> MaterialTheme.colorScheme.primary
                            PasswordStrength.STRONG -> OrbitEmerald
                        }
                    )
                    Text(
                        text = "Strength: ${strength.strength.label} (${strength.entropyBits} bits)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OrbitTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = "Confirm Master Password",
                isError = mismatch,
                supportingText = if (mismatch) {
                    { Text("Passwords do not match") }
                } else null,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (canCreate) onCreateVault(masterPassword) }),
                modifier = Modifier.testTag("vault_setup_confirm_input")
            )

            errorMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            OrbitPrimaryButton(
                text = "Create Encrypted Vault",
                onClick = { onCreateVault(masterPassword) },
                enabled = canCreate,
                isLoading = isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("vault_setup_create_button")
            )
        }
    }
}

@Composable
private fun VaultLockedCard(
    uiState: PasswordsUiState,
    canUseBiometric: Boolean,
    onUnlockPassword: (String) -> Unit,
    onUnlockBiometric: () -> Unit,
    onResetVault: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var showResetStep1 by remember { mutableStateOf(false) }
    var resetAcknowledge by remember { mutableStateOf(false) }
    var autoPrompted by remember { mutableStateOf(false) }

    LaunchedEffect(canUseBiometric) {
        if (canUseBiometric && !autoPrompted) {
            autoPrompted = true
            onUnlockBiometric()
        }
    }

    OrbitSurfaceCard(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 520.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(OrbitSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(OrbitSpacing.sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                    Text("Local Vault Locked", style = MaterialTheme.typography.titleLarge)
                }
                OrbitBadgeChip(text = "AES-256")
            }

            if (canUseBiometric) {
                Text(
                    text = "Verify with your phone's Fingerprint, Face, PIN, Pattern, or Password to decrypt stored credentials.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OrbitPrimaryButton(
                    text = "Unlock with Phone Lock / Biometrics",
                    icon = Icons.Rounded.Fingerprint,
                    enabled = !uiState.isBusy,
                    onClick = onUnlockBiometric,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vault_biometric_unlock_button")
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                    Text(
                        "OR MASTER PASSWORD",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                }
            } else {
                Text(
                    text = "Enter your Master Password to decrypt stored credentials in memory.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OrbitTextField(
                value = password,
                onValueChange = { password = it },
                label = "Master Password",
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { if (password.isNotEmpty() && !uiState.isBusy) onUnlockPassword(password) }
                ),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            imageVector = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = "Toggle password"
                        )
                    }
                },
                modifier = Modifier.testTag("vault_unlock_password_input")
            )

            uiState.errorMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            if (canUseBiometric) {
                OrbitSecondaryButton(
                    text = "Unlock with Master Password",
                    onClick = { onUnlockPassword(password) },
                    enabled = password.isNotEmpty() && !uiState.isBusy,
                    isLoading = uiState.isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vault_unlock_button")
                )
            } else {
                OrbitPrimaryButton(
                    text = "Unlock Vault",
                    onClick = { onUnlockPassword(password) },
                    enabled = password.isNotEmpty() && !uiState.isBusy,
                    isLoading = uiState.isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vault_unlock_button")
                )
            }

            Spacer(modifier = Modifier.height(2.dp))
            OrbitGhostButton(
                text = "Forgot Master Password? Reset Vault",
                isDestructive = true,
                onClick = {
                    resetAcknowledge = false
                    showResetStep1 = true
                },
                modifier = Modifier
                    .align(Alignment.End)
                    .testTag("vault_forgot_reset_button")
            )
        }
    }

    if (showResetStep1) {
        OrbitConfirmDialog(
            title = "Reset & Wipe Local Vault?",
            message = "Because Orbit cannot recover forgotten Master Passwords, resetting the vault permanently deletes all saved logins, folders, and password history from this device.",
            confirmText = "Wipe Vault",
            dismissText = "Cancel",
            acknowledgeText = "I understand all encrypted vault entries will be permanently erased.",
            acknowledged = resetAcknowledge,
            onAcknowledgedChange = { resetAcknowledge = it },
            onConfirm = {
                showResetStep1 = false
                onResetVault()
            },
            onDismiss = { showResetStep1 = false }
        )
    }
}
