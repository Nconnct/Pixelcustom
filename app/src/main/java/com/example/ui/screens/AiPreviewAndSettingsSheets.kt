package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.LauncherSettingsEntity
import com.example.domain.model.ArrangementMode
import com.example.domain.model.IconShapeOption
import com.example.engine.SystemWidgetProviderItem
import com.example.ui.components.FrostedGlassBox
import com.example.ui.theme.AccentColors
import com.example.ui.viewmodel.LauncherUiState

/**
 * Interactive AI Home Screen Auto-Arrangement Preview & Approval Sheet.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiLayoutPreviewSheet(
    state: LauncherUiState,
    onClose: () -> Unit,
    onSelectMode: (ArrangementMode) -> Unit,
    onApproveLayout: () -> Unit,
    onUndoPreviousLayout: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onClose)

    val preview = state.proposedLayoutPreview
    val appsMap = remember(state.installedApps) {
        state.installedApps.associateBy { it.packageName }
    }

    Surface(
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.97f),
        contentColor = MaterialTheme.colorScheme.onBackground,
        modifier = modifier
            .fillMaxSize()
            .testTag("ai_organizer_preview_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "AI Home Screen Organizer",
                        style = MaterialTheme.typography.titleLarge
                    )
                }

                if (state.layoutSnapshots.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onUndoPreviousLayout,
                        modifier = Modifier.testTag("preview_undo_button")
                    ) {
                        Icon(Icons.Default.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Undo")
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Arrangement Mode Selector
            Text(
                text = "Select Arrangement Mode:",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (mode in listOf(ArrangementMode.AI_ORGANIZED, ArrangementMode.PIXEL_DEFAULT)) {
                    val selected = preview?.mode == mode
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectMode(mode) },
                        label = { Text(mode.displayName) },
                        leadingIcon = if (selected) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        modifier = Modifier.testTag("mode_chip_${mode.name}")
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            if (preview != null) {
                // Summary Card
                FrostedGlassBox(
                    shape = RoundedCornerShape(20.dp),
                    alphaOverride = 0.65f,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Proposed ${preview.mode.displayName} Blueprint",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = preview.mode.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        for (bullet in preview.summaryPoints) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = bullet,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Proposed Smart Folders Preview
                if (preview.proposedFolders.isNotEmpty()) {
                    Text(
                        text = "Proposed Smart Folders (${preview.proposedFolders.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    for (folder in preview.proposedFolders) {
                        FrostedGlassBox(
                            shape = RoundedCornerShape(16.dp),
                            alphaOverride = 0.58f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "${folder.folderName} (Page ${folder.pageIndex + 1}, Row ${folder.row + 1})",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = folder.memberPackages.mapNotNull { appsMap[it]?.appName }.joinToString(" • "),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Proposed Dock Preview
                Text(
                    text = "Proposed Dock Configuration (${preview.dockPackages.size} slots)",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(6.dp))
                FrostedGlassBox(
                    shape = RoundedCornerShape(18.dp),
                    alphaOverride = 0.6f,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = preview.dockPackages.mapNotNull { appsMap[it]?.appName }.joinToString("  |  ")
                            .ifEmpty { "Default Essentials" },
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(14.dp)
                    )
                }

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onClose,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = onApproveLayout,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("approve_ai_layout_button")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Approve & Apply")
                    }
                }
            }
        }
    }
}

/**
 * System Widget Picker Sheet using WidgetHostController.
 */
