package com.example.core.navigation

/**
 * Centralized navigation routes for Orbit Browser.
 * New feature modules register their destination route here.
 */
object BrowserRoutes {
    const val BROWSER = "browser"
    const val TABS = "tabs"
    const val BOOKMARKS = "bookmarks"
    const val HISTORY = "history"
    const val DOWNLOADS = "downloads"
    const val OFFLINE_PAGES = "offline_pages"
    const val PRIVACY_VAULT = "privacy_vault"
    const val PASSWORDS = "passwords"
    const val SHIELD = "shield"
    const val SITE_DATA = "site_data"
    const val SITE_DATA_PATTERN = "site_data?origin={origin}"
    const val SANDBOXES = "sandboxes"
    const val SETTINGS = "settings"
    const val TOOLS_HUB = "tools_hub"

    fun siteDataRoute(origin: String? = null): String {
        return if (origin.isNullOrBlank()) {
            SITE_DATA
        } else {
            "$SITE_DATA?origin=${android.net.Uri.encode(origin)}"
        }
    }
}
