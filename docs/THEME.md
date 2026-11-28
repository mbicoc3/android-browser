# Orbit Browser — Design System & Theme Specification

**Source of Truth:** `app/src/main/java/com/example/core/ui/theme/` and `app/src/main/java/com/example/core/ui/components/`

---

## 1. Visual Direction
Orbit follows a **Microsoft Edge Mobile-inspired Clean Neutral & Cobalt Blue** aesthetic tailored for distraction-free mobile web browsing:
- **Default Theme:** **Light Theme is the default** (`AppThemeMode.LIGHT`), with full support for **Dark Theme** (`AppThemeMode.DARK`) and **System Default** (`AppThemeMode.SYSTEM`).
- **Surface Hierarchy (No Pastel Background Floods):**
  - Sheets, menus, and dialogs sit on crisp white (`#FFFFFF`) in Light mode and neutral charcoal (`#1F2024`) in Dark mode.
  - Grouped panels and bento tiles use subtle neutral containers (`#F4F5F7` / `#ECEEF2` Light, `#2A2B30` / `#32343A` Dark) with `0.8.dp` hairlines and **borderless `CircleShape` icon wells**.
  - Cobalt Blue (`#0078D4` Light / `#3AA0F3` Dark) is reserved for interactive accents, focus rings, switches, and selection borders—not large pastel cyan background washes.
- **Start Page Background Modes:** Supports **Solid Color** (`#F5F6F8` Light, `#1C1D20` Dark) and **Image Background** (customizable from `HomeCustomizeSheet` via Photo Picker or URL) with a **transparent bottom bar**.
- **Mode Awareness:** Adapts seamlessly across **Light Theme**, **Dark Theme**, and **Incognito Stealth Theme** (`#A78BFA` primary accent).

---

## 2. Color Tokens (`core/ui/theme/Color.kt`)

### Light Palette (Default)
| Token | Hex | Usage |
|---|---|---|
| `OrbitLightBackground` | `#F5F6F8` | Screen scaffold background |
| `OrbitLightSolidStartBg` | `#F5F6F8` | Start Page solid background |
| `OrbitLightSurface` | `#FFFFFF` | Bars, sheets, dialogs |
| `OrbitLightSurfaceVariant` | `#ECEEF2` | Omnibox pill, circular icon wells |
| `OrbitLightOnSurface` | `#1A1B1E` | Primary text |
| `OrbitLightOnSurfaceVariant` | `#5C5F66` | Secondary captions |
| `OrbitLightPrimary` | `#0078D4` | Cobalt Blue primary accent |
| `OrbitLightPrimaryContainer` | `#E7F2FC` | Subtle tonal badge accent |
| `OrbitLightSecondary` | `#4F46E5` | Secondary accent |
| `OrbitLightEmerald` | `#107C41` | HTTPS & security indicators |
| `OrbitLightError` | `#D13438` | Error & destructive actions |

### Neutral Dark Palette (Charcoal — Zero Muddy Blue Wash)
| Token | Hex | Usage |
|---|---|---|
| `OrbitDarkBackground` | `#141517` | Screen scaffold background |
| `OrbitDarkSolidStartBg` | `#1C1D20` | Start Page solid background |
| `OrbitDarkSurface` | `#1E1F23` | Top/bottom bars, dialogs |
| `OrbitDarkSurfaceVariant` | `#2A2B30` | Cards, panels, filter fields |
| `OrbitDarkSurfaceElevated` | `#34363C` | Elevated containers |
| `OrbitDarkOnSurface` | `#F4F5F7` | Primary headings & body text |
| `OrbitDarkOnSurfaceVariant` | `#B2B5BD` | URLs, timestamps, secondary captions |
| `OrbitDarkOutline` | `#3D4047` | Hairline card & pill borders |
| `OrbitCyan` | `#3AA0F3` | Dark Cobalt Blue primary accent |
| `OrbitEmerald` | `#34D399` | HTTPS lock badge, Tracker Shield, AES-GCM vault |
| `OrbitIncognitoPurple` | `#A78BFA` | Incognito mode accent & tab border |
| `OrbitIncognitoSurface` | `#1C1A26` | Incognito toolbar & shell surface |
| `OrbitRoseError` | `#F87171` | Failed downloads & destructive alerts |

### Centralized Surface Palette (`OrbitSurfacePalette` / `rememberOrbitSurfacePalette()`)
- **Light Sheet/Panel/Tile:** `canvas = #FFFFFF`, `panel = #F4F5F7`, `tile = #ECEEF2`, `activeFill = #FFFFFF`, `hairline = #14000000`
- **Dark Sheet/Panel/Tile:** `canvas = #1F2024`, `panel = #2A2B30`, `tile = #32343A`, `activeFill = #363840`, `hairline = #18FFFFFF`
- **Semantic Tokens:** `OrbitGradientIndigo`, `OrbitSpeedDialFallbackBg`, `OrbitSpeedDialDarkInk`, `OrbitLightDotInactive`, `OrbitWallpaperScrimTop/Mid/Low/Bottom`

> **Rule:** Never hardcode raw `Color(0x...)` values inside feature screens. Always read from `MaterialTheme.colorScheme`, `rememberOrbitSurfacePalette()`, or semantic tokens in `Color.kt`.

---

## 3. Typography (`core/ui/theme/Type.kt`)
Fonts are bundled locally in `app/src/main/res/font/`:
- **UI & Display Family:** `Plus Jakarta Sans` (`R.font.plus_jakarta_sans`)
- **Monospace / Technical Family:** `JetBrains Mono` (`R.font.jetbrains_mono`)

