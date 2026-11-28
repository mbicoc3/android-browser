package com.example.feature.tabs.preview

import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import android.view.View
import android.webkit.WebView
import com.example.core.common.UrlUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Memory-bounded thumbnail cache for Tab Card View previews.
 * Uses downscaled RGB_565 bitmaps (~170 KB per tab) inside a capped LruCache (max 12 MB)
 * with per-tab rate limiting to avoid UI thread stutter or OOM under many tabs.
 */
class TabPreviewStore {

    private val maxCacheKb = minOf(
        12 * 1024,
        (Runtime.getRuntime().maxMemory() / 1024L / 16L).toInt().coerceAtLeast(2 * 1024)
    )

    private val cache = object : LruCache<String, Bitmap>(maxCacheKb) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return (value.allocationByteCount / 1024).coerceAtLeast(1)
        }
    }

    private val lastCapturedMs = ConcurrentHashMap<String, Long>()
    private val lastCapturedUrl = ConcurrentHashMap<String, String>()

    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    fun getPreview(tabId: String): Bitmap? = cache.get(tabId)

    fun invalidateIfUrlChanged(tabId: String, newUrl: String) {
        val prevUrl = lastCapturedUrl[tabId] ?: return
        if (prevUrl != newUrl) {
            cache.remove(tabId)
            lastCapturedMs.remove(tabId)
            lastCapturedUrl.remove(tabId)
            _revision.value = _revision.value + 1L
        }
    }

    fun captureFromWebView(
        tabId: String,
        webView: WebView,
        force: Boolean = false,
        minIntervalMs: Long = 500L
    ) {
        if (!webView.isAttachedToWindow ||
            webView.visibility != View.VISIBLE ||
            webView.width <= 0 ||
            webView.height <= 0
        ) return
        val currentUrl = webView.url
        if (currentUrl.isNullOrBlank() || UrlUtils.isStartPage(currentUrl)) return

        val now = System.currentTimeMillis()
        val prev = lastCapturedMs[tabId] ?: 0L
        val urlChanged = lastCapturedUrl[tabId] != currentUrl
        if (!force && !urlChanged && (now - prev) < minIntervalMs) return

        val targetWidth = minOf(MAX_PREVIEW_WIDTH_PX, webView.width)
        val scale = targetWidth.toFloat() / webView.width.toFloat()
        if (scale <= 0f) return

        val targetHeight = (targetWidth * PREVIEW_ASPECT_HEIGHT_OVER_WIDTH)
            .toInt()
            .coerceIn(140, MAX_PREVIEW_HEIGHT_PX)

        val bitmap = runCatching {
            val bmp = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.RGB_565)
            val canvas = Canvas(bmp)
            canvas.scale(scale, scale)
            canvas.translate(-webView.scrollX.toFloat(), -webView.scrollY.toFloat())
            webView.draw(canvas)
            bmp
        }.getOrNull() ?: return

        cache.put(tabId, bitmap)
        lastCapturedMs[tabId] = now
        lastCapturedUrl[tabId] = currentUrl
        _revision.value = _revision.value + 1L
    }

    fun remove(tabId: String) {
        val hadEntry = cache.remove(tabId) != null ||
            lastCapturedMs.remove(tabId) != null ||
            lastCapturedUrl.remove(tabId) != null
        if (hadEntry) {
            _revision.value = _revision.value + 1L
        }
    }

    fun clear() {
        cache.evictAll()
        lastCapturedMs.clear()
        lastCapturedUrl.clear()
        _revision.value = _revision.value + 1L
    }

    companion object {
        private const val MAX_PREVIEW_WIDTH_PX = 360
        private const val MAX_PREVIEW_HEIGHT_PX = 260
        private const val PREVIEW_ASPECT_HEIGHT_OVER_WIDTH = 0.68f
    }
}
