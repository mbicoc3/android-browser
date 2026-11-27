package com.example.feature.sandbox.engine

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.core.content.ContextCompat
import androidx.webkit.Profile
import androidx.webkit.ProfileStore
import androidx.webkit.WebStorageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Engine bridge over AndroidX WebKit's MULTI_PROFILE API (`ProfileStore` + `WebViewCompat.setProfile`).
 * Each named sandbox maps to a persistent, Chromium-isolated storage partition (cookies, localStorage,
 * sessionStorage, IndexedDB, cache, and service workers) that survives restarts and tab closures.
 */
object SandboxProfileEngine {

    fun isMultiProfileSupported(): Boolean = runCatching {
        WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
    }.getOrDefault(false)

    fun ensureProfileReady(profileName: String): Boolean {
        if (profileName.isBlank() || !isMultiProfileSupported()) return false
        return runCatching {
            ProfileStore.getInstance().getOrCreateProfile(profileName)
            true
        }.getOrDefault(false)
    }

    fun attachProfileToWebView(webView: WebView, profileName: String): Boolean {
        if (profileName.isBlank() || !isMultiProfileSupported()) return false
        return runCatching {
            ProfileStore.getInstance().getOrCreateProfile(profileName)
            WebViewCompat.setProfile(webView, profileName)
            true
        }.getOrDefault(false)
    }

    fun resolveCookieManager(webView: WebView, sandboxProfileName: String?): CookieManager? {
        if (!sandboxProfileName.isNullOrBlank() && isMultiProfileSupported()) {
            val profile = runCatching { WebViewCompat.getProfile(webView) }.getOrNull()
                ?: runCatching { ProfileStore.getInstance().getProfile(sandboxProfileName) }.getOrNull()
            if (profile != null) {
                return runCatching { profile.cookieManager }.getOrNull()
            }
        }
        return runCatching { CookieManager.getInstance() }.getOrNull()
    }

    fun getProfileOrNull(profileName: String): Profile? {
        if (profileName.isBlank() || !isMultiProfileSupported()) return null
        return runCatching { ProfileStore.getInstance().getProfile(profileName) }.getOrNull()
    }

    suspend fun clearProfileBrowsingData(context: Context, profileName: String): Boolean =
        withContext(Dispatchers.Main.immediate) {
            if (profileName.isBlank() || !isMultiProfileSupported()) return@withContext false
            val profile = runCatching {
                ProfileStore.getInstance().getProfile(profileName)
            }.getOrNull() ?: return@withContext false

            runCatching {
                val cm = profile.cookieManager
                suspendCancellableCoroutine<Unit> { cont ->
                    cm.removeAllCookies {
                        cm.flush()
                        if (cont.isActive) cont.resume(Unit)
                    }
                }
            }

            runCatching {
                val ws = profile.webStorage
                if (WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA)) {
                    withTimeoutOrNull(3_000L) {
                        suspendCancellableCoroutine<Unit> { cont ->
                            WebStorageCompat.deleteBrowsingData(
                                ws,
                                ContextCompat.getMainExecutor(context),
                                Runnable { if (cont.isActive) cont.resume(Unit) }
                            )
                        }
                    }
                } else {
                    ws.deleteAllData()
                }
            }
            true
        }

    suspend fun deleteEngineProfile(context: Context, profileName: String): Boolean =
        withContext(Dispatchers.Main.immediate) {
            if (profileName.isBlank() || !isMultiProfileSupported()) return@withContext false
            clearProfileBrowsingData(context, profileName)
            runCatching {
                ProfileStore.getInstance().deleteProfile(profileName)
            }.getOrDefault(false)
        }
}
