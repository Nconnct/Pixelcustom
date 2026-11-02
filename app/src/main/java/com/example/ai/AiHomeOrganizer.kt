package com.example.ai

import com.example.data.local.FolderEntity
import com.example.data.local.HomeScreenItemEntity
import com.example.data.local.LayoutSnapshotEntity
import com.example.domain.model.AppInfo
import com.example.domain.model.ArrangementMode
import com.example.domain.model.DefaultCategory
import com.example.domain.model.FolderModel
import com.example.domain.model.HomeItemType
import com.example.domain.model.HomeScreenItemModel
import com.example.domain.model.ProposedLayoutPreview
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local Intelligent Home Screen Auto-Arrangement Engine.
 *
 * Analyzes:
 * - Installed applications & AI categories
 * - User favorites & launch frequency
 * - Existing shortcuts, pinned apps, and active widgets
 * - Home screen grid dimensions & dock capacity
 *
 * Generates non-destructive layout previews for PIXEL_DEFAULT and AI_ORGANIZED modes
 * and serializes/deserializes layout snapshots for instant Undo & Restore.
 */
class AiHomeOrganizer {

    fun generateProposedLayout(
        mode: ArrangementMode,
        installedApps: List<AppInfo>,
        existingItems: List<HomeScreenItemModel>,
        existingFolders: List<FolderModel>,
        gridColumns: Int,
        gridRows: Int,
        dockCapacity: Int,
        usageStatsScores: Map<String, Long> = emptyMap()
    ): ProposedLayoutPreview {
        val cols = gridColumns.coerceIn(3, 6)
        val rows = gridRows.coerceIn(4, 7)
        val dockLimit = dockCapacity.coerceIn(3, 6)

        // Always preserve existing widgets and user-pinned items
        val preservedWidgets = existingItems.filter { it.itemType == HomeItemType.WIDGET }
        val pinnedShortcuts = existingItems.filter {
            it.itemType == HomeItemType.APP_SHORTCUT && it.isPinnedByUser && !it.isDockItem
        }

        // Rank apps by composite utility score: Favorites > LaunchCount > UsageStats > AI Confidence
        val rankedApps = installedApps.sortedWith(
            compareByDescending<AppInfo> { it.isFavorite }
                .thenByDescending { it.launchCount }
                .thenByDescending { usageStatsScores[it.packageName] ?: 0L }
                .thenByDescending { !it.isSystemApp }
                .thenBy { it.appName.lowercase() }
        )

        // Select Dock Apps (prioritize Dialer/Messaging/Browser/Camera/Favorites)
        val dockPackages = selectDockApps(rankedApps, dockLimit)
        val usedPackages = mutableSetOf<String>()
        usedPackages.addAll(dockPackages)
        pinnedShortcuts.mapNotNullTo(usedPackages) { it.packageName }

        val proposedItems = mutableListOf<HomeScreenItemModel>()
        val proposedFolders = mutableListOf<FolderModel>()

        // Add dock items
        dockPackages.forEachIndexed { index, pkg ->
            val app = installedApps.firstOrNull { it.packageName == pkg }
            proposedItems.add(
                HomeScreenItemModel(
                    pageIndex = 0,
                    row = rows - 1,
                    column = index,
                    isDockItem = true,
                    dockIndex = index,
                    itemType = HomeItemType.APP_SHORTCUT,
                    packageName = pkg,
                    customLabel = app?.appName
                )
            )
        }

        // Preserve widgets and pinned items
        proposedItems.addAll(preservedWidgets)
        proposedItems.addAll(pinnedShortcuts)

        val occupiedCells = mutableSetOf<Triple<Int, Int, Int>>() // (page, row, col)
        // Row 0 on Page 0 is reserved for the Pixel At-a-Glance widget header
        for (c in 0 until cols) {
            occupiedCells.add(Triple(0, 0, c))
        }
        for (w in preservedWidgets + pinnedShortcuts) {
            for (r in w.row until (w.row + w.spanY)) {
                for (c in w.column until (w.column + w.spanX)) {
                    occupiedCells.add(Triple(w.pageIndex, r, c))
                }
            }
        }

        fun nextFreeCellOnPage(targetPage: Int): Triple<Int, Int, Int>? {
            val startRow = if (targetPage == 0) 1 else 0
            for (r in startRow until rows) {
                for (c in 0 until cols) {
                    val cell = Triple(targetPage, r, c)
                    if (cell !in occupiedCells) {
                        occupiedCells.add(cell)
                        return cell
                    }
                }
            }
            return null
        }

        fun nextFreeCellAcrossPages(startPage: Int, maxPageExclusive: Int = 3): Triple<Int, Int, Int>? {
            for (p in startPage until maxPageExclusive) {
                val cell = nextFreeCellOnPage(p)
                if (cell != null) return cell
            }
            return null
        }

        return when (mode) {
            ArrangementMode.PIXEL_DEFAULT -> {
                // 3-page standard layout with top-ranked apps across Pages 1, 2, and 3 + dock
                val maxPerGrid = cols * (rows - 1)
                val remaining = rankedApps.filter { it.packageName !in usedPackages }.take(maxPerGrid * 3)
                for (app in remaining) {
                    val cell = nextFreeCellAcrossPages(startPage = 0, maxPageExclusive = 3) ?: break
                    val (page, r, c) = cell
                    usedPackages.add(app.packageName)
                    proposedItems.add(
                        HomeScreenItemModel(
                            pageIndex = page,
                            row = r,
                            column = c,
                            isDockItem = false,
                            itemType = HomeItemType.APP_SHORTCUT,
                            packageName = app.packageName,
                            customLabel = app.appName
                        )
                    )
                }

                ProposedLayoutPreview(
                    mode = ArrangementMode.PIXEL_DEFAULT,
                    pagesCount = 3,
                    proposedItems = proposedItems,
                    proposedFolders = emptyList(),
                    dockPackages = dockPackages,
                    preservedWidgetsCount = preservedWidgets.size,
                    summaryPoints = listOf(
                        "3-page horizontal Home Screen with ${dockPackages.size} essential Dock apps",
                        "Distributed ${remaining.size} apps cleanly across Pages 1, 2, and 3",
                        "Preserved ${preservedWidgets.size} active widgets and ${pinnedShortcuts.size} pinned shortcuts"
                    )
                )
            }

            ArrangementMode.AI_ORGANIZED, ArrangementMode.CUSTOM_LAYOUT -> {
                // Helper to find a free 2x2 block for expanded folders on Page 0 (preferring cols 1 and 3 on a 5-col grid so col 0 stays free for 1x1 apps)
                fun nextFolderSlotOnPage0(): Triple<Int, Int, Int>? {
                    val startRow = 1
                    val preferredCols = if (cols >= 5) listOf(1, 3, 0, 2) else listOf(0, 2, 1)
                    for (r in startRow..(rows - 2)) {
                        for (c in preferredCols) {
                            if (c + 1 >= cols) continue
                            val blockCells = listOf(
                                Triple(0, r, c),
                                Triple(0, r, c + 1),
                                Triple(0, r + 1, c),
                                Triple(0, r + 1, c + 1)
                            )
                            if (blockCells.none { it in occupiedCells }) {
                                occupiedCells.addAll(blockCells)
                                return Triple(0, r, c)
                            }
                        }
                    }
                    // Fallback on small grids with widgets: place at next free cell on Page 0
                    return nextFreeCellOnPage(targetPage = 0)
                }

                // Page 1 (pageIndex = 0): Expanded 2x2 Smart Folders (supporting both 2x2 Bento and 3x3 Dense styles)
                val folderClusters = listOf(
                    Triple(
                        "Productivity",
                        "#3B82F6:BENTO",
                        setOf(DefaultCategory.PRODUCTIVITY.id, DefaultCategory.EDUCATION.id, DefaultCategory.TECH_DEV.id)
                    ),
                    Triple(
                        "Banking",
                        "#2563EB:DENSE",
                        setOf(DefaultCategory.BANKING.id, DefaultCategory.UPI_PAYMENTS.id)
                    ),
                    Triple(
                        "Social",
                        "#10B981:BENTO",
                        setOf(DefaultCategory.SOCIAL.id, DefaultCategory.MESSAGING.id)
                    ),
                    Triple(
                        "Media",
                        "#EF4444:DENSE",
                        setOf(DefaultCategory.VIDEO_STREAMING.id, DefaultCategory.MUSIC_AUDIO.id, DefaultCategory.PHOTOGRAPHY.id)
                    ),
                    Triple(
                        "Shopping",
                        "#F59E0B:BENTO",
                        setOf(DefaultCategory.SHOPPING.id, DefaultCategory.GAMES.id)
                    ),
                    Triple(
                        "Utilities",
                        "#8B5CF6:BENTO",
                        setOf(
                            DefaultCategory.UTILITIES.id,
                            DefaultCategory.SYSTEM_APPS.id,
                            DefaultCategory.BROWSERS.id,
                            DefaultCategory.INTERNET.id,
                            DefaultCategory.UNCATEGORIZED.id
                        )
                    )
                )

                var nextFolderTempId = 100L
                for ((folderTitle, colorHexWithStyle, catIds) in folderClusters) {
                    val matchingApps = rankedApps.filter { app ->
                        app.packageName !in usedPackages &&
                            (app.categoryId in catIds || app.allCategoryIds.any { it in catIds })
                    }
                    if (matchingApps.isNotEmpty()) {
                        val block = nextFolderSlotOnPage0() ?: break
                        val (page, r, c) = block
                        val isDense = colorHexWithStyle.endsWith(":DENSE")
                        val folderMembers = matchingApps.take(if (isDense) 12 else 7).map { it.packageName }
                        usedPackages.addAll(folderMembers)
                        val folderId = nextFolderTempId++
                        proposedFolders.add(
                            FolderModel(
                                id = folderId,
                                folderName = folderTitle,
                                pageIndex = page,
                                row = r,
                                column = c,
                                colorHex = colorHexWithStyle,
                                memberPackages = folderMembers
                            )
                        )
                        proposedItems.add(
                            HomeScreenItemModel(
                                pageIndex = page,
                                row = r,
                                column = c,
                                spanX = 2,
                                spanY = 2,
                                isDockItem = false,
                                itemType = HomeItemType.FOLDER,
                                customLabel = folderTitle,
                                folderId = folderId
                            )
                        )
                    }
                }

                // Fill column 0 (left column) and any remaining open 1x1 cells on Page 0 with top individual apps
                val unplacedAfterFolders = rankedApps.filter { it.packageName !in usedPackages }
                val nonDockEssentialApps = rankedApps.filter { it.packageName !in dockPackages }
                val page0AppCandidates = (unplacedAfterFolders + nonDockEssentialApps).distinctBy { it.packageName }
                for (app in page0AppCandidates) {
                    val cell = nextFreeCellOnPage(targetPage = 0) ?: break
                    val (page, r, c) = cell
                    usedPackages.add(app.packageName)
                    proposedItems.add(
                        HomeScreenItemModel(
                            pageIndex = page,
                            row = r,
                            column = c,
                            spanX = 1,
                            spanY = 1,
                            isDockItem = false,
                            itemType = HomeItemType.APP_SHORTCUT,
                            packageName = app.packageName,
                            customLabel = app.appName
                        )
                    )
                }

                // Pages 2 & 3 (pageIndex = 1 and pageIndex = 2): Extra & frequent individual apps
                val unplacedApps = rankedApps.filter { it.packageName !in usedPackages }
                val nonDockApps = rankedApps.filter { it.packageName !in dockPackages }
                val extraAppsForPages2And3 = (unplacedApps + nonDockApps)
                    .distinctBy { it.packageName }
                    .take(cols * 4)

                val halfCount = (extraAppsForPages2And3.size + 1) / 2
                val page2Apps = extraAppsForPages2And3.take(halfCount)
                val page3Apps = extraAppsForPages2And3.drop(halfCount)

                for (app in page2Apps) {
                    val cell = nextFreeCellOnPage(targetPage = 1) ?: break
                    val (page, r, c) = cell
                    proposedItems.add(
                        HomeScreenItemModel(
                            pageIndex = page,
                            row = r,
                            column = c,
                            spanX = 1,
                            spanY = 1,
                            isDockItem = false,
                            itemType = HomeItemType.APP_SHORTCUT,
                            packageName = app.packageName,
                            customLabel = app.appName
                        )
                    )
                }

                for (app in page3Apps) {
                    val cell = nextFreeCellOnPage(targetPage = 2) ?: break
                    val (page, r, c) = cell
                    proposedItems.add(
                        HomeScreenItemModel(
                            pageIndex = page,
                            row = r,
                            column = c,
                            spanX = 1,
                            spanY = 1,
                            isDockItem = false,
                            itemType = HomeItemType.APP_SHORTCUT,
                            packageName = app.packageName,
                            customLabel = app.appName
                        )
                    )
                }

                ProposedLayoutPreview(
                    mode = ArrangementMode.AI_ORGANIZED,
                    pagesCount = 3,
                    proposedItems = proposedItems,
                    proposedFolders = proposedFolders,
                    dockPackages = dockPackages,
                    preservedWidgetsCount = preservedWidgets.size,
                    summaryPoints = listOf(
                        "Page 1: ${proposedFolders.size} expanded 2×2 Smart Folders (2×2 Bento & 3×3 Dense) + left-column apps",
                        "Pages 2 & 3: ${page2Apps.size + page3Apps.size} extra apps arranged across horizontal pages",
                        "Pinned ${dockPackages.size} essential communication & favorite apps to the Frosted Dock",
                        "Hold & drag any app or folder to move, swap, or drop onto another item to merge"
                    )
                )
            }
        }
    }

