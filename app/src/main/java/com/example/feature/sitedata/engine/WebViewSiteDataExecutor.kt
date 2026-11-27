package com.example.feature.sitedata.engine

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.ContextCompat
import androidx.webkit.WebStorageCompat
import androidx.webkit.WebViewFeature
import com.example.core.common.UrlUtils
import com.example.feature.browser.webview.OrbitWebViewPool
import com.example.feature.sitedata.model.CookieEntry
import com.example.feature.sitedata.model.SiteDataCategory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.coroutines.resume

class WebViewSiteDataExecutor {

    suspend fun inspectCookies(origin: String): List<CookieEntry> =
        WebViewCookieStore.inspectCookies(origin)

    suspend fun countCookiesQuick(origin: String): Int =
        WebViewCookieStore.countCookiesQuick(origin)

    suspend fun clearCookiesForSite(origin: String, singleCookieName: String? = null) =
        WebViewCookieStore.clearCookiesForSite(origin, singleCookieName)

    suspend fun clearAllCookies() =
        WebViewCookieStore.clearAllCookies()

    suspend fun queryWebStorageOrigins(): Map<String, Pair<Long, Long>> = withContext(Dispatchers.Main) {
        withTimeoutOrNull(2_000L) {
            suspendCancellableCoroutine { cont ->
                WebStorage.getInstance().getOrigins { originsMap ->
                    val result = linkedMapOf<String, Pair<Long, Long>>()
                    originsMap?.forEach { (rawKey, originObj) ->
                        val originStr = (originObj as? WebStorage.Origin)?.origin ?: rawKey?.toString().orEmpty()
                        val normalized = UrlUtils.normalizeOrigin(originStr)
                        if (normalized != null && originObj is WebStorage.Origin) {
                            val existing = result[normalized]
                            val usage = (existing?.first ?: 0L) + originObj.usage
                            val quota = maxOf(existing?.second ?: 0L, originObj.quota)
                            result[normalized] = usage to quota
                        }
                    }
                    if (cont.isActive) cont.resume(result)
                }
            }
        } ?: emptyMap()
    }

    suspend fun inspectOriginLive(
        context: Context,
        origin: String,
        pool: OrbitWebViewPool
    ): Pair<Int, LiveJsProbeSnapshot> = withContext(Dispatchers.Main) {
        val matchingTabs = pool.getLiveWebViews().filter { (_, webView) ->
            !pool.isTabIncognito(webView) && UrlUtils.extractOrigin(webView.url) == origin
        }

        if (matchingTabs.isNotEmpty()) {
            var aggregated = LiveJsProbeSnapshot(ok = false)
            val combinedSession = mutableListOf<com.example.feature.sitedata.model.StorageKeyValueEntry>()
            var combinedSessionBytes = 0L
            var combinedSessionCount = 0

            matchingTabs.forEachIndexed { index, (_, webView) ->
                val raw = runScriptOnWebView(webView) { reqId ->
                    SiteDataJsScripts.buildInspectScript(reqId)
                }
                if (!raw.isNullOrBlank()) {
                    val parsed = OrbitSiteDataBridge.parseInspectPayload(raw)
                    if (index == 0) {
                        aggregated = parsed
                    }
                    combinedSession.addAll(parsed.sessionStorageEntries)
                    combinedSessionBytes += parsed.sessionStorageBytes
                    combinedSessionCount += parsed.sessionStorageCount
                }
            }
            val finalSnapshot = aggregated.copy(
                sessionStorageEntries = combinedSession.distinctBy { it.key },
                sessionStorageCount = combinedSessionCount,
                sessionStorageBytes = combinedSessionBytes
            )
            matchingTabs.size to finalSnapshot
        } else {
            val raw = runScriptOnOfflineOriginProbe(context, origin) { reqId ->
                SiteDataJsScripts.buildInspectScript(reqId)
            }
            val snapshot = if (!raw.isNullOrBlank()) {
                OrbitSiteDataBridge.parseInspectPayload(raw)
            } else {
                LiveJsProbeSnapshot(ok = false)
            }
            0 to snapshot
        }
    }

