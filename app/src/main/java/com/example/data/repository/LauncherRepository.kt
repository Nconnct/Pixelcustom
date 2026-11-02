package com.example.data.repository

import com.example.ai.AiHomeOrganizer
import com.example.ai.OfflineAiClassifier
import com.example.ai.OnlineAppIntelligenceService
import com.example.data.local.AIClassificationEntity
import com.example.data.local.AIUserCorrectionEntity
import com.example.data.local.AppCategoryCrossRef
import com.example.data.local.AppEntity
import com.example.data.local.CategoryEntity
import com.example.data.local.FolderEntity
import com.example.data.local.HomeScreenItemEntity
import com.example.data.local.HomeScreenPageEntity
import com.example.data.local.IconOverrideEntity
import com.example.data.local.IconPackEntity
import com.example.data.local.LauncherDao
import com.example.data.local.LauncherDatabase
import com.example.data.local.LauncherSettingsEntity
import com.example.data.local.LayoutSnapshotEntity
import com.example.data.local.SearchHistoryEntity
import com.example.domain.model.AppInfo
import com.example.domain.model.ArrangementMode
import com.example.domain.model.CategoryModel
import com.example.domain.model.DefaultCategory
import com.example.domain.model.FolderModel
import com.example.domain.model.HomeItemType
import com.example.domain.model.HomeScreenItemModel
import com.example.domain.model.IconPackInfo
import com.example.domain.model.InstallationState
import com.example.domain.model.ProposedLayoutPreview
import com.example.engine.AppDiscoveryEngine
import com.example.engine.DiscoveredPackageMetadata
import com.example.engine.IconPackManager
import com.example.engine.LocalBackupManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

