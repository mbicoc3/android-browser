# Orbit Browser — Software Architecture

## 1. Architectural Pattern
Orbit implements a **Feature-Based + Registry-Driven Clean MVVM** architecture with unidirectional data flow:

```text
Compose UI (Screens & Components)
        │  observes StateFlow / sends user events
        ▼
ViewModels (feature/*/viewmodel)
        │  coordinates use-cases & state
        ▼
Repositories (feature/*/repository)
        │  abstracts local, network, crypto & background workers
        ▼
Core Infrastructure (core/database, core/datastore, core/network, core/worker, core/security)
```

---

## 2. Package Boundaries & Dependency Direction

- **`app/`** (`OrbitApplication`, `MainActivity`, `OrbitNavHost`)
  - Composition root only. Owns navigation wiring and `AppContainer` bootstrap.
- **`core/`** (Zero dependencies on `feature/*`)
  - `core/common/` — `UiState`, `UrlUtils`, `SearchEngine`, `FormatUtils`, `OrbitShareHelper`
  - `core/navigation/` — `BrowserRoutes` constants
  - `core/registry/` — `BrowserFeatureModule` & `BrowserToolRegistry`
  - `core/database/` — Room `OrbitDatabase` (v6), 10 Entities, 10 DAOs
  - `core/datastore/` — `BrowserPreferences` & `BrowserPreferencesDataStore`
  - `core/network/` — `NetworkClientFactory` (OkHttp + Moshi) & `SearchSuggestionApi` / `FilterListApi` (Retrofit)
  - `core/worker/` — `FileDownloadWorker`, `MaintenanceWorker` & `FilterListSyncWorker` (WorkManager)
  - `core/security/` — `KeystoreCryptoManager` (Android Keystore `AES/GCM/NoPadding`)
  - `core/webengine/` — `WebEngineSession`, `ContentBlockerEngine`, `AndroidWebViewSession` (engine abstraction for Chromium migration)
  - `core/telemetry/` — `OrbitCrashReporter` (privacy-first local crash stacktrace capture)
  - `core/ui/` — Shared `theme/` tokens and `components/`
  - `core/di/` — `AppContainer` manual dependency provider & ViewModel factories
