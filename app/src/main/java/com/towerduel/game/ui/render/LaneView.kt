package com.towerduel.game.ui.render

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint as ComposePaint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import com.towerduel.game.R
import com.towerduel.game.data.LaneSpace
import com.towerduel.game.data.ShotKind
import com.towerduel.game.data.TroopType
import com.towerduel.game.engine.Battlefield
import com.towerduel.game.engine.EnemyUnit
import com.towerduel.game.engine.FxEvent
import com.towerduel.game.engine.FxKind
import com.towerduel.game.engine.GameEngine
import com.towerduel.game.engine.PlaceResult
import com.towerduel.game.engine.Projectile
import com.towerduel.game.ui.theme.Frost
import com.towerduel.game.ui.theme.Ink
import com.towerduel.game.ui.theme.Leaf
import com.towerduel.game.ui.theme.Sun
import com.towerduel.game.ui.theme.Tomato
import com.towerduel.game.ui.theme.lighten
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Where the player is about to drop a tower, and whether the engine would accept it there. */
data class Ghost(val type: TroopType, val x: Float, val y: Float, val result: PlaceResult)

/** Touch on the player's lane, already converted to lane units. */
class LaneGestures(
    val onPress: (Float, Float) -> Unit,
    val onDrag: (Float, Float) -> Unit,
    val onRelease: (Float, Float) -> Unit,
    val onCancel: () -> Unit
)

@Composable
fun rememberGameTypeface(): Typeface {
    val context = LocalContext.current
    return remember { ResourcesCompat.getFont(context, R.font.luckiest_guy) ?: Typeface.DEFAULT_BOLD }
}

/**
 * One lane, drawn at the lane's own aspect ratio so a lane unit is the same size on both axes.
 * [observeFrame] is read in the draw phase, which repaints the lane after every simulation tick.
 */
@Composable
fun LaneView(
    engine: GameEngine,
    field: Battlefield,
    team: Color,
    observeFrame: () -> Int,
    modifier: Modifier = Modifier,
    selectedTowerId: Long? = null,
    ghost: Ghost? = null,
    gestures: LaneGestures? = null
) {
    val typeface = rememberGameTypeface()
    val painter = remember(typeface) { LanePainter(typeface) }
    val currentGestures by rememberUpdatedState(gestures)
    val shape = RoundedCornerShape(14.dp)

    var canvas = modifier
        .aspectRatio(LaneSpace.WIDTH / LaneSpace.HEIGHT)
        .clip(shape)
        .border(2.5.dp, Ink, shape)
    if (gestures != null) {
        canvas = canvas.pointerInput(Unit) {
            fun laneX(px: Float) = px / size.width * LaneSpace.WIDTH
            fun laneY(py: Float) = py / size.height * LaneSpace.HEIGHT
            awaitEachGesture {
                val down = awaitFirstDown()
                currentGestures?.onPress?.invoke(laneX(down.position.x), laneY(down.position.y))
                var finished = false
                while (!finished) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null) {
                        currentGestures?.onCancel?.invoke()
                        finished = true
                    } else if (!change.pressed) {
                        currentGestures?.onRelease?.invoke(laneX(change.position.x), laneY(change.position.y))
                        finished = true
                    } else {
                        currentGestures?.onDrag?.invoke(laneX(change.position.x), laneY(change.position.y))
                        change.consume()
                    }
                }
            }
        }
    }

    Spacer(
        modifier = canvas.drawWithCache {
            val u = size.width / LaneSpace.WIDTH
            val terrain = TerrainCache.get(engine.map, engine.path, size.width.toInt(), size.height.toInt(), this)
            onDrawBehind {
                observeFrame()
                drawImage(terrain)
                with(painter) { drawField(engine, field, team, u, selectedTowerId, ghost) }
            }
        }
    )
}

/** Draws everything that moves on a lane. Holds the paints and scratch objects it reuses every frame. */
class LanePainter(typeface: Typeface) {

    private val textPaint = Paint().apply {
        this.typeface = typeface
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        strokeJoin = Paint.Join.ROUND
    }
    private val boltPath = Path()
    private val phasedPaint = ComposePaint().apply { alpha = 0.38f }
    private val inkArgb = Ink.toArgb()

