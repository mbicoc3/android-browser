package com.example.feature.sitedata.engine

import android.net.Uri
import android.webkit.CookieManager
import androidx.webkit.CookieManagerCompat
import androidx.webkit.WebViewFeature
import com.example.core.common.UrlUtils
import com.example.feature.sitedata.model.CookieEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Dedicated Android CookieManager + AndroidX CookieManagerCompat wrapper supporting
 * per-origin cookie inspection (including Domain, Path, Secure, HttpOnly, SameSite attributes)
 * and surgical cookie expiry/removal.
 */
object WebViewCookieStore {

    suspend fun inspectCookies(origin: String): List<CookieEntry> = withContext(Dispatchers.Main) {
        val cookieManager = CookieManager.getInstance()
        val candidateUrls = UrlUtils.cookieCandidateUrls(origin)
        val entries = linkedMapOf<String, CookieEntry>()

        if (WebViewFeature.isFeatureSupported(WebViewFeature.GET_COOKIE_INFO)) {
            candidateUrls.forEach { targetUrl ->
                runCatching {
                    CookieManagerCompat.getCookieInfo(cookieManager, targetUrl)
                }.getOrDefault(emptyList()).forEach { rawLine ->
                    parseSetCookieLine(rawLine, defaultDomain = UrlUtils.extractHost(origin))?.let { entry ->
                        val dedupeKey = "${entry.name}|${entry.domain}|${entry.path}"
                        entries[dedupeKey] = entry
                    }
                }
            }
        }

        if (entries.isEmpty()) {
            candidateUrls.forEach { targetUrl ->
                val raw = runCatching { cookieManager.getCookie(targetUrl) }.getOrNull()
                if (!raw.isNullOrBlank()) {
                    raw.split(";").forEach { pair ->
                        val idx = pair.indexOf('=')
                        if (idx > 0) {
                            val name = pair.substring(0, idx).trim()
                            val value = pair.substring(idx + 1).trim()
                            if (name.isNotEmpty()) {
                                val domain = UrlUtils.extractHost(targetUrl)
                                val dedupeKey = "$name|$domain|/"
                                entries.putIfAbsent(
                                    dedupeKey,
                                    CookieEntry(
                                        name = name,
                                        valuePreview = value.take(64).let { if (value.length > 64) "$it…" else it },
                                        domain = domain,
                                        path = "/",
                                        isSecure = origin.startsWith("https://"),
                                        isHttpOnly = false
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        entries.values.sortedBy { it.name.lowercase() }
    }

    suspend fun countCookiesQuick(origin: String): Int = withContext(Dispatchers.Main) {
        val cookieManager = CookieManager.getInstance()
        val names = linkedSetOf<String>()
        UrlUtils.cookieCandidateUrls(origin).forEach { targetUrl ->
            val raw = runCatching { cookieManager.getCookie(targetUrl) }.getOrNull()
            if (!raw.isNullOrBlank()) {
                raw.split(";").forEach { part ->
                    val name = part.substringBefore('=').trim()
                    if (name.isNotEmpty()) names.add(name)
                }
            }
        }
        names.size
    }

    suspend fun clearCookiesForSite(origin: String, singleCookieName: String? = null) {
        val cookies = inspectCookies(origin)
        withContext(Dispatchers.Main) {
            val cookieManager = CookieManager.getInstance()
            val candidateUrls = UrlUtils.cookieCandidateUrls(origin)
            val originHost = Uri.parse(origin).host.orEmpty()
            val targetCookies = if (singleCookieName != null) {
                cookies.filter { it.name == singleCookieName }
            } else {
                cookies
            }

            if (targetCookies.isEmpty() && singleCookieName != null) {
                expireCookieAcrossTargets(cookieManager, candidateUrls, singleCookieName, originHost, "/")
            } else {
                targetCookies.forEach { cookie ->
                    val path = cookie.path.ifBlank { "/" }
                    expireCookieAcrossTargets(cookieManager, candidateUrls, cookie.name, cookie.domain, path)
                    if (path != "/") {
                        expireCookieAcrossTargets(cookieManager, candidateUrls, cookie.name, cookie.domain, "/")
                    }
                    if (originHost.isNotBlank() && originHost != cookie.domain) {
                        expireCookieAcrossTargets(cookieManager, candidateUrls, cookie.name, originHost, path)
                    }
                }
            }
            cookieManager.flush()
        }
    }

    suspend fun clearAllCookies() = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine<Unit> { cont ->
            val cm = CookieManager.getInstance()
            cm.removeAllCookies {
                cm.flush()
                if (cont.isActive) cont.resume(Unit)
            }
        }
    }

    private fun expireCookieAcrossTargets(
        cookieManager: CookieManager,
        candidateUrls: List<String>,
        cookieName: String,
        domain: String,
        path: String
    ) {
        val cleanDomain = domain.trimStart('.')
        val domainVariants = linkedSetOf("", cleanDomain, ".$cleanDomain").filterIndexed { idx, d ->
            idx == 0 || d.isNotBlank()
        }
        val baseExpire = "$cookieName=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=$path"
        candidateUrls.forEach { url ->
            cookieManager.setCookie(url, baseExpire)
            if (url.startsWith("https://")) {
                cookieManager.setCookie(url, "$baseExpire; Secure")
            }
            domainVariants.forEach { dom ->
                if (dom.isNotEmpty()) {
                    cookieManager.setCookie(url, "$baseExpire; Domain=$dom")
                    if (url.startsWith("https://")) {
                        cookieManager.setCookie(url, "$baseExpire; Domain=$dom; Secure")
                    }
                }
            }
        }
    }

    private fun parseSetCookieLine(rawLine: String, defaultDomain: String): CookieEntry? {
        val parts = rawLine.split(";")
        if (parts.isEmpty()) return null
        val nameValue = parts.first()
        val eqIdx = nameValue.indexOf('=')
        if (eqIdx <= 0) return null
        val name = nameValue.substring(0, eqIdx).trim()
        val value = nameValue.substring(eqIdx + 1).trim()
        if (name.isEmpty()) return null

        var domain = defaultDomain
        var path = "/"
        var isSecure = false
        var isHttpOnly = false
        var sameSite: String? = null

        for (i in 1 until parts.size) {
            val attr = parts[i].trim()
            val attrEq = attr.indexOf('=')
            val attrName = (if (attrEq >= 0) attr.substring(0, attrEq) else attr).trim().lowercase()
            val attrVal = if (attrEq >= 0) attr.substring(attrEq + 1).trim() else ""
            when (attrName) {
                "domain" -> if (attrVal.isNotBlank()) domain = attrVal
                "path" -> if (attrVal.isNotBlank()) path = attrVal
                "secure" -> isSecure = true
                "httponly" -> isHttpOnly = true
                "samesite" -> if (attrVal.isNotBlank()) sameSite = attrVal
            }
        }

        return CookieEntry(
            name = name,
            valuePreview = value.take(64).let { if (value.length > 64) "$it…" else it },
            domain = domain,
            path = path,
            isSecure = isSecure,
            isHttpOnly = isHttpOnly,
            sameSite = sameSite,
            rawAttributeLine = rawLine
        )
    }
}
