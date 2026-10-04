package com.towerduel.game.data

import androidx.compose.ui.graphics.Color

object GameData {

    const val BASE_INCOME_PER_SEC = 9f
    const val STARTING_GOLD = 130
    const val STARTING_LIVES = 100
    const val MATCH_DURATION_SEC = 240
    const val MAX_TOWERS_PER_LANE = 10
    const val MIN_TOWER_SPACING = 7.5f
    const val MIN_DRAFT_DPS = 10f

    /** How many towers each side is offered, and how many of those it takes into the match. */
    const val DRAFT_OFFER = 5
    const val DRAFT_PICKS = 3

    /** Half the width of the walkable track, and how far a tower's centre must stay from its middle. */
    const val PATH_HALF_WIDTH = 4f
    const val PATH_CLEARANCE = 6.6f

    // Natural waves hit both lanes at once, one every round.
    const val ROUND_INTERVAL_SEC = 20
    const val FIRST_ROUND_DELAY_SEC = 10
    const val OVERTIME_ROUND_INTERVAL_SEC = 10
    const val OVERTIME_LIMIT_SEC = 90

    /** Wave units get tougher every round; past the scripted rounds the growth is steep on purpose. */
    const val WAVE_HP_GROWTH_PER_ROUND = 0.11f
    const val OVERTIME_HP_GROWTH = 1.45f

    /** Sudden-death waves also walk faster each round, so even a Boss arrives before the match is called. */
    const val OVERTIME_SPEED_GROWTH = 0.15f
    const val OVERTIME_MAX_SPEED = 2.5f

