package com.towerduel.game

import com.towerduel.game.data.Difficulty
import com.towerduel.game.engine.MatchOutcome
import com.towerduel.game.ui.DifficultyRecord
import com.towerduel.game.ui.LifetimeStats
import com.towerduel.game.ui.MatchRecord
import com.towerduel.game.ui.StatsFile
import com.towerduel.game.ui.StatsFileException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsFileTest {

    private fun played(): LifetimeStats {
        var stats = LifetimeStats()
        stats += MatchRecord(MatchOutcome.PLAYER_WIN, Difficulty.EASY, listOf("sentry", "bomb", "frost"), 210, 12, 140, 30, 5200, 9, 12)
        stats += MatchRecord(MatchOutcome.PLAYER_WIN, Difficulty.HARD, listOf("sentry", "sniper", "mortar"), 305, 17, 260, 55, 8100, 10, 40)
        stats += MatchRecord(MatchOutcome.AI_WIN, Difficulty.HARD, listOf("gatling", "sniper", "beacon"), 150, 8, 60, 12, 2500, 5, 100)
        stats += MatchRecord(MatchOutcome.DRAW, Difficulty.MEDIUM, listOf("sentry", "bomb", "chain"), 330, 20, 300, 80, 9900, 10, 100)
        return stats
    }

    @Test
    fun anExportReadsBackAsTheSameStats() {
        val stats = played()
        val read = StatsFile.decode(StatsFile.encode(stats, "2026-10-04T09:30:00Z"))
        // A drawn match leaves an empty per-difficulty record behind, which the file does not carry.
        assertEquals(stats.copy(byDifficulty = stats.byDifficulty.filterValues { it.matches > 0 }), read.stats)
        assertEquals("2026-10-04T09:30:00Z", read.exportedAt)
    }

    @Test
    fun aFileThatIsNotOursIsRefused() {
        for (text in listOf("", "not json at all", "[1, 2, 3]", "{}", """{"app": "SomethingElse", "stats": {}}""", """{"app": "TowerDuel"}""")) {
            val error = assertThrows(StatsFileException::class.java) { StatsFile.decode(text) }
            assertTrue(text, error.message!!.contains("not a TowerDuel stats export"))
        }
    }

    @Test
    fun aFileFromANewerVersionSaysSo() {
        val error = assertThrows(StatsFileException::class.java) {
            StatsFile.decode("""{"app": "TowerDuel", "format": 99, "stats": {"wins": 3}}""")
        }
        assertTrue(error.message!!.contains("newer version"))
    }

    @Test
    fun missingAndImpossibleNumbersAreRepaired() {
        val read = StatsFile.decode(
            """{"app": "TowerDuel", "format": 1, "stats": {
                "wins": 4, "losses": -7, "streak": 5, "bestStreak": 2, "goldEarned": -1,
                "byDifficulty": {"HARD": {"wins": 4, "losses": -2}, "NIGHTMARE": {"wins": 9}},
                "towerPicks": {"sentry": 3, "bomb": 0, "frost": -4}
            }}"""
        )
        val stats = read.stats
        assertEquals(4, stats.wins)
        assertEquals(0, stats.losses)
        assertEquals(0, stats.draws)
        assertEquals(5, stats.streak)
        assertEquals(5, stats.bestStreak)
        assertEquals(0L, stats.goldEarned)
        assertEquals(mapOf(Difficulty.HARD to DifficultyRecord(4, 0)), stats.byDifficulty)
        assertEquals(mapOf("sentry" to 3), stats.towerPicks)
        assertNull(read.exportedAt)
    }
}
