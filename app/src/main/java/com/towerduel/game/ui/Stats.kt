package com.towerduel.game.ui

import com.towerduel.game.data.Difficulty
import com.towerduel.game.engine.MatchOutcome

/** What one finished match adds to the player's lifetime numbers. */
class MatchRecord(
    val outcome: MatchOutcome,
    val difficulty: Difficulty,
    /** Ids of the towers the player took into the match. */
    val towerIds: List<String>,
    val seconds: Int,
    val round: Int,
    val pops: Int,
    val unitsSent: Int,
    val goldEarned: Int,
    val towersBuilt: Int,
    val livesLost: Int
)

data class DifficultyRecord(val wins: Int = 0, val losses: Int = 0) {
    val matches: Int get() = wins + losses
}

/** Everything the stats tab shows. Immutable: a finished match produces a new one via [plus]. */
data class LifetimeStats(
    val wins: Int = 0,
    val losses: Int = 0,
    val draws: Int = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val byDifficulty: Map<Difficulty, DifficultyRecord> = emptyMap(),
    val pops: Int = 0,
    val unitsSent: Int = 0,
    val goldEarned: Long = 0,
    val towersBuilt: Int = 0,
    val livesLost: Int = 0,
    val secondsPlayed: Long = 0,
    /** 0 until the first win. */
    val fastestWinSeconds: Int = 0,
    val bestRound: Int = 0,
    val mostPops: Int = 0,
    /** Tower id to how many matches it was drafted for. */
    val towerPicks: Map<String, Int> = emptyMap()
) {
    val matches: Int get() = wins + losses + draws

    /** Share of decided matches won, 0..1, or null before the first decided match. */
    val winRate: Float? get() = if (wins + losses == 0) null else wins / (wins + losses).toFloat()

    operator fun plus(match: MatchRecord): LifetimeStats {
        val won = match.outcome == MatchOutcome.PLAYER_WIN
        val lost = match.outcome == MatchOutcome.AI_WIN
        // A draw neither extends nor breaks a streak.
        val newStreak = if (won) streak + 1 else if (lost) 0 else streak
        val record = byDifficulty[match.difficulty] ?: DifficultyRecord()
        val picks = HashMap(towerPicks)
        for (id in match.towerIds) picks[id] = (picks[id] ?: 0) + 1

        return copy(
            wins = wins + if (won) 1 else 0,
            losses = losses + if (lost) 1 else 0,
            draws = draws + if (!won && !lost) 1 else 0,
            streak = newStreak,
            bestStreak = maxOf(bestStreak, newStreak),
            byDifficulty = byDifficulty + (
                match.difficulty to DifficultyRecord(record.wins + if (won) 1 else 0, record.losses + if (lost) 1 else 0)
            ),
            pops = pops + match.pops,
            unitsSent = unitsSent + match.unitsSent,
            goldEarned = goldEarned + match.goldEarned,
            towersBuilt = towersBuilt + match.towersBuilt,
            livesLost = livesLost + match.livesLost,
            secondsPlayed = secondsPlayed + match.seconds,
            fastestWinSeconds =
                if (won && (fastestWinSeconds == 0 || match.seconds < fastestWinSeconds)) match.seconds else fastestWinSeconds,
            bestRound = maxOf(bestRound, match.round),
            mostPops = maxOf(mostPops, match.pops),
            towerPicks = picks
        )
    }
}
