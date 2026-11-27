package com.example.core.webengine

/**
 * Abstraction layer decoupling Orbit's UI, tabs, and content blocker from a specific
 * browser rendering backend. Currently backed by Android System WebView + AndroidX WebKit;
 * future migration to embedded Chromium ContentShell or GeckoView only requires a new
 * WebEngineSession adapter implementing this contract.
 */
enum class WebEngineBackend(val displayName: String) {
    ANDROID_SYSTEM_WEBVIEW("Android System WebView (Chromium)"),
    CHROMIUM_EMBEDDED("Embedded Chromium / ContentShell")
}

enum class WebResourceCategory {
    MAIN_FRAME,
    SCRIPT,
    IMAGE,
    STYLESHEET,
    XHR_FETCH,
    OTHER
}

data class BlockDecision(
    val shouldBlock: Boolean,
    val matchedRule: String? = null,
    val listSource: String? = null
)

interface ContentBlockerEngine {
    fun evaluateRequest(
        requestUrl: String,
        documentHost: String?,
        category: WebResourceCategory
    ): BlockDecision

    fun cosmeticCssForHost(host: String?): String?
}

interface WebEngineSession {
    val backend: WebEngineBackend
    val currentUrl: String?
    val currentTitle: String?

    fun loadUrl(url: String)
    fun reload()
    fun stopLoading()
    fun goBack()
    fun goForward()
    fun findAllAsync(query: String)
    fun findNext(forward: Boolean)
    fun clearMatches()
    fun evaluateScript(script: String, onResult: ((String?) -> Unit)? = null)
    fun clearHttpCache()
    fun destroySession()
}
