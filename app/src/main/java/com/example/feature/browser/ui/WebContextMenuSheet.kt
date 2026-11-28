package com.example.feature.browser.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.core.common.UrlUtils
import com.example.core.ui.components.OrbitModalBottomSheet
import com.example.core.ui.components.OrbitSheetActionRow
import com.example.core.ui.components.OrbitSheetBentoTile
import com.example.core.ui.components.OrbitSheetHairlineDivider
import com.example.core.ui.components.OrbitSheetPanel
import com.example.core.ui.components.rememberOrbitSurfacePalette
import com.example.core.ui.theme.OrbitIncognitoPurple
import com.example.core.ui.theme.OrbitMonoStyle
import com.example.feature.browser.model.ContextTargetKind
import com.example.feature.browser.model.WebContextMenuTarget

@Composable
fun WebContextMenuSheet(
    target: WebContextMenuTarget,
    onOpenInCurrentTab: (String) -> Unit,
    onOpenInNewTab: (url: String, isIncognito: Boolean) -> Unit,
    onSelectText: () -> Unit,
    onSearchText: (String) -> Unit,
    onDownloadUrl: (String) -> Unit,
    onSetAsBackgroundImage: (String) -> Unit,
    onSaveBookmark: (title: String, url: String) -> Unit,
    onCollectImage: (title: String, imageUrl: String) -> Unit = { _, _ -> },
    onPinToHome: (title: String, url: String) -> Unit,
    onShowMessage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val linkUrl = target.linkUrl?.takeIf { it.isNotBlank() }
    val imageUrl = target.imageUrl?.takeIf { it.isNotBlank() }
    val primaryUrl = target.primaryUrl
    val copyableText = target.linkText?.takeIf { it.isNotBlank() }
        ?: target.imageAlt?.takeIf { it.isNotBlank() }
    val isWebUrl = linkUrl != null &&
        (linkUrl.startsWith("http://", ignoreCase = true) || linkUrl.startsWith("https://", ignoreCase = true))

    OrbitModalBottomSheet(onDismissRequest = onDismiss) {
        ContextTargetHeaderCard(target = target)

        if (linkUrl != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OrbitSheetBentoTile(
                    label = stringResource(R.string.ctx_open_link),
                    icon = Icons.Rounded.OpenInBrowser,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ctx_open_link"),
                    onClick = {
                        onDismiss()
                        if (isWebUrl) {
                            onOpenInCurrentTab(linkUrl)
                        } else {
                            launchExternalApp(context, linkUrl, onShowMessage)
                        }
                    }
                )
                if (isWebUrl) {
                    OrbitSheetBentoTile(
                        label = stringResource(R.string.ctx_open_new_tab),
                        icon = Icons.Rounded.Tab,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ctx_open_new_tab"),
                        onClick = {
                            onDismiss()
                            onOpenInNewTab(linkUrl, false)
                        }
                    )
                    OrbitSheetBentoTile(
                        label = stringResource(R.string.ctx_open_incognito),
                        icon = Icons.Rounded.Security,
                        accentColor = OrbitIncognitoPurple,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ctx_open_incognito"),
                        onClick = {
                            onDismiss()
                            onOpenInNewTab(linkUrl, true)
                        }
                    )
                }
                OrbitSheetBentoTile(
                    label = stringResource(R.string.ctx_open_external_app),
                    icon = Icons.AutoMirrored.Rounded.OpenInNew,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ctx_open_external"),
                    onClick = {
                        onDismiss()
                        launchExternalApp(context, linkUrl, onShowMessage)
                    }
                )
            }
        } else if (imageUrl != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OrbitSheetBentoTile(
                    label = stringResource(R.string.ctx_open_image_tab),
                    icon = Icons.Rounded.Image,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ctx_open_image_tab"),
                    onClick = {
                        onDismiss()
                        onOpenInNewTab(imageUrl, false)
                    }
                )
                OrbitSheetBentoTile(
                    label = stringResource(R.string.ctx_save_image),
                    icon = Icons.Rounded.Download,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ctx_save_image_tile"),
                    onClick = {
                        onDismiss()
                        onDownloadUrl(imageUrl)
                    }
                )
                OrbitSheetBentoTile(
                    label = stringResource(R.string.ctx_open_external_app),
                    icon = Icons.AutoMirrored.Rounded.OpenInNew,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ctx_open_external"),
                    onClick = {
                        onDismiss()
                        launchExternalApp(context, imageUrl, onShowMessage)
                    }
                )
            }
        }

        OrbitSheetPanel {
            if (primaryUrl != null) {
                OrbitSheetActionRow(
                    title = if (linkUrl != null) {
                        stringResource(R.string.ctx_copy_link)
                    } else {
                        stringResource(R.string.ctx_copy_image_link)
                    },
                    icon = Icons.Rounded.ContentCopy,
                    modifier = Modifier.testTag("ctx_copy_link"),
                    onClick = {
                        copyTextToClipboard(context, "Link URL", primaryUrl)
                        onShowMessage(context.getString(R.string.msg_link_copied))
                        onDismiss()
                    }
                )
            }
            if (copyableText != null) {
                if (primaryUrl != null) OrbitSheetHairlineDivider()
                OrbitSheetActionRow(
                    title = stringResource(R.string.ctx_copy_link_text),
                    icon = Icons.Rounded.TextFields,
                    modifier = Modifier.testTag("ctx_copy_link_text"),
                    onClick = {
                        copyTextToClipboard(context, "Link Text", copyableText)
                        onShowMessage(context.getString(R.string.msg_link_text_copied))
                        onDismiss()
                    }
                )
            }
            if (primaryUrl != null || copyableText != null) {
                OrbitSheetHairlineDivider()
            }
            OrbitSheetActionRow(
                title = stringResource(R.string.ctx_select_text),
                icon = Icons.Rounded.SelectAll,
                modifier = Modifier.testTag("ctx_select_text"),
                onClick = {
                    onDismiss()
                    onSelectText()
                }
            )
            if (copyableText != null) {
                OrbitSheetHairlineDivider()
                OrbitSheetActionRow(
                    title = stringResource(R.string.ctx_search_text),
                    icon = Icons.Rounded.Search,
                    modifier = Modifier.testTag("ctx_search_text"),
                    onClick = {
                        onDismiss()
                        onSearchText(copyableText)
                    }
                )
            }
        }

        if (imageUrl != null || isWebUrl || primaryUrl != null) {
            OrbitSheetPanel {
                var needsDivider = false
                if (imageUrl != null) {
                    OrbitSheetActionRow(
                        title = stringResource(R.string.ctx_collect_image),
                        icon = Icons.Rounded.CollectionsBookmark,
                        modifier = Modifier.testTag("ctx_collect_image"),
                        onClick = {
                            onCollectImage(
                                target.imageAlt?.takeIf { it.isNotBlank() } ?: UrlUtils.extractHost(imageUrl),
                                imageUrl
                            )
                            onShowMessage(context.getString(R.string.msg_collected_image))
                            onDismiss()
                        }
                    )
                    OrbitSheetHairlineDivider()
                    OrbitSheetActionRow(
                        title = stringResource(R.string.ctx_save_image),
                        icon = Icons.Rounded.Image,
                        modifier = Modifier.testTag("ctx_save_image"),
                        onClick = {
                            onDismiss()
                            onDownloadUrl(imageUrl)
                        }
                    )
                    OrbitSheetHairlineDivider()
                    OrbitSheetActionRow(
                        title = stringResource(R.string.ctx_set_background_image),
                        icon = Icons.Rounded.Wallpaper,
                        modifier = Modifier.testTag("ctx_set_background_image"),
                        onClick = {
                            onDismiss()
                            onSetAsBackgroundImage(imageUrl)
                        }
                    )
                    needsDivider = true
                }
                if (isWebUrl) {
                    if (needsDivider) OrbitSheetHairlineDivider()
                    OrbitSheetActionRow(
                        title = stringResource(R.string.ctx_download_link),
                        icon = Icons.Rounded.Download,
                        modifier = Modifier.testTag("ctx_download_link"),
                        onClick = {
                            onDismiss()
                            onDownloadUrl(linkUrl)
                        }
                    )
                    OrbitSheetHairlineDivider()
                    OrbitSheetActionRow(
                        title = stringResource(R.string.ctx_save_bookmark),
                        icon = Icons.Rounded.BookmarkAdd,
                        modifier = Modifier.testTag("ctx_save_bookmark"),
                        onClick = {
                            onSaveBookmark(copyableText ?: UrlUtils.extractHost(linkUrl), linkUrl)
                            onShowMessage(context.getString(R.string.msg_saved_to_app))
                            onDismiss()
                        }
                    )
                    OrbitSheetHairlineDivider()
                    OrbitSheetActionRow(
                        title = stringResource(R.string.ctx_pin_to_home),
                        icon = Icons.Rounded.PushPin,
                        modifier = Modifier.testTag("ctx_pin_speed_dial"),
                        onClick = {
                            onPinToHome(copyableText ?: UrlUtils.extractHost(linkUrl), linkUrl)
                            onDismiss()
                        }
                    )
                    needsDivider = true
                }
                if (primaryUrl != null) {
                    if (needsDivider) OrbitSheetHairlineDivider()
                    OrbitSheetActionRow(
                        title = stringResource(R.string.ctx_share_link),
                        icon = Icons.Rounded.Share,
                        modifier = Modifier.testTag("ctx_share_link"),
                        onClick = {
                            shareText(context, copyableText, primaryUrl)
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ContextTargetHeaderCard(target: WebContextMenuTarget) {
    val palette = rememberOrbitSurfacePalette()
    val hasImage = !target.imageUrl.isNullOrBlank()
    val containerShape = if (hasImage) RoundedCornerShape(14.dp) else CircleShape
    OrbitSheetPanel {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(if (hasImage) 50.dp else 40.dp)
                    .clip(containerShape)
                    .background(palette.tile)
                    .border(0.8.dp, palette.hairline, containerShape),
                contentAlignment = Alignment.Center
            ) {
                if (hasImage) {
                    AsyncImage(
                        model = target.imageUrl,
                        contentDescription = target.imageAlt ?: target.linkText,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    val icon = when (target.kind) {
                        ContextTargetKind.EMAIL -> Icons.Rounded.Email
                        ContextTargetKind.PHONE -> Icons.Rounded.Phone
                        ContextTargetKind.GEO -> Icons.Rounded.Place
                        ContextTargetKind.IMAGE -> Icons.Rounded.Image
                        else -> Icons.Rounded.Link
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = target.displayText.ifBlank { UrlUtils.extractHost(target.primaryUrl) },
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val url = target.primaryUrl
                if (!url.isNullOrBlank() && url != target.displayText) {
                    Text(
                        text = url,
                        style = OrbitMonoStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

fun copyTextToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

private fun launchExternalApp(
    context: Context,
    url: String,
    onShowMessage: (String) -> Unit
) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.ctx_open_external_app)))
    }.onFailure {
        onShowMessage(context.getString(R.string.msg_no_external_app))
    }
}

private fun shareText(context: Context, subject: String?, text: String) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        if (!subject.isNullOrBlank()) putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.ctx_share_link)))
}
