package com.example.core.network

import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.URI
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Pure RFC 8484 DNS-over-HTTPS (DoH) resolver implementing OkHttp [Dns].
 * Uses static bootstrap IPs to connect to Cloudflare, Quad9, or AdGuard DoH endpoints over HTTPS
 * so zero plaintext UDP DNS queries leak for app-level OkHttp requests.
 */
class OrbitDohDns(
    initialProvider: DohProvider = DohProvider.CLOUDFLARE,
    private val systemFallback: Dns = Dns.SYSTEM
) : Dns {

    private data class CachedResolution(
        val addresses: List<InetAddress>,
        val expiresAtMs: Long
    )

    @Volatile
    var currentProvider: DohProvider = initialProvider
        private set

    private val cache = ConcurrentHashMap<String, CachedResolution>()
    private val txIdCounter = AtomicInteger(1)

    private val bootstrapDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val clean = hostname.trim().lowercase()
            val match = DohProvider.entries.firstOrNull { provider ->
                provider.dohUrl.isNotBlank() &&
                    runCatching { URI(provider.dohUrl).host?.lowercase() }.getOrNull() == clean
            }
            return if (match != null && match.bootstrapIps.isNotEmpty()) {
                match.bootstrapIps.mapNotNull { ip ->
                    runCatching { InetAddress.getByName(ip) }.getOrNull()
                }.ifEmpty { systemFallback.lookup(hostname) }
            } else {
                systemFallback.lookup(hostname)
            }
        }
    }

    private val dohHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(bootstrapDns)
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .writeTimeout(6, TimeUnit.SECONDS)
            .build()
    }

    fun setProvider(provider: DohProvider) {
        if (currentProvider != provider) {
            currentProvider = provider
            cache.clear()
        }
    }

    override fun lookup(hostname: String): List<InetAddress> {
        val clean = hostname.trim()
        if (clean.isEmpty()) throw UnknownHostException("Empty hostname")

        if (isIpLiteral(clean)) {
            return listOf(InetAddress.getByName(clean))
        }

        val provider = currentProvider
        if (provider == DohProvider.SYSTEM || provider.dohUrl.isBlank()) {
            return systemFallback.lookup(clean)
        }

        val cacheKey = "${provider.id}:${clean.lowercase()}"
        val now = System.currentTimeMillis()
        cache[cacheKey]?.let { entry ->
            if (entry.expiresAtMs > now && entry.addresses.isNotEmpty()) {
                return entry.addresses
            }
        }

        val dohResult = runCatching { queryDohRecords(provider, clean) }.getOrNull()
        if (dohResult != null && dohResult.first.isNotEmpty()) {
            val (addresses, ttlSec) = dohResult
            if (cache.size > MAX_CACHE_ENTRIES) {
                cache.clear()
            }
            cache[cacheKey] = CachedResolution(
                addresses = addresses,
                expiresAtMs = now + (ttlSec.coerceIn(MIN_TTL_SEC, MAX_TTL_SEC) * 1_000L)
            )
            return addresses
        }

        return systemFallback.lookup(clean)
    }

    fun testProvider(provider: DohProvider, testHost: String = "example.com"): Result<List<String>> {
        return runCatching {
            if (provider == DohProvider.SYSTEM) {
                systemFallback.lookup(testHost).mapNotNull { it.hostAddress }
            } else {
                val (addrs, _) = queryDohRecords(provider, testHost)
                require(addrs.isNotEmpty()) { "No DNS records returned by ${provider.displayName}" }
                addrs.mapNotNull { it.hostAddress }
            }
        }
    }

    private fun queryDohRecords(provider: DohProvider, hostname: String): Pair<List<InetAddress>, Long> {
        val v4Result = runCatching { querySingleType(provider.dohUrl, hostname, TYPE_A) }.getOrNull()
        val v6Result = runCatching { querySingleType(provider.dohUrl, hostname, TYPE_AAAA) }.getOrNull()

        val combined = LinkedHashSet<InetAddress>()
        v4Result?.first?.let { combined.addAll(it) }
        v6Result?.first?.let { combined.addAll(it) }

        val minTtl = listOfNotNull(v4Result?.second, v6Result?.second).minOrNull() ?: DEFAULT_TTL_SEC
        return combined.toList() to minTtl
    }

    private fun querySingleType(dohUrl: String, hostname: String, qType: Int): Pair<List<InetAddress>, Long> {
        val wireQuery = buildDnsQueryPacket(
            transactionId = txIdCounter.incrementAndGet() and 0xFFFF,
            hostname = hostname,
            qType = qType
        )
        val request = Request.Builder()
            .url(dohUrl)
            .header("Accept", DOH_MEDIA_TYPE_STR)
            .post(wireQuery.toRequestBody(DOH_MEDIA_TYPE))
            .build()

        dohHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("DoH HTTP ${response.code}")
            }
            val bytes = response.body?.bytes() ?: error("Empty DoH response")
            return parseDnsResponsePacket(bytes)
        }
    }

    companion object {
        const val TYPE_A = 1
        const val TYPE_AAAA = 28
        private const val DOH_MEDIA_TYPE_STR = "application/dns-message"
        private val DOH_MEDIA_TYPE = DOH_MEDIA_TYPE_STR.toMediaType()
        private const val MAX_CACHE_ENTRIES = 256
        private const val MIN_TTL_SEC = 60L
        private const val DEFAULT_TTL_SEC = 300L
        private const val MAX_TTL_SEC = 3600L

        internal fun isIpLiteral(host: String): Boolean {
            val ipv4Regex = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")
            return ipv4Regex.matches(host) || host.contains(':')
        }

        /**
         * Encodes a standard RFC 1035 DNS query packet (Header + Question section).
         */
        internal fun buildDnsQueryPacket(
            transactionId: Int,
            hostname: String,
            qType: Int = TYPE_A
        ): ByteArray {
            val out = ByteArrayOutputStream(64)
            // Header (12 bytes)
            out.write((transactionId ushr 8) and 0xFF)
            out.write(transactionId and 0xFF)
            out.write(0x01) // Flags: Standard query, RD = 1
            out.write(0x00)
            out.write(0x00); out.write(0x01) // QDCOUNT = 1
            out.write(0x00); out.write(0x00) // ANCOUNT = 0
            out.write(0x00); out.write(0x00) // NSCOUNT = 0
            out.write(0x00); out.write(0x00) // ARCOUNT = 0

            val cleanHost = hostname.trim().trimEnd('.')
            cleanHost.split('.').forEach { label ->
                if (label.isNotEmpty()) {
                    val ascii = java.net.IDN.toASCII(label).toByteArray(Charsets.US_ASCII)
                    require(ascii.size <= 63) { "DNS label too long" }
                    out.write(ascii.size and 0x3F)
                    out.write(ascii)
                }
            }
            out.write(0x00) // Root terminator

            // QTYPE
            out.write((qType ushr 8) and 0xFF)
            out.write(qType and 0xFF)
            // QCLASS = IN (1)
            out.write(0x00)
            out.write(0x01)
            return out.toByteArray()
        }

        /**
         * Parses A (IPv4) and AAAA (IPv6) answer records from an RFC 1035 DNS wire response.
         */
        internal fun parseDnsResponsePacket(packet: ByteArray): Pair<List<InetAddress>, Long> {
            if (packet.size < 12) return emptyList<InetAddress>() to DEFAULT_TTL_SEC
            val rcode = packet[3].toInt() and 0x0F
            if (rcode != 0) return emptyList<InetAddress>() to DEFAULT_TTL_SEC

            val qdCount = ((packet[4].toInt() and 0xFF) shl 8) or (packet[5].toInt() and 0xFF)
            val anCount = ((packet[6].toInt() and 0xFF) shl 8) or (packet[7].toInt() and 0xFF)
            var offset = 12

            repeat(qdCount) {
                offset = skipDnsName(packet, offset)
                if (offset + 4 > packet.size) return emptyList<InetAddress>() to DEFAULT_TTL_SEC
                offset += 4 // QTYPE + QCLASS
            }

            val addresses = ArrayList<InetAddress>(anCount)
            var minTtl = Long.MAX_VALUE

            repeat(anCount) {
                offset = skipDnsName(packet, offset)
                if (offset + 10 > packet.size) return addresses to normalizeTtl(minTtl)

                val rrType = ((packet[offset].toInt() and 0xFF) shl 8) or (packet[offset + 1].toInt() and 0xFF)
                val rrClass = ((packet[offset + 2].toInt() and 0xFF) shl 8) or (packet[offset + 3].toInt() and 0xFF)
                val ttl = ((packet[offset + 4].toLong() and 0xFFL) shl 24) or
                    ((packet[offset + 5].toLong() and 0xFFL) shl 16) or
                    ((packet[offset + 6].toLong() and 0xFFL) shl 8) or
                    (packet[offset + 7].toLong() and 0xFFL)
                val rdLen = ((packet[offset + 8].toInt() and 0xFF) shl 8) or (packet[offset + 9].toInt() and 0xFF)
                offset += 10

                if (offset + rdLen > packet.size) return addresses to normalizeTtl(minTtl)

                if (rrClass == 1 && ((rrType == TYPE_A && rdLen == 4) || (rrType == TYPE_AAAA && rdLen == 16))) {
                    val rawIp = packet.copyOfRange(offset, offset + rdLen)
                    runCatching { InetAddress.getByAddress(rawIp) }.getOrNull()?.let { addr ->
                        addresses += addr
                        if (ttl in 1..minTtl) minTtl = ttl
                    }
                }
                offset += rdLen
            }

            return addresses to normalizeTtl(minTtl)
        }

        private fun normalizeTtl(raw: Long): Long =
            if (raw == Long.MAX_VALUE) DEFAULT_TTL_SEC else raw.coerceIn(MIN_TTL_SEC, MAX_TTL_SEC)

        private fun skipDnsName(packet: ByteArray, startOffset: Int): Int {
            var pos = startOffset
            var safety = 0
            while (pos < packet.size && safety++ < 128) {
                val len = packet[pos].toInt() and 0xFF
                if (len == 0) return pos + 1
                if ((len and 0xC0) == 0xC0) return pos + 2
                pos += 1 + len
            }
            return packet.size
        }
    }
}
