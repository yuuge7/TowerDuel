package com.towerduel.game.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.towerduel.game.data.AiPersonality
import com.towerduel.game.data.Difficulty
import com.towerduel.game.data.EnemySendType
import com.towerduel.game.data.GameData
import com.towerduel.game.data.GameMode
import com.towerduel.game.data.MapDef
import com.towerduel.game.data.MatchModifier
import com.towerduel.game.data.Rival
import com.towerduel.game.data.TroopType
import com.towerduel.game.engine.AiController
import com.towerduel.game.engine.Battlefield
import com.towerduel.game.engine.GameEngine
import com.towerduel.game.engine.MapGenerator
import com.towerduel.game.engine.MatchOutcome
import com.towerduel.game.engine.PlaceResult
import com.towerduel.game.engine.SendResult
import com.towerduel.game.engine.SoundCue
import com.towerduel.game.ui.audio.SoundFx
import com.towerduel.game.ui.render.Ghost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.Instant
import kotlin.math.sqrt
import kotlin.random.Random

/** A short message shown over the battle; [id] makes a repeat of the same text count as new. */
data class Notice(val text: String, val id: Int)

/** How the last export or import went, shown under the backup buttons. */
class BackupNotice(val text: String, val isError: Boolean)

/** Stats read from a file, waiting for the player to confirm replacing their own with them. */
class PendingImport(val stats: LifetimeStats, val fileName: String, val exportedAt: String?)

/** The simulation advances in fixed steps, so a match plays the same at any frame rate. */
private const val STEP_SECONDS = 1f / 60f
private const val MAX_STEPS_PER_FRAME = 8
private const val TOWER_TAP_RADIUS = 4.5f

// The rival's chatter, in match time.
private const val RIVAL_GREETING_MS = 1200f
private const val RIVAL_LINE_MS = 3400f
private const val RIVAL_QUIET_MS = 11_000f
private const val RIVAL_TALK_LIVES = 8

/** Stable for Compose: everything a screen reads from it is snapshot state. */
@Stable
class GameViewModel(app: Application) : AndroidViewModel(app) {

    val profile = ProfileStore(app)
    private val sound = SoundFx(app).also { it.enabled = profile.soundOn }

    /** The AI-versus-AI match that plays on the main menu; null while the first one is being prepared. */
    var demo by mutableStateOf<DemoMatch?>(null)

    /** The mode picked on the main menu. */
    var selectedMode by mutableStateOf(profile.lastMode)
        private set

    // ---- Pre-match setup (rerolled each time a match is queued) ----
    var selectedDifficulty by mutableStateOf(profile.lastDifficulty)
        private set

    /** What kind of match is set up or being played. A cup match is a duel that a bracket hangs on. */
    var matchMode by mutableStateOf(GameMode.DUEL)
        private set
    var offeredTroops by mutableStateOf<List<TroopType>>(emptyList())
        private set
    var pickedTroops by mutableStateOf<List<TroopType>>(emptyList())
        private set
    var aiDraft by mutableStateOf<List<TroopType>>(emptyList())
        private set
    var map by mutableStateOf<MapDef>(GameData.MAPS.first())
        private set
    var modifier by mutableStateOf<MatchModifier>(GameData.MODIFIERS.first())
        private set
    /** The rules in force: one, sometimes two. [modifier] is all of them folded into one. */
    var rules by mutableStateOf<List<MatchModifier>>(emptyList())
        private set
    var rival by mutableStateOf<Rival>(GameData.RIVALS.first())
        private set

    // A 2 v 2 has three more seats: a second rival, and an ally who shares the player's lane.
    var rival2 by mutableStateOf<Rival?>(null)
        private set
    var ally by mutableStateOf<Rival?>(null)
        private set
    var allyDraft by mutableStateOf<List<TroopType>>(emptyList())
        private set
    private var aiDraft2: List<TroopType> = emptyList()
    var roster by mutableStateOf<List<EnemySendType>>(GameData.CLASSIC_ROSTER)
        private set
    val aiPersonality: AiPersonality get() = rival.personality

    /** What the rival is saying right now, if anything. */
    var rivalLine by mutableStateOf<String?>(null)
        private set
    private var rivalLineUntilMs = 0f
    private var lastChatterAtMs = -100_000f
    private var rivalGreeted = false
    private var rivalPushed = false
    private var rivalLivesHeard = 0
    private var playerLivesHeard = 0