    fun DrawScope.drawField(
        engine: GameEngine, field: Battlefield, team: Color, u: Float,
        selectedTowerId: Long?, ghost: Ghost?
    ) {
        val now = engine.elapsedMs
        val path = engine.path
        val last = path.pointCount - 1

        // Ground-level effects sit under everything that stands on the lane.
        for (i in field.fx.indices) drawGroundFx(field.fx[i], u)

        for (i in field.towers.indices) {
            val t = field.towers[i]
            if (t.instanceId == selectedTowerId) rangeRing(t.x, t.y, engine.towerReach(t), u, t.type.color, ok = true)
        }
        if (ghost != null) {
            val ok = ghost.result == PlaceResult.OK || ghost.result == PlaceResult.NOT_ENOUGH_GOLD
            rangeRing(ghost.x, ghost.y, engine.baseReach(ghost.type), u, if (ok) Color.White else Tomato, ok)
        }

        for (i in field.incomingEnemies.indices) {
            val e = field.incomingEnemies[i]
            if (e.alive && !engine.isBurrowed(e)) drawUnitShadow(e.type, e.x * u, e.y * u, u)
        }
        for (i in field.incomingEnemies.indices) {
            val e = field.incomingEnemies[i]
            if (e.alive && !e.type.flying) drawEnemy(engine, e, u)
        }

        val hurt = (1f - (now - field.lastLeakAtMs) / 450f).coerceIn(0f, 1f)
        drawBase(path.xs[last] * u, path.ys[last] * u, u, team, hurt, now)

        for (i in field.towers.indices) {
            val t = field.towers[i]
            // A freshly placed or upgraded tower lands with a little bounce.
            val pop = popScale(now - t.placedAtMs) * popScale(now - t.upgradedAtMs)
            drawTower(t.type, t.level, t.shots, t.x * u, t.y * u, u * pop, t.aimAngle, now - t.lastFiredAtMs, now)
            if (t.auraBonus > 0f) drawStar(Sun, (t.x + 2.4f) * u, (t.y - 2.4f) * u, 0.9f * u, OUTLINE * 0.6f * u)
        }

        for (i in field.incomingEnemies.indices) {
            val e = field.incomingEnemies[i]
            if (e.alive && e.type.flying) drawEnemy(engine, e, u)
        }

        for (i in field.projectiles.indices) drawProjectile(field.projectiles[i], u)
        for (i in field.fx.indices) drawAirFx(field.fx[i], u)

        // A life was just lost: the whole lane flinches.
        if (hurt > 0f) {
            drawRect(Tomato.copy(alpha = 0.16f * hurt))
            drawRect(Tomato.copy(alpha = 0.8f * hurt), style = Stroke(2.2f * u))
        }

        if (ghost != null) {
            val ok = ghost.result == PlaceResult.OK || ghost.result == PlaceResult.NOT_ENOUGH_GOLD
            drawTower(ghost.type, 0, 1, ghost.x * u, ghost.y * u, u, -PI.toFloat() / 2f, 10_000f, now)
            if (!ok) {
                // A red cross over a spot that cannot be built on
                val c = Offset(ghost.x * u, ghost.y * u)
                val r = 2.2f * u
                drawLine(Ink, Offset(c.x - r, c.y - r), Offset(c.x + r, c.y + r), 1.5f * u, StrokeCap.Round)
                drawLine(Ink, Offset(c.x - r, c.y + r), Offset(c.x + r, c.y - r), 1.5f * u, StrokeCap.Round)
                drawLine(Tomato, Offset(c.x - r, c.y - r), Offset(c.x + r, c.y + r), 0.85f * u, StrokeCap.Round)
                drawLine(Tomato, Offset(c.x - r, c.y + r), Offset(c.x + r, c.y - r), 0.85f * u, StrokeCap.Round)
            }
        }
    }

    private fun popScale(ageMs: Float): Float {
        if (ageMs < 0f || ageMs > 260f) return 1f
        val t = ageMs / 260f
        return 1f + 0.28f * sin(t * PI.toFloat()) * (1f - t)
    }

