package com.example.feature.collections.ui

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.common.UrlUtils
import com.example.core.database.entity.ImageCollectionEntity
import com.example.core.ui.components.OrbitBadgeChip
import com.example.core.ui.components.OrbitEmptyState
import com.example.core.ui.components.OrbitFilterField
import com.example.core.ui.components.OrbitFloatingActionButton
import com.example.core.ui.components.OrbitSegmentPill
import com.example.core.ui.components.OrbitSurfaceCard
import com.example.core.ui.components.OrbitTopBar
import com.example.core.ui.theme.OrbitSpacing
import com.example.feature.browser.ui.copyTextToClipboard
import com.example.feature.collections.viewmodel.ImageCollectionsViewModel
import kotlinx.coroutines.launch

@Composable
fun ImageCollectionsScreen(
    viewModel: ImageCollectionsViewModel,
    onOpenUrl: (String) -> Unit,
    onDownloadUrl: (String) -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var showAddDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var inspectingItem by remember { mutableStateOf<ImageCollectionEntity?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            OrbitTopBar(
                title = stringResource(R.string.collections_title),
                subtitle = stringResource(R.string.collections_subtitle_count, state.items.size),
                onBackClick = onBack,
                actions = {
                    if (state.totalCount > 0) {
                        IconButton(
                            onClick = { showClearDialog = true },
                            modifier = Modifier.testTag("collections_clear_all_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteSweep,
                                contentDescription = stringResource(R.string.collections_clear_all)
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            OrbitFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = Icons.Rounded.Add,
                contentDescription = stringResource(R.string.collections_add),
                modifier = Modifier.testTag("add_collection_image_fab")
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
                placeholder = stringResource(R.string.collections_search),
                testTag = "collections_filter_input",
                modifier = Modifier.padding(horizontal = OrbitSpacing.md, vertical = OrbitSpacing.sm)
            )

            if (state.collections.size > 1) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = OrbitSpacing.md, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(OrbitSpacing.sm)
                ) {
                    items(state.collections) { folder ->
                        OrbitSegmentPill(
                            label = folder,
                            selected = folder == state.selectedCollection,
                            onClick = { viewModel.onCollectionSelected(folder) },
                            modifier = Modifier.testTag("collection_folder_pill_$folder")
                        )
                    }
                }
            }

            if (state.items.isEmpty()) {
                OrbitEmptyState(
                    title = stringResource(R.string.collections_empty_title),
                    subtitle = stringResource(R.string.collections_empty_subtitle),
                    actionLabel = stringResource(R.string.collections_add),
                    onActionClick = { showAddDialog = true },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("collections_grid"),
                    contentPadding = PaddingValues(OrbitSpacing.md),
                    horizontalArrangement = Arrangement.spacedBy(OrbitSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(OrbitSpacing.sm)
                ) {
                    items(state.items, key = { it.id }) { item ->
                        CollectedImageCard(
                            item = item,
                            onClick = { inspectingItem = item },
                            onDelete = { viewModel.deleteItem(item.id) }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddCollectedImageDialog(
            existingCollections = state.collections,
            onDismiss = { showAddDialog = false },
            onConfirm = { title, imageUrl, collectionName ->
                viewModel.addImageUrl(
                    title = title,
                    rawImageUrl = imageUrl,
                    collectionName = collectionName
                )
                showAddDialog = false
            }
        )
    }

    inspectingItem?.let { item ->
        ImageCollectionDetailDialog(
            item = item,
            existingCollections = state.collections,
            onOpenImageUrl = onOpenUrl,
            onOpenSourcePage = onOpenUrl,
            onCopyUrl = { url ->
                copyTextToClipboard(context, "Image URL", url)
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.msg_link_copied))
                }
            },
            onDownloadImage = { url ->
                onDownloadUrl(url)
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.collections_download_started))
                }
            },
            onMoveToCollection = viewModel::moveToCollection,
            onDelete = viewModel::deleteItem,
            onDismiss = { inspectingItem = null }
        )
    }

    if (showClearDialog) {
        ClearCollectionsConfirmDialog(
            onConfirm = {
                viewModel.clearAll()
                showClearDialog = false
            },
            onDismiss = { showClearDialog = false }
        )
    }
}

@Composable
private fun CollectedImageCard(
    item: ImageCollectionEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    OrbitSurfaceCard(
        onClick = onClick,
        modifier = Modifier.testTag("collection_item_${item.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(126.dp)
            ) {
                TemporaryThumbnailPreview(
                    imageUrl = item.imageUrl,
                    contentDescription = item.title,
                    maxDimensionPx = 320,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                OrbitBadgeChip(
                    text = item.collectionName,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 2.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = UrlUtils.extractHost(item.imageUrl).ifBlank { item.imageUrl },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("delete_collection_item_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = stringResource(R.string.action_delete),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
