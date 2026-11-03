package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ai.AiHomeOrganizer
import com.example.ai.OfflineAiClassifier
import com.example.ai.OnlineAppIntelligenceService
import com.example.data.local.LauncherDatabase
import com.example.data.local.LauncherSettingsEntity
import com.example.data.local.LayoutSnapshotEntity
import com.example.data.local.SearchHistoryEntity
import com.example.data.repository.LauncherRepository
import com.example.domain.model.AppInfo
import com.example.domain.model.ArrangementMode
import com.example.domain.model.CategoryModel
import com.example.domain.model.ContactSearchResult
import com.example.domain.model.DefaultCategory
import com.example.domain.model.DrawerSortMode
import com.example.domain.model.FolderModel
import com.example.domain.model.HomeScreenItemModel
import com.example.domain.model.IconPackInfo
import com.example.domain.model.LocalFileSearchResult
import com.example.domain.model.ProposedLayoutPreview
import com.example.domain.model.WebSearchSuggestion
import com.example.engine.AppDiscoveryEngine
import com.example.engine.IconPackManager
import com.example.engine.LauncherNotificationListenerService
import com.example.engine.LocalBackupManager
import com.example.engine.SystemWidgetProviderItem
import com.example.engine.UnifiedSearchEngine
import com.example.engine.WidgetHostController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ActiveOverlaySheet {
    NONE,
    APP_DRAWER,
    UNIFIED_SEARCH,
    AI_ORGANIZER_PREVIEW,
    SETTINGS_CUSTOMIZATION,
    WIDGET_PICKER
}

data class SearchUiState(
    val query: String = "",
    val debouncedQuery: String = "",
    val isSearching: Boolean = false,
    val appResults: List<AppInfo> = emptyList(),
    val folderResults: List<FolderModel> = emptyList(),
    val contactResults: List<ContactSearchResult> = emptyList(),
    val fileResults: List<LocalFileSearchResult> = emptyList(),
    val webSuggestions: List<WebSearchSuggestion> = emptyList(),
    val activeWebViewUrl: String? = null,
    val isOnline: Boolean = true,
    val hasContactsPermission: Boolean = false
)

data class LauncherUiState(
    val isLoading: Boolean = true,
    val installedApps: List<AppInfo> = emptyList(),
    val categories: List<CategoryModel> = emptyList(),
    val selectedCategoryId: String = DefaultCategory.ALL_APPS.id,
    val drawerSearchQuery: String = "",
    val homeItems: List<HomeScreenItemModel> = emptyList(),
    val folders: List<FolderModel> = emptyList(),
    val settings: LauncherSettingsEntity = LauncherSettingsEntity(),
    val activeOverlay: ActiveOverlaySheet = ActiveOverlaySheet.NONE,
    val openedFolder: FolderModel? = null,
    val contextMenuApp: AppInfo? = null,
    val contextMenuHomeItem: HomeScreenItemModel? = null,
    val proposedLayoutPreview: ProposedLayoutPreview? = null,
    val layoutSnapshots: List<LayoutSnapshotEntity> = emptyList(),
    val searchHistory: List<SearchHistoryEntity> = emptyList(),
    val iconPacks: List<IconPackInfo> = emptyList(),
    val availableWidgets: List<SystemWidgetProviderItem> = emptyList(),
    val isDefaultHomeLauncher: Boolean = false,
    val statusBannerMessage: String? = null,
    val backupJsonExportText: String = "",
    val notificationCounts: Map<String, Int> = emptyMap()
)

