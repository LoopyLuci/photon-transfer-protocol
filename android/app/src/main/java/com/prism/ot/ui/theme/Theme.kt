package com.prism.ot.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val PrismDarkColorScheme = darkColorScheme(
    primary = PrismColors.Cyan,
    onPrimary = PrismColors.Background,
    primaryContainer = PrismColors.SurfaceElevated,
    onPrimaryContainer = PrismColors.TextPrimary,
    secondary = PrismColors.Violet,
    onSecondary = PrismColors.Background,
    tertiary = PrismColors.Amber,
    onTertiary = PrismColors.Background,
    background = PrismColors.Background,
    onBackground = PrismColors.TextPrimary,
    surface = PrismColors.Surface,
    onSurface = PrismColors.TextPrimary,
    surfaceVariant = PrismColors.SurfaceElevated,
    onSurfaceVariant = PrismColors.TextSecondary,
    outline = PrismColors.Outline,
    error = PrismColors.Error,
    onError = PrismColors.Background,
)

/**
 * PRISM theme.
 *
 * The app is dark-only by design. A light variant would invert the
 * contrast relationship between the camera preview and the overlay, making
 * the scan-line and detection boxes hard to read. [isSystemInDarkTheme]
 * is therefore deliberately ignored.
 */
@Composable
fun PrismTheme(
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = PrismColors.Background.toArgb()
            window.navigationBarColor = PrismColors.Background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = PrismDarkColorScheme,
        typography = PrismTypography,
        content = content,
    )
}
