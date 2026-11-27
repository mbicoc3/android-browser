package com.example.feature.downloads.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PauseCircleOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.common.FormatUtils
import com.example.core.database.entity.DownloadEntity
import com.example.core.database.entity.DownloadStatus
import com.example.core.ui.components.OrbitBadgeChip
import com.example.core.ui.components.OrbitSurfaceCard
import com.example.core.ui.theme.OrbitEmerald
import com.example.core.ui.theme.OrbitSpacing

@Composable
fun DownloadItemCard(
    item: DownloadEntity,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    val status = runCatching { DownloadStatus.valueOf(item.status) }.getOrDefault(DownloadStatus.QUEUED)

    OrbitSurfaceCard(
        onClick = if (status == DownloadStatus.COMPLETED) onOpen else null,
        modifier = Modifier.testTag("download_card_${item.id}")
    ) {
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
                    imageVector = resolveDownloadIcon(status, item.fileName, item.mimeType),
                    contentDescription = null,
                    tint = when (status) {
                        DownloadStatus.COMPLETED -> OrbitEmerald
                        DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
                        DownloadStatus.PAUSED -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.fileName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildTelemetryLine(item, status),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (status == DownloadStatus.FAILED && item.errorMessage != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                OrbitBadgeChip(text = status.name)
            }

            if (status == DownloadStatus.DOWNLOADING || status == DownloadStatus.QUEUED || status == DownloadStatus.PAUSED) {
                if (item.totalBytes > 0L) {
                    LinearProgressIndicator(
                        progress = { (item.bytesDownloaded.toFloat() / item.totalBytes.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                    )
                } else if (status != DownloadStatus.PAUSED) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.targetFolderLabel.ifBlank { "Downloads (Default)" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (status) {
                        DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING -> {
                            IconButton(
                                onClick = onPause,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("download_pause_${item.id}")
                            ) {
                                Icon(Icons.Rounded.Pause, contentDescription = stringResource(R.string.downloads_pause))
                            }
                            IconButton(
                                onClick = onCancel,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("download_cancel_${item.id}")
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_cancel))
                            }
                        }
                        DownloadStatus.PAUSED -> {
                            IconButton(
                                onClick = onResume,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("download_resume_${item.id}")
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.downloads_resume))
                            }
                            IconButton(
                                onClick = onDelete,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("download_delete_${item.id}")
                            ) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = stringResource(R.string.action_delete))
                            }
                        }
                        DownloadStatus.FAILED, DownloadStatus.CANCELLED -> {
                            IconButton(
                                onClick = onRetry,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("download_retry_${item.id}")
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.action_retry))
                            }
                            IconButton(
                                onClick = onDelete,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("download_delete_${item.id}")
                            ) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = stringResource(R.string.action_delete))
                            }
                        }
                        DownloadStatus.COMPLETED -> {
                            IconButton(
                                onClick = onShare,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("download_share_${item.id}")
                            ) {
                                Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.downloads_share))
                            }
                            IconButton(
                                onClick = onDelete,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("download_delete_${item.id}")
                            ) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = stringResource(R.string.action_delete))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun resolveDownloadIcon(status: DownloadStatus, fileName: String, mimeType: String): ImageVector {
    if (status == DownloadStatus.FAILED || status == DownloadStatus.CANCELLED) return Icons.Rounded.ErrorOutline
    if (status == DownloadStatus.PAUSED) return Icons.Rounded.PauseCircleOutline
    if (status == DownloadStatus.QUEUED || status == DownloadStatus.DOWNLOADING) return Icons.Rounded.Downloading

    val lower = fileName.lowercase()
    val mime = mimeType.lowercase()
    return when {
        mime.startsWith("image/") || lower.endsWith(".jpg") || lower.endsWith(".png") || lower.endsWith(".webp") ->
            Icons.Rounded.Image
        mime.startsWith("video/") || lower.endsWith(".mp4") || lower.endsWith(".mkv") ->
            Icons.Rounded.VideoFile
        mime.startsWith("audio/") || lower.endsWith(".mp3") || lower.endsWith(".m4a") ->
            Icons.Rounded.AudioFile
        lower.endsWith(".apk") -> Icons.Rounded.Android
        lower.endsWith(".zip") || lower.endsWith(".rar") || lower.endsWith(".7z") || lower.endsWith(".tar") ->
            Icons.Rounded.FolderZip
        mime.contains("pdf") || lower.endsWith(".pdf") || lower.endsWith(".doc") || lower.endsWith(".txt") ->
            Icons.Rounded.Description
        else -> Icons.Rounded.DownloadDone
    }
}

private fun buildTelemetryLine(item: DownloadEntity, status: DownloadStatus): String {
    val sizePart = if (item.totalBytes > 0L) {
        "${FormatUtils.formatByteSize(item.bytesDownloaded)} / ${FormatUtils.formatByteSize(item.totalBytes)}"
    } else {
        FormatUtils.formatByteSize(item.bytesDownloaded)
    }

    return when (status) {
        DownloadStatus.DOWNLOADING -> {
            val parts = mutableListOf(sizePart)
            if (item.speedBytesPerSec > 0L) {
                parts.add("${FormatUtils.formatByteSize(item.speedBytesPerSec)}/s")
                if (item.totalBytes > item.bytesDownloaded) {
                    val remainingSec = (item.totalBytes - item.bytesDownloaded) / item.speedBytesPerSec
                    parts.add(formatEta(remainingSec))
                }
            }
            if (item.chunkCount > 1 && item.supportsRanges) {
                parts.add("${item.chunkCount}x chunks")
            }
            parts.joinToString(" · ")
        }
        DownloadStatus.QUEUED -> if (item.wifiOnly) "Waiting for Wi-Fi · ${item.chunkCount}x chunks" else "Queued · ${item.chunkCount}x chunks"
        DownloadStatus.PAUSED -> "Paused · $sizePart"
        DownloadStatus.COMPLETED -> {
            val doneSize = FormatUtils.formatByteSize(
                item.totalBytes.takeIf { it > 0L } ?: item.bytesDownloaded
            )
            val chunkInfo = if (item.chunkCount > 1 && item.supportsRanges) " · ${item.chunkCount}x chunks" else ""
            "$doneSize$chunkInfo"
        }
        DownloadStatus.FAILED -> item.errorMessage ?: "Download failed"
        DownloadStatus.CANCELLED -> "Cancelled"
    }
}

private fun formatEta(seconds: Long): String = when {
    seconds < 60L -> "${seconds}s left"
    seconds < 3600L -> "${seconds / 60}m ${seconds % 60}s left"
    else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m left"
}