    // ---------------------------------------------------------------------
    // TOWERS — the draft pool. Each side is offered DRAFT_OFFER and keeps DRAFT_PICKS.
    // ---------------------------------------------------------------------
    val TROOPS: List<TroopType> = listOf(
        TroopType(
            id = "sentry", name = "Sentry", color = Color(0xFF3FA7F5),
            cost = 50, role = "All-rounder", shot = ShotKind.DART,
            damage = 14f, range = 21f, fireRateMs = 650,
            targeting = TargetPriority.FIRST,
            upgrades = listOf(
                UpgradeTier("Sharp Darts", "+50% damage, longer reach", 60, damageMult = 1.5f, rangeMult = 1.1f),
                UpgradeTier("Twin Barrels", "Fires at two targets at once", 115, extraShots = 1, reloadMult = 0.85f)
            ),
            description = "Cheap, reliable darts at whoever is furthest along."
        ),
        TroopType(
            id = "sniper", name = "Sniper", color = Color(0xFFA55EEA),
            cost = 90, role = "Long range", shot = ShotKind.RAIL,
            damage = 55f, range = 44f, fireRateMs = 1800,
            targeting = TargetPriority.STRONGEST,
            upgrades = listOf(
                UpgradeTier("Marksman Scope", "+60% damage", 95, damageMult = 1.6f),
                UpgradeTier("Railgun", "Huge damage, quicker reload", 170, damageMult = 1.8f, reloadMult = 0.8f)
            ),
            description = "Covers most of the lane and picks off the toughest target."
        ),
        TroopType(
            id = "frost", name = "Frost Spire", color = Color(0xFF7FE3F2),
            cost = 60, role = "Slows", shot = ShotKind.FROST_PULSE,
            damage = 4f, range = 17f, fireRateMs = 1000,
            targeting = TargetPriority.CLOSEST, slowFactor = 0.35f, slowDurationMs = 1300,
            upgrades = listOf(
                UpgradeTier("Deep Chill", "Stronger slow, wider pulse", 70, effectMult = 1.3f, rangeMult = 1.15f),
                UpgradeTier("Blizzard", "Pulses faster and bites harder", 125, damageMult = 4f, effectMult = 1.25f, reloadMult = 0.8f)
            ),
            description = "Pulses cold that slows everything in range. Weak damage alone."
        ),
        TroopType(
            id = "bomb", name = "Bomb Tower", color = Color(0xFFF58B3F),
            cost = 80, role = "Splash", shot = ShotKind.SHELL,
            damage = 20f, range = 21f, fireRateMs = 1500,
            targeting = TargetPriority.FIRST, splashRadius = 9f,
            upgrades = listOf(
                UpgradeTier("Big Bombs", "+50% damage, bigger blast", 90, damageMult = 1.5f, splashMult = 1.25f),
                UpgradeTier("Cluster Bombs", "Reloads much faster", 155, damageMult = 1.5f, reloadMult = 0.72f)
            ),
            description = "Explodes on impact. Great against packed groups."
        ),
        TroopType(
            id = "gatling", name = "Gatling", color = Color(0xFFF2C744),
            cost = 55, role = "Rapid fire", shot = ShotKind.BULLET,
            damage = 5f, range = 17f, fireRateMs = 180,
            targeting = TargetPriority.CLOSEST,
            upgrades = listOf(
                UpgradeTier("Hot Barrels", "+50% damage", 65, damageMult = 1.5f),
                UpgradeTier("Minigun", "Spins up to a wall of lead", 125, damageMult = 1.3f, reloadMult = 0.62f, rangeMult = 1.1f)
            ),
            description = "Very fast, short range. Shreds whatever walks past."
        ),
        TroopType(
            id = "chain", name = "Tesla Coil", color = Color(0xFFFFE45C),
            cost = 85, role = "Chains", shot = ShotKind.BOLT,
            damage = 16f, range = 22f, fireRateMs = 900,
            targeting = TargetPriority.FIRST, chainTargets = 2,
            upgrades = listOf(
                UpgradeTier("Arc Coil", "One more jump, harder hits", 95, damageMult = 1.35f, extraChains = 1),
                UpgradeTier("Storm Core", "Two more jumps, faster arcs", 165, damageMult = 1.45f, extraChains = 2, reloadMult = 0.85f)
            ),
            description = "Lightning jumps from the first target to its neighbours."
        ),
        TroopType(
            id = "poison", name = "Poison Totem", color = Color(0xFF6BCB5A),
            cost = 65, role = "Poison", shot = ShotKind.POISON_PULSE,
            damage = 2f, range = 17f, fireRateMs = 1200,
            targeting = TargetPriority.CLOSEST,
            dotDamagePerSecond = 6f, dotDurationMs = 3000,
            upgrades = listOf(
                UpgradeTier("Venom", "+60% poison", 75, effectMult = 1.6f),
                UpgradeTier("Plague", "Stronger poison, wider cloud", 135, effectMult = 1.7f, rangeMult = 1.2f)
            ),
            description = "Poisons everything nearby. Weak up front, strong over time."
        ),
        TroopType(
            id = "goldmine", name = "Gold Mine", color = Color(0xFFF2B01E),
            cost = 100, role = "Economy", shot = ShotKind.NONE,
            isAttacker = false, incomeBonusPerSecond = 2f,
            upgrades = listOf(
                UpgradeTier("Deep Shaft", "+60% income", 90, effectMult = 1.6f),
                UpgradeTier("Motherlode", "+70% income again", 150, effectMult = 1.7f)
            ),
            description = "No attack. Pays out gold for the rest of the match."
        ),
        TroopType(
            id = "stun", name = "Stun Turret", color = Color(0xFFF2609E),
            cost = 75, role = "Stuns", shot = ShotKind.ORB,
            damage = 12f, range = 21f, fireRateMs = 1100,
            targeting = TargetPriority.FIRST, stunChance = 0.35f, stunDurationMs = 900,
            upgrades = listOf(
                UpgradeTier("Overcharge", "Stuns more often, hits harder", 85, damageMult = 1.5f, effectMult = 1.4f),
                UpgradeTier("Paralyzer", "Nearly every shot stuns", 145, damageMult = 1.8f, effectMult = 1.4f, reloadMult = 0.85f)
            ),
            description = "Shots have a chance to freeze the target in place."
        ),
        TroopType(
            id = "antiair", name = "Flak Net", color = Color(0xFF3FD9A4),
            cost = 60, role = "Anti-air", shot = ShotKind.NET,
            damage = 16f, range = 24f, fireRateMs = 800,
            targeting = TargetPriority.FIRST, bonusDamageVsFlyerPct = 100f,
            upgrades = listOf(
                UpgradeTier("Flak Rounds", "+50% damage, longer reach", 70, damageMult = 1.5f, rangeMult = 1.1f),
                UpgradeTier("Skyfall", "Fires at two targets at once", 130, damageMult = 1.3f, extraShots = 1)
            ),
            description = "Goes for Flyers first and hits them for double damage."
        ),
        TroopType(
            id = "beacon", name = "Beacon", color = Color(0xFFB7A6FF),
            cost = 70, role = "Support", shot = ShotKind.NONE,
            isAttacker = false, auraDamageBonusPct = 20f, auraRange = 16f,
            upgrades = listOf(
                UpgradeTier("Amplifier", "Bigger boost, wider signal", 80, effectMult = 1.5f, rangeMult = 1.2f),
                UpgradeTier("Command Link", "Bigger boost again", 140, effectMult = 1.5f)
            ),
            description = "No attack. Every tower in its signal deals more damage."
        ),
        TroopType(
            id = "mortar", name = "Mortar", color = Color(0xFFE8504A),
            cost = 120, role = "Siege", shot = ShotKind.MORTAR,
            damage = 45f, range = 32f, fireRateMs = 2600,
            targeting = TargetPriority.FIRST, splashRadius = 11f,
            upgrades = listOf(
                UpgradeTier("Heavy Shells", "+60% damage", 130, damageMult = 1.6f),
                UpgradeTier("Barrage", "Reloads far faster, bigger blast", 210, reloadMult = 0.6f, splashMult = 1.2f)
            ),
            description = "Lobs shells that flatten a wide area. Slow to reload."
        )
    )

