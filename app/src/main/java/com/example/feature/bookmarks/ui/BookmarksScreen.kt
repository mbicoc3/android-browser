package com.example.feature.bookmarks.ui

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.DeleteOutline
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
import com.example.core.database.entity.BookmarkEntity
import com.example.core.ui.components.OrbitBadgeChip
import com.example.core.ui.components.OrbitDialog
import com.example.core.ui.components.OrbitEmptyState
import com.example.core.ui.components.OrbitFilterField
import com.example.core.ui.components.OrbitFloatingActionButton
import com.example.core.ui.components.OrbitPrimaryButton
import com.example.core.ui.components.OrbitSecondaryButton
import com.example.core.ui.components.OrbitSegmentPill
import com.example.core.ui.components.OrbitSurfaceCard
import com.example.core.ui.components.OrbitTextField
import com.example.core.ui.components.OrbitTopBar
import com.example.core.ui.theme.OrbitSpacing
import com.example.feature.bookmarks.viewmodel.BookmarksViewModel

@Composable
fun BookmarksScreen(
    viewModel: BookmarksViewModel,
    onOpenUrl: (String) -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            OrbitTopBar(
                title = stringResource(R.string.bookmarks_title),
                subtitle = "${state.bookmarks.size} saved",
                onBackClick = onBack
            )
        },
        floatingActionButton = {
            OrbitFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = Icons.Rounded.Add,
                contentDescription = stringResource(R.string.bookmarks_add),
                modifier = Modifier.testTag("add_bookmark_fab")
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            OrbitFilterField(
                query = state.filterQuery,
                onQueryChange = viewModel::onQueryChange,
                placeholder = stringResource(R.string.bookmarks_search),
                testTag = "bookmarks_filter_input",
                modifier = Modifier.padding(horizontal = OrbitSpacing.md, vertical = OrbitSpacing.sm)
            )

            if (state.folders.size > 1) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = OrbitSpacing.md, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(OrbitSpacing.sm)
                ) {
                    items(state.folders) { folder ->
                        OrbitSegmentPill(
                            label = folder,
                            selected = folder == state.selectedFolder,
                            onClick = { viewModel.onFolderSelected(folder) }
                        )
                    }
                }
            }

            if (state.bookmarks.isEmpty()) {
                OrbitEmptyState(
                    title = stringResource(R.string.bookmarks_empty_title),
                    subtitle = stringResource(R.string.bookmarks_empty_subtitle),
                    actionLabel = stringResource(R.string.bookmarks_add),
                    onActionClick = { showAddDialog = true },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(OrbitSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(OrbitSpacing.sm)
                ) {
                    items(state.bookmarks, key = { it.id }) { bookmark ->
                        BookmarkRowCard(
                            bookmark = bookmark,
                            onClick = { onOpenUrl(bookmark.url) },
                            onDelete = { viewModel.deleteBookmark(bookmark.id) }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddBookmarkDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { title, url, folder ->
                viewModel.addBookmark(title, url, folder)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun BookmarkRowCard(
    bookmark: BookmarkEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    OrbitSurfaceCard(
        onClick = onClick,
        modifier = Modifier.testTag("bookmark_item_${bookmark.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = OrbitSpacing.md, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Bookmark,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = bookmark.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = bookmark.url,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            OrbitBadgeChip(text = bookmark.folder)
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = stringResource(R.string.action_delete)
                )
            }
        }
    }
}

@Composable
private fun AddBookmarkDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, url: String, folder: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var folder by remember { mutableStateOf("General") }

    OrbitDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.bookmarks_add),
        icon = Icons.Rounded.BookmarkAdd,
        actions = {
            OrbitSecondaryButton(
                text = stringResource(R.string.action_cancel),
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            )
            OrbitPrimaryButton(
                text = stringResource(R.string.action_save),
                onClick = { onConfirm(title, url, folder) },
                enabled = url.isNotBlank(),
                modifier = Modifier.weight(1f)
            )
        }
    ) {
        OrbitTextField(
            value = title,
            onValueChange = { title = it },
            label = "Title"
        )
        OrbitTextField(
            value = url,
            onValueChange = { url = it },
            label = "URL",
            placeholder = "https://example.com"
        )
        OrbitTextField(
            value = folder,
            onValueChange = { folder = it },
            label = "Folder"
        )
    }
}
