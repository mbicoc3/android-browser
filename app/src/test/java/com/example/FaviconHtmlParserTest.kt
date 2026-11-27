package com.example

import com.example.core.network.FaviconHtmlParser
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FaviconHtmlParserTest {

    @Test
    fun parseHtml_extractsAndPrioritizesTouchIconAndRasterIcons() {
        val html = """
            <html>
              <head>
                <link rel="icon" type="image/svg+xml" href="/logo.svg">
                <link href="/assets/icon-32.png" sizes="32x32" rel="icon">
                <link rel="apple-touch-icon" sizes="180x180" href="//cdn.example.com/apple-180.png">
                <link rel="manifest" href="/site.webmanifest">
                <meta name="msapplication-TileImage" content="/ms-tile.png">
              </head>
            </html>
        """.trimIndent()

        val parsed = FaviconHtmlParser.parseHtml(html, "https://example.com/docs/intro")
        assertEquals(listOf("https://example.com/site.webmanifest"), parsed.manifestUrls)
        assertTrue(parsed.iconCandidates.isNotEmpty())
        assertEquals("https://cdn.example.com/apple-180.png", parsed.iconCandidates.first().url)
        assertTrue(parsed.iconCandidates.any { it.url == "https://example.com/assets/icon-32.png" })
        assertTrue(parsed.iconCandidates.any { it.url == "https://example.com/ms-tile.png" })
        // Raster PNG icons should sort ahead of SVG icons
        val pngIdx = parsed.iconCandidates.indexOfFirst { it.url.endsWith("icon-32.png") }
        val svgIdx = parsed.iconCandidates.indexOfFirst { it.url.endsWith("logo.svg") }
        assertTrue(pngIdx in 0 until svgIdx)
    }

    @Test
    fun parseHtml_respectsBaseTagAndDataUri() {
        val html = """
            <html>
              <head>
                <base href="https://static.example.org/app/">
                <link rel="shortcut icon" href="favicon.png">
                <link rel="icon" href="data:image/png;base64,AAAA">
              </head>
            </html>
        """.trimIndent()

        val parsed = FaviconHtmlParser.parseHtml(html, "https://example.org/")
        assertTrue(parsed.iconCandidates.any { it.url == "https://static.example.org/app/favicon.png" })
        assertTrue(parsed.iconCandidates.any { it.url == "data:image/png;base64,AAAA" })
    }

    @Test
    fun parseManifestIcons_prefersLargestRasterIcon() {
        val manifest = """
            {
              "name": "Example App",
              "icons": [
                { "src": "/icons/icon-48.png", "sizes": "48x48", "type": "image/png" },
                { "src": "/icons/icon-512.png", "sizes": "512x512", "type": "image/png" },
                { "src": "/icons/icon.svg", "sizes": "any", "type": "image/svg+xml" }
              ]
            }
        """.trimIndent()

        val icons = FaviconHtmlParser.parseManifestIcons(manifest, "https://app.example.com/manifest.json")
        assertEquals("https://app.example.com/icons/icon-512.png", icons.first().url)
    }

    @Test
    fun extractLargestIcoImageBytes_extractsLargestEmbeddedPngFrame() {
        val smallPng = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2
        )
        val largePng = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 9, 8, 7, 6
        )

        val headerAndTableSize = 6 + 2 * 16
        val smallOffset = headerAndTableSize
        val largeOffset = smallOffset + smallPng.size
        val buf = ByteBuffer.allocate(largeOffset + largePng.size).order(ByteOrder.LITTLE_ENDIAN)
        // ICONDIR
        buf.putShort(0) // reserved
        buf.putShort(1) // type = ICO
        buf.putShort(2) // count = 2
        // Entry 0: 16x16
        buf.put(16.toByte()); buf.put(16.toByte()); buf.put(0); buf.put(0)
        buf.putShort(1); buf.putShort(32)
        buf.putInt(smallPng.size); buf.putInt(smallOffset)
        // Entry 1: 64x64
        buf.put(64.toByte()); buf.put(64.toByte()); buf.put(0); buf.put(0)
        buf.putShort(1); buf.putShort(32)
        buf.putInt(largePng.size); buf.putInt(largeOffset)

        buf.put(smallPng)
        buf.put(largePng)

        val extracted = FaviconHtmlParser.extractLargestIcoImageBytes(buf.array())
        assertArrayEquals(largePng, extracted)
    }

    @Test
    fun extractFirstLetterBadge_extractsCleanUppercaseLetter() {
        assertEquals("G", FaviconHtmlParser.extractFirstLetterBadge("GitHub", "https://github.com"))
        assertEquals("E", FaviconHtmlParser.extractFirstLetterBadge("", "https://www.example.com/path"))
        assertEquals("R", FaviconHtmlParser.extractFirstLetterBadge("https://m.reddit.com", "https://m.reddit.com"))
        assertEquals("W", FaviconHtmlParser.extractFirstLetterBadge("Wikipedia", "https://en.wikipedia.org"))
    }
}
