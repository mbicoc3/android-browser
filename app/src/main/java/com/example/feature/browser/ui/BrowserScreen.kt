package com.example.feature.browser.ui

import android.net.Uri
import android.view.ViewGroup
import android.webkit.URLUtil
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.common.OrbitShareHelper
import com.example.core.common.UrlUtils
import com.example.core.database.entity.OfflinePageEntity
import com.example.core.navigation.BrowserRoutes
import com.example.core.registry.BrowserFeatureModule
import com.example.core.registry.BrowserToolRegistry
import com.example.feature.browser.model.WebCommand
import com.example.feature.browser.model.WebContextMenuTarget
import com.example.feature.browser.viewmodel.BrowserViewModel
import com.example.feature.browser.webview.OrbitPullRefreshWebView
import com.example.feature.browser.webview.OrbitWebPullRefreshOverlay
import com.example.feature.browser.webview.OrbitWebViewPool
import com.example.feature.browser.webview.PullRefreshCallbacks
import com.example.feature.downloads.model.PendingDownloadRequest
import com.example.feature.passwords.autofill.BrowserPasswordCoordinator
import com.example.feature.passwords.crypto.VaultBiometricKeystoreHelper
import com.example.feature.passwords.provider.ProtonPassIntegration
import com.example.feature.passwords.repository.LocalVaultRepository
import kotlinx.coroutines.launch

