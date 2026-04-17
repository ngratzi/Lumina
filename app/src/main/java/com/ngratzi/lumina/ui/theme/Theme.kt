package com.ngratzi.lumina.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

val LocalSkyTheme = staticCompositionLocalOf { SkyThemeState() }

@Composable
fun LuminaTheme(
    skyTheme: SkyThemeState,
    content: @Composable () -> Unit,
) {
    val palette = skyTheme.palette

    val colorScheme = darkColorScheme(
        background          = palette.gradientTop,
        surface             = palette.surfaceDim,
        surfaceContainer    = palette.surfaceContainer,
        outline             = palette.outlineColor,
        onBackground        = palette.onSurface,
        onSurface           = palette.onSurface,
        onSurfaceVariant    = palette.onSurfaceVariant,
        primary             = palette.accent,
        onPrimary           = palette.gradientTop,
    )

    CompositionLocalProvider(LocalSkyTheme provides skyTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = LuminaTypography,
            content     = content,
        )
    }
}
