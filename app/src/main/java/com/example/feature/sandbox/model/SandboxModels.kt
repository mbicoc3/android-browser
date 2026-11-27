package com.example.feature.sandbox.model

import androidx.compose.ui.graphics.Color

data class SandboxPresetColor(
    val id: String,
    val label: String,
    val colorHex: Long
) {
    val color: Color get() = Color(colorHex)
}

object SandboxPresets {
    val colors: List<SandboxPresetColor> = listOf(
        SandboxPresetColor(id = "cyan", label = "Cyan", colorHex = 0xFF38BDF8L),
        SandboxPresetColor(id = "emerald", label = "Emerald", colorHex = 0xFF10B981L),
        SandboxPresetColor(id = "violet", label = "Violet", colorHex = 0xFFA78BFAL),
        SandboxPresetColor(id = "amber", label = "Amber", colorHex = 0xFFF59E0BL),
        SandboxPresetColor(id = "rose", label = "Rose", colorHex = 0xFFF43F5EL),
        SandboxPresetColor(id = "indigo", label = "Indigo", colorHex = 0xFF6366F1L)
    )

    val iconCategories: List<Pair<String, String>> = listOf(
        "work" to "Work",
        "personal" to "Personal",
        "dev" to "Dev",
        "finance" to "Finance",
        "social" to "Social",
        "shop" to "Shopping"
    )
}

data class SandboxModel(
    val id: String,
    val name: String,
    val profileName: String,
    val pinnedDomain: String = "",
    val startUrl: String = "",
    val colorHex: Long = 0xFF38BDF8L,
    val iconKey: String = "work",
    val keepLongTerm: Boolean = true,
    val preferredCredentialId: String? = null,
    val createdAt: Long = 0L,
    val lastUsedAt: Long = 0L,
    val openTabCount: Int = 0
) {
    val accentColor: Color get() = Color(colorHex)
}

data class SandboxUiState(
    val sandboxes: List<SandboxModel> = emptyList(),
    val isMultiProfileSupported: Boolean = false,
    val isBusy: Boolean = false,
    val statusMessage: String? = null
)
