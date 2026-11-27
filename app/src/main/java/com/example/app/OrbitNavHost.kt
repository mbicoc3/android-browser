package com.example.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.core.common.UrlUtils
import com.example.core.datastore.BrowserPreferences
import com.example.core.di.AppContainer
import com.example.core.navigation.BrowserRoutes
import com.example.core.registry.BrowserToolRegistry
import com.example.feature.bookmarks.ui.BookmarksScreen
import com.example.feature.bookmarks.viewmodel.BookmarksViewModel
import com.example.feature.browser.ui.BrowserScreen
import com.example.feature.browser.viewmodel.BrowserViewModel
import com.example.feature.browser.webview.OrbitWebViewPool
import com.example.feature.downloads.ui.DownloadsScreen
import com.example.feature.downloads.viewmodel.DownloadsViewModel
import com.example.feature.history.ui.HistoryScreen
import com.example.feature.history.viewmodel.HistoryViewModel
import com.example.feature.offline.ui.OfflinePagesScreen
import com.example.feature.offline.viewmodel.OfflinePagesViewModel
import com.example.feature.passwords.ui.PasswordsScreen
import com.example.feature.passwords.viewmodel.PasswordsViewModel
import com.example.feature.sandbox.ui.SandboxesScreen
import com.example.feature.sandbox.viewmodel.SandboxesViewModel
import com.example.feature.settings.ui.SettingsScreen
import com.example.feature.settings.viewmodel.SettingsViewModel
import com.example.feature.shield.ui.ShieldScreen
import com.example.feature.shield.viewmodel.ShieldViewModel
import com.example.feature.sitedata.ui.SiteDataScreen
import com.example.feature.sitedata.viewmodel.SiteDataViewModel
import com.example.feature.tabs.ui.TabsScreen
import com.example.feature.tools.ui.ToolsHubScreen
import com.example.feature.vault.ui.PrivacyVaultScreen
import com.example.feature.vault.viewmodel.VaultViewModel
import kotlinx.coroutines.launch

