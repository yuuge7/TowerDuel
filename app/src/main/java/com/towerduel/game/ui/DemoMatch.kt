package com.towerduel.game.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.towerduel.game.data.AiPersonality
import com.towerduel.game.data.Difficulty
import com.towerduel.game.data.GameData
import com.towerduel.game.data.MatchModifier
import com.towerduel.game.engine.AiController
import com.towerduel.game.engine.GameEngine
import com.towerduel.game.engine.MatchOutcome

private const val WARM_UP_SECONDS = 70
private const val MAX_DEMO_MS = 225_000f

/**
 * A real match played by two AIs, shown behind the main menu. It is the same engine and the same
 * renderer as a player's match, so the menu always shows what the game actually looks like.
 */
class DemoMatch private constructor() {

    val engine = GameEngine(
        GameData.randomMap(),
        MatchModifier(id = "demo", name = "Demo", description = ""),
        AiController.pickDraft(GameData.randomDraft(), Difficulty.HARD),
        AiController.pickDraft(GameData.randomDraft(), Difficulty.HARD)
    )
    private val left = AiController(AiPersonality.BALANCED, Difficulty.MEDIUM)
    private val right = AiController(AiPersonality.RUSHER, Difficulty.MEDIUM)

    private var frame by mutableIntStateOf(0)
    fun observeFrame(): Int = frame

    private var lastFrameNanos = 0L

    /** True once the match is decided or has run long enough; the menu then swaps in a fresh one. */
    val finished: Boolean
        get() = engine.outcome != MatchOutcome.ONGOING || engine.elapsedMs > MAX_DEMO_MS

    fun onFrame(frameNanos: Long) {
        val dt = if (lastFrameNanos == 0L) 0f else ((frameNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
        lastFrameNanos = frameNanos
        step(dt)
        frame++
    }

    private fun step(dt: Float) {
        engine.update(dt)
        left.update(dt, engine, engine.playerField, engine.aiField)
        right.update(dt, engine, engine.aiField, engine.playerField)
        engine.cues.clear() // the menu is silent
    }

    companion object {
        /**
         * A match already a few rounds in, so the menu opens on a busy lane instead of an empty
         * one. Simulating those rounds takes a moment: call this off the main thread.
         */
        fun warmedUp(): DemoMatch {
            val match = DemoMatch()
            repeat(WARM_UP_SECONDS * 30) { match.step(1f / 30f) }
            return match
        }
    }
}
