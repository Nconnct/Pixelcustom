package com.example

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.HomeScreenItemModel
import com.example.ui.components.FrostedGlassBox
import com.example.ui.components.PixelWallpaperBackground
import com.example.ui.screens.AiLayoutPreviewSheet
import com.example.ui.screens.AppDrawerSheet
import com.example.ui.screens.AppLongPressContextDialog
import com.example.ui.screens.HomeScreenContent
import com.example.ui.screens.SettingsAndCustomizationSheet
import com.example.ui.screens.UnifiedSearchSheet
import com.example.ui.screens.WidgetPickerSheet
import com.example.ui.theme.LocalLauncherTokens
import com.example.ui.theme.PixelCategoryLauncherTheme
import com.example.ui.viewmodel.ActiveOverlaySheet
import com.example.ui.viewmodel.LauncherViewModel
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels {
        LauncherViewModel.Factory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ensure the user's real Android system wallpaper shows through behind the launcher
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        enableEdgeToEdge()

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val searchState by viewModel.searchState.collectAsStateWithLifecycle()
            val settings = uiState.settings

            // Dynamically blur the live Android system wallpaper behind frosted glass overlays on Android 12+
            val shouldBlurWallpaper = uiState.activeOverlay != ActiveOverlaySheet.NONE || uiState.openedFolder != null
            LaunchedEffect(shouldBlurWallpaper, settings.blurStrengthDp) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    try {
                        val radiusPx = if (shouldBlurWallpaper) {
                            (settings.blurStrengthDp * resources.displayMetrics.density).toInt().coerceIn(20, 90)
                        } else {
                            0
                        }
                        window.setBackgroundBlurRadius(radiusPx)
                        val params = window.attributes
                        if (radiusPx > 0) {
                            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                            params.blurBehindRadius = radiusPx
                        } else {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                            params.blurBehindRadius = 0
                        }
                        window.attributes = params
                    } catch (_: Exception) {
                    }
                }
            }

            PixelCategoryLauncherTheme(
                themeMode = settings.themeMode,
                useDynamicWallpaperColors = settings.useWallpaperDynamicColors,
                accentColorName = settings.accentColorName,
                iconSizeDp = settings.homeIconSizeDp,
                drawerIconSizeDp = settings.drawerIconSizeDp,
                blurStrengthDp = settings.blurStrengthDp,
                frostedIntensity = settings.frostedGlassIntensity,
                dockTransparency = settings.dockTransparency,
                fontScale = settings.fontScale,
                useMonoFont = settings.useMonoFont
            ) {
                PixelCategoryLauncherRoot(
                    viewModel = viewModel,
                    uiState = uiState,
                    searchState = searchState
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            viewModel.closeOverlaysAndGoHome()
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshDefaultHomeStatus()
    }
}

