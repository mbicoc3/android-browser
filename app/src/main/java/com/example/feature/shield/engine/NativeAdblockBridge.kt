package com.example.feature.shield.engine

/**
 * Optional JNI bridge for Brave's open-source `adblock-rust` crate (`native/adblock-rust/`).
 * When `liborbit_adblock_rust.so` is bundled by an NDK+Rust CI build, Orbit automatically
 * delegates rule matching and cosmetic selector resolution to native Rust; otherwise it
 * falls back seamlessly to [OrbitFilterEngine] in pure Kotlin with zero crash risk.
 */
object NativeAdblockBridge {

    @Volatile
    private var loadAttempted = false

    @Volatile
    private var nativeLoaded = false

    @Volatile
    private var engineHandle: Long = 0L

    fun isAvailable(): Boolean {
        ensureLoaded()
        return nativeLoaded
    }

    private fun ensureLoaded() {
        if (loadAttempted) return
        synchronized(this) {
            if (loadAttempted) return
            loadAttempted = true
            nativeLoaded = runCatching {
                System.loadLibrary("orbit_adblock_rust")
                true
            }.getOrDefault(false)
        }
    }

    fun rebuildEngine(serializedRules: String): Boolean {
        if (!isAvailable()) return false
        return runCatching {
            if (engineHandle != 0L) {
                nativeDestroyEngine(engineHandle)
                engineHandle = 0L
            }
            engineHandle = nativeCreateEngine(serializedRules)
            engineHandle != 0L
        }.getOrDefault(false)
    }

    fun checkNetworkUrl(url: String, sourceUrl: String, requestType: String): Boolean? {
        val handle = engineHandle
        if (!isAvailable() || handle == 0L) return null
        return runCatching { nativeShouldBlock(handle, url, sourceUrl, requestType) }.getOrNull()
    }

    fun cosmeticCssForUrl(url: String): String? {
        val handle = engineHandle
        if (!isAvailable() || handle == 0L) return null
        return runCatching { nativeCosmeticCss(handle, url) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    @JvmStatic
    private external fun nativeCreateEngine(rulesText: String): Long

    @JvmStatic
    private external fun nativeShouldBlock(
        handle: Long,
        url: String,
        sourceUrl: String,
        requestType: String
    ): Boolean

    @JvmStatic
    private external fun nativeCosmeticCss(handle: Long, url: String): String

    @JvmStatic
    private external fun nativeDestroyEngine(handle: Long)
}
