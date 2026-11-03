package com.example.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
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
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Renders the user's real Android system wallpaper (when presetId == 0) with a subtle
 * frosted glass scrim when overlays are open, or optional built-in Pixel fluid presets (1..3).
 */
@Composable
fun PixelWallpaperBackground(
    presetId: Int,
    isDark: Boolean,
    isOverlayOpen: Boolean = false,
    modifier: Modifier = Modifier
) {
    // When presetId == 0 (default "System Live/Static Wallpaper"), let the user's real Android
    // wallpaper shine through directly! Only apply a soft frosted glass tint when an overlay is open.
    if (presetId == 0) {
        val overlayScrimAlpha = if (isOverlayOpen) {
            if (isDark) 0.36f else 0.28f
        } else {
            0f
        }
        Canvas(modifier = modifier.fillMaxSize()) {
            val h = size.height
            // Subtle top & bottom readability gradient so white clock/labels stay legible on any wallpaper
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.16f + overlayScrimAlpha),
                        Color.Black.copy(alpha = overlayScrimAlpha),
                        Color.Black.copy(alpha = 0.18f + overlayScrimAlpha)
                    ),
                    startY = 0f,
                    endY = h
                )
            )
        }
        return
    }

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
 * Reusable iOS/Pixel Frosted Glass surface with multi-layered specular sheen
 * and soft borderless glass luminance (matching the 4-app Home Screen dock).
 */
@Composable
fun FrostedGlassBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    alphaOverride: Float? = null,
    tintOverride: Color? = null,
    showBorder: Boolean = false,
    content: @Composable () -> Unit
) {
    val tokens = LocalLauncherTokens.current
    val surfaceAlpha = (alphaOverride ?: tokens.frostedSurfaceAlpha).coerceIn(0.14f, 0.94f)
    val baseColor = tintOverride ?: tokens.surfaceColor
    val topSpecular = if (tokens.isDarkTheme) {
        Color.White.copy(alpha = 0.14f)
    } else {
        Color.White.copy(alpha = 0.34f)
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        baseColor.copy(alpha = (surfaceAlpha + 0.08f).coerceAtMost(0.95f)),
                        baseColor.copy(alpha = surfaceAlpha),
                        baseColor.copy(alpha = (surfaceAlpha - 0.04f).coerceAtLeast(0.12f))
                    )
                )
            )
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        topSpecular,
                        Color.Transparent,
                        topSpecular.copy(alpha = 0.04f)
                    )
                )
            )
    ) {
        content()
    }
}

/**
 * Full-screen translucent Frosted Glass Page surface used by App Drawer, Unified Search,
 * In-Launcher Glass Internet Browser, and Customization sheets so the blurred system wallpaper
 * shines through with an authentic glassmorphic aesthetic.
 */
@Composable
fun FrostedGlassPageSurface(
    modifier: Modifier = Modifier,
    alphaMultiplier: Float = 0.54f,
    content: @Composable () -> Unit
) {
    val tokens = LocalLauncherTokens.current
    val effectiveAlpha = (tokens.frostedSurfaceAlpha * alphaMultiplier).coerceIn(0.28f, 0.64f)
    val baseColor = tokens.surfaceColor
    val topSheen = if (tokens.isDarkTheme) {
        Color(0xFF0F172A).copy(alpha = effectiveAlpha)
    } else {
        Color(0xFFF8FAFC).copy(alpha = effectiveAlpha)
    }
    val glassSpecular = if (tokens.isDarkTheme) {
        Color.White.copy(alpha = 0.07f)
    } else {
        Color.White.copy(alpha = 0.24f)
    }

    Surface(
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        topSheen,
                        baseColor.copy(alpha = (effectiveAlpha + 0.04f).coerceAtMost(0.70f)),
                        baseColor.copy(alpha = (effectiveAlpha + 0.08f).coerceAtMost(0.74f))
                    )
                )
            )
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        glassSpecular,
                        Color.Transparent,
                        glassSpecular.copy(alpha = 0.03f)
                    )
                )
            )
    ) {
        content()
    }
}

/**
 * Clean, Pixel-authentic At-a-Glance Header Widget showing only live Time & Date
 * alongside a small compact AI Arrange icon button and Settings button.
 */
