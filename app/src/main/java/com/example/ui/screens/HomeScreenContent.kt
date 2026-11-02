package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Widgets
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
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
import com.example.engine.LauncherNotificationListenerService
import com.example.engine.WidgetHostController
import com.example.ui.components.FrostedGlassBox
import com.example.ui.components.LauncherAppTile
import com.example.ui.components.LauncherFolderTile
import com.example.ui.components.PixelAtAGlanceHeader
import com.example.ui.components.PixelBottomSearchBar
import com.example.ui.viewmodel.ActiveOverlaySheet
import com.example.ui.viewmodel.LauncherUiState
import kotlin.math.abs
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreenContent(
    state: LauncherUiState,
    widgetHostController: WidgetHostController,
    movingHomeItem: HomeScreenItemModel?,
    onCancelMoveMode: () -> Unit,
    onOpenOverlay: (ActiveOverlaySheet) -> Unit,
    onLaunchApp: (AppInfo) -> Unit,
    onOpenFolder: (Long) -> Unit,
    onCloseFolder: () -> Unit,
    onOpenAppContextMenu: (AppInfo, HomeScreenItemModel?) -> Unit,
    onRemoveHomeItem: (Long) -> Unit,
    onMoveHomeItem: (Long, Int, Int, Int, Boolean) -> Unit,
    onCreateSmartFolder: (String, List<String>, String, Int) -> Unit,
    onUpdateFolder: (Long, String, List<String>) -> Unit,
    onDeleteFolder: (Long) -> Unit,
    onUndoLayout: () -> Unit,
    onRequestDefaultHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings = state.settings
    val maxPopulatedPage = remember(state.homeItems) {
        (state.homeItems.filter { !it.isDockItem }.maxOfOrNull { it.pageIndex } ?: 0) + 1
    }
    val pageCount = maxOf(3, settings.homePageCount, maxPopulatedPage)
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { pageCount })
    val coroutineScope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val view = LocalView.current
    val density = LocalDensity.current

    val verticalSwipeThresholdPx = remember(density) { with(density) { 42.dp.toPx() } }
    val axisLockSlopPx = remember(density) { with(density) { 14.dp.toPx() } }

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
            .pointerInput(
                settings.swipeUpAction,
                settings.swipeDownAction,
                state.openedFolder,
                showHomeLongPressDialog,
                showCreateFolderDialog
            ) {
                if (state.openedFolder != null || showHomeLongPressDialog || showCreateFolderDialog) {
                    return@pointerInput
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var totalDx = 0f
                    var totalDy = 0f
                    var lockedHorizontal = false
                    var gestureTriggered = false

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id }
                            ?: event.changes.firstOrNull()
                            ?: break
                        if (!change.pressed) break

                        val dx = change.position.x - change.previousPosition.x
                        val dy = change.position.y - change.previousPosition.y
                        totalDx += dx
                        totalDy += dy

                        if (!lockedHorizontal && !gestureTriggered) {
                            if (abs(totalDx) > abs(totalDy) * 1.15f && abs(totalDx) > axisLockSlopPx) {
                                // Horizontal swipe: let HorizontalPager handle left/right page navigation
                                lockedHorizontal = true
                            } else if (abs(totalDy) > abs(totalDx) * 1.15f && abs(totalDy) >= verticalSwipeThresholdPx) {
                                gestureTriggered = true
                                change.consume()
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                if (totalDy < 0f) {
                                    // Swipe Up -> Open App Drawer (default)
                                    when (settings.swipeUpAction) {
                                        "OPEN_SEARCH" -> onOpenOverlay(ActiveOverlaySheet.UNIFIED_SEARCH)
                                        "AI_ORGANIZE_PREVIEW" -> onOpenOverlay(ActiveOverlaySheet.AI_ORGANIZER_PREVIEW)
                                        else -> onOpenOverlay(ActiveOverlaySheet.APP_DRAWER)
                                    }
                                } else {
                                    // Swipe Down -> Open Unified Search (default)
                                    when (settings.swipeDownAction) {
                                        "OPEN_DRAWER" -> onOpenOverlay(ActiveOverlaySheet.APP_DRAWER)
                                        else -> onOpenOverlay(ActiveOverlaySheet.UNIFIED_SEARCH)
                                    }
                                }
                            }
                        } else if (gestureTriggered) {
                            change.consume()
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
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

            // Active Move Mode Banner
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
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Tap any highlighted slot to place '${movingHomeItem.customLabel ?: "Item"}'",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onCancelMoveMode) {
                            Text("Cancel")
                        }
                    }
                }
            }

            // 2. 3-Page Horizontal Home Screen Workspace Grid (Page 1: Smart Folders, Pages 2 & 3: Extra Apps)
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("home_workspace_pager")
            ) { pageIndex ->
                val rawPageItems = remember(state.homeItems, pageIndex) {
                    state.homeItems.filter { !it.isDockItem && it.pageIndex == pageIndex }
                }

                // Fallback extra apps for Pages 2 and 3 if database hasn't populated them yet
                val effectivePageItems = remember(rawPageItems, state.installedApps, state.homeItems, pageIndex, settings.homeGridColumns) {
                    if (rawPageItems.isNotEmpty() || pageIndex == 0 || state.installedApps.isEmpty()) {
                        rawPageItems
                    } else {
                        val dockPkgs = state.homeItems.filter { it.isDockItem }.mapNotNullTo(mutableSetOf()) { it.packageName }
                        val candidateApps = state.installedApps.filter { it.packageName !in dockPkgs }
                        val cols = settings.homeGridColumns.coerceIn(3, 6)
                        val perPage = cols * 2
                        val slice = if (pageIndex == 1) {
                            candidateApps.take(perPage)
                        } else {
                            candidateApps.drop(perPage).take(perPage)
                        }
                        slice.mapIndexed { idx, app ->
                            HomeScreenItemModel(
                                id = -1000L - (pageIndex * 100L + idx),
                                pageIndex = pageIndex,
                                row = idx / cols,
                                column = idx % cols,
                                isDockItem = false,
                                itemType = HomeItemType.APP_SHORTCUT,
                                packageName = app.packageName,
                                customLabel = app.appName
                            )
                        }
                    }
                }

                val widgetsOnPage = remember(effectivePageItems) {
                    effectivePageItems.filter { it.itemType == HomeItemType.WIDGET }
                }
                val nonWidgetItems = remember(effectivePageItems) {
                    effectivePageItems
                        .filter { it.itemType != HomeItemType.WIDGET }
                        .sortedWith(compareBy<HomeScreenItemModel> { it.row }.thenBy { it.column })
                }

                val gridCols = settings.homeGridColumns.coerceIn(3, 6)
                val totalGridRows = settings.homeGridRows.coerceIn(4, 7)
                val startRow = if (pageIndex == 0 && settings.showAtAGlanceWidget) 1 else 0
                val displayRows = (totalGridRows - startRow).coerceAtLeast(3)

                // Deterministically map every non-widget item to a unique (row, col) cell on this page
                val cellItemMap = remember(nonWidgetItems, displayRows, gridCols, startRow) {
                    val map = mutableMapOf<Pair<Int, Int>, HomeScreenItemModel>()
                    val unplaced = mutableListOf<HomeScreenItemModel>()
                    for (item in nonWidgetItems) {
                        val adjRow = (item.row - startRow).coerceIn(0, displayRows - 1)
                        val adjCol = item.column.coerceIn(0, gridCols - 1)
                        val key = adjRow to adjCol
                        if (key !in map) {
                            map[key] = item
                        } else {
                            unplaced.add(item)
                        }
                    }
                    for (item in unplaced) {
                        outer@ for (r in 0 until displayRows) {
                            for (c in 0 until gridCols) {
                                val key = r to c
                                if (key !in map) {
                                    map[key] = item
                                    break@outer
                                }
                            }
                        }
                    }
                    map
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .testTag("home_page_$pageIndex"),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    for (widgetItem in widgetsOnPage) {
                        FrostedGlassBox(
                            shape = RoundedCornerShape(20.dp),
                            alphaOverride = 0.65f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(96.dp)
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
                                                modifier = Modifier.size(26.dp)
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

                    // Structured Uniform Grid (Rows x Columns) — Circular Frosted Folders & App Icons
                    for (r in 0 until displayRows) {
                        val dbRow = r + startRow
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (c in 0 until gridCols) {
                                val cellItem = cellItemMap[r to c]
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
                                                        onMoveHomeItem(movingHomeItem.id, pageIndex, dbRow, c, false)
                                                        onCancelMoveMode()
                                                    }
                                            } else {
                                                Modifier
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    when (cellItem?.itemType) {
                                        HomeItemType.FOLDER -> {
                                            val folder = cellItem.folderId?.let { foldersById[it] }
                                            if (folder != null) {
                                                val memberApps = folder.memberPackages.mapNotNull { appsByPkg[it] }
                                                val folderUnreadCount = LauncherNotificationListenerService.computeFolderUnreadCount(
                                                    memberPackages = folder.memberPackages,
                                                    countsByPackage = state.notificationCounts
                                                )
                                                LauncherFolderTile(
                                                    folder = folder,
                                                    memberApps = memberApps,
                                                    iconSizeDp = settings.homeIconSizeDp.dp,
                                                    iconScale = settings.iconScale,
                                                    showLabel = settings.showHomeIconLabels,
                                                    unreadNotificationCount = folderUnreadCount,
                                                    onClick = {
                                                        if (movingHomeItem != null) {
                                                            onMoveHomeItem(movingHomeItem.id, pageIndex, dbRow, c, false)
                                                            onCancelMoveMode()
                                                        } else {
                                                            onOpenFolder(folder.id)
                                                        }
                                                    },
                                                    onLongClick = {
                                                        if (movingHomeItem == null) {
                                                            onOpenFolder(folder.id)
                                                        }
                                                    },
                                                    onLaunchApp = onLaunchApp
                                                )
                                            }
                                        }

                                        HomeItemType.APP_SHORTCUT -> {
                                            val app = cellItem.packageName?.let { appsByPkg[it] }
                                            if (app != null) {
                                                val appUnread = state.notificationCounts[app.packageName] ?: 0
                                                LauncherAppTile(
                                                    app = app,
                                                    iconSizeDp = settings.homeIconSizeDp.dp,
                                                    iconScale = settings.iconScale,
                                                    iconShape = iconShape,
                                                    showLabel = settings.showHomeIconLabels,
                                                    notificationCount = appUnread,
                                                    labelColor = Color.White,
                                                    onClick = {
                                                        if (movingHomeItem != null) {
                                                            onMoveHomeItem(movingHomeItem.id, pageIndex, dbRow, c, false)
                                                            onCancelMoveMode()
                                                        } else {
                                                            onLaunchApp(app)
                                                        }
                                                    },
                                                    onLongClick = {
                                                        if (movingHomeItem == null) {
                                                            onOpenAppContextMenu(app, cellItem)
                                                        }
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

            // 3. Interactive Frosted Glass Page Indicator Dots (Page 1, Page 2, Page 3)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .testTag("home_page_indicator"),
                contentAlignment = Alignment.Center
            ) {
                FrostedGlassBox(
                    shape = CircleShape,
                    alphaOverride = 0.36f,
                    showBorder = false,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
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
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                        coroutineScope.launch { pagerState.animateScrollToPage(i) }
                                    }
                                    .testTag("page_indicator_dot_$i")
                            )
                        }
                    }
                }
            }

            // 4. Pinned Borderless Frosted Glass Dock (seamless soft glass surface above the bottom search bar)
            if (settings.showDock) {
                val dockItems = remember(state.homeItems) {
                    state.homeItems.filter { it.isDockItem }.sortedBy { it.dockIndex }
                }
                val fallbackDockApps = remember(state.installedApps, dockItems) {
                    if (dockItems.isEmpty()) state.installedApps.take(settings.dockIconCount) else emptyList()
                }

                FrostedGlassBox(
                    shape = RoundedCornerShape(28.dp),
                    alphaOverride = (settings.dockTransparency * 0.58f).coerceIn(0.22f, 0.48f),
                    showBorder = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .heightIn(min = 74.dp)
                        .testTag("home_frosted_dock")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 74.dp)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (dockSlot in 0 until settings.dockIconCount) {
                            val dockItem = dockItems.firstOrNull { it.dockIndex == dockSlot }
                                ?: dockItems.getOrNull(dockSlot)
                            val dockApp = dockItem?.packageName?.let { appsByPkg[it] }
                                ?: fallbackDockApps.getOrNull(dockSlot)

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
                                                    onMoveHomeItem(
                                                        movingHomeItem.id,
                                                        0,
                                                        settings.homeGridRows - 1,
                                                        dockSlot,
                                                        true
                                                    )
                                                    onCancelMoveMode()
                                                }
                                        } else {
                                            Modifier
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (dockApp != null) {
                                    val dockUnread = state.notificationCounts[dockApp.packageName] ?: 0
                                    LauncherAppTile(
                                        app = dockApp,
                                        iconSizeDp = settings.homeIconSizeDp.dp,
                                        iconScale = settings.iconScale,
                                        iconShape = iconShape,
                                        showLabel = false,
                                        notificationCount = dockUnread,
                                        onClick = {
                                            if (movingHomeItem != null) {
                                                onMoveHomeItem(
                                                    movingHomeItem.id,
                                                    0,
                                                    settings.homeGridRows - 1,
                                                    dockSlot,
                                                    true
                                                )
                                                onCancelMoveMode()
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
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }

        // --- Opened Frosted Folder Modal ---
        val activeFolder = state.openedFolder
        if (activeFolder != null) {
            FolderDetailModal(
                folder = activeFolder,
                allApps = state.installedApps,
                notificationCounts = state.notificationCounts,
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

        // --- Empty Workspace Long-Press Frosted Glass Action Menu ---
        if (showHomeLongPressDialog) {
            Dialog(onDismissRequest = { showHomeLongPressDialog = false }) {
                FrostedGlassBox(
                    shape = RoundedCornerShape(28.dp),
                    alphaOverride = 0.78f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Home Screen Options",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (!state.isDefaultHomeLauncher) {
                            Button(
                                onClick = {
                                    showHomeLongPressDialog = false
                                    onRequestDefaultHome()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("menu_set_default_home")
                            ) {
                                Icon(Icons.Default.Home, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Set as Default Home Launcher")
                            }
                        }
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
                            Text("AI Auto-Arrange & Undo Layout")
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
                            Text("Create Adaptive iOS Smart Folder")
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
                            Text("Home & Glass Settings")
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showHomeLongPressDialog = false }) {
                                Text("Close")
                            }
                        }
                    }
                }
            }
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
    notificationCounts: Map<String, Int>,
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
    val tierDescription = remember(selectedPackages.size, memberApps.size) {
        val count = if (isEditing) selectedPackages.size else memberApps.size
        when {
            count >= 6 -> "Large iOS App Library Folder • $count apps"
            count >= 4 -> "Medium Expanded iOS Folder • $count apps"
            else -> "Compact iOS Folder • $count apps"
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        FrostedGlassBox(
            shape = RoundedCornerShape(32.dp),
            alphaOverride = 0.78f,
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = folder.folderName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = tierDescription,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FrostedGlassBox(
                            shape = RoundedCornerShape(16.dp),
                            alphaOverride = 0.55f,
                            modifier = Modifier
                                .clickable { isEditing = !isEditing }
                                .testTag("folder_add_edit_apps_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isEditing) Icons.Default.Close else Icons.Default.Add,
                                    contentDescription = "Add or Edit Apps",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (isEditing) "Cancel" else "Add Apps",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
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

                Spacer(modifier = Modifier.height(14.dp))

                if (!isEditing) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        maxItemsInEachRow = 3
                    ) {
                        for (app in memberApps) {
                            LauncherAppTile(
                                app = app,
                                iconSizeDp = 54.dp,
                                iconScale = 1.0f,
                                iconShape = iconShape,
                                showLabel = true,
                                notificationCount = notificationCounts[app.packageName] ?: 0,
                                labelColor = MaterialTheme.colorScheme.onSurface,
                                onClick = { onLaunchApp(app) },
                                onLongClick = {}
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Select apps (4+ apps expands folder to Medium, 6+ apps expands to Large iOS Bento):",
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("save_folder_apps_button")
                    ) {
                        Text("Save & Resize Folder (${selectedPackages.size} apps)")
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
        mutableStateOf(allApps.take(4).map { it.packageName }.toSet())
    }

    Dialog(onDismissRequest = onDismiss) {
        FrostedGlassBox(
            shape = RoundedCornerShape(30.dp),
            alphaOverride = 0.80f,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Create Adaptive iOS Folder (Page ${currentPage + 1})",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Folder Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Choose member apps (${chosenPackages.size} selected — 4+ makes a bigger folder):",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onCreate(folderName, chosenPackages.toList()) },
                        enabled = chosenPackages.isNotEmpty()
                    ) {
                        Text("Create Folder")
                    }
                }
            }
        }
    }
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

    Dialog(onDismissRequest = onDismiss) {
        FrostedGlassBox(
            shape = RoundedCornerShape(30.dp),
            alphaOverride = 0.80f,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = app.appName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Done")
                    }
                }
            }
        }
    }
}
