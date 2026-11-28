package com.example

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.example.core.datastore.AppThemeMode
import com.example.core.ui.components.OrbitActionPill
import com.example.core.ui.components.OrbitConfirmDialog
import com.example.core.ui.components.OrbitControlTokens
import com.example.core.ui.components.OrbitGhostButton
import com.example.core.ui.components.OrbitPrimaryButton
import com.example.core.ui.components.OrbitSecondaryButton
import com.example.core.ui.components.OrbitSegmentPill
import com.example.core.ui.components.OrbitTextField
import com.example.core.ui.theme.OrbitBrowserTheme
import com.example.feature.browser.ui.AddSpeedDialDialog
import com.example.feature.browser.ui.BrowserPageMenuCallbacks
import com.example.feature.browser.ui.BrowserPageMoreMenu
import com.example.feature.passwords.model.PendingCredentialSaveRequest
import com.example.feature.passwords.model.WebFormType
import com.example.feature.passwords.ui.SaveOrUpdateCredentialDialog
import com.example.feature.shield.ui.AddCustomRuleDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OrbitUnifiedControlsAndModalsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun unifiedControls_shareUniformHeightsInDarkTheme() {
        composeRule.setContent {
            OrbitBrowserTheme(themeMode = AppThemeMode.DARK) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OrbitTextField(
                        value = "",
                        onValueChange = {},
                        label = "Site URL",
                        modifier = Modifier.testTag("uniform_input")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OrbitPrimaryButton(
                            text = "Save",
                            onClick = {},
                            modifier = Modifier.testTag("uniform_primary")
                        )
                        OrbitSecondaryButton(
                            text = "Cancel",
                            onClick = {},
                            modifier = Modifier.testTag("uniform_secondary")
                        )
                        OrbitGhostButton(
                            text = "Reset",
                            onClick = {},
                            modifier = Modifier.testTag("uniform_ghost")
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OrbitSegmentPill(
                            label = "General",
                            selected = true,
                            onClick = {},
                            modifier = Modifier.testTag("uniform_segment")
                        )
                        OrbitActionPill(
                            label = "Copy",
                            leadingIcon = Icons.Rounded.Bolt,
                            onClick = {},
                            modifier = Modifier.testTag("uniform_action_pill")
                        )
                    }
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("uniform_input").assertHeightIsAtLeast(OrbitControlTokens.InputMinHeight)
        composeRule.onNodeWithTag("uniform_primary").assertHeightIsEqualTo(OrbitControlTokens.ControlHeight)
        composeRule.onNodeWithTag("uniform_secondary").assertHeightIsEqualTo(OrbitControlTokens.ControlHeight)
        composeRule.onNodeWithTag("uniform_ghost").assertHeightIsEqualTo(OrbitControlTokens.ControlHeight)
        composeRule.onNodeWithTag("uniform_segment").assertHeightIsEqualTo(OrbitControlTokens.CapsuleHeight)
        composeRule.onNodeWithTag("uniform_action_pill").assertHeightIsEqualTo(OrbitControlTokens.CapsuleHeight)
    }

    @Test
    fun addCustomRuleDialog_validatesAndSubmitsInLightTheme() {
        var addedRule = ""
        composeRule.setContent {
            OrbitBrowserTheme(themeMode = AppThemeMode.LIGHT) {
                AddCustomRuleDialog(
                    onDismiss = {},
                    onAddRule = { addedRule = it }
                )
            }
        }

        composeRule.onNodeWithTag("custom_rule_confirm_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("custom_rule_input").performTextInput("||tracker.example.org^")
        composeRule.onNodeWithTag("custom_rule_confirm_button").assertIsEnabled().performClick()
        composeRule.waitForIdle()
        assertEquals("||tracker.example.org^", addedRule)
    }

    @Test
    fun orbitConfirmDialog_requiresAcknowledgmentBeforeEnablingConfirm() {
        var confirmedWipe = false
        composeRule.setContent {
            var acknowledged by remember { mutableStateOf(false) }
            OrbitBrowserTheme(themeMode = AppThemeMode.DARK) {
                OrbitConfirmDialog(
                    title = "Clear Data?",
                    message = "Removes local cache.",
                    confirmText = "Clear Now",
                    acknowledgeText = "I understand",
                    acknowledged = acknowledged,
                    onAcknowledgedChange = { acknowledged = it },
                    confirmTestTag = "confirm_wipe_btn",
                    onConfirm = { confirmedWipe = true },
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithTag("confirm_wipe_btn").assertIsNotEnabled()
        composeRule.onNodeWithText("I understand").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("confirm_wipe_btn").assertIsEnabled().performClick()
        composeRule.waitForIdle()
        assertTrue(confirmedWipe)
    }

    @Test
    fun addSpeedDialDialog_selectsCollectionAndSubmits() {
        var pinnedUrl = ""
        var selectedIdx = -1
        composeRule.setContent {
            OrbitBrowserTheme(themeMode = AppThemeMode.DARK) {
                AddSpeedDialDialog(
                    collectionNames = listOf("Core", "Dev"),
                    defaultCollectionIndex = 0,
                    onDismiss = {},
                    onConfirm = { _, url, idx ->
                        pinnedUrl = url
                        selectedIdx = idx
                    }
                )
            }
        }

        composeRule.onNodeWithText("Dev").performClick()
        composeRule.onNodeWithText("Website URL").performTextInput("https://kotlinlang.org")
        composeRule.onNodeWithText("Add").performClick()
        composeRule.waitForIdle()
        assertEquals("https://kotlinlang.org", pinnedUrl)
        assertEquals(1, selectedIdx)
    }

    @Test
    fun saveOrUpdateCredentialDialog_supportsSavingSeparateEntry() {
        var savedTitle = ""
        var savedAsNew = false
        composeRule.setContent {
            OrbitBrowserTheme(themeMode = AppThemeMode.LIGHT) {
                SaveOrUpdateCredentialDialog(
                    request = PendingCredentialSaveRequest(
                        tabId = "tab-1",
                        pageUrl = "https://github.com/login",
                        domain = "github.com",
                        username = "dev@orbit.org",
                        password = "StrongPassword123!",
                        formType = WebFormType.LOGIN,
                        existingCredentialId = "cred-1",
                        existingTitle = "GitHub"
                    ),
                    onConfirmSave = { title, asNew ->
                        savedTitle = title
                        savedAsNew = asNew
                    },
                    onNeverForSite = {},
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithText("Update Saved Password?").assertIsDisplayed()
        composeRule.onNodeWithText("Save as a separate new entry").performClick()
        composeRule.waitForIdle()
        assertEquals("GitHub", savedTitle)
        assertTrue(savedAsNew)
    }

    @Test
    fun browserPageMoreMenu_preservesPopupItemsAndQuickShareRow() {
        var quickShared = false
        var toggledDesktop = false

        composeRule.setContent {
            OrbitBrowserTheme(themeMode = AppThemeMode.DARK) {
                BrowserPageMoreMenu(
                    expanded = true,
                    onDismiss = {},
                    currentUrl = "https://example.com",
                    isBookmarked = false,
                    isDesktopMode = false,
                    lastShareComponent = "",
                    lastShareAppLabel = "",
                    callbacks = BrowserPageMenuCallbacks(
                        onToggleBookmark = {},
                        onShare = {},
                        onQuickShare = { quickShared = true },
                        onToggleDesktopMode = { toggledDesktop = true },
                        onAddToSpeedDial = {},
                        onFindInPage = {},
                        onSaveOffline = {},
                        onOpenSiteData = {}
                    )
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("browser_page_more_menu").assertIsDisplayed()
        composeRule.onNodeWithTag("more_menu_share_row").assertIsDisplayed()
        composeRule.onNodeWithTag("more_menu_quick_share_button").assertIsDisplayed().performClick()
        assertTrue(quickShared)

        composeRule.onNodeWithTag("more_menu_desktop_agent").performClick()
        assertTrue(toggledDesktop)
    }
}
