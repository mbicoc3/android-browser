package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.database.OrbitDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class PasswordsApi24AndMigrationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun api24_openPasswords_createVault_andAddCredential_doesNotCrash() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("bottom_tools_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("sheet_tool_passwords").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("sheet_tool_passwords").performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("vault_setup_master_input").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("vault_setup_master_input").performScrollTo().performTextInput("Api24Pass123!")
        composeRule.onNodeWithTag("vault_setup_confirm_input").performScrollTo().performTextInput("Api24Pass123!")
        composeRule.onNodeWithTag("vault_setup_create_button").performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithTag("passwords_add_fab").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("passwords_add_fab").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("cred_dialog_title_input").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("cred_dialog_title_input").performTextInput("Proton")
        composeRule.onNodeWithTag("cred_dialog_url_input").performTextInput("https://proton.me")
        composeRule.onNodeWithTag("cred_dialog_password_input").performTextInput("Api24SecretValue!9")
        composeRule.onNodeWithTag("cred_dialog_save_button").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Proton").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun migration7To8_upgradesExistingDatabaseWithoutSchemaError() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration_7_to_8_test.db"
        context.deleteDatabase(dbName)
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()

        // Create minimal v7 database with user_version = 7
        val sqlite = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `bookmarks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `url` TEXT NOT NULL, `folder` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)")
        sqlite.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_bookmarks_url` ON `bookmarks` (`url`)")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `url` TEXT NOT NULL, `visitedAt` INTEGER NOT NULL)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_history_visitedAt` ON `history` (`visitedAt`)")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `browser_tabs` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `url` TEXT NOT NULL, `isIncognito` INTEGER NOT NULL, `isDesktopMode` INTEGER NOT NULL, `isActive` INTEGER NOT NULL, `position` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
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
        sqlite.version = 7
        sqlite.close()

        val roomDb = Room.databaseBuilder(context, OrbitDatabase::class.java, dbName)
            .addMigrations(OrbitDatabase.MIGRATION_7_8, OrbitDatabase.MIGRATION_8_9, OrbitDatabase.MIGRATION_9_10)
            .allowMainThreadQueries()
            .build()
        try {
            val metadata = roomDb.passwordVaultDao().getMetadata()
            assertNull(metadata)
            assertTrue(roomDb.passwordVaultDao().getAllCredentialsSnapshot().isEmpty())
            assertTrue(roomDb.passwordVaultDao().getFoldersSnapshot().isEmpty())
        } finally {
            roomDb.close()
            context.deleteDatabase(dbName)
        }
    }
}