@Composable
fun PixelCategoryLauncherRoot(
    viewModel: LauncherViewModel,
    uiState: com.example.ui.viewmodel.LauncherUiState,
    searchState: com.example.ui.viewmodel.SearchUiState
) {
    val context = LocalContext.current
    val tokens = LocalLauncherTokens.current

    var movingHomeItem by remember { mutableStateOf<HomeScreenItemModel?>(null) }

    val homeRoleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshDefaultHomeStatus()
    }

    val requestDefaultHomeAction: () -> Unit = {
        try {
            val intent = viewModel.widgetHostController.createRoleHomeRequestIntent()
            homeRoleLauncher.launch(intent)
        } catch (_: Exception) {
            try {
                context.startActivity(Intent(android.provider.Settings.ACTION_HOME_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(uiState.statusBannerMessage) {
        if (uiState.statusBannerMessage != null) {
            delay(3000)
            viewModel.showBanner(null)
        }
    }

    val filteredDrawerApps = remember(
        uiState.installedApps,
        uiState.selectedCategoryId,
        uiState.drawerSearchQuery,
        uiState.settings.drawerSortMode
    ) {
        viewModel.getFilteredAndSortedDrawerApps(uiState)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("launcher_root_container")
    ) {
        // 1. System Wallpaper & Frosted Glass Scrim (Transparent over the user's real Android wallpaper when presetId == 0)
        val isOverlayOpen = uiState.activeOverlay != ActiveOverlaySheet.NONE || uiState.openedFolder != null
        val wallpaperBlur = if (isOverlayOpen) {
            tokens.blurRadiusDp
        } else {
            0.dp
        }
        PixelWallpaperBackground(
            presetId = uiState.settings.wallpaperPresetId,
            isDark = tokens.isDarkTheme,
            isOverlayOpen = isOverlayOpen,
            modifier = Modifier.blur(wallpaperBlur)
        )

        // 2. Main Home Screen Workspace (Rendered only when no full-screen overlay sheet is open so screens never overlap!)
        AnimatedVisibility(
            visible = uiState.activeOverlay == ActiveOverlaySheet.NONE,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            HomeScreenContent(
                state = uiState,
                widgetHostController = viewModel.widgetHostController,
                movingHomeItem = movingHomeItem,
                onStartMoveHomeItem = { movingHomeItem = it },
                onCancelMoveMode = { movingHomeItem = null },
                onOpenOverlay = viewModel::openOverlay,
                onLaunchApp = viewModel::launchApp,
                onOpenFolder = viewModel::openFolder,
                onCloseFolder = viewModel::closeFolder,
                onOpenAppContextMenu = viewModel::openAppContextMenu,
                onRemoveHomeItem = viewModel::removeShortcutFromHome,
                onMoveHomeItem = viewModel::moveHomeItem,
                onMergeHomeItems = viewModel::mergeHomeItems,
                onToggleFolderStyle = viewModel::toggleFolderStyle,
                onResizeFolder = viewModel::resizeFolder,
                onExtractAppFromFolder = viewModel::extractAppFromFolder,
                onCreateSmartFolder = viewModel::createSmartFolder,
                onUpdateFolder = viewModel::updateFolder,
                onDeleteFolder = viewModel::deleteFolder,
                onUndoLayout = viewModel::undoLastLayoutChange,
                onRequestDefaultHome = requestDefaultHomeAction,
                onOpenWeather = {
                    viewModel.openOverlay(ActiveOverlaySheet.UNIFIED_SEARCH)
                    viewModel.updateUnifiedSearchQuery("weather", immediate = true)
                }
            )
        }

        // 3. Dedicated Full-Screen Overlay Sheets (Mutually exclusive with Home Screen)
        AnimatedVisibility(
            visible = uiState.activeOverlay == ActiveOverlaySheet.APP_DRAWER,
            enter = slideInVertically(initialOffsetY = { it / 4 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 4 }) + fadeOut()
        ) {
            AppDrawerSheet(
                state = uiState,
                filteredApps = filteredDrawerApps,
                onCloseDrawer = viewModel::closeOverlaysAndGoHome,
                onSelectCategory = viewModel::selectDrawerCategory,
                onUpdateSearchQuery = viewModel::updateDrawerSearchQuery,
                onChangeSortMode = { sortId ->
                    viewModel.updateSettings { it.copy(drawerSortMode = sortId) }
                },
                onLaunchApp = viewModel::launchApp,
                onLongPressApp = { app -> viewModel.openAppContextMenu(app, null) },
                onApproveSuggestion = viewModel::approveSuggestedCategory,
                onCreateOrUpdateCategory = viewModel::createOrUpdateCategory,
                onToggleCategoryVisibility = viewModel::toggleCategoryVisibility,
                onReorderCategory = viewModel::reorderCategory,
                onDeleteCategory = viewModel::deleteCustomCategory
            )
        }

        AnimatedVisibility(
            visible = uiState.activeOverlay == ActiveOverlaySheet.UNIFIED_SEARCH,
            enter = slideInVertically(initialOffsetY = { it / 5 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 5 }) + fadeOut()
        ) {
            UnifiedSearchSheet(
                launcherState = uiState,
                searchState = searchState,
                onCloseSearch = viewModel::closeOverlaysAndGoHome,
                onQueryChange = viewModel::updateUnifiedSearchQuery,
                onLaunchApp = viewModel::launchApp,
                onLongPressApp = { app -> viewModel.openAppContextMenu(app, null) },
                onOpenFolder = { folderId ->
                    viewModel.closeOverlaysAndGoHome()
                    viewModel.openFolder(folderId)
                },
                onOpenWebUrl = viewModel::openEmbeddedWebSearch,
                onCloseWebView = viewModel::closeEmbeddedWebView,
                onClearHistory = viewModel::clearSearchHistory,
                onDeleteHistoryEntry = viewModel::deleteSearchHistoryEntry,
                onRecordHistory = viewModel::recordSearchHistory
            )
        }

        AnimatedVisibility(
            visible = uiState.activeOverlay == ActiveOverlaySheet.AI_ORGANIZER_PREVIEW,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            AiLayoutPreviewSheet(
                state = uiState,
                onClose = viewModel::closeOverlaysAndGoHome,
                onSelectMode = viewModel::generateLayoutPreview,
                onApproveLayout = viewModel::approveAndApplyProposedLayout,
                onUndoPreviousLayout = viewModel::undoLastLayoutChange,
                onMatchAppsForCustomIntent = viewModel::matchAppsForSmartIntent,
                onCreateCustomSmartFolder = { name, packages, color, spanX, spanY, style ->
                    viewModel.createSmartFolder(
                        folderName = name,
                        memberPackages = packages,
                        colorHex = color,
                        pageIndex = 0,
                        spanX = spanX,
                        spanY = spanY,
                        displayStyle = style
                    )
                    viewModel.closeOverlaysAndGoHome()
                }
            )
        }

        AnimatedVisibility(
            visible = uiState.activeOverlay == ActiveOverlaySheet.WIDGET_PICKER,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            WidgetPickerSheet(
                availableWidgets = uiState.availableWidgets,
                onClose = viewModel::closeOverlaysAndGoHome,
                onSelectWidget = { provider -> viewModel.addSystemWidgetToHome(provider, 0) }
            )
        }

        AnimatedVisibility(
            visible = uiState.activeOverlay == ActiveOverlaySheet.SETTINGS_CUSTOMIZATION,
            enter = slideInVertically(initialOffsetY = { it / 4 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 4 }) + fadeOut()
        ) {
            SettingsAndCustomizationSheet(
                state = uiState,
                onClose = viewModel::closeOverlaysAndGoHome,
                onUpdateSettings = viewModel::updateSettings,
                onOpenAiOrganizer = { viewModel.openOverlay(ActiveOverlaySheet.AI_ORGANIZER_PREVIEW) },
                onRequestDefaultHome = requestDefaultHomeAction,
                onExportBackup = viewModel::prepareLocalBackupExport,
                onImportBackup = viewModel::restoreFromBackupJson
            )
        }

        // 4. Unified App Long-Press Context Dialog (Renders cleanly above whichever screen is active)
        val contextApp = uiState.contextMenuApp
        if (contextApp != null) {
            AppLongPressContextDialog(
                app = contextApp,
                homeItem = uiState.contextMenuHomeItem,
                categories = uiState.categories,
                onDismiss = viewModel::dismissAppContextMenu,
                onAssignPrimaryCategory = { catId ->
                    viewModel.assignAppCategory(contextApp.packageName, catId, false)
                },
                onToggleAdditionalCategory = { catId, assigned ->
                    viewModel.toggleAdditionalCategory(contextApp.packageName, catId, assigned)
                },
                onToggleFavorite = { viewModel.toggleAppFavorite(contextApp.packageName) },
                onPinToHome = { toDock -> viewModel.pinAppToHome(contextApp.packageName, toDock) },
                onStartGridMove = { item ->
                    movingHomeItem = item
                    viewModel.closeOverlaysAndGoHome()
                },
                onRemoveFromHome = { itemId -> viewModel.removeShortcutFromHome(itemId) },
                onSaveCustomAppearance = { label, iconKey ->
                    viewModel.setCustomAppIconOrLabel(contextApp.packageName, label, iconKey)
                }
            )
        }

        // 5. Floating Status Feedback Banner
        val bannerMessage = uiState.statusBannerMessage
        if (bannerMessage != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                FrostedGlassBox(
                    shape = RoundedCornerShape(22.dp),
                    alphaOverride = 0.94f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.showBanner(null) }
                        .testTag("status_feedback_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = bannerMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.showBanner(null) }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