@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    webViewPool: OrbitWebViewPool,
    passwordCoordinator: BrowserPasswordCoordinator,
    localVaultRepository: LocalVaultRepository,
    biometricHelper: VaultBiometricKeystoreHelper,
    protonPassIntegration: ProtonPassIntegration,
    onToggleBlockTrackers: (Boolean) -> Unit,
    onToggleCosmeticFiltering: (Boolean) -> Unit,
    onSaveBookmark: (title: String, url: String) -> Unit,
    onSaveOfflinePage: suspend (webView: WebView, title: String, url: String) -> Result<OfflinePageEntity>,
    onDownloadFolderPicked: (Uri) -> Unit,
    onConfirmBeforeDownloadChanged: (Boolean) -> Unit,
    onEnqueueDownload: (
        url: String,
        fileName: String,
        mimeType: String?,
        chunkCount: Int,
        folderUri: String,
        folderLabel: String,
        wifiOnly: Boolean
    ) -> Unit,
    onNavigateToRoute: (String) -> Unit,
    onOpenTabs: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activeTab = state.activeTab
    val isStartPage = UrlUtils.isStartPage(activeTab?.url)
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val omniboxFocusRequester = remember { FocusRequester() }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var showToolsSheet by remember { mutableStateOf(false) }
    var showSandboxPicker by remember { mutableStateOf(false) }
    var showCreateSandboxDialog by remember { mutableStateOf(false) }
    var isQuickPanelOpen by remember { mutableStateOf(false) }
    var contextMenuTarget by remember { mutableStateOf<WebContextMenuTarget?>(null) }
    var pendingDownload by remember { mutableStateOf<PendingDownloadRequest?>(null) }
    var pullRefreshFraction by remember { mutableFloatStateOf(0f) }
    var isPullRefreshing by remember { mutableStateOf(false) }

    val latestIsStartPage = rememberUpdatedState(isStartPage)
    val latestActiveTabId = rememberUpdatedState(activeTab?.id)
    val pullRefreshCallbacks = remember {
        object : PullRefreshCallbacks {
            override fun onPullProgress(fraction: Float) {
                pullRefreshFraction = if (latestIsStartPage.value) 0f else fraction
            }

            override fun onPullTriggered() {
                pullRefreshFraction = 0f
                val tabId = latestActiveTabId.value
                if (!latestIsStartPage.value && tabId != null) {
                    isPullRefreshing = true
                    webViewPool.getIfPresent(tabId)?.reload()
                }
            }

            override fun onPullCancelled() {
                pullRefreshFraction = 0f
            }
        }
    }

    val handleDownloadRequest: (String, String?, String?) -> Unit = { url, contentDisposition, mimeType ->
        val guessed = URLUtil.guessFileName(url, contentDisposition, mimeType)
        if (state.preferences.confirmBeforeDownload) {
            pendingDownload = PendingDownloadRequest(
                url = url,
                suggestedFileName = guessed,
                mimeType = mimeType
            )
        } else {
            val prefs = state.preferences
            onEnqueueDownload(
                url,
                guessed,
                mimeType,
                prefs.downloadChunkCount,
                prefs.downloadFolderUri,
                prefs.downloadFolderLabel,
                prefs.downloadWifiOnly
            )
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Download started: $guessed (${prefs.downloadChunkCount}x)")
            }
        }
    }

    val multiPurposeCallbacks = rememberOmniboxMultiPurposeCallbacks(
        context = context,
        activeTab = activeTab,
        isStartPage = isStartPage,
        focusManager = focusManager,
        snackbarHostState = snackbarHostState,
        coroutineScope = coroutineScope,
        viewModel = viewModel,
        onCloseQuickPanel = { isQuickPanelOpen = false },
        onRequestDownload = handleDownloadRequest,
        onNavigateToRoute = onNavigateToRoute,
        onOpenTabs = onOpenTabs
    )

    val pageMenuCallbacks = rememberBrowserPageMenuCallbacks(
        context = context,
        activeTab = activeTab,
        isStartPage = isStartPage,
        lastShareComponent = state.preferences.lastShareComponent,
        webViewPool = webViewPool,
        snackbarHostState = snackbarHostState,
        coroutineScope = coroutineScope,
        viewModel = viewModel,
        onSaveOfflinePage = onSaveOfflinePage,
        onNavigateToRoute = onNavigateToRoute,
        onOpenSandboxPicker = { showSandboxPicker = true }
    )

    LaunchedEffect(state.statusBanner) {
        val message = state.statusBanner
        if (!message.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message)
            viewModel.dismissBanner()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.webCommands.collect { command ->
            when (command) {
                is WebCommand.LoadUrl -> {
                    webViewPool.getSessionIfPresent(command.tabId)?.loadUrl(command.url)
                }
                is WebCommand.Reload -> webViewPool.getSessionIfPresent(command.tabId)?.reload()
                is WebCommand.StopLoading -> webViewPool.getSessionIfPresent(command.tabId)?.stopLoading()
                is WebCommand.GoBack -> webViewPool.getSessionIfPresent(command.tabId)?.goBack()
                is WebCommand.GoForward -> webViewPool.getSessionIfPresent(command.tabId)?.goForward()
                is WebCommand.FindAll -> webViewPool.getSessionIfPresent(command.tabId)?.findAllAsync(command.query)
                is WebCommand.FindNext -> webViewPool.getSessionIfPresent(command.tabId)?.findNext(command.forward)
                is WebCommand.ClearMatches -> webViewPool.getSessionIfPresent(command.tabId)?.clearMatches()
                is WebCommand.DestroyTab -> webViewPool.destroyTab(command.tabId)
            }
        }
    }

    LaunchedEffect(activeTab?.id, activeTab?.url, activeTab?.isLoading, state.preferences.tabPreviewEnabled) {
        if (activeTab != null && !isStartPage && !activeTab.isLoading && state.preferences.tabPreviewEnabled) {
            kotlinx.coroutines.delay(250)
            webViewPool.capturePreviewIfEnabled(activeTab.id, enabled = true)
        }
    }

    LaunchedEffect(isStartPage, activeTab?.id) {
        pullRefreshFraction = 0f
        isPullRefreshing = false
    }

    LaunchedEffect(isPullRefreshing, activeTab?.isLoading, isStartPage) {
        if (isStartPage) {
            pullRefreshFraction = 0f
            isPullRefreshing = false
        } else if (isPullRefreshing) {
            if (activeTab?.isLoading == true) {
                kotlinx.coroutines.delay(900)
                isPullRefreshing = false
            } else {
                kotlinx.coroutines.delay(450)
                if (activeTab?.isLoading != true) {
                    isPullRefreshing = false
                }
            }
        }
    }

    BackHandler(
        enabled = isQuickPanelOpen ||
            state.isOmniboxFocused ||
            state.findInPageVisible ||
            !isStartPage ||
            activeTab?.backToWebUrl != null
    ) {
        when {
            isQuickPanelOpen -> isQuickPanelOpen = false
            state.isOmniboxFocused -> focusManager.clearFocus()
            state.findInPageVisible -> viewModel.toggleFindInPage(false)
            else -> viewModel.goBack()
        }
    }

    val handleModuleAction: (BrowserFeatureModule) -> Unit = { module ->
        when {
            module.route == com.example.core.navigation.BrowserRoutes.SITE_DATA -> {
                val activeOrigin = UrlUtils.extractOrigin(activeTab?.url)
                onNavigateToRoute(com.example.core.navigation.BrowserRoutes.siteDataRoute(activeOrigin))
            }
            module.route != null -> onNavigateToRoute(module.route)
            module.id == BrowserToolRegistry.ACTION_FIND_IN_PAGE -> viewModel.toggleFindInPage(true)
            module.id == BrowserToolRegistry.ACTION_TOGGLE_DESKTOP -> viewModel.toggleDesktopModeForActiveTab()
            module.id == BrowserToolRegistry.ACTION_TOGGLE_INCOGNITO -> {
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

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            if (!isStartPage) {
                Column {
                    BrowserOmniboxBar(
                        activeTab = activeTab,
                        omniboxText = state.omniboxText,
                        isOmniboxFocused = state.isOmniboxFocused,
                        isQuickPanelOpen = isQuickPanelOpen,
                        isBookmarked = state.isCurrentUrlBookmarked,
                        lastShareComponent = state.preferences.lastShareComponent,
                        lastShareAppLabel = state.preferences.lastShareAppLabel,
                        activeSearchEngine = state.preferences.searchEngine,
                        suggestions = state.suggestions,
                        localMatches = state.localMatches,
                        focusRequester = omniboxFocusRequester,
                        onTextChange = viewModel::onOmniboxTextChanged,
                        onFocusChange = { focused ->
                            if (focused) isQuickPanelOpen = false
                            viewModel.onOmniboxFocusChanged(focused)
                        },
                        onToggleQuickPanel = { isQuickPanelOpen = !isQuickPanelOpen },
                        onSubmit = {
                            isQuickPanelOpen = false
                            focusManager.clearFocus()
                            viewModel.submitOmnibox()
                        },
                        onReloadOrStop = viewModel::reloadOrStop,
                        onOpenTabs = {
                            if (activeTab != null && !isStartPage) {
                                webViewPool.capturePreviewIfEnabled(
                                    tabId = activeTab.id,
                                    enabled = state.preferences.tabPreviewEnabled,
                                    force = true
                                )
                            }
                            onOpenTabs()
                        },
                        onSwitchTab = viewModel::switchTabByOffset,
                        onInspectSiteData = {
                            isQuickPanelOpen = false
                            val activeOrigin = UrlUtils.extractOrigin(activeTab?.url)
                            onNavigateToRoute(BrowserRoutes.siteDataRoute(activeOrigin))
                        },
                        multiPurposeCallbacks = multiPurposeCallbacks,
                        pageMenuCallbacks = pageMenuCallbacks
                    )
                    AnimatedVisibility(visible = state.findInPageVisible) {
                        FindInPageBar(
                            query = state.findInPageQuery,
                            matchIndex = state.findMatchIndex,
                            totalMatches = state.findTotalMatches,
                            onQueryChange = viewModel::updateFindQuery,
                            onPrevious = { viewModel.findNextMatch(forward = false) },
                            onNext = { viewModel.findNextMatch(forward = true) },
                            onClose = { viewModel.toggleFindInPage(false) }
                        )
                    }
                }
            }
        },
        bottomBar = {
            val isDark = com.example.core.ui.theme.isOrbitDarkTheme(state.preferences.themeMode)
            val isLightSolidStart = state.preferences.startBackgroundMode ==
                com.example.core.datastore.StartBackgroundMode.SOLID && !isDark
            BrowserBottomBar(
                canGoBack = if (isStartPage) activeTab?.backToWebUrl != null else true,
                canGoForward = if (isStartPage) {
                    activeTab?.forwardToWebUrl != null
                } else {
                    activeTab?.canGoForward == true || activeTab?.forwardToStartPage == true
                },
                tabsCount = state.tabs.size,
                isIncognito = activeTab?.isIncognito == true,
                isStartPage = isStartPage,
                isLightSolidStart = isLightSolidStart,
                onBack = { viewModel.goBack() },
                onForward = viewModel::goForward,
                onNewTab = { viewModel.openUrlInNewTab(UrlUtils.START_PAGE_URL) },
                onGoHome = {
                    if (!isStartPage) {
                        viewModel.goHome()
                    }
                },
                onOpenTabs = {
                    if (activeTab != null && !isStartPage) {
                        webViewPool.capturePreviewIfEnabled(
                            tabId = activeTab.id,
                            enabled = state.preferences.tabPreviewEnabled,
                            force = true
                        )
                    }
                    onOpenTabs()
                },
                onNewIncognitoTab = {
                    viewModel.openUrlInNewTab(UrlUtils.START_PAGE_URL, isIncognito = true)
                },
                onSwitchTab = viewModel::switchTabByOffset,
                onOpenTools = { showToolsSheet = true }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .let { mod ->
                    if (isStartPage) {
                        mod
                    } else {
                        mod.padding(
                            top = innerPadding.calculateTopPadding(),
                            bottom = innerPadding.calculateBottomPadding()
                        )
                    }
                }
        ) {
            if (activeTab != null && !isStartPage) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        FrameLayout(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    update = { container ->
                        val webView = webViewPool.getOrCreate(
                            context = context,
                            tab = activeTab,
                            preferences = state.preferences,
                            callbacks = viewModel,
                            onContextMenuRequested = { target ->
                                contextMenuTarget = target
                            },
                            onDownloadRequested = handleDownloadRequest
                        )
                        (webView as? OrbitPullRefreshWebView)?.let { pullWebView ->
                            pullWebView.pullRefreshEnabled = !isStartPage
                            pullWebView.pullRefreshCallbacks = pullRefreshCallbacks
                        }
                        if (webView.parent !== container) {
                            (webView.parent as? ViewGroup)?.removeView(webView)
                            container.removeAllViews()
                            container.addView(
                                webView,
                                FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            )
                        }
                        if (webView.url.isNullOrBlank() && !UrlUtils.isStartPage(activeTab.url)) {
                            webView.loadUrl(activeTab.url)
                        }
                    }
                )
                OrbitWebPullRefreshOverlay(
                    pullFraction = pullRefreshFraction,
                    isRefreshing = isPullRefreshing
                )
            } else {
                StartPageContent(
                    isIncognito = activeTab?.isIncognito == true,
                    themeMode = state.preferences.themeMode,
                    backgroundMode = state.preferences.startBackgroundMode,
                    wallpaperUri = state.preferences.startWallpaperUri,
                    searchEngine = state.preferences.searchEngine,
                    blockTrackers = state.preferences.blockTrackers,
                    omniboxText = state.omniboxText,
                    isOmniboxFocused = state.isOmniboxFocused,
                    suggestions = state.suggestions,
                    localMatches = state.localMatches,
                    speedDialItems = state.speedDialItems,
                    collectionNames = state.preferences.speedDialCollectionNames,
                    focusRequester = omniboxFocusRequester,
                    bottomBarPadding = innerPadding.calculateBottomPadding(),
                    onOmniboxTextChange = viewModel::onOmniboxTextChanged,
                    onOmniboxFocusChange = viewModel::onOmniboxFocusChanged,
                    onSubmitSearch = { input ->
                        focusManager.clearFocus()
                        viewModel.submitOmnibox(input)
                    },
                    onCycleSearchEngine = viewModel::cycleSearchEngine,
                    onShortcutClick = { url -> viewModel.loadUrlInActiveTab(url) },
                    onAddShortcut = viewModel::addSpeedDialShortcut,
                    onMoveShortcut = viewModel::moveSpeedDialToCollection,
                    onRefreshShortcutIcon = viewModel::refreshSpeedDialIcon,
                    onDeleteShortcut = viewModel::deleteSpeedDialShortcut,
                    onRenameCollection = viewModel::renameSpeedDialCollection,
                    onThemeModeSelected = viewModel::setThemeMode,
                    onBackgroundModeSelected = viewModel::setStartBackgroundMode,
                    onWallpaperUriSelected = viewModel::updateStartWallpaperUri,
                    onNavigateToRoute = onNavigateToRoute,
                    multiPurposeCallbacks = multiPurposeCallbacks
                )
            }
        }
    }

    BrowserOverlaysHost(
        context = context,
        activeTab = activeTab,
        isStartPage = isStartPage,
        preferences = state.preferences,
        viewModel = viewModel,
        webViewPool = webViewPool,
        snackbarHostState = snackbarHostState,
        coroutineScope = coroutineScope,
        showToolsSheet = showToolsSheet,
        onDismissToolsSheet = { showToolsSheet = false },
        sandboxes = state.sandboxes,
        showSandboxPicker = showSandboxPicker,
        onDismissSandboxPicker = { showSandboxPicker = false },
        showCreateSandboxDialog = showCreateSandboxDialog,
        onShowCreateSandboxDialog = { showCreateSandboxDialog = true },
        onDismissCreateSandboxDialog = { showCreateSandboxDialog = false },
        onOpenSandboxesManager = { onNavigateToRoute(BrowserRoutes.SANDBOXES) },
        contextMenuTarget = contextMenuTarget,
        onDismissContextMenu = { contextMenuTarget = null },
        pendingDownload = pendingDownload,
        onDismissPendingDownload = { pendingDownload = null },
        onToggleBlockTrackers = onToggleBlockTrackers,
        onToggleCosmeticFiltering = onToggleCosmeticFiltering,
        onSaveBookmark = onSaveBookmark,
        onDownloadFolderPicked = onDownloadFolderPicked,
        onConfirmBeforeDownloadChanged = onConfirmBeforeDownloadChanged,
        onRequestDownload = handleDownloadRequest,
        onEnqueueDownload = onEnqueueDownload,
        onModuleAction = handleModuleAction,
        passwordCoordinator = passwordCoordinator,
        localVaultRepository = localVaultRepository,
        biometricHelper = biometricHelper,
        protonPassIntegration = protonPassIntegration
    )
}
