package com.example

import com.example.feature.browser.model.DirectImageInfo
import com.example.feature.browser.model.OmniboxCommandAction
import com.example.feature.browser.model.OmniboxSmartHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OmniboxSmartHelperTest {

    @Test
    fun `evaluateSmartQuery calculates arithmetic and percentages`() {
        val calc = OmniboxSmartHelper.evaluateSmartQuery("= (1920 + 1080) * 2")
        assertNotNull(calc)
        assertEquals("6000", calc?.value)

        val pct = OmniboxSmartHelper.evaluateSmartQuery("25% of 400")
        assertNotNull(pct)
        assertEquals("100", pct?.value)

        assertNull(OmniboxSmartHelper.evaluateSmartQuery("https://example.com/1+2"))
        assertNull(OmniboxSmartHelper.evaluateSmartQuery("orbit browser"))
    }

    @Test
    fun `evaluateSmartQuery converts data length and temperature units`() {
        val data = OmniboxSmartHelper.evaluateSmartQuery("2 gb to mb")
        assertNotNull(data)
        assertEquals("2048 MB", data?.value)

        val temp = OmniboxSmartHelper.evaluateSmartQuery("100 c to f")
        assertNotNull(temp)
        assertEquals("212 F", temp?.value)

        val len = OmniboxSmartHelper.evaluateSmartQuery("5 km in m")
        assertNotNull(len)
        assertEquals("5000 M", len?.value)
    }

    @Test
    fun `matchCommands filters page-dependent commands cleanly`() {
        val activeCommands = OmniboxSmartHelper.matchCommands("/", isPageActive = true)
        assertTrue(activeCommands.isNotEmpty())

        val findActive = OmniboxSmartHelper.matchCommands("/find", isPageActive = true)
        assertEquals(listOf(OmniboxCommandAction.FIND_IN_PAGE), findActive.map { it.action })

        val findStart = OmniboxSmartHelper.matchCommands("/find", isPageActive = false)
        assertTrue(findStart.isEmpty())
    }

    @Test
    fun `formatImageSummary computes aspect ratio and megapixels`() {
        val info = DirectImageInfo(
            url = "https://example.com/sample.jpg",
            width = 1920,
            height = 1080,
            mimeType = "image/jpeg"
        )
        assertEquals("1920×1080", info.dimensionLabel)
        val summary = OmniboxSmartHelper.formatImageSummary(info)
        assertTrue(summary.contains("1920 × 1080 px"))
        assertTrue(summary.contains("16:9"))
        assertTrue(summary.contains("2.1 MP"))
        assertTrue(summary.contains("JPG"))
    }
}
