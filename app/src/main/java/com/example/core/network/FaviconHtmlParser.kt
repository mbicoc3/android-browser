package com.example.core.network

import com.example.core.common.UrlUtils
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.net.URI

data class FaviconCandidate(
    val url: String,
    val priority: Int,
    val estimatedSize: Int = 0,
    val isGoogleS2: Boolean = false
)

data class ParsedHtmlIcons(
    val iconCandidates: List<FaviconCandidate>,
    val manifestUrls: List<String>
)

/**
 * Pure Kotlin/JVM parser for extracting favicon, touch-icon, Web App Manifest,
 * OpenGraph/Tile meta icons, and ICO PNG frames across any website setup.
 */
object FaviconHtmlParser {

    private val moshi: Moshi by lazy { Moshi.Builder().build() }
    private val jsonMapAdapter by lazy {
        val type = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
        moshi.adapter<Map<String, Any>>(type)
    }

    private val BASE_TAG_REGEX = Regex("<base\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val LINK_TAG_REGEX = Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val META_TAG_REGEX = Regex("<meta\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val ATTR_REGEX = Regex(
        """([a-zA-Z0-9_:-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+))"""
    )

    private val FALLBACK_PALETTE = listOf(
        0xFF2563EBL,
        0xFF0EA5E9L,
        0xFF10B981L,
        0xFF8B5CF6L,
        0xFFF59E0BL,
        0xFFEC4899L,
        0xFFEF4444L,
        0xFF14B8A6L,
        0xFF6366F1L,
        0xFFD97706L
    )

    fun parseHtml(html: String, pageUrl: String): ParsedHtmlIcons {
        val headEnd = html.indexOf("</head>", ignoreCase = true)
        val scanScope = if (headEnd in 1..150_000) html.substring(0, headEnd + 7) else html.take(150_000)

        val resolvedBase = BASE_TAG_REGEX.find(scanScope)?.let { match ->
            val attrs = parseAttributes(match.value)
            val baseHref = attrs["href"]?.trim().orEmpty()
            if (baseHref.isNotBlank()) resolveUrl(pageUrl, baseHref) else null
        } ?: pageUrl

        val candidates = mutableListOf<FaviconCandidate>()
        val manifests = LinkedHashSet<String>()

        LINK_TAG_REGEX.findAll(scanScope).forEach { match ->
            val attrs = parseAttributes(match.value)
            val relTokens = attrs["rel"].orEmpty().lowercase()
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }
                .toSet()
            val href = attrs["href"]?.trim().orEmpty()
            if (href.isBlank() || relTokens.isEmpty()) return@forEach

            val resolvedUrl = resolveUrl(resolvedBase, href) ?: return@forEach
            val type = attrs["type"].orEmpty().lowercase()
            val sizeHint = parseMaxSize(attrs["sizes"].orEmpty())
            val isSvg = type.contains("svg") ||
                resolvedUrl.substringBefore('?').lowercase().endsWith(".svg") ||
                resolvedUrl.startsWith("data:image/svg+xml", ignoreCase = true)

            if ("manifest" in relTokens) {
                manifests += resolvedUrl
                return@forEach
            }

            val basePriority = when {
                "apple-touch-icon" in relTokens -> 120
                "apple-touch-icon-precomposed" in relTokens -> 115
                "icon" in relTokens && "shortcut" in relTokens -> 100
                "icon" in relTokens -> 105
                "shortcut" in relTokens -> 95
                "fluid-icon" in relTokens -> 85
                "mask-icon" in relTokens -> 40
                else -> -1
            }
            if (basePriority < 0) return@forEach

            val rasterBonus = if (isSvg) -55 else 25
            val sizeBonus = (sizeHint.coerceIn(0, 512) / 16)
            candidates += FaviconCandidate(
                url = resolvedUrl,
                priority = basePriority + rasterBonus + sizeBonus,
                estimatedSize = sizeHint
            )
        }

        META_TAG_REGEX.findAll(scanScope).forEach { match ->
            val attrs = parseAttributes(match.value)
            val key = (attrs["name"] ?: attrs["property"] ?: attrs["itemprop"]).orEmpty().lowercase()
            val content = attrs["content"]?.trim().orEmpty()
            if (content.isBlank()) return@forEach

            val priority = when (key) {
                "msapplication-tileimage" -> 75
                "og:image", "og:image:url", "og:image:secure_url" -> 35
                "twitter:image", "twitter:image:src" -> 30
                "image" -> 28
                else -> -1
            }
            if (priority < 0) return@forEach

            val resolvedUrl = resolveUrl(resolvedBase, content) ?: return@forEach
            candidates += FaviconCandidate(
                url = resolvedUrl,
                priority = priority,
                estimatedSize = 0
            )
        }

        return ParsedHtmlIcons(
            iconCandidates = deduplicateAndSort(candidates),
            manifestUrls = manifests.toList()
        )
    }

