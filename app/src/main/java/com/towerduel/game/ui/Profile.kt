package com.towerduel.game.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.towerduel.game.data.Difficulty
import com.towerduel.game.engine.MatchOutcome

/** The player's record and settings, kept in SharedPreferences and mirrored as Compose state. */
class ProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("profile", Context.MODE_PRIVATE)

    var soundOn by mutableStateOf(prefs.getBoolean(KEY_SOUND, true))
        private set
    var wins by mutableIntStateOf(prefs.getInt(KEY_WINS, 0))
        private set
    var losses by mutableIntStateOf(prefs.getInt(KEY_LOSSES, 0))
        private set
    var streak by mutableIntStateOf(prefs.getInt(KEY_STREAK, 0))
        private set
    var bestStreak by mutableIntStateOf(prefs.getInt(KEY_BEST_STREAK, 0))
        private set
    var lastDifficulty by mutableStateOf(
        Difficulty.entries.getOrElse(prefs.getInt(KEY_DIFFICULTY, Difficulty.MEDIUM.ordinal)) { Difficulty.MEDIUM }
    )
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

    fun markNotNew() {
        if (!isNewPlayer) return
        isNewPlayer = false
        prefs.edit().putBoolean(KEY_NEW, false).apply()
    }

    fun record(outcome: MatchOutcome) {
        when (outcome) {
            MatchOutcome.PLAYER_WIN -> {
                wins++
                streak++
                if (streak > bestStreak) bestStreak = streak
            }
            MatchOutcome.AI_WIN -> {
                losses++
                streak = 0
            }
            // A draw neither extends nor breaks a streak.
            MatchOutcome.DRAW, MatchOutcome.ONGOING -> Unit
        }
        isNewPlayer = false
        prefs.edit()
            .putInt(KEY_WINS, wins)
            .putInt(KEY_LOSSES, losses)
            .putInt(KEY_STREAK, streak)
            .putInt(KEY_BEST_STREAK, bestStreak)
            .putBoolean(KEY_NEW, false)
            .apply()
    }

    private companion object {
        const val KEY_SOUND = "sound_on"
        const val KEY_WINS = "wins"
        const val KEY_LOSSES = "losses"
        const val KEY_STREAK = "streak"
        const val KEY_BEST_STREAK = "best_streak"
        const val KEY_DIFFICULTY = "difficulty"
        const val KEY_NEW = "new_player"
    }
}
