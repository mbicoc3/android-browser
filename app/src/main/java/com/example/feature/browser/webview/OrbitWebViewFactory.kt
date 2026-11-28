package com.example.feature.browser.webview

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.webkit.WebViewRenderProcess
import androidx.webkit.WebViewRenderProcessClient
import com.example.core.common.UrlUtils
import com.example.core.datastore.BrowserPreferences
import com.example.core.webengine.ContentBlockerEngine
import com.example.core.webengine.WebResourceCategory
import com.example.feature.browser.model.DirectImageInfo
import com.example.feature.passwords.autofill.BrowserPasswordCoordinator
import com.example.feature.passwords.autofill.WebFormInspector
import com.example.feature.sandbox.engine.SandboxProfileEngine
import com.example.feature.sitedata.engine.OrbitSiteDataBridge
import java.io.ByteArrayInputStream
import java.net.URI

interface WebViewCallbacks {
    fun onPageStarted(tabId: String, url: String)
    fun onPageFinished(tabId: String, url: String, title: String, canGoBack: Boolean, canGoForward: Boolean)
    fun onProgressChanged(tabId: String, progress: Int)
    fun onTitleReceived(tabId: String, title: String)
    fun onTrackerBlocked(tabId: String)
    fun onDirectImageDetected(tabId: String, imageInfo: DirectImageInfo?)
    fun onDownloadRequested(url: String, contentDisposition: String?, mimeType: String?)
    fun onFindMatchesUpdated(tabId: String, activeMatchOrdinal: Int, numberOfMatches: Int)
    fun onPageFaviconBitmap(tabId: String, pageUrl: String, bitmap: Bitmap) {}
    fun onPageFaviconUrls(tabId: String, pageUrl: String, iconUrls: List<String>) {}
    fun onUrlHistoryUpdated(tabId: String, url: String, canGoBack: Boolean, canGoForward: Boolean) {}
    fun onVisualContentChanged(tabId: String) {}
}

object OrbitWebViewFactory {

