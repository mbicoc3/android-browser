package com.example.feature.browser.repository

import kotlin.math.max
import kotlin.math.roundToInt

data class WallpaperCropRect(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int
)

/**
 * Pure Kotlin edge-locked crop math matching device aspect ratio.
 * Guarantees that the cropped rectangle stays strictly within [0..srcWidth, 0..srcHeight]
 * so no black gaps or uncovered screen edges can ever occur.
 */
object WallpaperCropMath {

    const val DEFAULT_DEVICE_ASPECT = 9f / 19.5f
    const val MIN_ZOOM = 1f
    const val MAX_ZOOM = 3f

    fun normalizeAspectRatio(aspectRatio: Float): Float {
        if (!aspectRatio.isFinite() || aspectRatio <= 0f) return DEFAULT_DEVICE_ASPECT
        return aspectRatio.coerceIn(0.35f, 2.5f)
    }

    fun computeCropRect(
        srcWidth: Int,
        srcHeight: Int,
        targetAspectRatio: Float,
        zoom: Float = 1f,
        panNormX: Float = 0f,
        panNormY: Float = 0f
    ): WallpaperCropRect {
        val safeSrcW = srcWidth.coerceAtLeast(1)
        val safeSrcH = srcHeight.coerceAtLeast(1)
        val targetAspect = normalizeAspectRatio(targetAspectRatio)
        val safeZoom = if (zoom.isFinite()) zoom.coerceIn(MIN_ZOOM, MAX_ZOOM) else MIN_ZOOM
        val safePanX = if (panNormX.isFinite()) panNormX.coerceIn(-1f, 1f) else 0f
        val safePanY = if (panNormY.isFinite()) panNormY.coerceIn(-1f, 1f) else 0f

        val srcAspect = safeSrcW.toFloat() / safeSrcH.toFloat()
        val baseCropW: Float
        val baseCropH: Float
        if (srcAspect > targetAspect) {
            // Source is wider than device viewport: full height fits, width crops horizontally
            baseCropH = safeSrcH.toFloat()
            baseCropW = baseCropH * targetAspect
        } else {
            // Source is taller or equal: full width fits, height crops vertically
            baseCropW = safeSrcW.toFloat()
            baseCropH = baseCropW / targetAspect
        }

        val cropW = (baseCropW / safeZoom).roundToInt().coerceIn(1, safeSrcW)
        val cropH = (baseCropH / safeZoom).roundToInt().coerceIn(1, safeSrcH)

        val slackX = (safeSrcW - cropW).coerceAtLeast(0)
        val slackY = (safeSrcH - cropH).coerceAtLeast(0)

        // panNorm = +1 means user dragged image maximally right/down -> show left/top edge (offset 0)
        // panNorm = -1 means user dragged image maximally left/up -> show right/bottom edge (offset slack)
        val left = (slackX * 0.5f * (1f - safePanX)).roundToInt().coerceIn(0, slackX)
        val top = (slackY * 0.5f * (1f - safePanY)).roundToInt().coerceIn(0, slackY)

        return WallpaperCropRect(
            left = left,
            top = top,
            width = cropW,
            height = cropH
        )
    }

    /**
     * Computes maximum pan travel in screen pixels for the given viewport frame and zoom level,
     * enabling 1:1 finger tracking while keeping all 4 image edges locked outside/at the frame.
     */
    fun computeMaxScreenPanPx(
        frameWidthPx: Float,
        frameHeightPx: Float,
        srcWidth: Int,
        srcHeight: Int,
        zoom: Float
    ): Pair<Float, Float> {
        if (frameWidthPx <= 0f || frameHeightPx <= 0f || srcWidth <= 0 || srcHeight <= 0) {
            return 0f to 0f
        }
        val frameAspect = normalizeAspectRatio(frameWidthPx / frameHeightPx)
        val rect = computeCropRect(
            srcWidth = srcWidth,
            srcHeight = srcHeight,
            targetAspectRatio = frameAspect,
            zoom = zoom
        )
        val renderedW = frameWidthPx * (srcWidth.toFloat() / rect.width.toFloat())
        val renderedH = frameHeightPx * (srcHeight.toFloat() / rect.height.toFloat())
        val maxPanX = max(0f, (renderedW - frameWidthPx) * 0.5f)
        val maxPanY = max(0f, (renderedH - frameHeightPx) * 0.5f)
        return maxPanX to maxPanY
    }

    fun formatAspectLabel(aspectRatio: Float): String {
        val ratio = normalizeAspectRatio(aspectRatio)
        return when {
            ratio < 0.5f -> "9:20"
            ratio < 0.6f -> "9:16"
            ratio < 0.85f -> "3:4"
            ratio < 1.15f -> "1:1"
            ratio < 1.45f -> "4:3"
            else -> "16:9"
        }
    }
}
