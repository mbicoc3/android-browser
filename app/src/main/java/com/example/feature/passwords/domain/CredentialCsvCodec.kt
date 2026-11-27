package com.example.feature.passwords.domain

import com.example.feature.passwords.model.Credential

data class ImportedCsvCredential(
    val title: String,
    val websiteUrl: String,
    val normalizedDomain: String,
    val username: String,
    val password: String,
    val notes: String?
)

data class CsvImportParseResult(
    val uniqueToImport: List<ImportedCsvCredential>,
    val skippedDuplicates: Int,
    val skippedInvalid: Int
)

/**
 * RFC-4180 CSV serializer and importer compatible with standard browser/password-manager CSVs
 * (`name,url,username,password,note`) with duplicate detection.
 */
object CredentialCsvCodec {

    fun exportCsv(credentials: List<Credential>): String {
        val sb = StringBuilder()
        sb.append("name,url,username,password,note\n")
        credentials.forEach { cred ->
            sb.append(escapeCsv(cred.title)).append(',')
            sb.append(escapeCsv(cred.websiteUrl)).append(',')
            sb.append(escapeCsv(cred.username.orEmpty())).append(',')
            sb.append(escapeCsv(cred.password)).append(',')
            sb.append(escapeCsv(cred.notes.orEmpty())).append('\n')
        }
        return sb.toString()
    }

    fun parseCsv(
        csvText: String,
        existingCredentials: List<Credential>
    ): CsvImportParseResult {
        val rows = parseRfc4180Rows(csvText)
        if (rows.isEmpty()) {
            return CsvImportParseResult(emptyList(), skippedDuplicates = 0, skippedInvalid = 0)
        }

        val header = rows.first().map { it.trim().lowercase() }
        val hasHeader = header.any { it in setOf("url", "website", "username", "password", "name", "title") }
        val dataRows = if (hasHeader) rows.drop(1) else rows

        val nameIdx = header.indexOfFirst { it in setOf("name", "title") }.takeIf { hasHeader && it >= 0 } ?: 0
        val urlIdx = header.indexOfFirst { it in setOf("url", "website", "login_uri", "origin") }.takeIf { hasHeader && it >= 0 } ?: 1
        val userIdx = header.indexOfFirst { it in setOf("username", "login_username", "user", "email") }.takeIf { hasHeader && it >= 0 } ?: 2
        val passIdx = header.indexOfFirst { it in setOf("password", "login_password", "pass") }.takeIf { hasHeader && it >= 0 } ?: 3
        val noteIdx = header.indexOfFirst { it in setOf("note", "notes", "extra") }.takeIf { hasHeader && it >= 0 } ?: 4

        val existingKeys = existingCredentials.map {
            Triple(
                DomainMatcher.normalizeDomain(it.websiteUrl),
                it.username.orEmpty().trim().lowercase(),
                it.password
            )
        }.toHashSet()

        val seenInFile = HashSet<Triple<String, String, String>>()
        val toImport = mutableListOf<ImportedCsvCredential>()
        var duplicates = 0
        var invalid = 0

        dataRows.forEach { row ->
            if (row.all { it.isBlank() }) return@forEach
            val rawUrl = row.getOrNull(urlIdx)?.trim().orEmpty()
            val password = row.getOrNull(passIdx).orEmpty()
            if (password.isBlank() || rawUrl.isBlank()) {
                invalid++
                return@forEach
            }
            val normalizedDomain = DomainMatcher.normalizeDomain(rawUrl)
            if (normalizedDomain.isBlank()) {
                invalid++
                return@forEach
            }
            val username = row.getOrNull(userIdx)?.trim().orEmpty()
            val title = row.getOrNull(nameIdx)?.trim()?.takeIf { it.isNotBlank() } ?: normalizedDomain
            val notes = row.getOrNull(noteIdx)?.trim()?.takeIf { it.isNotBlank() }
            val resolvedUrl = if (rawUrl.contains("://")) rawUrl else "https://$rawUrl"

            val key = Triple(normalizedDomain, username.lowercase(), password)
            if (key in existingKeys || key in seenInFile) {
                duplicates++
            } else {
                seenInFile += key
                toImport += ImportedCsvCredential(
                    title = title,
                    websiteUrl = resolvedUrl,
                    normalizedDomain = normalizedDomain,
                    username = username,
                    password = password,
                    notes = notes
                )
            }
        }

        return CsvImportParseResult(
            uniqueToImport = toImport,
            skippedDuplicates = duplicates,
            skippedInvalid = invalid
        )
    }

    private fun escapeCsv(value: String): String {
        val safe = if (value.startsWith("=") || value.startsWith("+") ||
            value.startsWith("-") || value.startsWith("@")
        ) {
            "'$value"
        } else {
            value
        }
        return if (safe.contains(',') || safe.contains('"') || safe.contains('\n') || safe.contains('\r')) {
            "\"${safe.replace("\"", "\"\"")}\""
        } else {
            safe
        }
    }

    private fun parseRfc4180Rows(input: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val currentRow = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        val cleanInput = input.removePrefix("\uFEFF")

        while (i < cleanInput.length) {
            val c = cleanInput[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < cleanInput.length && cleanInput[i + 1] == '"') {
                        field.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    field.append(c)
                }
            } else {
                when (c) {
                    '"' -> inQuotes = true
                    ',' -> {
                        currentRow += field.toString()
                        field.setLength(0)
                    }
                    '\r' -> {
                        if (i + 1 < cleanInput.length && cleanInput[i + 1] == '\n') i++
                        currentRow += field.toString()
                        field.setLength(0)
                        rows += currentRow.toList()
                        currentRow.clear()
                    }
                    '\n' -> {
                        currentRow += field.toString()
                        field.setLength(0)
                        rows += currentRow.toList()
                        currentRow.clear()
                    }
                    else -> field.append(c)
                }
            }
            i++
        }
        if (field.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow += field.toString()
            rows += currentRow.toList()
        }
        return rows
    }
}
