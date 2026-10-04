package com.towerduel.game.engine

import com.towerduel.game.data.EnemySendType
import com.towerduel.game.data.GameData
import com.towerduel.game.data.LaneSpace
import com.towerduel.game.data.MapDef
import com.towerduel.game.data.MatchModifier
import com.towerduel.game.data.ShotKind
import com.towerduel.game.data.TargetPriority
import com.towerduel.game.data.TroopType
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private const val SPLASH_DAMAGE_FRACTION = 0.6f
private const val CHAIN_DAMAGE_FRACTION = 0.7f
private const val CHAIN_JUMP_RANGE = 13f
private const val SELL_REFUND_FRACTION = 0.7f
private const val MULTI_SEND_GAP_MS = 320f
private const val MORTAR_FLIGHT_MS = 900f
private const val MORTAR_DIRECT_HIT_FRACTION = 0.4f
private const val MINE_PAYOUT_GOLD = 10f
private const val STUN_IMMUNITY_MS = 1500f
private const val LANE_MARGIN = 4f
private const val BASE_CLEARANCE = 9f
private const val MUZZLE_OFFSET = 3f
private const val MAX_LANE_OFFSET = 1.5f
private const val HEAL_FX_INTERVAL_MS = 800f

/** Units still walking in from off the left edge cannot be shot yet. */
private const val MIN_TARGET_X = -1f

/** A send at least this expensive is announced to the lane it is heading for. */
private const val WARNING_SEND_COST = 90

