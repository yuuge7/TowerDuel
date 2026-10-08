package com.towerduel.game.net

import com.towerduel.game.data.AiPersonality
import com.towerduel.game.engine.AiController
import com.towerduel.game.engine.Battlefield
import com.towerduel.game.engine.Command
import com.towerduel.game.engine.MatchOutcome
import java.util.TreeMap
import kotlin.random.Random

/** The simulation's step, the same on every phone. */
private const val STEP = 1f / 60f

/** Commands are gathered and carried out a turn at a time: this many steps, a fifteenth of a second. */
private const val TICKS_PER_TURN = 4

/**
 * What a player does in one turn is carried out this many turns later, on every phone at once.
 * That is the time a command has to cross to the host and come back to everybody: a fifth of a
 * second, which a tower being placed does not miss and a Bluetooth link can keep.
 */
private const val INPUT_DELAY = 3

private const val MAX_STEPS_PER_ADVANCE = 8
private const val MAX_BACKLOG_SEC = 0.25f

/** The host gives a seat to a bot when its phone has said nothing for this long while everybody waits on it. */
private const val HOST_DROPS_AFTER_MS = 10_000f

/** A guest waits longer than that for the host, so the host's own verdict on a third phone always comes first. */
private const val GUEST_GIVES_UP_AFTER_MS = 16_000f

/** A wait shorter than this is a hiccup nobody needs telling about. */
private const val WAIT_SHOWN_AFTER_MS = 700f

/** How many turns of its own fingerprints the host keeps to check the guests' against. */
private const val CHECKS_KEPT = 600

/**
 * One friends match on one phone. Every phone holds the same engine, built from the same setup,
 * and they keep in step by exchanging only what the players do: a guest hands the host its
 * commands for every turn, the host puts everybody's together and sends each turn's full list
 * back, and no phone plays a turn before it holds that list. Bots are not sent at all: each
 * phone runs them itself, from the same seed, to the same decisions.
 *
 * Whenever a phone drops out, by leaving, by losing its link or by falling silent, its seat goes
 * to a bot on every other phone in the same turn, and the match goes on. A phone left with
 * nobody else ([solo]) simply carries on against bots.
 *
 * Nothing here blocks or runs on a thread of its own: [advance] does everything, and whoever
 * owns the session calls it once a frame.
 */
