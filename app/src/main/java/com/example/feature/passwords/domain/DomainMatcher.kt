package com.example.feature.passwords.domain

import java.net.IDN
import java.net.URI

enum class DomainMatchQuality(val rank: Int) {
    EXACT_ORIGIN(3),
    EXACT_HOST(2),
    REGISTRABLE_DOMAIN(1),
    BLOCKED_INSECURE_HTTP(0),
    NONE(-1)
}

data class NormalizedOriginInfo(
    val scheme: String,
    val host: String,
    val port: Int,
    val registrableDomain: String,
    val originKey: String
) {
    val isSecureHttps: Boolean get() = scheme == "https"
}

/**
 * Origin-strict domain normalizer and matcher for browser autofill.
 * Rejects non-HTTP(S) schemes, lookalike domains, and insecure HTTP origins by default.
 */
object DomainMatcher {

    // Common multi-label public suffixes so eTLD+1 extraction never merges e.g. a.co.uk & b.co.uk
    private val MULTI_PART_SUFFIXES = setOf(
        "co.uk", "org.uk", "ac.uk", "gov.uk", "me.uk", "net.uk",
        "com.au", "net.au", "org.au", "edu.au", "gov.au",
        "co.jp", "ne.jp", "or.jp", "ac.jp", "go.jp",
        "com.bd", "org.bd", "net.bd", "edu.bd", "gov.bd", "ac.bd",
        "co.in", "org.in", "net.in", "gov.in", "ac.in",
        "com.br", "org.br", "net.br", "gov.br",
        "co.nz", "org.nz", "govt.nz",
        "com.sg", "org.sg", "edu.sg",
        "com.hk", "org.hk", "edu.hk",
        "co.za", "org.za", "gov.za",
        "com.mx", "com.tr", "com.cn", "com.tw", "co.kr",
        "github.io", "gitlab.io", "pages.dev", "vercel.app", "netlify.app",
        "herokuapp.com", "appspot.com", "firebaseapp.com"
    )

    private val IPV4_REGEX = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")

    fun parseOrigin(rawUrlOrDomain: String?): NormalizedOriginInfo? {
        val raw = rawUrlOrDomain?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val lower = raw.lowercase()
        if (lower.startsWith("javascript:") ||
            lower.startsWith("data:") ||
            lower.startsWith("file:") ||
            lower.startsWith("about:") ||
            lower.startsWith("orbit:")
        ) {
            return null
        }

        val withScheme = if (lower.startsWith("http://") || lower.startsWith("https://")) {
            raw
        } else if (!raw.contains("://")) {
            "https://$raw"
        } else {
            return null
        }

        val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null

        val rawHost = uri.host?.trim()?.trimEnd('.')?.lowercase() ?: return null
        if (rawHost.isBlank()) return null
        val asciiHost = runCatching { IDN.toASCII(rawHost).lowercase() }.getOrNull() ?: return null
        if (asciiHost.isBlank() || asciiHost.contains("..")) return null

        val port = uri.port
        val normalizedPort = when {
            port == -1 -> -1
            scheme == "https" && port == 443 -> -1
            scheme == "http" && port == 80 -> -1
            else -> port
        }
        val originKey = if (normalizedPort != -1) "$scheme://$asciiHost:$normalizedPort" else "$scheme://$asciiHost"

        return NormalizedOriginInfo(
            scheme = scheme,
            host = asciiHost,
            port = normalizedPort,
            registrableDomain = extractRegistrableDomain(asciiHost),
            originKey = originKey
        )
    }

    fun normalizeDomain(rawUrlOrDomain: String?): String {
        val info = parseOrigin(rawUrlOrDomain) ?: return ""
        return info.registrableDomain.removePrefix("www.")
    }

    fun normalizeHost(rawUrlOrDomain: String?): String {
        return parseOrigin(rawUrlOrDomain)?.host?.removePrefix("www.").orEmpty()
    }

    fun extractRegistrableDomain(asciiHost: String): String {
        val clean = asciiHost.trim().trimEnd('.').lowercase()
        if (clean.isBlank()) return ""
        if (clean == "localhost" || IPV4_REGEX.matches(clean) || clean.contains(":")) {
            return clean
        }
        val labels = clean.split('.').filter { it.isNotBlank() }
        if (labels.size <= 2) return labels.joinToString(".")

        val lastTwo = "${labels[labels.size - 2]}.${labels[labels.size - 1]}"
        return if (lastTwo in MULTI_PART_SUFFIXES && labels.size >= 3) {
            "${labels[labels.size - 3]}.$lastTwo"
        } else {
            lastTwo
        }
    }

    fun match(
        pageUrl: String,
        storedUrlOrDomain: String,
        allowSubdomains: Boolean = true,
        allowInsecureHttp: Boolean = false
    ): DomainMatchQuality {
        val pageInfo = parseOrigin(pageUrl) ?: return DomainMatchQuality.NONE
        val storedInfo = parseOrigin(storedUrlOrDomain) ?: return DomainMatchQuality.NONE

        val hostWithoutWwwPage = pageInfo.host.removePrefix("www.")
        val hostWithoutWwwStored = storedInfo.host.removePrefix("www.")

        val sameRegistrable = pageInfo.registrableDomain == storedInfo.registrableDomain
        val exactHost = hostWithoutWwwPage == hostWithoutWwwStored
        val allowedHostMatch = exactHost || (allowSubdomains && sameRegistrable && isSubdomainOrSame(pageInfo.host, storedInfo.registrableDomain))

        if (!allowedHostMatch) return DomainMatchQuality.NONE

        if (!pageInfo.isSecureHttps && !allowInsecureHttp && pageInfo.host != "localhost" && !IPV4_REGEX.matches(pageInfo.host)) {
            return DomainMatchQuality.BLOCKED_INSECURE_HTTP
        }

        return when {
            pageInfo.originKey == storedInfo.originKey -> DomainMatchQuality.EXACT_ORIGIN
            exactHost -> DomainMatchQuality.EXACT_HOST
            else -> DomainMatchQuality.REGISTRABLE_DOMAIN
        }
    }

    private fun isSubdomainOrSame(host: String, registrable: String): Boolean {
        return host == registrable || host.endsWith(".$registrable")
    }
}
