package com.example.feature.passwords.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.ui.components.OrbitFloatingActionButton
import com.example.core.ui.components.OrbitGhostButton
import com.example.core.ui.components.OrbitNoticeBanner
import com.example.core.ui.components.OrbitSegmentPill
import com.example.core.ui.components.OrbitTopBar
import com.example.core.ui.theme.OrbitSpacing
import com.example.feature.passwords.crypto.VaultBiometricKeystoreHelper
import com.example.feature.passwords.model.Credential
import com.example.feature.passwords.model.VaultLockState
import com.example.feature.passwords.viewmodel.PasswordsSection
import com.example.feature.passwords.viewmodel.PasswordsViewModel

@Composable
fun PasswordsScreen(
    viewModel: PasswordsViewModel,
    biometricHelper: VaultBiometricKeystoreHelper,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    if (uiState.lockState != VaultLockState.UNLOCKED) {
        VaultGateScreen(
            uiState = uiState,
            viewModel = viewModel,
            biometricHelper = biometricHelper,
            onBack = onBack
        )
        return
    }

    var editingCredential by remember { mutableStateOf<Credential?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showFoldersDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.statusMessage, uiState.errorMessage) {
        val msg = uiState.errorMessage ?: uiState.statusMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            OrbitTopBar(
                title = "Password Manager",
                subtitle = "${uiState.credentials.size} encrypted in Local Vault",
                onBackClick = onBack,
                actions = {
                    IconButton(
                        onClick = viewModel::lockVault,
                        modifier = Modifier.testTag("passwords_lock_now_button")
                    ) {
                        Icon(Icons.Rounded.Lock, contentDescription = "Lock vault")
                    }
                }
            )
        },
        floatingActionButton = {
            if (uiState.activeSection == PasswordsSection.ALL ||
                uiState.activeSection == PasswordsSection.LOCAL_VAULT
            ) {
                OrbitFloatingActionButton(
                    onClick = { showAddDialog = true },
                    icon = Icons.Rounded.Add,
                    contentDescription = "Add password",
                    modifier = Modifier.testTag("passwords_add_fab")
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            uiState.clipboardClearRemainingSec?.let { remaining ->
                OrbitNoticeBanner(
                    text = "Clipboard clears in ${remaining}s",
                    icon = Icons.Rounded.ContentPaste,
                    modifier = Modifier.padding(horizontal = OrbitSpacing.md, vertical = 4.dp),
                    trailing = {
                        OrbitGhostButton(
                            text = "Clear Now",
                            icon = Icons.Rounded.Close,
                            onClick = viewModel::clearClipboardNow
                        )
                    }
                )
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = OrbitSpacing.md, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(PasswordsSection.entries) { section ->
                    OrbitSegmentPill(
                        label = section.label,
                        selected = uiState.activeSection == section,
                        onClick = { viewModel.selectSection(section) },
                        modifier = Modifier.testTag("passwords_section_${section.name.lowercase()}")
                    )
                }
            }

            when (uiState.activeSection) {
                PasswordsSection.ALL -> CredentialsListTab(
                    uiState = uiState,
                    viewModel = viewModel,
                    showProtonBanner = true,
                    onAddClick = { showAddDialog = true },
                    onManageFolders = { showFoldersDialog = true },
                    onOpenProtonTab = { viewModel.selectSection(PasswordsSection.PROTON_PASS) }
                )
                PasswordsSection.LOCAL_VAULT -> CredentialsListTab(
                    uiState = uiState,
                    viewModel = viewModel,
                    showProtonBanner = false,
                    onAddClick = { showAddDialog = true },
                    onManageFolders = { showFoldersDialog = true },
                    onOpenProtonTab = {}
                )
                PasswordsSection.PROTON_PASS -> ProtonPassTab(
                    status = uiState.protonStatus,
                    protonPassIntegration = viewModel.protonPassIntegration,
                    onRefreshStatus = viewModel::refreshProtonStatus,
                    onError = viewModel::reportError
                )
                PasswordsSection.GENERATOR -> PasswordGeneratorTab(
                    uiState = uiState,
                    viewModel = viewModel
                )
                PasswordsSection.SECURITY -> SecurityDashboardTab(
                    uiState = uiState,
                    viewModel = viewModel,
                    onInspectCredential = { cred -> viewModel.selectCredential(cred) }
                )
                PasswordsSection.SETTINGS -> VaultSettingsTab(
                    uiState = uiState,
                    viewModel = viewModel,
                    biometricHelper = biometricHelper
                )
            }
        }
    }

    uiState.selectedCredential?.let { selected ->
        CredentialDetailDialog(
            credential = selected,
            folders = uiState.folders,
            history = uiState.selectedHistory,
            onCopyUsername = {
                viewModel.copySensitiveSecret("Username", selected.username.orEmpty(), autoClear = false)
            },
            onCopyPassword = {
                viewModel.copySensitiveSecret("Password", selected.password, autoClear = true)
            },
            onEdit = {
                editingCredential = selected
                viewModel.selectCredential(null)
            },
            onDelete = { viewModel.deleteCredential(selected.id) },
            onDismiss = { viewModel.selectCredential(null) }
        )
    }

    if (showAddDialog || editingCredential != null) {
        AddEditCredentialDialog(
            initial = editingCredential,
            folders = uiState.folders,
            onSave = { existingId, title, websiteUrl, username, password, notes, folderId, isFavorite ->
                viewModel.saveCredential(
                    existingId = existingId,
                    title = title,
                    websiteUrl = websiteUrl,
                    username = username,
                    password = password,
                    notes = notes,
                    folderId = folderId,
                    isFavorite = isFavorite
                )
                showAddDialog = false
                editingCredential = null
            },
            onDismiss = {
                showAddDialog = false
                editingCredential = null
            }
        )
    }

    if (showFoldersDialog) {
        FolderManagerDialog(
            folders = uiState.folders,
            onCreateFolder = viewModel::createFolder,
            onDeleteFolder = viewModel::deleteFolder,
            onDismiss = { showFoldersDialog = false }
        )
    }
}
