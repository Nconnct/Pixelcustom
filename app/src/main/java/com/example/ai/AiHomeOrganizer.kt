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

        fun nextFreeCellOnSinglePage(): Triple<Int, Int, Int>? {
            // Row 0 is reserved for the clean Top Date & Time Header; rows 1..(rows-1) hold ML Folders & essentials
            for (r in 1 until rows) {
                for (c in 0 until cols) {
                    val cell = Triple(0, r, c)
                    if (cell !in occupiedCells) {
                        occupiedCells.add(cell)
                        return cell
                    }
                }
            }
            return null
        }

        return when (mode) {
            ArrangementMode.PIXEL_DEFAULT -> {
                // Clean single page with top essentials + dock
                val remaining = rankedApps.filter { it.packageName !in usedPackages }.take(cols * 2)
                for (app in remaining) {
                    val cell = nextFreeCellOnSinglePage() ?: break
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
                    pagesCount = 1,
                    proposedItems = proposedItems,
                    proposedFolders = emptyList(),
                    dockPackages = dockPackages,
                    preservedWidgetsCount = preservedWidgets.size,
                    summaryPoints = listOf(
                        "Clean single-page Home Screen with ${dockPackages.size} essential Dock apps",
                        "Placed ${remaining.size} top-ranked apps neatly on Page 1",
                        "Preserved ${preservedWidgets.size} active widgets and ${pinnedShortcuts.size} pinned shortcuts"
                    )
                )
            }

            ArrangementMode.AI_ORGANIZED, ArrangementMode.CUSTOM_LAYOUT -> {
                // Group apps into ML Smart Folders (Productivity, Social, Banking, Media, Utilities, Shopping, Games)
                val folderClusters = listOf(
                    Triple(
                        "Productivity",
                        "#3B82F6",
                        setOf(DefaultCategory.PRODUCTIVITY.id, DefaultCategory.EDUCATION.id, DefaultCategory.TECH_DEV.id)
                    ),
                    Triple(
                        "Social",
                        "#10B981",
                        setOf(DefaultCategory.SOCIAL.id, DefaultCategory.MESSAGING.id)
                    ),
                    Triple(
                        "Banking",
                        "#2563EB",
                        setOf(DefaultCategory.BANKING.id, DefaultCategory.UPI_PAYMENTS.id)
                    ),
                    Triple(
                        "Media",
                        "#EF4444",
                        setOf(DefaultCategory.VIDEO_STREAMING.id, DefaultCategory.MUSIC_AUDIO.id, DefaultCategory.PHOTOGRAPHY.id)
                    ),
                    Triple(
                        "Shopping",
                        "#F59E0B",
                        setOf(DefaultCategory.SHOPPING.id)
                    ),
                    Triple(
                        "Games",
                        "#8B5CF6",
                        setOf(DefaultCategory.GAMES.id)
                    ),
                    Triple(
                        "Utilities",
                        "#F97316",
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
                for ((folderTitle, colorHex, catIds) in folderClusters) {
                    val matchingApps = rankedApps.filter { app ->
                        app.packageName !in usedPackages &&
                            (app.categoryId in catIds || app.allCategoryIds.any { it in catIds })
                    }
                    if (matchingApps.isNotEmpty()) {
                        val cell = nextFreeCellOnSinglePage() ?: break
                        val (page, r, c) = cell
                        val folderMembers = matchingApps.take(12).map { it.packageName }
                        usedPackages.addAll(folderMembers)
                        val folderId = nextFolderTempId++
                        proposedFolders.add(
                            FolderModel(
                                id = folderId,
                                folderName = folderTitle,
                                pageIndex = page,
                                row = r,
                                column = c,
                                colorHex = colorHex,
                                memberPackages = folderMembers
                            )
                        )
                        proposedItems.add(
                            HomeScreenItemModel(
                                pageIndex = page,
                                row = r,
                                column = c,
                                isDockItem = false,
                                itemType = HomeItemType.FOLDER,
                                customLabel = folderTitle,
                                folderId = folderId
                            )
                        )
                    }
                }

                // Place at most 1 clean row of top remaining favorite/essential shortcuts on Page 1
                val standaloneApps = rankedApps
                    .filter { it.packageName !in usedPackages }
                    .take(cols)

                for (app in standaloneApps) {
                    val cell = nextFreeCellOnSinglePage() ?: break
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
                    mode = ArrangementMode.AI_ORGANIZED,
                    pagesCount = 1,
                    proposedItems = proposedItems,
                    proposedFolders = proposedFolders,
                    dockPackages = dockPackages,
                    preservedWidgetsCount = preservedWidgets.size,
                    summaryPoints = listOf(
                        "Organized into ${proposedFolders.size} ML Smart Folders (${proposedFolders.joinToString(", ") { it.folderName }})",
                        "Clean single-page Home Screen with zero multi-page icon clutter",
                        "Pinned ${dockPackages.size} essential communication & favorite apps to the Dock",
                        "Enriched categories using Online Store Metadata + AI & Offline TFLite ML"
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
