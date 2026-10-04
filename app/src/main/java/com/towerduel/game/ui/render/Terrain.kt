package com.towerduel.game.ui.render

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.towerduel.game.data.GameData
import com.towerduel.game.data.LaneSpace
import com.towerduel.game.data.MapDef
import com.towerduel.game.data.MapTheme
import com.towerduel.game.engine.LanePath
import com.towerduel.game.ui.theme.Ink
import com.towerduel.game.ui.theme.darken
import com.towerduel.game.ui.theme.lighten
import kotlin.random.Random

private class TerrainPalette(
    val ground: Color,
    val groundLight: Color,
    val groundDark: Color,
    val track: Color,
    val trackLight: Color,
    val trackEdge: Color
)

private fun paletteFor(theme: MapTheme): TerrainPalette = when (theme) {
    MapTheme.MEADOW -> TerrainPalette(
        ground = Color(0xFF7FCB4C), groundLight = Color(0xFF98DC62), groundDark = Color(0xFF66B53C),
        track = Color(0xFFEBC57C), trackLight = Color(0xFFF7DC9E), trackEdge = Color(0xFFBF8F48)
    )
    MapTheme.DUNES -> TerrainPalette(
        ground = Color(0xFFF2CF7E), groundLight = Color(0xFFFADFA0), groundDark = Color(0xFFE0B560),
        track = Color(0xFFCB8F5C), trackLight = Color(0xFFDFA878), trackEdge = Color(0xFF9B6538)
    )
    MapTheme.FROST -> TerrainPalette(
        ground = Color(0xFFE4F1FA), groundLight = Color(0xFFFFFFFF), groundDark = Color(0xFFC6DFF2),
        track = Color(0xFFA5C2DB), trackLight = Color(0xFFC2D9EA), trackEdge = Color(0xFF6D8FB0)
    )
    MapTheme.EMBER -> TerrainPalette(
        ground = Color(0xFF63505A), groundLight = Color(0xFF77626C), groundDark = Color(0xFF4D3D46),
        track = Color(0xFFD2C1AE), trackLight = Color(0xFFE5D8C8), trackEdge = Color(0xFF8D7968)
    )
}

/**
 * The static part of a lane (ground, track, scenery) as a bitmap. It never changes during a
 * match, so it is painted once per size and then blitted every frame.
 */
object TerrainCache {
    private class Entry(val key: String, val bitmap: ImageBitmap)
    private val entries = ArrayList<Entry>()

    fun get(map: MapDef, path: LanePath, width: Int, height: Int, density: Density): ImageBitmap {
        val key = "${map.id}:$width:$height"
        entries.firstOrNull { it.key == key }?.let { return it.bitmap }
        val bitmap = ImageBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1))
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
            drawTerrain(map, path, size.width / LaneSpace.WIDTH)
        }
        if (entries.size >= 4) entries.removeAt(0)
        entries.add(Entry(key, bitmap))
        return bitmap
    }
}