    private fun selectDockApps(rankedApps: List<AppInfo>, dockLimit: Int): List<String> {
        val selected = mutableListOf<String>()

        // 1. User favorites first
        for (app in rankedApps.filter { it.isFavorite }) {
            if (selected.size < dockLimit && app.packageName !in selected) {
                selected.add(app.packageName)
            }
        }

        // 2. Essential communication/browser categories
        val preferredCategories = listOf(
            DefaultCategory.MESSAGING.id,
            DefaultCategory.BROWSERS.id,
            DefaultCategory.PHOTOGRAPHY.id,
            DefaultCategory.PRODUCTIVITY.id
        )
        for (catId in preferredCategories) {
            if (selected.size >= dockLimit) break
            val candidate = rankedApps.firstOrNull { it.categoryId == catId && it.packageName !in selected }
            if (candidate != null) {
                selected.add(candidate.packageName)
            }
        }

        // 3. Fill remaining dock slots with top-ranked apps
        for (app in rankedApps) {
            if (selected.size >= dockLimit) break
            if (app.packageName !in selected) {
                selected.add(app.packageName)
            }
        }
        return selected
    }

    fun serializeLayoutSnapshot(
        snapshotName: String,
        mode: String,
        items: List<HomeScreenItemEntity>,
        folders: List<FolderEntity>
    ): LayoutSnapshotEntity {
        val itemsSerialized = try {
            val itemsArr = JSONArray()
            for (item in items) {
                val obj = JSONObject()
                obj.put("pageIndex", item.pageIndex)
                obj.put("row", item.row)
                obj.put("column", item.column)
                obj.put("spanX", item.spanX)
                obj.put("spanY", item.spanY)
                obj.put("isDockItem", item.isDockItem)
                obj.put("dockIndex", item.dockIndex)
                obj.put("itemType", item.itemType)
                obj.put("packageName", item.packageName ?: "")
                obj.put("customLabel", item.customLabel ?: "")
                obj.put("folderId", item.folderId ?: -1L)
                obj.put("appWidgetId", item.appWidgetId)
                obj.put("widgetProviderName", item.widgetProviderName ?: "")
                obj.put("isPinnedByUser", item.isPinnedByUser)
                itemsArr.put(obj)
            }
            itemsArr.toString()
        } catch (_: Throwable) {
            items.joinToString("\n") { item ->
                listOf(
                    item.pageIndex,
                    item.row,
                    item.column,
                    item.spanX,
                    item.spanY,
                    item.isDockItem,
                    item.dockIndex,
                    item.itemType,
                    item.packageName ?: "",
                    item.customLabel ?: "",
                    item.folderId ?: -1L,
                    item.appWidgetId,
                    item.widgetProviderName ?: "",
                    item.isPinnedByUser
                ).joinToString("|")
            }
        }

        val foldersSerialized = try {
            val foldersArr = JSONArray()
            for (folder in folders) {
                val obj = JSONObject()
                obj.put("id", folder.id)
                obj.put("folderName", folder.folderName)
                obj.put("pageIndex", folder.pageIndex)
                obj.put("row", folder.row)
                obj.put("column", folder.column)
                obj.put("colorHex", folder.colorHex)
                obj.put("memberPackagesCsv", folder.memberPackagesCsv)
                foldersArr.put(obj)
            }
            foldersArr.toString()
        } catch (_: Throwable) {
            folders.joinToString("\n") { folder ->
                listOf(
                    folder.id,
                    folder.folderName,
                    folder.pageIndex,
                    folder.row,
                    folder.column,
                    folder.colorHex,
                    folder.memberPackagesCsv
                ).joinToString("|")
            }
        }

        return LayoutSnapshotEntity(
            snapshotName = snapshotName,
            arrangementMode = mode,
            itemsJson = itemsSerialized,
            foldersJson = foldersSerialized
        )
    }

