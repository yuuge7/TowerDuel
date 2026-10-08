package com.towerduel.game.data

import androidx.compose.ui.graphics.Color
import kotlin.random.Random

object GameData {

    const val BASE_INCOME_PER_SEC = 9f
    const val STARTING_GOLD = 130
    const val STARTING_LIVES = 100
    const val MATCH_DURATION_SEC = 400
    /** Two tower centres may not be closer than this: the bodies nearly touch, the stone bases overlap. */
    const val MIN_TOWER_SPACING = 5.2f
    const val MIN_DRAFT_DPS = 10f

    /** How many towers each side is offered, and how many of those it takes into the match. */
    const val DRAFT_OFFER = 6
    const val DRAFT_PICKS = 4

    /** Half the width of the walkable track, and how far a tower's centre must stay from its middle. */
    const val PATH_HALF_WIDTH = 4f
    const val PATH_CLEARANCE = 6.6f

    // Waves hit both lanes at once, one every round.
    const val ROUND_INTERVAL_SEC = 20
    const val FIRST_ROUND_DELAY_SEC = 10
    const val OVERTIME_ROUND_INTERVAL_SEC = 10
    const val OVERTIME_LIMIT_SEC = 90

    /**
     * Waves are generated for a level from 1 to this. A match of any length climbs the whole
     * ladder: a short one skips levels, a long one repeats them.
     */
    const val WAVE_LEVELS = 20

    /** Wave units get tougher every level; past the last round the growth is steep on purpose. */
    const val WAVE_HP_GROWTH_PER_LEVEL = 0.09f

    /**
     * From [WAVE_LATE_FROM_LEVEL] on, units get this much tougher again with every level. By then
     * income has snowballed, and without it the second half of a match would be a walk for both sides.
     */
    const val WAVE_LATE_GROWTH = 1.13f
    const val WAVE_LATE_FROM_LEVEL = 10
    const val OVERTIME_HP_GROWTH = 1.45f

    /** Sudden-death waves also walk faster each round, so even a Boss arrives before the match is called. */
    const val OVERTIME_SPEED_GROWTH = 0.15f
    const val OVERTIME_MAX_SPEED = 2.5f

    /** How many units a match's roster holds, and the chance a match is played under two rules at once. */
    const val ROSTER_SIZE = 12
    const val SECOND_RULE_CHANCE = 0.3f

    /** A unit type with at least this much listed health counts as big: what a Giant Slayer hunts. */
    const val BIG_UNIT_HP = 200f

    /**
     * In a 2 v 2 two purses defend each lane, so every wave and send is made tougher to match:
     * units have this much more health, and a wave this many more of them.
     */
    const val TEAM_WAVE_HP = 1.6f
    const val TEAM_WAVE_SIZE = 1.25f

    /** Chance that a match is played on a freshly generated map instead of a named one. */
    const val WILD_MAP_CHANCE = 0.5f

