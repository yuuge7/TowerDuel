package com.towerduel.game.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.towerduel.game.ui.theme.Cream
import com.towerduel.game.ui.theme.Ink
import com.towerduel.game.ui.theme.Sun
import com.towerduel.game.ui.theme.Tomato
import com.towerduel.game.ui.theme.darken

enum class GameIconKind { COIN, HEART, PAUSE, FAST, SOUND_ON, SOUND_OFF, LOCK, TARGET, UP, CLOSE, CHECK, CLOCK, INCOME }

/** The game's own icon set, drawn in the same outlined style as the sprites. */
@Composable
fun GameIcon(kind: GameIconKind, modifier: Modifier = Modifier, tint: Color = Cream) {
    Canvas(modifier) { drawGameIcon(kind, tint) }
}

private fun DrawScope.outlined(path: Path, color: Color, outline: Float) {
    drawPath(path, Ink, style = Stroke(outline * 2f, join = StrokeJoin.Round, cap = StrokeCap.Round))
    drawPath(path, color)
}

private fun DrawScope.stroked(path: Path, color: Color, width: Float, outline: Float) {
    drawPath(path, Ink, style = Stroke(width + outline * 2f, join = StrokeJoin.Round, cap = StrokeCap.Round))
    drawPath(path, color, style = Stroke(width, join = StrokeJoin.Round, cap = StrokeCap.Round))
}

