package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val PlusJakartaSansFamily = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold)
)

val InterFamily = FontFamily(
    Font(R.font.inter, FontWeight.Normal),
    Font(R.font.inter, FontWeight.Medium),
    Font(R.font.inter, FontWeight.SemiBold)
)

val JetBrainsMonoFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono, FontWeight.Medium)
)

fun buildLauncherTypography(fontScale: Float = 1.0f, useMonoLabels: Boolean = false): Typography {
    val bodyFamily = if (useMonoLabels) JetBrainsMonoFamily else InterFamily
    return Typography(
        displayLarge = TextStyle(
            fontFamily = PlusJakartaSansFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = (44 * fontScale).sp,
            lineHeight = (52 * fontScale).sp,
            letterSpacing = (-0.5).sp
        ),
        headlineMedium = TextStyle(
            fontFamily = PlusJakartaSansFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = (24 * fontScale).sp,
            lineHeight = (32 * fontScale).sp
        ),
        titleLarge = TextStyle(
            fontFamily = PlusJakartaSansFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = (19 * fontScale).sp,
            lineHeight = (26 * fontScale).sp
        ),
        titleMedium = TextStyle(
            fontFamily = PlusJakartaSansFamily,
            fontWeight = FontWeight.Medium,
            fontSize = (15 * fontScale).sp,
            lineHeight = (22 * fontScale).sp
        ),
        bodyLarge = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (15 * fontScale).sp,
            lineHeight = (22 * fontScale).sp
        ),
        bodyMedium = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (13 * fontScale).sp,
            lineHeight = (18 * fontScale).sp
        ),
        labelLarge = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.Medium,
            fontSize = (12 * fontScale).sp,
            lineHeight = (16 * fontScale).sp
        ),
        labelSmall = TextStyle(
            fontFamily = JetBrainsMonoFamily,
            fontWeight = FontWeight.Medium,
            fontSize = (10 * fontScale).sp,
            lineHeight = (14 * fontScale).sp
        )
    )
}

val Typography = buildLauncherTypography()
