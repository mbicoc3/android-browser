package com.example

import com.example.core.network.DohProvider
import com.example.core.network.OrbitDohDns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class OrbitDohDnsTest {

    @Test
    fun dohProviderDefaultsAndLookupWorkCorrectly() {
        assertEquals("https://cloudflare-dns.com/dns-query", DohProvider.CLOUDFLARE.dohUrl)
        assertEquals("one.one.one.one", DohProvider.CLOUDFLARE.privateDnsHostname)
        assertEquals("https://dns.quad9.net/dns-query", DohProvider.QUAD9.dohUrl)
        assertEquals("https://dns.adguard-dns.com/dns-query", DohProvider.ADGUARD.dohUrl)
        assertEquals(DohProvider.ADGUARD, DohProvider.fromId("adguard"))
        assertEquals(DohProvider.CLOUDFLARE, DohProvider.fromId("unknown"))
    }

    @Test
    fun buildDnsQueryPacket_encodesStandardWireQuery() {
        val packet = OrbitDohDns.buildDnsQueryPacket(
            transactionId = 0x1234,
            hostname = "api.github.com",
            qType = OrbitDohDns.TYPE_A
        )
        assertEquals(0x12, packet[0].toInt() and 0xFF)
        assertEquals(0x34, packet[1].toInt() and 0xFF)
        assertEquals(1, packet[5].toInt() and 0xFF) // QDCOUNT = 1
        // First label "api"
        assertEquals(3, packet[12].toInt() and 0xFF)
        assertEquals('a'.code, packet[13].toInt() and 0xFF)
        // Last 4 bytes are QTYPE=1 (0x00, 0x01) and QCLASS=1 (0x00, 0x01)
        assertEquals(0x00, packet[packet.size - 4].toInt() and 0xFF)
        assertEquals(0x01, packet[packet.size - 3].toInt() and 0xFF)
        assertEquals(0x00, packet[packet.size - 2].toInt() and 0xFF)
        assertEquals(0x01, packet[packet.size - 1].toInt() and 0xFF)
    }

    @Test
    fun parseDnsResponsePacket_extractsIpv4AndIpv6WithPointerCompression() {
        val query = OrbitDohDns.buildDnsQueryPacket(0xABCD, "example.com", OrbitDohDns.TYPE_A)
        val out = ByteArrayOutputStream()
        out.write(query)
        val raw = out.toByteArray()
        // Mark response flag (QR=1) and ANCOUNT = 2
        raw[2] = 0x81.toByte()
        raw[3] = 0x80.toByte()
        raw[6] = 0x00
        raw[7] = 0x02

        val response = ByteArrayOutputStream()
        response.write(raw)
        // Answer 1: compressed pointer 0xC0 0x0C, TYPE A (1), CLASS IN (1), TTL 120s, RDLEN 4, IP 93.184.216.34
        response.write(byteArrayOf(0xC0.toByte(), 0x0C, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00, 0x00, 0x78, 0x00, 0x04))
        response.write(byteArrayOf(93.toByte(), 184.toByte(), 216.toByte(), 34.toByte()))
        // Answer 2: compressed pointer 0xC0 0x0C, TYPE AAAA (28), CLASS IN (1), TTL 90s, RDLEN 16, IPv6 2606:2800:220:1::
        response.write(byteArrayOf(0xC0.toByte(), 0x0C, 0x00, 0x1C, 0x00, 0x01, 0x00, 0x00, 0x00, 0x5A, 0x00, 0x10))
        response.write(byteArrayOf(0x26, 0x06, 0x28, 0x00, 0x02, 0x20, 0x00, 0x01, 0, 0, 0, 0, 0, 0, 0, 1))

        val (addresses, ttl) = OrbitDohDns.parseDnsResponsePacket(response.toByteArray())
        assertEquals(2, addresses.size)
        assertEquals("93.184.216.34", addresses[0].hostAddress)
        assertEquals(90L, ttl)
    }

    @Test
    fun isIpLiteral_identifiesIpAddresses() {
        assertTrue(OrbitDohDns.isIpLiteral("1.1.1.1"))
        assertTrue(OrbitDohDns.isIpLiteral("2606:4700:4700::1111"))
        assertFalse(OrbitDohDns.isIpLiteral("cloudflare-dns.com"))
    }
}
