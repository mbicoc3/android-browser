package com.example.feature.browser.ui

import android.content.Context
import android.webkit.WebView
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusManager
import com.example.R
import com.example.core.common.OrbitShareHelper
import com.example.core.common.UrlUtils
import com.example.core.database.entity.OfflinePageEntity
import com.example.core.navigation.BrowserRoutes
import com.example.feature.browser.model.BrowserTabModel
import com.example.feature.browser.model.OmniboxCommandAction
import com.example.feature.browser.viewmodel.BrowserViewModel
import com.example.feature.browser.webview.OrbitWebViewPool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun rememberOmniboxMultiPurposeCallbacks(
    context: Context,
    activeTab: BrowserTabModel?,
    isStartPage: Boolean,
    focusManager: FocusManager,
    snackbarHostState: SnackbarHostState,
    coroutineScope: CoroutineScope,
    viewModel: BrowserViewModel,
    onCloseQuickPanel: () -> Unit,
    onRequestDownload: (url: String, contentDisposition: String?, mimeType: String?) -> Unit,
    onNavigateToRoute: (String) -> Unit,
    onOpenTabs: () -> Unit
): OmniboxMultiPurposeCallbacks = remember(activeTab?.id, activeTab?.url, activeTab?.title, activeTab?.directImage, isStartPage) {
    OmniboxMultiPurposeCallbacks(
        onPasteAndGo = { clip ->
            onCloseQuickPanel()
            focusManager.clearFocus()
            viewModel.submitOmnibox(clip)
        },
        onCopyCurrentUrl = {
            val url = activeTab?.url.orEmpty()
            if (url.isNotBlank() && !isStartPage) {
                copyTextToClipboard(context, "URL", url)
                onCloseQuickPanel()
                focusManager.clearFocus()
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.msg_link_copied))
                }
            }
        },
        onDownloadCurrentUrl = {
            val url = activeTab?.directImage?.url ?: activeTab?.url.orEmpty()
            if (url.isNotBlank() && !isStartPage) {
                onCloseQuickPanel()
                focusManager.clearFocus()
                onRequestDownload(url, null, activeTab?.directImage?.mimeType)
            }
        },
        onShareCurrentUrl = {
            val url = activeTab?.url.orEmpty()
            if (url.isNotBlank() && !isStartPage) {
                onCloseQuickPanel()
                focusManager.clearFocus()
                OrbitShareHelper.shareWithChooser(context, activeTab?.title, url)
            }
        },
        onInspectSiteData = {
            onCloseQuickPanel()
            focusManager.clearFocus()
            val activeOrigin = UrlUtils.extractOrigin(activeTab?.url)
            onNavigateToRoute(BrowserRoutes.siteDataRoute(activeOrigin))
        },
        onFindInPage = { initialQuery ->
            onCloseQuickPanel()
            focusManager.clearFocus()
            viewModel.toggleFindInPage(true)
            if (initialQuery.isNotBlank()) {
                viewModel.updateFindQuery(initialQuery)
            }
        },
        onToggleDesktopMode = {
            onCloseQuickPanel()
            focusManager.clearFocus()
            viewModel.toggleDesktopModeForActiveTab()
        },
        onPinToHome = {
            val url = activeTab?.url.orEmpty()
            if (url.isNotBlank() && !isStartPage) {
                onCloseQuickPanel()
                focusManager.clearFocus()
                viewModel.addSpeedDialShortcut(activeTab?.title.orEmpty(), url)
            }
        },
        onOpenInPrivateTab = {
            val url = activeTab?.url.orEmpty()
            if (url.isNotBlank() && !isStartPage) {
                onCloseQuickPanel()
                focusManager.clearFocus()
                viewModel.openUrlInNewTab(url, isIncognito = true)
            }
        },
        onSearchWithEngine = { query, engine ->
            onCloseQuickPanel()
            focusManager.clearFocus()
            viewModel.submitOmnibox(query, engineOverride = engine)
        },
        onLocalMatchClick = { url ->
            onCloseQuickPanel()
            focusManager.clearFocus()
            viewModel.loadUrlInActiveTab(url)
        },
        onSuggestionClick = { suggestion ->
            onCloseQuickPanel()
            focusManager.clearFocus()
            viewModel.submitOmnibox(suggestion)
        },
        onFillOmniboxText = viewModel::onOmniboxTextChanged,
        onCopyText = { text ->
            copyTextToClipboard(context, "Result", text)
            coroutineScope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.omnibox_result_copied))
            }
        },
        onExecuteSlashCommand = { command ->
            onCloseQuickPanel()
            focusManager.clearFocus()
            viewModel.onOmniboxFocusChanged(false)
            when (command.action) {
                OmniboxCommandAction.OPEN_ROUTE -> {
                    val route = command.route
                    if (route == BrowserRoutes.SITE_DATA) {
                        onNavigateToRoute(BrowserRoutes.siteDataRoute(UrlUtils.extractOrigin(activeTab?.url)))
                    } else if (route != null) {
                        onNavigateToRoute(route)
                    }
                }
                OmniboxCommandAction.OPEN_TABS -> onOpenTabs()
                OmniboxCommandAction.NEW_INCOGNITO_TAB -> {
                    viewModel.openUrlInNewTab(UrlUtils.START_PAGE_URL, isIncognito = true)
                }
                OmniboxCommandAction.TOGGLE_DESKTOP_MODE -> viewModel.toggleDesktopModeForActiveTab()
                OmniboxCommandAction.FIND_IN_PAGE -> viewModel.toggleFindInPage(true)
            }
        }
    )
}

