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
import kotlin.math.ceil
import kotlin.math.roundToInt
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

/** A unit this heavy is thrown back half as far as a featherweight. The AI reads it to judge knockback. */
const val KNOCKBACK_HALF_HP = 300f

/** Regeneration waits this long after the last hit. */
private const val REGEN_DELAY_MS = 1000f

/** A Drummer's haste lasts this long after leaving its aura. */
private const val HASTE_LINGER_MS = 300f

/** A tower that was just jammed cannot be jammed again for this long once it recovers. */
private const val JAM_IMMUNITY_MS = 2000f

/**
 * Support towers stack, but only so far: towers stand close enough now for thirty Beacons to
 * surround one Sniper. A tower gets at most this much extra damage, fire rate and reach from them.
 */
const val MAX_DAMAGE_AURA = 1.5f
const val MAX_RELOAD_AURA = 1f
const val MAX_RANGE_AURA = 0.75f

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
private const val SECOND_WIND_LIVES = 10
private const val BOUNTY_RUSH_PAY = 3f
private const val CLEAR_SKIES_RANGE = 1.2f
private const val THUNDERCLAP_SHARE = 0.33f
private const val RECRUITING_INCOME = 2f
private const val IRON_HIDE_DAMAGE = 0.7f
private const val TAX_DAY_SHARE = 0.25f
private const val RALLY_COOLDOWN = 0.5f

/** Lives a keep stands back up with under the Second Chance rule. */
private const val REVIVE_LIVES = 25

/** A Railgun's shot carries this much past its own range, and catches units this far off its line. */
private const val RAIL_REACH = 1.15f
private const val RAIL_WIDTH = 1.2f