class GameEngine(
    val map: MapDef,
    val modifier: MatchModifier,
    playerDraft: List<TroopType>,
    aiDraft: List<TroopType>,
    seed: Long = Random.nextLong()
) {
    val path = LanePath(map.pathPoints)
    private val rng = Random(seed)
    private val scratch = FloatArray(4)

    /** Seconds of scripted rounds; after that, sudden death. */
    val matchDurationSec: Int = modifier.matchDurationOverrideSec ?: GameData.MATCH_DURATION_SEC
    val startingLives: Int = modifier.livesOverride ?: GameData.STARTING_LIVES
    private val startingGold = GameData.STARTING_GOLD + modifier.startingGoldBonus

    val totalRounds: Int = (matchDurationSec / GameData.ROUND_INTERVAL_SEC).coerceAtLeast(1)

    /** WAVES entries per round. Above 1 in a short match, so it still reaches the late waves. */
    private val waveStride: Float = GameData.WAVES.size / totalRounds.toFloat()

    val playerField = Battlefield("player", playerDraft, startingGold.toFloat(), startingLives)
    val aiField = Battlefield("ai", aiDraft, startingGold.toFloat(), startingLives)

    var elapsedMs: Float = 0f
        private set
    var outcome: MatchOutcome = MatchOutcome.ONGOING
        private set

    /** The round whose wave was released last; 0 until the first one. */
    var round = 0
        private set
    var roundStartedAtMs = -100_000f
        private set
    private var nextRoundAtMs = GameData.FIRST_ROUND_DELAY_SEC * 1000f

    /** Sounds waiting to be played. Whoever owns the engine drains this every frame. */
    val cues = ArrayList<CueEvent>()

    // Units born from a kill (Splitlings) wait here until the lane's lists are safe to change.
    private val newborns = ArrayList<EnemyUnit>()

    val suddenDeath: Boolean get() = elapsedMs >= matchDurationSec * 1000f

    /** Seconds until sudden death. */
    fun timeRemainingSec(): Int = ceil(matchDurationSec - elapsedMs / 1000f).toInt().coerceAtLeast(0)

    fun secondsToNextRound(): Float = ((nextRoundAtMs - elapsedMs) / 1000f).coerceAtLeast(0f)

    // -------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------

    /** How far [tower] reaches in lane units: its attack range, or its aura for support towers. */
    fun towerReach(tower: TowerInstance): Float =
        if (tower.type.auraRange > 0f) tower.auraRange else tower.range * modifier.rangeMultiplier

    /** The reach a freshly placed [type] would have. */
    fun baseReach(type: TroopType): Float =
        if (type.auraRange > 0f) type.auraRange else type.range * modifier.rangeMultiplier

    /** Damage of one shot from [tower], with the match modifier and Beacon boosts applied. */
    fun shotDamage(tower: TowerInstance): Float =
        tower.damage * modifier.damageMultiplier * (1f + tower.auraBonus)

    fun sellRefund(tower: TowerInstance): Int = (tower.invested * SELL_REFUND_FRACTION).toInt()

    fun incomePerSec(field: Battlefield): Float {
        var mines = 0f
        for (t in field.towers) mines += t.income
        return (GameData.BASE_INCOME_PER_SEC + field.ecoIncome) * modifier.incomeMultiplier + mines
    }

    /** Which WAVES entry the match has reached; also what gates the bigger sends. */
    private fun waveLevel(forRound: Int): Int = ceil(forRound * waveStride).toInt().coerceAtLeast(1)

    fun isUnlocked(type: EnemySendType): Boolean = type.unlockRound <= waveLevel(round)

    /** The round in which [type] becomes sendable in this match. */
    fun unlockRoundOf(type: EnemySendType): Int = ceil(type.unlockRound / waveStride).toInt().coerceAtLeast(1)

    /** 0 when [field] may send [type] again, counting down from 1 right after a send. */
    fun sendCooldownFraction(field: Battlefield, type: EnemySendType): Float {
        val readyAt = field.sendReadyAtMs[type.id] ?: return 0f
        return ((readyAt - elapsedMs) / type.cooldownMs).coerceIn(0f, 1f)
    }

    // -------------------------------------------------------------------
    // Player / AI actions
    // -------------------------------------------------------------------

    fun clampToLane(x: Float, y: Float): Pair<Float, Float> =
        x.coerceIn(LANE_MARGIN, LaneSpace.WIDTH - LANE_MARGIN) to y.coerceIn(LANE_MARGIN, LaneSpace.HEIGHT - LANE_MARGIN)

    /** What placing [type] at (x, y) would do, without doing it. Position problems win over a short purse. */
    fun checkPlacement(field: Battlefield, type: TroopType, x: Float, y: Float): PlaceResult {
        if (outcome != MatchOutcome.ONGOING) return PlaceResult.MATCH_OVER
        if (field.towers.size >= GameData.MAX_TOWERS_PER_LANE) return PlaceResult.LANE_FULL
        val (cx, cy) = clampToLane(x, y)
        if (path.distanceTo(cx, cy) < GameData.PATH_CLEARANCE) return PlaceResult.ON_PATH
        val last = path.pointCount - 1
        if (dist(cx, cy, path.xs[last], path.ys[last]) < BASE_CLEARANCE) return PlaceResult.ON_PATH
        for (t in field.towers) {
            if (dist(t.x, t.y, cx, cy) < GameData.MIN_TOWER_SPACING) return PlaceResult.TOO_CLOSE
        }
        if (field.gold < type.cost) return PlaceResult.NOT_ENOUGH_GOLD
        return PlaceResult.OK
    }

    fun placeTower(field: Battlefield, type: TroopType, x: Float, y: Float): PlaceResult {
        val result = checkPlacement(field, type, x, y)
        if (result != PlaceResult.OK) return result
        val (cx, cy) = clampToLane(x, y)
        field.gold -= type.cost
        field.towers.add(TowerInstance(field.nextInstanceId++, type, cx, cy, elapsedMs))
        field.stats.towersBuilt++
        refreshAuras(field)
        field.fx.add(FxEvent(FxKind.DUST, cx, cy, 420f, size = 4.5f))
        cue(SoundCue.PLACE, field)
        return PlaceResult.OK
    }

    fun upgradeTower(field: Battlefield, instanceId: Long): Boolean {
        if (outcome != MatchOutcome.ONGOING) return false
        val t = field.towers.find { it.instanceId == instanceId } ?: return false
        val tier = t.nextUpgrade ?: return false
        if (field.gold < tier.cost) return false
        field.gold -= tier.cost
        t.applyNextUpgrade(elapsedMs)
        refreshAuras(field)
        field.fx.add(FxEvent(FxKind.SPARKLE, t.x, t.y, 700f, size = 5f, seed = rng.nextInt()))
        cue(SoundCue.UPGRADE, field)
        return true
    }

    fun sellTower(field: Battlefield, instanceId: Long): Boolean {
        if (outcome != MatchOutcome.ONGOING) return false
        val t = field.towers.find { it.instanceId == instanceId } ?: return false
        val refund = sellRefund(t)
        field.gold += refund + t.payoutBank
        field.towers.remove(t)
        refreshAuras(field)
        field.fx.add(FxEvent(FxKind.DUST, t.x, t.y, 420f, size = 4.5f))
        field.fx.add(FxEvent(FxKind.GOLD_TEXT, t.x, t.y - 2f, 900f, value = refund))
        cue(SoundCue.SELL, field)
        return true
    }

    /** Steps [instanceId]'s targeting to the next priority. Pulse and support towers have none. */
    fun cycleTargeting(field: Battlefield, instanceId: Long): Boolean {
        val t = field.towers.find { it.instanceId == instanceId } ?: return false
        if (!usesTargeting(t.type)) return false
        val all = TargetPriority.entries
        t.targeting = all[(t.targeting.ordinal + 1) % all.size]
        return true
    }

    fun usesTargeting(type: TroopType): Boolean =
        type.isAttacker && type.shot != ShotKind.FROST_PULSE && type.shot != ShotKind.POISON_PULSE

    fun sendEnemy(source: Battlefield, target: Battlefield, type: EnemySendType): SendResult {
        if (outcome != MatchOutcome.ONGOING) return SendResult.MATCH_OVER
        if (!type.sendable || !isUnlocked(type)) return SendResult.LOCKED
        if (elapsedMs < (source.sendReadyAtMs[type.id] ?: 0f)) return SendResult.COOLING_DOWN
        if (source.gold < type.cost) return SendResult.NOT_ENOUGH_GOLD
        source.gold -= type.cost
        source.ecoIncome += type.incomeBonus
        source.sendReadyAtMs[type.id] = elapsedMs + type.cooldownMs
        source.stats.unitsSent += type.count
        // A sent unit is as tough as the wave units of the round it is sent in, so sends never go stale.
        val hpScale = sendHpScale()
        for (i in 0 until type.count) {
            target.pendingSpawns.add(PendingSpawn(type, i * MULTI_SEND_GAP_MS, hpScale))
        }
        if (type.cost >= WARNING_SEND_COST) {
            target.warning = LaneWarning(type, elapsedMs)
            cue(SoundCue.WARNING, target)
        }
        cue(SoundCue.SEND, source)
        return SendResult.OK
    }

    // -------------------------------------------------------------------
    // Simulation tick
    // -------------------------------------------------------------------

    fun update(dtSeconds: Float) {
        if (outcome != MatchOutcome.ONGOING) return
        elapsedMs += dtSeconds * 1000f

        if (elapsedMs >= nextRoundAtMs) startNextRound()

        tickField(playerField, dtSeconds)
        tickField(aiField, dtSeconds)

        resolveOutcome()
    }

    private fun tickField(field: Battlefield, dtSeconds: Float) {
        earn(field, (GameData.BASE_INCOME_PER_SEC + field.ecoIncome) * modifier.incomeMultiplier * dtSeconds)
        towerPass(field, dtSeconds)
        projectilePass(field, dtSeconds)
        enemyPass(field, dtSeconds)
        fxPass(field, dtSeconds)
    }

    private fun waveIndexFor(forRound: Int): Int {
        val lastWave = GameData.WAVES.size - 1
        if (forRound <= totalRounds) return (waveLevel(forRound) - 1).coerceIn(0, lastWave)
        // Sudden death replays the last few waves.
        return (lastWave - (forRound - totalRounds) % 3).coerceAtLeast(0)
    }

    private fun waveHpScaleFor(forRound: Int): Float {
        val growth = GameData.WAVE_HP_GROWTH_PER_ROUND
        if (forRound <= totalRounds) return 1f + growth * waveIndexFor(forRound)
        // ...each one much tougher than the one before, so the match cannot drag on.
        return (1f + growth * (GameData.WAVES.size - 1)) * GameData.OVERTIME_HP_GROWTH.pow(forRound - totalRounds)
    }

    private fun waveSpeedScaleFor(forRound: Int): Float =
        if (forRound <= totalRounds) 1f
        else (1f + GameData.OVERTIME_SPEED_GROWTH * (forRound - totalRounds)).coerceAtMost(GameData.OVERTIME_MAX_SPEED)

    /** How much tougher than its listed health a unit sent right now is. */
    fun sendHpScale(): Float = waveHpScaleFor(round.coerceAtLeast(1))

    /** Total health of the wave released in [forRound]; lets a side judge what its defense must handle. */
    fun waveHp(forRound: Int): Float {
        var sum = 0f
        for (group in GameData.WAVES[waveIndexFor(forRound)]) sum += GameData.unit(group.unitId).maxHp * group.count
        return sum * waveHpScaleFor(forRound)
    }

    private fun startNextRound() {
        round++
        roundStartedAtMs = elapsedMs
        val waveIndex = waveIndexFor(round)
        val hpScale = waveHpScaleFor(round)
        val speedScale = waveSpeedScaleFor(round)
        for (group in GameData.WAVES[waveIndex]) {
            val type = GameData.unit(group.unitId)
            for (i in 0 until group.count) {
                val delay = (group.delayMs + i * group.gapMs).toFloat()
                playerField.pendingSpawns.add(PendingSpawn(type, delay, hpScale, speedScale))
                aiField.pendingSpawns.add(PendingSpawn(type, delay, hpScale, speedScale))
            }
        }
        val interval = if (round >= totalRounds) GameData.OVERTIME_ROUND_INTERVAL_SEC else GameData.ROUND_INTERVAL_SEC
        nextRoundAtMs += interval * 1000f
        cue(SoundCue.ROUND, null)
    }

    private fun resolveOutcome() {
        val playerDead = playerField.lives <= 0
        val aiDead = aiField.lives <= 0
        val outOfTime = elapsedMs / 1000f >= matchDurationSec + GameData.OVERTIME_LIMIT_SEC
        outcome = when {
            playerDead && aiDead -> MatchOutcome.DRAW
            playerDead -> MatchOutcome.AI_WIN
            aiDead -> MatchOutcome.PLAYER_WIN
            outOfTime -> when {
                playerField.lives > aiField.lives -> MatchOutcome.PLAYER_WIN
                aiField.lives > playerField.lives -> MatchOutcome.AI_WIN
                else -> MatchOutcome.DRAW
            }
            else -> MatchOutcome.ONGOING
        }
        when (outcome) {
            MatchOutcome.PLAYER_WIN -> cue(SoundCue.WIN, null)
            MatchOutcome.AI_WIN, MatchOutcome.DRAW -> cue(SoundCue.LOSE, null)
            MatchOutcome.ONGOING -> Unit
        }
    }

    // -------------------------------------------------------------------
    // Towers: economy, aiming and attacks
    // -------------------------------------------------------------------

    private fun towerPass(field: Battlefield, dtSeconds: Float) {
        for (i in field.towers.indices) {
            val tower = field.towers[i]

            // Gold Mines bank their income and pay it out in visible lumps.
            if (tower.income > 0f) {
                tower.payoutBank += tower.income * dtSeconds
                if (tower.payoutBank >= MINE_PAYOUT_GOLD) {
                    val payout = tower.payoutBank.toInt()
                    earn(field, payout.toFloat())
                    tower.payoutBank -= payout
                    tower.lastFiredAtMs = elapsedMs
                    field.fx.add(FxEvent(FxKind.GOLD_TEXT, tower.x, tower.y - 3f, 900f, value = payout))
                    cue(SoundCue.COIN, field)
                }
            }

            tower.cooldownMs -= dtSeconds * 1000f
            if (!tower.type.isAttacker) continue

            val range = towerReach(tower)
            val target = pickTarget(field, tower, range, null) ?: continue
            tower.aimAngle = atan2(target.y - tower.y, target.x - tower.x)
            if (tower.cooldownMs > 0f) continue

            fire(field, tower, target, range)
            tower.cooldownMs = tower.reloadMs
            tower.lastFiredAtMs = elapsedMs
        }
    }

    private fun pickTarget(field: Battlefield, tower: TowerInstance, range: Float, skip: EnemyUnit?): EnemyUnit? {
        val r2 = range * range
        val huntsFlyers = tower.type.bonusDamageVsFlyerPct > 0f
        var best: EnemyUnit? = null
        var bestScore = Float.NEGATIVE_INFINITY
        // Units killed earlier this tick stay in the list until enemyPass, so skip them here.
        for (i in field.incomingEnemies.indices) {
            val e = field.incomingEnemies[i]
            if (!e.alive || e === skip || e.x < MIN_TARGET_X) continue
            val dx = e.x - tower.x
            val dy = e.y - tower.y
            val d2 = dx * dx + dy * dy
            if (d2 > r2) continue
            var score = when (tower.targeting) {
                TargetPriority.FIRST -> e.dist
                TargetPriority.LAST -> -e.dist
                TargetPriority.STRONGEST -> e.hp + e.progress
                TargetPriority.CLOSEST -> -d2
            }
            if (huntsFlyers && e.type.flying) score += 1_000_000f
            if (score > bestScore) {
                bestScore = score
                best = e
            }
        }
        return best
    }

    private fun fire(field: Battlefield, tower: TowerInstance, primary: EnemyUnit, range: Float) {
        val damage = shotDamage(tower)
        when (tower.type.shot) {
            ShotKind.NONE -> Unit
            ShotKind.FROST_PULSE, ShotKind.POISON_PULSE -> pulse(field, tower, range, damage)
            ShotKind.RAIL -> {
                field.fx.add(FxEvent(FxKind.TRACER, muzzleX(tower), muzzleY(tower), 180f, x2 = primary.x, y2 = primary.y, tower = tower.type))
                hit(field, tower, primary, damage)
                cue(SoundCue.SHOOT_HEAVY, field)
            }
            ShotKind.BOLT -> chainLightning(field, tower, primary, damage)
            ShotKind.MORTAR -> {
                // Lead the target: aim at where it will be when the shell lands.
                val lead = unitSpeed(primary) * (MORTAR_FLIGHT_MS / 1000f)
                path.sample((primary.dist + lead).coerceAtMost(path.length), scratch)
                field.projectiles.add(
                    Projectile(ShotKind.MORTAR, tower, null, tower.x, tower.y, scratch[0], scratch[1], 0f, damage, MORTAR_FLIGHT_MS)
                )
                cue(SoundCue.SHOOT_HEAVY, field)
            }
            else -> {
                launch(field, tower, primary, damage)
                var previous = primary
                repeat(tower.shots - 1) {
                    val extra = pickTarget(field, tower, range, previous) ?: primary
                    launch(field, tower, extra, damage)
                    previous = extra
                }
                cue(if (tower.type.shot == ShotKind.SHELL) SoundCue.SHOOT_HEAVY else SoundCue.SHOOT, field)
            }
        }
    }

    private fun launch(field: Battlefield, tower: TowerInstance, target: EnemyUnit, damage: Float) {
        val speed = when (tower.type.shot) {
            ShotKind.BULLET -> 95f
            ShotKind.SHELL -> 52f
            ShotKind.NET -> 68f
            ShotKind.ORB -> 62f
            else -> 78f
        }
        val angle = atan2(target.y - tower.y, target.x - tower.x)
        val p = Projectile(
            tower.type.shot, tower, target,
            tower.x + cos(angle) * MUZZLE_OFFSET, tower.y + sin(angle) * MUZZLE_OFFSET,
            target.x, target.y, speed, damage
        )
        p.angle = angle
        field.projectiles.add(p)
    }

    private fun pulse(field: Battlefield, tower: TowerInstance, range: Float, damage: Float) {
        val frost = tower.type.shot == ShotKind.FROST_PULSE
        field.fx.add(FxEvent(if (frost) FxKind.FROST_RING else FxKind.POISON_CLOUD, tower.x, tower.y, 520f, size = range))
        for (i in field.incomingEnemies.indices) {
            val e = field.incomingEnemies[i]
            if (!e.alive || dist(tower.x, tower.y, e.x, e.y) > range) continue
            // The strongest active slow or poison wins; a weaker tower never overwrites it.
            if (tower.slow > 0f) {
                val stillSlowed = elapsedMs < e.slowExpiresAtMs
                e.slowFactor = if (stillSlowed) maxOf(e.slowFactor, tower.slow) else tower.slow
                e.slowExpiresAtMs = elapsedMs + tower.type.slowDurationMs
            }
            if (tower.dotDps > 0f) {
                val stillPoisoned = elapsedMs < e.dotExpiresAtMs
                e.dotDps = if (stillPoisoned) maxOf(e.dotDps, tower.dotDps) else tower.dotDps
                e.dotExpiresAtMs = elapsedMs + tower.type.dotDurationMs
                e.dotSource = tower
            }
            hit(field, tower, e, damage)
        }
        cue(if (frost) SoundCue.FREEZE else SoundCue.SHOOT, field)
    }

    private fun chainLightning(field: Battlefield, tower: TowerInstance, primary: EnemyUnit, damage: Float) {
        var fromX = muzzleX(tower)
        var fromY = muzzleY(tower)
        var current: EnemyUnit = primary
        var jump = 0
        val struck = ArrayList<EnemyUnit>(tower.chains + 1)
        while (true) {
            field.fx.add(FxEvent(FxKind.BOLT, fromX, fromY, 200f, x2 = current.x, y2 = current.y, seed = rng.nextInt()))
            struck.add(current)
            fromX = current.x
            fromY = current.y
            hit(field, tower, current, damage, if (jump == 0) 1f else CHAIN_DAMAGE_FRACTION)
            if (jump >= tower.chains) break
            jump++

            var next: EnemyUnit? = null
            var nearest = CHAIN_JUMP_RANGE
            for (i in field.incomingEnemies.indices) {
                val e = field.incomingEnemies[i]
                if (!e.alive || e.x < MIN_TARGET_X || e in struck) continue
                val d = dist(fromX, fromY, e.x, e.y)
                if (d <= nearest) {
                    nearest = d
                    next = e
                }
            }
            current = next ?: break
        }
        cue(SoundCue.ZAP, field)
    }

    private fun muzzleX(tower: TowerInstance): Float = tower.x + cos(tower.aimAngle) * MUZZLE_OFFSET
    private fun muzzleY(tower: TowerInstance): Float = tower.y + sin(tower.aimAngle) * MUZZLE_OFFSET

    private fun refreshAuras(field: Battlefield) {
        for (tower in field.towers) {
            var bonus = 0f
            for (other in field.towers) {
                if (other === tower || other.auraPct <= 0f) continue
                if (dist(other.x, other.y, tower.x, tower.y) <= other.auraRange) bonus += other.auraPct / 100f
            }
            tower.auraBonus = bonus
        }
    }

    // -------------------------------------------------------------------
    // Projectiles and damage
    // -------------------------------------------------------------------

    private fun projectilePass(field: Battlefield, dtSeconds: Float) {
        for (i in field.projectiles.indices) {
            val p = field.projectiles[i]
            p.ageMs += dtSeconds * 1000f

            if (p.kind == ShotKind.MORTAR) {
                val t = (p.ageMs / p.flightMs).coerceAtMost(1f)
                p.x = p.startX + (p.targetX - p.startX) * t
                p.y = p.startY + (p.targetY - p.startY) * t
                if (t >= 1f) {
                    explode(field, p.source, p.targetX, p.targetY, p.damage, null)
                    p.done = true
                }
                continue
            }

            // Homing: follow the target while it lives, then finish the flight to where it was.
            val target = p.target
            if (target != null && target.alive) {
                p.targetX = target.x
                p.targetY = target.y
            }
            val dx = p.targetX - p.x
            val dy = p.targetY - p.y
            val d = sqrt(dx * dx + dy * dy)
            val step = p.speed * dtSeconds
            if (d <= step + 0.8f) {
                impact(field, p)
                p.done = true
            } else {
                p.x += dx / d * step
                p.y += dy / d * step
                p.angle = atan2(dy, dx)
            }
        }
        field.projectiles.removeAll { it.done }
    }

    private fun impact(field: Battlefield, p: Projectile) {
        val tower = p.source
        val target = p.target
        if (tower.splashRadius > 0f) {
            explode(field, tower, p.targetX, p.targetY, p.damage, target)
            return
        }
        if (target == null || !target.alive) return
        field.fx.add(FxEvent(FxKind.HIT, target.x, target.y, 160f, size = 1.6f, tower = tower.type))
        if (tower.stunChance > 0f && rng.nextFloat() < tower.stunChance &&
            elapsedMs >= target.stunExpiresAtMs + STUN_IMMUNITY_MS
        ) {
            target.stunExpiresAtMs = elapsedMs + tower.type.stunDurationMs
        }
        hit(field, tower, target, p.damage)
    }

    /** Splash around (x, y). [primary] takes the full hit; a shell with no primary rewards a direct hit instead. */
    private fun explode(field: Battlefield, tower: TowerInstance, x: Float, y: Float, damage: Float, primary: EnemyUnit?) {
        val radius = tower.splashRadius
        field.fx.add(FxEvent(FxKind.EXPLOSION, x, y, 380f, size = radius, seed = rng.nextInt()))
        for (i in field.incomingEnemies.indices) {
            val e = field.incomingEnemies[i]
            if (!e.alive) continue
            val d = dist(x, y, e.x, e.y)
            if (d > radius) continue
            val direct = if (primary != null) e === primary else d <= radius * MORTAR_DIRECT_HIT_FRACTION
            hit(field, tower, e, damage, if (direct) 1f else SPLASH_DAMAGE_FRACTION)
        }
        cue(SoundCue.BOOM, field)
    }

    private fun hit(field: Battlefield, tower: TowerInstance, target: EnemyUnit, damage: Float, fraction: Float = 1f) {
        if (!target.alive) return
        var dmg = damage * fraction
        dmg = if (tower.type.bonusDamageVsFlyerPct > 0f && target.type.flying) {
            dmg * (1f + tower.type.bonusDamageVsFlyerPct / 100f)
        } else {
            dmg * (1f - target.type.damageResistancePct / 100f)
        }
        dmg = dmg.coerceAtLeast(0f)
        target.hp -= dmg
        target.lastHitAtMs = elapsedMs
        if (target.hp <= 0f) kill(field, target, tower)
    }

    private fun kill(field: Battlefield, enemy: EnemyUnit, by: TowerInstance?) {
        enemy.alive = false
        if (by != null) by.kills++
        field.stats.kills++
        val bounty = enemy.type.bountyGold
        earn(field, bounty.toFloat())
        field.fx.add(FxEvent(FxKind.POP, enemy.x, enemy.y, 420f, size = enemy.type.radius, unit = enemy.type, seed = rng.nextInt()))
        if (bounty > 0) {
            field.fx.add(FxEvent(FxKind.GOLD_TEXT, enemy.x, enemy.y - enemy.type.radius, 800f, value = bounty))
        }
        cue(if (enemy.type.radius >= 3f) SoundCue.POP_BIG else SoundCue.POP, field)

        val childId = enemy.type.spawnOnDeathId ?: return
        val childType = GameData.unit(childId)
        val hpScale = enemy.maxHp / enemy.type.maxHp
        for (i in 0 until enemy.type.spawnOnDeathCount) {
            val child = newEnemy(field, childType, hpScale, enemy.speedScale)
            child.dist = (enemy.dist - i * 1.6f).coerceAtLeast(0f)
            place(child)
            newborns.add(child)
        }
    }

    // -------------------------------------------------------------------
    // Units: spawns, movement, poison/heal ticks, leaks
    // -------------------------------------------------------------------

    private fun newEnemy(field: Battlefield, type: EnemySendType, hpScale: Float, speedScale: Float): EnemyUnit =
        EnemyUnit(
            field.nextInstanceId++, type, type.maxHp * hpScale,
            laneOffset = (rng.nextFloat() * 2f - 1f) * MAX_LANE_OFFSET,
            speedScale = speedScale
        )

    private fun place(enemy: EnemyUnit) {
        path.sample(enemy.dist, scratch)
        enemy.dirX = scratch[2]
        enemy.dirY = scratch[3]
        enemy.x = scratch[0] - scratch[3] * enemy.laneOffset
        enemy.y = scratch[1] + scratch[2] * enemy.laneOffset
        enemy.progress = (enemy.dist / path.length).coerceIn(0f, 1f)
    }

    /** Lane units per second [enemy] is moving right now, with slows, stuns and the match modifier applied. */
    private fun unitSpeed(enemy: EnemyUnit): Float {
        val status = when {
            elapsedMs < enemy.stunExpiresAtMs -> 0f
            elapsedMs < enemy.slowExpiresAtMs -> 1f - enemy.slowFactor
            else -> 1f
        }
        return enemy.type.speed * enemy.speedScale * modifier.speedMultiplier * status
    }

    private fun enemyPass(field: Battlefield, dtSeconds: Float) {
        val dtMs = dtSeconds * 1000f
        val enemies = field.incomingEnemies

        var p = 0
        while (p < field.pendingSpawns.size) {
            val queued = field.pendingSpawns[p]
            queued.delayMs -= dtMs
            if (queued.delayMs <= 0f) {
                val unit = newEnemy(field, queued.type, queued.hpScale, queued.speedScale)
                place(unit)
                enemies.add(unit)
                field.pendingSpawns.removeAt(p)
            } else {
                p++
            }
        }

        // Healers pulse first so this tick's heal applies before hp checks
        val healFx = (elapsedMs / HEAL_FX_INTERVAL_MS).toInt() != ((elapsedMs - dtMs) / HEAL_FX_INTERVAL_MS).toInt()
        for (i in enemies.indices) {
            val healer = enemies[i]
            if (!healer.alive || healer.type.healPerSecond <= 0f) continue
            for (j in enemies.indices) {
                val target = enemies[j]
                if (!target.alive || abs(target.dist - healer.dist) > healer.type.healRadius) continue
                target.hp = (target.hp + healer.type.healPerSecond * dtSeconds).coerceAtMost(target.maxHp)
            }
            if (healFx && healer.x >= MIN_TARGET_X) {
                field.fx.add(FxEvent(FxKind.HEAL, healer.x, healer.y, 600f, size = healer.type.healRadius * 0.55f))
            }
        }

        for (i in enemies.indices) {
            val enemy = enemies[i]
            if (!enemy.alive) continue

            if (elapsedMs < enemy.dotExpiresAtMs) {
                enemy.hp -= enemy.dotDps * dtSeconds
                if (enemy.hp <= 0f) {
                    kill(field, enemy, enemy.dotSource)
                    continue
                }
            }

            enemy.dist += unitSpeed(enemy) * dtSeconds
            if (enemy.dist >= path.length) {
                enemy.alive = false
                field.lives = (field.lives - enemy.type.livesDamage).coerceAtLeast(0)
                field.stats.leaks++
                field.lastLeakAtMs = elapsedMs
                field.fx.add(FxEvent(FxKind.LIFE_TEXT, enemy.x, enemy.y - 4f, 1000f, value = enemy.type.livesDamage))
                cue(SoundCue.LEAK, field)
                continue
            }
            place(enemy)
        }

        if (newborns.isNotEmpty()) {
            enemies.addAll(newborns)
            newborns.clear()
        }
        enemies.removeAll { !it.alive }
    }

    private fun fxPass(field: Battlefield, dtSeconds: Float) {
        val dtMs = dtSeconds * 1000f
        for (i in field.fx.indices) field.fx[i].ageMs += dtMs
        field.fx.removeAll { it.ageMs >= it.durationMs }
    }

    // -------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------

    private fun earn(field: Battlefield, amount: Float) {
        field.gold += amount
        field.stats.goldEarned += amount
    }

    private fun cue(cue: SoundCue, field: Battlefield?) {
        // Nobody is listening (a headless match): do not let the queue grow without bound.
        if (cues.size > 96) cues.clear()
        cues.add(CueEvent(cue, field))
    }

    private fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1; val dy = y2 - y1
        return sqrt(dx * dx + dy * dy)
    }
}
