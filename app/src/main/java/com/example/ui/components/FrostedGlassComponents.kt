package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.model.AppInfo
import com.example.domain.model.FolderModel
import com.example.domain.model.IconShapeOption
import com.example.ui.theme.FrostedBorderDark
import com.example.ui.theme.FrostedBorderLight
import com.example.ui.theme.LocalLauncherTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Renders an atmospheric Pixel-inspired multi-layered fluid wallpaper background.
 */
@Composable
fun PixelWallpaperBackground(
    presetId: Int,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val palette = remember(presetId, isDark) {
        when (presetId % 4) {
            1 -> listOf(Color(0xFF06202B), Color(0xFF074173), Color(0xFF1679AB), Color(0xFF5DEBD7))
            2 -> listOf(Color(0xFF1A1025), Color(0xFF3B1C54), Color(0xFF6B2D5C), Color(0xFFF0A868))
            3 -> listOf(Color(0xFF0A1F17), Color(0xFF134E3A), Color(0xFF0D9488), Color(0xFFA7F3D0))
            else -> if (isDark) {
                listOf(Color(0xFF090D16), Color(0xFF13203C), Color(0xFF1E3A8A), Color(0xFF0F766E))
            } else {
                listOf(Color(0xFF1E293B), Color(0xFF334155), Color(0xFF2563EB), Color(0xFF0D9488))
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(palette[0], palette[1], palette[0]),
                startY = 0f,
                endY = h
            )
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(palette[2].copy(alpha = 0.40f), Color.Transparent),
                center = Offset(w * 0.82f, h * 0.22f),
                radius = w * 0.72f
            ),
            radius = w * 0.72f,
            center = Offset(w * 0.82f, h * 0.22f)
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(palette[3].copy(alpha = 0.24f), Color.Transparent),
                center = Offset(w * 0.18f, h * 0.68f),
                radius = w * 0.65f
            ),
            radius = w * 0.65f,
            center = Offset(w * 0.18f, h * 0.68f)
        )
    }
}

/**
 * Reusable Frosted Glass surface with contrast-aware translucency and subtle specular border.
 */
@Composable
fun FrostedGlassBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    alphaOverride: Float? = null,
    tintOverride: Color? = null,
    content: @Composable () -> Unit
) {
    val tokens = LocalLauncherTokens.current
    val surfaceAlpha = (alphaOverride ?: tokens.frostedSurfaceAlpha).coerceIn(0.25f, 0.98f)
    val baseColor = tintOverride ?: tokens.surfaceColor
    val borderColor = if (tokens.isDarkTheme) FrostedBorderDark else FrostedBorderLight

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        baseColor.copy(alpha = (surfaceAlpha + 0.05f).coerceAtMost(0.98f)),
                        baseColor.copy(alpha = surfaceAlpha)
                    )
                )
            )
            .border(width = 1.dp, color = borderColor, shape = shape)
    ) {
        content()
    }
}

/**
 * Clean, Pixel-authentic At-a-Glance Header Widget.
 */