@Composable
fun OrbitNavHost(
    navController: NavHostController,
    container: AppContainer,
    browserViewModel: BrowserViewModel,
    tabsStateProvider: () -> Pair<List<com.example.feature.browser.model.BrowserTabModel>, String>,
    webViewPool: OrbitWebViewPool
) {
    val preferences by container.preferencesDataStore.preferencesFlow
        .collectAsStateWithLifecycle(initialValue = BrowserPreferences())
    val previewRevision by webViewPool.previewStore.revision.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    NavHost(
        navController = navController,
        startDestination = BrowserRoutes.BROWSER
    ) {
        composable(BrowserRoutes.BROWSER) {
            BrowserScreen(
                viewModel = browserViewModel,
                webViewPool = webViewPool,
                passwordCoordinator = container.browserPasswordCoordinator,
                localVaultRepository = container.localVaultRepository,
                biometricHelper = container.vaultBiometricHelper,
                protonPassIntegration = container.protonPassIntegration,
                onToggleBlockTrackers = { enabled ->
                    coroutineScope.launch {
                        container.preferencesDataStore.setBlockTrackers(enabled)
                    }
                },
                onToggleCosmeticFiltering = { enabled ->
                    coroutineScope.launch {
                        container.preferencesDataStore.setCosmeticFilteringEnabled(enabled)
                    }
                },
                onSaveBookmark = { title, url ->
                    coroutineScope.launch {
                        container.bookmarkRepository.addOrUpdate(title, url)
                    }
                },
                onSaveOfflinePage = { webView, title, url ->
                    container.offlinePagesRepository.saveCurrentWebPage(webView, title, url)
                },
                onDownloadFolderPicked = { uri ->
                    coroutineScope.launch {
                        container.downloadRepository.setDownloadFolder(uri)
                    }
                },
                onConfirmBeforeDownloadChanged = { enabled ->
                    coroutineScope.launch {
                        container.downloadRepository.setConfirmBeforeDownload(enabled)
                    }
                },
                onEnqueueDownload = { url, fileName, mimeType, chunkCount, folderUri, folderLabel, wifiOnly ->
                    coroutineScope.launch {
                        container.downloadRepository.enqueueDownload(
                            url = url,
                            suggestedFileName = fileName,
                            mimeType = mimeType,
                            chunkCountOverride = chunkCount,
                            targetFolderUriOverride = folderUri,
                            targetFolderLabelOverride = folderLabel,
                            wifiOnlyOverride = wifiOnly
                        )
                    }
                },
                onNavigateToRoute = { route -> navController.navigate(route) },
                onOpenTabs = { navController.navigate(BrowserRoutes.TABS) }
            )
        }

        composable(BrowserRoutes.TABS) {
            val (tabs, activeTabId) = tabsStateProvider()
            TabsScreen(
                tabs = tabs,
                activeTabId = activeTabId,
                tabViewMode = preferences.tabViewMode,
                tabPreviewEnabled = preferences.tabPreviewEnabled,
                themeMode = preferences.themeMode,
                startBackgroundMode = preferences.startBackgroundMode,
                startWallpaperUri = preferences.startWallpaperUri,
                previewProvider = { tabId ->
                    previewRevision.let { webViewPool.getPreviewBitmap(tabId) }
                },
                onChangeViewMode = { mode ->
                    coroutineScope.launch {
                        container.preferencesDataStore.setTabViewMode(mode)
                    }
                },
                onSelectTab = { tabId ->
                    browserViewModel.selectTab(tabId)
                    navController.popBackStack()
                },
                onCloseTab = browserViewModel::closeTab,
                onNewTab = { isIncognito ->
                    browserViewModel.openUrlInNewTab(UrlUtils.START_PAGE_URL, isIncognito)
                    navController.popBackStack()
                },
                onCloseAll = {
                    browserViewModel.closeAllTabs()
                    navController.popBackStack()
                },
                onOpenSandboxes = { navController.navigate(BrowserRoutes.SANDBOXES) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(BrowserRoutes.BOOKMARKS) {
            val vm: BookmarksViewModel = viewModel(factory = container.bookmarksViewModelFactory())
            BookmarksScreen(
                viewModel = vm,
                onOpenUrl = { url ->
                    browserViewModel.loadUrlInActiveTab(url)
                    navController.popBackStack(BrowserRoutes.BROWSER, inclusive = false)
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(BrowserRoutes.HISTORY) {
            val vm: HistoryViewModel = viewModel(factory = container.historyViewModelFactory())
            HistoryScreen(
                viewModel = vm,
                onOpenUrl = { url ->
                    browserViewModel.loadUrlInActiveTab(url)
                    navController.popBackStack(BrowserRoutes.BROWSER, inclusive = false)
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(BrowserRoutes.OFFLINE_PAGES) {
            val vm: OfflinePagesViewModel = viewModel(factory = container.offlinePagesViewModelFactory())
            OfflinePagesScreen(
                viewModel = vm,
                onOpenOfflineUrl = { fileUrl ->
                    browserViewModel.loadUrlInActiveTab(fileUrl)
                    navController.popBackStack(BrowserRoutes.BROWSER, inclusive = false)
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(BrowserRoutes.DOWNLOADS) {
            val vm: DownloadsViewModel = viewModel(factory = container.downloadsViewModelFactory())
            DownloadsScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() }
            )
        }

        composable(BrowserRoutes.PRIVACY_VAULT) {
            val vm: VaultViewModel = viewModel(factory = container.vaultViewModelFactory())
            PrivacyVaultScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() }
            )
        }

        composable(BrowserRoutes.PASSWORDS) {
            val vm: PasswordsViewModel = viewModel(factory = container.passwordsViewModelFactory())
            PasswordsScreen(
                viewModel = vm,
                biometricHelper = container.vaultBiometricHelper,
                onBack = { navController.popBackStack() }
            )
        }

        composable(BrowserRoutes.SHIELD) {
            val vm: ShieldViewModel = viewModel(factory = container.shieldViewModelFactory())
            ShieldScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = BrowserRoutes.SITE_DATA_PATTERN,
            arguments = listOf(
                navArgument("origin") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val originArg = backStackEntry.arguments?.getString("origin")
            val vm: SiteDataViewModel = viewModel(
                factory = container.siteDataViewModelFactory(
                    webViewPool = webViewPool,
                    initialOrigin = originArg
                )
            )
            SiteDataScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() }
            )
        }

        composable(BrowserRoutes.SANDBOXES) {
            val vm: SandboxesViewModel = viewModel(factory = container.sandboxesViewModelFactory())
            SandboxesScreen(
                viewModel = vm,
                nextSuggestedColorHex = container.sandboxRepository::nextSuggestedColorHex,
                onOpenSandboxTab = { sandbox ->
                    browserViewModel.openUrlInSandbox(sandbox.startUrl, sandbox)
                    navController.popBackStack(BrowserRoutes.BROWSER, inclusive = false)
                },
                onBeforeDeleteCloseTabs = { sandboxId ->
                    browserViewModel.closeTabsForSandbox(sandboxId)
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(BrowserRoutes.SETTINGS) {
            val vm: SettingsViewModel = viewModel(factory = container.settingsViewModelFactory())
            SettingsScreen(
                viewModel = vm,
                onOpenSiteData = { navController.navigate(BrowserRoutes.SITE_DATA) },
                onOpenShield = { navController.navigate(BrowserRoutes.SHIELD) },
                onOpenDownloads = { navController.navigate(BrowserRoutes.DOWNLOADS) },
                onOpenPasswords = { navController.navigate(BrowserRoutes.PASSWORDS) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(BrowserRoutes.TOOLS_HUB) {
            ToolsHubScreen(
                onOpenRoute = { route -> navController.navigate(route) },
                onTriggerInPageTool = { module ->
                    when (module.id) {
                        BrowserToolRegistry.ACTION_TOGGLE_INCOGNITO -> {
                            browserViewModel.openUrlInNewTab(UrlUtils.START_PAGE_URL, isIncognito = true)
                        }
                        BrowserToolRegistry.ACTION_FIND_IN_PAGE -> {
                            browserViewModel.toggleFindInPage(true)
                        }
                        BrowserToolRegistry.ACTION_TOGGLE_DESKTOP -> {
                            browserViewModel.toggleDesktopModeForActiveTab()
                        }
                    }
                    navController.popBackStack(BrowserRoutes.BROWSER, inclusive = false)
                },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
