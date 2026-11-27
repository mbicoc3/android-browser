package com.example.feature.sandbox.repository

import android.content.Context
import com.example.core.common.SearchEngine
import com.example.core.common.UrlUtils
import com.example.core.database.dao.BrowserTabDao
import com.example.core.database.dao.SandboxDao
import com.example.core.database.entity.SandboxEntity
import com.example.feature.sandbox.engine.SandboxProfileEngine
import com.example.feature.sandbox.model.SandboxModel
import com.example.feature.sandbox.model.SandboxPresets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.util.UUID

class SandboxRepository(
    private val appContext: Context,
    private val sandboxDao: SandboxDao,
    private val browserTabDao: BrowserTabDao
) {
    fun observeSandboxes(): Flow<List<SandboxModel>> = combine(
        sandboxDao.observeAll(),
        browserTabDao.observeStandardTabs()
    ) { entities, tabs ->
        val countsBySandbox = tabs.mapNotNull { it.sandboxId }.groupingBy { it }.eachCount()
        entities.map { entity ->
            entity.toModel(openTabCount = countsBySandbox[entity.id] ?: 0)
        }
    }

    suspend fun getAllSnapshot(): List<SandboxModel> = withContext(Dispatchers.IO) {
        sandboxDao.getAllSnapshot().map { it.toModel() }
    }

    suspend fun getById(id: String): SandboxModel? = withContext(Dispatchers.IO) {
        sandboxDao.getById(id)?.toModel()
    }

    fun isMultiProfileSupported(): Boolean = SandboxProfileEngine.isMultiProfileSupported()

    suspend fun createSandbox(
        name: String,
        rawSiteUrl: String,
        colorHex: Long,
        iconKey: String = "work",
        keepLongTerm: Boolean = true,
        searchEngine: SearchEngine = SearchEngine.DUCKDUCKGO
    ): SandboxModel {
        val cleanUrl = normalizeStartUrl(rawSiteUrl, searchEngine)
        val domain = if (cleanUrl.isNotBlank()) UrlUtils.extractHost(cleanUrl).lowercase() else ""
        val cleanName = name.trim().ifBlank {
            if (domain.isNotBlank()) "${domain.removePrefix("www.")} Sandbox" else "Sandbox ${sandboxDao.count() + 1}"
        }
        val id = UUID.randomUUID().toString()
        val profileName = "orbit_sb_${id.replace("-", "").take(10)}"
        val now = System.currentTimeMillis()
        val entity = SandboxEntity(
            id = id,
            name = cleanName,
            profileName = profileName,
            pinnedDomain = domain,
            startUrl = cleanUrl,
            colorHex = colorHex,
            iconKey = iconKey,
            keepLongTerm = keepLongTerm,
            createdAt = now,
            lastUsedAt = now
        )
        withContext(Dispatchers.Main.immediate) {
            SandboxProfileEngine.ensureProfileReady(profileName)
        }
        withContext(Dispatchers.IO) {
            sandboxDao.upsert(entity)
        }
        return entity.toModel()
    }

    suspend fun updateSandbox(
        id: String,
        name: String,
        rawSiteUrl: String,
        colorHex: Long,
        iconKey: String,
        keepLongTerm: Boolean,
        searchEngine: SearchEngine = SearchEngine.DUCKDUCKGO
    ) {
        val existing = withContext(Dispatchers.IO) { sandboxDao.getById(id) } ?: return
        val cleanUrl = normalizeStartUrl(rawSiteUrl, searchEngine)
        val domain = if (cleanUrl.isNotBlank()) UrlUtils.extractHost(cleanUrl).lowercase() else ""
        val cleanName = name.trim().ifBlank { existing.name }
        withContext(Dispatchers.IO) {
            sandboxDao.upsert(
                existing.copy(
                    name = cleanName,
                    pinnedDomain = domain,
                    startUrl = cleanUrl,
                    colorHex = colorHex,
                    iconKey = iconKey,
                    keepLongTerm = keepLongTerm
                )
            )
        }
    }

    suspend fun touchLastUsed(id: String) = withContext(Dispatchers.IO) {
        sandboxDao.touchLastUsed(id, System.currentTimeMillis())
    }

    suspend fun bindPreferredCredential(sandboxId: String, credentialId: String?) =
        withContext(Dispatchers.IO) {
            sandboxDao.updatePreferredCredential(sandboxId, credentialId)
        }

    suspend fun clearSandboxData(sandboxId: String): Boolean {
        val entity = withContext(Dispatchers.IO) { sandboxDao.getById(sandboxId) } ?: return false
        return SandboxProfileEngine.clearProfileBrowsingData(appContext, entity.profileName)
    }

    suspend fun deleteSandbox(sandboxId: String): Boolean {
        val entity = withContext(Dispatchers.IO) { sandboxDao.getById(sandboxId) } ?: return false
        withContext(Dispatchers.IO) {
            browserTabDao.deleteTabsBySandbox(sandboxId)
            sandboxDao.deleteById(sandboxId)
        }
        return SandboxProfileEngine.deleteEngineProfile(appContext, entity.profileName)
    }

    fun nextSuggestedColorHex(existingCount: Int): Long {
        val colors = SandboxPresets.colors
        return colors[existingCount.coerceAtLeast(0) % colors.size].colorHex
    }

    private fun normalizeStartUrl(raw: String, searchEngine: SearchEngine): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank() || UrlUtils.isStartPage(trimmed)) return ""
        return UrlUtils.resolveInputToUrl(trimmed, searchEngine)
    }

    private fun SandboxEntity.toModel(openTabCount: Int = 0): SandboxModel = SandboxModel(
        id = id,
        name = name,
        profileName = profileName,
        pinnedDomain = pinnedDomain,
        startUrl = startUrl,
        colorHex = colorHex,
        iconKey = iconKey,
        keepLongTerm = keepLongTerm,
        preferredCredentialId = preferredCredentialId,
        createdAt = createdAt,
        lastUsedAt = lastUsedAt,
        openTabCount = openTabCount
    )
}
