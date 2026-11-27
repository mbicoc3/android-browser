package com.example.feature.browser.model

enum class ContextTargetKind {
    LINK,
    IMAGE,
    IMAGE_LINK,
    EMAIL,
    PHONE,
    GEO
}

data class WebContextMenuTarget(
    val tabId: String,
    val kind: ContextTargetKind,
    val linkUrl: String? = null,
    val linkText: String? = null,
    val imageUrl: String? = null,
    val imageAlt: String? = null,
    val touchX: Float = 0f,
    val touchY: Float = 0f
) {
    val primaryUrl: String?
        get() = linkUrl?.takeIf { it.isNotBlank() } ?: imageUrl?.takeIf { it.isNotBlank() }

    val displayText: String
        get() = linkText?.takeIf { it.isNotBlank() }
            ?: imageAlt?.takeIf { it.isNotBlank() }
            ?: primaryUrl.orEmpty()
}