| Style | Font | Size / Line Height | Usage |
|---|---|---|---|
| `headlineLarge` | Plus Jakarta Sans Bold | `28.sp / 34.sp` | Major display headers |
| `headlineSmall` | Plus Jakarta Sans SemiBold | `22.sp / 28.sp` | Start Page hero greeting |
| `titleLarge` | Plus Jakarta Sans SemiBold | `18.sp / 24.sp` | TopAppBar & sheet titles |
| `titleMedium` | Plus Jakarta Sans Medium | `16.sp / 22.sp` | Card titles, bookmark/history items |
| `bodyLarge` | Plus Jakarta Sans Normal | `15.sp / 22.sp` | Search pill & primary body copy |
| `bodyMedium` | Plus Jakarta Sans Normal | `14.sp / 20.sp` | Suggestions & descriptions |
| `bodySmall` | Plus Jakarta Sans Normal | `12.sp / 16.sp` | Subtitles & helper text |
| `labelLarge` | Plus Jakarta Sans SemiBold | `14.sp / 20.sp` | Section headers & buttons |
| `labelSmall` | JetBrains Mono Medium | `11.sp / 15.sp` | Tab counts, badges, timestamps |
| `OrbitMonoStyle` | JetBrains Mono Normal | `13.sp / 18.sp` | Omnibox URL input & encrypted vault preview |

---

## 4. Spacing, Shapes & Window Insets (`core/ui/theme/Spacing.kt` & `OrbitControlTokens`)
- **Spacing Scale (`OrbitSpacing`):** `xs = 4.dp`, `sm = 8.dp`, `md = 16.dp`, `lg = 24.dp`, `xl = 32.dp`, `touchTargetMin = 48.dp`.
- **Control Metrics (`OrbitControlTokens`):**
  - `ControlHeight = 40.dp`, `InputMinHeight = 42.dp`, `CapsuleHeight = 32.dp`, `OmniboxHeight = 40.dp`, `StartSearchHeight = 52.dp`
  - `ControlCornerRadius = 12.dp`, `SegmentCornerRadius = 16.dp`, `CardCornerRadius = 16.dp`, `PanelCornerRadius = 16.dp`, `DialogCornerRadius = 22.dp`, `PopupCornerRadius = 16.dp`, `SheetCornerRadius = 24.dp`, `FabCornerRadius = 16.dp`, `HairlineWidth = 0.8.dp`
- **Window Insets:**
  - `BrowserOmniboxBar` applies `WindowInsets.statusBars`.
  - `BrowserBottomBar` and `OrbitModalBottomSheet` apply `.navigationBarsPadding()`.
  - Screens use `Scaffold` `innerPadding` for safe edge-to-edge rendering.

---

## 5. Centralized Reusable UI Components (`core/ui/components/`)
Always reuse these centralized primitives instead of raw Material controls in `feature/*`:
- **`OrbitControls.kt`:** `OrbitControlTokens`, `OrbitSurfacePalette`, `rememberOrbitSurfacePalette()`, `OrbitPrimaryButton`, `OrbitSecondaryButton`, `OrbitGhostButton`, `OrbitFloatingActionButton`, `OrbitExtendedFloatingActionButton`, `OrbitTextField`, `OrbitSearchPillSurface`.
- **`OrbitPills.kt`:** `OrbitSegmentPill`, `OrbitActionPill`, `OrbitBadgeChip`, `OrbitSheetStatusPill`.
- **`OrbitSelectionControls.kt`:** `OrbitSwitch`, `OrbitCompactSwitchTile`, `OrbitSettingSwitchRow`, `OrbitCheckboxRow`, `OrbitRadioOptionRow`, `OrbitSlider`.
- **`OrbitCards.kt`:** `OrbitSectionHeader`, `OrbitSurfaceCard`, `OrbitSelectableOptionCard`, `OrbitNoticeBanner`.
- **`OrbitBottomSheet.kt` & `OrbitBottomSheetItems.kt`:** `OrbitModalBottomSheet`, `OrbitSheetHeader`, `OrbitSheetKicker`, `OrbitSheetPanel`, `OrbitSheetSegmentPill`, `OrbitSheetPagerTabs`, `OrbitSheetHairlineDivider`, `OrbitSheetPrimaryButton`, `OrbitSheetSecondaryButton`, `OrbitSheetBentoTile` (borderless `38.dp` `CircleShape` icon well), `OrbitSheetActionRow`, `OrbitSheetSwitchRow`.
- **`OrbitPopupMenu.kt`:** `OrbitPopupMenu`, `OrbitPopupMenuItem` (circular `28.dp` icon well), `OrbitPopupMenuDivider`.
- **`OrbitDialog.kt`:** `OrbitDialog`, `OrbitConfirmDialog`.
- **`OrbitCommonComponents.kt`:** `OrbitTopBar`, `OrbitEmptyState`, `OrbitFilterField`.

---

## 6. Visual Assets (`res/drawable/` & `res/mipmap-*`)
- **Launcher Icon:** Adaptive icon (`#07152F` background + centered Orbit orbital compass foreground) with raster PNG fallbacks across `mdpi`–`xxxhdpi`.
- **Start Page Hero:** `img_home_hero_1795536368553.jpg` (`16:9`) rendered with semantic wallpaper scrim tokens.
- **Empty State Artwork:** `img_empty_state_1795536369734.jpg` (`4:3`) used across empty Bookmarks, History, Downloads, and Privacy Vault views.
