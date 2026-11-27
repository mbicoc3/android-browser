package com.example.core.network

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.example.core.common.UrlUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Multi-strategy website icon extractor and disk cache for Speed Dial shortcuts.
 * Tries HTML <link> tags, Web App Manifests, base64 data URIs, origin root paths,
 * live WebView DOM URLs/bitmaps, and public favicon resolvers, caching crisp PNGs locally.
 */
class FaviconExtractor(
    private val okHttpClient: OkHttpClient
) {
    fun getCachedIconPathIfValid(context: Context, pageUrl: String): String? {
        val file = iconFileForUrl(context, pageUrl) ?: return null
        return file.absolutePath.takeIf { file.exists() && file.length() > 100L }
    }

    suspend fun extractAndCacheIcon(
        context: Context,
        pageUrl: String,
        forceRefresh: Boolean = false
    ): String? = withContext(Dispatchers.IO) {
        val targetFile = iconFileForUrl(context, pageUrl) ?: return@withContext null
        if (!forceRefresh && targetFile.exists() && targetFile.length() > 100L) {
            return@withContext targetFile.absolutePath
        }

        val candidates = LinkedHashMap<String, FaviconCandidate>()
        fun addAll(items: List<FaviconCandidate>) {
            for (c in items) {
                val prev = candidates[c.url]
                if (prev == null || c.priority > prev.priority) {
                    candidates[c.url] = c
                }
            }
        }

        val manifestUrls = LinkedHashSet<String>()
        val originUrl = UrlUtils.extractOrigin(pageUrl)

        // 1. Fetch HTML from target URL (and origin root if target URL was a deep path or blocked)
        val htmlFetchTargets = listOfNotNull(pageUrl, originUrl?.takeIf { it != pageUrl }).distinct()
        for (htmlUrl in htmlFetchTargets) {
            val html = fetchTextDocument(htmlUrl, maxChars = 160_000)
            if (!html.isNullOrBlank()) {
                val parsed = FaviconHtmlParser.parseHtml(html, htmlUrl)
                addAll(parsed.iconCandidates)
                manifestUrls.addAll(parsed.manifestUrls)
                if (parsed.iconCandidates.isNotEmpty()) break
            }
        }

        // 2. Fetch Web App Manifest(s) if present or probe standard manifest paths
        if (manifestUrls.isEmpty()) {
            manifestUrls.addAll(FaviconHtmlParser.extractWellKnownManifestCandidates(pageUrl))
        }
        for (manifestUrl in manifestUrls.take(3)) {
            val manifestJson = fetchTextDocument(manifestUrl, maxChars = 64_000)
            if (!manifestJson.isNullOrBlank() && manifestJson.trimStart().startsWith("{")) {
                addAll(FaviconHtmlParser.parseManifestIcons(manifestJson, manifestUrl))
            }
        }

        // 3. Probe standard well-known root icon paths
        addAll(FaviconHtmlParser.extractWellKnownRootCandidates(pageUrl))

        // 4. Final fallback: public favicon resolvers (handles JS-only SPAs / Cloudflare sites)
        addAll(FaviconHtmlParser.extractPublicResolverFallbacks(pageUrl))

        val ordered = candidates.values.sortedWith(
            compareByDescending<FaviconCandidate> { it.priority }
                .thenByDescending { it.estimatedSize }
        )

        for (candidate in ordered) {
            val bitmap = decodeCandidateBitmap(candidate) ?: continue
            if (saveBitmapToFile(bitmap, targetFile)) {
                bitmap.recycle()
                return@withContext targetFile.absolutePath
            }
            bitmap.recycle()
        }
        null
    }

    suspend fun saveFromCandidateUrls(
        context: Context,
        pageUrl: String,
        urls: List<String>
    ): String? = withContext(Dispatchers.IO) {
        if (urls.isEmpty()) return@withContext null
        val targetFile = iconFileForUrl(context, pageUrl) ?: return@withContext null
        for (rawUrl in urls.distinct()) {
            val resolved = FaviconHtmlParser.resolveUrl(pageUrl, rawUrl) ?: continue
            val bitmap = decodeCandidateBitmap(
                FaviconCandidate(url = resolved, priority = 100)
            ) ?: continue
            if (saveBitmapToFile(bitmap, targetFile)) {
                bitmap.recycle()
                return@withContext targetFile.absolutePath
            }
            bitmap.recycle()
        }
        null
    }

    suspend fun saveLiveBitmapForUrl(
        context: Context,
        pageUrl: String,
        bitmap: Bitmap
    ): String? = withContext(Dispatchers.IO) {
        if (bitmap.isRecycled || bitmap.width < 16 || bitmap.height < 16) return@withContext null
        val targetFile = iconFileForUrl(context, pageUrl) ?: return@withContext null
        // Preserve already-saved higher-res icon unless the new live icon is crisp (>= 32px)
        if (targetFile.exists() && targetFile.length() > 400L && bitmap.width < 32) {
            return@withContext targetFile.absolutePath
        }
        if (saveBitmapToFile(bitmap, targetFile)) targetFile.absolutePath else null
    }

    private fun decodeCandidateBitmap(candidate: FaviconCandidate): Bitmap? {
        val url = candidate.url
        if (url.startsWith("data:image/", ignoreCase = true)) {
            return decodeDataUriBitmap(url)
        }
        if (url.substringBefore('?').lowercase().endsWith(".svg")) {
            return null
        }
        val bytes = fetchBinaryBytes(url) ?: return null
        if (bytes.size < 32) return null

        // Skip SVG payloads served without .svg extension
        val headText = bytes.take(128).toByteArray().decodeToString().trimStart().lowercase()
        if (headText.startsWith("<svg") || headText.startsWith("<?xml") || headText.startsWith("<!doctype html")) {
            return null
        }

        val decodeBytes = FaviconHtmlParser.extractLargestIcoImageBytes(bytes)
        val bitmap = runCatching {
            BitmapFactory.decodeByteArray(decodeBytes, 0, decodeBytes.size)
        }.getOrNull() ?: return null

        if (bitmap.width < 12 || bitmap.height < 12) {
            bitmap.recycle()
            return null
        }
        // Google S2 returns a generic 16x16 globe when a domain has no icon despite sz=128
        if (candidate.isGoogleS2 && bitmap.width <= 16 && bitmap.height <= 16) {
            bitmap.recycle()
            return null
        }
        return bitmap
    }

    private fun decodeDataUriBitmap(dataUri: String): Bitmap? {
        val commaIdx = dataUri.indexOf(',')
        if (commaIdx <= 0 || commaIdx >= dataUri.lastIndex) return null
        val header = dataUri.substring(0, commaIdx).lowercase()
        if (header.contains("svg")) return null
        if (!header.contains(";base64")) return null
        val encoded = dataUri.substring(commaIdx + 1)
        val raw = runCatching { Base64.decode(encoded, Base64.DEFAULT) }.getOrNull() ?: return null
        val decodeBytes = FaviconHtmlParser.extractLargestIcoImageBytes(raw)
        return runCatching {
            BitmapFactory.decodeByteArray(decodeBytes, 0, decodeBytes.size)
        }.getOrNull()?.takeIf { it.width >= 12 && it.height >= 12 }
    }

    private fun fetchTextDocument(url: String, maxChars: Int): String? {
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", BROWSER_USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8")
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body ?: return@use null
                val reader = body.charStream()
                val buf = CharArray(8192)
                val sb = StringBuilder()
                while (sb.length < maxChars) {
                    val read = reader.read(buf)
                    if (read <= 0) break
                    val remaining = maxChars - sb.length
                    sb.append(buf, 0, read.coerceAtMost(remaining))
                }
                sb.toString()
            }
        }.getOrNull()
    }

    private fun fetchBinaryBytes(url: String): ByteArray? {
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", BROWSER_USER_AGENT)
                .header("Accept", "image/avif,image/webp,image/png,image/x-icon,image/*;q=0.8,*/*;q=0.5")
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val contentType = response.header("Content-Type").orEmpty().lowercase()
                if (contentType.contains("text/html")) return@use null
                val body = response.body ?: return@use null
                val bytes = body.bytes()
                if (bytes.size > MAX_ICON_BYTES) null else bytes
            }
        }.getOrNull()
    }

    private fun saveBitmapToFile(source: Bitmap, outFile: File): Boolean {
        return runCatching {
            outFile.parentFile?.mkdirs()
            val maxDim = max(source.width, source.height)
            val normalized = if (maxDim > TARGET_ICON_SIZE) {
                val scale = TARGET_ICON_SIZE.toFloat() / maxDim.toFloat()
                val w = (source.width * scale).roundToInt().coerceAtLeast(1)
                val h = (source.height * scale).roundToInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(source, w, h, true)
            } else {
                source
            }
            val temp = File(outFile.parentFile, "${outFile.name}.tmp")
            FileOutputStream(temp).use { fos ->
                normalized.compress(Bitmap.CompressFormat.PNG, 92, fos)
            }
            if (normalized !== source) {
                normalized.recycle()
            }
            if (outFile.exists()) outFile.delete()
            temp.renameTo(outFile)
        }.getOrDefault(false)
    }

    private fun iconFileForUrl(context: Context, pageUrl: String): File? {
        val host = UrlUtils.extractHost(pageUrl).trim().lowercase()
        if (host.isBlank()) return null
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(host.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(24)
        val dir = File(context.filesDir, "shortcut_icons")
        return File(dir, "$digest.png")
    }

    companion object {
        private const val TARGET_ICON_SIZE = 128
        private const val MAX_ICON_BYTES = 1_500_000
        private const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    }
}
