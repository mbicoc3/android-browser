package com.example.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.example.core.datastore.AppThemeMode

private val OrbitDarkColorScheme = darkColorScheme(
    primary = OrbitCyan,
    onPrimary = OrbitOnCyan,
    primaryContainer = OrbitCyanContainer,
    onPrimaryContainer = OrbitOnCyanContainer,
    secondary = OrbitIndigo,
    onSecondary = OrbitOnIndigo,
    secondaryContainer = OrbitIndigoContainer,
    onSecondaryContainer = OrbitDarkOnSurface,
    tertiary = OrbitEmerald,
    background = OrbitDarkBackground,
    onBackground = OrbitDarkOnSurface,
    surface = OrbitDarkSurface,
    onSurface = OrbitDarkOnSurface,
    surfaceVariant = OrbitDarkSurfaceVariant,
    onSurfaceVariant = OrbitDarkOnSurfaceVariant,
    surfaceContainerHigh = OrbitDarkSurfaceElevated,
    outline = OrbitDarkOutline,
    error = OrbitRoseError,
    onError = OrbitDarkOnError
)

private val OrbitLightColorScheme = lightColorScheme(
    primary = OrbitLightPrimary,
    onPrimary = OrbitLightSurface,
    primaryContainer = OrbitLightPrimaryContainer,
    onPrimaryContainer = OrbitLightOnPrimaryContainer,
    secondary = OrbitLightSecondary,
    onSecondary = OrbitLightSurface,
    tertiary = OrbitLightEmerald,
    background = OrbitLightBackground,
    onBackground = OrbitLightOnSurface,
    surface = OrbitLightSurface,
    onSurface = OrbitLightOnSurface,
    surfaceVariant = OrbitLightSurfaceVariant,
    onSurfaceVariant = OrbitLightOnSurfaceVariant,
    surfaceContainerHigh = OrbitLightSheetPanel,
    outline = OrbitLightOutline,
    error = OrbitLightError,
    onError = OrbitLightOnError
)

@Composable
fun isOrbitDarkTheme(themeMode: AppThemeMode): Boolean = when (themeMode) {
    AppThemeMode.LIGHT -> false
    AppThemeMode.DARK -> true
    AppThemeMode.SYSTEM -> isSystemInDarkTheme()
}

@Composable
fun OrbitBrowserTheme(
    themeMode: AppThemeMode = AppThemeMode.LIGHT,
    isIncognito: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = isOrbitDarkTheme(themeMode)
    val baseScheme = if (darkTheme) OrbitDarkColorScheme else OrbitLightColorScheme
    val colorScheme = if (isIncognito) {
        baseScheme.copy(
            primary = OrbitIncognitoPurple,
            surface = if (darkTheme) OrbitIncognitoSurface else baseScheme.surface,
            primaryContainer = OrbitIndigoContainer,
            onPrimaryContainer = OrbitDarkOnSurface
        )
    } else {
        baseScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = OrbitTypography,
        shapes = OrbitShapes,
        content = content
    )
}
