package com.towerduel.game.data

import androidx.compose.ui.graphics.Color

/** Virtual coordinate space every lane is simulated & drawn in, independent of screen pixels. */
object LaneSpace {
    const val WIDTH = 100f
    const val HEIGHT = 62f
}

enum class TargetPriority(val label: String) {
    FIRST("First"), LAST("Last"), STRONGEST("Strong"), CLOSEST("Close")
}

/** How a tower's attack travels. The engine reads it for timing and impact, the renderer for the sprite. */
enum class ShotKind {
    DART, BULLET, SHELL, MORTAR, NET, ORB, // projectiles that chase the target
    GLAIVE,                                // a projectile that flies straight and cuts through a line of units
    BOLT, RAIL, BEAM, FLAME,               // instant hits
    FROST_PULSE, POISON_PULSE, GUST_PULSE, // hits everything in range at once
    NONE;

    /** Pulse towers have no single target, so they have no targeting priority either. */
    val isPulse: Boolean get() = this == FROST_PULSE || this == POISON_PULSE || this == GUST_PULSE
}

/**
 * One step of a tower's upgrade track. Multipliers stack on top of every earlier tier.
 * [effectMult] scales whatever the tower's special is: slow, stun or crit chance, poison,
 * income, aura, knockback, weakening, execute threshold, bounty bonus, or how far a beam ramps.
 * [extraChains] adds chain jumps, or cuts for a tower that pierces.
 */
data class UpgradeTier(
    val name: String,
    val blurb: String,
    val cost: Int,
    val damageMult: Float = 1f,
    val rangeMult: Float = 1f,
    val reloadMult: Float = 1f,
    val splashMult: Float = 1f,
    val effectMult: Float = 1f,
    val extraChains: Int = 0,
    val extraShots: Int = 0
)

/**
 * A defensive tower type. Each side is dealt a hand from GameData.TROOPS and keeps 3 for the match.
 */
data class TroopType(
    val id: String,
    val name: String,
    val color: Color,
    val cost: Int,
    val role: String,
    val upgrades: List<UpgradeTier> = emptyList(),
    val isAttacker: Boolean = true,
    val shot: ShotKind = ShotKind.DART,
    val damage: Float = 0f,
    val range: Float = 0f,
    val fireRateMs: Long = 1000L,
    val targeting: TargetPriority = TargetPriority.FIRST,
    val splashRadius: Float = 0f,
    val chainTargets: Int = 0,
    val slowFactor: Float = 0f,
    val slowDurationMs: Long = 0L,
    val stunChance: Float = 0f,
    val stunDurationMs: Long = 0L,
    val dotDamagePerSecond: Float = 0f,
    val dotDurationMs: Long = 0L,
    val incomeBonusPerSecond: Float = 0f,
    val auraDamageBonusPct: Float = 0f,
    val auraRange: Float = 0f,
    val bonusDamageVsFlyerPct: Float = 0f,
    /** How many units past the first a GLAIVE cuts through. */
    val pierce: Int = 0,
    val critChance: Float = 0f,
    val critMultiplier: Float = 1f,
    /** A BEAM grows this much stronger with every hit on the same target, up to [rampMax] extra. */
    val rampPerHit: Float = 0f,
    val rampMax: Float = 0f,
    /** A hit unit takes this much more damage from everything for [vulnerabilityMs]. */
    val vulnerabilityPct: Float = 0f,
    val vulnerabilityMs: Long = 0L,
    /** Lane units a hit unit is thrown back along the track (less for heavy units). */
    val knockback: Float = 0f,
    /** A hit that leaves a unit below this share of its health finishes it off. */
    val executeBelowPct: Float = 0f,
    /** Extra bounty on this tower's kills. */
    val bountyBonusPct: Float = 0f,
    val description: String
) {
    val maxLevel: Int get() = upgrades.size

    /** Single-target damage per second before upgrades. */
    val baseDps: Float get() = if (isAttacker && fireRateMs > 0L) damage * 1000f / fireRateMs else 0f
}

/** A unit that walks a lane. A match's roster decides which ones both sides can send and its waves use. */
data class EnemySendType(
    val id: String,
    val name: String,
    val color: Color,
    val cost: Int,
    val maxHp: Float,
    val speed: Float, // lane units per second along the path
    val livesDamage: Int,
    val bountyGold: Int,
    val radius: Float = 2f, // body size in lane units
    val count: Int = 1,
    val flying: Boolean = false,
    val damageResistancePct: Float = 0f,
    val healPerSecond: Float = 0f,
    val healRadius: Float = 0f,
    /** Taken off every hit, so many small hits do little and a few big ones do nearly everything. */
    val armor: Float = 0f,
    /** Health regained per second while nothing has hit it for a moment. */
    val regenPerSecond: Float = 0f,
    /** Cannot be targeted or hurt for [phaseMs] out of every [phaseEveryMs]. */
    val phaseMs: Long = 0L,
    val phaseEveryMs: Long = 0L,
    /** Units walking within [hasteRadius] of it move this much faster. */
    val hasteAuraPct: Float = 0f,
    val hasteRadius: Float = 0f,
    /** Ignores slows, stuns and knockback. */
    val controlImmune: Boolean = false,
    /** Sending this raises the sender's income by this much gold per second for the rest of the match. */
    val incomeBonus: Float = 0f,
    val unlockRound: Int = 1,
    val cooldownMs: Long = 500L,
    val spawnOnDeathId: String? = null,
    val spawnOnDeathCount: Int = 0,
    val sendable: Boolean = true,
    val description: String
)

