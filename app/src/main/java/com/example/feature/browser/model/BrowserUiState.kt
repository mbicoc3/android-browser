package com.example.feature.browser.model

import androidx.compose.ui.graphics.Color
import com.example.core.common.UrlUtils
import com.example.core.datastore.BrowserPreferences
import com.example.core.ui.theme.OrbitSpeedDialFallbackBg
import com.example.feature.sandbox.model.SandboxModel

data class DirectImageInfo(
    val url: String,
    val width: Int,
    val height: Int,
    val mimeType: String = "image/*"
) {
    val hasValidDimensions: Boolean
        get() = width > 0 && height > 0

    val dimensionLabel: String
        get() = if (hasValidDimensions) "${width}×${height}" else "Image"
}

data class OmniboxLocalMatch(
    val title: String,
    val url: String,
    val isBookmark: Boolean
)

data class BrowserTabModel(
    val id: String,
    val title: String = "Start Page",
    val url: String = UrlUtils.START_PAGE_URL,
    val isIncognito: Boolean = false,
    val isDesktopMode: Boolean = false,
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val blockedTrackersCount: Int = 0,
    val directImage: DirectImageInfo? = null,
    val sandboxId: String? = null,
    val sandboxName: String? = null,
    val sandboxProfileName: String? = null,
    val sandboxColorHex: Long? = null,
    val backToWebUrl: String? = null,
    val backToWebTitle: String? = null,
    val backToWebCanGoBack: Boolean = false,
    val backToWebCanGoForward: Boolean = false,
    val forwardToWebUrl: String? = null,
    val forwardToWebTitle: String? = null,
    val forwardToWebCanGoForward: Boolean = false,
    val forwardToStartPage: Boolean = false
)

data class SpeedDialShortcut(
    val id: Long = 0L,
    val title: String,
    val url: String,
    val shortBadge: String,
    val accentColor: Color,
    val iconPath: String = "",
    val collectionIndex: Int = 0,
    val containerColor: Color = OrbitSpeedDialFallbackBg
)

sealed interface WebCommand {
    data class LoadUrl(val tabId: String, val url: String) : WebCommand
    data class Reload(val tabId: String) : WebCommand
    data class StopLoading(val tabId: String) : WebCommand
    data class GoBack(val tabId: String) : WebCommand
    data class GoForward(val tabId: String) : WebCommand
    data class FindAll(val tabId: String, val query: String) : WebCommand
    data class FindNext(val tabId: String, val forward: Boolean) : WebCommand
    data class ClearMatches(val tabId: String) : WebCommand
    data class DestroyTab(val tabId: String) : WebCommand
}

data class BrowserUiState(
    val tabs: List<BrowserTabModel> = emptyList(),
    val activeTabId: String = "",
    val omniboxText: String = "",
    val isOmniboxFocused: Boolean = false,
    val suggestions: List<String> = emptyList(),
    val localMatches: List<OmniboxLocalMatch> = emptyList(),
    val speedDialItems: List<SpeedDialShortcut> = emptyList(),
    val sandboxes: List<SandboxModel> = emptyList(),
    val isCurrentUrlBookmarked: Boolean = false,
    val findInPageVisible: Boolean = false,
    val findInPageQuery: String = "",
    val findMatchIndex: Int = 0,
    val findTotalMatches: Int = 0,
    val preferences: BrowserPreferences = BrowserPreferences(),
    val statusBanner: String? = null
) {
    val activeTab: BrowserTabModel?
        get() = tabs.firstOrNull { it.id == activeTabId } ?: tabs.firstOrNull()
}
