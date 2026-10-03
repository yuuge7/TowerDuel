package com.towerduel.game.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkScheme = darkColorScheme(
    primary = AccentTeal,
    secondary = AccentGold,
    tertiary = AccentBlue,
    error = AccentRed,
    background = BgDark,
    surface = BgPanel,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onPrimary = BgDark,
    onSecondary = BgDark,
    onTertiary = BgDark
)

@Composable
fun TowerDuelTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkScheme,
        typography = AppTypography,
        content = content
    )
}
