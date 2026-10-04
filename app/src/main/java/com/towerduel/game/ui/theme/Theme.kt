package com.towerduel.game.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkScheme = darkColorScheme(
    primary = Sun,
    secondary = Leaf,
    tertiary = Sky,
    error = Tomato,
    background = NightDeep,
    surface = Panel,
    onBackground = Cream,
    onSurface = Cream,
    onPrimary = Ink,
    onSecondary = Ink,
    onTertiary = Ink
)

@Composable
fun TowerDuelTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkScheme,
        typography = AppTypography,
        content = content
    )
}
