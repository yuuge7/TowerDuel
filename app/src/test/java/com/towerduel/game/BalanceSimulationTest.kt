package com.towerduel.game

import com.towerduel.game.data.AiPersonality
import com.towerduel.game.data.Difficulty
import com.towerduel.game.data.GameData
import com.towerduel.game.data.MatchModifier
import com.towerduel.game.data.TroopType
import com.towerduel.game.engine.AiController
import com.towerduel.game.engine.GameEngine
import com.towerduel.game.engine.MatchOutcome
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Plays whole matches headless, AI against AI. Guards the two things a balance change breaks
 * first: every match must end, and a harder AI must beat an easier one most of the time.
 * The printed table is the tool for tuning numbers in GameData.
 */
class BalanceSimulationTest {

    private class Result(
        val outcome: MatchOutcome, val seconds: Float, val round: Int, val livesA: Int, val livesB: Int,
        /** Lives when the scripted rounds ran out, or -1 if the match ended before that. */
        val livesAtSuddenDeathA: Int, val livesAtSuddenDeathB: Int, val timedOut: Boolean
    )

    private val plain = MatchModifier(id = "none", name = "None", description = "")

    /** Side A plays the "player" lane, side B the "ai" lane. */
    private fun play(
        seed: Int,
        diffA: Difficulty, diffB: Difficulty,
        persA: AiPersonality? = null, persB: AiPersonality? = null,
        modifier: MatchModifier = plain
    ): Result {
        val rng = Random(seed)
        val personalities = AiPersonality.entries
        val map = GameData.MAPS[seed % GameData.MAPS.size]
        val handA = AiController.pickDraft(GameData.TROOPS.shuffled(rng).take(GameData.DRAFT_OFFER).ensureDealer(rng), diffA, rng)
        val handB = AiController.pickDraft(GameData.TROOPS.shuffled(rng).take(GameData.DRAFT_OFFER).ensureDealer(rng), diffB, rng)
        val engine = GameEngine(map, modifier, handA, handB, seed.toLong())
        val a = AiController(persA ?: personalities[rng.nextInt(personalities.size)], diffA, rng)
        val b = AiController(persB ?: personalities[rng.nextInt(personalities.size)], diffB, rng)

        val dt = 1f / 30f
        var steps = 0
        var sdA = -1
        var sdB = -1
        while (engine.outcome == MatchOutcome.ONGOING && steps < 30 * 900) {
            engine.update(dt)
            a.update(dt, engine, engine.playerField, engine.aiField)
            b.update(dt, engine, engine.aiField, engine.playerField)
            if (sdA < 0 && engine.suddenDeath) {
                sdA = engine.playerField.lives
                sdB = engine.aiField.lives
            }
            steps++
        }
        val timedOut = engine.playerField.lives > 0 && engine.aiField.lives > 0
        return Result(
            engine.outcome, engine.elapsedMs / 1000f, engine.round,
            engine.playerField.lives, engine.aiField.lives, sdA, sdB, timedOut
        )
    }

    private fun List<TroopType>.ensureDealer(rng: Random) =
        if (GameData.hasDamageDealer(this)) this else GameData.TROOPS.filter { it.baseDps >= GameData.MIN_DRAFT_DPS }.shuffled(rng).take(size)

    private fun series(label: String, games: Int, a: Difficulty, b: Difficulty, modifier: MatchModifier = plain): Int {
        var winsA = 0; var winsB = 0; var draws = 0
        var seconds = 0f; var rounds = 0
        var early = 0; var timeouts = 0; var sdLivesA = 0; var sdLivesB = 0
        for (seed in 1..games) {
            val r = play(seed, a, b, modifier = modifier)
            assertNotEquals("match $label seed $seed never ended", MatchOutcome.ONGOING, r.outcome)
            when (r.outcome) {
                MatchOutcome.PLAYER_WIN -> winsA++
                MatchOutcome.AI_WIN -> winsB++
                else -> draws++
            }
            seconds += r.seconds
            rounds += r.round
            if (r.livesAtSuddenDeathA < 0) early++ else {
                sdLivesA += r.livesAtSuddenDeathA
                sdLivesB += r.livesAtSuddenDeathB
            }
            if (r.timedOut) timeouts++
        }
        val late = (games - early).coerceAtLeast(1)
        println(
            "%-18s A %2d  B %2d  draw %2d | avg %3.0fs  round %4.1f | ended early %2d  timed out %2d | lives at sudden death %3d / %3d"
                .format(label, winsA, winsB, draws, seconds / games, rounds / games.toFloat(), early, timeouts, sdLivesA / late, sdLivesB / late)
        )
        return winsA - winsB
    }

    @Test
    fun everyMatchEnds_andHarderAiWins() {
        println("---- difficulty ladder (A vs B) ----")
        series("EASY   vs EASY", 20, Difficulty.EASY, Difficulty.EASY)
        series("MEDIUM vs MEDIUM", 20, Difficulty.MEDIUM, Difficulty.MEDIUM)
        series("HARD   vs HARD", 20, Difficulty.HARD, Difficulty.HARD)
        val mediumOverEasy = series("MEDIUM vs EASY", 30, Difficulty.MEDIUM, Difficulty.EASY)
        val hardOverMedium = series("HARD   vs MEDIUM", 30, Difficulty.HARD, Difficulty.MEDIUM)
        val hardOverEasy = series("HARD   vs EASY", 30, Difficulty.HARD, Difficulty.EASY)
        assertTrue("Medium should beat Easy more often than not", mediumOverEasy > 0)
        assertTrue("Hard should beat Medium more often than not", hardOverMedium > 0)
        assertTrue("Hard should beat Easy clearly", hardOverEasy >= 10)
    }

    @Test
    fun everyModifierAndMapEnds() {
        println("---- modifiers (MEDIUM vs MEDIUM) ----")
        for (modifier in GameData.MODIFIERS) series(modifier.name, 8, Difficulty.MEDIUM, Difficulty.MEDIUM, modifier)
    }

    @Test
    fun personalitiesAreAllViable() {
        println("---- personalities, HARD mirror: wins out of 24 ----")
        for (p in AiPersonality.entries) {
            var wins = 0; var games = 0
            for (other in AiPersonality.entries) {
                if (other == p) continue
                for (seed in 1..8) {
                    val asA = seed % 2 == 0
                    val r = if (asA) play(seed * 7, Difficulty.HARD, Difficulty.HARD, p, other)
                    else play(seed * 7, Difficulty.HARD, Difficulty.HARD, other, p)
                    if (r.outcome == (if (asA) MatchOutcome.PLAYER_WIN else MatchOutcome.AI_WIN)) wins++
                    games++
                }
            }
            println("%-10s %2d / %d".format(p.label, wins, games))
            assertTrue("${p.label} never wins", wins > 0)
        }
    }
}
