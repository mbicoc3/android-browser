package com.example.feature.passwords.repository

import com.example.feature.passwords.crypto.Argon2Params
import com.example.feature.passwords.crypto.VaultCryptoEngine
import com.example.feature.passwords.domain.DomainMatcher
import com.example.feature.passwords.model.Credential
import com.example.feature.passwords.model.CredentialFolder
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.nio.charset.StandardCharsets

data class DecodedBackupPayload(
    val exportedAt: Long,
    val folders: List<CredentialFolder>,
    val credentials: List<Credential>
)

/**
 * Serializes and encrypts/decrypts portable `.orbitvault` backups independently of Android Keystore
 * so encrypted backups restore cleanly across devices with the user's backup passphrase.
 */
object VaultBackupHelper {

    private val moshi: Moshi by lazy { Moshi.Builder().build() }
    private val mapAdapter by lazy {
        val type = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
        moshi.adapter<Map<String, Any>>(type)
    }

    fun buildEncryptedBackup(
        passphrase: CharArray,
        folders: List<CredentialFolder>,
        credentials: List<Credential>,
        params: Argon2Params = Argon2Params()
    ): String {
        val payload = mapOf(
            "exportedAt" to System.currentTimeMillis(),
            "folders" to folders.map { folder ->
                mapOf(
                    "id" to folder.id,
                    "name" to folder.name,
                    "createdAt" to folder.createdAt
                )
            },
            "credentials" to credentials.map { cred ->
                mapOf(
                    "id" to cred.id,
                    "title" to cred.title,
                    "username" to cred.username.orEmpty(),
                    "password" to cred.password,
                    "websiteUrl" to cred.websiteUrl,
                    "notes" to cred.notes.orEmpty(),
                    "folderId" to cred.folderId.orEmpty(),
                    "isFavorite" to cred.isFavorite,
                    "createdAt" to cred.createdAt,
                    "updatedAt" to cred.updatedAt
                )
            }
        )
        val jsonBytes = mapAdapter.toJson(payload).toByteArray(StandardCharsets.UTF_8)
        return VaultCryptoEngine.encryptPortableBackup(passphrase, jsonBytes, params)
    }

    fun parseEncryptedBackup(
        passphrase: CharArray,
        backupFileContent: String
    ): DecodedBackupPayload {
        val plainBytes = VaultCryptoEngine.decryptPortableBackup(passphrase, backupFileContent)
        val json = String(plainBytes, StandardCharsets.UTF_8)
        plainBytes.fill(0)

        val root = mapAdapter.fromJson(json)
            ?: throw IllegalArgumentException("Corrupt decrypted backup JSON")
        val exportedAt = (root["exportedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()

        val rawFolders = (root["folders"] as? List<*>).orEmpty()
        val folders = rawFolders.mapNotNull { entry ->
            val m = entry as? Map<*, *> ?: return@mapNotNull null
            val id = (m["id"] as? String)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val name = (m["name"] as? String)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val createdAt = (m["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
            CredentialFolder(id = id, name = name, createdAt = createdAt)
        }

        val rawCreds = (root["credentials"] as? List<*>).orEmpty()
        val credentials = rawCreds.mapNotNull { entry ->
            val m = entry as? Map<*, *> ?: return@mapNotNull null
            val id = (m["id"] as? String)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val password = (m["password"] as? String).orEmpty()
            val websiteUrl = (m["websiteUrl"] as? String).orEmpty()
            if (password.isBlank() || websiteUrl.isBlank()) return@mapNotNull null
            val title = (m["title"] as? String)?.takeIf { it.isNotBlank() }
                ?: DomainMatcher.normalizeDomain(websiteUrl).ifBlank { "Login" }
            Credential(
                id = id,
                title = title,
                username = (m["username"] as? String)?.takeIf { it.isNotBlank() },
                password = password,
                websiteUrl = websiteUrl,
                notes = (m["notes"] as? String)?.takeIf { it.isNotBlank() },
                folderId = (m["folderId"] as? String)?.takeIf { it.isNotBlank() },
                isFavorite = m["isFavorite"] as? Boolean ?: false,
                createdAt = (m["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                updatedAt = (m["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
            )
        }

        return DecodedBackupPayload(
            exportedAt = exportedAt,
            folders = folders,
            credentials = credentials
        )
    }
}