    suspend fun clearSiteCategory(
        context: Context,
        origin: String,
        category: SiteDataCategory,
        specificItemKey: String?,
        pool: OrbitWebViewPool
    ) {
        when (category) {
            SiteDataCategory.COOKIES -> {
                clearCookiesForSite(origin, specificItemKey)
            }
            SiteDataCategory.SESSION_STORAGE -> withContext(Dispatchers.Main) {
                val matching = pool.getLiveWebViews().filter { (_, webView) ->
                    UrlUtils.extractOrigin(webView.url) == origin
                }
                matching.forEach { (_, webView) ->
                    runScriptOnWebView(webView) { reqId ->
                        SiteDataJsScripts.buildClearSessionStorageScript(reqId, specificItemKey)
                    }
                }
            }
            SiteDataCategory.LOCAL_STORAGE -> withContext(Dispatchers.Main) {
                val matching = pool.getLiveWebViews().filter { (_, webView) ->
                    !pool.isTabIncognito(webView) && UrlUtils.extractOrigin(webView.url) == origin
                }
                if (matching.isNotEmpty()) {
                    matching.forEach { (_, webView) ->
                        runScriptOnWebView(webView) { reqId ->
                            SiteDataJsScripts.buildClearLocalStorageScript(reqId, specificItemKey)
                        }
                    }
                } else {
                    runScriptOnOfflineOriginProbe(context, origin) { reqId ->
                        SiteDataJsScripts.buildClearLocalStorageScript(reqId, specificItemKey)
                    }
                }
            }
            SiteDataCategory.INDEXED_DB -> withContext(Dispatchers.Main) {
                val matching = pool.getLiveWebViews().filter { (_, webView) ->
                    !pool.isTabIncognito(webView) && UrlUtils.extractOrigin(webView.url) == origin
                }
                if (matching.isNotEmpty()) {
                    matching.forEach { (_, webView) ->
                        runScriptOnWebView(webView) { reqId ->
                            SiteDataJsScripts.buildClearIndexedDbScript(reqId, specificItemKey)
                        }
                    }
                } else {
                    runScriptOnOfflineOriginProbe(context, origin) { reqId ->
                        SiteDataJsScripts.buildClearIndexedDbScript(reqId, specificItemKey)
                    }
                }
            }
            SiteDataCategory.CACHE -> withContext(Dispatchers.Main) {
                val matching = pool.getLiveWebViews().filter { (_, webView) ->
                    !pool.isTabIncognito(webView) && UrlUtils.extractOrigin(webView.url) == origin
                }
                val unregisterSw = specificItemKey == null
                if (matching.isNotEmpty()) {
                    matching.forEach { (_, webView) ->
                        runScriptOnWebView(webView) { reqId ->
                            SiteDataJsScripts.buildClearCacheStorageScript(reqId, specificItemKey, unregisterSw)
                        }
                    }
                } else {
                    runScriptOnOfflineOriginProbe(context, origin) { reqId ->
                        SiteDataJsScripts.buildClearCacheStorageScript(reqId, specificItemKey, unregisterSw)
                    }
                }
            }
        }
    }

