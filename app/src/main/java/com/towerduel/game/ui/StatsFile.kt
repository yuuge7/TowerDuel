package com.towerduel.game.ui

import com.towerduel.game.data.Difficulty
import org.json.JSONException
import org.json.JSONObject

/** Why a file could not be imported. The message is shown to the player as it is. */
class StatsFileException(message: String) : Exception(message)

/** What a stats file holds: the numbers, and when they were exported (ISO-8601), if the file says. */
class StatsExport(val stats: LifetimeStats, val exportedAt: String?)

/**
 * The export file: the player's lifetime stats as JSON, so a backup can be read and checked by
 * a person. [decode] trusts nothing in the file: a missing number is 0, a negative one is 0.
 */
object StatsFile {
    const val MIME_TYPE = "application/json"

    /** Larger than any real export by a wide margin; anything bigger is not one of ours. */
    const val MAX_BYTES = 256 * 1024

    private const val APP = "TowerDuel"
    private const val FORMAT = 1

    private const val NOT_A_STATS_FILE = "That file is not a TowerDuel stats export."
    private const val FROM_NEWER_VERSION = "That file is from a newer version of TowerDuel. Update the game to import it."

    /** [date] as yyyy-mm-dd. */
    fun fileName(date: String): String = "towerduel-stats-$date.json"

    fun encode(stats: LifetimeStats, exportedAt: String): String {
        val byDifficulty = JSONObject()
        for ((difficulty, record) in stats.byDifficulty) {
            if (record.matches > 0) {
                byDifficulty.put(difficulty.name, JSONObject().put("wins", record.wins).put("losses", record.losses))
            }
        }
        val towerPicks = JSONObject()
        for ((towerId, picks) in stats.towerPicks) towerPicks.put(towerId, picks)
        val rivalWins = JSONObject()
        for ((rivalId, wins) in stats.rivalWins) rivalWins.put(rivalId, wins)

        val body = JSONObject()
            .put("wins", stats.wins)
            .put("losses", stats.losses)
            .put("draws", stats.draws)
            .put("streak", stats.streak)
            .put("bestStreak", stats.bestStreak)
            .put("byDifficulty", byDifficulty)
            .put("pops", stats.pops)
            .put("unitsSent", stats.unitsSent)
            .put("goldEarned", stats.goldEarned)
            .put("towersBuilt", stats.towersBuilt)
            .put("livesLost", stats.livesLost)
            .put("secondsPlayed", stats.secondsPlayed)
            .put("fastestWinSeconds", stats.fastestWinSeconds)
            .put("bestRound", stats.bestRound)
            .put("mostPops", stats.mostPops)
            .put("towerPicks", towerPicks)
            .put("rivalWins", rivalWins)

        return JSONObject()
            .put("app", APP)
            .put("format", FORMAT)
            .put("exportedAt", exportedAt)
            .put("stats", body)
            .toString(2)
    }

    /** @throws StatsFileException if [text] is not a stats export this version can read. */
    fun decode(text: String): StatsExport {
        val root = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw StatsFileException(NOT_A_STATS_FILE)
        }
        if (root.optString("app") != APP) throw StatsFileException(NOT_A_STATS_FILE)
        if (root.optInt("format", 0) > FORMAT) throw StatsFileException(FROM_NEWER_VERSION)
        val body = root.optJSONObject("stats") ?: throw StatsFileException(NOT_A_STATS_FILE)

        fun count(key: String): Int = body.optInt(key, 0).coerceAtLeast(0)
        fun bigCount(key: String): Long = body.optLong(key, 0L).coerceAtLeast(0L)

        val byDifficulty = HashMap<Difficulty, DifficultyRecord>()
        val difficulties = body.optJSONObject("byDifficulty")
        if (difficulties != null) {
            for (difficulty in Difficulty.entries) {
                val record = difficulties.optJSONObject(difficulty.name) ?: continue
                byDifficulty[difficulty] = DifficultyRecord(
                    record.optInt("wins", 0).coerceAtLeast(0),
                    record.optInt("losses", 0).coerceAtLeast(0)
                )
            }
        }

        // An id-to-count object; a file from before a counter existed simply has none.
        fun counts(key: String): Map<String, Int> {
            val out = HashMap<String, Int>()
            val obj = body.optJSONObject(key) ?: return out
            for (id in obj.keys()) {
                val count = obj.optInt(id, 0)
                if (count > 0) out[id] = count
            }
            return out
        }
        val towerPicks = counts("towerPicks")
        val rivalWins = counts("rivalWins")

        val streak = count("streak")
        val stats = LifetimeStats(
            wins = count("wins"),
            losses = count("losses"),
            draws = count("draws"),
            streak = streak,
            // A best streak below the current one cannot be right, whatever the file says.
            bestStreak = maxOf(count("bestStreak"), streak),
            byDifficulty = byDifficulty,
            pops = count("pops"),
            unitsSent = count("unitsSent"),
            goldEarned = bigCount("goldEarned"),
            towersBuilt = count("towersBuilt"),
            livesLost = count("livesLost"),
            secondsPlayed = bigCount("secondsPlayed"),
            fastestWinSeconds = count("fastestWinSeconds"),
            bestRound = count("bestRound"),
            mostPops = count("mostPops"),
            towerPicks = towerPicks,
            rivalWins = rivalWins
        )
        return StatsExport(stats, root.optString("exportedAt").takeIf { it.isNotEmpty() })
    }
}