    /** False until a match has been rolled, e.g. after the process was killed and restored. */
    val hasMatchSetup: Boolean get() = offeredTroops.isNotEmpty()

    /** Why the current picks cannot go to battle yet, or null when they can. */
    val draftProblem: String?
        get() {
            val missing = GameData.DRAFT_PICKS - pickedTroops.size
            return when {
                missing > 1 -> "Pick $missing more towers"
                missing == 1 -> "Pick 1 more tower"
                !GameData.hasDamageDealer(pickedTroops) -> "Swap in a tower that can deal real damage"
                else -> null
            }
        }

    // ---- Live match ----
    var engine by mutableStateOf<GameEngine?>(null)
        private set

    /** One AI and the seat it plays: its own purse, and the lane it sends its units down. */
    private class AiSeat(val ai: AiController, val own: Battlefield, val foe: Battlefield)
    private var aiSeats: List<AiSeat> = emptyList()

    private var frame by mutableIntStateOf(0)

    /**
     * The engine is plain mutable state, not Compose state. Calling this from a composable or a
     * draw block subscribes it to the simulation, so it re-runs after every tick.
     */
    fun observeFrame(): Int = frame

    var paused by mutableStateOf(false)
        private set
    var fastForward by mutableStateOf(false)
        private set
    var armedTroop by mutableStateOf<TroopType?>(null)
        private set
    var selectedTowerId by mutableStateOf<Long?>(null)
        private set
    var ghost by mutableStateOf<Ghost?>(null)
        private set
    var notice by mutableStateOf<Notice?>(null)
        private set
    private var nextNoticeId = 0

    private var lastFrameNanos = 0L
    private var accumulator = 0f
    private var resultRecorded = false

    fun selectMode(mode: GameMode) {
        selectedMode = mode
        profile.rememberMode(mode)
    }

    fun rollNewMatchSetup(difficulty: Difficulty, mode: GameMode = selectedMode) {
        selectedDifficulty = difficulty
        matchMode = mode
        // A cup's final is played a step harder than the player chose; that is not a new choice to remember.
        if (mode != GameMode.CUP) profile.rememberDifficulty(difficulty)
        offeredTroops = GameData.randomDraft()
        pickedTroops = emptyList()
        rules = GameData.randomRules()
        modifier = rules.reduce { all, rule -> all + rule }
        val cupRival = if (mode == GameMode.CUP) profile.cup?.opponentId else null
        rival = GameData.RIVALS.firstOrNull { it.id == cupRival } ?: GameData.RIVALS.random()
        roster = GameData.randomRoster()
        map = MapGenerator.randomMap()
        aiDraft = AiController.pickDraft(if (modifier.mirrorDraft) offeredTroops else GameData.randomDraft(), difficulty)

        if (mode == GameMode.TEAM) {
            val others = GameData.RIVALS.filter { it != rival }.shuffled()
            rival2 = others[0]
            ally = others[1]
            aiDraft2 = AiController.pickDraft(GameData.randomDraft(), difficulty)
            allyDraft = AiController.pickDraft(GameData.randomDraft(), difficulty)
        } else {
            rival2 = null
            ally = null
            allyDraft = emptyList()
        }
    }

    // ---- The cup ----------------------------------------------------------------

    /** Opens the cup in progress, or enters a new one at [difficulty] if there is none (or the last one is over). */
    fun enterCup(difficulty: Difficulty) {
        val current = profile.cup
        if (current == null || current.isOver) profile.saveCup(Cup.start(difficulty))
    }

    fun newCup() = profile.saveCup(Cup.start((profile.cup?.difficulty ?: selectedDifficulty)))

    fun abandonCup() = profile.saveCup(null)

    /** Sets up the player's next cup match. False if there is none to play. */
    fun rollCupMatch(): Boolean {
        val cup = profile.cup ?: return false
        if (cup.isOver) return false
        rollNewMatchSetup(cup.matchDifficulty, GameMode.CUP)
        return true
    }

    fun toggleDraftPick(troop: TroopType) {
        pickedTroops = when {
            troop in pickedTroops -> pickedTroops - troop
            pickedTroops.size < GameData.DRAFT_PICKS -> pickedTroops + troop
            else -> {
                playUi(SoundCue.DENIED)
                return
            }
        }
        playUi(SoundCue.PLACE)
    }

