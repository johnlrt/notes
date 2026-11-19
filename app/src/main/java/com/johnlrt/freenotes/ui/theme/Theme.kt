package com.johnlrt.freenotes.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.johnlrt.freenotes.data.AppAccentPalette
import com.johnlrt.freenotes.data.AppThemeMode

@Composable
fun FreeNotesTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    accentPalette: AppAccentPalette = AppAccentPalette.COBALT,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = Color(accentPalette.primaryDarkArgb),
            onPrimary = Color(0xFF0F172A),
            primaryContainer = Color(accentPalette.containerDarkArgb),
            onPrimaryContainer = Color(accentPalette.containerLightArgb),
            secondary = Color(0xFF2DD4BF),
            onSecondary = Color(0xFF042F2E),
            secondaryContainer = Color(0xFF115E59),
            onSecondaryContainer = Color(0xFFCCFBF1),
            tertiary = Color(0xFFFBBF24),
            onTertiary = Color(0xFF1C1917),
            background = Color(0xFF111318),
            onBackground = Color(0xFFF1F5F9),
            surface = Color(0xFF1B1E26),
            onSurface = Color(0xFFF1F5F9),
            surfaceVariant = Color(0xFF262B36),
            onSurfaceVariant = Color(0xFF94A3B8),
            outline = Color(0xFF333B4A)
        )
    } else {
        lightColorScheme(
            primary = Color(accentPalette.primaryLightArgb),
            onPrimary = Color.White,
            primaryContainer = Color(accentPalette.containerLightArgb),
            onPrimaryContainer = Color(accentPalette.containerDarkArgb),
            secondary = Color(0xFF0D9488),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFCCFBF1),
            onSecondaryContainer = Color(0xFF134E4A),
            tertiary = Color(0xFFF59E0B),
            onTertiary = Color(0xFF1C1917),
            background = Color(0xFFF1F4F9),
            onBackground = Color(0xFF0F172A),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF0F172A),
            surfaceVariant = Color(0xFFE9EEF5),
            onSurfaceVariant = Color(0xFF475569),
            outline = Color(0xFFD5DCE6)
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