class GameEngine(
    val map: MapDef,
    val modifier: MatchModifier,
    playerDraft: List<TroopType>,
    aiDraft: List<TroopType>,
    /** The units both sides may send this match, and what its waves are made of. */
    val roster: List<EnemySendType> = GameData.CLASSIC_ROSTER,
    private val seed: Long = Random.nextLong(),
    /** For a 2 v 2: the hand of the second seat on each lane. Give both or neither. */
    allyDraft: List<TroopType>? = null,
    aiAllyDraft: List<TroopType>? = null
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

    /** The second seat on each lane in a 2 v 2: its own purse and hand, the same lane as its partner. */
    val allyField: Battlefield? = allyDraft?.let { seatBeside(playerField, "ally", it) }
    val aiAllyField: Battlefield? = aiAllyDraft?.let { seatBeside(aiField, "ai-ally", it) }
    val isTeamMatch: Boolean get() = allyField != null || aiAllyField != null

    /** Every purse in the match; and one seat per lane, for whatever happens once to a lane. */
    private val seats: List<Battlefield> = listOfNotNull(playerField, allyField, aiField, aiAllyField)
    private val lanes = arrayOf(playerField, aiField)

    // Two purses behind every lane build twice the wall, so the units walking it are made to match.
    private val teamHp = if (allyDraft != null || aiAllyDraft != null) GameData.TEAM_WAVE_HP else 1f
    private val teamSize = if (allyDraft != null || aiAllyDraft != null) GameData.TEAM_WAVE_SIZE else 1f

    init {
        for (seat in lanes) seat.lane.revivesLeft = modifier.revives
    }

    private fun seatBeside(first: Battlefield, label: String, draft: List<TroopType>): Battlefield {
        val seat = Battlefield(label, draft, startingGold.toFloat(), first.lane)
        seat.partner = first
        first.partner = seat
        return seat
    }

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
        (GameData.FIRST_EVENT_MIN_SEC + rng.nextInt(GameData.FIRST_EVENT_MAX_SEC - GameData.FIRST_EVENT_MIN_SEC + 1)) *
            1000f * modifier.eventGapMultiplier

    /** Sounds waiting to be played. Whoever owns the engine drains this every frame. */
    val cues = ArrayList<CueEvent>()

    // Units born from a kill (Splitlings) wait here until the lane's lists are safe to change.
    private val newborns = ArrayList<EnemyUnit>()

    // Scratch list for a Railgun's shot: the units standing on its line.
    private val onLine = ArrayList<EnemyUnit>()

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

    private fun rangeFactor(): Float = modifier.rangeMultiplier * when (activeEvent()) {
        MatchEventType.FOG -> FOG_RANGE
        MatchEventType.CLEAR_SKIES -> CLEAR_SKIES_RANGE
        else -> 1f
    }

    /** How far [tower] reaches in lane units: its attack range, or its aura for support towers. */
    fun towerReach(tower: TowerInstance): Float =
        if (tower.type.auraRange > 0f) tower.auraRange else tower.range * rangeFactor() * (1f + tower.rangeBonus)

    /** The reach a freshly placed [type] would have. */
    fun baseReach(type: TroopType): Float =
        if (type.auraRange > 0f) type.auraRange else type.range * rangeFactor()

    /** Damage of one shot from [tower], with the match rule, Beacon boosts, its own kills and any event applied. */
    fun shotDamage(tower: TowerInstance): Float =
        tower.damage * modifier.damageMultiplier * (1f + tower.auraBonus) * (1f + tower.killBonus) *
            (if (activeEvent() == MatchEventType.POWER_SURGE) SURGE_DAMAGE else 1f)

    /** Milliseconds between [tower]'s shots right now. */
    fun reloadMs(tower: TowerInstance): Float =
        tower.reloadMs * modifier.reloadMultiplier / (1f + tower.reloadBonus) *
            (if (activeEvent() == MatchEventType.OVERDRIVE) OVERDRIVE_RELOAD else 1f)

    fun sellRefund(tower: TowerInstance): Int =
        (tower.invested * (modifier.sellRefundFraction ?: SELL_REFUND_FRACTION)).toInt()

    private fun incomeFactor(): Float =
        modifier.incomeMultiplier * (if (activeEvent() == MatchEventType.PAYDAY) PAYDAY_INCOME else 1f)

    fun incomePerSec(field: Battlefield): Float {
        var mines = 0f
        for (t in field.towers) if (t.owner === field) mines += t.income
        return (GameData.BASE_INCOME_PER_SEC + field.ecoIncome) * incomeFactor() + mines
    }

    /** The gold [field]'s whole team holds: its own purse and, in a 2 v 2, its partner's. */
    fun teamGold(field: Battlefield): Float = field.gold + (field.partner?.gold ?: 0f)

    // -------------------------------------------------------------------
    // Seats by number, commands, and what two phones compare
    // -------------------------------------------------------------------

    /** The seat with that number, or null if this match has none: 0 and 1 defend the first lane, 2 and 3 the second. */
    fun seat(index: Int): Battlefield? = when (index) {
        0 -> playerField
        1 -> allyField
        2 -> aiField
        3 -> aiAllyField
        else -> null
    }

    fun seatIndex(field: Battlefield): Int = when {
        field === playerField -> 0
        field === allyField -> 1
        field === aiField -> 2
        else -> 3
    }

    /** The seat [field]'s sends walk towards: the first seat of the other lane. */
    fun foeOf(field: Battlefield): Battlefield = if (field.lane === playerField.lane) aiField else playerField

    /** The first seat of [field]'s own lane: the one a lane is drawn and ticked through. */
    fun laneOf(field: Battlefield): Battlefield = if (field.lane === playerField.lane) playerField else aiField

    /** [outcome] as [field] sees it: PLAYER_WIN if its lane is the one left standing. */
    fun outcomeFor(field: Battlefield): MatchOutcome = when (outcome) {
        MatchOutcome.PLAYER_WIN -> if (field.lane === playerField.lane) MatchOutcome.PLAYER_WIN else MatchOutcome.AI_WIN
        MatchOutcome.AI_WIN -> if (field.lane === playerField.lane) MatchOutcome.AI_WIN else MatchOutcome.PLAYER_WIN
        else -> outcome
    }

    /**
     * Carries out [command] for its seat. Whatever the seat may not do right now (too little gold,
     * a spot that is taken, somebody else's tower) is quietly not done, exactly as for a local tap,
     * so a command from another phone needs no checking beyond this.
     */
    fun apply(command: Command) {
        val field = seat(command.seat) ?: return
        when (command) {
            is Command.Place -> {
                val type = field.draftedTroops.firstOrNull { it.id == command.towerId } ?: return
                placeTower(field, type, command.x, command.y)
            }
            is Command.Upgrade -> upgradeTower(field, command.instanceId)
            is Command.Sell -> sellTower(field, command.instanceId)
            is Command.Retarget -> cycleTargeting(field, command.instanceId)
            is Command.Send -> {
                val unit = roster.firstOrNull { it.id == command.unitId } ?: return
                sendEnemy(field, foeOf(field), unit)
            }
            is Command.HandOver -> Unit // whoever runs the match gives the seat a bot
        }
    }

    /**
     * A fingerprint of everything that matters in the match right now. Two phones that have run
     * the same commands must get the same number; if they ever do not, they are no longer playing
     * the same match.
     */
    fun checksum(): Int {
        var h = round * 31 + elapsedMs.toRawBits()
        for (seat in seats) {
            h = h * 31 + seat.gold.toRawBits()
            h = h * 31 + seat.ecoIncome.toRawBits()
        }
        for (seat in lanes) {
            h = h * 31 + seat.lives
            h = h * 31 + seat.towers.size
            for (t in seat.towers) h = (h * 31 + t.level) * 31 + t.kills
            h = h * 31 + seat.incomingEnemies.size
            for (e in seat.incomingEnemies) h = (h * 31 + e.hp.toRawBits()) * 31 + e.dist.toRawBits()
            h = h * 31 + seat.projectiles.size
        }
        return h
    }

    /** Income a send of [type] adds under this match's rules. */
    fun sendIncome(type: EnemySendType): Float = type.incomeBonus * modifier.sendIncomeMultiplier

    /** Milliseconds before [type] can be sent again under this match's rules. */
    fun sendCooldownMs(type: EnemySendType): Float =
        type.cooldownMs * modifier.sendCooldownMultiplier * (if (activeEvent() == MatchEventType.RALLY) RALLY_COOLDOWN else 1f)

    /** The wave level (1..WAVE_LEVELS) the match is at in [forRound]; also what gates the bigger sends. */
    private fun waveLevel(forRound: Int): Int =
        ceil(forRound * levelStride).toInt().coerceIn(1, GameData.WAVE_LEVELS)

    fun isUnlocked(type: EnemySendType): Boolean = type.unlockRound <= waveLevel(round)

    /** The round in which [type] becomes sendable in this match. */
    fun unlockRoundOf(type: EnemySendType): Int = ceil(type.unlockRound / levelStride).toInt().coerceAtLeast(1)

    /** 0 when [field] may send [type] again, counting down from 1 right after a send. */
    fun sendCooldownFraction(field: Battlefield, type: EnemySendType): Float {
        val readyAt = field.sendReadyAtMs[type.id] ?: return 0f
        return ((readyAt - elapsedMs) / sendCooldownMs(type)).coerceIn(0f, 1f)
    }

    /** True while [enemy] is still tunnelling under the first stretch of the track. */
    fun isBurrowed(enemy: EnemyUnit): Boolean = enemy.progress < enemy.type.burrowUntil

    /** True while [enemy] is out of reach, faded out or underground: it cannot be targeted or hurt. */
    fun isPhased(enemy: EnemyUnit): Boolean {
        if (isBurrowed(enemy)) return true
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
        val tower = TowerInstance(field.nextInstanceId++, type, cx, cy, elapsedMs)
        tower.owner = field
        repeat(modifier.freeTowerLevels) { tower.applyNextUpgrade(elapsedMs, free = true) }
        field.towers.add(tower)
        field.stats.towersBuilt++
        refreshAuras(field)
        field.fx.add(FxEvent(FxKind.DUST, cx, cy, 420f, size = 4.5f))
        cue(SoundCue.PLACE, field)
        return PlaceResult.OK
    }

    fun upgradeTower(field: Battlefield, instanceId: Long): Boolean {
        if (outcome != MatchOutcome.ONGOING) return false
        val t = ownTower(field, instanceId) ?: return false
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
        val t = ownTower(field, instanceId) ?: return false
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
        val t = ownTower(field, instanceId) ?: return false
        if (!usesTargeting(t.type)) return false
        val all = TargetPriority.entries
        t.targeting = all[(t.targeting.ordinal + 1) % all.size]
        return true
    }

    fun usesTargeting(type: TroopType): Boolean = type.isAttacker && !type.shot.isPulse

    /** [field]'s own tower with that id. A teammate's tower on the same lane is not: only its builder runs it. */
    private fun ownTower(field: Battlefield, instanceId: Long): TowerInstance? =
        field.towers.find { it.instanceId == instanceId && it.owner === field }

    /** What [source] sending [type] would do right now, without doing it. */
    fun checkSend(source: Battlefield, type: EnemySendType): SendResult {
        if (outcome != MatchOutcome.ONGOING) return SendResult.MATCH_OVER
        if (!type.sendable || type !in roster || !isUnlocked(type)) return SendResult.LOCKED
        if (elapsedMs < (source.sendReadyAtMs[type.id] ?: 0f)) return SendResult.COOLING_DOWN
        if (source.gold < type.cost) return SendResult.NOT_ENOUGH_GOLD
        return SendResult.OK
    }

    fun sendEnemy(source: Battlefield, target: Battlefield, type: EnemySendType): SendResult {
        val result = checkSend(source, type)
        if (result != SendResult.OK) return result
        source.gold -= type.cost
        source.ecoIncome += sendIncome(type) * (if (activeEvent() == MatchEventType.RECRUITING) RECRUITING_INCOME else 1f)
        source.sendReadyAtMs[type.id] = elapsedMs + sendCooldownMs(type)
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

        for (seat in seats) earn(seat, (GameData.BASE_INCOME_PER_SEC + seat.ecoIncome) * incomeFactor() * dtSeconds)
        for (seat in lanes) tickLane(seat, dtSeconds)

        resolveOutcome()
    }

    /** Everything that happens on [field]'s lane this tick. Called once per lane, however many seats share it. */
    private fun tickLane(field: Battlefield, dtSeconds: Float) {
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
        WaveGenerator.generate(waveLevel(forRound), roster, Random(seed * 31L + forRound), modifier.waveSizeMultiplier * teamSize)
    }

    private fun levelHpScale(level: Int): Float {
        val late = (level - GameData.WAVE_LATE_FROM_LEVEL).coerceAtLeast(0)
        return (1f + GameData.WAVE_HP_GROWTH_PER_LEVEL * (level - 1)) * GameData.WAVE_LATE_GROWTH.pow(late) * teamHp
    }

    private fun waveHpScaleFor(forRound: Int): Float {
        if (forRound <= totalRounds) return levelHpScale(waveLevel(forRound))
        // Sudden death: each wave much tougher than the one before, so the match cannot drag on.
        return levelHpScale(GameData.WAVE_LEVELS) * GameData.OVERTIME_HP_GROWTH.pow(forRound - totalRounds)
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
                for (seat in seats) earn(seat, gold)
            }
            MatchEventType.TAX_DAY -> {
                for (seat in seats) seat.gold -= seat.gold * TAX_DAY_SHARE
            }
            MatchEventType.SECOND_WIND -> {
                for (field in arrayOf(playerField, aiField)) {
                    field.lives = (field.lives + SECOND_WIND_LIVES).coerceAtMost(maxOf(startingLives, field.lives))
                }
            }
            MatchEventType.AMBUSH -> {
                val level = waveLevel(round.coerceAtLeast(1))
                val wave = WaveGenerator.generate(level, roster, rng, AMBUSH_WAVE_SCALE * modifier.waveSizeMultiplier * teamSize)
                release(wave, waveHpScaleFor(round.coerceAtLeast(1)), waveSpeedScaleFor(round))
            }
            MatchEventType.THUNDERCLAP -> {
                // Hurts, never kills: what is left still has to be shot down.
                for (field in arrayOf(playerField, aiField)) {
                    for (e in field.incomingEnemies) {
                        if (!canBeHit(e)) continue
                        e.hp -= e.hp * THUNDERCLAP_SHARE
                        e.lastHitAtMs = elapsedMs
                        field.fx.add(FxEvent(FxKind.BOLT, e.x + 3f, e.y - 16f, 260f, x2 = e.x, y2 = e.y, seed = rng.nextInt()))
                    }
                }
            }
            MatchEventType.TINKER -> {
                for (field in arrayOf(playerField, aiField)) {
                    val tower = field.towers.filter { it.nextUpgrade != null }.randomOrNull(rng) ?: continue
                    tower.applyNextUpgrade(elapsedMs, free = true)
                    refreshAuras(field)
                    field.fx.add(FxEvent(FxKind.SPARKLE, tower.x, tower.y, 900f, size = 6f, seed = rng.nextInt()))
                }
            }
            else -> Unit // a timed effect: read wherever it applies, through activeEvent()
        }
        val gap = (GameData.EVENT_GAP_MIN_SEC + rng.nextInt(GameData.EVENT_GAP_MAX_SEC - GameData.EVENT_GAP_MIN_SEC + 1)) *
            modifier.eventGapMultiplier
        nextEventAtMs = elapsedMs + (type.durationSec + gap) * 1000f
        cue(SoundCue.EVENT, null)
    }

    private fun resolveOutcome() {
        for (seat in lanes) {
            val lane = seat.lane
            if (lane.lives > 0 || lane.revivesLeft <= 0) continue
            // Second Chance: the keep stands back up, and whatever was on its lane scatters.
            lane.revivesLeft--
            lane.lives = REVIVE_LIVES
            for (e in lane.incomingEnemies) e.alive = false
            lane.pendingSpawns.clear()
            val last = path.pointCount - 1
            lane.fx.add(FxEvent(FxKind.SPARKLE, path.xs[last], path.ys[last], 1200f, size = 9f, seed = rng.nextInt()))
            lane.fx.add(FxEvent(FxKind.LIFE_GAIN, path.xs[last], path.ys[last] - 6f, 1400f, value = REVIVE_LIVES))
            cue(SoundCue.UPGRADE, seat)
        }
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
        val blackout = activeEvent() == MatchEventType.BLACKOUT
        for (i in field.towers.indices) {
            val tower = field.towers[i]

            // Gold Mines bank their income and pay it out in visible lumps.
            if (tower.income > 0f) {
                tower.payoutBank += tower.income * dtSeconds
                if (tower.payoutBank >= MINE_PAYOUT_GOLD) {
                    val payout = tower.payoutBank.toInt()
                    earn(tower.owner ?: field, payout.toFloat())
                    tower.payoutBank -= payout
                    tower.lastFiredAtMs = elapsedMs
                    field.fx.add(FxEvent(FxKind.GOLD_TEXT, tower.x, tower.y - 3f, 900f, value = payout))
                    cue(SoundCue.COIN, field)
                }
            }

            // Shrines give back lives one at a time, never past what the match started with.
            if (tower.livesPerMinute > 0f) {
                tower.lifeBank = (tower.lifeBank + tower.livesPerMinute / 60f * dtSeconds).coerceAtMost(1f)
                if (tower.lifeBank >= 1f && field.lives < startingLives) {
                    tower.lifeBank = 0f
                    field.lives++
                    tower.lastFiredAtMs = elapsedMs
                    field.fx.add(FxEvent(FxKind.LIFE_GAIN, tower.x, tower.y - 3f, 1000f, value = 1))
                    cue(SoundCue.COIN, field)
                }
            }

            tower.cooldownMs -= dtSeconds * 1000f
            if (!tower.type.isAttacker) continue
            if (blackout || elapsedMs < tower.jammedUntilMs) {
                tower.rampTarget = null
                tower.rampBonus = 0f
                continue
            }

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
        // A unit with enough shots already on their way to kill it is only a target if there is no other:
        // a cluster of towers spreads its fire instead of all burying the same Runner.
        var doomed: EnemyUnit? = null
        var doomedScore = Float.NEGATIVE_INFINITY
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
            // A Decoy is what it says: whatever can reach it shoots it first.
            if (e.type.taunts) score += 2_000_000f
            if (e.pendingDamage >= e.hp && e.shieldLeft == 0) {
                if (score > doomedScore) {
                    doomedScore = score
                    doomed = e
                }
            } else if (score > bestScore) {
                bestScore = score
                best = e
            }
        }
        return best ?: doomed
    }

    private fun fire(field: Battlefield, tower: TowerInstance, primary: EnemyUnit, range: Float) {
        val damage = shotDamage(tower)
        when (tower.type.shot) {
            ShotKind.NONE -> Unit
            ShotKind.FROST_PULSE, ShotKind.POISON_PULSE, ShotKind.GUST_PULSE, ShotKind.QUAKE_PULSE, ShotKind.NOVA_PULSE ->
                pulse(field, tower, range, damage)
            ShotKind.RAIL -> {
                if (tower.pierce > 0) {
                    railLine(field, tower, primary, damage, range)
                } else {
                    field.fx.add(FxEvent(FxKind.TRACER, muzzleX(tower), muzzleY(tower), 180f, x2 = primary.x, y2 = primary.y, tower = tower.type))
                    hit(field, tower, primary, damage)
                }
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
                val heavy = tower.type.shot == ShotKind.SHELL || tower.type.shot == ShotKind.ROCKET
                cue(if (heavy) SoundCue.SHOOT_HEAVY else SoundCue.SHOOT, field)
            }
        }
    }

    /**
     * A Railgun's shot: through [primary] and on along the same straight line, into as many more
     * units as it can pierce, the nearest to the tower first.
     */
    private fun railLine(field: Battlefield, tower: TowerInstance, primary: EnemyUnit, damage: Float, range: Float) {
        val dx = primary.x - tower.x
        val dy = primary.y - tower.y
        val len = sqrt(dx * dx + dy * dy).coerceAtLeast(0.01f)
        val ux = dx / len
        val uy = dy / len
        val reach = maxOf(range * RAIL_REACH, len)
        field.fx.add(
            FxEvent(FxKind.TRACER, muzzleX(tower), muzzleY(tower), 220f, x2 = tower.x + ux * reach, y2 = tower.y + uy * reach, tower = tower.type)
        )
        onLine.clear()
        for (i in field.incomingEnemies.indices) {
            val e = field.incomingEnemies[i]
            if (e === primary || !canBeHit(e)) continue
            val px = e.x - tower.x
            val py = e.y - tower.y
            val along = px * ux + py * uy
            if (along < 0f || along > reach || abs(px * uy - py * ux) > e.type.radius + RAIL_WIDTH) continue
            onLine.add(e)
        }
        onLine.sortBy { (it.x - tower.x) * ux + (it.y - tower.y) * uy }
        hit(field, tower, primary, damage)
        for (i in 0 until minOf(tower.pierce, onLine.size)) {
            val e = onLine[i]
            field.fx.add(FxEvent(FxKind.HIT, e.x, e.y, 160f, size = 1.6f, tower = tower.type))
            hit(field, tower, e, damage)
        }
    }

    private fun launch(field: Battlefield, tower: TowerInstance, target: EnemyUnit, damage: Float) {
        val speed = when (tower.type.shot) {
            ShotKind.BULLET -> 95f
            ShotKind.SHELL -> 52f
            ShotKind.NET -> 68f
            ShotKind.ORB -> 62f
            ShotKind.ROCKET -> 58f
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
        target.pendingDamage += damage
    }

    /** One burst that reaches everything in range: cold, poison, wind, a quake or raw energy. The effect itself is applied by [hit]. */
    private fun pulse(field: Battlefield, tower: TowerInstance, range: Float, damage: Float) {
        val kind = when (tower.type.shot) {
            ShotKind.FROST_PULSE -> FxKind.FROST_RING
            ShotKind.GUST_PULSE -> FxKind.GUST_RING
            ShotKind.QUAKE_PULSE -> FxKind.QUAKE_RING
            ShotKind.NOVA_PULSE -> FxKind.NOVA_RING
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
                ShotKind.QUAKE_PULSE -> SoundCue.BOOM
                ShotKind.NOVA_PULSE -> SoundCue.ZAP
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
            var damage = 0f
            var reload = 0f
            var reach = 0f
            for (other in field.towers) {
                if (other === tower || other.type.auraRange <= 0f) continue
                if (dist(other.x, other.y, tower.x, tower.y) > other.auraRange) continue
                damage += other.auraPct / 100f
                reload += other.auraReloadPct / 100f
                reach += other.auraRangePct / 100f
            }
            tower.auraBonus = damage.coerceAtMost(MAX_DAMAGE_AURA)
            tower.reloadBonus = reload.coerceAtMost(MAX_RELOAD_AURA)
            tower.rangeBonus = reach.coerceAtMost(MAX_RANGE_AURA)
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
                if (target != null) target.pendingDamage -= p.damage
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
     * it (slow, stun, poison, curse, knockback, cracked armour, execution).
     */
    private fun hit(field: Battlefield, tower: TowerInstance, target: EnemyUnit, damage: Float, fraction: Float = 1f) {
        if (!target.alive || isPhased(target)) return
        val type = tower.type
        val unit = target.type

        // A bubble swallows the whole hit, effects and all, whatever its size.
        if (target.shieldLeft > 0) {
            target.shieldLeft--
            field.fx.add(FxEvent(FxKind.BUBBLE_HIT, target.x, target.y, 260f, size = unit.radius))
            return
        }
        if (type.sundersArmor) target.sundered = true
        val armor = if (target.sundered) 0f else unit.armor
        val resistance = if (target.sundered) 0f else unit.damageResistancePct

        var dmg = damage * fraction
        val crit = tower.critChance > 0f && rng.nextFloat() < tower.critChance
        if (crit) dmg *= type.critMultiplier
        dmg = if (type.bonusDamageVsFlyerPct > 0f && unit.flying) {
            dmg * (1f + type.bonusDamageVsFlyerPct / 100f)
        } else {
            dmg * (1f - resistance / 100f)
        }
        if (tower.controlBonus > 0f && (elapsedMs < target.slowExpiresAtMs || elapsedMs < target.stunExpiresAtMs)) {
            dmg *= 1f + tower.controlBonus
        }
        if (tower.bigBonus > 0f && unit.maxHp >= GameData.BIG_UNIT_HP) dmg *= 1f + tower.bigBonus
        if (elapsedMs < target.vulnerableUntilMs) dmg *= 1f + target.vulnerability
        if (elapsedMs < target.wardUntilMs) dmg *= 1f - target.ward
        if (activeEvent() == MatchEventType.IRON_HIDE) dmg *= IRON_HIDE_DAMAGE
        if (armor > 0f) dmg = maxOf(dmg - armor, dmg * (1f - ARMOR_MAX_REDUCTION))
        // A Tortoise's shell takes the top off any big hit, until a Ballista has cracked it.
        if (unit.maxHitPct > 0f && !target.sundered) dmg = minOf(dmg, target.maxHp * unit.maxHitPct / 100f)
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
        (by?.owner ?: field).stats.kills++
        val rush = if (activeEvent() == MatchEventType.BOUNTY_RUSH) BOUNTY_RUSH_PAY else 1f
        val bounty = (enemy.type.bountyGold * modifier.bountyMultiplier * rush * (1f + (by?.bountyBonus ?: 0f))).roundToInt()
        earnShared(field, bounty.toFloat())
        field.fx.add(FxEvent(FxKind.POP, enemy.x, enemy.y, 420f, size = enemy.type.radius, unit = enemy.type, seed = rng.nextInt()))
        if (bounty > 0) {
            field.fx.add(FxEvent(FxKind.GOLD_TEXT, enemy.x, enemy.y - enemy.type.radius, 800f, value = bounty))
        }
        cue(if (enemy.type.radius >= 3f) SoundCue.POP_BIG else SoundCue.POP, field)

        if (enemy.type.jamOnDeathMs > 0L) jamTowers(field, enemy)

        // A Blighter's rot outlives its carrier: it jumps to whatever stood close by and is not rotting yet.
        val rot = enemy.dotSource
        if (rot != null && rot.dotSpread > 0f && elapsedMs < enemy.dotExpiresAtMs) {
            field.fx.add(FxEvent(FxKind.POISON_CLOUD, enemy.x, enemy.y, 520f, size = rot.dotSpread))
            for (i in field.incomingEnemies.indices) {
                val e = field.incomingEnemies[i]
                if (!e.alive || elapsedMs < e.dotExpiresAtMs || dist(enemy.x, enemy.y, e.x, e.y) > rot.dotSpread) continue
                hit(field, rot, e, 0f)
            }
        }

        val childId = enemy.type.spawnOnDeathId
        if (childId != null) {
            val childType = GameData.unit(childId)
            for (i in 0 until enemy.type.spawnOnDeathCount) {
                val child = newEnemy(field, childType, hpScaleOf(enemy), enemy.speedScale)
                child.dist = (enemy.dist - i * 1.6f).coerceAtLeast(0f)
                place(child)
                newborns.add(child)
            }
        }

        // A Detonator's kill goes off. The blast is a hit like any other, so it can set off the next one.
        if (by != null && by.deathBlast > 0f) {
            val blast = enemy.maxHp * by.deathBlast
            field.fx.add(FxEvent(FxKind.EXPLOSION, enemy.x, enemy.y, 380f, size = by.blastRadius, seed = rng.nextInt()))
            for (i in field.incomingEnemies.indices) {
                val e = field.incomingEnemies[i]
                if (!e.alive || dist(enemy.x, enemy.y, e.x, e.y) > by.blastRadius) continue
                hit(field, by, e, blast)
            }
            cue(SoundCue.BOOM, field)
        }
    }

    /** How much tougher than its listed health [enemy] was made: what its offspring inherit. */
    private fun hpScaleOf(enemy: EnemyUnit): Float = enemy.maxHp / (enemy.type.maxHp * modifier.unitHpMultiplier)

    /** A Jammer popped: the towers around it stop firing for a moment. */
    private fun jamTowers(field: Battlefield, enemy: EnemyUnit) {
        val radius = enemy.type.jamRadius
        for (t in field.towers) {
            if (!t.type.isAttacker || dist(t.x, t.y, enemy.x, enemy.y) > radius) continue
            // Still jammed, or only just recovered: a column of Jammers cannot hold a tower down for good.
            if (elapsedMs < t.jammedUntilMs + JAM_IMMUNITY_MS) continue
            t.jammedUntilMs = elapsedMs + enemy.type.jamOnDeathMs
        }
        field.fx.add(FxEvent(FxKind.JAM_RING, enemy.x, enemy.y, 520f, size = radius))
        cue(SoundCue.ZAP, field)
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
        // A Berserker speeds up in step with the health it has lost.
        val rage = 1f + enemy.type.enrageSpeedPct / 100f * (1f - enemy.hp / enemy.maxHp).coerceIn(0f, 1f)
        // A Lancer charges until something lands on it.
        val charge = if (enemy.type.chargeSpeedPct > 0f && enemy.lastHitAtMs < enemy.bornAtMs) 1f + enemy.type.chargeSpeedPct / 100f else 1f
        val weather = when (activeEvent()) {
            MatchEventType.STAMPEDE -> STAMPEDE_SPEED
            MatchEventType.COLD_SNAP -> COLD_SNAP_SPEED
            else -> 1f
        }
        return enemy.type.speed * enemy.speedScale * modifier.speedMultiplier * status * haste * rage * charge * weather
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
            val wards = source.type.wardAuraPct > 0f
            val cleanses = source.type.cleanseRadius > 0f
            if (!heals && !hastens && !wards && !cleanses) continue
            val radius = when {
                heals -> source.type.healRadius
                hastens -> source.type.hasteRadius
                wards -> source.type.wardRadius
                else -> source.type.cleanseRadius
            }
            for (j in enemies.indices) {
                val target = enemies[j]
                if (!target.alive || abs(target.dist - source.dist) > radius) continue
                // A healer mends as much more as it is itself tougher than listed, so it never goes stale.
                if (heals) target.hp = (target.hp + source.type.healPerSecond * hpScaleOf(source) * dtSeconds).coerceAtMost(target.maxHp)
                if (hastens) {
                    target.haste = source.type.hasteAuraPct / 100f
                    target.hasteUntilMs = elapsedMs + HASTE_LINGER_MS
                }
                // A Warder shields the others, not itself: popping it first is the answer.
                if (wards && target !== source) {
                    target.ward = source.type.wardAuraPct / 100f
                    target.wardUntilMs = elapsedMs + HASTE_LINGER_MS
                }
                // A Monk, like a Warder, looks after the others and not itself.
                if (cleanses && target !== source) {
                    target.slowExpiresAtMs = 0f
                    target.dotExpiresAtMs = 0f
                    target.vulnerableUntilMs = 0f
                }
            }
            if (auraFx && source.x >= MIN_TARGET_X && !isBurrowed(source)) {
                val kind = when {
                    heals -> FxKind.HEAL
                    hastens -> FxKind.HASTE
                    wards -> FxKind.WARD
                    else -> FxKind.CLEANSE
                }
                field.fx.add(FxEvent(kind, source.x, source.y, 600f, size = radius * 0.55f))
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

            if (enemy.type.blinkEveryMs > 0L && elapsedMs >= enemy.nextBlinkAtMs && elapsedMs >= enemy.stunExpiresAtMs &&
                enemy.x >= MIN_TARGET_X
            ) {
                // An Imp is gone from here and a little further on.
                enemy.nextBlinkAtMs = elapsedMs + enemy.type.blinkEveryMs
                field.fx.add(FxEvent(FxKind.DUST, enemy.x, enemy.y, 320f, size = 2.5f))
                enemy.dist += enemy.type.blinkDist
            }
            enemy.dist += unitSpeed(enemy) * dtSeconds
            if (enemy.dist >= path.length) {
                enemy.alive = false
                field.lives = (field.lives - enemy.type.livesDamage).coerceAtLeast(0)
                field.stats.leaks++
                field.lastLeakAtMs = elapsedMs
                field.fx.add(FxEvent(FxKind.LIFE_TEXT, enemy.x, enemy.y - 4f, 1000f, value = enemy.type.livesDamage))
                if (enemy.type.stealsIncomeSec > 0f) {
                    // A Bandit got home: every purse behind this lane is lighter, and the other side richer.
                    var loot = 0
                    for (seat in seats) {
                        if (seat.lane !== field.lane) continue
                        val taken = minOf(seat.gold, incomePerSec(seat) * enemy.type.stealsIncomeSec).toInt()
                        seat.gold -= taken
                        loot += taken
                    }
                    if (loot > 0) {
                        earnShared(if (field.lane === playerField.lane) aiField else playerField, loot.toFloat())
                        field.fx.add(FxEvent(FxKind.GOLD_LOSS, enemy.x - 5f, enemy.y + 1f, 1100f, value = loot))
                    }
                }
                cue(SoundCue.LEAK, field)
                continue
            }
            place(enemy)

            val layId = enemy.type.spawnEveryId
            if (layId != null && elapsedMs >= enemy.nextSpawnAtMs && enemy.x >= MIN_TARGET_X) {
                enemy.nextSpawnAtMs = elapsedMs + enemy.type.spawnEveryMs
                val child = newEnemy(field, GameData.unit(layId), hpScaleOf(enemy), enemy.speedScale)
                child.dist = (enemy.dist - enemy.type.radius).coerceAtLeast(0f)
                place(child)
                newborns.add(child)
            }
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

    /** What a lane takes in (bounties, loot) is split evenly between the seats defending it. */
    private fun earnShared(field: Battlefield, amount: Float) {
        val partner = field.partner
        if (partner == null) {
            earn(field, amount)
        } else {
            earn(field, amount / 2f)
            earn(partner, amount / 2f)
        }
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
