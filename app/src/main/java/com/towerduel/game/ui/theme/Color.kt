package com.towerduel.game.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

// Everything drawn in the game, sprites and UI alike, is outlined in this one ink colour.
val Ink = Color(0xFF1B1430)

// Night-sky surfaces: the UI stays dark so the daylight battlefield is the brightest thing on screen.
val NightDeep = Color(0xFF150F2E)
val Night = Color(0xFF231A4C)
val Panel = Color(0xFF32276A)
val PanelLight = Color(0xFF45388C)
val PanelEdge = Color(0xFF6C5CC7)

// Candy accents
val Sun = Color(0xFFFFC93C)
val Leaf = Color(0xFF5CC94C)
val Tomato = Color(0xFFF2543D)
val Sky = Color(0xFF3FA7F5)
val Frost = Color(0xFF8FE9F7)

val Cream = Color(0xFFFFF6DD)
val Lilac = Color(0xFFB9AEE6)
val Dim = Color(0xFF7D72B0)

val PlayerColor = Sky
val AiColor = Tomato

fun Color.darken(amount: Float): Color = lerp(this, Ink, amount).copy(alpha = alpha)
fun Color.lighten(amount: Float): Color = lerp(this, Color.White, amount).copy(alpha = alpha)
