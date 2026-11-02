package com.example

import com.example.ai.AiHomeOrganizer
import com.example.ai.OfflineAiClassifier
import com.example.data.local.AIUserCorrectionEntity
import com.example.data.local.FolderEntity
import com.example.data.local.HomeScreenItemEntity
import com.example.domain.model.AppInfo
import com.example.domain.model.ArrangementMode
import com.example.domain.model.DefaultCategory
import com.example.domain.model.HomeItemType
import com.example.domain.model.HomeScreenItemModel
import com.example.engine.UnifiedSearchEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `offline AI categorizes known and unknown apps accurately without internet`() {
        val classifier = OfflineAiClassifier(context = null)

        // Known package in local database
        val chromePred = classifier.classifyApp("com.android.chrome", "Chrome", isSystemApp = false)
        assertEquals(DefaultCategory.BROWSERS.id, chromePred.categoryId)
        assertTrue(chromePred.confidence >= 0.90f)

        // Unknown UPI payment app classified via domain/token inference
        val upiPred = classifier.classifyApp("in.NPCI.customupi.wallet", "FastUPI Pay Wallet", isSystemApp = false)
        assertEquals(DefaultCategory.UPI_PAYMENTS.id, upiPred.categoryId)
        assertTrue(upiPred.confidence >= OfflineAiClassifier.CONFIDENCE_THRESHOLD)

        // Unknown Banking app
        val bankPred = classifier.classifyApp("com.regionalbank.netbanking", "Metro Mobile Banking", isSystemApp = false)
        assertEquals(DefaultCategory.BANKING.id, bankPred.categoryId)

        // Unknown Game app
        val gamePred = classifier.classifyApp("com.indie.arcade.shooter", "Galaxy Arcade Shooter RPG", isSystemApp = false)
        assertEquals(DefaultCategory.GAMES.id, gamePred.categoryId)
    }

    @Test
    fun `offline AI routes low-confidence ambiguous apps to Uncategorized with suggestion`() {
        val classifier = OfflineAiClassifier(context = null)
        val ambiguous = classifier.classifyApp("org.xyz.qwertz", "XyzAlpha", isSystemApp = false)
        assertEquals(DefaultCategory.UNCATEGORIZED.id, ambiguous.categoryId)
        assertTrue(ambiguous.confidence < OfflineAiClassifier.CONFIDENCE_THRESHOLD)
        assertNotNull(ambiguous.suggestedCategoryId)
    }

    @Test
    fun `manual user category overrides take precedence and boost related tokens`() {
        val classifier = OfflineAiClassifier(context = null)
        val correction = AIUserCorrectionEntity(
            packageName = "com.custom.ledgerbook",
            previousCategoryId = DefaultCategory.UNCATEGORIZED.id,
            correctedCategoryId = DefaultCategory.PRODUCTIVITY.id,
            extractedTokensCsv = "custom,ledgerbook,khatabook"
        )
        classifier.syncUserCorrections(listOf(correction))

        // Direct package override returns 100% confidence
        val direct = classifier.classifyApp("com.custom.ledgerbook", "LedgerBook", isSystemApp = false)
        assertEquals(DefaultCategory.PRODUCTIVITY.id, direct.categoryId)
        assertEquals(1.0f, direct.confidence, 0.001f)
        assertEquals("USER_OVERRIDE", direct.source)
    }

    @Test
    fun `unified search ranks exact, prefix, and fuzzy matches properly`() {
        val searchEngine = UnifiedSearchEngine(context = null)
        val apps = listOf(
            AppInfo(appName = "Firefox Browser", packageName = "org.mozilla.firefox", categoryName = "Browsers"),
            AppInfo(appName = "Files by Google", packageName = "com.google.android.apps.nbu.files", categoryName = "Utilities"),
            AppInfo(appName = "Telegram", packageName = "org.telegram.messenger", categoryName = "Messaging", isFavorite = true),
            AppInfo(appName = "Termux", packageName = "com.termux", categoryName = "Technology & Development")
        )

        val prefixResults = searchEngine.searchApplications("fi", apps)
        assertEquals(2, prefixResults.size)
        assertTrue(prefixResults.any { it.appName == "Firefox Browser" })

        val fuzzyResults = searchEngine.searchApplications("tlgrm", apps)
        assertTrue(fuzzyResults.isNotEmpty())
        assertEquals("org.telegram.messenger", fuzzyResults.first().packageName)
    }

    @Test
    fun `AI home organizer groups categories into smart folders, preserves widgets, and supports undo restore`() {
        val organizer = AiHomeOrganizer()
        val sampleApps = listOf(
            AppInfo("WhatsApp", "com.whatsapp", categoryId = DefaultCategory.MESSAGING.id, isFavorite = true),
            AppInfo("Telegram", "org.telegram.messenger", categoryId = DefaultCategory.MESSAGING.id),
            AppInfo("Signal", "org.thoughtcrime.securesms", categoryId = DefaultCategory.MESSAGING.id),
            AppInfo("SBI YONO", "com.sbi.lotusintouch", categoryId = DefaultCategory.BANKING.id),
            AppInfo("Google Pay", "com.google.android.apps.nbu.paisa.user", categoryId = DefaultCategory.UPI_PAYMENTS.id),
            AppInfo("Gmail", "com.google.android.gm", categoryId = DefaultCategory.PRODUCTIVITY.id),
            AppInfo("Chrome", "com.android.chrome", categoryId = DefaultCategory.BROWSERS.id, isFavorite = true)
        )
        val existingWidget = HomeScreenItemModel(
            id = 99L,
            pageIndex = 0,
            row = 1,
            column = 0,
            spanX = 4,
            spanY = 1,
            itemType = HomeItemType.WIDGET,
            appWidgetId = 501,
            customLabel = "Clock Widget"
        )

        val preview = organizer.generateProposedLayout(
            mode = ArrangementMode.AI_ORGANIZED,
            installedApps = sampleApps,
            existingItems = listOf(existingWidget),
            existingFolders = emptyList(),
            gridColumns = 4,
            gridRows = 5,
            dockCapacity = 2
        )

        // 3-page layout with main ML folders on Page 1 and extra apps on Pages 2 & 3
        assertEquals(3, preview.pagesCount)
        val folderNames = preview.proposedFolders.map { it.folderName }.toSet()
        assertTrue("Social" in folderNames)
        assertTrue("Banking" in folderNames)
        assertTrue("Productivity" in folderNames)
        assertTrue(preview.proposedFolders.all { it.pageIndex == 0 })
        assertTrue(preview.proposedItems.any { !it.isDockItem && it.pageIndex in 1..2 })

        // Preserves existing widget
        assertEquals(1, preview.preservedWidgetsCount)
        assertTrue(preview.proposedItems.any { it.itemType == HomeItemType.WIDGET && it.appWidgetId == 501 })

        // Prioritizes favorite apps in dock without duplicating them inside folders
        assertTrue("com.whatsapp" in preview.dockPackages)
        val allFolderMemberPkgs = preview.proposedFolders.flatMap { it.memberPackages }
        assertFalse("com.whatsapp" in allFolderMemberPkgs)

        // Verify Undo / Restore snapshot serialization round-trip
        val snapshot = organizer.serializeLayoutSnapshot(
            snapshotName = "Test Snapshot",
            mode = "PIXEL_DEFAULT",
            items = listOf(
                HomeScreenItemEntity(
                    id = 1L,
                    pageIndex = 0,
                    row = 2,
                    column = 1,
                    isDockItem = false,
                    itemType = "APP_SHORTCUT",
                    packageName = "com.whatsapp",
                    customLabel = "WhatsApp"
                )
            ),
            folders = listOf(
                FolderEntity(
                    id = 10L,
                    folderName = "Finance",
                    pageIndex = 0,
                    row = 3,
                    column = 0,
                    memberPackagesCsv = "com.sbi.lotusintouch,com.phonepe.app"
                )
            )
        )

        val (restoredItems, restoredFolders) = organizer.deserializeLayoutSnapshot(snapshot)
        assertEquals(1, restoredItems.size)
        assertEquals("com.whatsapp", restoredItems.first().packageName)
        assertEquals(1, restoredFolders.size)
        assertEquals("Finance", restoredFolders.first().folderName)
    }

    @Test
    fun `ML folder notification badges aggregate unread counts and update dynamically on app launch`() {
        val sampleApps = listOf(
            AppInfo("Telegram", "org.telegram.messenger", categoryId = DefaultCategory.MESSAGING.id),
            AppInfo("Instagram", "com.instagram.android", categoryId = DefaultCategory.SOCIAL.id),
            AppInfo("Gmail", "com.google.android.gm", categoryId = DefaultCategory.PRODUCTIVITY.id)
        )
        com.example.engine.LauncherNotificationListenerService.syncDynamicBadgesWithInstalledApps(sampleApps)
        val countsBefore = com.example.engine.LauncherNotificationListenerService.packageNotificationCounts.value

        val socialFolderUnread = com.example.engine.LauncherNotificationListenerService.computeFolderUnreadCount(
            memberPackages = listOf("org.telegram.messenger", "com.instagram.android"),
            countsByPackage = countsBefore
        )
        assertTrue(socialFolderUnread > 0)

        // Launching Telegram clears its unread badge and dynamically reduces the folder's unread count
        com.example.engine.LauncherNotificationListenerService.clearPackageNotificationsOnLaunch("org.telegram.messenger")
        val countsAfter = com.example.engine.LauncherNotificationListenerService.packageNotificationCounts.value
        val socialFolderUnreadAfter = com.example.engine.LauncherNotificationListenerService.computeFolderUnreadCount(
            memberPackages = listOf("org.telegram.messenger", "com.instagram.android"),
            countsByPackage = countsAfter
        )
        assertTrue(socialFolderUnreadAfter < socialFolderUnread)
    }

    @Test
    fun `expanded 2x2 folders support both 2x2 Bento and 3x3 Dense display styles on 5-column grid`() {
        val organizer = AiHomeOrganizer()
        val sampleApps = listOf(
            AppInfo("ChatGPT", "com.openai.chatgpt", categoryId = DefaultCategory.PRODUCTIVITY.id),
            AppInfo("Gemini", "com.google.android.apps.bard", categoryId = DefaultCategory.PRODUCTIVITY.id),
            AppInfo("Claude", "com.anthropic.claude", categoryId = DefaultCategory.PRODUCTIVITY.id),
            AppInfo("Copilot", "com.microsoft.copilot", categoryId = DefaultCategory.PRODUCTIVITY.id),
            AppInfo("SBI YONO", "com.sbi.lotusintouch", categoryId = DefaultCategory.BANKING.id),
            AppInfo("Google Pay", "com.google.android.apps.nbu.paisa.user", categoryId = DefaultCategory.UPI_PAYMENTS.id),
            AppInfo("Paytm", "net.one97.paytm", categoryId = DefaultCategory.UPI_PAYMENTS.id),
            AppInfo("Instagram", "com.instagram.android", categoryId = DefaultCategory.SOCIAL.id),
            AppInfo("Facebook", "com.facebook.katana", categoryId = DefaultCategory.SOCIAL.id),
            AppInfo("Gmail", "com.google.android.gm", categoryId = DefaultCategory.INTERNET.id, isFavorite = true),
            AppInfo("Brave", "com.brave.browser", categoryId = DefaultCategory.BROWSERS.id, isFavorite = true)
        )

        val preview = organizer.generateProposedLayout(
            mode = ArrangementMode.AI_ORGANIZED,
            installedApps = sampleApps,
            existingItems = emptyList(),
            existingFolders = emptyList(),
            gridColumns = 5,
            gridRows = 7,
            dockCapacity = 4
        )

        val folderItemsOnPage0 = preview.proposedItems.filter { it.itemType == HomeItemType.FOLDER && it.pageIndex == 0 }
        assertTrue(folderItemsOnPage0.isNotEmpty())
        assertTrue(folderItemsOnPage0.all { it.spanX == 2 && it.spanY == 2 })

        val styles = preview.proposedFolders.map { it.displayStyle }.toSet()
        assertTrue(com.example.domain.model.FolderDisplayStyle.BENTO_2X2 in styles)
        assertTrue(com.example.domain.model.FolderDisplayStyle.DENSE_3X3 in styles)
    }
}
