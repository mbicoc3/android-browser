package com.example.feature.downloads.model

data class PendingDownloadRequest(
    val url: String,
    val suggestedFileName: String,
    val mimeType: String? = null
)

data class DownloadProbeResult(
    val url: String,
    val totalBytes: Long = -1L,
    val supportsRanges: Boolean = false,
    val mimeType: String = "application/octet-stream",
    val suggestedFileName: String? = null,
    val eTag: String? = null,
    val isProbing: Boolean = false,
    val probedSuccessfully: Boolean = false
)

enum class DownloadFilter(val label: String) {
    ALL("All"),
    ACTIVE("Active"),
    PAUSED("Paused"),
    COMPLETED("Completed")
}
