package com.towerduel.game.net

import com.towerduel.game.data.Difficulty

/** Who sits in one of a friends match's four seats while it is being put together. */
enum class SeatKind {
    /** The phone that hosts. Always seat 0. */
    HOST,

    /** A friend who has joined. */
    FRIEND,

    /** Waiting for a friend to join. */
    OPEN,

    /** A bot will play it. */
    BOT,

    /** Not in the match: the second seat of each lane in a 1 v 1. */
    CLOSED
}

/** [id] tells one friend from another, whatever they call themselves and wherever the host moves them. */
data class LobbySeat(val kind: SeatKind, val name: String = "", val id: Int = 0)

/**
 * A friends match being put together. Seats 0 and 1 are one team and share a lane, seats 2 and 3
 * the other; the host is always seat 0. Immutable: every change gives a new lobby, and only the
 * host makes them.
 *
 * A 1 v 1 uses seats 0 and 2. A 2 v 2 uses all four, each of the other three a friend or a bot:
 * that covers two friends against bots, a friend on each side with a bot beside them, and three
 * friends with one bot.
 */
data class Lobby(
    val team: Boolean,
    val seats: List<LobbySeat>,
    /** How hard the bots play, where there are any. */
    val botDifficulty: Difficulty
) {
    val friends: Int get() = seats.count { it.kind == SeatKind.FRIEND }

    /** Why the match cannot start yet, or null when it can. */
    val problem: String?
        get() = when {
            friends == 0 -> "Waiting for a friend to join"
            seats.any { it.kind == SeatKind.OPEN } -> "Fill every open seat, or give it to a bot"
            else -> null
        }

    /** The seats of a 1 v 1 or a 2 v 2, keeping every friend who still has somewhere to sit. Null if one would not. */
    fun withTeams(team: Boolean): Lobby? {
        if (team == this.team) return this
        if (team) {
            return copy(team = true, seats = seats.map { if (it.kind == SeatKind.CLOSED) LobbySeat(SeatKind.OPEN) else it })
        }
        // Down to a 1 v 1: one friend at most, and across the table.
        val staying = seats.filter { it.kind == SeatKind.FRIEND }
        if (staying.size > 1) return null
        val across = staying.firstOrNull() ?: LobbySeat(SeatKind.OPEN)
        return copy(team = false, seats = listOf(seats[0], LobbySeat(SeatKind.CLOSED), across, LobbySeat(SeatKind.CLOSED)))
    }

    /**
     * What the host tapping seat [index] does: an open seat goes to a bot and a bot's seat opens
     * again, where a bot is allowed (never in a 1 v 1, which is two people); a friend moves on to
     * the next open seat, if there is one.
     */
    fun tapped(index: Int): Lobby {
        val seat = seats.getOrNull(index) ?: return this
        return when (seat.kind) {
            SeatKind.OPEN -> if (team) with(index, LobbySeat(SeatKind.BOT)) else this
            SeatKind.BOT -> with(index, LobbySeat(SeatKind.OPEN))
            SeatKind.FRIEND -> {
                val target = (1..3).map { (index + it) % 4 }.firstOrNull { seats[it].kind == SeatKind.OPEN } ?: return this
                with(target, seat).with(index, LobbySeat(SeatKind.OPEN))
            }
            SeatKind.HOST, SeatKind.CLOSED -> this
        }
    }

    /** The seat a friend who joins now is given: across the table first, then beside the host. Null if all are taken. */
    fun seatForNewcomer(): Int? = JOIN_ORDER.firstOrNull { seats[it].kind == SeatKind.OPEN }

    fun joined(index: Int, name: String, id: Int): Lobby = with(index, LobbySeat(SeatKind.FRIEND, name, id))

    /** The seat friend [id] sits in now, or -1 if they are not here. */
    fun seatOf(id: Int): Int = seats.indexOfFirst { it.kind == SeatKind.FRIEND && it.id == id }

    /** The friend in seat [index] has gone: the seat is open again. */
    fun left(index: Int): Lobby = if (seats[index].kind == SeatKind.FRIEND) with(index, LobbySeat(SeatKind.OPEN)) else this

    fun withBotDifficulty(difficulty: Difficulty): Lobby = copy(botDifficulty = difficulty)

    private fun with(index: Int, seat: LobbySeat): Lobby = copy(seats = seats.toMutableList().also { it[index] = seat })

    companion object {
        const val SEATS = 4
        private val JOIN_ORDER = listOf(2, 1, 3)

        /** A fresh 1 v 1 with the host waiting for one friend. */
        fun hostedBy(name: String, botDifficulty: Difficulty = Difficulty.MEDIUM): Lobby = Lobby(
            team = false,
            seats = listOf(LobbySeat(SeatKind.HOST, name), LobbySeat(SeatKind.CLOSED), LobbySeat(SeatKind.OPEN), LobbySeat(SeatKind.CLOSED)),
            botDifficulty = botDifficulty
        )
    }
}
