package com.towerduel.game.engine

import com.towerduel.game.data.AiPersonality
import com.towerduel.game.data.Difficulty
import com.towerduel.game.data.EnemySendType
import com.towerduel.game.data.GameData
import com.towerduel.game.data.LaneSpace
import com.towerduel.game.data.ShotKind
import com.towerduel.game.data.TroopType
import com.towerduel.game.data.UpgradeTier
import kotlin.math.sqrt
import kotlin.random.Random

/** Seconds a wave spends under fire, give or take; turns "wave health" into "damage per second needed". */
private const val EXPOSURE_SEC = 11f
private const val EMERGENCY_SEC = 5f
private const val GRID_STEP = 3f
private const val GRID_MARGIN = 5f
private const val PUSH_THINK_MS = 300f

/** A push is sized to out-last the defense it is sent at by this much. */
private const val BREAK_MARGIN = 1.1f
private const val MAX_PUSH_GOLD = 1500f

/** A pulse tower hits everything in range, so its damage counts several times over. */
private const val PULSE_TARGETS = 2.5f

/**
 * Plays one side of a match. It works only from what a player could see: both lanes, both purses,
 * the round clock. It thinks on its own cadence (not every frame), so it makes discrete choices
 * instead of reacting with frame-perfect reflexes.
 *
 * Each think it sizes up its defense against the next wave and whatever is already walking its
 * lane. If the defense is short it buys the best-value tower or upgrade, saving up if it has to.
 * Otherwise the gold goes to income (Gold Mines, cheap sends) or is banked for a push.
 */
