package com.example.feature.passwords.crypto

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class WrappedKey(
    val encryptedKey: ByteArray,
    val nonce: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WrappedKey) return false
        return encryptedKey.contentEquals(other.encryptedKey) && nonce.contentEquals(other.nonce)
    }

    override fun hashCode(): Int = 31 * encryptedKey.contentHashCode() + nonce.contentHashCode()
}

/**
 * Envelope AES-256-GCM cryptography engine:
 *  - Random 256-bit DEK encrypts credential payloads, password history, and a canary verifier.
 *  - KEK derived via Argon2id from the user's Master Password wraps the DEK.
 *  - Unique 96-bit SecureRandom GCM nonce per encryption + authenticated AAD metadata.
 *  - Portable backups encrypt using passphrase-derived Argon2id keys independently of Keystore.
 */
object VaultCryptoEngine {

    const val ENVELOPE_VERSION = 1
    private const val BACKUP_FORMAT_VERSION = 1
    private const val AES_ALGORITHM = "AES"
    private const val GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_NONCE_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private const val DEK_BYTES = 32
    private const val VERIFIER_MARKER = "ORBIT_LOCAL_VAULT_CANARY_V1"

    val DEK_AAD: ByteArray = "orbit_vault_dek_v1".toByteArray(StandardCharsets.UTF_8)
    val VERIFIER_AAD: ByteArray = "orbit_vault_verifier_v1".toByteArray(StandardCharsets.UTF_8)
    private val BACKUP_AAD: ByteArray = "orbit_vault_portable_backup_v1".toByteArray(StandardCharsets.UTF_8)

    private val secureRandom = SecureRandom()
    private val moshi: Moshi by lazy { Moshi.Builder().build() }
    private val mapAdapter by lazy {
        val type = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
        moshi.adapter<Map<String, Any>>(type)
    }

    fun generateDek(): ByteArray {
        val dek = ByteArray(DEK_BYTES)
        secureRandom.nextBytes(dek)
        return dek
    }

    fun generateNonce(): ByteArray {
        val nonce = ByteArray(GCM_NONCE_BYTES)
        secureRandom.nextBytes(nonce)
        return nonce
    }

    fun buildCredentialAad(credentialId: String, normalizedDomain: String, version: Int): ByteArray {
        return "cred|$credentialId|$normalizedDomain|v$version".toByteArray(StandardCharsets.UTF_8)
    }

    fun buildHistoryAad(historyId: String, credentialId: String, version: Int): ByteArray {
        return "hist|$historyId|$credentialId|v$version".toByteArray(StandardCharsets.UTF_8)
    }

    fun wrapDekWithKek(
        dek: ByteArray,
        kek: ByteArray,
        aad: ByteArray = DEK_AAD
    ): WrappedKey {
        require(dek.size == DEK_BYTES) { "DEK must be 32 bytes" }
        require(kek.size == DEK_BYTES) { "KEK must be 32 bytes" }
        val nonce = generateNonce()
        val ciphertext = aesGcmEncryptRaw(key = kek, nonce = nonce, plaintext = dek, aad = aad)
        return WrappedKey(encryptedKey = ciphertext, nonce = nonce)
    }

    fun unwrapDekWithKek(
        wrapped: WrappedKey,
        kek: ByteArray,
        aad: ByteArray = DEK_AAD
    ): ByteArray {
        require(kek.size == DEK_BYTES) { "KEK must be 32 bytes" }
        return aesGcmDecryptRaw(key = kek, nonce = wrapped.nonce, ciphertext = wrapped.encryptedKey, aad = aad)
    }

    fun createVerifierBlob(dek: ByteArray): ByteArray {
        return encryptSecretPayload(
            dek = dek,
            plaintext = VERIFIER_MARKER.toByteArray(StandardCharsets.UTF_8),
            aad = VERIFIER_AAD
        )
    }

    fun verifyDekWithCanary(dek: ByteArray, verifierBlob: ByteArray): Boolean {
        return runCatching {
            val plain = decryptSecretPayload(dek = dek, blob = verifierBlob, aad = VERIFIER_AAD)
            val decoded = String(plain, StandardCharsets.UTF_8)
            plain.wipe()
            decoded == VERIFIER_MARKER
        }.getOrDefault(false)
    }

    /**
     * Format: [version: 1 byte][nonce: 12 bytes][ciphertext + 16-byte GCM tag]
     */
    fun encryptSecretPayload(
        dek: ByteArray,
        plaintext: ByteArray,
        aad: ByteArray,
        version: Int = ENVELOPE_VERSION
    ): ByteArray {
        require(dek.size == DEK_BYTES) { "DEK must be 32 bytes" }
        val nonce = generateNonce()
        val encrypted = aesGcmEncryptRaw(key = dek, nonce = nonce, plaintext = plaintext, aad = aad)
        val out = ByteArray(1 + GCM_NONCE_BYTES + encrypted.size)
        out[0] = (version and 0xFF).toByte()
        System.arraycopy(nonce, 0, out, 1, GCM_NONCE_BYTES)
        System.arraycopy(encrypted, 0, out, 1 + GCM_NONCE_BYTES, encrypted.size)
        return out
    }

