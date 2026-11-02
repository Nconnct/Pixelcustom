package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.example.domain.model.AppInfo
import com.example.domain.model.CategoryModel
import com.example.domain.model.DefaultCategory
import com.example.domain.model.FolderModel
import com.example.domain.model.HomeItemType
import com.example.domain.model.HomeScreenItemModel
import com.example.domain.model.IconShapeOption
import com.example.engine.WidgetHostController
import com.example.ui.components.FrostedGlassBox
import com.example.ui.components.LauncherAppTile
import com.example.ui.components.LauncherFolderTile
import com.example.ui.components.PixelAtAGlanceHeader
import com.example.ui.components.PixelBottomSearchBar
import com.example.ui.viewmodel.ActiveOverlaySheet
import com.example.ui.viewmodel.LauncherUiState
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreenContent(
    state: LauncherUiState,
    widgetHostController: WidgetHostController,
    onOpenOverlay: (ActiveOverlaySheet) -> Unit,
    onLaunchApp: (AppInfo) -> Unit,
    onOpenFolder: (Long) -> Unit,
    onCloseFolder: () -> Unit,
    onOpenAppContextMenu: (AppInfo, HomeScreenItemModel?) -> Unit,
    onDismissAppContextMenu: () -> Unit,
    onAssignCategory: (String, String, Boolean) -> Unit,
    onToggleMultiCategory: (String, String, Boolean) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onPinToHome: (String, Boolean) -> Unit,
    onRemoveHomeItem: (Long) -> Unit,
    onMoveHomeItem: (Long, Int, Int, Int, Boolean) -> Unit,
    onCreateSmartFolder: (String, List<String>, String, Int) -> Unit,
    onUpdateFolder: (Long, String, List<String>) -> Unit,
    onDeleteFolder: (Long) -> Unit,
    onCustomizeAppAppearance: (String, String?, String?) -> Unit,
    onUndoLayout: () -> Unit,
    onRequestDefaultHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings = state.settings
    val pageCount = maxOf(2, settings.homePageCount)
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { pageCount })
    val coroutineScope = rememberCoroutineScope()

    var movingHomeItem by remember { mutableStateOf<HomeScreenItemModel?>(null) }
    var showHomeLongPressDialog by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }

    val appsByPkg = remember(state.installedApps) {
        state.installedApps.associateBy { it.packageName }
    }
    val foldersById = remember(state.folders) {
        state.folders.associateBy { it.id }
    }
    val iconShape = remember(settings.iconShape) {
        IconShapeOption.fromId(settings.iconShape)
    }

    val categorizedCount = remember(state.installedApps) {
        state.installedApps.count { it.categoryId != DefaultCategory.UNCATEGORIZED.id }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .pointerInput(settings.swipeUpAction, settings.swipeDownAction) {
                var accumulatedDy = 0f
                detectVerticalDragGestures(
                    onDragStart = { accumulatedDy = 0f },
                    onVerticalDrag = { _, dragAmount -> accumulatedDy += dragAmount },
                    onDragEnd = {
                        if (accumulatedDy < -90f) {
                            when (settings.swipeUpAction) {
                                "OPEN_SEARCH" -> onOpenOverlay(ActiveOverlaySheet.UNIFIED_SEARCH)
                                "AI_ORGANIZE_PREVIEW" -> onOpenOverlay(ActiveOverlaySheet.AI_ORGANIZER_PREVIEW)
                                else -> onOpenOverlay(ActiveOverlaySheet.APP_DRAWER)
                            }
                        } else if (accumulatedDy > 90f) {
                            when (settings.swipeDownAction) {
                                "OPEN_DRAWER" -> onOpenOverlay(ActiveOverlaySheet.APP_DRAWER)
                                else -> onOpenOverlay(ActiveOverlaySheet.UNIFIED_SEARCH)
                            }
                        }
                    }
                )
            }
            .pointerInput(settings.doubleTapAction, settings.longPressAction) {
                detectTapGestures(
                    onDoubleTap = {
                        when (settings.doubleTapAction) {
                            "OPEN_SEARCH" -> onOpenOverlay(ActiveOverlaySheet.UNIFIED_SEARCH)
                            "OPEN_DRAWER" -> onOpenOverlay(ActiveOverlaySheet.APP_DRAWER)
                            else -> onOpenOverlay(ActiveOverlaySheet.AI_ORGANIZER_PREVIEW)
                        }
                    },
                    onLongPress = {
                        showHomeLongPressDialog = true
                    }
                )
            }
            .testTag("home_screen_root")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            // 1. Top Pixel At-a-Glance Header
            if (settings.showAtAGlanceWidget) {
                PixelAtAGlanceHeader(
                    totalAppsCount = state.installedApps.size,
                    categorizedCount = categorizedCount,
                    isDefaultHome = state.isDefaultHomeLauncher,
                    onOpenAiOrganizer = { onOpenOverlay(ActiveOverlaySheet.AI_ORGANIZER_PREVIEW) },
                    onRequestDefaultHome = onRequestDefaultHome,
                    onOpenSettings = { onOpenOverlay(ActiveOverlaySheet.SETTINGS_CUSTOMIZATION) }
                )
            }

            // Active Move Mode Banner or Undo Layout Strip
            if (movingHomeItem != null) {
                FrostedGlassBox(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Tap any highlighted grid cell or dock slot to move '${movingHomeItem?.customLabel ?: "Item"}'",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { movingHomeItem = null }) {
                            Text("Cancel")
                        }
                    }
                }
            } else if (state.layoutSnapshots.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    FrostedGlassBox(
                        shape = RoundedCornerShape(14.dp),
                        alphaOverride = 0.55f,
                        modifier = Modifier
                            .clickable(onClick = onUndoLayout)
                            .testTag("home_undo_layout_chip")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Undo,
                                contentDescription = "Undo layout",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Undo AI Layout",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // 2. Multi-Page Workspace Grid
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("home_workspace_pager")
            ) { pageIndex ->
                val pageItems = remember(state.homeItems, pageIndex) {
                    state.homeItems.filter { !it.isDockItem && it.pageIndex == pageIndex }
                }
                val widgetsOnPage = remember(pageItems) {
                    pageItems.filter { it.itemType == HomeItemType.WIDGET }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Render any active widgets on this page
                    for (widgetItem in widgetsOnPage) {
                        FrostedGlassBox(
                            shape = RoundedCornerShape(20.dp),
                            alphaOverride = 0.65f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(104.dp)
                                .padding(vertical = 4.dp)
                                .testTag("home_widget_${widgetItem.id}")
                        ) {
                            val hostView = remember(widgetItem.appWidgetId) {
                                widgetHostController.createWidgetHostView(widgetItem.appWidgetId)
                            }
                            Box(modifier = Modifier.fillMaxSize()) {
                                if (hostView != null) {
                                    AndroidView(
                                        factory = { hostView },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Widgets,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(28.dp)
                                            )
                                            Column {
                                                Text(
                                                    text = widgetItem.customLabel ?: "Android Widget",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = widgetItem.widgetProviderName ?: "AppWidgetHost Active",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        IconButton(onClick = { onRemoveHomeItem(widgetItem.id) }) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Remove Widget",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Render Workspace Grid Rows x Columns
                    val startRow = if (pageIndex == 0 && settings.showAtAGlanceWidget) 1 else 0
                    for (r in startRow until settings.homeGridRows) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (c in 0 until settings.homeGridColumns) {
                                val cellItem = pageItems.firstOrNull {
                                    it.row == r && it.column == c && it.itemType != HomeItemType.WIDGET
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxSize()
                                        .padding(2.dp)
                                        .then(
                                            if (movingHomeItem != null) {
                                                Modifier
                                                    .clip(RoundedCornerShape(14.dp))
                                                    .border(
                                                        1.dp,
                                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                                        RoundedCornerShape(14.dp)
                                                    )
                                                    .clickable {
                                                        val target = movingHomeItem
                                                        if (target != null) {
                                                            onMoveHomeItem(target.id, pageIndex, r, c, false)
                                                            movingHomeItem = null
                                                        }
                                                    }
                                            } else {
                                                Modifier
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    when (cellItem?.itemType) {
                                        HomeItemType.APP_SHORTCUT -> {
                                            val app = cellItem.packageName?.let { appsByPkg[it] }
                                            if (app != null) {
                                                LauncherAppTile(
                                                    app = app,
                                                    iconSizeDp = settings.homeIconSizeDp.dp,
                                                    iconScale = settings.iconScale,
                                                    iconShape = iconShape,
                                                    showLabel = settings.showHomeIconLabels,
                                                    onClick = {
                                                        if (movingHomeItem != null) {
                                                            onMoveHomeItem(movingHomeItem!!.id, pageIndex, r, c, false)
                                                            movingHomeItem = null
                                                        } else {
                                                            onLaunchApp(app)
                                                        }
                                                    },
                                                    onLongClick = {
                                                        onOpenAppContextMenu(app, cellItem)
                                                    }
                                                )
                                            }
                                        }

                                        HomeItemType.FOLDER -> {
                                            val folder = cellItem.folderId?.let { foldersById[it] }
                                            if (folder != null) {
                                                val memberApps = folder.memberPackages.mapNotNull { appsByPkg[it] }
                                                LauncherFolderTile(
                                                    folder = folder,
                                                    memberApps = memberApps,
                                                    iconSizeDp = settings.homeIconSizeDp.dp,
                                                    showLabel = settings.showHomeIconLabels,
                                                    onClick = {
                                                        if (movingHomeItem != null) {
                                                            onMoveHomeItem(movingHomeItem!!.id, pageIndex, r, c, false)
                                                            movingHomeItem = null
                                                        } else {
                                                            onOpenFolder(folder.id)
                                                        }
                                                    },
                                                    onLongClick = {
                                                        onOpenFolder(folder.id)
                                                    }
                                                )
                                            }
                                        }

                                        else -> Unit
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Page Indicator Dots
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until pageCount) {
                    val isSelected = pagerState.currentPage == i
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(width = if (isSelected) 20.dp else 7.dp, height = 7.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) Color.White else Color.White.copy(alpha = 0.38f)
                            )
                            .clickable {
                                coroutineScope.launch { pagerState.animateScrollToPage(i) }
                            }
                    )
                }
            }

            // 4. Pinned Frosted Glass Dock
            if (settings.showDock) {
                val dockItems = remember(state.homeItems) {
                    state.homeItems.filter { it.isDockItem }.sortedBy { it.dockIndex }
                }

                FrostedGlassBox(
                    shape = RoundedCornerShape(28.dp),
                    alphaOverride = settings.dockTransparency,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("home_frosted_dock")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (dockSlot in 0 until settings.dockIconCount) {
                            val dockItem = dockItems.firstOrNull { it.dockIndex == dockSlot }
                                ?: dockItems.getOrNull(dockSlot)
                            val dockApp = dockItem?.packageName?.let { appsByPkg[it] }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .then(
                                        if (movingHomeItem != null) {
                                            Modifier
                                                .clip(RoundedCornerShape(14.dp))
                                                .border(
                                                    1.dp,
                                                    MaterialTheme.colorScheme.primary,
                                                    RoundedCornerShape(14.dp)
                                                )
                                                .clickable {
                                                    val target = movingHomeItem
                                                    if (target != null) {
                                                        onMoveHomeItem(
                                                            target.id,
                                                            0,
                                                            settings.homeGridRows - 1,
                                                            dockSlot,
                                                            true
                                                        )
                                                        movingHomeItem = null
                                                    }
                                                }
                                        } else {
                                            Modifier
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (dockApp != null && dockItem != null) {
                                    LauncherAppTile(
                                        app = dockApp,
                                        iconSizeDp = settings.homeIconSizeDp.dp,
                                        iconScale = settings.iconScale,
                                        iconShape = iconShape,
                                        showLabel = false,
                                        onClick = {
                                            if (movingHomeItem != null) {
                                                onMoveHomeItem(
                                                    movingHomeItem!!.id,
                                                    0,
                                                    settings.homeGridRows - 1,
                                                    dockSlot,
                                                    true
                                                )
                                                movingHomeItem = null
                                            } else {
                                                onLaunchApp(dockApp)
                                            }
                                        },
                                        onLongClick = {
                                            onOpenAppContextMenu(dockApp, dockItem)
                                        }
                                    )
                                } else {
                                    IconButton(
                                        onClick = { onOpenOverlay(ActiveOverlaySheet.APP_DRAWER) },
                                        modifier = Modifier.size(settings.homeIconSizeDp.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Add app to dock",
                                            tint = Color.White.copy(alpha = 0.55f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. Bottom Pixel Search Bar
            if (settings.showBottomSearchBar) {
                PixelBottomSearchBar(
                    onSearchBarClick = { onOpenOverlay(ActiveOverlaySheet.UNIFIED_SEARCH) },
                    onOpenDrawerClick = { onOpenOverlay(ActiveOverlaySheet.APP_DRAWER) },
                    onOpenAiOrganizerClick = { onOpenOverlay(ActiveOverlaySheet.AI_ORGANIZER_PREVIEW) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // --- Opened Frosted Folder Modal ---
        val activeFolder = state.openedFolder
        if (activeFolder != null) {
            FolderDetailModal(
                folder = activeFolder,
                allApps = state.installedApps,
                iconShape = iconShape,
                onDismiss = onCloseFolder,
                onLaunchApp = { app ->
                    onCloseFolder()
                    onLaunchApp(app)
                },
                onUpdateFolder = onUpdateFolder,
                onDeleteFolder = onDeleteFolder
            )
        }

        // --- App Long-Press Context Menu Modal ---
        val contextApp = state.contextMenuApp
        if (contextApp != null) {
            AppLongPressContextDialog(
                app = contextApp,
                homeItem = state.contextMenuHomeItem,
                categories = state.categories,
                onDismiss = onDismissAppContextMenu,
                onAssignPrimaryCategory = { catId ->
                    onAssignCategory(contextApp.packageName, catId, false)
                },
                onToggleAdditionalCategory = { catId, assigned ->
                    onToggleMultiCategory(contextApp.packageName, catId, assigned)
                },
                onToggleFavorite = { onToggleFavorite(contextApp.packageName) },
                onPinToHome = { toDock -> onPinToHome(contextApp.packageName, toDock) },
                onStartGridMove = { item ->
                    movingHomeItem = item
                    onDismissAppContextMenu()
                },
                onRemoveFromHome = { itemId -> onRemoveHomeItem(itemId) },
                onSaveCustomAppearance = { label, iconKey ->
                    onCustomizeAppAppearance(contextApp.packageName, label, iconKey)
                }
            )
        }

        // --- Empty Workspace Long-Press Action Menu ---
        if (showHomeLongPressDialog) {
            AlertDialog(
                onDismissRequest = { showHomeLongPressDialog = false },
                title = { Text("Home Screen Options") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                showHomeLongPressDialog = false
                                onOpenOverlay(ActiveOverlaySheet.AI_ORGANIZER_PREVIEW)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("menu_ai_auto_organize")
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("AI Auto-Arrange Home Screen")
                        }
                        OutlinedButton(
                            onClick = {
                                showHomeLongPressDialog = false
                                showCreateFolderDialog = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("menu_create_folder")
                        ) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Create New Smart Folder")
                        }
                        OutlinedButton(
                            onClick = {
                                showHomeLongPressDialog = false
                                onOpenOverlay(ActiveOverlaySheet.WIDGET_PICKER)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("menu_add_widget")
                        ) {
                            Icon(Icons.Default.Widgets, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Widgets & AppWidgetHost")
                        }
                        OutlinedButton(
                            onClick = {
                                showHomeLongPressDialog = false
                                onOpenOverlay(ActiveOverlaySheet.SETTINGS_CUSTOMIZATION)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("menu_launcher_settings")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Home & Launcher Settings")
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showHomeLongPressDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }

        if (showCreateFolderDialog) {
            CreateFolderModal(
                allApps = state.installedApps,
                currentPage = pagerState.currentPage,
                onDismiss = { showCreateFolderDialog = false },
                onCreate = { name, pkgs ->
                    onCreateSmartFolder(name, pkgs, "#4285F4", pagerState.currentPage)
                    showCreateFolderDialog = false
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FolderDetailModal(
    folder: FolderModel,
    allApps: List<AppInfo>,
    iconShape: IconShapeOption,
    onDismiss: () -> Unit,
    onLaunchApp: (AppInfo) -> Unit,
    onUpdateFolder: (Long, String, List<String>) -> Unit,
    onDeleteFolder: (Long) -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var editedName by remember(folder.folderName) { mutableStateOf(folder.folderName) }
    var selectedPackages by remember(folder.memberPackages) {
        mutableStateOf(folder.memberPackages.toSet())
    }

    val appsMap = remember(allApps) { allApps.associateBy { it.packageName } }
    val memberApps = remember(folder.memberPackages, appsMap) {
        folder.memberPackages.mapNotNull { appsMap[it] }
    }

    Dialog(onDismissRequest = onDismiss) {
        FrostedGlassBox(
            shape = RoundedCornerShape(28.dp),
            alphaOverride = 0.90f,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .testTag("folder_detail_modal")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isEditing) {
                        OutlinedTextField(
                            value = editedName,
                            onValueChange = { editedName = it },
                            label = { Text("Folder Name") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Text(
                            text = folder.folderName,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Row {
                        IconButton(onClick = { isEditing = !isEditing }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Folder",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = { onDeleteFolder(folder.id) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Folder",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (!isEditing) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        maxItemsInEachRow = 3
                    ) {
                        for (app in memberApps) {
                            LauncherAppTile(
                                app = app,
                                iconSizeDp = 52.dp,
                                iconScale = 1.0f,
                                iconShape = iconShape,
                                showLabel = true,
                                onClick = { onLaunchApp(app) },
                                onLongClick = {}
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Select apps in this folder:",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (app in allApps) {
                            val selected = app.packageName in selectedPackages
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    selectedPackages = if (selected) {
                                        selectedPackages - app.packageName
                                    } else {
                                        selectedPackages + app.packageName
                                    }
                                },
                                label = { Text(app.appName) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            onUpdateFolder(folder.id, editedName, selectedPackages.toList())
                            isEditing = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save Folder")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateFolderModal(
    allApps: List<AppInfo>,
    currentPage: Int,
    onDismiss: () -> Unit,
    onCreate: (String, List<String>) -> Unit
) {
    var folderName by remember { mutableStateOf("Smart Folder") }
    var chosenPackages by remember {
        mutableStateOf(allApps.take(3).map { it.packageName }.toSet())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Folder on Page ${currentPage + 1}") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Folder Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text("Choose member apps:", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (app in allApps) {
                        val isSelected = app.packageName in chosenPackages
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                chosenPackages = if (isSelected) {
                                    chosenPackages - app.packageName
                                } else {
                                    chosenPackages + app.packageName
                                }
                            },
                            label = { Text(app.appName) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(folderName, chosenPackages.toList()) },
                enabled = chosenPackages.isNotEmpty()
            ) {
                Text("Create Folder")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppLongPressContextDialog(
    app: AppInfo,
    homeItem: HomeScreenItemModel?,
    categories: List<CategoryModel>,
    onDismiss: () -> Unit,
    onAssignPrimaryCategory: (String) -> Unit,
    onToggleAdditionalCategory: (String, Boolean) -> Unit,
    onToggleFavorite: () -> Unit,
    onPinToHome: (Boolean) -> Unit,
    onStartGridMove: (HomeScreenItemModel) -> Unit,
    onRemoveFromHome: (Long) -> Unit,
    onSaveCustomAppearance: (String?, String?) -> Unit
) {
    val context = LocalContext.current
    var customLabel by remember(app.appName) { mutableStateOf(app.appName) }
    var customIconDrawable by remember(app.customIconKey) { mutableStateOf(app.customIconKey.orEmpty()) }
    var showCustomizeFields by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(app.appName, fontWeight = FontWeight.Bold)
                    Text(
                        text = "${app.categoryName} • AI Confidence ${(app.categoryConfidence * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (app.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Toggle Favorite",
                        tint = if (app.isFavorite) Color(0xFFFBBC04) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Quick Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onPinToHome(false) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PushPin, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add to Home")
                    }
                    OutlinedButton(
                        onClick = { onPinToHome(true) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Pin to Dock")
                    }
                }

                if (homeItem != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onStartGridMove(homeItem) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.OpenWith, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Move on Grid")
                        }
                        OutlinedButton(
                            onClick = { onRemoveFromHome(homeItem.id) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Remove Shortcut")
                        }
                    }
                }

                // System App Info & Customize
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", app.packageName, null)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                            }
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("App Info")
                    }
                    OutlinedButton(
                        onClick = { showCustomizeFields = !showCustomizeFields },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Custom Icon/Name")
                    }
                }

                if (showCustomizeFields) {
                    OutlinedTextField(
                        value = customLabel,
                        onValueChange = { customLabel = it },
                        label = { Text("Custom App Label") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = customIconDrawable,
                        onValueChange = { customIconDrawable = it },
                        label = { Text("Icon Pack Drawable Override (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            onSaveCustomAppearance(customLabel, customIconDrawable)
                            showCustomizeFields = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Apply Custom Label / Icon")
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Primary Category (Overrides AI & Trains Offline Memory):",
                    style = MaterialTheme.typography.labelLarge
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (cat in categories.filter { it.id != DefaultCategory.ALL_APPS.id }) {
                        val isPrimary = app.categoryId == cat.id
                        FilterChip(
                            selected = isPrimary,
                            onClick = { onAssignPrimaryCategory(cat.id) },
                            label = { Text(cat.name) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Also Show in Secondary Categories:",
                    style = MaterialTheme.typography.labelLarge
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (cat in categories.filter { it.id != DefaultCategory.ALL_APPS.id && it.id != app.categoryId }) {
                        val isAssigned = cat.id in app.allCategoryIds
                        FilterChip(
                            selected = isAssigned,
                            onClick = { onToggleAdditionalCategory(cat.id, !isAssigned) },
                            label = { Text("+ ${cat.name}") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}
