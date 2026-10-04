package com.towerduel.game.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.towerduel.game.data.EnemySendType
import com.towerduel.game.data.TroopType
import com.towerduel.game.engine.SoundCue
import com.towerduel.game.ui.render.drawTower
import com.towerduel.game.ui.render.drawUnit
import com.towerduel.game.ui.render.drawUnitShadow
import com.towerduel.game.ui.theme.Cream
import com.towerduel.game.ui.theme.Dim
import com.towerduel.game.ui.theme.Display
import com.towerduel.game.ui.theme.Ink
import com.towerduel.game.ui.theme.Night
import com.towerduel.game.ui.theme.NightDeep
import com.towerduel.game.ui.theme.Panel
import com.towerduel.game.ui.theme.PanelEdge
import com.towerduel.game.ui.theme.PanelLight
import com.towerduel.game.ui.theme.Sun
import com.towerduel.game.ui.theme.darken

/** Plays a UI sound. Provided at the root so any button can click without knowing who owns the audio. */
val LocalSfx = staticCompositionLocalOf<(SoundCue) -> Unit> { {} }

val OutlineWidth = 2.5.dp

/** The backdrop every screen sits on. */
val ScreenBackground = Brush.verticalGradient(listOf(Night, NightDeep))

/**
 * Text with a dark outline, the way every number and title in the game is lettered.
 * Two passes: the outline as a stroke, then the fill on top of it.
 */
@Composable
fun OutlinedText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 20.sp,
    color: Color = Cream,
    outline: Color = Ink,
    fontFamily: FontFamily = Display,
    textAlign: TextAlign = TextAlign.Start,
    letterSpacing: TextUnit = 0.5.sp,
    maxLines: Int = 1
) {
    val strokePx = with(LocalDensity.current) { fontSize.toPx() * 0.24f }
    val style = TextStyle(
        fontFamily = fontFamily,
        fontSize = fontSize,
        textAlign = textAlign,
        letterSpacing = letterSpacing,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    )
    Box(modifier, propagateMinConstraints = true) {
        Text(
            text, color = outline, maxLines = maxLines, softWrap = maxLines > 1, overflow = TextOverflow.Visible,
            style = style.copy(drawStyle = Stroke(width = strokePx, join = StrokeJoin.Round))
        )
        Text(text, color = color, maxLines = maxLines, softWrap = maxLines > 1, overflow = TextOverflow.Visible, style = style)
    }
}

/**
 * A raised, outlined button that sinks when pressed. The lip under the face is what makes it
 * read as a physical thing to push.
 */
@Composable
fun ChunkyButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Sun,
    enabled: Boolean = true,
    corner: Dp = 16.dp,
    depth: Dp = 5.dp,
    sound: SoundCue? = SoundCue.CLICK,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp),
    /** What a screen reader says for a button that shows only an icon. */
    description: String? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val sink by animateDpAsState(if (pressed && enabled) depth else 0.dp, tween(50), label = "sink")
    val face = if (enabled) color else PanelLight
    val sfx = LocalSfx.current

    Box(
        modifier = modifier
            .semantics { if (description != null) contentDescription = description }
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button) {
                if (sound != null) sfx(sound)
                onClick()
            }
            .drawBehind {
                val o = OutlineWidth.toPx()
                val d = depth.toPx()
                val p = sink.toPx()
                val r = corner.toPx()
                val faceHeight = size.height - 2f * o - d
                drawRoundRect(Ink, Offset(0f, p), Size(size.width, size.height - p), CornerRadius(r))
                drawRoundRect(face.darken(0.4f), Offset(o, o + p), Size(size.width - 2f * o, size.height - 2f * o - p), CornerRadius(r - o))
                drawRoundRect(face, Offset(o, o + p), Size(size.width - 2f * o, faceHeight), CornerRadius(r - o))
                if (enabled) {
                    val inset = 3.dp.toPx()
                    drawRoundRect(
                        Color.White.copy(alpha = 0.25f),
                        Offset(o + inset, o + p + inset),
                        Size(size.width - 2f * (o + inset), minOf(faceHeight * 0.4f, 12.dp.toPx())),
                        CornerRadius((r - o - inset).coerceAtLeast(0f))
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.padding(contentPadding).padding(bottom = depth).offset(y = sink),
            contentAlignment = Alignment.Center,
            content = content
        )
    }
}

@Composable
fun ChunkyTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Sun,
    enabled: Boolean = true,
    fontSize: TextUnit = 22.sp,
    sound: SoundCue? = SoundCue.CLICK
) {
    ChunkyButton(onClick, modifier, color, enabled, sound = sound) {
        OutlinedText(text, fontSize = fontSize, color = if (enabled) Cream else Dim, modifier = Modifier.offset(y = fontSize.value.dp * 0.08f))
    }
}

/** An outlined card. [color] is its fill. */
@Composable
fun GamePanel(
    modifier: Modifier = Modifier,
    color: Color = Panel,
    corner: Dp = 18.dp,
    edge: Color = Ink,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(corner)
    Box(
        modifier = modifier
            .clip(shape)
            .background(color)
            .drawBehind {
                // A lit line along the top edge, so the panel looks raised rather than flat.
                val y = OutlineWidth.toPx() + 1.dp.toPx()
                drawLine(
                    PanelEdge.copy(alpha = 0.55f),
                    Offset(corner.toPx(), y), Offset(size.width - corner.toPx(), y),
                    strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round
                )
            }
            .border(OutlineWidth, edge, shape),
        content = content
    )
}

/** A small dark capsule with an icon and a number: lives, gold, rounds. */
@Composable
fun HudPill(
    icon: GameIconKind,
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = Cream,
    fontSize: TextUnit = 17.sp
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .clip(shape)
            .background(NightDeep.copy(alpha = 0.82f))
            .border(2.dp, Ink, shape)
            .padding(start = 5.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        GameIcon(icon, Modifier.size((fontSize.value + 3f).dp))
        OutlinedText(text, fontSize = fontSize, color = textColor, modifier = Modifier.offset(y = 1.5.dp))
    }
}

/** A tower drawn with the battlefield's own sprite code, so UI and lane can never drift apart. */
@Composable
fun TowerPortrait(type: TroopType, modifier: Modifier = Modifier, level: Int = 0) {
    Canvas(modifier) {
        val u = size.minDimension / 8.6f
        drawTower(type, level, if (level >= 2) 2 else 1, size.width / 2f, size.height / 2f - 0.2f * u, u, -0.6f, 10_000f, 0f)
    }
}

@Composable
fun UnitPortrait(type: EnemySendType, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        // How far the sprite reaches from its ground point, in body radii: up to whatever it
        // wears (crown, healer badge) or how high it hovers, down to its shadow, and sideways.
        val up = when {
            type.flying -> 2.4f
            type.healPerSecond > 0f -> 2.1f
            type.id == "boss" -> 1.6f
            else -> 1.15f
        }
        val down = 1.2f
        val across = if (type.flying) 4f else 2.4f
        val u = minOf(size.height / ((up + down) * type.radius), size.width / (across * type.radius))
        val cx = size.width / 2f
        val ground = (size.height - (up + down) * type.radius * u) / 2f + up * type.radius * u
        drawUnitShadow(type, cx, ground, u)
        drawUnit(type, cx, ground, u, 1f, 0f, 0f, 0, hpFrac = 1f, flash = 0f, slowed = false, stunned = false, poisoned = false)
    }
}
