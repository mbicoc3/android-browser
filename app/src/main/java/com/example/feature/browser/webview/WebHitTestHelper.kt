package com.example.feature.browser.webview

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.MotionEvent
import android.webkit.WebView
import com.example.feature.browser.model.ContextTargetKind
import com.example.feature.browser.model.WebContextMenuTarget
import org.json.JSONObject

/**
 * Attaches long-press hit-testing to an Android [WebView] while preserving native
 * text selection handles and floating Copy / Select All / Share action mode for plain text.
 */
object WebHitTestHelper {

    @SuppressLint("ClickableViewAccessibility")
    fun attach(
        webView: WebView,
        tabId: String,
        onContextMenuTarget: (WebContextMenuTarget) -> Unit
    ) {
        webView.isLongClickable = true
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.isHapticFeedbackEnabled = true

        var lastTouchX = 0f
        var lastTouchY = 0f

        webView.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN ||
                event.actionMasked == MotionEvent.ACTION_MOVE
            ) {
                lastTouchX = event.x
                lastTouchY = event.y
            }
            false
        }

        webView.setOnLongClickListener { view ->
            val targetView = view as? WebView ?: return@setOnLongClickListener false
            val hit = targetView.hitTestResult
            val kind = mapHitType(hit.type) ?: return@setOnLongClickListener false

            val touchX = lastTouchX
            val touchY = lastTouchY
            val fallbackExtra = hit.extra?.takeIf { it.isNotBlank() }

            resolveHitTarget(
                webView = targetView,
                tabId = tabId,
                kind = kind,
                fallbackExtra = fallbackExtra,
                touchX = touchX,
                touchY = touchY,
                onResolved = onContextMenuTarget
            )
            true
        }
    }

    fun selectTextAtPoint(webView: WebView, touchX: Float, touchY: Float) {
        val density = webView.resources.displayMetrics.density.coerceAtLeast(1f)
        val cssX = (touchX / density).toInt()
        val cssY = (touchY / density).toInt()
        val script = """
            (function(){
              try {
                var el = document.elementFromPoint($cssX, $cssY);
                if (!el) return;
                var target = el.closest('a, p, span, h1, h2, h3, h4, li, td, div') || el;
                var range = document.createRange();
                range.selectNodeContents(target);
                var sel = window.getSelection();
                if (sel) {
                  sel.removeAllRanges();
                  sel.addRange(range);
                }
              } catch (e) {}
            })();
        """.trimIndent()
        webView.evaluateJavascript(script, null)
    }

    fun selectAllText(webView: WebView) {
        val script = """
            (function(){
              try {
                if (document.body) {
                  var range = document.createRange();
                  range.selectNodeContents(document.body);
                  var sel = window.getSelection();
                  if (sel) {
                    sel.removeAllRanges();
                    sel.addRange(range);
                  }
                }
              } catch (e) {}
            })();
        """.trimIndent()
        webView.evaluateJavascript(script, null)
    }

    fun readSelectedText(webView: WebView, onResult: (String) -> Unit) {
        webView.evaluateJavascript("(function(){ return (window.getSelection() ? window.getSelection().toString() : ''); })();") { raw ->
            val cleaned = decodeJsString(raw).orEmpty().trim()
            onResult(cleaned)
        }
    }

    private fun mapHitType(hitType: Int): ContextTargetKind? = when (hitType) {
        WebView.HitTestResult.SRC_ANCHOR_TYPE -> ContextTargetKind.LINK
        WebView.HitTestResult.IMAGE_TYPE -> ContextTargetKind.IMAGE
        WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> ContextTargetKind.IMAGE_LINK
        WebView.HitTestResult.EMAIL_TYPE -> ContextTargetKind.EMAIL
        WebView.HitTestResult.PHONE_TYPE -> ContextTargetKind.PHONE
        WebView.HitTestResult.GEO_TYPE -> ContextTargetKind.GEO
        else -> null // Keep native text selection / ActionMode for UNKNOWN_TYPE & EDIT_TEXT_TYPE
    }

    private fun resolveHitTarget(
        webView: WebView,
        tabId: String,
        kind: ContextTargetKind,
        fallbackExtra: String?,
        touchX: Float,
        touchY: Float,
        onResolved: (WebContextMenuTarget) -> Unit
    ) {
        val density = webView.resources.displayMetrics.density.coerceAtLeast(1f)
        val cssX = (touchX / density).toInt()
        val cssY = (touchY / density).toInt()

        val hrefHandler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                val bundle = msg.data
                val hrefUrl = bundle.getString("url")?.takeIf { it.isNotBlank() }
                val imgSrc = bundle.getString("src")?.takeIf { it.isNotBlank() }
                val titleAttr = bundle.getString("title")?.takeIf { it.isNotBlank() }

                val js = """
                    (function(){
                      try {
                        var el = document.elementFromPoint($cssX, $cssY);
                        if (!el) return "{}";
                        var anchor = el.closest ? el.closest('a') : null;
                        var img = (el.tagName === 'IMG') ? el : (el.closest ? el.closest('img') : null);
                        if (!img && anchor && anchor.querySelector) {
                          img = anchor.querySelector('img');
                        }
                        var linkText = anchor ? (anchor.innerText || anchor.textContent || anchor.getAttribute('aria-label') || '') : (el.innerText || '');
                        linkText = linkText.replace(/\s+/g, ' ').trim();
                        return JSON.stringify({
                          href: anchor && anchor.href ? anchor.href : '',
                          linkText: linkText,
                          imgSrc: img && (img.currentSrc || img.src) ? (img.currentSrc || img.src) : '',
                          imgAlt: img && img.alt ? img.alt.trim() : ''
                        });
                      } catch (e) {
                        return "{}";
                      }
                    })();
                """.trimIndent()

                if (webView.settings.javaScriptEnabled) {
                    webView.evaluateJavascript(js) { rawJson ->
                        val parsed = parseJsMetadata(rawJson)
                        val resolvedLink = when (kind) {
                            ContextTargetKind.IMAGE -> null
                            ContextTargetKind.EMAIL -> normalizeScheme(parsed.href ?: hrefUrl ?: fallbackExtra, "mailto:")
                            ContextTargetKind.PHONE -> normalizeScheme(parsed.href ?: hrefUrl ?: fallbackExtra, "tel:")
                            ContextTargetKind.GEO -> normalizeScheme(parsed.href ?: hrefUrl ?: fallbackExtra, "geo:")
                            else -> parsed.href ?: hrefUrl ?: if (kind == ContextTargetKind.LINK) fallbackExtra else null
                        }
                        val resolvedImage = when (kind) {
                            ContextTargetKind.IMAGE, ContextTargetKind.IMAGE_LINK ->
                                parsed.imgSrc ?: imgSrc ?: fallbackExtra
                            else -> null
                        }
                        val resolvedLinkText = parsed.linkText ?: titleAttr
                        val resolvedKind = when {
                            resolvedLink != null && resolvedImage != null -> ContextTargetKind.IMAGE_LINK
                            else -> kind
                        }
                        onResolved(
                            WebContextMenuTarget(
                                tabId = tabId,
                                kind = resolvedKind,
                                linkUrl = resolvedLink,
                                linkText = resolvedLinkText,
                                imageUrl = resolvedImage,
                                imageAlt = parsed.imgAlt,
                                touchX = touchX,
                                touchY = touchY
                            )
                        )
                    }
                } else {
                    val resolvedLink = if (kind == ContextTargetKind.IMAGE) null else (hrefUrl ?: fallbackExtra)
                    val resolvedImage = if (kind == ContextTargetKind.IMAGE || kind == ContextTargetKind.IMAGE_LINK) {
                        imgSrc ?: fallbackExtra
                    } else null
                    onResolved(
                        WebContextMenuTarget(
                            tabId = tabId,
                            kind = kind,
                            linkUrl = resolvedLink,
                            linkText = titleAttr,
                            imageUrl = resolvedImage,
                            touchX = touchX,
                            touchY = touchY
                        )
                    )
                }
            }
        }

        val message = hrefHandler.obtainMessage()
        webView.requestFocusNodeHref(message)
    }

    private fun normalizeScheme(raw: String?, prefix: String): String? {
        val value = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return if (value.contains(":")) value else "$prefix$value"
    }

    private data class JsHitMeta(
        val href: String? = null,
        val linkText: String? = null,
        val imgSrc: String? = null,
        val imgAlt: String? = null
    )

    private fun parseJsMetadata(rawResult: String?): JsHitMeta {
        val unwrapped = decodeJsString(rawResult) ?: return JsHitMeta()
        return runCatching {
            val json = JSONObject(unwrapped)
            JsHitMeta(
                href = json.optString("href").takeIf { it.isNotBlank() },
                linkText = json.optString("linkText").takeIf { it.isNotBlank() },
                imgSrc = json.optString("imgSrc").takeIf { it.isNotBlank() },
                imgAlt = json.optString("imgAlt").takeIf { it.isNotBlank() }
            )
        }.getOrDefault(JsHitMeta())
    }

    private fun decodeJsString(rawResult: String?): String? {
        if (rawResult.isNullOrBlank() || rawResult == "null") return null
        return runCatching {
            if (rawResult.startsWith("\"")) {
                JSONObject("{\"v\":$rawResult}").getString("v")
            } else {
                rawResult
            }
        }.getOrNull()
    }
}
