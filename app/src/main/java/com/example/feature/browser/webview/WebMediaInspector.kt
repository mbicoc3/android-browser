package com.example.feature.browser.webview

import android.graphics.BitmapFactory
import android.webkit.WebView
import com.example.core.common.UrlUtils
import com.example.core.network.NetworkClientFactory
import com.example.feature.browser.model.DirectImageInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Request
import org.json.JSONObject

/**
 * Inspects pages loaded in WebView to detect direct image URLs (via DOM `document.contentType`
 * and `img.naturalWidth` / `img.naturalHeight`, with OkHttp `inJustDecodeBounds` fallback
 * when JavaScript is disabled).
 */
object WebMediaInspector {

    private val probeClient by lazy { NetworkClientFactory.createOkHttpClient() }
    private val ioScope = CoroutineScope(Dispatchers.IO)

    fun inspectDirectImage(
        webView: WebView,
        pageUrl: String,
        onResult: (DirectImageInfo?) -> Unit
    ) {
        if (UrlUtils.isStartPage(pageUrl)) {
            onResult(null)
            return
        }

        if (!webView.settings.javaScriptEnabled) {
            if (UrlUtils.isLikelyDirectImageUrl(pageUrl)) {
                probeViaNetworkBounds(webView, pageUrl, onResult)
            } else {
                onResult(null)
            }
            return
        }

        evaluateImageDom(webView, pageUrl, attemptsRemaining = 2, onResult = onResult)
    }

    private fun evaluateImageDom(
        webView: WebView,
        expectedUrl: String,
        attemptsRemaining: Int,
        onResult: (DirectImageInfo?) -> Unit
    ) {
        val script = """
            (function(){
              try {
                var ct = (document.contentType || '').toLowerCase();
                var imgs = document.images;
                var bodyChildren = document.body ? document.body.children : [];
                var isDirect = ct.indexOf('image/') === 0 ||
                  (imgs && imgs.length === 1 && bodyChildren.length === 1 && bodyChildren[0].tagName === 'IMG');
                if (!isDirect || !imgs || imgs.length === 0) {
                  return JSON.stringify({ isImage: false });
                }
                var img = imgs[0];
                return JSON.stringify({
                  isImage: true,
                  width: img.naturalWidth || 0,
                  height: img.naturalHeight || 0,
                  mimeType: ct || 'image/*',
                  src: img.currentSrc || img.src || location.href || ''
                });
              } catch (e) {
                return JSON.stringify({ isImage: false });
              }
            })();
        """.trimIndent()

        webView.evaluateJavascript(script) { raw ->
            val currentUrl = webView.url.orEmpty()
            if (currentUrl.isNotBlank() && currentUrl != expectedUrl) {
                return@evaluateJavascript
            }
            val parsed = parseInspectionResult(raw, expectedUrl)
            if (parsed == null) {
                if (UrlUtils.isLikelyDirectImageUrl(expectedUrl)) {
                    probeViaNetworkBounds(webView, expectedUrl, onResult)
                } else {
                    onResult(null)
                }
                return@evaluateJavascript
            }

            if (parsed.hasValidDimensions) {
                onResult(parsed)
            } else if (attemptsRemaining > 0) {
                webView.postDelayed(
                    {
                        if (webView.url == expectedUrl) {
                            evaluateImageDom(webView, expectedUrl, attemptsRemaining - 1, onResult)
                        }
                    },
                    220L
                )
            } else {
                probeViaNetworkBounds(webView, expectedUrl) { fallback ->
                    onResult(fallback ?: parsed)
                }
            }
        }
    }

