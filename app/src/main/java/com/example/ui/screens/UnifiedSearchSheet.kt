package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import android.view.HapticFeedbackConstants
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.domain.model.AppInfo
import com.example.domain.model.IconShapeOption
import com.example.ui.components.FrostedGlassBox
import com.example.ui.components.FrostedGlassPageSurface
import com.example.ui.components.LauncherAppTile
import com.example.ui.theme.LocalLauncherTokens
import com.example.ui.viewmodel.LauncherUiState
import com.example.ui.viewmodel.SearchUiState
import kotlin.math.roundToInt

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.ui.graphics.Color

private const val SEARCH_DISMISS_DRAG_THRESHOLD_PX = 95f
private const val SEARCH_DISMISS_FLING_VELOCITY = 520f

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UnifiedSearchSheet(
    launcherState: LauncherUiState,
    searchState: SearchUiState,
    onCloseSearch: () -> Unit,
    onQueryChange: (String) -> Unit,
    onLaunchApp: (AppInfo) -> Unit,
    onLongPressApp: (AppInfo) -> Unit,
    onOpenFolder: (Long) -> Unit,
    onOpenWebUrl: (String) -> Unit,
    onCloseWebView: () -> Unit,
    onClearHistory: () -> Unit,
    onDeleteHistoryEntry: (String) -> Unit = {},
    onRecordHistory: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val view = LocalView.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val settings = launcherState.settings
    val iconShape = remember(settings.iconShape) {
        IconShapeOption.fromId(settings.iconShape)
    }

    // Immediate local text state so typing is 100% lag-free and never drops characters
    var localQuery by remember { mutableStateOf(searchState.query) }
    var showAllHistory by remember { mutableStateOf(false) }

    LaunchedEffect(searchState.query) {
        if (localQuery != searchState.query) {
            localQuery = searchState.query
        }
    }

    // Always clear the search text when exiting the search sheet so old text never lingers
    val closeSearchAndClear = {
        localQuery = ""
        onQueryChange("")
        keyboardController?.hide()
        onCloseSearch()
    }

    DisposableEffect(Unit) {
        onDispose {
            localQuery = ""
            onQueryChange("")
        }
    }

    // Automatically focus the search input field and open keyboard when Unified Search opens
    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
            keyboardController?.show()
        } catch (_: Exception) {
        }
    }

    BackHandler {
        if (searchState.activeWebViewUrl != null) {
            onCloseWebView()
        } else {
            closeSearchAndClear()
        }
    }

    val listState = rememberLazyListState()
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var dismissThresholdHapticFired by remember { mutableStateOf(false) }
    val animatedDragOffsetY by animateFloatAsState(
        targetValue = dragOffsetY,
        label = "search_swipe_down_offset"
    )

    val searchNestedScrollConnection = remember(listState, searchState.activeWebViewUrl) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (searchState.activeWebViewUrl != null) return Offset.Zero
                if (dragOffsetY > 0f && available.y < 0f) {
                    val consumedY = available.y.coerceAtLeast(-dragOffsetY)
                    dragOffsetY = (dragOffsetY + consumedY).coerceAtLeast(0f)
                    if (dragOffsetY < SEARCH_DISMISS_DRAG_THRESHOLD_PX) {
                        dismissThresholdHapticFired = false
                    }
                    return Offset(0f, consumedY)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (searchState.activeWebViewUrl != null) return Offset.Zero
                if (available.y > 0f) {
                    val atTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                    if (atTop || consumed.y == 0f) {
                        dragOffsetY = (dragOffsetY + available.y * 0.85f).coerceAtLeast(0f)
                        if (!dismissThresholdHapticFired && dragOffsetY >= SEARCH_DISMISS_DRAG_THRESHOLD_PX) {
                            dismissThresholdHapticFired = true
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                        return Offset(0f, available.y)
                    }
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (searchState.activeWebViewUrl != null) return Velocity.Zero
                val atTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                if (dragOffsetY >= SEARCH_DISMISS_DRAG_THRESHOLD_PX ||
                    (atTop && available.y >= SEARCH_DISMISS_FLING_VELOCITY)
                ) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    dragOffsetY = 0f
                    dismissThresholdHapticFired = false
                    closeSearchAndClear()
                    return available
                }
                dragOffsetY = 0f
                dismissThresholdHapticFired = false
                return Velocity.Zero
            }
        }
    }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        onQueryChange(localQuery)
    }

    val safDocumentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(viewIntent)
            } catch (_: Exception) {
            }
        }
    }

    FrostedGlassPageSurface(
        alphaMultiplier = if (searchState.activeWebViewUrl != null) 0.44f else 0.62f,
        modifier = modifier
            .fillMaxSize()
            .offset { IntOffset(0, animatedDragOffsetY.roundToInt().coerceAtLeast(0)) }
            .nestedScroll(searchNestedScrollConnection)
            .testTag("unified_search_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(top = 6.dp)
        ) {
            // Top Swipe-Down Handle & Frosted Glass Unified Search Input Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(searchState.activeWebViewUrl) {
                        var headerDragDy = 0f
                        var headerThresholdFired = false
                        detectVerticalDragGestures(
                            onDragStart = {
                                headerDragDy = 0f
                                headerThresholdFired = false
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                headerDragDy += dragAmount
                                dragOffsetY = (dragOffsetY + dragAmount).coerceAtLeast(0f)
                                if (!headerThresholdFired &&
                                    (headerDragDy >= SEARCH_DISMISS_DRAG_THRESHOLD_PX ||
                                        dragOffsetY >= SEARCH_DISMISS_DRAG_THRESHOLD_PX)
                                ) {
                                    headerThresholdFired = true
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                            },
                            onDragCancel = { dragOffsetY = 0f },
                            onDragEnd = {
                                if (headerDragDy >= SEARCH_DISMISS_DRAG_THRESHOLD_PX ||
                                    dragOffsetY >= SEARCH_DISMISS_DRAG_THRESHOLD_PX
                                ) {
                                    dragOffsetY = 0f
                                    closeSearchAndClear()
                                } else {
                                    dragOffsetY = 0f
                                }
                            }
                        )
                    }
            ) {
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = closeSearchAndClear)
                        .padding(horizontal = 24.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 40.dp, height = 4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.50f))
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Close Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f),
                        modifier = Modifier.size(15.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                FrostedGlassBox(
                    shape = RoundedCornerShape(28.dp),
                    alphaOverride = 0.52f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = {
                                if (searchState.activeWebViewUrl != null) {
                                    onCloseWebView()
                                } else {
                                    closeSearchAndClear()
                                }
                            },
                            modifier = Modifier.testTag("unified_search_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        OutlinedTextField(
                            value = localQuery,
                            onValueChange = { newText ->
                                localQuery = newText
                                onQueryChange(newText)
                            },
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.search_hint),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (localQuery.isNotEmpty() && searchState.isOnline) {
                                        IconButton(
                                            onClick = {
                                                val submitted = localQuery.trim()
                                                if (submitted.isNotEmpty()) {
                                                    keyboardController?.hide()
                                                    onRecordHistory(submitted, "WEB")
                                                    onOpenWebUrl(submitted)
                                                }
                                            },
                                            modifier = Modifier.testTag("search_bar_browse_web_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Public,
                                                contentDescription = "Browse Web in Glass View",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    if (localQuery.isNotEmpty()) {
                                        IconButton(
                                            onClick = {
                                                localQuery = ""
                                                onQueryChange("")
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Clear,
                                                contentDescription = "Clear search",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    if (localQuery.isNotBlank()) {
                                        val submittedQuery = localQuery.trim()
                                        keyboardController?.hide()
                                        onRecordHistory(submittedQuery, "UNIFIED")
                                        if (searchState.activeWebViewUrl != null && searchState.isOnline) {
                                            onOpenWebUrl(submittedQuery)
                                        } else {
                                            val topApp = searchState.appResults.firstOrNull()
                                            if (topApp != null && !submittedQuery.contains(".")) {
                                                localQuery = ""
                                                onQueryChange("")
                                                onLaunchApp(topApp)
                                            } else if (searchState.isOnline) {
                                                onOpenWebUrl(submittedQuery)
                                            }
                                        }
                                    }
                                }
                            ),
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                cursorColor = MaterialTheme.colorScheme.primary,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester)
                                .testTag("unified_search_input")
                        )
                    }
                }
            }

            // Fixed-height 10.dp slot so the debounce indicator never shifts the list vertically
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                if (searchState.isSearching) {
                    androidx.compose.material3.LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .testTag("search_debounce_indicator"),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                }
            }

            // Use the settled debouncedQuery to drive section headers and visibility so UI never jitters mid-keystroke
            val settledQuery = searchState.debouncedQuery

            // Embedded Frosted-Glass WebView Mode (when user searches or browses the internet from the launcher)
            val activeUrl = searchState.activeWebViewUrl
            if (activeUrl != null) {
                EmbeddedSecureSearchWebView(
                    url = activeUrl,
                    isOnline = searchState.isOnline,
                    onNavigateQueryOrUrl = { newQueryOrUrl ->
                        localQuery = newQueryOrUrl
                        onRecordHistory(newQueryOrUrl, "WEB")
                        onOpenWebUrl(newQueryOrUrl)
                    },
                    onClose = onCloseWebView,
                    onExitSearchCompletely = closeSearchAndClear,
                    onOpenExternal = {
                        try {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(activeUrl)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        } catch (_: Exception) {
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("unified_search_results_list")
                ) {
                    // Section F: Recent Search History (up to 50 searches stored & managed)
                    if (launcherState.searchHistory.isNotEmpty() && settledQuery.isBlank()) {
                        item(key = "section_recent_history") {
                            FrostedGlassBox(
                                shape = RoundedCornerShape(22.dp),
                                alphaOverride = 0.48f,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("search_history_section_card")
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.History,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = "Search History (${launcherState.searchHistory.size}/50)",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (launcherState.searchHistory.size > 6) {
                                                TextButton(
                                                    onClick = { showAllHistory = !showAllHistory },
                                                    modifier = Modifier.testTag("toggle_expand_history_button")
                                                ) {
                                                    Text(if (showAllHistory) "Show Less" else "Show All (${launcherState.searchHistory.size})")
                                                }
                                            }
                                            TextButton(
                                                onClick = onClearHistory,
                                                modifier = Modifier.testTag("clear_search_history_button")
                                            ) {
                                                Text("Clear All")
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    val visibleHistory = if (showAllHistory) {
                                        launcherState.searchHistory.take(50)
                                    } else {
                                        launcherState.searchHistory.take(12)
                                    }

                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        for (entry in visibleHistory) {
                                            FrostedGlassBox(
                                                shape = RoundedCornerShape(16.dp),
                                                alphaOverride = 0.56f,
                                                modifier = Modifier
                                                    .clickable {
                                                        localQuery = entry.query
                                                        onQueryChange(entry.query)
                                                        if (entry.searchType == "WEB" && searchState.isOnline) {
                                                            onOpenWebUrl(entry.query)
                                                        }
                                                    }
                                                    .testTag("history_chip_${entry.query}")
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (entry.searchType == "WEB") Icons.Default.Public else Icons.Default.History,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(14.dp),
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                    Text(
                                                        text = entry.query,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1
                                                    )
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Remove ${entry.query} from history",
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier
                                                            .size(16.dp)
                                                            .clip(CircleShape)
                                                            .clickable {
                                                                onDeleteHistoryEntry(entry.query)
                                                            }
                                                            .testTag("delete_history_${entry.query}")
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Section A: Applications
                    item(key = "section_apps") {
                        FrostedGlassBox(
                            shape = RoundedCornerShape(22.dp),
                            alphaOverride = 0.46f,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = if (settledQuery.isBlank()) {
                                        "Suggested Apps"
                                    } else {
                                        "Apps (${searchState.appResults.size})"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                if (searchState.appResults.isEmpty()) {
                                    Text(
                                        text = "No matching installed applications",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        items(searchState.appResults, key = { it.packageName }) { app ->
                                            LauncherAppTile(
                                                app = app,
                                                iconSizeDp = 52.dp,
                                                iconScale = settings.iconScale,
                                                iconShape = iconShape,
                                                showLabel = true,
                                                showCategoryBadge = false,
                                                labelColor = MaterialTheme.colorScheme.onSurface,
                                                onClick = {
                                                    if (settledQuery.isNotBlank()) {
                                                        onRecordHistory(settledQuery, "APP")
                                                    }
                                                    localQuery = ""
                                                    onQueryChange("")
                                                    onLaunchApp(app)
                                                },
                                                onLongClick = { onLongPressApp(app) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Section B: Folders
                    if (searchState.folderResults.isNotEmpty()) {
                        item {
                            Text(
                                text = "Folders (${searchState.folderResults.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        items(searchState.folderResults, key = { "folder_${it.id}" }) { folder ->
                            FrostedGlassBox(
                                shape = RoundedCornerShape(16.dp),
                                alphaOverride = 0.56f,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        closeSearchAndClear()
                                        onOpenFolder(folder.id)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Column {
                                        Text(
                                            text = folder.folderName,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${folder.memberPackages.size} apps inside",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Section C: Contacts
                    if (settings.enableContactsSearch && (settledQuery.isNotBlank() || !searchState.hasContactsPermission)) {
                        item(key = "section_contacts_header") {
                            Text(
                                text = "Contacts",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (!searchState.hasContactsPermission) {
                            item(key = "section_contacts_permission") {
                                FrostedGlassBox(
                                    shape = RoundedCornerShape(16.dp),
                                    alphaOverride = 0.58f,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Local Contacts Search",
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = stringResource(R.string.contacts_permission_rationale),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        OutlinedButton(
                                            onClick = {
                                                contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                                            },
                                            modifier = Modifier.testTag("grant_contacts_permission_button")
                                        ) {
                                            Text("Allow")
                                        }
                                    }
                                }
                            }
                        } else if (searchState.contactResults.isNotEmpty()) {
                            items(
                                searchState.contactResults,
                                key = { "contact_${it.contactId}_${it.phoneNumber}" }
                            ) { contact ->
                                FrostedGlassBox(
                                    shape = RoundedCornerShape(16.dp),
                                    alphaOverride = 0.58f,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Person,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.secondary
                                            )
                                            Column {
                                                Text(
                                                    text = contact.displayName,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = contact.phoneNumber,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        Row {
                                            IconButton(
                                                onClick = {
                                                    try {
                                                        val dialIntent = Intent(
                                                            Intent.ACTION_DIAL,
                                                            Uri.parse("tel:${contact.phoneNumber}")
                                                        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                                                        context.startActivity(dialIntent)
                                                    } catch (_: Exception) {
                                                    }
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Call,
                                                    contentDescription = "Call ${contact.displayName}",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    try {
                                                        val smsIntent = Intent(
                                                            Intent.ACTION_SENDTO,
                                                            Uri.parse("smsto:${contact.phoneNumber}")
                                                        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                                                        context.startActivity(smsIntent)
                                                    } catch (_: Exception) {
                                                    }
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.Message,
                                                    contentDescription = "Message ${contact.displayName}",
                                                    tint = MaterialTheme.colorScheme.secondary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Section D: Files (MediaStore + SAF Document Picker)
                    if (settings.enableFilesSearch) {
                        item(key = "section_files_header") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Local Files & Documents",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                TextButton(
                                    onClick = { safDocumentPicker.launch(arrayOf("*/*")) },
                                    modifier = Modifier.testTag("saf_browse_files_button")
                                ) {
                                    Icon(
                                        Icons.Default.FolderOpen,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("Browse Storage")
                                }
                            }
                        }
                        items(searchState.fileResults, key = { "file_${it.id}" }) { file ->
                            FrostedGlassBox(
                                shape = RoundedCornerShape(14.dp),
                                alphaOverride = 0.56f,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        try {
                                            val openIntent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(Uri.parse(file.contentUri), file.mimeType)
                                                addFlags(
                                                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                                        Intent.FLAG_ACTIVITY_NEW_TASK
                                                )
                                            }
                                            context.startActivity(openIntent)
                                        } catch (_: Exception) {
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Description,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary
                                    )
                                    Column {
                                        Text(
                                            text = file.displayName,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "${file.relativePath} • ${file.sizeBytes / 1024} KB",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Section E: Web Search & Quick Glass Browser Launch
                    item(key = "section_web_header") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Glass Web Browser (${settings.defaultSearchEngine})",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (searchState.isOnline) {
                                FrostedGlassBox(
                                    shape = RoundedCornerShape(14.dp),
                                    alphaOverride = 0.56f,
                                    modifier = Modifier
                                        .clickable {
                                            keyboardController?.hide()
                                            val target = settledQuery.ifBlank { "https://www.google.com" }
                                            if (settledQuery.isNotBlank()) {
                                                onRecordHistory(settledQuery, "WEB")
                                            }
                                            onOpenWebUrl(target)
                                        }
                                        .testTag("open_glass_webview_quick_button")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Public,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = "Open Glass WebView",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (!searchState.isOnline) {
                        item {
                            FrostedGlassBox(
                                shape = RoundedCornerShape(16.dp),
                                alphaOverride = 0.62f,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudOff,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary
                                    )
                                    Column {
                                        Text(
                                            text = "You are currently offline",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Web search requires internet. Local App, Folder, Contact, File search, and AI categorization remain active.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    } else if (settledQuery.isNotBlank()) {
                        items(searchState.webSuggestions, key = { it.url }) { suggestion ->
                            FrostedGlassBox(
                                shape = RoundedCornerShape(16.dp),
                                alphaOverride = 0.56f,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        keyboardController?.hide()
                                        onRecordHistory(suggestion.query, "WEB")
                                        onOpenWebUrl(suggestion.url)
                                    }
                                    .testTag("web_suggestion_${suggestion.query}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Public,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = suggestion.query,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "Browse in Glass WebView • ${suggestion.searchEngineName}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.OpenInBrowser,
                                        contentDescription = "Open web search",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
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

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun EmbeddedSecureSearchWebView(
    url: String,
    isOnline: Boolean,
    onNavigateQueryOrUrl: (String) -> Unit,
    onClose: () -> Unit,
    onExitSearchCompletely: () -> Unit,
    onOpenExternal: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalLauncherTokens.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var isGlassWebMode by remember { mutableStateOf(true) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var addressBarInput by remember(url) {
        mutableStateOf(url.removePrefix("https://").removePrefix("http://"))
    }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }

    val applyFrostedWebCss: (WebView, Boolean) -> Unit = { view, glassEnabled ->
        val isDark = tokens.isDarkTheme
        val js = if (glassEnabled) {
            val bgRgba = if (isDark) "rgba(15, 23, 42, 0.24)" else "rgba(248, 250, 252, 0.30)"
            val cardRgba = if (isDark) "rgba(30, 41, 59, 0.50)" else "rgba(255, 255, 255, 0.56)"
            val searchBarRgba = if (isDark) "rgba(15, 23, 42, 0.58)" else "rgba(255, 255, 255, 0.66)"
            val borderRgba = if (isDark) "rgba(255, 255, 255, 0.24)" else "rgba(255, 255, 255, 0.62)"
            val textHex = if (isDark) "#F8FAFC" else "#0F172A"
            """
            (function() {
                var css = 'html, body, main, #main, #cnt, #rcnt, .L3eUgb, .sfbg, #appbar, #b_content, .site-content, #content, .mw-page-container { ' +
                    'background: $bgRgba !important; background-color: $bgRgba !important; color: $textHex !important; ' +
                '} ' +
                'header, nav, #gb, #searchform, .b_scopebar, .zGVn2e, .RNNXgb, .SDkEP, .A8SBwf, #sb_form, .b_searchboxForm, form[role="search"], form[action*="search"], .search-container, .searchbox_searchbox__bfbmv { ' +
                    'background: $searchBarRgba !important; background-color: $searchBarRgba !important; ' +
                    'backdrop-filter: blur(20px) saturate(185%) !important; -webkit-backdrop-filter: blur(20px) saturate(185%) !important; ' +
                    'border: 1px solid $borderRgba !important; border-radius: 24px !important; ' +
                    'box-shadow: 0 8px 28px rgba(0, 0, 0, 0.20) !important; ' +
                '} ' +
                'input[type="search"], input[type="text"], input[name="q"], textarea[name="q"], .gLFyf, #sb_form_q, .b_searchbox, .search-input, input { ' +
                    'background: transparent !important; background-color: transparent !important; ' +
                    'color: $textHex !important; caret-color: #60A5FA !important; ' +
                '} ' +
                'div.g, .MjjYud > div, .result, .b_algo, article, section, .kp-blk, .xpd, .b_ans, .card { ' +
                    'background: $cardRgba !important; background-color: $cardRgba !important; ' +
                    'backdrop-filter: blur(16px) saturate(175%) !important; -webkit-backdrop-filter: blur(16px) saturate(175%) !important; ' +
                    'border: 1px solid $borderRgba !important; border-radius: 18px !important; ' +
                    'margin-bottom: 10px !important; padding: 10px !important; ' +
                    'box-shadow: 0 4px 18px rgba(0, 0, 0, 0.14) !important; ' +
                '}';
                var applyStyle = function() {
                    var head = document.head || document.documentElement;
                    if (!head) return;
                    var style = document.getElementById('pixel-frosted-glass-style');
                    if (!style) {
                        style = document.createElement('style');
                        style.id = 'pixel-frosted-glass-style';
                        head.appendChild(style);
                    }
                    if (style.innerHTML !== css) {
                        style.innerHTML = css;
                    }
                };
                applyStyle();
                if (!window.__pixelGlassObserver) {
                    window.__pixelGlassObserver = new MutationObserver(function() { applyStyle(); });
                    window.__pixelGlassObserver.observe(document.documentElement, { childList: true, subtree: true });
                }
            })();
            """.trimIndent()
        } else {
            """
            (function() {
                if (window.__pixelGlassObserver) {
                    window.__pixelGlassObserver.disconnect();
                    window.__pixelGlassObserver = null;
                }
                var style = document.getElementById('pixel-frosted-glass-style');
                if (style) { style.remove(); }
            })();
            """.trimIndent()
        }
        try {
            view.evaluateJavascript(js, null)
        } catch (_: Exception) {
        }
    }

    FrostedGlassBox(
        shape = RoundedCornerShape(26.dp),
        alphaOverride = if (isGlassWebMode) 0.30f else 0.80f,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .testTag("frosted_internet_browser_container")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Frosted Glass Internet Browser Toolbar & Glass Address/Search Bar
            FrostedGlassBox(
                shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                alphaOverride = 0.56f,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Interactive Glass Web Search / URL Bar inside the Embedded WebView
                    FrostedGlassBox(
                        shape = RoundedCornerShape(20.dp),
                        alphaOverride = 0.48f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("webview_glass_search_bar_container")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Public,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            OutlinedTextField(
                                value = addressBarInput,
                                onValueChange = { addressBarInput = it },
                                placeholder = {
                                    Text(
                                        text = "Search web or enter URL in Glass View...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                keyboardActions = KeyboardActions(
                                    onGo = {
                                        val target = addressBarInput.trim()
                                        if (target.isNotEmpty()) {
                                            keyboardController?.hide()
                                            onNavigateQueryOrUrl(target)
                                        }
                                    }
                                ),
                                trailingIcon = {
                                    if (addressBarInput.isNotEmpty()) {
                                        IconButton(
                                            onClick = { addressBarInput = "" },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Clear,
                                                contentDescription = "Clear URL",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    cursorColor = MaterialTheme.colorScheme.primary,
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("webview_glass_address_input")
                            )
                        }
                    }

                    // Glass Browser Navigation Controls Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            IconButton(
                                onClick = { webViewRef?.goBack() },
                                enabled = canGoBack,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Web Back",
                                    tint = if (canGoBack) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                            IconButton(
                                onClick = { webViewRef?.goForward() },
                                enabled = canGoForward,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Web Forward",
                                    tint = if (canGoForward) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                            IconButton(
                                onClick = { webViewRef?.reload() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Reload Page",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            FilterChip(
                                selected = isGlassWebMode,
                                onClick = {
                                    isGlassWebMode = !isGlassWebMode
                                    webViewRef?.let { applyFrostedWebCss(it, isGlassWebMode) }
                                },
                                label = {
                                    Text(
                                        text = if (isGlassWebMode) "Glass Web ON" else "Solid Web",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                modifier = Modifier.testTag("toggle_glass_web_mode_chip")
                            )

                            IconButton(
                                onClick = onOpenExternal,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OpenInBrowser,
                                    contentDescription = "Open in External Browser",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    onClose()
                                    onExitSearchCompletely()
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("close_web_browser_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close Internet View",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (!isOnline) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "No Internet Connection",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Web results require an active internet connection.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = onClose) {
                            Text("Return to Local Search")
                        }
                    }
                }
            } else {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                allowFileAccess = false
                                allowContentAccess = false
                                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            }
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val targetUri = request?.url ?: return true
                                    val scheme = targetUri.scheme?.lowercase()
                                    return scheme != "https" && scheme != "http"
                                }

                                override fun onPageStarted(
                                    view: WebView?,
                                    startedUrl: String?,
                                    favicon: android.graphics.Bitmap?
                                ) {
                                    super.onPageStarted(view, startedUrl, favicon)
                                    if (startedUrl != null) {
                                        addressBarInput = startedUrl.removePrefix("https://").removePrefix("http://")
                                    }
                                    if (view != null) {
                                        view.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                        applyFrostedWebCss(view, isGlassWebMode)
                                    }
                                }

                                override fun onPageCommitVisible(view: WebView?, visibleUrl: String?) {
                                    super.onPageCommitVisible(view, visibleUrl)
                                    if (view != null) {
                                        view.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                        applyFrostedWebCss(view, isGlassWebMode)
                                    }
                                }

                                override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                                    super.onPageFinished(view, loadedUrl)
                                    if (loadedUrl != null) {
                                        addressBarInput = loadedUrl.removePrefix("https://").removePrefix("http://")
                                    }
                                    if (view != null) {
                                        canGoBack = view.canGoBack()
                                        canGoForward = view.canGoForward()
                                        view.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                        applyFrostedWebCss(view, isGlassWebMode)
                                    }
                                }
                            }
                            webViewRef = this
                            loadUrl(url)
                        }
                    },
                    update = { webView ->
                        webViewRef = webView
                        webView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        canGoBack = webView.canGoBack()
                        canGoForward = webView.canGoForward()
                        if (webView.url != url) {
                            webView.loadUrl(url)
                        } else {
                            applyFrostedWebCss(webView, isGlassWebMode)
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp))
                )
            }
        }
    }
}

