package com.example.feature.browser.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.ui.components.OrbitBadgeChip
import com.example.core.ui.components.OrbitDialog
import com.example.core.ui.components.OrbitGhostButton
import com.example.core.ui.components.OrbitPrimaryButton
import com.example.core.ui.components.OrbitSecondaryButton
import com.example.core.ui.components.OrbitSegmentPill
import com.example.core.ui.theme.OrbitSpacing
import com.example.feature.browser.repository.WallpaperCropMath
import com.example.feature.browser.repository.WallpaperStorageHelper
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

sealed interface WallpaperAdjustInput {
    data class LocalUri(val uri: Uri) : WallpaperAdjustInput
    data class WebUrl(val url: String) : WallpaperAdjustInput
    data class CurrentWallpaper(val value: String) : WallpaperAdjustInput
}

@Composable
fun WallpaperAdjustDialog(
    input: WallpaperAdjustInput,
    onSaved: (savedExternalPath: String) -> Unit,
    onError: (String) -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val hostView = LocalView.current
    val scope = rememberCoroutineScope()

    val deviceAspectRatio = remember(hostView.width, hostView.height) {
        if (hostView.width > 0 && hostView.height > 0) {
            WallpaperCropMath.normalizeAspectRatio(hostView.width.toFloat() / hostView.height.toFloat())
        } else {
            WallpaperCropMath.DEFAULT_DEVICE_ASPECT
        }
    }

    var primaryBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var dummyBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var useDummySample by remember { mutableStateOf(false) }
    var showDummyUiOverlay by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }

    var zoom by remember { mutableFloatStateOf(1f) }
    var panNormX by remember { mutableFloatStateOf(0f) }
    var panNormY by remember { mutableFloatStateOf(0f) }
    var frameSizePx by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(input) {
        isLoading = true
        loadError = null
        val dummyResult = WallpaperStorageHelper.loadDummySampleBitmap(context)
        dummyBitmap = dummyResult.getOrNull()
        val loaded = when (input) {
            is WallpaperAdjustInput.LocalUri -> WallpaperStorageHelper.loadPreviewBitmapFromUri(context, input.uri)
            is WallpaperAdjustInput.WebUrl -> WallpaperStorageHelper.loadPreviewBitmapFromUrl(context, input.url)
            is WallpaperAdjustInput.CurrentWallpaper ->
                WallpaperStorageHelper.loadPreviewBitmapFromWallpaperValue(context, input.value)
        }
        loaded
            .onSuccess { bmp ->
                primaryBitmap = bmp
                useDummySample = false
            }
            .onFailure { err ->
                loadError = err.localizedMessage
                if (dummyBitmap != null) {
                    useDummySample = true
                }
            }
        zoom = 1f
        panNormX = 0f
        panNormY = 0f
        isLoading = false
    }

    val activeBitmap = if (useDummySample) (dummyBitmap ?: primaryBitmap) else (primaryBitmap ?: dummyBitmap)
    val activeImageBitmap = remember(activeBitmap) { activeBitmap?.asImageBitmap() }

    val latestBitmap = rememberUpdatedState(activeBitmap)
    val latestFrameSize = rememberUpdatedState(frameSizePx)
    val latestZoom = rememberUpdatedState(zoom)
    val latestPanX = rememberUpdatedState(panNormX)
    val latestPanY = rememberUpdatedState(panNormY)

    OrbitDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = stringResource(R.string.wallpaper_adjust_title),
        icon = Icons.Rounded.Crop,
        showCloseButton = true,
        scrollable = false,
        maxWidth = 440.dp,
        modifier = Modifier.testTag("wallpaper_adjust_dialog"),
        actions = {
            OrbitSecondaryButton(
                text = stringResource(R.string.action_cancel),
                onClick = onDismiss,
                enabled = !isSaving,
                modifier = Modifier.weight(1f)
            )
            OrbitPrimaryButton(
                text = stringResource(R.string.wallpaper_adjust_save),
                isLoading = isSaving,
                enabled = activeBitmap != null && !isLoading,
                onClick = {
                    val bmp = activeBitmap ?: return@OrbitPrimaryButton
                    isSaving = true
                    scope.launch {
                        WallpaperStorageHelper.saveCroppedExternalWallpaper(
                            context = context,
                            sourceBitmap = bmp,
                            targetAspectRatio = deviceAspectRatio,
                            zoom = zoom,
                            panNormX = panNormX,
                            panNormY = panNormY
                        ).onSuccess { savedPath ->
                            isSaving = false
                            onSaved(savedPath)
                        }.onFailure { err ->
                            isSaving = false
                            onError(err.localizedMessage ?: "Failed to save wallpaper")
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("wallpaper_adjust_save_button")
            )
        }
    ) { palette ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 190.dp, max = 300.dp),
            contentAlignment = Alignment.Center
        ) {
            val candidateH = maxWidth / deviceAspectRatio
            val (frameW, frameH) = if (candidateH <= maxHeight) {
                maxWidth to candidateH
            } else {
                (maxHeight * deviceAspectRatio) to maxHeight
            }

            Box(
                modifier = Modifier
                    .size(width = frameW, height = frameH)
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.panel)
                    .border(
                        width = 1.4.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.78f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .onSizeChanged { frameSizePx = it }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoomChange, _ ->
                            val bmp = latestBitmap.value ?: return@detectTransformGestures
                            val fSize = latestFrameSize.value
                            if (fSize.width <= 0 || fSize.height <= 0) return@detectTransformGestures

                            val nextZoom = (latestZoom.value * zoomChange).coerceIn(
                                WallpaperCropMath.MIN_ZOOM,
                                WallpaperCropMath.MAX_ZOOM
                            )
                            val (maxPanX, maxPanY) = WallpaperCropMath.computeMaxScreenPanPx(
                                frameWidthPx = fSize.width.toFloat(),
                                frameHeightPx = fSize.height.toFloat(),
                                srcWidth = bmp.width,
                                srcHeight = bmp.height,
                                zoom = nextZoom
                            )
                            val nextPanX = if (maxPanX > 0.5f) {
                                (latestPanX.value + pan.x / maxPanX).coerceIn(-1f, 1f)
                            } else 0f
                            val nextPanY = if (maxPanY > 0.5f) {
                                (latestPanY.value + pan.y / maxPanY).coerceIn(-1f, 1f)
                            } else 0f

                            zoom = nextZoom
                            panNormX = nextPanX
                            panNormY = nextPanY
                        }
                    }
                    .testTag("wallpaper_adjust_viewport"),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isLoading -> {
                        CircularProgressIndicator(modifier = Modifier.size(26.dp))
                    }
                    activeBitmap != null && activeImageBitmap != null -> {
                        val crop = WallpaperCropMath.computeCropRect(
                            srcWidth = activeBitmap.width,
                            srcHeight = activeBitmap.height,
                            targetAspectRatio = deviceAspectRatio,
                            zoom = zoom,
                            panNormX = panNormX,
                            panNormY = panNormY
                        )
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawImage(
                                image = activeImageBitmap,
                                srcOffset = IntOffset(crop.left, crop.top),
                                srcSize = IntSize(crop.width, crop.height),
                                dstOffset = IntOffset.Zero,
                                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
                            )
                        }
                        if (showDummyUiOverlay) {
                            DummyStartUiOverlay()
                        }
                    }
                    else -> {
                        Text(
                            text = loadError ?: "No image",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OrbitSpacing.xs)
        ) {
            OrbitSegmentPill(
                label = stringResource(R.string.wallpaper_adjust_dummy_ui),
                selected = showDummyUiOverlay,
                leadingIcon = Icons.Rounded.Visibility,
                onClick = { showDummyUiOverlay = !showDummyUiOverlay },
                modifier = Modifier.testTag("wallpaper_adjust_chip_ui_overlay")
            )
            Slider(
                value = zoom,
                onValueChange = { zoom = it.coerceIn(WallpaperCropMath.MIN_ZOOM, WallpaperCropMath.MAX_ZOOM) },
                valueRange = WallpaperCropMath.MIN_ZOOM..WallpaperCropMath.MAX_ZOOM,
                enabled = activeBitmap != null && !isSaving,
                modifier = Modifier
                    .weight(1f)
                    .testTag("wallpaper_adjust_zoom_slider")
            )
            OrbitGhostButton(
                text = stringResource(R.string.wallpaper_adjust_reset),
                icon = Icons.Rounded.Refresh,
                enabled = activeBitmap != null && !isSaving,
                onClick = {
                    zoom = 1f
                    panNormX = 0f
                    panNormY = 0f
                }
            )
        }
    }
}

@Composable
private fun DummyStartUiOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0.0f to Color(0x22000000),
                    0.5f to Color(0x36000000),
                    1.0f to Color(0x90141418)
                )
            )
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.22f),
                modifier = Modifier.size(14.dp)
            ) {}
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.White.copy(alpha = 0.22f),
                modifier = Modifier.size(width = 48.dp, height = 14.dp)
            ) {}
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.22f),
                modifier = Modifier.size(14.dp)
            ) {}
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.26f),
                border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(22.dp)
            ) {}
            Row(
                modifier = Modifier.fillMaxWidth(0.88f),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                repeat(4) {
                    Surface(
                        shape = RoundedCornerShape(7.dp),
                        color = Color.White.copy(alpha = 0.22f),
                        modifier = Modifier.size(18.dp)
                    ) {}
                }
            }
        }
    }
}
