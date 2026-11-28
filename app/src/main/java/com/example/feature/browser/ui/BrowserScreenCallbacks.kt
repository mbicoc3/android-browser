package com.example.feature.browser.ui

import android.content.Context
import android.webkit.WebView
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.core.common.OrbitShareHelper
import com.example.core.common.UrlUtils
import com.example.core.database.entity.OfflinePageEntity
import com.example.core.navigation.BrowserRoutes
import com.example.core.registry.BrowserFeatureModule
import com.example.core.registry.BrowserToolRegistry
import com.example.feature.browser.model.BrowserTabModel
import com.example.feature.browser.model.OmniboxCommandAction
import com.example.feature.browser.viewmodel.BrowserViewModel
import com.example.feature.browser.webview.OrbitWebViewPool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
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
    onCollectImage: (title: String, imageUrl: String, sourcePageUrl: String) -> Unit = { _, _, _ -> },
    onBeforeTabSwitch: () -> Unit = {},
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
        onCollectCurrentImageUrl = {
            val imgUrl = activeTab?.directImage?.url ?: activeTab?.url.orEmpty()
            if (imgUrl.isNotBlank() && !isStartPage) {
                onCloseQuickPanel()
                focusManager.clearFocus()
                onCollectImage(
                    activeTab?.title?.takeIf { it.isNotBlank() } ?: UrlUtils.extractHost(imgUrl),
                    imgUrl,
                    activeTab?.url.orEmpty()
                )
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.msg_collected_image))
                }
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
                onBeforeTabSwitch()
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
                    onBeforeTabSwitch()
                    val route = command.route
                    if (route == BrowserRoutes.SITE_DATA) {
                        onNavigateToRoute(BrowserRoutes.siteDataRoute(UrlUtils.extractOrigin(activeTab?.url)))
                    } else if (route != null) {
                        onNavigateToRoute(route)
                    }
                }
                OmniboxCommandAction.OPEN_TABS -> {
                    onBeforeTabSwitch()
                    onOpenTabs()
                }
                OmniboxCommandAction.NEW_INCOGNITO_TAB -> {
                    onBeforeTabSwitch()
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
    onCollectImage: (title: String, imageUrl: String, sourcePageUrl: String) -> Unit = { _, _, _ -> },
    onNavigateToRoute: (String) -> Unit,
    onOpenSandboxPicker: () -> Unit = {}
): BrowserPageMenuCallbacks = remember(
    activeTab?.id,
    activeTab?.url,
    activeTab?.title,
    activeTab?.directImage,
    activeTab?.sandboxId,
    isStartPage,
    lastShareComponent
) {
    BrowserPageMenuCallbacks(
        onToggleBookmark = viewModel::toggleCurrentBookmark,
        onCollectImage = {
            val imgUrl = activeTab?.directImage?.url ?: activeTab?.url.orEmpty()
            if (imgUrl.isNotBlank() && !isStartPage) {
                onCollectImage(
                    activeTab?.title?.takeIf { it.isNotBlank() } ?: UrlUtils.extractHost(imgUrl),
                    imgUrl,
                    activeTab?.url.orEmpty()
                )
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.msg_collected_image))
                }
            }
        },
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

@Composable
internal fun BrowserPreviewSyncEffects(
    activeTab: BrowserTabModel?,
    isStartPage: Boolean,
    tabPreviewEnabled: Boolean,
    webViewPool: OrbitWebViewPool,
    onCaptureNow: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                onCaptureNow()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            onCaptureNow()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(
        activeTab?.id,
        activeTab?.url,
        activeTab?.title,
        activeTab?.isLoading,
        activeTab?.progress,
        activeTab?.isDesktopMode,
        tabPreviewEnabled
    ) {
        val tab = activeTab ?: return@LaunchedEffect
        if (isStartPage) {
            webViewPool.previewStore.remove(tab.id)
        } else if (tabPreviewEnabled) {
            delay(220)
            webViewPool.capturePreviewIfEnabled(tab.id, enabled = true, force = true)
        }
    }
}

internal fun dispatchBrowserModuleAction(
    context: Context,
    module: BrowserFeatureModule,
    activeTab: BrowserTabModel?,
    viewModel: BrowserViewModel,
    onBeforeTabChange: () -> Unit,
    onNavigateToRoute: (String) -> Unit
) {
    when {
        module.route == BrowserRoutes.SITE_DATA -> {
            onBeforeTabChange()
            val activeOrigin = UrlUtils.extractOrigin(activeTab?.url)
            onNavigateToRoute(BrowserRoutes.siteDataRoute(activeOrigin))
        }
        module.route != null -> {
            onBeforeTabChange()
            onNavigateToRoute(module.route)
        }
        module.id == BrowserToolRegistry.ACTION_FIND_IN_PAGE -> viewModel.toggleFindInPage(true)
        module.id == BrowserToolRegistry.ACTION_TOGGLE_DESKTOP -> viewModel.toggleDesktopModeForActiveTab()
        module.id == BrowserToolRegistry.ACTION_TOGGLE_INCOGNITO -> {
            onBeforeTabChange()
            viewModel.openUrlInNewTab(UrlUtils.START_PAGE_URL, isIncognito = true)
        }
        module.id == BrowserToolRegistry.ACTION_SHARE_PAGE -> {
            val url = activeTab?.url.orEmpty()
            if (!UrlUtils.isStartPage(url)) {
                OrbitShareHelper.shareWithChooser(context, activeTab?.title, url)
            }
        }
    }
}