class LauncherRepository(
    private val dao: LauncherDao,
    private val discoveryEngine: AppDiscoveryEngine,
    private val aiClassifier: OfflineAiClassifier,
    private val homeOrganizer: AiHomeOrganizer,
    private val iconPackManager: IconPackManager,
    private val backupManager: LocalBackupManager,
    private val onlineIntelligenceService: OnlineAppIntelligenceService? = null
) {

    val settingsFlow: Flow<LauncherSettingsEntity?> = dao.observeSettings()
    val searchHistoryFlow: Flow<List<SearchHistoryEntity>> = dao.observeSearchHistory()
    val layoutSnapshotsFlow: Flow<List<LayoutSnapshotEntity>> = dao.observeLayoutSnapshots()

    val categoriesFlow: Flow<List<CategoryModel>> = combine(
        dao.observeCategories(),
        dao.observeInstalledApps(),
        dao.observeAppCategoryCrossRefs()
    ) { cats, apps, crossRefs ->
        val installedPkgSet = apps.mapTo(mutableSetOf()) { it.packageName }
        val countByCat = mutableMapOf<String, MutableSet<String>>()
        for (app in apps) {
            countByCat.getOrPut(app.primaryCategoryId) { mutableSetOf() }.add(app.packageName)
        }
        for (ref in crossRefs) {
            if (ref.packageName in installedPkgSet) {
                countByCat.getOrPut(ref.categoryId) { mutableSetOf() }.add(ref.packageName)
            }
        }
        cats.map { entity ->
            val count = if (entity.id == DefaultCategory.ALL_APPS.id) {
                apps.size
            } else {
                countByCat[entity.id]?.size ?: 0
            }
            CategoryModel(
                id = entity.id,
                name = entity.name,
                iconName = entity.iconName,
                colorHex = entity.colorHex,
                sortOrder = entity.sortOrder,
                isVisibleInDrawer = entity.isVisibleInDrawer,
                isSystemDefault = entity.isSystemDefault,
                appCount = count
            )
        }
    }

    val installedAppsFlow: Flow<List<AppInfo>> = combine(
        dao.observeInstalledApps(),
        dao.observeCategories(),
        dao.observeAppCategoryCrossRefs(),
        dao.observeIconOverrides(),
        dao.observeSettings()
    ) { appEntities, categoryEntities, crossRefs, iconOverrides, settings ->
        val categoryNameMap = categoryEntities.associate { it.id to it.name }
        val crossRefsByPkg = crossRefs.groupBy({ it.packageName }, { it.categoryId })
        val overrideMap = iconOverrides.associateBy { it.targetPackageName }
        val activePack = settings?.activeIconPackPackage.orEmpty()

        appEntities.map { entity ->
            val overrideItem = overrideMap[entity.packageName]
            val displayLabel = overrideItem?.customLabel?.takeIf { it.isNotBlank() } ?: entity.appName
            val assignedCats = (listOf(entity.primaryCategoryId) + crossRefsByPkg[entity.packageName].orEmpty()).distinct()
            val iconBitmap = discoveryEngine.getOrLoadAppIcon(
                packageName = entity.packageName,
                appName = displayLabel,
                activeIconPack = activePack,
                customIconKey = overrideItem?.customIconKey
            )
            val suggestedName = entity.suggestedCategoryId?.let { categoryNameMap[it] }
            AppInfo(
                appName = displayLabel,
                packageName = entity.packageName,
                activityName = entity.activityName,
                appIcon = iconBitmap,
                isSystemApp = entity.isSystemApp,
                categoryId = entity.primaryCategoryId,
                categoryName = categoryNameMap[entity.primaryCategoryId] ?: "Uncategorized",
                allCategoryIds = assignedCats,
                categoryConfidence = entity.categoryConfidence,
                suggestedCategoryId = entity.suggestedCategoryId,
                suggestedCategoryName = suggestedName,
                installationState = if (entity.isDisabled) InstallationState.DISABLED else InstallationState.INSTALLED,
                isUserOverridden = entity.isUserOverridden,
                isFavorite = entity.isFavorite,
                launchCount = entity.launchCount,
                firstInstallTime = entity.firstInstallTime,
                lastUpdateTime = entity.lastUpdateTime,
                customIconKey = overrideItem?.customIconKey
            )
        }
    }

    val homeItemsFlow: Flow<List<HomeScreenItemModel>> = dao.observeHomeItems().combine(dao.observeFolders()) { items, _ ->
        items.map { it.toDomainModel() }
    }

    val foldersFlow: Flow<List<FolderModel>> = dao.observeFolders().combine(dao.observeInstalledApps()) { folders, _ ->
        folders.map { it.toDomainModel() }
    }

    suspend fun initializeLauncherState() = withContext(Dispatchers.IO) {
        // 1. Seed default settings if absent, and ensure at least 3 Home Screen pages
        val existingSettings = dao.getSettingsSnapshot()
        if (existingSettings == null) {
            dao.upsertSettings(LauncherSettingsEntity(homePageCount = 3))
        } else if (existingSettings.homePageCount < 3) {
            dao.upsertSettings(existingSettings.copy(homePageCount = 3))
        }

        // 2. Seed default 18 categories without overwriting user modifications
        val existingCats = dao.getCategoriesSnapshot().associateBy { it.id }
        val defaultCats = LauncherDatabase.buildDefaultCategoryEntities()
        val missingCats = defaultCats.filter { it.id !in existingCats }
        if (missingCats.isNotEmpty()) {
            dao.upsertCategories(missingCats)
        }

        // 3. Seed 3 default home pages if absent or fewer than 3
        val existingPages = dao.getHomePagesSnapshot()
        if (existingPages.size < 3) {
            dao.upsertHomePages(
                listOf(
                    HomeScreenPageEntity(pageIndex = 0, title = "Home", hasAtAGlanceWidget = true),
                    HomeScreenPageEntity(pageIndex = 1, title = "Workspace 2", hasAtAGlanceWidget = false),
                    HomeScreenPageEntity(pageIndex = 2, title = "Workspace 3", hasAtAGlanceWidget = false)
                )
            )
        }

        // 4. Sync learned user corrections into the offline AI classifier
        val userCorrections = dao.getAllUserCorrections()
        aiClassifier.syncUserCorrections(userCorrections)

        // 5. Discover installed apps and run offline AI classification where needed
        refreshInstalledApps()

        // 6. Seed 3-page ML Smart Folders (Page 1) + Extra Apps (Pages 2 & 3) if empty or still on legacy 1-page layout
        val currentHomeItems = dao.getHomeItemsSnapshot()
        val currentFolders = dao.getFoldersSnapshot()
        val hasMultiPageItems = currentHomeItems.any { !it.isDockItem && it.pageIndex >= 1 }
        if (currentHomeItems.isEmpty() || currentFolders.isEmpty() || !hasMultiPageItems) {
            seedDefaultHomeLayout()
        }
    }

    /**
     * When internet is available, fetches Play Store metadata + AI semantic classification
     * for uncategorized or lower-confidence apps, updates Room, and keeps ML Smart Folders clean.
     */
    suspend fun enrichInstalledAppsWithOnlineIntelligence(): Int = withContext(Dispatchers.IO) {
        val onlineService = onlineIntelligenceService ?: return@withContext 0
        if (!onlineService.isInternetAvailable()) return@withContext 0

        val allApps = dao.getAllAppsSnapshot().filter { it.isInstalled && !it.isUserOverridden }
        val candidates = allApps.filter {
            it.primaryCategoryId == DefaultCategory.UNCATEGORIZED.id ||
                it.primaryCategoryId == DefaultCategory.UTILITIES.id ||
                it.categoryConfidence < 0.85f
        }.map { Triple(it.packageName, it.appName, it.isSystemApp) }

        if (candidates.isEmpty()) return@withContext 0

        val enrichedList = onlineService.enrichAppsWhenOnline(candidates)
        var updatedCount = 0
        for (res in enrichedList) {
            val current = allApps.firstOrNull { it.packageName == res.packageName } ?: continue
            if (res.categoryId != current.primaryCategoryId || res.confidence > current.categoryConfidence) {
                dao.updateAppCategory(
                    packageName = res.packageName,
                    categoryId = res.categoryId,
                    confidence = res.confidence,
                    isManual = false
                )
                dao.upsertAIClassification(
                    AIClassificationEntity(
                        packageName = res.packageName,
                        predictedCategoryId = res.categoryId,
                        suggestedSecondaryCategoryId = res.secondaryCategoryId,
                        confidence = res.confidence,
                        classificationSource = res.source
                    )
                )
                dao.insertAppCategoryCrossRef(
                    AppCategoryCrossRef(
                        packageName = res.packageName,
                        categoryId = res.categoryId,
                        isManualAssignment = false
                    )
                )
                updatedCount++
            }
        }

        if (updatedCount > 0) {
            seedDefaultHomeLayout()
        }
        updatedCount
    }

    suspend fun refreshInstalledApps() = withContext(Dispatchers.IO) {
        val discoveredList = discoveryEngine.discoverLaunchableApps()
        val existingAppsMap = dao.getAllAppsSnapshot().associateBy { it.packageName }
        val existingClassifications = dao.getAllAIClassifications().associateBy { it.packageName }
        val discoveredPkgSet = discoveredList.mapTo(mutableSetOf()) { it.packageName }

        val updatedAppEntities = mutableListOf<AppEntity>()
        val newClassifications = mutableListOf<AIClassificationEntity>()
        val newCrossRefs = mutableListOf<AppCategoryCrossRef>()

        for (meta in discoveredList) {
            val existing = existingAppsMap[meta.packageName]
            if (existing != null && existing.isUserOverridden) {
                // Respect manual override; only refresh label/timestamps
                updatedAppEntities.add(
                    existing.copy(
                        activityName = meta.activityName,
                        appName = meta.appName,
                        isSystemApp = meta.isSystemApp,
                        isInstalled = true,
                        isDisabled = meta.isDisabled,
                        lastUpdateTime = meta.lastUpdateTime
                    )
                )
            } else {
                val cachedAi = existingClassifications[meta.packageName]
                val prediction = if (cachedAi != null && existing != null) {
                    null
                } else {
                    aiClassifier.classifyApp(
                        packageName = meta.packageName,
                        appName = meta.appName,
                        isSystemApp = meta.isSystemApp,
                        androidCategoryHint = meta.androidCategoryHint
                    )
                }

                val catId = prediction?.categoryId
                    ?: cachedAi?.predictedCategoryId
                    ?: existing?.primaryCategoryId
                    ?: DefaultCategory.UNCATEGORIZED.id
                val conf = prediction?.confidence
                    ?: cachedAi?.confidence
                    ?: existing?.categoryConfidence
                    ?: 0.5f
                val suggestedId = prediction?.suggestedCategoryId ?: cachedAi?.suggestedSecondaryCategoryId

                updatedAppEntities.add(
                    AppEntity(
                        packageName = meta.packageName,
                        activityName = meta.activityName,
                        appName = meta.appName,
                        isSystemApp = meta.isSystemApp,
                        primaryCategoryId = catId,
                        categoryConfidence = conf,
                        suggestedCategoryId = suggestedId,
                        isInstalled = true,
                        isDisabled = meta.isDisabled,
                        isUserOverridden = false,
                        isFavorite = existing?.isFavorite ?: false,
                        launchCount = existing?.launchCount ?: 0,
                        firstInstallTime = existing?.firstInstallTime ?: meta.firstInstallTime,
                        lastUpdateTime = meta.lastUpdateTime
                    )
                )

                if (prediction != null) {
                    newClassifications.add(
                        AIClassificationEntity(
                            packageName = meta.packageName,
                            predictedCategoryId = prediction.categoryId,
                            suggestedSecondaryCategoryId = prediction.suggestedCategoryId,
                            confidence = prediction.confidence,
                            classificationSource = prediction.source
                        )
                    )
                }
                newCrossRefs.add(
                    AppCategoryCrossRef(
                        packageName = meta.packageName,
                        categoryId = catId,
                        isManualAssignment = false
                    )
                )
            }
        }

        // Mark uninstalled apps without losing user history/overrides
        for ((pkg, existing) in existingAppsMap) {
            if (pkg !in discoveredPkgSet && existing.isInstalled) {
                updatedAppEntities.add(existing.copy(isInstalled = false))
            }
        }

        if (updatedAppEntities.isNotEmpty()) {
            dao.upsertApps(updatedAppEntities)
        }
        if (newClassifications.isNotEmpty()) {
            dao.upsertAIClassifications(newClassifications)
        }
        if (newCrossRefs.isNotEmpty()) {
            dao.insertAppCategoryCrossRefs(newCrossRefs)
        }
    }

    suspend fun handleIncrementalPackageAddedOrUpdated(packageName: String) = withContext(Dispatchers.IO) {
        val meta: DiscoveredPackageMetadata = discoveryEngine.inspectSinglePackage(packageName) ?: return@withContext
        val existing = dao.getAppByPackage(packageName)
        if (existing != null && existing.isUserOverridden) {
            dao.upsertApp(
                existing.copy(
                    activityName = meta.activityName,
                    appName = meta.appName,
                    isInstalled = true,
                    isDisabled = meta.isDisabled,
                    lastUpdateTime = System.currentTimeMillis()
                )
            )
            return@withContext
        }

        val prediction = aiClassifier.classifyApp(
            packageName = meta.packageName,
            appName = meta.appName,
            isSystemApp = meta.isSystemApp,
            androidCategoryHint = meta.androidCategoryHint
        )
        dao.upsertApp(
            AppEntity(
                packageName = meta.packageName,
                activityName = meta.activityName,
                appName = meta.appName,
                isSystemApp = meta.isSystemApp,
                primaryCategoryId = prediction.categoryId,
                categoryConfidence = prediction.confidence,
                suggestedCategoryId = prediction.suggestedCategoryId,
                isInstalled = true,
                isDisabled = meta.isDisabled,
                isUserOverridden = false,
                isFavorite = existing?.isFavorite ?: false,
                launchCount = existing?.launchCount ?: 0,
                firstInstallTime = existing?.firstInstallTime ?: meta.firstInstallTime,
                lastUpdateTime = System.currentTimeMillis()
            )
        )
        dao.upsertAIClassification(
            AIClassificationEntity(
                packageName = meta.packageName,
                predictedCategoryId = prediction.categoryId,
                suggestedSecondaryCategoryId = prediction.suggestedCategoryId,
                confidence = prediction.confidence,
                classificationSource = prediction.source
            )
        )
        dao.insertAppCategoryCrossRef(
            AppCategoryCrossRef(
                packageName = meta.packageName,
                categoryId = prediction.categoryId,
                isManualAssignment = false
            )
        )
    }

    suspend fun handleIncrementalPackageRemoved(packageName: String) = withContext(Dispatchers.IO) {
        dao.markAppUninstalled(packageName)
        dao.deleteHomeItemsByPackage(packageName)
    }

    private suspend fun seedDefaultHomeLayout() {
        val settings = dao.getSettingsSnapshot() ?: LauncherSettingsEntity()
        val apps = dao.getAllAppsSnapshot().filter { it.isInstalled }.map {
            AppInfo(
                appName = it.appName,
                packageName = it.packageName,
                activityName = it.activityName,
                isSystemApp = it.isSystemApp,
                categoryId = it.primaryCategoryId,
                categoryConfidence = it.categoryConfidence,
                isFavorite = it.isFavorite,
                launchCount = it.launchCount
            )
        }
        if (apps.isEmpty()) return

        val preview = homeOrganizer.generateProposedLayout(
            mode = ArrangementMode.AI_ORGANIZED,
            installedApps = apps,
            existingItems = emptyList(),
            existingFolders = emptyList(),
            gridColumns = settings.homeGridColumns,
            gridRows = settings.homeGridRows,
            dockCapacity = settings.dockIconCount
        )
        applyProposedLayout(preview, saveUndoSnapshot = false)
        dao.upsertSettings(
            settings.copy(
                homePageCount = maxOf(3, preview.pagesCount),
                arrangementMode = ArrangementMode.AI_ORGANIZED.name
            )
        )
    }

    suspend fun assignAppToCategoryManually(
        packageName: String,
        targetCategoryId: String,
        keepExistingCategories: Boolean = false
    ) = withContext(Dispatchers.IO) {
        val app = dao.getAppByPackage(packageName) ?: return@withContext
        val previousCategory = app.primaryCategoryId

        dao.updateAppCategory(
            packageName = packageName,
            categoryId = targetCategoryId,
            confidence = 1.0f,
            isManual = true
        )

        if (!keepExistingCategories) {
            dao.deleteAutoCrossRefsForPackage(packageName)
        }
        dao.insertAppCategoryCrossRef(
            AppCategoryCrossRef(
                packageName = packageName,
                categoryId = targetCategoryId,
                isManualAssignment = true
            )
        )

        val tokens = aiClassifier.extractCleanTokens(packageName, app.appName).joinToString(",")
        val correction = AIUserCorrectionEntity(
            packageName = packageName,
            previousCategoryId = previousCategory,
            correctedCategoryId = targetCategoryId,
            extractedTokensCsv = tokens
        )
        dao.upsertUserCorrection(correction)
        aiClassifier.syncUserCorrections(dao.getAllUserCorrections())
    }

    suspend fun toggleAdditionalCategoryForApp(
        packageName: String,
        categoryId: String,
        assigned: Boolean
    ) = withContext(Dispatchers.IO) {
        if (assigned) {
            dao.insertAppCategoryCrossRef(
                AppCategoryCrossRef(
                    packageName = packageName,
                    categoryId = categoryId,
                    isManualAssignment = true
                )
            )
        } else {
            dao.removeAppFromCategory(packageName, categoryId)
        }
    }

    suspend fun createOrUpdateCategory(
        id: String?,
        name: String,
        colorHex: String,
        isVisibleInDrawer: Boolean = true
    ) = withContext(Dispatchers.IO) {
        val current = dao.getCategoriesSnapshot()
        val targetId = id ?: "cat_custom_${System.currentTimeMillis()}"
        val existing = current.firstOrNull { it.id == targetId }
        val sortOrder = existing?.sortOrder ?: (current.maxOfOrNull { it.sortOrder } ?: 17) + 1
        dao.upsertCategory(
            CategoryEntity(
                id = targetId,
                name = name.trim(),
                iconName = existing?.iconName ?: "folder",
                colorHex = colorHex,
                sortOrder = sortOrder,
                isVisibleInDrawer = isVisibleInDrawer,
                isSystemDefault = existing?.isSystemDefault ?: false
            )
        )
    }

    suspend fun toggleCategoryVisibility(categoryId: String, isVisible: Boolean) = withContext(Dispatchers.IO) {
        val existing = dao.getCategoriesSnapshot().firstOrNull { it.id == categoryId } ?: return@withContext
        dao.upsertCategory(existing.copy(isVisibleInDrawer = isVisible))
    }

    suspend fun moveCategoryOrder(categoryId: String, moveUp: Boolean) = withContext(Dispatchers.IO) {
        val cats = dao.getCategoriesSnapshot().sortedBy { it.sortOrder }.toMutableList()
        val idx = cats.indexOfFirst { it.id == categoryId }
        if (idx == -1) return@withContext
        val swapIdx = if (moveUp) idx - 1 else idx + 1
        if (swapIdx !in cats.indices) return@withContext
        val a = cats[idx]
        val b = cats[swapIdx]
        cats[idx] = b.copy(sortOrder = a.sortOrder)
        cats[swapIdx] = a.copy(sortOrder = b.sortOrder)
        dao.upsertCategories(listOf(cats[idx], cats[swapIdx]))
    }

    suspend fun deleteCustomCategory(categoryId: String) = withContext(Dispatchers.IO) {
        dao.deleteCustomCategory(categoryId)
    }

    suspend fun toggleFavorite(packageName: String) = withContext(Dispatchers.IO) {
        val app = dao.getAppByPackage(packageName) ?: return@withContext
        dao.updateAppFavorite(packageName, !app.isFavorite)
    }

    suspend fun launchAppAndRecordUsage(packageName: String, activityName: String = ""): Boolean {
        val launched = discoveryEngine.launchApp(packageName, activityName)
        if (launched) {
            withContext(Dispatchers.IO) {
                dao.incrementLaunchCount(packageName)
            }
        }
        return launched
    }

    // --- Home Screen Manipulation ---

    suspend fun addAppShortcutToHome(packageName: String, toDock: Boolean = false) = withContext(Dispatchers.IO) {
        val app = dao.getAppByPackage(packageName) ?: return@withContext
        val settings = dao.getSettingsSnapshot() ?: LauncherSettingsEntity()
        val items = dao.getHomeItemsSnapshot()

        if (toDock) {
            val dockItems = items.filter { it.isDockItem }
            if (dockItems.any { it.packageName == packageName }) return@withContext
            val nextDockIndex = (dockItems.maxOfOrNull { it.dockIndex } ?: -1) + 1
            if (nextDockIndex < settings.dockIconCount) {
                dao.insertHomeItem(
                    HomeScreenItemEntity(
                        pageIndex = 0,
                        row = settings.homeGridRows - 1,
                        column = nextDockIndex,
                        isDockItem = true,
                        dockIndex = nextDockIndex,
                        itemType = HomeItemType.APP_SHORTCUT.name,
                        packageName = packageName,
                        customLabel = app.appName,
                        isPinnedByUser = true
                    )
                )
                return@withContext
            }
        }

        // Find next open cell on workspace
        val occupied = items.filter { !it.isDockItem }.mapTo(mutableSetOf()) {
            Triple(it.pageIndex, it.row, it.column)
        }
        var targetCell = Triple(0, 1, 0)
        outer@ for (p in 0 until maxOf(3, settings.homePageCount)) {
            val startRow = if (p == 0 && settings.showAtAGlanceWidget) 1 else 0
            for (r in startRow until settings.homeGridRows) {
                for (c in 0 until settings.homeGridColumns) {
                    val cell = Triple(p, r, c)
                    if (cell !in occupied) {
                        targetCell = cell
                        break@outer
                    }
                }
            }
        }

        dao.insertHomeItem(
            HomeScreenItemEntity(
                pageIndex = targetCell.first,
                row = targetCell.second,
                column = targetCell.third,
                isDockItem = false,
                itemType = HomeItemType.APP_SHORTCUT.name,
                packageName = packageName,
                customLabel = app.appName,
                isPinnedByUser = true
            )
        )
    }

    suspend fun moveHomeItemToCell(
        itemId: Long,
        targetPage: Int,
        targetRow: Int,
        targetColumn: Int,
        toDock: Boolean = false
    ) = withContext(Dispatchers.IO) {
        val items = dao.getHomeItemsSnapshot()
        val moving = items.firstOrNull { it.id == itemId } ?: return@withContext
        val occupant = items.firstOrNull {
            it.id != itemId && it.isDockItem == toDock &&
                (if (toDock) it.dockIndex == targetColumn else (it.pageIndex == targetPage && it.row == targetRow && it.column == targetColumn))
        }

        // If dropping an app shortcut onto another app shortcut, offer/create a folder or swap positions
        if (occupant != null) {
            dao.updateHomeItem(
                occupant.copy(
                    pageIndex = moving.pageIndex,
                    row = moving.row,
                    column = moving.column,
                    isDockItem = moving.isDockItem,
                    dockIndex = moving.dockIndex
                )
            )
        }

        dao.updateHomeItem(
            moving.copy(
                pageIndex = targetPage,
                row = targetRow,
                column = targetColumn,
                isDockItem = toDock,
                dockIndex = if (toDock) targetColumn else 0,
                isPinnedByUser = true
            )
        )
    }

    suspend fun createFolderOnHome(
        folderName: String,
        memberPackages: List<String>,
        colorHex: String = "#4285F4",
        pageIndex: Int = 0
    ) = withContext(Dispatchers.IO) {
        val settings = dao.getSettingsSnapshot() ?: LauncherSettingsEntity()
        val items = dao.getHomeItemsSnapshot()
        val occupied = items.filter { !it.isDockItem }.mapTo(mutableSetOf()) {
            Triple(it.pageIndex, it.row, it.column)
        }
        var cell = Triple(pageIndex, 1, 0)
        outer@ for (p in pageIndex until maxOf(3, settings.homePageCount)) {
            val startRow = if (p == 0 && settings.showAtAGlanceWidget) 1 else 0
            for (r in startRow until settings.homeGridRows) {
                for (c in 0 until settings.homeGridColumns) {
                    val candidate = Triple(p, r, c)
                    if (candidate !in occupied) {
                        cell = candidate
                        break@outer
                    }
                }
            }
        }

        val folderId = dao.insertFolder(
            FolderEntity(
                folderName = folderName.ifBlank { "Smart Folder" },
                pageIndex = cell.first,
                row = cell.second,
                column = cell.third,
                colorHex = colorHex,
                memberPackagesCsv = memberPackages.distinct().joinToString(",")
            )
        )
        dao.insertHomeItem(
            HomeScreenItemEntity(
                pageIndex = cell.first,
                row = cell.second,
                column = cell.third,
                isDockItem = false,
                itemType = HomeItemType.FOLDER.name,
                customLabel = folderName.ifBlank { "Smart Folder" },
                folderId = folderId,
                isPinnedByUser = true
            )
        )
    }

    suspend fun updateFolderMembers(
        folderId: Long,
        newName: String,
        memberPackages: List<String>
    ) = withContext(Dispatchers.IO) {
        val existing = dao.getFoldersSnapshot().firstOrNull { it.id == folderId } ?: return@withContext
        dao.insertFolder(
            existing.copy(
                folderName = newName.ifBlank { existing.folderName },
                memberPackagesCsv = memberPackages.distinct().joinToString(",")
            )
        )
    }

    suspend fun removeHomeItem(itemId: Long) = withContext(Dispatchers.IO) {
        val item = dao.getHomeItemsSnapshot().firstOrNull { it.id == itemId } ?: return@withContext
        if (item.folderId != null) {
            dao.deleteFolderById(item.folderId)
        }
        dao.deleteHomeItemById(itemId)
    }

    suspend fun addWidgetToHome(
        appWidgetId: Int,
        providerComponentName: String,
        label: String,
        spanX: Int = 4,
        spanY: Int = 2,
        pageIndex: Int = 0
    ) = withContext(Dispatchers.IO) {
        dao.insertHomeItem(
            HomeScreenItemEntity(
                pageIndex = pageIndex,
                row = 1,
                column = 0,
                spanX = spanX.coerceIn(1, 4),
                spanY = spanY.coerceIn(1, 4),
                isDockItem = false,
                itemType = HomeItemType.WIDGET.name,
                customLabel = label,
                appWidgetId = appWidgetId,
                widgetProviderName = providerComponentName,
                isPinnedByUser = true
            )
        )
    }

    // --- AI Auto-Arrangement & Layout Undo/Restore ---

    suspend fun buildLayoutPreview(
        mode: ArrangementMode,
        installedApps: List<AppInfo>
    ): ProposedLayoutPreview = withContext(Dispatchers.IO) {
        val settings = dao.getSettingsSnapshot() ?: LauncherSettingsEntity()
        val currentItems = dao.getHomeItemsSnapshot().map { it.toDomainModel() }
        val currentFolders = dao.getFoldersSnapshot().map { it.toDomainModel() }
        homeOrganizer.generateProposedLayout(
            mode = mode,
            installedApps = installedApps,
            existingItems = currentItems,
            existingFolders = currentFolders,
            gridColumns = settings.homeGridColumns,
            gridRows = settings.homeGridRows,
            dockCapacity = settings.dockIconCount
        )
    }

    suspend fun applyProposedLayout(
        preview: ProposedLayoutPreview,
        saveUndoSnapshot: Boolean = true
    ) = withContext(Dispatchers.IO) {
        if (saveUndoSnapshot) {
            val currentItems = dao.getHomeItemsSnapshot()
            val currentFolders = dao.getFoldersSnapshot()
            val currentSettings = dao.getSettingsSnapshot() ?: LauncherSettingsEntity()
            if (currentItems.isNotEmpty()) {
                val snapshot = homeOrganizer.serializeLayoutSnapshot(
                    snapshotName = "Before ${preview.mode.displayName}",
                    mode = currentSettings.arrangementMode,
                    items = currentItems,
                    folders = currentFolders
                )
                dao.insertLayoutSnapshot(snapshot)
            }
        }

        dao.clearAllHomeItems()
        dao.clearAllFolders()

        val tempFolderIdMap = mutableMapOf<Long, Long>()
        for (folder in preview.proposedFolders) {
            val realId = dao.insertFolder(
                FolderEntity(
                    folderName = folder.folderName,
                    pageIndex = folder.pageIndex,
                    row = folder.row,
                    column = folder.column,
                    colorHex = folder.colorHex,
                    memberPackagesCsv = folder.memberPackages.joinToString(",")
                )
            )
            tempFolderIdMap[folder.id] = realId
        }

        val itemEntities = preview.proposedItems.map { item ->
            HomeScreenItemEntity(
                pageIndex = item.pageIndex,
                row = item.row,
                column = item.column,
                spanX = item.spanX,
                spanY = item.spanY,
                isDockItem = item.isDockItem,
                dockIndex = item.dockIndex,
                itemType = item.itemType.name,
                packageName = item.packageName,
                customLabel = item.customLabel,
                folderId = item.folderId?.let { tempFolderIdMap[it] ?: it },
                appWidgetId = item.appWidgetId,
                widgetProviderName = item.widgetProviderName,
                isPinnedByUser = item.isPinnedByUser
            )
        }
        dao.insertHomeItems(itemEntities)

        val settings = dao.getSettingsSnapshot() ?: LauncherSettingsEntity()
        dao.upsertSettings(
            settings.copy(
                arrangementMode = preview.mode.name,
                homePageCount = maxOf(settings.homePageCount, preview.pagesCount)
            )
        )
    }

    suspend fun undoAndRestorePreviousLayout(): Boolean = withContext(Dispatchers.IO) {
        val latestSnapshot = dao.getLatestLayoutSnapshot() ?: return@withContext false
        val (restoredItems, restoredFolders) = homeOrganizer.deserializeLayoutSnapshot(latestSnapshot)
        dao.clearAllHomeItems()
        dao.clearAllFolders()
        dao.insertFolders(restoredFolders)
        dao.insertHomeItems(restoredItems)

        val settings = dao.getSettingsSnapshot() ?: LauncherSettingsEntity()
        dao.upsertSettings(settings.copy(arrangementMode = latestSnapshot.arrangementMode))
        true
    }

    // --- Settings, Icon Packs, Overrides & Search History ---

    suspend fun updateSettings(transform: (LauncherSettingsEntity) -> LauncherSettingsEntity) =
        withContext(Dispatchers.IO) {
            val current = dao.getSettingsSnapshot() ?: LauncherSettingsEntity()
            val updated = transform(current)
            if (updated.activeIconPackPackage != current.activeIconPackPackage) {
                discoveryEngine.clearIconCache()
            }
            dao.upsertSettings(updated)
        }

    suspend fun getAvailableIconPacks(): List<IconPackInfo> = withContext(Dispatchers.IO) {
        val settings = dao.getSettingsSnapshot() ?: LauncherSettingsEntity()
        val packs = iconPackManager.discoverInstalledIconPacks(settings.activeIconPackPackage)
        dao.upsertIconPacks(
            packs.filter { it.packageName.isNotEmpty() }.map {
                IconPackEntity(
                    packageName = it.packageName,
                    name = it.name,
                    iconCount = it.iconCount,
                    isApplied = it.isApplied
                )
            }
        )
        packs
    }

    suspend fun setCustomAppOverride(
        packageName: String,
        customLabel: String?,
        customIconKey: String?
    ) = withContext(Dispatchers.IO) {
        discoveryEngine.clearIconCache()
        if (customLabel.isNullOrBlank() && customIconKey.isNullOrBlank()) {
            dao.removeIconOverride(packageName)
        } else {
            dao.upsertIconOverride(
                IconOverrideEntity(
                    targetPackageName = packageName,
                    customIconKey = customIconKey.orEmpty(),
                    customLabel = customLabel?.trim()
                )
            )
        }
    }

    suspend fun recordSearchQuery(query: String, type: String = "UNIFIED") = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.isEmpty()) return@withContext
        dao.deleteSearchHistoryByQuery(clean)
        dao.insertSearchHistory(SearchHistoryEntity(query = clean, searchType = type))
        dao.trimSearchHistoryToMax50()
    }

    suspend fun deleteSearchHistoryEntry(query: String) = withContext(Dispatchers.IO) {
        dao.deleteSearchHistoryByQuery(query)
    }

    suspend fun clearSearchHistory() = withContext(Dispatchers.IO) {
        dao.clearSearchHistory()
    }

    suspend fun exportLocalBackupJson(): String = withContext(Dispatchers.IO) {
        backupManager.exportBackupJson()
    }

    suspend fun importLocalBackupJson(json: String): Boolean = withContext(Dispatchers.IO) {
        val ok = backupManager.importBackupJson(json)
        if (ok) {
            aiClassifier.syncUserCorrections(dao.getAllUserCorrections())
            discoveryEngine.clearIconCache()
        }
        ok
    }

    private fun HomeScreenItemEntity.toDomainModel() = HomeScreenItemModel(
        id = id,
        pageIndex = pageIndex,
        row = row,
        column = column,
        spanX = spanX,
        spanY = spanY,
        isDockItem = isDockItem,
        dockIndex = dockIndex,
        itemType = runCatching { HomeItemType.valueOf(itemType) }.getOrDefault(HomeItemType.APP_SHORTCUT),
        packageName = packageName,
        customLabel = customLabel,
        folderId = folderId,
        appWidgetId = appWidgetId,
        widgetProviderName = widgetProviderName,
        isPinnedByUser = isPinnedByUser
    )

    private fun FolderEntity.toDomainModel() = FolderModel(
        id = id,
        folderName = folderName,
        pageIndex = pageIndex,
        row = row,
        column = column,
        colorHex = colorHex,
        memberPackages = memberPackagesCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    )
}