@Composable
fun PixelAtAGlanceHeader(
    totalAppsCount: Int,
    categorizedCount: Int,
    isDefaultHome: Boolean,
    onOpenAiOrganizer: () -> Unit,
    onRequestDefaultHome: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenWeather: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val view = LocalView.current
    var currentMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentMillis = System.currentTimeMillis()
            delay(15_000L)
        }
    }

    val timeText = remember(currentMillis) {
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(currentMillis))
    }
    val dateText = remember(currentMillis) {
        SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date(currentMillis))
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("pixel_at_a_glance_header"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FrostedGlassBox(
            shape = RoundedCornerShape(30.dp),
            alphaOverride = 0.40f,
            showBorder = false,
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 22.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontSize = 39.sp,
                            fontWeight = FontWeight.Light
                        ),
                        color = Color.White,
                        maxLines = 1
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = dateText,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White.copy(alpha = 0.88f),
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(66.dp)
                        .background(Color.White.copy(alpha = 0.20f))
                )

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(22.dp))
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            onOpenWeather()
                        }
                        .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = "Weather",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                    Column {
                        Text(
                            text = "Weather",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Tap for forecast",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.78f),
                            maxLines = 1
                        )
                    }
                }

            }
        }
    }
}

/**
 * Compact centered frosted glass Search pill above the dock matching the user's screenshot.
 */
@Composable
fun PixelBottomSearchBar(
    onSearchBarClick: () -> Unit,
    onOpenDrawerClick: () -> Unit = {},
    onOpenAiOrganizerClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val view = LocalView.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        FrostedGlassBox(
            shape = RoundedCornerShape(32.dp),
            alphaOverride = 0.42f,
            showBorder = false,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .clickable {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onSearchBarClick()
                }
                .testTag("pixel_bottom_search_bar")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "G",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 29.sp
                    ),
                    color = Color.White
                )
                Text(
                    text = "Search anything...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.92f),
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Voice search",
                    tint = Color.White.copy(alpha = 0.94f),
                    modifier = Modifier.size(23.dp)
                )
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Visual search",
                    tint = Color.White.copy(alpha = 0.94f),
                    modifier = Modifier.size(23.dp)
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
    notificationCount: Int = 0,
    labelColor: Color = Color.White,
    enableInternalLongClick: Boolean = true,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = remember(iconShape) { resolveIconShape(iconShape) }
    val scaledSize = (iconSizeDp.value * iconScale.coerceIn(0.75f, 1.25f)).dp
    val haptics = LocalHapticFeedback.current
    val view = LocalView.current

    val clickModifier = if (enableInternalLongClick) {
        Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                onLongClick()
            }
        )
    } else {
        Modifier.clickable(onClick = onClick)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(14.dp))
            .then(clickModifier)
            .padding(vertical = 2.dp, horizontal = 2.dp)
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

            if (notificationCount > 0) {
                val badgeLabel = if (notificationCount > 99) "99+" else notificationCount.toString()
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .widthIn(min = 18.dp)
                        .height(18.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 4.dp)
                        .testTag("app_badge_${app.packageName}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = badgeLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
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
                modifier = Modifier.width(68.dp)
            )
        }
    }
}

