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
    DART, BULLET, SHELL, MORTAR, NET, ORB, // projectiles that fly to the target
    BOLT, RAIL,                            // instant hits
    FROST_PULSE, POISON_PULSE,             // hits everything in range at once
    NONE
}

/**
 * One step of a tower's upgrade track. Multipliers stack on top of every earlier tier.
 * [effectMult] scales whatever the tower's special is: slow, stun chance, poison, income or aura.
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
    val description: String
) {
    val maxLevel: Int get() = upgrades.size

    /** Single-target damage per second before upgrades. */
    val baseDps: Float get() = if (isAttacker && fireRateMs > 0L) damage * 1000f / fireRateMs else 0f
}

/** A unit that walks a lane. Both sides can send the [sendable] ones; waves use all of them. */
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
    /** Sending this raises the sender's income by this much gold per second for the rest of the match. */
    val incomeBonus: Float = 0f,
    val unlockRound: Int = 1,
    val cooldownMs: Long = 500L,
    val spawnOnDeathId: String? = null,
    val spawnOnDeathCount: Int = 0,
    val sendable: Boolean = true,
    val description: String
)

enum class MapTheme { MEADOW, DUNES, FROST, EMBER }

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
    val livesOverride: Int? = null,
    val matchDurationOverrideSec: Int? = null,
    val startingGoldBonus: Int = 0
)

/** One batch of a natural wave: [count] units of [unitId], [gapMs] apart, starting [delayMs] into the round. */
data class WaveGroup(val unitId: String, val count: Int, val gapMs: Int, val delayMs: Int = 0)

enum class Difficulty(val label: String, val blurb: String) {
    EASY("Easy", "Slow to react and careless with its towers."),
    MEDIUM("Medium", "Plays a solid, honest game."),
    HARD("Hard", "Reads your defense and hits where it is weak.")
}

enum class AiPersonality(val label: String, val blurb: String) {
    RUSHER("Rusher", "Sends units from the first second and builds only what it must."),
    TURTLE("Turtle", "Walls up first, then drops one huge push."),
    BALANCED("Balanced", "Adapts its spending to how you play."),
    TYCOON("Tycoon", "Grows its income early and buries you late.")
}