@Composable
fun WidgetPickerSheet(
    availableWidgets: List<SystemWidgetProviderItem>,
    onClose: () -> Unit,
    onSelectWidget: (SystemWidgetProviderItem) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onClose)

    Surface(
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.97f),
        contentColor = MaterialTheme.colorScheme.onBackground,
        modifier = modifier
            .fillMaxSize()
            .testTag("widget_picker_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Icon(
                    imageVector = Icons.Default.Widgets,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Android System Widgets (${availableWidgets.size})",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            Spacer(Modifier.height(12.dp))

            if (availableWidgets.isEmpty()) {
                Text(
                    text = "No external AppWidgetProviders reported by the system.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                for (provider in availableWidgets) {
                    FrostedGlassBox(
                        shape = RoundedCornerShape(16.dp),
                        alphaOverride = 0.6f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onSelectWidget(provider) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = provider.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${provider.packageName} • ${provider.minWidthDp}x${provider.minHeightDp}dp",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "Add",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Complete Launcher Settings, Customization, Icon Packs, Gestures, Local Backup, & Privacy Center Sheet.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsAndCustomizationSheet(
    state: LauncherUiState,
    onClose: () -> Unit,
    onUpdateSettings: ((LauncherSettingsEntity) -> LauncherSettingsEntity) -> Unit,
    onOpenAiOrganizer: () -> Unit,
    onRequestDefaultHome: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onClose)

    val settings = state.settings
    var selectedTab by remember { mutableIntStateOf(0) }
    var importJsonInput by remember { mutableStateOf("") }

    val tabs = listOf("Home & Grid", "Drawer", "Theme & Glass", "Gestures", "Privacy & Backup")

    Surface(
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.97f),
        contentColor = MaterialTheme.colorScheme.onBackground,
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_customization_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("close_settings_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close Settings")
                    }
                    Text(
                        text = "PixelCategory Settings",
                        style = MaterialTheme.typography.titleLarge
                    )
                }

                OutlinedButton(
                    onClick = onRequestDefaultHome,
                    modifier = Modifier.testTag("settings_default_home_button")
                ) {
                    Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (state.isDefaultHomeLauncher) "Default Home Active" else "Set Default Home")
                }
            }

            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, maxLines = 1) },
                        modifier = Modifier.testTag("settings_tab_$index")
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when (selectedTab) {
                    // TAB 0: HOME SCREEN & GRID CUSTOMIZATION
                    0 -> {
                        Text(
                            text = "Home Screen Grid & Dock",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )

                        SettingSliderItem(
                            label = "Grid Columns: ${settings.homeGridColumns}",
                            value = settings.homeGridColumns.toFloat(),
                            valueRange = 3f..6f,
                            steps = 2,
                            onValueChange = { v -> onUpdateSettings { it.copy(homeGridColumns = v.toInt()) } }
                        )

                        SettingSliderItem(
                            label = "Grid Rows: ${settings.homeGridRows}",
                            value = settings.homeGridRows.toFloat(),
                            valueRange = 4f..7f,
                            steps = 2,
                            onValueChange = { v -> onUpdateSettings { it.copy(homeGridRows = v.toInt()) } }
                        )

                        SettingSliderItem(
                            label = "Home Icon Size: ${settings.homeIconSizeDp} dp",
                            value = settings.homeIconSizeDp.toFloat(),
                            valueRange = 42f..68f,
                            steps = 12,
                            onValueChange = { v -> onUpdateSettings { it.copy(homeIconSizeDp = v.toInt()) } }
                        )

                        SettingSliderItem(
                            label = "Home Screen Pages: ${settings.homePageCount}",
                            value = settings.homePageCount.toFloat(),
                            valueRange = 2f..5f,
                            steps = 2,
                            onValueChange = { v -> onUpdateSettings { it.copy(homePageCount = v.toInt()) } }
                        )

                        SettingSliderItem(
                            label = "Dock Icon Count: ${settings.dockIconCount}",
                            value = settings.dockIconCount.toFloat(),
                            valueRange = 3f..6f,
                            steps = 2,
                            onValueChange = { v -> onUpdateSettings { it.copy(dockIconCount = v.toInt()) } }
                        )

                        SettingSliderItem(
                            label = "Dock Frosted Opacity: ${(settings.dockTransparency * 100).toInt()}%",
                            value = settings.dockTransparency,
                            valueRange = 0.25f..0.95f,
                            steps = 10,
                            onValueChange = { v -> onUpdateSettings { it.copy(dockTransparency = v) } }
                        )

                        SettingSwitchRow(
                            title = "Show Home App Labels",
                            checked = settings.showHomeIconLabels,
                            onCheckedChange = { c -> onUpdateSettings { it.copy(showHomeIconLabels = c) } }
                        )
                        SettingSwitchRow(
                            title = "Show Pixel At-a-Glance Header",
                            checked = settings.showAtAGlanceWidget,
                            onCheckedChange = { c -> onUpdateSettings { it.copy(showAtAGlanceWidget = c) } }
                        )
                        SettingSwitchRow(
                            title = "Show Pinned Dock",
                            checked = settings.showDock,
                            onCheckedChange = { c -> onUpdateSettings { it.copy(showDock = c) } }
                        )
                        SettingSwitchRow(
                            title = "Show Bottom Pixel Search Bar",
                            checked = settings.showBottomSearchBar,
                            onCheckedChange = { c -> onUpdateSettings { it.copy(showBottomSearchBar = c) } }
                        )

                        Button(
                            onClick = onOpenAiOrganizer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Preview AI Home Auto-Arrangement")
                        }
                    }

                    // TAB 1: APP DRAWER CUSTOMIZATION
                    1 -> {
                        Text(
                            text = "App Drawer & Category Customization",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )

                        SettingSliderItem(
                            label = "Drawer Columns: ${settings.drawerGridColumns}",
                            value = settings.drawerGridColumns.toFloat(),
                            valueRange = 3f..6f,
                            steps = 2,
                            onValueChange = { v -> onUpdateSettings { it.copy(drawerGridColumns = v.toInt()) } }
                        )

                        SettingSliderItem(
                            label = "Drawer Icon Size: ${settings.drawerIconSizeDp} dp",
                            value = settings.drawerIconSizeDp.toFloat(),
                            valueRange = 42f..66f,
                            steps = 11,
                            onValueChange = { v -> onUpdateSettings { it.copy(drawerIconSizeDp = v.toInt()) } }
                        )

                        SettingSliderItem(
                            label = "Drawer Frosted Background Opacity: ${(settings.drawerBackgroundAlpha * 100).toInt()}%",
                            value = settings.drawerBackgroundAlpha,
                            valueRange = 0.45f..0.96f,
                            steps = 10,
                            onValueChange = { v -> onUpdateSettings { it.copy(drawerBackgroundAlpha = v) } }
                        )

                        SettingSwitchRow(
                            title = "Show Category Tabs in App Drawer",
                            checked = settings.showCategoryTabs,
                            onCheckedChange = { c -> onUpdateSettings { it.copy(showCategoryTabs = c) } }
                        )

                        SettingSwitchRow(
                            title = "Show App Labels in Drawer",
                            checked = settings.showDrawerAppLabels,
                            onCheckedChange = { c -> onUpdateSettings { it.copy(showDrawerAppLabels = c) } }
                        )
                    }

                    // TAB 2: THEME, FROSTED GLASS & ICON PACKS
                    2 -> {
                        Text(
                            text = "Theme, Frosted Glass & Icon Packs",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Text("Theme Mode:", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (mode in listOf("DARK", "LIGHT", "SYSTEM")) {
                                FilterChip(
                                    selected = settings.themeMode == mode,
                                    onClick = { onUpdateSettings { it.copy(themeMode = mode) } },
                                    label = { Text(mode) }
                                )
                            }
                        }

                        SettingSwitchRow(
                            title = "Wallpaper Dynamic Colors (Material You)",
                            checked = settings.useWallpaperDynamicColors,
                            onCheckedChange = { c -> onUpdateSettings { it.copy(useWallpaperDynamicColors = c) } }
                        )

                        Text("Accent Color Override:", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for ((name, colorVal) in AccentColors) {
                                FilterChip(
                                    selected = settings.accentColorName == name,
                                    onClick = { onUpdateSettings { it.copy(accentColorName = name) } },
                                    label = { Text(name) },
                                    leadingIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clip(CircleShape)
                                                .background(colorVal)
                                        )
                                    }
                                )
                            }
                        }

                        Text("Wallpaper Atmospheric Preset:", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val presets = listOf(
                                0 to "Pixel Obsidian",
                                1 to "Ocean Aurora",
                                2 to "Twilight Dusk",
                                3 to "Emerald Mist"
                            )
                            for ((id, label) in presets) {
                                FilterChip(
                                    selected = settings.wallpaperPresetId == id,
                                    onClick = { onUpdateSettings { it.copy(wallpaperPresetId = id) } },
                                    label = { Text(label) }
                                )
                            }
                        }

                        SettingSliderItem(
                            label = "Frosted Glass Surface Intensity: ${(settings.frostedGlassIntensity * 100).toInt()}%",
                            value = settings.frostedGlassIntensity,
                            valueRange = 0.40f..0.95f,
                            steps = 10,
                            onValueChange = { v -> onUpdateSettings { it.copy(frostedGlassIntensity = v) } }
                        )

                        SettingSliderItem(
                            label = "Blur Radius Strength: ${settings.blurStrengthDp} dp",
                            value = settings.blurStrengthDp.toFloat(),
                            valueRange = 4f..32f,
                            steps = 13,
                            onValueChange = { v -> onUpdateSettings { it.copy(blurStrengthDp = v.toInt()) } }
                        )

                        SettingSliderItem(
                            label = "Typography Font Scale: ${(settings.fontScale * 100).toInt()}%",
                            value = settings.fontScale,
                            valueRange = 0.85f..1.25f,
                            steps = 7,
                            onValueChange = { v -> onUpdateSettings { it.copy(fontScale = v) } }
                        )

                        SettingSwitchRow(
                            title = "Use JetBrains Mono Typography for App Labels",
                            checked = settings.useMonoFont,
                            onCheckedChange = { c -> onUpdateSettings { it.copy(useMonoFont = c) } }
                        )

                        Text("Icon Shape:", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (shapeOpt in IconShapeOption.entries) {
                                FilterChip(
                                    selected = settings.iconShape == shapeOpt.id,
                                    onClick = { onUpdateSettings { it.copy(iconShape = shapeOpt.id) } },
                                    label = { Text(shapeOpt.displayName) }
                                )
                            }
                        }

                        SettingSliderItem(
                            label = "Icon Scaling: ${(settings.iconScale * 100).toInt()}%",
                            value = settings.iconScale,
                            valueRange = 0.80f..1.20f,
                            steps = 7,
                            onValueChange = { v -> onUpdateSettings { it.copy(iconScale = v) } }
                        )

                        Text(
                            text = "Icon Pack Selection (ADW / Nova / Built-in Packs):",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        for (pack in state.iconPacks) {
                            FrostedGlassBox(
                                shape = RoundedCornerShape(14.dp),
                                alphaOverride = if (pack.isApplied) 0.85f else 0.52f,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onUpdateSettings { it.copy(activeIconPackPackage = pack.packageName) }
                                    }
                                    .testTag("icon_pack_option_${pack.packageName.ifEmpty { "default" }}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Palette,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Column {
                                            Text(
                                                text = pack.name,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = if (pack.packageName.isEmpty()) {
                                                    "Native system adaptive icons"
                                                } else {
                                                    pack.packageName
                                                },
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    if (pack.isApplied) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // TAB 3: GESTURES & SEARCH ENGINE
                    3 -> {
                        Text(
                            text = "Gesture Bindings & Unified Search",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )

                        val gestureActions = listOf(
                            "OPEN_DRAWER" to "Open App Drawer",
                            "OPEN_SEARCH" to "Open Unified Search",
                            "AI_ORGANIZE_PREVIEW" to "Open AI Organizer Preview"
                        )

                        Text("Swipe Up Gesture:", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for ((actId, label) in gestureActions) {
                                FilterChip(
                                    selected = settings.swipeUpAction == actId,
                                    onClick = { onUpdateSettings { it.copy(swipeUpAction = actId) } },
                                    label = { Text(label) }
                                )
                            }
                        }

                        Text("Swipe Down Gesture:", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for ((actId, label) in gestureActions) {
                                FilterChip(
                                    selected = settings.swipeDownAction == actId,
                                    onClick = { onUpdateSettings { it.copy(swipeDownAction = actId) } },
                                    label = { Text(label) }
                                )
                            }
                        }

                        Text("Double-Tap Home Gesture:", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for ((actId, label) in gestureActions) {
                                FilterChip(
                                    selected = settings.doubleTapAction == actId,
                                    onClick = { onUpdateSettings { it.copy(doubleTapAction = actId) } },
                                    label = { Text(label) }
                                )
                            }
                        }

                        Text("Default Web Search Engine:", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (engine in listOf("Google", "DuckDuckGo", "Brave", "Bing", "Startpage")) {
                                FilterChip(
                                    selected = settings.defaultSearchEngine == engine,
                                    onClick = { onUpdateSettings { it.copy(defaultSearchEngine = engine) } },
                                    label = { Text(engine) }
                                )
                            }
                        }

                        SettingSwitchRow(
                            title = "Enable Local Contacts in Unified Search",
                            checked = settings.enableContactsSearch,
                            onCheckedChange = { c -> onUpdateSettings { it.copy(enableContactsSearch = c) } }
                        )
                        SettingSwitchRow(
                            title = "Enable Local MediaStore Files in Unified Search",
                            checked = settings.enableFilesSearch,
                            onCheckedChange = { c -> onUpdateSettings { it.copy(enableFilesSearch = c) } }
                        )
                        SettingSwitchRow(
                            title = "Enable Web Search Suggestions",
                            checked = settings.enableWebSuggestions,
                            onCheckedChange = { c -> onUpdateSettings { it.copy(enableWebSuggestions = c) } }
                        )
                    }

                    // TAB 4: PRIVACY GUARANTEE & LOCAL BACKUP/RESTORE
                    4 -> {
                        FrostedGlassBox(
                            shape = RoundedCornerShape(20.dp),
                            alphaOverride = 0.68f,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Security,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                    Text(
                                        text = "100% On-Device Privacy & Offline AI Guarantee",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "• Zero Advertisements, Zero Analytics SDKs, Zero Cloud Telemetry.\n" +
                                        "• App Categorization runs strictly on-device via our embedded 2-Layer Neural Softmax + TF-IDF model (app_category_model.tflite).\n" +
                                        "• Installed app lists, contacts, files, and banking apps are never uploaded or inspected.\n" +
                                        "• Optional permissions (Contacts, Local Files) are used exclusively for local on-device search.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Text(
                            text = "Local Offline Backup & Restore (JSON)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onExportBackup,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("export_backup_button")
                            ) {
                                Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Generate Backup")
                            }

                            OutlinedButton(
                                onClick = {
                                    val payload = importJsonInput.ifBlank { state.backupJsonExportText }
                                    if (payload.isNotBlank()) {
                                        onImportBackup(payload)
                                    }
                                },
                                enabled = importJsonInput.isNotBlank() || state.backupJsonExportText.isNotBlank(),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("restore_backup_button")
                            ) {
                                Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Restore Backup")
                            }
                        }

                        if (state.backupJsonExportText.isNotBlank()) {
                            OutlinedTextField(
                                value = state.backupJsonExportText,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Exported Local Backup JSON (Copy or Restore directly)") },
                                maxLines = 6,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        OutlinedTextField(
                            value = importJsonInput,
                            onValueChange = { importJsonInput = it },
                            label = { Text("Paste Backup JSON to Restore") },
                            maxLines = 5,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingSliderItem(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps
        )
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
