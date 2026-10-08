package com.towerduel.game.ui

import com.towerduel.game.data.Difficulty
import com.towerduel.game.data.GameData
import kotlin.random.Random

/**
 * A knockout cup of eight: the player and seven rivals, three rounds, one loss and you are out.
 * Immutable: the player's result gives the next state, with every other match of the round
 * played out by a coin. Holds no Android state, so it can be saved as text and tested headless.
 */
data class Cup(
    /** The difficulty it was entered at. The final is played one step harder. */
    val difficulty: Difficulty,
    /** Eight ids in bracket order, [PLAYER] among seven rival ids: slots 0-1, 2-3, 4-5 and 6-7 meet first. */
    val entrants: List<String>,
    /** Who went through each finished round, still in bracket order: four, then two, then the champion. */
    val rounds: List<List<String>> = emptyList()
) {
    /** The round about to be played: 0 the quarterfinals, 1 the semifinals, 2 the final, 3 once it is over. */
    val round: Int get() = rounds.size

    /** Who is still in, in bracket order. */
    val alive: List<String> get() = rounds.lastOrNull() ?: entrants

    val champion: String? get() = alive.singleOrNull()
    val playerIsIn: Boolean get() = PLAYER in alive
    val playerWon: Boolean get() = champion == PLAYER

    /** Over for the player: knocked out, or holding the cup. */
    val isOver: Boolean get() = !playerIsIn || champion != null

    /** The round the player went out in, or null if still in or the winner. */
    val knockedOutIn: Int?
        get() {
            if (playerIsIn) return null
            return (0 until rounds.size).firstOrNull { PLAYER !in rounds[it] }
        }

    /** The rival the player meets next, or null once the cup is over for them. */
    val opponentId: String?
        get() {
            if (isOver) return null
            val slot = alive.indexOf(PLAYER)
            return alive[slot xor 1]
        }

    /** The difficulty of the player's next match: the final is one step up from the rest. */
    val matchDifficulty: Difficulty
        get() = if (round >= FINAL) Difficulty.entries[(difficulty.ordinal + 1).coerceAtMost(Difficulty.entries.lastIndex)] else difficulty

    /** Who stood in round [index] (0..2), in bracket order: every pair of neighbours is one match. */
    fun field(index: Int): List<String>? = if (index == 0) entrants else rounds.getOrNull(index - 1)

    /**
     * The player's match of this round is decided. Every other match of the round falls to [rng];
     * if the player is out, so do all the rounds still to come, so the bracket shows who took the cup.
     */
    fun afterPlayerMatch(won: Boolean, rng: Random): Cup {
        if (isOver) return this
        var next = copy(rounds = rounds + listOf(playRound(alive, if (won) PLAYER else opponentId, rng)))
        while (!next.playerIsIn && next.champion == null) {
            next = next.copy(rounds = next.rounds + listOf(playRound(next.alive, null, rng)))
        }
        return next
    }

    /** One round: neighbours meet, [fixedWinner] wins its match, a coin decides the others. */
    private fun playRound(field: List<String>, fixedWinner: String?, rng: Random): List<String> =
        field.chunked(2).map { (a, b) ->
            when (fixedWinner) {
                a -> a
                b -> b
                else -> if (rng.nextBoolean()) a else b
            }
        }

    /** One line of text that [decode] reads back. */
    fun encode(): String =
        listOf(FORMAT, difficulty.name, entrants.joinToString(","), rounds.joinToString(";") { it.joinToString(",") }).joinToString("|")

    companion object {
        const val PLAYER = "you"
        const val ROUNDS = 3
        const val FINAL = ROUNDS - 1
        private const val FORMAT = "cup1"
        private const val SIZE = 8

        fun roundName(index: Int): String = when (index) {
            0 -> "Quarterfinal"
            1 -> "Semifinal"
            else -> "Final"
        }

        /** A fresh bracket: seven different rivals and the player, in a random order. */
        fun start(difficulty: Difficulty, rng: Random = Random.Default): Cup {
            val rivals = GameData.RIVALS.shuffled(rng).take(SIZE - 1).map { it.id }
            return Cup(difficulty, (rivals + PLAYER).shuffled(rng))
        }

        /** The cup [text] was saved from, or null if it is not one this version can carry on with. */
        fun decode(text: String?): Cup? {
            val parts = text?.split("|") ?: return null
            if (parts.size != 4 || parts[0] != FORMAT) return null
            val difficulty = Difficulty.entries.firstOrNull { it.name == parts[1] } ?: return null
            val entrants = parts[2].split(",")
            val known = GameData.RIVALS.map { it.id }.toSet() + PLAYER
            if (entrants.size != SIZE || entrants.toSet().size != SIZE || PLAYER !in entrants || !known.containsAll(entrants)) return null

            // Every saved round must be exactly one winner out of each pair of the round before it.
            val rounds = if (parts[3].isEmpty()) emptyList() else parts[3].split(";").map { it.split(",") }
            if (rounds.size > ROUNDS) return null
            var field = entrants
            for (winners in rounds) {
                val pairs = field.chunked(2)
                if (winners.size != pairs.size) return null
                for ((i, winner) in winners.withIndex()) if (winner !in pairs[i]) return null
                field = winners
            }
            return Cup(difficulty, entrants, rounds)
        }
    }
}
