package com.example.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
 * Reusable iOS/Pixel Frosted Glass surface with multi-layered specular sheen,
 * diagonal glass luminance, and optional specular border.
 */
@Composable
fun FrostedGlassBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    alphaOverride: Float? = null,
    tintOverride: Color? = null,
    showBorder: Boolean = true,
    content: @Composable () -> Unit
) {
    val tokens = LocalLauncherTokens.current
    val surfaceAlpha = (alphaOverride ?: tokens.frostedSurfaceAlpha).coerceIn(0.14f, 0.94f)
    val baseColor = tintOverride ?: tokens.surfaceColor
    val borderColor = if (tokens.isDarkTheme) {
        Color.White.copy(alpha = 0.22f)
    } else {
        Color.White.copy(alpha = 0.52f)
    }
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
            .then(
                if (showBorder) {
                    Modifier.border(width = 1.dp, color = borderColor, shape = shape)
                } else {
                    Modifier
                }
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
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .testTag("pixel_at_a_glance_header"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = timeText,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = dateText,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.86f),
                fontWeight = FontWeight.Medium
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Small, compact AI Arrange icon button
            FrostedGlassBox(
                shape = CircleShape,
                alphaOverride = 0.56f,
                modifier = Modifier
                    .size(34.dp)
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        onOpenAiOrganizer()
                    }
                    .testTag("ai_organize_header_button")
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = stringResource(R.string.ai_organize_title),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            IconButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onOpenSettings()
                },
                modifier = Modifier
                    .size(34.dp)
                    .testTag("home_settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = stringResource(R.string.settings_title),
                    tint = Color.White.copy(alpha = 0.90f),
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}

/**
 * Pixel-style Frosted Bottom Search Pill (App Drawer button removed — swipe up opens the App Drawer).
 */
@Composable
fun PixelBottomSearchBar(
    onSearchBarClick: () -> Unit,
    onOpenDrawerClick: () -> Unit = {},
    onOpenAiOrganizerClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val tokens = LocalLauncherTokens.current
    val haptics = LocalHapticFeedback.current
    val view = LocalView.current

    FrostedGlassBox(
        shape = RoundedCornerShape(tokens.searchCornerRadius),
        alphaOverride = tokens.frostedSurfaceAlpha,
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
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
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
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
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = remember(iconShape) { resolveIconShape(iconShape) }
    val scaledSize = (iconSizeDp.value * iconScale.coerceIn(0.75f, 1.25f)).dp
    val haptics = LocalHapticFeedback.current
    val view = LocalView.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    onLongClick()
                }
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
                        .border(1.5.dp, Color(0xFF0F172A), CircleShape)
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
                modifier = Modifier.width(74.dp)
            )
        }
    }
}