    fun decryptSecretPayload(
        dek: ByteArray,
        blob: ByteArray,
        aad: ByteArray
    ): ByteArray {
        require(dek.size == DEK_BYTES) { "DEK must be 32 bytes" }
        require(blob.size > 1 + GCM_NONCE_BYTES + 16) { "Corrupt encrypted payload length" }
        val version = blob[0].toInt() and 0xFF
        require(version == ENVELOPE_VERSION) { "Unsupported encryption version: $version" }
        val nonce = blob.copyOfRange(1, 1 + GCM_NONCE_BYTES)
        val ciphertext = blob.copyOfRange(1 + GCM_NONCE_BYTES, blob.size)
        return aesGcmDecryptRaw(key = dek, nonce = nonce, ciphertext = ciphertext, aad = aad)
    }

    fun encryptPortableBackup(
        passphrase: CharArray,
        plainJsonBytes: ByteArray,
        params: Argon2Params = Argon2Params()
    ): String {
        val salt = Argon2Kdf.generateSalt()
        val key = Argon2Kdf.deriveKey(passphrase, salt, params)
        return try {
            val encryptedBlob = encryptSecretPayload(
                dek = key,
                plaintext = plainJsonBytes,
                aad = BACKUP_AAD,
                version = ENVELOPE_VERSION
            )
            val container = mapOf(
                "format" to "orbit_vault_backup",
                "backupVersion" to BACKUP_FORMAT_VERSION,
                "kdf" to "argon2id",
                "memoryKb" to params.memoryKb,
                "iterations" to params.iterations,
                "parallelism" to params.parallelism,
                "salt" to salt.toByteString().base64(),
                "payload" to encryptedBlob.toByteString().base64()
            )
            mapAdapter.toJson(container)
        } finally {
            key.wipe()
        }
    }

    fun decryptPortableBackup(
        passphrase: CharArray,
        backupFileText: String
    ): ByteArray {
        val root = mapAdapter.fromJson(backupFileText)
            ?: throw IllegalArgumentException("Invalid backup file format")
        require(root["format"] == "orbit_vault_backup") { "Unrecognized backup format" }
        val version = (root["backupVersion"] as? Number)?.toInt() ?: 0
        require(version == BACKUP_FORMAT_VERSION) { "Unsupported backup version: $version" }

        val params = Argon2Params(
            memoryKb = (root["memoryKb"] as? Number)?.toInt() ?: Argon2Params.DEFAULT_MEMORY_KB,
            iterations = (root["iterations"] as? Number)?.toInt() ?: Argon2Params.DEFAULT_ITERATIONS,
            parallelism = (root["parallelism"] as? Number)?.toInt() ?: Argon2Params.DEFAULT_PARALLELISM
        )
        val saltB64 = (root["salt"] as? String).orEmpty()
        val payloadB64 = (root["payload"] as? String).orEmpty()
        require(saltB64.isNotBlank() && payloadB64.isNotBlank()) { "Incomplete backup envelope" }

        val salt = saltB64.decodeBase64()?.toByteArray()
            ?: throw IllegalArgumentException("Invalid backup salt encoding")
        val payload = payloadB64.decodeBase64()?.toByteArray()
            ?: throw IllegalArgumentException("Invalid backup payload encoding")
        val key = Argon2Kdf.deriveKey(passphrase, salt, params)
        return try {
            decryptSecretPayload(dek = key, blob = payload, aad = BACKUP_AAD)
        } finally {
            key.wipe()
        }
    }

    private fun aesGcmEncryptRaw(
        key: ByteArray,
        nonce: ByteArray,
        plaintext: ByteArray,
        aad: ByteArray
    ): ByteArray {
        val cipher = Cipher.getInstance(GCM_TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_BITS, nonce)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, AES_ALGORITHM), spec)
        cipher.updateAAD(aad)
        return cipher.doFinal(plaintext)
    }

    private fun aesGcmDecryptRaw(
        key: ByteArray,
        nonce: ByteArray,
        ciphertext: ByteArray,
        aad: ByteArray
    ): ByteArray {
        val cipher = Cipher.getInstance(GCM_TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_BITS, nonce)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, AES_ALGORITHM), spec)
        cipher.updateAAD(aad)
        return cipher.doFinal(ciphertext)
    }
}
