package com.example.feature.shield.model

enum class FilterRuleType(val storageKey: String, val label: String) {
    BLOCK("BLOCK", "Block"),
    EXCEPTION("EXCEPTION", "Allow"),
    COSMETIC("COSMETIC", "Cosmetic");

    companion object {
        fun fromKey(value: String?): FilterRuleType =
            entries.firstOrNull { it.storageKey.equals(value, ignoreCase = true) } ?: BLOCK
    }
}

data class FilterListItem(
    val id: String,
    val name: String,
    val sourceUrl: String,
    val category: String,
    val enabled: Boolean,
    val ruleCount: Int,
    val cosmeticRuleCount: Int,
    val lastUpdatedAt: Long,
    val isBuiltIn: Boolean
)

data class FilterRuleItem(
    val id: Long,
    val rawRule: String,
    val ruleType: FilterRuleType,
    val domainFilter: String,
    val enabled: Boolean,
    val createdAt: Long
)

data class ShieldEngineStats(
    val backendLabel: String,
    val isNativeRustActive: Boolean,
    val networkBlockRuleCount: Int,
    val exceptionRuleCount: Int,
    val cosmeticRuleCount: Int
)

data class RuleEvaluationPreview(
    val requestUrl: String,
    val documentHost: String?,
    val blocked: Boolean,
    val matchedRule: String?,
    val listSource: String?
)

data class ShieldUiState(
    val filterLists: List<FilterListItem> = emptyList(),
    val customRules: List<FilterRuleItem> = emptyList(),
    val cosmeticFilteringEnabled: Boolean = true,
    val trackerShieldEnabled: Boolean = true,
    val engineStats: ShieldEngineStats = ShieldEngineStats(
        backendLabel = "OrbitFilterEngine (ABP / EasyPrivacy / Cosmetic)",
        isNativeRustActive = false,
        networkBlockRuleCount = 0,
        exceptionRuleCount = 0,
        cosmeticRuleCount = 0
    ),
    val isSyncing: Boolean = false,
    val testPreview: RuleEvaluationPreview? = null,
    val statusBanner: String? = null
)
