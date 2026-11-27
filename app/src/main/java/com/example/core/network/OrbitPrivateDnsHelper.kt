package com.example.core.network

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

enum class PrivateDnsMode(val label: String) {
    UNSUPPORTED("Unsupported (Android 8 or older)"),
    OFF("Off"),
    AUTOMATIC("Automatic"),
    STRICT_HOSTNAME("Private DNS Active")
}

data class PrivateDnsStatus(
    val isSupported: Boolean,
    val mode: PrivateDnsMode,
    val serverName: String? = null
) {
    val isEncrypted: Boolean
        get() = mode == PrivateDnsMode.AUTOMATIC || mode == PrivateDnsMode.STRICT_HOSTNAME
}

/**
 * Inspects Android 9+ (API 28+) Private DNS (DNS-over-TLS) state via ConnectivityManager.
 * Android System WebView delegates its DNS resolution to Android OS netd, so enabling Private DNS
 * protects all WebView browsing traffic automatically at no cost.
 */
object OrbitPrivateDnsHelper {

    fun getCurrentStatus(context: Context): PrivateDnsStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return PrivateDnsStatus(isSupported = false, mode = PrivateDnsMode.UNSUPPORTED)
        }
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return PrivateDnsStatus(isSupported = true, mode = PrivateDnsMode.OFF)
        val linkProps = runCatching { cm.getLinkProperties(cm.activeNetwork) }.getOrNull()
        return parseLinkProperties(linkProps)
    }

    fun observeStatus(context: Context): Flow<PrivateDnsStatus> = callbackFlow {
        trySend(getCurrentStatus(context))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            awaitClose { }
            return@callbackFlow
        }
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm == null) {
            awaitClose { }
            return@callbackFlow
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(getCurrentStatus(context))
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                trySend(parseLinkProperties(linkProperties))
            }

            override fun onLost(network: Network) {
                trySend(getCurrentStatus(context))
            }
        }
        val registered = runCatching {
            cm.registerDefaultNetworkCallback(callback)
            true
        }.getOrDefault(false)

        awaitClose {
            if (registered) {
                runCatching { cm.unregisterNetworkCallback(callback) }
            }
        }
    }.distinctUntilChanged()

    private fun parseLinkProperties(linkProps: LinkProperties?): PrivateDnsStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return PrivateDnsStatus(isSupported = false, mode = PrivateDnsMode.UNSUPPORTED)
        }
        if (linkProps == null) {
            return PrivateDnsStatus(isSupported = true, mode = PrivateDnsMode.OFF)
        }
        val isActive = linkProps.isPrivateDnsActive
        val server = linkProps.privateDnsServerName?.takeIf { it.isNotBlank() }
        val mode = when {
            !isActive -> PrivateDnsMode.OFF
            server != null -> PrivateDnsMode.STRICT_HOSTNAME
            else -> PrivateDnsMode.AUTOMATIC
        }
        return PrivateDnsStatus(
            isSupported = true,
            mode = mode,
            serverName = server
        )
    }

    fun openPrivateDnsSettings(context: Context) {
        val candidates = listOf(
            Intent(Settings.ACTION_WIRELESS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val started = runCatching {
                context.startActivity(intent)
                true
            }.getOrDefault(false)
            if (started) return
        }
    }
}