enum class MapTheme { MEADOW, DUNES, FROST, EMBER, SWAMP, AUTUMN }

/** [pathPoints] are control points; the lane path is a smooth curve through them (see LanePath). */
data class MapDef(
    val id: String,
    val name: String,
    val theme: MapTheme,
    val pathPoints: List<Pair<Float, Float>>
)

data class MatchModifier(
    val id: String,
    val name: String,
    val description: String,
    val speedMultiplier: Float = 1f,
    val incomeMultiplier: Float = 1f,
    val damageMultiplier: Float = 1f,
    val rangeMultiplier: Float = 1f,
    val reloadMultiplier: Float = 1f,
    val bountyMultiplier: Float = 1f,
    val unitHpMultiplier: Float = 1f,
    val sendIncomeMultiplier: Float = 1f,
    val livesOverride: Int? = null,
    val matchDurationOverrideSec: Int? = null,
    val roundIntervalSec: Int? = null,
    val startingGoldBonus: Int = 0,
    /** The rival is offered the same towers as the player. */
    val mirrorDraft: Boolean = false
) {
    /** True if [other] can be in force at the same time without the two fighting over one setting. */
    fun compatibleWith(other: MatchModifier): Boolean =
        id != other.id &&
            (livesOverride == null || other.livesOverride == null) &&
            (matchDurationOverrideSec == null || other.matchDurationOverrideSec == null) &&
            (roundIntervalSec == null || other.roundIntervalSec == null)

    /** Both rules at once. Only meaningful for [compatibleWith] pairs. */
    operator fun plus(other: MatchModifier): MatchModifier = MatchModifier(
        id = "$id+${other.id}",
        name = "$name + ${other.name}",
        description = "$description ${other.description}",
        speedMultiplier = speedMultiplier * other.speedMultiplier,
        incomeMultiplier = incomeMultiplier * other.incomeMultiplier,
        damageMultiplier = damageMultiplier * other.damageMultiplier,
        rangeMultiplier = rangeMultiplier * other.rangeMultiplier,
        reloadMultiplier = reloadMultiplier * other.reloadMultiplier,
        bountyMultiplier = bountyMultiplier * other.bountyMultiplier,
        unitHpMultiplier = unitHpMultiplier * other.unitHpMultiplier,
        sendIncomeMultiplier = sendIncomeMultiplier * other.sendIncomeMultiplier,
        livesOverride = livesOverride ?: other.livesOverride,
        matchDurationOverrideSec = matchDurationOverrideSec ?: other.matchDurationOverrideSec,
        roundIntervalSec = roundIntervalSec ?: other.roundIntervalSec,
        startingGoldBonus = startingGoldBonus + other.startingGoldBonus,
        mirrorDraft = mirrorDraft || other.mirrorDraft
    )
}

/** One batch of a wave: [count] units of [unitId], [gapMs] apart, starting [delayMs] into the round. */
data class WaveGroup(val unitId: String, val count: Int, val gapMs: Int, val delayMs: Int = 0)

/** A round's wave. [title] names a themed one ("AIR RAID"); an ordinary mixed wave has none. */
data class Wave(val title: String?, val groups: List<WaveGroup>)

/**
 * Something that happens to both lanes in the middle of a match. A timed one changes the rules
 * for [durationSec]; one with no duration happens once.
 */
enum class MatchEventType(val label: String, val blurb: String, val durationSec: Int) {
    GOLD_RAIN("Gold Rain", "Free gold for both sides", 0),
    AMBUSH("Ambush", "An extra wave hits both lanes", 0),
    PAYDAY("Payday", "Income is doubled", 14),
    STAMPEDE("Stampede", "Units move 35% faster", 12),
    POWER_SURGE("Power Surge", "Towers hit 40% harder", 12),
    OVERDRIVE("Overdrive", "Towers fire 30% faster", 12),
    FOG("Fog", "Towers reach 20% less far", 12),
    COLD_SNAP("Cold Snap", "Units move at half speed", 7)
}

enum class Difficulty(val label: String, val blurb: String) {
    EASY("Easy", "Slow to react and careless with its towers."),
    MEDIUM("Medium", "Plays a solid, honest game."),
    HARD("Hard", "Reads your defense and hits where it is weak.")
}

enum class AiPersonality(val label: String, val blurb: String) {
    RUSHER("Rusher", "Sends units from the first second and builds only what it must."),
    TURTLE("Turtle", "Walls up first, then drops one huge push."),
    BALANCED("Balanced", "Adapts its spending to how you play."),
    TYCOON("Tycoon", "Grows its income early and buries you late."),
    SWARMER("Swarmer", "Floods your lane with small units, again and again."),
    BRUISER("Bruiser", "Saves for the heaviest units it can send."),
    GAMBLER("Gambler", "Pokes with scraps, then bets everything at once."),
    TRICKSTER("Trickster", "Mixes units that cover for each other and never repeats itself.")
}

/** What a rival says, and when. Each list is picked from at random. */
data class RivalLines(
    val start: List<String>,
    /** When it launches a big push. */
    val push: List<String>,
    /** When it loses a chunk of lives. */
    val hurt: List<String>,
    /** When the player loses a chunk of lives. */
    val gloat: List<String>,
    val win: String,
    val lose: String
)

/** A named opponent: a face, a play style and a voice. [unitId] is the unit drawn as its portrait. */
data class Rival(
    val id: String,
    val name: String,
    val personality: AiPersonality,
    val unitId: String,
    val lines: RivalLines
)
