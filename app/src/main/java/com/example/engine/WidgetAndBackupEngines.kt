package com.example.engine

import android.app.role.RoleManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import com.example.data.local.AIUserCorrectionEntity
import com.example.data.local.AppCategoryCrossRef
import com.example.data.local.CategoryEntity
import com.example.data.local.FolderEntity
import com.example.data.local.HomeScreenItemEntity
import com.example.data.local.LauncherDao
import com.example.data.local.LauncherSettingsEntity
import org.json.JSONArray
import org.json.JSONObject

data class SystemWidgetProviderItem(
    val providerComponentName: String,
    val label: String,
    val packageName: String,
    val minWidthDp: Int,
    val minHeightDp: Int,
    val providerInfo: AppWidgetProviderInfo
)

/**
 * Hosts real Android AppWidgets on the PixelCategory Launcher home screen
 * using Android's AppWidgetHost and AppWidgetManager APIs, and manages
 * Android HOME role checks.
 */
class WidgetHostController(private val context: Context) {

    companion object {
        const val APP_WIDGET_HOST_ID = 2048
    }

    val appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context)
    val appWidgetHost: AppWidgetHost = AppWidgetHost(context.applicationContext, APP_WIDGET_HOST_ID)

    private var isListening = false

    fun startListening() {
        if (isListening) return
        try {
            appWidgetHost.startListening()
            isListening = true
        } catch (_: Exception) {
        }
    }

    fun stopListening() {
        if (!isListening) return
        try {
            appWidgetHost.stopListening()
            isListening = false
        } catch (_: Exception) {
        }
    }

    fun allocateWidgetId(): Int {
        return try {
            appWidgetHost.allocateAppWidgetId()
        } catch (_: Exception) {
            -1
        }
    }

    fun deleteWidgetId(appWidgetId: Int) {
        if (appWidgetId <= 0) return
        try {
            appWidgetHost.deleteAppWidgetId(appWidgetId)
        } catch (_: Exception) {
        }
    }

    fun getAvailableWidgetProviders(): List<SystemWidgetProviderItem> {
        return try {
            val providers = appWidgetManager.installedProviders
            val density = context.resources.displayMetrics.density.coerceAtLeast(1f)
            providers.mapNotNull { info ->
                val comp = info.provider ?: return@mapNotNull null
                val label = info.loadLabel(context.packageManager) ?: comp.shortClassName
                SystemWidgetProviderItem(
                    providerComponentName = comp.flattenToString(),
                    label = label,
                    packageName = comp.packageName,
                    minWidthDp = (info.minWidth / density).toInt().coerceAtLeast(110),
                    minHeightDp = (info.minHeight / density).toInt().coerceAtLeast(60),
                    providerInfo = info
                )
            }.sortedBy { it.label.lowercase() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun createWidgetHostView(appWidgetId: Int): AppWidgetHostView? {
        if (appWidgetId <= 0) return null
        return try {
            val info = appWidgetManager.getAppWidgetInfo(appWidgetId) ?: return null
            appWidgetHost.createView(context, appWidgetId, info)
        } catch (_: Exception) {
            null
        }
    }

    fun isDefaultHomeLauncher(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(RoleManager::class.java)
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                    return roleManager.isRoleHeld(RoleManager.ROLE_HOME)
                }
            }
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val res = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            res?.activityInfo?.packageName == context.packageName
        } catch (_: Exception) {
            false
        }
    }

    fun createRoleHomeRequestIntent(): Intent {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                return roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
            }
        }
        return Intent(Settings.ACTION_HOME_SETTINGS)
    }
}

/**
 * Local Offline Backup & Restore Engine.
 * Serializes categories, app category assignments, AI user corrections,
 * home screen items, folders, and settings to/from a self-contained JSON payload.
 */
class LocalBackupManager(private val dao: LauncherDao) {

