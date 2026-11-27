package com.example.feature.passwords.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.core.common.FormatUtils
import com.example.core.ui.components.OrbitBadgeChip
import com.example.core.ui.components.OrbitConfirmDialog
import com.example.core.ui.components.OrbitPrimaryButton
import com.example.core.ui.components.OrbitSecondaryButton
import com.example.core.ui.components.OrbitSectionHeader
import com.example.core.ui.components.OrbitSurfaceCard
import com.example.core.ui.theme.OrbitEmerald
import com.example.core.ui.theme.OrbitSpacing
import com.example.feature.passwords.model.Credential
import com.example.feature.passwords.viewmodel.PasswordsUiState
import com.example.feature.passwords.viewmodel.PasswordsViewModel

private enum class PassphraseDialogMode {
    EXPORT_BACKUP,
    RESTORE_BACKUP
}

@Composable
fun SecurityDashboardTab(
    uiState: PasswordsUiState,
    viewModel: PasswordsViewModel,
    onInspectCredential: (Credential) -> Unit
) {
    val report = uiState.healthReport
    var pendingBackupUri by remember { mutableStateOf<Uri?>(null) }
    var passphraseMode by remember { mutableStateOf<PassphraseDialogMode?>(null) }
    var confirmPlainCsvExport by remember { mutableStateOf(false) }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            pendingBackupUri = uri
            passphraseMode = PassphraseDialogMode.EXPORT_BACKUP
        }
    }

    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingBackupUri = uri
            passphraseMode = PassphraseDialogMode.RESTORE_BACKUP
        }
    }

    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) viewModel.exportCsvToUri(uri)
    }

    val importCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importCsvFromUri(uri)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(OrbitSpacing.md),
        verticalArrangement = Arrangement.spacedBy(OrbitSpacing.sm)
    ) {
        item {
            OrbitSurfaceCard {
                Column(
                    modifier = Modifier.padding(OrbitSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Vault Health Score", style = MaterialTheme.typography.titleMedium)
                        OrbitBadgeChip(text = "${report.overallScore}/100")
                    }
                    LinearProgressIndicator(
                        progress = { report.overallScore / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = if (report.overallScore >= 80) OrbitEmerald else MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "${report.totalCount} saved · ${report.weakCredentialIds.size} weak · ${report.reusedCount} reused",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (report.weakCredentialIds.isNotEmpty()) {
            item { OrbitSectionHeader(title = "Weak Passwords") }
            report.weakCredentialIds.mapNotNull { id ->
                uiState.credentials.firstOrNull { it.id == id }
            }.forEach { cred ->
                item(key = "weak_${cred.id}") {
                    OrbitSurfaceCard(onClick = { onInspectCredential(cred) }) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(OrbitSpacing.md),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(cred.title, style = MaterialTheme.typography.titleSmall)
                            Text("Tap to strengthen", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        item { OrbitSectionHeader(title = "Known Data Breach Audit (k-Anonymity)") }
        item {
            OrbitSurfaceCard {
                Column(
                    modifier = Modifier.padding(OrbitSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Privacy guarantee: Only the first 5 characters of each password's SHA-1 hash are sent to Have I Been Pwned over HTTPS. Plaintext passwords never leave Orbit.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OrbitPrimaryButton(
                        text = "Check Known Breaches",
                        icon = Icons.Rounded.Security,
                        isLoading = uiState.isCheckingBreaches,
                        enabled = !uiState.isCheckingBreaches && uiState.credentials.isNotEmpty(),
                        onClick = viewModel::runBreachCheck,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (uiState.hasCompletedBreachCheck) {
                        if (uiState.breachCountsById.isEmpty()) {
                            Text("All passwords look clean against HIBP k-Anonymity database.", color = OrbitEmerald, style = MaterialTheme.typography.bodySmall)
                        } else {
                            uiState.breachCountsById.forEach { (credId, hits) ->
                                val cred = uiState.credentials.firstOrNull { it.id == credId }
                                if (cred != null) {
                                    Text(
                                        text = "• ${cred.title}: seen $hits time(s) in breaches",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item { OrbitSectionHeader(title = "Portable Encrypted Backup (.orbitvault)") }
        item {
            OrbitSurfaceCard {
                Column(
                    modifier = Modifier.padding(OrbitSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Encrypted directly with a passphrase you choose (Argon2id + AES-256-GCM) so you can restore on another phone independently of Android Keystore.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(OrbitSpacing.sm)) {
                        OrbitPrimaryButton(
                            text = "Export",
                            icon = Icons.Rounded.Backup,
                            onClick = {
                                runCatching {
                                    exportBackupLauncher.launch("orbit-vault-${System.currentTimeMillis()}.orbitvault")
                                }.onFailure {
                                    viewModel.reportError("File picker unavailable on this device")
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        OrbitSecondaryButton(
                            text = "Restore",
                            icon = Icons.Rounded.Restore,
                            onClick = {
                                runCatching {
                                    restoreBackupLauncher.launch(arrayOf("*/*"))
                                }.onFailure {
                                    viewModel.reportError("File picker unavailable on this device")
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    uiState.backupHistory.take(3).forEach { b ->
                        Text(
                            text = "Last: ${b.fileName} · ${b.credentialCount} items (${FormatUtils.formatDateBucket(b.createdAt)})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item { OrbitSectionHeader(title = "CSV Import & Export") }
        item {
            OrbitSurfaceCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(OrbitSpacing.md),
                    horizontalArrangement = Arrangement.spacedBy(OrbitSpacing.sm)
                ) {
                    OrbitSecondaryButton(
                        text = "Import CSV",
                        icon = Icons.Rounded.UploadFile,
                        onClick = {
                            runCatching {
                                importCsvLauncher.launch(arrayOf("text/*", "*/*"))
                            }.onFailure {
                                viewModel.reportError("File picker unavailable on this device")
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    OrbitSecondaryButton(
                        text = "Export CSV",
                        icon = Icons.Rounded.Warning,
                        isDestructive = true,
                        onClick = { confirmPlainCsvExport = true },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    val activeMode = passphraseMode
    val activeUri = pendingBackupUri
    if (activeMode != null && activeUri != null) {
        BackupPassphraseDialog(
            title = if (activeMode == PassphraseDialogMode.EXPORT_BACKUP) {
                "Encrypt Portable Backup"
            } else {
                "Decrypt Portable Backup"
            },
            onConfirm = { passphrase ->
                passphraseMode = null
                pendingBackupUri = null
                if (activeMode == PassphraseDialogMode.EXPORT_BACKUP) {
                    viewModel.exportEncryptedBackup(passphrase, activeUri, "orbit-vault.orbitvault")
                } else {
                    viewModel.restoreEncryptedBackup(passphrase, activeUri)
                }
            },
            onDismiss = {
                passphraseMode = null
                pendingBackupUri = null
            }
        )
    }

    if (confirmPlainCsvExport) {
        OrbitConfirmDialog(
            title = "Export Unencrypted CSV?",
            message = "CSV exports contain plaintext passwords readable by any app with file access. Use Encrypted Backup (.orbitvault) whenever possible.",
            confirmText = "Export Plaintext CSV",
            dismissText = "Cancel",
            onConfirm = {
                confirmPlainCsvExport = false
                runCatching {
                    exportCsvLauncher.launch("orbit-passwords.csv")
                }.onFailure {
                    viewModel.reportError("File picker unavailable on this device")
                }
            },
            onDismiss = { confirmPlainCsvExport = false }
        )
    }
}