/**
 * Expanded 2x2 or Compact 1x1 Home Screen Frosted Glass Folder Tile supporting:
 * 1) `1x1 Compact` when `isExpanded2x2 == false`
 * 2) `FolderDisplayStyle.BENTO_2X2`: 3 large direct-launchable circular app icons (Top-Left, Top-Right, Bottom-Left)
 *    + a 2x2 mini-grid in the Bottom-Right quadrant that opens the full folder modal when tapped.
 * 3) `FolderDisplayStyle.DENSE_3X3`: Up to 8 medium direct-launchable circular app icons in slots 0..7
 *    + a 2x2 mini-grid in the 9th (Bottom-Right) slot that opens the full folder modal when tapped.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherFolderTile(
    folder: FolderModel,
    memberApps: List<AppInfo>,
    iconSizeDp: Dp,
    iconScale: Float = 1.0f,
    showLabel: Boolean,
    isExpanded2x2: Boolean = true,
    unreadNotificationCount: Int = 0,
    enableInternalLongClick: Boolean = true,
    onLaunchApp: (AppInfo) -> Unit = {},
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val view = LocalView.current
    val folderShape = RoundedCornerShape(26.dp)
    val style = folder.displayStyle

    val folderClickModifier = if (enableInternalLongClick) {
        Modifier.combinedClickable(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onClick()
            },
            onLongClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                onLongClick()
            }
        )
    } else {
        Modifier.clickable {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onClick()
        }
    }

    if (!isExpanded2x2) {
        val scaledSize = (iconSizeDp.value * iconScale.coerceIn(0.75f, 1.25f)).dp
        val miniIconSize = (scaledSize.value * 0.235f).dp
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = modifier
                .minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(16.dp))
                .then(folderClickModifier)
                .padding(4.dp)
                .testTag("folder_tile_${folder.id}")
        ) {
            Box(contentAlignment = Alignment.TopEnd) {
                FrostedGlassBox(
                    shape = RoundedCornerShape(percent = 26),
                    alphaOverride = 0.42f,
                    showBorder = false,
                    modifier = Modifier.size(scaledSize)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding((scaledSize.value * 0.11f).dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        for (r in 0..2) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                for (c in 0..2) {
                                    val idx = r * 3 + c
                                    IosFolderMiniIcon(
                                        app = memberApps.getOrNull(idx),
                                        size = miniIconSize,
                                        showEmptyPlaceholder = false
                                    )
                                }
                            }
                        }
                    }
                }
                FolderUnreadBadge(folderId = folder.id, unreadNotificationCount = unreadNotificationCount)
            }
            if (showLabel) {
                Text(
                    text = folder.folderName,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.96f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
        return
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .testTag("folder_tile_${folder.id}")
    ) {
        Box(
            contentAlignment = Alignment.TopEnd,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            FrostedGlassBox(
                shape = folderShape,
                alphaOverride = 0.36f,
                showBorder = false,
                modifier = Modifier
                    .fillMaxSize()
                    .then(folderClickModifier)
            ) {
                if (style == com.example.domain.model.FolderDisplayStyle.BENTO_2X2) {
                    // Style 1: 2x2 Bento ("3 Large Circular Icons + Bottom-Right 2x2 Mini-Grid")
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.SpaceEvenly,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Top Row: Slot 0 (Top-Left) & Slot 1 (Top-Right)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                FolderInteractiveAppIcon(
                                    app = memberApps.getOrNull(0),
                                    iconSize = 44.dp,
                                    enableInternalLongClick = enableInternalLongClick,
                                    onLaunchApp = onLaunchApp,
                                    onOpenFolder = onClick,
                                    onLongClick = onLongClick
                                )
                            }
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                FolderInteractiveAppIcon(
                                    app = memberApps.getOrNull(1),
                                    iconSize = 44.dp,
                                    enableInternalLongClick = enableInternalLongClick,
                                    onLaunchApp = onLaunchApp,
                                    onOpenFolder = onClick,
                                    onLongClick = onLongClick
                                )
                            }
                        }

                        // Bottom Row: Slot 2 (Bottom-Left) & Bottom-Right 2x2 Mini-Grid
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                FolderInteractiveAppIcon(
                                    app = memberApps.getOrNull(2),
                                    iconSize = 44.dp,
                                    enableInternalLongClick = enableInternalLongClick,
                                    onLaunchApp = onLaunchApp,
                                    onOpenFolder = onClick,
                                    onLongClick = onLongClick
                                )
                            }

                            // Bottom-Right Quadrant: 2x2 Mini-Grid (Tapping opens full FolderDetailModal)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(12.dp))
                                    .then(folderClickModifier)
                                    .testTag("folder_mini_grid_${folder.id}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    modifier = Modifier.size(42.dp),
                                    verticalArrangement = Arrangement.SpaceBetween,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    for (mr in 0..1) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            for (mc in 0..1) {
                                                val appIdx = 3 + mr * 2 + mc
                                                IosFolderMiniIcon(
                                                    app = memberApps.getOrNull(appIdx),
                                                    size = 18.dp,
                                                    showEmptyPlaceholder = true
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Style 2: 3x3 Dense ("Up to 8 Medium Direct-Launch Icons + 9th Slot 2x2 Mini-Grid")
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(9.dp),
                        verticalArrangement = Arrangement.SpaceEvenly,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        for (r in 0..2) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                for (c in 0..2) {
                                    val slotIdx = r * 3 + c
                                    Box(
                                        modifier = Modifier.weight(1f),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (slotIdx < 8) {
                                            val app = memberApps.getOrNull(slotIdx)
                                            if (app != null) {
                                                FolderInteractiveAppIcon(
                                                    app = app,
                                                    iconSize = 30.dp,
                                                    enableInternalLongClick = enableInternalLongClick,
                                                    onLaunchApp = onLaunchApp,
                                                    onOpenFolder = onClick,
                                                    onLongClick = onLongClick
                                                )
                                            } else {
                                                Spacer(modifier = Modifier.size(30.dp))
                                            }
                                        } else {
                                            // 9th Slot (Bottom-Right): 2x2 Mini-Grid Cluster
                                            val showCluster = memberApps.size > 8
                                            Box(
                                                modifier = Modifier
                                                    .size(30.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .then(folderClickModifier)
                                                    .testTag("folder_mini_grid_${folder.id}"),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (showCluster) {
                                                    Column(
                                                        modifier = Modifier.size(28.dp),
                                                        verticalArrangement = Arrangement.SpaceBetween,
                                                        horizontalAlignment = Alignment.CenterHorizontally
                                                    ) {
                                                        for (mr in 0..1) {
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                for (mc in 0..1) {
                                                                    val appIdx = 8 + mr * 2 + mc
                                                                    IosFolderMiniIcon(
                                                                        app = memberApps.getOrNull(appIdx),
                                                                        size = 12.dp,
                                                                        showEmptyPlaceholder = true
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
                            }
                        }
                    }
                }
            }

            FolderUnreadBadge(folderId = folder.id, unreadNotificationCount = unreadNotificationCount)
        }

        if (showLabel) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = folder.folderName,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderInteractiveAppIcon(
    app: AppInfo?,
    iconSize: Dp,
    enableInternalLongClick: Boolean = true,
    onLaunchApp: (AppInfo) -> Unit,
    onOpenFolder: () -> Unit,
    onLongClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val view = LocalView.current

    if (app == null) {
        Box(
            modifier = Modifier
                .size(iconSize)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.14f))
                .clickable { onOpenFolder() }
        )
        return
    }

    val iconClickModifier = if (enableInternalLongClick) {
        Modifier.combinedClickable(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onLaunchApp(app)
            },
            onLongClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                onLongClick()
            }
        )
    } else {
        Modifier.clickable {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onLaunchApp(app)
        }
    }

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        tonalElevation = 1.dp,
        modifier = Modifier
            .size(iconSize)
            .clip(CircleShape)
            .then(iconClickModifier)
    ) {
        if (app.appIcon != null) {
            Image(
                bitmap = app.appIcon,
                contentDescription = app.appName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
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
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun FolderUnreadBadge(
    folderId: Long,
    unreadNotificationCount: Int
) {
    if (unreadNotificationCount > 0) {
        val badgeText = if (unreadNotificationCount > 99) "99+" else unreadNotificationCount.toString()
        Box(
            modifier = Modifier
                .offset(x = 4.dp, y = (-4).dp)
                .widthIn(min = 19.dp)
                .height(19.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 4.dp)
                .testTag("folder_badge_$folderId"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = badgeText,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@Composable
private fun IosFolderMiniIcon(
    app: AppInfo?,
    size: Dp,
    showEmptyPlaceholder: Boolean = false
) {
    if (app == null) {
        if (showEmptyPlaceholder) {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(RoundedCornerShape(percent = 28))
                    .background(Color.White.copy(alpha = 0.18f))
            )
        } else {
            Spacer(modifier = Modifier.size(size))
        }
        return
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.52f)),
        contentAlignment = Alignment.Center
    ) {
        if (app.appIcon != null) {
            Image(
                bitmap = app.appIcon,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        }
    }
}

