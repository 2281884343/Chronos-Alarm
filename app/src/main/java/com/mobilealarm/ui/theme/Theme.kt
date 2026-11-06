package com.mobilealarm.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightScheme = lightColorScheme(
    primary = Vermilion,
    onPrimary = PaperRaised,
    primaryContainer = PaperSunken,
    onPrimaryContainer = Ink,
    secondary = Moss,
    onSecondary = PaperRaised,
    secondaryContainer = MossWash,
    onSecondaryContainer = Ink,
    tertiary = Ink3,
    onTertiary = PaperRaised,
    tertiaryContainer = PaperSunken,
    onTertiaryContainer = Ink,
    background = Paper,
    onBackground = Ink,
    surface = PaperRaised,
    onSurface = Ink,
    surfaceVariant = PaperSunken,
    onSurfaceVariant = Ink3,
    outline = Hairline,
    outlineVariant = Hairline,
    error = Vermilion,
    onError = PaperRaised,
    errorContainer = VermilionWash,
    onErrorContainer = Color(0xFF4A1614),
    surfaceTint = Paper,
)

private val DarkScheme = darkColorScheme(
    primary = NightInk,
    onPrimary = Night,
    primaryContainer = Color(0xFF262A23),
    onPrimaryContainer = NightInk,
    secondary = Color(0xFFC8B98F),
    onSecondary = Night,
    secondaryContainer = Color(0xFF30342B),
    onSecondaryContainer = NightInk,
    tertiary = NightInk2,
    onTertiary = Night,
    tertiaryContainer = Color(0xFF262A23),
    onTertiaryContainer = NightInk,
    background = Night,
    onBackground = NightInk,
    surface = NightRaised,
    onSurface = NightInk,
    surfaceVariant = Color(0xFF262A23),
    onSurfaceVariant = NightInk2,
    outline = NightHairline,
    outlineVariant = NightHairline,
    error = Color(0xFFE0A090),
    onError = Night,
    errorContainer = Color(0xFF43241F),
    onErrorContainer = Color(0xFFF3D8D0),
    surfaceTint = Night,
)

/**
 * 主界面刻意固定为纸色：这是产品识别的一部分。
 * 响铃页由 AlarmActivity 单独传入 darkTheme = true。
 */
@Composable
fun MobileAlarmTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkScheme else LightScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = MobileAlarmTypography,
        content = content,
    )
}