    fun startMatch() {
        if (draftProblem != null) return
        // Keep the build bar in the order the towers were offered, not the order they were tapped.
        pickedTroops = offeredTroops.filter { it in pickedTroops }
        val team = matchMode == GameMode.TEAM
        val eng = GameEngine(
            map, modifier, pickedTroops, aiDraft, roster,
            allyDraft = if (team) allyDraft else null, aiAllyDraft = if (team) aiDraft2 else null
        )
        engine = eng
        val seats = ArrayList<AiSeat>()
        seats.add(AiSeat(AiController(rival.personality, selectedDifficulty), eng.aiField, eng.playerField))
        val rivalSeat = eng.aiAllyField
        val allySeat = eng.allyField
        if (rivalSeat != null) seats.add(AiSeat(AiController((rival2 ?: rival).personality, selectedDifficulty), rivalSeat, eng.playerField))
        if (allySeat != null) seats.add(AiSeat(AiController((ally ?: rival).personality, selectedDifficulty), allySeat, eng.aiField))
        aiSeats = seats
        rivalLine = null
        rivalGreeted = false
        rivalPushed = false
        lastChatterAtMs = -100_000f
        rivalLivesHeard = eng.startingLives
        playerLivesHeard = eng.startingLives
        armedTroop = null
        selectedTowerId = null
        ghost = null
        notice = null
        paused = false
        fastForward = false
        lastFrameNanos = 0L
        accumulator = 0f
        resultRecorded = false
    }