private fun DrawScope.drawTerrain(map: MapDef, path: LanePath, u: Float) {
    val pal = paletteFor(map.theme)
    // Seeded by the map, so a map looks the same in every match and on both lanes.
    val rnd = Random(map.id.hashCode())
    val w = LaneSpace.WIDTH
    val h = LaneSpace.HEIGHT
    val half = GameData.PATH_HALF_WIDTH

    drawRect(pal.ground)

    // Big soft patches break up the flat ground colour.
    repeat(48) {
        val rx = (4f + rnd.nextFloat() * 8f) * u
        val ry = rx * (0.55f + rnd.nextFloat() * 0.3f)
        val x = rnd.nextFloat() * w * u
        val y = rnd.nextFloat() * h * u
        val color = if (rnd.nextBoolean()) pal.groundLight else pal.groundDark
        drawOval(color.copy(alpha = 0.5f), Offset(x - rx, y - ry), Size(rx * 2f, ry * 2f))
    }

    // Small ground detail, kept off the track.
    repeat(170) {
        val x = rnd.nextFloat() * w
        val y = rnd.nextFloat() * h
        if (path.distanceTo(x, y) > half + 1.2f) groundDetail(map.theme, pal, x * u, y * u, u, rnd)
    }

    // The track: ink outline, darker edge, surface, and a lighter worn middle.
    val track = Path()
    var i = 0
    while (i < path.pointCount) {
        if (i == 0) track.moveTo(path.xs[i] * u, path.ys[i] * u) else track.lineTo(path.xs[i] * u, path.ys[i] * u)
        i += 2
    }
    track.lineTo(path.xs[path.pointCount - 1] * u, path.ys[path.pointCount - 1] * u)
    fun band(color: Color, width: Float, alpha: Float = 1f) =
        drawPath(track, color, alpha = alpha, style = Stroke(width * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
    band(Ink, half * 2f + 1.5f)
    band(pal.trackEdge, half * 2f + 0.75f)
    band(pal.track, half * 2f - 0.6f)
    band(pal.trackLight, half * 0.95f, alpha = 0.6f)

    // Pebbles and scuffs on the track
    val scratch = FloatArray(4)
    var d = 2f
    while (d < path.length) {
        path.sample(d, scratch)
        val off = (rnd.nextFloat() * 2f - 1f) * (half - 1.1f)
        val px = (scratch[0] - scratch[3] * off) * u
        val py = (scratch[1] + scratch[2] * off) * u
        val r = (0.25f + rnd.nextFloat() * 0.35f) * u
        drawOval(pal.trackEdge.copy(alpha = 0.55f), Offset(px - r * 1.3f, py - r), Size(r * 2.6f, r * 2f))
        d += 2.2f + rnd.nextFloat() * 3.5f
    }

    // Scenery. Flat and small on purpose: towers can be built on top of it.
    var placed = 0
    var tries = 0
    while (placed < 30 && tries < 400) {
        tries++
        val x = 2f + rnd.nextFloat() * (w - 4f)
        val y = 2f + rnd.nextFloat() * (h - 4f)
        if (path.distanceTo(x, y) < half + 3.2f) continue
        prop(map.theme, pal, x * u, y * u, u, rnd)
        placed++
    }
}

private fun DrawScope.groundDetail(theme: MapTheme, pal: TerrainPalette, x: Float, y: Float, u: Float, rnd: Random) {
    val stroke = 0.28f * u
    when (theme) {
        MapTheme.MEADOW -> {
            // A tuft of three blades
            val c = pal.groundDark.darken(0.12f)
            for (k in -1..1) {
                drawLine(c, Offset(x + k * 0.45f * u, y), Offset(x + k * 0.85f * u, y - (0.9f + 0.3f * (1 - k * k)) * u), stroke, StrokeCap.Round)
            }
        }
        MapTheme.DUNES -> {
            // A wind ripple
            val len = (1.2f + rnd.nextFloat() * 1.6f) * u
            drawLine(pal.groundDark.darken(0.08f), Offset(x - len, y), Offset(x + len, y - 0.2f * u), stroke, StrokeCap.Round)
        }
        MapTheme.FROST -> {
            drawCircle(Color.White, (0.2f + rnd.nextFloat() * 0.3f) * u, Offset(x, y))
            drawCircle(pal.groundDark.darken(0.06f), 0.2f * u, Offset(x + 1.1f * u, y + 0.5f * u))
        }
        MapTheme.EMBER -> {
            // A glowing crack
            val dx = (rnd.nextFloat() - 0.5f) * 2.4f * u
            val dy = (rnd.nextFloat() - 0.5f) * 1.6f * u
            val hot = rnd.nextFloat() < 0.35f
            drawLine(
                if (hot) Color(0xFFFF8A3C).copy(alpha = 0.8f) else pal.groundDark.darken(0.2f),
                Offset(x, y), Offset(x + dx, y + dy), stroke, StrokeCap.Round
            )
        }
    }
}

private fun DrawScope.prop(theme: MapTheme, pal: TerrainPalette, x: Float, y: Float, u: Float, rnd: Random) {
    val ow = OUTLINE * 0.8f * u
    val pick = rnd.nextFloat()
    when (theme) {
        MapTheme.MEADOW -> if (pick < 0.6f) {
            // Flowers
            val petal = when (rnd.nextInt(3)) {
                0 -> Color(0xFFFFFFFF)
                1 -> Color(0xFFFF9EC4)
                else -> Color(0xFFFFE066)
            }
            repeat(3) {
                val fx = x + (rnd.nextFloat() - 0.5f) * 3.2f * u
                val fy = y + (rnd.nextFloat() - 0.5f) * 2.2f * u
                blob(petal, fx, fy, 0.55f * u, ow * 0.7f)
                drawCircle(Color(0xFFF2A21E), 0.22f * u, Offset(fx, fy))
            }
        } else {
            // A low bush
            val green = Color(0xFF3F9A3A)
            blob(green, x - 0.9f * u, y + 0.2f * u, 1.1f * u, ow)
            blob(green, x + 0.9f * u, y + 0.3f * u, 1f * u, ow)
            blob(green.lighten(0.12f), x, y - 0.4f * u, 1.25f * u, ow)
            drawCircle(Color.White.copy(alpha = 0.25f), 0.45f * u, Offset(x - 0.3f * u, y - 0.8f * u))
        }
        MapTheme.DUNES -> if (pick < 0.45f) {
            // Cactus
            val green = Color(0xFF58A857)
            slab(green, x - 1.5f * u, y - 0.9f * u, 0.8f * u, 1.3f * u, 0.4f * u, ow)
            slab(green, x + 0.7f * u, y - 1.5f * u, 0.8f * u, 1.3f * u, 0.4f * u, ow)
            slab(green, x - 0.6f * u, y - 2.2f * u, 1.2f * u, 3.4f * u, 0.6f * u, ow)
            drawRoundRect(Color.White.copy(alpha = 0.3f), Offset(x - 0.35f * u, y - 1.9f * u), Size(0.3f * u, 2.4f * u), CornerRadius(0.15f * u))
        } else {
            // Sandstone rocks
            val rock = Color(0xFFC9A873)
            blob(rock.darken(0.12f), x + 0.8f * u, y + 0.2f * u, 0.9f * u, ow)
            blob(rock, x - 0.3f * u, y, 1.2f * u, ow)
            drawCircle(Color.White.copy(alpha = 0.3f), 0.4f * u, Offset(x - 0.6f * u, y - 0.4f * u))
        }
        MapTheme.FROST -> if (pick < 0.5f) {
            // Snowy pine
            val pine = Color(0xFF3E9273)
            shape(pine, ow, x, y - 2.6f * u, x + 1.5f * u, y + 0.2f * u, x - 1.5f * u, y + 0.2f * u)
            shape(pine, ow, x, y - 1.2f * u, x + 1.8f * u, y + 1.6f * u, x - 1.8f * u, y + 1.6f * u)
            shape(Color.White, 0f, x, y - 2.6f * u, x + 0.75f * u, y - 1.2f * u, x - 0.75f * u, y - 1.2f * u)
        } else {
            // Ice shards
            val ice = Color(0xFF9BE4F6)
            shape(ice.darken(0.12f), ow, x + 0.9f * u, y - 1.2f * u, x + 1.6f * u, y + 0.2f * u, x + 0.9f * u, y + 1f * u, x + 0.2f * u, y + 0.2f * u)
            shape(ice, ow, x - 0.3f * u, y - 2f * u, x + 0.6f * u, y, x - 0.3f * u, y + 1.1f * u, x - 1.2f * u, y)
            shape(Color.White.copy(alpha = 0.7f), 0f, x - 0.3f * u, y - 1.6f * u, x + 0.1f * u, y - 0.1f * u, x - 0.6f * u, y - 0.1f * u)
        }
        MapTheme.EMBER -> if (pick < 0.5f) {
            // Lava pool
            val rx = (1.4f + rnd.nextFloat() * 0.9f) * u
            drawOval(Ink, Offset(x - rx - ow, y - rx * 0.6f - ow), Size(2f * (rx + ow), 1.2f * rx + 2f * ow))
            drawOval(Color(0xFFFF7A2E), Offset(x - rx, y - rx * 0.6f), Size(2f * rx, 1.2f * rx))
            drawOval(Color(0xFFFFD45C), Offset(x - rx * 0.55f, y - rx * 0.3f), Size(rx * 1.1f, rx * 0.6f))
        } else {
            // Basalt
            val rock = Color(0xFF3B2F38)
            blob(rock.lighten(0.08f), x + 0.9f * u, y + 0.3f * u, 0.85f * u, ow)
            blob(rock, x - 0.2f * u, y, 1.25f * u, ow)
            drawCircle(pal.groundLight.copy(alpha = 0.5f), 0.4f * u, Offset(x - 0.6f * u, y - 0.45f * u))
        }
    }
}
