package com.bingkil.tuktuk.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TukTukLightColors = lightColorScheme(
    primary = TukTeal,
    onPrimary = TukSurface,
    primaryContainer = TukTeal,
    onPrimaryContainer = TukSurface,
    secondary = TukCoral,
    onSecondary = TukSurface,
    secondaryContainer = TukCoral,
    onSecondaryContainer = TukSurface,
    tertiary = TukPurple,
    onTertiary = TukSurface,
    tertiaryContainer = TukPurple,
    onTertiaryContainer = TukSurface,
    background = TukCream,
    onBackground = TukInk,
    surface = TukSurface,
    onSurface = TukInk,
    error = TukCoralDark,
    onError = TukSurface
)

private val TukTukDarkColors = darkColorScheme(
    primary = TukTeal,
    onPrimary = TukInk,
    primaryContainer = TukTealDark,
    onPrimaryContainer = TukSurface,
    secondary = TukCoral,
    onSecondary = TukInk,
    secondaryContainer = TukCoralDark,
    onSecondaryContainer = TukSurface,
    tertiary = TukPurple,
    onTertiary = TukSurface,
    tertiaryContainer = TukPurpleDark,
    onTertiaryContainer = TukSurface,
    background = TukInk,
    onBackground = TukCream,
    surface = Color(0xFF2E2340),
    onSurface = TukCream,
    error = TukCoral,
    onError = TukInk
)

@Composable
fun TukTukTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colorScheme = if (darkTheme) TukTukDarkColors else TukTukLightColors
    MaterialTheme(colorScheme = colorScheme, content = content)
}
