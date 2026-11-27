package com.example.core.registry

import androidx.compose.ui.graphics.vector.ImageVector

enum class ToolCategory(val title: String) {
    NAVIGATION("Navigation & Library"),
    PRIVACY("Privacy & Encryption"),
    UTILITIES("Browser Engine & Tools")
}

/**
 * Contract describing a modular browser tool or feature module.
 * Future tools can be plugged into BrowserToolRegistry without restructuring the app.
 */
data class BrowserFeatureModule(
    val id: String,
    val title: String,
    val subtitle: String,
    val route: String?,
    val icon: ImageVector,
    val category: ToolCategory,
    val badgeText: String? = null,
    val isInPageAction: Boolean = false
)
