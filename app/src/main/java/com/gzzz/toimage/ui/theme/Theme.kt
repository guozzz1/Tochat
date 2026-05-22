package com.gzzz.toimage.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = AccentBlue,
    onPrimary = DarkGray900,
    primaryContainer = AccentBlueDark,
    onPrimaryContainer = DarkGray100,
    secondary = DarkGray400,
    onSecondary = DarkGray100,
    secondaryContainer = DarkGray600,
    onSecondaryContainer = DarkGray200,
    tertiary = AccentBlue,
    background = DarkGray900,
    onBackground = DarkGray100,
    surface = DarkGray800,
    onSurface = DarkGray100,
    surfaceVariant = DarkGray700,
    onSurfaceVariant = DarkGray200,
    outline = DarkGray400,
    error = ErrorRed,
    onError = DarkGray900
)

private val LightColorScheme = lightColorScheme(
    primary = AccentBlueDark,
    onPrimary = LightGray50,
    primaryContainer = AccentBlue,
    onPrimaryContainer = LightGray900,
    secondary = LightGray700,
    onSecondary = LightGray50,
    secondaryContainer = LightGray200,
    onSecondaryContainer = LightGray900,
    background = LightGray50,
    onBackground = LightGray900,
    surface = LightGray100,
    onSurface = LightGray900,
    surfaceVariant = LightGray200,
    onSurfaceVariant = LightGray700,
    error = ErrorRed,
    onError = LightGray50
)

@Composable
fun ToimageTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
