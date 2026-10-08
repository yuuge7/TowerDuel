package com.towerduel.game.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.towerduel.game.data.Difficulty
import com.towerduel.game.data.GameData
import com.towerduel.game.data.GameMode
import com.towerduel.game.engine.MatchOutcome

/** The player's lifetime stats and settings, kept in SharedPreferences and mirrored as Compose state. */
class ProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("profile", Context.MODE_PRIVATE)

    var soundOn by mutableStateOf(prefs.getBoolean(KEY_SOUND, true))
        private set
    var stats by mutableStateOf(load())
        private set
    var lastDifficulty by mutableStateOf(
        Difficulty.entries.getOrElse(prefs.getInt(KEY_DIFFICULTY, Difficulty.MEDIUM.ordinal)) { Difficulty.MEDIUM }
    )
        private set

    var lastMode by mutableStateOf(GameMode.entries.getOrElse(prefs.getInt(KEY_MODE, 0)) { GameMode.DUEL })
        private set

    /** The cup the player is in the middle of, if any. It survives the app being closed. */
    var cup by mutableStateOf(Cup.decode(prefs.getString(KEY_CUP, null)))
        private set

    /** True until the player has opened the how-to-play sheet or finished a match. */
    var isNewPlayer by mutableStateOf(prefs.getBoolean(KEY_NEW, true))
        private set

    fun toggleSound() {
        soundOn = !soundOn
        prefs.edit().putBoolean(KEY_SOUND, soundOn).apply()
    }

    fun rememberDifficulty(difficulty: Difficulty) {
        lastDifficulty = difficulty
        prefs.edit().putInt(KEY_DIFFICULTY, difficulty.ordinal).apply()
    }

    /** What the player calls themselves in a game with friends; empty until they have said. */
    var playerName by mutableStateOf(prefs.getString(KEY_NAME, "") ?: "")
        private set

    fun rememberName(name: String) {
        playerName = name
        prefs.edit().putString(KEY_NAME, name).apply()
    }

    fun rememberMode(mode: GameMode) {
        lastMode = mode
        prefs.edit().putInt(KEY_MODE, mode.ordinal).apply()
    }

    /** Keeps [next] as the cup in progress; null forgets it. */
    fun saveCup(next: Cup?) {
        cup = next
        prefs.edit().also { if (next == null) it.remove(KEY_CUP) else it.putString(KEY_CUP, next.encode()) }.apply()
    }

    fun markNotNew() {
        if (!isNewPlayer) return
        isNewPlayer = false
        prefs.edit().putBoolean(KEY_NEW, false).apply()
    }

    fun record(match: MatchRecord) {
        if (match.outcome == MatchOutcome.ONGOING) return
        stats += match
        isNewPlayer = false
        prefs.edit().also { save(it, stats) }.putBoolean(KEY_NEW, false).apply()
    }

    /** Swaps in stats read from an export file, in place of whatever was recorded on this device. */
    fun replaceStats(imported: LifetimeStats) {
        // Picks for towers this version does not have would have nothing to show.
        val towers = GameData.TROOPS.map { it.id }.toSet()
        val rivals = GameData.RIVALS.map { it.id }.toSet()
        stats = imported.copy(
            towerPicks = imported.towerPicks.filterKeys { it in towers },
            rivalWins = imported.rivalWins.filterKeys { it in rivals }
        )

        val editor = prefs.edit()
        // Clear the per-tower and per-difficulty counters first: the import only writes the ones
        // it has, and the rest must not survive from the old record. (An editor applies its
        // removals before its puts, so the values saved below are kept.)
        for (tower in GameData.TROOPS) editor.remove(KEY_PICK + tower.id)
        for (rival in GameData.RIVALS) editor.remove(KEY_RIVAL + rival.id)
        for (difficulty in Difficulty.entries) {
            editor.remove(KEY_WINS + "_" + difficulty.name)
            editor.remove(KEY_LOSSES + "_" + difficulty.name)
        }
        save(editor, stats)
        editor.apply()
    }

    private fun load(): LifetimeStats = LifetimeStats(
        wins = prefs.getInt(KEY_WINS, 0),
        losses = prefs.getInt(KEY_LOSSES, 0),
        draws = prefs.getInt(KEY_DRAWS, 0),
        streak = prefs.getInt(KEY_STREAK, 0),
        bestStreak = prefs.getInt(KEY_BEST_STREAK, 0),
        byDifficulty = Difficulty.entries.associateWith {
            DifficultyRecord(prefs.getInt(KEY_WINS + "_" + it.name, 0), prefs.getInt(KEY_LOSSES + "_" + it.name, 0))
        },
        pops = prefs.getInt(KEY_POPS, 0),
        unitsSent = prefs.getInt(KEY_SENT, 0),
        goldEarned = prefs.getLong(KEY_GOLD, 0L),
        towersBuilt = prefs.getInt(KEY_TOWERS, 0),
        livesLost = prefs.getInt(KEY_LIVES_LOST, 0),
        secondsPlayed = prefs.getLong(KEY_SECONDS, 0L),
        fastestWinSeconds = prefs.getInt(KEY_FASTEST_WIN, 0),
        bestRound = prefs.getInt(KEY_BEST_ROUND, 0),
        mostPops = prefs.getInt(KEY_MOST_POPS, 0),
        // Only towers that still exist: a pick count for a removed tower has nothing to show.
        towerPicks = GameData.TROOPS.associate { it.id to prefs.getInt(KEY_PICK + it.id, 0) }.filterValues { it > 0 },
        rivalWins = GameData.RIVALS.associate { it.id to prefs.getInt(KEY_RIVAL + it.id, 0) }.filterValues { it > 0 },
        teamWins = prefs.getInt(KEY_TEAM_WINS, 0),
        cupsEntered = prefs.getInt(KEY_CUPS_ENTERED, 0),
        cupsWon = prefs.getInt(KEY_CUPS_WON, 0),
        friendMatches = prefs.getInt(KEY_FRIEND_MATCHES, 0),
        friendWins = prefs.getInt(KEY_FRIEND_WINS, 0)
    )

    private fun save(editor: SharedPreferences.Editor, s: LifetimeStats) {
        editor
            .putInt(KEY_WINS, s.wins)
            .putInt(KEY_LOSSES, s.losses)
            .putInt(KEY_DRAWS, s.draws)
            .putInt(KEY_STREAK, s.streak)
            .putInt(KEY_BEST_STREAK, s.bestStreak)
            .putInt(KEY_POPS, s.pops)
            .putInt(KEY_SENT, s.unitsSent)
            .putLong(KEY_GOLD, s.goldEarned)
            .putInt(KEY_TOWERS, s.towersBuilt)
            .putInt(KEY_LIVES_LOST, s.livesLost)
            .putLong(KEY_SECONDS, s.secondsPlayed)
            .putInt(KEY_FASTEST_WIN, s.fastestWinSeconds)
            .putInt(KEY_BEST_ROUND, s.bestRound)
            .putInt(KEY_MOST_POPS, s.mostPops)
            .putInt(KEY_TEAM_WINS, s.teamWins)
            .putInt(KEY_CUPS_ENTERED, s.cupsEntered)
            .putInt(KEY_CUPS_WON, s.cupsWon)
            .putInt(KEY_FRIEND_MATCHES, s.friendMatches)
            .putInt(KEY_FRIEND_WINS, s.friendWins)
        for ((difficulty, record) in s.byDifficulty) {
            editor.putInt(KEY_WINS + "_" + difficulty.name, record.wins)
            editor.putInt(KEY_LOSSES + "_" + difficulty.name, record.losses)
        }
        for ((towerId, picks) in s.towerPicks) editor.putInt(KEY_PICK + towerId, picks)
        for ((rivalId, wins) in s.rivalWins) editor.putInt(KEY_RIVAL + rivalId, wins)
    }

    private companion object {
        const val KEY_SOUND = "sound_on"
        const val KEY_WINS = "wins"
        const val KEY_LOSSES = "losses"
        const val KEY_DRAWS = "draws"
        const val KEY_STREAK = "streak"
        const val KEY_BEST_STREAK = "best_streak"
        const val KEY_POPS = "pops"
        const val KEY_SENT = "units_sent"
        const val KEY_GOLD = "gold_earned"
        const val KEY_TOWERS = "towers_built"
        const val KEY_LIVES_LOST = "lives_lost"
        const val KEY_SECONDS = "seconds_played"
        const val KEY_FASTEST_WIN = "fastest_win"
        const val KEY_BEST_ROUND = "best_round"
        const val KEY_MOST_POPS = "most_pops"
        const val KEY_PICK = "pick_"
        const val KEY_RIVAL = "rival_"
        const val KEY_TEAM_WINS = "team_wins"
        const val KEY_CUPS_ENTERED = "cups_entered"
        const val KEY_CUPS_WON = "cups_won"
        const val KEY_FRIEND_MATCHES = "friend_matches"
        const val KEY_FRIEND_WINS = "friend_wins"
        const val KEY_NAME = "player_name"
        const val KEY_DIFFICULTY = "difficulty"
        const val KEY_MODE = "mode"
        const val KEY_CUP = "cup"
        const val KEY_NEW = "new_player"
    }
}
