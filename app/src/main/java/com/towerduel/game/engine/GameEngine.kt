package com.towerduel.game.engine

import com.towerduel.game.data.EnemySendType
import com.towerduel.game.data.GameData
import com.towerduel.game.data.LaneSpace
import com.towerduel.game.data.MapDef
import com.towerduel.game.data.MatchEventType
import com.towerduel.game.data.MatchModifier
import com.towerduel.game.data.ShotKind
import com.towerduel.game.data.TargetPriority
import com.towerduel.game.data.TroopType
import com.towerduel.game.data.Wave
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
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

/** Armour never takes more than this share off a hit, so even rapid fire does something. */
private const val ARMOR_MAX_REDUCTION = 0.8f

/** A unit this heavy is thrown back half as far as a featherweight. */
private const val KNOCKBACK_HALF_HP = 300f

/** Regeneration waits this long after the last hit. */
private const val REGEN_DELAY_MS = 1000f

/** A Drummer's haste lasts this long after leaving its aura. */
private const val HASTE_LINGER_MS = 300f

private const val GLAIVE_SPEED = 60f
private const val GLAIVE_REACH = 1.3f
private const val GLAIVE_WIDTH = 1.4f

// What the timed events do while they last.
private const val PAYDAY_INCOME = 2f
private const val STAMPEDE_SPEED = 1.35f
private const val SURGE_DAMAGE = 1.4f
private const val OVERDRIVE_RELOAD = 0.7f
private const val FOG_RANGE = 0.8f
private const val COLD_SNAP_SPEED = 0.5f
private const val AMBUSH_WAVE_SCALE = 0.5f