    private fun DrawScope.rangeRing(x: Float, y: Float, reach: Float, u: Float, color: Color, ok: Boolean) {
        val c = Offset(x * u, y * u)
        drawCircle((if (ok) Color.White else Tomato).copy(alpha = if (ok) 0.2f else 0.3f), reach * u, c)
        drawCircle(Ink.copy(alpha = 0.55f), reach * u, c, style = Stroke(0.7f * u))
        drawCircle(color.lighten(0.5f), reach * u, c, style = Stroke(0.35f * u))
    }

    private fun DrawScope.drawEnemy(engine: GameEngine, e: EnemyUnit, u: Float) {
        val now = engine.elapsedMs
        if (engine.isBurrowed(e)) {
            drawBurrowMound(e.type, e.x * u, e.y * u, u, now, e.instanceId.toInt())
            return
        }
        // A phased unit is out of reach: drawn see-through, as one layer so its parts do not show through each other.
        val phased = engine.isPhased(e)
        if (phased) {
            val r = (e.type.radius + 2.5f) * u
            drawContext.canvas.saveLayer(Rect(e.x * u - r, e.y * u - r * 1.8f, e.x * u + r, e.y * u + r), phasedPaint)
        }
        drawUnit(
            e.type, e.x * u, e.y * u, u, e.dirX, e.dirY, now, e.instanceId.toInt(),
            hpFrac = e.hp / e.maxHp,
            flash = (1f - (now - e.lastHitAtMs) / 110f).coerceIn(0f, 1f),
            slowed = now < e.slowExpiresAtMs,
            stunned = now < e.stunExpiresAtMs,
            poisoned = now < e.dotExpiresAtMs,
            cursed = now < e.vulnerableUntilMs,
            hasted = now < e.hasteUntilMs
        )
        if (phased) drawContext.canvas.restore()
    }

    private fun DrawScope.drawProjectile(p: Projectile, u: Float) {
        val ow = OUTLINE * u
        val x = p.x * u
        val y = p.y * u
        val color = p.source.type.color
        when (p.kind) {
            ShotKind.MORTAR -> {
                val t = (p.ageMs / p.flightMs).coerceIn(0f, 1f)
                val height = sin(t * PI.toFloat())
                drawOval(Shadow, Offset(x - 1.1f * u, y - 0.5f * u), Size(2.2f * u, 1f * u))
                blob(Color(0xFF3A3448), x, y - height * 9f * u, (0.9f + 0.7f * height) * u, ow)
            }
            ShotKind.SHELL -> {
                blob(Color(0xFF3A3448), x, y, 0.95f * u, ow)
                drawCircle(Color.White.copy(alpha = 0.5f), 0.3f * u, Offset(x - 0.3f * u, y - 0.3f * u))
            }
            ShotKind.BULLET -> {
                val tail = 1.8f * u
                drawLine(Sun.copy(alpha = 0.6f), Offset(x - cos(p.angle) * tail, y - sin(p.angle) * tail), Offset(x, y), 0.5f * u, StrokeCap.Round)
                blob(Sun.lighten(0.4f), x, y, 0.4f * u, ow * 0.7f)
            }
            ShotKind.NET -> {
                rotate(p.ageMs * 0.9f, Offset(x, y)) {
                    drawCircle(Ink, 1.25f * u, Offset(x, y), style = Stroke(0.75f * u))
                    drawCircle(Color.White, 1.25f * u, Offset(x, y), style = Stroke(0.35f * u))
                    drawLine(Color.White, Offset(x - 1.25f * u, y), Offset(x + 1.25f * u, y), 0.3f * u)
                    drawLine(Color.White, Offset(x, y - 1.25f * u), Offset(x, y + 1.25f * u), 0.3f * u)
                }
            }
            ShotKind.ORB -> {
                drawCircle(color.copy(alpha = 0.35f), 1.4f * u, Offset(x, y))
                blob(color, x, y, 0.75f * u, ow)
                drawCircle(Color.White, 0.32f * u, Offset(x, y))
            }
            ShotKind.GLAIVE -> {
                rotate(p.ageMs * 1.4f, Offset(x, y)) {
                    drawStar(color, x, y, 1.9f * u, ow)
                    drawCircle(Ink, 0.4f * u, Offset(x, y))
                }
            }
            else -> {
                // A dart: pointed, with a fin in the tower's colour
                rotate(p.angle * 180f / PI.toFloat(), Offset(x, y)) {
                    shape(color, ow, x - 1.5f * u, y - 0.7f * u, x - 0.4f * u, y, x - 1.5f * u, y + 0.7f * u)
                    shape(Color.White, ow, x - 1.1f * u, y - 0.32f * u, x + 1.4f * u, y, x - 1.1f * u, y + 0.32f * u)
                }
            }
        }
    }

