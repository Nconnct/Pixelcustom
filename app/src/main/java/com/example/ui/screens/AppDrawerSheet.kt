package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.domain.model.AppInfo
import com.example.domain.model.CategoryModel
import com.example.domain.model.DefaultCategory
import com.example.domain.model.DrawerSortMode
import com.example.domain.model.IconShapeOption
import com.example.ui.components.FrostedGlassBox
import com.example.ui.components.LauncherAppTile
import com.example.ui.viewmodel.LauncherUiState

@Composable
fun AppDrawerSheet(
    state: LauncherUiState,
    filteredApps: List<AppInfo>,
    onCloseDrawer: () -> Unit,
    onSelectCategory: (String) -> Unit,
    onUpdateSearchQuery: (String) -> Unit,
    onChangeSortMode: (String) -> Unit,
    onLaunchApp: (AppInfo) -> Unit,
    onLongPressApp: (AppInfo) -> Unit,
    onApproveSuggestion: (AppInfo) -> Unit,
    onCreateOrUpdateCategory: (String?, String, String) -> Unit,
    onToggleCategoryVisibility: (String, Boolean) -> Unit,
    onReorderCategory: (String, Boolean) -> Unit,
    onDeleteCategory: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onCloseDrawer)

    val focusManager = LocalFocusManager.current
    val settings = state.settings
    val iconShape = remember(settings.iconShape) {
        IconShapeOption.fromId(settings.iconShape)
    }
    val visibleCategories = remember(state.categories) {
        state.categories.filter { it.isVisibleInDrawer }
    }

    // Local text state so typing in the drawer search bar is 100% instant and never drops characters
    var localSearchText by remember { mutableStateOf(state.drawerSearchQuery) }
    LaunchedEffect(state.drawerSearchQuery) {
        if (localSearchText != state.drawerSearchQuery) {
            localSearchText = state.drawerSearchQuery
        }
    }

    // Only show AI suggestion banner when viewing the "Uncategorized" tab
    val uncertainSuggestions = remember(filteredApps, state.selectedCategoryId) {
        if (state.selectedCategoryId == DefaultCategory.UNCATEGORIZED.id) {
            filteredApps.filter { it.suggestedCategoryId != null && !it.isUserOverridden }.take(2)
        } else {
            emptyList()
        }
    }

    val showConfidenceBadges = settings.drawerSortMode == DrawerSortMode.AI_CONFIDENCE.id

    var showSortDropdown by remember { mutableStateOf(false) }
    var showCategoryManagerModal by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
        contentColor = MaterialTheme.colorScheme.onBackground,
        modifier = modifier
            .fillMaxSize()
            .testTag("app_drawer_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(top = 8.dp)
        ) {
            // Drawer Handle Pill
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.40f))
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Top Search & Action Bar inside App Drawer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = onCloseDrawer,
                    modifier = Modifier.testTag("close_drawer_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Home",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                OutlinedTextField(
                    value = localSearchText,
                    onValueChange = { newText ->
                        localSearchText = newText
                        onUpdateSearchQuery(newText)
                    },
                    placeholder = {
                        Text(
                            text = "Search apps…",
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
                        if (localSearchText.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    localSearchText = ""
                                    onUpdateSearchQuery("")
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            focusManager.clearFocus()
                            filteredApps.firstOrNull()?.let { onLaunchApp(it) }
                        }
                    ),
                    shape = RoundedCornerShape(26.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("drawer_search_input")
                )

                Box {
                    IconButton(
                        onClick = { showSortDropdown = true },
                        modifier = Modifier.testTag("drawer_sort_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "Sort Apps",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    DropdownMenu(
                        expanded = showSortDropdown,
                        onDismissRequest = { showSortDropdown = false }
                    ) {
                        for (mode in DrawerSortMode.entries) {
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(mode.displayName)
                                        if (settings.drawerSortMode == mode.id) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    onChangeSortMode(mode.id)
                                    showSortDropdown = false
                                }
                            )
                        }
                    }
                }

                IconButton(
                    onClick = { showCategoryManagerModal = true },
                    modifier = Modifier.testTag("open_category_manager_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Category,
                        contentDescription = "Manage Categories",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Frosted Category Tabs
            if (settings.showCategoryTabs) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("drawer_category_tabs_row")
                ) {
                    items(visibleCategories, key = { it.id }) { cat ->
                        val isSelected = cat.id == state.selectedCategoryId
                        FrostedGlassBox(
                            shape = RoundedCornerShape(20.dp),
                            alphaOverride = if (isSelected) 0.95f else 0.55f,
                            tintOverride = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            modifier = Modifier
                                .clickable { onSelectCategory(cat.id) }
                                .testTag("category_tab_${cat.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = cat.name,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                                if (cat.appCount > 0) {
                                    Text(
                                        text = "${cat.appCount}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Offline AI Uncertain Category Suggestion Banner (shown in Uncategorized tab)
            if (uncertainSuggestions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (suggestedApp in uncertainSuggestions) {
                        FrostedGlassBox(
                            shape = RoundedCornerShape(16.dp),
                            alphaOverride = 0.78f,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Move ${suggestedApp.appName} to '${suggestedApp.suggestedCategoryName}'?",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                TextButton(onClick = { onApproveSuggestion(suggestedApp) }) {
                                    Text("Approve")
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // App Grid
            if (filteredApps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Category,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (localSearchText.isNotBlank()) {
                                "No apps matching \"$localSearchText\""
                            } else {
                                "No apps in this category yet"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Long-press any app in 'All Apps' to assign it to this category.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(settings.drawerGridColumns.coerceIn(3, 6)),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("drawer_apps_grid")
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        LauncherAppTile(
                            app = app,
                            iconSizeDp = settings.drawerIconSizeDp.dp,
                            iconScale = settings.iconScale,
                            iconShape = iconShape,
                            showLabel = settings.showDrawerAppLabels,
                            showCategoryBadge = showConfidenceBadges,
                            labelColor = MaterialTheme.colorScheme.onSurface,
                            onClick = { onLaunchApp(app) },
                            onLongClick = { onLongPressApp(app) }
                        )
                    }
                }
            }
        }
    }

    if (showCategoryManagerModal) {
        CategoryManagerModal(
            categories = state.categories,
            onDismiss = { showCategoryManagerModal = false },
            onSaveCategory = onCreateOrUpdateCategory,
            onToggleVisibility = onToggleCategoryVisibility,
            onReorder = onReorderCategory,
            onDelete = onDeleteCategory
        )
    }
}

@Composable
private fun CategoryManagerModal(
    categories: List<CategoryModel>,
    onDismiss: () -> Unit,
    onSaveCategory: (String?, String, String) -> Unit,
    onToggleVisibility: (String, Boolean) -> Unit,
    onReorder: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit
) {
    var editingCategoryId by remember { mutableStateOf<String?>(null) }
    var categoryNameInput by remember { mutableStateOf("") }
    var categoryColorInput by remember { mutableStateOf("#4285F4") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage Categories (${categories.size})") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = categoryNameInput,
                    onValueChange = { categoryNameInput = it },
                    label = {
                        Text(if (editingCategoryId == null) "New Custom Category Name" else "Rename Category")
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_category_name_input")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (categoryNameInput.isNotBlank()) {
                                onSaveCategory(editingCategoryId, categoryNameInput, categoryColorInput)
                                editingCategoryId = null
                                categoryNameInput = ""
                            }
                        },
                        enabled = categoryNameInput.isNotBlank(),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_category_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (editingCategoryId == null) "Create Category" else "Save Rename")
                    }
                    if (editingCategoryId != null) {
                        TextButton(
                            onClick = {
                                editingCategoryId = null
                                categoryNameInput = ""
                            }
                        ) {
                            Text("Cancel")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                for (cat in categories) {
                    FrostedGlassBox(
                        shape = RoundedCornerShape(14.dp),
                        alphaOverride = 0.55f,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${cat.name} (${cat.appCount})",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (cat.isSystemDefault) "Built-in Category" else "Custom Category",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { onReorder(cat.id, true) },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ArrowUpward,
                                        contentDescription = "Move Up",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onReorder(cat.id, false) },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ArrowDownward,
                                        contentDescription = "Move Down",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        editingCategoryId = cat.id
                                        categoryNameInput = cat.name
                                        categoryColorInput = cat.colorHex
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Rename Category",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onToggleVisibility(cat.id, !cat.isVisibleInDrawer) },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = if (cat.isVisibleInDrawer) {
                                            Icons.Default.Visibility
                                        } else {
                                            Icons.Default.VisibilityOff
                                        },
                                        contentDescription = "Toggle Visibility",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                if (!cat.isSystemDefault) {
                                    IconButton(
                                        onClick = { onDelete(cat.id) },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete Category",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
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
