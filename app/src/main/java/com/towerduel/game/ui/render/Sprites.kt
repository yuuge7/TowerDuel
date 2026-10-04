package com.towerduel.game.ui.render

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.towerduel.game.data.EnemySendType
import com.towerduel.game.data.ShotKind
import com.towerduel.game.data.TroopType
import com.towerduel.game.ui.theme.Frost
import com.towerduel.game.ui.theme.Ink
import com.towerduel.game.ui.theme.Leaf
import com.towerduel.game.ui.theme.Sun
import com.towerduel.game.ui.theme.Tomato
import com.towerduel.game.ui.theme.darken
import com.towerduel.game.ui.theme.lighten
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// Every sprite is drawn from circles, slabs and a few paths, sized in lane units: `u` is the
// number of pixels in one lane unit, so the same code draws the battlefield and the UI portraits.

/** Outline thickness, in lane units. */
internal const val OUTLINE = 0.32f

private val Stone = Color(0xFFDCE1EA)
private val StoneDark = Color(0xFFAEB7C7)
private val Steel = Color(0xFF55607A)
private val Gunmetal = Color(0xFF3A3448)
internal val Shadow = Color(0x40150F2E)

// Compose drawing is single-threaded, so one scratch path is enough for every sprite.
private val scratchPath = Path()

internal fun DrawScope.blob(color: Color, cx: Float, cy: Float, r: Float, ow: Float) {
    drawCircle(Ink, r + ow, Offset(cx, cy))
    drawCircle(color, r, Offset(cx, cy))
}

internal fun DrawScope.slab(color: Color, left: Float, top: Float, w: Float, h: Float, corner: Float, ow: Float) {
    drawRoundRect(Ink, Offset(left - ow, top - ow), Size(w + 2f * ow, h + 2f * ow), CornerRadius(corner + ow))
    drawRoundRect(color, Offset(left, top), Size(w, h), CornerRadius(corner))
}

