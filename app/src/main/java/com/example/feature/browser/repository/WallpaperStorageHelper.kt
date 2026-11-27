package com.example.feature.browser.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Base64
import android.webkit.CookieManager
import com.example.R
import com.example.core.network.NetworkClientFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Manages Start Page background images as optimized external files in app-specific external
 * Pictures storage (`Android/data/<pkg>/files/Pictures/orbit_wallpapers/`) instead of Base64
 * strings, keeping DataStore/Room lightweight and avoiding stale image caches.
 */
object WallpaperStorageHelper {

    private const val WALLPAPER_DIR_NAME = "orbit_wallpapers"
    private const val FILE_PREFIX = "wallpaper_"
    private const val LEGACY_INTERNAL_WALLPAPER = "orbit_start_wallpaper.jpg"
    private const val MAX_WALLPAPER_DIMENSION_PX = 1920
    private const val JPEG_QUALITY = 88
    private const val MAX_REMOTE_IMAGE_BYTES = 25L * 1024L * 1024L

    private val downloadClient by lazy { NetworkClientFactory.createOkHttpClient() }

    fun getWallpaperDirectory(context: Context): File {
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
        return File(baseDir, WALLPAPER_DIR_NAME).apply {
            if (!exists()) mkdirs()
        }
    }

    suspend fun loadPreviewBitmapFromUri(context: Context, sourceUri: Uri): Result<Bitmap> =
        withContext(Dispatchers.IO) {
            runCatching {
                decodeScaledBitmapFromUri(context, sourceUri)
                    ?: error("Could not decode the selected image")
            }
        }

    suspend fun loadPreviewBitmapFromUrl(context: Context, imageUrl: String): Result<Bitmap> =
        withContext(Dispatchers.IO) {
            runCatching {
                val trimmed = imageUrl.trim()
                if (trimmed.startsWith("data:", ignoreCase = true)) {
                    decodeDataUrlToBitmap(trimmed)
                } else {
                    downloadAndDecodeWebBitmap(context, trimmed)
                } ?: error("Could not load image from website")
            }
        }

    suspend fun loadPreviewBitmapFromWallpaperValue(context: Context, wallpaperUri: String): Result<Bitmap> =
        withContext(Dispatchers.IO) {
            runCatching {
                val trimmed = wallpaperUri.trim()
                when {
                    trimmed.startsWith("/") && File(trimmed).exists() -> {
                        decodeScaledBitmapFromFile(File(trimmed))
                    }
                    trimmed.startsWith("http://", ignoreCase = true) ||
                        trimmed.startsWith("https://", ignoreCase = true) -> {
                        runCatching { downloadAndDecodeWebBitmap(context, trimmed) }.getOrNull()
                    }
                    else -> null
                } ?: decodeDummySampleBitmap(context)
                    ?: error("Could not load background preview image")
            }
        }

    suspend fun loadDummySampleBitmap(context: Context): Result<Bitmap> =
        withContext(Dispatchers.IO) {
            runCatching {
                decodeDummySampleBitmap(context) ?: error("Could not decode sample image")
            }
        }