    // ---------------------------------------------------------------------
    // UNITS — the shared offense roster, also used by the natural waves.
    // ---------------------------------------------------------------------
    val ENEMY_SENDS: List<EnemySendType> = listOf(
        EnemySendType(
            id = "runner", name = "Runner", color = Color(0xFFF4F1E8),
            cost = 20, maxHp = 18f, speed = 14f, livesDamage = 1, bountyGold = 4, radius = 1.7f,
            incomeBonus = 0.30f, unlockRound = 1, cooldownMs = 350,
            description = "Cheap and fast. Easy to pop, easy to spam."
        ),
        EnemySendType(
            id = "grunt", name = "Grunt", color = Color(0xFF7D95B8),
            cost = 35, maxHp = 42f, speed = 9f, livesDamage = 2, bountyGold = 6, radius = 2.2f,
            incomeBonus = 0.45f, unlockRound = 1, cooldownMs = 450,
            description = "Sturdy foot soldier."
        ),
        EnemySendType(
            id = "swarm", name = "Swarm", color = Color(0xFFFFD23F),
            cost = 60, maxHp = 12f, speed = 11f, livesDamage = 1, bountyGold = 2, radius = 1.35f, count = 5,
            incomeBonus = 0.70f, unlockRound = 2, cooldownMs = 900,
            description = "Five tiny units at once. Overwhelms single-target towers."
        ),
        EnemySendType(
            id = "flyer", name = "Flyer", color = Color(0xFF5CD6F0),
            cost = 55, maxHp = 35f, speed = 12f, livesDamage = 2, bountyGold = 8, radius = 1.9f,
            flying = true, damageResistancePct = 50f,
            incomeBonus = 0.50f, unlockRound = 3, cooldownMs = 700,
            description = "Shrugs off half of all damage, unless it is Anti-air."
        ),
        EnemySendType(
            id = "tank", name = "Tank", color = Color(0xFF5E6B7E),
            cost = 90, maxHp = 240f, speed = 5.5f, livesDamage = 5, bountyGold = 14, radius = 3.1f,
            incomeBonus = 0.60f, unlockRound = 3, cooldownMs = 1200,
            description = "A slow wall of health. Needs sustained fire."
        ),
        EnemySendType(
            id = "healer", name = "Healer", color = Color(0xFF6BCB5A),
            cost = 65, maxHp = 55f, speed = 8f, livesDamage = 2, bountyGold = 9, radius = 2.1f,
            healPerSecond = 10f, healRadius = 12f,
            incomeBonus = 0.50f, unlockRound = 4, cooldownMs = 1000,
            description = "Heals itself and everyone walking near it."
        ),
        EnemySendType(
            id = "splitter", name = "Splitter", color = Color(0xFFB36BE8),
            cost = 80, maxHp = 90f, speed = 8f, livesDamage = 3, bountyGold = 8, radius = 2.6f,
            spawnOnDeathId = "splitling", spawnOnDeathCount = 3,
            incomeBonus = 0.60f, unlockRound = 5, cooldownMs = 1000,
            description = "Bursts into three fast Splitlings when popped."
        ),
        EnemySendType(
            id = "boss", name = "Boss", color = Color(0xFFE8504A),
            cost = 220, maxHp = 950f, speed = 4.2f, livesDamage = 20, bountyGold = 45, radius = 4.3f,
            incomeBonus = 0f, unlockRound = 7, cooldownMs = 6000,
            description = "Pure pressure, no income. Costs 20 lives if it gets through."
        ),
        EnemySendType(
            id = "splitling", name = "Splitling", color = Color(0xFFD29BF5),
            cost = 0, maxHp = 22f, speed = 13f, livesDamage = 1, bountyGold = 2, radius = 1.4f,
            sendable = false,
            description = "What is left of a Splitter."
        )
    )

