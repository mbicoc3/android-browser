package com.example.feature.passwords.autofill

import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.example.core.common.UrlUtils
import com.example.feature.passwords.domain.DomainMatcher
import com.example.feature.passwords.model.WebFormType
import org.json.JSONObject

/**
 * Constrained WebView form inspector and user-triggered autofill injector.
 *  - On field focus: reports only form metadata (never input values).
 *  - On submit: reports candidate credential strictly for recognized password forms.
 *  - On explicit user pick: fills username/password using native value setter + input/change events,
 *    never auto-submitting the form.
 */
object WebFormInspector {

    const val BRIDGE_NAME = "OrbitPasswordBridge"

    class JsBridge(
        private val tabId: String,
        private val onFieldFocused: (tabId: String, pageUrl: String, formType: WebFormType, hasUser: Boolean, hasPass: Boolean) -> Unit,
        private val onFormSubmitted: (tabId: String, pageUrl: String, formType: WebFormType, username: String, password: String) -> Unit
    ) {
        @JavascriptInterface
        fun notifyFocus(pageUrl: String?, formTypeRaw: String?, hasUser: Boolean, hasPass: Boolean) {
            val url = pageUrl?.trim().orEmpty()
            if (DomainMatcher.parseOrigin(url) == null) return
            onFieldFocused(tabId, url, parseFormType(formTypeRaw), hasUser, hasPass)
        }

        @JavascriptInterface
        fun notifySubmit(pageUrl: String?, formTypeRaw: String?, username: String?, password: String?) {
            val url = pageUrl?.trim().orEmpty()
            if (DomainMatcher.parseOrigin(url) == null) return
            val pass = password.orEmpty()
            if (pass.isEmpty()) return
            onFormSubmitted(tabId, url, parseFormType(formTypeRaw), username?.trim().orEmpty(), pass)
        }

        private fun parseFormType(raw: String?): WebFormType = when (raw?.lowercase()) {
            "registration" -> WebFormType.REGISTRATION
            "password_change" -> WebFormType.PASSWORD_CHANGE
            else -> WebFormType.LOGIN
        }
    }

    fun attachFormDetector(webView: WebView, pageUrl: String) {
        if (UrlUtils.isStartPage(pageUrl)) return
        if (DomainMatcher.parseOrigin(pageUrl) == null) return
        webView.evaluateJavascript(ATTACH_SCRIPT, null)
    }

    fun fillCredentials(
        webView: WebView,
        expectedPageUrl: String,
        username: String,
        password: String
    ) {
        val currentOrigin = DomainMatcher.parseOrigin(webView.url)?.originKey
        val targetOrigin = DomainMatcher.parseOrigin(expectedPageUrl)?.originKey
        if (currentOrigin == null || currentOrigin != targetOrigin) return

        val quotedUser = JSONObject.quote(username)
        val quotedPass = JSONObject.quote(password)
        val script = """
            (function(uVal, pVal) {
              try {
                function setNativeValue(el, val) {
                  if (!el || val === null || val === undefined) return;
                  var proto = window.HTMLInputElement && window.HTMLInputElement.prototype;
                  var desc = proto ? Object.getOwnPropertyDescriptor(proto, 'value') : null;
                  if (desc && desc.set) {
                    desc.set.call(el, val);
                  } else {
                    el.value = val;
                  }
                  el.dispatchEvent(new Event('input', { bubbles: true }));
                  el.dispatchEvent(new Event('change', { bubbles: true }));
                }
                function isVisible(el) {
                  if (!el || el.disabled || el.readOnly) return false;
                  var st = window.getComputedStyle(el);
                  return st && st.display !== 'none' && st.visibility !== 'hidden';
                }
                var passInputs = Array.from(document.querySelectorAll('input[type="password"]')).filter(isVisible);
                var active = document.activeElement;
                var scope = (active && active.form) ? active.form : (passInputs[0] && passInputs[0].form ? passInputs[0].form : document);
                var passTarget = (active && active.type === 'password') ? active : passInputs.find(function(p){ return scope.contains(p); }) || passInputs[0];
                var userCandidates = Array.from(scope.querySelectorAll('input[type="email"],input[type="text"],input[type="tel"],input:not([type])')).filter(function(el) {
                  if (!isVisible(el)) return false;
                  var ac = (el.getAttribute('autocomplete') || '').toLowerCase();
                  var nm = (el.name || el.id || el.placeholder || '').toLowerCase();
                  return el.type === 'email' || ac === 'username' || ac === 'email' || /user|email|login|account|phone|id/.test(nm);
                });
                var userTarget = (active && active.tagName === 'INPUT' && active.type !== 'password') ? active : userCandidates[0];
                if (userTarget && uVal) setNativeValue(userTarget, uVal);
                if (passTarget && pVal) setNativeValue(passTarget, pVal);
              } catch (e) {}
            })($quotedUser, $quotedPass);
        """.trimIndent()
        webView.evaluateJavascript(script, null)
    }

