package com.example.domain.model

import android.content.Intent
import androidx.compose.ui.graphics.ImageBitmap

/**
 * The 18 required default categories in PixelCategory Launcher.
 */
enum class DefaultCategory(
    val id: String,
    val displayName: String,
    val iconName: String,
    val colorHex: String,
    val sortOrder: Int
) {
    ALL_APPS("cat_all", "All Apps", "apps", "#4285F4", 0),
    BROWSERS("cat_browsers", "Browsers", "public", "#0284C7", 1),
    INTERNET("cat_internet", "Internet", "wifi", "#0D9488", 2),
    GAMES("cat_games", "Games", "sports_esports", "#8B5CF6", 3),
    VIDEO_STREAMING("cat_video", "Video & Streaming", "play_circle", "#EF4444", 4),
    SOCIAL("cat_social", "Social", "groups", "#EC4899", 5),
    MESSAGING("cat_messaging", "Messaging", "chat", "#10B981", 6),
    BANKING("cat_banking", "Banking", "account_balance", "#2563EB", 7),
    UPI_PAYMENTS("cat_upi", "UPI & Payments", "payments", "#059669", 8),
    SHOPPING("cat_shopping", "Shopping", "shopping_bag", "#F59E0B", 9),
    PRODUCTIVITY("cat_productivity", "Productivity", "task_alt", "#3B82F6", 10),
    EDUCATION("cat_education", "Education", "school", "#6366F1", 11),
    PHOTOGRAPHY("cat_photography", "Photography", "photo_camera", "#D946EF", 12),
    MUSIC_AUDIO("cat_music", "Music & Audio", "headphones", "#14B8A6", 13),
    TECH_DEV("cat_tech", "Technology & Development", "code", "#64748B", 14),
    UTILITIES("cat_utilities", "Utilities", "build", "#F97316", 15),
    SYSTEM_APPS("cat_system", "System Apps", "settings", "#475569", 16),
    UNCATEGORIZED("cat_uncategorized", "Uncategorized", "help_outline", "#94A3B8", 17);

    companion object {
        fun fromDisplayName(name: String): DefaultCategory {
            return entries.firstOrNull { it.displayName.equals(name, ignoreCase = true) }
                ?: UNCATEGORIZED
        }

        fun fromId(id: String): DefaultCategory? {
            return entries.firstOrNull { it.id == id }
        }
    }
}

enum class InstallationState {
    INSTALLED,
    DISABLED,
    UNINSTALLED
}

/**
 * Complete domain representation of a launchable application.
 */
data class AppInfo(
    val appName: String,
    val packageName: String,
    val activityName: String = "",
    val appIcon: ImageBitmap? = null,
    val launchIntent: Intent? = null,
    val isSystemApp: Boolean = false,
    val categoryId: String = DefaultCategory.UNCATEGORIZED.id,
    val categoryName: String = DefaultCategory.UNCATEGORIZED.displayName,
    val allCategoryIds: List<String> = listOf(DefaultCategory.UNCATEGORIZED.id),
    val categoryConfidence: Float = 0.0f,
    val suggestedCategoryId: String? = null,
    val suggestedCategoryName: String? = null,
    val installationState: InstallationState = InstallationState.INSTALLED,
    val isUserOverridden: Boolean = false,
    val isFavorite: Boolean = false,
    val launchCount: Int = 0,
    val firstInstallTime: Long = 0L,
    val lastUpdateTime: Long = 0L,
    val customIconKey: String? = null
)

data class CategoryModel(
    val id: String,
    val name: String,
    val iconName: String,
    val colorHex: String,
    val sortOrder: Int,
    val isVisibleInDrawer: Boolean = true,
    val isSystemDefault: Boolean = true,
    val appCount: Int = 0
)

enum class HomeItemType {
    APP_SHORTCUT,
    FOLDER,
    WIDGET
}

data class HomeScreenItemModel(
    val id: Long = 0L,
    val pageIndex: Int = 0,
    val row: Int = 0,
    val column: Int = 0,
    val spanX: Int = 1,
    val spanY: Int = 1,
    val isDockItem: Boolean = false,
    val dockIndex: Int = 0,
    val itemType: HomeItemType = HomeItemType.APP_SHORTCUT,
    val packageName: String? = null,
    val customLabel: String? = null,
    val folderId: Long? = null,
    val appWidgetId: Int = -1,
    val widgetProviderName: String? = null,
    val isPinnedByUser: Boolean = false
)

data class FolderModel(
    val id: Long = 0L,
    val folderName: String,
    val pageIndex: Int = 0,
    val row: Int = 0,
    val column: Int = 0,
    val colorHex: String = "#4285F4",
    val memberPackages: List<String> = emptyList()
)

enum class ArrangementMode(val displayName: String, val description: String) {
    PIXEL_DEFAULT(
        "Pixel Default",
        "Clean Pixel At-a-Glance top space with favorite essentials in the bottom rows and dock."
    ),
    AI_ORGANIZED(
        "AI Organized",
        "Groups related categories into smart folders, prioritizes top favorites in the dock, and preserves widgets."
    ),
    CUSTOM_LAYOUT(
        "Custom Layout",
        "Your manually placed shortcuts, folders, and widgets."
    )
}

data class ProposedLayoutPreview(
    val mode: ArrangementMode,
    val pagesCount: Int,
    val proposedItems: List<HomeScreenItemModel>,
    val proposedFolders: List<FolderModel>,
    val dockPackages: List<String>,
    val preservedWidgetsCount: Int,
    val summaryPoints: List<String>
)

enum class IconShapeOption(val id: String, val displayName: String) {
    CIRCLE("CIRCLE", "Pixel Circle"),
    SQUIRCLE("SQUIRCLE", "Smooth Squircle"),
    ROUNDED_SQUARE("ROUNDED_SQUARE", "Rounded Square"),
    TEARDROP("TEARDROP", "Pixel Teardrop"),
    SHARP("SHARP", "Modern Tile");

    companion object {
        fun fromId(id: String): IconShapeOption =
            entries.firstOrNull { it.id == id } ?: CIRCLE
    }
}

enum class DrawerSortMode(val id: String, val displayName: String) {
    ALPHABETICAL_ASC("ALPHA_ASC", "Name (A → Z)"),
    ALPHABETICAL_DESC("ALPHA_DESC", "Name (Z → A)"),
    MOST_USED("MOST_USED", "Most Launched"),
    AI_CONFIDENCE("AI_CONFIDENCE", "AI Confidence"),
    RECENTLY_INSTALLED("RECENT", "Recently Installed")
}

data class ContactSearchResult(
    val contactId: String,
    val displayName: String,
    val phoneNumber: String,
    val photoUri: String? = null
)

data class LocalFileSearchResult(
    val id: Long,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val contentUri: String,
    val relativePath: String
)

data class WebSearchSuggestion(
    val query: String,
    val url: String,
    val searchEngineName: String
)

data class IconPackInfo(
    val packageName: String,
    val name: String,
    val iconCount: Int,
    val isApplied: Boolean = false
)
