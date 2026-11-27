package com.example.core.network

/**
 * Free, zero-cost DNS-over-HTTPS (DoH) providers for Orbit Browser's OkHttp stack,
 * paired with matching Android Private DNS (DNS-over-TLS) hostnames for WebView browsing.
 */
enum class DohProvider(
    val id: String,
    val displayName: String,
    val subtitle: String,
    val dohUrl: String,
    val privateDnsHostname: String,
    val bootstrapIps: List<String>
) {
    CLOUDFLARE(
        id = "cloudflare",
        displayName = "Cloudflare (1.1.1.1)",
        subtitle = "Ultra-fast no-logs DoH · 1.1.1.1",
        dohUrl = "https://cloudflare-dns.com/dns-query",
        privateDnsHostname = "one.one.one.one",
        bootstrapIps = listOf("1.1.1.1", "1.0.0.1")
    ),
    QUAD9(
        id = "quad9",
        displayName = "Quad9 (9.9.9.9)",
        subtitle = "Security & phishing-blocking DoH · 9.9.9.9",
        dohUrl = "https://dns.quad9.net/dns-query",
        privateDnsHostname = "dns.quad9.net",
        bootstrapIps = listOf("9.9.9.9", "149.112.112.112")
    ),
    ADGUARD(
        id = "adguard",
        displayName = "AdGuard DNS",
        subtitle = "Ad, tracker & telemetry-blocking DoH",
        dohUrl = "https://dns.adguard-dns.com/dns-query",
        privateDnsHostname = "dns.adguard-dns.com",
        bootstrapIps = listOf("94.140.14.14", "94.140.15.15")
    ),
    SYSTEM(
        id = "system",
        displayName = "System Default DNS",
        subtitle = "Follow Android OS / Private DNS settings",
        dohUrl = "",
        privateDnsHostname = "",
        bootstrapIps = emptyList()
    );

    companion object {
        fun fromId(id: String?): DohProvider =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: CLOUDFLARE
    }
}
