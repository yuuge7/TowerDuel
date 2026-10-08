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
    MapTheme.SWAMP -> TerrainPalette(
        ground = Color(0xFF5F9470), groundLight = Color(0xFF76AB84), groundDark = Color(0xFF4A7A5B),
        track = Color(0xFFA68E6E), trackLight = Color(0xFFBFA888), trackEdge = Color(0xFF6F5B43)
    )
    MapTheme.AUTUMN -> TerrainPalette(
        ground = Color(0xFFD3A54A), groundLight = Color(0xFFE3BC66), groundDark = Color(0xFFB98B38),
        track = Color(0xFFEBDDBD), trackLight = Color(0xFFF8EFD8), trackEdge = Color(0xFFB09868)
    )
    MapTheme.CRYSTAL -> TerrainPalette(
        ground = Color(0xFF8E7CD8), groundLight = Color(0xFFA696E6), groundDark = Color(0xFF7566C2),
        track = Color(0xFFE6E0F5), trackLight = Color(0xFFF5F2FF), trackEdge = Color(0xFF9A8FC0)
    )
    MapTheme.CANDY -> TerrainPalette(
        ground = Color(0xFFF7A8C8), groundLight = Color(0xFFFFC2DA), groundDark = Color(0xFFEF8DB5),
        track = Color(0xFFFFF1D6), trackLight = Color(0xFFFFFAEC), trackEdge = Color(0xFFD9A066)
    )
    MapTheme.NIGHT -> TerrainPalette(
        ground = Color(0xFF36527A), groundLight = Color(0xFF45658F), groundDark = Color(0xFF2A4265),
        track = Color(0xFFB9C6DA), trackLight = Color(0xFFD5DEEC), trackEdge = Color(0xFF6B7C99)
    )
    MapTheme.SKY -> TerrainPalette(
        ground = Color(0xFF9FD3F5), groundLight = Color(0xFFCBE8FB), groundDark = Color(0xFF7FBFEA),
        track = Color(0xFFF6D98A), trackLight = Color(0xFFFFEDB8), trackEdge = Color(0xFFCFA348)
    )
    MapTheme.RUINS -> TerrainPalette(
        ground = Color(0xFF9AA58C), groundLight = Color(0xFFAEB89F), groundDark = Color(0xFF86917A),
        track = Color(0xFFD9D2C0), trackLight = Color(0xFFE9E4D6), trackEdge = Color(0xFF8E8571)
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
        MapTheme.SWAMP -> {
            // A pair of reeds
            val c = pal.groundDark.darken(0.2f)
            drawLine(c, Offset(x, y), Offset(x + 0.2f * u, y - 1.3f * u), stroke, StrokeCap.Round)
            drawLine(c, Offset(x + 0.6f * u, y), Offset(x + 0.5f * u, y - 0.9f * u), stroke, StrokeCap.Round)
        }
        MapTheme.AUTUMN -> {
            // A fallen leaf
            val leaf = when (rnd.nextInt(3)) {
                0 -> Color(0xFFD9622B)
                1 -> Color(0xFFB8402A)
                else -> Color(0xFF8C5A2B)
            }
            drawOval(leaf.copy(alpha = 0.85f), Offset(x - 0.5f * u, y - 0.28f * u), Size(1f * u, 0.56f * u))
        }
        MapTheme.CRYSTAL -> {
            // A glint in the rock
            val r = (0.3f + rnd.nextFloat() * 0.3f) * u
            shape(Color.White.copy(alpha = 0.55f), 0f, x, y - r * 1.6f, x + r, y, x, y + r * 1.6f, x - r, y)
        }
        MapTheme.CANDY -> {
            // A sprinkle
            val sprinkle = when (rnd.nextInt(4)) {
                0 -> Color(0xFFFFFFFF)
                1 -> Color(0xFF7FD6F2)
                2 -> Color(0xFFFFE066)
                else -> Color(0xFFB98CF0)
            }
            val dx = (rnd.nextFloat() - 0.5f) * 1.6f * u
            val dy = (rnd.nextFloat() - 0.5f) * 1.6f * u
            drawLine(sprinkle, Offset(x, y), Offset(x + dx, y + dy), 0.4f * u, StrokeCap.Round)
        }
        MapTheme.SKY -> {
            // A wisp of cloud
            val len = (1.2f + rnd.nextFloat() * 1.8f) * u
            drawOval(Color.White.copy(alpha = 0.55f), Offset(x - len, y - 0.3f * u), Size(len * 2f, 0.6f * u))
        }
        MapTheme.RUINS -> if (rnd.nextFloat() < 0.5f) {
            // A crack in the old paving
            val dx = (rnd.nextFloat() - 0.5f) * 2.6f * u
            val dy = (rnd.nextFloat() - 0.5f) * 1.6f * u
            drawLine(pal.groundDark.darken(0.2f), Offset(x, y), Offset(x + dx, y + dy), stroke, StrokeCap.Round)
        } else {
            // Moss
            drawOval(Color(0xFF6FA35A).copy(alpha = 0.6f), Offset(x - 0.7f * u, y - 0.35f * u), Size(1.4f * u, 0.7f * u))
        }
        MapTheme.NIGHT -> if (rnd.nextFloat() < 0.4f) {
            // A firefly
            drawCircle(Color(0xFFFFF2A0).copy(alpha = 0.25f), 0.7f * u, Offset(x, y))
            drawCircle(Color(0xFFFFF2A0), 0.22f * u, Offset(x, y))
        } else {
            val c = pal.groundDark.darken(0.15f)
            for (k in -1..1) {
                drawLine(c, Offset(x + k * 0.45f * u, y), Offset(x + k * 0.85f * u, y - (0.9f + 0.3f * (1 - k * k)) * u), stroke, StrokeCap.Round)
            }
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
        MapTheme.SWAMP -> if (pick < 0.5f) {
            // A pool with a lily pad
            val rx = (1.5f + rnd.nextFloat() * 0.9f) * u
            drawOval(Ink, Offset(x - rx - ow, y - rx * 0.6f - ow), Size(2f * (rx + ow), 1.2f * rx + 2f * ow))
            drawOval(Color(0xFF3F7F8C), Offset(x - rx, y - rx * 0.6f), Size(2f * rx, 1.2f * rx))
            drawOval(Color(0xFF7FD06A), Offset(x - rx * 0.5f, y - rx * 0.25f), Size(rx * 0.8f, rx * 0.5f))
            drawCircle(Color(0xFFFF9EC4), 0.25f * u, Offset(x - rx * 0.1f, y))
        } else {
            // Toadstools
            for (k in 0..1) {
                val mx = x + (k * 1.6f - 0.8f) * u
                val my = y + k * 0.4f * u
                slab(Color(0xFFF4E6C8), mx - 0.25f * u, my, 0.5f * u, 0.8f * u, 0.2f * u, ow * 0.8f)
                drawArc(Ink, 180f, 180f, true, Offset(mx - 0.85f * u - ow, my - 0.75f * u - ow), Size(1.7f * u + 2f * ow, 1.5f * u + 2f * ow))
                drawArc(Color(0xFFD9483B), 180f, 180f, true, Offset(mx - 0.85f * u, my - 0.75f * u), Size(1.7f * u, 1.5f * u))
                drawCircle(Color.White, 0.16f * u, Offset(mx - 0.3f * u, my - 0.35f * u))
                drawCircle(Color.White, 0.13f * u, Offset(mx + 0.35f * u, my - 0.3f * u))
            }
        }
        MapTheme.AUTUMN -> if (pick < 0.5f) {
            // A bush that has turned
            val bush = Color(0xFFD9622B)
            blob(bush.darken(0.12f), x - 0.9f * u, y + 0.2f * u, 1.1f * u, ow)
            blob(Color(0xFFB8402A), x + 0.9f * u, y + 0.3f * u, 1f * u, ow)
            blob(bush, x, y - 0.4f * u, 1.25f * u, ow)
            drawCircle(Color.White.copy(alpha = 0.25f), 0.45f * u, Offset(x - 0.3f * u, y - 0.8f * u))
        } else {
            // A pumpkin
            val pumpkin = Color(0xFFF2882B)
            drawOval(Ink, Offset(x - 1.3f * u - ow, y - 0.95f * u - ow), Size(2.6f * u + 2f * ow, 1.9f * u + 2f * ow))
            drawOval(pumpkin, Offset(x - 1.3f * u, y - 0.95f * u), Size(2.6f * u, 1.9f * u))
            drawOval(pumpkin.darken(0.15f), Offset(x - 0.45f * u, y - 0.95f * u), Size(0.9f * u, 1.9f * u), style = Stroke(0.2f * u))
            slab(Color(0xFF5E8F3A), x - 0.2f * u, y - 1.5f * u, 0.4f * u, 0.7f * u, 0.15f * u, ow * 0.7f)
        }
        MapTheme.CRYSTAL -> if (pick < 0.6f) {
            // A cluster of crystals
            val gem = if (rnd.nextBoolean()) Color(0xFF7FE3F2) else Color(0xFFFF9EEA)
            shape(gem.darken(0.15f), ow, x + 1f * u, y - 1.1f * u, x + 1.7f * u, y + 0.3f * u, x + 1f * u, y + 1f * u, x + 0.3f * u, y + 0.3f * u)
            shape(gem.darken(0.08f), ow, x - 1.1f * u, y - 0.7f * u, x - 0.5f * u, y + 0.3f * u, x - 1.1f * u, y + 1f * u, x - 1.7f * u, y + 0.3f * u)
            shape(gem, ow, x - 0.1f * u, y - 2.2f * u, x + 0.8f * u, y - 0.1f * u, x - 0.1f * u, y + 1.2f * u, x - 1f * u, y - 0.1f * u)
            shape(Color.White.copy(alpha = 0.7f), 0f, x - 0.1f * u, y - 1.8f * u, x + 0.3f * u, y - 0.2f * u, x - 0.4f * u, y - 0.2f * u)
        } else {
            // A geode, cracked open
            val rock = Color(0xFF5A5470)
            blob(rock, x, y, 1.35f * u, ow)
            drawOval(Color(0xFFC79BF2), Offset(x - 0.85f * u, y - 0.65f * u), Size(1.7f * u, 1.3f * u))
            drawOval(Color.White.copy(alpha = 0.8f), Offset(x - 0.4f * u, y - 0.35f * u), Size(0.7f * u, 0.5f * u))
        }
        MapTheme.CANDY -> if (pick < 0.5f) {
            // A lollipop, lying where it fell
            val swirl = if (rnd.nextBoolean()) Color(0xFFFF5C8A) else Color(0xFF58C7D8)
            slab(Color.White, x - 0.2f * u, y, 0.4f * u, 2f * u, 0.2f * u, ow * 0.8f)
            blob(swirl, x, y - 0.6f * u, 1.25f * u, ow)
            drawCircle(Color.White, 0.8f * u, Offset(x, y - 0.6f * u), style = Stroke(0.3f * u))
            drawCircle(Color.White, 0.3f * u, Offset(x, y - 0.6f * u))
        } else {
            // Gumdrops
            val drops = arrayOf(Color(0xFF7FD06A), Color(0xFFFFB03C), Color(0xFFB98CF0))
            for (k in 0..2) {
                val gx = x + (k - 1) * 1.5f * u
                val gy = y + (if (k == 1) -0.5f else 0.3f) * u
                drawArc(Ink, 180f, 180f, true, Offset(gx - 0.8f * u - ow, gy - 0.9f * u - ow), Size(1.6f * u + 2f * ow, 1.8f * u + 2f * ow))
                drawArc(drops[k], 180f, 180f, true, Offset(gx - 0.8f * u, gy - 0.9f * u), Size(1.6f * u, 1.8f * u))
                drawCircle(Color.White.copy(alpha = 0.7f), 0.18f * u, Offset(gx - 0.3f * u, gy - 0.5f * u))
            }
        }
        MapTheme.SKY -> if (pick < 0.7f) {
            // A cloud
            blob(Color.White, x - 1.1f * u, y + 0.3f * u, 1f * u, ow)
            blob(Color.White, x + 1.2f * u, y + 0.35f * u, 0.9f * u, ow)
            blob(Color.White, x, y - 0.35f * u, 1.3f * u, ow)
            drawRoundRect(Color.White, Offset(x - 1.6f * u, y - 0.1f * u), Size(3.3f * u, 1.2f * u), CornerRadius(0.6f * u))
            drawCircle(pal.groundLight, 0.5f * u, Offset(x + 0.3f * u, y + 0.5f * u))
        } else {
            // A fallen star
            drawCircle(Color(0xFFFFE066).copy(alpha = 0.3f), 1.6f * u, Offset(x, y))
            drawStar(Color(0xFFFFE066), x, y, 1.2f * u, ow)
        }
        MapTheme.RUINS -> if (pick < 0.5f) {
            // The stump of a column, seen from above
            blob(Color(0xFFE2DCCB), x, y, 1.25f * u, ow)
            drawCircle(Color(0xFFBDB5A0), 0.8f * u, Offset(x, y))
            drawCircle(Color(0xFFE2DCCB), 0.8f * u, Offset(x, y), style = Stroke(0.2f * u))
            drawCircle(Color(0xFF6FA35A), 0.3f * u, Offset(x + 0.7f * u, y + 0.6f * u))
        } else {
            // Fallen blocks
            val stone = Color(0xFFCFC8B4)
            slab(stone.darken(0.12f), x - 1.5f * u, y - 0.2f * u, 1.7f * u, 1.1f * u, 0.2f * u, ow)
            slab(stone, x - 0.2f * u, y - 0.9f * u, 1.8f * u, 1.2f * u, 0.2f * u, ow)
            drawCircle(Color(0xFF6FA35A), 0.28f * u, Offset(x - 1.1f * u, y + 0.75f * u))
        }
        MapTheme.NIGHT -> if (pick < 0.55f) {
            // Mushrooms that glow in the dark
            val glow = Color(0xFF7FF2E0)
            for (k in 0..1) {
                val mx = x + (k * 1.7f - 0.85f) * u
                val my = y + k * 0.4f * u
                drawCircle(glow.copy(alpha = 0.22f), 1.6f * u, Offset(mx, my - 0.3f * u))
                slab(Color(0xFFD5DEEC), mx - 0.22f * u, my, 0.44f * u, 0.8f * u, 0.2f * u, ow * 0.8f)
                drawArc(Ink, 180f, 180f, true, Offset(mx - 0.85f * u - ow, my - 0.75f * u - ow), Size(1.7f * u + 2f * ow, 1.5f * u + 2f * ow))
                drawArc(glow, 180f, 180f, true, Offset(mx - 0.85f * u, my - 0.75f * u), Size(1.7f * u, 1.5f * u))
                drawCircle(Color.White, 0.15f * u, Offset(mx - 0.3f * u, my - 0.35f * u))
            }
        } else {
            // A pond with the moon in it
            val rx = (1.6f + rnd.nextFloat() * 0.9f) * u
            drawOval(Ink, Offset(x - rx - ow, y - rx * 0.6f - ow), Size(2f * (rx + ow), 1.2f * rx + 2f * ow))
            drawOval(Color(0xFF1E2F52), Offset(x - rx, y - rx * 0.6f), Size(2f * rx, 1.2f * rx))
            drawCircle(Color(0xFFFFF6DD), rx * 0.26f, Offset(x - rx * 0.2f, y - rx * 0.05f))
            drawCircle(Color(0xFF1E2F52), rx * 0.22f, Offset(x - rx * 0.08f, y - rx * 0.12f))
        }
    }
}