/** Fills the scratch path from (x, y) pairs and draws it outlined. */
internal fun DrawScope.shape(color: Color, ow: Float, vararg points: Float) {
    scratchPath.rewind()
    scratchPath.moveTo(points[0], points[1])
    var i = 2
    while (i < points.size) {
        scratchPath.lineTo(points[i], points[i + 1])
        i += 2
    }
    scratchPath.close()
    if (ow > 0f) drawPath(scratchPath, Ink, style = Stroke(ow * 2f, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    drawPath(scratchPath, color)
}

// ---------------------------------------------------------------------------
// Towers
// ---------------------------------------------------------------------------

/**
 * @param aimRad where the barrel points, in radians
 * @param sinceFiredMs time since the last shot, for the recoil kick
 */
fun DrawScope.drawTower(
    type: TroopType, level: Int, shots: Int,
    cx: Float, cy: Float, u: Float,
    aimRad: Float, sinceFiredMs: Float, timeMs: Float
) {
    val ow = OUTLINE * u
    val maxed = type.maxLevel > 0 && level >= type.maxLevel

    drawOval(Shadow, Offset(cx - 3.4f * u, cy - 1.3f * u), Size(6.8f * u, 5.2f * u))
    blob(if (maxed) Sun else Stone, cx, cy, 3f * u, ow)
    drawCircle(if (maxed) Sun.darken(0.22f) else StoneDark, 2.4f * u, Offset(cx, cy))

    val kick = (1f - sinceFiredMs / 130f).coerceIn(0f, 1f) * 0.7f * u
    val aimDeg = aimRad * 180f / PI.toFloat()
    val color = type.color

    when (type.id) {
        "sentry" -> turret(cx, cy, u, aimDeg, kick, color, 1.95f, barrels = shots.coerceIn(1, 2), spread = 1.25f, length = 2.9f, width = 0.95f)
        "sniper" -> turret(cx, cy, u, aimDeg, kick, color, 1.7f, barrels = 1, spread = 0f, length = 4.3f, width = 0.62f, muzzle = 1.05f)
        "bomb" -> turret(cx, cy, u, aimDeg, kick, color, 2.05f, barrels = 1, spread = 0f, length = 2.6f, width = 2f, barrelColor = Gunmetal, muzzle = 2.5f)
        "gatling" -> turret(cx, cy, u, aimDeg, kick * 0.5f, color, 1.85f, barrels = 3, spread = 0.62f, length = 3f, width = 0.42f)
        "antiair" -> turret(cx, cy, u, aimDeg, kick, color, 1.95f, barrels = 2, spread = 2.1f, length = 2.9f, width = 0.7f, muzzle = 1f)
        "stun" -> {
            turret(cx, cy, u, aimDeg, kick, color, 1.85f, barrels = 2, spread = 1.7f, length = 2.8f, width = 0.5f)
            // The charge crackling between the prongs
            val pulse = 0.5f + 0.5f * sin(timeMs * 0.012f)
            val tipX = cx + cos(aimRad) * 3f * u
            val tipY = cy + sin(aimRad) * 3f * u
            drawCircle(Color.White.copy(alpha = 0.5f + 0.5f * pulse), (0.35f + 0.25f * pulse) * u, Offset(tipX, tipY))
        }
        "mortar" -> {
            blob(color, cx, cy, 2.25f * u, ow)
            val tx = cx + cos(aimRad) * (0.55f * u - kick)
            val ty = cy + sin(aimRad) * (0.55f * u - kick)
            blob(Gunmetal, tx, ty, 1.6f * u, ow)
            drawCircle(Ink, 1.05f * u, Offset(tx, ty))
        }
        "chain" -> {
            blob(Color(0xFF5B4FD0), cx, cy, 1.95f * u, ow)
            drawCircle(Color(0xFF7D72E8), 1.35f * u, Offset(cx, cy), style = Stroke(0.3f * u))
            val charged = (1f - sinceFiredMs / 260f).coerceIn(0f, 1f)
            if (charged > 0f) drawCircle(Color.White.copy(alpha = 0.6f * charged), (1.6f + charged) * u, Offset(cx, cy - 0.3f * u))
            blob(color, cx, cy - 0.3f * u, 1.15f * u, ow)
            drawCircle(Color.White.copy(alpha = 0.8f), 0.4f * u, Offset(cx - 0.35f * u, cy - 0.7f * u))
            // Idle sparks hopping around the coil
            val spark = (timeMs * 0.004f + cx) % (2f * PI.toFloat())
            drawCircle(Color.White, 0.28f * u, Offset(cx + cos(spark) * 1.75f * u, cy - 0.3f * u + sin(spark) * 1.75f * u))
        }
        "frost" -> {
            val glow = 0.5f + 0.5f * sin(timeMs * 0.003f)
            drawCircle(Color.White.copy(alpha = 0.18f + 0.14f * glow), 2.3f * u, Offset(cx, cy))
            shape(color.darken(0.18f), ow, cx - 2.1f * u, cy + 0.4f * u, cx - 1.1f * u, cy - 1.1f * u, cx - 0.3f * u, cy + 0.9f * u)
            shape(color.darken(0.18f), ow, cx + 2.1f * u, cy + 0.4f * u, cx + 1.1f * u, cy - 1.1f * u, cx + 0.3f * u, cy + 0.9f * u)
            shape(color, ow, cx, cy - 2.7f * u, cx + 1.35f * u, cy - 0.3f * u, cx, cy + 1.9f * u, cx - 1.35f * u, cy - 0.3f * u)
            shape(Color.White.copy(alpha = 0.75f), 0f, cx, cy - 2.2f * u, cx + 0.5f * u, cy - 0.4f * u, cx, cy + 0.2f * u, cx - 0.9f * u, cy - 0.3f * u)
        }
        "poison" -> {
            blob(Gunmetal, cx, cy, 2.15f * u, ow)
            drawCircle(color, 1.6f * u, Offset(cx, cy))
            drawCircle(color.lighten(0.35f), 1.6f * u, Offset(cx, cy), style = Stroke(0.25f * u))
            for (i in 0 until 3) {
                val phase = (timeMs * 0.0016f + i * 0.37f) % 1f
                val bx = cx + (i - 1) * 0.75f * u
                val by = cy + 0.5f * u - phase * 1.1f * u
                drawCircle(color.lighten(0.55f).copy(alpha = 1f - phase), (0.2f + 0.3f * phase) * u, Offset(bx, by))
            }
        }
        "goldmine" -> {
            blob(Color(0xFF9A6A3A), cx, cy, 2.25f * u, ow)
            drawOval(Ink, Offset(cx - 1.5f * u, cy - 1.5f * u), Size(3f * u, 2f * u))
            val hop = (1f - sinceFiredMs / 300f).coerceIn(0f, 1f)
            val lift = sin(hop * PI.toFloat()) * 1.2f * u
            blob(Sun, cx - 0.9f * u, cy + 0.9f * u, 0.7f * u, ow * 0.8f)
            blob(Sun, cx + 0.95f * u, cy + 0.7f * u, 0.8f * u, ow * 0.8f)
            blob(Sun.lighten(0.25f), cx + 0.05f * u, cy + 0.2f * u - lift, 0.9f * u, ow * 0.8f)
            drawCircle(Color.White.copy(alpha = 0.8f), 0.25f * u, Offset(cx - 0.2f * u, cy - 0.1f * u - lift))
        }
        "beacon" -> {
            val wave = (timeMs * 0.0007f) % 1f
            drawCircle(color.copy(alpha = 0.7f * (1f - wave)), (1.2f + 2.2f * wave) * u, Offset(cx, cy), style = Stroke(0.3f * u))
            blob(Gunmetal, cx, cy, 1.9f * u, ow)
            blob(color, cx, cy, 1.25f * u, ow)
            drawRoundRect(Color.White, Offset(cx - 0.75f * u, cy - 0.22f * u), Size(1.5f * u, 0.44f * u), CornerRadius(0.2f * u))
            drawRoundRect(Color.White, Offset(cx - 0.22f * u, cy - 0.75f * u), Size(0.44f * u, 1.5f * u), CornerRadius(0.2f * u))
        }
        "flamer" -> {
            turret(cx, cy, u, aimDeg, kick * 0.3f, color, 1.9f, barrels = 1, spread = 0f, length = 2.2f, width = 1.5f, barrelColor = Gunmetal, muzzle = 1.9f)
            // Pilot light, flaring while it fires
            val firing = (1f - sinceFiredMs / 220f).coerceIn(0f, 1f)
            val tipX = cx + cos(aimRad) * 3.1f * u
            val tipY = cy + sin(aimRad) * 3.1f * u
            drawCircle(Color(0xFFFFC23C), (0.35f + 0.5f * firing) * u, Offset(tipX, tipY))
            drawCircle(Color.White.copy(alpha = 0.8f), (0.15f + 0.2f * firing) * u, Offset(tipX, tipY))
        }
        "prism" -> {
            blob(Gunmetal, cx, cy, 2.05f * u, ow)
            val charged = (1f - sinceFiredMs / 300f).coerceIn(0f, 1f)
            if (charged > 0f) drawCircle(color.copy(alpha = 0.5f * charged), 2.6f * u, Offset(cx, cy))
            // A glass triangle, three faces catching different light
            shape(Color.White, ow, cx, cy - 1.9f * u, cx + 1.7f * u, cy + 1.2f * u, cx - 1.7f * u, cy + 1.2f * u)
            shape(color, 0f, cx, cy - 1.9f * u, cx, cy + 1.2f * u, cx - 1.7f * u, cy + 1.2f * u)
            shape(Frost, 0f, cx, cy - 0.2f * u, cx + 1.7f * u, cy + 1.2f * u, cx, cy + 1.2f * u)
        }
        "glaive" -> {
            blob(color, cx, cy, 1.95f * u, ow)
            drawCircle(color.darken(0.3f), 0.8f * u, Offset(cx, cy))
            // The next blade, waiting on the arm; gone for a moment after a throw
            val ready = (sinceFiredMs / 400f).coerceIn(0f, 1f)
            if (ready > 0.2f) {
                val bx = cx + cos(aimRad) * 2.4f * u
                val by = cy + sin(aimRad) * 2.4f * u
                rotate(timeMs * 0.25f, Offset(bx, by)) { drawStar(Stone, bx, by, 1.5f * u * ready, ow * 0.8f) }
            }
        }
        "crossbow" -> {
            rotate(aimDeg, Offset(cx, cy)) {
                // Limbs and string, then the stock on top
                val tipX = cx + 2.2f * u - kick
                for (side in -1..1 step 2) {
                    val endX = cx + 0.9f * u - kick
                    val endY = cy + side * 2.3f * u
                    drawLine(Ink, Offset(tipX, cy), Offset(endX, endY), 0.75f * u + ow * 2f, StrokeCap.Round)
                    drawLine(color.lighten(0.2f), Offset(tipX, cy), Offset(endX, endY), 0.75f * u, StrokeCap.Round)
                    drawLine(Color.White.copy(alpha = 0.9f), Offset(endX, endY), Offset(cx - 0.4f * u, cy), 0.18f * u)
                }
                slab(color, cx - 1.6f * u, cy - 0.45f * u, 4.2f * u - kick, 0.9f * u, 0.3f * u, ow)
            }
            blob(color.darken(0.25f), cx, cy, 1.1f * u, ow)
        }
        "hex" -> {
            blob(Gunmetal, cx, cy, 2.1f * u, ow)
            blob(color, cx, cy, 1.55f * u, ow)
            // An eye that follows its target
            drawOval(Color.White, Offset(cx - 1.05f * u, cy - 0.7f * u), Size(2.1f * u, 1.4f * u))
            drawCircle(Ink, 0.5f * u, Offset(cx + cos(aimRad) * 0.45f * u, cy + sin(aimRad) * 0.3f * u))
            val orbit = timeMs * 0.004f
            drawCircle(color.lighten(0.5f), 0.4f * u, Offset(cx + cos(orbit) * 2.5f * u, cy + sin(orbit) * 2.5f * u))
        }
        "gust" -> {
            blob(Steel, cx, cy, 2.15f * u, ow)
            // Fan blades: lazy when idle, a blur right after a gust
            val spin = timeMs * 0.2f + (1f - sinceFiredMs / 700f).coerceIn(0f, 1f) * timeMs * 0.9f
            for (blade in 0 until 3) {
                rotate(spin + blade * 120f, Offset(cx, cy)) {
                    drawOval(Ink, Offset(cx - 0.55f * u - ow * 0.6f, cy - 1.95f * u - ow * 0.6f), Size(1.1f * u + ow * 1.2f, 1.9f * u + ow * 1.2f))
                    drawOval(color, Offset(cx - 0.55f * u, cy - 1.95f * u), Size(1.1f * u, 1.9f * u))
                }
            }
            blob(Stone, cx, cy, 0.6f * u, ow * 0.8f)
        }
        "bounty" -> {
            turret(cx, cy, u, aimDeg, kick, color, 1.9f, barrels = 1, spread = 0f, length = 3f, width = 0.75f)
            // Its badge: a coin
            blob(Sun, cx, cy, 0.85f * u, ow * 0.8f)
            drawCircle(Sun.darken(0.25f), 0.45f * u, Offset(cx, cy), style = Stroke(0.18f * u))
        }
        "reaper" -> {
            rotate(aimDeg, Offset(cx, cy)) {
                // The blade swings out on a strike
                val swing = (1f - sinceFiredMs / 200f).coerceIn(0f, 1f) * 0.8f * u
                shape(
                    Stone, ow,
                    cx + 1.2f * u, cy - 0.5f * u, cx + 3.3f * u + swing, cy - 2.1f * u,
                    cx + 4.1f * u + swing, cy - 0.2f * u, cx + 2.9f * u + swing, cy - 0.9f * u, cx + 1.2f * u, cy + 0.5f * u
                )
            }
            blob(color, cx, cy, 1.95f * u, ow)
            drawCircle(Ink, 0.75f * u, Offset(cx, cy))
            drawCircle(Tomato, 0.3f * u, Offset(cx, cy))
        }
        "comet" -> {
            blob(Gunmetal, cx, cy, 2.2f * u, ow)
            blob(color, cx, cy, 1.7f * u, ow)
            // The star it watches, flaring when it calls one down
            val flare = (1f - sinceFiredMs / 500f).coerceIn(0f, 1f)
            if (flare > 0f) drawCircle(Color.White.copy(alpha = 0.5f * flare), (1.4f + flare) * u, Offset(cx, cy))
            drawStar(Color.White, cx, cy, (1.05f + 0.4f * flare) * u, 0f)
            drawCircle(Sun, 0.3f * u, Offset(cx + 1.1f * u, cy - 1.1f * u))
        }
        "thumper" -> {
            blob(color.darken(0.25f), cx, cy, 2.2f * u, ow)
            // The hammer head: seen from above it swells as it comes down
            val slam = (1f - sinceFiredMs / 260f).coerceIn(0f, 1f)
            val half = (1.25f + 0.45f * slam) * u
            slab(color, cx - half, cy - half, half * 2f, half * 2f, 0.45f * u, ow)
            drawLine(Ink.copy(alpha = 0.45f), Offset(cx - half * 0.6f, cy), Offset(cx + half * 0.6f, cy), 0.25f * u, StrokeCap.Round)
            drawLine(Ink.copy(alpha = 0.45f), Offset(cx, cy - half * 0.6f), Offset(cx, cy + half * 0.6f), 0.25f * u, StrokeCap.Round)
        }
        "overclock" -> {
            blob(Gunmetal, cx, cy, 2.1f * u, ow)
            // A cog that never stops turning
            rotate(timeMs * 0.09f, Offset(cx, cy)) {
                for (tooth in 0 until 8) {
                    rotate(tooth * 45f, Offset(cx, cy)) {
                        drawRoundRect(color, Offset(cx - 0.38f * u, cy - 1.95f * u), Size(0.76f * u, 0.9f * u), CornerRadius(0.15f * u))
                    }
                }
            }
            blob(color, cx, cy, 1.3f * u, ow * 0.8f)
            drawCircle(Gunmetal, 0.5f * u, Offset(cx, cy))
        }
        "shrine" -> {
            val glow = (1f - sinceFiredMs / 700f).coerceIn(0f, 1f)
            val beat = 0.5f + 0.5f * sin(timeMs * 0.004f)
            drawCircle(Tomato.copy(alpha = 0.18f + 0.3f * glow), (2.2f + 0.8f * glow) * u, Offset(cx, cy))
            blob(color, cx, cy, 1.95f * u, ow)
            // A heart, beating slowly
            val h = (0.62f + 0.08f * beat + 0.25f * glow) * u
            drawCircle(Tomato, h, Offset(cx - h * 0.8f, cy - h * 0.35f))
            drawCircle(Tomato, h, Offset(cx + h * 0.8f, cy - h * 0.35f))
            shape(Tomato, 0f, cx - h * 1.75f, cy - h * 0.05f, cx + h * 1.75f, cy - h * 0.05f, cx, cy + h * 1.9f)
        }
        else -> {
            // A tower with no art of its own: a plain turret in its colour.
            val kind = type.shot
            if (type.isAttacker && !kind.isPulse) {
                turret(cx, cy, u, aimDeg, kick, color, 1.95f, barrels = 1, spread = 0f, length = 2.8f, width = 0.9f)
            } else {
                blob(color, cx, cy, 2f * u, ow)
            }
        }
    }

    // Upgrade pips along the bottom of the platform
    for (i in 0 until level) {
        val px = cx + (i - (level - 1) / 2f) * 1.5f * u
        blob(Sun, px, cy + 2.95f * u, 0.52f * u, ow * 0.8f)
    }
}

private fun DrawScope.turret(
    cx: Float, cy: Float, u: Float, aimDeg: Float, kick: Float,
    body: Color, bodyR: Float, barrels: Int, spread: Float, length: Float, width: Float,
    barrelColor: Color = Steel, muzzle: Float = 0f
) {
    val ow = OUTLINE * u
    rotate(aimDeg, Offset(cx, cy)) {
        for (i in 0 until barrels) {
            val off = (i - (barrels - 1) / 2f) * spread * u
            val left = cx + 0.5f * u - kick
            slab(barrelColor, left, cy + off - width * u / 2f, length * u, width * u, 0.2f * u, ow)
            if (muzzle > 0f) {
                slab(barrelColor.darken(0.3f), left + (length - 0.75f) * u, cy + off - muzzle * u / 2f, 0.75f * u, muzzle * u, 0.15f * u, ow)
            }
        }
    }
    blob(body, cx, cy, bodyR * u, ow)
    drawCircle(body.darken(0.3f), bodyR * u * 0.42f, Offset(cx, cy))
    drawCircle(Color.White.copy(alpha = 0.4f), bodyR * u * 0.3f, Offset(cx - bodyR * u * 0.42f, cy - bodyR * u * 0.42f))
}

// ---------------------------------------------------------------------------
// Units
// ---------------------------------------------------------------------------

fun DrawScope.drawUnitShadow(type: EnemySendType, cx: Float, cy: Float, u: Float) {
    val r = type.radius * u
    val scale = if (type.flying) 0.7f else 1f
    val drop = if (type.flying) 1.1f else 0.72f
    drawOval(Shadow, Offset(cx - r * 0.95f * scale, cy + r * drop - r * 0.36f * scale), Size(r * 1.9f * scale, r * 0.72f * scale))
}

/**
 * @param seed any per-unit number; keeps a pack from bouncing in step
 * @param flash 0..1, how white the unit is from a hit it just took
 */
fun DrawScope.drawUnit(
    type: EnemySendType, cx: Float, groundY: Float, u: Float,
    dirX: Float, dirY: Float, timeMs: Float, seed: Int,
    hpFrac: Float, flash: Float, slowed: Boolean, stunned: Boolean, poisoned: Boolean,
    cursed: Boolean = false, hasted: Boolean = false
) {
    val r = type.radius * u
    val ow = OUTLINE * u
    val still = stunned
    val phase = if (still) seed.toFloat() else timeMs * (0.006f + type.speed * 0.0006f) * (if (slowed) 0.5f else 1f) + seed
    val squash = sin(phase) * 0.08f
    val rx = r * (1f + squash)
    val ry = r * (1f - squash)
    var cy = groundY - abs(sin(phase * 0.5f)) * r * 0.2f
    if (type.flying) cy -= r * 1.25f + sin(timeMs * 0.004f + seed) * r * 0.2f
    val body = type.color
    val shade = body.darken(0.22f)

    if (type.flying) {
        val flap = sin(timeMs * 0.03f + seed)
        for (side in -1..1 step 2) {
            rotate((side * (20f + 28f * flap)), Offset(cx, cy)) {
                drawOval(Ink, Offset(cx + side * r * 0.5f - (if (side < 0) r * 1.3f else 0f) - ow, cy - r * 0.5f - ow), Size(r * 1.3f + 2f * ow, r * 0.75f + 2f * ow))
                drawOval(Color.White, Offset(cx + side * r * 0.5f - (if (side < 0) r * 1.3f else 0f), cy - r * 0.5f), Size(r * 1.3f, r * 0.75f))
            }
        }
    }

    drawOval(Ink, Offset(cx - rx - ow, cy - ry - ow), Size(2f * (rx + ow), 2f * (ry + ow)))
    drawOval(body, Offset(cx - rx, cy - ry), Size(2f * rx, 2f * ry))
    drawArc(shade, 15f, 150f, false, Offset(cx - rx, cy - ry), Size(2f * rx, 2f * ry))
    drawOval(Color.White.copy(alpha = 0.45f), Offset(cx - rx * 0.62f, cy - ry * 0.72f), Size(rx * 0.5f, ry * 0.34f))

    when (type.id) {
        "grunt", "tank" -> {
            // Helmet
            val helm = if (type.id == "tank") Color(0xFF39424F) else Color(0xFF4C5F85)
            drawArc(Ink, 180f, 180f, true, Offset(cx - rx - ow, cy - ry - ow), Size(2f * (rx + ow), 2f * (ry + ow) * 0.95f))
            drawArc(helm, 180f, 180f, true, Offset(cx - rx, cy - ry), Size(2f * rx, 2f * ry * 0.92f))
            drawRoundRect(Ink, Offset(cx - rx - ow, cy - ry * 0.16f), Size(2f * (rx + ow), ry * 0.26f), CornerRadius(ry * 0.1f))
            if (type.id == "tank") {
                drawCircle(Color(0xFFB8C2D0), r * 0.1f, Offset(cx - rx * 0.5f, cy - ry * 0.55f))
                drawCircle(Color(0xFFB8C2D0), r * 0.1f, Offset(cx + rx * 0.5f, cy - ry * 0.55f))
            }
        }
        "splitter" -> {
            // The seam it splits along
            val seam = Stroke(ow * 0.9f, cap = StrokeCap.Round)
            scratchPath.rewind()
            scratchPath.moveTo(cx, cy - ry)
            scratchPath.lineTo(cx - rx * 0.2f, cy - ry * 0.45f)
            scratchPath.lineTo(cx + rx * 0.15f, cy - ry * 0.1f)
            drawPath(scratchPath, Ink, style = seam)
        }
        "boss" -> {
            val top = cy - ry
            shape(
                Sun, ow,
                cx - rx * 0.62f, top + ry * 0.22f, cx - rx * 0.62f, top - ry * 0.34f, cx - rx * 0.3f, top - ry * 0.02f,
                cx, top - ry * 0.46f, cx + rx * 0.3f, top - ry * 0.02f, cx + rx * 0.62f, top - ry * 0.34f,
                cx + rx * 0.62f, top + ry * 0.22f
            )
        }
        "healer" -> {
            val bob = sin(timeMs * 0.005f + seed) * r * 0.08f
            val px = cx
            val py = cy - ry - r * 0.55f + bob
            blob(Color.White, px, py, r * 0.42f, ow * 0.8f)
            drawRoundRect(Leaf.darken(0.15f), Offset(px - r * 0.27f, py - r * 0.08f), Size(r * 0.54f, r * 0.16f), CornerRadius(r * 0.05f))
            drawRoundRect(Leaf.darken(0.15f), Offset(px - r * 0.08f, py - r * 0.27f), Size(r * 0.16f, r * 0.54f), CornerRadius(r * 0.05f))
        }
        "bulwark" -> {
            // A riveted plate across the lower half
            drawArc(Ink, 0f, 180f, true, Offset(cx - rx - ow, cy - ry - ow), Size(2f * (rx + ow), 2f * (ry + ow)))
            drawArc(Color(0xFFC3CCD9), 0f, 180f, true, Offset(cx - rx, cy - ry), Size(2f * rx, 2f * ry))
            drawRoundRect(Ink, Offset(cx - rx - ow, cy - ry * 0.12f), Size(2f * (rx + ow), ry * 0.24f), CornerRadius(ry * 0.1f))
            for (side in -1..1) drawCircle(Ink, r * 0.09f, Offset(cx + side * rx * 0.55f, cy + ry * 0.5f))
        }
        "troll" -> {
            // Tusks
            for (side in -1..1 step 2) {
                shape(
                    Color.White, ow * 0.7f,
                    cx + side * rx * 0.26f, cy + ry * 0.4f, cx + side * rx * 0.52f, cy + ry * 0.4f, cx + side * rx * 0.44f, cy + ry * 0.02f
                )
            }
        }
        "brood" -> {
            // Eggs showing through
            drawCircle(shade.darken(0.12f), r * 0.17f, Offset(cx - rx * 0.45f, cy + ry * 0.4f))
            drawCircle(shade.darken(0.12f), r * 0.17f, Offset(cx + rx * 0.08f, cy + ry * 0.58f))
            drawCircle(shade.darken(0.12f), r * 0.17f, Offset(cx + rx * 0.5f, cy + ry * 0.3f))
        }
        "drummer" -> {
            // The drum it carries
            slab(Color(0xFF8A4B2A), cx - rx * 0.55f, cy + ry * 0.38f, rx * 1.1f, ry * 0.56f, ry * 0.15f, ow * 0.8f)
            drawRoundRect(Color(0xFFF4E6C8), Offset(cx - rx * 0.55f, cy + ry * 0.38f), Size(rx * 1.1f, ry * 0.2f), CornerRadius(ry * 0.1f))
        }
        "burrower" -> {
            // A snout and two front teeth
            drawCircle(Color(0xFFFF9EB0), r * 0.2f, Offset(cx + dirX * r * 0.16f, cy + ry * 0.3f))
            for (side in -1..1 step 2) {
                drawRoundRect(Color.White, Offset(cx + side * r * 0.12f - r * 0.08f + dirX * r * 0.16f, cy + ry * 0.48f), Size(r * 0.16f, r * 0.22f))
            }
        }
        "warder" -> {
            // The shield it carries for everyone else
            shape(
                Color.White, ow * 0.7f,
                cx - rx * 0.4f, cy + ry * 0.2f, cx + rx * 0.4f, cy + ry * 0.2f,
                cx + rx * 0.4f, cy + ry * 0.55f, cx, cy + ry * 0.92f, cx - rx * 0.4f, cy + ry * 0.55f
            )
            drawCircle(body, r * 0.12f, Offset(cx, cy + ry * 0.5f))
        }
        "berserker" -> {
            // Horns
            for (side in -1..1 step 2) {
                shape(
                    Color.White, ow * 0.7f,
                    cx + side * rx * 0.35f, cy - ry * 0.78f, cx + side * rx * 0.95f, cy - ry * 1.3f, cx + side * rx * 0.7f, cy - ry * 0.55f
                )
            }
        }
        "wyvern" -> {
            // Horns and a pale belly
            for (side in -1..1 step 2) {
                shape(
                    Sun, ow * 0.7f,
                    cx + side * rx * 0.3f, cy - ry * 0.82f, cx + side * rx * 0.62f, cy - ry * 1.35f, cx + side * rx * 0.62f, cy - ry * 0.62f
                )
            }
            drawOval(body.lighten(0.45f), Offset(cx - rx * 0.45f, cy + ry * 0.25f), Size(rx * 0.9f, ry * 0.55f))
        }
        "colossus" -> {
            // Cracked stone and an iron brow band
            val crack = Stroke(ow * 0.8f, cap = StrokeCap.Round)
            scratchPath.rewind()
            scratchPath.moveTo(cx - rx * 0.55f, cy + ry * 0.2f)
            scratchPath.lineTo(cx - rx * 0.3f, cy + ry * 0.5f)
            scratchPath.lineTo(cx - rx * 0.45f, cy + ry * 0.8f)
            scratchPath.moveTo(cx + rx * 0.5f, cy + ry * 0.1f)
            scratchPath.lineTo(cx + rx * 0.3f, cy + ry * 0.45f)
            scratchPath.lineTo(cx + rx * 0.55f, cy + ry * 0.7f)
            drawPath(scratchPath, Ink.copy(alpha = 0.6f), style = crack)
            drawRoundRect(Ink, Offset(cx - rx - ow, cy - ry * 0.62f - ow), Size(2f * (rx + ow), ry * 0.3f + 2f * ow), CornerRadius(ry * 0.1f))
            drawRoundRect(Gunmetal, Offset(cx - rx, cy - ry * 0.62f), Size(2f * rx, ry * 0.3f), CornerRadius(ry * 0.1f))
            for (side in -1..1) drawCircle(Stone, r * 0.06f, Offset(cx + side * rx * 0.6f, cy - ry * 0.47f))
        }
        "juggernaut" -> {
            // Spikes along the top
            val top = cy - ry
            for (side in -1..1) {
                val sx = cx + side * rx * 0.5f
                val foot = top + ry * (0.16f + 0.14f * side * side)
                shape(Gunmetal, ow, sx - rx * 0.2f, foot, sx, foot - ry * 0.5f, sx + rx * 0.2f, foot)
            }
        }
    }

    // Eyes look where the unit is heading.
    val eyeR = r * (if (type.id == "swarm" || type.id == "splitling") 0.3f else 0.26f)
    val eyeY = cy - ry * 0.02f + dirY * r * 0.1f
    val gap = rx * 0.38f
    val lookX = dirX * r * 0.16f
    for (side in -1..1 step 2) {
        val ex = cx + side * gap + lookX
        drawCircle(Ink, eyeR + ow * 0.6f, Offset(ex, eyeY))
        drawCircle(Color.White, eyeR, Offset(ex, eyeY))
        drawCircle(Ink, eyeR * 0.52f, Offset(ex + dirX * eyeR * 0.4f, eyeY + dirY * eyeR * 0.4f))
    }
    if (type.id == "boss" || type.id == "tank" || type.id == "juggernaut" || type.id == "troll" ||
        type.id == "berserker" || type.id == "colossus" || type.id == "wyvern"
    ) {
        // Angry brows
        val brow = Stroke(ow * 1.3f, cap = StrokeCap.Round)
        for (side in -1..1 step 2) {
            scratchPath.rewind()
            scratchPath.moveTo(cx + side * (gap + eyeR * 1.2f) + lookX, eyeY - eyeR * 1.7f)
            scratchPath.lineTo(cx + side * (gap - eyeR * 0.9f) + lookX, eyeY - eyeR * 0.9f)
            drawPath(scratchPath, Ink, style = brow)
        }
    }

    if (flash > 0f) drawOval(Color.White.copy(alpha = 0.75f * flash), Offset(cx - rx, cy - ry), Size(2f * rx, 2f * ry))
    if (slowed) {
        drawOval(Frost.copy(alpha = 0.3f), Offset(cx - rx, cy - ry), Size(2f * rx, 2f * ry))
        drawOval(Color.White.copy(alpha = 0.9f), Offset(cx - rx - ow, cy - ry - ow), Size(2f * (rx + ow), 2f * (ry + ow)), style = Stroke(ow * 0.7f))
    }
    if (poisoned) {
        for (i in 0 until 2) {
            val p = (timeMs * 0.002f + i * 0.5f + seed * 0.13f) % 1f
            drawCircle(Leaf.lighten(0.3f).copy(alpha = 1f - p), r * (0.14f + 0.14f * p), Offset(cx + (i * 2 - 1) * rx * 0.6f, cy - ry * 0.6f - p * r * 0.9f))
        }
    }
    if (stunned) {
        for (i in 0 until 3) {
            val a = timeMs * 0.008f + i * 2.094f
            drawStar(Sun, cx + cos(a) * rx * 0.8f, cy - ry - r * 0.35f + sin(a) * r * 0.18f, r * 0.26f + 0.5f * u, ow * 0.5f)
        }
    }

    if (cursed) {
        // The mark of a Hex Totem, hanging over its head
        val mx = cx + rx * 0.9f
        val my = cy - ry - r * 0.15f
        val s = r * 0.24f + 0.35f * u
        shape(Color(0xFF8A63D2), ow * 0.6f, mx, my - s, mx + s * 0.7f, my, mx, my + s, mx - s * 0.7f, my)
    }
    if (hasted) {
        // Speed lines trailing behind
        for (line in -1..1 step 2) {
            val oy = line * ry * 0.4f
            drawLine(
                Color.White.copy(alpha = 0.75f),
                Offset(cx - dirX * rx * 1.25f, cy - dirY * ry * 1.25f + oy),
                Offset(cx - dirX * rx * 2f, cy - dirY * ry * 2f + oy),
                ow * 0.8f, StrokeCap.Round
            )
        }
    }

    if (hpFrac < 0.999f) {
        val w = (r * 2.1f).coerceAtLeast(3.4f * u)
        val h = 0.72f * u
        val left = cx - w / 2f
        val top = cy - ry - h - 0.9f * u - (if (type.id == "boss") ry * 0.5f else 0f)
        drawRoundRect(Ink, Offset(left - ow * 0.7f, top - ow * 0.7f), Size(w + ow * 1.4f, h + ow * 1.4f), CornerRadius(h))
        val barColor = when {
            hpFrac > 0.55f -> Leaf
            hpFrac > 0.28f -> Sun
            else -> Tomato
        }
        drawRoundRect(barColor, Offset(left, top), Size(w * hpFrac.coerceIn(0f, 1f), h), CornerRadius(h))
    }
}

/** What shows of a unit while it tunnels: a mound of earth, shaking as it moves. */
fun DrawScope.drawBurrowMound(type: EnemySendType, cx: Float, cy: Float, u: Float, timeMs: Float, seed: Int) {
    val r = type.radius * u
    val ow = OUTLINE * u
    val wobble = sin(timeMs * 0.02f + seed) * r * 0.06f
    val earth = Color(0xFF8A5E3A)
    drawOval(Ink, Offset(cx - r * 1.05f - ow, cy - r * 0.5f - ow + wobble), Size(r * 2.1f + 2f * ow, r * 1.15f + 2f * ow))
    drawOval(earth, Offset(cx - r * 1.05f, cy - r * 0.5f + wobble), Size(r * 2.1f, r * 1.15f))
    drawOval(earth.lighten(0.25f), Offset(cx - r * 0.6f, cy - r * 0.38f + wobble), Size(r * 0.9f, r * 0.4f))
    // Clods thrown up behind it
    for (k in 0 until 3) {
        val phase = (timeMs * 0.003f + k * 0.33f + seed * 0.17f) % 1f
        drawCircle(earth.darken(0.2f).copy(alpha = 1f - phase), r * 0.16f, Offset(cx + (k - 1) * r * 0.6f, cy - r * 0.4f - phase * r * 0.9f))
    }
}

// ---------------------------------------------------------------------------
// Shared bits
// ---------------------------------------------------------------------------

/** A four-point sparkle. */
internal fun DrawScope.drawStar(color: Color, cx: Float, cy: Float, r: Float, ow: Float) {
    val s = r * 0.32f
    shape(
        color, ow,
        cx, cy - r, cx + s, cy - s, cx + r, cy, cx + s, cy + s,
        cx, cy + r, cx - s, cy + s, cx - r, cy, cx - s, cy - s
    )
}

/** The keep at the end of a lane, flying its owner's colour. [hurt] is 0..1 right after a leak. */
fun DrawScope.drawBase(cx: Float, cy: Float, u: Float, team: Color, hurt: Float, timeMs: Float) {
    val ow = OUTLINE * u
    val x = cx + sin(timeMs * 0.09f) * hurt * 0.7f * u
    val wall = Color(0xFFF1EBDD)
    drawOval(Shadow, Offset(x - 4.6f * u, cy + 1.2f * u), Size(9.2f * u, 4f * u))

    // Flag
    val poleTop = cy - 8.6f * u
    drawLine(Ink, Offset(x, cy - 4.2f * u), Offset(x, poleTop), strokeWidth = 0.55f * u, cap = StrokeCap.Round)
    val wave = sin(timeMs * 0.006f) * 0.35f * u
    shape(team, ow * 0.8f, x + 0.2f * u, poleTop, x + 3.1f * u, poleTop + 0.9f * u + wave, x + 0.2f * u, poleTop + 1.9f * u)

    // Battlements, then the wall they sit on
    for (i in -1..1) slab(wall, x + i * 2.6f * u - 0.95f * u, cy - 5.4f * u, 1.9f * u, 2f * u, 0.25f * u, ow)
    slab(wall, x - 3.8f * u, cy - 4.1f * u, 7.6f * u, 7.2f * u, 0.7f * u, ow)
    drawRoundRect(team, Offset(x - 3.8f * u, cy - 2.6f * u), Size(7.6f * u, 1.1f * u))
    drawRoundRect(Color.White.copy(alpha = 0.35f), Offset(x - 3.4f * u, cy - 3.8f * u), Size(1f * u, 6.4f * u), CornerRadius(0.5f * u))
    // Gate
    slab(Gunmetal, x - 1.4f * u, cy - 0.6f * u, 2.8f * u, 3.7f * u, 1.4f * u, ow * 0.8f)

    if (hurt > 0f) {
        drawRoundRect(Tomato.copy(alpha = 0.55f * hurt), Offset(x - 3.8f * u, cy - 4.1f * u), Size(7.6f * u, 7.2f * u), CornerRadius(0.7f * u))
    }
}
