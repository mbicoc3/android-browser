# Orbit Browser — Design System & Theme Specification

**Source of Truth:** `app/src/main/java/com/example/core/ui/theme/` and `app/src/main/java/com/example/core/ui/components/`

---

## 1. Visual Direction
Orbit follows a **Clean Neutral & Electric Cyan** aesthetic tailored for distraction-free mobile web browsing:
- **Default Theme:** **Light Theme is the default** (`AppThemeMode.LIGHT`), with full support for **Dark Theme** (`AppThemeMode.DARK`) and **System Default** (`AppThemeMode.SYSTEM`).
- **Start Page Background Modes:** Supports **Solid Color** (white-grey `#F2F3F5` in Light theme, medium-dark neutral grey `#2B2B2E` in Dark theme—never bluish grey) and **Image Background** (customizable from `HomeCustomizeSheet` via Photo Picker or URL) with a **transparent bottom bar**.
- **Mode Awareness:** Automatically adapts between **Light Theme**, **Dark Theme**, and **Incognito Stealth Theme** (`#A78BFA` primary accent).

---

## 2. Color Tokens (`core/ui/theme/Color.kt`)

### Light Palette (Default)
| Token | Hex | Usage |
|---|---|---|
| `OrbitLightBackground` | `#F3F4F5` | Screen scaffold background (white-grey) |
| `OrbitLightSolidStartBg` | `#F2F3F5` | Start Page solid background (white-grey) |
| `OrbitLightSurface` | `#FFFFFF` | Bars, sheets, dialogs |
| `OrbitLightSurfaceVariant` | `#E6E7EA` | Cards, omnibox pill |
| `OrbitLightOnSurface` | `#18181B` | Primary text |
| `OrbitLightOnSurfaceVariant` | `#52525B` | Secondary captions |
| `OrbitLightPrimary` | `#0284C7` | Primary accent |
| `OrbitLightSecondary` | `#4F46E5` | Secondary accent |
| `OrbitLightEmerald` | `#059669` | HTTPS & security indicators |
| `OrbitLightError` | `#E11D48` | Error states |

### Neutral Dark Palette (Medium-Dark Neutral Grey — Non-Bluish)
| Token | Hex | Usage |
|---|---|---|
| `OrbitDarkBackground` | `#242426` | Screen scaffold background |
| `OrbitDarkSolidStartBg` | `#2B2B2E` | Start Page solid background (neutral dark grey) |
| `OrbitDarkSurface` | `#2C2C2F` | Top/bottom bars, sheets, dialogs |
| `OrbitDarkSurfaceVariant` | `#36363A` | Cards, omnibox pill, filter fields |
| `OrbitDarkSurfaceElevated` | `#404044` | High-emphasis containers |
| `OrbitDarkOnSurface` | `#F4F4F5` | Primary headings & body text |
| `OrbitDarkOnSurfaceVariant` | `#B4B4BC` | URLs, timestamps, secondary captions |
| `OrbitDarkOutline` | `#525258` | Hairline card & pill borders |
| `OrbitCyan` | `#38BDF8` | Primary brand accent & active actions |
| `OrbitCyanContainer` | `#0C4A6E` | Active pills, chips, desktop toggle highlight |
| `OrbitIndigo` | `#818CF8` | Secondary accent (history, tools) |
| `OrbitEmerald` | `#34D399` | HTTPS lock badge, Tracker Shield, AES-GCM vault |
| `OrbitIncognitoPurple` | `#A78BFA` | Incognito mode accent & tab border |
| `OrbitIncognitoSurface` | `#1D1829` | Incognito toolbar & shell surface |
| `OrbitRoseError` | `#FB7185` | Failed downloads & destructive alerts |

> **Rule:** Never hardcode raw `Color(0xFF...)` values inside feature screens. Always read from `MaterialTheme.colorScheme` or semantic tokens in `Color.kt`.

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

## 4. Spacing, Shapes & Accessibility (`core/ui/theme/Spacing.kt`)
- **Spacing Scale (`OrbitSpacing`):** `xs = 4.dp`, `sm = 8.dp`, `md = 16.dp`, `lg = 24.dp`, `xl = 32.dp`.
- **Minimum Touch Target:** `48.dp` (`OrbitSpacing.touchTargetMin`).
- **Shape Tokens (`OrbitShapes`):**
  - `extraSmall`: `8.dp` (badge chips)
  - `small`: `12.dp` (compact tiles)
  - `medium`: `16.dp` (standard cards & tool tiles)
  - `large`: `22.dp` (hero banners, filter fields)
  - `extraLarge`: `28.dp` (omnibox & floating search pills)
- **Window Insets:**
  - `BrowserOmniboxBar` applies `WindowInsets.statusBars`.
  - `BrowserBottomBar` and `ToolsMenuSheet` apply `.navigationBarsPadding()`.
  - Screens use `Scaffold` `innerPadding` for safe edge-to-edge rendering.

---

## 5. Reusable UI Components (`core/ui/components/`)
Always check and reuse these before building new UI primitives:
- `OrbitControls` (`OrbitControlTokens`) — Uniform `48.dp` height, `16.dp` corner radius (`14.dp` for segment pills), `0.9.dp` hairline borders across `OrbitPrimaryButton`, `OrbitSecondaryButton`, `OrbitGhostButton`, `OrbitSegmentPill`, `OrbitActionPill`, and `54.dp` min-height `OrbitTextField` (`#2E2E2E` family dark surfaces / light-grey `#E9ECF2`–`#F5F6FA` surfaces).
- `OrbitDialog` & `OrbitConfirmDialog` — Unified `28.dp` radius spatial modal container with `palette.canvas` fill, hairline border, top ambient highlight + radial glow, `40.dp` squircle icon header, bounded scrollable body, and equal-width `48.dp` footer buttons.
- `OrbitPopupMenu` & `OrbitPopupMenuItem` — Unified `22.dp` radius popup menu with `48.dp` min-height rows and `32.dp` squircle icon badges.
- `OrbitBottomSheet` & `OrbitBottomSheetItems` — Unified `32.dp` top-radius bottom sheet system (`OrbitModalBottomSheet`, `OrbitSheetHeader`, `OrbitSheetPanel`, `OrbitSheetBentoTile`, `OrbitSheetActionRow`, `OrbitSheetSwitchRow`, `OrbitSheetStatusPill`).
- `OrbitTopBar` — Standard back navigation bar with optional subtitle and trailing actions.
- `OrbitEmptyState` — Centered empty state featuring bundled `img_empty_state` illustration and optional CTA `OrbitPrimaryButton`.
- `OrbitSectionHeader` — Consistent section title styled in primary accent.
- `OrbitSurfaceCard` — Tonal card with hairline border and optional click ripple.
- `OrbitSettingSwitchRow` — Icon + title + subtitle + M3 `Switch` row for settings.
- `OrbitBadgeChip` — Compact pill badge for folder tags, encryption labels, and status pills.
- `OrbitFilterField` — Standard `OrbitTextField`-backed search/filter bar with clear button.

---

## 6. Visual Assets (`res/drawable/` & `res/mipmap-*`)
- **Launcher Icon:** Adaptive icon (`#07152F` background + centered Orbit orbital compass foreground) with raster PNG fallbacks across `mdpi`–`xxxhdpi`.
- **Start Page Hero:** `img_home_hero_1795536368553.jpg` (`16:9`) rendered with a vertical slate scrim for crisp text legibility.
- **Empty State Artwork:** `img_empty_state_1795536369734.jpg` (`4:3`) used across empty Bookmarks, History, Downloads, and Privacy Vault views.
