package com.towerduel.game.engine

import com.towerduel.game.data.GameData
import com.towerduel.game.data.LaneSpace
import com.towerduel.game.data.MapDef
import com.towerduel.game.data.MapTheme
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

private const val START_X = -8f
private const val END_X = 91f

// What makes a generated track fair to play on.
private const val MIN_LENGTH = 150f
private const val MAX_LENGTH = 300f
private const val EDGE_MARGIN = 5.5f
private const val MIN_BUILD_SPOTS = 90

/** Two stretches of track closer than this, far apart along the path, are touching. */
private const val TOUCH_DISTANCE = 8.6f
private const val TOUCH_AFTER_UNITS = 30f

/** A clean crossing puts a short stretch of track in touch with a later one; more means it runs along itself. */
private const val MAX_TOUCHING_UNITS = 36f

private const val ATTEMPTS = 40

/**
 * Makes new maps: a random track from one of a few families, in a random theme. Every candidate
 * is checked for fairness (long enough, on the lane, room to build, not folded onto itself) and
 * thrown away if it fails, so a generated map plays as well as a hand-made one.
 */
object MapGenerator {

    /** A map for one match: a named one or a fresh one, either way sometimes mirrored top to bottom. */
    fun randomMap(rng: Random = Random.Default): MapDef {
        val wild = if (rng.nextFloat() < GameData.WILD_MAP_CHANCE) generate(rng) else null
        val map = wild ?: GameData.MAPS.random(rng)
        return if (rng.nextBoolean()) flipped(map) else map
    }

    /** A fresh map, or null if no candidate passed the checks (callers fall back to a named map). */
    fun generate(rng: Random): MapDef? {
        repeat(ATTEMPTS) {
            val points = when (rng.nextInt(7)) {
                0 -> zigzag(rng)
                1 -> serpentine(rng)
                2 -> hairpin(rng)
                3 -> wave(rng)
                4 -> loop(rng)
                5 -> spiral(rng)
                else -> remix(rng)
            }
            if (isPlayable(points)) {
                val theme = MapTheme.entries.random(rng)
                return MapDef("wild-${rng.nextInt(1_000_000)}", wildName(theme, rng), theme, points)
            }
        }
        return null
    }

    /** The same map mirrored top to bottom: familiar, but every tower spot is somewhere new. */
    fun flipped(map: MapDef): MapDef =
        map.copy(id = map.id + "-flip", pathPoints = map.pathPoints.map { (x, y) -> x to LaneSpace.HEIGHT - y })

    fun isPlayable(points: List<Pair<Float, Float>>): Boolean = problemWith(points) == null

    /** Why a track is not fit to play on, or null if it is. */
    fun problemWith(points: List<Pair<Float, Float>>): String? {
        val path = LanePath(points)
        if (path.length < MIN_LENGTH) return "too short (${path.length.toInt()} units)"
        if (path.length > MAX_LENGTH) return "too long (${path.length.toInt()} units)"

        for (i in 0 until path.pointCount) {
            val x = path.xs[i]
            val y = path.ys[i]
            if (x > END_X + 3f) return "runs past the keep"
            if (x >= 0f && (y < EDGE_MARGIN || y > LaneSpace.HEIGHT - EDGE_MARGIN)) return "runs off the lane at y = $y"
        }

        // Does the track lie on top of itself?
        val skip = (TOUCH_AFTER_UNITS / LanePath.STEP).toInt()
        val stride = 4
        var touchingSamples = 0
        var i = 0
        while (i < path.pointCount) {
            var j = i + skip
            while (j < path.pointCount) {
                val dx = path.xs[i] - path.xs[j]
                val dy = path.ys[i] - path.ys[j]
                if (dx * dx + dy * dy < TOUCH_DISTANCE * TOUCH_DISTANCE) {
                    touchingSamples++
                    break
                }
                j += stride
            }
            i += stride
        }
        val touchingUnits = touchingSamples * stride * LanePath.STEP
        if (touchingUnits > MAX_TOUCHING_UNITS) return "runs along itself for ${touchingUnits.toInt()} units"

        // Is there room left to build?
        var spots = 0
        var y = 5f
        while (y <= LaneSpace.HEIGHT - 5f) {
            var x = 5f
            while (x <= LaneSpace.WIDTH - 5f) {
                if (path.distanceTo(x, y) >= GameData.PATH_CLEARANCE) spots++
                x += 3f
            }
            y += 3f
        }
        return if (spots < MIN_BUILD_SPOTS) "only $spots places to build" else null
    }

    private fun between(rng: Random, from: Float, to: Float): Float = from + rng.nextFloat() * (to - from)

    /** Up and down the lane two to four times. */
    private fun zigzag(rng: Random): List<Pair<Float, Float>> {
        val turns = 2 + rng.nextInt(3)
        var high = rng.nextBoolean()
        fun band() = if (high) between(rng, 10f, 17f) else between(rng, 45f, 52f)
        val points = ArrayList<Pair<Float, Float>>()
        val first = band()
        points.add(START_X to first)
        points.add(between(rng, 8f, 14f) to first)
        for (t in 1..turns) {
            high = !high
            val x = 14f + 66f * t / turns + between(rng, -3f, 3f)
            points.add(x.coerceAtMost(82f) to band())
        }
        points.add(END_X to between(rng, 16f, 46f))
        return points
    }

    /** Three long rows joined by two turns, like a snake. */
    private fun serpentine(rng: Random): List<Pair<Float, Float>> {
        val top = between(rng, 9f, 13f)
        val mid = between(rng, 28f, 34f)
        val bottom = between(rng, 49f, 53f)
        val right = between(rng, 64f, 82f)
        val left = between(rng, 14f, 34f)
        return listOf(
            START_X to top,
            right - 18f to top, right to top + 4f, right + 4f to (top + mid) / 2f, right - 4f to mid,
            left + 10f to mid, left to mid + 6f, left to bottom - 6f, left + 10f to bottom,
            END_X to bottom
        )
    }

