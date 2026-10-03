package com.towerduel.game.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.towerduel.game.data.AiPersonality
import com.towerduel.game.data.Difficulty
import com.towerduel.game.data.EnemySendType
import com.towerduel.game.data.GameData
import com.towerduel.game.data.MapDef
import com.towerduel.game.data.MatchModifier
import com.towerduel.game.data.TroopType
import com.towerduel.game.engine.AiController
import com.towerduel.game.engine.GameEngine
import com.towerduel.game.engine.MatchOutcome
import com.towerduel.game.engine.PlaceResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/** A short message shown over the battle; [id] makes a repeat of the same text count as new. */
data class Notice(val text: String, val id: Int)

class GameViewModel : ViewModel() {

    // ---- Pre-match setup (rerolled each time a match is queued) ----
    var selectedDifficulty by mutableStateOf(Difficulty.MEDIUM)
        private set
    var playerDraft by mutableStateOf<List<TroopType>>(emptyList())
        private set
    var aiDraft by mutableStateOf<List<TroopType>>(emptyList())
        private set
    var map by mutableStateOf<MapDef>(GameData.MAPS.first())
        private set
    var modifier by mutableStateOf<MatchModifier>(GameData.MODIFIERS.first())
        private set
    var aiPersonality by mutableStateOf(AiPersonality.BALANCED)
        private set

    /** False until a match has been rolled, e.g. after the process was killed and restored. */
    val hasMatchSetup: Boolean get() = playerDraft.isNotEmpty()

    // ---- Live match ----
    var engine by mutableStateOf<GameEngine?>(null)
        private set
    private var aiController: AiController? = null
    private var loopJob: Job? = null

    private var frame by mutableIntStateOf(0)

    /**
     * The engine is plain mutable state, not Compose state. Calling this from a composable or a
     * draw block subscribes it to the simulation, so it re-runs after every tick.
     */
    fun observeFrame(): Int = frame

    var paused by mutableStateOf(false)
        private set
    var armedTroop by mutableStateOf<TroopType?>(null)
        private set
    var selectedTowerId by mutableStateOf<Long?>(null)
        private set
    var notice by mutableStateOf<Notice?>(null)
        private set
    private var nextNoticeId = 0

    fun rollNewMatchSetup(difficulty: Difficulty) {
        selectedDifficulty = difficulty
        playerDraft = GameData.randomDraft()
        aiDraft = GameData.randomDraft()
        map = GameData.randomMap()
        modifier = GameData.randomModifier()
        aiPersonality = GameData.randomPersonality()
    }

    fun startMatch() {
        val newEngine = GameEngine(map, modifier, playerDraft, aiDraft)
        engine = newEngine
        aiController = AiController(aiPersonality, selectedDifficulty)
        armedTroop = null
        selectedTowerId = null
        notice = null
        paused = false
        startLoop()
    }

    private fun startLoop() {
        loopJob?.cancel()
        loopJob = viewModelScope.launch {
            var lastNanos = System.nanoTime()
            while (isActive) {
                val now = System.nanoTime()
                val dt = ((now - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.1f)
                lastNanos = now

                val eng = engine ?: break
                if (eng.outcome != MatchOutcome.ONGOING) {
                    frame++
                    break
                }
                if (!paused) {
                    eng.update(dt)
                    aiController?.update(dt, eng)
                    frame++
                }
                delay(16L)
            }
        }
    }

    private fun matchRunning(): Boolean =
        loopJob?.isActive == true && engine?.outcome == MatchOutcome.ONGOING

    fun pause() {
        if (matchRunning()) paused = true
    }

    fun resume() {
        paused = false
    }

    /** Abandons the current match. The engine is kept so the screen can finish animating out. */
    fun quitMatch() {
        loopJob?.cancel()
        aiController = null
        paused = false
        armedTroop = null
        selectedTowerId = null
    }

    // ---- Player interactions -------------------------------------------------

    fun armTroop(type: TroopType) {
        armedTroop = if (armedTroop == type) null else type
        selectedTowerId = null
        notice = null
    }

    fun onPlayerLaneTap(x: Float, y: Float) {
        val eng = engine ?: return
        if (paused || eng.outcome != MatchOutcome.ONGOING) return
        val nearest = eng.playerField.towers.minByOrNull { dist(it.x, it.y, x, y) }
        if (nearest != null && dist(nearest.x, nearest.y, x, y) <= 6f) {
            selectedTowerId = nearest.instanceId
            armedTroop = null
            return
        }
        val troop = armedTroop
        if (troop == null) {
            selectedTowerId = null
            return
        }
        when (eng.placeTower(eng.playerField, troop, x, y)) {
            PlaceResult.OK -> armedTroop = null
            PlaceResult.NOT_ENOUGH_GOLD -> showNotice("Not enough gold for ${troop.name}")
            PlaceResult.TOO_CLOSE -> showNotice("Too close to another tower")
            PlaceResult.LANE_FULL -> showNotice("Tower limit reached (${GameData.MAX_TOWERS_PER_LANE})")
            PlaceResult.MATCH_OVER -> Unit
        }
    }

    fun upgradeSelectedTower() {
        val eng = engine ?: return
        val id = selectedTowerId ?: return
        eng.upgradeTower(eng.playerField, id)
    }

    fun sellSelectedTower() {
        val eng = engine ?: return
        val id = selectedTowerId ?: return
        eng.sellTower(eng.playerField, id)
        selectedTowerId = null
    }

    fun deselect() {
        selectedTowerId = null
        armedTroop = null
        notice = null
    }

    fun sendUnit(type: EnemySendType) {
        val eng = engine ?: return
        if (paused) return
        eng.sendEnemy(eng.playerField, eng.aiField, type)
    }

    private fun showNotice(text: String) {
        notice = Notice(text, nextNoticeId++)
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
        loopJob?.cancel()
        super.onCleared()
    }
}
