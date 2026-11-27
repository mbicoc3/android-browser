package com.example.feature.shield.engine

import com.example.core.webengine.BlockDecision
import com.example.core.webengine.ContentBlockerEngine
import com.example.core.webengine.WebResourceCategory
import com.example.feature.shield.model.ShieldEngineStats
import java.net.URI

private data class NetworkMatcherEntry(
    val rawRule: String,
    val listSource: String,
    val thirdPartyOnly: Boolean = false,
    val requiredCategory: WebResourceCategory? = null
)

private data class PathRuleEntry(
    val patternLower: String,
    val rawRule: String,
    val listSource: String,
    val thirdPartyOnly: Boolean = false
)

private data class CosmeticSelectorEntry(
    val selector: String,
    val excludedHosts: Set<String>
)

private data class CompiledShieldSnapshot(
    val blockDomains: Map<String, NetworkMatcherEntry> = emptyMap(),
    val exceptionDomains: Map<String, NetworkMatcherEntry> = emptyMap(),
    val blockPathRules: List<PathRuleEntry> = emptyList(),
    val globalCosmeticSelectors: List<CosmeticSelectorEntry> = emptyList(),
    val hostCosmeticSelectors: Map<String, List<CosmeticSelectorEntry>> = emptyMap(),
    val blockRuleCount: Int = 0,
    val exceptionRuleCount: Int = 0,
    val cosmeticRuleCount: Int = 0,
    val nativeRustActive: Boolean = false
)

/**
 * High-speed ABP / EasyPrivacy / AdGuard rule & CSS cosmetic filter engine.
 * Compiles rules into an immutable domain map snapshot for O(label-depth) sub-resource lookups
 * on WebView's background IO thread, with optional native `adblock-rust` JNI delegation.
 */
class OrbitFilterEngine : ContentBlockerEngine {

    @Volatile
    private var snapshot: CompiledShieldSnapshot = compileSources(
        sources = BuiltInFilterRules.defaultFilterLists(0L)
            .filter { it.enabled }
            .map { it.name to BuiltInFilterRules.seedRulesForList(it.id) },
        customRules = emptyList()
    )

    fun reloadRules(
        sources: List<Pair<String, List<String>>>,
        customRules: List<String>
    ) {
        val compiled = compileSources(sources, customRules)
        val combinedRaw = buildString {
            sources.forEach { (_, lines) ->
                lines.take(MAX_NATIVE_RULES_PER_LIST).forEach { appendLine(it) }
            }
            customRules.forEach { appendLine(it) }
        }
        val nativeActive = NativeAdblockBridge.rebuildEngine(combinedRaw)
        snapshot = compiled.copy(nativeRustActive = nativeActive)
    }

    fun getStats(): ShieldEngineStats {
        val current = snapshot
        return ShieldEngineStats(
            backendLabel = if (current.nativeRustActive) {
                "Brave adblock-rust (JNI)"
            } else {
                "OrbitFilterEngine (Kotlin ABP + Cosmetic)"
            },
            isNativeRustActive = current.nativeRustActive,
            networkBlockRuleCount = current.blockRuleCount,
            exceptionRuleCount = current.exceptionRuleCount,
            cosmeticRuleCount = current.cosmeticRuleCount
        )
    }

    override fun evaluateRequest(
        requestUrl: String,
        documentHost: String?,
        category: WebResourceCategory
    ): BlockDecision {
        if (category == WebResourceCategory.MAIN_FRAME) {
            return BlockDecision(shouldBlock = false)
        }
        val current = snapshot
        if (current.nativeRustActive) {
            val sourcePage = documentHost?.let { "https://$it/" }.orEmpty()
            val nativeBlocked = NativeAdblockBridge.checkNetworkUrl(
                url = requestUrl,
                sourceUrl = sourcePage,
                requestType = category.toNativeType()
            )
            if (nativeBlocked != null) {
                return BlockDecision(
                    shouldBlock = nativeBlocked,
                    matchedRule = if (nativeBlocked) "adblock-rust" else null,
                    listSource = if (nativeBlocked) "adblock-rust" else null
                )
            }
        }

        val requestHost = extractHostLower(requestUrl) ?: return BlockDecision(shouldBlock = false)
        val normalizedDocHost = documentHost?.lowercase()?.removePrefix("www.")
        val isThirdParty = normalizedDocHost != null &&
            baseDomain(requestHost) != baseDomain(normalizedDocHost)

        val exceptionMatch = findMatchingDomain(requestHost, current.exceptionDomains, isThirdParty, category)
        if (exceptionMatch != null) {
            return BlockDecision(
                shouldBlock = false,
                matchedRule = exceptionMatch.rawRule,
                listSource = exceptionMatch.listSource
            )
        }

        val domainMatch = findMatchingDomain(requestHost, current.blockDomains, isThirdParty, category)
        if (domainMatch != null) {
            return BlockDecision(
                shouldBlock = true,
                matchedRule = domainMatch.rawRule,
                listSource = domainMatch.listSource
            )
        }

        if (current.blockPathRules.isNotEmpty()) {
            val lowerUrl = requestUrl.lowercase()
            val pathMatch = current.blockPathRules.firstOrNull { rule ->
                (!rule.thirdPartyOnly || isThirdParty) && lowerUrl.contains(rule.patternLower)
            }
            if (pathMatch != null) {
                return BlockDecision(
                    shouldBlock = true,
                    matchedRule = pathMatch.rawRule,
                    listSource = pathMatch.listSource
                )
            }
        }

        return BlockDecision(shouldBlock = false)
    }

