package com.example

import android.app.KeyguardManager
import android.content.Context
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.core.common.UrlUtils
import com.example.feature.browser.model.WebCommand
import com.example.feature.browser.viewmodel.BrowserViewModel
import com.example.feature.passwords.crypto.VaultBiometricKeystoreHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StartPageAndHistoryNavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun startPage_switchingAll4Collections_keepsSearchBarAndPagerHeightUniform() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("start_page_search_input").fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithTag("speed_dial_collection_dot_3").fetchSemanticsNodes().isNotEmpty()
        }

        val initialSearchBounds = composeRule.onNodeWithTag("start_page_search_input").fetchSemanticsNode().boundsInRoot
        val initialPagerBounds = composeRule.onNodeWithTag("speed_dial_collections_pager").fetchSemanticsNode().boundsInRoot
        val initialSearchTop = initialSearchBounds.top
        val initialPagerHeight = initialPagerBounds.height

        for (dotIdx in listOf(1, 2, 3, 0)) {
            composeRule.onNodeWithTag("speed_dial_collection_dot_$dotIdx").performClick()
            composeRule.waitForIdle()

            val searchBounds = composeRule.onNodeWithTag("start_page_search_input").fetchSemanticsNode().boundsInRoot
            val pagerBounds = composeRule.onNodeWithTag("speed_dial_collections_pager").fetchSemanticsNode().boundsInRoot
            val currentSearchTop = searchBounds.top
            val currentPagerHeight = pagerBounds.height

            assertTrue(
                "Search bar jumped on collection $dotIdx: expected ~$initialSearchTop, was $currentSearchTop",
                abs(initialSearchTop - currentSearchTop) <= 1f
            )
            assertTrue(
                "Pager height changed on collection $dotIdx: expected ~$initialPagerHeight, was $currentPagerHeight",
                abs(initialPagerHeight - currentPagerHeight) <= 1f
            )
        }
    }

    @Test
    fun browserViewModel_homeGestureAndBackForward_preservesSessionAndHistory() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("start_page_search_input").fetchSemanticsNodes().isNotEmpty()
        }

        val vm = androidx.lifecycle.ViewModelProvider(composeRule.activity)[BrowserViewModel::class.java]
        composeRule.waitUntil(timeoutMillis = 5_000) {
            vm.uiState.value.activeTab != null
        }
        val activeId = requireNotNull(vm.uiState.value.activeTab?.id)

        // 1. Navigate to webpage and finish loading
        vm.loadUrlInActiveTab("https://example.com")
        vm.onPageFinished(
            tabId = activeId,
            url = "https://example.com",
            title = "Example Domain",
            canGoBack = false,
            canGoForward = false
        )
        composeRule.waitForIdle()

        // 2. Trigger Home gesture (+ hold / swipe)
        vm.goHome()
        composeRule.waitForIdle()
        val atHomeFromGesture = requireNotNull(vm.uiState.value.activeTab)
        assertTrue(UrlUtils.isStartPage(atHomeFromGesture.url))
        assertTrue("Back button must remain active after Home gesture", atHomeFromGesture.canGoBack)
        assertEquals("https://example.com", atHomeFromGesture.backToWebUrl)

        // 3. Press Back (<) from Start Page -> restores webpage with Forward to Start Page
        assertTrue(vm.goBack())
        composeRule.waitForIdle()
        val restoredTab = requireNotNull(vm.uiState.value.activeTab)
        assertEquals("https://example.com", restoredTab.url)
        assertTrue(restoredTab.forwardToStartPage)
        assertFalse("Back on restored single-page site must still navigate to Start Page", restoredTab.canGoBack)

        // 4. Press Forward (>) -> returns to Start Page
        vm.goForward()
        composeRule.waitForIdle()
        val backAtStart = requireNotNull(vm.uiState.value.activeTab)
        assertTrue(UrlUtils.isStartPage(backAtStart.url))
        assertEquals("https://example.com", backAtStart.backToWebUrl)

        // 5. Restore page then press Back (<) as user without Home -> goes to Start Page with Forward to page
        assertTrue(vm.goBack())
        assertTrue(vm.goBack())
        composeRule.waitForIdle()
        val startWithForward = requireNotNull(vm.uiState.value.activeTab)
        assertTrue(UrlUtils.isStartPage(startWithForward.url))
        assertEquals("https://example.com", startWithForward.forwardToWebUrl)
        assertTrue(startWithForward.canGoForward)

        // 6. Press Forward (>) from Start Page -> restores webpage
        vm.goForward()
        composeRule.waitForIdle()
        val forwardRestored = requireNotNull(vm.uiState.value.activeTab)
        assertEquals("https://example.com", forwardRestored.url)

        // Reset back to Start Page for test isolation
        vm.closeAllTabs()
        composeRule.waitForIdle()
    }

    @Test
    fun biometricHelper_detectsPhoneLockDeviceSecurity() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val helper = VaultBiometricKeystoreHelper()
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val shadowKeyguard = Shadows.shadowOf(keyguard)

        shadowKeyguard.setIsDeviceSecure(false)
        assertFalse(helper.isDeviceSecure(context))

        shadowKeyguard.setIsDeviceSecure(true)
        assertTrue(helper.isDeviceSecure(context))
        assertTrue(helper.isDeviceLockOrBiometricAvailable(context))
    }
}
