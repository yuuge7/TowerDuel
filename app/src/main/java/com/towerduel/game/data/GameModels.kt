package com.towerduel.game.data

import androidx.compose.ui.graphics.Color

/** Virtual coordinate space every lane is simulated & drawn in, independent of screen pixels. */
object LaneSpace {
    const val WIDTH = 100f
    const val HEIGHT = 46f
}

enum class TargetPriority { FIRST, STRONGEST, CLOSEST }

/**
 * A defensive tower type. Players (and the AI) draft 3 of these at random per match
 * from the shared pool in GameData.TROOPS.
 */
data class TroopType(
    val id: String,
    val name: String,
    val glyph: String, // short text glyph used to draw the tower (no external art)
    val color: Color,
    val cost: Int,
    val upgradeCost: Int,
    val isAttacker: Boolean = true,
    val damage: Float = 0f,
    val range: Float = 0f,
    val fireRateMs: Long = 1000L,
    val targeting: TargetPriority = TargetPriority.FIRST,
    val splashRadius: Float = 0f,
    val chainTargets: Int = 0,
    val slowFactor: Float = 0f,
    val stunChance: Float = 0f,
    val stunDurationMs: Long = 0L,
    val dotDamagePerSecond: Float = 0f,
    val dotDurationMs: Long = 0L,
    val incomeBonusPerSecond: Float = 0f,
    val auraDamageBonusPct: Float = 0f,
    val auraRange: Float = 0f,
    val bonusDamageVsFlyerPct: Float = 0f,
    val description: String
)

/** A sendable offense unit. Shared roster — both player and AI can send any of these. */
data class EnemySendType(
    val id: String,
    val name: String,
    val glyph: String,
    val color: Color,
    val cost: Int,
    val maxHp: Float,
    val speed: Float, // virtual lane-units per second of arc-length travel
    val livesDamage: Int,
    val bountyGold: Int,
    val count: Int = 1,
    val damageResistancePct: Float = 0f,
    val healPerSecond: Float = 0f,
    val healRadius: Float = 0f,
    val description: String
)

data class MapDef(
    val id: String,
    val name: String,
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

enum class Difficulty(val label: String) { EASY("Easy"), MEDIUM("Medium"), HARD("Hard") }

enum class AiPersonality(val label: String, val blurb: String) {
    RUSHER("Rusher", "Sends waves constantly, builds defense as an afterthought."),
    TURTLE("Turtle", "Builds up a strong defense first, sends offense only when flush with gold."),
    BALANCED("Balanced", "Reacts to how you're playing and adapts spending accordingly.")
}
