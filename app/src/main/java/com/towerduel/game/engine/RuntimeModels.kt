package com.towerduel.game.engine

import com.towerduel.game.data.EnemySendType
import com.towerduel.game.data.TroopType

data class TowerInstance(
    val instanceId: Long,
    val type: TroopType,
    val x: Float,
    val y: Float,
    var upgraded: Boolean = false,
    var cooldownMs: Float = 0f
) {
    val effectiveDamage: Float get() = if (upgraded) type.damage * 1.6f else type.damage
    val effectiveRange: Float get() = if (upgraded) type.range * 1.15f else type.range
    val effectiveFireRateMs: Long get() = if (upgraded) (type.fireRateMs * 0.85f).toLong() else type.fireRateMs
    val effectiveIncome: Float get() = if (upgraded) type.incomeBonusPerSecond * 1.6f else type.incomeBonusPerSecond
    val effectiveAuraBonus: Float get() = if (upgraded) type.auraDamageBonusPct * 1.5f else type.auraDamageBonusPct
    val effectiveSlow: Float get() = if (upgraded) (type.slowFactor * 1.3f).coerceAtMost(0.8f) else type.slowFactor
    val effectiveDotDps: Float get() = if (upgraded) type.dotDamagePerSecond * 1.6f else type.dotDamagePerSecond
}

data class EnemyUnit(
    val instanceId: Long,
    val type: EnemySendType,
    var progress: Float = 0f, // 0f..1f fraction of path completed
    var hp: Float,
    var slowFactor: Float = 0f,
    var slowExpiresAtMs: Float = 0f,
    var stunExpiresAtMs: Float = 0f,
    var dotDps: Float = 0f,
    var dotExpiresAtMs: Float = 0f,
    // Lane position for the current progress, refreshed by the engine whenever the unit moves.
    var x: Float = 0f,
    var y: Float = 0f
)

/** The rest of a multi-unit send, released one at a time so the pack walks in a line. */
data class PendingSpawn(val type: EnemySendType, var delayMs: Float)

/** A short-lived visual-only tracer for a tower's shot; carries no gameplay effect. */
data class FxTracer(
    val fromX: Float, val fromY: Float,
    val toX: Float, val toY: Float,
    var ageMs: Float = 0f
)

/**
 * One side's battlefield: the towers defending it, and the enemies currently
 * marching down its path toward its base.
 */
class Battlefield(
    val ownerLabel: String,
    val draftedTroops: List<TroopType>,
    var gold: Float,
    var lives: Int
) {
    val towers = mutableListOf<TowerInstance>()
    val incomingEnemies = mutableListOf<EnemyUnit>()
    val pendingSpawns = mutableListOf<PendingSpawn>()
    val tracers = mutableListOf<FxTracer>()
    var nextInstanceId = 0L
}

enum class MatchOutcome { PLAYER_WIN, AI_WIN, DRAW, ONGOING }

enum class PlaceResult { OK, NOT_ENOUGH_GOLD, TOO_CLOSE, LANE_FULL, MATCH_OVER }
