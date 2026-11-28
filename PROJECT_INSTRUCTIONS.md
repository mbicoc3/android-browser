# Project Instructions — Orbit Browser (Android)

## 1. Project Identity & Source of Truth
- **Application Name:** `Orbit` (`app_name` in `app/src/main/res/values/strings.xml`, `rootProject.name` in `settings.gradle.kts`, and `metadata.json`).
- **Application ID:** `com.mirazkbr.orbitbrowser` (do not alter `namespace = "com.example"` or package folder layout).
- **Platform & Tech Stack:**
  - **Language:** Kotlin exclusively
  - **UI:** Jetpack Compose (Material Design 3)
  - **Web Engine:** Android System WebView (`android.webkit.WebView`) wrapped behind `core/webengine/` (`WebEngineSession` + `ContentBlockerEngine`) for clean future Chromium/ContentShell migration
  - **WebView Compatibility:** AndroidX WebKit (`androidx.webkit`)
  - **Ad & Tracking Shield:** `OrbitFilterEngine` (EasyPrivacy, AdGuard, Peter Lowe's + CSS cosmetic filtering) with optional Brave `adblock-rust` JNI bridge (`native/adblock-rust/`)
  - **Local Database:** Room (`androidx.room`) via KSP
  - **Settings & Preferences:** Jetpack DataStore Preferences (`androidx.datastore:datastore-preferences`)
  - **Networking:** Retrofit + OkHttp + Moshi
  - **Background Tasks:** WorkManager (`androidx.work:work-runtime-ktx`)
  - **Encryption:** Android Keystore (`AES/GCM/NoPadding`)
  - **Crash Diagnostics:** Privacy-first local `OrbitCrashReporter` (`core/telemetry/`, Sentry-compatible)
  - **CI/CD & Dependency Updates:** GitHub Actions (`.github/workflows/android-ci.yml`) + Renovate (`.github/renovate.json`)
  - **Architecture:** Feature-Based + Registry-Driven Clean MVVM + Repository
- **Authoritative Version:** Defined in `app/build.gradle.kts` (`versionCode` and `versionName`). Never hardcode duplicated version strings in UI or source files.

---

## 2. Required Documentation
Read the relevant docs before making changes:
1. `PROJECT_INSTRUCTIONS.md` — always before any task.
2. `docs/ARCHITECTURE.md` — before adding/modifying modules, repositories, ViewModels, or WebView boundaries.
3. `docs/CODEBASE_REGISTRY.md` — before touching existing files; update it whenever file ownership, modules, or routes change.
4. `docs/THEME.md` — before creating or modifying any UI, theme tokens, typography, or shared components.
5. `docs/ALPHA_PHASES.md` — check active/upcoming Alpha phase tasks and exit criteria; mark `[x]` when a task completes.

---

## 3. Change Workflow
For every feature or fix:
1. Inspect the relevant `feature/` or `core/` module and check `docs/CODEBASE_REGISTRY.md` and `docs/ALPHA_PHASES.md`.
2. Confirm scope and keep changes within the target feature module.
3. Make the smallest cohesive change in the owning module rather than scattering logic across `MainActivity.kt` or unrelated screens.
4. Reuse shared UI components (`core/ui/components/`) and shared theme tokens (`core/ui/theme/`). Avoid raw hardcoded colors, font sizes, or duplicated UI patterns.
5. Keep every Kotlin file **under 550 lines** whenever practical. If a file approaches or exceeds 550 lines, split it into focused sub-files (e.g., toolbar, sheet, sub-components, model, repository) unless splitting breaks cohesion or safety.
6. Verify compilation (`compile_applet` / Gradle build) and unit tests when applicable.
7. Update `docs/CODEBASE_REGISTRY.md` and `docs/ALPHA_PHASES.md` only when structure or phase task completion actually changes.

---

## 4. Design & Engineering Priorities
Priority order:
1. **Correctness & Data Safety** — No data loss, safe WebView & file handling, strict incognito isolation.
2. **Reliability & Responsiveness** — Never run disk, database, network, or crypto operations on the main UI thread.
3. **Low CPU/RAM Usage & Fast Startup** — Clean WebView pooling (`OrbitWebViewPool`) and proper `destroy()` on tab close.
4. **Maintainable Modular Architecture** — Plug-and-play feature modules registered in `BrowserToolRegistry`.
5. **Accessible, Polished Android UI** — Edge-to-edge layouts, 48dp minimum touch targets, semantic content descriptions, and Compose `testTag` modifiers.
6. **Small APK Footprint** — Avoid unnecessary libraries and keep unused template dependencies commented out.

---

## 5. Architecture & Modularity Rules
- Keep **UI**, **ViewModel**, **Repository**, **Database/DataStore**, **Networking**, **Workers**, **Security**, and **WebView Engine** strictly separated.
- **Large Module File Separation:** Do not keep a large module or feature inside a single monolithic file. Split every major feature (`feature/<module>/`) into dedicated separate files by responsibility (`model/`, `repository/`, `viewmodel/`, `ui/`, and focused sub-components/sheets/workers), keeping every file under **550 lines**.
- No circular dependencies between `feature/*` modules. Shared contracts live in `core/`.
- `MainActivity.kt` stays minimal: edge-to-edge setup, incoming intent routing, theme binding, and `OrbitNavHost` hosting.
- New browser tools must follow the **Registry Pattern**:
  - Implement under `feature/<tool_name>/`
  - Register a `BrowserFeatureModule` in `BrowserToolRegistry`
  - Add route in `BrowserRoutes` and destination in `OrbitNavHost`
  - Register factory in `AppContainer`

---

## 6. UI & Jetpack Compose Rules
- **Single Centralized Common Theme:** All theme tokens (`Color.kt`, `Type.kt`, `Spacing.kt`, `Theme.kt`) and shared UI components (`core/ui/components/`) must remain in one centralized place (`core/ui/`). Never duplicate theme files or scatter raw hex colors, font sizes, or one-off theme overrides inside individual `feature/*` modules. Light Theme (`AppThemeMode.LIGHT`) is the default.
- Production UI uses **Jetpack Compose + Material 3** exclusively (no XML screen layouts).
- Mandatory `enableEdgeToEdge()` and proper `WindowInsets` (`statusBars`, `navigationBars`, `ime` / `adjustResize`).
- Bundled local fonts (`Plus Jakarta Sans` and `JetBrains Mono` in `res/font/`) are the sole typography source—never use runtime downloadable fonts.
- All interactive controls must include `Modifier.testTag("...")` in `snake_case` and meaningful `contentDescription` values.
- Never include dead-end UI buttons or mock/placeholder features; every visible control must perform real work.

---

## 7. Security, Privacy & WebView Safety Rules
- **Incognito Isolation:** Incognito tabs must never write to Room `browser_tabs` or `history`, must disable DOM storage/database persistence, disable WebView caching (`LOAD_NO_CACHE`), and reject cookies.
- **AndroidX WebKit Guarding:** Always check `WebViewFeature.isFeatureSupported(...)` before invoking modern WebKit APIs (`SAFE_BROWSING_ENABLE`, `ALGORITHMIC_DARKENING`, `WEB_VIEW_RENDERER_CLIENT_BASIC_USAGE`).
- **Safe Browsing & External Schemes:** Restrict WebView navigation to `http`, `https`, and `about`; delegate external schemes safely with `try/catch` (`runCatching`).
- **Tracker Shield:** When enabled in DataStore, intercept known tracker sub-resource requests in `shouldInterceptRequest` without breaking main-frame navigation.
- **Encryption:** All sensitive user vault items must be encrypted via `KeystoreCryptoManager` (`AndroidKeyStore`, `AES/GCM/NoPadding`, 256-bit key) before persisting in Room. Never store plaintext secrets or hardcoded keys.
- **File Downloads:** Run file transfers in background via WorkManager (`FileDownloadWorker`) + OkHttp into app-scoped storage and open files safely via `FileProvider`.

---

## 8. Dependency Policy
- Prefer standard AndroidX/Jetpack and mature Kotlin-first libraries already vetted in `gradle/libs.versions.toml`.
- Keep all unused dependencies commented out in `app/build.gradle.kts` to minimize APK size and build time.
- Never hardcode API keys in code or `local.properties`.

---

## 9. Definition of Done
A task is complete only when:
- Changes stay within the planned feature scope and module boundaries.
- All modified files remain modular and under 550 lines (unless explicitly justified).
- `OrbitBrowserTheme` tokens and reusable `core/ui/components/` are used consistently.
- Build compiles cleanly with zero errors.
- `docs/CODEBASE_REGISTRY.md` and `docs/ALPHA_PHASES.md` accurately reflect the current codebase.
