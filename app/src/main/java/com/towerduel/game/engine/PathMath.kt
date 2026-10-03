package com.towerduel.game.engine

import kotlin.math.sqrt

object PathMath {

    fun segmentLengths(points: List<Pair<Float, Float>>): List<Float> {
        val lens = mutableListOf<Float>()
        for (i in 0 until points.size - 1) {
            val (x1, y1) = points[i]
            val (x2, y2) = points[i + 1]
            lens.add(sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1)))
        }
        return lens
    }

    fun totalLength(points: List<Pair<Float, Float>>): Float = segmentLengths(points).sum()

    /** Returns the (x, y) position along [points] at fraction [progress] (0f..1f) of total path length. */
    fun pointAtProgress(points: List<Pair<Float, Float>>, progress: Float): Pair<Float, Float> =
        pointAtProgress(points, segmentLengths(points), progress)

    /** Same as above, for callers that sample the same path every tick and keep [lens] around. */
    fun pointAtProgress(
        points: List<Pair<Float, Float>>,
        lens: List<Float>,
        progress: Float
    ): Pair<Float, Float> {
        if (points.size < 2) return points.firstOrNull() ?: (0f to 0f)
        val clamped = progress.coerceIn(0f, 1f)
        val total = lens.sum()
        if (total <= 0f) return points.first()
        var distTarget = clamped * total
        for (i in lens.indices) {
            val segLen = lens[i]
            if (distTarget <= segLen || i == lens.lastIndex) {
                val t = if (segLen > 0f) (distTarget / segLen).coerceIn(0f, 1f) else 0f
                val (x1, y1) = points[i]
                val (x2, y2) = points[i + 1]
                return (x1 + (x2 - x1) * t) to (y1 + (y2 - y1) * t)
            }
            distTarget -= segLen
        }
        return points.last()
    }
}