    suspend fun exportBackupJson(): String {
        val root = JSONObject()
        root.put("backupVersion", 2)
        root.put("app", "PixelCategory Launcher")
        root.put("timestamp", System.currentTimeMillis())

        // 1. Settings
        val s = dao.getSettingsSnapshot() ?: LauncherSettingsEntity()
        val settingsObj = JSONObject().apply {
            put("homeGridColumns", s.homeGridColumns)
            put("homeGridRows", s.homeGridRows)
            put("homeIconSizeDp", s.homeIconSizeDp)
            put("showHomeIconLabels", s.showHomeIconLabels)
            put("fontScale", s.fontScale.toDouble())
            put("dockIconCount", s.dockIconCount)
            put("showDock", s.showDock)
            put("dockTransparency", s.dockTransparency.toDouble())
            put("homePageCount", s.homePageCount)
            put("showAtAGlanceWidget", s.showAtAGlanceWidget)
            put("showBottomSearchBar", s.showBottomSearchBar)
            put("arrangementMode", s.arrangementMode)
            put("drawerGridColumns", s.drawerGridColumns)
            put("drawerSortMode", s.drawerSortMode)
            put("themeMode", s.themeMode)
            put("useWallpaperDynamicColors", s.useWallpaperDynamicColors)
            put("accentColorName", s.accentColorName)
            put("frostedGlassIntensity", s.frostedGlassIntensity.toDouble())
            put("blurStrengthDp", s.blurStrengthDp)
            put("iconShape", s.iconShape)
            put("activeIconPackPackage", s.activeIconPackPackage)
            put("defaultSearchEngine", s.defaultSearchEngine)
        }
        root.put("settings", settingsObj)

        // 2. Categories
        val catsArr = JSONArray()
        for (c in dao.getCategoriesSnapshot()) {
            catsArr.put(
                JSONObject().apply {
                    put("id", c.id)
                    put("name", c.name)
                    put("iconName", c.iconName)
                    put("colorHex", c.colorHex)
                    put("sortOrder", c.sortOrder)
                    put("isVisibleInDrawer", c.isVisibleInDrawer)
                    put("isSystemDefault", c.isSystemDefault)
                }
            )
        }
        root.put("categories", catsArr)

        // 3. CrossRefs
        val refsArr = JSONArray()
        for (r in dao.getAppCategoryCrossRefsSnapshot()) {
            refsArr.put(
                JSONObject().apply {
                    put("packageName", r.packageName)
                    put("categoryId", r.categoryId)
                    put("isManualAssignment", r.isManualAssignment)
                }
            )
        }
        root.put("appCategoryCrossRefs", refsArr)

        // 4. AI User Corrections
        val corrArr = JSONArray()
        for (corr in dao.getAllUserCorrections()) {
            corrArr.put(
                JSONObject().apply {
                    put("packageName", corr.packageName)
                    put("previousCategoryId", corr.previousCategoryId)
                    put("correctedCategoryId", corr.correctedCategoryId)
                    put("extractedTokensCsv", corr.extractedTokensCsv)
                }
            )
        }
        root.put("userCorrections", corrArr)

        // 5. Folders & Home Items
        val foldersArr = JSONArray()
        for (f in dao.getFoldersSnapshot()) {
            foldersArr.put(
                JSONObject().apply {
                    put("id", f.id)
                    put("folderName", f.folderName)
                    put("pageIndex", f.pageIndex)
                    put("row", f.row)
                    put("column", f.column)
                    put("colorHex", f.colorHex)
                    put("memberPackagesCsv", f.memberPackagesCsv)
                }
            )
        }
        root.put("folders", foldersArr)

        val itemsArr = JSONArray()
        for (item in dao.getHomeItemsSnapshot()) {
            itemsArr.put(
                JSONObject().apply {
                    put("pageIndex", item.pageIndex)
                    put("row", item.row)
                    put("column", item.column)
                    put("spanX", item.spanX)
                    put("spanY", item.spanY)
                    put("isDockItem", item.isDockItem)
                    put("dockIndex", item.dockIndex)
                    put("itemType", item.itemType)
                    put("packageName", item.packageName ?: "")
                    put("customLabel", item.customLabel ?: "")
                    put("folderId", item.folderId ?: -1L)
                    put("isPinnedByUser", item.isPinnedByUser)
                }
            )
        }
        root.put("homeItems", itemsArr)

        return root.toString(2)
    }

