package com.example

import com.example.feature.passwords.crypto.Argon2Kdf
import com.example.feature.passwords.crypto.Argon2Params
import com.example.feature.passwords.crypto.VaultCryptoEngine
import com.example.feature.passwords.domain.CredentialCsvCodec
import com.example.feature.passwords.domain.DomainMatchQuality
import com.example.feature.passwords.domain.DomainMatcher
import com.example.feature.passwords.domain.PasswordGenerator
import com.example.feature.passwords.domain.PasswordGeneratorOptions
import com.example.feature.passwords.domain.PasswordHealthAnalyzer
import com.example.feature.passwords.domain.PasswordStrength
import com.example.feature.passwords.model.Credential
import com.example.feature.passwords.model.CredentialFolder
import com.example.feature.passwords.repository.VaultBackupHelper
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import javax.crypto.AEADBadTagException

class PasswordVaultCryptoAndDomainTest {

    @Test
    fun argon2AndEnvelopeCrypto_roundTripAndTamperDetection() {
        val fastParams = Argon2Params(memoryKb = 4096, iterations = 1, parallelism = 1)
        val salt = Argon2Kdf.generateSalt()
        val kek = Argon2Kdf.deriveKey("StrongMaster!23".toCharArray(), salt, fastParams)
        val dek = VaultCryptoEngine.generateDek()

        val wrapped = VaultCryptoEngine.wrapDekWithKek(dek, kek)
        val verifier = VaultCryptoEngine.createVerifierBlob(dek)

        val unwrapped = VaultCryptoEngine.unwrapDekWithKek(wrapped, kek)
        assertArrayEquals(dek, unwrapped)
        assertTrue(VaultCryptoEngine.verifyDekWithCanary(unwrapped, verifier))

        val wrongKek = Argon2Kdf.deriveKey("WrongMaster!99".toCharArray(), salt, fastParams)
        var wrongKeyRejected = false
        try {
            VaultCryptoEngine.unwrapDekWithKek(wrapped, wrongKek)
        } catch (_: AEADBadTagException) {
            wrongKeyRejected = true
        }
        assertTrue("Wrong KEK must fail GCM tag verification", wrongKeyRejected)

        val aad = VaultCryptoEngine.buildCredentialAad("id-1", "github.com", VaultCryptoEngine.ENVELOPE_VERSION)
        val secretBytes = """{"username":"alice","password":"s3cr3t!Pass"}""".toByteArray(StandardCharsets.UTF_8)
        val blob1 = VaultCryptoEngine.encryptSecretPayload(dek, secretBytes, aad)
        val blob2 = VaultCryptoEngine.encryptSecretPayload(dek, secretBytes, aad)

        assertFalse("Each encryption must generate a fresh nonce", blob1.contentEquals(blob2))
        assertArrayEquals(secretBytes, VaultCryptoEngine.decryptSecretPayload(dek, blob1, aad))

        // Tamper ciphertext byte
        val tampered = blob1.copyOf()
        tampered[tampered.lastIndex] = (tampered.last().toInt() xor 0xFF).toByte()
        var tamperRejected = false
        try {
            VaultCryptoEngine.decryptSecretPayload(dek, tampered, aad)
        } catch (_: AEADBadTagException) {
            tamperRejected = true
        }
        assertTrue("Tampered ciphertext must fail authentication", tamperRejected)

        // Wrong AAD rejection
        val wrongAad = VaultCryptoEngine.buildCredentialAad("id-2", "evil.com", VaultCryptoEngine.ENVELOPE_VERSION)
        var aadRejected = false
        try {
            VaultCryptoEngine.decryptSecretPayload(dek, blob1, wrongAad)
        } catch (_: AEADBadTagException) {
            aadRejected = true
        }
        assertTrue("Mismatched AAD must fail authentication", aadRejected)

        // Master password rotation (rewrap same DEK under new KEK)
        val newSalt = Argon2Kdf.generateSalt()
        val newKek = Argon2Kdf.deriveKey("RotatedMaster#45".toCharArray(), newSalt, fastParams)
        val rewrapped = VaultCryptoEngine.wrapDekWithKek(dek, newKek)
        val rotatedDek = VaultCryptoEngine.unwrapDekWithKek(rewrapped, newKek)
        assertArrayEquals(secretBytes, VaultCryptoEngine.decryptSecretPayload(rotatedDek, blob1, aad))
    }

