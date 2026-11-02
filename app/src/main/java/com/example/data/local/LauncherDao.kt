package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LauncherDao {

    // --- Apps ---
    @Query("SELECT * FROM apps WHERE isInstalled = 1 ORDER BY LOWER(appName) ASC")
    fun observeInstalledApps(): Flow<List<AppEntity>>

    @Query("SELECT * FROM apps")
    suspend fun getAllAppsSnapshot(): List<AppEntity>

    @Query("SELECT * FROM apps WHERE packageName = :packageName LIMIT 1")
    suspend fun getAppByPackage(packageName: String): AppEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertApps(apps: List<AppEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertApp(app: AppEntity)

    @Query("UPDATE apps SET isInstalled = 0 WHERE packageName = :packageName")
    suspend fun markAppUninstalled(packageName: String)

    @Query("UPDATE apps SET isFavorite = :isFavorite WHERE packageName = :packageName")
    suspend fun updateAppFavorite(packageName: String, isFavorite: Boolean)

    @Query("UPDATE apps SET launchCount = launchCount + 1 WHERE packageName = :packageName")
    suspend fun incrementLaunchCount(packageName: String)

    @Query(
        "UPDATE apps SET primaryCategoryId = :categoryId, categoryConfidence = :confidence, " +
            "isUserOverridden = :isManual, suggestedCategoryId = NULL WHERE packageName = :packageName"
    )
    suspend fun updateAppCategory(
        packageName: String,
        categoryId: String,
        confidence: Float,
        isManual: Boolean
    )

    // --- Categories & CrossRefs ---
    @Query("SELECT * FROM categories ORDER BY sortOrder ASC")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC")
    suspend fun getCategoriesSnapshot(): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategories(categories: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategory(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :categoryId AND isSystemDefault = 0")
    suspend fun deleteCustomCategory(categoryId: String)

    @Query("SELECT * FROM app_category_cross_ref")
    fun observeAppCategoryCrossRefs(): Flow<List<AppCategoryCrossRef>>

    @Query("SELECT * FROM app_category_cross_ref")
    suspend fun getAppCategoryCrossRefsSnapshot(): List<AppCategoryCrossRef>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppCategoryCrossRef(crossRef: AppCategoryCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppCategoryCrossRefs(crossRefs: List<AppCategoryCrossRef>)

    @Query("DELETE FROM app_category_cross_ref WHERE packageName = :packageName AND isManualAssignment = 0")
    suspend fun deleteAutoCrossRefsForPackage(packageName: String)

    @Query("DELETE FROM app_category_cross_ref WHERE packageName = :packageName AND categoryId = :categoryId")
    suspend fun removeAppFromCategory(packageName: String, categoryId: String)

    // --- Home Screen Pages, Items & Folders ---
    @Query("SELECT * FROM home_screen_pages ORDER BY pageIndex ASC")
    fun observeHomePages(): Flow<List<HomeScreenPageEntity>>

    @Query("SELECT * FROM home_screen_pages ORDER BY pageIndex ASC")
    suspend fun getHomePagesSnapshot(): List<HomeScreenPageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHomePages(pages: List<HomeScreenPageEntity>)

    @Query("DELETE FROM home_screen_pages WHERE pageIndex >= :maxPageIndex")
    suspend fun trimHomePages(maxPageIndex: Int)

    @Query("SELECT * FROM home_screen_items ORDER BY isDockItem DESC, pageIndex ASC, row ASC, column ASC")
    fun observeHomeItems(): Flow<List<HomeScreenItemEntity>>

    @Query("SELECT * FROM home_screen_items ORDER BY isDockItem DESC, pageIndex ASC, row ASC, column ASC")
    suspend fun getHomeItemsSnapshot(): List<HomeScreenItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHomeItem(item: HomeScreenItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHomeItems(items: List<HomeScreenItemEntity>)

    @Update
    suspend fun updateHomeItem(item: HomeScreenItemEntity)

    @Query("DELETE FROM home_screen_items WHERE id = :itemId")
    suspend fun deleteHomeItemById(itemId: Long)

    @Query("DELETE FROM home_screen_items WHERE packageName = :packageName")
    suspend fun deleteHomeItemsByPackage(packageName: String)

    @Query("DELETE FROM home_screen_items")
    suspend fun clearAllHomeItems()

    @Query("SELECT * FROM folders ORDER BY pageIndex ASC, row ASC, column ASC")
    fun observeFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders ORDER BY pageIndex ASC, row ASC, column ASC")
    suspend fun getFoldersSnapshot(): List<FolderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: FolderEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolders(folders: List<FolderEntity>)

    @Query("DELETE FROM folders WHERE id = :folderId")
    suspend fun deleteFolderById(folderId: Long)

    @Query("DELETE FROM folders")
    suspend fun clearAllFolders()

    @Transaction
    suspend fun replaceHomeLayout(
        items: List<HomeScreenItemEntity>,
        folders: List<FolderEntity>
    ) {
        clearAllHomeItems()
        clearAllFolders()
        val insertedFolderIds = mutableListOf<Long>()
        for (folder in folders) {
            val fId = insertFolder(folder.copy(id = 0L))
            insertedFolderIds.add(fId)
        }
        // Map any folder placeholder indices or insert direct items
        insertHomeItems(items)
    }

    // --- Settings ---
    @Query("SELECT * FROM launcher_settings WHERE id = 1 LIMIT 1")
    fun observeSettings(): Flow<LauncherSettingsEntity?>

    @Query("SELECT * FROM launcher_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettingsSnapshot(): LauncherSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSettings(settings: LauncherSettingsEntity)

    // --- Icon Packs & Overrides ---
    @Query("SELECT * FROM icon_packs ORDER BY name ASC")
    fun observeIconPacks(): Flow<List<IconPackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertIconPacks(packs: List<IconPackEntity>)

    @Query("SELECT * FROM icon_overrides")
    fun observeIconOverrides(): Flow<List<IconOverrideEntity>>

    @Query("SELECT * FROM icon_overrides")
    suspend fun getIconOverridesSnapshot(): List<IconOverrideEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertIconOverride(overrideEntity: IconOverrideEntity)

    @Query("DELETE FROM icon_overrides WHERE targetPackageName = :packageName")
    suspend fun removeIconOverride(packageName: String)

    // --- Search History ---
    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT 15")
    fun observeSearchHistory(): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchHistory(entry: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE query = :query")
    suspend fun deleteSearchHistoryByQuery(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clearSearchHistory()

    // --- AI Classifications & User Corrections ---
    @Query("SELECT * FROM ai_classifications WHERE packageName = :packageName LIMIT 1")
    suspend fun getAIClassification(packageName: String): AIClassificationEntity?

    @Query("SELECT * FROM ai_classifications")
    suspend fun getAllAIClassifications(): List<AIClassificationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAIClassification(classification: AIClassificationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAIClassifications(classifications: List<AIClassificationEntity>)

    @Query("SELECT * FROM ai_user_corrections")
    suspend fun getAllUserCorrections(): List<AIUserCorrectionEntity>

    @Query("SELECT * FROM ai_user_corrections WHERE packageName = :packageName LIMIT 1")
    suspend fun getUserCorrectionForPackage(packageName: String): AIUserCorrectionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUserCorrection(correction: AIUserCorrectionEntity)

    // --- Layout Snapshots (Undo / Restore) ---
    @Query("SELECT * FROM layout_snapshots ORDER BY timestamp DESC LIMIT 10")
    fun observeLayoutSnapshots(): Flow<List<LayoutSnapshotEntity>>

    @Query("SELECT * FROM layout_snapshots ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestLayoutSnapshot(): LayoutSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLayoutSnapshot(snapshot: LayoutSnapshotEntity): Long
}