/**
 * Adaptive Modern iOS App Library-style Smart Folder tile on the Home Screen:
 * - Few apps (1..3): Compact iOS Frosted Squircle Folder
 * - Medium apps (4..5): Expanded iOS App Library Pod with 3 direct-launch squircles + mini cluster
 * - Many apps (6+): Big iOS App Library Bento Folder with header pill, 3 large app icons, and 2x2 mini cluster
 * Dynamically grows in size as apps are added to the folder, and displays the unread notification count badge on the top-right corner.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherFolderTile(
    folder: FolderModel,
    memberApps: List<AppInfo>,
    iconSizeDp: Dp,
    showLabel: Boolean,
    unreadNotificationCount: Int = 0,
    onLaunchApp: (AppInfo) -> Unit = {},
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val view = LocalView.current
    val appCount = memberApps.size

    // Adaptive sizing tier based on number of apps inside the folder:
    // Tier 0 (Small: 1..3 apps), Tier 1 (Medium: 4..5 apps), Tier 2 (Big iOS App Library: 6+ apps)
    val sizeTier = when {
        appCount >= 6 -> 2
        appCount >= 4 -> 1
        else -> 0
    }

    val boxWidth = when (sizeTier) {
        2 -> (iconSizeDp.value * 2.45f).dp
        1 -> (iconSizeDp.value * 1.75f).dp
        else -> (iconSizeDp.value * 1.16f).dp
    }
    val boxHeight = when (sizeTier) {
        2 -> (iconSizeDp.value * 1.72f).dp
        1 -> (iconSizeDp.value * 1.38f).dp
        else -> (iconSizeDp.value * 1.16f).dp
    }
    val cornerRadius = when (sizeTier) {
        2 -> 28.dp
        1 -> 24.dp
        else -> 22.dp
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(cornerRadius))
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    onLongClick()
                }
            )
            .padding(vertical = 4.dp, horizontal = 4.dp)
            .testTag("folder_tile_${folder.id}")
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            FrostedGlassBox(
                shape = RoundedCornerShape(cornerRadius),
                alphaOverride = 0.44f,
                modifier = Modifier
                    .width(boxWidth)
                    .height(boxHeight)
            ) {
                when (sizeTier) {
                    // Tier 2: Big iOS App Library Bento Folder (6+ apps)
                    2 -> {
                        val primarySlotSize = (iconSizeDp.value * 0.56f).dp
                        val miniClusterSlotSize = (iconSizeDp.value * 0.24f).dp
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Top Row: 2 Primary App Squircles + 1 Primary App Squircle
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                for (idx in 0..2) {
                                    val app = memberApps.getOrNull(idx)
                                    IosFolderAppSquircle(
                                        app = app,
                                        slotSize = primarySlotSize,
                                        onTap = {
                                            if (app != null) {
                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onLaunchApp(app)
                                            } else {
                                                onClick()
                                            }
                                        }
                                    )
                                }
                            }

                            // Bottom Row: 4th Primary App + 2x2 iOS Mini-Icon Cluster + App Count Pill
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val fourthApp = memberApps.getOrNull(3)
                                IosFolderAppSquircle(
                                    app = fourthApp,
                                    slotSize = primarySlotSize,
                                    onTap = {
                                        if (fourthApp != null) {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onLaunchApp(fourthApp)
                                        } else {
                                            onClick()
                                        }
                                    }
                                )

                                // iOS 2x2 Mini Cluster (tapping opens full folder modal)
                                Box(
                                    modifier = Modifier
                                        .size(primarySlotSize)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color.White.copy(alpha = 0.14f))
                                        .clickable(onClick = onClick)
                                        .padding(3.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        verticalArrangement = Arrangement.SpaceEvenly,
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        for (mr in 0..1) {
                                            Row(
                                                horizontalArrangement = Arrangement.SpaceEvenly,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                for (mc in 0..1) {
                                                    val subApp = memberApps.getOrNull(4 + mr * 2 + mc)
                                                    IosFolderMiniIcon(
                                                        app = subApp,
                                                        size = miniClusterSlotSize
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Frosted iOS App Count Pill
                                Box(
                                    modifier = Modifier
                                        .height(primarySlotSize * 0.68f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.White.copy(alpha = 0.16f))
                                        .clickable(onClick = onClick)
                                        .padding(horizontal = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${memberApps.size} apps",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }

                    // Tier 1: Medium Expanded iOS Folder (4..5 apps)
                    1 -> {
                        val primarySlotSize = (iconSizeDp.value * 0.48f).dp
                        val miniSlotSize = (iconSizeDp.value * 0.20f).dp
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            verticalArrangement = Arrangement.SpaceEvenly,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                for (idx in 0..1) {
                                    val app = memberApps.getOrNull(idx)
                                    IosFolderAppSquircle(
                                        app = app,
                                        slotSize = primarySlotSize,
                                        onTap = {
                                            if (app != null) onLaunchApp(app) else onClick()
                                        }
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val thirdApp = memberApps.getOrNull(2)
                                IosFolderAppSquircle(
                                    app = thirdApp,
                                    slotSize = primarySlotSize,
                                    onTap = {
                                        if (thirdApp != null) onLaunchApp(thirdApp) else onClick()
                                    }
                                )
                                // 4th quadrant: 2x2 mini cluster for remaining apps
                                Box(
                                    modifier = Modifier
                                        .size(primarySlotSize)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.White.copy(alpha = 0.14f))
                                        .clickable(onClick = onClick)
                                        .padding(2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        IosFolderMiniIcon(app = memberApps.getOrNull(3), size = miniSlotSize)
                                        IosFolderMiniIcon(app = memberApps.getOrNull(4), size = miniSlotSize)
                                    }
                                }
                            }
                        }
                    }

                    // Tier 0: Compact Small iOS Squircle Folder (1..3 apps)
                    else -> {
                        val miniSize = (iconSizeDp.value * 0.38f).dp
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(7.dp),
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
                                        IosFolderMiniIcon(app = app, size = miniSize)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Dynamic Unread Count Number Badge on the Folder Corner (keeps user informed without opening folder)
            if (unreadNotificationCount > 0) {
                val badgeText = if (unreadNotificationCount > 99) "99+" else unreadNotificationCount.toString()
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 5.dp, y = (-5).dp)
                        .widthIn(min = 21.dp)
                        .height(21.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .border(1.5.dp, Color(0xFF0F172A), CircleShape)
                        .padding(horizontal = 5.dp)
                        .testTag("folder_badge_${folder.id}"),
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

        if (showLabel) {
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = folder.folderName,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = boxWidth + 16.dp)
            )
        }
    }
}

@Composable
private fun IosFolderAppSquircle(
    app: AppInfo?,
    slotSize: Dp,
    onTap: () -> Unit
) {
    val squircleShape = RoundedCornerShape(percent = 26)
    Box(
        modifier = Modifier
            .size(slotSize)
            .clip(squircleShape)
            .background(Color.White.copy(alpha = 0.18f))
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center
    ) {
        if (app?.appIcon != null) {
            Image(
                bitmap = app.appIcon,
                contentDescription = app.appName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(squircleShape)
            )
        } else if (app != null) {
            Text(
                text = app.appName.take(1).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun IosFolderMiniIcon(
    app: AppInfo?,
    size: Dp
) {
    val squircleShape = RoundedCornerShape(percent = 28)
    Box(
        modifier = Modifier
            .size(size)
            .clip(squircleShape)
            .background(
                if (app != null) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                } else {
                    Color.White.copy(alpha = 0.08f)
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (app?.appIcon != null) {
            Image(
                bitmap = app.appIcon,
                contentDescription = app.appName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(squircleShape)
            )
        } else if (app != null) {
            Text(
                text = app.appName.take(1).uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