    fun fillGeneratedPassword(
        webView: WebView,
        expectedPageUrl: String,
        generatedPassword: String
    ) {
        val currentOrigin = DomainMatcher.parseOrigin(webView.url)?.originKey
        val targetOrigin = DomainMatcher.parseOrigin(expectedPageUrl)?.originKey
        if (currentOrigin == null || currentOrigin != targetOrigin) return

        val quotedPass = JSONObject.quote(generatedPassword)
        val script = """
            (function(pVal) {
              try {
                function setNativeValue(el, val) {
                  var proto = window.HTMLInputElement && window.HTMLInputElement.prototype;
                  var desc = proto ? Object.getOwnPropertyDescriptor(proto, 'value') : null;
                  if (desc && desc.set) desc.set.call(el, val); else el.value = val;
                  el.dispatchEvent(new Event('input', { bubbles: true }));
                  el.dispatchEvent(new Event('change', { bubbles: true }));
                }
                var active = document.activeElement;
                var scope = (active && active.form) ? active.form : document;
                var passInputs = Array.from(scope.querySelectorAll('input[type="password"]')).filter(function(el) {
                  return !el.disabled && !el.readOnly;
                });
                if (passInputs.length > 0) {
                  passInputs.forEach(function(p) {
                    var ac = (p.getAttribute('autocomplete') || '').toLowerCase();
                    if (ac !== 'current-password' || passInputs.length === 1) {
                      setNativeValue(p, pVal);
                    }
                  });
                } else if (active && active.tagName === 'INPUT') {
                  setNativeValue(active, pVal);
                }
              } catch (e) {}
            })($quotedPass);
        """.trimIndent()
        webView.evaluateJavascript(script, null)
    }

    private val ATTACH_SCRIPT = """
        (function() {
          try {
            if (window.__orbitPwdAttached) return;
            window.__orbitPwdAttached = true;
            var bridge = window.$BRIDGE_NAME;
            if (!bridge) return;

            function classifyForm(root) {
              if (!root) return 'login';
              var passes = root.querySelectorAll('input[type="password"]');
              if (passes.length >= 3) return 'password_change';
              var hasCurrent = false, hasNew = false;
              passes.forEach(function(p) {
                var ac = (p.getAttribute('autocomplete') || '').toLowerCase();
                var nm = (p.name || p.id || '').toLowerCase();
                if (ac === 'current-password' || /old|current/.test(nm)) hasCurrent = true;
                if (ac === 'new-password' || /new|confirm|repeat/.test(nm)) hasNew = true;
              });
              if (hasCurrent && hasNew) return 'password_change';
              if (hasNew || passes.length >= 2) return 'registration';
              return 'login';
            }

            function isLoginLikeInput(el) {
              if (!el || el.tagName !== 'INPUT') return false;
              var t = (el.type || 'text').toLowerCase();
              if (t === 'password' || t === 'email') return true;
              if (t === 'text' || t === 'tel') {
                var ac = (el.getAttribute('autocomplete') || '').toLowerCase();
                var nm = (el.name || el.id || el.placeholder || '').toLowerCase();
                return ac === 'username' || ac === 'email' || /user|email|login|account/.test(nm);
              }
              return false;
            }

            function findUsernameValue(scope) {
              var inputs = scope.querySelectorAll('input[type="email"],input[type="text"],input[type="tel"],input:not([type])');
              for (var i = 0; i < inputs.length; i++) {
                var el = inputs[i];
                var v = (el.value || '').trim();
                if (!v) continue;
                var ac = (el.getAttribute('autocomplete') || '').toLowerCase();
                var nm = (el.name || el.id || el.placeholder || '').toLowerCase();
                if (el.type === 'email' || ac === 'username' || ac === 'email' || /user|email|login|account/.test(nm)) {
                  return v;
                }
              }
              return '';
            }

            function captureScope(scope) {
              if (!scope) return;
              var passes = Array.from(scope.querySelectorAll('input[type="password"]')).filter(function(p) {
                return (p.value || '').length > 0;
              });
              if (passes.length === 0) return;
              var chosenPass = passes[passes.length - 1].value;
              var userVal = findUsernameValue(scope);
              bridge.notifySubmit(window.location.href, classifyForm(scope), userVal, chosenPass);
            }

            document.addEventListener('focusin', function(e) {
              var target = e.target;
              if (!isLoginLikeInput(target)) return;
              var scope = target.form || document;
              var hasPass = scope.querySelector('input[type="password"]') !== null;
              var hasUser = scope.querySelector('input[type="email"],input[autocomplete="username"],input[type="text"]') !== null;
              if (!hasPass && target.type !== 'password') return;
              bridge.notifyFocus(window.location.href, classifyForm(scope), hasUser, hasPass);
            }, true);

            document.addEventListener('submit', function(e) {
              captureScope(e.target);
            }, true);

            document.addEventListener('click', function(e) {
              var btn = e.target && e.target.closest ? e.target.closest('button,[type="submit"],[role="button"]') : null;
              if (!btn) return;
              var scope = btn.form || (btn.closest ? btn.closest('form') : null);
              if (scope) captureScope(scope);
            }, true);
          } catch (err) {}
        })();
    """.trimIndent()
}
