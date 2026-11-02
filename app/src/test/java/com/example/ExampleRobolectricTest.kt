package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ai.OfflineAiClassifier
import com.example.data.local.AppEntity
import com.example.data.local.LauncherDatabase
import com.example.domain.model.DefaultCategory
import com.example.engine.IconPackManager
import com.example.engine.LocalBackupManager
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var database: LauncherDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, LauncherDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `verify app name resource and embedded TFLite model loading`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("PixelCategory Launcher", appName)

        // Verify OfflineAiClassifier loads real assets/app_category_model.tflite & weights
        val classifier = OfflineAiClassifier(context)
        val pred = classifier.classifyApp("com.spotify.music", "Spotify Music", isSystemApp = false)
        assertEquals(DefaultCategory.MUSIC_AUDIO.id, pred.categoryId)
        assertTrue(pred.confidence >= 0.90f)
    }

    @Test
    fun `room database persists categories, app overrides, and exports local JSON backup`() = runTest {
        val dao = database.launcherDao()
        val defaultCats = LauncherDatabase.buildDefaultCategoryEntities()
        dao.upsertCategories(defaultCats)

        val storedCats = dao.getCategoriesSnapshot()
        assertEquals(18, storedCats.size)

        dao.upsertApp(
            AppEntity(
                packageName = "org.mozilla.firefox",
                appName = "Firefox",
                primaryCategoryId = DefaultCategory.BROWSERS.id,
                categoryConfidence = 0.98f,
                isUserOverridden = true,
                isFavorite = true
            )
        )

        val backupManager = LocalBackupManager(dao)
        val exportedJson = backupManager.exportBackupJson()
        assertTrue(exportedJson.contains("PixelCategory Launcher"))
        assertTrue(exportedJson.contains("cat_browsers"))

        // Verify restore round-trip
        val restoredOk = backupManager.importBackupJson(exportedJson)
        assertTrue(restoredOk)
    }

    @Test
    fun `icon pack manager parses ADW and Nova ComponentInfo strings accurately`() {
        val iconPackManager = IconPackManager(context)
        val pkg = iconPackManager.extractPackageFromComponentInfo(
            "ComponentInfo{com.whatsapp/com.whatsapp.Main}"
        )
        assertEquals("com.whatsapp", pkg)

        val packs = iconPackManager.discoverInstalledIconPacks("")
        assertTrue(packs.size >= 3) // System Default + 2 built-in shader packs
    }
}
