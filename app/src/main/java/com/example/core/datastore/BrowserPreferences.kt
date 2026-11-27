package com.example.core.datastore

import com.example.core.common.SearchEngine
import com.example.core.common.UrlUtils
import com.example.core.network.DohProvider

/**
 * Default Start Page background image URL.
 * Change this constant to manually update the default Start Page wallpaper image.
 */
const val DEFAULT_START_WALLPAPER_URL =
    "https://i.pinimg.com/736x/93/a8/98/93a898a6df83dca9e3e3e1d9ec19f666.jpg"

enum class AppThemeMode(val displayName: String) {
    LIGHT("Light"),
    DARK("Dark"),
    SYSTEM("System Default");

    companion object {
        fun fromName(value: String?): AppThemeMode =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: LIGHT
    }
}

enum class StartBackgroundMode(val displayName: String) {
    SOLID("Solid Color"),
    IMAGE("Image Background");

    companion object {
        fun fromName(value: String?): StartBackgroundMode =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: IMAGE
    }
}

enum class TabViewMode(val displayName: String) {
    CARD("Card View"),
    LIST("List View");

    companion object {
        fun fromName(value: String?): TabViewMode =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: CARD
    }
}

val DEFAULT_SPEED_DIAL_COLLECTIONS = listOf("Quick Access", "Work & Dev", "Media & Social", "Saved")

data class BrowserPreferences(
    val themeMode: AppThemeMode = AppThemeMode.LIGHT,
    val startBackgroundMode: StartBackgroundMode = StartBackgroundMode.IMAGE,
    val startWallpaperUri: String = DEFAULT_START_WALLPAPER_URL,
    val searchEngine: SearchEngine = SearchEngine.DUCKDUCKGO,
    val homepageUrl: String = UrlUtils.START_PAGE_URL,
    val javaScriptEnabled: Boolean = true,
    val blockTrackers: Boolean = true,
    val cosmeticFilteringEnabled: Boolean = true,
    val forceDarkWeb: Boolean = false,
    val desktopModeDefault: Boolean = false,
    val clearHistoryOnExit: Boolean = false,
    val tabViewMode: TabViewMode = TabViewMode.CARD,
    val tabPreviewEnabled: Boolean = true,
    val downloadFolderUri: String = "",
    val downloadFolderLabel: String = "Downloads (Default)",
    val confirmBeforeDownload: Boolean = true,
    val downloadChunkCount: Int = 4,
    val downloadWifiOnly: Boolean = false,
    val lastShareComponent: String = "",
    val lastShareAppLabel: String = "",
    val speedDialCollectionNames: List<String> = DEFAULT_SPEED_DIAL_COLLECTIONS,
    val dohProvider: DohProvider = DohProvider.CLOUDFLARE
)
