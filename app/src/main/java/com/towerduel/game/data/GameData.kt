package com.towerduel.game.data

import androidx.compose.ui.graphics.Color

object GameData {

    val BASE_INCOME_PER_SEC = 9f
    val STARTING_GOLD = 120
    val STARTING_LIVES = 100
    val MATCH_DURATION_SEC = 240
    val MAX_TOWERS_PER_LANE = 8
    val MIN_TOWER_SPACING = 8f
    val MIN_DRAFT_DPS = 10f

    // ---------------------------------------------------------------------
    // TOWERS — the drafted pool. 3 are randomly dealt to each side per match.
    // ---------------------------------------------------------------------
    val TROOPS: List<TroopType> = listOf(
        TroopType(
            id = "sentry", name = "Sentry", glyph = "S", color = Color(0xFF5AA9E6),
            cost = 50, upgradeCost = 60, damage = 14f, range = 22f, fireRateMs = 650,
            targeting = TargetPriority.FIRST,
            description = "Reliable all-rounder. Fires at whichever enemy is furthest along."
        ),
        TroopType(
            id = "sniper", name = "Sniper", glyph = "N", color = Color(0xFFB967D9),
            cost = 90, upgradeCost = 100, damage = 55f, range = 40f, fireRateMs = 1800,
            targeting = TargetPriority.STRONGEST,
            description = "Huge range and damage, slow reload. Picks off the toughest target."
        ),
        TroopType(
            id = "frost", name = "Frost Spire", glyph = "F", color = Color(0xFF8FE3E3),
            cost = 60, upgradeCost = 70, damage = 4f, range = 26f, fireRateMs = 1000,
            targeting = TargetPriority.CLOSEST, slowFactor = 0.35f,
            description = "Slows every enemy in range continuously. Weak damage on its own."
        ),
        TroopType(
            id = "bomb", name = "Bomb Tower", glyph = "B", color = Color(0xFFE8965D),
            cost = 80, upgradeCost = 90, damage = 20f, range = 22f, fireRateMs = 1500,
            targeting = TargetPriority.FIRST, splashRadius = 14f,
            description = "Splash damage in a radius. Great against grouped enemies."
        ),
        TroopType(
            id = "gatling", name = "Gatling", glyph = "G", color = Color(0xFFD9C24C),
            cost = 55, upgradeCost = 65, damage = 5f, range = 18f, fireRateMs = 180,
            targeting = TargetPriority.CLOSEST,
            description = "Very fast, short range, low hit damage. Shreds swarms."
        ),
        TroopType(
            id = "chain", name = "Chain Lightning", glyph = "L", color = Color(0xFFF2E85D),
            cost = 85, upgradeCost = 95, damage = 16f, range = 24f, fireRateMs = 900,
            targeting = TargetPriority.FIRST, chainTargets = 2,
            description = "Bolt jumps to nearby enemies after the first hit — hits up to 3 targets."
        ),
        TroopType(
            id = "poison", name = "Poison Totem", glyph = "P", color = Color(0xFF7BC96F),
            cost = 65, upgradeCost = 75, damage = 2f, range = 20f, fireRateMs = 1200,
            targeting = TargetPriority.CLOSEST,
            dotDamagePerSecond = 6f, dotDurationMs = 3000,
            description = "Poisons everything nearby. Weak up front, strong over time."
        ),
        TroopType(
            id = "goldmine", name = "Gold Mine", glyph = "\$", color = Color(0xFFF2C744),
            cost = 70, upgradeCost = 90, isAttacker = false, incomeBonusPerSecond = 4f,
            description = "No attack — pure economy. Boosts your passive income for the match."
        ),
        TroopType(
            id = "stun", name = "Stun Turret", glyph = "T", color = Color(0xFFE85D8A),
            cost = 75, upgradeCost = 85, damage = 8f, range = 22f, fireRateMs = 1300,
            targeting = TargetPriority.FIRST, stunChance = 0.3f, stunDurationMs = 800,
            description = "Chance to freeze a target in place on hit."
        ),
        TroopType(
            id = "antiair", name = "Anti-Air Net", glyph = "A", color = Color(0xFF6FE3A8),
            cost = 60, upgradeCost = 70, damage = 16f, range = 26f, fireRateMs = 800,
            targeting = TargetPriority.FIRST, bonusDamageVsFlyerPct = 100f,
            description = "Deals double damage to Flyers, ignoring their usual resistance."
        ),
        TroopType(
            id = "beacon", name = "Support Beacon", glyph = "+", color = Color(0xFF9FA8DA),
            cost = 70, upgradeCost = 80, isAttacker = false,
            auraDamageBonusPct = 20f, auraRange = 30f,
            description = "No attack — boosts the damage of every tower near it."
        ),
        TroopType(
            id = "mortar", name = "Mortar", glyph = "M", color = Color(0xFFE85D5D),
            cost = 120, upgradeCost = 130, damage = 45f, range = 30f, fireRateMs = 2600,
            targeting = TargetPriority.FIRST, splashRadius = 20f,
            description = "Massive splash damage, very slow reload. A late-game investment."
        )
    )

