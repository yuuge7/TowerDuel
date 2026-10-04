package com.towerduel.game.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.towerduel.game.R

/** Chunky all-caps display face: titles, buttons and every number in the HUD. */
val Display = FontFamily(Font(R.font.luckiest_guy))

@OptIn(ExperimentalTextApi::class)
private fun fredoka(weight: FontWeight) = Font(
    R.font.fredoka, weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight))
)

/** Rounded body face for anything that has to be read rather than glanced at. */
val Body = FontFamily(
    fredoka(FontWeight.Normal),
    fredoka(FontWeight.Medium),
    fredoka(FontWeight.SemiBold),
    fredoka(FontWeight.Bold)
)

val AppTypography = Typography(
    headlineLarge = TextStyle(fontFamily = Display, fontSize = 40.sp),
    headlineMedium = TextStyle(fontFamily = Display, fontSize = 28.sp),
    titleLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
    bodyMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 13.sp),
    labelSmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.6.sp)
)
