package com.example.feature.sitedata.engine

import android.webkit.JavascriptInterface
import com.example.feature.sitedata.model.CacheStorageEntry
import com.example.feature.sitedata.model.IndexedDbEntry
import com.example.feature.sitedata.model.ServiceWorkerEntry
import com.example.feature.sitedata.model.StorageKeyValueEntry
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CompletableDeferred
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class LiveJsProbeSnapshot(
    val ok: Boolean = false,
    val localStorageEntries: List<StorageKeyValueEntry> = emptyList(),
    val localStorageCount: Int = 0,
    val localStorageBytes: Long = 0L,
    val sessionStorageEntries: List<StorageKeyValueEntry> = emptyList(),
    val sessionStorageCount: Int = 0,
    val sessionStorageBytes: Long = 0L,
    val indexedDbs: List<IndexedDbEntry> = emptyList(),
    val cacheBuckets: List<CacheStorageEntry> = emptyList(),
    val serviceWorkers: List<ServiceWorkerEntry> = emptyList(),
    val estimatedUsageBytes: Long = 0L,
    val estimatedQuotaBytes: Long = 0L
)

@JsonClass(generateAdapter = true)
internal data class JsProbePayloadDto(
    val ok: Boolean = false,
    val local: JsStorageBucketDto? = null,
    val session: JsStorageBucketDto? = null,
    val indexedDb: List<JsIndexedDbDto> = emptyList(),
    val caches: List<JsCacheDto> = emptyList(),
    val serviceWorkers: List<JsServiceWorkerDto> = emptyList(),
    val estUsage: Long = 0L,
    val estQuota: Long = 0L
)

@JsonClass(generateAdapter = true)
internal data class JsStorageBucketDto(
    val count: Int = 0,
    val totalBytes: Long = 0L,
    val items: List<JsKvItemDto> = emptyList()
)

@JsonClass(generateAdapter = true)
internal data class JsKvItemDto(
    val k: String = "",
    val v: String = "",
    val b: Long = 0L
)

@JsonClass(generateAdapter = true)
internal data class JsIndexedDbDto(
    val name: String = "unnamed",
    val version: Int = 0
)

@JsonClass(generateAdapter = true)
internal data class JsCacheDto(
    val name: String = "default",
    val count: Int = 0
)

@JsonClass(generateAdapter = true)
internal data class JsServiceWorkerDto(
    val scope: String = "",
    val scriptUrl: String = ""
)

/**
 * Nonce-authenticated bridge used ONLY to collect async JSON payloads from WebView
 * evaluateJavascript calls initiated by the native Kotlin SiteDataEngine.
 * Untrusted web pages cannot forge pending requestIds or trigger native actions.
 */
object OrbitSiteDataBridge {

    const val BRIDGE_NAME = "OrbitSiteBridge"

    private val pending = ConcurrentHashMap<String, CompletableDeferred<String>>()
    private val moshi: Moshi by lazy { Moshi.Builder().build() }
    private val payloadAdapter by lazy { moshi.adapter(JsProbePayloadDto::class.java) }

    fun registerRequest(): Pair<String, CompletableDeferred<String>> {
        val requestId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<String>()
        pending[requestId] = deferred
        return requestId to deferred
    }

    fun cancelRequest(requestId: String) {
        pending.remove(requestId)?.cancel()
    }

    fun createJsInterface(): JsReceiver = JsReceiver()

    class JsReceiver {
        @JavascriptInterface
        fun onResult(requestId: String?, payloadJson: String?) {
            if (requestId.isNullOrBlank()) return
            val deferred = pending.remove(requestId) ?: return
            deferred.complete(payloadJson ?: "{}")
        }
    }

    fun parseInspectPayload(jsonStr: String): LiveJsProbeSnapshot {
        return runCatching {
            val dto = payloadAdapter.fromJson(jsonStr) ?: return LiveJsProbeSnapshot()
            LiveJsProbeSnapshot(
                ok = dto.ok,
                localStorageEntries = dto.local?.items.orEmpty().map {
                    StorageKeyValueEntry(key = it.k, valuePreview = it.v, sizeBytes = it.b)
                },
                localStorageCount = dto.local?.count ?: 0,
                localStorageBytes = dto.local?.totalBytes ?: 0L,
                sessionStorageEntries = dto.session?.items.orEmpty().map {
                    StorageKeyValueEntry(key = it.k, valuePreview = it.v, sizeBytes = it.b)
                },
                sessionStorageCount = dto.session?.count ?: 0,
                sessionStorageBytes = dto.session?.totalBytes ?: 0L,
                indexedDbs = dto.indexedDb.map {
                    IndexedDbEntry(name = it.name, version = it.version)
                },
                cacheBuckets = dto.caches.map {
                    CacheStorageEntry(name = it.name, itemCount = it.count)
                },
                serviceWorkers = dto.serviceWorkers.map {
                    ServiceWorkerEntry(scope = it.scope, scriptUrl = it.scriptUrl)
                },
                estimatedUsageBytes = dto.estUsage,
                estimatedQuotaBytes = dto.estQuota
            )
        }.getOrDefault(LiveJsProbeSnapshot())
    }
}