    /** Out along the top, all the way back across the middle, then home along the bottom: a Z. */
    private fun hairpin(rng: Random): List<Pair<Float, Float>> {
        val top = between(rng, 9f, 14f)
        val bottom = between(rng, 48f, 53f)
        val right = between(rng, 66f, 82f)
        val left = between(rng, 12f, 28f)
        val midX = (left + right) / 2f + between(rng, -6f, 6f)
        val midY = (top + bottom) / 2f + between(rng, -5f, 5f)
        return listOf(
            START_X to top,
            right - 12f to top, right to top + 6f,
            midX to midY,
            left to bottom - 6f, left + 12f to bottom,
            END_X to bottom
        )
    }

    /** A sine wave across the lane, two to three crests of it. */
    private fun wave(rng: Random): List<Pair<Float, Float>> {
        val crests = between(rng, 2f, 3f)
        val height = between(rng, 15f, 20f)
        val mid = LaneSpace.HEIGHT / 2f + between(rng, -2f, 2f)
        val phase = if (rng.nextBoolean()) 0f else PI.toFloat()
        val from = 4f
        val to = END_X - 4f
        val points = ArrayList<Pair<Float, Float>>()
        points.add(START_X to mid)
        var x = from
        while (x < to) {
            val angle = (x - from) / (to - from) * crests * 2f * PI.toFloat() + phase
            points.add(x to mid + height * sin(angle))
            x += 6f
        }
        points.add(END_X to mid + height * sin(crests * 2f * PI.toFloat() + phase))
        return points
    }

    /** A straight run with one full loop thrown in: the track crosses itself once. */
    private fun loop(rng: Random): List<Pair<Float, Float>> {
        // The loop is laid out for a radius of 14.5 around (cx, cy) and scaled by k.
        val k = between(rng, 0.85f, 1f)
        val cx = between(rng, 44f, 58f)
        val cy = between(rng, 9.5f + 14f * k, 52.5f - 28f * k)
        val run = cy + 18f * k
        return listOf(
            START_X to run, cx - 32f to run, cx - 8f to run,
            cx + 6f * k to cy + 16f * k, cx + 15f * k to cy + 7f * k, cx + 14f * k to cy - 6f * k,
            cx + 4f * k to cy - 14f * k, cx - 8f * k to cy - 13f * k, cx - 15f * k to cy - 4f * k,
            cx - 13f * k to cy + 9f * k, cx - 6f * k to cy + 20f * k, cx + 4f * k to cy + 28f * k,
            cx + 20f to cy + 28f * k, END_X - 7f to cy + 24f * k, END_X to cy + 16f * k
        )
    }

    /** In along the top, around the edge and inwards: the keep stands in the middle of the lane. */
    private fun spiral(rng: Random): List<Pair<Float, Float>> {
        val top = between(rng, 10f, 12f)
        val bottom = between(rng, 50f, 52f)
        val right = between(rng, 80f, 86f)
        val left = between(rng, 13f, 17f)
        val mid = (top + bottom) / 2f + between(rng, -2f, 2f)
        val end = between(rng, 50f, 62f)
        return listOf(
            START_X to top, 40f to top, right - 8f to top, right to top + 6f, right to bottom - 6f, right - 8f to bottom,
            left + 8f to bottom, left to bottom - 6f, left to mid + 6f, left + 7f to mid, 40f to mid, end to mid
        )
    }

    /** A named map with its inner control points nudged: the same idea, a different track. */
    private fun remix(rng: Random): List<Pair<Float, Float>> {
        val base = GameData.MAPS.random(rng).pathPoints
        return base.mapIndexed { i, (x, y) ->
            // The ends stay put, so the track still starts off-lane and stops at the keep.
            if (i < 2 || i == base.lastIndex) x to y
            else (x + between(rng, -5f, 5f)) to (y + between(rng, -5f, 5f)).coerceIn(9f, LaneSpace.HEIGHT - 9f)
        }
    }

    private fun wildName(theme: MapTheme, rng: Random): String {
        val first = when (theme) {
            MapTheme.MEADOW -> listOf("Daisy", "Bramble", "Willow", "Thistle")
            MapTheme.DUNES -> listOf("Dusty", "Scorpion", "Mirage", "Amber")
            MapTheme.FROST -> listOf("Frozen", "Glacier", "Snowdrift", "Winter")
            MapTheme.EMBER -> listOf("Ashen", "Magma", "Scorched", "Ember")
            MapTheme.SWAMP -> listOf("Murky", "Toadstool", "Reedy", "Mossy")
            MapTheme.AUTUMN -> listOf("Rusty", "Harvest", "Amberleaf", "Pumpkin")
            MapTheme.CRYSTAL -> listOf("Amethyst", "Glimmer", "Quartz", "Prism")
            MapTheme.CANDY -> listOf("Sugar", "Toffee", "Jellybean", "Sherbet")
            MapTheme.NIGHT -> listOf("Midnight", "Starlit", "Owl", "Lantern")
            MapTheme.SKY -> listOf("Cirrus", "Skylark", "Zephyr", "Sunbeam")
            MapTheme.RUINS -> listOf("Crumbled", "Forgotten", "Weathered", "Fallen")
        }.random(rng)
        val second = listOf("Trail", "Run", "Crossing", "Gap", "Twist", "Reach", "Hollow", "Way").random(rng)
        return "$first $second"
    }
}
