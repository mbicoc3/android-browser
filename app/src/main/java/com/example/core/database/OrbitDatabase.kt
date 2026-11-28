package com.example.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.core.database.dao.BookmarkDao
import com.example.core.database.dao.BrowserTabDao
import com.example.core.database.dao.DownloadDao
import com.example.core.database.dao.FilterListDao
import com.example.core.database.dao.FilterRuleDao
import com.example.core.database.dao.HistoryDao
import com.example.core.database.dao.ImageCollectionDao
import com.example.core.database.dao.OfflinePageDao
import com.example.core.database.dao.PasswordVaultDao
import com.example.core.database.dao.SandboxDao
import com.example.core.database.dao.SiteRecordDao
import com.example.core.database.dao.SpeedDialDao
import com.example.core.database.dao.VaultNoteDao
import com.example.core.database.entity.BackupMetadataEntity
import com.example.core.database.entity.BookmarkEntity
import com.example.core.database.entity.BrowserTabEntity
import com.example.core.database.entity.CredentialEntity
import com.example.core.database.entity.CredentialFolderEntity
import com.example.core.database.entity.DownloadEntity
import com.example.core.database.entity.FilterListEntity
import com.example.core.database.entity.FilterRuleEntity
import com.example.core.database.entity.HistoryEntity
import com.example.core.database.entity.ImageCollectionEntity
import com.example.core.database.entity.OfflinePageEntity
import com.example.core.database.entity.PasswordHistoryEntity
import com.example.core.database.entity.SandboxEntity
import com.example.core.database.entity.SiteRecordEntity
import com.example.core.database.entity.SpeedDialEntity
import com.example.core.database.entity.VaultMetadataEntity
import com.example.core.database.entity.VaultNoteEntity

