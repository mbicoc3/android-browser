package com.example.core.registry

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.EnhancedEncryption
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.FindInPage
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tab
import com.example.core.navigation.BrowserRoutes

/**
 * Central Registry of Orbit Browser modules & tools.
 * Start Page, Tools Sheet, and Tools Hub read from this registry so adding new tools
 * later requires only registering a BrowserFeatureModule and its composable destination.
 */
object BrowserToolRegistry {

    const val ACTION_FIND_IN_PAGE = "action_find_in_page"
    const val ACTION_TOGGLE_DESKTOP = "action_toggle_desktop"
    const val ACTION_SHARE_PAGE = "action_share_page"
    const val ACTION_TOGGLE_INCOGNITO = "action_toggle_incognito"

    private val builtInModules: List<BrowserFeatureModule> = listOf(
        BrowserFeatureModule(
            id = "bookmarks",
            title = "Bookmarks",
            subtitle = "Saved sites & folders",
            route = BrowserRoutes.BOOKMARKS,
            icon = Icons.Rounded.Bookmarks,
            category = ToolCategory.NAVIGATION
        ),
        BrowserFeatureModule(
            id = "history",
            title = "History",
            subtitle = "Recent browsing timeline",
            route = BrowserRoutes.HISTORY,
            icon = Icons.Rounded.History,
            category = ToolCategory.NAVIGATION
        ),
        BrowserFeatureModule(
            id = "downloads",
            title = "Downloads",
            subtitle = "WorkManager background queue",
            route = BrowserRoutes.DOWNLOADS,
            icon = Icons.Rounded.Download,
            category = ToolCategory.NAVIGATION
        ),
        BrowserFeatureModule(
            id = "offline_pages",
            title = "Offline Pages",
            subtitle = "Saved web archives for offline reading",
            route = BrowserRoutes.OFFLINE_PAGES,
            icon = Icons.Rounded.OfflinePin,
            category = ToolCategory.NAVIGATION
        ),
        BrowserFeatureModule(
            id = "tabs",
            title = "Tabs",
            subtitle = "Manage standard & private tabs",
            route = BrowserRoutes.TABS,
            icon = Icons.Rounded.Tab,
            category = ToolCategory.NAVIGATION
        ),
        BrowserFeatureModule(
            id = "shield",
            title = "Ad & Tracker Shield",
            subtitle = "EasyPrivacy, AdGuard & cosmetic CSS",
            route = BrowserRoutes.SHIELD,
            icon = Icons.Rounded.Shield,
            category = ToolCategory.PRIVACY,
            badgeText = "ABP"
        ),
        BrowserFeatureModule(
            id = "site_data",
            title = "Site Data",
            subtitle = "Cookies, storage, IndexedDB & cache",
            route = BrowserRoutes.SITE_DATA,
            icon = Icons.Rounded.Storage,
            category = ToolCategory.PRIVACY,
            badgeText = "DATA"
        ),
        BrowserFeatureModule(
            id = "sandboxes",
            title = "Sandboxes",
            subtitle = "Isolated site sessions & profiles",
            route = BrowserRoutes.SANDBOXES,
            icon = Icons.Rounded.Layers,
            category = ToolCategory.PRIVACY,
            badgeText = "ISO"
        ),
        BrowserFeatureModule(
            id = "passwords",
            title = "Passwords",
            subtitle = "Encrypted vault, autofill & Proton Pass",
            route = BrowserRoutes.PASSWORDS,
            icon = Icons.Rounded.Key,
            category = ToolCategory.PRIVACY,
            badgeText = "VAULT"
        ),
        BrowserFeatureModule(
            id = "privacy_vault",
            title = "Privacy Vault",
            subtitle = "Android Keystore AES-GCM notes",
            route = BrowserRoutes.PRIVACY_VAULT,
            icon = Icons.Rounded.EnhancedEncryption,
            category = ToolCategory.PRIVACY,
            badgeText = "AES"
        ),
        BrowserFeatureModule(
            id = ACTION_TOGGLE_INCOGNITO,
            title = "Incognito Tab",
            subtitle = "Open ephemeral private session",
            route = null,
            icon = Icons.Rounded.Security,
            category = ToolCategory.PRIVACY,
            isInPageAction = true
        ),
        BrowserFeatureModule(
            id = ACTION_FIND_IN_PAGE,
            title = "Find in Page",
            subtitle = "Search text on active page",
            route = null,
            icon = Icons.Rounded.FindInPage,
            category = ToolCategory.UTILITIES,
            isInPageAction = true
        ),
        BrowserFeatureModule(
            id = ACTION_TOGGLE_DESKTOP,
            title = "Desktop Site",
            subtitle = "Switch user-agent viewport",
            route = null,
            icon = Icons.Rounded.DesktopWindows,
            category = ToolCategory.UTILITIES,
            isInPageAction = true
        ),
        BrowserFeatureModule(
            id = ACTION_SHARE_PAGE,
            title = "Share Link",
            subtitle = "Send active URL to apps",
            route = null,
            icon = Icons.Rounded.Share,
            category = ToolCategory.UTILITIES,
            isInPageAction = true
        ),
        BrowserFeatureModule(
            id = "settings",
            title = "Settings",
            subtitle = "Engine, shield & DataStore prefs",
            route = BrowserRoutes.SETTINGS,
            icon = Icons.Rounded.Settings,
            category = ToolCategory.UTILITIES
        ),
        BrowserFeatureModule(
            id = "tools_hub",
            title = "All Modules",
            subtitle = "Browse registered Orbit tools",
            route = BrowserRoutes.TOOLS_HUB,
            icon = Icons.Rounded.Extension,
            category = ToolCategory.UTILITIES
        )
    )

    private val dynamicModules = mutableListOf<BrowserFeatureModule>()

    fun registerModule(module: BrowserFeatureModule) {
        if (builtInModules.none { it.id == module.id } && dynamicModules.none { it.id == module.id }) {
            dynamicModules.add(module)
        }
    }

    fun getAllModules(): List<BrowserFeatureModule> = builtInModules + dynamicModules

    fun getNavigationDestinations(): List<BrowserFeatureModule> =
        getAllModules().filter { it.route != null && it.id != "tools_hub" }

    fun getQuickSheetTools(): List<BrowserFeatureModule> = getAllModules()

    fun getGroupedModules(): Map<ToolCategory, List<BrowserFeatureModule>> =
        getAllModules().groupBy { it.category }
}
