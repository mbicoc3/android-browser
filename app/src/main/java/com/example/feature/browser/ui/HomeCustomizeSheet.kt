package com.example.feature.browser.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SettingsBrightness
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.datastore.AppThemeMode
import com.example.core.datastore.DEFAULT_START_WALLPAPER_URL
import com.example.core.datastore.StartBackgroundMode
import com.example.core.ui.components.OrbitDialog
import com.example.core.ui.components.OrbitGhostButton
import com.example.core.ui.components.OrbitModalBottomSheet
import com.example.core.ui.components.OrbitPrimaryButton
import com.example.core.ui.components.OrbitSecondaryButton
import com.example.core.ui.components.OrbitSegmentPill
import com.example.core.ui.components.OrbitSheetHeader
import com.example.core.ui.components.OrbitSheetKicker
import com.example.core.ui.components.OrbitSheetPanel
import com.example.core.ui.components.OrbitSheetSecondaryButton
import com.example.core.ui.components.OrbitSheetSegmentPill
import com.example.core.ui.components.OrbitTextField
import com.example.core.ui.theme.OrbitDarkSolidStartBg
import com.example.core.ui.theme.OrbitLightSolidStartBg
import com.example.feature.browser.repository.WallpaperStorageHelper
import kotlinx.coroutines.launch