    fun parseManifestIcons(manifestJson: String, manifestUrl: String): List<FaviconCandidate> {
        val root = runCatching { jsonMapAdapter.fromJson(manifestJson) }.getOrNull() ?: return emptyList()
        val array = root["icons"] as? List<*> ?: return emptyList()
        val results = mutableListOf<FaviconCandidate>()
        for (rawItem in array) {
            val item = rawItem as? Map<*, *> ?: continue
            val src = (item["src"] as? String)?.trim().orEmpty()
            if (src.isBlank()) continue
            val resolved = resolveUrl(manifestUrl, src) ?: continue
            val type = (item["type"] as? String).orEmpty().lowercase()
            val sizeHint = parseMaxSize((item["sizes"] as? String).orEmpty())
            val isSvg = type.contains("svg") ||
                resolved.substringBefore('?').lowercase().endsWith(".svg")
            val rasterBonus = if (isSvg) -45 else 30
            val sizeBonus = (sizeHint.coerceIn(0, 512) / 12)
            results += FaviconCandidate(
                url = resolved,
                priority = 95 + rasterBonus + sizeBonus,
                estimatedSize = sizeHint
            )
        }
        return deduplicateAndSort(results)
    }

    fun extractWellKnownRootCandidates(pageUrl: String): List<FaviconCandidate> {
        val origin = UrlUtils.extractOrigin(pageUrl) ?: return emptyList()
        return listOf(
            FaviconCandidate("$origin/apple-touch-icon.png", priority = 90, estimatedSize = 180),
            FaviconCandidate("$origin/apple-touch-icon-precomposed.png", priority = 88, estimatedSize = 180),
            FaviconCandidate("$origin/favicon-192x192.png", priority = 86, estimatedSize = 192),
            FaviconCandidate("$origin/favicon-96x96.png", priority = 84, estimatedSize = 96),
            FaviconCandidate("$origin/favicon-32x32.png", priority = 80, estimatedSize = 32),
            FaviconCandidate("$origin/favicon.png", priority = 78, estimatedSize = 64),
            FaviconCandidate("$origin/favicon.ico", priority = 70, estimatedSize = 32)
        )
    }

    fun extractWellKnownManifestCandidates(pageUrl: String): List<String> {
        val origin = UrlUtils.extractOrigin(pageUrl) ?: return emptyList()
        return listOf(
            "$origin/manifest.webmanifest",
            "$origin/manifest.json",
            "$origin/site.webmanifest"
        )
    }

    fun extractPublicResolverFallbacks(pageUrl: String): List<FaviconCandidate> {
        val host = UrlUtils.extractHost(pageUrl).trim().lowercase()
        if (host.isBlank() || !host.contains(".")) return emptyList()
        return listOf(
            FaviconCandidate(
                url = "https://icons.duckduckgo.com/ip3/$host.ico",
                priority = 20,
                estimatedSize = 64,
                isGoogleS2 = false
            ),
            FaviconCandidate(
                url = "https://www.google.com/s2/favicons?domain=$host&sz=128",
                priority = 15,
                estimatedSize = 128,
                isGoogleS2 = true
            )
        )
    }

    fun resolveUrl(baseUrl: String, rawHref: String): String? {
        val trimmed = rawHref.trim()
        if (trimmed.isBlank()) return null
        if (trimmed.startsWith("data:image/", ignoreCase = true)) return trimmed
        if (trimmed.startsWith("javascript:", ignoreCase = true) ||
            trimmed.startsWith("mailto:", ignoreCase = true) ||
            trimmed.startsWith("#")
        ) {
            return null
        }
        return runCatching {
            val base = URI.create(baseUrl)
            val resolved = if (trimmed.startsWith("//")) {
                URI.create("${base.scheme ?: "https"}:$trimmed")
            } else {
                base.resolve(trimmed)
            }
            val scheme = resolved.scheme?.lowercase().orEmpty()
            if (scheme == "http" || scheme == "https") resolved.toString() else null
        }.getOrNull()
    }