    // Random events: the first one comes a while into the match, the rest at uneven intervals.
    const val FIRST_EVENT_MIN_SEC = 45
    const val FIRST_EVENT_MAX_SEC = 75
    const val EVENT_GAP_MIN_SEC = 40
    const val EVENT_GAP_MAX_SEC = 70

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
                UpgradeTier("Twin Barrels", "Fires at two targets at once", 115, extraShots = 1, reloadMult = 0.85f),
                UpgradeTier("Triple Threat", "A third barrel and harder darts", 190, damageMult = 1.4f, extraShots = 1),
                UpgradeTier("Quad Battery", "A fourth barrel, +40% damage", 300, damageMult = 1.4f, extraShots = 1)
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
                UpgradeTier("Railgun", "Huge damage, quicker reload", 170, damageMult = 1.8f, reloadMult = 0.8f),
                UpgradeTier("One Shot", "Double damage", 280, damageMult = 2f),
                UpgradeTier("Deadshot", "+70% damage, quicker reload", 430, damageMult = 1.7f, reloadMult = 0.85f)
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
                UpgradeTier("Blizzard", "Pulses faster and bites harder", 125, damageMult = 4f, effectMult = 1.25f, reloadMult = 0.8f),
                UpgradeTier("Absolute Zero", "Nearly stops units, far wider pulse", 200, damageMult = 2f, effectMult = 1.3f, rangeMult = 1.2f),
                UpgradeTier("Ice Age", "Pulses faster, bites far harder", 310, damageMult = 2.5f, reloadMult = 0.75f)
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
                UpgradeTier("Cluster Bombs", "Reloads much faster", 155, damageMult = 1.5f, reloadMult = 0.72f),
                UpgradeTier("Carpet Bombs", "Far more damage, bigger blast", 250, damageMult = 1.7f, splashMult = 1.3f),
                UpgradeTier("Megaton", "+70% damage, reloads faster", 390, damageMult = 1.7f, reloadMult = 0.85f)
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
                UpgradeTier("Minigun", "Spins up to a wall of lead", 125, damageMult = 1.3f, reloadMult = 0.62f, rangeMult = 1.1f),
                UpgradeTier("Bullet Storm", "+80% damage, longer reach", 200, damageMult = 1.8f, rangeMult = 1.15f),
                UpgradeTier("Lead Hurricane", "+60% damage, spins faster", 310, damageMult = 1.6f, reloadMult = 0.85f)
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
                UpgradeTier("Storm Core", "Two more jumps, faster arcs", 165, damageMult = 1.45f, extraChains = 2, reloadMult = 0.85f),
                UpgradeTier("Thunderhead", "Three more jumps, much harder hits", 270, damageMult = 1.6f, extraChains = 3),
                UpgradeTier("Wrath Of Zeus", "+70% damage, faster arcs", 420, damageMult = 1.7f, reloadMult = 0.85f)
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
                UpgradeTier("Plague", "Stronger poison, wider cloud", 135, effectMult = 1.7f, rangeMult = 1.2f),
                UpgradeTier("Black Death", "Double poison", 220, effectMult = 2f),
                UpgradeTier("Pandemic", "+80% poison, wider cloud", 340, effectMult = 1.8f, rangeMult = 1.15f)
            ),
            description = "Poisons everything nearby. Weak up front, strong over time."
        ),
        TroopType(
            id = "goldmine", name = "Gold Mine", color = Color(0xFFF2B01E),
            cost = 100, role = "Economy", shot = ShotKind.NONE,
            isAttacker = false, incomeBonusPerSecond = 2f,
            upgrades = listOf(
                UpgradeTier("Deep Shaft", "+60% income", 90, effectMult = 1.6f),
                UpgradeTier("Motherlode", "+70% income again", 150, effectMult = 1.7f),
                UpgradeTier("Royal Mint", "Income doubled", 260, effectMult = 2f),
                UpgradeTier("Dragon Hoard", "+70% income", 420, effectMult = 1.7f)
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
                UpgradeTier("Paralyzer", "Nearly every shot stuns", 145, damageMult = 1.8f, effectMult = 1.4f, reloadMult = 0.85f),
                UpgradeTier("Lockdown", "Double damage, reloads faster", 240, damageMult = 2f, reloadMult = 0.75f),
                UpgradeTier("Shock Trooper", "+70% damage, a second target", 370, damageMult = 1.7f, extraShots = 1)
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
                UpgradeTier("Skyfall", "Fires at two targets at once", 130, damageMult = 1.3f, extraShots = 1),
                UpgradeTier("Iron Dome", "A third target, +50% damage", 210, damageMult = 1.5f, extraShots = 1),
                UpgradeTier("Sky Fortress", "A fourth target, +60% damage", 330, damageMult = 1.6f, extraShots = 1)
            ),
            description = "Goes for Flyers first and hits them for double damage."
        ),
        TroopType(
            id = "beacon", name = "Beacon", color = Color(0xFFB7A6FF),
            cost = 70, role = "Support", shot = ShotKind.NONE,
            isAttacker = false, auraDamageBonusPct = 20f, auraRange = 16f,
            upgrades = listOf(
                UpgradeTier("Amplifier", "Bigger boost, wider signal", 80, effectMult = 1.5f, rangeMult = 1.2f),
                UpgradeTier("Command Link", "Bigger boost again", 140, effectMult = 1.5f),
                UpgradeTier("Supremacy", "Boost and signal both grow again", 230, effectMult = 1.4f, rangeMult = 1.2f),
                UpgradeTier("Ascendancy", "A far bigger boost", 360, effectMult = 1.35f)
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
                UpgradeTier("Barrage", "Reloads far faster, bigger blast", 210, reloadMult = 0.6f, splashMult = 1.2f),
                UpgradeTier("Doomsday Shells", "Double damage, bigger blast", 340, damageMult = 2f, splashMult = 1.2f),
                UpgradeTier("Armageddon", "+70% damage, reloads faster", 520, damageMult = 1.7f, reloadMult = 0.85f)
            ),
            description = "Lobs shells that flatten a wide area. Slow to reload."
        ),
        TroopType(
            id = "flamer", name = "Flame Tower", color = Color(0xFFFF6A2A),
            cost = 70, role = "Burns", shot = ShotKind.FLAME,
            damage = 4f, range = 12f, fireRateMs = 160,
            targeting = TargetPriority.CLOSEST, splashRadius = 5f,
            dotDamagePerSecond = 5f, dotDurationMs = 2000,
            upgrades = listOf(
                UpgradeTier("Hotter Fuel", "+50% damage, fiercer burn", 80, damageMult = 1.5f, effectMult = 1.5f),
                UpgradeTier("Inferno", "Longer, wider flame", 140, damageMult = 1.4f, rangeMult = 1.25f, splashMult = 1.4f),
                UpgradeTier("Dragon Breath", "Much hotter, burns far worse", 230, damageMult = 1.6f, effectMult = 1.8f),
                UpgradeTier("Hellfire", "+60% damage, longer flame", 360, damageMult = 1.6f, rangeMult = 1.15f)
            ),
            description = "A short jet of fire that sets a whole clump of units burning."
        ),
        TroopType(
            id = "prism", name = "Prism", color = Color(0xFFFF9EEA),
            cost = 95, role = "Melts bosses", shot = ShotKind.BEAM,
            damage = 5f, range = 24f, fireRateMs = 200,
            targeting = TargetPriority.STRONGEST, rampPerHit = 0.12f, rampMax = 2.5f,
            upgrades = listOf(
                UpgradeTier("Focus Lens", "+50% damage", 100, damageMult = 1.5f),
                UpgradeTier("Death Ray", "Ramps much further, pulses faster", 175, effectMult = 1.6f, reloadMult = 0.85f),
                UpgradeTier("Supernova", "Double damage", 290, damageMult = 2f),
                UpgradeTier("Singularity", "+70% damage, ramps further", 450, damageMult = 1.7f, effectMult = 1.3f)
            ),
            description = "A beam that grows stronger the longer it stays on one target."
        ),
        TroopType(
            id = "glaive", name = "Glaive Thrower", color = Color(0xFFB6E63A),
            cost = 85, role = "Pierces", shot = ShotKind.GLAIVE,
            damage = 18f, range = 22f, fireRateMs = 1000,
            targeting = TargetPriority.FIRST, pierce = 3,
            upgrades = listOf(
                UpgradeTier("Serrated Edge", "+50% damage", 90, damageMult = 1.5f),
                UpgradeTier("Whirlwind", "Cuts through three more, throws faster", 150, extraChains = 3, reloadMult = 0.8f),
                UpgradeTier("Razor Storm", "Double damage, cuts three more", 250, damageMult = 2f, extraChains = 3),
                UpgradeTier("Blade Cyclone", "+70% damage, throws faster", 390, damageMult = 1.7f, reloadMult = 0.85f)
            ),
            description = "Throws a blade that cuts through every unit in a line."
        ),
        TroopType(
            id = "crossbow", name = "Crossbow", color = Color(0xFFC08A52),
            cost = 80, role = "Crits", shot = ShotKind.DART,
            damage = 30f, range = 30f, fireRateMs = 1200,
            targeting = TargetPriority.FIRST, critChance = 0.25f, critMultiplier = 3f,
            upgrades = listOf(
                UpgradeTier("Heavy Bolts", "+50% damage", 90, damageMult = 1.5f),
                UpgradeTier("Deadeye", "Crits far more often, longer reach", 160, effectMult = 1.8f, rangeMult = 1.15f),
                UpgradeTier("Arbalest", "Double damage, faster reload", 260, damageMult = 2f, reloadMult = 0.8f),
                UpgradeTier("Dragon Slayer", "+70% damage, longer reach", 400, damageMult = 1.7f, rangeMult = 1.1f)
            ),
            description = "Long, hard-hitting bolts. One in four hits for triple damage."
        ),
        TroopType(
            id = "hex", name = "Hex Totem", color = Color(0xFF8A63D2),
            cost = 70, role = "Weakens", shot = ShotKind.ORB,
            damage = 6f, range = 20f, fireRateMs = 900,
            targeting = TargetPriority.STRONGEST, vulnerabilityPct = 25f, vulnerabilityMs = 3000,
            upgrades = listOf(
                UpgradeTier("Deeper Curse", "Cursed units take more damage", 80, effectMult = 1.4f),
                UpgradeTier("Doom", "Stronger curse, two targets at once", 140, effectMult = 1.3f, extraShots = 1),
                UpgradeTier("Damnation", "A far stronger curse, a third target", 230, effectMult = 1.4f, extraShots = 1),
                UpgradeTier("Oblivion", "Stronger curse, hexes faster", 360, effectMult = 1.3f, reloadMult = 0.75f)
            ),
            description = "Curses a unit so every other tower hurts it more."
        ),
        TroopType(
            id = "gust", name = "Gust Fan", color = Color(0xFFBFE3FF),
            cost = 75, role = "Pushes back", shot = ShotKind.GUST_PULSE,
            damage = 3f, range = 15f, fireRateMs = 2200,
            targeting = TargetPriority.CLOSEST, knockback = 5f,
            upgrades = listOf(
                UpgradeTier("Gale", "Stronger push, wider gust", 85, effectMult = 1.4f, rangeMult = 1.15f),
                UpgradeTier("Hurricane", "Gusts more often and stings", 140, damageMult = 4f, reloadMult = 0.75f),
                UpgradeTier("Tornado", "Throws units much further, wider gust", 230, effectMult = 1.6f, rangeMult = 1.2f),
                UpgradeTier("Jet Stream", "Gusts far more often", 360, reloadMult = 0.65f)
            ),
            description = "Blows every unit in range back down the track. Heavy units barely budge."
        ),
        TroopType(
            id = "bounty", name = "Bounty Hunter", color = Color(0xFF2FA4A9),
            cost = 65, role = "Extra gold", shot = ShotKind.BULLET,
            damage = 11f, range = 20f, fireRateMs = 600,
            targeting = TargetPriority.FIRST, bountyBonusPct = 60f,
            upgrades = listOf(
                UpgradeTier("Marked Bills", "+50% damage, bigger bounties", 75, damageMult = 1.5f, effectMult = 1.4f),
                UpgradeTier("Jackpot", "Bigger bounties again, fires faster", 130, effectMult = 1.5f, reloadMult = 0.8f),
                UpgradeTier("Kingpin", "Double damage, richer bounties", 210, damageMult = 2f, effectMult = 1.3f),
                UpgradeTier("Crime Lord", "+70% damage, richer bounties", 330, damageMult = 1.7f, effectMult = 1.25f)
            ),
            description = "A modest gun whose kills pay 60% more gold."
        ),
        TroopType(
            id = "reaper", name = "Reaper", color = Color(0xFF9C4A6E),
            cost = 100, role = "Executes", shot = ShotKind.RAIL,
            damage = 26f, range = 18f, fireRateMs = 900,
            targeting = TargetPriority.FIRST, executeBelowPct = 12f,
            upgrades = listOf(
                UpgradeTier("Keen Edge", "+50% damage", 110, damageMult = 1.5f),
                UpgradeTier("Grim Harvest", "Finishes units off much sooner, longer reach", 170, effectMult = 1.8f, rangeMult = 1.2f),
                UpgradeTier("Death's Door", "+80% damage, faster swings", 280, damageMult = 1.8f, reloadMult = 0.8f),
                UpgradeTier("Grim Finale", "+70% damage, finishes sooner", 430, damageMult = 1.7f, effectMult = 1.3f)
            ),
            description = "Hits hard up close and finishes off anything nearly dead."
        ),
        TroopType(
            id = "comet", name = "Comet Caller", color = Color(0xFF5B7BE8),
            cost = 130, role = "Whole lane", shot = ShotKind.MORTAR,
            damage = 60f, range = 200f, fireRateMs = 4200,
            targeting = TargetPriority.STRONGEST, splashRadius = 9f,
            upgrades = listOf(
                UpgradeTier("Bigger Rocks", "+60% damage", 140, damageMult = 1.6f),
                UpgradeTier("Meteor Shower", "Calls them down far more often", 230, reloadMult = 0.65f),
                UpgradeTier("Extinction", "+80% damage, bigger blast", 360, damageMult = 1.8f, splashMult = 1.3f),
                UpgradeTier("Apocalypse", "+70% damage, falls faster", 550, damageMult = 1.7f, reloadMult = 0.85f)
            ),
            description = "Drops a rock on the toughest unit anywhere on the lane. Slow, but nothing is out of reach."
        ),
        TroopType(
            id = "thumper", name = "Thumper", color = Color(0xFFB8A58C),
            cost = 90, role = "Stuns a crowd", shot = ShotKind.QUAKE_PULSE,
            damage = 6f, range = 13f, fireRateMs = 3800,
            targeting = TargetPriority.CLOSEST, stunChance = 0.9f, stunDurationMs = 700,
            upgrades = listOf(
                UpgradeTier("Heavy Hammer", "Hits harder, wider quake", 100, damageMult = 3f, rangeMult = 1.15f),
                UpgradeTier("Aftershock", "Slams more often", 160, reloadMult = 0.75f),
                UpgradeTier("Earthshaker", "More often again, harder and wider", 250, damageMult = 2f, reloadMult = 0.75f, rangeMult = 1.15f),
                UpgradeTier("Tectonic", "Slams faster, far harder", 390, damageMult = 2.5f, reloadMult = 0.8f)
            ),
            description = "Slams the ground and stops everything nearby in its tracks for a moment."
        ),
        TroopType(
            id = "overclock", name = "Overclocker", color = Color(0xFFFFA63D),
            cost = 85, role = "Support", shot = ShotKind.NONE,
            isAttacker = false, auraReloadBonusPct = 15f, auraRange = 15f,
            upgrades = listOf(
                UpgradeTier("Tuned Gears", "Bigger boost, wider reach", 90, effectMult = 1.4f, rangeMult = 1.15f),
                UpgradeTier("Redline", "Bigger boost again", 150, effectMult = 1.4f),
                UpgradeTier("Perpetual Motion", "Bigger boost, wider reach again", 240, effectMult = 1.35f, rangeMult = 1.2f),
                UpgradeTier("Time Warp", "A far bigger boost", 370, effectMult = 1.35f)
            ),
            description = "No attack. Every tower near it fires faster."
        ),
        TroopType(
            id = "shrine", name = "Shrine", color = Color(0xFFFFE9A8),
            cost = 110, role = "Restores lives", shot = ShotKind.NONE,
            isAttacker = false, livesPerMinute = 5f,
            upgrades = listOf(
                UpgradeTier("Bigger Altar", "+60% lives restored", 110, effectMult = 1.6f),
                UpgradeTier("Pilgrimage", "+60% again", 180, effectMult = 1.6f),
                UpgradeTier("Miracle", "+60% once more", 280, effectMult = 1.6f),
                UpgradeTier("Divine Favour", "And a third more on top", 430, effectMult = 1.33f)
            ),
            description = "No attack. Slowly gives lost lives back, up to what you started with."
        ),
        TroopType(
            id = "ballista", name = "Ballista", color = Color(0xFFD9B26A),
            cost = 95, role = "Breaks armour", shot = ShotKind.DART,
            damage = 36f, range = 27f, fireRateMs = 1500,
            targeting = TargetPriority.STRONGEST, sundersArmor = true,
            upgrades = listOf(
                UpgradeTier("Barbed Bolts", "+50% damage", 100, damageMult = 1.5f),
                UpgradeTier("Twin Strings", "Reloads much faster", 170, reloadMult = 0.7f),
                UpgradeTier("Siege Bolts", "Double damage, longer reach", 280, damageMult = 2f, rangeMult = 1.15f),
                UpgradeTier("Titan Bow", "+70% damage, reloads faster", 430, damageMult = 1.7f, reloadMult = 0.85f)
            ),
            description = "Heavy bolts that crack a unit open: it loses its armour and its toughness for good."
        ),
        TroopType(
            id = "harpoon", name = "Harpoon", color = Color(0xFF4FB3C8),
            cost = 90, role = "Drags back", shot = ShotKind.DART,
            damage = 22f, range = 25f, fireRateMs = 2300,
            targeting = TargetPriority.STRONGEST, knockback = 8f,
            upgrades = listOf(
                UpgradeTier("Barbed Hook", "+60% damage, stronger pull", 95, damageMult = 1.6f, effectMult = 1.3f),
                UpgradeTier("Winch", "Reels in much faster", 160, reloadMult = 0.65f),
                UpgradeTier("Leviathan Line", "Double damage, pulls far harder", 250, damageMult = 2f, effectMult = 1.6f),
                UpgradeTier("Kraken's Grip", "Reels faster, pulls harder still", 390, reloadMult = 0.75f, effectMult = 1.3f)
            ),
            description = "Hooks the toughest unit in reach and hauls it back down the track."
        ),
        TroopType(
            id = "glue", name = "Glue Gun", color = Color(0xFFE6D36A),
            cost = 70, role = "Slows a crowd", shot = ShotKind.SHELL,
            damage = 6f, range = 22f, fireRateMs = 1300,
            targeting = TargetPriority.FIRST, splashRadius = 7f, slowFactor = 0.3f, slowDurationMs = 1800,
            upgrades = listOf(
                UpgradeTier("Sticky Mix", "Stronger slow, bigger splat", 75, effectMult = 1.3f, splashMult = 1.2f),
                UpgradeTier("Rapid Pump", "Fires much faster and stings", 130, damageMult = 3f, reloadMult = 0.7f),
                UpgradeTier("Tar Flood", "A far stronger slow, bigger splat", 210, damageMult = 2f, effectMult = 1.35f, splashMult = 1.25f),
                UpgradeTier("Quagmire", "Fires faster, far bigger splat", 320, reloadMult = 0.75f, splashMult = 1.3f)
            ),
            description = "Lobs a blob that gums up a whole clump of units from a distance."
        ),
        TroopType(
            id = "missiles", name = "Missile Pod", color = Color(0xFFE2574C),
            cost = 100, role = "Volleys", shot = ShotKind.ROCKET,
            damage = 11f, range = 25f, fireRateMs = 1400,
            targeting = TargetPriority.FIRST, shots = 2, splashRadius = 4.5f,
            upgrades = listOf(
                UpgradeTier("Bigger Warheads", "+50% damage, bigger blast", 105, damageMult = 1.5f, splashMult = 1.2f),
                UpgradeTier("Double Rack", "Two more rockets per volley", 180, extraShots = 2),
                UpgradeTier("Rocket Storm", "Two more again, +50% damage", 290, damageMult = 1.5f, extraShots = 2),
                UpgradeTier("Rain Of Fire", "+60% damage, reloads faster", 450, damageMult = 1.6f, reloadMult = 0.85f)
            ),
            description = "Fires a volley of small rockets, each at a different unit."
        ),
        TroopType(
            id = "lookout", name = "Lookout", color = Color(0xFF7FD1B9),
            cost = 60, role = "Support", shot = ShotKind.NONE,
            isAttacker = false, auraRangeBonusPct = 20f, auraRange = 16f,
            upgrades = listOf(
                UpgradeTier("Spyglass", "Bigger boost, wider view", 80, effectMult = 1.4f, rangeMult = 1.15f),
                UpgradeTier("Signal Fires", "Bigger boost again", 140, effectMult = 1.35f),
                UpgradeTier("Eagle Eye", "Boost and view both grow again", 230, effectMult = 1.3f, rangeMult = 1.2f),
                UpgradeTier("All-Seeing", "A far bigger boost", 360, effectMult = 1.3f)
            ),
            description = "No attack. Every tower near it reaches further."
        ),
        TroopType(
            id = "pulsar", name = "Pulsar", color = Color(0xFFFF7AD9),
            cost = 85, role = "Hits everything", shot = ShotKind.NOVA_PULSE,
            damage = 11f, range = 14f, fireRateMs = 1000,
            targeting = TargetPriority.CLOSEST,
            upgrades = listOf(
                UpgradeTier("Brighter Core", "+60% damage", 90, damageMult = 1.6f),
                UpgradeTier("Wide Band", "Wider pulse, pulses faster", 150, rangeMult = 1.2f, reloadMult = 0.8f),
                UpgradeTier("Starburst", "Double damage, wider again", 240, damageMult = 2f, rangeMult = 1.15f),
                UpgradeTier("Quasar", "+70% damage, pulses faster", 370, damageMult = 1.7f, reloadMult = 0.85f)
            ),
            description = "A burst of raw energy that hurts every unit around it. No tricks, just damage."
        ),
        TroopType(
            id = "veteran", name = "Veteran", color = Color(0xFF6E8F4E),
            cost = 70, role = "Grows", shot = ShotKind.BULLET,
            damage = 9f, range = 21f, fireRateMs = 500,
            targeting = TargetPriority.FIRST, killGrowthPct = 1f, killGrowthMaxPct = 100f,
            upgrades = listOf(
                UpgradeTier("Battle Scars", "+50% damage", 80, damageMult = 1.5f),
                UpgradeTier("Old Hand", "Fires faster, learns faster", 140, reloadMult = 0.75f, effectMult = 1.4f),
                UpgradeTier("Living Legend", "Double damage, learns more", 240, damageMult = 2f, effectMult = 1.3f),
                UpgradeTier("War Hero", "+60% damage, fires faster", 370, damageMult = 1.6f, reloadMult = 0.85f)
            ),
            description = "A plain gun that hits 1% harder with every kill, up to double. Sell it and the lessons are lost."
        ),
        TroopType(
            id = "detonator", name = "Detonator", color = Color(0xFFF2A03D),
            cost = 95, role = "Chain blasts", shot = ShotKind.ORB,
            damage = 20f, range = 21f, fireRateMs = 1000,
            targeting = TargetPriority.FIRST, deathBlastPct = 35f, deathBlastRadius = 7f,
            upgrades = listOf(
                UpgradeTier("Short Fuse", "+50% damage, bigger blasts", 100, damageMult = 1.5f, effectMult = 1.3f),
                UpgradeTier("Black Powder", "Fires faster, wider blasts", 170, reloadMult = 0.75f, splashMult = 1.3f),
                UpgradeTier("Chain Reaction", "Double damage, far bigger blasts", 270, damageMult = 2f, effectMult = 1.4f),
                UpgradeTier("Doomsday Fuse", "+70% damage, fires faster", 420, damageMult = 1.7f, reloadMult = 0.85f)
            ),
            description = "Whatever it kills blows up and hurts the units around it. One pop can set off a whole pack."
        ),
        TroopType(
            id = "railgun", name = "Railgun", color = Color(0xFF00C2A8),
            cost = 110, role = "Pierces a line", shot = ShotKind.RAIL,
            damage = 34f, range = 30f, fireRateMs = 2000,
            targeting = TargetPriority.FIRST, pierce = 4,
            upgrades = listOf(
                UpgradeTier("Capacitors", "+50% damage", 115, damageMult = 1.5f),
                UpgradeTier("Long Rails", "Longer reach, two more pierced", 190, rangeMult = 1.2f, extraChains = 2),
                UpgradeTier("Overcharge", "Double damage", 310, damageMult = 2f),
                UpgradeTier("Annihilator", "+70% damage, reloads faster", 470, damageMult = 1.7f, reloadMult = 0.8f)
            ),
            description = "One shot goes straight through its target and on down the line behind it."
        ),
        TroopType(
            id = "icebreaker", name = "Icebreaker", color = Color(0xFF5AA9E6),
            cost = 85, role = "Punishes the slowed", shot = ShotKind.DART,
            damage = 16f, range = 22f, fireRateMs = 800,
            targeting = TargetPriority.FIRST, bonusVsControlledPct = 120f,
            upgrades = listOf(
                UpgradeTier("Ice Picks", "+50% damage", 90, damageMult = 1.5f),
                UpgradeTier("Cold Read", "Hits the helpless far harder", 150, effectMult = 1.4f),
                UpgradeTier("Glacier Cutter", "Double damage, longer reach", 250, damageMult = 2f, rangeMult = 1.15f),
                UpgradeTier("Permafrost", "+70% damage, fires faster", 390, damageMult = 1.7f, reloadMult = 0.85f)
            ),
            description = "An honest gun on its own. Against anything slowed or stunned it hits more than twice as hard."
        ),
        TroopType(
            id = "slayer", name = "Giant Slayer", color = Color(0xFFCD7F32),
            cost = 105, role = "Hunts giants", shot = ShotKind.DART,
            damage = 30f, range = 26f, fireRateMs = 1400,
            targeting = TargetPriority.STRONGEST, bonusVsBigPct = 150f,
            upgrades = listOf(
                UpgradeTier("Sharpened", "+50% damage", 110, damageMult = 1.5f),
                UpgradeTier("Giant's Bane", "Far more against the big ones", 180, effectMult = 1.4f),
                UpgradeTier("Titan Feller", "Double damage", 290, damageMult = 2f),
                UpgradeTier("Colossus Killer", "+70% damage, reloads faster", 440, damageMult = 1.7f, reloadMult = 0.85f)
            ),
            description = "Goes for the toughest unit, and does two and a half times the damage to Tanks, bosses and their like."
        ),
        TroopType(
            id = "bolas", name = "Bolas", color = Color(0xFFC9B037),
            cost = 80, role = "Roots one", shot = ShotKind.NET,
            damage = 14f, range = 24f, fireRateMs = 2600,
            targeting = TargetPriority.STRONGEST, stunChance = 0.9f, stunDurationMs = 2400,
            upgrades = listOf(
                UpgradeTier("Quick Hands", "Throws much faster", 85, reloadMult = 0.7f),
                UpgradeTier("Twin Bolas", "Two targets at once", 150, extraShots = 1),
                UpgradeTier("Iron Bolas", "Hits hard, throws faster", 240, damageMult = 4f, reloadMult = 0.8f),
                UpgradeTier("Ensnare", "A third target, faster still", 370, extraShots = 1, reloadMult = 0.85f)
            ),
            description = "Trips up the toughest unit in reach and holds it there for well over two seconds."
        ),
        TroopType(
            id = "scatter", name = "Scattergun", color = Color(0xFF7A8BA6),
            cost = 75, role = "Close blast", shot = ShotKind.BULLET,
            damage = 8f, range = 15f, fireRateMs = 1000,
            targeting = TargetPriority.CLOSEST, shots = 5,
            upgrades = listOf(
                UpgradeTier("Heavy Shot", "+50% damage", 80, damageMult = 1.5f),
                UpgradeTier("Wide Choke", "Two more pellets, longer reach", 140, extraShots = 2, rangeMult = 1.15f),
                UpgradeTier("Pump Action", "Fires much faster", 230, reloadMult = 0.65f),
                UpgradeTier("Dragon Shells", "Double damage, three more pellets", 360, damageMult = 2f, extraShots = 3)
            ),
            description = "Five pellets a blast, spread over whatever is close. Useless at a distance, brutal on a corner."
        ),
        TroopType(
            id = "blight", name = "Blighter", color = Color(0xFF7D9D3C),
            cost = 90, role = "Spreading rot", shot = ShotKind.ORB,
            damage = 5f, range = 22f, fireRateMs = 1100,
            targeting = TargetPriority.FIRST,
            dotDamagePerSecond = 12f, dotDurationMs = 4000, dotSpreadRadius = 8f,
            upgrades = listOf(
                UpgradeTier("Virulent", "+60% rot", 95, effectMult = 1.6f),
                UpgradeTier("Rapid Spit", "Spits much faster", 160, reloadMult = 0.65f),
                UpgradeTier("Pestilence", "Double rot, and it jumps further", 260, effectMult = 2f, splashMult = 1.4f),
                UpgradeTier("End Times", "+80% rot, spits faster", 400, effectMult = 1.8f, reloadMult = 0.85f)
            ),
            description = "Its rot eats one unit at a time. When the carrier dies, the rot jumps to everything around it."
        )
    )

    // ---------------------------------------------------------------------
    // UNITS — everything that can walk a lane. Each match uses a roster of ROSTER_SIZE of them.
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
            id = "drummer", name = "Drummer", color = Color(0xFFE0744F),
            cost = 60, maxHp = 60f, speed = 9f, livesDamage = 2, bountyGold = 8, radius = 2.1f,
            hasteAuraPct = 35f, hasteRadius = 12f,
            incomeBonus = 0.45f, unlockRound = 3, cooldownMs = 1000,
            description = "Everyone marching near it moves 35% faster."
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
            id = "phantom", name = "Phantom", color = Color(0xFFCDBEFF),
            cost = 70, maxHp = 60f, speed = 10f, livesDamage = 2, bountyGold = 9, radius = 2.1f,
            phaseMs = 1200, phaseEveryMs = 3000,
            incomeBonus = 0.50f, unlockRound = 4, cooldownMs = 900,
            description = "Fades out of reach for a moment every few seconds."
        ),
        EnemySendType(
            id = "healer", name = "Healer", color = Color(0xFF6BCB5A),
            cost = 65, maxHp = 55f, speed = 8f, livesDamage = 2, bountyGold = 9, radius = 2.1f,
            healPerSecond = 10f, healRadius = 12f,
            incomeBonus = 0.50f, unlockRound = 4, cooldownMs = 1000,
            description = "Heals itself and everyone walking near it."
        ),
        EnemySendType(
            id = "bulwark", name = "Bulwark", color = Color(0xFF4F86C6),
            cost = 75, maxHp = 110f, speed = 7f, livesDamage = 3, bountyGold = 10, radius = 2.6f,
            armor = 4f,
            incomeBonus = 0.55f, unlockRound = 4, cooldownMs = 1000,
            description = "Armour takes 4 off every hit. Rapid fire bounces, big hits do not."
        ),
        EnemySendType(
            id = "splitter", name = "Splitter", color = Color(0xFFB36BE8),
            cost = 80, maxHp = 90f, speed = 8f, livesDamage = 3, bountyGold = 8, radius = 2.6f,
            spawnOnDeathId = "splitling", spawnOnDeathCount = 3,
            incomeBonus = 0.60f, unlockRound = 5, cooldownMs = 1000,
            description = "Bursts into three fast Splitlings when popped."
        ),
        EnemySendType(
            id = "troll", name = "Troll", color = Color(0xFF8FAE4E),
            cost = 85, maxHp = 150f, speed = 6.5f, livesDamage = 4, bountyGold = 12, radius = 2.8f,
            regenPerSecond = 14f,
            incomeBonus = 0.55f, unlockRound = 5, cooldownMs = 1100,
            description = "Heals fast the moment the shooting stops."
        ),
        EnemySendType(
            id = "brood", name = "Brood Mother", color = Color(0xFFE8A13C),
            cost = 95, maxHp = 110f, speed = 6f, livesDamage = 3, bountyGold = 8, radius = 2.9f,
            spawnOnDeathId = "swarm", spawnOnDeathCount = 6,
            incomeBonus = 0.60f, unlockRound = 6, cooldownMs = 1300,
            description = "Pops into six Swarm units."
        ),
        EnemySendType(
            id = "burrower", name = "Burrower", color = Color(0xFFB07A4A),
            cost = 75, maxHp = 85f, speed = 9f, livesDamage = 3, bountyGold = 9, radius = 2.2f,
            burrowUntil = 0.35f,
            incomeBonus = 0.50f, unlockRound = 6, cooldownMs = 1000,
            description = "Tunnels under the first third of the track, where nothing can touch it."
        ),
        EnemySendType(
            id = "warder", name = "Warder", color = Color(0xFF6FA8FF),
            cost = 85, maxHp = 80f, speed = 8f, livesDamage = 2, bountyGold = 10, radius = 2.2f,
            wardAuraPct = 30f, wardRadius = 12f,
            incomeBonus = 0.50f, unlockRound = 8, cooldownMs = 1200,
            description = "Everyone near it takes 30% less damage. Pop it first."
        ),
        EnemySendType(
            id = "berserker", name = "Berserker", color = Color(0xFFD94B6A),
            cost = 80, maxHp = 130f, speed = 7f, livesDamage = 3, bountyGold = 10, radius = 2.5f,
            enrageSpeedPct = 120f,
            incomeBonus = 0.55f, unlockRound = 9, cooldownMs = 1000,
            description = "The more it is hurt, the faster it charges."
        ),
        EnemySendType(
            id = "wyvern", name = "Wyvern", color = Color(0xFF3FBF9A),
            cost = 190, maxHp = 600f, speed = 7.5f, livesDamage = 10, bountyGold = 28, radius = 3.4f,
            flying = true, damageResistancePct = 40f,
            incomeBonus = 0.30f, unlockRound = 11, cooldownMs = 4000,
            description = "A flying heavyweight. Without Anti-air it takes a lot of killing."
        ),
        EnemySendType(
            id = "colossus", name = "Colossus", color = Color(0xFF9AA3B5),
            cost = 480, maxHp = 2400f, speed = 3.6f, livesDamage = 40, bountyGold = 90, radius = 5f,
            armor = 6f,
            incomeBonus = 0f, unlockRound = 13, cooldownMs = 9000,
            description = "An armoured giant for the late rounds. Costs 40 lives if it arrives."
        ),
        EnemySendType(
            id = "boss", name = "Boss", color = Color(0xFFE8504A),
            cost = 220, maxHp = 950f, speed = 4.2f, livesDamage = 20, bountyGold = 45, radius = 4.3f,
            incomeBonus = 0f, unlockRound = 7, cooldownMs = 6000,
            description = "Pure pressure, no income. Costs 20 lives if it gets through."
        ),
        EnemySendType(
            id = "juggernaut", name = "Juggernaut", color = Color(0xFFA83246),
            cost = 170, maxHp = 520f, speed = 5f, livesDamage = 12, bountyGold = 30, radius = 3.7f,
            controlImmune = true,
            incomeBonus = 0.30f, unlockRound = 7, cooldownMs = 4000,
            description = "Cannot be slowed, stunned or pushed back."
        ),
        EnemySendType(
            id = "warlord", name = "Warlord", color = Color(0xFF7A4BC2),
            cost = 240, maxHp = 800f, speed = 3.8f, livesDamage = 18, bountyGold = 45, radius = 4.1f,
            hasteAuraPct = 25f, hasteRadius = 14f,
            incomeBonus = 0f, unlockRound = 8, cooldownMs = 6000,
            description = "A boss that drives itself and everyone near it 25% faster."
        ),
        EnemySendType(
            id = "bats", name = "Bats", color = Color(0xFF6B5B95),
            cost = 70, maxHp = 14f, speed = 13f, livesDamage = 1, bountyGold = 2, radius = 1.3f, count = 4,
            flying = true, damageResistancePct = 40f,
            incomeBonus = 0.60f, unlockRound = 4, cooldownMs = 1000,
            description = "Four tiny flyers at once. Hard to hit hard, quick to slip by."
        ),
        EnemySendType(
            id = "bandit", name = "Bandit", color = Color(0xFFC96F4A),
            cost = 65, maxHp = 55f, speed = 11f, livesDamage = 1, bountyGold = 12, radius = 2f,
            stealsIncomeSec = 3f,
            incomeBonus = 0.45f, unlockRound = 4, cooldownMs = 900,
            description = "Quick. If it gets through it runs off with 3 seconds of the defender's income."
        ),
        EnemySendType(
            id = "bubbler", name = "Bubbler", color = Color(0xFF58C7D8),
            cost = 75, maxHp = 70f, speed = 9f, livesDamage = 2, bountyGold = 9, radius = 2.2f,
            shieldHits = 5,
            incomeBonus = 0.50f, unlockRound = 5, cooldownMs = 1000,
            description = "Its bubble swallows the first 5 hits, however hard. Rapid fire pops it fast."
        ),
        EnemySendType(
            id = "sapper", name = "Sapper", color = Color(0xFF3D3A4E),
            cost = 55, maxHp = 34f, speed = 13f, livesDamage = 4, bountyGold = 7, radius = 1.8f,
            incomeBonus = 0.35f, unlockRound = 5, cooldownMs = 1500,
            description = "Fast and fragile, but costs 4 lives if it slips through."
        ),
        EnemySendType(
            id = "jammer", name = "Jammer", color = Color(0xFFF0D84A),
            cost = 80, maxHp = 75f, speed = 9f, livesDamage = 2, bountyGold = 10, radius = 2.2f,
            jamOnDeathMs = 1500, jamRadius = 12f,
            incomeBonus = 0.50f, unlockRound = 6, cooldownMs = 1100,
            description = "When it pops, every tower near it stops firing for a moment."
        ),
        EnemySendType(
            id = "monk", name = "Monk", color = Color(0xFFF2EAD0),
            cost = 75, maxHp = 65f, speed = 8f, livesDamage = 2, bountyGold = 9, radius = 2.1f,
            cleanseRadius = 11f,
            incomeBonus = 0.50f, unlockRound = 6, cooldownMs = 1100,
            description = "Units walking near it shake off slows, poison and curses. It cannot help itself."
        ),
        EnemySendType(
            id = "hydra", name = "Hydra", color = Color(0xFF3F9E6B),
            cost = 110, maxHp = 120f, speed = 7f, livesDamage = 4, bountyGold = 10, radius = 2.9f,
            spawnOnDeathId = "hydra_head", spawnOnDeathCount = 2,
            incomeBonus = 0.55f, unlockRound = 8, cooldownMs = 1300,
            description = "Pop it and two heads walk on. Pop those and each splits again."
        ),
        EnemySendType(
            id = "queen", name = "Queen", color = Color(0xFFE8B23C),
            cost = 150, maxHp = 380f, speed = 5f, livesDamage = 8, bountyGold = 22, radius = 3.5f,
            spawnEveryMs = 2600, spawnEveryId = "swarm",
            incomeBonus = 0.40f, unlockRound = 9, cooldownMs = 3500,
            description = "Lays a Swarm unit every few seconds for as long as she walks."
        ),
        EnemySendType(
            id = "lich", name = "Lich", color = Color(0xFFA7C4A0),
            cost = 230, maxHp = 700f, speed = 4.3f, livesDamage = 16, bountyGold = 42, radius = 4f,
            healPerSecond = 18f, healRadius = 14f,
            incomeBonus = 0f, unlockRound = 9, cooldownMs = 6000,
            description = "A boss that mends itself and everything walking near it."
        ),
        EnemySendType(
            id = "imp", name = "Imp", color = Color(0xFFFF6B4A),
            cost = 60, maxHp = 40f, speed = 9f, livesDamage = 2, bountyGold = 7, radius = 1.8f,
            blinkEveryMs = 3500, blinkDist = 9f,
            incomeBonus = 0.45f, unlockRound = 4, cooldownMs = 800,
            description = "Blinks a short way down the track every few seconds."
        ),
        EnemySendType(
            id = "lancer", name = "Lancer", color = Color(0xFF4A6FE3),
            cost = 70, maxHp = 80f, speed = 7f, livesDamage = 3, bountyGold = 9, radius = 2.2f,
            chargeSpeedPct = 110f,
            incomeBonus = 0.50f, unlockRound = 5, cooldownMs = 1000,
            description = "Charges at more than double speed until the first hit lands on it."
        ),
        EnemySendType(
            id = "tortoise", name = "Tortoise", color = Color(0xFF5F8A6A),
            cost = 85, maxHp = 130f, speed = 6f, livesDamage = 3, bountyGold = 10, radius = 2.6f,
            maxHitPct = 15f,
            incomeBonus = 0.55f, unlockRound = 5, cooldownMs = 1100,
            description = "No single hit takes more than a seventh of its health. Many small hits beat a few big ones."
        ),
        EnemySendType(
            id = "decoy", name = "Decoy", color = Color(0xFFC9A66B),
            cost = 70, maxHp = 170f, speed = 7f, livesDamage = 1, bountyGold = 5, radius = 2.5f,
            taunts = true,
            incomeBonus = 0.45f, unlockRound = 6, cooldownMs = 1200,
            description = "Every tower that can reach it shoots it first. It costs 1 life; what walks behind it costs more."
        ),
        EnemySendType(
            id = "airship", name = "Airship", color = Color(0xFFDB8A5A),
            cost = 120, maxHp = 160f, speed = 6.5f, livesDamage = 5, bountyGold = 12, radius = 3f,
            flying = true, damageResistancePct = 30f,
            spawnOnDeathId = "grunt", spawnOnDeathCount = 3,
            incomeBonus = 0.55f, unlockRound = 7, cooldownMs = 1500,
            description = "A flyer that drops three Grunts where it is shot down."
        ),
        EnemySendType(
            id = "splitling", name = "Splitling", color = Color(0xFFD29BF5),
            cost = 0, maxHp = 22f, speed = 13f, livesDamage = 1, bountyGold = 2, radius = 1.4f,
            sendable = false,
            description = "What is left of a Splitter."
        ),
        EnemySendType(
            id = "hydra_head", name = "Hydra Head", color = Color(0xFF5DBB85),
            cost = 0, maxHp = 50f, speed = 9f, livesDamage = 2, bountyGold = 4, radius = 2f,
            spawnOnDeathId = "hydra_spawn", spawnOnDeathCount = 2,
            sendable = false,
            description = "Half a Hydra, and it splits once more."
        ),
        EnemySendType(
            id = "hydra_spawn", name = "Hydra Spawn", color = Color(0xFF8ED8A8),
            cost = 0, maxHp = 20f, speed = 12f, livesDamage = 1, bountyGold = 2, radius = 1.4f,
            sendable = false,
            description = "The last of a Hydra."
        )
    )

    private val unitsById: Map<String, EnemySendType> = ENEMY_SENDS.associateBy { it.id }
    fun unit(id: String): EnemySendType = unitsById.getValue(id)

    // Every roster has the two basic units and one finisher; the rest is drawn from the pool.
    private val ROSTER_CORE = listOf("runner", "grunt")
    val ROSTER_FINISHERS = listOf("boss", "juggernaut", "warlord", "lich")
    private val ROSTER_POOL: List<EnemySendType> = ENEMY_SENDS.filter {
        it.sendable && it.id !in ROSTER_CORE && it.id !in ROSTER_FINISHERS
    }

    /** The original units, for a match that does not roll its own. */
    val CLASSIC_ROSTER: List<EnemySendType> = listOf(
        "runner", "grunt", "swarm", "drummer", "flyer", "tank", "phantom", "healer", "bulwark", "splitter", "troll", "boss"
    ).map(::unit)

    /** The units both sides can send, and the waves are built from, for one match. */
    fun randomRoster(rng: Random = Random.Default): List<EnemySendType> {
        val picked = ROSTER_CORE.map(::unit) +
            ROSTER_POOL.shuffled(rng).take(ROSTER_SIZE - ROSTER_CORE.size - 1) +
            unit(ROSTER_FINISHERS.random(rng))
        return picked.sortedWith(compareBy<EnemySendType> { it.unlockRound }.thenBy { it.cost })
    }

    // ---------------------------------------------------------------------
    // MAPS — control points in the 100 x 62 lane space. Paths enter off the left edge.
    // Half of all matches use one of these; the other half a generated one (see MapGenerator).
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
        ),
        MapDef(
            id = "swamp", name = "Bog Hook", theme = MapTheme.SWAMP,
            pathPoints = listOf(
                -8f to 30f, 12f to 30f, 24f to 16f, 40f to 11f, 56f to 17f, 62f to 31f,
                54f to 45f, 38f to 50f, 30f to 42f, 36f to 32f, 50f to 31f, 70f to 48f, 91f to 48f
            )
        ),
        MapDef(
            id = "autumn", name = "Maple Run", theme = MapTheme.AUTUMN,
            pathPoints = listOf(
                -8f to 50f, 14f to 50f, 24f to 40f, 22f to 22f, 32f to 11f, 46f to 14f, 50f to 30f,
                46f to 46f, 58f to 52f, 72f to 46f, 74f to 28f, 82f to 14f, 91f to 20f
            )
        ),
        // The next three do not end at the right edge: the keep stands inside the lane.
        MapDef(
            id = "geode", name = "Geode Spiral", theme = MapTheme.CRYSTAL,
            pathPoints = listOf(
                -8f to 11f, 40f to 11f, 78f to 11f, 86f to 17f, 86f to 45f, 78f to 51f,
                22f to 51f, 14f to 45f, 14f to 37f, 21f to 31f, 40f to 31f, 62f to 31f
            )
        ),
        MapDef(
            id = "knot", name = "Moonlit Knot", theme = MapTheme.NIGHT,
            pathPoints = listOf(
                -8f to 14f, 14f to 13f, 30f to 15f, 42f to 24f, 50f to 31f, 58f to 38f, 70f to 47f,
                82f to 44f, 88f to 31f, 82f to 18f, 70f to 15f, 58f to 24f, 50f to 31f, 42f to 38f,
                30f to 47f, 18f to 46f, 12f to 38f
            )
        ),
        MapDef(
            id = "gulch", name = "Horseshoe Gulch", theme = MapTheme.DUNES,
            pathPoints = listOf(
                -8f to 11f, 30f to 11f, 60f to 11f, 78f to 13f, 87f to 22f, 88f to 31f,
                87f to 40f, 78f to 49f, 60f to 51f, 42f to 49f, 32f to 41f
            )
        ),
        MapDef(
            id = "gumdrop", name = "Gumdrop Loop", theme = MapTheme.CANDY,
            pathPoints = listOf(
                -8f to 42f, 20f to 42f, 44f to 42f, 58f to 40f, 67f to 31f, 66f to 18f, 56f to 10f, 44f to 11f,
                37f to 20f, 39f to 33f, 46f to 44f, 56f to 52f, 72f to 52f, 84f to 48f, 91f to 40f
            )
        ),
        MapDef(
            id = "battlements", name = "Old Battlements", theme = MapTheme.RUINS,
            pathPoints = listOf(
                -8f to 48f, 10f to 48f, 16f to 43f, 16f to 17f, 22f to 11f, 32f to 11f, 38f to 17f, 38f to 43f,
                44f to 50f, 54f to 50f, 60f to 43f, 60f to 17f, 66f to 11f, 76f to 11f, 82f to 17f, 83f to 34f, 91f to 44f
            )
        ),
        MapDef(
            id = "halo", name = "Cloud Halo", theme = MapTheme.SKY,
            pathPoints = listOf(
                -8f to 31f, 14f to 31f, 32f to 19f, 50f to 9f, 68f to 19f, 86f to 31f, 68f to 43f,
                50f to 53f, 38f to 46f, 30f to 37f
            )
        ),
        MapDef(
            id = "crossing", name = "Mire Crossing", theme = MapTheme.SWAMP,
            pathPoints = listOf(
                -8f to 31f, 20f to 31f, 50f to 31f, 68f to 29f, 76f to 20f, 66f to 11f, 46f to 10f, 32f to 14f,
                28f to 24f, 28f to 38f, 34f to 49f, 50f to 52f, 72f to 52f, 91f to 48f
            )
        ),
        MapDef(
            id = "summit", name = "Twin Summits", theme = MapTheme.SKY,
            pathPoints = listOf(
                -8f to 50f, 12f to 50f, 22f to 36f, 28f to 14f, 36f to 10f, 44f to 16f, 48f to 34f,
                52f to 48f, 60f to 50f, 66f to 34f, 70f to 14f, 78f to 10f, 86f to 18f, 91f to 34f
            )
        )
    )

    // ---------------------------------------------------------------------
    // RULES — one is rolled per match, sometimes two.
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
        ),
        MatchModifier(
            id = "bounty_boom", name = "Bounty Boom",
            description = "Every popped unit pays double.", bountyMultiplier = 2f
        ),
        MatchModifier(
            id = "thick_skin", name = "Thick Skin",
            description = "Every unit has 35% more health.", unitHpMultiplier = 1.35f
        ),
        MatchModifier(
            id = "war_economy", name = "War Economy",
            description = "Sending units raises income twice as much.", sendIncomeMultiplier = 2f
        ),
        MatchModifier(
            id = "quick_march", name = "Quick March",
            description = "A wave comes every 15 seconds instead of 20.", roundIntervalSec = 15
        ),
        MatchModifier(
            id = "rapid_fire", name = "Rapid Fire",
            description = "All towers fire 20% faster.", reloadMultiplier = 0.8f
        ),
        MatchModifier(
            id = "marathon", name = "Marathon",
            description = "A 10-minute match before sudden death.", matchDurationOverrideSec = 600
        ),
        MatchModifier(
            id = "featherweight", name = "Featherweight",
            description = "Units are 25% faster but have 25% less health.",
            speedMultiplier = 1.25f, unitHpMultiplier = 0.75f
        ),
        MatchModifier(
            id = "head_start", name = "Head Start",
            description = "Both sides start with 200 extra gold.", startingGoldBonus = 200
        ),
        MatchModifier(
            id = "heavy_hitters", name = "Heavy Hitters",
            description = "Towers hit 30% harder but fire 15% slower.",
            damageMultiplier = 1.3f, reloadMultiplier = 1.15f
        ),
        MatchModifier(
            id = "mirror", name = "Mirror Match",
            description = "Your rival is offered the same towers you are.", mirrorDraft = true
        ),
        MatchModifier(
            id = "horde", name = "Horde",
            description = "Every wave is half as big again.", waveSizeMultiplier = 1.5f
        ),
        MatchModifier(
            id = "full_refund", name = "Full Refund",
            description = "Towers sell for everything they cost.", sellRefundFraction = 1f
        ),
        MatchModifier(
            id = "wild_weather", name = "Wild Weather",
            description = "Events strike twice as often.", eventGapMultiplier = 0.5f
        ),
        MatchModifier(
            id = "open_gates", name = "Open Gates",
            description = "Sends are ready again twice as fast.", sendCooldownMultiplier = 0.5f
        ),
        MatchModifier(
            id = "knife_edge", name = "Knife Edge",
            description = "Both sides start with only 40 lives.", livesOverride = 40
        ),
        MatchModifier(
            id = "molasses", name = "Molasses",
            description = "Units are 20% slower but have 30% more health.",
            speedMultiplier = 0.8f, unitHpMultiplier = 1.3f
        ),
        MatchModifier(
            id = "long_shot", name = "Long Shot",
            description = "Towers reach 30% further but fire 15% slower.",
            rangeMultiplier = 1.3f, reloadMultiplier = 1.15f
        ),
        MatchModifier(
            id = "lean_times", name = "Lean Times",
            description = "Income is cut 25%, but popped units pay 60% more.",
            incomeMultiplier = 0.75f, bountyMultiplier = 1.6f
        ),
        MatchModifier(
            id = "battle_hardened", name = "Battle Hardened",
            description = "Every tower is built with its first upgrade already on it.", freeTowerLevels = 1
        ),
        MatchModifier(
            id = "second_chance", name = "Second Chance",
            description = "The first time a keep falls, it stands back up with 25 lives.", revives = 1
        ),
        MatchModifier(
            id = "sprint", name = "Sprint",
            description = "A 5-minute match, and both sides start with 60 extra gold.",
            matchDurationOverrideSec = 300, startingGoldBonus = 60
        ),
        MatchModifier(
            id = "double_time", name = "Double Time",
            description = "Units move 15% faster and towers fire 15% faster.",
            speedMultiplier = 1.15f, reloadMultiplier = 0.87f
        ),
        MatchModifier(
            id = "big_spender", name = "Big Spender",
            description = "Both sides start with 300 extra gold, but income is cut 20%.",
            startingGoldBonus = 300, incomeMultiplier = 0.8f
        ),
        MatchModifier(
            id = "elite_waves", name = "Elite Waves",
            description = "Waves are a third smaller, but every unit has 40% more health.",
            waveSizeMultiplier = 0.67f, unitHpMultiplier = 1.4f
        )
    )

    /** A match with no rule at all: headless runs and the menu's demo. */
    val NO_RULE = MatchModifier(id = "none", name = "No rule", description = "")

    /** One rule, or now and then two that do not contradict each other. */
    fun randomRules(rng: Random = Random.Default): List<MatchModifier> {
        val first = MODIFIERS.random(rng)
        if (rng.nextFloat() >= SECOND_RULE_CHANCE) return listOf(first)
        val second = MODIFIERS.filter { it.compatibleWith(first) }.random(rng)
        return listOf(first, second)
    }

    // ---------------------------------------------------------------------
    // RIVALS — who the AI is this match. The personality decides how it plays.
    // ---------------------------------------------------------------------
    val RIVALS: List<Rival> = listOf(
        Rival(
            "dash", "Sgt. Dash", AiPersonality.RUSHER, "runner",
            RivalLines(
                start = listOf("No time to build. Go, go, go!", "Hope you placed something already."),
                push = listOf("Incoming! Hope you are awake!", "Faster than you can count them."),
                hurt = listOf("Ow! Lucky shot.", "That one slipped past me."),
                gloat = listOf("Too slow!", "Blink and they are through."),
                win = "Outrun again.", lose = "I... need to catch my breath."
            )
        ),
        Rival(
            "mossback", "Old Mossback", AiPersonality.TURTLE, "troll",
            RivalLines(
                start = listOf("Take your time. I will.", "Walls first. Then we talk."),
                push = listOf("Now. All of it.", "I saved these for you."),
                hurt = listOf("A scratch on the shell.", "Hm. Noted."),
                gloat = listOf("Walls win wars.", "Slow and steady."),
                win = "Patience pays.", lose = "Hm. A crack after all."
            )
        ),
        Rival(
            "penny", "Penny Vault", AiPersonality.TYCOON, "healer",
            RivalLines(
                start = listOf("Every send is an investment.", "Let us see who is richer in a minute."),
                push = listOf("Paid in full.", "I can afford this. Can you?"),
                hurt = listOf("An acceptable loss.", "That will come out of your share."),
                gloat = listOf("Compound interest.", "Money talks."),
                win = "A profitable match.", lose = "The market turned on me."
            )
        ),
        Rival(
            "even", "The Even Hand", AiPersonality.BALANCED, "grunt",
            RivalLines(
                start = listOf("Show me how you play.", "I will match whatever you do."),
                push = listOf("Your move was noted. Here is mine.", "Balance must be kept."),
                hurt = listOf("Well played.", "I will adjust."),
                gloat = listOf("You left a gap.", "As expected."),
                win = "A fair result.", lose = "You tipped the scales."
            )
        ),
        Rival(
            "buzz", "Queen Buzz", AiPersonality.SWARMER, "swarm",
            RivalLines(
                start = listOf("One of us is many.", "Count them. I dare you."),
                push = listOf("Swarm!", "There are always more."),
                hurt = listOf("Just a few of us.", "Bzz. Rude."),
                gloat = listOf("Too many for you?", "The hive is pleased."),
                win = "The hive wins.", lose = "We will be back. All of us."
            )
        ),
        Rival(
            "bulk", "Baron Bulk", AiPersonality.BRUISER, "tank",
            RivalLines(
                start = listOf("I only bring the big ones.", "Small units are beneath me."),
                push = listOf("Make way.", "Try stopping this."),
                hurt = listOf("Barely felt it.", "Hmph."),
                gloat = listOf("Heavy is good.", "Crushed."),
                win = "Flattened.", lose = "Even mountains fall, I suppose."
            )
        ),
        Rival(
            "seven", "Lucky Seven", AiPersonality.GAMBLER, "splitter",
            RivalLines(
                start = listOf("Feeling lucky?", "I have not decided what to do. Fun, no?"),
                push = listOf("All in!", "Let it ride!"),
                hurt = listOf("Bad roll.", "The dice owe me one."),
                gloat = listOf("Jackpot!", "Told you I was lucky."),
                win = "The house wins.", lose = "Double or nothing?"
            )
        ),
        Rival(
            "misty", "Misty", AiPersonality.TRICKSTER, "phantom",
            RivalLines(
                start = listOf("Watch closely.", "Nothing up my sleeve."),
                push = listOf("Look over here.", "Now you see them."),
                hurt = listOf("That was the decoy.", "Clever. Annoying, but clever."),
                gloat = listOf("Now you do not.", "You watched the wrong hand."),
                win = "Ta-da.", lose = "You saw through it."
            )
        ),
        Rival(
            "gale", "Captain Gale", AiPersonality.RUSHER, "flyer",
            RivalLines(
                start = listOf("Clear skies. Good day for a raid.", "Look up."),
                push = listOf("Squadron, dive!", "Coming in fast."),
                hurt = listOf("Turbulence.", "We lost a wing."),
                gloat = listOf("Right over your head.", "No flak? Lovely."),
                win = "Mission complete.", lose = "Grounded. For now."
            )
        ),
        Rival(
            "crimson", "King Crimson", AiPersonality.BRUISER, "boss",
            RivalLines(
                start = listOf("Kneel now and save us both the time.", "A king does not hurry."),
                push = listOf("The crown marches.", "Bow."),
                hurt = listOf("You dare?", "Insolence."),
                gloat = listOf("As it should be.", "Your keep looks tired."),
                win = "Long live me.", lose = "This is not abdication. It is a pause."
            )
        ),
        Rival(
            "ledger", "Madame Ledger", AiPersonality.TYCOON, "drummer",
            RivalLines(
                start = listOf("I keep the beat and the books.", "Tempo is everything."),
                push = listOf("And... march.", "On my count."),
                hurt = listOf("Off beat.", "A wrong note."),
                gloat = listOf("Right on time.", "You are behind the beat."),
                win = "Perfect rhythm.", lose = "The band plays on without me."
            )
        ),
        Rival(
            "shell", "Sir Shellby", AiPersonality.TURTLE, "bulwark",
            RivalLines(
                start = listOf("Shields up.", "Come and knock."),
                push = listOf("Advance behind the shields.", "Hold the line. Forward."),
                hurt = listOf("A dent.", "The line bends."),
                gloat = listOf("Nothing gets through.", "Your shots bounce."),
                win = "The line held.", lose = "Outflanked."
            )
        ),
        Rival(
            "flutter", "Count Flutter", AiPersonality.OPPORTUNIST, "bats",
            RivalLines(
                start = listOf("I only bite when you are not looking.", "Spend freely. Please."),
                push = listOf("Your purse is empty. How delicious.", "Now, while you are short."),
                hurt = listOf("A nick. I have had worse.", "Hiss."),
                gloat = listOf("You should have kept some gold.", "Drained."),
                win = "A fine vintage.", lose = "Daylight. Curses."
            )
        ),
        Rival(
            "fingers", "Sticky Fingers", AiPersonality.OPPORTUNIST, "bandit",
            RivalLines(
                start = listOf("Nice purse. Shame if it got lighter.", "I am only here for the gold."),
                push = listOf("Hands up!", "This is a stick-up."),
                hurt = listOf("Hey, that was mine!", "Easy, easy."),
                gloat = listOf("Finders keepers.", "Thanks for the tip."),
                win = "Pleasure doing business.", lose = "Fine. Keep it."
            )
        ),
        Rival(
            "hank", "Hydra Hank", AiPersonality.AVALANCHE, "hydra",
            RivalLines(
                start = listOf("We start small.", "One head now. More later."),
                push = listOf("Bigger than the last one.", "Count the heads this time."),
                hurt = listOf("Cut one off. Go on.", "That only makes more of us."),
                gloat = listOf("It keeps growing.", "Two more where that came from."),
                win = "All heads agree: we won.", lose = "We ran out of heads."
            )
        ),
        Rival(
            "grim", "Warlord Grim", AiPersonality.AVALANCHE, "warlord",
            RivalLines(
                start = listOf("A skirmish first. Then the war.", "Each charge harder than the last."),
                push = listOf("Again. Harder!", "Forward, all of you!"),
                hurt = listOf("A flesh wound.", "Is that your best?"),
                gloat = listOf("The next one will be worse.", "Your walls are tired."),
                win = "The banner flies.", lose = "A retreat. Not a defeat."
            )
        ),
        Rival(
            "static", "Dr. Static", AiPersonality.TRICKSTER, "jammer",
            RivalLines(
                start = listOf("Have you tried turning it off?", "Let us run an experiment."),
                push = listOf("Lights out.", "Watch your towers."),
                hurt = listOf("An unexpected result.", "Noted in the log."),
                gloat = listOf("Zzzap.", "Was that tower important?"),
                win = "Hypothesis confirmed.", lose = "Back to the lab."
            )
        ),
        Rival(
            "fuse", "Short Fuse", AiPersonality.GAMBLER, "sapper",
            RivalLines(
                start = listOf("Tick, tick, tick.", "I do not do patience."),
                push = listOf("Fire in the hole!", "Boom time!"),
                hurt = listOf("Too early!", "That one was a dud."),
                gloat = listOf("Kaboom.", "Did you hear that?"),
                win = "What a blast.", lose = "Fizzled out."
            )
        ),
        Rival(
            "granny", "Granny Shell", AiPersonality.TURTLE, "tortoise",
            RivalLines(
                start = listOf("No rush, dear.", "I have all day. Do you?"),
                push = listOf("Off you go, slowly now.", "Mind the shells."),
                hurt = listOf("Oh, that tickled.", "Careful, I bruise."),
                gloat = listOf("Slow gets there too.", "Told you there was no rush."),
                win = "Home in time for tea.", lose = "Well. I never."
            )
        ),
        Rival(
            "pip", "Pip The Imp", AiPersonality.SWARMER, "imp",
            RivalLines(
                start = listOf("Now you see me!", "Catch me, catch me."),
                push = listOf("Here! No, here!", "Blink and we are past."),
                hurt = listOf("Missed! Mostly.", "Ow, ow, ow."),
                gloat = listOf("Too slow!", "Were you aiming at something?"),
                win = "Hee hee hee.", lose = "No fair, you were looking."
            )
        ),
        Rival(
            "zeppo", "Admiral Zeppo", AiPersonality.BRUISER, "airship",
            RivalLines(
                start = listOf("All hands aloft.", "The fleet is airborne."),
                push = listOf("Drop the cargo!", "Full ballast ahead."),
                hurt = listOf("We are losing gas.", "Patch that envelope!"),
                gloat = listOf("Delivered, on schedule.", "Look out below."),
                win = "A smooth landing.", lose = "Abandon ship."
            )
        ),
        Rival(
            "gallop", "Sir Gallop", AiPersonality.RUSHER, "lancer",
            RivalLines(
                start = listOf("Lances down!", "A charge settles everything."),
                push = listOf("Chaaarge!", "Do not stop for anything."),
                hurt = listOf("A scratch on the plate.", "Unhorsed, briefly."),
                gloat = listOf("Straight through.", "You never touched us."),
                win = "For glory!", lose = "The horse was tired."
            )
        ),
        Rival(
            "sam", "Strawman Sam", AiPersonality.TRICKSTER, "decoy",
            RivalLines(
                start = listOf("Shoot me. Go on.", "I am very distracting."),
                push = listOf("Look at me, not at them.", "Eyes on the hat."),
                hurt = listOf("Just straw.", "That was meant to happen."),
                gloat = listOf("You shot the wrong one.", "Fell for it."),
                win = "Stuffed you.", lose = "I have been seen through."
            )
        ),
        Rival(
            "pale", "The Pale King", AiPersonality.BALANCED, "lich",
            RivalLines(
                start = listOf("Nothing of mine stays dead.", "I have waited longer than this."),
                push = listOf("Rise. Walk.", "Mend, and march on."),
                hurt = listOf("It will knit.", "A wound is a small thing."),
                gloat = listOf("We outlast you.", "Your towers tire. We do not."),
                win = "As it was written.", lose = "I can wait for the next one."
            )
        )
    )

    /** True if [hand] holds a tower that can actually kill things on its own. */
    fun hasDamageDealer(hand: List<TroopType>): Boolean = hand.any { it.baseDps >= MIN_DRAFT_DPS }

    /**
     * Deals [count] towers, rerolling hands with too few real damage dealers so every draft can
     * hold a lane. A full offer needs two, so that which dealer to take is still a choice.
     */
    fun randomDraft(count: Int = DRAFT_OFFER, rng: Random = Random.Default): List<TroopType> {
        val dealersNeeded = if (count > DRAFT_PICKS) 2 else 1
        while (true) {
            val draft = TROOPS.shuffled(rng).take(count)
            if (draft.count { it.baseDps >= MIN_DRAFT_DPS } >= dealersNeeded) return draft
        }
    }
}
