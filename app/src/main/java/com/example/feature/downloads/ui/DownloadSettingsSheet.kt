package com.example.feature.downloads.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.datastore.BrowserPreferences
import com.example.core.ui.components.OrbitModalBottomSheet
import com.example.core.ui.components.OrbitSheetHairlineDivider
import com.example.core.ui.components.OrbitSheetHeader
import com.example.core.ui.components.OrbitSheetPanel
import com.example.core.ui.components.OrbitSheetSecondaryButton
import com.example.core.ui.components.OrbitSheetSegmentPill
import com.example.core.ui.components.OrbitSheetStatusPill
import com.example.core.ui.components.OrbitSheetSwitchRow

@Composable
fun DownloadSettingsSheet(
    preferences: BrowserPreferences,
    onFolderPicked: (Uri) -> Unit,
    onResetFolder: () -> Unit,
    onConfirmBeforeDownloadChanged: (Boolean) -> Unit,
    onChunkCountChanged: (Int) -> Unit,
    onWifiOnlyChanged: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { pickedUri ->
        if (pickedUri != null) {
            onFolderPicked(pickedUri)
        }
    }

    OrbitModalBottomSheet(onDismissRequest = onDismiss) {
        OrbitSheetHeader(
            title = stringResource(R.string.downloads_settings_title),
            subtitle = "Storage, parallel streams & network rules",
            icon = Icons.Rounded.Tune,
            trailing = {
                OrbitSheetStatusPill(text = "${preferences.downloadChunkCount}x")
            }
        )

        OrbitSheetPanel {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FolderOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.downloads_folder_title),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium)
                        )
                        Text(
                            text = preferences.downloadFolderLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OrbitSheetSecondaryButton(
                        text = stringResource(R.string.downloads_choose_folder),
                        icon = Icons.Rounded.FolderOpen,
                        onClick = { folderPicker.launch(null) },
                        modifier = Modifier.weight(1f)
                    )
                    if (preferences.downloadFolderUri.isNotBlank()) {
                        TextButton(onClick = onResetFolder) {
                            Text(stringResource(R.string.downloads_reset_folder))
                        }
                    }
                }
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
                        modifier = Modifier.size(19.dp)
                    )
                    Text(
                        text = stringResource(R.string.downloads_chunks_title),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium)
                    )
                }
                Text(
                    text = "Splits large files into parallel HTTP Range connections for faster downloads when servers allow.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(1, 2, 4, 6, 8).forEach { count ->
                        OrbitSheetSegmentPill(
                            label = "${count}x",
                            selected = preferences.downloadChunkCount == count,
                            onClick = { onChunkCountChanged(count) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        OrbitSheetPanel {
            OrbitSheetSwitchRow(
                title = stringResource(R.string.downloads_confirm_toggle),
                subtitle = stringResource(R.string.downloads_confirm_subtitle),
                icon = Icons.Rounded.Download,
                checked = preferences.confirmBeforeDownload,
                onCheckedChange = onConfirmBeforeDownloadChanged,
                testTag = "settings_confirm_download_switch"
            )
            OrbitSheetHairlineDivider()
            OrbitSheetSwitchRow(
                title = stringResource(R.string.downloads_wifi_only),
                subtitle = stringResource(R.string.downloads_wifi_only_subtitle),
                icon = Icons.Rounded.Wifi,
                checked = preferences.downloadWifiOnly,
                onCheckedChange = onWifiOnlyChanged,
                testTag = "settings_wifi_only_download_switch"
            )
        }
    }
}
