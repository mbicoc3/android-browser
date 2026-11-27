package com.example

import com.example.core.webengine.WebResourceCategory
import com.example.feature.shield.engine.OrbitFilterEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShieldEngineTest {

    @Test
    fun `blocks built-in EasyPrivacy and AdGuard tracking subresources while allowing main frame`() {
        val engine = OrbitFilterEngine()

        val trackerResult = engine.evaluateRequest(
            requestUrl = "https://www.google-analytics.com/analytics.js",
            documentHost = "example.org",
            category = WebResourceCategory.SCRIPT
        )
        assertTrue(trackerResult.shouldBlock)

        val mainFrameResult = engine.evaluateRequest(
            requestUrl = "https://www.google-analytics.com/",
            documentHost = "google-analytics.com",
            category = WebResourceCategory.MAIN_FRAME
        )
        assertFalse(mainFrameResult.shouldBlock)
    }

    @Test
    fun `exception rules override block rules and third-party modifiers work`() {
        val engine = OrbitFilterEngine()
        engine.reloadRules(
            sources = listOf(
                "EasyPrivacy" to listOf(
                    "||telemetry.example.net^",
                    "||analytics.firstparty.com^\$third-party"
                )
            ),
            customRules = listOf("@@||telemetry.example.net^")
        )

        val excepted = engine.evaluateRequest(
            requestUrl = "https://telemetry.example.net/ping.js",
            documentHost = "news.com",
            category = WebResourceCategory.SCRIPT
        )
        assertFalse(excepted.shouldBlock)
        assertEquals("@@||telemetry.example.net^", excepted.matchedRule)

        val samePartyAllowed = engine.evaluateRequest(
            requestUrl = "https://analytics.firstparty.com/hit",
            documentHost = "www.firstparty.com",
            category = WebResourceCategory.XHR_FETCH
        )
        assertFalse(samePartyAllowed.shouldBlock)

        val thirdPartyBlocked = engine.evaluateRequest(
            requestUrl = "https://analytics.firstparty.com/hit",
            documentHost = "other-site.org",
            category = WebResourceCategory.XHR_FETCH
        )
        assertTrue(thirdPartyBlocked.shouldBlock)
    }

    @Test
    fun `cosmetic rules generate safe scoped CSS and respect domain exclusions`() {
        val engine = OrbitFilterEngine()
        engine.reloadRules(
            sources = listOf(
                "Cosmetic" to listOf(
                    "##.global-ad-slot",
                    "news.example.com##.sponsored-box",
                    "~trusted.example.com##.promo-popup"
                )
            ),
            customRules = listOf("##.custom-banner")
        )

        val newsCss = engine.cosmeticCssForHost("news.example.com")
        assertNotNull(newsCss)
        assertTrue(newsCss!!.contains(".global-ad-slot"))
        assertTrue(newsCss.contains(".sponsored-box"))
        assertTrue(newsCss.contains(".promo-popup"))
        assertTrue(newsCss.contains(".custom-banner"))

        val trustedCss = engine.cosmeticCssForHost("trusted.example.com")
        assertNotNull(trustedCss)
        assertFalse(trustedCss!!.contains(".promo-popup"))
        assertFalse(trustedCss.contains(".sponsored-box"))
        assertTrue(trustedCss.contains(".global-ad-slot"))

        assertNull(engine.cosmeticCssForHost(""))
    }
}
