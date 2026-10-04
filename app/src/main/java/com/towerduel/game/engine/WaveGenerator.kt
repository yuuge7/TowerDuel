package com.towerduel.game.engine

import com.towerduel.game.data.EnemySendType
import com.towerduel.game.data.GameData
import com.towerduel.game.data.Wave
import com.towerduel.game.data.WaveGroup
import kotlin.math.roundToInt
import kotlin.random.Random

/** Most units one batch may hold; keeps a cheap unit from flooding the lane with hundreds. */
private const val MAX_GROUP = 28

/** A wave may not take longer than this to walk on, or it would run into the next one. */
private const val MAX_GROUP_SPAN_MS = 11_000f

/**
 * Builds a round's wave from the match's roster: a health budget that grows with the level,
 * spent on a random theme. No two matches get the same waves.
 */
object WaveGenerator {

    private enum class Shape { MIXED, RUSH, SWARM, AIR, HEAVY, BOSS }

    /** Total wave health (before the per-level toughness scale) at [level], 1..WAVE_LEVELS. */
    fun budget(level: Int): Float = 70f + 45f * level + 5f * level * level

    /** Health one unit brings, counting what it bursts into. */
    fun unitHp(type: EnemySendType): Float {
        var hp = type.maxHp
        val childId = type.spawnOnDeathId
        if (childId != null) hp += GameData.unit(childId).maxHp * type.spawnOnDeathCount
        return hp
    }

    /** How much harder a unit is to kill than its health says. */
    private fun toughness(type: EnemySendType): Float {
        var t = 1f
        if (type.damageResistancePct > 0f) t *= 1.6f
        if (type.armor > 0f) t *= 1.3f
        if (type.phaseMs > 0L) t *= 1.3f
        if (type.regenPerSecond > 0f) t *= 1.2f
        return t
    }

    fun generate(level: Int, roster: List<EnemySendType>, rng: Random, budgetScale: Float = 1f): Wave {
        val eligible = roster.filter { it.unlockRound <= level }.ifEmpty { roster.take(1) }
        val budget = budget(level) * budgetScale

        val finishers = eligible.filter { it.maxHp >= 500f }
        val regulars = eligible.filter { it.maxHp < 500f }.ifEmpty { eligible }
        val heavies = regulars.filter { it.maxHp >= 100f }
        val flyers = regulars.filter { it.flying }
        val swarmers = regulars.filter { it.count > 1 || it.spawnOnDeathCount >= 5 }
        val sprinters = regulars.filter { it.speed >= 11f && !it.flying }
        val escorts = regulars.filter { it.healPerSecond > 0f || it.hasteAuraPct > 0f }
        val filler = regulars.firstOrNull { it.id == "grunt" } ?: regulars.first()

        val shape = when {
            // Every fifth level from the tenth is a boss round, if the roster has anything boss-sized by then.
            level >= 10 && level % 5 == 0 && finishers.isNotEmpty() -> Shape.BOSS
            else -> {
                val options = ArrayList<Shape>()
                repeat(4) { options.add(Shape.MIXED) }
                if (sprinters.isNotEmpty() && level >= 2) repeat(2) { options.add(Shape.RUSH) }
                if (swarmers.isNotEmpty()) repeat(2) { options.add(Shape.SWARM) }
                if (flyers.isNotEmpty()) repeat(2) { options.add(Shape.AIR) }
                if (heavies.isNotEmpty()) repeat(2) { options.add(Shape.HEAVY) }
                options[rng.nextInt(options.size)]
            }
        }

        // What the wave is made of, as shares of the budget.
        val parts = ArrayList<Pair<EnemySendType, Float>>()
        val title: String?
        when (shape) {
            Shape.MIXED -> {
                title = null
                val kinds = regulars.shuffled(rng).take(if (level >= 6) 3 else 2)
                val weights = kinds.map { 0.4f + rng.nextFloat() }
                val total = weights.sum()
                for ((i, kind) in kinds.withIndex()) parts.add(kind to weights[i] / total)
            }
            Shape.RUSH -> {
                title = "RUSH"
                parts.add(sprinters.random(rng) to 0.75f)
                parts.add(filler to 0.25f)
            }
            Shape.SWARM -> {
                title = "SWARM"
                parts.add(swarmers.random(rng) to 0.7f)
                parts.add(regulars.random(rng) to 0.3f)
            }
            Shape.AIR -> {
                title = "AIR RAID"
                parts.add(flyers.random(rng) to 0.7f)
                parts.add(regulars.random(rng) to 0.3f)
            }
            Shape.HEAVY -> {
                title = "HEAVY ARMOUR"
                parts.add(heavies.random(rng) to 0.7f)
                parts.add((escorts.randomOrNull(rng) ?: filler) to 0.3f)
            }
            Shape.BOSS -> {
                title = "BOSS ROUND"
                parts.add(finishers.random(rng) to 0.6f)
                parts.add((heavies.randomOrNull(rng) ?: filler) to 0.25f)
                parts.add((escorts.randomOrNull(rng) ?: filler) to 0.15f)
            }
        }

        // Shares to head counts. The same unit picked twice is one batch.
        val counts = LinkedHashMap<EnemySendType, Int>()
        var spent = 0f
        for ((type, share) in parts) {
            val each = unitHp(type) * toughness(type)
            // A unit too big for its share is left out rather than rounded up to one: at a low
            // level a single Tank would otherwise be most of the wave on its own.
            val count = (budget * share / each).roundToInt().coerceAtMost(MAX_GROUP)
            if (count <= 0) continue
            counts[type] = ((counts[type] ?: 0) + count).coerceAtMost(MAX_GROUP)
            spent += count * each
        }
        // A skipped unit, or a capped batch of small ones, leaves budget unspent. Top it up with the
        // biggest units that still fit, so a late wave is made of heavyweights, not of hundreds of Grunts.
        val reinforcements = (regulars + (if (level >= 12) finishers else emptyList())).sortedByDescending { unitHp(it) }
        var passes = 0
        while (spent < budget * 0.85f && passes < 4) {
            passes++
            val remaining = budget - spent
            val type = reinforcements.firstOrNull { unitHp(it) * toughness(it) <= remaining && (counts[it] ?: 0) < MAX_GROUP }
                ?: filler
            val each = unitHp(type) * toughness(type)
            val room = MAX_GROUP - (counts[type] ?: 0)
            val extra = (remaining / each).roundToInt().coerceIn(if (counts.isEmpty()) 1 else 0, room)
            if (extra <= 0) break
            counts[type] = (counts[type] ?: 0) + extra
            spent += extra * each
        }

        val groups = ArrayList<WaveGroup>()
        var delay = 0f
        for ((type, count) in counts) {
            val baseGap = when {
                type.maxHp >= 500f -> 4500f
                type.maxHp >= 120f -> 1500f + rng.nextFloat() * 900f
                type.maxHp >= 30f -> 550f + rng.nextFloat() * 350f
                else -> 240f + rng.nextFloat() * 120f
            }
            val gap = minOf(baseGap, MAX_GROUP_SPAN_MS / count)
            groups.add(WaveGroup(type.id, count, gap.toInt(), delay.toInt()))
            delay += 1300f + rng.nextFloat() * 1400f
        }
        return Wave(title, groups)
    }

    /** Total health of [wave], before any toughness scale. */
    fun totalHp(wave: Wave): Float {
        var sum = 0f
        for (group in wave.groups) sum += unitHp(GameData.unit(group.unitId)) * group.count
        return sum
    }
}
