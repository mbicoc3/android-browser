package com.example.core.di

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.core.database.OrbitDatabase
import com.example.core.datastore.BrowserPreferencesDataStore
import com.example.core.network.FaviconExtractor
import com.example.core.network.NetworkClientFactory
import com.example.core.security.KeystoreCryptoManager
import com.example.core.worker.MaintenanceWorker
import com.example.feature.bookmarks.repository.BookmarkRepository
import com.example.feature.bookmarks.viewmodel.BookmarksViewModel
import com.example.feature.browser.repository.BrowserRepository
import com.example.feature.browser.viewmodel.BrowserViewModel
import com.example.feature.browser.webview.OrbitWebViewPool
import com.example.feature.collections.repository.ImageCollectionRepository
import com.example.feature.collections.viewmodel.ImageCollectionsViewModel
import com.example.feature.downloads.repository.DownloadRepository
import com.example.feature.downloads.viewmodel.DownloadsViewModel
import com.example.feature.history.repository.HistoryRepository
import com.example.feature.history.viewmodel.HistoryViewModel
import com.example.feature.offline.repository.OfflinePagesRepository
import com.example.feature.offline.viewmodel.OfflinePagesViewModel
import com.example.feature.passwords.autofill.BrowserPasswordCoordinator
import com.example.feature.passwords.crypto.VaultBiometricKeystoreHelper
import com.example.feature.passwords.provider.CredentialCoordinator
import com.example.feature.passwords.provider.ProtonPassIntegration
import com.example.feature.passwords.repository.LocalVaultRepository
import com.example.feature.passwords.viewmodel.PasswordsViewModel
import com.example.feature.sandbox.repository.SandboxRepository
import com.example.feature.sandbox.viewmodel.SandboxesViewModel
import com.example.feature.settings.viewmodel.SettingsViewModel
import com.example.feature.shield.engine.OrbitFilterEngine
import com.example.feature.shield.repository.ShieldRepository
import com.example.feature.shield.viewmodel.ShieldViewModel
import com.example.feature.sitedata.engine.WebViewSiteDataExecutor
import com.example.feature.sitedata.repository.SiteDataRepository
import com.example.feature.sitedata.viewmodel.SiteDataViewModel
import com.example.feature.vault.repository.VaultRepository
import com.example.feature.vault.viewmodel.VaultViewModel
import java.util.concurrent.TimeUnit

