package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import com.example.core.datastore.AppThemeMode
import com.example.core.datastore.BrowserPreferences
import com.example.core.datastore.StartBackgroundMode
import com.example.core.ui.theme.OrbitBrowserTheme
import com.example.feature.browser.model.ContextTargetKind
import com.example.feature.browser.model.WebContextMenuTarget
import com.example.feature.browser.ui.BrowserBottomBar
import com.example.feature.browser.ui.HomeCustomizeSheet
import com.example.feature.browser.ui.ToolsMenuSheet
import com.example.feature.browser.ui.WebContextMenuSheet
import com.example.feature.downloads.ui.DownloadConfirmSheet
import com.example.feature.downloads.ui.DownloadSettingsSheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BottomSheetAndTabSwipeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun bottomTabIcon_swipeLeftAndRight_triggersExactNextPrevTabSwitch() {
        val switches = mutableListOf<Int>()
        var openedTabs = 0
        var openedTools = 0
        var openedPrivate = 0

        composeRule.setContent {
            OrbitBrowserTheme(themeMode = AppThemeMode.DARK) {
                BrowserBottomBar(
                    canGoBack = true,
                    canGoForward = false,
                    tabsCount = 3,
                    isIncognito = false,
                    isStartPage = false,
                    isLightSolidStart = false,
                    onBack = {},
                    onForward = {},
                    onNewTab = {},
                    onGoHome = {},
                    onOpenTabs = { openedTabs++ },
                    onNewIncognitoTab = { openedPrivate++ },
                    onSwitchTab = { switches.add(it) },
                    onOpenTools = { openedTools++ }
                )
            }
        }

        // Swipe left directly on Tab icon -> next tab (+1)
        composeRule.onNodeWithTag("bottom_tabs_button").performTouchInput {
            swipeLeft(startX = centerX + 18f, endX = centerX - 95f, durationMillis = 120)
        }
        composeRule.waitForIdle()
        assertEquals(listOf(1), switches)
        assertEquals(0, openedTabs)

        // Swipe right directly on Tab icon -> previous tab (-1)
        composeRule.onNodeWithTag("bottom_tabs_button").performTouchInput {
            swipeRight(startX = centerX - 18f, endX = centerX + 95f, durationMillis = 120)
        }
        composeRule.waitForIdle()
        assertEquals(listOf(1, -1), switches)
        assertEquals(0, openedTabs)

        // Tap Tab icon -> opens tab switcher
        composeRule.onNodeWithTag("bottom_tabs_button").performClick()
        composeRule.waitForIdle()
        assertEquals(1, openedTabs)

        // Swipe up on Tab icon -> opens Tools sheet
        composeRule.onNodeWithTag("bottom_tabs_button").performTouchInput {
            swipeUp(startY = bottom - 4f, endY = top - 60f, durationMillis = 100)
        }
        composeRule.waitForIdle()
        assertEquals(1, openedTools)

        // Long press Tab icon -> opens private tab
        composeRule.onNodeWithTag("bottom_tabs_button").performTouchInput {
            longClick()
        }
        composeRule.waitForIdle()
        assertEquals(1, openedPrivate)
    }

    @Test
    fun toolsMenuSheet_redesigned2050_preservesAllControlsAndGestureCard() {
        var clickedModuleId: String? = null
        var toggledShield = false

        composeRule.setContent {
            OrbitBrowserTheme(themeMode = AppThemeMode.DARK) {
                ToolsMenuSheet(
                    isDesktopMode = true,
                    isPageActive = true,
                    blockTrackers = true,
                    cosmeticFilteringEnabled = true,
                    blockedTrackersCount = 14,
                    onToggleBlockTrackers = { toggledShield = !toggledShield },
                    onToggleCosmeticFiltering = {},
                    onReloadActivePage = {},
                    onDismiss = {},
                    onModuleClick = { clickedModuleId = it.id }
                )
            }
        }

        composeRule.onNodeWithTag("menu_protection_reload_button").assertIsDisplayed()
        composeRule.onNodeWithTag("menu_toggle_track_ad_blocker").assertIsDisplayed().performClick()
        assertTrue(toggledShield)

        composeRule.onNodeWithTag("sheet_tool_bookmarks").assertIsDisplayed().performClick()
        assertEquals("bookmarks", clickedModuleId)

        composeRule.onNodeWithTag("tools_sheet_tab_more").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("sheet_tool_passwords").assertIsDisplayed()

        composeRule.onNodeWithTag("tools_sheet_pager").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("menu_gestures_guide_card").assertIsDisplayed()
    }

    @Test
    fun homeCustomizeSheet_rendersAndSwitchesThemeInLightMode() {
        var selectedTheme: AppThemeMode? = null

        composeRule.setContent {
            OrbitBrowserTheme(themeMode = AppThemeMode.LIGHT) {
                HomeCustomizeSheet(
                    themeMode = AppThemeMode.LIGHT,
                    backgroundMode = StartBackgroundMode.SOLID,
                    wallpaperUri = "",
                    onThemeModeSelected = { selectedTheme = it },
                    onBackgroundModeSelected = {},
                    onWallpaperUriSelected = {},
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithTag("customize_theme_dark").performScrollTo().performClick()
        assertEquals(AppThemeMode.DARK, selectedTheme)
        composeRule.onNodeWithTag("customize_bg_solid").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("customize_bg_image").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("customize_pick_wallpaper").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("customize_image_url_button").performScrollTo().assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodes(androidx.compose.ui.test.hasText("Shortcut")).fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodes(androidx.compose.ui.test.hasText("Modules")).fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodes(androidx.compose.ui.test.hasText("Adjust")).fetchSemanticsNodes().size)
    }

    @Test
    fun downloadSettingsSheet_rendersAndTogglesInLightMode() {
        var confirmChanged = false

        composeRule.setContent {
            OrbitBrowserTheme(themeMode = AppThemeMode.LIGHT) {
                DownloadSettingsSheet(
                    preferences = BrowserPreferences(),
                    onFolderPicked = {},
                    onResetFolder = {},
                    onConfirmBeforeDownloadChanged = { confirmChanged = it },
                    onChunkCountChanged = {},
                    onWifiOnlyChanged = {},
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithTag("settings_confirm_download_switch").performScrollTo().performClick()
        assertEquals(false, confirmChanged)
        composeRule.onNodeWithTag("settings_wifi_only_download_switch").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun contextMenuSheet_andDownloadConfirmSheet_preserveActionTags() {
        var startedDownload = false
        var openedNewTab = false

        composeRule.setContent {
            OrbitBrowserTheme(themeMode = AppThemeMode.DARK) {
                WebContextMenuSheet(
                    target = WebContextMenuTarget(
                        tabId = "tab-1",
                        kind = ContextTargetKind.LINK,
                        linkUrl = "https://example.org/article",
                        linkText = "Read Article"
                    ),
                    onOpenInCurrentTab = {},
                    onOpenInNewTab = { _, _ -> openedNewTab = true },
                    onSelectText = {},
                    onSearchText = {},
                    onDownloadUrl = {},
                    onSetAsBackgroundImage = {},
                    onSaveBookmark = { _, _ -> },
                    onPinToHome = { _, _ -> },
                    onShowMessage = {},
                    onDismiss = {}
                )
                DownloadConfirmSheet(
                    initialUrl = "https://example.org/archive.zip",
                    initialFileName = "archive.zip",
                    initialMimeType = "application/zip",
                    preferences = BrowserPreferences(),
                    onFolderPicked = {},
                    onConfirmBeforeDownloadChanged = {},
                    onConfirmStart = { _, _, _, _, _, _, _ -> startedDownload = true },
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithTag("ctx_open_new_tab").performClick()
        assertTrue(openedNewTab)

        composeRule.onNodeWithTag("download_confirm_chunk_6").performScrollTo().performClick()
        composeRule.onNodeWithTag("download_confirm_start_button").performScrollTo().performClick()
        assertTrue(startedDownload)
    }
}