class MatchSession(
    val setup: MatchSetup,
    hands: Map<Int, List<String>>,
    /** The seat the player on this phone holds. */
    val localSeat: Int,
    private val matchId: Int,
    /** On the host: the link to each guest, by the seat that guest plays. Empty on a guest. */
    guests: Map<Int, Link>,
    /** On a guest: the link to the host. Null on the host. */
    private val host: Link?
) {
    val engine = setup.engine(hands)
    val isHost: Boolean get() = host == null

    private class Bot(val seat: Int, val ai: AiController, val own: Battlefield, val foe: Battlefield)

    private val bots = ArrayList<Bot>()

    /** The seats a person still plays, here or on another phone. */
    private val people = sortedSetOf<Int>()

    private val guests = HashMap(guests)

    /** Steps simulated so far. */
    var tick = 0
        private set
    private var accumulator = 0f
    private var clockMs = 0f

    /** What this phone's player has done since the last turn began. */
    private val pending = ArrayList<Command>()

    /** What this phone's player did, by the turn it is to be carried out in, until that turn is played. */
    private val mine = TreeMap<Int, List<Command>>()
    private var submittedThrough = INPUT_DELAY - 1

    /** Turns whose full command list is known and that have not been played yet. */
    private val turns = HashMap<Int, List<Command>>()

    // Host only: what each guest handed in, turns closed so far, and the fingerprints to compare.
    private val inputs = HashMap<Int, HashMap<Int, List<Command>>>()
    private var nextToClose = INPUT_DELAY
    private val handovers = ArrayList<Command>()
    private val checks = HashMap<Int, Int>()
    private val claims = ArrayList<IntArray>()
    private val lastHeard = HashMap<Int, Float>()
    private var hostLastHeardMs = 0f

    /** True once nobody else is in the match: every other seat is a bot on this phone. */
    var solo = false
        private set

    /** Whose phone the match has been waiting on for long enough to say so, or null while it runs. */
    val waitingFor: String? get() = if (waitedMs >= WAIT_SHOWN_AFTER_MS) waitingOn else null
    private var waitingOn: String? = null
    private var waitedMs = 0f

    /** The last thing that happened to the company: somebody left, the link went. Shown to the player. */
    var notice: String? = null
        private set

    /** Messages that arrived during the match but are not about it; whoever owns the session reads them. */
    val strays = ArrayList<Message>()

    init {
        for ((index, seat) in setup.seats) {
            val field = engine.seat(index) ?: continue
            if (seat.human) {
                people.add(index)
            } else {
                bots.add(Bot(index, AiController(setup.personalityOf(index), setup.botDifficulty, Random(setup.seed + 7919L * (index + 1))), field, engine.foeOf(field)))
            }
        }
        // Nobody has had the chance to do anything in the first turns: they are empty for everyone.
        for (turn in 0 until INPUT_DELAY) turns[turn] = emptyList()
        for (seat in guests.keys) {
            inputs[seat] = HashMap()
            lastHeard[seat] = 0f
        }
        if (people.size <= 1) solo = true
    }

    /** Something the local player wants done. It happens a moment later, on every phone in the same step. */
    fun issue(command: Command) {
        if (engine.outcome == MatchOutcome.ONGOING) pending.add(command)
    }

    /** The local player walks out. Everybody else is told, and plays on with a bot in this seat. */
    fun leave() {
        val bye = Message.Bye.encode()
        host?.send(bye)
        for (link in guests.values) link.send(bye)
        closeLinks()
    }

    /** Takes in what has arrived, then moves the match on by [dtSeconds], or as far as it may go. */
    fun advance(dtSeconds: Float) {
        clockMs += dtSeconds * 1000f
        receive()
        if (engine.outcome != MatchOutcome.ONGOING) {
            waitedMs = 0f
            return
        }
        accumulator = (accumulator + dtSeconds).coerceAtMost(MAX_BACKLOG_SEC)
        var steps = 0
        var stalled = false
        while (accumulator >= STEP && steps < MAX_STEPS_PER_ADVANCE && engine.outcome == MatchOutcome.ONGOING) {
            if (tick % TICKS_PER_TURN == 0 && !beginTurn(tick / TICKS_PER_TURN)) {
                stalled = true
                break
            }
            engine.update(STEP)
            for (i in bots.indices) bots[i].ai.update(STEP, engine, bots[i].own, bots[i].foe)
            tick++
            accumulator -= STEP
            steps++
        }
        if (stalled) {
            waitedMs += dtSeconds * 1000f
            whileWaiting()
        } else {
            waitedMs = 0f
        }
    }

    // -------------------------------------------------------------------
    // Turns
    // -------------------------------------------------------------------

    /** Carries out [turn]'s commands. False if this phone does not hold them all yet and must wait. */
    private fun beginTurn(turn: Int): Boolean {
        if (solo) {
            // Nobody to agree with: what the player does, happens.
            for (command in pending) engine.apply(command)
            pending.clear()
            return true
        }
        submit(turn)
        if (isHost) closeTurns()
        val commands = turns.remove(turn) ?: return false
        mine.remove(turn)
        for (command in commands) carryOut(command)
        return true
    }

    /** Hands in the local player's commands for the turn [INPUT_DELAY] ahead of [turn]. Once per turn, however often asked. */
    private fun submit(turn: Int) {
        val target = turn + INPUT_DELAY
        if (target <= submittedThrough) return
        submittedThrough = target
        val commands = ArrayList(pending)
        pending.clear()
        mine[target] = commands
        // The match as it stands before this turn's commands: every phone takes its fingerprint at the same point.
        val sum = engine.checksum()
        val link = host
        if (link != null) {
            link.send(Message.Input(matchId, target, turn, sum, commands).encode())
        } else {
            checks[turn] = sum
            checks.remove(turn - CHECKS_KEPT)
            compareFingerprints()
        }
    }

    /** Host: every turn that all the players have handed in for is put together and sent out. */
    private fun closeTurns() {
        while (true) {
            val turn = nextToClose
            val own = mine[turn] ?: return
            if (guests.keys.any { inputs[it]?.containsKey(turn) != true }) return
            val all = ArrayList<Command>(handovers)
            handovers.clear()
            // Seat by seat, so every phone carries the turn out in one order. A guest speaks for its own seat only.
            for (seat in people) {
                val commands = if (seat == localSeat) own else inputs[seat]?.remove(turn) ?: continue
                for (command in commands) if (command.seat == seat && command !is Command.HandOver) all.add(command)
            }
            turns[turn] = all
            val bytes = Message.Turn(matchId, turn, all).encode()
            for (link in guests.values) link.send(bytes)
            nextToClose++
        }
    }

    private fun carryOut(command: Command) {
        if (command !is Command.HandOver) {
            engine.apply(command)
        } else if (command.seat == localSeat) {
            // The host gave this seat away while this phone was silent. The others have moved on: play on alone.
            goSolo("You were away too long. Bots play on with you.")
        } else {
            handOver(command.seat)
            if (isHost && guests.isEmpty()) goSolo(null)
        }
    }

    /** A person's seat goes to a bot. Called in the same step on every phone, so the bot is the same on all of them. */
    private fun handOver(seat: Int) {
        if (!people.remove(seat)) return
        val field = engine.seat(seat) ?: return
        val rng = Random(setup.seed xor (tick.toLong() shl 8) xor seat.toLong())
        bots.add(Bot(seat, AiController(AiPersonality.BALANCED, setup.botDifficulty, rng), field, engine.foeOf(field)))
        bots.sortBy { it.seat }
        if (notice == null || !solo) notice = "${setup.seats[seat]?.name ?: "A player"} left. A bot plays on."
    }

    // -------------------------------------------------------------------
    // The other phones
    // -------------------------------------------------------------------

    private fun receive() {
        val link = host
        if (link != null) {
            while (true) {
                val message = Message.decode(link.poll() ?: break)
                hostLastHeardMs = clockMs
                when {
                    message is Message.Turn && message.matchId == matchId -> if (!solo) turns[message.turn] = message.commands
                    message is Message.Unsync && message.matchId == matchId -> goSolo("Lost step with the others. Bots play on.")
                    message is Message.Bye -> goSolo("The host left. Bots play on.")
                    message is Message.Turn || message is Message.Unsync -> Unit // from a match that is over
                    message != null -> strays.add(message)
                }
            }
            if (!link.isOpen && !solo) goSolo("Lost the link to the host. Bots play on.")
            return
        }

        for (seat in guests.keys.toList()) {
            val guest = guests[seat] ?: continue
            while (true) {
                val message = Message.decode(guest.poll() ?: break)
                lastHeard[seat] = clockMs
                when {
                    message is Message.Input && message.matchId == matchId -> {
                        if (message.turn >= nextToClose) inputs[seat]?.put(message.turn, message.commands)
                        claims.add(intArrayOf(message.checkTurn, message.checksum))
                    }
                    message is Message.Bye -> drop(seat)
                    else -> Unit
                }
            }
            if (!guest.isOpen && seat in guests) drop(seat)
        }
        compareFingerprints()
        if (!solo) closeTurns()
    }

    /** Host: a guest has gone. Its seat is handed to a bot in the next turn to be closed, on every phone. */
    private fun drop(seat: Int) {
        guests.remove(seat)?.close()
        inputs.remove(seat)
        lastHeard.remove(seat)
        handovers.add(Command.HandOver(seat))
    }

    /** Host: a guest whose match differs from this one in a turn both have played is playing another match. */
    private fun compareFingerprints() {
        if (solo) return
        val waiting = claims.iterator()
        while (waiting.hasNext()) {
            val claim = waiting.next()
            val own = checks[claim[0]] ?: continue
            waiting.remove()
            if (own != claim[1]) {
                val bytes = Message.Unsync(matchId).encode()
                for (link in guests.values) link.send(bytes)
                goSolo("Lost step with the others. Bots play on.")
                return
            }
        }
    }

    /** The match cannot move: say whom it waits on, and stop waiting for a phone that has gone quiet for too long. */
    private fun whileWaiting() {
        accumulator = accumulator.coerceAtMost(STEP)
        if (host != null) {
            waitingOn = setup.seats[0]?.name ?: "the host"
            if (clockMs - hostLastHeardMs > GUEST_GIVES_UP_AFTER_MS) goSolo("Lost the host. Bots play on.")
            return
        }
        val late = guests.keys.sorted().firstOrNull { inputs[it]?.containsKey(nextToClose) != true }
        waitingOn = late?.let { setup.seats[it]?.name }
        for (seat in guests.keys.toList()) {
            if (inputs[seat]?.containsKey(nextToClose) != true && clockMs - (lastHeard[seat] ?: 0f) > HOST_DROPS_AFTER_MS) drop(seat)
        }
    }

    /**
     * From here this phone plays alone. Whatever was already agreed or handed in for the turns
     * ahead still happens, now; every other person's seat goes to a bot; the links are let go.
     */
    private fun goSolo(reason: String?) {
        if (solo) return
        solo = true
        waitedMs = 0f
        val ahead = sortedSetOf<Int>()
        ahead.addAll(turns.keys)
        ahead.addAll(mine.keys)
        for (turn in ahead) {
            for (command in turns[turn] ?: mine[turn].orEmpty()) {
                if (command !is Command.HandOver) engine.apply(command) else if (command.seat != localSeat) handOver(command.seat)
            }
        }
        turns.clear()
        mine.clear()
        for (seat in people.filter { it != localSeat }) handOver(seat)
        closeLinks()
        if (reason != null) notice = reason
    }

    private fun closeLinks() {
        host?.close()
        for (link in guests.values) link.close()
        guests.clear()
    }
}
