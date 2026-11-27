package com.example.feature.browser.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.common.UrlUtils
import com.example.feature.browser.model.BrowserTabModel
import com.example.feature.browser.model.BrowserUiState
import com.example.feature.browser.model.WebCommand
import com.example.feature.browser.repository.BrowserRepository
import com.example.feature.browser.webview.WebViewCallbacks
import com.example.feature.sandbox.model.SandboxModel
import com.example.feature.sandbox.repository.SandboxRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class BrowserViewModel(
    private val repository: BrowserRepository,
    private val sandboxRepository: SandboxRepository? = null
) : ViewModel(), WebViewCallbacks {

    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    private val _webCommands = MutableSharedFlow<WebCommand>(extraBufferCapacity = 32)
    val webCommands: SharedFlow<WebCommand> = _webCommands.asSharedFlow()

    private var suggestionsJob: Job? = null
    private val callbackHandler = BrowserWebCallbackHandler(
        scope = viewModelScope,
        uiState = _uiState,
        repository = repository,
        persistTabById = ::persistTabById
    )

    init {
        observePreferences()
        observeSpeedDial()
        observeSandboxes()
        observeActiveBookmarkState()
        restoreInitialTabs()
    }

    private fun observePreferences() {
        viewModelScope.launch {
            repository.preferencesFlow.collectLatest { prefs ->
                _uiState.update { it.copy(preferences = prefs) }
            }
        }
    }

    private fun observeSpeedDial() {
        viewModelScope.launch {
            repository.ensureDefaultSpeedDial()
            repository.syncMissingShortcutIcons()
            repository.observeSpeedDial().collectLatest { shortcuts ->
                _uiState.update { it.copy(speedDialItems = shortcuts) }
            }
        }
    }

    private fun observeSandboxes() {
        val repo = sandboxRepository ?: return
        viewModelScope.launch {
            repo.observeSandboxes().collectLatest { list ->
                val map = list.associateBy { it.id }
                _uiState.update { state ->
                    state.copy(
                        sandboxes = list,
                        tabs = state.tabs.map { tab ->
                            val sb = tab.sandboxId?.let { map[it] }
                            if (sb != null) {
                                tab.copy(
                                    sandboxName = sb.name,
                                    sandboxProfileName = sb.profileName,
                                    sandboxColorHex = sb.colorHex
                                )
                            } else tab
                        }
                    )
                }
            }
        }
    }

    fun addSpeedDialShortcut(title: String, rawUrl: String, collectionIndex: Int = 0) {
        if (rawUrl.isBlank()) return
        viewModelScope.launch {
            val (insertedId, resolvedUrl) = repository.addSpeedDial(
                title = title,
                rawUrl = rawUrl,
                searchEngine = _uiState.value.preferences.searchEngine,
                collectionIndex = collectionIndex
            )
            _uiState.update { it.copy(statusBanner = "Added to Speed Dial") }
            repository.refreshSpeedDialIcon(insertedId, resolvedUrl, forceRefresh = false)
        }
    }

    fun moveSpeedDialToCollection(id: Long, collectionIndex: Int) {
        viewModelScope.launch {
            repository.moveSpeedDialToCollection(id, collectionIndex)
            val names = _uiState.value.preferences.speedDialCollectionNames
            val targetName = names.getOrNull(collectionIndex.coerceIn(0, 3)) ?: "Collection"
            _uiState.update { it.copy(statusBanner = "Moved to $targetName") }
        }
    }

    fun refreshSpeedDialIcon(id: Long, url: String) {
        viewModelScope.launch {
            val updated = repository.refreshSpeedDialIcon(id, url, forceRefresh = true)
            _uiState.update {
                it.copy(statusBanner = if (updated) "Shortcut icon updated" else "Using letter fallback icon")
            }
        }
    }

    fun renameSpeedDialCollection(index: Int, name: String) {
        viewModelScope.launch {
            repository.setSpeedDialCollectionName(index, name)
        }
    }

    fun deleteSpeedDialShortcut(id: Long) {
        viewModelScope.launch {
            repository.deleteSpeedDial(id)
            _uiState.update { it.copy(statusBanner = "Removed shortcut") }
        }
    }

    fun cycleSearchEngine() {
        val entries = com.example.core.common.SearchEngine.entries
        val currentIdx = entries.indexOf(_uiState.value.preferences.searchEngine)
        val next = entries[(currentIdx + 1) % entries.size]
        viewModelScope.launch {
            repository.setSearchEngine(next)
            _uiState.update { it.copy(statusBanner = "Search engine: ${next.displayName}") }
        }
    }

    fun setThemeMode(mode: com.example.core.datastore.AppThemeMode) {
        viewModelScope.launch {
            repository.setThemeMode(mode)
            _uiState.update { it.copy(statusBanner = "Theme: ${mode.displayName}") }
        }
    }

    fun setStartBackgroundMode(mode: com.example.core.datastore.StartBackgroundMode) {
        viewModelScope.launch {
            repository.setStartBackgroundMode(mode)
            _uiState.update { it.copy(statusBanner = "Start background: ${mode.displayName}") }
        }
    }

    fun updateStartWallpaperUri(uri: String) {
        viewModelScope.launch {
            repository.setStartWallpaperUri(uri)
            if (uri.isNotBlank()) {
                repository.setStartBackgroundMode(com.example.core.datastore.StartBackgroundMode.IMAGE)
            }
            _uiState.update {
                it.copy(
                    statusBanner = if (uri.isBlank()) "Default wallpaper restored" else "Start wallpaper updated"
                )
            }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun observeActiveBookmarkState() {
        viewModelScope.launch {
            _uiState
                .map { it.activeTab?.url.orEmpty() }
                .distinctUntilChanged()
                .flatMapLatest { url ->
                    if (UrlUtils.isStartPage(url)) flowOf(false)
                    else repository.observeIsBookmarked(url)
                }
                .collectLatest { isBookmarked ->
                    _uiState.update { it.copy(isCurrentUrlBookmarked = isBookmarked) }
                }
        }
    }

    private fun restoreInitialTabs() {
        viewModelScope.launch {
            val saved = repository.loadSavedTabs()
            val sandboxMap = sandboxRepository?.getAllSnapshot()?.associateBy { it.id }.orEmpty()
            if (saved.isEmpty()) {
                val firstTab = BrowserTabModel(
                    id = UUID.randomUUID().toString(),
                    isDesktopMode = _uiState.value.preferences.desktopModeDefault
                )
                _uiState.update {
                    it.copy(
                        tabs = listOf(firstTab),
                        activeTabId = firstTab.id,
                        omniboxText = ""
                    )
                }
                repository.saveStandardTab(firstTab, isActive = true, position = 0)
            } else {
                val mapped = saved.map { entity ->
                    val sb = entity.sandboxId?.let { sandboxMap[it] }
                    BrowserTabModel(
                        id = entity.id,
                        title = entity.title,
                        url = entity.url,
                        isIncognito = false,
                        isDesktopMode = entity.isDesktopMode,
                        sandboxId = sb?.id ?: entity.sandboxId,
                        sandboxName = sb?.name,
                        sandboxProfileName = sb?.profileName,
                        sandboxColorHex = sb?.colorHex
                    )
                }
                val activeId = saved.firstOrNull { it.isActive }?.id ?: mapped.first().id
                val activeUrl = mapped.firstOrNull { it.id == activeId }?.url.orEmpty()
                _uiState.update {
                    it.copy(
                        tabs = mapped,
                        activeTabId = activeId,
                        omniboxText = if (UrlUtils.isStartPage(activeUrl)) "" else activeUrl
                    )
                }
            }
        }
    }

    fun onOmniboxTextChanged(text: String) {
        _uiState.update { it.copy(omniboxText = text) }
        suggestionsJob?.cancel()
        val trimmed = text.trim()
        if (trimmed.length < 2 || trimmed.startsWith("/")) {
            _uiState.update { it.copy(suggestions = emptyList(), localMatches = emptyList()) }
            return
        }
        suggestionsJob = viewModelScope.launch {
            val local = repository.searchLocalMatches(trimmed)
            _uiState.update { it.copy(localMatches = local) }
            delay(200)
            val results = repository.fetchSearchSuggestions(trimmed)
            _uiState.update { it.copy(suggestions = results) }
        }
    }

    fun onOmniboxFocusChanged(focused: Boolean) {
        val currentUrl = _uiState.value.activeTab?.url.orEmpty()
        _uiState.update { state ->
            val updatedText = if (UrlUtils.isStartPage(currentUrl)) "" else currentUrl
            state.copy(
                isOmniboxFocused = focused,
                omniboxText = updatedText,
                suggestions = if (focused) state.suggestions else emptyList(),
                localMatches = if (focused) state.localMatches else emptyList()
            )
        }
    }

    fun submitOmnibox(
        rawInput: String = _uiState.value.omniboxText,
        engineOverride: com.example.core.common.SearchEngine? = null
    ) {
        val engine = engineOverride ?: _uiState.value.preferences.searchEngine
        val resolvedUrl = UrlUtils.resolveInputToUrl(rawInput, engine)
        loadUrlInActiveTab(resolvedUrl)
    }

    fun loadUrlInActiveTab(url: String) {
        val activeTab = _uiState.value.activeTab ?: return
        val goingToStart = UrlUtils.isStartPage(url)
        _uiState.update { state ->
            state.copy(
                isOmniboxFocused = false,
                suggestions = emptyList(),
                localMatches = emptyList(),
                omniboxText = if (goingToStart) "" else url,
                findInPageVisible = if (goingToStart) false else state.findInPageVisible,
                findInPageQuery = if (goingToStart) "" else state.findInPageQuery,
                findMatchIndex = if (goingToStart) 0 else state.findMatchIndex,
                findTotalMatches = if (goingToStart) 0 else state.findTotalMatches,
                tabs = state.tabs.map { tab ->
                    if (tab.id == activeTab.id) {
                        val hadPreviousWeb = UrlUtils.isStartPage(tab.url) && tab.backToWebUrl != null
                        tab.copy(
                            url = url,
                            title = if (goingToStart) "Start Page" else UrlUtils.extractHost(url),
                            isLoading = !goingToStart,
                            progress = if (goingToStart) 0 else 10,
                            canGoBack = if (goingToStart) false else (tab.canGoBack || hadPreviousWeb),
                            canGoForward = false,
                            blockedTrackersCount = if (goingToStart) 0 else tab.blockedTrackersCount,
                            directImage = null,
                            backToWebUrl = null,
                            backToWebTitle = null,
                            backToWebCanGoBack = false,
                            backToWebCanGoForward = false,
                            forwardToWebUrl = null,
                            forwardToWebTitle = null,
                            forwardToWebCanGoForward = false,
                            forwardToStartPage = false
                        )
                    } else tab
                }
            )
        }
        if (goingToStart) {
            _webCommands.tryEmit(WebCommand.DestroyTab(activeTab.id))
        } else {
            _webCommands.tryEmit(WebCommand.LoadUrl(activeTab.id, url))
        }
        persistTabById(activeTab.id)
    }

    fun openUrlInNewTab(url: String, isIncognito: Boolean = false) {
        val newTab = BrowserTabModel(
            id = UUID.randomUUID().toString(),
            title = if (UrlUtils.isStartPage(url)) "Start Page" else UrlUtils.extractHost(url),
            url = url,
            isIncognito = isIncognito,
            isDesktopMode = _uiState.value.preferences.desktopModeDefault,
            isLoading = !UrlUtils.isStartPage(url),
            progress = if (UrlUtils.isStartPage(url)) 0 else 10
        )
        _uiState.update { state ->
            state.copy(
                tabs = state.tabs + newTab,
                activeTabId = newTab.id,
                isOmniboxFocused = false,
                suggestions = emptyList(),
                omniboxText = if (UrlUtils.isStartPage(url)) "" else url
            )
        }
        viewModelScope.launch {
            val idx = _uiState.value.tabs.indexOfFirst { it.id == newTab.id }.coerceAtLeast(0)
            repository.saveStandardTab(newTab, isActive = true, position = idx)
            if (!newTab.isIncognito) repository.setActiveStandardTab(newTab.id)
        }
        if (!UrlUtils.isStartPage(url)) {
            _webCommands.tryEmit(WebCommand.LoadUrl(newTab.id, url))
        }
    }

    fun openUrlInSandbox(url: String, sandbox: SandboxModel) {
        val targetUrl = when {
            !UrlUtils.isStartPage(url) && url.isNotBlank() -> url
            sandbox.startUrl.isNotBlank() -> sandbox.startUrl
            else -> UrlUtils.START_PAGE_URL
        }
        val isStart = UrlUtils.isStartPage(targetUrl)
        val newTab = BrowserTabModel(
            id = UUID.randomUUID().toString(),
            title = if (isStart) sandbox.name else UrlUtils.extractHost(targetUrl),
            url = targetUrl,
            isIncognito = false,
            isDesktopMode = _uiState.value.preferences.desktopModeDefault,
            isLoading = !isStart,
            progress = if (isStart) 0 else 10,
            sandboxId = sandbox.id,
            sandboxName = sandbox.name,
            sandboxProfileName = sandbox.profileName,
            sandboxColorHex = sandbox.colorHex
        )
        _uiState.update { state ->
            state.copy(
                tabs = state.tabs + newTab,
                activeTabId = newTab.id,
                isOmniboxFocused = false,
                suggestions = emptyList(),
                omniboxText = if (isStart) "" else targetUrl,
                statusBanner = "Opened in ${sandbox.name} sandbox"
            )
        }
        viewModelScope.launch {
            val idx = _uiState.value.tabs.indexOfFirst { it.id == newTab.id }.coerceAtLeast(0)
            repository.saveStandardTab(newTab, isActive = true, position = idx)
            repository.setActiveStandardTab(newTab.id)
            sandboxRepository?.touchLastUsed(sandbox.id)
        }
        if (!isStart) _webCommands.tryEmit(WebCommand.LoadUrl(newTab.id, targetUrl))
    }

    fun createSandboxAndOpenUrl(
        name: String,
        targetUrl: String,
        colorHex: Long,
        iconKey: String,
        keepLongTerm: Boolean,
        openNow: Boolean = true
    ) {
        val repo = sandboxRepository ?: return
        viewModelScope.launch {
            val created = repo.createSandbox(
                name = name,
                rawSiteUrl = targetUrl,
                colorHex = colorHex,
                iconKey = iconKey,
                keepLongTerm = keepLongTerm,
                searchEngine = _uiState.value.preferences.searchEngine
            )
            if (openNow) {
                openUrlInSandbox(targetUrl, created)
            } else {
                _uiState.update { it.copy(statusBanner = "Created sandbox \"${created.name}\"") }
            }
        }
    }

    fun closeTabsForSandbox(sandboxId: String) {
        val matchingIds = _uiState.value.tabs.filter { it.sandboxId == sandboxId }.map { it.id }
        matchingIds.forEach { closeTab(it) }
    }

    fun bindSandboxPreferredCredential(sandboxId: String, credentialId: String) {
        viewModelScope.launch {
            sandboxRepository?.bindPreferredCredential(sandboxId, credentialId)
        }
    }

    fun selectTab(tabId: String) {
        val target = _uiState.value.tabs.firstOrNull { it.id == tabId } ?: return
        _uiState.update {
            it.copy(
                activeTabId = target.id,
                omniboxText = if (UrlUtils.isStartPage(target.url)) "" else target.url,
                findInPageVisible = false
            )
        }
        viewModelScope.launch {
            if (!target.isIncognito) repository.setActiveStandardTab(target.id)
        }
    }

    fun switchTabByOffset(offset: Int) {
        val tabs = _uiState.value.tabs
        if (tabs.size <= 1 || offset == 0) return
        val currentIdx = tabs.indexOfFirst { it.id == _uiState.value.activeTabId }.takeIf { it >= 0 } ?: 0
        val nextIdx = ((currentIdx + offset) % tabs.size + tabs.size) % tabs.size
        selectTab(tabs[nextIdx].id)
    }

    fun closeTab(tabId: String) {
        val currentTabs = _uiState.value.tabs
        val remaining = currentTabs.filterNot { it.id == tabId }
        _webCommands.tryEmit(WebCommand.DestroyTab(tabId))

        viewModelScope.launch { repository.deleteStandardTab(tabId) }

        if (remaining.isEmpty()) {
            val replacement = BrowserTabModel(
                id = UUID.randomUUID().toString(),
                isDesktopMode = _uiState.value.preferences.desktopModeDefault
            )
            _uiState.update {
                it.copy(
                    tabs = listOf(replacement),
                    activeTabId = replacement.id,
                    omniboxText = ""
                )
            }
            viewModelScope.launch {
                repository.saveStandardTab(replacement, isActive = true, position = 0)
            }
        } else {
            val newActive = if (_uiState.value.activeTabId == tabId) remaining.last() else _uiState.value.activeTab ?: remaining.last()
            _uiState.update {
                it.copy(
                    tabs = remaining,
                    activeTabId = newActive.id,
                    omniboxText = if (UrlUtils.isStartPage(newActive.url)) "" else newActive.url
                )
            }
            viewModelScope.launch {
                if (!newActive.isIncognito) repository.setActiveStandardTab(newActive.id)
            }
        }
    }

    fun closeAllTabs() {
        _uiState.value.tabs.forEach { _webCommands.tryEmit(WebCommand.DestroyTab(it.id)) }
        val freshTab = BrowserTabModel(
            id = UUID.randomUUID().toString(),
            isDesktopMode = _uiState.value.preferences.desktopModeDefault
        )
        _uiState.update {
            it.copy(
                tabs = listOf(freshTab),
                activeTabId = freshTab.id,
                omniboxText = "",
                findInPageVisible = false
            )
        }
        viewModelScope.launch {
            repository.clearAllStandardTabs()
            repository.saveStandardTab(freshTab, isActive = true, position = 0)
        }
    }

    fun goBack(): Boolean {
        val active = _uiState.value.activeTab ?: return false
        return if (!UrlUtils.isStartPage(active.url) && active.canGoBack) {
            _webCommands.tryEmit(WebCommand.GoBack(active.id))
            true
        } else if (!UrlUtils.isStartPage(active.url)) {
            _uiState.update { state ->
                state.copy(
                    isOmniboxFocused = false,
                    suggestions = emptyList(),
                    localMatches = emptyList(),
                    omniboxText = "",
                    findInPageVisible = false,
                    tabs = state.tabs.map { tab ->
                        if (tab.id == active.id) {
                            tab.copy(
                                url = UrlUtils.START_PAGE_URL,
                                title = "Start Page",
                                isLoading = false,
                                progress = 0,
                                canGoBack = false,
                                canGoForward = true,
                                directImage = null,
                                backToWebUrl = null,
                                backToWebTitle = null,
                                backToWebCanGoBack = false,
                                backToWebCanGoForward = false,
                                forwardToWebUrl = active.url,
                                forwardToWebTitle = active.title,
                                forwardToWebCanGoForward = active.canGoForward,
                                forwardToStartPage = false
                            )
                        } else tab
                    }
                )
            }
            persistTabById(active.id)
            true
        } else if (active.backToWebUrl != null) {
            val restoreUrl = active.backToWebUrl
            val restoreTitle = active.backToWebTitle?.takeIf { it.isNotBlank() }
                ?: UrlUtils.extractHost(restoreUrl)
            _uiState.update { state ->
                state.copy(
                    isOmniboxFocused = false,
                    suggestions = emptyList(),
                    localMatches = emptyList(),
                    omniboxText = restoreUrl,
                    tabs = state.tabs.map { tab ->
                        if (tab.id == active.id) {
                            tab.copy(
                                url = restoreUrl,
                                title = restoreTitle,
                                isLoading = false,
                                progress = 100,
                                canGoBack = active.backToWebCanGoBack,
                                canGoForward = true,
                                backToWebUrl = null,
                                backToWebTitle = null,
                                backToWebCanGoBack = false,
                                backToWebCanGoForward = false,
                                forwardToWebUrl = null,
                                forwardToWebTitle = null,
                                forwardToWebCanGoForward = false,
                                forwardToStartPage = true
                            )
                        } else tab
                    }
                )
            }
            persistTabById(active.id)
            true
        } else {
            false
        }
    }

    fun goForward() {
        val active = _uiState.value.activeTab ?: return
        if (UrlUtils.isStartPage(active.url) && active.forwardToWebUrl != null) {
            val restoreUrl = active.forwardToWebUrl
            val restoreTitle = active.forwardToWebTitle?.takeIf { it.isNotBlank() }
                ?: UrlUtils.extractHost(restoreUrl)
            _uiState.update { state ->
                state.copy(
                    isOmniboxFocused = false,
                    suggestions = emptyList(),
                    localMatches = emptyList(),
                    omniboxText = restoreUrl,
                    tabs = state.tabs.map { tab ->
                        if (tab.id == active.id) {
                            tab.copy(
                                url = restoreUrl,
                                title = restoreTitle,
                                isLoading = false,
                                progress = 100,
                                canGoBack = true,
                                canGoForward = active.forwardToWebCanGoForward,
                                forwardToWebUrl = null,
                                forwardToWebTitle = null,
                                forwardToWebCanGoForward = false,
                                forwardToStartPage = false
                            )
                        } else tab
                    }
                )
            }
            persistTabById(active.id)
        } else if (!UrlUtils.isStartPage(active.url) && active.forwardToStartPage) {
            goHome()
        } else if (!UrlUtils.isStartPage(active.url) && active.canGoForward) {
            _webCommands.tryEmit(WebCommand.GoForward(active.id))
        }
    }

    fun reloadOrStop() {
        val active = _uiState.value.activeTab ?: return
        if (UrlUtils.isStartPage(active.url)) return
        if (active.isLoading) {
            _webCommands.tryEmit(WebCommand.StopLoading(active.id))
        } else {
            _webCommands.tryEmit(WebCommand.Reload(active.id))
        }
    }

    fun goHome() {
        val active = _uiState.value.activeTab ?: return
        val homeUrl = _uiState.value.preferences.homepageUrl
        if (!UrlUtils.isStartPage(homeUrl)) {
            loadUrlInActiveTab(homeUrl)
            return
        }
        if (UrlUtils.isStartPage(active.url)) return

        _uiState.update { state ->
            state.copy(
                isOmniboxFocused = false,
                suggestions = emptyList(),
                localMatches = emptyList(),
                omniboxText = "",
                findInPageVisible = false,
                findInPageQuery = "",
                findMatchIndex = 0,
                findTotalMatches = 0,
                tabs = state.tabs.map { tab ->
                    if (tab.id == active.id) {
                        tab.copy(
                            url = UrlUtils.START_PAGE_URL,
                            title = "Start Page",
                            isLoading = false,
                            progress = 0,
                            canGoBack = true,
                            canGoForward = false,
                            directImage = null,
                            backToWebUrl = active.url,
                            backToWebTitle = active.title,
                            backToWebCanGoBack = active.canGoBack,
                            backToWebCanGoForward = active.canGoForward,
                            forwardToWebUrl = null,
                            forwardToWebTitle = null,
                            forwardToWebCanGoForward = false,
                            forwardToStartPage = false
                        )
                    } else tab
                }
            )
        }
        persistTabById(active.id)
    }

    fun toggleDesktopModeForActiveTab() {
        val active = _uiState.value.activeTab ?: return
        _uiState.update { state ->
            state.copy(
                tabs = state.tabs.map { tab ->
                    if (tab.id == active.id) tab.copy(isDesktopMode = !tab.isDesktopMode) else tab
                }
            )
        }
        persistTabById(active.id)
    }

    fun toggleCurrentBookmark() {
        val active = _uiState.value.activeTab ?: return
        if (UrlUtils.isStartPage(active.url)) return
        viewModelScope.launch {
            val saved = repository.toggleBookmark(active.title, active.url)
            _uiState.update {
                it.copy(statusBanner = if (saved) "Saved to Bookmarks" else "Removed from Bookmarks")
            }
        }
    }

    fun toggleFindInPage(visible: Boolean) {
        val activeId = _uiState.value.activeTabId
        _uiState.update {
            it.copy(
                findInPageVisible = visible,
                findInPageQuery = if (visible) it.findInPageQuery else "",
                findMatchIndex = 0,
                findTotalMatches = 0
            )
        }
        if (!visible && activeId.isNotBlank()) {
            _webCommands.tryEmit(WebCommand.ClearMatches(activeId))
        }
    }

    fun updateFindQuery(query: String) {
        val activeId = _uiState.value.activeTabId
        _uiState.update { it.copy(findInPageQuery = query) }
        if (activeId.isNotBlank()) {
            _webCommands.tryEmit(WebCommand.FindAll(activeId, query))
        }
    }

    fun findNextMatch(forward: Boolean) {
        val activeId = _uiState.value.activeTabId
        if (activeId.isNotBlank()) {
            _webCommands.tryEmit(WebCommand.FindNext(activeId, forward))
        }
    }

    fun dismissBanner() {
        _uiState.update { it.copy(statusBanner = null) }
    }

    private fun persistTabById(tabId: String) {
        val tabs = _uiState.value.tabs
        val idx = tabs.indexOfFirst { it.id == tabId }
        val tab = tabs.getOrNull(idx) ?: return
        viewModelScope.launch {
            repository.saveStandardTab(
                tab = tab,
                isActive = tab.id == _uiState.value.activeTabId,
                position = idx
            )
        }
    }

    override fun onPageStarted(tabId: String, url: String) =
        callbackHandler.onPageStarted(tabId, url)

    override fun onPageFinished(
        tabId: String,
        url: String,
        title: String,
        canGoBack: Boolean,
        canGoForward: Boolean
    ) = callbackHandler.onPageFinished(tabId, url, title, canGoBack, canGoForward)

    override fun onProgressChanged(tabId: String, progress: Int) =
        callbackHandler.onProgressChanged(tabId, progress)

    override fun onTitleReceived(tabId: String, title: String) =
        callbackHandler.onTitleReceived(tabId, title)

    override fun onTrackerBlocked(tabId: String) =
        callbackHandler.onTrackerBlocked(tabId)

    override fun onDirectImageDetected(
        tabId: String,
        imageInfo: com.example.feature.browser.model.DirectImageInfo?
    ) = callbackHandler.onDirectImageDetected(tabId, imageInfo)

    override fun onDownloadRequested(url: String, contentDisposition: String?, mimeType: String?) =
        callbackHandler.onDownloadRequested(url, contentDisposition, mimeType)

    override fun onFindMatchesUpdated(tabId: String, activeMatchOrdinal: Int, numberOfMatches: Int) =
        callbackHandler.onFindMatchesUpdated(tabId, activeMatchOrdinal, numberOfMatches)

    override fun onPageFaviconBitmap(tabId: String, pageUrl: String, bitmap: android.graphics.Bitmap) =
        callbackHandler.onPageFaviconBitmap(tabId, pageUrl, bitmap)

    override fun onPageFaviconUrls(tabId: String, pageUrl: String, iconUrls: List<String>) =
        callbackHandler.onPageFaviconUrls(tabId, pageUrl, iconUrls)
}