    private fun probeViaNetworkBounds(
        webView: WebView,
        imageUrl: String,
        onResult: (DirectImageInfo?) -> Unit
    ) {
        ioScope.launch {
            val info = runCatching {
                val request = Request.Builder().url(imageUrl).get().build()
                probeClient.newCall(request).execute().use { response ->
                    val contentType = response.header("Content-Type")
                        ?.substringBefore(';')
                        ?.trim()
                        ?.lowercase()
                        .orEmpty()
                    if (!response.isSuccessful) return@use null
                    if (!contentType.startsWith("image/") && !UrlUtils.isLikelyDirectImageUrl(imageUrl)) {
                        return@use null
                    }
                    val body = response.body ?: return@use null
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    body.byteStream().use { stream ->
                        BitmapFactory.decodeStream(stream, null, options)
                    }
                    val w = options.outWidth
                    val h = options.outHeight
                    if (w > 0 && h > 0) {
                        DirectImageInfo(
                            url = imageUrl,
                            width = w,
                            height = h,
                            mimeType = contentType.ifBlank { options.outMimeType ?: "image/*" }
                        )
                    } else null
                }
            }.getOrNull()

            webView.post {
                if (webView.url == imageUrl) {
                    onResult(info)
                }
            }
        }
    }

    private fun parseInspectionResult(rawResult: String?, expectedUrl: String): DirectImageInfo? {
        if (rawResult.isNullOrBlank() || rawResult == "null") return null
        val jsonText = runCatching {
            if (rawResult.startsWith("\"")) {
                JSONObject("{\"v\":$rawResult}").getString("v")
            } else {
                rawResult
            }
        }.getOrNull() ?: return null

        return runCatching {
            val json = JSONObject(jsonText)
            if (!json.optBoolean("isImage", false)) return null
            DirectImageInfo(
                url = json.optString("src").ifBlank { expectedUrl },
                width = json.optInt("width", 0),
                height = json.optInt("height", 0),
                mimeType = json.optString("mimeType").ifBlank { "image/*" }
            )
        }.getOrNull()
    }

    fun extractFaviconUrls(
        webView: WebView,
        pageUrl: String,
        onResult: (List<String>) -> Unit
    ) {
        if (UrlUtils.isStartPage(pageUrl) || !webView.settings.javaScriptEnabled) return
        val script = """
            (function(){
              try {
                var out = [];
                var pushUnique = function(u){
                  if (u && typeof u === 'string' && out.indexOf(u) === -1) out.push(u);
                };
                var selectors = [
                  'link[rel="apple-touch-icon"]',
                  'link[rel="apple-touch-icon-precomposed"]',
                  'link[rel~="icon"]',
                  'link[rel="shortcut icon"]',
                  'link[rel="fluid-icon"]',
                  'meta[name="msapplication-TileImage"]'
                ];
                var items = [];
                selectors.forEach(function(sel, idx){
                  var nodes = document.querySelectorAll(sel);
                  for (var i = 0; i < nodes.length; i++) {
                    var el = nodes[i];
                    var u = el.href || el.content || el.getAttribute('href') || el.getAttribute('content') || '';
                    if (!u) continue;
                    var szAttr = (el.getAttribute('sizes') || '').toLowerCase();
                    var w = 0;
                    if (szAttr === 'any') w = 256;
                    else {
                      var m = szAttr.match(/(\d+)/);
                      if (m) w = parseInt(m[1], 10) || 0;
                    }
                    var isSvg = u.toLowerCase().indexOf('.svg') !== -1 || (el.type || '').toLowerCase().indexOf('svg') !== -1;
                    items.push({ u: u, score: (100 - idx * 10) + Math.min(w, 256) / 8 - (isSvg ? 40 : 0) });
                  }
                });
                items.sort(function(a, b){ return b.score - a.score; });
                items.forEach(function(it){ pushUnique(it.u); });
                return JSON.stringify(out.slice(0, 8));
              } catch (e) {
                return '[]';
              }
            })();
        """.trimIndent()

        webView.evaluateJavascript(script) { raw ->
            if (raw.isNullOrBlank() || raw == "null") return@evaluateJavascript
            val jsonArrayText = runCatching {
                if (raw.startsWith("\"")) JSONObject("{\"v\":$raw}").getString("v") else raw
            }.getOrNull() ?: return@evaluateJavascript
            val urls = runCatching {
                val arr = org.json.JSONArray(jsonArrayText)
                (0 until arr.length()).mapNotNull { idx -> arr.optString(idx).takeIf { it.isNotBlank() } }
            }.getOrDefault(emptyList())
            if (urls.isNotEmpty()) {
                onResult(urls)
            }
        }
    }
}
