package com.towerduel.game.engine

import com.towerduel.game.data.EnemySendType
import com.towerduel.game.data.MatchEventType
import com.towerduel.game.data.ShotKind
import com.towerduel.game.data.TroopType
import com.towerduel.game.data.UpgradeTier

private const val MAX_SLOW = 0.8f
private const val MAX_STUN_CHANCE = 0.9f
private const val MAX_CRIT_CHANCE = 0.9f
private const val MAX_EXECUTE = 0.4f
private const val MAX_DEATH_BLAST = 0.9f

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

    /** The seat that built it: who may upgrade or sell it, and whose purse its income goes to. */
    var owner: Battlefield? = null

    /** Fraction of extra damage from Beacons in range; the engine refreshes it when towers change. */
    var auraBonus = 0f

    /** Fraction of extra fire rate from Overclockers in range; refreshed the same way. */
    var reloadBonus = 0f

    /** Fraction of extra reach from Lookouts in range; refreshed the same way. */
    var rangeBonus = 0f

    /** A Jammer popped nearby: this tower does not fire until then. */
    var jammedUntilMs = -100_000f

    // Render-only state: where the barrel points and when it last kicked.
    var aimAngle = -1.5708f
    var lastFiredAtMs = -100_000f
    var upgradedAtMs = -100_000f

    /** Gold Mines pay out in lumps; this is the gold banked towards the next one. */
    var payoutBank = 0f

    /** A Shrine's progress towards the next life it gives back, 0..1. */
    var lifeBank = 0f

    // Stats with every bought tier applied (match modifiers are applied by the engine on top).
    var damage = type.damage; private set
    var range = type.range; private set
    var reloadMs = type.fireRateMs.toFloat(); private set
    var splashRadius = type.splashRadius; private set
    var chains = type.chainTargets; private set
    var shots = type.shots; private set
    var slow = type.slowFactor; private set
    var stunChance = type.stunChance; private set
    var dotDps = type.dotDamagePerSecond; private set
    var income = type.incomeBonusPerSecond; private set
    var auraPct = type.auraDamageBonusPct; private set
    var auraReloadPct = type.auraReloadBonusPct; private set
    var auraRangePct = type.auraRangeBonusPct; private set
    var auraRange = type.auraRange; private set
    var livesPerMinute = type.livesPerMinute; private set
    var pierce = type.pierce; private set
    var critChance = type.critChance; private set
    var rampMax = type.rampMax; private set
    var vulnerability = type.vulnerabilityPct / 100f; private set
    var knockback = type.knockback; private set
    var executeBelow = type.executeBelowPct / 100f; private set
    var bountyBonus = type.bountyBonusPct / 100f; private set
    var killGrowth = type.killGrowthPct / 100f; private set
    var killGrowthMax = type.killGrowthMaxPct / 100f; private set
    var deathBlast = type.deathBlastPct / 100f; private set
    var blastRadius = type.deathBlastRadius; private set
    var controlBonus = type.bonusVsControlledPct / 100f; private set
    var bigBonus = type.bonusVsBigPct / 100f; private set
    var dotSpread = type.dotSpreadRadius; private set

    /** Fraction of extra damage a Veteran has earned from its kills so far. */
    val killBonus: Float get() = minOf(kills * killGrowth, killGrowthMax)

    // A beam ramps up while it stays on one unit and starts over on the next.
    var rampTarget: EnemyUnit? = null
    var rampBonus = 0f

    val nextUpgrade: UpgradeTier? get() = type.upgrades.getOrNull(level)

    /** [free] is a tier nobody paid for (a rule, an event): it adds nothing to what selling gives back. */
    fun applyNextUpgrade(nowMs: Float, free: Boolean = false): Boolean {
        val tier = nextUpgrade ?: return false
        level++
        if (!free) invested += tier.cost
        upgradedAtMs = nowMs
        damage *= tier.damageMult
        reloadMs *= tier.reloadMult
        splashRadius *= tier.splashMult
        if (type.pierce > 0) pierce += tier.extraChains else chains += tier.extraChains
        shots += tier.extraShots
        critChance = (critChance * tier.effectMult).coerceAtMost(MAX_CRIT_CHANCE)
        rampMax *= tier.effectMult
        vulnerability *= tier.effectMult
        knockback *= tier.effectMult
        executeBelow = (executeBelow * tier.effectMult).coerceAtMost(MAX_EXECUTE)
        bountyBonus *= tier.effectMult
        killGrowth *= tier.effectMult
        killGrowthMax *= tier.effectMult
        deathBlast = (deathBlast * tier.effectMult).coerceAtMost(MAX_DEATH_BLAST)
        blastRadius *= tier.splashMult
        controlBonus *= tier.effectMult
        bigBonus *= tier.effectMult
        dotSpread *= tier.splashMult
        slow = (slow * tier.effectMult).coerceAtMost(MAX_SLOW)
        stunChance = (stunChance * tier.effectMult).coerceAtMost(MAX_STUN_CHANCE)
        dotDps *= tier.effectMult
        income *= tier.effectMult
        auraPct *= tier.effectMult
        auraReloadPct *= tier.effectMult
        auraRangePct *= tier.effectMult
        livesPerMinute *= tier.effectMult
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
    val speedScale: Float = 1f,
    val bornAtMs: Float = 0f
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

    /** Extra damage taken (as a fraction) while cursed, and until when. */
    var vulnerability = 0f
    var vulnerableUntilMs = 0f

    /** Speed bonus (as a fraction) from a Drummer nearby, and until when. */
    var haste = 0f
    var hasteUntilMs = 0f

    /** Damage shrugged off (as a fraction) thanks to a Warder nearby, and until when. */
    var ward = 0f
    var wardUntilMs = 0f

    /** Damage of the shots already in the air for this unit; enough of it and nobody else needs to aim here. */
    var pendingDamage = 0f

    /** Hits a Bubbler's bubble can still swallow. */
    var shieldLeft = type.shieldHits

    /** Cracked open by a Ballista: its armour and damage resistance no longer count. */
    var sundered = false

    /** When a Queen lays her next unit. */
    var nextSpawnAtMs = bornAtMs + type.spawnEveryMs

    /** When an Imp blinks next. Spread out by id, so a pack does not jump as one. */
    var nextBlinkAtMs = bornAtMs + type.blinkEveryMs * (0.4f + (instanceId % 5L) * 0.15f)
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

    // Glaives only: how many more units it can cut, how far it may still fly, and who it already hit.
    var cutsLeft = 0
    var travelLeft = 0f
    val struck = ArrayList<EnemyUnit>(0)
}