    suspend fun saveCroppedExternalWallpaper(
        context: Context,
        sourceBitmap: Bitmap,
        targetAspectRatio: Float,
        zoom: Float,
        panNormX: Float,
        panNormY: Float
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val rect = WallpaperCropMath.computeCropRect(
                srcWidth = sourceBitmap.width,
                srcHeight = sourceBitmap.height,
                targetAspectRatio = targetAspectRatio,
                zoom = zoom,
                panNormX = panNormX,
                panNormY = panNormY
            )
            val cropped = Bitmap.createBitmap(sourceBitmap, rect.left, rect.top, rect.width, rect.height)
            val scaled = scaleDownIfNeeded(cropped)
            if (scaled !== cropped && cropped !== sourceBitmap && !cropped.isRecycled) {
                cropped.recycle()
            }
            writeCompressedExternalWallpaper(
                context = context,
                bitmap = scaled,
                recycleAfterWrite = scaled !== sourceBitmap
            )
        }
    }

    suspend fun saveFromLocalUri(context: Context, sourceUri: Uri): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val bitmap = decodeScaledBitmapFromUri(context, sourceUri)
                    ?: error("Could not decode the selected image")
                writeCompressedExternalWallpaper(context, bitmap)
            }
        }

    suspend fun saveFromWebImage(context: Context, imageUrl: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val trimmed = imageUrl.trim()
                val bitmap = if (trimmed.startsWith("data:", ignoreCase = true)) {
                    decodeDataUrlToBitmap(trimmed)
                } else {
                    downloadAndDecodeWebBitmap(context, trimmed)
                } ?: error("Could not load image from website")
                writeCompressedExternalWallpaper(context, bitmap)
            }
        }

    suspend fun cleanupManagedWallpapers(context: Context, keepPath: String? = null) {
        withContext(Dispatchers.IO) {
            val dir = getWallpaperDirectory(context)
            dir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.startsWith(FILE_PREFIX) && file.absolutePath != keepPath) {
                    runCatching { file.delete() }
                }
            }
            val legacy = File(context.filesDir, LEGACY_INTERNAL_WALLPAPER)
            if (legacy.exists() && legacy.absolutePath != keepPath) {
                runCatching { legacy.delete() }
            }
        }
    }

    private fun writeCompressedExternalWallpaper(
        context: Context,
        bitmap: Bitmap,
        recycleAfterWrite: Boolean = true
    ): String {
        val dir = getWallpaperDirectory(context)
        val outFile = File(dir, "$FILE_PREFIX${System.currentTimeMillis()}.jpg")
        try {
            FileOutputStream(outFile).use { stream ->
                val ok = bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
                stream.flush()
                if (!ok) error("Failed to save optimized wallpaper file")
            }
        } finally {
            if (recycleAfterWrite && !bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
        cleanupOtherFilesSync(context, dir, keepPath = outFile.absolutePath)
        return outFile.absolutePath
    }

    private fun cleanupOtherFilesSync(context: Context, dir: File, keepPath: String) {
        dir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.startsWith(FILE_PREFIX) && file.absolutePath != keepPath) {
                runCatching { file.delete() }
            }
        }
        val legacy = File(context.filesDir, LEGACY_INTERNAL_WALLPAPER)
        if (legacy.exists() && legacy.absolutePath != keepPath) {
            runCatching { legacy.delete() }
        }
    }

    private fun decodeScaledBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val (targetW, targetH) = computeTargetSize(info.size.width, info.size.height)
                decoder.setTargetSize(targetW, targetH)
            }
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        } ?: return null

        val sampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight, MAX_WALLPAPER_DIMENSION_PX)
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        } ?: return null
        return scaleDownIfNeeded(decoded)
    }

    private fun downloadAndDecodeWebBitmap(context: Context, url: String): Bitmap? {
        val tempFile = File.createTempFile("orbit_wp_dl_", ".tmp", context.cacheDir)
        try {
            val requestBuilder = Request.Builder()
                .url(url)
                .header("Accept", "image/avif,image/webp,image/png,image/jpeg,image/*;q=0.8")
            runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { cookieHeader ->
                    requestBuilder.header("Cookie", cookieHeader)
                }

            downloadClient.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val body = response.body ?: error("Empty image response")
                if (body.contentLength() > MAX_REMOTE_IMAGE_BYTES) {
                    error("Image exceeds 25 MB limit")
                }
                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var copied = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            copied += read
                            if (copied > MAX_REMOTE_IMAGE_BYTES) error("Image exceeds 25 MB limit")
                            output.write(buffer, 0, read)
                        }
                    }
                }
            }
            return decodeScaledBitmapFromFile(tempFile)
        } finally {
            runCatching { tempFile.delete() }
        }
    }

    private fun decodeScaledBitmapFromFile(file: File): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(file)
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val (targetW, targetH) = computeTargetSize(info.size.width, info.size.height)
                decoder.setTargetSize(targetW, targetH)
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val sampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight, MAX_WALLPAPER_DIMENSION_PX)
        val decoded = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        ) ?: return null
        return scaleDownIfNeeded(decoded)
    }

    private fun decodeDataUrlToBitmap(dataUrl: String): Bitmap? {
        val commaIndex = dataUrl.indexOf(',')
        if (commaIndex <= 0) return null
        val header = dataUrl.substring(0, commaIndex)
        if (!header.contains(";base64", ignoreCase = true)) return null
        val rawBytes = Base64.decode(dataUrl.substring(commaIndex + 1), Base64.DEFAULT)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, bounds)
        val sampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight, MAX_WALLPAPER_DIMENSION_PX)
        val decoded = BitmapFactory.decodeByteArray(
            rawBytes,
            0,
            rawBytes.size,
            BitmapFactory.Options().apply { inSampleSize = sampleSize }
        ) ?: return null
        return scaleDownIfNeeded(decoded)
    }

    private fun decodeDummySampleBitmap(context: Context): Bitmap? {
        val decoded = BitmapFactory.decodeResource(
            context.resources,
            R.drawable.img_home_hero_1795536368553
        ) ?: return null
        return scaleDownIfNeeded(decoded)
    }

    internal fun computeTargetSize(srcWidth: Int, srcHeight: Int): Pair<Int, Int> {
        if (srcWidth <= 0 || srcHeight <= 0) return 1080 to 1920
        val longest = max(srcWidth, srcHeight)
        if (longest <= MAX_WALLPAPER_DIMENSION_PX) return srcWidth to srcHeight
        val scale = MAX_WALLPAPER_DIMENSION_PX.toDouble() / longest.toDouble()
        return (srcWidth * scale).roundToInt().coerceAtLeast(1) to
            (srcHeight * scale).roundToInt().coerceAtLeast(1)
    }

    internal fun computeInSampleSize(srcWidth: Int, srcHeight: Int, maxDim: Int): Int {
        var sampleSize = 1
        if (srcWidth > maxDim || srcHeight > maxDim) {
            val halfW = srcWidth / 2
            val halfH = srcHeight / 2
            while ((halfW / sampleSize) >= maxDim || (halfH / sampleSize) >= maxDim) {
                sampleSize *= 2
            }
        }
        return sampleSize.coerceAtLeast(1)
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap): Bitmap {
        val (targetW, targetH) = computeTargetSize(bitmap.width, bitmap.height)
        if (targetW == bitmap.width && targetH == bitmap.height) return bitmap
        val scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }
}