- **`feature/*`** (Depends only on `core/` and explicit cross-feature repositories wired in `AppContainer`)
  - `feature/browser/` — WebView engine, Start Page, Omnibox, Find-in-Page, Tools Sheet
  - `feature/tabs/` — Tab Switcher (`Card View` / `List View`) with `TabPreviewStore` (`RGB_565` `LruCache`) for Standard & Incognito tabs
  - `feature/bookmarks/` — Local Room bookmarks + folder filter
  - `feature/history/` — Chronological visit timeline
  - `feature/offline/` — Saved offline `.mht` webpage archives (`WebView.saveWebArchive`)
  - `feature/downloads/` — Multi-chunk WorkManager download manager (`ChunkedDownloadEngine`, SAF folder picker, pre-download confirmation, pause/resume)
  - `feature/vault/` — Keystore-encrypted private notes & links
  - `feature/shield/` — Ad/Tracker & Cosmetic Shield (EasyPrivacy, AdGuard, Peter Lowe's, custom ABP rules, optional `adblock-rust` JNI)
  - `feature/sitedata/` — Per-site & global storage manager (Cookies, LocalStorage, SessionStorage, IndexedDB, Cache & Service Workers)
  - `feature/settings/` — DataStore-backed engine, privacy & crash diagnostics configuration
  - `feature/tools/` — Registry-powered tools directory

---

## 3. Web Engine Abstraction & Migration Readiness (`core/webengine/` + `feature/browser/webview/`)
- **`WebEngineSession` & `ContentBlockerEngine` (`core/webengine/`)**:
  - Decouples `BrowserScreen`, `OrbitWebViewPool`, and `OrbitFilterEngine` from `android.webkit.WebView` specifics.
  - Today `AndroidWebViewSession` adapts Android System WebView + AndroidX WebKit (`Tiny APK`, Play-updated Chromium security, low RAM).
  - Future migration to **Embedded Chromium / ContentShell** or **GeckoView** only requires swapping in a new `WebEngineSession` implementation and factory without rewriting ViewModels, Tabs, or Shield logic.
- **`OrbitWebViewFactory`**:
  - Configures `android.webkit.WebView` settings (JS, zoom, viewport, mixed content, CookieManager, custom desktop User-Agent, `OrbitSiteDataBridge`).
  - Applies **AndroidX WebKit** capabilities guarded by `WebViewFeature.isFeatureSupported(...)`:
    - `SAFE_BROWSING_ENABLE`
    - `ALGORITHMIC_DARKENING` / `FORCE_DARK`
    - `WEB_VIEW_RENDERER_CLIENT_BASIC_USAGE` (`WebViewRenderProcessClient` crash/unresponsive recovery)
    - `GET_COOKIE_INFO` & `DELETE_BROWSING_DATA` (`CookieManagerCompat`, `WebStorageCompat` in `WebViewSiteDataExecutor`)
  - Enforces **Incognito isolation**: disables DOM storage, database, caching (`LOAD_NO_CACHE`), and cookies.
  - Enforces **Ad & Cosmetic Shield**: delegates sub-resource filtering in `shouldInterceptRequest` to `ContentBlockerEngine` (`OrbitFilterEngine` / optional `adblock-rust` JNI) and injects `<style id="__orbit_cosmetic">` CSS selectors on page finish.
- **`OrbitWebViewPool`**:
  - Maintains live `WebEngineSession` / `WebView` instances keyed by `tabId` so switching tabs or visiting secondary screens preserves DOM and scroll state.
  - Destroys sessions cleanly via `destroyTab(tabId)` and `destroyAll()` to prevent memory leaks.

---

## 4. Persistence, Networking, Background Work & Security

### Room Database (`OrbitDatabase` v6)
| Entity | Table | Purpose |
|---|---|---|
| `BrowserTabEntity` | `browser_tabs` | Persists standard (non-incognito) open tabs |
| `BookmarkEntity` | `bookmarks` | Saved URLs with unique URL index & folder tag |
| `HistoryEntity` | `history` | Chronological page visits (indexed by `visitedAt`) |
| `OfflinePageEntity` | `offline_pages` | Saved `.mht` webpage archive metadata & file path |
| `DownloadEntity` | `downloads` | Download metadata, progress, speed, chunk count, SAF/MediaStore destination, WorkRequest UUID |
| `VaultNoteEntity` | `vault_notes` | Keystore-encrypted payload (`iv:ciphertext` Base64) |
| `SpeedDialEntity` | `speed_dial` | Start Page pinned Speed Dial shortcuts |
| `SiteRecordEntity` | `site_records` | Visited site origins & per-origin storage/cookie telemetry |
| `FilterListEntity` | `filter_lists` | Subscribed open-source lists (EasyPrivacy, AdGuard, Peter Lowe's) |
| `FilterRuleEntity` | `filter_rules` | User-defined ABP block, exception, and cosmetic CSS rules |

### DataStore (`BrowserPreferencesDataStore`)
Stores lightweight user settings reactively: `themeMode`, `startBackgroundMode`, `startWallpaperUri`, `searchEngine`, `homepageUrl`, `javaScriptEnabled`, `blockTrackers`, `cosmeticFilteringEnabled`, `forceDarkWeb`, `desktopModeDefault`, `clearHistoryOnExit`, `tabViewMode`, `tabPreviewEnabled`, `downloadFolderUri`, `downloadFolderLabel`, `confirmBeforeDownload`, `downloadChunkCount`, `downloadWifiOnly`, `lastShareComponent`, `lastShareAppLabel`.

### Retrofit + OkHttp (`NetworkClientFactory`)
- Provides shared `OkHttpClient` (timeouts, redirect handling, debug `HttpLoggingInterceptor`), high-throughput `createDownloadOkHttpClient()` (`maxRequestsPerHost = 16`), and `Moshi`.
- Powers `SearchSuggestionApi` for debounced live omnibox autocomplete and `FilterListApi` for streaming open-source filter list updates.

### WorkManager (`core/worker/` + `feature/shield/worker/`)
- `FileDownloadWorker`: Executes `ChunkedDownloadEngine` (parallel HTTP Range `FileChannel` writes + `.meta.json` pause/resume) and publishes finished files into SAF tree URI, `MediaStore.Downloads`, or app Downloads storage.
- `MaintenanceWorker`: Runs periodically every 24h (and on-demand from Settings) to prune history older than 30 days and clean temporary cache files.
- `FilterListSyncWorker`: Runs periodically every 7 days on unmetered networks (or manually from ShieldScreen) to refresh EasyPrivacy & AdGuard rules.

### Android Keystore (`KeystoreCryptoManager`)
- Generates and retrieves a 256-bit AES-GCM key stored in `AndroidKeyStore` (`orbit_browser_vault_key_v1`).
- Encrypts each entry with a fresh random IV (`AES/GCM/NoPadding`, 128-bit auth tag).

---

## 5. Registry-Driven Extensibility (`BrowserToolRegistry`)
New browser tools plug in without changing existing screens or project layout:
1. Create `feature/<new_tool>/` containing `repository/` (optional), `viewmodel/` (optional), and `ui/`.
2. Add a route key in `BrowserRoutes` and register a `BrowserFeatureModule` in `BrowserToolRegistry`.
3. Wire its `composable(...)` route in `OrbitNavHost` (and factory in `AppContainer`).
Tools automatically appear in the **Start Page Tools Strip**, **Tools Bottom Sheet**, and **Tools Hub**.