enum class FxKind {
    POP, HIT, EXPLOSION, BOLT, TRACER, BEAM, FLAME, FROST_RING, POISON_CLOUD, GUST_RING, QUAKE_RING, NOVA_RING,
    GOLD_TEXT, GOLD_LOSS, LIFE_TEXT, LIFE_GAIN, CRIT, EXECUTE, DUST, SPARKLE, HEAL, HASTE, WARD, CLEANSE,
    JAM_RING, BUBBLE_HIT
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
    ROUND, WARNING, EVENT, WIN, LOSE, CLICK, DENIED
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

/** A random event: when it struck, and when its effect wears off (the same moment for a one-off). */
class ActiveEvent(val type: MatchEventType, val startedAtMs: Float, val endsAtMs: Float)

/** Everything that stands on or walks one lane, and the keep at the end of it. */
class Lane(var lives: Int) {
    val towers = ArrayList<TowerInstance>()
    val incomingEnemies = ArrayList<EnemyUnit>()
    val pendingSpawns = ArrayList<PendingSpawn>()
    val projectiles = ArrayList<Projectile>()
    val fx = ArrayList<FxEvent>()
    var nextInstanceId = 0L
    var lastLeakAtMs = -100_000f
    var warning: LaneWarning? = null

    /** Times this keep can still stand back up after falling (the Second Chance rule). */
    var revivesLeft = 0
}

/**
 * One seat at the match: a purse, a hand of towers, and the lane it defends. In a duel every seat
 * has a lane to itself. In a 2 v 2 the two seats of a team share one [lane]: its towers, its lives
 * and the units walking it are the same objects for both, while gold, income and stats stay apart.
 */
class Battlefield(
    val ownerLabel: String,
    val draftedTroops: List<TroopType>,
    var gold: Float,
    val lane: Lane
) {
    constructor(ownerLabel: String, draftedTroops: List<TroopType>, gold: Float, lives: Int) :
        this(ownerLabel, draftedTroops, gold, Lane(lives))

    val towers: ArrayList<TowerInstance> get() = lane.towers
    val incomingEnemies: ArrayList<EnemyUnit> get() = lane.incomingEnemies
    val pendingSpawns: ArrayList<PendingSpawn> get() = lane.pendingSpawns
    val projectiles: ArrayList<Projectile> get() = lane.projectiles
    val fx: ArrayList<FxEvent> get() = lane.fx
    var lives: Int
        get() = lane.lives
        set(value) { lane.lives = value }
    var nextInstanceId: Long
        get() = lane.nextInstanceId
        set(value) { lane.nextInstanceId = value }
    var lastLeakAtMs: Float
        get() = lane.lastLeakAtMs
        set(value) { lane.lastLeakAtMs = value }
    var warning: LaneWarning?
        get() = lane.warning
        set(value) { lane.warning = value }

    val stats = MatchStats()

    /** The other seat on this lane in a 2 v 2; null in a duel. */
    var partner: Battlefield? = null

    /** Extra gold per second bought by sending units. */
    var ecoIncome = 0f

    /** Per unit id: when this side may send that unit again. */
    val sendReadyAtMs = HashMap<String, Float>()
}

enum class MatchOutcome { PLAYER_WIN, AI_WIN, DRAW, ONGOING }

enum class PlaceResult { OK, NOT_ENOUGH_GOLD, TOO_CLOSE, ON_PATH, MATCH_OVER }

enum class SendResult { OK, NOT_ENOUGH_GOLD, LOCKED, COOLING_DOWN, MATCH_OVER }