    val SENDABLE_UNITS: List<EnemySendType> = ENEMY_SENDS.filter { it.sendable }

    private val unitsById: Map<String, EnemySendType> = ENEMY_SENDS.associateBy { it.id }
    fun unit(id: String): EnemySendType = unitsById.getValue(id)

    // ---------------------------------------------------------------------
    // WAVES — one entry per round. Shorter matches skip entries so they still reach the late ones.
    // ---------------------------------------------------------------------
    val WAVES: List<List<WaveGroup>> = listOf(
        listOf(WaveGroup("runner", 5, 750)),
        listOf(WaveGroup("grunt", 4, 950)),
        listOf(WaveGroup("runner", 8, 450), WaveGroup("grunt", 3, 950, 2500)),
        listOf(WaveGroup("swarm", 12, 260), WaveGroup("grunt", 4, 850, 2200)),
        listOf(WaveGroup("tank", 1, 0), WaveGroup("grunt", 6, 700, 1500)),
        listOf(WaveGroup("flyer", 5, 800), WaveGroup("runner", 8, 350, 3000)),
        listOf(WaveGroup("grunt", 8, 600), WaveGroup("healer", 2, 2400, 1200)),
        listOf(WaveGroup("splitter", 3, 1500), WaveGroup("swarm", 14, 220, 3000)),
        listOf(WaveGroup("tank", 3, 2200), WaveGroup("healer", 2, 2600, 1500), WaveGroup("flyer", 4, 700, 5000)),
        listOf(WaveGroup("boss", 1, 0), WaveGroup("grunt", 8, 500, 2000)),
        listOf(WaveGroup("swarm", 24, 180), WaveGroup("flyer", 8, 500, 2500), WaveGroup("splitter", 3, 1200, 5000)),
        listOf(WaveGroup("boss", 2, 5000), WaveGroup("tank", 4, 1800, 1500), WaveGroup("healer", 3, 2000, 3000))
    )

