package com.example

import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToString
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PasswordsNavigationRobolectricTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun openingPasswordsFromToolsMenu_createAddLockUnlockAndTabs_doesNotCrash() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("bottom_tools_button").performClick()
        composeRule.waitForIdle()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("sheet_tool_passwords").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("sheet_tool_passwords").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("vault_setup_master_input").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("vault_setup_master_input").performScrollTo().performTextInput("StrongPass123!")
        composeRule.onNodeWithTag("vault_setup_confirm_input").performScrollTo().performTextInput("StrongPass123!")
        composeRule.onNodeWithTag("vault_setup_create_button").performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithTag("passwords_add_fab").fetchSemanticsNodes().isNotEmpty()
        }

        // Add a credential and verify it decrypts and shows up
        composeRule.onNodeWithTag("passwords_add_fab").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("cred_dialog_title_input").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("cred_dialog_title_input").performTextInput("GitHub")
        composeRule.onNodeWithTag("cred_dialog_url_input").performTextInput("https://github.com")
        composeRule.onNodeWithTag("cred_dialog_username_input").performTextInput("alice@example.com")
        composeRule.onNodeWithTag("cred_dialog_password_input").performTextInput("CorrectHorseBatteryStaple9!")
        composeRule.onNodeWithTag("cred_dialog_save_button").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("GitHub").fetchSemanticsNodes().isNotEmpty()
        }

        // Switch through all tabs
        composeRule.onNodeWithTag("passwords_section_local_vault").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("passwords_section_generator").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("passwords_section_security").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("passwords_section_proton_pass").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("passwords_section_settings").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("passwords_section_all").performClick()
        composeRule.waitForIdle()

        // Lock and unlock again
        composeRule.onNodeWithTag("passwords_lock_now_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("vault_unlock_password_input").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("vault_unlock_password_input").performScrollTo().performTextInput("StrongPass123!")
        composeRule.onNodeWithTag("vault_unlock_button").performScrollTo().performClick()

        try {
            composeRule.waitUntil(timeoutMillis = 15_000) {
                composeRule.onAllNodesWithTag("passwords_add_fab").fetchSemanticsNodes().isNotEmpty() &&
                    composeRule.onAllNodesWithText("GitHub").fetchSemanticsNodes().isNotEmpty()
            }
        } catch (t: Throwable) {
            throw AssertionError("Tree after unlock:\n" + composeRule.onRoot(useUnmergedTree = true).printToString(), t)
        }
    }

    @Test
    fun openingPasswordsFromSettingsAndToolsHub_andPrivacyVault_doesNotCrash() {
        composeRule.waitForIdle()

        // 1) Open Passwords from Settings screen
        composeRule.onNodeWithTag("bottom_tools_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("sheet_tool_settings").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("sheet_tool_settings").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasScrollToNodeAction()).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasTestTag("settings_open_passwords_button"))
        composeRule.onNodeWithTag("settings_open_passwords_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("vault_setup_master_input").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithTag("vault_unlock_password_input").fetchSemanticsNodes().isNotEmpty()
        }

        // Navigate back to Start Page
        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()

        // 2) Open Passwords from Tools Hub
        composeRule.onNodeWithTag("bottom_tools_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("sheet_tool_tools_hub").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("sheet_tool_tools_hub").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasScrollToNodeAction()).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasTestTag("hub_module_passwords"))
        composeRule.onNodeWithTag("hub_module_passwords").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("vault_setup_master_input").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithTag("vault_unlock_password_input").fetchSemanticsNodes().isNotEmpty()
        }

        // Navigate back to Start Page and test Privacy Vault avatar entry
        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("start_avatar_button").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("start_avatar_button").performClick()
        composeRule.waitForIdle()
    }
}