@Composable
fun HomeCustomizeSheet(
    themeMode: AppThemeMode,
    backgroundMode: StartBackgroundMode,
    wallpaperUri: String,
    onThemeModeSelected: (AppThemeMode) -> Unit,
    onBackgroundModeSelected: (StartBackgroundMode) -> Unit,
    onWallpaperUriSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showImageUrlDialog by remember { mutableStateOf(false) }
    var adjustInput by remember { mutableStateOf<WallpaperAdjustInput?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            adjustInput = WallpaperAdjustInput.LocalUri(uri)
        }
    }

    OrbitModalBottomSheet(onDismissRequest = onDismiss) {
        OrbitSheetHeader(
            title = "Customize",
            icon = Icons.Rounded.Palette
        )

        OrbitSheetKicker(text = "Theme")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppThemeMode.entries.forEach { mode ->
                val icon = when (mode) {
                    AppThemeMode.LIGHT -> Icons.Rounded.LightMode
                    AppThemeMode.DARK -> Icons.Rounded.DarkMode
                    AppThemeMode.SYSTEM -> Icons.Rounded.SettingsBrightness
                }
                OrbitSheetSegmentPill(
                    label = mode.displayName,
                    leadingIcon = icon,
                    selected = themeMode == mode,
                    onClick = { onThemeModeSelected(mode) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("customize_theme_${mode.name.lowercase()}")
                )
            }
        }

        OrbitSheetKicker(text = "Background")

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BackgroundModeOption(
                title = "Solid",
                icon = Icons.Rounded.FormatColorFill,
                selected = backgroundMode == StartBackgroundMode.SOLID,
                isWallpaperPreview = false,
                onClick = { onBackgroundModeSelected(StartBackgroundMode.SOLID) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("customize_bg_solid")
            )
            BackgroundModeOption(
                title = "Image",
                icon = Icons.Rounded.Image,
                selected = backgroundMode == StartBackgroundMode.IMAGE,
                isWallpaperPreview = true,
                onClick = { onBackgroundModeSelected(StartBackgroundMode.IMAGE) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("customize_bg_image")
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OrbitSheetSecondaryButton(
                text = stringResource(R.string.customize_pick_local_image),
                icon = Icons.Rounded.Image,
                onClick = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("customize_pick_wallpaper")
            )
            OrbitSheetSecondaryButton(
                text = "URL",
                icon = Icons.Rounded.Link,
                onClick = { showImageUrlDialog = true },
                modifier = Modifier
                    .weight(1f)
                    .testTag("customize_image_url_button")
            )
            if (wallpaperUri != DEFAULT_START_WALLPAPER_URL) {
                OrbitGhostButton(
                    text = "Reset",
                    icon = Icons.Rounded.Refresh,
                    onClick = {
                        scope.launch {
                            WallpaperStorageHelper.cleanupManagedWallpapers(context)
                            onWallpaperUriSelected("")
                        }
                    },
                    modifier = Modifier.testTag("customize_reset_wallpaper")
                )
            }
        }
    }

    if (showImageUrlDialog) {
        SetWallpaperUrlDialog(
            initialUrl = if (wallpaperUri.startsWith("http")) wallpaperUri else DEFAULT_START_WALLPAPER_URL,
            onDismiss = { showImageUrlDialog = false },
            onConfirm = { newUrl ->
                showImageUrlDialog = false
                adjustInput = WallpaperAdjustInput.WebUrl(newUrl)
            }
        )
    }

    adjustInput?.let { pendingInput ->
        WallpaperAdjustDialog(
            input = pendingInput,
            onSaved = { savedPath ->
                adjustInput = null
                onWallpaperUriSelected(savedPath)
                onDismiss()
            },
            onDismiss = { adjustInput = null }
        )
    }
}

@Composable
private fun BackgroundModeOption(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    isWallpaperPreview: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    OrbitSheetPanel(
        selected = selected,
        onClick = onClick,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(
                        if (isWallpaperPreview) {
                            Brush.linearGradient(
                                listOf(
                                    primary.copy(alpha = 0.78f),
                                    secondary.copy(alpha = 0.68f)
                                )
                            )
                        } else {
                            Brush.linearGradient(
                                listOf(
                                    OrbitLightSolidStartBg,
                                    OrbitDarkSolidStartBg
                                )
                            )
                        }
                    )
                    .border(
                        width = 0.8.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(9.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isWallpaperPreview) MaterialTheme.colorScheme.onPrimary else primary,
                    modifier = Modifier.size(16.dp)
                )
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            if (selected) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(primary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Selected",
                        tint = primary,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SetWallpaperUrlDialog(
    initialUrl: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var url by remember { mutableStateOf(initialUrl) }
    val isValid = url.trim().startsWith("http://") || url.trim().startsWith("https://")

    OrbitDialog(
        onDismissRequest = onDismiss,
        title = "Image URL",
        icon = Icons.Rounded.Link,
        actions = {
            OrbitSecondaryButton(
                text = stringResource(R.string.action_cancel),
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            )
            OrbitPrimaryButton(
                text = stringResource(R.string.action_save),
                onClick = { onConfirm(url.trim()) },
                enabled = isValid,
                modifier = Modifier.weight(1f)
            )
        }
    ) {
        OrbitTextField(
            value = url,
            onValueChange = { url = it },
            label = "URL",
            placeholder = "https://...",
            singleLine = true
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddSpeedDialDialog(
    collectionNames: List<String> = com.example.core.datastore.DEFAULT_SPEED_DIAL_COLLECTIONS,
    defaultCollectionIndex: Int = 0,
    onDismiss: () -> Unit,
    onConfirm: (title: String, url: String, collectionIndex: Int) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var selectedCollection by remember(defaultCollectionIndex) {
        mutableStateOf(defaultCollectionIndex.coerceIn(0, (collectionNames.size - 1).coerceAtLeast(0)))
    }

    OrbitDialog(
        onDismissRequest = onDismiss,
        title = "Add Shortcut",
        icon = Icons.Rounded.Add,
        actions = {
            OrbitSecondaryButton(
                text = stringResource(R.string.action_cancel),
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            )
            OrbitPrimaryButton(
                text = stringResource(R.string.action_add),
                onClick = { onConfirm(title, url, selectedCollection) },
                enabled = url.isNotBlank(),
                modifier = Modifier.weight(1f)
            )
        }
    ) {
        OrbitTextField(
            value = title,
            onValueChange = { title = it },
            label = "Name",
            placeholder = "GitHub",
            singleLine = true
        )
        OrbitTextField(
            value = url,
            onValueChange = { url = it },
            label = "Website URL",
            placeholder = "https://github.com",
            singleLine = true
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            collectionNames.forEachIndexed { idx, name ->
                OrbitSegmentPill(
                    label = name,
                    selected = selectedCollection == idx,
                    onClick = { selectedCollection = idx }
                )
            }
        }
    }
}