    override fun cosmeticCssForHost(host: String?): String? {
        val normalizedHost = host?.lowercase()?.trim()?.removePrefix("www.") ?: return null
        if (normalizedHost.isBlank()) return null

        val current = snapshot
        if (current.nativeRustActive) {
            NativeAdblockBridge.cosmeticCssForUrl("https://$normalizedHost/")?.let { return it }
        }

        val selectors = linkedSetOf<String>()
        current.globalCosmeticSelectors.forEach { entry ->
            if (!isHostExcluded(normalizedHost, entry.excludedHosts)) {
                selectors.add(entry.selector)
            }
        }
        hostChain(normalizedHost).forEach { candidateHost ->
            current.hostCosmeticSelectors[candidateHost]?.forEach { entry ->
                if (!isHostExcluded(normalizedHost, entry.excludedHosts)) {
                    selectors.add(entry.selector)
                }
            }
        }
        if (selectors.isEmpty()) return null
        val capped = selectors.take(MAX_SELECTORS_PER_PAGE)
        return capped.joinToString(separator = ", ") +
            " { display: none !important; visibility: hidden !important; }"
    }

    private fun findMatchingDomain(
        host: String,
        table: Map<String, NetworkMatcherEntry>,
        isThirdParty: Boolean,
        category: WebResourceCategory
    ): NetworkMatcherEntry? {
        if (table.isEmpty()) return null
        for (candidate in hostChain(host)) {
            val match = table[candidate] ?: continue
            if (match.thirdPartyOnly && !isThirdParty) continue
            if (match.requiredCategory != null && match.requiredCategory != category) continue
            return match
        }
        return null
    }

    private fun isHostExcluded(host: String, excludedHosts: Set<String>): Boolean {
        if (excludedHosts.isEmpty()) return false
        return hostChain(host).any { it in excludedHosts }
    }

    private fun compileSources(
        sources: List<Pair<String, List<String>>>,
        customRules: List<String>
    ): CompiledShieldSnapshot {
        val blockDomains = HashMap<String, NetworkMatcherEntry>(1024)
        val exceptionDomains = HashMap<String, NetworkMatcherEntry>(128)
        val blockPaths = ArrayList<PathRuleEntry>()
        val globalCosmetic = ArrayList<CosmeticSelectorEntry>()
        val hostCosmetic = HashMap<String, MutableList<CosmeticSelectorEntry>>()

        fun ingest(sourceName: String, lines: List<String>) {
            lines.forEach { rawLine ->
                val line = rawLine.trim()
                if (line.isEmpty() ||
                    line.startsWith("!") ||
                    line.startsWith("[Adblock", ignoreCase = true) ||
                    line.startsWith("# ") ||
                    line == "#"
                ) {
                    return@forEach
                }
                if (line.contains("#?#") || line.contains("#$#") || line.contains("#@#") || line.contains("+js(")) {
                    return@forEach
                }
                val cosmeticIdx = line.indexOf("##")
                if (cosmeticIdx >= 0) {
                    val domainPart = line.substring(0, cosmeticIdx).trim()
                    val selector = line.substring(cosmeticIdx + 2).trim()
                    if (isSafeCssSelector(selector)) {
                        parseCosmeticRule(domainPart, selector, globalCosmetic, hostCosmetic)
                    }
                    return@forEach
                }

                val isException = line.startsWith("@@")
                val body = if (isException) line.removePrefix("@@") else line
                val dollarIdx = body.indexOf('$')
                val patternPart = if (dollarIdx >= 0) body.substring(0, dollarIdx).trim() else body.trim()
                val optionsPart = if (dollarIdx >= 0) body.substring(dollarIdx + 1).lowercase() else ""

                val thirdPartyOnly = optionsPart.split(',').any { it.trim() == "third-party" || it.trim() == "3p" }
                val categoryHint = parseCategoryHint(optionsPart)

                val domainCandidate = extractDomainCandidate(patternPart)
                if (domainCandidate != null) {
                    val entry = NetworkMatcherEntry(
                        rawRule = line,
                        listSource = sourceName,
                        thirdPartyOnly = thirdPartyOnly,
                        requiredCategory = categoryHint
                    )
                    if (isException) {
                        exceptionDomains[domainCandidate] = entry
                    } else {
                        blockDomains.putIfAbsent(domainCandidate, entry)
                    }
                } else if (!isException && patternPart.length >= 6 && !patternPart.startsWith("/")) {
                    val cleanPattern = patternPart.trim('*').lowercase()
                    if (cleanPattern.length >= 6 && !cleanPattern.contains('*')) {
                        blockPaths.add(
                            PathRuleEntry(
                                patternLower = cleanPattern,
                                rawRule = line,
                                listSource = sourceName,
                                thirdPartyOnly = thirdPartyOnly
                            )
                        )
                    }
                }
            }
        }

        sources.forEach { (name, lines) -> ingest(name, lines) }
        if (customRules.isNotEmpty()) {
            ingest("Custom Rules", customRules)
        }

        val totalCosmetic = globalCosmetic.size + hostCosmetic.values.sumOf { it.size }
        return CompiledShieldSnapshot(
            blockDomains = blockDomains,
            exceptionDomains = exceptionDomains,
            blockPathRules = blockPaths.take(MAX_PATH_FALLBACK_RULES),
            globalCosmeticSelectors = globalCosmetic,
            hostCosmeticSelectors = hostCosmetic,
            blockRuleCount = blockDomains.size + blockPaths.size.coerceAtMost(MAX_PATH_FALLBACK_RULES),
            exceptionRuleCount = exceptionDomains.size,
            cosmeticRuleCount = totalCosmetic,
            nativeRustActive = false
        )
    }