    /** Effects that lie flat on the ground: rings and clouds. */
    private fun DrawScope.drawGroundFx(fx: FxEvent, u: Float) {
        val t = fx.t
        val c = Offset(fx.x * u, fx.y * u)
        val ease = 1f - (1f - t) * (1f - t)
        when (fx.kind) {
            FxKind.FROST_RING -> {
                val r = fx.size * u * (0.25f + 0.75f * ease)
                drawCircle(Frost.copy(alpha = 0.22f * (1f - t)), r, c)
                drawCircle(Color.White.copy(alpha = 0.9f * (1f - t)), r, c, style = Stroke(0.7f * u))
            }
            FxKind.POISON_CLOUD -> {
                val r = fx.size * u * (0.4f + 0.6f * ease)
                drawCircle(Leaf.copy(alpha = 0.3f * (1f - t)), r, c)
                drawCircle(Leaf.lighten(0.4f).copy(alpha = 0.6f * (1f - t)), r, c, style = Stroke(0.45f * u))
            }
            FxKind.HEAL -> {
                val r = fx.size * u * (0.3f + 0.7f * ease)
                drawCircle(Leaf.lighten(0.3f).copy(alpha = 0.75f * (1f - t)), r, c, style = Stroke(0.5f * u))
            }
            FxKind.HASTE -> {
                val r = fx.size * u * (0.3f + 0.7f * ease)
                drawCircle(Color(0xFFFFB45C).copy(alpha = 0.75f * (1f - t)), r, c, style = Stroke(0.5f * u))
            }
            FxKind.WARD -> {
                val r = fx.size * u * (0.3f + 0.7f * ease)
                drawCircle(Color(0xFF8FC1FF).copy(alpha = 0.8f * (1f - t)), r, c, style = Stroke(0.55f * u))
            }
            FxKind.QUAKE_RING -> {
                // The ground rippling outwards, dust at the front of it
                val r = fx.size * u * (0.15f + 0.85f * ease)
                drawCircle(Color(0xFFE8D7B8).copy(alpha = 0.25f * (1f - t)), r, c)
                drawCircle(Ink.copy(alpha = 0.45f * (1f - t)), r, c, style = Stroke(1.1f * u))
                drawCircle(Color(0xFFF3E9D2).copy(alpha = 0.9f * (1f - t)), r, c, style = Stroke(0.6f * u))
                drawCircle(Color(0xFFF3E9D2).copy(alpha = 0.6f * (1f - t)), r * 0.62f, c, style = Stroke(0.35f * u))
            }
            FxKind.GUST_RING -> {
                // Two rings chasing each other outwards
                for (k in 0..1) {
                    val r = fx.size * u * (0.2f + 0.8f * ease) * (1f - 0.28f * k)
                    drawCircle(Color.White.copy(alpha = 0.8f * (1f - t)), r, c, style = Stroke((0.7f - 0.25f * k) * u))
                }
                drawCircle(Color(0xFFBFE3FF).copy(alpha = 0.18f * (1f - t)), fx.size * u * (0.2f + 0.8f * ease), c)
            }
            FxKind.DUST -> {
                for (i in 0 until 7) {
                    val a = i * 0.9f
                    val d = fx.size * u * (0.35f + 0.65f * ease)
                    drawCircle(Color(0xFFF3E9D2).copy(alpha = 0.8f * (1f - t)), (1.1f - 0.5f * t) * u, Offset(c.x + cos(a) * d, c.y + sin(a) * d * 0.7f))
                }
            }
            else -> Unit
        }
    }

