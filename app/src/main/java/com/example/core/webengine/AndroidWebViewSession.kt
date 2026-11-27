package com.example.core.webengine

import android.webkit.WebView

/**
 * Android System WebView implementation of [WebEngineSession].
 * Isolates direct android.webkit.WebView invocations behind the engine contract.
 */
class AndroidWebViewSession(
    val webView: WebView
) : WebEngineSession {

    override val backend: WebEngineBackend = WebEngineBackend.ANDROID_SYSTEM_WEBVIEW

    override val currentUrl: String?
        get() = webView.url

    override val currentTitle: String?
        get() = webView.title

    override fun loadUrl(url: String) {
        webView.loadUrl(url)
    }

    override fun reload() {
        webView.reload()
    }

    override fun stopLoading() {
        webView.stopLoading()
    }

    override fun goBack() {
        webView.goBack()
    }

    override fun goForward() {
        webView.goForward()
    }

    override fun findAllAsync(query: String) {
        webView.findAllAsync(query)
    }

    override fun findNext(forward: Boolean) {
        webView.findNext(forward)
    }

    override fun clearMatches() {
        webView.clearMatches()
    }

    override fun evaluateScript(script: String, onResult: ((String?) -> Unit)?) {
        webView.evaluateJavascript(script) { value -> onResult?.invoke(value) }
    }

    override fun clearHttpCache() {
        webView.clearCache(true)
    }

    override fun destroySession() {
        webView.stopLoading()
        webView.clearHistory()
        webView.destroy()
    }
}