    private const val DESKTOP_USER_AGENT =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    @SuppressLint("SetJavaScriptEnabled")
    fun createWebView(
        context: Context,
        tabId: String,
        isIncognito: Boolean,
        isDesktopMode: Boolean,
        preferencesProvider: () -> BrowserPreferences,
        contentBlocker: ContentBlockerEngine,
        callbacks: WebViewCallbacks,
        downloadInterceptProvider: (() -> ((url: String, contentDisposition: String?, mimeType: String?) -> Unit)?)? = null,
        passwordCoordinator: BrowserPasswordCoordinator? = null,
        sandboxProfileName: String? = null
    ): WebView {
        val initialPreferences = preferencesProvider()
        return OrbitPullRefreshWebView(context).apply {
            if (!isIncognito && !sandboxProfileName.isNullOrBlank()) {
                SandboxProfileEngine.attachProfileToWebView(this, sandboxProfileName)
            }
            applySettings(
                webView = this,
                isIncognito = isIncognito,
                isDesktopMode = isDesktopMode,
                preferences = initialPreferences,
                sandboxProfileName = sandboxProfileName
            )

            addJavascriptInterface(
                OrbitSiteDataBridge.createJsInterface(),
                OrbitSiteDataBridge.BRIDGE_NAME
            )
            if (passwordCoordinator != null) {
                addJavascriptInterface(
                    WebFormInspector.JsBridge(
                        tabId = tabId,
                        onFieldFocused = { focusTabId, pageUrl, formType, hasUser, hasPass ->
                            passwordCoordinator.onLoginFieldFocused(
                                tabId = focusTabId,
                                pageUrl = pageUrl,
                                formType = formType,
                                hasUsernameField = hasUser,
                                hasPasswordField = hasPass
                            )
                        },
                        onFormSubmitted = { subTabId, pageUrl, formType, username, password ->
                            if (!isIncognito) {
                                passwordCoordinator.onCredentialFormSubmitted(
                                    tabId = subTabId,
                                    pageUrl = pageUrl,
                                    formType = formType,
                                    username = username,
                                    password = password
                                )
                            }
                        }
                    ),
                    WebFormInspector.BRIDGE_NAME
                )
            }

            setFindListener { activeOrdinal, count, isDoneCounting ->
                if (isDoneCounting) {
                    callbacks.onFindMatchesUpdated(tabId, activeOrdinal, count)
                }
            }

            setDownloadListener { url, _, contentDisposition, mimeType, _ ->
                if (!url.isNullOrBlank()) {
                    val interceptor = downloadInterceptProvider?.invoke()
                    if (interceptor != null) {
                        interceptor(url, contentDisposition, mimeType)
                    } else {
                        callbacks.onDownloadRequested(url, contentDisposition, mimeType)
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    callbacks.onProgressChanged(tabId, newProgress)
                    if (newProgress >= 75) {
                        callbacks.onVisualContentChanged(tabId)
                    }
                }

                override fun onReceivedTitle(view: WebView?, title: String?) {
                    if (!title.isNullOrBlank()) {
                        callbacks.onTitleReceived(tabId, title)
                    }
                }

                override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
                    val pageUrl = view?.url.orEmpty()
                    if (icon != null && pageUrl.isNotBlank() && !UrlUtils.isStartPage(pageUrl)) {
                        callbacks.onPageFaviconBitmap(tabId, pageUrl, icon)
                    }
                }
            }

            webViewClient = object : WebViewClient() {
                @Volatile
                private var currentDocumentHost: String? = null

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val uri = request?.url ?: return false
                    val scheme = uri.scheme?.lowercase().orEmpty()
                    if (scheme == "http" || scheme == "https" || scheme == "about" ||
                        (scheme == "file" && UrlUtils.isOfflineArchiveUrl(uri.toString()))
                    ) {
                        return false
                    }
                    runCatching {
                        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                    return true
                }

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val currentPreferences = preferencesProvider()
                    if (!currentPreferences.blockTrackers || request == null) {
                        return super.shouldInterceptRequest(view, request)
                    }
                    if (request.isForMainFrame) {
                        currentDocumentHost = request.url?.host?.lowercase()
                        return super.shouldInterceptRequest(view, request)
                    }
                    val reqUrl = request.url?.toString() ?: return super.shouldInterceptRequest(view, request)
                    val docHost = currentDocumentHost ?: request.requestHeaders["Referer"]?.let { ref ->
                        runCatching { URI(ref).host?.lowercase() }.getOrNull()
                    }
                    val category = classifyResource(reqUrl, request.requestHeaders)
                    val decision = contentBlocker.evaluateRequest(
                        requestUrl = reqUrl,
                        documentHost = docHost,
                        category = category
                    )
                    if (decision.shouldBlock) {
                        callbacks.onTrackerBlocked(tabId)
                        return WebResourceResponse(
                            "text/plain",
                            "utf-8",
                            ByteArrayInputStream(ByteArray(0))
                        )
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    if (!url.isNullOrBlank()) {
                        currentDocumentHost = runCatching { URI(url).host?.lowercase() }.getOrNull()
                        passwordCoordinator?.onPageNavigated(tabId, url)
                        callbacks.onPageStarted(tabId, url)
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    val resolvedUrl = url ?: view?.url.orEmpty()
                    val resolvedTitle = view?.title?.takeIf { it.isNotBlank() } ?: resolvedUrl
                    val host = runCatching { URI(resolvedUrl).host?.lowercase() }.getOrNull()
                    currentDocumentHost = host

                    val currentPreferences = preferencesProvider()
                    if (view != null &&
                        currentPreferences.blockTrackers &&
                        currentPreferences.cosmeticFilteringEnabled &&
                        currentPreferences.javaScriptEnabled &&
                        !UrlUtils.isStartPage(resolvedUrl)
                    ) {
                        val css = contentBlocker.cosmeticCssForHost(host)
                        if (!css.isNullOrBlank()) {
                            view.evaluateJavascript(buildCosmeticInjectScript(css), null)
                        }
                    }

                    if (view != null && !UrlUtils.isStartPage(resolvedUrl)) {
                        WebMediaInspector.inspectDirectImage(view, resolvedUrl) { imageInfo ->
                            callbacks.onDirectImageDetected(tabId, imageInfo)
                        }
                        WebMediaInspector.extractFaviconUrls(view, resolvedUrl) { iconUrls ->
                            callbacks.onPageFaviconUrls(tabId, resolvedUrl, iconUrls)
                        }
                        if (passwordCoordinator != null && currentPreferences.javaScriptEnabled) {
                            WebFormInspector.attachFormDetector(view, resolvedUrl)
                        }
                        passwordCoordinator?.onPageNavigated(tabId, resolvedUrl)
                    } else {
                        callbacks.onDirectImageDetected(tabId, null)
                    }

                    if (view != null && !UrlUtils.isStartPage(resolvedUrl) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        view.postVisualStateCallback(
                            System.nanoTime(),
                            object : WebView.VisualStateCallback() {
                                override fun onComplete(requestId: Long) {
                                    callbacks.onVisualContentChanged(tabId)
                                }
                            }
                        )
                    }

                    callbacks.onPageFinished(
                        tabId = tabId,
                        url = resolvedUrl,
                        title = resolvedTitle,
                        canGoBack = view?.canGoBack() == true,
                        canGoForward = view?.canGoForward() == true
                    )
                }

                override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                    super.doUpdateVisitedHistory(view, url, isReload)
                    if (!url.isNullOrBlank() && !UrlUtils.isStartPage(url)) {
                        callbacks.onUrlHistoryUpdated(
                            tabId = tabId,
                            url = url,
                            canGoBack = view?.canGoBack() == true,
                            canGoForward = view?.canGoForward() == true
                        )
                        callbacks.onVisualContentChanged(tabId)
                    }
                }
            }

            if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_VIEW_RENDERER_CLIENT_BASIC_USAGE)) {
                WebViewCompat.setWebViewRenderProcessClient(
                    this,
                    object : WebViewRenderProcessClient() {
                        override fun onRenderProcessUnresponsive(
                            view: WebView,
                            renderer: WebViewRenderProcess?
                        ) {
                            renderer?.terminate()
                        }

                        override fun onRenderProcessResponsive(
                            view: WebView,
                            renderer: WebViewRenderProcess?
                        ) = Unit
                    }
                )
            }
        }
    }

    private fun classifyResource(url: String, headers: Map<String, String>?): WebResourceCategory {
        val accept = headers?.entries
            ?.firstOrNull { it.key.equals("Accept", ignoreCase = true) }
            ?.value?.lowercase()
            .orEmpty()
        val pathLower = url.substringBefore('?').lowercase()
        return when {
            pathLower.endsWith(".js") || accept.contains("javascript") -> WebResourceCategory.SCRIPT
            pathLower.endsWith(".css") || accept.contains("text/css") -> WebResourceCategory.STYLESHEET
            pathLower.endsWith(".png") || pathLower.endsWith(".jpg") || pathLower.endsWith(".jpeg") ||
                pathLower.endsWith(".webp") || pathLower.endsWith(".gif") || accept.startsWith("image/") ->
                WebResourceCategory.IMAGE
            accept.contains("application/json") -> WebResourceCategory.XHR_FETCH
            else -> WebResourceCategory.OTHER
        }
    }

    private fun buildCosmeticInjectScript(css: String): String {
        val escaped = css
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", " ")
        return """
            (function(){
              try{
                var id='__orbit_cosmetic';
                var el=document.getElementById(id);
                if(!el){
                  el=document.createElement('style');
                  el.id=id;
                  (document.head||document.documentElement).appendChild(el);
                }
                el.textContent='$escaped';
              }catch(e){}
            })();
        """.trimIndent()
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun applySettings(
        webView: WebView,
        isIncognito: Boolean,
        isDesktopMode: Boolean,
        preferences: BrowserPreferences,
        sandboxProfileName: String? = null
    ) {
        val settings = webView.settings
        settings.javaScriptEnabled = preferences.javaScriptEnabled
        settings.domStorageEnabled = !isIncognito
        settings.databaseEnabled = !isIncognito
        settings.allowFileAccess = true
        @Suppress("DEPRECATION")
        settings.allowFileAccessFromFileURLs = false
        @Suppress("DEPRECATION")
        settings.allowUniversalAccessFromFileURLs = false
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        settings.cacheMode = if (isIncognito) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
        settings.userAgentString = if (isDesktopMode) DESKTOP_USER_AGENT else null

        runCatching {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
                WebSettingsCompat.setSafeBrowsingEnabled(settings, true)
            }
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)
            ) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, preferences.forceDarkWeb)
            } else if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                @Suppress("DEPRECATION")
                WebSettingsCompat.setForceDark(
                    settings,
                    if (preferences.forceDarkWeb) {
                        WebSettingsCompat.FORCE_DARK_ON
                    } else {
                        WebSettingsCompat.FORCE_DARK_OFF
                    }
                )
            }
        }

        runCatching {
            val cookieManager = if (isIncognito) {
                CookieManager.getInstance()
            } else {
                SandboxProfileEngine.resolveCookieManager(webView, sandboxProfileName)
            }
            cookieManager?.apply {
                setAcceptCookie(!isIncognito)
                setAcceptThirdPartyCookies(webView, false)
            }
        }
    }
}