@OptIn(FlowPreview::class)
class LauncherViewModel(
    private val repository: LauncherRepository,
    private val discoveryEngine: AppDiscoveryEngine,
    private val searchEngine: UnifiedSearchEngine,
    val widgetHostController: WidgetHostController
) : ViewModel() {

    companion object {
        const val SEARCH_DEBOUNCE_MS = 280L
        const val DRAWER_SEARCH_DEBOUNCE_MS = 220L
    }

    private val _selectedCategoryId = MutableStateFlow(DefaultCategory.ALL_APPS.id)
    private val _rawDrawerSearchQuery = MutableStateFlow("")
    private val _debouncedDrawerSearchQuery = MutableStateFlow("")
    private val _activeOverlay = MutableStateFlow(ActiveOverlaySheet.NONE)
    private val _openedFolderId = MutableStateFlow<Long?>(null)
    private val _contextMenuAppPkg = MutableStateFlow<String?>(null)
    private val _contextMenuHomeItemId = MutableStateFlow<Long?>(null)
    private val _proposedLayoutPreview = MutableStateFlow<ProposedLayoutPreview?>(null)
    private val _iconPacks = MutableStateFlow<List<IconPackInfo>>(emptyList())
    private val _availableWidgets = MutableStateFlow<List<SystemWidgetProviderItem>>(emptyList())
    private val _isDefaultHome = MutableStateFlow(false)
    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    private val _backupJsonExportText = MutableStateFlow("")

    private val _rawUnifiedSearchQuery = MutableStateFlow("")
    private val _searchState = MutableStateFlow(SearchUiState())
    val searchState: StateFlow<SearchUiState> = _searchState.asStateFlow()

    private var unifiedSearchJob: Job? = null

    val uiState: StateFlow<LauncherUiState> = combine(
        repository.installedAppsFlow,
        repository.categoriesFlow,
        repository.homeItemsFlow,
        repository.foldersFlow,
        repository.settingsFlow,
        repository.layoutSnapshotsFlow,
        repository.searchHistoryFlow,
        _selectedCategoryId,
        _debouncedDrawerSearchQuery,
        _activeOverlay
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val apps = values[0] as List<AppInfo>
        @Suppress("UNCHECKED_CAST")
        val categories = values[1] as List<CategoryModel>
        @Suppress("UNCHECKED_CAST")
        val homeItems = values[2] as List<HomeScreenItemModel>
        @Suppress("UNCHECKED_CAST")
        val folders = values[3] as List<FolderModel>
        val settings = (values[4] as? LauncherSettingsEntity) ?: LauncherSettingsEntity()
        @Suppress("UNCHECKED_CAST")
        val snapshots = values[5] as List<LayoutSnapshotEntity>
        @Suppress("UNCHECKED_CAST")
        val history = values[6] as List<SearchHistoryEntity>
        val selectedCat = values[7] as String
        val drawerQuery = values[8] as String
        val overlay = values[9] as ActiveOverlaySheet

        LauncherUiState(
            isLoading = false,
            installedApps = apps,
            categories = categories,
            selectedCategoryId = selectedCat,
            drawerSearchQuery = drawerQuery,
            homeItems = homeItems,
            folders = folders,
            settings = settings,
            activeOverlay = overlay,
            openedFolder = folders.firstOrNull { it.id == _openedFolderId.value },
            contextMenuApp = apps.firstOrNull { it.packageName == _contextMenuAppPkg.value },
            contextMenuHomeItem = homeItems.firstOrNull { it.id == _contextMenuHomeItemId.value },
            proposedLayoutPreview = _proposedLayoutPreview.value,
            layoutSnapshots = snapshots,
            searchHistory = history,
            iconPacks = _iconPacks.value,
            availableWidgets = _availableWidgets.value,
            isDefaultHomeLauncher = _isDefaultHome.value,
            statusBannerMessage = _statusBannerMessage.value,
            backupJsonExportText = _backupJsonExportText.value
        )
    }.combine(
        combine(
            _openedFolderId,
            _contextMenuAppPkg,
            _contextMenuHomeItemId,
            _proposedLayoutPreview,
            _statusBannerMessage,
            _iconPacks,
            _availableWidgets,
            _isDefaultHome,
            _backupJsonExportText,
            LauncherNotificationListenerService.packageNotificationCounts
        ) { arr -> arr }
    ) { base, extras ->
        @Suppress("UNCHECKED_CAST")
        val notifCounts = (extras.getOrNull(9) as? Map<String, Int>) ?: emptyMap()
        base.copy(
            openedFolder = base.folders.firstOrNull { it.id == _openedFolderId.value },
            contextMenuApp = base.installedApps.firstOrNull { it.packageName == _contextMenuAppPkg.value },
            contextMenuHomeItem = base.homeItems.firstOrNull { it.id == _contextMenuHomeItemId.value },
            proposedLayoutPreview = _proposedLayoutPreview.value,
            iconPacks = _iconPacks.value,
            availableWidgets = _availableWidgets.value,
            isDefaultHomeLauncher = _isDefaultHome.value,
            statusBannerMessage = _statusBannerMessage.value,
            backupJsonExportText = _backupJsonExportText.value,
            notificationCounts = notifCounts
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LauncherUiState()
    )

    init {
        widgetHostController.startListening()

        // Observe debounced App Drawer search input
        viewModelScope.launch {
            _rawDrawerSearchQuery
                .debounce { if (it.isBlank()) 0L else DRAWER_SEARCH_DEBOUNCE_MS }
                .distinctUntilChanged()
                .collectLatest { debounced ->
                    _debouncedDrawerSearchQuery.value = debounced
                }
        }

        // Keep initial default suggestions and dynamic folder notification badges synced when installedApps finishes loading
        viewModelScope.launch {
            repository.installedAppsFlow.collectLatest { apps ->
                if (apps.isNotEmpty()) {
                    LauncherNotificationListenerService.syncDynamicBadgesWithInstalledApps(apps)
                }
                if (_searchState.value.debouncedQuery.isBlank() && _searchState.value.appResults.isEmpty() && apps.isNotEmpty()) {
                    executeUnifiedSearchFilter("")
                }
            }
        }

        viewModelScope.launch {
            repository.initializeLauncherState()
            refreshDefaultHomeStatus()
            _iconPacks.value = repository.getAvailableIconPacks()
            _availableWidgets.value = widgetHostController.getAvailableWidgetProviders()
            executeUnifiedSearchFilter("")
            // Enrich installed apps with online store metadata + AI classification when internet is available
            repository.enrichInstalledAppsWithOnlineIntelligence()
        }

        discoveryEngine.registerPackageChangeListener(
            onPackageAddedOrUpdated = { pkg ->
                viewModelScope.launch {
                    repository.handleIncrementalPackageAddedOrUpdated(pkg)
                }
            },
            onPackageRemoved = { pkg ->
                viewModelScope.launch {
                    repository.handleIncrementalPackageRemoved(pkg)
                }
            }
        )
    }

    override fun onCleared() {
        super.onCleared()
        discoveryEngine.unregisterPackageChangeListener()
        widgetHostController.stopListening()
    }

    fun refreshDefaultHomeStatus() {
        _isDefaultHome.value = widgetHostController.isDefaultHomeLauncher()
    }

    fun openOverlay(sheet: ActiveOverlaySheet) {
        _openedFolderId.value = null
        _contextMenuAppPkg.value = null
        _contextMenuHomeItemId.value = null
        _activeOverlay.value = sheet

        when (sheet) {
            ActiveOverlaySheet.UNIFIED_SEARCH -> {
                updateUnifiedSearchQuery("", immediate = true)
            }
            ActiveOverlaySheet.APP_DRAWER -> {
                updateDrawerSearchQuery("")
            }
            ActiveOverlaySheet.SETTINGS_CUSTOMIZATION -> {
                viewModelScope.launch {
                    _iconPacks.value = repository.getAvailableIconPacks()
                }
            }
            ActiveOverlaySheet.WIDGET_PICKER -> {
                _availableWidgets.value = widgetHostController.getAvailableWidgetProviders()
            }
            ActiveOverlaySheet.AI_ORGANIZER_PREVIEW -> {
                generateLayoutPreview(ArrangementMode.AI_ORGANIZED)
            }
            ActiveOverlaySheet.NONE -> Unit
        }
    }

    fun closeOverlaysAndGoHome() {
        _activeOverlay.value = ActiveOverlaySheet.NONE
        _openedFolderId.value = null
        _contextMenuAppPkg.value = null
        _contextMenuHomeItemId.value = null
        clearAllSearchQueries()
    }

    fun clearAllSearchQueries() {
        updateDrawerSearchQuery("")
        unifiedSearchJob?.cancel()
        _rawUnifiedSearchQuery.value = ""
        _searchState.update {
            it.copy(
                query = "",
                debouncedQuery = "",
                isSearching = false,
                activeWebViewUrl = null
            )
        }
        viewModelScope.launch {
            executeUnifiedSearchFilter("")
        }
    }

    fun selectDrawerCategory(categoryId: String) {
        _selectedCategoryId.value = categoryId
    }

    fun updateDrawerSearchQuery(query: String) {
        _rawDrawerSearchQuery.value = query
        if (query.isBlank()) {
            _debouncedDrawerSearchQuery.value = ""
        }
    }

    fun getFilteredAndSortedDrawerApps(state: LauncherUiState): List<AppInfo> {
        val catFilter = state.selectedCategoryId
        val query = state.drawerSearchQuery.trim().lowercase()

        val byCategory = if (catFilter == DefaultCategory.ALL_APPS.id) {
            state.installedApps
        } else {
            state.installedApps.filter { app ->
                app.categoryId == catFilter || catFilter in app.allCategoryIds
            }
        }

        val bySearch = if (query.isEmpty()) {
            byCategory
        } else {
            byCategory.filter {
                it.appName.lowercase().contains(query) ||
                    it.packageName.lowercase().contains(query) ||
                    it.categoryName.lowercase().contains(query)
            }
        }

        return when (state.settings.drawerSortMode) {
            DrawerSortMode.ALPHABETICAL_DESC.id -> bySearch.sortedByDescending { it.appName.lowercase() }
            DrawerSortMode.MOST_USED.id -> bySearch.sortedWith(
                compareByDescending<AppInfo> { it.launchCount }
                    .thenByDescending { it.isFavorite }
                    .thenBy { it.appName.lowercase() }
            )
            DrawerSortMode.AI_CONFIDENCE.id -> bySearch.sortedWith(
                compareByDescending<AppInfo> { it.categoryConfidence }
                    .thenBy { it.appName.lowercase() }
            )
            DrawerSortMode.RECENTLY_INSTALLED.id -> bySearch.sortedByDescending { it.firstInstallTime }
            else -> bySearch.sortedBy { it.appName.lowercase() }
        }
    }

    fun launchApp(app: AppInfo) {
        clearAllSearchQueries()
        LauncherNotificationListenerService.clearPackageNotificationsOnLaunch(app.packageName)
        viewModelScope.launch {
            val ok = repository.launchAppAndRecordUsage(app.packageName, app.activityName)
            if (!ok) {
                showBanner("Unable to launch ${app.appName}")
            }
        }
    }

    fun openFolder(folderId: Long) {
        _contextMenuAppPkg.value = null
        _contextMenuHomeItemId.value = null
        _openedFolderId.value = folderId
    }

    fun closeFolder() {
        _openedFolderId.value = null
    }

    fun openAppContextMenu(app: AppInfo, homeItem: HomeScreenItemModel? = null) {
        _openedFolderId.value = null
        _contextMenuAppPkg.value = app.packageName
        _contextMenuHomeItemId.value = homeItem?.id
    }

    fun dismissAppContextMenu() {
        _contextMenuAppPkg.value = null
        _contextMenuHomeItemId.value = null
    }

    fun assignAppCategory(packageName: String, categoryId: String, keepExisting: Boolean = false) {
        viewModelScope.launch {
            repository.assignAppToCategoryManually(packageName, categoryId, keepExisting)
            val catName = uiState.value.categories.firstOrNull { it.id == categoryId }?.name ?: "Category"
            showBanner("Moved to $catName")
        }
    }

    fun toggleAdditionalCategory(packageName: String, categoryId: String, assigned: Boolean) {
        viewModelScope.launch {
            repository.toggleAdditionalCategoryForApp(packageName, categoryId, assigned)
        }
    }

    fun approveSuggestedCategory(app: AppInfo) {
        val suggestedId = app.suggestedCategoryId ?: return
        assignAppCategory(app.packageName, suggestedId, keepExisting = false)
    }

    fun toggleAppFavorite(packageName: String) {
        viewModelScope.launch {
            repository.toggleFavorite(packageName)
        }
    }

    fun pinAppToHome(packageName: String, toDock: Boolean = false) {
        viewModelScope.launch {
            repository.addAppShortcutToHome(packageName, toDock)
            showBanner(if (toDock) "Pinned to Dock" else "Added to Home Screen")
            dismissAppContextMenu()
        }
    }

    fun removeShortcutFromHome(itemId: Long) {
        viewModelScope.launch {
            repository.removeHomeItem(itemId)
            showBanner("Removed from Home Screen")
            dismissAppContextMenu()
        }
    }

    fun moveHomeItem(
        itemId: Long,
        targetPage: Int,
        targetRow: Int,
        targetCol: Int,
        toDock: Boolean = false,
        fallbackPackageName: String? = null
    ) {
        viewModelScope.launch {
            repository.moveHomeItemToCell(itemId, targetPage, targetRow, targetCol, toDock, fallbackPackageName)
        }
    }

    fun mergeHomeItems(
        draggedItemId: Long,
        targetItemId: Long,
        draggedFallbackPkg: String? = null
    ) {
        viewModelScope.launch {
            val folderName = repository.mergeHomeItemsIntoFolder(draggedItemId, targetItemId, draggedFallbackPkg)
            if (folderName != null) {
                showBanner("Merged into '$folderName'")
            }
        }
    }

    fun toggleFolderStyle(folderId: Long) {
        viewModelScope.launch {
            val newStyleLabel = repository.toggleFolderDisplayStyle(folderId)
            if (newStyleLabel != null) {
                showBanner("Folder style: $newStyleLabel")
            }
        }
    }

    fun resizeFolder(
        folderId: Long,
        spanX: Int,
        spanY: Int,
        displayStyle: com.example.domain.model.FolderDisplayStyle
    ) {
        viewModelScope.launch {
            val label = repository.resizeAndStyleFolder(folderId, spanX, spanY, displayStyle)
            if (label != null) {
                showBanner("Resized to $label")
            }
        }
    }

    fun extractAppFromFolder(folderId: Long, packageName: String, targetPage: Int? = null) {
        viewModelScope.launch {
            val appName = repository.extractAppFromFolderToHome(folderId, packageName, targetPage)
            if (appName != null) {
                showBanner("Moved '$appName' to Home Screen")
            }
        }
    }

    fun createSmartFolder(
        folderName: String,
        memberPackages: List<String>,
        colorHex: String,
        pageIndex: Int = 0,
        spanX: Int = 2,
        spanY: Int = 2,
        displayStyle: com.example.domain.model.FolderDisplayStyle = com.example.domain.model.FolderDisplayStyle.BENTO_2X2
    ) {
        viewModelScope.launch {
            val styleSuffix = if (displayStyle == com.example.domain.model.FolderDisplayStyle.DENSE_3X3) "DENSE" else "BENTO"
            repository.createFolderOnHome(
                folderName = folderName,
                memberPackages = memberPackages,
                colorHex = colorHex,
                pageIndex = pageIndex,
                spanX = spanX,
                spanY = spanY,
                preferredStyle = styleSuffix
            )
            showBanner("Created folder '$folderName'")
        }
    }

    fun matchAppsForSmartIntent(prompt: String): List<AppInfo> {
        return repository.matchAppsForCustomIntent(prompt, uiState.value.installedApps)
    }

    fun updateFolder(folderId: Long, newName: String, memberPackages: List<String>, newColorHex: String? = null) {
        viewModelScope.launch {
            repository.updateFolderMembers(folderId, newName, memberPackages, newColorHex)
            showBanner("Updated folder '$newName'")
        }
    }

    fun deleteFolder(folderId: Long) {
        viewModelScope.launch {
            val item = uiState.value.homeItems.firstOrNull { it.folderId == folderId }
            if (item != null) {
                repository.removeHomeItem(item.id)
            }
            _openedFolderId.value = null
            showBanner("Removed folder")
        }
    }

    fun addSystemWidgetToHome(provider: SystemWidgetProviderItem, pageIndex: Int = 0) {
        viewModelScope.launch {
            val widgetId = widgetHostController.allocateWidgetId()
            val spanX = (provider.minWidthDp / 75).coerceIn(2, 4)
            val spanY = (provider.minHeightDp / 75).coerceIn(1, 3)
            repository.addWidgetToHome(
                appWidgetId = widgetId,
                providerComponentName = provider.providerComponentName,
                label = provider.label,
                spanX = spanX,
                spanY = spanY,
                pageIndex = pageIndex
            )
            _activeOverlay.value = ActiveOverlaySheet.NONE
            showBanner("Added widget '${provider.label}'")
        }
    }

    // --- Category Management ---

    fun createOrUpdateCategory(id: String?, name: String, colorHex: String) {
        viewModelScope.launch {
            repository.createOrUpdateCategory(id, name, colorHex)
            showBanner("Saved category '$name'")
        }
    }

    fun toggleCategoryVisibility(categoryId: String, isVisible: Boolean) {
        viewModelScope.launch {
            repository.toggleCategoryVisibility(categoryId, isVisible)
        }
    }

    fun reorderCategory(categoryId: String, moveUp: Boolean) {
        viewModelScope.launch {
            repository.moveCategoryOrder(categoryId, moveUp)
        }
    }

    fun deleteCustomCategory(categoryId: String) {
        viewModelScope.launch {
            repository.deleteCustomCategory(categoryId)
            if (_selectedCategoryId.value == categoryId) {
                _selectedCategoryId.value = DefaultCategory.ALL_APPS.id
            }
            showBanner("Deleted custom category")
        }
    }

    // --- AI Home Screen Auto-Arrangement ---

    fun generateLayoutPreview(mode: ArrangementMode) {
        viewModelScope.launch {
            repository.enrichInstalledAppsWithOnlineIntelligence()
            val preview = repository.buildLayoutPreview(mode, uiState.value.installedApps)
            _proposedLayoutPreview.value = preview
            _activeOverlay.value = ActiveOverlaySheet.AI_ORGANIZER_PREVIEW
        }
    }

    fun approveAndApplyProposedLayout() {
        val preview = _proposedLayoutPreview.value ?: return
        viewModelScope.launch {
            repository.applyProposedLayout(preview, saveUndoSnapshot = true)
            _proposedLayoutPreview.value = null
            _activeOverlay.value = ActiveOverlaySheet.NONE
            showBanner("Applied ${preview.mode.displayName} layout")
        }
    }

    fun undoLastLayoutChange() {
        viewModelScope.launch {
            val restored = repository.undoAndRestorePreviousLayout()
            if (restored) {
                showBanner("Restored previous home screen layout")
            } else {
                showBanner("No previous layout snapshot to restore")
            }
        }
    }

    // --- Debounced Unified Search ---

    /**
     * Updates the search input text immediately on the main thread while debouncing
     * result filtering by [SEARCH_DEBOUNCE_MS] so UI sections only update after the
     * user pauses typing, eliminating layout jitter.
     */
    fun updateUnifiedSearchQuery(query: String, immediate: Boolean = false) {
        _rawUnifiedSearchQuery.value = query
        val shouldDebounce = !immediate && query.isNotBlank()

        // 1. Update raw query immediately on main thread without mutating result lists mid-keystroke
        _searchState.update {
            it.copy(
                query = query,
                isSearching = shouldDebounce
            )
        }

        // 2. Cancel any in-flight debounce timer or previous search computation
        unifiedSearchJob?.cancel()
        unifiedSearchJob = viewModelScope.launch {
            if (shouldDebounce) {
                delay(SEARCH_DEBOUNCE_MS)
            }
            executeUnifiedSearchFilter(query)
        }
    }

    private suspend fun executeUnifiedSearchFilter(query: String) = withContext(Dispatchers.IO) {
        val state = uiState.value
        val appsMap = state.installedApps.associateBy { it.packageName }
        val matchedApps = searchEngine.searchApplications(query, state.installedApps)
        val matchedFolders = searchEngine.searchFolders(query, state.folders, appsMap)
        val hasContactsPerm = searchEngine.hasContactsPermission()
        val matchedContacts = if (state.settings.enableContactsSearch && hasContactsPerm && query.isNotBlank()) {
            searchEngine.searchContacts(query)
        } else {
            emptyList()
        }
        val matchedFiles = if (state.settings.enableFilesSearch && query.length >= 2) {
            searchEngine.searchLocalFiles(query)
        } else {
            emptyList()
        }
        val online = searchEngine.isNetworkOnline()
        val webSuggestions = if (state.settings.enableWebSuggestions && query.isNotBlank()) {
            searchEngine.buildWebSearchSuggestions(query, state.settings.defaultSearchEngine)
        } else {
            emptyList()
        }

        _searchState.update { current ->
            if (current.query == query) {
                current.copy(
                    debouncedQuery = query,
                    isSearching = false,
                    appResults = matchedApps,
                    folderResults = matchedFolders,
                    contactResults = matchedContacts,
                    fileResults = matchedFiles,
                    webSuggestions = webSuggestions,
                    isOnline = online,
                    hasContactsPermission = hasContactsPerm
                )
            } else {
                current
            }
        }
    }

    fun openEmbeddedWebSearch(queryOrUrl: String) {
        val trimmed = queryOrUrl.trim()
        if (trimmed.isEmpty()) return
        val settings = uiState.value.settings
        val online = searchEngine.isNetworkOnline()
        val url = when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            !trimmed.contains(" ") && trimmed.contains(".") && trimmed.length >= 4 -> "https://$trimmed"
            else -> searchEngine.buildSearchEngineUrl(trimmed, settings.defaultSearchEngine)
        }
        viewModelScope.launch {
            repository.recordSearchQuery(trimmed, "WEB")
        }
        _searchState.update {
            it.copy(
                activeWebViewUrl = url,
                isOnline = online
            )
        }
    }

    fun closeEmbeddedWebView() {
        _searchState.update { it.copy(activeWebViewUrl = null) }
    }

    fun recordSearchHistory(query: String, type: String = "UNIFIED") {
        viewModelScope.launch {
            repository.recordSearchQuery(query, type)
        }
    }

    fun deleteSearchHistoryEntry(query: String) {
        viewModelScope.launch {
            repository.deleteSearchHistoryEntry(query)
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            repository.clearSearchHistory()
            showBanner("Cleared search history")
        }
    }

    // --- Customization, Icon Packs & Local Backup ---

    fun updateSettings(transform: (LauncherSettingsEntity) -> LauncherSettingsEntity) {
        viewModelScope.launch {
            repository.updateSettings(transform)
            _iconPacks.value = repository.getAvailableIconPacks()
        }
    }

    fun setCustomAppIconOrLabel(packageName: String, customLabel: String?, customIconKey: String?) {
        viewModelScope.launch {
            repository.setCustomAppOverride(packageName, customLabel, customIconKey)
            showBanner("Updated custom app appearance")
        }
    }

    fun prepareLocalBackupExport() {
        viewModelScope.launch {
            _backupJsonExportText.value = repository.exportLocalBackupJson()
            showBanner("Generated local JSON backup")
        }
    }

    fun restoreFromBackupJson(jsonText: String) {
        viewModelScope.launch {
            val ok = repository.importLocalBackupJson(jsonText)
            if (ok) {
                showBanner("Restored launcher layout & settings")
            } else {
                showBanner("Invalid backup JSON format")
            }
        }
    }

    fun showBanner(message: String?) {
        _statusBannerMessage.value = message
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val appCtx = context.applicationContext
            val db = LauncherDatabase.getInstance(appCtx)
            val dao = db.launcherDao()
            val iconPackManager = IconPackManager(appCtx)
            val discoveryEngine = AppDiscoveryEngine(appCtx, iconPackManager)
            val aiClassifier = OfflineAiClassifier(appCtx)
            val onlineIntelligence = OnlineAppIntelligenceService(appCtx, aiClassifier)
            val homeOrganizer = AiHomeOrganizer()
            val backupManager = LocalBackupManager(dao)
            val searchEngine = UnifiedSearchEngine(appCtx)
            val widgetController = WidgetHostController(appCtx)
            val repo = LauncherRepository(
                dao = dao,
                discoveryEngine = discoveryEngine,
                aiClassifier = aiClassifier,
                homeOrganizer = homeOrganizer,
                iconPackManager = iconPackManager,
                backupManager = backupManager,
                onlineIntelligenceService = onlineIntelligence
            )
            @Suppress("UNCHECKED_CAST")
            return LauncherViewModel(
                repository = repo,
                discoveryEngine = discoveryEngine,
                searchEngine = searchEngine,
                widgetHostController = widgetController
            ) as T
        }
    }
}