@Composable
internal fun rememberBrowserPageMenuCallbacks(
    context: Context,
    activeTab: BrowserTabModel?,
    isStartPage: Boolean,
    lastShareComponent: String,
    webViewPool: OrbitWebViewPool,
    snackbarHostState: SnackbarHostState,
    coroutineScope: CoroutineScope,
    viewModel: BrowserViewModel,
    onSaveOfflinePage: suspend (webView: WebView, title: String, url: String) -> Result<OfflinePageEntity>,
    onNavigateToRoute: (String) -> Unit,
    onOpenSandboxPicker: () -> Unit = {}
): BrowserPageMenuCallbacks = remember(
    activeTab?.id,
    activeTab?.url,
    activeTab?.title,
    activeTab?.sandboxId,
    isStartPage,
    lastShareComponent
) {
    BrowserPageMenuCallbacks(
        onToggleBookmark = viewModel::toggleCurrentBookmark,
        onShare = {
            val url = activeTab?.url.orEmpty()
            if (url.isNotBlank() && !isStartPage) {
                OrbitShareHelper.shareWithChooser(context, activeTab?.title, url)
            }
        },
        onQuickShare = {
            val url = activeTab?.url.orEmpty()
            if (url.isNotBlank() && !isStartPage) {
                OrbitShareHelper.quickShareToLastApp(
                    context = context,
                    title = activeTab?.title,
                    text = url,
                    lastComponentFlatten = lastShareComponent
                )
            }
        },
        onToggleDesktopMode = viewModel::toggleDesktopModeForActiveTab,
        onAddToSpeedDial = {
            val url = activeTab?.url.orEmpty()
            if (url.isNotBlank() && !isStartPage) {
                viewModel.addSpeedDialShortcut(activeTab?.title.orEmpty(), url)
            }
        },
        onFindInPage = { viewModel.toggleFindInPage(true) },
        onSaveOffline = {
            val tab = activeTab ?: return@BrowserPageMenuCallbacks
            val webView = webViewPool.getIfPresent(tab.id) ?: return@BrowserPageMenuCallbacks
            coroutineScope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.msg_saving_offline))
                onSaveOfflinePage(webView, tab.title, tab.url)
                    .onSuccess {
                        snackbarHostState.showSnackbar(context.getString(R.string.msg_saved_offline))
                    }
                    .onFailure {
                        snackbarHostState.showSnackbar(context.getString(R.string.msg_save_offline_failed))
                    }
            }
        },
        onOpenSiteData = {
            val activeOrigin = UrlUtils.extractOrigin(activeTab?.url)
            onNavigateToRoute(BrowserRoutes.siteDataRoute(activeOrigin))
        },
        onOpenSandboxPicker = onOpenSandboxPicker
    )
}
