package com.example

import com.example.feature.browser.repository.WallpaperCropMath
import com.example.feature.browser.repository.WallpaperStorageHelper
import com.example.feature.browser.webview.OrbitPullRefreshMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperStorageHelperTest {

    @Test
    fun `computeTargetSize preserves images under 1920px and downscales oversized photos`() {
        assertEquals(1200 to 800, WallpaperStorageHelper.computeTargetSize(1200, 800))
        assertEquals(1920 to 1080, WallpaperStorageHelper.computeTargetSize(3840, 2160))
        assertEquals(1080 to 1920, WallpaperStorageHelper.computeTargetSize(2160, 3840))
    }

    @Test
    fun `computeInSampleSize returns power of two subsampling`() {
        assertEquals(1, WallpaperStorageHelper.computeInSampleSize(1280, 720, 1920))
        assertEquals(2, WallpaperStorageHelper.computeInSampleSize(4000, 3000, 1920))
        assertEquals(4, WallpaperStorageHelper.computeInSampleSize(8000, 6000, 1920))
    }

    @Test
    fun `computeCropRect keeps all edges locked inside source bounds at device ratio`() {
        val deviceAspect = 0.5f // 1:2 portrait phone
        val srcW = 2000
        val srcH = 1000

        // Center crop at 1x
        val center = WallpaperCropMath.computeCropRect(srcW, srcH, deviceAspect, zoom = 1f, panNormX = 0f, panNormY = 0f)
        assertEquals(1000, center.height)
        assertEquals(500, center.width)
        assertEquals(750, center.left)
        assertEquals(0, center.top)

        // Extreme positive pan (+2f coerced to +1f) locks to left=0, top=0
        val topLeftEdge = WallpaperCropMath.computeCropRect(srcW, srcH, deviceAspect, zoom = 2f, panNormX = 2f, panNormY = 2f)
        assertEquals(0, topLeftEdge.left)
        assertEquals(0, topLeftEdge.top)
        assertEquals(250, topLeftEdge.width)
        assertEquals(500, topLeftEdge.height)

        // Extreme negative pan (-2f coerced to -1f) locks cleanly to right/bottom edge without going out of bounds
        val bottomRightEdge = WallpaperCropMath.computeCropRect(srcW, srcH, deviceAspect, zoom = 2f, panNormX = -2f, panNormY = -2f)
        assertEquals(srcW, bottomRightEdge.left + bottomRightEdge.width)
        assertEquals(srcH, bottomRightEdge.top + bottomRightEdge.height)
    }

    @Test
    fun `pull refresh math triggers only when drag meets threshold`() {
        assertEquals(0f, OrbitPullRefreshMath.computeDampedFraction(-20f, 200f), 0.001f)
        val halfway = OrbitPullRefreshMath.computeDampedFraction(100f, 200f)
        assertEquals(0.5f, halfway, 0.001f)
        assertFalse(OrbitPullRefreshMath.shouldTriggerRefresh(halfway))

        val ready = OrbitPullRefreshMath.computeDampedFraction(220f, 200f)
        assertTrue(OrbitPullRefreshMath.shouldTriggerRefresh(ready))
        assertTrue(ready <= 1.45f)
    }
}
