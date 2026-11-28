package com.example.feature.passwords.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.core.common.FormatUtils
import com.example.core.ui.components.OrbitBadgeChip
import com.example.core.ui.components.OrbitCheckboxRow
import com.example.core.ui.components.OrbitConfirmDialog
import com.example.core.ui.components.OrbitDialog
import com.example.core.ui.components.OrbitGhostButton
import com.example.core.ui.components.OrbitPrimaryButton
import com.example.core.ui.components.OrbitSecondaryButton
import com.example.core.ui.components.OrbitSegmentPill
import com.example.core.ui.components.OrbitSheetPanel
import com.example.core.ui.components.OrbitTextField
import com.example.core.ui.theme.OrbitEmerald
import com.example.core.ui.theme.OrbitMonoStyle
import com.example.feature.passwords.domain.PasswordGenerator
import com.example.feature.passwords.domain.PasswordStrength
import com.example.feature.passwords.model.Credential
import com.example.feature.passwords.model.CredentialFolder
import com.example.feature.passwords.model.DecryptedPasswordHistoryItem

@Composable
fun CredentialDetailDialog(
    credential: Credential,
    folders: List<CredentialFolder>,
    history: List<DecryptedPasswordHistoryItem>,
    onCopyUsername: () -> Unit,
    onCopyPassword: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var revealPassword by remember(credential.id) { mutableStateOf(false) }
    var showHistory by remember(credential.id) { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val folderName = remember(credential.folderId, folders) {
        folders.firstOrNull { it.id == credential.folderId }?.name
    }
    val strength = remember(credential.password) { PasswordGenerator.evaluateStrength(credential.password) }

    OrbitDialog(
        onDismissRequest = onDismiss,
        title = credential.title,
        subtitle = credential.websiteUrl,
        icon = Icons.Rounded.Key,
        showCloseButton = true,
        actions = {
            OrbitSecondaryButton(
                text = "Delete",
                isDestructive = true,
                icon = Icons.Rounded.DeleteOutline,
                onClick = { confirmDelete = true },
                modifier = Modifier.weight(1f)
            )
            OrbitPrimaryButton(
                text = "Edit",
                icon = Icons.Rounded.Edit,
                onClick = onEdit,
                modifier = Modifier.weight(1f)
            )
        }
    ) { palette ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OrbitBadgeChip(text = "${credential.source.label} · ${strength.strength.label}")
            if (!folderName.isNullOrBlank()) {
                OrbitBadgeChip(text = folderName)
            }
        }
        HorizontalDivider(color = palette.hairline)

        if (!credential.username.isNullOrBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Username",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(credential.username, style = MaterialTheme.typography.bodyMedium)
                }
                IconButton(onClick = onCopyUsername) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy username")
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Password",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (revealPassword) credential.password else "••••••••••••",
                    style = OrbitMonoStyle
                )
            }
            Row {
                IconButton(onClick = { revealPassword = !revealPassword }) {
                    Icon(
                        imageVector = if (revealPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = "Reveal password"
                    )
                }
                IconButton(onClick = onCopyPassword) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy password")
                }
            }
        }

        if (!credential.notes.isNullOrBlank()) {
            Text(
                text = "Notes",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(credential.notes, style = MaterialTheme.typography.bodySmall)
        }

        if (history.isNotEmpty()) {
            OrbitGhostButton(
                text = if (showHistory) "Hide Password History (${history.size})" else "Password History (${history.size})",
                icon = Icons.Rounded.History,
                onClick = { showHistory = !showHistory },
                modifier = Modifier.fillMaxWidth()
            )
            if (showHistory) {
                history.forEach { item ->
                    Text(
                        text = "${FormatUtils.formatDateBucket(item.changedAt)}: ${if (revealPassword) item.password else "••••••••"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        OrbitConfirmDialog(
            title = "Delete Credential?",
            message = "Permanently remove ${credential.title} from Local Vault?",
            confirmText = "Delete",
            dismissText = "Cancel",
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddEditCredentialDialog(
    initial: Credential?,
    folders: List<CredentialFolder>,
    onSave: (
        existingId: String?,
        title: String,
        websiteUrl: String,
        username: String?,
        password: String,
        notes: String?,
        folderId: String?,
        isFavorite: Boolean
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember(initial) { mutableStateOf(initial?.title.orEmpty()) }
    var websiteUrl by remember(initial) { mutableStateOf(initial?.websiteUrl.orEmpty()) }
    var username by remember(initial) { mutableStateOf(initial?.username.orEmpty()) }
    var password by remember(initial) { mutableStateOf(initial?.password.orEmpty()) }
    var notes by remember(initial) { mutableStateOf(initial?.notes.orEmpty()) }
    var folderId by remember(initial) { mutableStateOf(initial?.folderId) }
    var isFavorite by remember(initial) { mutableStateOf(initial?.isFavorite == true) }
    var showPassword by remember { mutableStateOf(false) }

    val strength = remember(password) { PasswordGenerator.evaluateStrength(password) }

    OrbitDialog(
        onDismissRequest = onDismiss,
        title = if (initial == null) "Add Password" else "Edit Password",
        subtitle = "Encrypted locally with AES-256-GCM",
        icon = if (initial == null) Icons.Rounded.Key else Icons.Rounded.Edit,
        actions = {
            OrbitSecondaryButton(
                text = "Cancel",
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            )
            OrbitPrimaryButton(
                text = "Save",
                onClick = {
                    onSave(
                        initial?.id,
                        title,
                        websiteUrl,
                        username,
                        password,
                        notes,
                        folderId,
                        isFavorite
                    )
                },
                enabled = password.isNotEmpty() && (websiteUrl.isNotBlank() || title.isNotBlank()),
                modifier = Modifier
                    .weight(1f)
                    .testTag("cred_dialog_save_button")
            )
        }
    ) { _ ->
        OrbitTextField(
            value = title,
            onValueChange = { title = it },
            label = "Title (e.g. GitHub)",
            modifier = Modifier.testTag("cred_dialog_title_input")
        )
        OrbitTextField(
            value = websiteUrl,
            onValueChange = { websiteUrl = it },
            label = "Website URL or Domain",
            modifier = Modifier.testTag("cred_dialog_url_input")
        )
        OrbitTextField(
            value = username,
            onValueChange = { username = it },
            label = "Username / Email",
            modifier = Modifier.testTag("cred_dialog_username_input")
        )
        OrbitTextField(
            value = password,
            onValueChange = { password = it },
            label = "Password",
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                Row {
                    IconButton(onClick = { password = PasswordGenerator.generate() }) {
                        Icon(Icons.Rounded.AutoFixHigh, contentDescription = "Generate strong password")
                    }
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            imageVector = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = "Toggle password"
                        )
                    }
                }
            },
            modifier = Modifier.testTag("cred_dialog_password_input")
        )
        if (password.isNotEmpty()) {
            LinearProgressIndicator(
                progress = { strength.strength.scorePercent / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = if (strength.strength == PasswordStrength.STRONG) OrbitEmerald else MaterialTheme.colorScheme.primary
            )
        }
        if (folders.isNotEmpty()) {
            Text(
                text = "Folder",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OrbitSegmentPill(
                    label = "None",
                    selected = folderId == null,
                    onClick = { folderId = null }
                )
                folders.forEach { f ->
                    OrbitSegmentPill(
                        label = f.name,
                        selected = folderId == f.id,
                        onClick = { folderId = f.id }
                    )
                }
            }
        }
        OrbitTextField(
            value = notes,
            onValueChange = { notes = it },
            label = "Notes (optional)",
            singleLine = false,
            minLines = 2,
            maxLines = 3
        )
        OrbitSheetPanel(
            selected = isFavorite,
            onClick = { isFavorite = !isFavorite }
        ) {
            OrbitCheckboxRow(
                title = "Favorite",
                checked = isFavorite,
                onCheckedChange = { isFavorite = it },
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
fun FolderManagerDialog(
    folders: List<CredentialFolder>,
    onCreateFolder: (String) -> Unit,
    onDeleteFolder: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }

    OrbitDialog(
        onDismissRequest = onDismiss,
        title = "Manage Vault Folders",
        subtitle = "Organize saved logins by workspace or category",
        icon = Icons.Rounded.CreateNewFolder,
        actions = {
            OrbitPrimaryButton(
                text = "Done",
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            )
        }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OrbitTextField(
                value = name,
                onValueChange = { name = it },
                label = "New Folder",
                modifier = Modifier.weight(1f)
            )
            OrbitSecondaryButton(
                text = "Add",
                onClick = {
                    onCreateFolder(name)
                    name = ""
                },
                enabled = name.isNotBlank()
            )
        }
        folders.forEach { folder ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(folder.name, style = MaterialTheme.typography.bodyMedium)
                IconButton(onClick = { onDeleteFolder(folder.id) }) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete folder")
                }
            }
        }
    }
}