    /** Effects that happen above the units: pops, blasts, bolts and floating numbers. */
    private fun DrawScope.drawAirFx(fx: FxEvent, u: Float) {
        val t = fx.t
        val c = Offset(fx.x * u, fx.y * u)
        val ease = 1f - (1f - t) * (1f - t)
        val ow = OUTLINE * u
        when (fx.kind) {
            FxKind.POP -> {
                val color = fx.unit?.color ?: Color.White
                val r = fx.size * u
                drawCircle(Color.White.copy(alpha = 0.9f * (1f - t)), r * (0.7f + 1.5f * ease), c, style = Stroke(0.6f * u * (1f - t) + 1f))
                for (i in 0 until 7) {
                    val a = fx.seed * 0.37f + i * 0.8976f
                    val d = r * (0.5f + 2.3f * ease)
                    val dr = r * 0.36f * (1f - t)
                    val px = c.x + cos(a) * d
                    val py = c.y + sin(a) * d + t * t * r * 0.9f
                    drawCircle(Ink, dr + ow * 0.6f, Offset(px, py))
                    drawCircle(color, dr, Offset(px, py))
                }
                if (t < 0.4f) drawStar(Color.White, c.x, c.y, r * (1.2f + 2f * t), 0f)
            }
            FxKind.HIT -> drawStar(Color.White.copy(alpha = 1f - t), c.x, c.y, fx.size * u * (0.5f + ease), 0f)
            FxKind.EXPLOSION -> {
                val r = fx.size * u * (0.3f + 0.7f * ease)
                val fade = (1f - t) * (1f - t)
                drawCircle(Ink.copy(alpha = 0.5f * fade), r + ow, c)
                drawCircle(Color(0xFFFF8A2B).copy(alpha = 0.95f * fade), r, c)
                drawCircle(Color(0xFFFFD75A).copy(alpha = fade), r * 0.68f, c)
                drawCircle(Color.White.copy(alpha = fade), r * 0.36f, c)
                for (i in 0 until 6) {
                    val a = fx.seed * 0.21f + i * 1.047f
                    val d = fx.size * u * (0.5f + 0.8f * ease)
                    drawCircle(Color(0xFF6B5D66).copy(alpha = 0.7f * (1f - t)), (0.7f + 0.9f * t) * u, Offset(c.x + cos(a) * d, c.y + sin(a) * d))
                }
            }
            FxKind.BOLT -> drawBolt(c, Offset(fx.x2 * u, fx.y2 * u), fx.seed, 1f - t, u)
            FxKind.TRACER -> {
                val end = Offset(fx.x2 * u, fx.y2 * u)
                val color = fx.tower?.color ?: Color.White
                drawLine(color.copy(alpha = 0.7f * (1f - t)), c, end, 0.9f * u * (1f - t) + 1f, StrokeCap.Round)
                drawLine(Color.White.copy(alpha = 1f - t), c, end, 0.32f * u, StrokeCap.Round)
                drawStar(Color.White.copy(alpha = 1f - t), end.x, end.y, (1.2f + 1.6f * ease) * u, 0f)
                drawCircle(Color.White.copy(alpha = 0.8f * (1f - t)), (0.8f + ease) * u, c)
            }
            FxKind.SPARKLE -> {
                for (i in 0 until 6) {
                    val a = fx.seed * 0.11f + i * 1.047f
                    val d = fx.size * u * (0.4f + 0.6f * ease)
                    drawStar(Sun.copy(alpha = 1f - t * t), c.x + cos(a) * d, c.y + sin(a) * d * 0.8f - ease * 2.5f * u, (0.6f + 0.8f * (1f - t)) * u, ow * 0.5f * (1f - t))
                }
            }
            FxKind.GOLD_TEXT -> floatText("+${fx.value}", c.x, c.y - ease * 3.6f * u, 3.1f * u, Sun, 1f - t * t * t)
            FxKind.BEAM -> {
                val end = Offset(fx.x2 * u, fx.y2 * u)
                val color = fx.tower?.color ?: Color.White
                drawLine(color.copy(alpha = 0.55f * (1f - t)), c, end, (0.6f + fx.size) * u, StrokeCap.Round)
                drawLine(Color.White.copy(alpha = 0.95f * (1f - t)), c, end, (0.2f + fx.size * 0.3f) * u, StrokeCap.Round)
                drawCircle(Color.White.copy(alpha = 0.8f * (1f - t)), (0.6f + fx.size) * u, end)
            }
            FxKind.FLAME -> {
                // Puffs of fire along the jet, growing towards the target, then a burst where it lands
                val end = Offset(fx.x2 * u, fx.y2 * u)
                val fade = 1f - t
                for (k in 0..5) {
                    val f = k / 5f
                    val wobble = (((fx.seed * 31 + k * 7919) and 0xFF) / 255f - 0.5f) * 1.2f * u
                    val puff = Offset(c.x + (end.x - c.x) * f + wobble, c.y + (end.y - c.y) * f + wobble * 0.6f)
                    val r = (0.7f + f * fx.size * 0.35f) * u * (0.7f + 0.5f * t)
                    drawCircle(Color(0xFFFF7A2E).copy(alpha = 0.6f * fade), r, puff)
                    drawCircle(Color(0xFFFFD75A).copy(alpha = 0.8f * fade), r * 0.55f, puff)
                }
                drawCircle(Color(0xFFFF8A2B).copy(alpha = 0.3f * fade), fx.size * u * (0.6f + 0.4f * ease), end)
            }
            FxKind.CRIT -> {
                drawStar(Sun.copy(alpha = 1f - t), c.x, c.y, fx.size * u * (1.2f + 1.4f * ease), ow * (1f - t))
                floatText("CRIT!", c.x, c.y - (1.5f + ease * 2.5f) * u, 2.9f * u, Sun, 1f - t * t)
            }
            FxKind.EXECUTE -> {
                // A red slash across the unit
                val r = fx.size * u * (1.1f + 0.5f * ease)
                drawLine(Ink.copy(alpha = 1f - t), Offset(c.x - r, c.y + r * 0.6f), Offset(c.x + r, c.y - r * 0.6f), 1.2f * u, StrokeCap.Round)
                drawLine(Tomato.copy(alpha = 1f - t), Offset(c.x - r, c.y + r * 0.6f), Offset(c.x + r, c.y - r * 0.6f), 0.65f * u, StrokeCap.Round)
            }
            FxKind.LIFE_GAIN -> floatText("+${fx.value}", c.x, c.y - ease * 3.6f * u, 3.4f * u, Leaf, 1f - t * t * t)
            FxKind.LIFE_TEXT -> floatText("-${fx.value}", c.x, c.y - ease * 4.5f * u, 4.6f * u, Tomato, 1f - t * t * t)
            else -> Unit
        }
    }

