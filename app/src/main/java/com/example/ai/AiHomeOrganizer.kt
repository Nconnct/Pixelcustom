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

        fun nextFreeCell(preferredPage: Int = 0): Triple<Int, Int, Int> {
            var p = preferredPage
            while (p < 4) {
                val startRow = if (p == 0) 1 else 0
                for (r in startRow until rows) {
                    for (c in 0 until cols) {
                        val cell = Triple(p, r, c)
                        if (cell !in occupiedCells) {
                            occupiedCells.add(cell)
                            return cell
                        }
                    }
                }
                p++
            }
            return Triple(1, rows - 1, cols - 1)
        }

        return when (mode) {
            ArrangementMode.PIXEL_DEFAULT -> {
                // Place top essentials on Page 0 bottom rows (Pixel style) and Page 1
                val remaining = rankedApps.filter { it.packageName !in usedPackages }.take(cols * 2 + cols)
                for (app in remaining) {
                    val (page, r, c) = nextFreeCell(0)
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
                    pagesCount = maxOf(2, (proposedItems.maxOfOrNull { it.pageIndex } ?: 0) + 1),
                    proposedItems = proposedItems,
                    proposedFolders = emptyList(),
                    dockPackages = dockPackages,
                    preservedWidgetsCount = preservedWidgets.size,
                    summaryPoints = listOf(
                        "Pinned ${dockPackages.size} essential communication & browser apps to the Dock",
                        "Reserved Page 1 top space for the Pixel At-a-Glance widget",
                        "Placed ${remaining.size} top-ranked daily apps across clean Pixel grid rows",
                        "Preserved ${preservedWidgets.size} active widgets and ${pinnedShortcuts.size} pinned shortcuts"
                    )
                )
            }

            ArrangementMode.AI_ORGANIZED, ArrangementMode.CUSTOM_LAYOUT -> {
                // Group apps into intelligent category clusters for Smart Folders
                val folderClusters = listOf(
                    Triple("Social & Chat", "#10B981", setOf(DefaultCategory.SOCIAL.id, DefaultCategory.MESSAGING.id)),
                    Triple("Finance & UPI", "#2563EB", setOf(DefaultCategory.BANKING.id, DefaultCategory.UPI_PAYMENTS.id)),
                    Triple("Media & Music", "#EF4444", setOf(DefaultCategory.VIDEO_STREAMING.id, DefaultCategory.MUSIC_AUDIO.id, DefaultCategory.PHOTOGRAPHY.id)),
                    Triple("Work & Study", "#3B82F6", setOf(DefaultCategory.PRODUCTIVITY.id, DefaultCategory.EDUCATION.id, DefaultCategory.TECH_DEV.id)),
                    Triple("Web & Cloud", "#0284C7", setOf(DefaultCategory.BROWSERS.id, DefaultCategory.INTERNET.id)),
                    Triple("Shop & Life", "#F59E0B", setOf(DefaultCategory.SHOPPING.id)),
                    Triple("Games", "#8B5CF6", setOf(DefaultCategory.GAMES.id)),
                    Triple("Tools & System", "#F97316", setOf(DefaultCategory.UTILITIES.id, DefaultCategory.SYSTEM_APPS.id))
                )

                var nextFolderTempId = 100L
                for ((folderTitle, colorHex, catIds) in folderClusters) {
                    val matchingApps = rankedApps.filter {
                        it.packageName !in usedPackages && it.categoryId in catIds
                    }
                    if (matchingApps.size >= 2) {
                        val folderMembers = matchingApps.take(9).map { it.packageName }
                        usedPackages.addAll(folderMembers)
                        val (page, r, c) = nextFreeCell(0)
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

                // Place top remaining standalone favorites and high-priority apps
                val standaloneApps = rankedApps
                    .filter { it.packageName !in usedPackages }
                    .take(cols * 2)

                for (app in standaloneApps) {
                    val (page, r, c) = nextFreeCell(0)
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
                    pagesCount = maxOf(2, (proposedItems.maxOfOrNull { it.pageIndex } ?: 0) + 1),
                    proposedItems = proposedItems,
                    proposedFolders = proposedFolders,
                    dockPackages = dockPackages,
                    preservedWidgetsCount = preservedWidgets.size,
                    summaryPoints = listOf(
                        "Created ${proposedFolders.size} AI Smart Folders grouping related categories",
                        "Prioritized ${dockPackages.size} favorite & communication apps in the Dock",
                        "Arranged ${standaloneApps.size} frequent standalone apps with zero duplicate shortcuts",
                        "Preserved ${preservedWidgets.size} widgets and ${pinnedShortcuts.size} user-pinned items"
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

        return LayoutSnapshotEntity(
            snapshotName = snapshotName,
            arrangementMode = mode,
            itemsJson = itemsArr.toString(),
            foldersJson = foldersArr.toString()
        )
    }

    fun deserializeLayoutSnapshot(
        snapshot: LayoutSnapshotEntity
    ): Pair<List<HomeScreenItemEntity>, List<FolderEntity>> {
        val folders = mutableListOf<FolderEntity>()
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

        val items = mutableListOf<HomeScreenItemEntity>()
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
        return items to folders
    }
}
