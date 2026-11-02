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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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
    onCancelMoveMode: () -> Unit,
    onOpenOverlay: (ActiveOverlaySheet) -> Unit,
    onLaunchApp: (AppInfo) -> Unit,
    onOpenFolder: (Long) -> Unit,
    onCloseFolder: () -> Unit,
    onOpenAppContextMenu: (AppInfo, HomeScreenItemModel?) -> Unit,
    onRemoveHomeItem: (Long) -> Unit,
    onMoveHomeItem: (Long, Int, Int, Int, Boolean) -> Unit,
    onMergeHomeItems: (Long, Long) -> Unit = { _, _ -> },
    onToggleFolderStyle: (Long) -> Unit = {},
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
    val dragMoveThresholdPx = remember(density) { with(density) { 10.dp.toPx() } }
    val edgeScrollZonePx = remember(density) { with(density) { 36.dp.toPx() } }

    var showHomeLongPressDialog by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }

    // Hold-and-Swipe Real-Time Drag State
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
                state.openedFolder,
                showHomeLongPressDialog,
                showCreateFolderDialog,
                draggedItem
            ) {
                if (state.openedFolder != null || showHomeLongPressDialog || showCreateFolderDialog || draggedItem != null) {
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
                        if (draggedItem != null) break

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
            .pointerInput(draggedItem) {
                if (draggedItem != null) return@pointerInput
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

            // Active Drag / Move Mode Banner
            val activeDragged = draggedItem
            if (activeDragged != null) {
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
                            hoverGridCell != null -> "Release to place on Page ${pagerState.currentPage + 1}"
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

            // 2. 3-Page Horizontal Home Screen Workspace Grid (5-Column Multi-Span Layout with 2x2 Folders & 1x1 Apps)
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

                // Deterministically place multi-span (2x2 folders) and 1x1 app shortcuts on this page without overlap
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
                        val sx = if (isFolder) item.spanX.coerceAtLeast(2).coerceAtMost(gridCols) else 1
                        val sy = if (isFolder) item.spanY.coerceAtLeast(2).coerceAtMost(displayRows) else 1
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
                        val sx = if (isFolder) item.spanX.coerceAtLeast(2).coerceAtMost(gridCols) else 1
                        val sy = if (isFolder) item.spanY.coerceAtLeast(2).coerceAtMost(displayRows) else 1
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

                    // Multi-Span 2D Workspace Grid (Supports 1x1 Apps and 2x2 Expanded Folders + Real-Time Drag & Drop)
                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .onGloballyPositioned { coords ->
                                if (pagerState.currentPage == pageIndex) {
                                    activeGridBoundsInRoot = coords.boundsInRoot()
                                }
                            }
                    ) {
                        val cellWidth = maxWidth / gridCols
                        val cellHeight = maxHeight / displayRows

                        // 1. Render subtle drop target slots when movingHomeItem != null or when dragging
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
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                                            .clickable {
                                                onMoveHomeItem(movingHomeItem.id, pageIndex, dbRow, c, false)
                                                onCancelMoveMode()
                                            }
                                    )
                                }
                            }
                        }

                        // Highlight hovered cell/footprint during active hold-and-swipe drag on current page
                        val currentHoverCell = hoverGridCell
                        if (draggedItem != null && pagerState.currentPage == pageIndex && currentHoverCell != null && mergeTargetItem == null) {
                            val isDraggedFolder = draggedItem?.itemType == HomeItemType.FOLDER
                            val dragSpanX = if (isDraggedFolder) 2 else 1
                            val dragSpanY = if (isDraggedFolder) 2 else 1
                            val clampedR = currentHoverCell.first.coerceIn(0, (displayRows - dragSpanY).coerceAtLeast(0))
                            val clampedC = currentHoverCell.second.coerceIn(0, (gridCols - dragSpanX).coerceAtLeast(0))
                            Box(
                                modifier = Modifier
                                    .offset(x = cellWidth * clampedC, y = cellHeight * clampedR)
                                    .size(width = cellWidth * dragSpanX, height = cellHeight * dragSpanY)
                                    .padding(4.dp)
                                    .clip(RoundedCornerShape(if (isDraggedFolder) 26.dp else 18.dp))
                                    .background(Color.White.copy(alpha = 0.22f))
                                    .border(
                                        width = 1.5.dp,
                                        color = Color.White.copy(alpha = 0.65f),
                                        shape = RoundedCornerShape(if (isDraggedFolder) 26.dp else 18.dp)
                                    )
                            )
                        }

                        // 2. Render each PlacedGridItem (1x1 App or 2x2 Expanded Folder)
                        for (placed in placedPageItems) {
                            val cellItem = placed.item
                            val dbRow = placed.displayRow + startRow
                            val isBeingDragged = draggedItem?.id == cellItem.id && dragTotalDistance > dragMoveThresholdPx
                            val isMergeTarget = mergeTargetItem?.id == cellItem.id
                            var itemBoundsInRoot by remember(cellItem.id) { mutableStateOf(Rect.Zero) }

                            Box(
                                modifier = Modifier
                                    .offset(x = cellWidth * placed.col, y = cellHeight * placed.displayRow)
                                    .size(width = cellWidth * placed.spanX, height = cellHeight * placed.spanY)
                                    .alpha(if (isBeingDragged) 0.28f else 1f)
                                    .scale(if (isMergeTarget) 1.07f else 1f)
                                    .then(
                                        if (isMergeTarget) {
                                            Modifier
                                                .padding(2.dp)
                                                .clip(RoundedCornerShape(26.dp))
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.32f))
                                                .border(
                                                    width = 2.dp,
                                                    color = Color.White.copy(alpha = 0.85f),
                                                    shape = RoundedCornerShape(26.dp)
                                                )
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .onGloballyPositioned { coords ->
                                        itemBoundsInRoot = coords.boundsInRoot()
                                    }
                                    .pointerInput(cellItem.id, pageIndex, displayRows, gridCols) {
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
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragFingerInRoot += dragAmount
                                                dragTotalDistance += dragAmount.getDistance()

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
                                                        if (normX in 0.18f..0.82f && normY in 0.18f..0.82f) {
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
                                                    // Stationary long-press -> open context menu or folder modal
                                                    when (currentDragged.itemType) {
                                                        HomeItemType.APP_SHORTCUT -> {
                                                            val app = currentDragged.packageName?.let { appsByPkg[it] }
                                                            if (app != null) {
                                                                onOpenAppContextMenu(app, currentDragged)
                                                            }
                                                        }
                                                        HomeItemType.FOLDER -> {
                                                            val fId = currentDragged.folderId
                                                            if (fId != null) {
                                                                onOpenFolder(fId)
                                                            }
                                                        }
                                                        else -> Unit
                                                    }
                                                } else if (currentDragged.id > 0L) {
                                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                                    val destPage = pagerState.currentPage
                                                    val destStartRow = if (destPage == 0 && settings.showAtAGlanceWidget) 1 else 0
                                                    when {
                                                        targetMerge != null && targetMerge.id > 0L -> {
                                                            onMergeHomeItems(currentDragged.id, targetMerge.id)
                                                        }
                                                        targetDock != null -> {
                                                            onMoveHomeItem(
                                                                currentDragged.id,
                                                                0,
                                                                settings.homeGridRows - 1,
                                                                targetDock,
                                                                true
                                                            )
                                                        }
                                                        targetCell != null -> {
                                                            onMoveHomeItem(
                                                                currentDragged.id,
                                                                destPage,
                                                                targetCell.first + destStartRow,
                                                                targetCell.second,
                                                                false
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
                                                onClick = {
                                                    if (movingHomeItem != null) {
                                                        onMoveHomeItem(movingHomeItem.id, pageIndex, dbRow, placed.col, false)
                                                        onCancelMoveMode()
                                                    } else {
                                                        onOpenFolder(folder.id)
                                                    }
                                                },
                                                onLongClick = {
                                                    if (movingHomeItem == null && draggedItem == null) {
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
                                                        onMoveHomeItem(movingHomeItem.id, pageIndex, dbRow, placed.col, false)
                                                        onCancelMoveMode()
                                                    } else {
                                                        onLaunchApp(app)
                                                    }
                                                },
                                                onLongClick = {
                                                    if (movingHomeItem == null && draggedItem == null) {
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
                                                            true
                                                        )
                                                        onCancelMoveMode()
                                                    }
                                                }
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .pointerInput(dockItem?.id ?: (-dockSlot.toLong())) {
                                        if (dockItem == null) return@pointerInput
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { startOffset ->
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                                draggedItem = dockItem
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
                                                                true
                                                            )
                                                        }
                                                        targetCell != null -> {
                                                            onMoveHomeItem(
                                                                currentDragged.id,
                                                                destPage,
                                                                targetCell.first + destStartRow,
                                                                targetCell.second,
                                                                false
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
                                            if (draggedItem == null) {
                                                onOpenAppContextMenu(dockApp, dockItem)
                                            }
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
        }

        // --- Floating Drag Preview Under User's Finger ---
        val floatingDragged = draggedItem
        if (floatingDragged != null && dragTotalDistance > dragMoveThresholdPx) {
            val previewSizeDp = if (floatingDragged.itemType == HomeItemType.FOLDER) 118.dp else 64.dp
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
                                isExpanded2x2 = true,
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
                                onClick = {},
                                onLongClick = {}
                            )
                        }
                    }
                    else -> Unit
                }
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
                onToggleFolderStyle = { onToggleFolderStyle(activeFolder.id) },
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FolderDetailModal(
    folder: FolderModel,
    allApps: List<AppInfo>,
    notificationCounts: Map<String, Int>,
    iconShape: IconShapeOption,
    onDismiss: () -> Unit,
    onLaunchApp: (AppInfo) -> Unit,
    onToggleFolderStyle: () -> Unit = {},
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
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .testTag("folder_detail_modal")
        ) {
            // iOS Style Centered Folder Title Above/At Top of Folder + Style Switcher Pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isEditing) {
                    OutlinedTextField(
                        value = editedName,
                        onValueChange = { editedName = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = Color.White
                        ),
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = MaterialTheme.colorScheme.primary,
                            focusedContainerColor = Color.White.copy(alpha = 0.16f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.12f),
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        ),
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Text(
                        text = folder.folderName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Toggle between 2x2 Bento and 3x3 Dense Home Screen Folder Display Style
                    FrostedGlassBox(
                        shape = CircleShape,
                        alphaOverride = 0.46f,
                        showBorder = false,
                        modifier = Modifier
                            .height(36.dp)
                            .clickable { onToggleFolderStyle() }
                            .testTag("toggle_folder_style_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.GridView,
                                contentDescription = "Switch Folder Grid Style",
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = folder.displayStyle.displayName,
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    FrostedGlassBox(
                        shape = CircleShape,
                        alphaOverride = 0.46f,
                        showBorder = false,
                        modifier = Modifier
                            .size(36.dp)
                            .clickable {
                                if (isEditing) {
                                    onUpdateFolder(folder.id, editedName, selectedPackages.toList())
                                    isEditing = false
                                } else {
                                    isEditing = true
                                }
                            }
                            .testTag(if (isEditing) "save_folder_apps_button" else "folder_add_edit_apps_button")
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isEditing) Icons.Default.Check else Icons.Default.Add,
                                contentDescription = if (isEditing) "Save Folder" else "Add or Edit Apps",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    FrostedGlassBox(
                        shape = CircleShape,
                        alphaOverride = 0.46f,
                        showBorder = false,
                        modifier = Modifier
                            .size(36.dp)
                            .clickable { onDeleteFolder(folder.id) }
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Folder",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Authentic iOS Frosted Glass Folder Surface (3x3 Icon-Only Grid — Zero Internal Text)
            FrostedGlassBox(
                shape = RoundedCornerShape(38.dp),
                alphaOverride = 0.56f,
                showBorder = false,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (!isEditing) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalArrangement = Arrangement.spacedBy(18.dp),
                            maxItemsInEachRow = 3
                        ) {
                            for (app in memberApps) {
                                LauncherAppTile(
                                    app = app,
                                    iconSizeDp = 58.dp,
                                    iconScale = 1.0f,
                                    iconShape = iconShape,
                                    showLabel = false,
                                    showCategoryBadge = false,
                                    notificationCount = notificationCounts[app.packageName] ?: 0,
                                    labelColor = Color.Transparent,
                                    onClick = { onLaunchApp(app) },
                                    onLongClick = {}
                                )
                            }
                        }
                    } else {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            maxItemsInEachRow = 4
                        ) {
                            for (app in allApps) {
                                val selected = app.packageName in selectedPackages
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(
                                            if (selected) {
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.34f)
                                            } else {
                                                Color.Transparent
                                            }
                                        )
                                        .padding(4.dp)
                                ) {
                                    LauncherAppTile(
                                        app = app,
                                        iconSizeDp = 50.dp,
                                        iconScale = if (selected) 1.0f else 0.86f,
                                        iconShape = iconShape,
                                        showLabel = false,
                                        showCategoryBadge = false,
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
