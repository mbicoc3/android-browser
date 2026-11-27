package com.example.feature.passwords.domain

import com.example.feature.passwords.model.Credential
import okhttp3.OkHttpClient
import okhttp3.Request
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ReusedPasswordGroup(
    val credentialIds: List<String>,
    val count: Int
)

data class PasswordHealthReport(
    val totalCount: Int,
    val weakCredentialIds: Set<String>,
    val reusedGroups: List<ReusedPasswordGroup>,
    val overallScore: Int
) {
    val reusedCount: Int get() = reusedGroups.sumOf { it.count }
}

/**
 * Computes password strength and reuse reports strictly in memory without exposing secrets,
 * and offers optional k-Anonymity Pwned Passwords breach checks (sending only 5-char SHA-1 prefix).
 */
object PasswordHealthAnalyzer {

    fun analyzeLocalHealth(credentials: List<Credential>): PasswordHealthReport {
        if (credentials.isEmpty()) {
            return PasswordHealthReport(
                totalCount = 0,
                weakCredentialIds = emptySet(),
                reusedGroups = emptyList(),
                overallScore = 100
            )
        }

        val weakIds = LinkedHashSet<String>()
        val byPasswordHash = LinkedHashMap<String, MutableList<String>>()

        credentials.forEach { cred ->
            val strength = PasswordGenerator.evaluateStrength(cred.password).strength
            if (strength == PasswordStrength.WEAK || strength == PasswordStrength.FAIR) {
                weakIds += cred.id
            }
            val digestKey = sha1HexUpper(cred.password)
            byPasswordHash.getOrPut(digestKey) { mutableListOf() }.add(cred.id)
        }

        val reusedGroups = byPasswordHash.values
            .filter { it.size >= 2 }
            .map { ReusedPasswordGroup(credentialIds = it.toList(), count = it.size) }

        val weakPenalty = (weakIds.size.toFloat() / credentials.size) * 45f
        val reusedUniqueCount = reusedGroups.sumOf { it.count }.toFloat()
        val reusePenalty = (reusedUniqueCount / credentials.size) * 45f
        val score = (100f - weakPenalty - reusePenalty).toInt().coerceIn(5, 100)

        return PasswordHealthReport(
            totalCount = credentials.size,
            weakCredentialIds = weakIds,
            reusedGroups = reusedGroups,
            overallScore = score
        )
    }

    /**
     * Implements Have I Been Pwned k-Anonymity Range API v3:
     * Only the first 5 characters of the SHA-1 digest ever leave the device.
     */
    suspend fun checkPwnedBreaches(
        credentials: List<Credential>,
        okHttpClient: OkHttpClient
    ): Result<Map<String, Int>> = withContext(Dispatchers.IO) {
        runCatching {
            val hashesByCredId = credentials.associate { it.id to sha1HexUpper(it.password) }
            val uniquePrefixes = hashesByCredId.values.map { it.substring(0, 5) }.distinct()
            val prefixCache = HashMap<String, Map<String, Int>>(uniquePrefixes.size)

            for (prefix in uniquePrefixes) {
                val request = Request.Builder()
                    .url("https://api.pwnedpasswords.com/range/$prefix")
                    .header("Add-Padding", "true")
                    .header("User-Agent", "OrbitBrowser-PasswordAudit")
                    .get()
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        error("Breach service returned HTTP ${response.code}")
                    }
                    val body = response.body?.string().orEmpty()
                    val suffixMap = HashMap<String, Int>()
                    body.lineSequence().forEach { line ->
                        val sep = line.indexOf(':')
                        if (sep > 0) {
                            val suffix = line.substring(0, sep).trim().uppercase()
                            val count = line.substring(sep + 1).trim().toIntOrNull() ?: 0
                            if (count > 0) {
                                suffixMap[suffix] = count
                            }
                        }
                    }
                    prefixCache[prefix] = suffixMap
                }
            }

            val breached = LinkedHashMap<String, Int>()
            hashesByCredId.forEach { (credId, fullHash) ->
                val prefix = fullHash.substring(0, 5)
                val suffix = fullHash.substring(5)
                val hits = prefixCache[prefix]?.get(suffix) ?: 0
                if (hits > 0) {
                    breached[credId] = hits
                }
            }
            breached
        }
    }

    fun sha1HexUpper(input: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
            .digest(input.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02X".format(it) }
    }
}
