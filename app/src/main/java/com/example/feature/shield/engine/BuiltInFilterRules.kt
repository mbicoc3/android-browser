package com.example.feature.shield.engine

import com.example.core.database.entity.FilterListEntity

/**
 * Default free & open-source tracking/ad filter lists (EasyPrivacy, AdGuard, Peter Lowe's)
 * plus compact built-in offline seed rules so protection works immediately before first sync.
 */
object BuiltInFilterRules {

    const val LIST_EASYPRIVACY = "easyprivacy"
    const val LIST_ADGUARD_TRACKING = "adguard_tracking"
    const val LIST_ADGUARD_MOBILE = "adguard_mobile"
    const val LIST_PETER_LOWE = "peter_lowe"

    fun defaultFilterLists(now: Long): List<FilterListEntity> = listOf(
        FilterListEntity(
            id = LIST_EASYPRIVACY,
            name = "EasyPrivacy (Tracking Protection)",
            sourceUrl = "https://easylist.to/easylist/easyprivacy.txt",
            category = "Trackers",
            enabled = true,
            ruleCount = easyPrivacySeedRules.size,
            cosmeticRuleCount = 0,
            lastUpdatedAt = now,
            isBuiltIn = true
        ),
        FilterListEntity(
            id = LIST_ADGUARD_TRACKING,
            name = "AdGuard Tracking Protection",
            sourceUrl = "https://filters.adtidy.org/android/filters/3.txt",
            category = "Trackers & Telemetry",
            enabled = true,
            ruleCount = adguardTrackingSeedRules.size,
            cosmeticRuleCount = 0,
            lastUpdatedAt = now,
            isBuiltIn = true
        ),
        FilterListEntity(
            id = LIST_ADGUARD_MOBILE,
            name = "AdGuard Mobile Ads + Cosmetic",
            sourceUrl = "https://filters.adtidy.org/android/filters/11.txt",
            category = "Ads & Cosmetic",
            enabled = true,
            ruleCount = adguardMobileNetworkSeedRules.size,
            cosmeticRuleCount = cosmeticSeedRules.size,
            lastUpdatedAt = now,
            isBuiltIn = true
        ),
        FilterListEntity(
            id = LIST_PETER_LOWE,
            name = "Peter Lowe's Ad & Tracker List",
            sourceUrl = "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=adblockplus&showintro=0",
            category = "Ad Servers",
            enabled = false,
            ruleCount = peterLoweSeedRules.size,
            cosmeticRuleCount = 0,
            lastUpdatedAt = now,
            isBuiltIn = true
        )
    )

    fun seedRulesForList(listId: String): List<String> = when (listId) {
        LIST_EASYPRIVACY -> easyPrivacySeedRules
        LIST_ADGUARD_TRACKING -> adguardTrackingSeedRules
        LIST_ADGUARD_MOBILE -> adguardMobileNetworkSeedRules + cosmeticSeedRules
        LIST_PETER_LOWE -> peterLoweSeedRules
        else -> emptyList()
    }

    private val easyPrivacySeedRules = listOf(
        "||google-analytics.com^",
        "||ssl.google-analytics.com^",
        "||googletagmanager.com^",
        "||googletagservices.com^",
        "||scorecardresearch.com^",
        "||quantserve.com^",
        "||hotjar.com^",
        "||mixpanel.com^",
        "||amplitude.com^",
        "||segment.io^",
        "||segment.com^",
        "||newrelic.com^",
        "||nr-data.net^",
        "||chartbeat.com^",
        "||mouseflow.com^",
        "||fullstory.com^",
        "||clarity.ms^",
        "||omtrdc.net^",
        "||demdex.net^",
        "||bluekai.com^"
    )

    private val adguardTrackingSeedRules = listOf(
        "||doubleclick.net^",
        "||adservice.google.com^",
        "||pagead2.googlesyndication.com^",
        "||googlesyndication.com^",
        "||connect.facebook.net^",
        "||pixel.facebook.com^",
        "||analytics.tiktok.com^",
        "||ads-twitter.com^",
        "||analytics.twitter.com^",
        "||ads.linkedin.com^",
        "||snapads.com^",
        "||app-measurement.com^",
        "||branch.io^",
        "||adjust.com^",
        "||appsflyer.com^",
        "||bugsnag.com^",
        "||sentry-cdn.com^"
    )

    private val adguardMobileNetworkSeedRules = listOf(
        "||taboola.com^",
        "||outbrain.com^",
        "||criteo.com^",
        "||pubmatic.com^",
        "||rubiconproject.com^",
        "||openx.net^",
        "||adnxs.com^",
        "||casalemedia.com^",
        "||media.net^",
        "||mgid.com^",
        "||revcontent.com^",
        "||amazon-adsystem.com^",
        "||moatads.com^",
        "||yieldmo.com^",
        "||33across.com^"
    )

    private val peterLoweSeedRules = listOf(
        "||adform.net^",
        "||adroll.com^",
        "||advertising.com^",
        "||bidswitch.net^",
        "||chartboost.com^",
        "||clicktale.net^",
        "||exoclick.com^",
        "||flurry.com^",
        "||inmobi.com^",
        "||mathtag.com^",
        "||smartadserver.com^",
        "||zedo.com^"
    )

    private val cosmeticSeedRules = listOf(
        "##.adsbygoogle",
        "##ins.adsbygoogle",
        "##.ad-banner",
        "##.ad-container",
        "##.ad-wrapper",
        "##.sponsored-content",
        "##[data-ad-slot]",
        "##[data-testid=\"ad-slot\"]",
        "##div[id^=\"div-gpt-ad\"]",
        "##.taboola-widget",
        "##.outbrain-widget"
    )
}
