package com.example.feature.shield.repository

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.core.database.dao.FilterListDao
import com.example.core.database.dao.FilterRuleDao
import com.example.core.database.entity.FilterListEntity
import com.example.core.database.entity.FilterRuleEntity
import com.example.core.datastore.BrowserPreferencesDataStore
import com.example.core.webengine.BlockDecision
import com.example.core.webengine.WebResourceCategory
import com.example.feature.shield.engine.BuiltInFilterRules
import com.example.feature.shield.engine.OrbitFilterEngine
import com.example.feature.shield.model.FilterListItem
import com.example.feature.shield.model.FilterRuleItem
import com.example.feature.shield.model.FilterRuleType
import com.example.feature.shield.model.ShieldEngineStats
import com.example.feature.shield.network.FilterListApi
import com.example.feature.shield.worker.FilterListSyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

class ShieldRepository(
    private val appContext: Context,
    private val filterListDao: FilterListDao,
    private val filterRuleDao: FilterRuleDao,
    private val filterListApi: FilterListApi,
    private val filterEngine: OrbitFilterEngine,
    private val preferencesDataStore: BrowserPreferencesDataStore,
    private val workManager: WorkManager
) {
    private val listsDir: File by lazy {
        File(appContext.filesDir, "filter_lists").apply { mkdirs() }
    }

    private val _engineStats = MutableStateFlow(filterEngine.getStats())
    val engineStats: StateFlow<ShieldEngineStats> = _engineStats.asStateFlow()

    val preferencesFlow = preferencesDataStore.preferencesFlow

    fun observeFilterLists(): Flow<List<FilterListItem>> =
        filterListDao.observeAll().map { rows ->
            rows.map { entity ->
                FilterListItem(
                    id = entity.id,
                    name = entity.name,
                    sourceUrl = entity.sourceUrl,
                    category = entity.category,
                    enabled = entity.enabled,
                    ruleCount = entity.ruleCount,
                    cosmeticRuleCount = entity.cosmeticRuleCount,
                    lastUpdatedAt = entity.lastUpdatedAt,
                    isBuiltIn = entity.isBuiltIn
                )
            }
        }

    fun observeCustomRules(): Flow<List<FilterRuleItem>> =
        filterRuleDao.observeAll().map { rows ->
            rows.map { entity ->
                FilterRuleItem(
                    id = entity.id,
                    rawRule = entity.rawRule,
                    ruleType = FilterRuleType.fromKey(entity.ruleType),
                    domainFilter = entity.domainFilter,
                    enabled = entity.enabled,
                    createdAt = entity.createdAt
                )
            }
        }

    suspend fun ensureInitializedAndCompile() = withContext(Dispatchers.IO) {
        if (filterListDao.count() == 0) {
            filterListDao.upsertAll(BuiltInFilterRules.defaultFilterLists(System.currentTimeMillis()))
        }
        rebuildCompiledEngine()
    }

    suspend fun setFilterListEnabled(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        filterListDao.setEnabled(id, enabled)
        rebuildCompiledEngine()
    }

    suspend fun setTrackerShieldEnabled(enabled: Boolean) {
        preferencesDataStore.setBlockTrackers(enabled)
    }

    suspend fun setCosmeticFilteringEnabled(enabled: Boolean) {
        preferencesDataStore.setCosmeticFilteringEnabled(enabled)
    }

    suspend fun addCustomRule(rawInput: String): Result<Unit> = withContext(Dispatchers.IO) {
        val rule = rawInput.trim()
        if (rule.isEmpty() || rule.length < 3) {
            return@withContext Result.failure(IllegalArgumentException("Enter a valid domain, ||rule^, @@||allow^, or ##.selector"))
        }
        val type = when {
            rule.contains("##") -> FilterRuleType.COSMETIC
            rule.startsWith("@@") -> FilterRuleType.EXCEPTION
            else -> FilterRuleType.BLOCK
        }
        val normalizedRule = if (type == FilterRuleType.BLOCK &&
            !rule.startsWith("||") &&
            !rule.contains("/") &&
            !rule.contains("*")
        ) {
            "||${rule.removePrefix("www.").lowercase()}^"
        } else {
            rule
        }
        val domainHint = normalizedRule
            .substringBefore("##")
            .removePrefix("@@")
            .removePrefix("||")
            .substringBefore("^")
            .substringBefore("$")
            .ifBlank { "Global" }

        filterRuleDao.insert(
            FilterRuleEntity(
                rawRule = normalizedRule,
                ruleType = type.storageKey,
                domainFilter = domainHint,
                enabled = true,
                createdAt = System.currentTimeMillis()
            )
        )
        rebuildCompiledEngine()
        Result.success(Unit)
    }

    suspend fun setCustomRuleEnabled(id: Long, enabled: Boolean) = withContext(Dispatchers.IO) {
        filterRuleDao.setEnabled(id, enabled)
        rebuildCompiledEngine()
    }

    suspend fun deleteCustomRule(id: Long) = withContext(Dispatchers.IO) {
        filterRuleDao.deleteById(id)
        rebuildCompiledEngine()
    }

    fun evaluateTestUrl(requestUrl: String, documentHost: String?): BlockDecision {
        return filterEngine.evaluateRequest(
            requestUrl = requestUrl,
            documentHost = documentHost?.takeIf { it.isNotBlank() },
            category = WebResourceCategory.SCRIPT
        )
    }

    fun enqueueManualSync() {
        val request = OneTimeWorkRequestBuilder<FilterListSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        workManager.enqueueUniqueWork(
            FilterListSyncWorker.UNIQUE_ONETIME_SYNC,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun schedulePeriodicSync() {
        val request = PeriodicWorkRequestBuilder<FilterListSyncWorker>(7, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .build()
            )
            .build()
        workManager.enqueueUniquePeriodicWork(
            FilterListSyncWorker.UNIQUE_PERIODIC_SYNC,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    suspend fun performNetworkSync(): Int = withContext(Dispatchers.IO) {
        if (filterListDao.count() == 0) {
            filterListDao.upsertAll(BuiltInFilterRules.defaultFilterLists(System.currentTimeMillis()))
        }
        val enabledLists = filterListDao.getEnabledSnapshot()
        var syncedCount = 0
        for (list in enabledLists) {
            val result = syncSingleList(list)
            if (result) syncedCount++
        }
        rebuildCompiledEngine()
        syncedCount
    }

    private suspend fun syncSingleList(list: FilterListEntity): Boolean {
        return runCatching {
            val responseBody = filterListApi.downloadFilterList(list.sourceUrl)
            val keptLines = ArrayList<String>(4096)
            var networkCount = 0
            var cosmeticCount = 0
            responseBody.byteStream().bufferedReader().useLines { sequence ->
                for (raw in sequence) {
                    val line = raw.trim()
                    if (line.isEmpty() ||
                        line.startsWith("!") ||
                        line.startsWith("[Adblock", ignoreCase = true) ||
                        line.startsWith("# ") ||
                        line == "#"
                    ) {
                        continue
                    }
                    if (line.contains("#?#") || line.contains("#$#") || line.contains("+js(")) {
                        continue
                    }
                    if (line.contains("##")) {
                        if (cosmeticCount < MAX_COSMETIC_RULES_PER_LIST) {
                            keptLines.add(line)
                            cosmeticCount++
                        }
                    } else if (networkCount < MAX_NETWORK_RULES_PER_LIST) {
                        keptLines.add(line)
                        networkCount++
                    }
                    if (keptLines.size >= MAX_NETWORK_RULES_PER_LIST + MAX_COSMETIC_RULES_PER_LIST) {
                        break
                    }
                }
            }
            if (keptLines.isNotEmpty()) {
                val targetFile = File(listsDir, "${list.id}.txt")
                targetFile.writeText(keptLines.joinToString("\n"))
                filterListDao.updateSyncStats(
                    id = list.id,
                    ruleCount = networkCount,
                    cosmeticRuleCount = cosmeticCount,
                    updatedAt = System.currentTimeMillis()
                )
                true
            } else {
                false
            }
        }.getOrDefault(false)
    }

    private suspend fun rebuildCompiledEngine() {
        val enabledLists = filterListDao.getEnabledSnapshot()
        val listSources = enabledLists.map { list ->
            val cachedFile = File(listsDir, "${list.id}.txt")
            val rules = if (cachedFile.isFile && cachedFile.length() > 0L) {
                runCatching { cachedFile.readLines() }.getOrElse { BuiltInFilterRules.seedRulesForList(list.id) }
            } else {
                BuiltInFilterRules.seedRulesForList(list.id)
            }
            list.name to rules
        }
        val customRules = filterRuleDao.getEnabledSnapshot().map { it.rawRule }
        filterEngine.reloadRules(listSources, customRules)
        _engineStats.value = filterEngine.getStats()
    }

    companion object {
        private const val MAX_NETWORK_RULES_PER_LIST = 25_000
        private const val MAX_COSMETIC_RULES_PER_LIST = 5_000
    }
}