    // ---------------------------------------------------------------------
    // SENDABLE UNITS — shared offense roster, available to both sides.
    // ---------------------------------------------------------------------
    val ENEMY_SENDS: List<EnemySendType> = listOf(
        EnemySendType(
            id = "runner", name = "Runner", glyph = "r", color = Color(0xFFDDE3E8),
            cost = 20, maxHp = 18f, speed = 14f, livesDamage = 1, bountyGold = 4,
            description = "Cheap and fast. Easy to kill but easy to spam."
        ),
        EnemySendType(
            id = "grunt", name = "Grunt", glyph = "g", color = Color(0xFF8B97A6),
            cost = 35, maxHp = 40f, speed = 9f, livesDamage = 1, bountyGold = 7,
            description = "Balanced middle-of-the-road unit."
        ),
        EnemySendType(
            id = "tank", name = "Tank", glyph = "T", color = Color(0xFF5A6B7A),
            cost = 90, maxHp = 220f, speed = 5f, livesDamage = 2, bountyGold = 18,
            description = "Huge health pool, slow. Needs sustained fire to bring down."
        ),
        EnemySendType(
            id = "swarm", name = "Swarm Pack", glyph = "s", color = Color(0xFFD9C24C),
            cost = 60, maxHp = 12f, speed = 10f, livesDamage = 1, bountyGold = 3, count = 5,
            description = "Sends 5 weak units at once. Overwhelms single-target towers."
        ),
        EnemySendType(
            id = "flyer", name = "Flyer", glyph = "f", color = Color(0xFF8FE3E3),
            cost = 55, maxHp = 35f, speed = 11f, livesDamage = 1, bountyGold = 9,
            damageResistancePct = 50f,
            description = "Resists half of all incoming damage — unless it's Anti-Air."
        ),
        EnemySendType(
            id = "healer", name = "Healer", glyph = "h", color = Color(0xFF7BC96F),
            cost = 65, maxHp = 50f, speed = 8f, livesDamage = 1, bountyGold = 10,
            healPerSecond = 8f, healRadius = 14f,
            description = "Heals itself and nearby allies as it walks."
        ),
        EnemySendType(
            id = "boss", name = "Boss", glyph = "B", color = Color(0xFFE85D5D),
            cost = 220, maxHp = 900f, speed = 4f, livesDamage = 10, bountyGold = 60,
            description = "A serious investment. Devastating if it reaches the base."
        )
    )

    // ---------------------------------------------------------------------
    // MAPS — different path shapes change chokepoints & tower placement.
    // ---------------------------------------------------------------------
    val MAPS: List<MapDef> = listOf(
        MapDef(
            id = "s_curve", name = "S-Curve",
            pathPoints = listOf(0f to 23f, 25f to 8f, 50f to 38f, 75f to 8f, 100f to 23f)
        ),
        MapDef(
            id = "zigzag", name = "Zigzag",
            pathPoints = listOf(0f to 5f, 20f to 41f, 40f to 5f, 60f to 41f, 80f to 5f, 100f to 23f)
        ),
        MapDef(
            id = "sweep", name = "Diagonal Sweep",
            pathPoints = listOf(0f to 41f, 30f to 5f, 55f to 41f, 80f to 5f, 100f to 23f)
        )
    )

    // ---------------------------------------------------------------------
    // MODIFIERS — one is rolled at random per match.
    // ---------------------------------------------------------------------
    val MODIFIERS: List<MatchModifier> = listOf(
        MatchModifier(
            id = "rush_hour", name = "Rush Hour",
            description = "All enemies move 30% faster.", speedMultiplier = 1.3f
        ),
        MatchModifier(
            id = "gold_rush", name = "Gold Rush",
            description = "Passive income is boosted 50% for both sides.", incomeMultiplier = 1.5f
        ),
        MatchModifier(
            id = "glass_cannons", name = "Glass Cannons",
            description = "Towers deal 40% more damage, but max lives are cut to 60.",
            damageMultiplier = 1.4f, livesOverride = 60
        ),
        MatchModifier(
            id = "fortified", name = "Fortified",
            description = "All towers gain 20% extra range.", rangeMultiplier = 1.2f
        ),
        MatchModifier(
            id = "blitz", name = "Blitz",
            description = "Short 2-minute match, but everyone starts with extra gold.",
            matchDurationOverrideSec = 120, startingGoldBonus = 80
        ),
        MatchModifier(
            id = "iron_lives", name = "Iron Lives",
            description = "Lives raised to 150, but income is reduced 20%.",
            livesOverride = 150, incomeMultiplier = 0.8f
        )
    )

    /** Deals [count] towers, rerolling hands with no real damage dealer so every draft can hold a lane. */
    fun randomDraft(count: Int = 3): List<TroopType> {
        while (true) {
            val draft = TROOPS.shuffled().take(count)
            if (draft.any { it.isAttacker && it.damage * 1000f / it.fireRateMs >= MIN_DRAFT_DPS }) return draft
        }
    }
    fun randomMap(): MapDef = MAPS.random()
    fun randomModifier(): MatchModifier = MODIFIERS.random()
    fun randomPersonality(): AiPersonality = AiPersonality.entries.toTypedArray().random()
}
