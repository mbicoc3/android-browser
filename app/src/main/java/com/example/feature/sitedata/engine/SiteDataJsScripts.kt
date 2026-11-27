package com.example.feature.sitedata.engine

/**
 * Generates self-contained, error-isolated JavaScript probes & selective clear commands.
 * Async Promise results (IndexedDB, Cache API, Service Workers) are returned securely via
 * OrbitSiteBridge using a single-use cryptographic requestId.
 */
object SiteDataJsScripts {

    private fun quoteJsString(value: String): String = buildString(value.length + 2) {
        append('"')
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '<' -> append("\\u003C")
                '>' -> append("\\u003E")
                else -> {
                    if (ch.code < 0x20) {
                        append(String.format("\\u%04x", ch.code))
                    } else {
                        append(ch)
                    }
                }
            }
        }
        append('"')
    }

    fun buildInspectScript(requestId: String): String {
        val quotedId = quoteJsString(requestId)
        return """
            (function() {
              const reqId = $quotedId;
              function deliver(obj) {
                try {
                  if (window.OrbitSiteBridge && typeof window.OrbitSiteBridge.onResult === 'function') {
                    window.OrbitSiteBridge.onResult(reqId, JSON.stringify(obj));
                  }
                } catch (e) {}
              }
              function readStorage(store) {
                const items = [];
                let totalBytes = 0;
                try {
                  if (!store) return { count: 0, totalBytes: 0, items: [] };
                  const len = store.length || 0;
                  for (let i = 0; i < len; i++) {
                    const k = store.key(i);
                    if (k === null || k === undefined) continue;
                    const v = store.getItem(k) || '';
                    const bytes = (k.length + v.length) * 2;
                    totalBytes += bytes;
                    if (items.length < 100) {
                      items.push({
                        k: String(k),
                        v: v.length > 140 ? v.slice(0, 140) + '…' : String(v),
                        b: bytes
                      });
                    }
                  }
                  return { count: len, totalBytes: totalBytes, items: items };
                } catch (e) {
                  return { count: 0, totalBytes: 0, items: [] };
                }
              }
              (async function() {
                const timeout = new Promise(resolve => setTimeout(() => resolve('timeout'), 2400));
                const work = (async function() {
                  const local = readStorage(window.localStorage);
                  const session = readStorage(window.sessionStorage);
                  let idbs = [];
                  try {
                    if (window.indexedDB && typeof window.indexedDB.databases === 'function') {
                      const list = await window.indexedDB.databases();
                      idbs = (list || []).map(d => ({
                        name: String(d.name || 'unnamed'),
                        version: Number(d.version || 0)
                      }));
                    }
                  } catch (e) {}
                  let cachesList = [];
                  try {
                    if (window.caches && typeof window.caches.keys === 'function') {
                      const names = await window.caches.keys();
                      for (let i = 0; i < names.length; i++) {
                        let cnt = 0;
                        try {
                          const c = await window.caches.open(names[i]);
                          const keys = await c.keys();
                          cnt = keys.length;
                        } catch (e2) {}
                        cachesList.push({ name: String(names[i]), count: cnt });
                      }
                    }
                  } catch (e) {}
                  let swList = [];
                  try {
                    if (navigator.serviceWorker && typeof navigator.serviceWorker.getRegistrations === 'function') {
                      const regs = await navigator.serviceWorker.getRegistrations();
                      swList = (regs || []).map(r => ({
                        scope: String(r.scope || ''),
                        scriptUrl: String((r.active && r.active.scriptURL) || (r.waiting && r.waiting.scriptURL) || (r.installing && r.installing.scriptURL) || '')
                      }));
                    }
                  } catch (e) {}
                  let estUsage = 0;
                  let estQuota = 0;
                  try {
                    if (navigator.storage && typeof navigator.storage.estimate === 'function') {
                      const est = await navigator.storage.estimate();
                      estUsage = Number(est.usage || 0);
                      estQuota = Number(est.quota || 0);
                    }
                  } catch (e) {}
                  return {
                    ok: true,
                    local: local,
                    session: session,
                    indexedDb: idbs,
                    caches: cachesList,
                    serviceWorkers: swList,
                    estUsage: estUsage,
                    estQuota: estQuota
                  };
                })();
                const res = await Promise.race([work, timeout]);
                if (res === 'timeout') {
                  deliver({
                    ok: true,
                    local: readStorage(window.localStorage),
                    session: readStorage(window.sessionStorage),
                    indexedDb: [],
                    caches: [],
                    serviceWorkers: [],
                    estUsage: 0,
                    estQuota: 0
                  });
                } else {
                  deliver(res);
                }
              })();
            })();
        """.trimIndent()
    }

    fun buildClearLocalStorageScript(requestId: String, singleKey: String?): String {
        val quotedId = quoteJsString(requestId)
        val quotedKey = if (singleKey != null) quoteJsString(singleKey) else "null"
        return """
            (function() {
              const reqId = $quotedId;
              const targetKey = $quotedKey;
              let cleared = 0;
              try {
                if (window.localStorage) {
                  if (targetKey !== null) {
                    window.localStorage.removeItem(targetKey);
                    cleared = 1;
                  } else {
                    cleared = window.localStorage.length || 0;
                    window.localStorage.clear();
                  }
                }
              } catch (e) {}
              try {
                if (window.OrbitSiteBridge && typeof window.OrbitSiteBridge.onResult === 'function') {
                  window.OrbitSiteBridge.onResult(reqId, JSON.stringify({ ok: true, cleared: cleared }));
                }
              } catch (e) {}
            })();
        """.trimIndent()
    }

    fun buildClearSessionStorageScript(requestId: String, singleKey: String?): String {
        val quotedId = quoteJsString(requestId)
        val quotedKey = if (singleKey != null) quoteJsString(singleKey) else "null"
        return """
            (function() {
              const reqId = $quotedId;
              const targetKey = $quotedKey;
              let cleared = 0;
              try {
                if (window.sessionStorage) {
                  if (targetKey !== null) {
                    window.sessionStorage.removeItem(targetKey);
                    cleared = 1;
                  } else {
                    cleared = window.sessionStorage.length || 0;
                    window.sessionStorage.clear();
                  }
                }
              } catch (e) {}
              try {
                if (window.OrbitSiteBridge && typeof window.OrbitSiteBridge.onResult === 'function') {
                  window.OrbitSiteBridge.onResult(reqId, JSON.stringify({ ok: true, cleared: cleared }));
                }
              } catch (e) {}
            })();
        """.trimIndent()
    }

    fun buildClearIndexedDbScript(requestId: String, singleDbName: String?): String {
        val quotedId = quoteJsString(requestId)
        val quotedDb = if (singleDbName != null) quoteJsString(singleDbName) else "null"
        return """
            (function() {
              const reqId = $quotedId;
              const targetDb = $quotedDb;
              function deliver(obj) {
                try {
                  if (window.OrbitSiteBridge && typeof window.OrbitSiteBridge.onResult === 'function') {
                    window.OrbitSiteBridge.onResult(reqId, JSON.stringify(obj));
                  }
                } catch (e) {}
              }
              function deleteDb(name) {
                return new Promise(resolve => {
                  try {
                    const req = window.indexedDB.deleteDatabase(name);
                    let settled = false;
                    const done = (status) => {
                      if (!settled) { settled = true; resolve(status); }
                    };
                    req.onsuccess = () => done('deleted');
                    req.onerror = () => done('error');
                    req.onblocked = () => done('blocked');
                    setTimeout(() => done('timeout'), 1500);
                  } catch (e) {
                    resolve('error');
                  }
                });
              }
              (async function() {
                let cleared = 0;
                let blocked = 0;
                try {
                  if (window.indexedDB) {
                    let names = [];
                    if (targetDb !== null) {
                      names = [targetDb];
                    } else if (typeof window.indexedDB.databases === 'function') {
                      const dbs = await window.indexedDB.databases();
                      names = (dbs || []).map(d => d.name).filter(Boolean);
                    }
                    for (let i = 0; i < names.length; i++) {
                      const st = await deleteDb(names[i]);
                      if (st === 'deleted') cleared++;
                      else if (st === 'blocked') blocked++;
                    }
                  }
                } catch (e) {}
                deliver({ ok: true, cleared: cleared, blocked: blocked });
              })();
            })();
        """.trimIndent()
    }

    fun buildClearCacheStorageScript(
        requestId: String,
        singleCacheName: String?,
        unregisterSw: Boolean
    ): String {
        val quotedId = quoteJsString(requestId)
        val quotedCache = if (singleCacheName != null) quoteJsString(singleCacheName) else "null"
        return """
            (function() {
              const reqId = $quotedId;
              const targetCache = $quotedCache;
              const doUnregisterSw = $unregisterSw;
              function deliver(obj) {
                try {
                  if (window.OrbitSiteBridge && typeof window.OrbitSiteBridge.onResult === 'function') {
                    window.OrbitSiteBridge.onResult(reqId, JSON.stringify(obj));
                  }
                } catch (e) {}
              }
              (async function() {
                let clearedCaches = 0;
                let unregisteredSw = 0;
                try {
                  if (window.caches) {
                    if (targetCache !== null) {
                      const ok = await window.caches.delete(targetCache);
                      if (ok) clearedCaches = 1;
                    } else if (typeof window.caches.keys === 'function') {
                      const names = await window.caches.keys();
                      for (let i = 0; i < names.length; i++) {
                        if (await window.caches.delete(names[i])) clearedCaches++;
                      }
                    }
                  }
                } catch (e) {}
                try {
                  if (doUnregisterSw && navigator.serviceWorker && typeof navigator.serviceWorker.getRegistrations === 'function') {
                    const regs = await navigator.serviceWorker.getRegistrations();
                    for (let i = 0; i < regs.length; i++) {
                      if (await regs[i].unregister()) unregisteredSw++;
                    }
                  }
                } catch (e) {}
                deliver({ ok: true, clearedCaches: clearedCaches, unregisteredSw: unregisteredSw });
              })();
            })();
        """.trimIndent()
    }
}
