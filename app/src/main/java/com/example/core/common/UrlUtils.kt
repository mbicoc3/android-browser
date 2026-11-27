package com.example.core.common

import android.net.Uri
import android.util.Patterns
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

enum class SearchEngine(
    val id: String,
    val displayName: String,
    val searchTemplate: String,
    val homeUrl: String
) {
    DUCKDUCKGO(
        id = "duckduckgo",
        displayName = "DuckDuckGo",
        searchTemplate = "https://duckduckgo.com/?q=%s",
        homeUrl = "https://duckduckgo.com"
    ),
    GOOGLE(
        id = "google",
        displayName = "Google",
        searchTemplate = "https://www.google.com/search?q=%s",
        homeUrl = "https://www.google.com"
    ),
    BRAVE(
        id = "brave",
        displayName = "Brave Search",
        searchTemplate = "https://search.brave.com/search?q=%s",
        homeUrl = "https://search.brave.com"
    ),
    STARTPAGE(
        id = "startpage",
        displayName = "Startpage",
        searchTemplate = "https://www.startpage.com/do/search?query=%s",
        homeUrl = "https://www.startpage.com"
    ),
    BING(
        id = "bing",
        displayName = "Bing",
        searchTemplate = "https://www.bing.com/search?q=%s",
        homeUrl = "https://www.bing.com"
    );

    companion object {
        fun fromId(id: String?): SearchEngine =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DUCKDUCKGO
    }
}

object UrlUtils {
    const val START_PAGE_URL = "orbit://start"

    fun isStartPage(url: String?): Boolean =
        url.isNullOrBlank() || url == START_PAGE_URL || url == "about:blank"

    fun resolveInputToUrl(rawInput: String, searchEngine: SearchEngine): String {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return START_PAGE_URL
        if (trimmed.equals(START_PAGE_URL, ignoreCase = true)) return START_PAGE_URL

        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("file://", ignoreCase = true) ||
            trimmed.startsWith("about:", ignoreCase = true)
        ) {
            return trimmed
        }

        val looksLikeDomain = !trimmed.contains(" ") &&
            (trimmed.contains(".") || trimmed.startsWith("localhost", ignoreCase = true)) &&
            Patterns.WEB_URL.matcher("https://$trimmed").matches()

        return if (looksLikeDomain) {
            "https://$trimmed"
        } else {
            val encoded = URLEncoder.encode(trimmed, StandardCharsets.UTF_8.name())
            searchEngine.searchTemplate.replace("%s", encoded)
        }
    }

    fun extractHost(url: String?): String {
        if (isStartPage(url)) return "Start Page"
        if (isOfflineArchiveUrl(url)) return "Offline Page"
        val trimmed = url?.trim().orEmpty()
        val javaHost = runCatching { java.net.URI(trimmed).host }.getOrNull()
        if (!javaHost.isNullOrBlank()) {
            return javaHost.removePrefix("www.")
        }
        return runCatching {
            val uri = Uri.parse(trimmed)
            uri?.host?.removePrefix("www.") ?: trimmed
        }.getOrDefault(trimmed)
    }

    fun isOfflineArchiveUrl(url: String?): Boolean =
        url?.startsWith("file://", ignoreCase = true) == true && url.contains("orbit_offline_pages")

    fun extractOrigin(url: String?): String? {
        if (isStartPage(url)) return null
        return runCatching {
            val uri = java.net.URI(url?.trim().orEmpty())
            val scheme = uri.scheme?.lowercase() ?: return null
            if (scheme != "http" && scheme != "https") return null
            val host = uri.host?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
            val port = uri.port
            val includePort = port != -1 && !((scheme == "https" && port == 443) || (scheme == "http" && port == 80))
            if (includePort) "$scheme://$host:$port" else "$scheme://$host"
        }.getOrNull()
    }

    fun normalizeOrigin(rawOrigin: String): String? {
        val trimmed = rawOrigin.trim().trimEnd('/')
        extractOrigin(trimmed)?.let { return it }
        if (!trimmed.contains("://") && trimmed.contains(".")) {
            return extractOrigin("https://$trimmed")
        }
        return null
    }

    fun cookieCandidateUrls(origin: String): List<String> {
        val uri = runCatching { java.net.URI(origin) }.getOrNull() ?: return listOf(origin)
        val scheme = uri.scheme?.lowercase() ?: "https"
        val host = uri.host?.lowercase() ?: return listOf(origin)
        val results = linkedSetOf(origin)
        results.add("$scheme://$host/")
        if (!host.startsWith("www.")) {
            results.add("$scheme://www.$host/")
        } else {
            results.add("$scheme://${host.removePrefix("www.")}/")
        }
        val parts = host.split('.')
        if (parts.size >= 3) {
            val parent = parts.takeLast(2).joinToString(".")
            results.add("$scheme://$parent/")
        }
        return results.toList()
    }

    fun isSecureHttps(url: String?): Boolean =
        url?.startsWith("https://", ignoreCase = true) == true

    fun looksLikeDirectUrl(rawInput: String): Boolean {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty() || trimmed.contains(" ")) return false
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("file://", ignoreCase = true) ||
            trimmed.startsWith("orbit://", ignoreCase = true)
        ) {
            return true
        }
        return (trimmed.contains(".") || trimmed.startsWith("localhost", ignoreCase = true)) &&
            Patterns.WEB_URL.matcher("https://$trimmed").matches()
    }

    fun isLikelyDirectImageUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val cleanPath = url.substringBefore('?').substringBefore('#').lowercase()
        return IMAGE_EXTENSIONS.any { cleanPath.endsWith(it) }
    }

    private val IMAGE_EXTENSIONS = listOf(
        ".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp", ".avif", ".svg", ".ico"
    )
}