    /**
     * Parses a Windows `.ico` container and extracts the largest embedded PNG frame directly
     * when present (so Android's BitmapFactory decodes multi-icon `.ico` bundles cleanly on all API levels).
     * Falls back to returning the original bytes when no embedded PNG frame exists.
     */
    fun extractLargestIcoImageBytes(bytes: ByteArray): ByteArray {
        if (bytes.size < 22) return bytes
        val reserved = ((bytes[1].toInt() and 0xFF) shl 8) or (bytes[0].toInt() and 0xFF)
        val type = ((bytes[3].toInt() and 0xFF) shl 8) or (bytes[2].toInt() and 0xFF)
        val count = ((bytes[5].toInt() and 0xFF) shl 8) or (bytes[4].toInt() and 0xFF)
        if (reserved != 0 || type != 1 || count !in 1..64) return bytes

        var bestPngBytes: ByteArray? = null
        var bestArea = -1

        for (i in 0 until count) {
            val entryOffset = 6 + i * 16
            if (entryOffset + 16 > bytes.size) break
            val wRaw = bytes[entryOffset].toInt() and 0xFF
            val hRaw = bytes[entryOffset + 1].toInt() and 0xFF
            val width = if (wRaw == 0) 256 else wRaw
            val height = if (hRaw == 0) 256 else hRaw
            val area = width * height

            val dataSize = readIntLE(bytes, entryOffset + 8)
            val dataOffset = readIntLE(bytes, entryOffset + 12)
            if (dataSize <= 8 || dataOffset < 6 || dataOffset + dataSize > bytes.size) continue

            if (isPngSignature(bytes, dataOffset, dataSize) && area > bestArea) {
                bestArea = area
                bestPngBytes = bytes.copyOfRange(dataOffset, dataOffset + dataSize)
            }
        }
        return bestPngBytes ?: bytes
    }

    fun extractFirstLetterBadge(title: String, url: String): String {
        val cleanTitle = title.trim()
        val fromTitle = if (cleanTitle.isNotBlank() &&
            !cleanTitle.startsWith("http://", ignoreCase = true) &&
            !cleanTitle.startsWith("https://", ignoreCase = true)
        ) {
            cleanTitle.firstOrNull { it.isLetterOrDigit() }
        } else {
            null
        }
        if (fromTitle != null) return fromTitle.uppercaseChar().toString()

        val host = UrlUtils.extractHost(url)
            .removePrefix("www.")
            .removePrefix("m.")
            .trim()
        val fromHost = host.firstOrNull { it.isLetterOrDigit() }
        return (fromHost?.uppercaseChar() ?: 'W').toString()
    }

    fun pickAccentColorHex(title: String, url: String): Long {
        val seed = UrlUtils.extractHost(url).ifBlank { title }.lowercase()
        val index = (seed.hashCode() and 0x7FFFFFFF) % FALLBACK_PALETTE.size
        return FALLBACK_PALETTE[index]
    }

    private fun deduplicateAndSort(candidates: List<FaviconCandidate>): List<FaviconCandidate> {
        val bestByUrl = LinkedHashMap<String, FaviconCandidate>()
        for (c in candidates) {
            val existing = bestByUrl[c.url]
            if (existing == null || c.priority > existing.priority) {
                bestByUrl[c.url] = c
            }
        }
        return bestByUrl.values.sortedWith(
            compareByDescending<FaviconCandidate> { it.priority }
                .thenByDescending { it.estimatedSize }
        )
    }

    private fun parseAttributes(tag: String): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        ATTR_REGEX.findAll(tag).forEach { match ->
            val key = match.groupValues[1].lowercase()
            val value = match.groupValues[2].ifEmpty {
                match.groupValues[3].ifEmpty { match.groupValues[4] }
            }
            map[key] = value
        }
        return map
    }

    private fun parseMaxSize(sizesAttr: String): Int {
        if (sizesAttr.isBlank()) return 0
        if (sizesAttr.equals("any", ignoreCase = true)) return 256
        var best = 0
        sizesAttr.split(Regex("\\s+")).forEach { token ->
            val w = token.lowercase()
                .substringBefore('x', missingDelimiterValue = "")
                .toIntOrNull() ?: 0
            if (w > best) best = w
        }
        return best
    }

    private fun readIntLE(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)

    private fun isPngSignature(bytes: ByteArray, offset: Int, size: Int): Boolean {
        if (size < 8 || offset + 8 > bytes.size) return false
        return bytes[offset] == 0x89.toByte() &&
            bytes[offset + 1] == 0x50.toByte() &&
            bytes[offset + 2] == 0x4E.toByte() &&
            bytes[offset + 3] == 0x47.toByte() &&
            bytes[offset + 4] == 0x0D.toByte() &&
            bytes[offset + 5] == 0x0A.toByte() &&
            bytes[offset + 6] == 0x1A.toByte() &&
            bytes[offset + 7] == 0x0A.toByte()
    }
}
