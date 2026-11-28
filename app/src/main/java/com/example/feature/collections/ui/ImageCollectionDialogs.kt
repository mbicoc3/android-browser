package com.example.feature.collections.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import com.example.R
import com.example.core.database.entity.ImageCollectionEntity
import com.example.core.ui.components.OrbitConfirmDialog
import com.example.core.ui.components.OrbitDialog
import com.example.core.ui.components.OrbitGhostButton
import com.example.core.ui.components.OrbitPrimaryButton
import com.example.core.ui.components.OrbitSecondaryButton
import com.example.core.ui.components.OrbitSegmentPill
import com.example.core.ui.components.OrbitTextField
import com.example.core.ui.components.rememberOrbitSurfacePalette
import com.example.core.ui.theme.OrbitMonoStyle
import com.example.core.ui.theme.OrbitSpacing
import com.example.feature.collections.repository.ImageCollectionRepository

@Composable
internal fun rememberTemporaryThumbnailRequest(
    imageUrl: String,
    maxDimensionPx: Int = 320
): ImageRequest {
    val context = LocalContext.current
    return remember(imageUrl, maxDimensionPx, context) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .size(maxDimensionPx, maxDimensionPx)
            .precision(Precision.INEXACT)
            .bitmapConfig(Bitmap.Config.RGB_565)
            .diskCachePolicy(CachePolicy.DISABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .crossfade(true)
            .build()
    }
}

@Composable
internal fun TemporaryThumbnailPreview(
    imageUrl: String,
    contentDescription: String,
    maxDimensionPx: Int,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val palette = rememberOrbitSurfacePalette()
    val request = rememberTemporaryThumbnailRequest(
        imageUrl = imageUrl,
        maxDimensionPx = maxDimensionPx
    )
    Box(
        modifier = modifier.background(palette.tile),
        contentAlignment = Alignment.Center
    ) {
        SubcomposeAsyncImage(
            model = request,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = Modifier.fillMaxSize()
        ) {
            when (painter.state) {
                is coil.compose.AsyncImagePainter.State.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                is coil.compose.AsyncImagePainter.State.Error -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(OrbitSpacing.sm)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.BrokenImage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = stringResource(R.string.collections_preview_unavailable),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                else -> SubcomposeAsyncImageContent()
            }
        }
    }
}

@Composable
internal fun AddCollectedImageDialog(
    existingCollections: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (title: String, imageUrl: String, collectionName: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var imageUrl by remember { mutableStateOf("") }
    var collectionName by remember { mutableStateOf(ImageCollectionRepository.DEFAULT_COLLECTION) }
    val folderSuggestions = remember(existingCollections) {
        (listOf(ImageCollectionRepository.DEFAULT_COLLECTION) + existingCollections.filter { it != "All" })
            .distinct()
    }

    OrbitDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.collections_add),
        icon = Icons.Rounded.AddPhotoAlternate,
        actions = {
            OrbitSecondaryButton(
                text = stringResource(R.string.action_cancel),
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            )
            OrbitPrimaryButton(
                text = stringResource(R.string.action_save),
                onClick = { onConfirm(title, imageUrl, collectionName) },
                enabled = imageUrl.isNotBlank(),
                modifier = Modifier
                    .weight(1f)
                    .testTag("collections_confirm_add")
            )
        }
    ) {
        OrbitTextField(
            value = imageUrl,
            onValueChange = { imageUrl = it },
            label = stringResource(R.string.collections_field_image_url),
            placeholder = "https://example.com/photo.jpg",
            modifier = Modifier.testTag("collections_input_url")
        )
        OrbitTextField(
            value = title,
            onValueChange = { title = it },
            label = stringResource(R.string.collections_field_title),
            modifier = Modifier.testTag("collections_input_title")
        )
        OrbitTextField(
            value = collectionName,
            onValueChange = { collectionName = it },
            label = stringResource(R.string.collections_field_folder),
            modifier = Modifier.testTag("collections_input_folder")
        )
        if (folderSuggestions.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                items(folderSuggestions) { suggestion ->
                    OrbitSegmentPill(
                        label = suggestion,
                        selected = collectionName.equals(suggestion, ignoreCase = true),
                        onClick = { collectionName = suggestion }
                    )
                }
            }
        }
    }
}

@Composable
internal fun ImageCollectionDetailDialog(
    item: ImageCollectionEntity,
    existingCollections: List<String>,
    onOpenImageUrl: (String) -> Unit,
    onOpenSourcePage: (String) -> Unit,
    onCopyUrl: (String) -> Unit,
    onDownloadImage: (String) -> Unit,
    onMoveToCollection: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var folderDraft by remember(item.id, item.collectionName) { mutableStateOf(item.collectionName) }
    val folderSuggestions = remember(existingCollections) {
        (listOf(ImageCollectionRepository.DEFAULT_COLLECTION) + existingCollections.filter { it != "All" })
            .distinct()
    }

    OrbitDialog(
        onDismissRequest = onDismiss,
        title = item.title,
        subtitle = item.collectionName,
        icon = Icons.Rounded.CollectionsBookmark,
        actions = {
            OrbitSecondaryButton(
                text = stringResource(R.string.action_close),
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            )
            OrbitPrimaryButton(
                text = stringResource(R.string.collections_action_open_image),
                icon = Icons.Rounded.OpenInBrowser,
                onClick = {
                    onDismiss()
                    onOpenImageUrl(item.imageUrl)
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("collection_detail_open")
            )
        }
    ) {
        TemporaryThumbnailPreview(
            imageUrl = item.imageUrl,
            contentDescription = item.title,
            maxDimensionPx = 480,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .clip(RoundedCornerShape(14.dp))
        )

        Text(
            text = item.imageUrl,
            style = OrbitMonoStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OrbitSecondaryButton(
                text = stringResource(R.string.omnibox_action_copy_url),
                icon = Icons.Rounded.ContentCopy,
                onClick = { onCopyUrl(item.imageUrl) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("collection_detail_copy")
            )
            OrbitSecondaryButton(
                text = stringResource(R.string.omnibox_action_download),
                icon = Icons.Rounded.Download,
                onClick = {
                    onDismiss()
                    onDownloadImage(item.imageUrl)
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("collection_detail_download")
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (item.sourcePageUrl.isNotBlank()) {
                OrbitGhostButton(
                    text = stringResource(R.string.collections_action_source_page),
                    icon = Icons.Rounded.Public,
                    onClick = {
                        onDismiss()
                        onOpenSourcePage(item.sourcePageUrl)
                    },
                    modifier = Modifier.testTag("collection_detail_source")
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            OrbitGhostButton(
                text = stringResource(R.string.action_delete),
                icon = Icons.Rounded.DeleteOutline,
                isDestructive = true,
                onClick = {
                    onDismiss()
                    onDelete(item.id)
                },
                modifier = Modifier.testTag("collection_detail_delete")
            )
        }

        if (folderSuggestions.size > 1 || !folderDraft.equals(item.collectionName, ignoreCase = true)) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(top = 2.dp)
            ) {
                items(folderSuggestions) { folder ->
                    OrbitSegmentPill(
                        label = folder,
                        selected = folder.equals(folderDraft, ignoreCase = true),
                        onClick = {
                            folderDraft = folder
                            onMoveToCollection(item.id, folder)
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun ClearCollectionsConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    OrbitConfirmDialog(
        title = stringResource(R.string.collections_clear_all_title),
        message = stringResource(R.string.collections_clear_all_message),
        confirmText = stringResource(R.string.collections_clear_all),
        dismissText = stringResource(R.string.action_cancel),
        isDestructive = true,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}