    suspend fun importBackupJson(jsonPayload: String): Boolean {
        return try {
            val root = JSONObject(jsonPayload)
            val sObj = root.optJSONObject("settings")
            if (sObj != null) {
                val current = dao.getSettingsSnapshot() ?: LauncherSettingsEntity()
                dao.upsertSettings(
                    current.copy(
                        homeGridColumns = sObj.optInt("homeGridColumns", current.homeGridColumns),
                        homeGridRows = sObj.optInt("homeGridRows", current.homeGridRows),
                        homeIconSizeDp = sObj.optInt("homeIconSizeDp", current.homeIconSizeDp),
                        showHomeIconLabels = sObj.optBoolean("showHomeIconLabels", current.showHomeIconLabels),
                        fontScale = sObj.optDouble("fontScale", current.fontScale.toDouble()).toFloat(),
                        dockIconCount = sObj.optInt("dockIconCount", current.dockIconCount),
                        showDock = sObj.optBoolean("showDock", current.showDock),
                        dockTransparency = sObj.optDouble("dockTransparency", current.dockTransparency.toDouble()).toFloat(),
                        homePageCount = sObj.optInt("homePageCount", current.homePageCount),
                        showAtAGlanceWidget = sObj.optBoolean("showAtAGlanceWidget", current.showAtAGlanceWidget),
                        showBottomSearchBar = sObj.optBoolean("showBottomSearchBar", current.showBottomSearchBar),
                        arrangementMode = sObj.optString("arrangementMode", current.arrangementMode),
                        drawerGridColumns = sObj.optInt("drawerGridColumns", current.drawerGridColumns),
                        drawerSortMode = sObj.optString("drawerSortMode", current.drawerSortMode),
                        themeMode = sObj.optString("themeMode", current.themeMode),
                        useWallpaperDynamicColors = sObj.optBoolean("useWallpaperDynamicColors", current.useWallpaperDynamicColors),
                        accentColorName = sObj.optString("accentColorName", current.accentColorName),
                        frostedGlassIntensity = sObj.optDouble("frostedGlassIntensity", current.frostedGlassIntensity.toDouble()).toFloat(),
                        blurStrengthDp = sObj.optInt("blurStrengthDp", current.blurStrengthDp),
                        iconShape = sObj.optString("iconShape", current.iconShape),
                        activeIconPackPackage = sObj.optString("activeIconPackPackage", current.activeIconPackPackage),
                        defaultSearchEngine = sObj.optString("defaultSearchEngine", current.defaultSearchEngine)
                    )
                )
            }

            val catsArr = root.optJSONArray("categories")
            if (catsArr != null) {
                val list = mutableListOf<CategoryEntity>()
                for (i in 0 until catsArr.length()) {
                    val c = catsArr.getJSONObject(i)
                    list.add(
                        CategoryEntity(
                            id = c.getString("id"),
                            name = c.getString("name"),
                            iconName = c.optString("iconName", "folder"),
                            colorHex = c.optString("colorHex", "#4285F4"),
                            sortOrder = c.optInt("sortOrder", i),
                            isVisibleInDrawer = c.optBoolean("isVisibleInDrawer", true),
                            isSystemDefault = c.optBoolean("isSystemDefault", false)
                        )
                    )
                }
                if (list.isNotEmpty()) dao.upsertCategories(list)
            }

            val refsArr = root.optJSONArray("appCategoryCrossRefs")
            if (refsArr != null) {
                val refs = mutableListOf<AppCategoryCrossRef>()
                for (i in 0 until refsArr.length()) {
                    val r = refsArr.getJSONObject(i)
                    refs.add(
                        AppCategoryCrossRef(
                            packageName = r.getString("packageName"),
                            categoryId = r.getString("categoryId"),
                            isManualAssignment = r.optBoolean("isManualAssignment", false)
                        )
                    )
                }
                if (refs.isNotEmpty()) dao.insertAppCategoryCrossRefs(refs)
            }

            val corrArr = root.optJSONArray("userCorrections")
            if (corrArr != null) {
                for (i in 0 until corrArr.length()) {
                    val c = corrArr.getJSONObject(i)
                    dao.upsertUserCorrection(
                        AIUserCorrectionEntity(
                            packageName = c.getString("packageName"),
                            previousCategoryId = c.optString("previousCategoryId", "cat_uncategorized"),
                            correctedCategoryId = c.getString("correctedCategoryId"),
                            extractedTokensCsv = c.optString("extractedTokensCsv", "")
                        )
                    )
                }
            }

            val foldersArr = root.optJSONArray("folders")
            val itemsArr = root.optJSONArray("homeItems")
            if (foldersArr != null && itemsArr != null) {
                val folders = mutableListOf<FolderEntity>()
                for (i in 0 until foldersArr.length()) {
                    val f = foldersArr.getJSONObject(i)
                    folders.add(
                        FolderEntity(
                            id = f.optLong("id", (i + 1).toLong()),
                            folderName = f.getString("folderName"),
                            pageIndex = f.optInt("pageIndex", 0),
                            row = f.optInt("row", 0),
                            column = f.optInt("column", 0),
                            colorHex = f.optString("colorHex", "#4285F4"),
                            memberPackagesCsv = f.optString("memberPackagesCsv", "")
                        )
                    )
                }
                val items = mutableListOf<HomeScreenItemEntity>()
                for (i in 0 until itemsArr.length()) {
                    val item = itemsArr.getJSONObject(i)
                    val fId = item.optLong("folderId", -1L)
                    items.add(
                        HomeScreenItemEntity(
                            pageIndex = item.optInt("pageIndex", 0),
                            row = item.optInt("row", 0),
                            column = item.optInt("column", 0),
                            spanX = item.optInt("spanX", 1),
                            spanY = item.optInt("spanY", 1),
                            isDockItem = item.optBoolean("isDockItem", false),
                            dockIndex = item.optInt("dockIndex", 0),
                            itemType = item.optString("itemType", "APP_SHORTCUT"),
                            packageName = item.optString("packageName", "").takeIf { it.isNotEmpty() },
                            customLabel = item.optString("customLabel", "").takeIf { it.isNotEmpty() },
                            folderId = if (fId > 0) fId else null,
                            isPinnedByUser = item.optBoolean("isPinnedByUser", false)
                        )
                    )
                }
                dao.clearAllFolders()
                dao.clearAllHomeItems()
                dao.insertFolders(folders)
                dao.insertHomeItems(items)
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