class AiController(
    private val personality: AiPersonality,
    private val difficulty: Difficulty,
    private val rng: Random = Random.Default
) {
    private class Skill(
        val thinkMs: Float,
        val hesitate: Float,
        /** Share of the ranked placement spots it picks from; 0 means always the best one. */
        val sloppiness: Float,
        /** Chance that a choice is the calculated best one instead of a random reasonable one. */
        val sharpness: Float,
        val reactsToLeaks: Boolean,
        val timesPushes: Boolean,
        val pushScale: Float
    )

    private val skill = when (difficulty) {
        Difficulty.EASY -> Skill(2300f, 0.30f, 0.6f, 0.1f, reactsToLeaks = false, timesPushes = false, pushScale = 0.7f)
        Difficulty.MEDIUM -> Skill(1250f, 0.10f, 0.15f, 0.6f, reactsToLeaks = true, timesPushes = false, pushScale = 1f)
        Difficulty.HARD -> Skill(650f, 0.02f, 0f, 1f, reactsToLeaks = true, timesPushes = true, pushScale = 1f)
    }

    /** How much more defense than the bare minimum this personality wants before it attacks. */
    private val safety = when (personality) {
        AiPersonality.TURTLE -> 1.35f
        AiPersonality.BALANCED -> 1.05f
        AiPersonality.TYCOON -> 0.95f
        AiPersonality.RUSHER -> 0.85f
    }

    /** Chance to attack anyway while the defense is still short. */
    private val recklessness = when (personality) {
        AiPersonality.RUSHER -> 0.45f
        AiPersonality.BALANCED -> 0.2f
        AiPersonality.TYCOON -> 0.2f
        AiPersonality.TURTLE -> 0.08f
    }

    /** Share of spare gold that goes into cheap income sends while those can still pay back. */
    private val ecoShare = when (personality) {
        AiPersonality.TYCOON -> 0.8f
        AiPersonality.BALANCED -> 0.4f
        AiPersonality.TURTLE -> 0.25f
        AiPersonality.RUSHER -> 0f
    }

    private val reserveGold = when (personality) {
        AiPersonality.TURTLE -> 60f
        AiPersonality.RUSHER -> 0f
        else -> 30f
    }

    private val maxMines = when (personality) {
        AiPersonality.TYCOON -> 3
        AiPersonality.RUSHER -> 1
        else -> 2
    }

    private var thinkTimerMs = 700f + rng.nextFloat() * 600f
    private var lastLives = -1

    /** Rises when lives are lost and decays over time: the lane is leaking, so over-build for a while. */
    private var defenseBias = 0f

    // Each push is sized from a fresh roll, so the opponent cannot learn the rhythm.
    private var pushRoll = rng.nextFloat()
    private var pushCount = 0
    private var pushBudget = 0f
    private var pushing = false
    private var lastHeavySendAtMs = -100_000f

    // What has been walking this lane lately; steers which towers are worth more.
    private var seenFlying = 0f
    private var seenSwarm = 0f
    private var seenHeavy = 0f

    // Grid of spots that are clear of the track. The track never changes, so this is built once.
    private var gridX = FloatArray(0)
    private var gridY = FloatArray(0)
    private var gridReady = false

    private class Purchase(
        val type: TroopType?,
        val tower: TowerInstance?,
        val x: Float,
        val y: Float,
        val cost: Int,
        val value: Float
    )

    private class Spot(val x: Float, val y: Float, val score: Float)

    fun update(dtSeconds: Float, engine: GameEngine, own: Battlefield, foe: Battlefield) {
        thinkTimerMs -= dtSeconds * 1000f
        defenseBias = (defenseBias - 0.02f * dtSeconds).coerceAtLeast(0f)
        if (lastLives < 0) lastLives = own.lives
        if (own.lives < lastLives) {
            defenseBias = (defenseBias + 0.08f * (lastLives - own.lives)).coerceAtMost(1.2f)
            lastLives = own.lives
        }
        if (thinkTimerMs > 0f) return
        thinkTimerMs = skill.thinkMs * (0.8f + rng.nextFloat() * 0.5f)
        if (engine.outcome != MatchOutcome.ONGOING) return
        if (rng.nextFloat() < skill.hesitate) return // hesitates and wastes the beat

        observeLane(own)
        think(engine, own, foe)
    }

    private fun think(engine: GameEngine, own: Battlefield, foe: Battlefield) {
        val power = lanePower(engine, own)
        val need = requiredPower(engine, own, foe)
        val emergency = skill.reactsToLeaks && deepThreat(own) > power * EMERGENCY_SEC

        when {
            emergency -> defend(engine, own, urgent = true)
            pushing -> continuePush(engine, own, foe)
            power < need && rng.nextFloat() >= recklessness * (power / need) -> {
                // Nothing left to buy means the gold is better spent attacking.
                if (!defend(engine, own, urgent = false)) invest(engine, own, foe)
            }
            else -> invest(engine, own, foe)
        }
    }

    // -------------------------------------------------------------------
    // Reading the lane
    // -------------------------------------------------------------------

    private fun observeLane(own: Battlefield) {
        seenFlying *= 0.92f
        seenSwarm *= 0.92f
        seenHeavy *= 0.92f
        for (e in own.incomingEnemies) {
            when {
                e.type.flying -> seenFlying += 0.5f
                e.maxHp >= 200f -> seenHeavy += 0.5f
                e.maxHp <= 25f -> seenSwarm += 0.25f
            }
        }
    }

    /** Health of everything already past the middle of the lane: what is about to leak. */
    private fun deepThreat(own: Battlefield): Float {
        var hp = 0f
        for (e in own.incomingEnemies) if (e.progress > 0.5f) hp += e.hp * e.type.livesDamage.coerceAtMost(4)
        return hp
    }

    private fun requiredPower(engine: GameEngine, own: Battlefield, foe: Battlefield): Float {
        var walking = 0f
        for (e in own.incomingEnemies) walking += e.hp
        for (p in own.pendingSpawns) walking += p.type.maxHp * p.hpScale
        var hp = maxOf(engine.waveHp(engine.round + 1), walking)
        // A sharp player counts the opponent's purse as a push that has not been sent yet.
        if (skill.timesPushes) hp += foe.gold * 0.8f
        return hp / EXPOSURE_SEC * safety * (1f + defenseBias)
    }

    private fun lanePower(engine: GameEngine, field: Battlefield): Float {
        var sum = 0f
        for (t in field.towers) sum += towerPower(engine, t)
        return sum
    }

    private fun towerPower(engine: GameEngine, t: TowerInstance): Float = power(
        t.type, engine.shotDamage(t), t.shots, t.reloadMs, t.splashRadius, t.chains, t.slow, t.dotDps, t.stunChance,
        engine.path.coverage(t.x, t.y, engine.towerReach(t), LaneSpace.WIDTH)
    )

    /** A rough "damage per second this tower is worth", counting splash, chains and control. */
    private fun power(
        type: TroopType, damage: Float, shots: Int, reloadMs: Float, splash: Float, chains: Int,
        slow: Float, dot: Float, stun: Float, coverage: Float
    ): Float {
        if (!type.isAttacker) return 0f
        var dps = damage * shots * 1000f / reloadMs
        if (splash > 0f) dps *= 1f + splash / 9f
        if (chains > 0) dps *= 1f + 0.5f * chains
        when (type.shot) {
            ShotKind.FROST_PULSE -> dps = dps * PULSE_TARGETS + slow * 45f
            ShotKind.POISON_PULSE -> dps = (dps + dot) * PULSE_TARGETS
            else -> Unit
        }
        dps += stun * 14f
        // A tower only works while something is in range, so more track covered means more uptime.
        return dps * (coverage / 30f).coerceIn(0.2f, 1.6f)
    }

    // -------------------------------------------------------------------
    // Defense: towers and upgrades
    // -------------------------------------------------------------------

    /** Buys (or saves for) the best defensive purchase. False if there is nothing left to buy. */
    private fun defend(engine: GameEngine, own: Battlefield, urgent: Boolean): Boolean {
        val options = defenseOptions(engine, own, urgent)
        if (options.isEmpty()) return false
        options.sortByDescending { it.value }

        var choice = when {
            rng.nextFloat() < skill.sharpness -> options[0]
            else -> options[rng.nextInt(options.size.coerceAtMost(3))]
        }
        if (choice.cost > own.gold) {
            // Under fire there is no time to save: take the best thing the purse allows right now.
            if (!urgent) return true
            choice = options.firstOrNull { it.cost <= own.gold } ?: return true
        }
        buy(engine, own, choice)
        return true
    }

    private fun buy(engine: GameEngine, own: Battlefield, purchase: Purchase) {
        val tower = purchase.tower
        val type = purchase.type
        if (tower != null) engine.upgradeTower(own, tower.instanceId)
        else if (type != null) engine.placeTower(own, type, purchase.x, purchase.y)
    }

    private fun defenseOptions(engine: GameEngine, own: Battlefield, lateBias: Boolean): ArrayList<Purchase> {
        val out = ArrayList<Purchase>()
        val powers = FloatArray(own.towers.size) { towerPower(engine, own.towers[it]) }

        if (own.towers.size < GameData.MAX_TOWERS_PER_LANE) {
            val cover = coverCounts(engine, own)
            for (type in own.draftedTroops) {
                if (type.incomeBonusPerSecond > 0f) continue // economy, bought in invest()
                val spot = bestSpot(engine, own, type, cover, powers, lateBias) ?: continue
                val gain = if (type.auraRange > 0f) {
                    spot.score * type.auraDamageBonusPct / 100f
                } else {
                    power(
                        type, type.damage * engine.modifier.damageMultiplier, 1, type.fireRateMs.toFloat(),
                        type.splashRadius, type.chainTargets, type.slowFactor, type.dotDamagePerSecond, type.stunChance,
                        engine.path.coverage(spot.x, spot.y, engine.baseReach(type), LaneSpace.WIDTH)
                    )
                }
                if (gain > 0f) out.add(Purchase(type, null, spot.x, spot.y, type.cost, gain / type.cost * mixWeight(type)))
            }
        }

        for ((i, t) in own.towers.withIndex()) {
            val tier = t.nextUpgrade ?: continue
            if (t.income > 0f) continue
            val gain = upgradeGain(engine, own, t, tier, powers, powers[i])
            if (gain > 0f) out.add(Purchase(null, t, t.x, t.y, tier.cost, gain / tier.cost * mixWeight(t.type)))
        }
        return out
    }

    private fun upgradeGain(
        engine: GameEngine, own: Battlefield, t: TowerInstance, tier: UpgradeTier, powers: FloatArray, current: Float
    ): Float {
        if (t.auraPct > 0f) {
            var boosted = 0f
            for ((i, other) in own.towers.withIndex()) {
                if (other !== t && dist(other.x, other.y, t.x, t.y) <= t.auraRange) boosted += powers[i]
            }
            return boosted * t.auraPct * (tier.effectMult - 1f) / 100f
        }
        val upgraded = power(
            t.type, engine.shotDamage(t) * tier.damageMult, t.shots + tier.extraShots, t.reloadMs * tier.reloadMult,
            t.splashRadius * tier.splashMult, t.chains + tier.extraChains, t.slow * tier.effectMult,
            t.dotDps * tier.effectMult, t.stunChance * tier.effectMult,
            engine.path.coverage(t.x, t.y, engine.towerReach(t) * tier.rangeMult, LaneSpace.WIDTH)
        )
        return upgraded - current
    }

    /** Values a tower type by what has been attacking this lane. A sloppy AI ignores the mix. */
    private fun mixWeight(type: TroopType): Float {
        if (rng.nextFloat() >= skill.sharpness) return 0.7f + rng.nextFloat() * 0.6f
        var w = 1f
        if (type.bonusDamageVsFlyerPct > 0f) w *= 0.8f + (seenFlying / 3f).coerceAtMost(1.2f)
        val hitsMany = type.splashRadius > 0f || type.chainTargets > 0 ||
            type.shot == ShotKind.FROST_PULSE || type.shot == ShotKind.POISON_PULSE
        if (hitsMany) w *= 1f + (seenSwarm / 10f).coerceAtMost(0.6f)
        if (type.damage >= 40f && type.splashRadius == 0f) w *= 1f + (seenHeavy / 2f).coerceAtMost(0.6f)
        return w
    }

    // -------------------------------------------------------------------
    // Placement
    // -------------------------------------------------------------------

    private fun buildGrid(engine: GameEngine) {
        val xs = ArrayList<Float>()
        val ys = ArrayList<Float>()
        val path = engine.path
        val endX = path.xs[path.pointCount - 1]
        val endY = path.ys[path.pointCount - 1]
        var y = GRID_MARGIN
        while (y <= LaneSpace.HEIGHT - GRID_MARGIN) {
            var x = GRID_MARGIN
            while (x <= LaneSpace.WIDTH - GRID_MARGIN) {
                // A little slack over the engine's own rules, so a chosen spot is never rejected.
                if (path.distanceTo(x, y) >= GameData.PATH_CLEARANCE + 0.2f && dist(x, y, endX, endY) >= 9.5f) {
                    xs.add(x)
                    ys.add(y)
                }
                x += GRID_STEP
            }
            y += GRID_STEP
        }
        gridX = xs.toFloatArray()
        gridY = ys.toFloatArray()
        gridReady = true
    }

    /** For every other point of the track: how many of this side's attackers can reach it. */
    private fun coverCounts(engine: GameEngine, own: Battlefield): FloatArray {
        val path = engine.path
        val cover = FloatArray(path.pointCount)
        for (t in own.towers) {
            if (!t.type.isAttacker) continue
            val reach = engine.towerReach(t)
            val r2 = reach * reach
            var i = 0
            while (i < path.pointCount) {
                val dx = path.xs[i] - t.x
                val dy = path.ys[i] - t.y
                if (dx * dx + dy * dy <= r2) cover[i] += 1f
                i += 2
            }
        }
        return cover
    }

    private fun bestSpot(
        engine: GameEngine, own: Battlefield, type: TroopType,
        cover: FloatArray, powers: FloatArray, lateBias: Boolean
    ): Spot? {
        if (!gridReady) buildGrid(engine)
        val spots = ArrayList<Spot>()
        for (g in gridX.indices) {
            val x = gridX[g]
            val y = gridY[g]
            var free = true
            for (t in own.towers) {
                if (dist(t.x, t.y, x, y) < GameData.MIN_TOWER_SPACING + 0.2f) {
                    free = false
                    break
                }
            }
            if (free) spots.add(Spot(x, y, spotScore(engine, own, type, x, y, cover, powers, lateBias)))
        }
        if (spots.isEmpty()) return null
        spots.sortByDescending { it.score }
        // A Gold Mine works anywhere; anything else needs a spot where it actually does something.
        if (type.incomeBonusPerSecond <= 0f && spots[0].score <= 0f) return null
        val pool = (spots.size * skill.sloppiness).toInt().coerceAtLeast(1)
        return spots[rng.nextInt(pool)]
    }

    private fun spotScore(
        engine: GameEngine, own: Battlefield, type: TroopType, x: Float, y: Float,
        cover: FloatArray, powers: FloatArray, lateBias: Boolean
    ): Float {
        val path = engine.path
        // Support auras go where the firepower already is.
        if (type.auraRange > 0f) {
            var boosted = 0f
            for ((i, t) in own.towers.withIndex()) {
                if (dist(t.x, t.y, x, y) <= type.auraRange) boosted += powers[i]
            }
            return boosted
        }
        // A Gold Mine should not take a spot a real tower could use.
        if (type.incomeBonusPerSecond > 0f) return -path.coverage(x, y, 16f, LaneSpace.WIDTH)

        val reach = engine.baseReach(type)
        val r2 = reach * reach
        val pulse = type.shot == ShotKind.FROST_PULSE || type.shot == ShotKind.POISON_PULSE
        val lateFrom = (path.pointCount * 0.55f).toInt()
        var score = 0f
        var i = 0
        while (i < path.pointCount) {
            val px = path.xs[i]
            if (px in 0f..LaneSpace.WIDTH) {
                val dx = px - x
                val dy = path.ys[i] - y
                if (dx * dx + dy * dy <= r2) {
                    // Slows and poison stack with other towers' fire; everything else spreads out
                    // to track that nobody is covering yet.
                    var w = if (pulse) 1f + 0.4f * cover[i] else 1f / (1f + 0.6f * cover[i])
                    if (lateBias && i >= lateFrom) w *= 2f
                    score += w
                }
            }
            i += 2
        }
        // Long-range towers reach the track from anywhere, so they should leave the prime spots free.
        if (reach >= 35f) score -= 0.05f * path.coverage(x, y, 12f, LaneSpace.WIDTH)
        return score
    }

    // -------------------------------------------------------------------
    // Economy and offense
    // -------------------------------------------------------------------

    private fun invest(engine: GameEngine, own: Battlefield, foe: Battlefield) {
        val ecoWindow = engine.timeRemainingSec() > 60
        if (ecoWindow && buyMine(engine, own)) return

        val spare = own.gold - reserveGold
        val pushTarget = pushTarget(engine, foe)
        if (!pushing) {
            val goodMoment = !skill.timesPushes ||
                engine.elapsedMs - engine.roundStartedAtMs < 4000f || spare >= pushTarget * 1.5f
            if (spare >= pushTarget && goodMoment) {
                pushing = true
                pushBudget = spare
            } else if (ecoWindow && rng.nextFloat() < ecoShare) {
                sendForIncome(engine, own, foe, spare)
                return
            } else if (engine.suddenDeath || spare > pushTarget * 2f) {
                // Nothing better to do with a full purse than more defense.
                defend(engine, own, urgent = false)
                return
            }
        }
        if (pushing) continuePush(engine, own, foe)
    }

    /** Builds or upgrades a Gold Mine if one is drafted and still worth it. True if this think is spent on it. */
    private fun buyMine(engine: GameEngine, own: Battlefield): Boolean {
        val mineType = own.draftedTroops.firstOrNull { it.incomeBonusPerSecond > 0f } ?: return false
        val mines = own.towers.filter { it.income > 0f }
        val secondsLeft = engine.timeRemainingSec().toFloat()

        val upgradable = mines.firstOrNull { it.nextUpgrade != null }
        if (upgradable != null) {
            val tier = upgradable.nextUpgrade!!
            val extra = upgradable.income * (tier.effectMult - 1f)
            if (extra * secondsLeft > tier.cost * 1.3f) {
                if (own.gold >= tier.cost) engine.upgradeTower(own, upgradable.instanceId)
                return true
            }
        }
        if (mines.size < maxMines && own.towers.size < GameData.MAX_TOWERS_PER_LANE - 3 &&
            mineType.incomeBonusPerSecond * secondsLeft > mineType.cost * 1.3f
        ) {
            if (own.gold >= mineType.cost) {
                val spot = bestSpot(engine, own, mineType, FloatArray(engine.path.pointCount), FloatArray(own.towers.size), false)
                if (spot != null) engine.placeTower(own, mineType, spot.x, spot.y)
            }
            return true
        }
        return false
    }

    private fun sendForIncome(engine: GameEngine, own: Battlefield, foe: Battlefield, spare: Float) {
        var best: EnemySendType? = null
        var bestRate = 0f
        for (unit in GameData.SENDABLE_UNITS) {
            if (unit.cost > spare || unit.incomeBonus <= 0f || !engine.isUnlocked(unit)) continue
            if (engine.sendCooldownFraction(own, unit) > 0f) continue
            val rate = unit.incomeBonus / unit.cost
            if (rate > bestRate) {
                bestRate = rate
                best = unit
            }
        }
        if (best != null) engine.sendEnemy(own, foe, best)
    }

    private fun continuePush(engine: GameEngine, own: Battlefield, foe: Battlefield) {
        val unit = choosePressureUnit(engine, own, foe, minOf(pushBudget, own.gold))
        if (unit == null) {
            pushing = false
            pushRoll = rng.nextFloat()
            pushCount++
            return
        }
        if (engine.sendEnemy(own, foe, unit) == SendResult.OK) {
            pushBudget -= unit.cost
            if (unit.maxHp >= 200f) lastHeavySendAtMs = engine.elapsedMs
        }
        // Keep the units coming in one tight group instead of one per think.
        thinkTimerMs = PUSH_THINK_MS
    }

    /** The unit that should hurt [foe]'s defense most per gold, among those [budget] can buy right now. */
    private fun choosePressureUnit(engine: GameEngine, own: Battlefield, foe: Battlefield, budget: Float): EnemySendType? {
        var antiAir = false
        var areaPower = 0f
        var totalPower = 0f
        for (t in foe.towers) {
            val p = towerPower(engine, t)
            totalPower += p
            if (t.type.bonusDamageVsFlyerPct > 0f) antiAir = true
            if (t.splashRadius > 0f || t.chains > 0 || t.dotDps > 0f || t.slow > 0f) areaPower += p
        }
        val areaShare = if (totalPower > 0f) areaPower / totalPower else 0f
        val sharp = rng.nextFloat() < skill.sharpness
        val escorting = engine.elapsedMs - lastHeavySendAtMs < 4000f

        var best: EnemySendType? = null
        var bestScore = 0f
        var waiting = false
        for (unit in GameData.SENDABLE_UNITS) {
            if (unit.cost > budget || !engine.isUnlocked(unit)) continue
            if (engine.sendCooldownFraction(own, unit) > 0f) {
                waiting = true
                continue
            }
            var hp = unit.maxHp * unit.count
            val childId = unit.spawnOnDeathId
            if (childId != null) hp += GameData.unit(childId).maxHp * unit.spawnOnDeathCount
            var score = hp / unit.cost * sqrt(unit.speed / 9f)
            if (sharp) {
                if (unit.flying) score *= if (antiAir) 0.8f else 2f
                if (unit.count > 1) score *= if (areaShare < 0.25f) 1.6f else 0.6f
                if (unit.healPerSecond > 0f) score *= if (escorting) 2.2f else 0.6f
                // One fat unit walks through a defense built to mow down crowds.
                if (unit.maxHp >= 200f) score *= 1f + areaShare * 0.5f
            } else {
                score = 0.5f + rng.nextFloat()
            }
            if (score > bestScore) {
                bestScore = score
                best = unit
            }
        }
        // Everything affordable is cooling down. Returning one of those anyway makes this send
        // fail harmlessly and keeps the push open for the next think, instead of ending it early.
        if (best == null && waiting) {
            return GameData.SENDABLE_UNITS.firstOrNull { it.cost <= budget && engine.isUnlocked(it) }
        }
        return best
    }

    /** How much gold the next push should be worth before it is sent. */
    private fun pushTarget(engine: GameEngine, foe: Battlefield): Float {
        val habit = when (personality) {
            AiPersonality.RUSHER -> 40f + pushRoll * 60f
            AiPersonality.BALANCED -> 130f + pushRoll * 120f + engine.round * 6f
            AiPersonality.TURTLE -> 260f + pushRoll * 200f + engine.round * 10f
            AiPersonality.TYCOON ->
                if (engine.timeRemainingSec() > 60) 260f + pushRoll * 160f else 350f + pushRoll * 250f
        }
        // A sharp AI does not push by habit: it works out what the defense in front of it can
        // absorb and saves until it can send more than that. A Rusher only bothers every third push.
        val calculates = skill.sharpness >= 0.5f && (personality != AiPersonality.RUSHER || pushCount % 3 == 2)
        val target = if (calculates) maxOf(habit, breakBudget(engine, foe) * breakAmbition()) else habit
        return target * skill.pushScale
    }

    private fun breakAmbition(): Float = when (personality) {
        AiPersonality.TURTLE -> 1.3f
        AiPersonality.TYCOON -> 1.2f
        AiPersonality.BALANCED -> 1f
        AiPersonality.RUSHER -> 0.8f
    }

    /** Gold worth of units that should be more than [foe]'s towers can kill in one pass. */
    private fun breakBudget(engine: GameEngine, foe: Battlefield): Float {
        var bestHpPerGold = 0.5f
        for (unit in GameData.SENDABLE_UNITS) {
            if (!engine.isUnlocked(unit)) continue
            var hp = unit.maxHp * unit.count
            val childId = unit.spawnOnDeathId
            if (childId != null) hp += GameData.unit(childId).maxHp * unit.spawnOnDeathCount
            bestHpPerGold = maxOf(bestHpPerGold, hp / unit.cost)
        }
        val absorbs = lanePower(engine, foe) * EXPOSURE_SEC * BREAK_MARGIN
        return (absorbs / (bestHpPerGold * engine.sendHpScale())).coerceAtMost(MAX_PUSH_GOLD)
    }

    private fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1; val dy = y2 - y1
        return sqrt(dx * dx + dy * dy)
    }

    companion object {
        /** Chooses which [GameData.DRAFT_PICKS] of the [offered] towers the AI takes into the match. */
        fun pickDraft(offered: List<TroopType>, difficulty: Difficulty, rng: Random = Random.Default): List<TroopType> {
            val picks = GameData.DRAFT_PICKS
            if (offered.size <= picks) return offered
            if (difficulty == Difficulty.EASY) {
                while (true) {
                    val hand = offered.shuffled(rng).take(picks)
                    if (GameData.hasDamageDealer(hand)) return hand
                }
            }
            // Take the best damage dealer first, then round the hand out with different roles.
            val dealers = offered.filter { it.baseDps >= GameData.MIN_DRAFT_DPS }
                .sortedByDescending { draftValue(it) + rng.nextFloat() * 0.1f }
            val hand = ArrayList<TroopType>()
            hand.add(dealers.first())
            val rest = offered.filter { it !== hand[0] }
                .sortedByDescending { draftValue(it) + rng.nextFloat() * (if (difficulty == Difficulty.HARD) 0.1f else 0.5f) }
            for (candidate in rest) {
                if (hand.size >= picks) break
                // Two towers that do not shoot leave too little firepower.
                if (!candidate.isAttacker && hand.any { !it.isAttacker }) continue
                hand.add(candidate)
            }
            for (candidate in rest) {
                if (hand.size >= picks) break
                if (candidate !in hand) hand.add(candidate)
            }
            return hand
        }

        /** Rough strength per gold; only used to rank a draft. */
        private fun draftValue(type: TroopType): Float {
            var dps = type.baseDps
            if (type.splashRadius > 0f) dps *= 1f + type.splashRadius / 9f
            if (type.chainTargets > 0) dps *= 1f + 0.5f * type.chainTargets
            dps += type.dotDamagePerSecond * PULSE_TARGETS + type.slowFactor * 45f + type.stunChance * 14f
            dps += type.incomeBonusPerSecond * 12f + type.auraDamageBonusPct * 0.9f
            return dps / type.cost
        }
    }
}
