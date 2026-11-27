package com.example.feature.sandbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.ui.components.OrbitActionPill
import com.example.core.ui.components.OrbitBadgeChip
import com.example.core.ui.components.OrbitConfirmDialog
import com.example.core.ui.components.OrbitEmptyState
import com.example.core.ui.components.OrbitFilterField
import com.example.core.ui.components.OrbitSurfaceCard
import com.example.core.ui.components.OrbitTopBar
import com.example.core.ui.theme.OrbitSpacing
import com.example.feature.sandbox.model.SandboxModel
import com.example.feature.sandbox.viewmodel.SandboxesViewModel

@Composable
fun SandboxesScreen(
    viewModel: SandboxesViewModel,
    nextSuggestedColorHex: (Int) -> Long,
    onOpenSandboxTab: (SandboxModel) -> Unit,
    onBeforeDeleteCloseTabs: (String) -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var searchQuery by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingSandbox by remember { mutableStateOf<SandboxModel?>(null) }
    var confirmClearSandbox by remember { mutableStateOf<SandboxModel?>(null) }
    var confirmDeleteSandbox by remember { mutableStateOf<SandboxModel?>(null) }

    val filtered = remember(state.sandboxes, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) state.sandboxes
        else state.sandboxes.filter {
            it.name.lowercase().contains(q) ||
                it.pinnedDomain.lowercase().contains(q) ||
                it.startUrl.lowercase().contains(q)
        }
    }

    LaunchedEffect(state.statusMessage) {
        val msg = state.statusMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissStatus()
        }
    }

    Scaffold(
        topBar = {
            OrbitTopBar(
                title = stringResource(R.string.sandboxes_title),
                subtitle = "${state.sandboxes.size} profiles",
                onBackClick = onBack
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.sandboxes_new)) },
                modifier = Modifier.testTag("sandboxes_add_fab")
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = OrbitSpacing.md,
                end = OrbitSpacing.md,
                top = 4.dp,
                bottom = 96.dp
            ),
            verticalArrangement = Arrangement.spacedBy(OrbitSpacing.sm)
        ) {
            if (!state.isMultiProfileSupported) {
                item(key = "engine_unsupported_banner") {
                    Text(
                        text = "Update System WebView to enable multi-profile isolation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            if (state.sandboxes.isNotEmpty()) {
                item(key = "sandbox_search") {
                    OrbitFilterField(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        placeholder = stringResource(R.string.sandboxes_search),
                        testTag = "sandboxes_search_field"
                    )
                }
            }

            if (filtered.isEmpty()) {
                item(key = "empty_sandboxes") {
                    OrbitEmptyState(
                        title = stringResource(R.string.sandboxes_empty_title),
                        subtitle = stringResource(R.string.sandboxes_empty_subtitle),
                        actionLabel = stringResource(R.string.sandboxes_new),
                        onActionClick = { showCreateDialog = true },
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            } else {
                items(filtered, key = { it.id }) { sandbox ->
                    SandboxProfileCard(
                        sandbox = sandbox,
                        onOpenTab = { onOpenSandboxTab(sandbox) },
                        onEdit = { editingSandbox = sandbox },
                        onClearData = { confirmClearSandbox = sandbox },
                        onDelete = { confirmDeleteSandbox = sandbox }
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateEditSandboxDialog(
            suggestedColorHex = nextSuggestedColorHex(state.sandboxes.size),
            onConfirm = { name, siteUrl, colorHex, iconKey, keepLongTerm ->
                viewModel.createSandbox(name, siteUrl, colorHex, iconKey, keepLongTerm)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false }
        )
    }

    editingSandbox?.let { target ->
        CreateEditSandboxDialog(
            initial = target,
            onConfirm = { name, siteUrl, colorHex, iconKey, keepLongTerm ->
                viewModel.updateSandbox(target.id, name, siteUrl, colorHex, iconKey, keepLongTerm)
                editingSandbox = null
            },
            onDismiss = { editingSandbox = null }
        )
    }

    confirmClearSandbox?.let { target ->
        OrbitConfirmDialog(
            title = "Clear Session?",
            message = "Clear cookies and site data for \"${target.name}\".",
            confirmText = "Clear",
            isDestructive = true,
            onConfirm = {
                confirmClearSandbox = null
                viewModel.clearSandboxData(target.id, target.name)
            },
            onDismiss = { confirmClearSandbox = null }
        )
    }

    confirmDeleteSandbox?.let { target ->
        OrbitConfirmDialog(
            title = "Delete Sandbox?",
            message = "Remove \"${target.name}\" and its isolated session data.",
            confirmText = "Delete",
            isDestructive = true,
            onConfirm = {
                confirmDeleteSandbox = null
                viewModel.deleteSandbox(target.id, target.name, onBeforeDeleteCloseTabs)
            },
            onDismiss = { confirmDeleteSandbox = null }
        )
    }
}

@Composable
private fun SandboxProfileCard(
    sandbox: SandboxModel,
    onOpenTab: () -> Unit,
    onEdit: () -> Unit,
    onClearData: () -> Unit,
    onDelete: () -> Unit
) {
    OrbitSurfaceCard(
        onClick = onOpenTab,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sandbox_card_${sandbox.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(sandbox.accentColor)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = sandbox.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val subtitle = sandbox.pinnedDomain.ifBlank { sandbox.startUrl }
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (sandbox.openTabCount > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    OrbitBadgeChip(text = "${sandbox.openTabCount} open")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OrbitActionPill(
                    label = "Open",
                    leadingIcon = Icons.AutoMirrored.Rounded.OpenInNew,
                    onClick = onOpenTab,
                    modifier = Modifier.testTag("sandbox_open_${sandbox.id}")
                )
                OrbitActionPill(
                    label = "Clear",
                    leadingIcon = Icons.Rounded.CleaningServices,
                    onClick = onClearData,
                    modifier = Modifier.testTag("sandbox_clear_${sandbox.id}")
                )
                OrbitActionPill(
                    label = "Edit",
                    leadingIcon = Icons.Rounded.Edit,
                    onClick = onEdit,
                    modifier = Modifier.testTag("sandbox_edit_${sandbox.id}")
                )
                OrbitActionPill(
                    label = "Delete",
                    leadingIcon = Icons.Rounded.DeleteOutline,
                    onClick = onDelete,
                    modifier = Modifier.testTag("sandbox_delete_${sandbox.id}")
                )
            }
        }
    }
}
