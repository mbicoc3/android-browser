package com.example.feature.browser.webview

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import com.example.core.datastore.BrowserPreferences
import com.example.core.webengine.AndroidWebViewSession
import com.example.core.webengine.ContentBlockerEngine
import com.example.core.webengine.WebEngineSession
import com.example.feature.browser.model.BrowserTabModel
import com.example.feature.browser.model.DirectImageInfo
import com.example.feature.browser.model.WebContextMenuTarget
import com.example.feature.passwords.autofill.BrowserPasswordCoordinator
import com.example.feature.tabs.preview.TabPreviewStore
import java.util.concurrent.atomic.AtomicReference

/**
 * Manages live [WebEngineSession] / WebView instances per tabId so switching tabs or visiting
 * secondary screens preserves the active page DOM and scroll position, while freeing resources cleanly.
 */
class OrbitWebViewPool(
    private val contentBlocker: ContentBlockerEngine,
    private val passwordCoordinator: BrowserPasswordCoordinator? = null
) {
    private val pool = mutableMapOf<String, WebView>()
    private val sessions = mutableMapOf<String, WebEngineSession>()
    private val incognitoByTabId = mutableMapOf<String, Boolean>()
    private val sandboxProfileByTabId = mutableMapOf<String, String?>()
    private val lastSettingsSignature = mutableMapOf<String, String>()
    private val runtimePreferences = mutableMapOf<String, AtomicReference<BrowserPreferences>>()
    private val contextMenuCallbacks = mutableMapOf<String, AtomicReference<((WebContextMenuTarget) -> Unit)?>>()
    private val downloadCallbacks = mutableMapOf<String, AtomicReference<((String, String?, String?) -> Unit)?>>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingCaptureRunnables = mutableMapOf<String, Runnable>()
    val previewStore = TabPreviewStore()

    fun getOrCreate(
        context: Context,
        tab: BrowserTabModel,
        preferences: BrowserPreferences,
        callbacks: WebViewCallbacks,
        onContextMenuRequested: ((WebContextMenuTarget) -> Unit)? = null,
        onDownloadRequested: ((url: String, contentDisposition: String?, mimeType: String?) -> Unit)? = null
    ): WebView {
        val targetProfile = if (tab.isIncognito) null else tab.sandboxProfileName
        var existing = pool[tab.id]
        if (existing != null && sandboxProfileByTabId[tab.id] != targetProfile) {
            destroyTab(tab.id)
            existing = null
        }

        val signature = "${tab.isIncognito}_${tab.isDesktopMode}_${preferences.javaScriptEnabled}_${preferences.forceDarkWeb}_${preferences.blockTrackers}_${preferences.cosmeticFilteringEnabled}_${targetProfile.orEmpty()}"
        incognitoByTabId[tab.id] = tab.isIncognito
        sandboxProfileByTabId[tab.id] = targetProfile
        val prefsRef = runtimePreferences.getOrPut(tab.id) { AtomicReference(preferences) }
        prefsRef.set(preferences)
        val ctxMenuRef = contextMenuCallbacks.getOrPut(tab.id) { AtomicReference(onContextMenuRequested) }
        if (onContextMenuRequested != null) {
            ctxMenuRef.set(onContextMenuRequested)
        }
        val downloadRef = downloadCallbacks.getOrPut(tab.id) { AtomicReference(onDownloadRequested) }
        if (onDownloadRequested != null) {
            downloadRef.set(onDownloadRequested)
        }

        if (existing != null) {
            if (lastSettingsSignature[tab.id] != signature) {
                val desktopChanged = lastSettingsSignature[tab.id]?.split("_")?.getOrNull(1) != tab.isDesktopMode.toString()
                OrbitWebViewFactory.applySettings(
                    webView = existing,
                    isIncognito = tab.isIncognito,
                    isDesktopMode = tab.isDesktopMode,
                    preferences = preferences,
                    sandboxProfileName = targetProfile
                )
                lastSettingsSignature[tab.id] = signature
                if (desktopChanged) {
                    existing.reload()
                }
            }
            return existing
        }

        val poolAwareCallbacks = object : WebViewCallbacks {
            override fun onPageStarted(tabId: String, url: String) {
                previewStore.invalidateIfUrlChanged(tabId, url)
                callbacks.onPageStarted(tabId, url)
            }

            override fun onPageFinished(
                tabId: String,
                url: String,
                title: String,
                canGoBack: Boolean,
                canGoForward: Boolean
            ) {
                callbacks.onPageFinished(tabId, url, title, canGoBack, canGoForward)
                scheduleVisualPreviewCapture(tabId, delayMs = 200L)
            }

            override fun onProgressChanged(tabId: String, progress: Int) =
                callbacks.onProgressChanged(tabId, progress)

            override fun onTitleReceived(tabId: String, title: String) =
                callbacks.onTitleReceived(tabId, title)

            override fun onTrackerBlocked(tabId: String) =
                callbacks.onTrackerBlocked(tabId)

            override fun onDirectImageDetected(tabId: String, imageInfo: DirectImageInfo?) =
                callbacks.onDirectImageDetected(tabId, imageInfo)

            override fun onDownloadRequested(url: String, contentDisposition: String?, mimeType: String?) =
                callbacks.onDownloadRequested(url, contentDisposition, mimeType)

            override fun onFindMatchesUpdated(tabId: String, activeMatchOrdinal: Int, numberOfMatches: Int) =
                callbacks.onFindMatchesUpdated(tabId, activeMatchOrdinal, numberOfMatches)

            override fun onPageFaviconBitmap(tabId: String, pageUrl: String, bitmap: Bitmap) =
                callbacks.onPageFaviconBitmap(tabId, pageUrl, bitmap)

            override fun onPageFaviconUrls(tabId: String, pageUrl: String, iconUrls: List<String>) =
                callbacks.onPageFaviconUrls(tabId, pageUrl, iconUrls)

            override fun onUrlHistoryUpdated(tabId: String, url: String, canGoBack: Boolean, canGoForward: Boolean) {
                callbacks.onUrlHistoryUpdated(tabId, url, canGoBack, canGoForward)
                scheduleVisualPreviewCapture(tabId, delayMs = 300L)
            }

            override fun onVisualContentChanged(tabId: String) {
                scheduleVisualPreviewCapture(tabId, delayMs = 260L)
            }
        }

        val created = OrbitWebViewFactory.createWebView(
            context = context,
            tabId = tab.id,
            isIncognito = tab.isIncognito,
            isDesktopMode = tab.isDesktopMode,
            preferencesProvider = { prefsRef.get() },
            contentBlocker = contentBlocker,
            callbacks = poolAwareCallbacks,
            downloadInterceptProvider = { downloadRef.get() },
            passwordCoordinator = passwordCoordinator,
            sandboxProfileName = targetProfile
        ).apply {
            WebHitTestHelper.attach(
                webView = this,
                tabId = tab.id,
                onContextMenuTarget = { target -> ctxMenuRef.get()?.invoke(target) }
            )
            (this as? OrbitPullRefreshWebView)?.onVisualContentInvalidated = {
                scheduleVisualPreviewCapture(tab.id, delayMs = 320L)
            }
        }
        pool[tab.id] = created
        sessions[tab.id] = AndroidWebViewSession(created)
        lastSettingsSignature[tab.id] = signature
        return created
    }

    fun scheduleVisualPreviewCapture(tabId: String, delayMs: Long = 300L) {
        if (runtimePreferences[tabId]?.get()?.tabPreviewEnabled != true) return
        pendingCaptureRunnables.remove(tabId)?.let { mainHandler.removeCallbacks(it) }
        val task = Runnable {
            pendingCaptureRunnables.remove(tabId)
            val enabled = runtimePreferences[tabId]?.get()?.tabPreviewEnabled == true
            capturePreviewIfEnabled(tabId = tabId, enabled = enabled, force = true)
        }
        pendingCaptureRunnables[tabId] = task
        mainHandler.postDelayed(task, delayMs)
    }

    fun getIfPresent(tabId: String): WebView? = pool[tabId]

    fun getSessionIfPresent(tabId: String): WebEngineSession? = sessions[tabId]

    fun capturePreviewIfEnabled(tabId: String, enabled: Boolean, force: Boolean = false) {
        if (!enabled) return
        val webView = pool[tabId] ?: return
        previewStore.captureFromWebView(tabId = tabId, webView = webView, force = force)
    }

    fun getPreviewBitmap(tabId: String): Bitmap? = previewStore.getPreview(tabId)

    fun clearPreviews() {
        previewStore.clear()
    }

    fun getLiveWebViews(): List<Pair<String, WebView>> = pool.entries.map { it.key to it.value }

    fun getAnyLiveWebView(): WebView? = pool.values.firstOrNull()

    fun getOpenTabCount(): Int = pool.size

    fun isTabIncognito(webView: WebView): Boolean {
        val tabId = pool.entries.firstOrNull { it.value === webView }?.key ?: return false
        return incognitoByTabId[tabId] == true
    }

    fun destroyTab(tabId: String) {
        pendingCaptureRunnables.remove(tabId)?.let { mainHandler.removeCallbacks(it) }
        val session = sessions.remove(tabId)
        pool.remove(tabId)
        if (session != null) {
            session.destroySession()
        }
        previewStore.remove(tabId)
        incognitoByTabId.remove(tabId)
        sandboxProfileByTabId.remove(tabId)
        lastSettingsSignature.remove(tabId)
        runtimePreferences.remove(tabId)
        contextMenuCallbacks.remove(tabId)
        downloadCallbacks.remove(tabId)
        passwordCoordinator?.clearTabState(tabId)
    }

    fun destroyAll() {
        pendingCaptureRunnables.values.forEach { mainHandler.removeCallbacks(it) }
        pendingCaptureRunnables.clear()
        sessions.values.forEach { it.destroySession() }
        sessions.clear()
        pool.clear()
        previewStore.clear()
        incognitoByTabId.clear()
        sandboxProfileByTabId.clear()
        lastSettingsSignature.clear()
        runtimePreferences.clear()
        contextMenuCallbacks.clear()
        downloadCallbacks.clear()
    }
}