@Database(
    entities = [
        BookmarkEntity::class,
        HistoryEntity::class,
        BrowserTabEntity::class,
        DownloadEntity::class,
        VaultNoteEntity::class,
        SpeedDialEntity::class,
        SiteRecordEntity::class,
        FilterListEntity::class,
        FilterRuleEntity::class,
        OfflinePageEntity::class,
        CredentialEntity::class,
        VaultMetadataEntity::class,
        CredentialFolderEntity::class,
        PasswordHistoryEntity::class,
        BackupMetadataEntity::class,
        SandboxEntity::class,
        ImageCollectionEntity::class
    ],
    version = 10,
    exportSchema = false
)
abstract class OrbitDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun historyDao(): HistoryDao
    abstract fun browserTabDao(): BrowserTabDao
    abstract fun downloadDao(): DownloadDao
    abstract fun vaultNoteDao(): VaultNoteDao
    abstract fun speedDialDao(): SpeedDialDao
    abstract fun siteRecordDao(): SiteRecordDao
    abstract fun filterListDao(): FilterListDao
    abstract fun filterRuleDao(): FilterRuleDao
    abstract fun offlinePageDao(): OfflinePageDao
    abstract fun passwordVaultDao(): PasswordVaultDao
    abstract fun sandboxDao(): SandboxDao
    abstract fun imageCollectionDao(): ImageCollectionDao

    companion object {
        @Volatile
        private var instance: OrbitDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `site_records` (
                        `origin` TEXT NOT NULL,
                        `host` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `lastVisitedAt` INTEGER NOT NULL,
                        `visitCount` INTEGER NOT NULL,
                        `cookieCount` INTEGER NOT NULL,
                        `localStorageCount` INTEGER NOT NULL,
                        `localStorageBytes` INTEGER NOT NULL,
                        `sessionStorageCount` INTEGER NOT NULL,
                        `sessionStorageBytes` INTEGER NOT NULL,
                        `indexedDbCount` INTEGER NOT NULL,
                        `cacheStorageCount` INTEGER NOT NULL,
                        `serviceWorkerCount` INTEGER NOT NULL,
                        `webStorageUsageBytes` INTEGER NOT NULL,
                        `webStorageQuotaBytes` INTEGER NOT NULL,
                        `lastInspectedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`origin`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_site_records_host` ON `site_records` (`host`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_site_records_lastVisitedAt` ON `site_records` (`lastVisitedAt`)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `filter_lists` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `sourceUrl` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `enabled` INTEGER NOT NULL,
                        `ruleCount` INTEGER NOT NULL,
                        `cosmeticRuleCount` INTEGER NOT NULL,
                        `lastUpdatedAt` INTEGER NOT NULL,
                        `isBuiltIn` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `filter_rules` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `rawRule` TEXT NOT NULL,
                        `ruleType` TEXT NOT NULL,
                        `domainFilter` TEXT NOT NULL,
                        `enabled` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_filter_rules_enabled` ON `filter_rules` (`enabled`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_filter_rules_ruleType` ON `filter_rules` (`ruleType`)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `speedBytesPerSec` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `chunkCount` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `supportsRanges` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `targetFolderUri` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `targetFolderLabel` TEXT NOT NULL DEFAULT 'Downloads (Default)'")
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `stagingPath` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `wifiOnly` INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `offline_pages` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `originalUrl` TEXT NOT NULL,
                        `filePath` TEXT NOT NULL,
                        `fileSizeBytes` INTEGER NOT NULL,
                        `savedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_offline_pages_originalUrl` ON `offline_pages` (`originalUrl`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_offline_pages_savedAt` ON `offline_pages` (`savedAt`)")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `speed_dial` ADD COLUMN `iconPath` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `speed_dial` ADD COLUMN `collectionIndex` INTEGER NOT NULL DEFAULT 0")
            }
        }

        internal val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `credentials` (
                        `id` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `normalizedDomain` TEXT NOT NULL,
                        `usernameHint` TEXT,
                        `folderId` TEXT,
                        `isFavorite` INTEGER NOT NULL,
                        `encryptedPayload` BLOB NOT NULL,
                        `encryptionVersion` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_credentials_normalizedDomain` ON `credentials` (`normalizedDomain`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_credentials_folderId` ON `credentials` (`folderId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_credentials_updatedAt` ON `credentials` (`updatedAt`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `vault_metadata` (
                        `id` TEXT NOT NULL,
                        `encryptedDek` BLOB NOT NULL,
                        `dekNonce` BLOB NOT NULL,
                        `salt` BLOB NOT NULL,
                        `argon2MemoryKb` INTEGER NOT NULL,
                        `argon2Iterations` INTEGER NOT NULL,
                        `argon2Parallelism` INTEGER NOT NULL,
                        `verifierBlob` BLOB NOT NULL,
                        `keystoreWrappedDek` BLOB,
                        `keystoreDekNonce` BLOB,
                        `biometricEnabled` INTEGER NOT NULL,
                        `autoLockTimeoutMs` INTEGER NOT NULL,
                        `lockOnBackground` INTEGER NOT NULL,
                        `clipboardClearSeconds` INTEGER NOT NULL,
                        `allowHttpAutofill` INTEGER NOT NULL,
                        `neverSaveDomainsJson` TEXT NOT NULL,
                        `encryptionVersion` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `credential_folders` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `password_history` (
                        `id` TEXT NOT NULL,
                        `credentialId` TEXT NOT NULL,
                        `encryptedPassword` BLOB NOT NULL,
                        `encryptionVersion` INTEGER NOT NULL,
                        `changedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`credentialId`) REFERENCES `credentials`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_password_history_credentialId` ON `password_history` (`credentialId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_password_history_changedAt` ON `password_history` (`changedAt`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `backup_metadata` (
                        `id` TEXT NOT NULL,
                        `fileName` TEXT NOT NULL,
                        `credentialCount` INTEGER NOT NULL,
                        `backupVersion` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        internal val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sandboxes` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `profileName` TEXT NOT NULL,
                        `pinnedDomain` TEXT NOT NULL DEFAULT '',
                        `startUrl` TEXT NOT NULL DEFAULT '',
                        `colorHex` INTEGER NOT NULL DEFAULT 4281909240,
                        `iconKey` TEXT NOT NULL DEFAULT 'work',
                        `keepLongTerm` INTEGER NOT NULL DEFAULT 1,
                        `preferredCredentialId` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `lastUsedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sandboxes_profileName` ON `sandboxes` (`profileName`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sandboxes_lastUsedAt` ON `sandboxes` (`lastUsedAt`)")
                db.execSQL("ALTER TABLE `browser_tabs` ADD COLUMN `sandboxId` TEXT")
            }
        }

        internal val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `image_collections` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `imageUrl` TEXT NOT NULL,
                        `sourcePageUrl` TEXT NOT NULL DEFAULT '',
                        `collectionName` TEXT NOT NULL DEFAULT 'General',
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_image_collections_imageUrl` ON `image_collections` (`imageUrl`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_image_collections_collectionName` ON `image_collections` (`collectionName`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_image_collections_createdAt` ON `image_collections` (`createdAt`)")
            }
        }

        fun getInstance(context: Context): OrbitDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    OrbitDatabase::class.java,
                    "orbit_browser.db"
                )
                    .addMigrations(
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10
                    )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