@Composable
fun PixelAtAGlanceHeader(
    totalAppsCount: Int,
    categorizedCount: Int,
    isDefaultHome: Boolean,
    onOpenAiOrganizer: () -> Unit,
    onRequestDefaultHome: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateText = remember {
        SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date())
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .testTag("pixel_at_a_glance_header"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = dateText,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color(0xFF81C995),
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = "$categorizedCount/$totalAppsCount Categorized • Offline AI",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.80f)
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FrostedGlassBox(
                shape = CircleShape,
                alphaOverride = 0.58f,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clickable(onClick = onOpenAiOrganizer)
                    .testTag("ai_organize_header_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = stringResource(R.string.ai_organize_title),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Arrange",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(42.dp)
                    .testTag("home_settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = stringResource(R.string.settings_title),
                    tint = Color.White.copy(alpha = 0.90f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * Pixel-style Frosted Bottom Search Pill with immediate Unified Search and App Drawer triggers.
 */
@Composable
fun PixelBottomSearchBar(
    onSearchBarClick: () -> Unit,
    onOpenDrawerClick: () -> Unit,
    onOpenAiOrganizerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalLauncherTokens.current

    FrostedGlassBox(
        shape = RoundedCornerShape(tokens.searchCornerRadius),
        alphaOverride = tokens.frostedSurfaceAlpha,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("pixel_bottom_search_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Tapping the main search area opens Unified Search and focuses the input field
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(tokens.searchCornerRadius))
                    .clickable(onClick = onSearchBarClick)
                    .padding(horizontal = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(21.dp)
                )
                Text(
                    text = stringResource(R.string.search_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onOpenDrawerClick,
                modifier = Modifier
                    .size(44.dp)
                    .testTag("open_app_drawer_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Apps,
                    contentDescription = stringResource(R.string.drawer_title),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * Returns the Shape corresponding to the user's selected IconShapeOption.
 */
fun resolveIconShape(option: IconShapeOption): Shape {
    return when (option) {
        IconShapeOption.CIRCLE -> CircleShape
        IconShapeOption.SQUIRCLE -> RoundedCornerShape(percent = 32)
        IconShapeOption.ROUNDED_SQUARE -> RoundedCornerShape(percent = 22)
        IconShapeOption.TEARDROP -> RoundedCornerShape(
            topStartPercent = 50,
            topEndPercent = 50,
            bottomStartPercent = 50,
            bottomEndPercent = 14
        )
        IconShapeOption.SHARP -> GenericShape { size, _ ->
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
    }
}

/**
 * Clean, responsive App Icon + Label tile.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherAppTile(
    app: AppInfo,
    iconSizeDp: Dp,
    iconScale: Float,
    iconShape: IconShapeOption,
    showLabel: Boolean,
    showCategoryBadge: Boolean = false,
    labelColor: Color = Color.White,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = remember(iconShape) { resolveIconShape(iconShape) }
    val scaledSize = (iconSizeDp.value * iconScale.coerceIn(0.75f, 1.25f)).dp

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(vertical = 4.dp, horizontal = 4.dp)
            .testTag("app_tile_${app.packageName}")
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Surface(
                shape = shape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                tonalElevation = 2.dp,
                modifier = Modifier.size(scaledSize)
            ) {
                if (app.appIcon != null) {
                    Image(
                        bitmap = app.appIcon,
                        contentDescription = app.appName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(shape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = app.appName.take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (showCategoryBadge) {
                val confPct = (app.categoryConfidence * 100).toInt()
                val badgeBg = if (app.isUserOverridden) {
                    MaterialTheme.colorScheme.primary
                } else if (confPct >= 80) {
                    Color(0xFF059669)
                } else {
                    Color(0xFFD97706)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(badgeBg)
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = if (app.isUserOverridden) "PIN" else "$confPct%",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                        color = Color.White
                    )
                }
            }
        }

        if (showLabel) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = app.appName,
                style = MaterialTheme.typography.labelLarge,
                color = labelColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(74.dp)
            )
        }
    }
}

/**
 * Renders a 2x2 Frosted Glass Smart Folder icon on the Home Screen.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherFolderTile(
    folder: FolderModel,
    memberApps: List<AppInfo>,
    iconSizeDp: Dp,
    showLabel: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(vertical = 4.dp, horizontal = 4.dp)
            .testTag("folder_tile_${folder.id}")
    ) {
        FrostedGlassBox(
            shape = RoundedCornerShape(percent = 30),
            alphaOverride = 0.72f,
            modifier = Modifier.size(iconSizeDp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                for (r in 0..1) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        for (c in 0..1) {
                            val idx = r * 2 + c
                            val app = memberApps.getOrNull(idx)
                            Box(
                                modifier = Modifier
                                    .size((iconSizeDp.value * 0.34f).dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (app?.appIcon != null) {
                                    Image(
                                        bitmap = app.appIcon,
                                        contentDescription = app.appName,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else if (app != null) {
                                    Text(
                                        text = app.appName.take(1).uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showLabel) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = folder.folderName,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(74.dp)
            )
        }
    }
}