class GameEngine(
    val map: MapDef,
    val modifier: MatchModifier,
    playerDraft: List<TroopType>,
    aiDraft: List<TroopType>,
    /** The units both sides may send this match, and what its waves are made of. */
    val roster: List<EnemySendType> = GameData.CLASSIC_ROSTER,
    private val seed: Long = Random.nextLong()
) {
    val path = LanePath(map.pathPoints)
    private val rng = Random(seed)
    private val scratch = FloatArray(4)

    /** Seconds of regular rounds; after that, sudden death. */
    val matchDurationSec: Int = modifier.matchDurationOverrideSec ?: GameData.MATCH_DURATION_SEC
    val startingLives: Int = modifier.livesOverride ?: GameData.STARTING_LIVES
    private val startingGold = GameData.STARTING_GOLD + modifier.startingGoldBonus

    val roundIntervalSec: Int = modifier.roundIntervalSec ?: GameData.ROUND_INTERVAL_SEC
    val totalRounds: Int = (matchDurationSec / roundIntervalSec).coerceAtLeast(1)

    /** Wave levels per round. Above 1 in a short match, so it still reaches the late waves. */
    private val levelStride: Float = GameData.WAVE_LEVELS / totalRounds.toFloat()

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

    /** The theme of the current round's wave ("AIR RAID"), or null for an ordinary one. */
    var waveTitle: String? = null
        private set

    // Waves are generated on demand and kept: the AI asks about the next one before it is released.
    private val waves = HashMap<Int, Wave>()

    /** The last random event. Its effect, if it has one, lasts until [ActiveEvent.endsAtMs]. */
    var event: ActiveEvent? = null
        private set
    private var nextEventAtMs =
        (GameData.FIRST_EVENT_MIN_SEC + rng.nextInt(GameData.FIRST_EVENT_MAX_SEC - GameData.FIRST_EVENT_MIN_SEC + 1)) * 1000f

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

    /** The timed event in force right now, if any. */
    private fun activeEvent(): MatchEventType? = event?.takeIf { elapsedMs < it.endsAtMs }?.type

    /** Seconds the current event's effect still lasts; 0 if none is in force. */
    fun eventSecondsLeft(): Int {
        val e = event ?: return 0
        return ceil((e.endsAtMs - elapsedMs) / 1000f).toInt().coerceAtLeast(0)
    }

    private fun rangeFactor(): Float = modifier.rangeMultiplier * (if (activeEvent() == MatchEventType.FOG) FOG_RANGE else 1f)

    /** How far [tower] reaches in lane units: its attack range, or its aura for support towers. */
    fun towerReach(tower: TowerInstance): Float =
        if (tower.type.auraRange > 0f) tower.auraRange else tower.range * rangeFactor()

    /** The reach a freshly placed [type] would have. */
    fun baseReach(type: TroopType): Float =
        if (type.auraRange > 0f) type.auraRange else type.range * rangeFactor()

    /** Damage of one shot from [tower], with the match rule, Beacon boosts and any event applied. */
    fun shotDamage(tower: TowerInstance): Float =
        tower.damage * modifier.damageMultiplier * (1f + tower.auraBonus) *
            (if (activeEvent() == MatchEventType.POWER_SURGE) SURGE_DAMAGE else 1f)

    /** Milliseconds between [tower]'s shots right now. */
    fun reloadMs(tower: TowerInstance): Float =
        tower.reloadMs * modifier.reloadMultiplier * (if (activeEvent() == MatchEventType.OVERDRIVE) OVERDRIVE_RELOAD else 1f)

    fun sellRefund(tower: TowerInstance): Int = (tower.invested * SELL_REFUND_FRACTION).toInt()

    private fun incomeFactor(): Float =
        modifier.incomeMultiplier * (if (activeEvent() == MatchEventType.PAYDAY) PAYDAY_INCOME else 1f)

    fun incomePerSec(field: Battlefield): Float {
        var mines = 0f
        for (t in field.towers) mines += t.income
        return (GameData.BASE_INCOME_PER_SEC + field.ecoIncome) * incomeFactor() + mines
    }

    /** Income a send of [type] adds under this match's rules. */
    fun sendIncome(type: EnemySendType): Float = type.incomeBonus * modifier.sendIncomeMultiplier

    /** The wave level (1..WAVE_LEVELS) the match is at in [forRound]; also what gates the bigger sends. */
    private fun waveLevel(forRound: Int): Int =
        ceil(forRound * levelStride).toInt().coerceIn(1, GameData.WAVE_LEVELS)

    fun isUnlocked(type: EnemySendType): Boolean = type.unlockRound <= waveLevel(round)

    /** The round in which [type] becomes sendable in this match. */
    fun unlockRoundOf(type: EnemySendType): Int = ceil(type.unlockRound / levelStride).toInt().coerceAtLeast(1)

    /** 0 when [field] may send [type] again, counting down from 1 right after a send. */
    fun sendCooldownFraction(field: Battlefield, type: EnemySendType): Float {
        val readyAt = field.sendReadyAtMs[type.id] ?: return 0f
        return ((readyAt - elapsedMs) / type.cooldownMs).coerceIn(0f, 1f)
    }

    /** True while [enemy] is faded out: it cannot be targeted or hurt. */
    fun isPhased(enemy: EnemyUnit): Boolean {
        val every = enemy.type.phaseEveryMs
        if (every <= 0L) return false
        // Offset by id, so a pack of Phantoms does not blink in step.
        val t = (elapsedMs - enemy.bornAtMs + enemy.instanceId * 370L) % every
        return t < enemy.type.phaseMs
    }

    // -------------------------------------------------------------------
    // Player / AI actions
    // -------------------------------------------------------------------

    fun clampToLane(x: Float, y: Float): Pair<Float, Float> =
        x.coerceIn(LANE_MARGIN, LaneSpace.WIDTH - LANE_MARGIN) to y.coerceIn(LANE_MARGIN, LaneSpace.HEIGHT - LANE_MARGIN)

    /**
     * What placing [type] at (x, y) would do, without doing it. Position problems win over a short
     * purse. There is no cap on towers: a lane holds as many as fit beside the track.
     */
    fun checkPlacement(field: Battlefield, type: TroopType, x: Float, y: Float): PlaceResult {
        if (outcome != MatchOutcome.ONGOING) return PlaceResult.MATCH_OVER
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

    fun usesTargeting(type: TroopType): Boolean = type.isAttacker && !type.shot.isPulse

    fun sendEnemy(source: Battlefield, target: Battlefield, type: EnemySendType): SendResult {
        if (outcome != MatchOutcome.ONGOING) return SendResult.MATCH_OVER
        if (!type.sendable || type !in roster || !isUnlocked(type)) return SendResult.LOCKED
        if (elapsedMs < (source.sendReadyAtMs[type.id] ?: 0f)) return SendResult.COOLING_DOWN
        if (source.gold < type.cost) return SendResult.NOT_ENOUGH_GOLD
        source.gold -= type.cost
        source.ecoIncome += sendIncome(type)
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
        if (elapsedMs >= nextEventAtMs) startEvent()

        tickField(playerField, dtSeconds)
        tickField(aiField, dtSeconds)

        resolveOutcome()
    }

    private fun tickField(field: Battlefield, dtSeconds: Float) {
        earn(field, (GameData.BASE_INCOME_PER_SEC + field.ecoIncome) * incomeFactor() * dtSeconds)
        towerPass(field, dtSeconds)
        projectilePass(field, dtSeconds)
        enemyPass(field, dtSeconds)
        fxPass(field, dtSeconds)
    }

    // -------------------------------------------------------------------
    // Rounds and waves
    // -------------------------------------------------------------------

    /** The wave of [forRound]: generated once from the match seed, the same for both lanes. */
    private fun waveFor(forRound: Int): Wave = waves.getOrPut(forRound) {
        WaveGenerator.generate(waveLevel(forRound), roster, Random(seed * 31L + forRound))
    }

    private fun waveHpScaleFor(forRound: Int): Float {
        val growth = GameData.WAVE_HP_GROWTH_PER_LEVEL
        if (forRound <= totalRounds) return 1f + growth * (waveLevel(forRound) - 1)
        // Sudden death: each wave much tougher than the one before, so the match cannot drag on.
        return (1f + growth * (GameData.WAVE_LEVELS - 1)) * GameData.OVERTIME_HP_GROWTH.pow(forRound - totalRounds)
    }

    private fun waveSpeedScaleFor(forRound: Int): Float =
        if (forRound <= totalRounds) 1f
        else (1f + GameData.OVERTIME_SPEED_GROWTH * (forRound - totalRounds)).coerceAtMost(GameData.OVERTIME_MAX_SPEED)

    /** How much tougher than its listed health a unit sent right now is. */
    fun sendHpScale(): Float = waveHpScaleFor(round.coerceAtLeast(1))

    /** Total health of the wave released in [forRound]; lets a side judge what its defense must handle. */
    fun waveHp(forRound: Int): Float =
        WaveGenerator.totalHp(waveFor(forRound)) * waveHpScaleFor(forRound) * modifier.unitHpMultiplier

    private fun release(wave: Wave, hpScale: Float, speedScale: Float) {
        for (group in wave.groups) {
            val type = GameData.unit(group.unitId)
            for (i in 0 until group.count) {
                val delay = (group.delayMs + i * group.gapMs).toFloat()
                playerField.pendingSpawns.add(PendingSpawn(type, delay, hpScale, speedScale))
                aiField.pendingSpawns.add(PendingSpawn(type, delay, hpScale, speedScale))
            }
        }
    }

    private fun startNextRound() {
        round++
        roundStartedAtMs = elapsedMs
        val wave = waveFor(round)
        waveTitle = wave.title
        release(wave, waveHpScaleFor(round), waveSpeedScaleFor(round))
        val interval = if (round >= totalRounds) GameData.OVERTIME_ROUND_INTERVAL_SEC else roundIntervalSec
        nextRoundAtMs += interval * 1000f
        cue(SoundCue.ROUND, null)
    }

    // -------------------------------------------------------------------
    // Random events
    // -------------------------------------------------------------------

    private fun startEvent() {
        // Never the same event twice in a row.
        val previous = event?.type
        val type = MatchEventType.entries.filter { it != previous }.random(rng)
        event = ActiveEvent(type, elapsedMs, elapsedMs + type.durationSec * 1000f)
        when (type) {
            MatchEventType.GOLD_RAIN -> {
                val gold = 50f + 10f * round
                earn(playerField, gold)
                earn(aiField, gold)
            }
            MatchEventType.AMBUSH -> {
                val level = waveLevel(round.coerceAtLeast(1))
                val wave = WaveGenerator.generate(level, roster, rng, AMBUSH_WAVE_SCALE)
                release(wave, waveHpScaleFor(round.coerceAtLeast(1)), waveSpeedScaleFor(round))
            }
            else -> Unit // a timed effect: read wherever it applies, through activeEvent()
        }
        val gap = GameData.EVENT_GAP_MIN_SEC + rng.nextInt(GameData.EVENT_GAP_MAX_SEC - GameData.EVENT_GAP_MIN_SEC + 1)
        nextEventAtMs = elapsedMs + (type.durationSec + gap) * 1000f
        cue(SoundCue.EVENT, null)
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
            val target = pickTarget(field, tower, range, null)
            if (target == null) {
                // Nothing to shoot: a beam loses its charge.
                tower.rampTarget = null
                tower.rampBonus = 0f
                continue
            }
            tower.aimAngle = atan2(target.y - tower.y, target.x - tower.x)
            if (tower.cooldownMs > 0f) continue

            fire(field, tower, target, range)
            tower.cooldownMs = reloadMs(tower)
            tower.lastFiredAtMs = elapsedMs
        }
    }

    private fun canBeHit(e: EnemyUnit): Boolean = e.alive && e.x >= MIN_TARGET_X && !isPhased(e)

    private fun pickTarget(field: Battlefield, tower: TowerInstance, range: Float, skip: EnemyUnit?): EnemyUnit? {
        val r2 = range * range
        val huntsFlyers = tower.type.bonusDamageVsFlyerPct > 0f
        var best: EnemyUnit? = null
        var bestScore = Float.NEGATIVE_INFINITY
        // Units killed earlier this tick stay in the list until enemyPass, so skip them here.
        for (i in field.incomingEnemies.indices) {
            val e = field.incomingEnemies[i]
            if (e === skip || !canBeHit(e)) continue
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
            ShotKind.FROST_PULSE, ShotKind.POISON_PULSE, ShotKind.GUST_PULSE -> pulse(field, tower, range, damage)
            ShotKind.RAIL -> {
                field.fx.add(FxEvent(FxKind.TRACER, muzzleX(tower), muzzleY(tower), 180f, x2 = primary.x, y2 = primary.y, tower = tower.type))
                hit(field, tower, primary, damage)
                cue(SoundCue.SHOOT_HEAVY, field)
            }
            ShotKind.BEAM -> {
                // The longer the beam stays on one unit, the harder it burns.
                if (tower.rampTarget === primary) {
                    tower.rampBonus = (tower.rampBonus + tower.type.rampPerHit).coerceAtMost(tower.rampMax)
                } else {
                    tower.rampTarget = primary
                    tower.rampBonus = 0f
                }
                field.fx.add(
                    FxEvent(
                        FxKind.BEAM, muzzleX(tower), muzzleY(tower), 230f, x2 = primary.x, y2 = primary.y,
                        size = 0.5f + tower.rampBonus * 0.35f, tower = tower.type
                    )
                )
                hit(field, tower, primary, damage * (1f + tower.rampBonus))
                cue(SoundCue.SHOOT, field)
            }
            ShotKind.FLAME -> {
                field.fx.add(FxEvent(FxKind.FLAME, muzzleX(tower), muzzleY(tower), 260f, x2 = primary.x, y2 = primary.y, size = tower.splashRadius, seed = rng.nextInt()))
                val px = primary.x
                val py = primary.y
                for (i in field.incomingEnemies.indices) {
                    val e = field.incomingEnemies[i]
                    if (e === primary || !e.alive || dist(px, py, e.x, e.y) > tower.splashRadius) continue
                    hit(field, tower, e, damage, SPLASH_DAMAGE_FRACTION)
                }
                hit(field, tower, primary, damage)
                cue(SoundCue.SHOOT, field)
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
            ShotKind.GLAIVE -> {
                // Thrown straight at where the target is now; it cuts whatever is on that line.
                val angle = atan2(primary.y - tower.y, primary.x - tower.x)
                val p = Projectile(
                    ShotKind.GLAIVE, tower, null,
                    tower.x + cos(angle) * MUZZLE_OFFSET, tower.y + sin(angle) * MUZZLE_OFFSET,
                    primary.x, primary.y, GLAIVE_SPEED, damage
                )
                p.angle = angle
                p.cutsLeft = tower.pierce + 1
                p.travelLeft = range * GLAIVE_REACH
                field.projectiles.add(p)
                cue(SoundCue.SHOOT, field)
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

    /** One burst that reaches everything in range: cold, poison or wind. The effect itself is applied by [hit]. */
    private fun pulse(field: Battlefield, tower: TowerInstance, range: Float, damage: Float) {
        val kind = when (tower.type.shot) {
            ShotKind.FROST_PULSE -> FxKind.FROST_RING
            ShotKind.GUST_PULSE -> FxKind.GUST_RING
            else -> FxKind.POISON_CLOUD
        }
        field.fx.add(FxEvent(kind, tower.x, tower.y, 520f, size = range))
        for (i in field.incomingEnemies.indices) {
            val e = field.incomingEnemies[i]
            if (!e.alive || dist(tower.x, tower.y, e.x, e.y) > range) continue
            hit(field, tower, e, damage)
        }
        cue(
            when (tower.type.shot) {
                ShotKind.FROST_PULSE -> SoundCue.FREEZE
                ShotKind.GUST_PULSE -> SoundCue.SEND
                else -> SoundCue.SHOOT
            },
            field
        )
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
                if (!canBeHit(e) || e in struck) continue
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

            if (p.kind == ShotKind.GLAIVE) {
                val step = p.speed * dtSeconds
                p.x += cos(p.angle) * step
                p.y += sin(p.angle) * step
                p.travelLeft -= step
                for (j in field.incomingEnemies.indices) {
                    val e = field.incomingEnemies[j]
                    if (!canBeHit(e) || e in p.struck) continue
                    if (dist(p.x, p.y, e.x, e.y) > e.type.radius + GLAIVE_WIDTH) continue
                    p.struck.add(e)
                    field.fx.add(FxEvent(FxKind.HIT, e.x, e.y, 160f, size = 1.6f, tower = p.source.type))
                    hit(field, p.source, e, p.damage)
                    if (--p.cutsLeft <= 0) break
                }
                if (p.cutsLeft <= 0 || p.travelLeft <= 0f) p.done = true
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

    /**
     * Every way a tower hurts a unit ends here: the damage, and whatever the tower does on top of
     * it (slow, stun, poison, curse, knockback, execution).
     */
    private fun hit(field: Battlefield, tower: TowerInstance, target: EnemyUnit, damage: Float, fraction: Float = 1f) {
        if (!target.alive || isPhased(target)) return
        val type = tower.type
        val unit = target.type

        var dmg = damage * fraction
        val crit = tower.critChance > 0f && rng.nextFloat() < tower.critChance
        if (crit) dmg *= type.critMultiplier
        dmg = if (type.bonusDamageVsFlyerPct > 0f && unit.flying) {
            dmg * (1f + type.bonusDamageVsFlyerPct / 100f)
        } else {
            dmg * (1f - unit.damageResistancePct / 100f)
        }
        if (elapsedMs < target.vulnerableUntilMs) dmg *= 1f + target.vulnerability
        if (unit.armor > 0f) dmg = maxOf(dmg - unit.armor, dmg * (1f - ARMOR_MAX_REDUCTION))
        target.hp -= dmg.coerceAtLeast(0f)
        target.lastHitAtMs = elapsedMs
        if (crit) field.fx.add(FxEvent(FxKind.CRIT, target.x, target.y - unit.radius, 520f, size = unit.radius))

        // The strongest active slow, poison or curse wins; a weaker tower never overwrites it.
        if (tower.slow > 0f && !unit.controlImmune) {
            val stillSlowed = elapsedMs < target.slowExpiresAtMs
            target.slowFactor = if (stillSlowed) maxOf(target.slowFactor, tower.slow) else tower.slow
            target.slowExpiresAtMs = elapsedMs + type.slowDurationMs
        }
        if (tower.dotDps > 0f) {
            val stillPoisoned = elapsedMs < target.dotExpiresAtMs
            target.dotDps = if (stillPoisoned) maxOf(target.dotDps, tower.dotDps) else tower.dotDps
            target.dotExpiresAtMs = elapsedMs + type.dotDurationMs
            target.dotSource = tower
        }
        if (tower.vulnerability > 0f) {
            val stillCursed = elapsedMs < target.vulnerableUntilMs
            target.vulnerability = if (stillCursed) maxOf(target.vulnerability, tower.vulnerability) else tower.vulnerability
            target.vulnerableUntilMs = elapsedMs + type.vulnerabilityMs
        }
        if (tower.stunChance > 0f && !unit.controlImmune && rng.nextFloat() < tower.stunChance &&
            elapsedMs >= target.stunExpiresAtMs + STUN_IMMUNITY_MS
        ) {
            target.stunExpiresAtMs = elapsedMs + type.stunDurationMs
        }
        if (tower.knockback > 0f && !unit.controlImmune) {
            target.dist = (target.dist - tower.knockback / (1f + target.maxHp / KNOCKBACK_HALF_HP)).coerceAtLeast(0f)
            place(target)
        }

        if (target.hp <= 0f) {
            kill(field, target, tower)
        } else if (tower.executeBelow > 0f && target.hp < target.maxHp * tower.executeBelow) {
            field.fx.add(FxEvent(FxKind.EXECUTE, target.x, target.y, 380f, size = unit.radius))
            kill(field, target, tower)
        }
    }

    private fun kill(field: Battlefield, enemy: EnemyUnit, by: TowerInstance?) {
        enemy.alive = false
        if (by != null) by.kills++
        field.stats.kills++
        val bounty = (enemy.type.bountyGold * modifier.bountyMultiplier * (1f + (by?.bountyBonus ?: 0f))).roundToInt()
        earn(field, bounty.toFloat())
        field.fx.add(FxEvent(FxKind.POP, enemy.x, enemy.y, 420f, size = enemy.type.radius, unit = enemy.type, seed = rng.nextInt()))
        if (bounty > 0) {
            field.fx.add(FxEvent(FxKind.GOLD_TEXT, enemy.x, enemy.y - enemy.type.radius, 800f, value = bounty))
        }
        cue(if (enemy.type.radius >= 3f) SoundCue.POP_BIG else SoundCue.POP, field)

        val childId = enemy.type.spawnOnDeathId ?: return
        val childType = GameData.unit(childId)
        val hpScale = enemy.maxHp / (enemy.type.maxHp * modifier.unitHpMultiplier)
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
            field.nextInstanceId++, type, type.maxHp * hpScale * modifier.unitHpMultiplier,
            laneOffset = (rng.nextFloat() * 2f - 1f) * MAX_LANE_OFFSET,
            speedScale = speedScale, bornAtMs = elapsedMs
        )

    private fun place(enemy: EnemyUnit) {
        path.sample(enemy.dist, scratch)
        enemy.dirX = scratch[2]
        enemy.dirY = scratch[3]
        enemy.x = scratch[0] - scratch[3] * enemy.laneOffset
        enemy.y = scratch[1] + scratch[2] * enemy.laneOffset
        enemy.progress = (enemy.dist / path.length).coerceIn(0f, 1f)
    }

    /** Lane units per second [enemy] is moving right now, with slows, stuns, haste, the match rule and any event applied. */
    private fun unitSpeed(enemy: EnemyUnit): Float {
        val status = when {
            elapsedMs < enemy.stunExpiresAtMs -> 0f
            elapsedMs < enemy.slowExpiresAtMs -> 1f - enemy.slowFactor
            else -> 1f
        }
        val haste = if (elapsedMs < enemy.hasteUntilMs) 1f + enemy.haste else 1f
        val weather = when (activeEvent()) {
            MatchEventType.STAMPEDE -> STAMPEDE_SPEED
            MatchEventType.COLD_SNAP -> COLD_SNAP_SPEED
            else -> 1f
        }
        return enemy.type.speed * enemy.speedScale * modifier.speedMultiplier * status * haste * weather
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

        // Auras first, so this tick's heal and haste apply before hp checks and movement.
        val auraFx = (elapsedMs / HEAL_FX_INTERVAL_MS).toInt() != ((elapsedMs - dtMs) / HEAL_FX_INTERVAL_MS).toInt()
        for (i in enemies.indices) {
            val source = enemies[i]
            if (!source.alive) continue
            val heals = source.type.healPerSecond > 0f
            val hastens = source.type.hasteAuraPct > 0f
            if (!heals && !hastens) continue
            val radius = if (heals) source.type.healRadius else source.type.hasteRadius
            for (j in enemies.indices) {
                val target = enemies[j]
                if (!target.alive || abs(target.dist - source.dist) > radius) continue
                if (heals) target.hp = (target.hp + source.type.healPerSecond * dtSeconds).coerceAtMost(target.maxHp)
                if (hastens) {
                    target.haste = source.type.hasteAuraPct / 100f
                    target.hasteUntilMs = elapsedMs + HASTE_LINGER_MS
                }
            }
            if (auraFx && source.x >= MIN_TARGET_X) {
                field.fx.add(FxEvent(if (heals) FxKind.HEAL else FxKind.HASTE, source.x, source.y, 600f, size = radius * 0.55f))
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
            if (enemy.type.regenPerSecond > 0f && elapsedMs - enemy.lastHitAtMs > REGEN_DELAY_MS) {
                enemy.hp = (enemy.hp + enemy.type.regenPerSecond * dtSeconds).coerceAtMost(enemy.maxHp)
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