    suspend fun clearAllForSite(
        context: Context,
        origin: String,
        pool: OrbitWebViewPool
    ) {
        withContext(Dispatchers.Main) {
            val matching = pool.getLiveWebViews().filter { (_, webView) ->
                UrlUtils.extractOrigin(webView.url) == origin
            }
            matching.forEach { (_, webView) ->
                runScriptOnWebView(webView) { reqId ->
                    SiteDataJsScripts.buildClearSessionStorageScript(reqId, null)
                }
                runScriptOnWebView(webView) { reqId ->
                    SiteDataJsScripts.buildClearLocalStorageScript(reqId, null)
                }
                runScriptOnWebView(webView) { reqId ->
                    SiteDataJsScripts.buildClearIndexedDbScript(reqId, null)
                }
                runScriptOnWebView(webView) { reqId ->
                    SiteDataJsScripts.buildClearCacheStorageScript(reqId, null, unregisterSw = true)
                }
            }

            if (WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA)) {
                withTimeoutOrNull(2_500L) {
                    suspendCancellableCoroutine<Unit> { cont ->
                        runCatching {
                            WebStorageCompat.deleteBrowsingDataForSite(
                                WebStorage.getInstance(),
                                origin,
                                ContextCompat.getMainExecutor(context),
                                Runnable {
                                    if (cont.isActive) cont.resume(Unit)
                                }
                            )
                        }.onFailure {
                            if (cont.isActive) cont.resume(Unit)
                        }
                    }
                }
            }

            runCatching {
                WebStorage.getInstance().deleteOrigin(origin)
                WebStorage.getInstance().deleteOrigin("$origin/")
            }
        }
        clearCookiesForSite(origin, null)
    }

    suspend fun clearHttpAndAppCacheGlobally(
        context: Context,
        pool: OrbitWebViewPool
    ) {
        withContext(Dispatchers.Main) {
            val liveWebView = pool.getAnyLiveWebView()
            if (liveWebView != null) {
                liveWebView.clearCache(true)
            } else {
                val temp = WebView(context.applicationContext)
                try {
                    temp.clearCache(true)
                } finally {
                    temp.destroy()
                }
            }
        }
        withContext(Dispatchers.IO) {
            context.cacheDir.listFiles()?.forEach { file ->
                if (file.name == "image_manager_disk_cache" ||
                    file.name == "http_cache" ||
                    file.name.startsWith("temp_")
                ) {
                    runCatching { file.deleteRecursively() }
                }
            }
        }
    }

    suspend fun clearAllBrowsingDataCompat(context: Context) = withContext(Dispatchers.Main) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA)) {
            withTimeoutOrNull(3_000L) {
                suspendCancellableCoroutine<Unit> { cont ->
                    runCatching {
                        WebStorageCompat.deleteBrowsingData(
                            WebStorage.getInstance(),
                            ContextCompat.getMainExecutor(context),
                            Runnable {
                                if (cont.isActive) cont.resume(Unit)
                            }
                        )
                    }.onFailure {
                        if (cont.isActive) cont.resume(Unit)
                    }
                }
            }
        } else {
            runCatching { WebStorage.getInstance().deleteAllData() }
        }
    }

    suspend fun clearSessionStorageAllTabs(pool: OrbitWebViewPool) = withContext(Dispatchers.Main) {
        pool.getLiveWebViews().forEach { (_, webView) ->
            runScriptOnWebView(webView) { reqId ->
                SiteDataJsScripts.buildClearSessionStorageScript(reqId, null)
            }
        }
    }

    suspend fun computeWebViewDiskTelemetry(context: Context): Pair<Long, Long> = withContext(Dispatchers.IO) {
        val dataDir = context.applicationInfo.dataDir?.let { File(it) } ?: context.filesDir.parentFile
        val appWebViewDir = dataDir?.let { File(it, "app_webview") }
        val totalWebViewBytes = if (appWebViewDir?.exists() == true) safeDirSize(appWebViewDir) else 0L

        var cacheBytes = safeDirSize(context.cacheDir)
        if (appWebViewDir?.exists() == true) {
            val cacheFolders = listOf(
                File(appWebViewDir, "Default/Cache"),
                File(appWebViewDir, "Default/Code Cache"),
                File(appWebViewDir, "Default/GPUCache"),
                File(appWebViewDir, "Cache"),
                File(appWebViewDir, "GPUCache")
            )
            cacheFolders.forEach { folder ->
                if (folder.exists()) cacheBytes += safeDirSize(folder)
            }
        }
        totalWebViewBytes to cacheBytes
    }

    private fun safeDirSize(root: File): Long {
        if (!root.exists()) return 0L
        return runCatching {
            root.walkTopDown()
                .filter { it.isFile }
                .sumOf { it.length() }
        }.getOrDefault(0L)
    }

    private suspend fun runScriptOnWebView(
        webView: WebView,
        scriptBuilder: (String) -> String
    ): String? {
        val (reqId, deferred) = OrbitSiteDataBridge.registerRequest()
        return try {
            webView.evaluateJavascript(scriptBuilder(reqId), null)
            withTimeoutOrNull(3_200L) { deferred.await() }
        } finally {
            OrbitSiteDataBridge.cancelRequest(reqId)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun runScriptOnOfflineOriginProbe(
        context: Context,
        origin: String,
        scriptBuilder: (String) -> String
    ): String? {
        val probeUrl = "$origin/__orbit_site_probe"
        val pageLoaded = CompletableDeferred<Unit>()
        val probeWebView = WebView(context.applicationContext)

        return try {
            probeWebView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                cacheMode = WebSettings.LOAD_DEFAULT
            }
            probeWebView.addJavascriptInterface(
                OrbitSiteDataBridge.createJsInterface(),
                OrbitSiteDataBridge.BRIDGE_NAME
            )
            probeWebView.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse {
                    val reqUrl = request?.url?.toString().orEmpty()
                    return if (reqUrl.startsWith(probeUrl)) {
                        WebResourceResponse(
                            "text/html",
                            "utf-8",
                            200,
                            "OK",
                            emptyMap(),
                            ByteArrayInputStream("<!doctype html><html><body></body></html>".toByteArray())
                        )
                    } else {
                        WebResourceResponse(
                            "text/plain",
                            "utf-8",
                            200,
                            "OK",
                            emptyMap(),
                            ByteArrayInputStream(ByteArray(0))
                        )
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    if (!pageLoaded.isCompleted) {
                        pageLoaded.complete(Unit)
                    }
                }
            }
            probeWebView.loadUrl(probeUrl)
            val loaded = withTimeoutOrNull(2_500L) { pageLoaded.await() } != null
            if (!loaded) return null
            runScriptOnWebView(probeWebView, scriptBuilder)
        } catch (_: Throwable) {
            null
        } finally {
            runCatching {
                probeWebView.stopLoading()
                probeWebView.removeJavascriptInterface(OrbitSiteDataBridge.BRIDGE_NAME)
                probeWebView.destroy()
            }
        }
    }
}
