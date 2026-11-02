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
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.domain.model.AppInfo
import com.example.domain.model.CategoryModel
import com.example.domain.model.DefaultCategory
import com.example.domain.model.FolderDisplayStyle
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
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private data class PlacedGridItem(
    val item: HomeScreenItemModel,
    val displayRow: Int,
    val col: Int,
    val spanX: Int,
    val spanY: Int
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreenContent(
    state: LauncherUiState,
    widgetHostController: WidgetHostController,
    movingHomeItem: HomeScreenItemModel?,
    onStartMoveHomeItem: (HomeScreenItemModel) -> Unit = {},
    onCancelMoveMode: () -> Unit,
    onOpenOverlay: (ActiveOverlaySheet) -> Unit,
    onLaunchApp: (AppInfo) -> Unit,
    onOpenFolder: (Long) -> Unit,
    onCloseFolder: () -> Unit,
    onOpenAppContextMenu: (AppInfo, HomeScreenItemModel?) -> Unit,
    onRemoveHomeItem: (Long) -> Unit,
    onMoveHomeItem: (Long, Int, Int, Int, Boolean, String?) -> Unit,
    onMergeHomeItems: (Long, Long, String?) -> Unit = { _, _, _ -> },
    onToggleFolderStyle: (Long) -> Unit = {},
    onResizeFolder: (Long, Int, Int, FolderDisplayStyle) -> Unit = { _, _, _, _ -> },
    onExtractAppFromFolder: (Long, String, Int?) -> Unit = { _, _, _ -> },
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
    val dragMoveThresholdPx = remember(density) { with(density) { 8.dp.toPx() } }
    val edgeScrollZonePx = remember(density) { with(density) { 36.dp.toPx() } }

    var showHomeLongPressDialog by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var openFolderInEditMode by remember { mutableStateOf(false) }

    // Hold-and-Swipe Real-Time Drag State & Floating Folder Resize Pill State
    var rootBoundsInRoot by remember { mutableStateOf(Rect.Zero) }
    var activeGridBoundsInRoot by remember { mutableStateOf(Rect.Zero) }
    var dockBoundsInRoot by remember { mutableStateOf(Rect.Zero) }
    var draggedItem by remember { mutableStateOf<HomeScreenItemModel?>(null) }
    var dragFingerInRoot by remember { mutableStateOf(Offset.Zero) }
    var dragTotalDistance by remember { mutableStateOf(0f) }
    var hoverGridCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var hoverDockSlot by remember { mutableStateOf<Int?>(null) }
    var mergeTargetItem by remember { mutableStateOf<HomeScreenItemModel?>(null) }
    var lastEdgePageTurnMs by remember { mutableLongStateOf(0L) }

    var folderQuickMenuTarget by remember { mutableStateOf<PlacedGridItem?>(null) }
    var folderQuickMenuBounds by remember { mutableStateOf(Rect.Zero) }
    var editingFolderSheetFolderId by remember { mutableStateOf<Long?>(null) }
    var editingFolderInitial1x1 by remember { mutableStateOf(false) }

    val latestDraggedItem by rememberUpdatedState(draggedItem)
    val latestFolderQuickMenu by rememberUpdatedState(folderQuickMenuTarget)
    val latestOpenedFolder by rememberUpdatedState(state.openedFolder)

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

    val gridCols = settings.homeGridColumns.coerceIn(3, 6)
    val totalGridRows = settings.homeGridRows.coerceIn(4, 7)

    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .onGloballyPositioned { rootBoundsInRoot = it.boundsInRoot() }
            .pointerInput(
                settings.swipeUpAction,
                settings.swipeDownAction,
                showHomeLongPressDialog,
                showCreateFolderDialog
            ) {
                if (showHomeLongPressDialog || showCreateFolderDialog) {
                    return@pointerInput
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val downTimeMs = System.currentTimeMillis()
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
                        // Never intercept pointer events when a folder modal, quick menu, or drag is active,
                        // or once a touch has been held long enough to be a long-press / drag (>= 360ms).
                        if (
                            latestOpenedFolder != null ||
                            latestDraggedItem != null ||
                            latestFolderQuickMenu != null ||
                            (!gestureTriggered && System.currentTimeMillis() - downTimeMs >= 360L)
                        ) {
                            break
                        }

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
                                    when (settings.swipeUpAction) {
                                        "OPEN_SEARCH" -> onOpenOverlay(ActiveOverlaySheet.UNIFIED_SEARCH)
                                        "AI_ORGANIZE_PREVIEW" -> onOpenOverlay(ActiveOverlaySheet.AI_ORGANIZER_PREVIEW)
                                        else -> onOpenOverlay(ActiveOverlaySheet.APP_DRAWER)
                                    }
                                } else {
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

            // Active Drag / Move Mode Banner
            val activeDragged = draggedItem
            if (activeDragged != null && dragTotalDistance > dragMoveThresholdPx) {
                FrostedGlassBox(
                    shape = RoundedCornerShape(18.dp),
                    alphaOverride = 0.56f,
                    showBorder = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val hintText = when {
                            mergeTargetItem != null -> {
                                val targetLabel = mergeTargetItem?.customLabel ?: "Folder"
                                "Release to merge into '$targetLabel'"
                            }
                            hoverDockSlot != null -> "Release to place in Dock slot ${(hoverDockSlot ?: 0) + 1}"
                            hoverGridCell != null -> {
                                val cell = hoverGridCell!!
                                "Release to place at Row ${cell.first + 1}, Col ${cell.second + 1} (Page ${pagerState.currentPage + 1})"
                            }
                            else -> "Hold & swipe to move, swap, or drop on an icon to merge"
                        }
                        Text(
                            text = hintText,
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else if (movingHomeItem != null) {
                FrostedGlassBox(
                    shape = RoundedCornerShape(16.dp),
                    showBorder = false,
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
                            text = "Tap any highlighted grid cell to place '${movingHomeItem.customLabel ?: "Item"}'",
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

            // 2. 3-Page Horizontal Home Screen Workspace Grid (5-Column Multi-Span Layout with 1x1 / 2x2 Folders & 1x1 Apps)
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = draggedItem == null,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("home_workspace_pager")
            ) { pageIndex ->
                val rawPageItems = remember(state.homeItems, pageIndex) {
                    state.homeItems.filter { !it.isDockItem && it.pageIndex == pageIndex }
                }

                // Fallback extra apps for Pages 2 and 3 if database hasn't populated them yet
                val effectivePageItems = remember(rawPageItems, state.installedApps, state.homeItems, pageIndex, gridCols) {
                    if (rawPageItems.isNotEmpty() || pageIndex == 0 || state.installedApps.isEmpty()) {
                        rawPageItems
                    } else {
                        val dockPkgs = state.homeItems.filter { it.isDockItem }.mapNotNullTo(mutableSetOf()) { it.packageName }
                        val candidateApps = state.installedApps.filter { it.packageName !in dockPkgs }
                        val perPage = gridCols * 2
                        val slice = if (pageIndex == 1) {
                            candidateApps.take(perPage)
                        } else {
                            candidateApps.drop(perPage).take(perPage)
                        }
                        slice.mapIndexed { idx, app ->
                            HomeScreenItemModel(
                                id = -1000L - (pageIndex * 100L + idx),
                                pageIndex = pageIndex,
                                row = idx / gridCols,
                                column = idx % gridCols,
                                spanX = 1,
                                spanY = 1,
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
                        .sortedWith(
                            compareByDescending<HomeScreenItemModel> { it.spanX * it.spanY }
                                .thenBy { it.row }
                                .thenBy { it.column }
                        )
                }

                val startRow = if (pageIndex == 0 && settings.showAtAGlanceWidget) 1 else 0
                val displayRows = (totalGridRows - startRow).coerceAtLeast(4)

                // Deterministically place multi-span (2x2 or 1x1 folders) and 1x1 app shortcuts at their user-chosen (row, col)
                val placedPageItems = remember(nonWidgetItems, displayRows, gridCols, startRow) {
                    val occupied = Array(displayRows) { BooleanArray(gridCols) }
                    val placed = mutableListOf<PlacedGridItem>()
                    val unplaced = mutableListOf<HomeScreenItemModel>()

                    fun canFit(r: Int, c: Int, sx: Int, sy: Int): Boolean {
                        if (r < 0 || c < 0 || r + sy > displayRows || c + sx > gridCols) return false
                        for (rr in r until (r + sy)) {
                            for (cc in c until (c + sx)) {
                                if (occupied[rr][cc]) return false
                            }
                        }
                        return true
                    }

                    fun markOccupied(r: Int, c: Int, sx: Int, sy: Int) {
                        for (rr in r until (r + sy)) {
                            for (cc in c until (c + sx)) {
                                occupied[rr][cc] = true
                            }
                        }
                    }

                    for (item in nonWidgetItems) {
                        val isFolder = item.itemType == HomeItemType.FOLDER
                        val sx = if (isFolder) item.spanX.coerceIn(1, gridCols) else 1
                        val sy = if (isFolder) item.spanY.coerceIn(1, displayRows) else 1
                        val desiredR = (item.row - startRow).coerceIn(0, (displayRows - sy).coerceAtLeast(0))
                        val desiredC = item.column.coerceIn(0, (gridCols - sx).coerceAtLeast(0))
                        if (canFit(desiredR, desiredC, sx, sy)) {
                            markOccupied(desiredR, desiredC, sx, sy)
                            placed.add(PlacedGridItem(item, desiredR, desiredC, sx, sy))
                        } else {
                            unplaced.add(item)
                        }
                    }

                    for (item in unplaced) {
                        val isFolder = item.itemType == HomeItemType.FOLDER
                        val sx = if (isFolder) item.spanX.coerceIn(1, gridCols) else 1
                        val sy = if (isFolder) item.spanY.coerceIn(1, displayRows) else 1
                        outer@ for (r in 0..(displayRows - sy).coerceAtLeast(0)) {
                            for (c in 0..(gridCols - sx).coerceAtLeast(0)) {
                                if (canFit(r, c, sx, sy)) {
                                    markOccupied(r, c, sx, sy)
                                    placed.add(PlacedGridItem(item, r, c, sx, sy))
                                    break@outer
                                }
                            }
                        }
                    }
                    placed
                }

                // Lookup map from any (displayRow, col) cell to the PlacedGridItem covering it
                val cellToPlacedItem = remember(placedPageItems) {
                    val map = mutableMapOf<Pair<Int, Int>, PlacedGridItem>()
                    for (p in placedPageItems) {
                        for (rr in p.displayRow until (p.displayRow + p.spanY)) {
                            for (cc in p.col until (p.col + p.spanX)) {
                                map[rr to cc] = p
                            }
                        }
                    }
                    map
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp, vertical = 2.dp)
                        .testTag("home_page_$pageIndex"),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    for (widgetItem in widgetsOnPage) {
                        FrostedGlassBox(
                            shape = RoundedCornerShape(20.dp),
                            alphaOverride = 0.65f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(92.dp)
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

                    // Multi-Span 2D Workspace Grid (Supports 1x1 Apps, 1x1 Compact Folders, and 2x2 Expanded Folders + Real-Time Drag & Drop)
                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .onGloballyPositioned { coords ->
                                if (pagerState.currentPage == pageIndex) {
                                    activeGridBoundsInRoot = coords.boundsInRoot()
                                }
                            }
                            .pointerInput(pageIndex) {
                                detectTapGestures(
                                    onTap = {
                                        if (folderQuickMenuTarget != null) {
                                            folderQuickMenuTarget = null
                                        }
                                    },
                                    onLongPress = {
                                        if (draggedItem == null && folderQuickMenuTarget == null) {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                            showHomeLongPressDialog = true
                                        }
                                    }
                                )
                            }
                    ) {
                        val cellWidth = maxWidth / gridCols
                        val cellHeight = maxHeight / displayRows

                        // 1. Render subtle drop target slots when movingHomeItem != null
                        if (movingHomeItem != null) {
                            for (r in 0 until displayRows) {
                                for (c in 0 until gridCols) {
                                    val dbRow = r + startRow
                                    Box(
                                        modifier = Modifier
                                            .offset(x = cellWidth * c, y = cellHeight * r)
                                            .size(width = cellWidth, height = cellHeight)
                                            .padding(3.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
                                            .clickable {
                                                onMoveHomeItem(
                                                    movingHomeItem.id,
                                                    pageIndex,
                                                    dbRow,
                                                    c,
                                                    false,
                                                    movingHomeItem.packageName
                                                )
                                                onCancelMoveMode()
                                            }
                                    )
                                }
                            }
                        }

                        // Highlight hovered cell/footprint during active hold-and-swipe drag on current page
                        val currentHoverCell = hoverGridCell
                        val activeDraggedItem = draggedItem
                        if (activeDraggedItem != null && pagerState.currentPage == pageIndex && currentHoverCell != null && mergeTargetItem == null) {
                            val isDraggedFolder = activeDraggedItem.itemType == HomeItemType.FOLDER
                            val dragSpanX = if (isDraggedFolder) activeDraggedItem.spanX.coerceIn(1, 2) else 1
                            val dragSpanY = if (isDraggedFolder) activeDraggedItem.spanY.coerceIn(1, 2) else 1
                            val clampedR = currentHoverCell.first.coerceIn(0, (displayRows - dragSpanY).coerceAtLeast(0))
                            val clampedC = currentHoverCell.second.coerceIn(0, (gridCols - dragSpanX).coerceAtLeast(0))
                            Box(
                                modifier = Modifier
                                    .offset(x = cellWidth * clampedC, y = cellHeight * clampedR)
                                    .size(width = cellWidth * dragSpanX, height = cellHeight * dragSpanY)
                                    .padding(4.dp)
                                    .clip(RoundedCornerShape(if (dragSpanX >= 2) 26.dp else 18.dp))
                                    .background(Color.White.copy(alpha = 0.24f))
                            )
                        }

                        // 2. Render each PlacedGridItem (1x1 App, 1x1 Compact Folder, or 2x2 Expanded Folder)
                        for (placed in placedPageItems) {
                            val cellItem = placed.item
                            val dbRow = placed.displayRow + startRow
                            val isBeingDragged = draggedItem?.id == cellItem.id && dragTotalDistance > dragMoveThresholdPx
                            val isMergeTarget = mergeTargetItem?.id == cellItem.id
                            var itemBoundsInRoot by remember(cellItem.id, placed.displayRow, placed.col, placed.spanX, placed.spanY) {
                                mutableStateOf(Rect.Zero)
                            }

                            Box(
                                modifier = Modifier
                                    .offset(x = cellWidth * placed.col, y = cellHeight * placed.displayRow)
                                    .size(width = cellWidth * placed.spanX, height = cellHeight * placed.spanY)
                                    .alpha(if (isBeingDragged) 0.25f else 1f)
                                    .scale(if (isMergeTarget) 1.08f else 1f)
                                    .then(
                                        if (isMergeTarget) {
                                            Modifier
                                                .padding(2.dp)
                                                .clip(RoundedCornerShape(26.dp))
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.36f))
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .onGloballyPositioned { coords ->
                                        itemBoundsInRoot = coords.boundsInRoot()
                                    }
                                    .pointerInput(cellItem.id, pageIndex, displayRows, gridCols, placed.spanX, placed.spanY) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { startOffset ->
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                                draggedItem = cellItem
                                                dragTotalDistance = 0f
                                                dragFingerInRoot = itemBoundsInRoot.topLeft + startOffset
                                                hoverGridCell = placed.displayRow to placed.col
                                                hoverDockSlot = null
                                                mergeTargetItem = null
                                                // Immediately show floating folder resize/style pill on long-press of a folder;
                                                // if user starts dragging, we dismiss the pill in onDrag.
                                                if (cellItem.itemType == HomeItemType.FOLDER) {
                                                    folderQuickMenuTarget = placed
                                                    folderQuickMenuBounds = itemBoundsInRoot
                                                } else {
                                                    folderQuickMenuTarget = null
                                                }
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragFingerInRoot += dragAmount
                                                dragTotalDistance += dragAmount.getDistance()
                                                if (dragTotalDistance > dragMoveThresholdPx && folderQuickMenuTarget != null) {
                                                    folderQuickMenuTarget = null
                                                }

                                                // Auto-scroll pager when dragging near left/right screen edges
                                                val now = System.currentTimeMillis()
                                                if (now - lastEdgePageTurnMs > 550L && rootBoundsInRoot.width > 0f) {
                                                    if (dragFingerInRoot.x - rootBoundsInRoot.left < edgeScrollZonePx && pagerState.currentPage > 0) {
                                                        lastEdgePageTurnMs = now
                                                        coroutineScope.launch {
                                                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                                        }
                                                    } else if (rootBoundsInRoot.right - dragFingerInRoot.x < edgeScrollZonePx && pagerState.currentPage < pageCount - 1) {
                                                        lastEdgePageTurnMs = now
                                                        coroutineScope.launch {
                                                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                                        }
                                                    }
                                                }

                                                // Check if finger is over the Dock
                                                if (
                                                    settings.showDock &&
                                                    cellItem.itemType == HomeItemType.APP_SHORTCUT &&
                                                    dockBoundsInRoot.width > 0f &&
                                                    dragFingerInRoot.y >= dockBoundsInRoot.top
                                                ) {
                                                    val relX = (dragFingerInRoot.x - dockBoundsInRoot.left)
                                                        .coerceIn(0f, dockBoundsInRoot.width - 1f)
                                                    val slot = ((relX / dockBoundsInRoot.width) * settings.dockIconCount)
                                                        .toInt()
                                                        .coerceIn(0, settings.dockIconCount - 1)
                                                    hoverDockSlot = slot
                                                    hoverGridCell = null
                                                    mergeTargetItem = null
                                                } else if (activeGridBoundsInRoot.width > 0f && activeGridBoundsInRoot.height > 0f) {
                                                    hoverDockSlot = null
                                                    val relX = (dragFingerInRoot.x - activeGridBoundsInRoot.left)
                                                        .coerceIn(0f, activeGridBoundsInRoot.width - 1f)
                                                    val relY = (dragFingerInRoot.y - activeGridBoundsInRoot.top)
                                                        .coerceIn(0f, activeGridBoundsInRoot.height - 1f)
                                                    val cWidthPx = activeGridBoundsInRoot.width / gridCols
                                                    val cHeightPx = activeGridBoundsInRoot.height / displayRows
                                                    val targetC = (relX / cWidthPx).toInt().coerceIn(0, gridCols - 1)
                                                    val targetR = (relY / cHeightPx).toInt().coerceIn(0, displayRows - 1)
                                                    hoverGridCell = targetR to targetC

                                                    // Check if hovering over another item to merge into a folder
                                                    val occupant = cellToPlacedItem[targetR to targetC]
                                                    if (
                                                        cellItem.itemType == HomeItemType.APP_SHORTCUT &&
                                                        occupant != null &&
                                                        occupant.item.id != cellItem.id &&
                                                        occupant.item.id > 0L
                                                    ) {
                                                        val occLeft = occupant.col * cWidthPx
                                                        val occTop = occupant.displayRow * cHeightPx
                                                        val occWidth = occupant.spanX * cWidthPx
                                                        val occHeight = occupant.spanY * cHeightPx
                                                        val normX = (relX - occLeft) / occWidth
                                                        val normY = (relY - occTop) / occHeight
                                                        if (normX in 0.22f..0.78f && normY in 0.22f..0.78f) {
                                                            mergeTargetItem = occupant.item
                                                        } else {
                                                            mergeTargetItem = null
                                                        }
                                                    } else {
                                                        mergeTargetItem = null
                                                    }
                                                }
                                            },
                                            onDragEnd = {
                                                val currentDragged = draggedItem
                                                val movedDistance = dragTotalDistance
                                                val targetMerge = mergeTargetItem
                                                val targetDock = hoverDockSlot
                                                val targetCell = hoverGridCell

                                                draggedItem = null
                                                dragTotalDistance = 0f
                                                hoverGridCell = null
                                                hoverDockSlot = null
                                                mergeTargetItem = null

                                                if (currentDragged == null) return@detectDragGesturesAfterLongPress

                                                if (movedDistance <= dragMoveThresholdPx) {
                                                    // Stationary long-press -> open App Context Menu or Floating Folder Resize Pill
                                                    when (currentDragged.itemType) {
                                                        HomeItemType.APP_SHORTCUT -> {
                                                            val app = currentDragged.packageName?.let { appsByPkg[it] }
                                                            if (app != null) {
                                                                onOpenAppContextMenu(app, currentDragged)
                                                            }
                                                        }
                                                        HomeItemType.FOLDER -> {
                                                            folderQuickMenuTarget = placed
                                                            folderQuickMenuBounds = itemBoundsInRoot
                                                        }
                                                        else -> Unit
                                                    }
                                                } else {
                                                    folderQuickMenuTarget = null
                                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                                    val destPage = pagerState.currentPage
                                                    val destStartRow = if (destPage == 0 && settings.showAtAGlanceWidget) 1 else 0
                                                    when {
                                                        targetMerge != null && targetMerge.id > 0L -> {
                                                            onMergeHomeItems(
                                                                currentDragged.id,
                                                                targetMerge.id,
                                                                currentDragged.packageName
                                                            )
                                                        }
                                                        targetDock != null -> {
                                                            onMoveHomeItem(
                                                                currentDragged.id,
                                                                0,
                                                                settings.homeGridRows - 1,
                                                                targetDock,
                                                                true,
                                                                currentDragged.packageName
                                                            )
                                                        }
                                                        targetCell != null -> {
                                                            onMoveHomeItem(
                                                                currentDragged.id,
                                                                destPage,
                                                                targetCell.first + destStartRow,
                                                                targetCell.second,
                                                                false,
                                                                currentDragged.packageName
                                                            )
                                                        }
                                                    }
                                                }
                                            },
                                            onDragCancel = {
                                                draggedItem = null
                                                dragTotalDistance = 0f
                                                hoverGridCell = null
                                                hoverDockSlot = null
                                                mergeTargetItem = null
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                when (cellItem.itemType) {
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
                                                isExpanded2x2 = placed.spanX >= 2 && placed.spanY >= 2,
                                                unreadNotificationCount = folderUnreadCount,
                                                enableInternalLongClick = false,
                                                onClick = {
                                                    if (folderQuickMenuTarget != null) {
                                                        folderQuickMenuTarget = null
                                                    } else if (movingHomeItem != null) {
                                                        onMoveHomeItem(
                                                            movingHomeItem.id,
                                                            pageIndex,
                                                            dbRow,
                                                            placed.col,
                                                            false,
                                                            movingHomeItem.packageName
                                                        )
                                                        onCancelMoveMode()
                                                    } else {
                                                        openFolderInEditMode = false
                                                        onOpenFolder(folder.id)
                                                    }
                                                },
                                                onLongClick = {},
                                                onLaunchApp = { app ->
                                                    if (folderQuickMenuTarget != null) {
                                                        folderQuickMenuTarget = null
                                                    } else {
                                                        onLaunchApp(app)
                                                    }
                                                }
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
                                                enableInternalLongClick = false,
                                                onClick = {
                                                    if (folderQuickMenuTarget != null) {
                                                        folderQuickMenuTarget = null
                                                    } else if (movingHomeItem != null) {
                                                        onMoveHomeItem(
                                                            movingHomeItem.id,
                                                            pageIndex,
                                                            dbRow,
                                                            placed.col,
                                                            false,
                                                            movingHomeItem.packageName
                                                        )
                                                        onCancelMoveMode()
                                                    } else {
                                                        onLaunchApp(app)
                                                    }
                                                },
                                                onLongClick = {}
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

            // 3. Interactive Frosted Glass Page Indicator Dots (Page 1, Page 2, Page 3)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
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
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (i in 0 until pageCount) {
                            val isSelected = pagerState.currentPage == i
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .size(width = if (isSelected) 18.dp else 6.dp, height = 6.dp)
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

            // 4. Compact Centered Frosted Glass Search Pill (matching screenshot above dock)
            if (settings.showBottomSearchBar) {
                PixelBottomSearchBar(
                    onSearchBarClick = { onOpenOverlay(ActiveOverlaySheet.UNIFIED_SEARCH) },
                    onOpenDrawerClick = { onOpenOverlay(ActiveOverlaySheet.APP_DRAWER) },
                    onOpenAiOrganizerClick = { onOpenOverlay(ActiveOverlaySheet.AI_ORGANIZER_PREVIEW) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // 5. Pinned Borderless Frosted Glass Dock
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
                        .heightIn(min = 72.dp)
                        .onGloballyPositioned { dockBoundsInRoot = it.boundsInRoot() }
                        .testTag("home_frosted_dock")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 72.dp)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (dockSlot in 0 until settings.dockIconCount) {
                            val dockItem = dockItems.firstOrNull { it.dockIndex == dockSlot }
                                ?: dockItems.getOrNull(dockSlot)
                            val dockApp = dockItem?.packageName?.let { appsByPkg[it] }
                                ?: fallbackDockApps.getOrNull(dockSlot)
                            val isHoveredDockSlot = hoverDockSlot == dockSlot && draggedItem != null
                            var dockSlotBoundsInRoot by remember(dockSlot) { mutableStateOf(Rect.Zero) }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .onGloballyPositioned { dockSlotBoundsInRoot = it.boundsInRoot() }
                                    .then(
                                        if (movingHomeItem != null || isHoveredDockSlot) {
                                            Modifier
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(
                                                    MaterialTheme.colorScheme.primary.copy(
                                                        alpha = if (isHoveredDockSlot) 0.32f else 0.20f
                                                    )
                                                )
                                                .clickable(enabled = movingHomeItem != null) {
                                                    if (movingHomeItem != null) {
                                                        onMoveHomeItem(
                                                            movingHomeItem.id,
                                                            0,
                                                            settings.homeGridRows - 1,
                                                            dockSlot,
                                                            true,
                                                            movingHomeItem.packageName
                                                        )
                                                        onCancelMoveMode()
                                                    }
                                                }
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .pointerInput(dockItem?.id ?: (-dockSlot.toLong()), dockApp?.packageName) {
                                        val effectiveDockItem = dockItem ?: dockApp?.let { app ->
                                            HomeScreenItemModel(
                                                id = -2000L - dockSlot,
                                                pageIndex = 0,
                                                row = settings.homeGridRows - 1,
                                                column = dockSlot,
                                                spanX = 1,
                                                spanY = 1,
                                                isDockItem = true,
                                                dockIndex = dockSlot,
                                                itemType = HomeItemType.APP_SHORTCUT,
                                                packageName = app.packageName,
                                                customLabel = app.appName
                                            )
                                        } ?: return@pointerInput
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { startOffset ->
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                                folderQuickMenuTarget = null
                                                draggedItem = effectiveDockItem
                                                dragTotalDistance = 0f
                                                dragFingerInRoot = dockSlotBoundsInRoot.topLeft + startOffset
                                                hoverDockSlot = dockSlot
                                                hoverGridCell = null
                                                mergeTargetItem = null
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragFingerInRoot += dragAmount
                                                dragTotalDistance += dragAmount.getDistance()
                                                if (dockBoundsInRoot.width > 0f && dragFingerInRoot.y >= dockBoundsInRoot.top) {
                                                    val relX = (dragFingerInRoot.x - dockBoundsInRoot.left)
                                                        .coerceIn(0f, dockBoundsInRoot.width - 1f)
                                                    hoverDockSlot = ((relX / dockBoundsInRoot.width) * settings.dockIconCount)
                                                        .toInt()
                                                        .coerceIn(0, settings.dockIconCount - 1)
                                                    hoverGridCell = null
                                                } else if (activeGridBoundsInRoot.width > 0f && activeGridBoundsInRoot.height > 0f) {
                                                    hoverDockSlot = null
                                                    val startRow = if (pagerState.currentPage == 0 && settings.showAtAGlanceWidget) 1 else 0
                                                    val displayRows = (totalGridRows - startRow).coerceAtLeast(4)
                                                    val relX = (dragFingerInRoot.x - activeGridBoundsInRoot.left)
                                                        .coerceIn(0f, activeGridBoundsInRoot.width - 1f)
                                                    val relY = (dragFingerInRoot.y - activeGridBoundsInRoot.top)
                                                        .coerceIn(0f, activeGridBoundsInRoot.height - 1f)
                                                    val targetC = ((relX / activeGridBoundsInRoot.width) * gridCols)
                                                        .toInt()
                                                        .coerceIn(0, gridCols - 1)
                                                    val targetR = ((relY / activeGridBoundsInRoot.height) * displayRows)
                                                        .toInt()
                                                        .coerceIn(0, displayRows - 1)
                                                    hoverGridCell = targetR to targetC
                                                }
                                            },
                                            onDragEnd = {
                                                val currentDragged = draggedItem
                                                val movedDistance = dragTotalDistance
                                                val targetDock = hoverDockSlot
                                                val targetCell = hoverGridCell

                                                draggedItem = null
                                                dragTotalDistance = 0f
                                                hoverGridCell = null
                                                hoverDockSlot = null
                                                mergeTargetItem = null

                                                if (currentDragged == null) return@detectDragGesturesAfterLongPress
                                                if (movedDistance <= dragMoveThresholdPx) {
                                                    if (dockApp != null) {
                                                        onOpenAppContextMenu(dockApp, currentDragged)
                                                    }
                                                } else {
                                                    val destPage = pagerState.currentPage
                                                    val destStartRow = if (destPage == 0 && settings.showAtAGlanceWidget) 1 else 0
                                                    when {
                                                        targetDock != null -> {
                                                            onMoveHomeItem(
                                                                currentDragged.id,
                                                                0,
                                                                settings.homeGridRows - 1,
                                                                targetDock,
                                                                true,
                                                                currentDragged.packageName
                                                            )
                                                        }
                                                        targetCell != null -> {
                                                            onMoveHomeItem(
                                                                currentDragged.id,
                                                                destPage,
                                                                targetCell.first + destStartRow,
                                                                targetCell.second,
                                                                false,
                                                                currentDragged.packageName
                                                            )
                                                        }
                                                    }
                                                }
                                            },
                                            onDragCancel = {
                                                draggedItem = null
                                                dragTotalDistance = 0f
                                                hoverGridCell = null
                                                hoverDockSlot = null
                                                mergeTargetItem = null
                                            }
                                        )
                                    },
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
                                        enableInternalLongClick = false,
                                        onClick = {
                                            if (folderQuickMenuTarget != null) {
                                                folderQuickMenuTarget = null
                                            } else if (movingHomeItem != null) {
                                                onMoveHomeItem(
                                                    movingHomeItem.id,
                                                    0,
                                                    settings.homeGridRows - 1,
                                                    dockSlot,
                                                    true,
                                                    movingHomeItem.packageName
                                                )
                                                onCancelMoveMode()
                                            } else {
                                                onLaunchApp(dockApp)
                                            }
                                        },
                                        onLongClick = {}
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
        }

        // --- Floating Folder Resize & Style Pill (On Long-Press of Any Folder) ---
        val activeQuickMenu = folderQuickMenuTarget
        if (activeQuickMenu != null && draggedItem == null) {
            val targetFolder = activeQuickMenu.item.folderId?.let { foldersById[it] }
            if (targetFolder != null) {
                val isCurrently1x1 = activeQuickMenu.spanX == 1 && activeQuickMenu.spanY == 1
                val isCurrentlyBento = !isCurrently1x1 && targetFolder.displayStyle == FolderDisplayStyle.BENTO_2X2
                val isCurrentlyDense = !isCurrently1x1 && targetFolder.displayStyle == FolderDisplayStyle.DENSE_3X3

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            folderQuickMenuTarget = null
                        }
                ) {
                    val pillWidthDp = 290.dp
                    val pillHeightDp = 96.dp
                    val pillWidthPx = with(density) { pillWidthDp.toPx() }
                    val pillHeightPx = with(density) { pillHeightDp.toPx() }
                    val centerX = folderQuickMenuBounds.center.x - rootBoundsInRoot.left
                    val desiredX = (centerX - pillWidthPx / 2f)
                        .coerceIn(with(density) { 12.dp.toPx() }, (rootBoundsInRoot.width - pillWidthPx - with(density) { 12.dp.toPx() }).coerceAtLeast(12f))
                    val aboveY = folderQuickMenuBounds.top - rootBoundsInRoot.top - pillHeightPx - with(density) { 10.dp.toPx() }
                    val belowY = folderQuickMenuBounds.bottom - rootBoundsInRoot.top + with(density) { 10.dp.toPx() }
                    val desiredY = if (aboveY >= with(density) { 48.dp.toPx() }) aboveY else belowY

                    FrostedGlassBox(
                        shape = RoundedCornerShape(24.dp),
                        alphaOverride = 0.82f,
                        showBorder = false,
                        modifier = Modifier
                            .offset { IntOffset(desiredX.roundToInt(), desiredY.roundToInt()) }
                            .width(pillWidthDp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {}
                            .testTag("folder_floating_resize_pill")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Row 1: Folder Size & Style Options (Regular 1x1, Enlarged 2x2 Bento, XXL 2x2 Dense)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FolderStylePillChip(
                                    label = "Regular",
                                    selected = isCurrently1x1,
                                    onClick = {
                                        onResizeFolder(targetFolder.id, 1, 1, targetFolder.displayStyle)
                                        folderQuickMenuTarget = null
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("resize_folder_1x1_chip")
                                )
                                FolderStylePillChip(
                                    label = "Enlarged",
                                    selected = isCurrentlyBento,
                                    onClick = {
                                        onResizeFolder(targetFolder.id, 2, 2, FolderDisplayStyle.BENTO_2X2)
                                        folderQuickMenuTarget = null
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("resize_folder_bento_chip")
                                )
                                FolderStylePillChip(
                                    label = "XXL",
                                    selected = isCurrentlyDense,
                                    onClick = {
                                        onResizeFolder(targetFolder.id, 2, 2, FolderDisplayStyle.DENSE_3X3)
                                        folderQuickMenuTarget = null
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("resize_folder_dense_chip")
                                )
                            }

                            // Row 2: Quick Actions (Edit folder sheet, Move on Grid, Remove)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable {
                                            editingFolderInitial1x1 = isCurrently1x1
                                            editingFolderSheetFolderId = targetFolder.id
                                            folderQuickMenuTarget = null
                                        }
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                        .testTag("open_edit_folder_sheet_button"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit folder",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Edit folder",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White
                                    )
                                }

                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable {
                                            val itemToMove = activeQuickMenu.item
                                            folderQuickMenuTarget = null
                                            onStartMoveHomeItem(itemToMove)
                                        }
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.OpenWith,
                                        contentDescription = "Move Folder",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Move",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White
                                    )
                                }

                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable {
                                            folderQuickMenuTarget = null
                                            onDeleteFolder(targetFolder.id)
                                        }
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Folder",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Remove",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- Floating Drag Preview Under User's Finger ---
        val floatingDragged = draggedItem
        if (floatingDragged != null && dragTotalDistance > dragMoveThresholdPx) {
            val isExpandedFolderDrag = floatingDragged.itemType == HomeItemType.FOLDER &&
                floatingDragged.spanX >= 2 && floatingDragged.spanY >= 2
            val previewSizeDp = if (isExpandedFolderDrag) 118.dp else 64.dp
            val previewHalfPx = with(density) { (previewSizeDp / 2).toPx() }
            val localX = (dragFingerInRoot.x - rootBoundsInRoot.left - previewHalfPx).roundToInt()
            val localY = (dragFingerInRoot.y - rootBoundsInRoot.top - previewHalfPx).roundToInt()

            Box(
                modifier = Modifier
                    .offset { IntOffset(localX, localY) }
                    .size(previewSizeDp)
                    .scale(1.08f)
                    .alpha(0.92f)
            ) {
                when (floatingDragged.itemType) {
                    HomeItemType.FOLDER -> {
                        val folder = floatingDragged.folderId?.let { foldersById[it] }
                        if (folder != null) {
                            val memberApps = folder.memberPackages.mapNotNull { appsByPkg[it] }
                            LauncherFolderTile(
                                folder = folder,
                                memberApps = memberApps,
                                iconSizeDp = settings.homeIconSizeDp.dp,
                                iconScale = settings.iconScale,
                                showLabel = false,
                                isExpanded2x2 = isExpandedFolderDrag,
                                enableInternalLongClick = false,
                                onClick = {},
                                onLongClick = {}
                            )
                        }
                    }
                    HomeItemType.APP_SHORTCUT -> {
                        val app = floatingDragged.packageName?.let { appsByPkg[it] }
                        if (app != null) {
                            LauncherAppTile(
                                app = app,
                                iconSizeDp = settings.homeIconSizeDp.dp,
                                iconScale = settings.iconScale,
                                iconShape = iconShape,
                                showLabel = false,
                                enableInternalLongClick = false,
                                onClick = {},
                                onLongClick = {}
                            )
                        }
                    }
                    else -> Unit
                }
            }
        }

        // --- Full-Screen Borderless Opened Folder View (Matching "Tools" Reference Screenshot) ---
        val activeFolder = state.openedFolder
        if (activeFolder != null) {
            FolderDetailModal(
                folder = activeFolder,
                allApps = state.installedApps,
                notificationCounts = state.notificationCounts,
                iconShape = iconShape,
                initialEditing = openFolderInEditMode,
                onDismiss = {
                    openFolderInEditMode = false
                    onCloseFolder()
                },
                onLaunchApp = { app ->
                    openFolderInEditMode = false
                    onCloseFolder()
                    onLaunchApp(app)
                },
                onExtractAppToHome = { pkg ->
                    onExtractAppFromFolder(activeFolder.id, pkg, pagerState.currentPage)
                    openFolderInEditMode = false
                    onCloseFolder()
                },
                onOpenEditFolderSheet = {
                    val homeFolderItem = state.homeItems.firstOrNull {
                        it.itemType == HomeItemType.FOLDER && it.folderId == activeFolder.id
                    }
                    editingFolderInitial1x1 = homeFolderItem != null && homeFolderItem.spanX == 1 && homeFolderItem.spanY == 1
                    editingFolderSheetFolderId = activeFolder.id
                    openFolderInEditMode = false
                    onCloseFolder()
                },
                onUpdateFolder = onUpdateFolder
            )
        }

        // --- "Edit folder" Bottom Sheet Modal (Matching Xiaomi/HyperOS Reference Screenshot) ---
        val editingSheetFolder = editingFolderSheetFolderId?.let { foldersById[it] }
        if (editingSheetFolder != null) {
            EditFolderBottomSheet(
                folder = editingSheetFolder,
                allApps = state.installedApps,
                initialIs1x1 = editingFolderInitial1x1,
                iconSizeDp = settings.homeIconSizeDp.dp,
                iconScale = settings.iconScale,
                onDismiss = { editingFolderSheetFolderId = null },
                onConfirm = { updatedName, spanX, spanY, selectedStyle, updatedPackages ->
                    onUpdateFolder(editingSheetFolder.id, updatedName, updatedPackages)
                    onResizeFolder(editingSheetFolder.id, spanX, spanY, selectedStyle)
                    editingFolderSheetFolderId = null
                }
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
                        FilledTonalButton(
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
                        FilledTonalButton(
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
                            Text("Create 2×2 Smart Folder")
                        }
                        FilledTonalButton(
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
                        FilledTonalButton(
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

@Composable
private fun FolderStylePillChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(34.dp)
            .clip(CircleShape)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.White.copy(alpha = 0.14f)
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else Color.White,
            maxLines = 1
        )
    }
}

/**
 * Full-Screen Borderless Opened Folder View matching the user's "Tools" reference screenshot:
 * - Full-screen dimmed/blurred wallpaper backdrop with zero container card or border around the apps.
 * - Large white left-aligned folder title ("Tools") above the first column of icons; tapping the title
 *   enables inline renaming and optional app selection.
 * - Spacious 3-column circular app icon grid with zero text labels under icons.
 * - Holding and dragging any app icon outside the grid extracts it from the folder onto the Home Screen.
 * - Tapping anywhere on the empty background closes the folder.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FolderDetailModal(
    folder: FolderModel,
    allApps: List<AppInfo>,
    notificationCounts: Map<String, Int>,
    iconShape: IconShapeOption,
    initialEditing: Boolean = false,
    onDismiss: () -> Unit,
    onLaunchApp: (AppInfo) -> Unit,
    onExtractAppToHome: (String) -> Unit = {},
    onOpenEditFolderSheet: () -> Unit = {},
    onUpdateFolder: (Long, String, List<String>) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val view = LocalView.current
    val density = LocalDensity.current

    var isEditingTitle by remember(initialEditing) { mutableStateOf(initialEditing) }
    var showAppSelector by remember { mutableStateOf(false) }
    var editedName by remember(folder.folderName) { mutableStateOf(folder.folderName) }
    var selectedPackages by remember(folder.memberPackages) {
        mutableStateOf(folder.memberPackages.toSet())
    }

    var draggedFolderApp by remember { mutableStateOf<AppInfo?>(null) }
    var draggedFingerOffset by remember { mutableStateOf(Offset.Zero) }
    var draggedDistancePx by remember { mutableStateOf(0f) }
    var modalRootBounds by remember { mutableStateOf(Rect.Zero) }
    var gridBoundsInRoot by remember { mutableStateOf(Rect.Zero) }

    val extractThresholdPx = remember(density) { with(density) { 64.dp.toPx() } }

    val appsMap = remember(allApps) { allApps.associateBy { it.packageName } }
    val memberApps = remember(folder.memberPackages, appsMap) {
        folder.memberPackages.mapNotNull { appsMap[it] }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.22f))
                .onGloballyPositioned { modalRootBounds = it.boundsInRoot() }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    if (isEditingTitle || showAppSelector) {
                        onUpdateFolder(folder.id, editedName, selectedPackages.toList())
                        isEditingTitle = false
                        showAppSelector = false
                    } else {
                        onDismiss()
                    }
                }
                .testTag("folder_detail_modal")
        ) {
            // Drag-out hint banner at top when holding & dragging an icon inside the opened folder
            if (draggedFolderApp != null) {
                val isOutsideGrid = gridBoundsInRoot.width > 0f &&
                    (!gridBoundsInRoot.contains(draggedFingerOffset) || draggedDistancePx >= extractThresholdPx)
                FrostedGlassBox(
                    shape = CircleShape,
                    alphaOverride = 0.62f,
                    showBorder = false,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 56.dp, start = 24.dp, end = 24.dp)
                ) {
                    Text(
                        text = if (isOutsideGrid) {
                            "Release to place '${draggedFolderApp?.appName}' on Home Screen"
                        } else {
                            "Drag outside folder to move '${draggedFolderApp?.appName}' to Home Screen"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                    )
                }
            }

            // Center-Left Positioned Title + Spacious 3-Column Borderless Icon Grid (Matching "Tools" Screenshot)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .padding(horizontal = 28.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.Start
            ) {
                // Folder Title ("Tools") — Tap Title to Rename Inline
                if (isEditingTitle) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 8.dp, bottom = 24.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {},
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = editedName,
                            onValueChange = { editedName = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.headlineMedium.copy(
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Normal,
                                color = Color.White
                            ),
                            shape = RoundedCornerShape(20.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = Color.White,
                                focusedContainerColor = Color.White.copy(alpha = 0.16f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.12f),
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        FrostedGlassBox(
                            shape = CircleShape,
                            alphaOverride = 0.48f,
                            showBorder = false,
                            modifier = Modifier
                                .height(40.dp)
                                .clickable { showAppSelector = !showAppSelector }
                                .testTag("folder_add_edit_apps_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add or Remove Apps",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Apps",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White
                                )
                            }
                        }

                        FrostedGlassBox(
                            shape = CircleShape,
                            alphaOverride = 0.48f,
                            showBorder = false,
                            modifier = Modifier
                                .height(40.dp)
                                .clickable { onOpenEditFolderSheet() }
                                .testTag("folder_open_size_sheet_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GridView,
                                    contentDescription = "Folder Size",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Size",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White
                                )
                            }
                        }

                        FrostedGlassBox(
                            shape = CircleShape,
                            alphaOverride = 0.56f,
                            showBorder = false,
                            modifier = Modifier
                                .size(40.dp)
                                .clickable {
                                    onUpdateFolder(folder.id, editedName, selectedPackages.toList())
                                    isEditingTitle = false
                                    showAppSelector = false
                                }
                                .testTag("save_folder_apps_button")
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Save Folder",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = folder.folderName,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Normal,
                            letterSpacing = 0.2.sp
                        ),
                        color = Color.White,
                        textAlign = TextAlign.Start,
                        modifier = Modifier
                            .padding(start = 20.dp, bottom = 30.dp)
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                isEditingTitle = true
                            }
                            .testTag("folder_title_text")
                    )
                }

                if (!showAppSelector) {
                    // 3-Column Floating Circular App Grid Directly Over Wallpaper (Zero Container Box, Zero App Labels)
                    val rows = memberApps.chunked(3)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { gridBoundsInRoot = it.boundsInRoot() },
                        verticalArrangement = Arrangement.spacedBy(34.dp)
                    ) {
                        for (rowApps in rows) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                for (colIdx in 0..2) {
                                    val app = rowApps.getOrNull(colIdx)
                                    Box(
                                        modifier = Modifier.weight(1f),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (app != null) {
                                            var iconBoundsInRoot by remember(app.packageName) { mutableStateOf(Rect.Zero) }
                                            val isDraggingThis = draggedFolderApp?.packageName == app.packageName
                                            Box(
                                                modifier = Modifier
                                                    .alpha(if (isDraggingThis) 0.28f else 1f)
                                                    .onGloballyPositioned { iconBoundsInRoot = it.boundsInRoot() }
                                                    .pointerInput(app.packageName) {
                                                        detectDragGesturesAfterLongPress(
                                                            onDragStart = { startOffset ->
                                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                                                draggedFolderApp = app
                                                                draggedDistancePx = 0f
                                                                draggedFingerOffset = iconBoundsInRoot.topLeft + startOffset
                                                            },
                                                            onDrag = { change, dragAmount ->
                                                                change.consume()
                                                                draggedFingerOffset += dragAmount
                                                                draggedDistancePx += dragAmount.getDistance()
                                                            },
                                                            onDragEnd = {
                                                                val currentApp = draggedFolderApp
                                                                val finalOffset = draggedFingerOffset
                                                                val dist = draggedDistancePx
                                                                draggedFolderApp = null
                                                                draggedDistancePx = 0f
                                                                if (currentApp != null) {
                                                                    val isOutside = gridBoundsInRoot.width > 0f &&
                                                                        (!gridBoundsInRoot.contains(finalOffset) || dist >= extractThresholdPx)
                                                                    if (isOutside) {
                                                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                                                        onExtractAppToHome(currentApp.packageName)
                                                                    }
                                                                }
                                                            },
                                                            onDragCancel = {
                                                                draggedFolderApp = null
                                                                draggedDistancePx = 0f
                                                            }
                                                        )
                                                    }
                                            ) {
                                                LauncherAppTile(
                                                    app = app,
                                                    iconSizeDp = 62.dp,
                                                    iconScale = 1.0f,
                                                    iconShape = IconShapeOption.CIRCLE,
                                                    showLabel = false,
                                                    showCategoryBadge = false,
                                                    notificationCount = notificationCounts[app.packageName] ?: 0,
                                                    labelColor = Color.Transparent,
                                                    enableInternalLongClick = false,
                                                    onClick = { onLaunchApp(app) },
                                                    onLongClick = {}
                                                )
                                            }
                                        } else {
                                            Spacer(modifier = Modifier.size(62.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Optional App Picker Grid when "+ Apps" is toggled during title edit
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {},
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        maxItemsInEachRow = 4
                    ) {
                        for (app in allApps) {
                            val selected = app.packageName in selectedPackages
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(
                                        if (selected) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
                                        } else {
                                            Color.Transparent
                                        }
                                    )
                                    .padding(4.dp)
                            ) {
                                LauncherAppTile(
                                    app = app,
                                    iconSizeDp = 52.dp,
                                    iconScale = if (selected) 1.0f else 0.86f,
                                    iconShape = IconShapeOption.CIRCLE,
                                    showLabel = false,
                                    showCategoryBadge = false,
                                    enableInternalLongClick = false,
                                    onClick = {
                                        selectedPackages = if (selected) {
                                            selectedPackages - app.packageName
                                        } else {
                                            selectedPackages + app.packageName
                                        }
                                    },
                                    onLongClick = {}
                                )
                            }
                        }
                    }
                }
            }

            // Floating icon preview when dragging an app out of the opened folder
            val activeDraggedApp = draggedFolderApp
            if (activeDraggedApp != null) {
                val previewHalfPx = with(density) { 34.dp.toPx() }
                val localX = (draggedFingerOffset.x - modalRootBounds.left - previewHalfPx).roundToInt()
                val localY = (draggedFingerOffset.y - modalRootBounds.top - previewHalfPx).roundToInt()
                Box(
                    modifier = Modifier
                        .offset { IntOffset(localX, localY) }
                        .size(68.dp)
                        .scale(1.1f)
                ) {
                    LauncherAppTile(
                        app = activeDraggedApp,
                        iconSizeDp = 62.dp,
                        iconScale = 1.0f,
                        iconShape = IconShapeOption.CIRCLE,
                        showLabel = false,
                        enableInternalLongClick = false,
                        onClick = {},
                        onLongClick = {}
                    )
                }
            }
        }
    }
}

private enum class FolderSizeSelection {
    REGULAR_1X1,
    ENLARGED_BENTO,
    XXL_DENSE
}

/**
 * "Edit folder" Bottom Sheet matching the Xiaomi/HyperOS reference screenshot:
 * - Dark rounded bottom sheet (#242424) with top handle pill
 * - Header row: ✕ (cancel), "Edit folder" centered title, ✓ (confirm)
 * - Rounded folder name input pill (#383838) showing e.g. "Tools"
 * - Purple-to-blue gradient preview banner with live LauncherFolderTile centered inside
 * - "Size" section with 3 schematic cards: Regular (1x1), Enlarged (2x2 Bento), XXL (2x2 Dense 3x3)
 *   highlighted with a #3B82F6 blue selection border and label
 * - "Today's recommendations" toggle row at the bottom
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditFolderBottomSheet(
    folder: FolderModel,
    allApps: List<AppInfo>,
    initialIs1x1: Boolean,
    iconSizeDp: androidx.compose.ui.unit.Dp,
    iconScale: Float,
    onDismiss: () -> Unit,
    onConfirm: (
        updatedName: String,
        spanX: Int,
        spanY: Int,
        displayStyle: FolderDisplayStyle,
        updatedPackages: List<String>
    ) -> Unit
) {
    var folderName by remember(folder.folderName) { mutableStateOf(folder.folderName) }
    var selectedSize by remember(initialIs1x1, folder.displayStyle) {
        mutableStateOf(
            when {
                initialIs1x1 -> FolderSizeSelection.REGULAR_1X1
                folder.displayStyle == FolderDisplayStyle.DENSE_3X3 -> FolderSizeSelection.XXL_DENSE
                else -> FolderSizeSelection.ENLARGED_BENTO
            }
        )
    }
    var recommendationsEnabled by remember { mutableStateOf(true) }
    var showManageApps by remember { mutableStateOf(false) }
    var selectedPackages by remember(folder.memberPackages) {
        mutableStateOf(folder.memberPackages.toSet())
    }

    val appsMap = remember(allApps) { allApps.associateBy { it.packageName } }
    val memberApps = remember(selectedPackages, appsMap) {
        selectedPackages.mapNotNull { appsMap[it] }
    }

    val previewStyle = when (selectedSize) {
        FolderSizeSelection.REGULAR_1X1 -> folder.displayStyle
        FolderSizeSelection.ENLARGED_BENTO -> FolderDisplayStyle.BENTO_2X2
        FolderSizeSelection.XXL_DENSE -> FolderDisplayStyle.DENSE_3X3
    }
    val previewFolder = remember(folder, folderName, previewStyle, selectedPackages) {
        val styleSuffix = if (previewStyle == FolderDisplayStyle.DENSE_3X3) "DENSE" else "BENTO"
        folder.copy(
            folderName = folderName,
            colorHex = "${folder.cleanColorHex}:$styleSuffix",
            memberPackages = selectedPackages.toList()
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.54f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() }
                .testTag("edit_folder_bottom_sheet"),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .background(Color(0xFF242424))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {}
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top drag handle bar
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp, bottom = 12.dp)
                        .width(42.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF6E6E6E))
                )

                // Header row: ✕ (Close), "Edit folder", ✓ (Confirm)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("edit_folder_cancel_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel Edit Folder",
                            tint = Color(0xFFD4D4D8),
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Text(
                        text = "Edit folder",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = Color.White
                    )

                    IconButton(
                        onClick = {
                            val (sx, sy) = if (selectedSize == FolderSizeSelection.REGULAR_1X1) {
                                1 to 1
                            } else {
                                2 to 2
                            }
                            onConfirm(
                                folderName.ifBlank { folder.folderName },
                                sx,
                                sy,
                                previewStyle,
                                selectedPackages.toList()
                            )
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("edit_folder_confirm_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Save Folder Changes",
                            tint = Color(0xFFD4D4D8),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                // Folder Name Input Pill ("Tools")
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color.White
                    ),
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color(0xFF3B82F6),
                        focusedContainerColor = Color(0xFF383838),
                        unfocusedContainerColor = Color(0xFF383838),
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 18.dp)
                        .testTag("edit_folder_name_input")
                )

                // Live Purple-Blue Gradient Folder Preview Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFFB469FF),
                                    Color(0xFF6B5BFF),
                                    Color(0xFF2855D8)
                                ),
                                start = Offset(0f, 0f),
                                end = Offset(900f, 600f)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val isExpandedPreview = selectedSize != FolderSizeSelection.REGULAR_1X1
                    val previewBoxSize = if (isExpandedPreview) 144.dp else 82.dp
                    Box(
                        modifier = Modifier.size(previewBoxSize),
                        contentAlignment = Alignment.Center
                    ) {
                        LauncherFolderTile(
                            folder = previewFolder,
                            memberApps = memberApps,
                            iconSizeDp = iconSizeDp,
                            iconScale = iconScale,
                            showLabel = false,
                            isExpanded2x2 = isExpandedPreview,
                            enableInternalLongClick = false,
                            onClick = {},
                            onLongClick = {}
                        )
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                // "Size" Section Header
                Text(
                    text = "Size",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = Color(0xFF8E8E93),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, bottom = 14.dp)
                )

                // 3 Visual Schematic Size Cards: Regular, Enlarged, XXL
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Top
                ) {
                    FolderSizeSchematicOption(
                        label = "Regular",
                        sizeType = FolderSizeSelection.REGULAR_1X1,
                        selected = selectedSize == FolderSizeSelection.REGULAR_1X1,
                        onClick = { selectedSize = FolderSizeSelection.REGULAR_1X1 },
                        testTag = "edit_folder_size_regular"
                    )
                    FolderSizeSchematicOption(
                        label = "Enlarged",
                        sizeType = FolderSizeSelection.ENLARGED_BENTO,
                        selected = selectedSize == FolderSizeSelection.ENLARGED_BENTO,
                        onClick = { selectedSize = FolderSizeSelection.ENLARGED_BENTO },
                        testTag = "edit_folder_size_enlarged"
                    )
                    FolderSizeSchematicOption(
                        label = "XXL",
                        sizeType = FolderSizeSelection.XXL_DENSE,
                        selected = selectedSize == FolderSizeSelection.XXL_DENSE,
                        onClick = { selectedSize = FolderSizeSelection.XXL_DENSE },
                        testTag = "edit_folder_size_xxl"
                    )
                }

                // Subtle horizontal divider
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.08f))
                )

                // "Today's recommendations" row with blue Switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Today's recommendations",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        color = Color.White
                    )
                    Switch(
                        checked = recommendationsEnabled,
                        onCheckedChange = { recommendationsEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF3B82F6),
                            uncheckedThumbColor = Color(0xFFB0B0B8),
                            uncheckedTrackColor = Color(0xFF3A3A3C)
                        ),
                        modifier = Modifier.testTag("edit_folder_recommendations_switch")
                    )
                }

                // Optional expandable "Manage folder apps" row
                TextButton(
                    onClick = { showManageApps = !showManageApps },
                    modifier = Modifier.testTag("edit_folder_toggle_apps_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = Color(0xFF3B82F6),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (showManageApps) "Hide app list" else "Add or remove folder apps (${selectedPackages.size})",
                        color = Color(0xFF3B82F6),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                if (showManageApps) {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (app in allApps) {
                            val isSelected = app.packageName in selectedPackages
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedPackages = if (isSelected) {
                                        selectedPackages - app.packageName
                                    } else {
                                        selectedPackages + app.packageName
                                    }
                                },
                                label = { Text(app.appName, maxLines = 1) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderSizeSchematicOption(
    label: String,
    sizeType: FolderSizeSelection,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val activeBlue = Color(0xFF3B82F6)
    val cardBg = Color(0xFF383838)
    val dotColor = Color(0xFF68686E)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .testTag(testTag)
    ) {
        // Outer selection ring
        Box(
            modifier = Modifier
                .size(104.dp)
                .then(
                    if (selected) {
                        Modifier.border(2.5.dp, activeBlue, RoundedCornerShape(26.dp))
                    } else {
                        Modifier
                    }
                )
                .padding(5.dp)
                .clip(RoundedCornerShape(21.dp))
                .background(cardBg),
            contentAlignment = Alignment.Center
        ) {
            when (sizeType) {
                FolderSizeSelection.REGULAR_1X1 -> {
                    // Centered 3x3 small grid schematic
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        for (r in 0..2) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                for (c in 0..2) {
                                    Box(
                                        modifier = Modifier
                                            .size(9.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(dotColor)
                                    )
                                }
                            }
                        }
                    }
                }
                FolderSizeSelection.ENLARGED_BENTO -> {
                    // 2x2 Bento schematic: 3 large squares + bottom-right 2x2 mini grid
                    Column(
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(dotColor)
                            )
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(dotColor)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(dotColor)
                            )
                            // Bottom-right 2x2 mini squares
                            Column(
                                modifier = Modifier.size(26.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(11.dp)
                                            .clip(RoundedCornerShape(3.5.dp))
                                            .background(dotColor)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(11.dp)
                                            .clip(RoundedCornerShape(3.5.dp))
                                            .background(dotColor)
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(11.dp)
                                            .clip(RoundedCornerShape(3.5.dp))
                                            .background(dotColor)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(11.dp)
                                            .clip(RoundedCornerShape(3.5.dp))
                                            .background(dotColor)
                                    )
                                }
                            }
                        }
                    }
                }
                FolderSizeSelection.XXL_DENSE -> {
                    // 3x3 Dense schematic: 8 medium squares + 9th-slot 2x2 mini grid
                    Column(
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        for (r in 0..2) {
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                for (c in 0..2) {
                                    if (r == 2 && c == 2) {
                                        Column(
                                            modifier = Modifier.size(17.dp),
                                            verticalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(7.dp)
                                                        .clip(RoundedCornerShape(2.dp))
                                                        .background(dotColor)
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .size(7.dp)
                                                        .clip(RoundedCornerShape(2.dp))
                                                        .background(dotColor)
                                                )
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(7.dp)
                                                        .clip(RoundedCornerShape(2.dp))
                                                        .background(dotColor)
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .size(7.dp)
                                                        .clip(RoundedCornerShape(2.dp))
                                                        .background(dotColor)
                                                )
                                            }
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(17.dp)
                                                .clip(RoundedCornerShape(5.dp))
                                                .background(dotColor)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
            ),
            color = if (selected) activeBlue else Color.White
        )
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
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Choose member apps (${chosenPackages.size} selected):",
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
                            border = null,
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
                    FilledTonalButton(
                        onClick = { onPinToHome(false) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PushPin, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add to Home")
                    }
                    FilledTonalButton(
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
                        FilledTonalButton(
                            onClick = { onStartGridMove(homeItem) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.OpenWith, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Move on Grid")
                        }
                        FilledTonalButton(
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
                    FilledTonalButton(
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
                    FilledTonalButton(
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
                        shape = RoundedCornerShape(18.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = customIconDrawable,
                        onValueChange = { customIconDrawable = it },
                        label = { Text("Icon Pack Drawable Override (optional)") },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        ),
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
                            border = null,
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
                            border = null,
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
