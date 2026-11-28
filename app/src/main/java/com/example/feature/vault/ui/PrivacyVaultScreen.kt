package com.example.feature.vault.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EnhancedEncryption
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.common.FormatUtils
import com.example.core.ui.components.OrbitBadgeChip
import com.example.core.ui.components.OrbitDialog
import com.example.core.ui.components.OrbitEmptyState
import com.example.core.ui.components.OrbitFloatingActionButton
import com.example.core.ui.components.OrbitPrimaryButton
import com.example.core.ui.components.OrbitSecondaryButton
import com.example.core.ui.components.OrbitSurfaceCard
import com.example.core.ui.components.OrbitTextField
import com.example.core.ui.components.OrbitTopBar
import com.example.core.ui.theme.OrbitEmerald
import com.example.core.ui.theme.OrbitMonoStyle
import com.example.core.ui.theme.OrbitSpacing
import com.example.feature.vault.repository.DecryptedVaultEntry
import com.example.feature.vault.viewmodel.VaultViewModel

@Composable
fun PrivacyVaultScreen(
    viewModel: VaultViewModel,
    onBack: () -> Unit
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val revealedIds by viewModel.revealedIds.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            OrbitTopBar(
                title = stringResource(R.string.vault_title),
                subtitle = stringResource(R.string.vault_subtitle),
                onBackClick = onBack
            )
        },
        floatingActionButton = {
            OrbitFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = Icons.Rounded.Add,
                contentDescription = stringResource(R.string.vault_add),
                modifier = Modifier.testTag("add_vault_entry_fab")
            )
        }
    ) { innerPadding ->
        if (entries.isEmpty()) {
            OrbitEmptyState(
                title = stringResource(R.string.vault_empty_title),
                subtitle = stringResource(R.string.vault_empty_subtitle),
                actionLabel = stringResource(R.string.vault_add),
                onActionClick = { showAddDialog = true },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(OrbitSpacing.md),
                verticalArrangement = Arrangement.spacedBy(OrbitSpacing.sm)
            ) {
                items(entries, key = { it.id }) { entry ->
                    VaultEntryCard(
                        entry = entry,
                        revealed = entry.id in revealedIds,
                        onToggleReveal = { viewModel.toggleReveal(entry.id) },
                        onDelete = { viewModel.deleteEntry(entry.id) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddVaultEntryDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { label, secret ->
                viewModel.addEntry(label, secret)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun VaultEntryCard(
    entry: DecryptedVaultEntry,
    revealed: Boolean,
    onToggleReveal: () -> Unit,
    onDelete: () -> Unit
) {
    OrbitSurfaceCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(OrbitSpacing.md),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.EnhancedEncryption,
                    contentDescription = null,
                    tint = OrbitEmerald
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.label,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = FormatUtils.formatDateBucket(entry.updatedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OrbitBadgeChip(text = "AES-GCM")
                IconButton(onClick = onToggleReveal) {
                    Icon(
                        imageVector = if (revealed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (revealed) "Hide secret" else "Reveal secret"
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = stringResource(R.string.action_delete)
                    )
                }
            }

            Text(
                text = if (revealed) entry.secretContent else "••••••••••••••••••••••••",
                style = OrbitMonoStyle,
                color = if (entry.decryptionFailed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
private fun AddVaultEntryDialog(
    onDismiss: () -> Unit,
    onConfirm: (label: String, secret: String) -> Unit
) {
    var label by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }

    OrbitDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.vault_add),
        icon = Icons.Rounded.EnhancedEncryption,
        iconTint = OrbitEmerald,
        actions = {
            OrbitSecondaryButton(
                text = stringResource(R.string.action_cancel),
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            )
            OrbitPrimaryButton(
                text = stringResource(R.string.action_save),
                onClick = { onConfirm(label, secret) },
                enabled = label.isNotBlank() && secret.isNotBlank(),
                modifier = Modifier.weight(1f)
            )
        }
    ) {
        OrbitTextField(
            value = label,
            onValueChange = { label = it },
            label = "Label (e.g. Router Admin / Private Link)"
        )
        OrbitTextField(
            value = secret,
            onValueChange = { secret = it },
            label = "Encrypted Content",
            singleLine = false,
            minLines = 2,
            maxLines = 4
        )
    }
}
