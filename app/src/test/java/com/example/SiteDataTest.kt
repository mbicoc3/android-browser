package com.example

import com.example.core.common.UrlUtils
import com.example.feature.sitedata.engine.OrbitSiteDataBridge
import com.example.feature.sitedata.model.GlobalClearSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteDataTest {

    @Test
    fun `extractOrigin normalizes standard https and custom port urls`() {
        assertEquals("https://github.com", UrlUtils.extractOrigin("https://github.com/trending?since=daily"))
        assertEquals("https://github.com", UrlUtils.extractOrigin("https://github.com:443/login"))
        assertEquals("http://localhost:8080", UrlUtils.extractOrigin("http://localhost:8080/api/test"))
        assertNull(UrlUtils.extractOrigin(UrlUtils.START_PAGE_URL))
        assertNull(UrlUtils.extractOrigin("about:blank"))
    }

    @Test
    fun `cookieCandidateUrls covers www and parent registrable host`() {
        val candidates = UrlUtils.cookieCandidateUrls("https://m.youtube.com")
        assertTrue(candidates.contains("https://m.youtube.com"))
        assertTrue(candidates.contains("https://www.m.youtube.com/"))
        assertTrue(candidates.contains("https://youtube.com/"))
    }

    @Test
    fun `parseInspectPayload extracts localStorage sessionStorage indexedDb caches and serviceWorkers`() {
        val json = """
            {
              "ok": true,
              "local": {
                "count": 2,
                "totalBytes": 42,
                "items": [
                  {"k": "theme", "v": "dark", "b": 20},
                  {"k": "token", "v": "abc", "b": 22}
                ]
              },
              "session": {
                "count": 1,
                "totalBytes": 16,
                "items": [{"k": "tabState", "v": "open", "b": 16}]
              },
              "indexedDb": [{"name": "app_db", "version": 3}],
              "caches": [{"name": "static-v1", "count": 12}],
              "serviceWorkers": [{"scope": "https://example.com/", "scriptUrl": "https://example.com/sw.js"}],
              "estUsage": 10240,
              "estQuota": 52428800
            }
        """.trimIndent()

        val parsed = OrbitSiteDataBridge.parseInspectPayload(json)
        assertTrue(parsed.ok)
        assertEquals(2, parsed.localStorageCount)
        assertEquals(42L, parsed.localStorageBytes)
        assertEquals(1, parsed.sessionStorageCount)
        assertEquals("app_db", parsed.indexedDbs.first().name)
        assertEquals(3, parsed.indexedDbs.first().version)
        assertEquals("static-v1", parsed.cacheBuckets.first().name)
        assertEquals(12, parsed.cacheBuckets.first().itemCount)
        assertEquals("https://example.com/sw.js", parsed.serviceWorkers.first().scriptUrl)
        assertEquals(10240L, parsed.estimatedUsageBytes)
    }

    @Test
    fun `globalClearSelection counts independent toggles accurately`() {
        val all = GlobalClearSelection()
        assertEquals(5, all.selectedCount)

        val onlyCookiesAndCache = GlobalClearSelection(
            clearCookies = true,
            clearLocalStorage = false,
            clearSessionStorage = false,
            clearIndexedDb = false,
            clearCache = true
        )
        assertEquals(2, onlyCookiesAndCache.selectedCount)
        assertFalse(onlyCookiesAndCache.clearLocalStorage)
        assertFalse(onlyCookiesAndCache.clearSessionStorage)
        assertFalse(onlyCookiesAndCache.clearIndexedDb)
    }

    @Test
    fun `mapEntitiesToSummaries keeps zero telemetry unfabricated`() {
        val entity = com.example.core.database.entity.SiteRecordEntity(
            origin = "https://orbit.test",
            host = "orbit.test",
            title = "orbit.test",
            lastVisitedAt = 0L,
            visitCount = 0
        )
        val summaries = com.example.feature.sitedata.repository.SiteDataRepository.mapEntitiesToSummaries(
            entities = listOf(entity),
            openTabsByOrigin = emptyMap()
        )
        val item = summaries.single()
        assertEquals(0, item.cookieCount)
        assertEquals(0, item.localStorageCount)
        assertEquals(0, item.sessionStorageCount)
        assertEquals(0, item.indexedDbCount)
        assertEquals(0, item.cacheStorageCount)
        assertEquals(0L, item.estimatedTotalBytes)
        assertFalse(item.hasAnyTrackedData)
    }
}
