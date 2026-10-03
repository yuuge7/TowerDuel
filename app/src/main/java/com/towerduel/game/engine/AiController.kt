package com.towerduel.game.engine

import com.towerduel.game.data.AiPersonality
import com.towerduel.game.data.Difficulty
import com.towerduel.game.data.GameData
import com.towerduel.game.data.LaneSpace
import com.towerduel.game.data.TroopType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val PLACEMENT_ATTEMPTS = 6

/**
 * Drives the AI's spending decisions each match. Runs on its own decision
 * cadence (not every frame) so it feels like discrete choices rather than
 * a twitch-perfect reflex.
 */
class AiController(
    private val personality: AiPersonality,
    private val difficulty: Difficulty
) {
    private var decisionTimerMs = 600f

    fun update(dtSeconds: Float, engine: GameEngine) {
        decisionTimerMs -= dtSeconds * 1000f
        if (decisionTimerMs > 0f) return
        decisionTimerMs = nextDecisionIntervalMs()
        decide(engine)
    }

    private fun nextDecisionIntervalMs(): Float {
        val base = when (difficulty) {
            Difficulty.EASY -> 2600f
            Difficulty.MEDIUM -> 1700f
            Difficulty.HARD -> 1000f
        }
        val personalityAdj = when (personality) {
            AiPersonality.RUSHER -> -200f
            AiPersonality.TURTLE -> 300f
            AiPersonality.BALANCED -> 0f
        }
        return (base + personalityAdj + Random.nextFloat() * 600f).coerceAtLeast(350f)
    }

    private fun decide(engine: GameEngine) {
        val ai = engine.aiField
        val player = engine.playerField

        val mistakeChance = when (difficulty) {
            Difficulty.EASY -> 0.35f
            Difficulty.MEDIUM -> 0.15f
            Difficulty.HARD -> 0.05f
        }
        if (Random.nextFloat() < mistakeChance) return // AI hesitates / wastes the beat

        val baseBuildWeight = when (personality) {
            AiPersonality.TURTLE -> 0.65f
            AiPersonality.RUSHER -> 0.25f
            AiPersonality.BALANCED -> 0.45f
        }
        val threatFactor = (ai.incomingEnemies.size.toFloat() / 6f).coerceIn(0f, 1f)
        val buildWeight = (baseBuildWeight + threatFactor * 0.3f).coerceIn(0f, 0.9f)

        if (Random.nextFloat() < buildWeight) {
            // A finished defense has nothing left to buy, so that gold goes to offense instead.
            if (!buildOrUpgrade(engine) && defenseMaxedOut(ai)) sendOffense(engine, ai, player)
        } else {
            sendOffense(engine, ai, player)
        }
    }

    private fun defenseMaxedOut(ai: Battlefield): Boolean =
        ai.towers.size >= GameData.MAX_TOWERS_PER_LANE && ai.towers.all { it.upgraded }

    private fun buildOrUpgrade(engine: GameEngine): Boolean {
        val ai = engine.aiField
        val canPlaceMore = ai.towers.size < GameData.MAX_TOWERS_PER_LANE
        val affordableNew = ai.draftedTroops.filter { it.cost <= ai.gold }
        val upgradable = ai.towers.filter { !it.upgraded && it.type.upgradeCost <= ai.gold }

        val preferUpgrade = upgradable.isNotEmpty() && (ai.towers.size >= 2 || affordableNew.isEmpty())

        if (preferUpgrade) {
            val target = if (difficulty == Difficulty.HARD) {
                upgradable.minByOrNull { it.type.upgradeCost }
            } else upgradable.random()
            return target != null && engine.upgradeTower(ai, target.instanceId)
        }
        if (canPlaceMore && affordableNew.isNotEmpty()) {
            val type = if (difficulty == Difficulty.HARD) {
                affordableNew.maxByOrNull { it.damage + it.range + it.incomeBonusPerSecond * 3f }
            } else affordableNew.random()
            return type != null && placeSomewhereUseful(engine, type)
        }
        return false
    }

    private fun placeSomewhereUseful(engine: GameEngine, type: TroopType): Boolean {
        repeat(PLACEMENT_ATTEMPTS) {
            val (x, y) = pickSpot(engine, type)
            when (engine.placeTower(engine.aiField, type, x, y)) {
                PlaceResult.OK -> return true
                PlaceResult.TOO_CLOSE -> Unit // spot taken, roll another
                else -> return false
            }
        }
        return false
    }

    private fun pickSpot(engine: GameEngine, type: TroopType): Pair<Float, Float> {
        // Gold Mines work anywhere, and an Easy AI sometimes just drops a tower wherever.
        val sloppy = difficulty == Difficulty.EASY && Random.nextFloat() < 0.4f
        if (sloppy || type.incomeBonusPerSecond > 0f) {
            return (Random.nextFloat() * (LaneSpace.WIDTH - 16f) + 8f) to
                (Random.nextFloat() * (LaneSpace.HEIGHT - 16f) + 8f)
        }

        // Support auras go next to a tower that can actually use the buff.
        if (type.auraRange > 0f) {
            val anchor = engine.aiField.towers.filter { it.type.isAttacker }.randomOrNull()
            if (anchor != null) return offsetFrom(anchor.x, anchor.y, 8f, type.auraRange * 0.5f)
        }

        // Everything else hugs the path so its range actually covers the lane.
        val (px, py) = PathMath.pointAtProgress(engine.map.pathPoints, 0.15f + Random.nextFloat() * 0.7f)
        return offsetFrom(px, py, 4f, (type.range * 0.5f).coerceIn(5f, 12f))
    }

    private fun offsetFrom(x: Float, y: Float, minDist: Float, maxDist: Float): Pair<Float, Float> {
        val angle = Random.nextFloat() * 2f * PI.toFloat()
        val d = minDist + Random.nextFloat() * (maxDist - minDist).coerceAtLeast(0f)
        return (x + cos(angle) * d) to (y + sin(angle) * d)
    }

    private fun sendOffense(engine: GameEngine, ai: Battlefield, player: Battlefield) {
        val affordable = GameData.ENEMY_SENDS.filter { it.cost <= ai.gold }
        if (affordable.isEmpty()) return
        val choice = when (personality) {
            // Mostly cheap spam, with the odd heavier unit so it isn't only Runners.
            AiPersonality.RUSHER ->
                if (Random.nextFloat() < 0.6f) affordable.minByOrNull { it.cost } else affordable.random()
            AiPersonality.TURTLE -> if (ai.gold > 150f) affordable.maxByOrNull { it.cost } else null
            AiPersonality.BALANCED -> affordable.random()
        } ?: return
        engine.sendEnemy(ai, player, choice)
    }
}