fun DrawScope.drawGameIcon(kind: GameIconKind, tint: Color) {
    val s = size.minDimension
    val ox = (size.width - s) / 2f
    val oy = (size.height - s) / 2f
    val o = s * 0.07f
    fun p(x: Float, y: Float) = Offset(ox + x * s, oy + y * s)
    val c = p(0.5f, 0.5f)

    when (kind) {
        GameIconKind.COIN, GameIconKind.INCOME -> {
            drawCircle(Ink, s * 0.44f, c)
            drawCircle(Sun, s * 0.44f - o, c)
            drawCircle(Sun.darken(0.25f), s * 0.27f, c, style = Stroke(s * 0.07f))
            drawCircle(Color.White.copy(alpha = 0.75f), s * 0.07f, p(0.36f, 0.33f))
            if (kind == GameIconKind.INCOME) {
                // A rising arrow over the coin
                val arrow = Path().apply {
                    moveTo(p(0.72f, 0.02f).x, p(0.72f, 0.02f).y)
                    lineTo(p(1f, 0.36f).x, p(1f, 0.36f).y)
                    lineTo(p(0.82f, 0.36f).x, p(0.82f, 0.36f).y)
                    lineTo(p(0.82f, 0.62f).x, p(0.82f, 0.62f).y)
                    lineTo(p(0.62f, 0.62f).x, p(0.62f, 0.62f).y)
                    lineTo(p(0.62f, 0.36f).x, p(0.62f, 0.36f).y)
                    lineTo(p(0.44f, 0.36f).x, p(0.44f, 0.36f).y)
                    close()
                }
                outlined(arrow, Color(0xFF5CC94C), o * 0.8f)
            }
        }
        GameIconKind.HEART -> {
            val heart = Path().apply {
                moveTo(p(0.5f, 0.9f).x, p(0.5f, 0.9f).y)
                cubicTo(p(0.02f, 0.56f).x, p(0.02f, 0.56f).y, p(0.06f, 0.14f).x, p(0.06f, 0.14f).y, p(0.3f, 0.14f).x, p(0.3f, 0.14f).y)
                cubicTo(p(0.42f, 0.14f).x, p(0.42f, 0.14f).y, p(0.5f, 0.24f).x, p(0.5f, 0.24f).y, p(0.5f, 0.32f).x, p(0.5f, 0.32f).y)
                cubicTo(p(0.5f, 0.24f).x, p(0.5f, 0.24f).y, p(0.58f, 0.14f).x, p(0.58f, 0.14f).y, p(0.7f, 0.14f).x, p(0.7f, 0.14f).y)
                cubicTo(p(0.94f, 0.14f).x, p(0.94f, 0.14f).y, p(0.98f, 0.56f).x, p(0.98f, 0.56f).y, p(0.5f, 0.9f).x, p(0.5f, 0.9f).y)
                close()
            }
            outlined(heart, Tomato, o)
            drawCircle(Color.White.copy(alpha = 0.7f), s * 0.07f, p(0.3f, 0.32f))
        }
        GameIconKind.PAUSE -> {
            for (x in listOf(0.2f, 0.56f)) {
                drawRoundRect(Ink, p(x - 0.04f, 0.12f), Size(s * 0.32f, s * 0.76f), CornerRadius(s * 0.1f))
                drawRoundRect(tint, p(x + 0.03f, 0.19f), Size(s * 0.18f, s * 0.62f), CornerRadius(s * 0.06f))
            }
        }
        GameIconKind.FAST -> {
            outlined(triangle(p(0.08f, 0.2f), p(0.5f, 0.5f), p(0.08f, 0.8f)), tint, o)
            outlined(triangle(p(0.5f, 0.2f), p(0.92f, 0.5f), p(0.5f, 0.8f)), tint, o)
        }
        GameIconKind.SOUND_ON, GameIconKind.SOUND_OFF -> {
            val speaker = Path().apply {
                moveTo(p(0.08f, 0.38f).x, p(0.08f, 0.38f).y)
                lineTo(p(0.26f, 0.38f).x, p(0.26f, 0.38f).y)
                lineTo(p(0.5f, 0.16f).x, p(0.5f, 0.16f).y)
                lineTo(p(0.5f, 0.84f).x, p(0.5f, 0.84f).y)
                lineTo(p(0.26f, 0.62f).x, p(0.26f, 0.62f).y)
                lineTo(p(0.08f, 0.62f).x, p(0.08f, 0.62f).y)
                close()
            }
            outlined(speaker, tint, o)
            if (kind == GameIconKind.SOUND_ON) {
                val wave = Path().apply {
                    moveTo(p(0.66f, 0.32f).x, p(0.66f, 0.32f).y)
                    cubicTo(p(0.8f, 0.4f).x, p(0.8f, 0.4f).y, p(0.8f, 0.6f).x, p(0.8f, 0.6f).y, p(0.66f, 0.68f).x, p(0.66f, 0.68f).y)
                }
                stroked(wave, tint, s * 0.09f, o * 0.8f)
            } else {
                val cross = Path().apply {
                    moveTo(p(0.64f, 0.36f).x, p(0.64f, 0.36f).y)
                    lineTo(p(0.92f, 0.64f).x, p(0.92f, 0.64f).y)
                    moveTo(p(0.92f, 0.36f).x, p(0.92f, 0.36f).y)
                    lineTo(p(0.64f, 0.64f).x, p(0.64f, 0.64f).y)
                }
                stroked(cross, Tomato, s * 0.1f, o * 0.8f)
            }
        }
        GameIconKind.LOCK -> {
            val shackle = Path().apply {
                moveTo(p(0.32f, 0.48f).x, p(0.32f, 0.48f).y)
                lineTo(p(0.32f, 0.34f).x, p(0.32f, 0.34f).y)
                cubicTo(p(0.32f, 0.1f).x, p(0.32f, 0.1f).y, p(0.68f, 0.1f).x, p(0.68f, 0.1f).y, p(0.68f, 0.34f).x, p(0.68f, 0.34f).y)
                lineTo(p(0.68f, 0.48f).x, p(0.68f, 0.48f).y)
            }
            stroked(shackle, tint.darken(0.15f), s * 0.1f, o)
            drawRoundRect(Ink, p(0.16f, 0.42f), Size(s * 0.68f, s * 0.5f), CornerRadius(s * 0.12f))
            drawRoundRect(tint, p(0.16f + 0.07f, 0.49f), Size(s * 0.54f, s * 0.36f), CornerRadius(s * 0.07f))
            drawCircle(Ink, s * 0.07f, p(0.5f, 0.66f))
        }
        GameIconKind.TARGET -> {
            drawCircle(Ink, s * 0.36f, c, style = Stroke(s * 0.1f + o * 2f))
            drawCircle(tint, s * 0.36f, c, style = Stroke(s * 0.1f))
            val cross = Path().apply {
                moveTo(p(0.5f, 0.04f).x, p(0.5f, 0.04f).y); lineTo(p(0.5f, 0.3f).x, p(0.5f, 0.3f).y)
                moveTo(p(0.5f, 0.7f).x, p(0.5f, 0.7f).y); lineTo(p(0.5f, 0.96f).x, p(0.5f, 0.96f).y)
                moveTo(p(0.04f, 0.5f).x, p(0.04f, 0.5f).y); lineTo(p(0.3f, 0.5f).x, p(0.3f, 0.5f).y)
                moveTo(p(0.7f, 0.5f).x, p(0.7f, 0.5f).y); lineTo(p(0.96f, 0.5f).x, p(0.96f, 0.5f).y)
            }
            stroked(cross, tint, s * 0.1f, o)
            drawCircle(Tomato, s * 0.09f, c)
        }
        GameIconKind.UP -> {
            val arrow = Path().apply {
                moveTo(p(0.5f, 0.06f).x, p(0.5f, 0.06f).y)
                lineTo(p(0.92f, 0.5f).x, p(0.92f, 0.5f).y)
                lineTo(p(0.66f, 0.5f).x, p(0.66f, 0.5f).y)
                lineTo(p(0.66f, 0.92f).x, p(0.66f, 0.92f).y)
                lineTo(p(0.34f, 0.92f).x, p(0.34f, 0.92f).y)
                lineTo(p(0.34f, 0.5f).x, p(0.34f, 0.5f).y)
                lineTo(p(0.08f, 0.5f).x, p(0.08f, 0.5f).y)
                close()
            }
            outlined(arrow, tint, o)
        }
        GameIconKind.CLOSE -> {
            val cross = Path().apply {
                moveTo(p(0.22f, 0.22f).x, p(0.22f, 0.22f).y); lineTo(p(0.78f, 0.78f).x, p(0.78f, 0.78f).y)
                moveTo(p(0.78f, 0.22f).x, p(0.78f, 0.22f).y); lineTo(p(0.22f, 0.78f).x, p(0.22f, 0.78f).y)
            }
            stroked(cross, tint, s * 0.16f, o)
        }
        GameIconKind.CHECK -> {
            val check = Path().apply {
                moveTo(p(0.16f, 0.52f).x, p(0.16f, 0.52f).y)
                lineTo(p(0.4f, 0.76f).x, p(0.4f, 0.76f).y)
                lineTo(p(0.86f, 0.24f).x, p(0.86f, 0.24f).y)
            }
            stroked(check, tint, s * 0.17f, o)
        }
        GameIconKind.CLOCK -> {
            drawCircle(Ink, s * 0.44f, c)
            drawCircle(Cream, s * 0.44f - o, c)
            val hands = Path().apply {
                moveTo(p(0.5f, 0.24f).x, p(0.5f, 0.24f).y)
                lineTo(c.x, c.y)
                lineTo(p(0.68f, 0.6f).x, p(0.68f, 0.6f).y)
            }
            drawPath(hands, Ink, style = Stroke(s * 0.1f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

private fun triangle(a: Offset, b: Offset, c: Offset): Path = Path().apply {
    moveTo(a.x, a.y)
    lineTo(b.x, b.y)
    lineTo(c.x, c.y)
    close()
}