    private fun parseCosmeticRule(
        domainPart: String,
        selector: String,
        globalCosmetic: MutableList<CosmeticSelectorEntry>,
        hostCosmetic: MutableMap<String, MutableList<CosmeticSelectorEntry>>
    ) {
        if (domainPart.isEmpty()) {
            globalCosmetic.add(CosmeticSelectorEntry(selector = selector, excludedHosts = emptySet()))
            return
        }
        val tokens = domainPart.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        val excluded = tokens.filter { it.startsWith("~") }.map { it.removePrefix("~") }.toSet()
        val included = tokens.filterNot { it.startsWith("~") }
        val entry = CosmeticSelectorEntry(selector = selector, excludedHosts = excluded)
        if (included.isEmpty()) {
            globalCosmetic.add(entry)
        } else {
            included.forEach { domain ->
                hostCosmetic.getOrPut(domain) { ArrayList() }.add(entry)
            }
        }
    }

    private fun parseCategoryHint(options: String): WebResourceCategory? {
        if (options.isBlank()) return null
        val parts = options.split(',').map { it.trim() }
        return when {
            "script" in parts -> WebResourceCategory.SCRIPT
            "image" in parts -> WebResourceCategory.IMAGE
            "stylesheet" in parts -> WebResourceCategory.STYLESHEET
            "xmlhttprequest" in parts || "xhr" in parts -> WebResourceCategory.XHR_FETCH
            else -> null
        }
    }

    private fun extractDomainCandidate(pattern: String): String? {
        var clean = pattern.trim()
        if (clean.startsWith("0.0.0.0 ") || clean.startsWith("127.0.0.1 ")) {
            clean = clean.substringAfter(' ').trim()
        }
        if (clean.startsWith("||")) {
            clean = clean.removePrefix("||")
        } else if (clean.startsWith("https://") || clean.startsWith("http://")) {
            clean = clean.substringAfter("://")
        }
        clean = clean.removePrefix("www.")
        val isPureDomainAnchor = clean.endsWith("^") || (!clean.contains('/') && !clean.contains('*'))
        if (!isPureDomainAnchor) return null

        val domain = clean.trimEnd('^', '/').lowercase()
        if (domain.isBlank() ||
            domain.contains('*') ||
            domain.contains('/') ||
            domain.contains(' ') ||
            !domain.contains('.')
        ) {
            return null
        }
        return domain
    }

    private fun isSafeCssSelector(selector: String): Boolean {
        if (selector.isBlank() || selector.length > 180) return false
        return !selector.contains('{') &&
            !selector.contains('}') &&
            !selector.contains("</style", ignoreCase = true)
    }

    private fun extractHostLower(url: String): String? = runCatching {
        URI(url.trim()).host?.lowercase()?.removePrefix("www.")?.takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun hostChain(host: String): List<String> {
        val parts = host.split('.')
        if (parts.size <= 1) return listOf(host)
        val result = ArrayList<String>(parts.size)
        for (i in 0 until parts.size - 1) {
            result.add(parts.subList(i, parts.size).joinToString("."))
        }
        return result
    }

    private fun baseDomain(host: String): String {
        val parts = host.split('.')
        return if (parts.size >= 2) parts.takeLast(2).joinToString(".") else host
    }

    private fun WebResourceCategory.toNativeType(): String = when (this) {
        WebResourceCategory.MAIN_FRAME -> "document"
        WebResourceCategory.SCRIPT -> "script"
        WebResourceCategory.IMAGE -> "image"
        WebResourceCategory.STYLESHEET -> "stylesheet"
        WebResourceCategory.XHR_FETCH -> "xmlhttprequest"
        WebResourceCategory.OTHER -> "other"
    }

    companion object {
        private const val MAX_SELECTORS_PER_PAGE = 160
        private const val MAX_PATH_FALLBACK_RULES = 250
        private const val MAX_NATIVE_RULES_PER_LIST = 15_000
    }
}
