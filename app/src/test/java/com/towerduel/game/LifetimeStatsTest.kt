package com.towerduel.game

import com.towerduel.game.data.Difficulty
import com.towerduel.game.engine.MatchOutcome
import com.towerduel.game.ui.DifficultyRecord
import com.towerduel.game.ui.LifetimeStats
import com.towerduel.game.ui.MatchRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LifetimeStatsTest {

    private fun match(
        outcome: MatchOutcome,
        difficulty: Difficulty = Difficulty.MEDIUM,
        towers: List<String> = listOf("sentry", "bomb", "frost"),
        seconds: Int = 200,
        round: Int = 9,
        pops: Int = 50
    ) = MatchRecord(outcome, difficulty, towers, seconds, round, pops, unitsSent = 12, goldEarned = 3000, towersBuilt = 6, livesLost = 20)

    @Test
    fun winRateIsUnknownUntilAMatchIsDecided() {
        assertNull(LifetimeStats().winRate)
        assertNull((LifetimeStats() + match(MatchOutcome.DRAW)).winRate)
        assertEquals(1f, (LifetimeStats() + match(MatchOutcome.PLAYER_WIN)).winRate)
    }

    @Test
    fun streakGrowsOnWins_resetsOnLoss_andSurvivesADraw() {
        var stats = LifetimeStats()
        stats += match(MatchOutcome.PLAYER_WIN)
        stats += match(MatchOutcome.PLAYER_WIN)
        stats += match(MatchOutcome.DRAW)
        assertEquals(2, stats.streak)
        stats += match(MatchOutcome.PLAYER_WIN)
        assertEquals(3, stats.streak)
        stats += match(MatchOutcome.AI_WIN)
        assertEquals(0, stats.streak)
        assertEquals(3, stats.bestStreak)
        assertEquals(5, stats.matches)
        assertEquals(0.75f, stats.winRate)
    }

    @Test
    fun recordIsSplitByDifficulty() {
        var stats = LifetimeStats()
        stats += match(MatchOutcome.PLAYER_WIN, Difficulty.EASY)
        stats += match(MatchOutcome.AI_WIN, Difficulty.HARD)
        stats += match(MatchOutcome.AI_WIN, Difficulty.HARD)
        assertEquals(DifficultyRecord(1, 0), stats.byDifficulty[Difficulty.EASY])
        assertEquals(DifficultyRecord(0, 2), stats.byDifficulty[Difficulty.HARD])
        assertNull(stats.byDifficulty[Difficulty.MEDIUM])
    }

    @Test
    fun totalsAddUp_andBestsKeepTheBest() {
        var stats = LifetimeStats()
        stats += match(MatchOutcome.AI_WIN, seconds = 90, round = 5, pops = 30)
        stats += match(MatchOutcome.PLAYER_WIN, seconds = 260, round = 14, pops = 120)
        stats += match(MatchOutcome.PLAYER_WIN, seconds = 210, round = 11, pops = 80)
        assertEquals(230, stats.pops)
        assertEquals(36, stats.unitsSent)
        assertEquals(9000L, stats.goldEarned)
        assertEquals(560L, stats.secondsPlayed)
        assertEquals(60, stats.livesLost)
        // The 90-second match was a loss, so it is not the fastest win.
        assertEquals(210, stats.fastestWinSeconds)
        assertEquals(14, stats.bestRound)
        assertEquals(120, stats.mostPops)
    }

    @Test
    fun aRivalIsBeatenOnlyByWinning() {
        fun against(rival: String, outcome: MatchOutcome) =
            MatchRecord(outcome, Difficulty.MEDIUM, listOf("sentry"), 200, 9, 50, 12, 3000, 6, 20, rivalId = rival)
        var stats = LifetimeStats()
        stats += against("dash", MatchOutcome.AI_WIN)
        stats += against("dash", MatchOutcome.PLAYER_WIN)
        stats += against("dash", MatchOutcome.PLAYER_WIN)
        stats += against("misty", MatchOutcome.DRAW)
        assertEquals(mapOf("dash" to 2), stats.rivalWins)
    }

    @Test
    fun towerPicksCountMatchesNotTowersBuilt() {
        var stats = LifetimeStats()
        stats += match(MatchOutcome.PLAYER_WIN, towers = listOf("sentry", "bomb", "frost"))
        stats += match(MatchOutcome.AI_WIN, towers = listOf("sentry", "sniper", "mortar"))
        assertEquals(2, stats.towerPicks["sentry"])
        assertEquals(1, stats.towerPicks["mortar"])
        assertNull(stats.towerPicks["gatling"])
    }

    @Test
    fun cupsAndTeamWinsAreCountedOnTheirOwn() {
        fun played(outcome: MatchOutcome, team: Boolean = false, entered: Boolean = false, cup: Boolean = false) =
            MatchRecord(outcome, Difficulty.MEDIUM, listOf("sentry"), 200, 9, 50, 12, 3000, 6, 20, teamMatch = team, cupEntered = entered, cupWon = cup)

        var stats = LifetimeStats()
        stats += played(MatchOutcome.PLAYER_WIN, team = true)
        stats += played(MatchOutcome.AI_WIN, team = true)
        stats += played(MatchOutcome.PLAYER_WIN, entered = true)
        stats += played(MatchOutcome.PLAYER_WIN)
        stats += played(MatchOutcome.PLAYER_WIN, cup = true)
        stats += played(MatchOutcome.AI_WIN, entered = true)
        assertEquals(1, stats.teamWins)
        assertEquals(2, stats.cupsEntered)
        assertEquals(1, stats.cupsWon)
        assertEquals(4, stats.wins)
    }

    @Test
    fun aMatchWithFriendsAddsToTheTotals_notToTheRecordAgainstTheAi() {
        var stats = LifetimeStats()
        stats += match(MatchOutcome.PLAYER_WIN)
        val before = stats
        stats += MatchRecord(MatchOutcome.PLAYER_WIN, Difficulty.HARD, listOf("sentry", "glue"), 300, 18, 400, 20, 5000, 9, 10, friendMatch = true)
        stats += MatchRecord(MatchOutcome.AI_WIN, Difficulty.HARD, listOf("sentry"), 100, 6, 30, 5, 900, 3, 100, friendMatch = true)

        assertEquals(2, stats.friendMatches)
        assertEquals(1, stats.friendWins)
        // The record, the streak and the per-difficulty table are as they were.
        assertEquals(before.wins, stats.wins)
        assertEquals(before.losses, stats.losses)
        assertEquals(before.streak, stats.streak)
        assertEquals(before.byDifficulty, stats.byDifficulty)
        assertEquals(before.fastestWinSeconds, stats.fastestWinSeconds)
        // What was done in them still counts.
        assertEquals(before.pops + 430, stats.pops)
        assertEquals(before.secondsPlayed + 400, stats.secondsPlayed)
        assertEquals(18, stats.bestRound)
        assertEquals(400, stats.mostPops)
        assertEquals(3, stats.towerPicks["sentry"])
        assertEquals(1, stats.towerPicks["glue"])
    }
}
