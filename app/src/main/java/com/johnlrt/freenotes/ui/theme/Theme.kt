package com.johnlrt.freenotes.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.johnlrt.freenotes.data.AppAccentPalette
import com.johnlrt.freenotes.data.AppThemeMode

/**
 * Exact Material 3 theme matching the colors extracted from `Freenotes 3.29.0.apk` (`resources.arsc`):
 * - Light: primary `#047AFF` (`colorPrimary`), bg `#F5F5F5` (`colorPopupBg`), surface `#FFFFFF` (`colorBrushToolsBg`),
 *   surfaceVariant `#EDEBEB` (`colorPrimarySecondary`), text `#191A1A` (`colorIconPrimaryLight`), divider `#D6D6D6`.
 * - Dark: primary `#0A84FF`, bg `#191A1A` (`common_ai_chat_bg_dark`), surface `#272828` (`common_ai_chat_elevated_bg_dark`),
 *   surfaceVariant `#333434` (`common_ai_chat_attachment_bg_dark`), text `#F4F5F5` (`colorIconPrimaryDark`), divider `#3A3A3C`.
 */
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
            onPrimary = Color.White,
            primaryContainer = Color(accentPalette.containerDarkArgb),
            onPrimaryContainer = Color(accentPalette.containerLightArgb),
            secondary = Color(0xFF2DD4BF),
            onSecondary = Color(0xFF042F2E),
            secondaryContainer = Color(0xFF115E59),
            onSecondaryContainer = Color(0xFFCCFBF1),
            tertiary = Color(0xFFEADC63),
            onTertiary = Color(0xFF191A1A),
            background = Color(0xFF191A1A),
            onBackground = Color(0xFFF4F5F5),
            surface = Color(0xFF272828),
            onSurface = Color(0xFFF4F5F5),
            surfaceVariant = Color(0xFF333434),
            onSurfaceVariant = Color(0xFFAAAAAA),
            outline = Color(0xFF3A3A3C)
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
            tertiary = Color(0xFFEADC63),
            onTertiary = Color(0xFF191A1A),
            background = Color(0xFFF5F5F5),
            onBackground = Color(0xFF191A1A),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF191A1A),
            surfaceVariant = Color(0xFFEDEBEB),
            onSurfaceVariant = Color(0xFF666666),
            outline = Color(0xFFD6D6D6)
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
