package com.example.feature.browser.ui

import android.content.Context
import android.net.Uri
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.common.UrlUtils
import com.example.core.datastore.BrowserPreferences
import com.example.core.registry.BrowserFeatureModule
import com.example.feature.browser.model.BrowserTabModel
import com.example.feature.browser.model.WebContextMenuTarget
import com.example.feature.browser.viewmodel.BrowserViewModel
import com.example.feature.browser.webview.OrbitWebViewPool
import com.example.feature.browser.webview.WebHitTestHelper
import com.example.feature.downloads.model.PendingDownloadRequest
import com.example.feature.downloads.ui.DownloadConfirmSheet
import com.example.feature.passwords.autofill.BrowserPasswordCoordinator
import com.example.feature.passwords.crypto.VaultBiometricKeystoreHelper
import com.example.feature.passwords.provider.ProtonPassIntegration
import com.example.feature.passwords.repository.LocalVaultRepository
import com.example.feature.passwords.ui.BrowserAutofillPromptSheet
import com.example.feature.passwords.ui.SaveOrUpdateCredentialDialog
import com.example.feature.sandbox.engine.SandboxProfileEngine
import com.example.feature.sandbox.model.SandboxModel
import com.example.feature.sandbox.model.SandboxPresets
import com.example.feature.sandbox.ui.CreateEditSandboxDialog
import com.example.feature.sandbox.ui.SandboxPickerDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun BrowserOverlaysHost(
    context: Context,
    activeTab: BrowserTabModel?,
    isStartPage: Boolean,
    preferences: BrowserPreferences,
    viewModel: BrowserViewModel,
    webViewPool: OrbitWebViewPool,
    snackbarHostState: SnackbarHostState,
    coroutineScope: CoroutineScope,
    showToolsSheet: Boolean,
    onDismissToolsSheet: () -> Unit,
    sandboxes: List<SandboxModel> = emptyList(),
    showSandboxPicker: Boolean = false,
    onDismissSandboxPicker: () -> Unit = {},
    showCreateSandboxDialog: Boolean = false,
    onShowCreateSandboxDialog: () -> Unit = {},
    onDismissCreateSandboxDialog: () -> Unit = {},
    onOpenSandboxesManager: () -> Unit = {},
    contextMenuTarget: WebContextMenuTarget?,
    onDismissContextMenu: () -> Unit,
    pendingDownload: PendingDownloadRequest?,
    onDismissPendingDownload: () -> Unit,
    onToggleBlockTrackers: (Boolean) -> Unit,
    onToggleCosmeticFiltering: (Boolean) -> Unit,
    onSaveBookmark: (title: String, url: String) -> Unit,
    onDownloadFolderPicked: (Uri) -> Unit,
    onConfirmBeforeDownloadChanged: (Boolean) -> Unit,
    onRequestDownload: (url: String, contentDisposition: String?, mimeType: String?) -> Unit,
    onEnqueueDownload: (
        url: String,
        fileName: String,
        mimeType: String?,
        chunkCount: Int,
        folderUri: String,
        folderLabel: String,
        wifiOnly: Boolean
    ) -> Unit,
    onModuleAction: (BrowserFeatureModule) -> Unit,
    passwordCoordinator: BrowserPasswordCoordinator,
    localVaultRepository: LocalVaultRepository,
    biometricHelper: VaultBiometricKeystoreHelper,
    protonPassIntegration: ProtonPassIntegration
) {
    var pendingWallpaperAdjust by remember { mutableStateOf<WallpaperAdjustInput?>(null) }
    val autofillPrompt by passwordCoordinator.autofillPrompt.collectAsStateWithLifecycle()
    val pendingSavePrompt by passwordCoordinator.pendingSavePrompt.collectAsStateWithLifecycle()
    val vaultSecuritySettings by localVaultRepository.securitySettings.collectAsStateWithLifecycle()

    if (showToolsSheet) {
        ToolsMenuSheet(
            isDesktopMode = activeTab?.isDesktopMode == true,
            isPageActive = !isStartPage,
            blockTrackers = preferences.blockTrackers,
            cosmeticFilteringEnabled = preferences.cosmeticFilteringEnabled,
            blockedTrackersCount = activeTab?.blockedTrackersCount ?: 0,
            onToggleBlockTrackers = onToggleBlockTrackers,
            onToggleCosmeticFiltering = onToggleCosmeticFiltering,
            onReloadActivePage = viewModel::reloadOrStop,
            onDismiss = onDismissToolsSheet,
            onModuleClick = onModuleAction
        )
    }

    contextMenuTarget?.let { target ->
        WebContextMenuSheet(
            target = target,
            onOpenInCurrentTab = { url -> viewModel.loadUrlInActiveTab(url) },
            onOpenInNewTab = { url, isIncognito -> viewModel.openUrlInNewTab(url, isIncognito) },
            onSelectText = {
                webViewPool.getIfPresent(target.tabId)?.let { webView ->
                    WebHitTestHelper.selectTextAtPoint(webView, target.touchX, target.touchY)
                }
            },
            onSearchText = { query ->
                val searchUrl = UrlUtils.resolveInputToUrl(query, preferences.searchEngine)
                viewModel.openUrlInNewTab(searchUrl)
            },
            onDownloadUrl = { url ->
                onRequestDownload(url, null, null)
            },
            onSetAsBackgroundImage = { imageUrl ->
                pendingWallpaperAdjust = WallpaperAdjustInput.WebUrl(imageUrl)
            },
            onSaveBookmark = onSaveBookmark,
            onPinToHome = { title, url ->
                viewModel.addSpeedDialShortcut(title, url)
            },
            onShowMessage = { msg ->
                coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
            },
            onDismiss = onDismissContextMenu
        )
    }

    pendingDownload?.let { request ->
        DownloadConfirmSheet(
            initialUrl = request.url,
            initialFileName = request.suggestedFileName,
            initialMimeType = request.mimeType,
            preferences = preferences,
            allowEditingUrl = false,
            onFolderPicked = onDownloadFolderPicked,
            onConfirmBeforeDownloadChanged = onConfirmBeforeDownloadChanged,
            onConfirmStart = { url, fileName, mimeType, chunkCount, folderUri, folderLabel, wifiOnly ->
                onEnqueueDownload(
                    url,
                    fileName,
                    mimeType,
                    chunkCount,
                    folderUri,
                    folderLabel,
                    wifiOnly
                )
                onDismissPendingDownload()
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Download queued: $fileName (${chunkCount}x chunks)")
                }
            },
            onDismiss = onDismissPendingDownload
        )
    }

    pendingWallpaperAdjust?.let { input ->
        WallpaperAdjustDialog(
            input = input,
            onSaved = { savedExternalPath ->
                pendingWallpaperAdjust = null
                viewModel.updateStartWallpaperUri(savedExternalPath)
            },
            onError = { errMsg ->
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        "${context.getString(R.string.msg_background_failed)}: $errMsg"
                    )
                }
            },
            onDismiss = { pendingWallpaperAdjust = null }
        )
    }

    if (showSandboxPicker) {
        val currentUrl = activeTab?.url.orEmpty()
        SandboxPickerDialog(
            currentUrl = currentUrl,
            activeSandboxId = activeTab?.sandboxId,
            sandboxes = sandboxes,
            isMultiProfileSupported = remember { SandboxProfileEngine.isMultiProfileSupported() },
            onSelectSandbox = { sandbox ->
                val targetUrl = if (UrlUtils.isStartPage(currentUrl)) sandbox.startUrl else currentUrl
                viewModel.openUrlInSandbox(targetUrl, sandbox)
            },
            onOpenInDefaultProfile = {
                val targetUrl = if (UrlUtils.isStartPage(currentUrl)) UrlUtils.START_PAGE_URL else currentUrl
                viewModel.openUrlInNewTab(targetUrl, isIncognito = false)
            },
            onCreateNewSandbox = onShowCreateSandboxDialog,
            onManageSandboxes = onOpenSandboxesManager,
            onDismiss = onDismissSandboxPicker
        )
    }

    if (showCreateSandboxDialog) {
        val currentUrl = activeTab?.url.orEmpty()
        val host = UrlUtils.extractHost(currentUrl).removePrefix("www.")
        val suggestedColor = SandboxPresets.colors[sandboxes.size % SandboxPresets.colors.size].colorHex
        CreateEditSandboxDialog(
            prefillSiteUrl = if (UrlUtils.isStartPage(currentUrl)) "" else currentUrl,
            suggestedName = if (host.isNotBlank()) "$host (${sandboxes.size + 1})" else "",
            suggestedColorHex = suggestedColor,
            confirmLabel = "Create & Open",
            onConfirm = { name, siteUrl, colorHex, iconKey, keepLongTerm ->
                onDismissCreateSandboxDialog()
                val targetUrl = siteUrl.ifBlank { currentUrl }
                viewModel.createSandboxAndOpenUrl(
                    name = name,
                    targetUrl = targetUrl,
                    colorHex = colorHex,
                    iconKey = iconKey,
                    keepLongTerm = keepLongTerm,
                    openNow = true
                )
            },
            onDismiss = onDismissCreateSandboxDialog
        )
    }

    autofillPrompt?.let { prompt ->
        val protonStatus = remember(prompt.pageUrl) { protonPassIntegration.getStatus() }
        val activeSandbox = remember(activeTab?.sandboxId, sandboxes) {
            activeTab?.sandboxId?.let { id -> sandboxes.firstOrNull { it.id == id } }
        }
        BrowserAutofillPromptSheet(
            request = prompt,
            webViewProvider = { webViewPool.getIfPresent(prompt.tabId) },
            coordinator = passwordCoordinator,
            localVaultRepository = localVaultRepository,
            biometricHelper = biometricHelper,
            protonPassIntegration = protonPassIntegration,
            protonStatus = protonStatus,
            biometricEnabled = vaultSecuritySettings.biometricEnabled,
            activeSandbox = activeSandbox,
            onCredentialSelectedInSandbox = { sandboxId, credId ->
                viewModel.bindSandboxPreferredCredential(sandboxId, credId)
            }
        )
    }

    pendingSavePrompt?.let { saveRequest ->
        SaveOrUpdateCredentialDialog(
            request = saveRequest,
            onConfirmSave = { title, saveAsNew ->
                passwordCoordinator.confirmSave(title, saveAsNew)
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Saved to Local Vault")
                }
            },
            onNeverForSite = {
                passwordCoordinator.neverSaveForPendingSite()
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Added ${saveRequest.domain} to Never Save list")
                }
            },
            onDismiss = passwordCoordinator::dismissSavePrompt
        )
    }
}
