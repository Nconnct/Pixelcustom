package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.domain.model.DefaultCategory

@Database(
    entities = [
        AppEntity::class,
        CategoryEntity::class,
        AppCategoryCrossRef::class,
        HomeScreenPageEntity::class,
        HomeScreenItemEntity::class,
        FolderEntity::class,
        LauncherSettingsEntity::class,
        IconPackEntity::class,
        IconOverrideEntity::class,
        SearchHistoryEntity::class,
        AIClassificationEntity::class,
        AIUserCorrectionEntity::class,
        LayoutSnapshotEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class LauncherDatabase : RoomDatabase() {

    abstract fun launcherDao(): LauncherDao

    companion object {
        @Volatile
        private var INSTANCE: LauncherDatabase? = null

        /**
         * Explicit non-destructive migration from version 1 to version 2.
         * Ensures user categories, app overrides, and home layouts are never lost across updates.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `layout_snapshots` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`snapshotName` TEXT NOT NULL, " +
                        "`arrangementMode` TEXT NOT NULL, " +
                        "`itemsJson` TEXT NOT NULL, " +
                        "`foldersJson` TEXT NOT NULL, " +
                        "`timestamp` INTEGER NOT NULL)"
                )
            }
        }

        fun getInstance(context: Context): LauncherDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LauncherDatabase::class.java,
                    "pixel_category_launcher.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        fun buildDefaultCategoryEntities(): List<CategoryEntity> {
            return DefaultCategory.entries.map { cat ->
                CategoryEntity(
                    id = cat.id,
                    name = cat.displayName,
                    iconName = cat.iconName,
                    colorHex = cat.colorHex,
                    sortOrder = cat.sortOrder,
                    isVisibleInDrawer = true,
                    isSystemDefault = true
                )
            }
        }
    }
}