/**
 * Lightweight manual dependency container providing singletons and ViewModel factories
 * with zero reflection or code-gen overhead.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val database: OrbitDatabase by lazy { OrbitDatabase.getInstance(appContext) }
    val preferencesDataStore: BrowserPreferencesDataStore by lazy {
        BrowserPreferencesDataStore(appContext)
    }
    val cryptoManager: KeystoreCryptoManager by lazy { KeystoreCryptoManager() }
    val workManager: WorkManager by lazy { WorkManager.getInstance(appContext) }

    private val okHttpClient by lazy { NetworkClientFactory.createOkHttpClient() }
    private val moshi by lazy { NetworkClientFactory.createMoshi() }
    private val suggestionApi by lazy {
        NetworkClientFactory.createSearchSuggestionApi(okHttpClient, moshi)
    }
    private val filterListApi by lazy {
        NetworkClientFactory.createFilterListApi(okHttpClient)
    }

    val filterEngine: OrbitFilterEngine by lazy { OrbitFilterEngine() }

    val shieldRepository: ShieldRepository by lazy {
        ShieldRepository(
            appContext = appContext,
            filterListDao = database.filterListDao(),
            filterRuleDao = database.filterRuleDao(),
            filterListApi = filterListApi,
            filterEngine = filterEngine,
            preferencesDataStore = preferencesDataStore,
            workManager = workManager
        )
    }

    val bookmarkRepository: BookmarkRepository by lazy {
        BookmarkRepository(database.bookmarkDao())
    }
    val imageCollectionRepository: ImageCollectionRepository by lazy {
        ImageCollectionRepository(database.imageCollectionDao())
    }
    val historyRepository: HistoryRepository by lazy {
        HistoryRepository(database.historyDao())
    }
    val offlinePagesRepository: OfflinePagesRepository by lazy {
        OfflinePagesRepository(appContext, database.offlinePageDao())
    }
    val downloadRepository: DownloadRepository by lazy {
        DownloadRepository(
            appContext = appContext,
            downloadDao = database.downloadDao(),
            workManager = workManager,
            preferencesDataStore = preferencesDataStore
        )
    }
    val vaultRepository: VaultRepository by lazy {
        VaultRepository(database.vaultNoteDao(), cryptoManager)
    }
    val vaultBiometricHelper: VaultBiometricKeystoreHelper by lazy {
        VaultBiometricKeystoreHelper()
    }
    val localVaultRepository: LocalVaultRepository by lazy {
        LocalVaultRepository(
            appContext = appContext,
            dao = database.passwordVaultDao(),
            biometricHelper = vaultBiometricHelper
        )
    }
    val protonPassIntegration: ProtonPassIntegration by lazy {
        ProtonPassIntegration(appContext)
    }
    val credentialCoordinator: CredentialCoordinator by lazy {
        CredentialCoordinator(
            localVaultRepository = localVaultRepository,
            protonPassIntegration = protonPassIntegration
        )
    }
    val browserPasswordCoordinator: BrowserPasswordCoordinator by lazy {
        BrowserPasswordCoordinator(
            localVaultRepository = localVaultRepository,
            coordinator = credentialCoordinator
        )
    }
    val siteDataExecutor: WebViewSiteDataExecutor by lazy {
        WebViewSiteDataExecutor()
    }
    val siteDataRepository: SiteDataRepository by lazy {
        SiteDataRepository(
            appContext = appContext,
            siteRecordDao = database.siteRecordDao(),
            historyDao = database.historyDao(),
            browserTabDao = database.browserTabDao(),
            executor = siteDataExecutor
        )
    }
    val sandboxRepository: SandboxRepository by lazy {
        SandboxRepository(
            appContext = appContext,
            sandboxDao = database.sandboxDao(),
            browserTabDao = database.browserTabDao()
        )
    }
    val faviconExtractor: FaviconExtractor by lazy {
        FaviconExtractor(okHttpClient)
    }

    val browserRepository: BrowserRepository by lazy {
        BrowserRepository(
            appContext = appContext,
            tabDao = database.browserTabDao(),
            speedDialDao = database.speedDialDao(),
            siteRecordDao = database.siteRecordDao(),
            bookmarkRepository = bookmarkRepository,
            historyRepository = historyRepository,
            downloadRepository = downloadRepository,
            preferencesDataStore = preferencesDataStore,
            suggestionApi = suggestionApi,
            faviconExtractor = faviconExtractor
        )
    }

    fun schedulePeriodicMaintenance() {
        val request = PeriodicWorkRequestBuilder<MaintenanceWorker>(24, TimeUnit.HOURS)
            .build()
        workManager.enqueueUniquePeriodicWork(
            MaintenanceWorker.UNIQUE_PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun browserViewModelFactory(): ViewModelProvider.Factory = factory {
        BrowserViewModel(browserRepository, sandboxRepository)
    }

    fun sandboxesViewModelFactory(): ViewModelProvider.Factory = factory {
        SandboxesViewModel(sandboxRepository)
    }

    fun bookmarksViewModelFactory(): ViewModelProvider.Factory = factory {
        BookmarksViewModel(bookmarkRepository)
    }

    fun imageCollectionsViewModelFactory(): ViewModelProvider.Factory = factory {
        ImageCollectionsViewModel(imageCollectionRepository)
    }

    fun historyViewModelFactory(): ViewModelProvider.Factory = factory {
        HistoryViewModel(historyRepository)
    }

    fun offlinePagesViewModelFactory(): ViewModelProvider.Factory = factory {
        OfflinePagesViewModel(offlinePagesRepository)
    }

    fun downloadsViewModelFactory(): ViewModelProvider.Factory = factory {
        DownloadsViewModel(downloadRepository)
    }

    fun vaultViewModelFactory(): ViewModelProvider.Factory = factory {
        VaultViewModel(vaultRepository)
    }

    fun passwordsViewModelFactory(): ViewModelProvider.Factory = factory {
        PasswordsViewModel(
            appContext = appContext,
            localVaultRepository = localVaultRepository,
            protonPassIntegration = protonPassIntegration,
            okHttpClient = okHttpClient
        )
    }

    fun settingsViewModelFactory(): ViewModelProvider.Factory = factory {
        SettingsViewModel(appContext, preferencesDataStore, workManager)
    }

    fun shieldViewModelFactory(): ViewModelProvider.Factory = factory {
        ShieldViewModel(shieldRepository)
    }

    fun siteDataViewModelFactory(
        webViewPool: OrbitWebViewPool,
        initialOrigin: String?
    ): ViewModelProvider.Factory = factory {
        SiteDataViewModel(
            repository = siteDataRepository,
            webViewPool = webViewPool,
            initialOrigin = initialOrigin
        )
    }

    private inline fun <reified VM : ViewModel> factory(
        crossinline creator: () -> VM
    ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = creator() as T
    }
}
