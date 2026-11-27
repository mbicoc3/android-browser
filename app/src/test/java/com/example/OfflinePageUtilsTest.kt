package com.example

import com.example.core.common.UrlUtils
import com.example.core.navigation.BrowserRoutes
import com.example.feature.browser.model.OmniboxCommandAction
import com.example.feature.browser.model.OmniboxSmartHelper
import com.example.feature.offline.repository.OfflinePagesRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflinePageUtilsTest {

    @Test
    fun `isOfflineArchiveUrl identifies saved mht paths only`() {
        val archive = "file:///storage/emulated/0/Android/data/com.example/files/Documents/orbit_offline_pages/news_1234.mht"
        assertTrue(UrlUtils.isOfflineArchiveUrl(archive))
        assertEquals("Offline Page", UrlUtils.extractHost(archive))

        assertFalse(UrlUtils.isOfflineArchiveUrl("https://example.com/orbit_offline_pages/test"))
        assertFalse(UrlUtils.isOfflineArchiveUrl("file:///sdcard/Download/random.html"))
        assertFalse(UrlUtils.isOfflineArchiveUrl(null))
    }

    @Test
    fun `buildArchiveFileName sanitizes title and produces mht extension`() {
        val fileName = OfflinePagesRepository.buildArchiveFileName(
            title = "Orbit Browser: Release Notes! (2026)",
            url = "https://example.com/orbit/notes",
            timestampMs = 1_700_000_000_000L
        )
        assertTrue(fileName.startsWith("orbit_browser_release_notes_2026_"))
        assertTrue(fileName.endsWith("_1700000000000.mht"))
        assertFalse(fileName.contains(" "))
        assertFalse(fileName.contains("/"))
    }

    @Test
    fun `slash offline command routes to offline pages screen`() {
        val matches = OmniboxSmartHelper.matchCommands("/off", isPageActive = false)
        assertEquals(1, matches.size)
        assertEquals(OmniboxCommandAction.OPEN_ROUTE, matches.first().action)
        assertEquals(BrowserRoutes.OFFLINE_PAGES, matches.first().route)
    }
}
