package com.example.feature.downloads.ui

import android.net.Uri
import android.webkit.URLUtil
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.common.FormatUtils
import com.example.core.datastore.BrowserPreferences
import com.example.core.network.NetworkClientFactory
import com.example.core.ui.components.OrbitModalBottomSheet
import com.example.core.ui.components.OrbitSecondaryButton
import com.example.core.ui.components.OrbitSheetHairlineDivider
import com.example.core.ui.components.OrbitSheetHeader
import com.example.core.ui.components.OrbitSheetPanel
import com.example.core.ui.components.OrbitSheetPrimaryButton
import com.example.core.ui.components.OrbitSheetSecondaryButton
import com.example.core.ui.components.OrbitSheetSegmentPill
import com.example.core.ui.components.OrbitSheetStatusPill
import com.example.core.ui.components.OrbitSheetSwitchRow
import com.example.core.ui.components.OrbitTextField
import com.example.core.ui.theme.OrbitMonoStyle
import com.example.feature.downloads.engine.DownloadFolderHelper
import com.example.feature.downloads.model.DownloadProbeResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DownloadConfirmSheet(
    initialUrl: String,
    initialFileName: String,
    initialMimeType: String?,
    preferences: BrowserPreferences,
    allowEditingUrl: Boolean = false,
    onFolderPicked: (Uri) -> Unit,
    onConfirmBeforeDownloadChanged: (Boolean) -> Unit,
    onConfirmStart: (
        url: String,
        fileName: String,
        mimeType: String?,
        chunkCount: Int,
        folderUri: String,
        folderLabel: String,
        wifiOnly: Boolean
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var urlText by remember(initialUrl) { mutableStateOf(initialUrl) }
    var fileNameText by remember(initialFileName) {
        mutableStateOf(
            initialFileName.ifBlank {
                if (initialUrl.isNotBlank()) URLUtil.guessFileName(initialUrl, null, initialMimeType) else ""
            }
        )
    }
    var userEditedName by remember { mutableStateOf(false) }
    var selectedChunks by remember(preferences.downloadChunkCount) {
        mutableIntStateOf(preferences.downloadChunkCount.coerceIn(1, 8))
    }
    var folderUri by remember(preferences.downloadFolderUri) { mutableStateOf(preferences.downloadFolderUri) }
    var folderLabel by remember(preferences.downloadFolderLabel) { mutableStateOf(preferences.downloadFolderLabel) }
    var wifiOnly by remember(preferences.downloadWifiOnly) { mutableStateOf(preferences.downloadWifiOnly) }
    var askBeforeDownload by remember(preferences.confirmBeforeDownload) {
        mutableStateOf(preferences.confirmBeforeDownload)
    }

    var probeResult by remember(initialUrl) { mutableStateOf<DownloadProbeResult?>(null) }
    var isProbing by remember { mutableStateOf(false) }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { pickedUri ->
        if (pickedUri != null) {
            DownloadFolderHelper.persistTreePermission(context, pickedUri)
            folderUri = pickedUri.toString()
            folderLabel = DownloadFolderHelper.parseFolderDisplayName(pickedUri)
            onFolderPicked(pickedUri)
        }
    }

    LaunchedEffect(urlText) {
        val trimmed = urlText.trim()
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            probeResult = null
            isProbing = false
            return@LaunchedEffect
        }
        isProbing = true
        val result = withContext(Dispatchers.IO) {
            val client = NetworkClientFactory.createDownloadOkHttpClient()
            DownloadFolderHelper.probeRemoteFile(client, trimmed, initialMimeType)
        }
        probeResult = result
        isProbing = false
        if (!userEditedName) {
            val resolved = result.suggestedFileName?.takeIf { it.isNotBlank() }
                ?: URLUtil.guessFileName(trimmed, null, result.mimeType)
            if (resolved.isNotBlank()) {
                fileNameText = resolved
            }
        }
    }

    val isValidUrl = urlText.trim().startsWith("http://") || urlText.trim().startsWith("https://")

    OrbitModalBottomSheet(onDismissRequest = onDismiss) {
        OrbitSheetHeader(
            title = stringResource(R.string.downloads_confirm_title),
            subtitle = if (isValidUrl) buildProbeSummary(isProbing, probeResult) else "Multi-stream WorkManager engine",
            icon = Icons.Rounded.Download,
            trailing = {
                OrbitSheetStatusPill(text = "${selectedChunks}x boost")
            }
        )

        if (allowEditingUrl) {
            OrbitTextField(
                value = urlText,
                onValueChange = { urlText = it },
                label = "Direct File URL (https://...)",
                singleLine = true,
                modifier = Modifier.testTag("download_confirm_url_input")
            )
        } else {
            OrbitSheetPanel {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = urlText,
                        style = OrbitMonoStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildProbeSummary(isProbing, probeResult),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        OrbitTextField(
            value = fileNameText,
            onValueChange = {
                userEditedName = true
                fileNameText = it
            },
            label = "File Name",
            singleLine = true,
            modifier = Modifier.testTag("download_confirm_filename_input")
        )

        OrbitSheetPanel {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
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
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.downloads_folder_title),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium)
                        )
                        Text(
                            text = folderLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                OrbitSheetSecondaryButton(
                    text = "Change",
                    onClick = { folderPicker.launch(null) },
                    modifier = Modifier.testTag("download_confirm_folder_button")
                )
            }
        }

        OrbitSheetPanel {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bolt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.downloads_chunks_title),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(1, 2, 4, 6, 8).forEach { count ->
                        OrbitSheetSegmentPill(
                            label = "${count}x",
                            selected = selectedChunks == count,
                            onClick = { selectedChunks = count },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("download_confirm_chunk_$count")
                        )
                    }
                }
            }
        }

        OrbitSheetPanel {
            OrbitSheetSwitchRow(
                title = stringResource(R.string.downloads_wifi_only),
                icon = Icons.Rounded.Wifi,
                checked = wifiOnly,
                onCheckedChange = { wifiOnly = it },
                testTag = "download_confirm_wifi_switch"
            )
            OrbitSheetHairlineDivider()
            OrbitSheetSwitchRow(
                title = stringResource(R.string.downloads_confirm_toggle),
                icon = Icons.Rounded.Download,
                checked = askBeforeDownload,
                onCheckedChange = {
                    askBeforeDownload = it
                    onConfirmBeforeDownloadChanged(it)
                },
                testTag = "download_confirm_ask_switch"
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OrbitSecondaryButton(
                text = stringResource(R.string.action_cancel),
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            )
            OrbitSheetPrimaryButton(
                text = stringResource(R.string.downloads_confirm_start),
                icon = Icons.Rounded.Download,
                enabled = isValidUrl,
                onClick = {
                    val finalUrl = urlText.trim()
                    val resolvedName = fileNameText.trim().ifBlank {
                        URLUtil.guessFileName(finalUrl, null, probeResult?.mimeType ?: initialMimeType)
                    }
                    onConfirmStart(
                        finalUrl,
                        resolvedName,
                        probeResult?.mimeType ?: initialMimeType,
                        selectedChunks,
                        folderUri,
                        folderLabel,
                        wifiOnly
                    )
                },
                modifier = Modifier
                    .weight(2f)
                    .testTag("download_confirm_start_button")
            )
        }
    }
}

private fun buildProbeSummary(isProbing: Boolean, result: DownloadProbeResult?): String {
    if (isProbing) return "Checking file size & multi-chunk support..."
    if (result == null || !result.probedSuccessfully) return "Size unknown until download starts"
    val sizeStr = if (result.totalBytes > 0L) FormatUtils.formatByteSize(result.totalBytes) else "Unknown size"
    val modeStr = if (result.supportsRanges) "Multi-chunk range boost ready" else "Single stream"
    return "$sizeStr · ${result.mimeType} · $modeStr"
}