    @Test
    fun portableEncryptedBackup_roundTripsAndRejectsWrongPassphrase() {
        val fastParams = Argon2Params(memoryKb = 4096, iterations = 1, parallelism = 1)
        val folders = listOf(CredentialFolder("f1", "Work", 100L))
        val creds = listOf(
            Credential(
                id = "c1",
                title = "GitHub",
                username = "dev@example.com",
                password = "Complex!Password99",
                websiteUrl = "https://github.com/login",
                notes = "2FA enabled",
                folderId = "f1",
                isFavorite = true,
                createdAt = 100L,
                updatedAt = 200L
            )
        )

        val encryptedText = VaultBackupHelper.buildEncryptedBackup(
            passphrase = "PortableBackupPass!1".toCharArray(),
            folders = folders,
            credentials = creds,
            params = fastParams
        )
        assertFalse(encryptedText.contains("Complex!Password99"))

        val decoded = VaultBackupHelper.parseEncryptedBackup(
            passphrase = "PortableBackupPass!1".toCharArray(),
            backupFileContent = encryptedText
        )
        assertEquals(1, decoded.folders.size)
        assertEquals("Work", decoded.folders.first().name)
        assertEquals(1, decoded.credentials.size)
        assertEquals("Complex!Password99", decoded.credentials.first().password)

        var rejected = false
        try {
            VaultBackupHelper.parseEncryptedBackup("WrongPassphrase!".toCharArray(), encryptedText)
        } catch (_: Exception) {
            rejected = true
        }
        assertTrue(rejected)
    }

    @Test
    fun domainMatcher_enforcesOriginSubdomainSuffixAndHttpRules() {
        assertEquals(
            DomainMatchQuality.EXACT_ORIGIN,
            DomainMatcher.match("https://accounts.google.com/v3", "https://accounts.google.com")
        )
        assertEquals(
            DomainMatchQuality.REGISTRABLE_DOMAIN,
            DomainMatcher.match("https://mail.proton.me/inbox", "https://account.proton.me")
        )
        assertEquals(
            DomainMatchQuality.NONE,
            DomainMatcher.match("https://evil.co.uk", "https://bank.co.uk")
        )
        assertEquals(
            DomainMatchQuality.NONE,
            DomainMatcher.match("https://paypal.com.evil.org", "https://paypal.com")
        )
        assertEquals(
            DomainMatchQuality.NONE,
            DomainMatcher.match("javascript:alert(1)", "https://example.com")
        )
        assertEquals(
            DomainMatchQuality.BLOCKED_INSECURE_HTTP,
            DomainMatcher.match("http://example.com/login", "https://example.com", allowInsecureHttp = false)
        )
        assertEquals(
            DomainMatchQuality.EXACT_HOST,
            DomainMatcher.match("http://example.com/login", "https://example.com", allowInsecureHttp = true)
        )
    }

    @Test
    fun passwordGeneratorAndHealthAnalyzer_workAccurately() {
        val generated = PasswordGenerator.generate(
            PasswordGeneratorOptions(
                length = 20,
                includeUppercase = true,
                includeLowercase = true,
                includeDigits = true,
                includeSymbols = true,
                excludeAmbiguous = true
            )
        )
        assertEquals(20, generated.length)
        assertTrue(generated.any { it.isUpperCase() })
        assertTrue(generated.any { it.isLowerCase() })
        assertTrue(generated.any { it.isDigit() })
        assertTrue(generated.any { !it.isLetterOrDigit() })
        assertFalse(generated.contains('0') || generated.contains('O') || generated.contains('l'))
        assertEquals(PasswordStrength.STRONG, PasswordGenerator.evaluateStrength(generated).strength)

        val weakCred = Credential("1", "WeakSite", "a", "123456", "https://a.com", null, null, false, 1, 1)
        val reusedA = Credential("2", "SiteB", "b", "SameStrongPass!9", "https://b.com", null, null, false, 1, 1)
        val reusedB = Credential("3", "SiteC", "c", "SameStrongPass!9", "https://c.com", null, null, false, 1, 1)
        val report = PasswordHealthAnalyzer.analyzeLocalHealth(listOf(weakCred, reusedA, reusedB))
        assertTrue("1" in report.weakCredentialIds)
        assertEquals(1, report.reusedGroups.size)
        assertEquals(2, report.reusedCount)
        assertNotEquals(100, report.overallScore)
        assertEquals(40, PasswordHealthAnalyzer.sha1HexUpper("password").length)
    }

    @Test
    fun csvCodec_escapesFormulasAndSkipsDuplicates() {
        val creds = listOf(
            Credential("1", "=CMD()", "user1", "Pass!1234", "https://example.com", "Line 1, note", null, false, 1, 1)
        )
        val csv = CredentialCsvCodec.exportCsv(creds)
        assertTrue(csv.contains("'=CMD()"))

        val incomingCsv = """
            name,url,username,password,note
            Example,https://example.com,user1,Pass!1234,Duplicate of existing
            NewSite,newsite.org,alice@newsite.org,Fresh!Pass88,"Has, comma"
            BrokenSite,,alice,NoUrl,Ignored
        """.trimIndent()

        val parsed = CredentialCsvCodec.parseCsv(incomingCsv, creds)
        assertEquals(1, parsed.uniqueToImport.size)
        assertEquals("newsite.org", parsed.uniqueToImport.first().normalizedDomain)
        assertEquals(1, parsed.skippedDuplicates)
        assertEquals(1, parsed.skippedInvalid)
    }
}
