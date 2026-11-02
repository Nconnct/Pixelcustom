package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "apps",
    indices = [Index(value = ["primaryCategoryId"]), Index(value = ["appName"])]
)
data class AppEntity(
    @PrimaryKey val packageName: String,
    val activityName: String = "",
    val appName: String,
    val isSystemApp: Boolean = false,
    val primaryCategoryId: String = "cat_uncategorized",
    val categoryConfidence: Float = 0.0f,
    val suggestedCategoryId: String? = null,
    val isInstalled: Boolean = true,
    val isDisabled: Boolean = false,
    val isUserOverridden: Boolean = false,
    val isFavorite: Boolean = false,
    val launchCount: Int = 0,
    val firstInstallTime: Long = System.currentTimeMillis(),
    val lastUpdateTime: Long = System.currentTimeMillis()
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val iconName: String = "folder",
    val colorHex: String = "#4285F4",
    val sortOrder: Int = 0,
    val isVisibleInDrawer: Boolean = true,
    val isSystemDefault: Boolean = false
)

@Entity(
    tableName = "app_category_cross_ref",
    primaryKeys = ["packageName", "categoryId"],
    indices = [Index(value = ["categoryId"]), Index(value = ["packageName"])]
)
data class AppCategoryCrossRef(
    val packageName: String,
    val categoryId: String,
    val isManualAssignment: Boolean = false
)

@Entity(tableName = "home_screen_pages")
data class HomeScreenPageEntity(
    @PrimaryKey val pageIndex: Int,
    val title: String = "Workspace",
    val hasAtAGlanceWidget: Boolean = false
)

@Entity(
    tableName = "home_screen_items",
    indices = [Index(value = ["pageIndex"]), Index(value = ["folderId"])]
)
data class HomeScreenItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val pageIndex: Int = 0,
    val row: Int = 0,
    val column: Int = 0,
    val spanX: Int = 1,
    val spanY: Int = 1,
    val isDockItem: Boolean = false,
    val dockIndex: Int = 0,
    val itemType: String = "APP_SHORTCUT", // APP_SHORTCUT, FOLDER, WIDGET
    val packageName: String? = null,
    val customLabel: String? = null,
    val folderId: Long? = null,
    val appWidgetId: Int = -1,
    val widgetProviderName: String? = null,
    val isPinnedByUser: Boolean = false
)

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val folderName: String,
    val pageIndex: Int = 0,
    val row: Int = 0,
    val column: Int = 0,
    val colorHex: String = "#4285F4",
    val memberPackagesCsv: String = ""
)

@Entity(tableName = "launcher_settings")
data class LauncherSettingsEntity(
    @PrimaryKey val id: Int = 1,
    // Home Screen Customization
    val homeGridColumns: Int = 5,
    val homeGridRows: Int = 7,
    val homeIconSizeDp: Int = 52,
    val showHomeIconLabels: Boolean = false,
    val fontScale: Float = 1.0f,
    val dockIconCount: Int = 4,
    val showDock: Boolean = true,
    val dockTransparency: Float = 0.68f,
    val homePageCount: Int = 3,
    val showAtAGlanceWidget: Boolean = true,
    val folderStyle: String = "EXPANDED_2X2",
    val showBottomSearchBar: Boolean = true,
    val wallpaperPresetId: Int = 0,
    val arrangementMode: String = "AI_ORGANIZED",
    // App Drawer Customization
    val drawerGridColumns: Int = 4,
    val drawerGridRows: Int = 6,
    val showCategoryTabs: Boolean = true,
    val drawerSortMode: String = "ALPHA_ASC",
    val drawerIconSizeDp: Int = 52,
    val showDrawerAppLabels: Boolean = true,
    val drawerBackgroundAlpha: Float = 0.78f,
    // Theme & Frosted Glass Customization
    val themeMode: String = "DARK", // SYSTEM, DARK, LIGHT
    val useWallpaperDynamicColors: Boolean = true,
    val accentColorName: String = "Pixel Blue",
    val frostedGlassIntensity: Float = 0.74f,
    val blurStrengthDp: Int = 18,
    val useMonoFont: Boolean = false,
    val iconShape: String = "CIRCLE",
    val iconScale: Float = 1.0f,
    val activeIconPackPackage: String = "",
    // Gesture Customization
    val swipeUpAction: String = "OPEN_DRAWER",
    val swipeDownAction: String = "OPEN_SEARCH",
    val doubleTapAction: String = "AI_ORGANIZE_PREVIEW",
    val longPressAction: String = "HOME_CUSTOMIZATION",
    val pinchAction: String = "OVERVIEW_PAGES",
    val homeButtonAction: String = "GO_FIRST_PAGE",
    // Search & Privacy
    val defaultSearchEngine: String = "Google",
    val enableContactsSearch: Boolean = true,
    val enableFilesSearch: Boolean = true,
    val enableWebSuggestions: Boolean = true
)

@Entity(tableName = "icon_packs")
data class IconPackEntity(
    @PrimaryKey val packageName: String,
    val name: String,
    val iconCount: Int = 0,
    val isApplied: Boolean = false,
    val lastScannedTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "icon_overrides")
data class IconOverrideEntity(
    @PrimaryKey val targetPackageName: String,
    val customIconKey: String, // e.g. icon pack drawable name or shape preset
    val customLabel: String? = null,
    val updatedTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val query: String,
    val searchType: String = "UNIFIED", // APP, WEB, CONTACT, FILE
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "ai_classifications")
data class AIClassificationEntity(
    @PrimaryKey val packageName: String,
    val predictedCategoryId: String,
    val suggestedSecondaryCategoryId: String? = null,
    val confidence: Float,
    val classificationSource: String, // KNOWN_DB, METADATA, NEURAL_TFLITE, USER_OVERRIDE
    val modelVersion: String = "1.0.0-offline-hybrid",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "ai_user_corrections",
    indices = [Index(value = ["packageName"])]
)
data class AIUserCorrectionEntity(
    @PrimaryKey val packageName: String,
    val previousCategoryId: String,
    val correctedCategoryId: String,
    val extractedTokensCsv: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "layout_snapshots")
data class LayoutSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val snapshotName: String,
    val arrangementMode: String,
    val itemsJson: String,
    val foldersJson: String,
    val timestamp: Long = System.currentTimeMillis()
)
