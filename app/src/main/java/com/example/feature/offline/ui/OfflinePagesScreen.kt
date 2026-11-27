package com.example.feature.offline.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.OfflinePin
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.common.FormatUtils
import com.example.core.database.entity.OfflinePageEntity
import com.example.core.ui.components.OrbitBadgeChip
import com.example.core.ui.components.OrbitConfirmDialog
import com.example.core.ui.components.OrbitEmptyState
import com.example.core.ui.components.OrbitFilterField
import com.example.core.ui.components.OrbitSurfaceCard
import com.example.core.ui.components.OrbitTopBar
import com.example.core.ui.theme.OrbitSpacing
import com.example.feature.offline.viewmodel.OfflinePagesViewModel
import java.io.File

@Composable
fun OfflinePagesScreen(
    viewModel: OfflinePagesViewModel,
    onOpenOfflineUrl: (String) -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            OrbitTopBar(
                title = stringResource(R.string.offline_pages_title),
                subtitle = if (state.pages.isEmpty()) {
                    stringResource(R.string.offline_pages_subtitle)
                } else {
                    "${state.pages.size} saved"
                },
                onBackClick = onBack,
                actions = {
                    if (state.pages.isNotEmpty()) {
                        IconButton(
                            onClick = { showClearDialog = true },
                            modifier = Modifier.testTag("offline_pages_clear_all")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteSweep,
                                contentDescription = stringResource(R.string.offline_pages_clear_all)
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            OrbitFilterField(
                query = state.searchQuery,
                onQueryChange = viewModel::updateSearchQuery,
                placeholder = stringResource(R.string.offline_pages_search),
                testTag = "offline_pages_filter_input",
                modifier = Modifier.padding(horizontal = OrbitSpacing.md, vertical = OrbitSpacing.sm)
            )

            if (state.pages.isEmpty()) {
                OrbitEmptyState(
                    title = stringResource(R.string.offline_pages_empty_title),
                    subtitle = stringResource(R.string.offline_pages_empty_subtitle),
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(OrbitSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(OrbitSpacing.sm)
                ) {
                    items(state.pages, key = { it.id }) { page ->
                        OfflinePageRowCard(
                            page = page,
                            onOpen = {
                                val fileUri = Uri.fromFile(File(page.filePath)).toString()
                                onOpenOfflineUrl(fileUri)
                            },
                            onDelete = { viewModel.deletePage(page) }
                        )
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        OrbitConfirmDialog(
            title = stringResource(R.string.offline_pages_clear_all),
            message = "Delete all saved offline webpage archives from device storage?",
            confirmText = stringResource(R.string.action_delete),
            dismissText = stringResource(R.string.action_cancel),
            onConfirm = {
                viewModel.clearAll()
                showClearDialog = false
            },
            onDismiss = { showClearDialog = false }
        )
    }
}

@Composable
private fun OfflinePageRowCard(
    page: OfflinePageEntity,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    OrbitSurfaceCard(
        onClick = onOpen,
        modifier = Modifier.testTag("offline_page_item_${page.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = OrbitSpacing.md, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.OfflinePin,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = page.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = page.originalUrl,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${FormatUtils.formatDateBucket(page.savedAt)} · ${FormatUtils.formatTimeShort(page.savedAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.width(OrbitSpacing.xs))
            OrbitBadgeChip(text = FormatUtils.formatByteSize(page.fileSizeBytes))
            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("offline_page_delete_${page.id}")
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = stringResource(R.string.action_delete)
                )
            }
        }
    }
}