    private fun DrawScope.drawBolt(from: Offset, to: Offset, seed: Int, alpha: Float, u: Float) {
        val dx = to.x - from.x
        val dy = to.y - from.y
        val len = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        val nx = -dy / len
        val ny = dx / len
        boltPath.rewind()
        boltPath.moveTo(from.x, from.y)
        val segments = 5
        for (i in 1 until segments) {
            val f = i / segments.toFloat()
            // A cheap hash of the seed, so a bolt keeps its shape for its whole (short) life.
            val jitter = (((seed * 31 + i * 7919) and 0xFF) / 255f - 0.5f) * 2.6f * u
            boltPath.lineTo(from.x + dx * f + nx * jitter, from.y + dy * f + ny * jitter)
        }
        boltPath.lineTo(to.x, to.y)
        drawPath(boltPath, Sun.copy(alpha = 0.55f * alpha), style = Stroke(1.5f * u, cap = StrokeCap.Round))
        drawPath(boltPath, Color.White.copy(alpha = alpha), style = Stroke(0.5f * u, cap = StrokeCap.Round))
        drawCircle(Color.White.copy(alpha = alpha), 0.9f * u, to)
    }

    private fun DrawScope.floatText(text: String, x: Float, y: Float, sizePx: Float, color: Color, alpha: Float) {
        val a = (alpha.coerceIn(0f, 1f) * 255f).toInt()
        val canvas = drawContext.canvas.nativeCanvas
        textPaint.textSize = sizePx
        textPaint.style = Paint.Style.STROKE
        textPaint.strokeWidth = sizePx * 0.26f
        textPaint.color = inkArgb
        textPaint.alpha = a
        canvas.drawText(text, x, y, textPaint)
        textPaint.style = Paint.Style.FILL
        textPaint.color = color.toArgb()
        textPaint.alpha = a
        canvas.drawText(text, x, y, textPaint)
    }
}
