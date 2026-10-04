package com.towerduel.game.engine

import kotlin.math.sqrt

/**
 * A lane's track: a smooth curve through a map's control points, resampled into points a fixed
 * [STEP] apart so that "the position [dist] units along the path" is a constant-time lookup.
 */
class LanePath(controlPoints: List<Pair<Float, Float>>) {

    val xs: FloatArray
    val ys: FloatArray
    val length: Float
    val pointCount: Int get() = xs.size

    init {
        val dense = PathMath.smooth(controlPoints, SAMPLES_PER_SEGMENT)
        var total = 0f
        for (i in 1 until dense.size) {
            total += dist(dense[i - 1].first, dense[i - 1].second, dense[i].first, dense[i].second)
        }
        length = total.coerceAtLeast(STEP)

        val count = (length / STEP).toInt() + 1
        xs = FloatArray(count)
        ys = FloatArray(count)
        var segment = 0
        var walked = 0f // path length up to the start of `segment`
        for (i in 0 until count) {
            val target = i * STEP
            while (segment < dense.size - 2) {
                val segLen = dist(
                    dense[segment].first, dense[segment].second,
                    dense[segment + 1].first, dense[segment + 1].second
                )
                if (walked + segLen >= target) break
                walked += segLen
                segment++
            }
            val (x1, y1) = dense[segment]
            val (x2, y2) = dense[(segment + 1).coerceAtMost(dense.size - 1)]
            val segLen = dist(x1, y1, x2, y2)
            val t = if (segLen > 0f) ((target - walked) / segLen).coerceIn(0f, 1f) else 0f
            xs[i] = x1 + (x2 - x1) * t
            ys[i] = y1 + (y2 - y1) * t
        }
    }

    /** Writes x, y and the unit direction of travel at [dist] units along the path into [out] (size 4). */
    fun sample(dist: Float, out: FloatArray) {
        val last = xs.size - 1
        val pos = (dist / STEP).coerceIn(0f, last.toFloat())
        val i = pos.toInt().coerceAtMost(last - 1).coerceAtLeast(0)
        val t = pos - i
        val j = (i + 1).coerceAtMost(last)
        out[0] = xs[i] + (xs[j] - xs[i]) * t
        out[1] = ys[i] + (ys[j] - ys[i]) * t
        val dx = xs[j] - xs[i]
        val dy = ys[j] - ys[i]
        val len = sqrt(dx * dx + dy * dy)
        if (len > 0f) {
            out[2] = dx / len
            out[3] = dy / len
        } else {
            out[2] = 1f
            out[3] = 0f
        }
    }

    /** Distance from (x, y) to the middle of the track. */
    fun distanceTo(x: Float, y: Float): Float {
        var best = Float.MAX_VALUE
        for (i in xs.indices) {
            val dx = xs[i] - x
            val dy = ys[i] - y
            val d = dx * dx + dy * dy
            if (d < best) best = d
        }
        return sqrt(best)
    }

    /** How many lane units of track lie within [range] of (x, y) and inside the visible lane. */
    fun coverage(x: Float, y: Float, range: Float, laneWidth: Float): Float {
        val r2 = range * range
        var hits = 0
        for (i in xs.indices) {
            if (xs[i] < 0f || xs[i] > laneWidth) continue
            val dx = xs[i] - x
            val dy = ys[i] - y
            if (dx * dx + dy * dy <= r2) hits++
        }
        return hits * STEP
    }

    private fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1; val dy = y2 - y1
        return sqrt(dx * dx + dy * dy)
    }

    companion object {
        const val STEP = 0.5f
        private const val SAMPLES_PER_SEGMENT = 14
    }
}

object PathMath {

    /** A Catmull-Rom curve through [points], as a dense polyline. */
    fun smooth(points: List<Pair<Float, Float>>, samplesPerSegment: Int): List<Pair<Float, Float>> {
        if (points.size < 3) return points
        val out = ArrayList<Pair<Float, Float>>(points.size * samplesPerSegment + 1)
        for (i in 0 until points.size - 1) {
            val p0 = points[(i - 1).coerceAtLeast(0)]
            val p1 = points[i]
            val p2 = points[i + 1]
            val p3 = points[(i + 2).coerceAtMost(points.size - 1)]
            for (s in 0 until samplesPerSegment) {
                val t = s / samplesPerSegment.toFloat()
                out.add(catmullRom(p0.first, p1.first, p2.first, p3.first, t) to
                    catmullRom(p0.second, p1.second, p2.second, p3.second, t))
            }
        }
        out.add(points.last())
        return out
    }

    private fun catmullRom(p0: Float, p1: Float, p2: Float, p3: Float, t: Float): Float {
        val t2 = t * t
        val t3 = t2 * t
        return 0.5f * (2f * p1 + (-p0 + p2) * t + (2f * p0 - 5f * p1 + 4f * p2 - p3) * t2 +
            (-p0 + 3f * p1 - 3f * p2 + p3) * t3)
    }
}
