package com.example.feature.settings.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.common.SearchEngine
import com.example.core.ui.components.OrbitPrimaryButton
import com.example.core.ui.components.OrbitSecondaryButton
import com.example.core.ui.components.OrbitSectionHeader
import com.example.core.ui.components.OrbitSettingSwitchRow
import com.example.core.ui.components.OrbitSurfaceCard
import com.example.core.ui.components.OrbitTopBar
import com.example.core.ui.theme.OrbitSpacing
import com.example.core.webengine.WebEngineBackend
import com.example.feature.browser.ui.WallpaperAdjustDialog
import com.example.feature.browser.ui.WallpaperAdjustInput
import com.example.feature.settings.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onOpenSiteData: () -> Unit,
    onOpenShield: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenPasswords: () -> Unit,
    onBack: () -> Unit
) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val crashDiagnostics by viewModel.crashDiagnostics.collectAsStateWithLifecycle()
    val privateDnsStatus by viewModel.privateDnsStatus.collectAsStateWithLifecycle()
    val dohTestState by viewModel.dohTestState.collectAsStateWithLifecycle()
    val statusMessage by viewModel.cleanupScheduledMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var adjustInput by remember { mutableStateOf<WallpaperAdjustInput?>(null) }
    val wallpaperPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            adjustInput = WallpaperAdjustInput.LocalUri(uri)
        }
    }

    LaunchedEffect(statusMessage) {
        if (!statusMessage.isNullOrBlank()) {
            snackbarHostState.showSnackbar(statusMessage!!)
            viewModel.clearStatusMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            OrbitTopBar(
                title = stringResource(R.string.settings_title),
                subtitle = "DataStore Preferences · WebKit Engine",
                onBackClick = onBack
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(OrbitSpacing.md),
            verticalArrangement = Arrangement.spacedBy(OrbitSpacing.sm)
        ) {
            item {
                OrbitSectionHeader(title = "Appearance & Theme")
            }
            item {
                OrbitSurfaceCard {
                    Column(modifier = Modifier.padding(vertical = OrbitSpacing.xs)) {
                        com.example.core.datastore.AppThemeMode.entries.forEach { mode ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = OrbitSpacing.md, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = prefs.themeMode == mode,
                                    onClick = { viewModel.setThemeMode(mode) },
                                    modifier = Modifier.testTag("theme_mode_${mode.name.lowercase()}")
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (mode == com.example.core.datastore.AppThemeMode.LIGHT) {
                                        "${mode.displayName} (Default)"
                                    } else {
                                        mode.displayName
                                    },
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }
                }
            }

            item {
                OrbitSectionHeader(title = stringResource(R.string.settings_start_bg_section))
            }
            item {
                val localWallpaper = remember(prefs.startWallpaperUri) {
                    if (prefs.startWallpaperUri.startsWith("/")) {
                        java.io.File(prefs.startWallpaperUri).takeIf { it.exists() }
                    } else null
                }
                OrbitSurfaceCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(OrbitSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(OrbitSpacing.sm)
                    ) {
                        Text(
                            text = when {
                                prefs.startBackgroundMode == com.example.core.datastore.StartBackgroundMode.SOLID ->
                                    "Solid Color"
                                localWallpaper != null ->
                                    "Custom Image (${com.example.core.common.FormatUtils.formatByteSize(localWallpaper.length())})"
                                prefs.startWallpaperUri == com.example.core.datastore.DEFAULT_START_WALLPAPER_URL ->
                                    "Default Image"
                                else ->
                                    "Custom Image URL"
                            },
                            style = MaterialTheme.typography.titleSmall
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(OrbitSpacing.xs)
                        ) {
                            OrbitSecondaryButton(
                                text = "Solid",
                                icon = Icons.Rounded.FormatColorFill,
                                contentPadding = PaddingValues(horizontal = 10.dp),
                                onClick = {
                                    viewModel.setStartBackgroundMode(com.example.core.datastore.StartBackgroundMode.SOLID)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("settings_bg_solid_button")
                            )
                            OrbitSecondaryButton(
                                text = stringResource(R.string.customize_pick_local_image),
                                icon = Icons.Rounded.Image,
                                contentPadding = PaddingValues(horizontal = 10.dp),
                                onClick = {
                                    wallpaperPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("settings_bg_local_button")
                            )
                        }
                        if (prefs.startWallpaperUri != com.example.core.datastore.DEFAULT_START_WALLPAPER_URL) {
                            OrbitSecondaryButton(
                                text = "Reset Default",
                                icon = Icons.Rounded.Refresh,
                                onClick = viewModel::resetWallpaperToDefault,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("settings_bg_reset_button")
                            )
                        }
                    }
                }
            }

            item {
                OrbitSectionHeader(title = stringResource(R.string.settings_search_engine))
            }
            item {
                OrbitSurfaceCard {
                    Column(modifier = Modifier.padding(vertical = OrbitSpacing.xs)) {
                        SearchEngine.entries.forEach { engine ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = OrbitSpacing.md, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = prefs.searchEngine == engine,
                                    onClick = { viewModel.setSearchEngine(engine) },
                                    modifier = Modifier.testTag("search_engine_${engine.id}")
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = engine.displayName,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = engine.homeUrl,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                OrbitSectionHeader(title = stringResource(R.string.settings_privacy))
            }
            item {
                OrbitSettingSwitchRow(
                    title = "Tracker & Ad Shield",
                    subtitle = "Intercept EasyPrivacy, AdGuard & custom rules in WebView",
                    icon = Icons.Rounded.Shield,
                    checked = prefs.blockTrackers,
                    onCheckedChange = viewModel::setBlockTrackers,
                    testTag = "setting_block_trackers"
                )
            }
            item {
                OrbitSettingSwitchRow(
                    title = "Cosmetic Element Filtering",
                    subtitle = "Hide ad placeholders & sponsored slots on pages",
                    icon = Icons.Rounded.AutoFixHigh,
                    checked = prefs.cosmeticFilteringEnabled,
                    onCheckedChange = viewModel::setCosmeticFilteringEnabled,
                    testTag = "setting_cosmetic_filtering"
                )
            }
            item {
                OrbitSurfaceCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(OrbitSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(OrbitSpacing.xs)
                    ) {
                        OrbitSecondaryButton(
                            text = "Manage Filter Lists (EasyPrivacy, AdGuard)",
                            icon = Icons.Rounded.Shield,
                            onClick = onOpenShield,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_open_shield_button")
                        )
                        OrbitSecondaryButton(
                            text = "Password Manager & Autofill Vault",
                            icon = Icons.Rounded.Key,
                            onClick = onOpenPasswords,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_open_passwords_button")
                        )
                    }
                }
            }
            item {
                OrbitSectionHeader(title = "DNS Privacy · DoH & Private DNS")
            }
            item {
                DnsPrivacySection(
                    selectedDohProvider = prefs.dohProvider,
                    dohTestState = dohTestState,
                    privateDnsStatus = privateDnsStatus,
                    onSelectDohProvider = viewModel::setDohProvider,
                    onTestDoh = viewModel::testDohResolution,
                    onOpenPrivateDnsSettings = viewModel::openPrivateDnsSettings,
                    onNotify = viewModel::reportStatusMessage
                )
            }
            item {
                OrbitSettingSwitchRow(
                    title = "Enable JavaScript",
                    subtitle = "Allow interactive web scripts on visited pages",
                    icon = Icons.Rounded.Code,
                    checked = prefs.javaScriptEnabled,
                    onCheckedChange = viewModel::setJavaScriptEnabled,
                    testTag = "setting_javascript"
                )
            }
            item {
                OrbitSettingSwitchRow(
                    title = "Clear History on Exit",
                    subtitle = "Automatically purge history when closing the app",
                    icon = Icons.Rounded.DeleteSweep,
                    checked = prefs.clearHistoryOnExit,
                    onCheckedChange = viewModel::setClearHistoryOnExit,
                    testTag = "setting_clear_on_exit"
                )
            }

            item {
                OrbitSectionHeader(title = stringResource(R.string.settings_engine))
            }
            item {
                OrbitSurfaceCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(OrbitSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Active Backend: ${WebEngineBackend.ANDROID_SYSTEM_WEBVIEW.displayName}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Wrapped behind WebEngineSession abstraction for clean future migration to Embedded Chromium / ContentShell.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item {
                OrbitSettingSwitchRow(
                    title = "Force Dark Web Pages",
                    subtitle = "Use AndroidX WebKit algorithmic darkening on bright sites",
                    icon = Icons.Rounded.DarkMode,
                    checked = prefs.forceDarkWeb,
                    onCheckedChange = viewModel::setForceDarkWeb,
                    testTag = "setting_force_dark_web"
                )
            }
            item {
                OrbitSettingSwitchRow(
                    title = "Desktop Mode by Default",
                    subtitle = "Open new tabs with desktop user-agent viewport",
                    icon = Icons.Rounded.DesktopWindows,
                    checked = prefs.desktopModeDefault,
                    onCheckedChange = viewModel::setDesktopModeDefault,
                    testTag = "setting_desktop_default"
                )
            }
            item {
                OrbitSettingSwitchRow(
                    title = "Tab Card Previews",
                    subtitle = "Capture lightweight thumbnails for Card View tabs (clears cache when off)",
                    icon = Icons.Rounded.PhotoLibrary,
                    checked = prefs.tabPreviewEnabled,
                    onCheckedChange = viewModel::setTabPreviewEnabled,
                    testTag = "setting_tab_previews"
                )
            }

            item {
                OrbitSectionHeader(title = stringResource(R.string.settings_maintenance))
            }
            item {
                OrbitSurfaceCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(OrbitSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(OrbitSpacing.sm)
                    ) {
                        Text(
                            text = "Inspect & selectively clear Cookies, Local Storage, Session Storage, IndexedDB, and Cache per site or globally.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OrbitSecondaryButton(
                            text = "Downloads (${prefs.downloadFolderLabel} · ${prefs.downloadChunkCount}x)",
                            icon = Icons.Rounded.Download,
                            onClick = onOpenDownloads,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_open_downloads_button")
                        )
                        OrbitSecondaryButton(
                            text = "Manage Site Data, Cookies & Cache",
                            icon = Icons.Rounded.Storage,
                            onClick = onOpenSiteData,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_open_site_data_button")
                        )
                        Text(
                            text = "Periodic WorkManager maintenance trims history older than 30 days and cleans temporary cache files.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OrbitPrimaryButton(
                            text = stringResource(R.string.settings_run_cleanup),
                            icon = Icons.Rounded.CleaningServices,
                            onClick = viewModel::triggerImmediateMaintenance,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("run_maintenance_button")
                        )
                    }
                }
            }

            item {
                OrbitSectionHeader(title = "Crash Diagnostics (Local Privacy-First)")
            }
            item {
                OrbitSurfaceCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(OrbitSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(OrbitSpacing.sm)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.BugReport,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Recorded Crash Logs: ${crashDiagnostics.logCount}",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Text(
                            text = crashDiagnostics.latestSummary
                                ?: "No uncaught crashes recorded. Stacktraces stay local in app-private storage (Sentry-compatible format).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (crashDiagnostics.logCount > 0) {
                            OrbitSecondaryButton(
                                text = "Clear Local Crash Logs",
                                isDestructive = true,
                                onClick = viewModel::clearCrashLogs,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("settings_clear_crash_logs_button")
                            )
                        }
                    }
                }
            }
        }
    }

    adjustInput?.let { pendingInput ->
        WallpaperAdjustDialog(
            input = pendingInput,
            onSaved = { savedPath ->
                adjustInput = null
                viewModel.applySavedExternalWallpaper(savedPath)
            },
            onError = { msg -> viewModel.reportStatusMessage(msg) },
            onDismiss = { adjustInput = null }
        )
    }
}
