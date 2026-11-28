package com.example.feature.downloads.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Tune
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.database.entity.DownloadStatus
import com.example.core.ui.components.OrbitEmptyState
import com.example.core.ui.components.OrbitFloatingActionButton
import com.example.core.ui.components.OrbitSecondaryButton
import com.example.core.ui.components.OrbitSegmentPill
import com.example.core.ui.components.OrbitSurfaceCard
import com.example.core.ui.components.OrbitTopBar
import com.example.core.ui.theme.OrbitSpacing
import com.example.feature.downloads.engine.DownloadFolderHelper
import com.example.feature.downloads.model.DownloadFilter
import com.example.feature.downloads.viewmodel.DownloadsViewModel

@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel,
    onBack: () -> Unit
) {
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showNewDownloadSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            viewModel.setDownloadFolder(uri)
        }
    }

    val filteredDownloads = remember(downloads, selectedFilter) {
        when (selectedFilter) {
            DownloadFilter.ALL -> downloads
            DownloadFilter.ACTIVE -> downloads.filter {
                it.status == DownloadStatus.QUEUED.name || it.status == DownloadStatus.DOWNLOADING.name
            }
            DownloadFilter.PAUSED -> downloads.filter {
                it.status == DownloadStatus.PAUSED.name ||
                    it.status == DownloadStatus.FAILED.name ||
                    it.status == DownloadStatus.CANCELLED.name
            }
            DownloadFilter.COMPLETED -> downloads.filter {
                it.status == DownloadStatus.COMPLETED.name
            }
        }
    }

    Scaffold(
        topBar = {
            OrbitTopBar(
                title = stringResource(R.string.downloads_title),
                subtitle = "${downloads.size} items · ${prefs.downloadChunkCount}x Chunk Engine",
                onBackClick = onBack,
                actions = {
                    if (downloads.any {
                            it.status == DownloadStatus.COMPLETED.name ||
                                it.status == DownloadStatus.FAILED.name ||
                                it.status == DownloadStatus.CANCELLED.name
                        }
                    ) {
                        IconButton(
                            onClick = viewModel::clearFinished,
                            modifier = Modifier.testTag("downloads_clear_finished_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CleaningServices,
                                contentDescription = stringResource(R.string.downloads_clear_finished)
                            )
                        }
                    }
                    IconButton(
                        onClick = { showSettingsSheet = true },
                        modifier = Modifier.testTag("downloads_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = stringResource(R.string.downloads_settings_title)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            OrbitFloatingActionButton(
                onClick = { showNewDownloadSheet = true },
                icon = Icons.Rounded.Add,
                contentDescription = stringResource(R.string.downloads_new),
                modifier = Modifier.testTag("add_download_fab")
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            OrbitSurfaceCard(
                modifier = Modifier.padding(horizontal = OrbitSpacing.md, vertical = OrbitSpacing.xs)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = OrbitSpacing.md, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(OrbitSpacing.sm))
                        Column {
                            Text(
                                text = prefs.downloadFolderLabel,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val summary = buildString {
                                append("${prefs.downloadChunkCount}x chunks")
                                append(" · ")
                                append(if (prefs.confirmBeforeDownload) "Confirm On" else "Auto Start")
                                if (prefs.downloadWifiOnly) append(" · Wi-Fi Only")
                            }
                            Text(
                                text = summary,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    OrbitSecondaryButton(
                        text = "Folder",
                        onClick = { folderPicker.launch(null) },
                        modifier = Modifier.testTag("downloads_select_folder_button")
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = OrbitSpacing.md, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(OrbitSpacing.xs)
            ) {
                DownloadFilter.entries.forEach { filter ->
                    OrbitSegmentPill(
                        label = filter.label,
                        selected = selectedFilter == filter,
                        onClick = { viewModel.setFilter(filter) },
                        modifier = Modifier.testTag("downloads_filter_${filter.name.lowercase()}")
                    )
                }
            }

            if (filteredDownloads.isEmpty()) {
                OrbitEmptyState(
                    title = stringResource(R.string.downloads_empty_title),
                    subtitle = stringResource(R.string.downloads_empty_subtitle),
                    actionLabel = stringResource(R.string.downloads_new),
                    onActionClick = { showNewDownloadSheet = true },
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        start = OrbitSpacing.md,
                        end = OrbitSpacing.md,
                        top = OrbitSpacing.xs,
                        bottom = 96.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(OrbitSpacing.sm)
                ) {
                    items(filteredDownloads, key = { it.id }) { item ->
                        DownloadItemCard(
                            item = item,
                            onOpen = { DownloadFolderHelper.openDownloadedItem(context, item) },
                            onShare = { DownloadFolderHelper.shareDownloadedItem(context, item) },
                            onPause = { viewModel.pauseDownload(item) },
                            onResume = { viewModel.resumeDownload(item) },
                            onRetry = { viewModel.retryDownload(item) },
                            onCancel = { viewModel.cancelDownload(item) },
                            onDelete = { viewModel.deleteDownload(item) }
                        )
                    }
                }
            }
        }
    }

    if (showNewDownloadSheet) {
        DownloadConfirmSheet(
            initialUrl = "",
            initialFileName = "",
            initialMimeType = null,
            preferences = prefs,
            allowEditingUrl = true,
            onFolderPicked = viewModel::setDownloadFolder,
            onConfirmBeforeDownloadChanged = viewModel::setConfirmBeforeDownload,
            onConfirmStart = { url, fileName, mimeType, chunkCount, folderUri, folderLabel, wifiOnly ->
                viewModel.startDownload(
                    url = url,
                    fileName = fileName,
                    mimeType = mimeType,
                    chunkCount = chunkCount,
                    folderUri = folderUri,
                    folderLabel = folderLabel,
                    wifiOnly = wifiOnly
                )
                showNewDownloadSheet = false
            },
            onDismiss = { showNewDownloadSheet = false }
        )
    }

    if (showSettingsSheet) {
        DownloadSettingsSheet(
            preferences = prefs,
            onFolderPicked = viewModel::setDownloadFolder,
            onResetFolder = viewModel::resetDownloadFolder,
            onConfirmBeforeDownloadChanged = viewModel::setConfirmBeforeDownload,
            onChunkCountChanged = viewModel::setDownloadChunkCount,
            onWifiOnlyChanged = viewModel::setDownloadWifiOnly,
            onDismiss = { showSettingsSheet = false }
        )
    }
}
