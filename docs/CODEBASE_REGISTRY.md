# Orbit Browser — Codebase Registry & Module Architecture

## Overview
Orbit is built on a **Feature-Based + Registry-Driven Clean MVVM** architecture designed so future tools and browser capabilities can be added without altering the project structure.

## Directory & Module Ownership

### 1. `core/` (Shared Infrastructure & Design System)
- `core/common/` — `UiState`, `UrlUtils`, `SearchEngine`, `FormatUtils`, `OrbitShareHelper`
- `core/navigation/` — `BrowserRoutes` (single source of truth for navigation keys)
- `core/registry/` — `BrowserFeatureModule` & `BrowserToolRegistry` (plug-in discovery for tools & modules)
- `core/database/` — Room `OrbitDatabase` (v9), entities (`BookmarkEntity`, `HistoryEntity`, `BrowserTabEntity`, `DownloadEntity`, `OfflinePageEntity`, `VaultNoteEntity`, `SpeedDialEntity`, `SiteRecordEntity`, `FilterListEntity`, `FilterRuleEntity`, `PasswordVaultMetadataEntity`, `PasswordCredentialEntity`, `PasswordFolderEntity`, `NeverSaveDomainEntity`, `SandboxEntity`), and DAOs
- `core/datastore/` — `BrowserPreferences` & `BrowserPreferencesDataStore`
- `core/network/` — `NetworkClientFactory` (OkHttp + Moshi), `SearchSuggestionApi`, & `FilterListApi` (Retrofit)
- `core/worker/` — WorkManager `FileDownloadWorker` & `MaintenanceWorker`
- `core/security/` — `KeystoreCryptoManager` (Android Keystore `AES/GCM/NoPadding`)
- `core/webengine/` — `WebEngineContracts` (`WebEngineSession`, `ContentBlockerEngine`) & `AndroidWebViewSession`
- `core/telemetry/` — `OrbitCrashReporter` (privacy-first local crash diagnostics)
- `core/ui/theme/` — `Color.kt`, `Type.kt` (`Plus Jakarta Sans` + `JetBrains Mono`), `Spacing.kt`, `Theme.kt`
- `core/ui/components/` — Reusable Compose components (`OrbitTopBar`, `OrbitEmptyState`, `OrbitSurfaceCard`, `OrbitSettingSwitchRow`, `OrbitBadgeChip`, `OrbitFilterField`, `OrbitControls` [`OrbitPrimaryButton`, `OrbitSecondaryButton`, `OrbitGhostButton`, `OrbitTextField`, `OrbitSegmentPill`, `OrbitActionPill`], `OrbitDialog` / `OrbitConfirmDialog`, `OrbitPopupMenu` / `OrbitPopupMenuItem`, `OrbitBottomSheet` / `OrbitBottomSheetItems`)
- `core/di/` — `AppContainer` manual dependency container and ViewModel factories

### 2. `feature/` (Self-Contained Feature Modules)
- `feature/browser/` — WebView engine (`OrbitPullRefreshWebView`, `OrbitWebViewFactory` + AndroidX WebKit, `OrbitWebViewPool`, `WebHitTestHelper`, `WebMediaInspector`), `WebContextMenuTarget`, `OmniboxSmartHelper`, `WallpaperCropMath`, `WallpaperStorageHelper`, `BrowserRepository`, `BrowserViewModel`, `BrowserScreen`, `BrowserScreenCallbacks`, `BrowserOverlays`, `StartPageContent`, `StartPageComponents`, `HomeCustomizeSheet`, `WallpaperAdjustDialog`, `BrowserToolbar`, `BrowserPageMoreMenu`, `OmniboxMultiPurposePanel`, `FindInPageBar`, `ToolsMenuSheet`, `WebContextMenuSheet`
- `feature/tabs/` — `TabPreviewStore` (memory-bounded `RGB_565` `LruCache`), `TabPreviewViewport`, `TabItemCards` (`TabGridCard` + `TabListRow`), `TabsScreen` (Card & List view switcher for Standard & Incognito tabs)
- `feature/bookmarks/` — `BookmarkRepository`, `BookmarksViewModel`, `BookmarksScreen`
- `feature/history/` — `HistoryRepository`, `HistoryViewModel`, `HistoryScreen`
- `feature/offline/` — `OfflinePagesRepository`, `OfflinePagesViewModel`, `OfflinePagesScreen` (complete `.mht` web archives)
- `feature/downloads/` — `DownloadModels`, `DownloadFolderHelper` (SAF + MediaStore + remote HTTP probing), `ChunkedDownloadEngine` (parallel HTTP Range + pause/resume checkpointing), `DownloadRepository`, `DownloadsViewModel`, `DownloadItemCard`, `DownloadConfirmSheet`, `DownloadSettingsSheet`, `DownloadsScreen`
- `feature/vault/` — `VaultRepository`, `VaultViewModel`, `PrivacyVaultScreen` (Keystore AES-GCM encrypted notes)
- `feature/shield/` — `ShieldModels`, `BuiltInFilterRules`, `NativeAdblockBridge`, `OrbitFilterEngine`, `FilterListApi`, `FilterListSyncWorker`, `ShieldRepository`, `ShieldViewModel`, `ShieldComponents`, `ShieldScreen`
- `feature/sitedata/` — `SiteDataModels`, `SiteDataJsScripts`, `OrbitSiteDataBridge`, `WebViewCookieStore`, `WebViewSiteDataExecutor`, `SiteDataRepository`, `SiteDataViewModel`, `SiteDataScreen`, `SiteDataOverviewCards`, `SiteDetailView`
- `feature/sandbox/` — `SandboxModels`, `SandboxProfileEngine` (AndroidX WebKit `MULTI_PROFILE` / `ProfileStore` isolation), `SandboxRepository`, `SandboxesViewModel`, `SandboxDialogs`, `SandboxesScreen`
- `feature/passwords/` — Local Argon2id + AES-256-GCM Password Vault, Biometric/Phone Lock gate (`VaultGateScreen`), `PasswordsScreen`, `CredentialsListAndDetail`, `CredentialDialogs`, `PasswordSecurityAndBackupTabs`, `VaultSettingsTab`, `PasswordGeneratorAndProtonTabs`, `BrowserPasswordPrompts`
- `feature/settings/` — `SettingsViewModel`, `DnsPrivacySection`, `SettingsScreen`
- `feature/tools/` — `ToolsHubScreen` (renders all registered modules from `BrowserToolRegistry`)

### 3. `app/`, Native Bridge & CI/CD
- `OrbitApplication.kt` — Installs `OrbitCrashReporter`, initializes `AppContainer`, and schedules WorkManager maintenance + filter sync
- `MainActivity.kt` — Edge-to-edge host, intent handling, and `OrbitWebViewPool` lifecycle
- `app/OrbitNavHost.kt` — Navigation Compose graph
- `native/adblock-rust/` — Optional Brave `adblock-rust` Cargo crate & JNI bridge (`liborbit_adblock_rust.so`)
- `.github/workflows/android-ci.yml` & `.github/renovate.json` — Free GitHub Actions CI and Renovate dependency automation

## How to Add a New Browser Tool in 3 Steps
1. Create `feature/<new_tool>/` with its `repository/`, `viewmodel/`, and `ui/` files (keeping each file under 550 lines).
2. Add its route constant in `BrowserRoutes` and register a `BrowserFeatureModule` in `BrowserToolRegistry`.
3. Add its `composable(...)` destination in `OrbitNavHost.kt` (and factory in `AppContainer.kt` if it needs a ViewModel).
