package com.towerduel.game

import com.towerduel.game.data.AiPersonality
import com.towerduel.game.data.Difficulty
import com.towerduel.game.data.GameData
import com.towerduel.game.data.MatchModifier
import com.towerduel.game.engine.AiController
import com.towerduel.game.engine.GameEngine
import com.towerduel.game.engine.MapGenerator
import com.towerduel.game.engine.MatchOutcome
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Plays whole matches headless, AI against AI, each on its own random map, roster and draft.
 * Guards the two things a balance change breaks first: every match must end, and a harder AI
 * must beat an easier one most of the time. The printed table is the tool for tuning numbers
 * in GameData.
 */
class BalanceSimulationTest {

    private class Result(
        val outcome: MatchOutcome, val seconds: Float, val round: Int, val livesA: Int, val livesB: Int,
        /** Lives when the regular rounds ran out, or -1 if the match ended before that. */
        val livesAtSuddenDeathA: Int, val livesAtSuddenDeathB: Int, val timedOut: Boolean
    )

    /** Side A plays the "player" lane, side B the "ai" lane. */
    private fun play(
        seed: Int,
        diffA: Difficulty, diffB: Difficulty,
        persA: AiPersonality? = null, persB: AiPersonality? = null,
        modifier: MatchModifier = GameData.NO_RULE
    ): Result {
        val rng = Random(seed)
        val personalities = AiPersonality.entries
        val handA = AiController.pickDraft(GameData.randomDraft(rng = rng), diffA, rng)
        val handB = AiController.pickDraft(GameData.randomDraft(rng = rng), diffB, rng)
        val engine = GameEngine(
            MapGenerator.randomMap(rng), modifier, handA, handB, GameData.randomRoster(rng), seed.toLong()
        )
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

    /** A 2 v 2: two AIs to a lane, each with its own hand and purse. Team A defends the "player" lane. */
    private fun playTeams(seed: Int, diffA: Difficulty, diffB: Difficulty): Result {
        val rng = Random(seed)
        val personalities = AiPersonality.entries
        fun hand(difficulty: Difficulty) = AiController.pickDraft(GameData.randomDraft(rng = rng), difficulty, rng)
        fun ai(difficulty: Difficulty) = AiController(personalities[rng.nextInt(personalities.size)], difficulty, rng)
        val engine = GameEngine(
            MapGenerator.randomMap(rng), GameData.NO_RULE, hand(diffA), hand(diffB), GameData.randomRoster(rng), seed.toLong(),
            allyDraft = hand(diffA), aiAllyDraft = hand(diffB)
        )
        val a1 = ai(diffA); val a2 = ai(diffA); val b1 = ai(diffB); val b2 = ai(diffB)
        val allyA = engine.allyField!!
        val allyB = engine.aiAllyField!!

        val dt = 1f / 30f
        var steps = 0
        var sdA = -1
        var sdB = -1
        while (engine.outcome == MatchOutcome.ONGOING && steps < 30 * 900) {
            engine.update(dt)
            a1.update(dt, engine, engine.playerField, engine.aiField)
            a2.update(dt, engine, allyA, engine.aiField)
            b1.update(dt, engine, engine.aiField, engine.playerField)
            b2.update(dt, engine, allyB, engine.playerField)
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

    private fun series(
        label: String, games: Int, a: Difficulty, b: Difficulty, modifier: MatchModifier = GameData.NO_RULE, teams: Boolean = false
    ): Int {
        var winsA = 0; var winsB = 0; var draws = 0
        var seconds = 0f; var rounds = 0
        var early = 0; var timeouts = 0; var sdLivesA = 0; var sdLivesB = 0
        for (seed in 1..games) {
            val r = if (teams) playTeams(seed, a, b) else play(seed, a, b, modifier = modifier)
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
        val mediumOverEasy = series("MEDIUM vs EASY", 50, Difficulty.MEDIUM, Difficulty.EASY)
        val hardOverMedium = series("HARD   vs MEDIUM", 50, Difficulty.HARD, Difficulty.MEDIUM)
        val hardOverEasy = series("HARD   vs EASY", 50, Difficulty.HARD, Difficulty.EASY)
        assertTrue("Medium should beat Easy more often than not", mediumOverEasy > 0)
        assertTrue("Hard should beat Medium more often than not", hardOverMedium > 0)
        // A third of the 50 games clear, as it was when this series was 30 games long.
        assertTrue("Hard should beat Easy clearly", hardOverEasy >= 16)
    }

    @Test
    fun teamMatchesEnd_andTheHarderTeamWins() {
        println("---- 2 v 2 (team A vs team B) ----")
        series("EASY   2v2", 12, Difficulty.EASY, Difficulty.EASY, teams = true)
        series("MEDIUM 2v2", 12, Difficulty.MEDIUM, Difficulty.MEDIUM, teams = true)
        series("HARD   2v2", 12, Difficulty.HARD, Difficulty.HARD, teams = true)
        val hardOverEasy = series("HARD vs EASY 2v2", 16, Difficulty.HARD, Difficulty.EASY, teams = true)
        assertTrue("A Hard team should beat an Easy team more often than not", hardOverEasy > 0)
    }

    @Test
    fun everyRuleEnds() {
        println("---- rules (MEDIUM vs MEDIUM) ----")
        for (modifier in GameData.MODIFIERS) series(modifier.name, 6, Difficulty.MEDIUM, Difficulty.MEDIUM, modifier)
        println("---- two rules at once ----")
        val rng = Random(7)
        repeat(6) {
            val rules = GameData.randomRules(rng)
            val both = rules.reduce { all, rule -> all + rule }
            series(both.name.take(18), 4, Difficulty.MEDIUM, Difficulty.MEDIUM, both)
        }
    }

    @Test
    fun personalitiesAreAllViable() {
        val seeds = 4
        val others = AiPersonality.entries.size - 1
        println("---- personalities, HARD mirror: wins out of ${others * seeds} ----")
        for (p in AiPersonality.entries) {
            var wins = 0; var games = 0
            for (other in AiPersonality.entries) {
                if (other == p) continue
                for (seed in 1..seeds) {
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
