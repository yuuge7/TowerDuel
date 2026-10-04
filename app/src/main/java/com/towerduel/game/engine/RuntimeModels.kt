package com.towerduel.game.engine

import com.towerduel.game.data.EnemySendType
import com.towerduel.game.data.ShotKind
import com.towerduel.game.data.TroopType
import com.towerduel.game.data.UpgradeTier

private const val MAX_SLOW = 0.8f
private const val MAX_STUN_CHANCE = 0.9f

class TowerInstance(
    val instanceId: Long,
    val type: TroopType,
    val x: Float,
    val y: Float,
    val placedAtMs: Float
) {
    /** Number of upgrade tiers bought, 0..type.maxLevel. */
    var level = 0
        private set
    var targeting = type.targeting
    var cooldownMs = 0f
    var invested = type.cost
        private set
    var kills = 0

    /** Fraction of extra damage from Beacons in range; the engine refreshes it when towers change. */
    var auraBonus = 0f

    // Render-only state: where the barrel points and when it last kicked.
    var aimAngle = -1.5708f
    var lastFiredAtMs = -100_000f
    var upgradedAtMs = -100_000f

    /** Gold Mines pay out in lumps; this is the gold banked towards the next one. */
    var payoutBank = 0f

    // Stats with every bought tier applied (match modifiers are applied by the engine on top).
    var damage = type.damage; private set
    var range = type.range; private set
    var reloadMs = type.fireRateMs.toFloat(); private set
    var splashRadius = type.splashRadius; private set
    var chains = type.chainTargets; private set
    var shots = 1; private set
    var slow = type.slowFactor; private set
    var stunChance = type.stunChance; private set
    var dotDps = type.dotDamagePerSecond; private set
    var income = type.incomeBonusPerSecond; private set
    var auraPct = type.auraDamageBonusPct; private set
    var auraRange = type.auraRange; private set

    val nextUpgrade: UpgradeTier? get() = type.upgrades.getOrNull(level)

    fun applyNextUpgrade(nowMs: Float): Boolean {
        val tier = nextUpgrade ?: return false
        level++
        invested += tier.cost
        upgradedAtMs = nowMs
        damage *= tier.damageMult
        reloadMs *= tier.reloadMult
        splashRadius *= tier.splashMult
        chains += tier.extraChains
        shots += tier.extraShots
        slow = (slow * tier.effectMult).coerceAtMost(MAX_SLOW)
        stunChance = (stunChance * tier.effectMult).coerceAtMost(MAX_STUN_CHANCE)
        dotDps *= tier.effectMult
        income *= tier.effectMult
        auraPct *= tier.effectMult
        // A support tower's "range" is its aura.
        if (type.auraRange > 0f) auraRange *= tier.rangeMult else range *= tier.rangeMult
        return true
    }
}

class EnemyUnit(
    val instanceId: Long,
    val type: EnemySendType,
    val maxHp: Float,
    /** Sideways offset from the middle of the track, so a pack does not walk as one stacked dot. */
    val laneOffset: Float,
    /** Sudden-death waves walk faster than the unit's listed speed. */
    val speedScale: Float = 1f
) {
    var hp = maxHp
    var alive = true

    /** Lane units walked so far, and the same as a 0..1 fraction of the path. */
    var dist = 0f
    var progress = 0f

    // Lane position and heading for the current dist, refreshed by the engine whenever the unit moves.
    var x = 0f
    var y = 0f
    var dirX = 1f
    var dirY = 0f

    var slowFactor = 0f
    var slowExpiresAtMs = 0f
    var stunExpiresAtMs = 0f
    var dotDps = 0f
    var dotExpiresAtMs = 0f
    var dotSource: TowerInstance? = null
    var lastHitAtMs = -100_000f
}

/** A wave or send unit waiting for its turn to step onto the lane. */
class PendingSpawn(
    val type: EnemySendType,
    var delayMs: Float,
    val hpScale: Float,
    val speedScale: Float = 1f
)

class Projectile(
    val kind: ShotKind,
    val source: TowerInstance,
    /** Null for a lobbed shell, which lands on a spot instead of chasing a unit. */
    val target: EnemyUnit?,
    val startX: Float,
    val startY: Float,
    var targetX: Float,
    var targetY: Float,
    val speed: Float,
    val damage: Float,
    /** Lobbed shells only: total time in the air. */
    val flightMs: Float = 0f
) {
    var x = startX
    var y = startY
    var ageMs = 0f
    var angle = 0f
    var done = false
}

enum class FxKind {
    POP, HIT, EXPLOSION, BOLT, TRACER, FROST_RING, POISON_CLOUD,
    GOLD_TEXT, LIFE_TEXT, DUST, SPARKLE, HEAL
}

/** A short-lived visual; carries no gameplay effect. Positions and [size] are in lane units. */
class FxEvent(
    val kind: FxKind,
    val x: Float,
    val y: Float,
    val durationMs: Float,
    val x2: Float = 0f,
    val y2: Float = 0f,
    val size: Float = 0f,
    val value: Int = 0,
    val unit: EnemySendType? = null,
    val tower: TroopType? = null,
    val seed: Int = 0
) {
    var ageMs = 0f
    val t: Float get() = (ageMs / durationMs).coerceIn(0f, 1f)
}

enum class SoundCue {
    SHOOT, SHOOT_HEAVY, ZAP, FREEZE, POP, POP_BIG, BOOM, COIN, LEAK, PLACE, UPGRADE, SELL, SEND,
    ROUND, WARNING, WIN, LOSE, CLICK, DENIED
}

/** A sound the engine wants played; [field] is the lane it happened on, or null for match-wide cues. */
class CueEvent(val cue: SoundCue, val field: Battlefield?)

class MatchStats {
    var kills = 0
    var leaks = 0
    var goldEarned = 0f
    var unitsSent = 0
    var towersBuilt = 0
}

/** A big send announced to the lane it is about to hit. */
class LaneWarning(val unit: EnemySendType, val atMs: Float)

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
    val towers = ArrayList<TowerInstance>()
    val incomingEnemies = ArrayList<EnemyUnit>()
    val pendingSpawns = ArrayList<PendingSpawn>()
    val projectiles = ArrayList<Projectile>()
    val fx = ArrayList<FxEvent>()
    val stats = MatchStats()
    var nextInstanceId = 0L

    /** Extra gold per second bought by sending units. */
    var ecoIncome = 0f

    /** Per unit id: when this side may send that unit again. */
    val sendReadyAtMs = HashMap<String, Float>()

    var lastLeakAtMs = -100_000f
    var warning: LaneWarning? = null
}

enum class MatchOutcome { PLAYER_WIN, AI_WIN, DRAW, ONGOING }

enum class PlaceResult { OK, NOT_ENOUGH_GOLD, TOO_CLOSE, ON_PATH, LANE_FULL, MATCH_OVER }

enum class SendResult { OK, NOT_ENOUGH_GOLD, LOCKED, COOLING_DOWN, MATCH_OVER }
