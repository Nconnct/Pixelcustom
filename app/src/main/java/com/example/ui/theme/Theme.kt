package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class LauncherDesignTokens(
    val primaryColor: Color = PixelBluePrimaryDark,
    val secondaryColor: Color = PixelSecondaryMintDark,
    val surfaceColor: Color = ObsidianSurface,
    val backgroundColor: Color = ObsidianBackground,
    val textPrimaryColor: Color = Color(0xFFF8FAFC),
    val textSecondaryColor: Color = Color(0xFFCBD5E1),
    val iconSizeDp: Dp = 54.dp,
    val drawerIconSizeDp: Dp = 52.dp,
    val searchCornerRadius: Dp = 28.dp,
    val cardCornerRadius: Dp = 22.dp,
    val folderCornerRadius: Dp = 24.dp,
    val gridSpacing: Dp = 10.dp,
    val screenHorizontalPadding: Dp = 16.dp,
    val shortAnimationMs: Int = 180,
    val mediumAnimationMs: Int = 260,
    val blurRadiusDp: Dp = 18.dp,
    val frostedSurfaceAlpha: Float = 0.74f,
    val dockSurfaceAlpha: Float = 0.65f,
    val isDarkTheme: Boolean = true
)

val LocalLauncherTokens = staticCompositionLocalOf { LauncherDesignTokens() }

private val DarkColorScheme = darkColorScheme(
    primary = PixelBluePrimaryDark,
    secondary = PixelSecondaryMintDark,
    tertiary = PixelTertiaryAmberDark,
    background = ObsidianBackground,
    surface = ObsidianSurface,
    surfaceVariant = ObsidianSurfaceVariant,
    onPrimary = Color(0xFF041E49),
    onSecondary = Color(0xFF072711),
    onBackground = Color(0xFFF8FAFC),
    onSurface = Color(0xFFF8FAFC),
    onSurfaceVariant = Color(0xFFCBD5E1)
)

private val LightColorScheme = lightColorScheme(
    primary = PixelBluePrimaryLight,
    secondary = PixelSecondaryMintLight,
    tertiary = PixelTertiaryAmberLight,
    background = PorcelainBackground,
    surface = PorcelainSurface,
    surfaceVariant = PorcelainSurfaceVariant,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A),
    onSurfaceVariant = Color(0xFF334155)
)

@Composable
fun PixelCategoryLauncherTheme(
    themeMode: String = "SYSTEM", // "SYSTEM", "DARK", "LIGHT"
    useDynamicWallpaperColors: Boolean = true,
    accentColorName: String = "Pixel Blue",
    iconSizeDp: Int = 54,
    drawerIconSizeDp: Int = 52,
    blurStrengthDp: Int = 18,
    frostedIntensity: Float = 0.74f,
    dockTransparency: Float = 0.68f,
    fontScale: Float = 1.0f,
    useMonoFont: Boolean = false,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        "DARK" -> true
        "LIGHT" -> false
        else -> systemDark
    }

    val context = LocalContext.current
    val baseScheme = when {
        useDynamicWallpaperColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val customAccent = AccentColors[accentColorName]
    val finalScheme = if (!useDynamicWallpaperColors && customAccent != null) {
        baseScheme.copy(primary = customAccent)
    } else {
        baseScheme
    }

    val tokens = LauncherDesignTokens(
        primaryColor = finalScheme.primary,
        secondaryColor = finalScheme.secondary,
        surfaceColor = if (darkTheme) FrostedDarkTint else FrostedLightTint,
        backgroundColor = finalScheme.background,
        textPrimaryColor = finalScheme.onSurface,
        textSecondaryColor = finalScheme.onSurfaceVariant,
        iconSizeDp = iconSizeDp.dp,
        drawerIconSizeDp = drawerIconSizeDp.dp,
        blurRadiusDp = blurStrengthDp.dp,
        frostedSurfaceAlpha = frostedIntensity.coerceIn(0.35f, 0.96f),
        dockSurfaceAlpha = dockTransparency.coerceIn(0.20f, 0.95f),
        isDarkTheme = darkTheme
    )

    CompositionLocalProvider(LocalLauncherTokens provides tokens) {
        MaterialTheme(
            colorScheme = finalScheme,
            typography = buildLauncherTypography(fontScale, useMonoFont),
            content = content
        )
    }
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    PixelCategoryLauncherTheme(
        themeMode = if (darkTheme) "DARK" else "LIGHT",
        useDynamicWallpaperColors = dynamicColor,
        content = content
    )
}
