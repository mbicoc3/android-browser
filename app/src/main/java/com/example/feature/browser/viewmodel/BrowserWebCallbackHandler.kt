package com.example.feature.browser.viewmodel

import android.graphics.Bitmap
import android.webkit.URLUtil
import com.example.core.common.UrlUtils
import com.example.feature.browser.model.BrowserTabModel
import com.example.feature.browser.model.BrowserUiState
import com.example.feature.browser.model.DirectImageInfo
import com.example.feature.browser.repository.BrowserRepository
import com.example.feature.browser.webview.WebViewCallbacks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns WebView callback events and syncs tab/progress/history/favicon state into [BrowserUiState].
 */
internal class BrowserWebCallbackHandler(
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<BrowserUiState>,
    private val repository: BrowserRepository,
    private val persistTabById: (String) -> Unit
) : WebViewCallbacks {

    private var lastRecordedHistoryUrl: String? = null

    private fun updateTab(tabId: String, transform: (BrowserTabModel) -> BrowserTabModel) {
        uiState.update { state ->
            state.copy(tabs = state.tabs.map { tab -> if (tab.id == tabId) transform(tab) else tab })
        }
    }

    override fun onPageStarted(tabId: String, url: String) {
        val isRealWebPage = !UrlUtils.isStartPage(url)
        uiState.update { state ->
            state.copy(
                omniboxText = if (state.activeTabId == tabId && !state.isOmniboxFocused) {
                    if (isRealWebPage) url else ""
                } else state.omniboxText,
                tabs = state.tabs.map { tab ->
                    if (tab.id == tabId) {
                        val sameRestoredPage = isRealWebPage && tab.url == url
                        tab.copy(
                            url = url,
                            isLoading = true,
                            blockedTrackersCount = 0,
                            directImage = null,
                            backToWebUrl = if (isRealWebPage) null else tab.backToWebUrl,
                            backToWebTitle = if (isRealWebPage) null else tab.backToWebTitle,
                            backToWebCanGoBack = if (isRealWebPage) false else tab.backToWebCanGoBack,
                            backToWebCanGoForward = if (isRealWebPage) false else tab.backToWebCanGoForward,
                            forwardToWebUrl = if (isRealWebPage) null else tab.forwardToWebUrl,
                            forwardToWebTitle = if (isRealWebPage) null else tab.forwardToWebTitle,
                            forwardToWebCanGoForward = if (isRealWebPage) false else tab.forwardToWebCanGoForward,
                            forwardToStartPage = if (sameRestoredPage) tab.forwardToStartPage else false
                        )
                    } else tab
                }
            )
        }
    }

    override fun onPageFinished(
        tabId: String,
        url: String,
        title: String,
        canGoBack: Boolean,
        canGoForward: Boolean
    ) {
        val matchedTab = uiState.value.tabs.firstOrNull { it.id == tabId }
        uiState.update { state ->
            state.copy(
                omniboxText = if (state.activeTabId == tabId && !state.isOmniboxFocused) {
                    if (UrlUtils.isStartPage(url)) "" else url
                } else state.omniboxText,
                tabs = state.tabs.map { tab ->
                    if (tab.id == tabId) {
                        val keepForwardToStart = tab.forwardToStartPage && tab.url == url
                        tab.copy(
                            url = url,
                            title = title,
                            isLoading = false,
                            progress = 100,
                            canGoBack = canGoBack,
                            canGoForward = canGoForward || keepForwardToStart
                        )
                    } else tab
                }
            )
        }
        persistTabById(tabId)
        if (matchedTab != null && url != lastRecordedHistoryUrl && !UrlUtils.isStartPage(url)) {
            lastRecordedHistoryUrl = url
            scope.launch {
                repository.recordHistoryIfAllowed(title, url, matchedTab.isIncognito)
            }
        }
    }

    override fun onProgressChanged(tabId: String, progress: Int) =
        updateTab(tabId) { it.copy(progress = progress, isLoading = progress in 1..99) }

    override fun onTitleReceived(tabId: String, title: String) {
        updateTab(tabId) { it.copy(title = title) }
        persistTabById(tabId)
    }

    override fun onTrackerBlocked(tabId: String) =
        updateTab(tabId) { it.copy(blockedTrackersCount = it.blockedTrackersCount + 1) }

    override fun onDirectImageDetected(tabId: String, imageInfo: DirectImageInfo?) =
        updateTab(tabId) { it.copy(directImage = imageInfo) }

    override fun onDownloadRequested(url: String, contentDisposition: String?, mimeType: String?) {
        val guessedName = URLUtil.guessFileName(url, contentDisposition, mimeType)
        scope.launch {
            repository.enqueueDownload(url, guessedName)
            uiState.update { it.copy(statusBanner = "Download queued: $guessedName") }
        }
    }

    override fun onFindMatchesUpdated(tabId: String, activeMatchOrdinal: Int, numberOfMatches: Int) {
        if (tabId == uiState.value.activeTabId) {
            uiState.update {
                it.copy(
                    findMatchIndex = if (numberOfMatches > 0) activeMatchOrdinal + 1 else 0,
                    findTotalMatches = numberOfMatches
                )
            }
        }
    }

    override fun onPageFaviconBitmap(tabId: String, pageUrl: String, bitmap: Bitmap) {
        if (UrlUtils.isStartPage(pageUrl)) return
        scope.launch {
            repository.onLiveFaviconBitmap(pageUrl, bitmap)
        }
    }

    override fun onPageFaviconUrls(tabId: String, pageUrl: String, iconUrls: List<String>) {
        if (UrlUtils.isStartPage(pageUrl) || iconUrls.isEmpty()) return
        scope.launch {
            repository.onLiveFaviconUrls(pageUrl, iconUrls)
        }
    }

    override fun onUrlHistoryUpdated(
        tabId: String,
        url: String,
        canGoBack: Boolean,
        canGoForward: Boolean
    ) {
        if (UrlUtils.isStartPage(url)) return
        val matchedTab = uiState.value.tabs.firstOrNull { it.id == tabId } ?: return
        val urlChanged = matchedTab.url != url
        uiState.update { state ->
            state.copy(
                omniboxText = if (state.activeTabId == tabId && !state.isOmniboxFocused) {
                    url
                } else state.omniboxText,
                tabs = state.tabs.map { tab ->
                    if (tab.id == tabId) {
                        tab.copy(
                            url = url,
                            canGoBack = canGoBack,
                            canGoForward = canGoForward || (tab.forwardToStartPage && tab.url == url)
                        )
                    } else tab
                }
            )
        }
        if (urlChanged) {
            persistTabById(tabId)
        }
    }
}