    fun deserializeLayoutSnapshot(
        snapshot: LayoutSnapshotEntity
    ): Pair<List<HomeScreenItemEntity>, List<FolderEntity>> {
        val folders = mutableListOf<FolderEntity>()
        try {
            val foldersArr = JSONArray(snapshot.foldersJson)
            for (i in 0 until foldersArr.length()) {
                val obj = foldersArr.getJSONObject(i)
                folders.add(
                    FolderEntity(
                        id = obj.optLong("id", (i + 1).toLong()),
                        folderName = obj.getString("folderName"),
                        pageIndex = obj.optInt("pageIndex", 0),
                        row = obj.optInt("row", 0),
                        column = obj.optInt("column", 0),
                        colorHex = obj.optString("colorHex", "#4285F4"),
                        memberPackagesCsv = obj.optString("memberPackagesCsv", "")
                    )
                )
            }
        } catch (_: Throwable) {
            snapshot.foldersJson.lineSequence().filter { it.isNotBlank() }.forEachIndexed { idx, line ->
                val parts = line.split("|")
                if (parts.size >= 7) {
                    folders.add(
                        FolderEntity(
                            id = parts[0].toLongOrNull() ?: (idx + 1).toLong(),
                            folderName = parts[1],
                            pageIndex = parts[2].toIntOrNull() ?: 0,
                            row = parts[3].toIntOrNull() ?: 0,
                            column = parts[4].toIntOrNull() ?: 0,
                            colorHex = parts[5].ifBlank { "#4285F4" },
                            memberPackagesCsv = parts[6]
                        )
                    )
                }
            }
        }

        val items = mutableListOf<HomeScreenItemEntity>()
        try {
            val itemsArr = JSONArray(snapshot.itemsJson)
            for (i in 0 until itemsArr.length()) {
                val obj = itemsArr.getJSONObject(i)
                val fId = obj.optLong("folderId", -1L)
                val pkg = obj.optString("packageName", "").takeIf { it.isNotEmpty() }
                val label = obj.optString("customLabel", "").takeIf { it.isNotEmpty() }
                val widgetProv = obj.optString("widgetProviderName", "").takeIf { it.isNotEmpty() }
                items.add(
                    HomeScreenItemEntity(
                        pageIndex = obj.optInt("pageIndex", 0),
                        row = obj.optInt("row", 0),
                        column = obj.optInt("column", 0),
                        spanX = obj.optInt("spanX", 1),
                        spanY = obj.optInt("spanY", 1),
                        isDockItem = obj.optBoolean("isDockItem", false),
                        dockIndex = obj.optInt("dockIndex", 0),
                        itemType = obj.optString("itemType", "APP_SHORTCUT"),
                        packageName = pkg,
                        customLabel = label,
                        folderId = if (fId > 0) fId else null,
                        appWidgetId = obj.optInt("appWidgetId", -1),
                        widgetProviderName = widgetProv,
                        isPinnedByUser = obj.optBoolean("isPinnedByUser", false)
                    )
                )
            }
        } catch (_: Throwable) {
            snapshot.itemsJson.lineSequence().filter { it.isNotBlank() }.forEach { line ->
                val parts = line.split("|")
                if (parts.size >= 14) {
                    val fId = parts[10].toLongOrNull() ?: -1L
                    items.add(
                        HomeScreenItemEntity(
                            pageIndex = parts[0].toIntOrNull() ?: 0,
                            row = parts[1].toIntOrNull() ?: 0,
                            column = parts[2].toIntOrNull() ?: 0,
                            spanX = parts[3].toIntOrNull() ?: 1,
                            spanY = parts[4].toIntOrNull() ?: 1,
                            isDockItem = parts[5].toBooleanStrictOrNull() ?: false,
                            dockIndex = parts[6].toIntOrNull() ?: 0,
                            itemType = parts[7].ifBlank { "APP_SHORTCUT" },
                            packageName = parts[8].takeIf { it.isNotEmpty() },
                            customLabel = parts[9].takeIf { it.isNotEmpty() },
                            folderId = if (fId > 0) fId else null,
                            appWidgetId = parts[11].toIntOrNull() ?: -1,
                            widgetProviderName = parts[12].takeIf { it.isNotEmpty() },
                            isPinnedByUser = parts[13].toBooleanStrictOrNull() ?: false
                        )
                    )
                }
            }
        }
        return items to folders
    }
}
