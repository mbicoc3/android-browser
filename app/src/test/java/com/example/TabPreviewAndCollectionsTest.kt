package com.example

import android.app.Activity
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.common.UrlUtils
import com.example.core.database.OrbitDatabase
import com.example.core.datastore.AppThemeMode
import com.example.core.datastore.StartBackgroundMode
import com.example.core.navigation.BrowserRoutes
import com.example.core.registry.BrowserToolRegistry
import com.example.core.ui.theme.OrbitBrowserTheme
import com.example.feature.browser.model.BrowserTabModel
import com.example.feature.collections.repository.ImageCollectionRepository
import com.example.feature.collections.ui.ImageCollectionsScreen
import com.example.feature.collections.viewmodel.ImageCollectionsViewModel
import com.example.feature.tabs.preview.TabPreviewStore
import com.example.feature.tabs.ui.TabPreviewViewport
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TabPreviewAndCollectionsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun migration9To10_createsImageCollectionsTable_preservingExistingTables() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "orbit_migration_9_10_test.db"
        context.deleteDatabase(dbName)

        val dbPath = context.getDatabasePath(dbName)
        dbPath.parentFile?.mkdirs()
        val sqlite = SQLiteDatabase.openOrCreateDatabase(dbPath, null)
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `bookmarks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `url` TEXT NOT NULL, `folder` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)")
        sqlite.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_bookmarks_url` ON `bookmarks` (`url`)")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `url` TEXT NOT NULL, `visitedAt` INTEGER NOT NULL)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_history_visitedAt` ON `history` (`visitedAt`)")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `browser_tabs` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `url` TEXT NOT NULL, `isIncognito` INTEGER NOT NULL, `isDesktopMode` INTEGER NOT NULL, `isActive` INTEGER NOT NULL, `position` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `sandboxId` TEXT, PRIMARY KEY(`id`))")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `downloads` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `fileName` TEXT NOT NULL, `url` TEXT NOT NULL, `localPath` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `bytesDownloaded` INTEGER NOT NULL, `totalBytes` INTEGER NOT NULL, `speedBytesPerSec` INTEGER NOT NULL DEFAULT 0, `chunkCount` INTEGER NOT NULL DEFAULT 1, `supportsRanges` INTEGER NOT NULL DEFAULT 0, `targetFolderUri` TEXT NOT NULL DEFAULT '', `targetFolderLabel` TEXT NOT NULL DEFAULT 'Downloads (Default)', `stagingPath` TEXT NOT NULL DEFAULT '', `wifiOnly` INTEGER NOT NULL DEFAULT 0, `status` TEXT NOT NULL, `workRequestId` TEXT NOT NULL, `errorMessage` TEXT, `createdAt` INTEGER NOT NULL)")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `vault_notes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `label` TEXT NOT NULL, `encryptedPayload` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `speed_dial` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `url` TEXT NOT NULL, `shortBadge` TEXT NOT NULL, `accentColorHex` INTEGER NOT NULL, `position` INTEGER NOT NULL, `iconPath` TEXT NOT NULL DEFAULT '', `collectionIndex` INTEGER NOT NULL DEFAULT 0)")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `site_records` (`origin` TEXT NOT NULL, `host` TEXT NOT NULL, `title` TEXT NOT NULL, `lastVisitedAt` INTEGER NOT NULL, `visitCount` INTEGER NOT NULL, `cookieCount` INTEGER NOT NULL, `localStorageCount` INTEGER NOT NULL, `localStorageBytes` INTEGER NOT NULL, `sessionStorageCount` INTEGER NOT NULL, `sessionStorageBytes` INTEGER NOT NULL, `indexedDbCount` INTEGER NOT NULL, `cacheStorageCount` INTEGER NOT NULL, `serviceWorkerCount` INTEGER NOT NULL, `webStorageUsageBytes` INTEGER NOT NULL, `webStorageQuotaBytes` INTEGER NOT NULL, `lastInspectedAt` INTEGER NOT NULL, PRIMARY KEY(`origin`))")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_site_records_host` ON `site_records` (`host`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_site_records_lastVisitedAt` ON `site_records` (`lastVisitedAt`)")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `filter_lists` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `sourceUrl` TEXT NOT NULL, `category` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `ruleCount` INTEGER NOT NULL, `cosmeticRuleCount` INTEGER NOT NULL, `lastUpdatedAt` INTEGER NOT NULL, `isBuiltIn` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `filter_rules` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `rawRule` TEXT NOT NULL, `ruleType` TEXT NOT NULL, `domainFilter` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_filter_rules_enabled` ON `filter_rules` (`enabled`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_filter_rules_ruleType` ON `filter_rules` (`ruleType`)")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `offline_pages` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `originalUrl` TEXT NOT NULL, `filePath` TEXT NOT NULL, `fileSizeBytes` INTEGER NOT NULL, `savedAt` INTEGER NOT NULL)")
        sqlite.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_offline_pages_originalUrl` ON `offline_pages` (`originalUrl`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_offline_pages_savedAt` ON `offline_pages` (`savedAt`)")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `credentials` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `normalizedDomain` TEXT NOT NULL, `usernameHint` TEXT, `folderId` TEXT, `isFavorite` INTEGER NOT NULL, `encryptedPayload` BLOB NOT NULL, `encryptionVersion` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_credentials_normalizedDomain` ON `credentials` (`normalizedDomain`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_credentials_folderId` ON `credentials` (`folderId`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_credentials_updatedAt` ON `credentials` (`updatedAt`)")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `vault_metadata` (`id` TEXT NOT NULL, `encryptedDek` BLOB NOT NULL, `dekNonce` BLOB NOT NULL, `salt` BLOB NOT NULL, `argon2MemoryKb` INTEGER NOT NULL, `argon2Iterations` INTEGER NOT NULL, `argon2Parallelism` INTEGER NOT NULL, `verifierBlob` BLOB NOT NULL, `keystoreWrappedDek` BLOB, `keystoreDekNonce` BLOB, `biometricEnabled` INTEGER NOT NULL, `autoLockTimeoutMs` INTEGER NOT NULL, `lockOnBackground` INTEGER NOT NULL, `clipboardClearSeconds` INTEGER NOT NULL, `allowHttpAutofill` INTEGER NOT NULL, `neverSaveDomainsJson` TEXT NOT NULL, `encryptionVersion` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `credential_folders` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `password_history` (`id` TEXT NOT NULL, `credentialId` TEXT NOT NULL, `encryptedPassword` BLOB NOT NULL, `encryptionVersion` INTEGER NOT NULL, `changedAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`credentialId`) REFERENCES `credentials`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_password_history_credentialId` ON `password_history` (`credentialId`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_password_history_changedAt` ON `password_history` (`changedAt`)")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `backup_metadata` (`id` TEXT NOT NULL, `fileName` TEXT NOT NULL, `credentialCount` INTEGER NOT NULL, `backupVersion` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `sandboxes` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `profileName` TEXT NOT NULL, `pinnedDomain` TEXT NOT NULL DEFAULT '', `startUrl` TEXT NOT NULL DEFAULT '', `colorHex` INTEGER NOT NULL DEFAULT 4281909240, `iconKey` TEXT NOT NULL DEFAULT 'work', `keepLongTerm` INTEGER NOT NULL DEFAULT 1, `preferredCredentialId` TEXT, `createdAt` INTEGER NOT NULL, `lastUsedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        sqlite.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sandboxes_profileName` ON `sandboxes` (`profileName`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_sandboxes_lastUsedAt` ON `sandboxes` (`lastUsedAt`)")
        sqlite.execSQL("INSERT INTO `bookmarks` (`title`, `url`, `folder`, `createdAt`) VALUES ('Example', 'https://example.com', 'General', 1000)")
        sqlite.version = 9
        sqlite.close()

        val roomDb = Room.databaseBuilder(context, OrbitDatabase::class.java, dbName)
            .addMigrations(OrbitDatabase.MIGRATION_9_10)
            .allowMainThreadQueries()
            .build()
        try {
            val bookmarks = roomDb.bookmarkDao().observeAllBookmarks().first()
            assertEquals(1, bookmarks.size)
            assertEquals("https://example.com", bookmarks.first().url)

            val repo = ImageCollectionRepository(roomDb.imageCollectionDao())
            repo.collectImage(
                title = "Sunset",
                imageUrl = "https://images.example.com/sunset.jpg",
                sourcePageUrl = "https://example.com/gallery",
                collectionName = "Wallpapers"
            )
            repo.collectImage(
                title = "Sunset Updated",
                imageUrl = "https://images.example.com/sunset.jpg",
                sourcePageUrl = "https://example.com/gallery",
                collectionName = "Nature"
            )
            val saved = repo.observeAll().first()
            assertEquals(1, saved.size)
            assertEquals("Sunset Updated", saved.first().title)
            assertEquals("Nature", saved.first().collectionName)
        } finally {
            roomDb.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun browserToolRegistry_exposesImageCollectionsModule() {
        val collectionsModule = BrowserToolRegistry.getAllModules()
            .firstOrNull { it.id == "image_collections" }
        assertNotNull(collectionsModule)
        assertEquals(BrowserRoutes.IMAGE_COLLECTIONS, collectionsModule?.route)
    }

    @Test
    fun tabPreviewStore_updatesSameTabOnForceCapture_andInvalidatesOnUrlChange() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val store = TabPreviewStore()
        var reportedUrl = "https://example.com/page-1"
        var drawCalls = 0
        val webView = object : WebView(activity) {
            override fun isAttachedToWindow(): Boolean = true
            override fun getUrl(): String = reportedUrl
            override fun draw(canvas: Canvas) {
                drawCalls++
                canvas.drawColor(Color.BLUE)
            }
        }.apply {
            visibility = View.VISIBLE
        }
        activity.setContentView(webView)
        webView.measure(
            View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY)
        )
        webView.layout(0, 0, 720, 1280)
        webView.left = 0
        webView.top = 0
        webView.right = 720
        webView.bottom = 1280
        assertEquals(720, webView.width)
        assertEquals(1280, webView.height)

        store.captureFromWebView(tabId = "tab-1", webView = webView, force = true)
        val firstBitmap = store.getPreview("tab-1")
        assertNotNull(firstBitmap)
        assertEquals(1, drawCalls)
        val firstRevision = store.revision.value

        // Same tab, same URL, content updated -> forced refresh creates a new Bitmap revision
        store.captureFromWebView(tabId = "tab-1", webView = webView, force = true)
        val updatedBitmap = store.getPreview("tab-1")
        assertNotNull(updatedBitmap)
        assertEquals(2, drawCalls)
        assertNotSame(firstBitmap, updatedBitmap)
        assertTrue(store.revision.value > firstRevision)

        // Same tab, URL changes -> bypasses interval throttle even with force = false
        reportedUrl = "https://example.com/page-2"
        store.captureFromWebView(tabId = "tab-1", webView = webView, force = false, minIntervalMs = 60_000L)
        val newUrlBitmap = store.getPreview("tab-1")
        assertNotNull(newUrlBitmap)
        assertEquals(3, drawCalls)
        assertNotSame(updatedBitmap, newUrlBitmap)

        // Same tab, navigating to a third URL invalidates stale screenshot until captured
        reportedUrl = "https://example.com/page-3"
        store.invalidateIfUrlChanged(tabId = "tab-1", newUrl = reportedUrl)
        assertNull(store.getPreview("tab-1"))
    }

    @Test
    fun tabPreviewViewport_showsStartPageMiniPreview_evenIfStaleBitmapExists() {
        val staleBitmap = Bitmap.createBitmap(80, 60, Bitmap.Config.RGB_565)
        val startTab = BrowserTabModel(
            id = "tab-home",
            title = "Start Page",
            url = UrlUtils.START_PAGE_URL
        )

        composeRule.setContent {
            OrbitBrowserTheme(themeMode = AppThemeMode.LIGHT) {
                TabPreviewViewport(
                    tab = startTab,
                    isStartPage = true,
                    previewBitmap = staleBitmap,
                    themeMode = AppThemeMode.LIGHT,
                    startBackgroundMode = StartBackgroundMode.SOLID,
                    startWallpaperUri = ""
                )
            }
        }

        composeRule.onNodeWithText("Search").assertIsDisplayed()
    }

    @Test
    fun imageCollectionsScreen_displaysCollectedUrls_andAllowsAddingNewUrl() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, OrbitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = ImageCollectionRepository(db.imageCollectionDao())
        val viewModel = ImageCollectionsViewModel(repo)

        composeRule.setContent {
            OrbitBrowserTheme(themeMode = AppThemeMode.LIGHT) {
                ImageCollectionsScreen(
                    viewModel = viewModel,
                    onOpenUrl = {},
                    onDownloadUrl = {},
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithTag("add_collection_image_fab").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("collections_input_url")
            .performTextInput("https://example.com/sample-wallpaper.webp")
        composeRule.onNodeWithTag("collections_input_title")
            .performTextInput("Sample Wallpaper")
        composeRule.onNodeWithTag("collections_confirm_add").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Sample Wallpaper").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("Sample Wallpaper").assertIsDisplayed()
        db.close()
    }
}