    /** Advances the match to the display's frame time. The battle screen calls this once per frame. */
    fun onFrame(frameNanos: Long) {
        val eng = engine ?: return
        val dt = if (lastFrameNanos == 0L) 0f else ((frameNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.1f)
        lastFrameNanos = frameNanos
        if (paused || eng.outcome != MatchOutcome.ONGOING) return

        accumulator += dt * (if (fastForward) 2f else 1f)
        var steps = 0
        while (accumulator >= STEP_SECONDS && steps < MAX_STEPS_PER_FRAME) {
            eng.update(STEP_SECONDS)
            for (seat in aiSeats) seat.ai.update(STEP_SECONDS, eng, seat.own, seat.foe)
            accumulator -= STEP_SECONDS
            steps++
        }
        // Too far behind to catch up (a long hitch): drop the backlog instead of fast-forwarding.
        if (steps == MAX_STEPS_PER_FRAME) accumulator = 0f

        playCues(eng)
        rivalChatter(eng)
        if (eng.outcome != MatchOutcome.ONGOING && !resultRecorded) {
            resultRecorded = true
            val mine = eng.playerField
            // A cup match moves the bracket on. A draw decides nothing: the round is played again.
            var cupEntered = false
            var cupWon = false
            val cup = profile.cup
            if (matchMode == GameMode.CUP && cup != null && !cup.isOver && eng.outcome != MatchOutcome.DRAW) {
                cupEntered = cup.round == 0
                val next = cup.afterPlayerMatch(eng.outcome == MatchOutcome.PLAYER_WIN, Random.Default)
                profile.saveCup(next)
                cupWon = next.playerWon
            }
            profile.record(
                MatchRecord(
                    outcome = eng.outcome,
                    difficulty = selectedDifficulty,
                    towerIds = pickedTroops.map { it.id },
                    seconds = (eng.elapsedMs / 1000f).toInt(),
                    round = eng.round,
                    pops = mine.stats.kills,
                    unitsSent = mine.stats.unitsSent,
                    goldEarned = mine.stats.goldEarned.toInt(),
                    towersBuilt = mine.stats.towersBuilt,
                    livesLost = (eng.startingLives - mine.lives).coerceAtLeast(0),
                    rivalId = rival.id,
                    teamMatch = matchMode == GameMode.TEAM,
                    cupEntered = cupEntered,
                    cupWon = cupWon
                )
            )
            ghost = null
        }
        frame++
    }

    /**
     * Gives the rival a voice: a greeting, a boast when it pushes, a wince or a gloat when lives
     * drop. Spaced out, so it comments on the match instead of narrating it.
     */
    private fun rivalChatter(eng: GameEngine) {
        val now = eng.elapsedMs
        if (rivalLine != null && now > rivalLineUntilMs) rivalLine = null
        val lines = rival.lines
        val say: String? = when {
            !rivalGreeted && now > RIVAL_GREETING_MS -> {
                rivalGreeted = true
                lines.start.random()
            }
            now - lastChatterAtMs < RIVAL_QUIET_MS -> null
            rivalPushed -> lines.push.random()
            rivalLivesHeard - eng.aiField.lives >= RIVAL_TALK_LIVES -> lines.hurt.random()
            playerLivesHeard - eng.playerField.lives >= RIVAL_TALK_LIVES -> lines.gloat.random()
            else -> null
        }
        if (say != null) {
            rivalLine = say
            rivalLineUntilMs = now + RIVAL_LINE_MS
            lastChatterAtMs = now
            rivalPushed = false
            rivalLivesHeard = eng.aiField.lives
            playerLivesHeard = eng.playerField.lives
        }
    }

    private fun playCues(eng: GameEngine) {
        for (event in eng.cues) {
            if (event.cue == SoundCue.WARNING && event.field === eng.playerField) rivalPushed = true
            when {
                event.field == null || event.field === eng.playerField -> sound.play(event.cue)
                // From the opponent's lane the player only needs to hear their own sends landing.
                event.cue == SoundCue.POP || event.cue == SoundCue.POP_BIG || event.cue == SoundCue.BOOM ->
                    sound.play(event.cue, 0.3f)
                event.cue == SoundCue.LEAK -> sound.play(SoundCue.COIN, 0.9f)
            }
        }
        eng.cues.clear()
    }

    private fun matchRunning(): Boolean = engine?.outcome == MatchOutcome.ONGOING

    fun pause() {
        if (matchRunning()) {
            paused = true
            ghost = null
        }
    }

    fun resume() {
        paused = false
    }

    fun toggleFastForward() {
        fastForward = !fastForward
    }

    /**
     * Abandons the current match. The engine is kept so the screen can finish animating out.
     * Walking out of a cup match forfeits it: the rival goes through.
     */
    fun quitMatch() {
        val cup = profile.cup
        if (matchMode == GameMode.CUP && cup != null && !cup.isOver && matchRunning() && !resultRecorded) {
            resultRecorded = true
            profile.saveCup(cup.afterPlayerMatch(won = false, Random.Default))
        }
        aiSeats = emptyList()
        paused = false
        armedTroop = null
        selectedTowerId = null
        ghost = null
    }

    // ---- Sound and settings ---------------------------------------------------

    fun playUi(cue: SoundCue) = sound.play(cue)

    fun toggleSound() {
        profile.toggleSound()
        sound.enabled = profile.soundOn
        playUi(SoundCue.CLICK)
    }

    // ---- Stats backup ---------------------------------------------------------

    var backupNotice by mutableStateOf<BackupNotice?>(null)
        private set
    var pendingImport by mutableStateOf<PendingImport?>(null)
        private set

    private val resolver get() = getApplication<Application>().contentResolver

    /** Writes the stats to [uri], the file the player just created in the system's save dialog. */
    fun exportStats(uri: Uri) {
        val text = StatsFile.encode(profile.stats, Instant.now().toString())
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    // "wt" empties a file the player chose to overwrite; not every provider knows it.
                    val out = runCatching { resolver.openOutputStream(uri, "wt") }.getOrNull()
                        ?: resolver.openOutputStream(uri)
                        ?: error("no output stream")
                    out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                    displayName(uri)
                }
            }
            if (saved.isSuccess) {
                backupNotice = BackupNotice("Saved ${saved.getOrNull() ?: "your stats"}", isError = false)
                playUi(SoundCue.COIN)
            } else {
                backupNotice = BackupNotice("Could not save the file there. Try another folder.", isError = true)
                playUi(SoundCue.DENIED)
            }
        }
    }

    /** Reads [uri], the file the player picked. A good file waits in [pendingImport] to be confirmed. */
    fun openStatsFile(uri: Uri) {
        viewModelScope.launch {
            val read = withContext(Dispatchers.IO) {
                runCatching {
                    val input = resolver.openInputStream(uri) ?: error("no input stream")
                    val bytes = input.use { stream ->
                        val buffer = ByteArrayOutputStream()
                        val chunk = ByteArray(8192)
                        while (true) {
                            val n = stream.read(chunk)
                            if (n < 0) break
                            buffer.write(chunk, 0, n)
                            if (buffer.size() > StatsFile.MAX_BYTES) {
                                throw StatsFileException("That file is too big to be a TowerDuel stats export.")
                            }
                        }
                        buffer.toByteArray()
                    }
                    val export = StatsFile.decode(String(bytes, Charsets.UTF_8))
                    PendingImport(export.stats, displayName(uri) ?: "the chosen file", export.exportedAt)
                }
            }
            val pending = read.getOrNull()
            if (pending != null) {
                backupNotice = null
                pendingImport = pending
            } else {
                // Our own refusals are already worded for the player; anything else is a read error.
                val reason = (read.exceptionOrNull() as? StatsFileException)?.message ?: "Could not read that file."
                backupNotice = BackupNotice(reason, isError = true)
                playUi(SoundCue.DENIED)
            }
        }
    }

    fun confirmImport() {
        val pending = pendingImport ?: return
        profile.replaceStats(pending.stats)
        pendingImport = null
        backupNotice = BackupNotice("Imported ${pending.fileName}", isError = false)
        playUi(SoundCue.UPGRADE)
    }

    fun cancelImport() {
        pendingImport = null
    }

    fun reportNoFilePicker() {
        backupNotice = BackupNotice("This device has no file picker to do that with.", isError = true)
        playUi(SoundCue.DENIED)
    }

    private fun displayName(uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    // ---- Player interactions -------------------------------------------------

    fun armTroop(type: TroopType) {
        val eng = engine ?: return
        // An armed tower can always be put down again, even after the gold for it is gone.
        if (armedTroop != type && eng.playerField.gold < type.cost) {
            deny("Not enough gold for ${type.name}")
            return
        }
        armedTroop = if (armedTroop == type) null else type
        selectedTowerId = null
        ghost = null
        notice = null
    }

    private fun canAct(): Boolean = !paused && matchRunning()

    /** While a tower is armed, a finger on the lane previews where it would go. */
    fun onLaneTouch(x: Float, y: Float) {
        val eng = engine ?: return
        val troop = armedTroop
        if (troop == null || !canAct()) return
        val (cx, cy) = eng.clampToLane(x, y)
        ghost = Ghost(troop, cx, cy, eng.checkPlacement(eng.playerField, troop, cx, cy))
    }

    fun onLaneRelease(x: Float, y: Float) {
        val eng = engine ?: return
        ghost = null
        if (!canAct()) return
        val troop = armedTroop
        if (troop == null) {
            val nearest = eng.playerField.towers.minByOrNull { dist(it.x, it.y, x, y) }
            selectedTowerId = nearest?.takeIf { dist(it.x, it.y, x, y) <= TOWER_TAP_RADIUS }?.instanceId
            return
        }
        when (eng.placeTower(eng.playerField, troop, x, y)) {
            PlaceResult.OK -> armedTroop = null
            PlaceResult.NOT_ENOUGH_GOLD -> deny("Not enough gold for ${troop.name}")
            PlaceResult.TOO_CLOSE -> deny("Too close to another tower")
            PlaceResult.ON_PATH -> deny("Can't build on the track")
            PlaceResult.MATCH_OVER -> Unit
        }
    }

    fun onLaneCancel() {
        ghost = null
    }

    fun upgradeSelectedTower() {
        val eng = engine ?: return
        val id = selectedTowerId ?: return
        if (!canAct()) return
        if (!eng.upgradeTower(eng.playerField, id)) playUi(SoundCue.DENIED)
    }

    fun sellSelectedTower() {
        val eng = engine ?: return
        val id = selectedTowerId ?: return
        if (!canAct()) return
        eng.sellTower(eng.playerField, id)
        selectedTowerId = null
    }

    fun cycleSelectedTargeting() {
        val eng = engine ?: return
        val id = selectedTowerId ?: return
        if (eng.cycleTargeting(eng.playerField, id)) playUi(SoundCue.CLICK)
    }

    fun deselect() {
        selectedTowerId = null
        armedTroop = null
        ghost = null
        notice = null
    }

    fun sendUnit(type: EnemySendType) {
        val eng = engine ?: return
        if (!canAct()) return
        when (eng.sendEnemy(eng.playerField, eng.aiField, type)) {
            SendResult.LOCKED -> deny("${type.name} unlocks in round ${eng.unlockRoundOf(type)}")
            SendResult.NOT_ENOUGH_GOLD -> deny("Not enough gold for ${type.name}")
            SendResult.OK, SendResult.COOLING_DOWN, SendResult.MATCH_OVER -> Unit
        }
    }

    private fun deny(text: String) {
        notice = Notice(text, nextNoticeId++)
        playUi(SoundCue.DENIED)
    }

    /** Clears [shown] unless a newer notice has replaced it in the meantime. */
    fun clearNotice(shown: Notice) {
        if (notice == shown) notice = null
    }

    private fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1; val dy = y2 - y1
        return sqrt(dx * dx + dy * dy)
    }

    override fun onCleared() {
        sound.release()
        super.onCleared()
    }
}
