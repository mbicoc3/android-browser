package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.database.OrbitDatabase
import com.example.core.database.entity.BrowserTabEntity
import com.example.core.ui.theme.OrbitBrowserTheme
import com.example.feature.browser.ui.BrowserPageMenuCallbacks
import com.example.feature.browser.ui.BrowserPageMoreMenu
import com.example.feature.sandbox.model.SandboxModel
import com.example.feature.sandbox.model.SandboxPresets
import com.example.feature.sandbox.repository.SandboxRepository
import com.example.feature.sandbox.ui.CreateEditSandboxDialog
import com.example.feature.sandbox.ui.SandboxPickerDialog
import com.example.feature.sandbox.ui.SandboxesScreen
import com.example.feature.sandbox.viewmodel.SandboxesViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SandboxFeatureTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun migration8To9_preservesTabs_andCreatesSandboxesTable() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration_8_to_9_test.db"
        context.deleteDatabase(dbName)
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()

        val sqlite = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `bookmarks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `url` TEXT NOT NULL, `folder` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)")
        sqlite.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_bookmarks_url` ON `bookmarks` (`url`)")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `url` TEXT NOT NULL, `visitedAt` INTEGER NOT NULL)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_history_visitedAt` ON `history` (`visitedAt`)")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `browser_tabs` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `url` TEXT NOT NULL, `isIncognito` INTEGER NOT NULL, `isDesktopMode` INTEGER NOT NULL, `isActive` INTEGER NOT NULL, `position` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        sqlite.execSQL("INSERT INTO `browser_tabs` VALUES ('tab-1', 'GitHub', 'https://github.com', 0, 0, 1, 0, 1000)")
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
        sqlite.version = 8
        sqlite.close()

        val roomDb = Room.databaseBuilder(context, OrbitDatabase::class.java, dbName)
            .addMigrations(OrbitDatabase.MIGRATION_8_9)
            .allowMainThreadQueries()
            .build()
        try {
            val restoredTabs = roomDb.browserTabDao().getStandardTabsSnapshot()
            assertEquals(1, restoredTabs.size)
            assertEquals("tab-1", restoredTabs.first().id)
            assertNull(restoredTabs.first().sandboxId)
            assertTrue(roomDb.sandboxDao().getAllSnapshot().isEmpty())
        } finally {
            roomDb.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun repository_createsDistinctPersistentProfilesForSameSite_andPreservesProfileKeyOnRename() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, OrbitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = SandboxRepository(
                appContext = context,
                sandboxDao = db.sandboxDao(),
                browserTabDao = db.browserTabDao()
            )
            val work = repo.createSandbox(
                name = "Work GitHub",
                rawSiteUrl = "https://github.com/org",
                colorHex = SandboxPresets.colors[0].colorHex,
                iconKey = "work",
                keepLongTerm = true
            )
            val personal = repo.createSandbox(
                name = "Personal GitHub",
                rawSiteUrl = "https://github.com",
                colorHex = SandboxPresets.colors[1].colorHex,
                iconKey = "personal",
                keepLongTerm = true
            )

            assertNotEquals(work.id, personal.id)
            assertNotEquals(work.profileName, personal.profileName)
            assertEquals("github.com", work.pinnedDomain)
            assertEquals("github.com", personal.pinnedDomain)

            // Rename work sandbox — immutable Chromium profileName must stay identical for long-term session continuity
            repo.updateSandbox(
                id = work.id,
                name = "Work Enterprise",
                rawSiteUrl = "https://github.com/enterprise",
                colorHex = work.colorHex,
                iconKey = "work",
                keepLongTerm = true
            )
            val updated = repo.getById(work.id)
            assertNotNull(updated)
            assertEquals("Work Enterprise", updated?.name)
            assertEquals(work.profileName, updated?.profileName)

            repo.bindPreferredCredential(work.id, "cred-work-1")
            assertEquals("cred-work-1", repo.getById(work.id)?.preferredCredentialId)

            // Deleting sandbox removes its bound tabs too
            db.browserTabDao().upsertTab(
                BrowserTabEntity(
                    id = "sb-tab-1",
                    title = "Enterprise",
                    url = "https://github.com/enterprise",
                    isIncognito = false,
                    isDesktopMode = false,
                    isActive = true,
                    position = 0,
                    updatedAt = 2000L,
                    sandboxId = work.id
                )
            )
            repo.deleteSandbox(work.id)
            assertNull(repo.getById(work.id))
            assertTrue(db.browserTabDao().getStandardTabsSnapshot().isEmpty())
            assertNotNull(repo.getById(personal.id))
        } finally {
            db.close()
        }
    }

    @Test
    fun moreMenu_displaysSandboxEntry_andTriggersCallback() {
        var sandboxClicked = false
        composeRule.setContent {
            OrbitBrowserTheme {
                BrowserPageMoreMenu(
                    expanded = true,
                    onDismiss = {},
                    currentUrl = "https://github.com",
                    isBookmarked = false,
                    isDesktopMode = false,
                    activeSandboxName = "Work Account",
                    lastShareComponent = "",
                    lastShareAppLabel = "",
                    callbacks = BrowserPageMenuCallbacks(
                        onToggleBookmark = {},
                        onShare = {},
                        onQuickShare = {},
                        onToggleDesktopMode = {},
                        onAddToSpeedDial = {},
                        onFindInPage = {},
                        onSaveOffline = {},
                        onOpenSiteData = {},
                        onOpenSandboxPicker = { sandboxClicked = true }
                    )
                )
            }
        }
        composeRule.onNodeWithTag("more_menu_sandbox").assertIsDisplayed().performClick()
        assertTrue(sandboxClicked)
    }

    @Test
    fun createSandboxDialog_capturesInput() {
        var createdName = ""
        var createdUrl = ""
        var createdLongTerm = false

        composeRule.setContent {
            OrbitBrowserTheme {
                CreateEditSandboxDialog(
                    prefillSiteUrl = "https://mail.example.com",
                    onConfirm = { name, url, _, _, keep ->
                        createdName = name
                        createdUrl = url
                        createdLongTerm = keep
                    },
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithTag("sandbox_name_input").performTextInput("Second Account")
        composeRule.onNodeWithTag("sandbox_save_button").performClick()

        assertEquals("Second Account", createdName)
        assertEquals("https://mail.example.com", createdUrl)
        assertTrue(createdLongTerm)
    }

    @Test
    fun sandboxPickerDialog_selectsProfile() {
        var pickedSandboxId: String? = null
        val sample = SandboxModel(
            id = "sb-1",
            name = "Client A",
            profileName = "orbit_sb_clienta",
            pinnedDomain = "mail.example.com",
            startUrl = "https://mail.example.com",
            colorHex = SandboxPresets.colors.first().colorHex,
            iconKey = "work",
            keepLongTerm = true,
            preferredCredentialId = null,
            createdAt = 100L,
            lastUsedAt = 200L,
            openTabCount = 1
        )

        composeRule.setContent {
            OrbitBrowserTheme {
                SandboxPickerDialog(
                    currentUrl = "https://mail.example.com",
                    activeSandboxId = null,
                    sandboxes = listOf(sample),
                    isMultiProfileSupported = true,
                    onSelectSandbox = { pickedSandboxId = it.id },
                    onOpenInDefaultProfile = {},
                    onCreateNewSandbox = {},
                    onManageSandboxes = {},
                    onDismiss = {}
                )
            }
        }
        composeRule.onNodeWithTag("sandbox_pick_sb-1").assertIsDisplayed().performClick()
        assertEquals("sb-1", pickedSandboxId)
    }

    @Test
    fun sandboxesScreen_rendersCreatedProfiles() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, OrbitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = SandboxRepository(context, db.sandboxDao(), db.browserTabDao())
            val created = runBlocking {
                repo.createSandbox(
                    name = "Alt Shopping",
                    rawSiteUrl = "https://shop.example.com",
                    colorHex = SandboxPresets.colors[2].colorHex
                )
            }
            val vm = SandboxesViewModel(repo)
            composeRule.setContent {
                OrbitBrowserTheme {
                    SandboxesScreen(
                        viewModel = vm,
                        nextSuggestedColorHex = repo::nextSuggestedColorHex,
                        onOpenSandboxTab = {},
                        onBeforeDeleteCloseTabs = {},
                        onBack = {}
                    )
                }
            }
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithText("Alt Shopping").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Alt Shopping").assertIsDisplayed()
            composeRule.onNodeWithTag("sandbox_card_${created.id}").assertIsDisplayed()
        } finally {
            db.close()
        }
    }
}
