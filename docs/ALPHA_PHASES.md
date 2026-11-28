# Orbit Browser — Alpha Version Roadmap & Phase Checklists

**Current Active Phase:** `Alpha 0.4 — Extensible Browser Tools` (`v0.4.1-alpha`, Build `5`)
**Baseline Build:** Alpha 0.1 – 0.3 Complete · Tab Previews & Image Collections Added

---

## Alpha 0.1 — Core Architecture, Theme & Navigation Shell (Completed)
**Goal:** Establish scalable feature-based structure, design system, and registry foundation.
- [x] Configure unique `applicationId`, manifest permissions, `FileProvider`, and `OrbitApplication`
- [x] Bundle local typography (`Plus Jakarta Sans`, `JetBrains Mono`) and generate custom Orbit adaptive icon + UI artwork
- [x] Create `core/ui/theme/` tokens (`Color`, `Type`, `Spacing`, `OrbitShapes`, `OrbitBrowserTheme`)
- [x] Create reusable `core/ui/components/` (`OrbitTopBar`, `OrbitEmptyState`, `OrbitSurfaceCard`, `OrbitSettingSwitchRow`, `OrbitBadgeChip`, `OrbitFilterField`)
- [x] Implement `BrowserRoutes`, `BrowserToolRegistry`, `AppContainer`, and `OrbitNavHost`
- [x] Document architecture & ownership in `docs/CODEBASE_REGISTRY.md`

---

## Alpha 0.2 — WebView Engine, Tabs, Bookmarks & History (Completed)
**Goal:** Deliver a responsive, multi-tab browser with Room persistence and live autocomplete.
- [x] Implement `OrbitWebViewFactory` with AndroidX WebKit (`SAFE_BROWSING_ENABLE`, `ALGORITHMIC_DARKENING`, `WebViewRenderProcessClient`)
- [x] Implement `OrbitWebViewPool` for tab state preservation and leak-free disposal
- [x] Build `StartPageContent` (hero banner, quick-search pill, Speed Dial, registered tools strip)
- [x] Build `BrowserOmniboxBar`, `BrowserBottomBar`, `FindInPageBar`, and `ToolsMenuSheet`
- [x] Integrate Retrofit + OkHttp + Moshi (`SearchSuggestionApi`) for debounced omnibox suggestions
- [x] Build `TabsScreen` supporting Standard tabs (persisted in Room) and Incognito tabs (ephemeral in memory)
- [x] Build `BookmarksScreen` with folder filter chips and add/delete flows
- [x] Build `HistoryScreen` with chronological date buckets, search, and clear-all confirmation

---

## Alpha 0.3 — Privacy, Keystore Vault, WorkManager Downloads & Settings (Completed)
**Goal:** Complete core privacy, background processing, encryption, and preferences infrastructure.
- [x] Implement `BrowserPreferencesDataStore` & `SettingsScreen` (search engine, tracker shield, JS, force dark web, desktop default, clear-on-exit)
- [x] Implement WebView Tracker Shield (`shouldInterceptRequest` blocking + per-tab blocked counter)
- [x] Implement `FileDownloadWorker` & `DownloadsScreen` with live OkHttp streaming progress, cancel/retry/delete, and `FileProvider` open
- [x] Implement `MaintenanceWorker` (24h periodic + manual trigger from Settings)
- [x] Implement `KeystoreCryptoManager` (`AndroidKeyStore` AES-256-GCM) + `PrivacyVaultScreen`
- [x] Implement `ToolsHubScreen` powered dynamically by `BrowserToolRegistry`

---

## Alpha 0.4 — Extensible Browser Tools (Active — Add One by One)
**Goal:** Incrementally add specialized browser tools into `feature/<tool>/` and register each in `BrowserToolRegistry`.
- [x] **Tool 1: Site Data Manager & Cookie/Storage Inspector** — Granular global and per-site management for Cookies (`CookieManagerCompat`), Local Storage, Session Storage, IndexedDB, and Cache/Service Workers (`WebStorageCompat` + offline same-origin probe)
- [ ] **Tool 2: Reader Mode** — Clean distraction-free article extraction view with font size & reading theme controls
- [x] **Tool 3: Ad/Tracker & Cosmetic Shield (`feature/shield/`)** — EasyPrivacy, AdGuard, Peter Lowe's WorkManager sync, cosmetic CSS element hiding, user-editable ABP rules in Room, `WebEngineSession` engine abstraction, and optional Brave `adblock-rust` JNI bridge + GitHub Actions CI & Renovate
- [x] **Tool 4: Offline Saved Pages (WebArchive)** — Save active pages via `WebView.saveWebArchive` and read offline (`feature/offline/`)
- [ ] **Tool 5: QR Code Link Scanner & Share Generator** — Scan URLs via CameraX/ML Kit or generate shareable QR links
- [ ] **Tool 6: Tab Groups & Session Snapshots** — Group open tabs by workspace and save/restore named tab collections
- [ ] **Alpha Stability & Robolectric Tests** — Add unit + Robolectric CUJ tests for `UrlUtils`, `KeystoreCryptoManager`, repositories, and navigation flows

---

## Exit Criteria for Alpha -> Beta Promotion
- Every file remains modular (<550 lines unless explicitly justified).
- Zero crashes on rotation, tab switching, external `VIEW` intents, or renderer termination.
- All Alpha 0.4 tools integrated cleanly through `BrowserToolRegistry` with passing tests and clean Gradle release build.
