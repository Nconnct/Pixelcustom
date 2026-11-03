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
        // Detect a genuinely fresh install before writing the default settings row.
        // Existing users must never have their customized home layout re-seeded on every launch.
        val existingSettings = dao.getSettingsSnapshot()
        val isFirstInstall = existingSettings == null && dao.getHomeItemsSnapshot().isEmpty()
        if (existingSettings == null) {
            dao.upsertSettings(LauncherSettingsEntity())
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

        // 6. Seed a clean four-folder starter home only on a true first install.
        // Later launches and app-category refreshes must preserve every user edit.
        if (isFirstInstall) {
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

        if (updatedCount > 0 && dao.getHomeItemsSnapshot().isEmpty()) {
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

        // Reuse the launcher's essential-app ranking for the dock, but do not scatter
        // individual shortcuts across the first-run home page.
        val dockPreview = homeOrganizer.generateProposedLayout(
            mode = ArrangementMode.PIXEL_DEFAULT,
            installedApps = apps,
            existingItems = emptyList(),
            existingFolders = emptyList(),
            gridColumns = settings.homeGridColumns,
            gridRows = settings.homeGridRows,
            dockCapacity = settings.dockIconCount
        )
        val dockPackages = dockPreview.dockPackages.take(settings.dockIconCount).toSet()
        val appByPackage = apps.associateBy { it.packageName }

        val starterFolders = listOf(
            Triple(
                "Social",
                "#10B981:BENTO",
                setOf(DefaultCategory.SOCIAL.id, DefaultCategory.MESSAGING.id)
            ),
            Triple(
                "Media",
                "#EF4444:BENTO",
                setOf(
                    DefaultCategory.VIDEO_STREAMING.id,
                    DefaultCategory.MUSIC_AUDIO.id,
                    DefaultCategory.PHOTOGRAPHY.id,
                    DefaultCategory.GAMES.id
                )
            ),
            Triple(
                "Tools",
                "#3B82F6:BENTO",
                setOf(
                    DefaultCategory.BROWSERS.id,
                    DefaultCategory.INTERNET.id,
                    DefaultCategory.UTILITIES.id,
                    DefaultCategory.SYSTEM_APPS.id,
                    DefaultCategory.BANKING.id,
                    DefaultCategory.UPI_PAYMENTS.id,
                    DefaultCategory.SHOPPING.id,
                    DefaultCategory.UNCATEGORIZED.id
                )
            ),
            Triple(
                "Work",
                "#8B5CF6:BENTO",
                setOf(
                    DefaultCategory.PRODUCTIVITY.id,
                    DefaultCategory.EDUCATION.id,
                    DefaultCategory.TECH_DEV.id
                )
            )
        )

        val cols = settings.homeGridColumns.coerceIn(3, 6)
        val rows = settings.homeGridRows.coerceIn(4, 7)
        val startRow = if (settings.showAtAGlanceWidget) 1 else 0
        val folderRow = (rows - 2).coerceIn(startRow, rows - 1)
        val homeItems = mutableListOf<HomeScreenItemEntity>()

        starterFolders.forEachIndexed { index, (name, color, categories) ->
            val column = index % cols
            val row = (folderRow + index / cols).coerceAtMost(rows - 1)
            val members = apps
                .asSequence()
                .filter { it.packageName !in dockPackages && it.categoryId in categories }
                .map { it.packageName }
                .distinct()
                .toList()

            val folderId = dao.insertFolder(
                FolderEntity(
                    folderName = name,
                    pageIndex = 0,
                    row = row,
                    column = column,
                    colorHex = color,
                    memberPackagesCsv = members.joinToString(",")
                )
            )
            homeItems.add(
                HomeScreenItemEntity(
                    pageIndex = 0,
                    row = row,
                    column = column,
                    spanX = 1,
                    spanY = 1,
                    isDockItem = false,
                    itemType = HomeItemType.FOLDER.name,
                    customLabel = name,
                    folderId = folderId,
                    isPinnedByUser = true
                )
            )
        }

        dockPackages.forEachIndexed { index, packageName ->
            val app = appByPackage[packageName] ?: return@forEachIndexed
            homeItems.add(
                HomeScreenItemEntity(
                    pageIndex = 0,
                    row = rows - 1,
                    column = index,
                    spanX = 1,
                    spanY = 1,
                    isDockItem = true,
                    dockIndex = index,
                    itemType = HomeItemType.APP_SHORTCUT.name,
                    packageName = packageName,
                    customLabel = app.appName,
                    isPinnedByUser = true
                )
            )
        }

        dao.clearAllHomeItems()
        dao.clearAllFolders()
        dao.insertHomeItems(homeItems)
        dao.upsertSettings(
            settings.copy(
                homePageCount = 3,
                arrangementMode = ArrangementMode.PIXEL_DEFAULT.name
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

        // Find next open cell on workspace (respecting multi-span 2x2 folders & widgets)
        val occupied = mutableSetOf<Triple<Int, Int, Int>>()
        for (it in items.filter { !it.isDockItem }) {
            for (r in it.row until (it.row + it.spanY.coerceAtLeast(1))) {
                for (c in it.column until (it.column + it.spanX.coerceAtLeast(1))) {
                    occupied.add(Triple(it.pageIndex, r, c))
                }
            }
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
                spanX = 1,
                spanY = 1,
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
        toDock: Boolean = false,
        fallbackPackageName: String? = null
    ) = withContext(Dispatchers.IO) {
        val settings = dao.getSettingsSnapshot() ?: LauncherSettingsEntity()
        val cols = settings.homeGridColumns.coerceIn(3, 6)
        val rows = settings.homeGridRows.coerceIn(4, 7)
        val items = dao.getHomeItemsSnapshot()
        val moving = items.firstOrNull { it.id == itemId } ?: run {
            val pkg = fallbackPackageName ?: return@withContext
            val app = dao.getAppByPackage(pkg)
            val newId = dao.insertHomeItem(
                HomeScreenItemEntity(
                    pageIndex = targetPage,
                    row = targetRow.coerceAtLeast(0),
                    column = targetColumn.coerceAtLeast(0),
                    spanX = 1,
                    spanY = 1,
                    isDockItem = false,
                    itemType = HomeItemType.APP_SHORTCUT.name,
                    packageName = pkg,
                    customLabel = app?.appName ?: pkg,
                    isPinnedByUser = true
                )
            )
            dao.getHomeItemsSnapshot().firstOrNull { it.id == newId } ?: return@withContext
        }

        if (toDock) {
            // Only 1x1 app shortcuts can move into the dock
            if (moving.itemType == HomeItemType.FOLDER.name) return@withContext
            val clampedDockSlot = targetColumn.coerceIn(0, settings.dockIconCount - 1)
            val occupant = items.firstOrNull {
                it.id != moving.id && it.isDockItem && it.dockIndex == clampedDockSlot
            }
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
                    pageIndex = 0,
                    row = rows - 1,
                    column = clampedDockSlot,
                    spanX = 1,
                    spanY = 1,
                    isDockItem = true,
                    dockIndex = clampedDockSlot,
                    isPinnedByUser = true
                )
            )
            return@withContext
        }

        val startRow = if (targetPage == 0 && settings.showAtAGlanceWidget) 1 else 0
        val effSpanX = moving.spanX.coerceIn(1, cols)
        val effSpanY = moving.spanY.coerceIn(1, (rows - startRow).coerceAtLeast(1))
        val clampedCol = targetColumn.coerceIn(0, (cols - effSpanX).coerceAtLeast(0))
        val clampedRow = targetRow.coerceIn(startRow, (rows - effSpanY).coerceAtLeast(startRow))

        val targetCells = mutableSetOf<Pair<Int, Int>>()
        for (r in clampedRow until (clampedRow + effSpanY)) {
            for (c in clampedCol until (clampedCol + effSpanX)) {
                targetCells.add(r to c)
            }
        }

        val otherPageItems = items.filter { it.id != moving.id && !it.isDockItem && it.pageIndex == targetPage }
        val overlappingItems = otherPageItems.filter { item ->
            var overlaps = false
            for (r in item.row until (item.row + item.spanY.coerceAtLeast(1))) {
                for (c in item.column until (item.column + item.spanX.coerceAtLeast(1))) {
                    if ((r to c) in targetCells) {
                        overlaps = true
                    }
                }
            }
            overlaps
        }

        // Build set of occupied cells on targetPage after placing `moving` at (clampedRow, clampedCol)
        val occupiedCells = mutableSetOf<Pair<Int, Int>>()
        occupiedCells.addAll(targetCells)
        for (nonOverlapping in otherPageItems.filter { it !in overlappingItems }) {
            for (r in nonOverlapping.row until (nonOverlapping.row + nonOverlapping.spanY.coerceAtLeast(1))) {
                for (c in nonOverlapping.column until (nonOverlapping.column + nonOverlapping.spanX.coerceAtLeast(1))) {
                    occupiedCells.add(r to c)
                }
            }
        }

        // Relocate any overlapping items to the moving item's old position (if on same page and fits) or nearest free block
        for ((idx, displaced) in overlappingItems.withIndex()) {
            if (idx == 0 && moving.isDockItem && displaced.spanX == 1 && displaced.spanY == 1) {
                dao.updateHomeItem(
                    displaced.copy(
                        pageIndex = 0,
                        row = rows - 1,
                        column = moving.dockIndex,
                        isDockItem = true,
                        dockIndex = moving.dockIndex
                    )
                )
                continue
            }

            val dSpanX = displaced.spanX.coerceIn(1, cols)
            val dSpanY = displaced.spanY.coerceIn(1, (rows - startRow).coerceAtLeast(1))

            // Candidate positions: first try the moving item's old (row, col) if on the same page, then scan the page
            val candidateOrigins = mutableListOf<Pair<Int, Int>>()
            if (!moving.isDockItem && moving.pageIndex == targetPage) {
                val oldR = moving.row.coerceIn(startRow, (rows - dSpanY).coerceAtLeast(startRow))
                val oldC = moving.column.coerceIn(0, (cols - dSpanX).coerceAtLeast(0))
                candidateOrigins.add(oldR to oldC)
            }
            for (r in startRow..(rows - dSpanY).coerceAtLeast(startRow)) {
                for (c in 0..(cols - dSpanX).coerceAtLeast(0)) {
                    candidateOrigins.add(r to c)
                }
            }

            var placedOrigin: Pair<Int, Int>? = null
            for ((candR, candC) in candidateOrigins) {
                var fits = true
                for (rr in candR until (candR + dSpanY)) {
                    for (cc in candC until (candC + dSpanX)) {
                        if ((rr to cc) in occupiedCells) {
                            fits = false
                            break
                        }
                    }
                    if (!fits) break
                }
                if (fits) {
                    placedOrigin = candR to candC
                    for (rr in candR until (candR + dSpanY)) {
                        for (cc in candC until (candC + dSpanX)) {
                            occupiedCells.add(rr to cc)
                        }
                    }
                    break
                }
            }

            val finalR = placedOrigin?.first ?: displaced.row
            val finalC = placedOrigin?.second ?: displaced.column
            dao.updateHomeItem(
                displaced.copy(
                    pageIndex = targetPage,
                    row = finalR,
                    column = finalC,
                    isDockItem = false,
                    dockIndex = 0
                )
            )
            if (displaced.folderId != null) {
                val folder = dao.getFoldersSnapshot().firstOrNull { it.id == displaced.folderId }
                if (folder != null) {
                    dao.insertFolder(folder.copy(pageIndex = targetPage, row = finalR, column = finalC))
                }
            }
        }

        dao.updateHomeItem(
            moving.copy(
                pageIndex = targetPage,
                row = clampedRow,
                column = clampedCol,
                spanX = effSpanX,
                spanY = effSpanY,
                isDockItem = false,
                dockIndex = 0,
                isPinnedByUser = true
            )
        )
        if (moving.folderId != null) {
            val folder = dao.getFoldersSnapshot().firstOrNull { it.id == moving.folderId }
            if (folder != null) {
                dao.insertFolder(folder.copy(pageIndex = targetPage, row = clampedRow, column = clampedCol))
            }
        }
    }

    /**
     * Merges a dragged app shortcut onto another app shortcut (creating a new 2x2 folder)
     * or adds a dragged app shortcut into an existing folder.
     */
    suspend fun mergeHomeItemsIntoFolder(
        draggedItemId: Long,
        targetItemId: Long,
        draggedFallbackPkg: String? = null
    ): String? = withContext(Dispatchers.IO) {
        if (draggedItemId == targetItemId && draggedItemId > 0L) return@withContext null
        val settings = dao.getSettingsSnapshot() ?: LauncherSettingsEntity()
        val cols = settings.homeGridColumns.coerceIn(3, 6)
        val rows = settings.homeGridRows.coerceIn(4, 7)
        val items = dao.getHomeItemsSnapshot()
        val folders = dao.getFoldersSnapshot()

        val dragged = items.firstOrNull { it.id == draggedItemId }
        val target = items.firstOrNull { it.id == targetItemId } ?: return@withContext null

        val draggedPkg = dragged?.packageName ?: draggedFallbackPkg ?: return@withContext null

        // Case 1: Dropping an App Shortcut onto an existing Folder
        if (target.itemType == HomeItemType.FOLDER.name && target.folderId != null) {
            val existingFolder = folders.firstOrNull { it.id == target.folderId } ?: return@withContext null
            val currentMembers = existingFolder.memberPackagesCsv
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            val updatedMembers = (currentMembers + draggedPkg).distinct()
            val updatedColorHex = if (updatedMembers.size >= 8 && !existingFolder.colorHex.contains(":")) {
                "${existingFolder.colorHex}:DENSE"
            } else {
                existingFolder.colorHex
            }
            dao.insertFolder(
                existingFolder.copy(
                    colorHex = updatedColorHex,
                    memberPackagesCsv = updatedMembers.joinToString(",")
                )
            )
            if (dragged != null) {
                dao.deleteHomeItemById(dragged.id)
            }
            return@withContext existingFolder.folderName
        }

        // Case 2: Dropping an App Shortcut onto another App Shortcut -> Create new 2x2 Folder
        if (target.itemType == HomeItemType.APP_SHORTCUT.name && !target.isDockItem) {
            val targetPkg = target.packageName ?: return@withContext null
            if (targetPkg == draggedPkg) return@withContext null

            val targetApp = dao.getAppByPackage(targetPkg)
            val catEntity = targetApp?.let { app ->
                dao.getCategoriesSnapshot().firstOrNull { it.id == app.primaryCategoryId }
            }
            val newFolderName = catEntity?.name?.takeIf {
                it != DefaultCategory.UNCATEGORIZED.displayName && it != DefaultCategory.ALL_APPS.displayName
            } ?: "Smart Folder"

            val startRow = if (target.pageIndex == 0 && settings.showAtAGlanceWidget) 1 else 0
            val folderRow = target.row.coerceIn(startRow, (rows - 2).coerceAtLeast(startRow))
            val folderCol = target.column.coerceIn(0, (cols - 2).coerceAtLeast(0))

            if (dragged != null) {
                dao.deleteHomeItemById(dragged.id)
            }
            dao.deleteHomeItemById(target.id)

            val folderId = dao.insertFolder(
                FolderEntity(
                    folderName = newFolderName,
                    pageIndex = target.pageIndex,
                    row = folderRow,
                    column = folderCol,
                    colorHex = "#3B82F6:BENTO",
                    memberPackagesCsv = listOf(targetPkg, draggedPkg).distinct().joinToString(",")
                )
            )
            val insertedItemId = dao.insertHomeItem(
                HomeScreenItemEntity(
                    pageIndex = target.pageIndex,
                    row = folderRow,
                    column = folderCol,
                    spanX = 2,
                    spanY = 2,
                    isDockItem = false,
                    itemType = HomeItemType.FOLDER.name,
                    customLabel = newFolderName,
                    folderId = folderId,
                    isPinnedByUser = true
                )
            )
            // Ensure any items overlapped by the new 2x2 folder footprint are shifted to free cells
            moveHomeItemToCell(insertedItemId, target.pageIndex, folderRow, folderCol, toDock = false)
            return@withContext newFolderName
        }

        null
    }

    suspend fun toggleFolderDisplayStyle(folderId: Long): String? = withContext(Dispatchers.IO) {
        val existing = dao.getFoldersSnapshot().firstOrNull { it.id == folderId } ?: return@withContext null
        val domain = existing.toDomainModel()
        val nextSuffix = if (domain.displayStyle == com.example.domain.model.FolderDisplayStyle.BENTO_2X2) {
            "DENSE"
        } else {
            "BENTO"
        }
        val updatedColorHex = "${domain.cleanColorHex}:$nextSuffix"
        dao.insertFolder(existing.copy(colorHex = updatedColorHex))
        if (nextSuffix == "DENSE") "3×3 Dense" else "2×2 Bento"
    }

    /**
     * Resizes a folder on the Home Screen between 1x1 Compact and 2x2 Expanded (Bento or Dense 3x3),
     * automatically resolving any grid cell collisions when expanding to 2x2.
     */
    suspend fun resizeAndStyleFolder(
        folderId: Long,
        spanX: Int,
        spanY: Int,
        displayStyle: com.example.domain.model.FolderDisplayStyle
    ): String? = withContext(Dispatchers.IO) {
        val existingFolder = dao.getFoldersSnapshot().firstOrNull { it.id == folderId } ?: return@withContext null
        val domain = existingFolder.toDomainModel()
        val styleSuffix = if (displayStyle == com.example.domain.model.FolderDisplayStyle.DENSE_3X3) "DENSE" else "BENTO"
        val updatedColorHex = "${domain.cleanColorHex}:$styleSuffix"
        dao.insertFolder(existingFolder.copy(colorHex = updatedColorHex))

        val homeItem = dao.getHomeItemsSnapshot().firstOrNull { it.folderId == folderId }
        val clampedSpanX = spanX.coerceIn(1, 2)
        val clampedSpanY = spanY.coerceIn(1, 2)
        if (homeItem != null) {
            dao.updateHomeItem(
                homeItem.copy(
                    spanX = clampedSpanX,
                    spanY = clampedSpanY
                )
            )
            moveHomeItemToCell(
                itemId = homeItem.id,
                targetPage = homeItem.pageIndex,
                targetRow = homeItem.row,
                targetColumn = homeItem.column,
                toDock = false
            )
        }
        when {
            clampedSpanX == 1 && clampedSpanY == 1 -> "1×1 Compact Folder"
            displayStyle == com.example.domain.model.FolderDisplayStyle.DENSE_3X3 -> "2×2 Dense 3×3 Folder"
            else -> "2×2 Bento Folder"
        }
    }

    /**
     * Removes an app from an opened folder and places it directly onto the Home Screen grid.
     */
    suspend fun extractAppFromFolderToHome(
        folderId: Long,
        packageName: String,
        targetPage: Int? = null
    ): String? = withContext(Dispatchers.IO) {
        val existingFolder = dao.getFoldersSnapshot().firstOrNull { it.id == folderId } ?: return@withContext null
        val currentMembers = existingFolder.memberPackagesCsv
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != packageName }

        val folderHomeItem = dao.getHomeItemsSnapshot().firstOrNull { it.folderId == folderId }
        val destinationPage = targetPage ?: folderHomeItem?.pageIndex ?: existingFolder.pageIndex

        if (currentMembers.isEmpty()) {
            if (folderHomeItem != null) {
                dao.deleteHomeItemById(folderHomeItem.id)
            }
            dao.deleteFolderById(folderId)
        } else {
            dao.insertFolder(
                existingFolder.copy(
                    memberPackagesCsv = currentMembers.joinToString(",")
                )
            )
        }

        // Place extracted app on the Home Screen page near the folder
        val settings = dao.getSettingsSnapshot() ?: LauncherSettingsEntity()
        val cols = settings.homeGridColumns.coerceIn(3, 6)
        val rows = settings.homeGridRows.coerceIn(4, 7)
        val startRow = if (destinationPage == 0 && settings.showAtAGlanceWidget) 1 else 0
        val app = dao.getAppByPackage(packageName)

        val newItemId = dao.insertHomeItem(
            HomeScreenItemEntity(
                pageIndex = destinationPage,
                row = (folderHomeItem?.row ?: startRow).coerceIn(startRow, rows - 1),
                column = (folderHomeItem?.column ?: 0).coerceIn(0, cols - 1),
                spanX = 1,
                spanY = 1,
                isDockItem = false,
                itemType = HomeItemType.APP_SHORTCUT.name,
                packageName = packageName,
                customLabel = app?.appName ?: packageName,
                isPinnedByUser = true
            )
        )
        moveHomeItemToCell(
            itemId = newItemId,
            targetPage = destinationPage,
            targetRow = (folderHomeItem?.row ?: startRow).coerceIn(startRow, rows - 1),
            targetColumn = ((folderHomeItem?.column ?: 0) + (folderHomeItem?.spanX ?: 1)).coerceIn(0, cols - 1),
            toDock = false
        )
        app?.appName ?: packageName
    }

    suspend fun createFolderOnHome(
        folderName: String,
        memberPackages: List<String>,
        colorHex: String = "#4285F4",
        pageIndex: Int = 0,
        spanX: Int = 2,
        spanY: Int = 2,
        preferredStyle: String? = null
    ) = withContext(Dispatchers.IO) {
        val settings = dao.getSettingsSnapshot() ?: LauncherSettingsEntity()
        val cols = settings.homeGridColumns.coerceIn(3, 6)
        val rows = settings.homeGridRows.coerceIn(4, 7)
        val items = dao.getHomeItemsSnapshot()
        val occupied = mutableSetOf<Triple<Int, Int, Int>>()
        for (it in items.filter { !it.isDockItem }) {
            for (r in it.row until (it.row + it.spanY.coerceAtLeast(1))) {
                for (c in it.column until (it.column + it.spanX.coerceAtLeast(1))) {
                    occupied.add(Triple(it.pageIndex, r, c))
                }
            }
        }

        var cell = Triple(pageIndex, 1, 1.coerceAtMost(cols - 2))
        outer@ for (p in pageIndex until maxOf(3, settings.homePageCount)) {
            val startRow = if (p == 0 && settings.showAtAGlanceWidget) 1 else 0
            for (r in startRow..(rows - 2).coerceAtLeast(startRow)) {
                for (c in 0..(cols - 2).coerceAtLeast(0)) {
                    val block = listOf(
                        Triple(p, r, c),
                        Triple(p, r, c + 1),
                        Triple(p, r + 1, c),
                        Triple(p, r + 1, c + 1)
                    )
                    if (block.none { it in occupied }) {
                        cell = Triple(p, r, c)
                        break@outer
                    }
                }
            }
        }

        val cleanColor = colorHex.substringBefore(":")
        val styleSuffix = preferredStyle ?: if (memberPackages.size >= 8) "DENSE" else "BENTO"
        val folderId = dao.insertFolder(
            FolderEntity(
                folderName = folderName.ifBlank { "Smart Folder" },
                pageIndex = cell.first,
                row = cell.second,
                column = cell.third,
                colorHex = "$cleanColor:$styleSuffix",
                memberPackagesCsv = memberPackages.distinct().joinToString(",")
            )
        )
        dao.insertHomeItem(
            HomeScreenItemEntity(
                pageIndex = cell.first,
                row = cell.second,
                column = cell.third,
                spanX = spanX.coerceIn(1, 2),
                spanY = spanY.coerceIn(1, 2),
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
        memberPackages: List<String>,
        newColorHex: String? = null
    ) = withContext(Dispatchers.IO) {
        val existing = dao.getFoldersSnapshot().firstOrNull { it.id == folderId } ?: return@withContext
        val cleanColor = newColorHex?.substringBefore(":") ?: existing.toDomainModel().cleanColorHex
        val currentSuffix = if (existing.colorHex.endsWith(":DENSE")) "DENSE" else "BENTO"
        val resolvedColor = if (newColorHex != null && (newColorHex.endsWith(":DENSE") || newColorHex.endsWith(":BENTO"))) {
            newColorHex
        } else {
            "$cleanColor:$currentSuffix"
        }
        dao.insertFolder(
            existing.copy(
                folderName = newName.ifBlank { existing.folderName },
                colorHex = resolvedColor,
                memberPackagesCsv = memberPackages.distinct().joinToString(",")
            )
        )
    }

    fun matchAppsForCustomIntent(prompt: String, installedApps: List<AppInfo>): List<AppInfo> {
        return aiClassifier.matchAppsForCustomIntent(prompt, installedApps)
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