    // ---------------------------------------------------------------------
    // MAPS — control points in the 100 x 62 lane space. Paths enter off the left edge.
    // ---------------------------------------------------------------------
    val MAPS: List<MapDef> = listOf(
        MapDef(
            id = "meadow", name = "Clover Bend", theme = MapTheme.MEADOW,
            pathPoints = listOf(
                -8f to 13f, 14f to 13f, 27f to 22f, 27f to 40f, 39f to 50f,
                53f to 46f, 59f to 31f, 59f to 19f, 71f to 11f, 84f to 17f, 91f to 33f
            )
        ),
        MapDef(
            id = "dunes", name = "Sidewinder", theme = MapTheme.DUNES,
            pathPoints = listOf(
                -8f to 10f, 62f to 10f, 80f to 14f, 84f to 23f, 76f to 31f,
                26f to 31f, 16f to 37f, 16f to 46f, 26f to 52f, 91f to 52f
            )
        ),
        MapDef(
            id = "frost", name = "Icicle Pass", theme = MapTheme.FROST,
            pathPoints = listOf(
                -8f to 50f, 10f to 48f, 20f to 14f, 34f to 12f, 46f to 46f,
                58f to 50f, 68f to 14f, 80f to 12f, 91f to 40f
            )
        ),
        MapDef(
            id = "ember", name = "Cinder Loop", theme = MapTheme.EMBER,
            pathPoints = listOf(
                -8f to 49f, 14f to 49f, 34f to 46f, 49f to 34f, 47f to 18f, 36f to 12f,
                26f to 20f, 30f to 34f, 46f to 43f, 64f to 45f, 78f to 37f, 84f to 23f, 91f to 14f
            )
        )
    )

    // ---------------------------------------------------------------------
    // MODIFIERS — one is rolled at random per match.
    // ---------------------------------------------------------------------
    val MODIFIERS: List<MatchModifier> = listOf(
        MatchModifier(
            id = "rush_hour", name = "Rush Hour",
            description = "Every unit moves 30% faster.", speedMultiplier = 1.3f
        ),
        MatchModifier(
            id = "gold_rush", name = "Gold Rush",
            description = "Income is boosted 50% for both sides.", incomeMultiplier = 1.5f
        ),
        MatchModifier(
            id = "glass_cannons", name = "Glass Cannons",
            description = "Towers deal 40% more damage, but both sides start with 60 lives.",
            damageMultiplier = 1.4f, livesOverride = 60
        ),
        MatchModifier(
            id = "fortified", name = "Fortified",
            description = "All towers reach 20% further.", rangeMultiplier = 1.2f
        ),
        MatchModifier(
            id = "blitz", name = "Blitz",
            description = "A 2-minute match that starts with extra gold.",
            matchDurationOverrideSec = 120, startingGoldBonus = 80
        ),
        MatchModifier(
            id = "iron_lives", name = "Iron Lives",
            description = "Both sides start with 150 lives, but income is cut 20%.",
            livesOverride = 150, incomeMultiplier = 0.8f
        )
    )

    /** True if [hand] holds a tower that can actually kill things on its own. */
    fun hasDamageDealer(hand: List<TroopType>): Boolean = hand.any { it.baseDps >= MIN_DRAFT_DPS }

    /**
     * Deals [count] towers, rerolling hands with too few real damage dealers so every draft can
     * hold a lane. A full offer needs two, so that which dealer to take is still a choice.
     */
    fun randomDraft(count: Int = DRAFT_OFFER): List<TroopType> {
        val dealersNeeded = if (count > DRAFT_PICKS) 2 else 1
        while (true) {
            val draft = TROOPS.shuffled().take(count)
            if (draft.count { it.baseDps >= MIN_DRAFT_DPS } >= dealersNeeded) return draft
        }
    }
    fun randomMap(): MapDef = MAPS.random()
    fun randomModifier(): MatchModifier = MODIFIERS.random()
    fun randomPersonality(): AiPersonality = AiPersonality.entries.toTypedArray().random()
}
