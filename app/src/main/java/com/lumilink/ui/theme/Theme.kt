package com.lumilink.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Two fixed brand color schemes (not Android 12 "dynamic color") so LumiLink always looks
// like LumiLink. Each maps our palette onto Material 3's semantic slots.

private val LightColors = lightColorScheme(
    primary = AmberDeep,
    onPrimary = OnAmberLight,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    error = CriticalRed,
)

private val DarkColors = darkColorScheme(
    primary = AmberBright,
    onPrimary = OnAmberDark,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    error = CriticalRed,
)

/**
 * Wraps the whole app UI. Kotlin note: a `@Composable` function with a trailing `content`
 * lambda is the standard Compose "wrapper" pattern — callers write `LumiLinkTheme { ... }`.
 */
@Composable
fun LumiLinkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = LumiLinkTypography,
        content = content,
    )
}
