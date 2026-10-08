package com.towerduel.game.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.towerduel.game.data.Difficulty
import com.towerduel.game.data.GameData
import com.towerduel.game.engine.MatchOutcome
import com.towerduel.game.net.BluetoothTransport
import com.towerduel.game.net.Link
import com.towerduel.game.net.Lobby
import com.towerduel.game.net.MatchSession
import com.towerduel.game.net.MatchSetup
import com.towerduel.game.net.Message
import com.towerduel.game.net.PairedPhone
import com.towerduel.game.net.SeatKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Where a game with friends stands on this phone. */
enum class FriendsPhase {
    /** Not in one: choosing to host or to join. */
    IDLE,

    /** Hosting: the lobby is open and this phone decides who sits where. */
    HOSTING,

    /** Reaching for a host. */
    JOINING,

    /** In somebody's lobby, waiting for them to start. */
    JOINED,

    /** The match is rolled: picking towers. */
    DRAFT,

    /** Picked, and waiting for the others to. */
    READY,

    /** The match is on (or just over, with the result still on screen). */
    PLAYING
}

/**
 * A game with friends from this phone's side: the lobby, the draft everybody does at once, and
 * the handing over to a [MatchSession] when the match begins. It owns the links; while a match
 * runs the session reads them, and the rest of the time this does.
 *
 * Everything happens on the main thread: [pump] is called from a slow loop of its own, and all
 * the Bluetooth threads do is leave things where it finds them.
 */
@Stable
class Friends(
    private val transport: BluetoothTransport,
    /** The release installed on this phone. Only phones on the same one play each other. */
    private val build: Int,
    scope: CoroutineScope,
    /** The match is rolled: show this seat its draft. */
    private val onDraft: (MatchSetup, Int) -> Unit,
    /** Everybody has picked: the match begins. */
    private val onBegin: (MatchSession) -> Unit
) {
    var phase by mutableStateOf(FriendsPhase.IDLE)
        private set
    var lobby by mutableStateOf<Lobby?>(null)
        private set

    /** The seat this phone's player holds. */
    var mySeat by mutableStateOf(0)
        private set

    /** The last thing that went wrong or changed, in words for the player. */
    var status by mutableStateOf<String?>(null)
        private set
    var paired by mutableStateOf<List<PairedPhone>>(emptyList())
        private set

    /** Who a joining phone is reaching for. */
    var hostName by mutableStateOf("")
        private set

    val isHost: Boolean get() = hostLink == null && phase != FriendsPhase.IDLE && phase != FriendsPhase.JOINING

    /** The match being drafted for or played; null in the lobby. */
    var session: MatchSession? = null
        private set
    private var setup: MatchSetup? = null
    private var matchId = 0

    // Host: the door, friends not yet introduced, and the link of each friend by their id.
    private var listener: BluetoothTransport.Listener? = null
    private class Stranger(val link: Link, val sinceMs: Long)
    private val strangers = ArrayList<Stranger>()
    private val friendLinks = HashMap<Int, Link>()
    private var nextFriendId = 1
    private val picks = HashMap<Int, List<String>>()

    // Guest: the attempt to reach a host, then the link to it.
    private var connecting: BluetoothTransport.Connecting? = null
    private var hostLink: Link? = null
    private var myName = "Player"

    init {
        // The wait comes first: on the main thread a coroutine starts running at once, inside this
        // constructor, before the fields declared further down exist.
        scope.launch {
            while (isActive) {
                delay(PUMP_MS)
                pump()
            }
        }
    }

    val bluetoothExists: Boolean get() = transport.exists
    fun hasPermission(): Boolean = transport.hasPermission()
    fun bluetoothOn(): Boolean = transport.isOn()
    fun deviceName(): String = transport.ownName()

    fun refreshPaired() {
        paired = transport.pairedPhones()
    }

    // -------------------------------------------------------------------
    // Hosting
    // -------------------------------------------------------------------

    fun host(name: String, botDifficulty: Difficulty) {
        close()
        val door = transport.listen()
        if (door == null) {
            status = "Bluetooth would not start. Is it on?"
            return
        }
        listener = door
        myName = Message.cleanName(name)
        lobby = Lobby.hostedBy(myName, botDifficulty)
        mySeat = 0
        status = null
        phase = FriendsPhase.HOSTING
    }

    fun setTeams(team: Boolean) = changeLobby { it.withTeams(team) ?: it.also { status = "Too many friends for a 1 v 1" } }
    fun tapSeat(index: Int) = changeLobby { it.tapped(index) }
    fun setBotDifficulty(difficulty: Difficulty) = changeLobby { it.withBotDifficulty(difficulty) }

    private fun changeLobby(change: (Lobby) -> Lobby) {
        val current = lobby ?: return
        if (phase != FriendsPhase.HOSTING) return
        status = null
        val next = change(current)
        if (next == current) return
        lobby = next
        tellLobby()
    }

    /** Every friend hears how the lobby stands, and which seat is theirs now. */
    private fun tellLobby() {
        val current = lobby ?: return
        for ((id, link) in friendLinks) link.send(Message.LobbyState(current, current.seatOf(id)).encode())
    }

    /** Host: rolls the match and sends everybody to their draft. */
    fun start() {
        val current = lobby ?: return
        if (phase != FriendsPhase.HOSTING || current.problem != null) return
        matchId++
        val rolled = MatchSetup.roll(current)
        setup = rolled
        picks.clear()
        for ((id, link) in friendLinks) link.send(Message.Start(matchId, rolled, current.seatOf(id)).encode())
        status = null
        phase = FriendsPhase.DRAFT
        onDraft(rolled, 0)
    }

    // -------------------------------------------------------------------
    // Joining
    // -------------------------------------------------------------------

    fun join(phone: PairedPhone, name: String) {
        close()
        myName = Message.cleanName(name)
        hostName = phone.name
        status = null
        connecting = transport.connect(phone.address)
        phase = FriendsPhase.JOINING
    }

    // -------------------------------------------------------------------
    // The draft, and leaving
    // -------------------------------------------------------------------

    /** This phone's player has picked their towers. The match begins when everybody has. */
    fun ready(towerIds: List<String>) {
        if (phase != FriendsPhase.DRAFT) return
        phase = FriendsPhase.READY
        val link = hostLink
        if (link != null) {
            link.send(Message.Picks(matchId, towerIds).encode())
        } else {
            picks[0] = towerIds
            beginIfAllPicked()
        }
    }

    /** The match is over and its result has been looked at: back to the lobby, with whoever is still there. */
    fun backToLobby() {
        val played = session
        session = null
        setup = null
        if (phase == FriendsPhase.IDLE) return
        val link = hostLink
        if (link != null) {
            // A guest whose link went during the match has no lobby to go back to.
            if (link.isOpen && played?.solo != true) phase = FriendsPhase.JOINED else leaveQuietly("The match with ${hostName.ifEmpty { "the host" }} is over.")
            return
        }
        // Host: the match let go of the links of friends it lost; their seats are open again.
        var current = lobby ?: return
        for (id in friendLinks.keys.toList()) {
            if (friendLinks[id]?.isOpen != true) {
                friendLinks.remove(id)?.close()
                current = current.left(current.seatOf(id).takeIf { it >= 0 } ?: continue)
            }
        }
        lobby = current
        phase = FriendsPhase.HOSTING
        tellLobby()
    }

    /** Walks out of whatever this is: the lobby, the draft or the match. Everybody else is told. */
    fun close() {
        session?.leave()
        val bye = Message.Bye.encode()
        hostLink?.send(bye)
        for (link in friendLinks.values) link.send(bye)
        leaveQuietly(null)
    }

    private fun leaveQuietly(why: String?) {
        connecting?.cancel()
        connecting = null
        hostLink?.close()
        hostLink = null
        for (link in friendLinks.values) link.close()
        friendLinks.clear()
        for (stranger in strangers) stranger.link.close()
        strangers.clear()
        listener?.close()
        listener = null
        session = null
        setup = null
        lobby = null
        picks.clear()
        phase = FriendsPhase.IDLE
        status = why
    }

    // -------------------------------------------------------------------
    // The loop
    // -------------------------------------------------------------------

    /** Takes in whatever has arrived since the last call. Safe to call at any time, from the main thread. */
    fun pump() {
        when {
            phase == FriendsPhase.IDLE -> Unit
            phase == FriendsPhase.JOINING -> pumpConnecting()
            hostLink != null -> pumpGuest()
            else -> pumpHost()
        }
    }

    private fun pumpConnecting() {
        val attempt = connecting ?: return
        val link = attempt.link
        if (link != null) {
            connecting = null
            hostLink = link
            link.send(Message.Hello(Message.VERSION, build, Message.CONTENT, myName).encode())
            // Still JOINING until the host answers with its lobby, or refuses.
            phase = FriendsPhase.JOINED
            lobby = null
        } else if (attempt.failed) {
            leaveQuietly("Could not reach $hostName. Are they hosting, and close by?")
        }
    }

    private fun pumpGuest() {
        val link = hostLink ?: return
        val playing = session
        if (playing != null) {
            // While the match runs its session reads the link. Once it has a result nobody advances it any more.
            if (playing.engine.outcome != MatchOutcome.ONGOING) playing.advance(0f)
            for (message in playing.strays) guestHears(message)
            playing.strays.clear()
            return
        }
        while (true) guestHears(Message.decode(link.poll() ?: break) ?: continue)
        if (!link.isOpen && phase != FriendsPhase.IDLE) leaveQuietly("Lost the link to $hostName.")
    }

    private fun guestHears(message: Message) {
        when (message) {
            is Message.Refuse -> leaveQuietly(message.reason)
            is Message.LobbyState -> {
                lobby = message.lobby
                mySeat = message.yourSeat
                // The host is back in its lobby: a draft it called off, or a match that is over on its side.
                if (phase == FriendsPhase.DRAFT || phase == FriendsPhase.READY) {
                    setup = null
                    phase = FriendsPhase.JOINED
                }
            }
            is Message.Start -> {
                session = null
                setup = message.setup
                matchId = message.matchId
                mySeat = message.yourSeat
                phase = FriendsPhase.DRAFT
                onDraft(message.setup, message.yourSeat)
            }
            is Message.Begin -> {
                val rolled = setup
                val link = hostLink
                if (rolled != null && link != null && message.matchId == matchId && (phase == FriendsPhase.DRAFT || phase == FriendsPhase.READY)) {
                    val match = MatchSession(rolled, message.hands, mySeat, matchId, emptyMap(), link)
                    session = match
                    phase = FriendsPhase.PLAYING
                    onBegin(match)
                }
            }
            is Message.Bye -> leaveQuietly("$hostName closed the match.")
            else -> Unit
        }
    }

    private fun pumpHost() {
        // Whoever comes through the door has a few seconds to say who they are.
        val door = listener
        if (door != null) while (true) strangers.add(Stranger(door.poll() ?: break, System.currentTimeMillis()))
        val waiting = strangers.iterator()
        while (waiting.hasNext()) {
            val stranger = waiting.next()
            val hello = stranger.link.poll()?.let(Message::decode)
            when {
                hello is Message.Hello -> {
                    waiting.remove()
                    welcome(stranger.link, hello)
                }
                hello != null || !stranger.link.isOpen || System.currentTimeMillis() - stranger.sinceMs > HELLO_WITHIN_MS -> {
                    waiting.remove()
                    stranger.link.close()
                }
            }
        }

        val playing = session
        if (playing != null) {
            if (playing.engine.outcome != MatchOutcome.ONGOING) playing.advance(0f)
            return
        }

        var current = lobby ?: return
        var changed = false
        for (id in friendLinks.keys.toList()) {
            val link = friendLinks[id] ?: continue
            var gone = false
            while (true) {
                when (val message = Message.decode(link.poll() ?: break)) {
                    is Message.Picks -> if (message.matchId == matchId && phase != FriendsPhase.HOSTING) {
                        picks[current.seatOf(id)] = message.towerIds
                    }
                    is Message.Bye -> gone = true
                    else -> Unit
                }
            }
            if (gone || !link.isOpen) {
                val seat = current.seatOf(id)
                val name = current.seats.getOrNull(seat)?.name ?: "A friend"
                friendLinks.remove(id)?.close()
                if (seat >= 0) current = current.left(seat)
                changed = true
                status = "$name left."
            }
        }
        if (changed) {
            lobby = current
            // A draft cannot go on with a seat empty: everybody is back in the lobby.
            if (phase == FriendsPhase.DRAFT || phase == FriendsPhase.READY) {
                setup = null
                picks.clear()
                phase = FriendsPhase.HOSTING
            }
            tellLobby()
        }
        if (phase == FriendsPhase.READY) beginIfAllPicked()
    }

    /** Host: a phone has said hello. It gets a seat, or a reason why not. */
    private fun welcome(link: Link, hello: Message.Hello) {
        val current = lobby
        val refusal = when {
            !hello.fits(build) -> "Your games are different versions. Update both, then try again."
            current == null || phase != FriendsPhase.HOSTING -> "A match is already being played. Try again when it is over."
            current.seatForNewcomer() == null -> "That match is full."
            else -> null
        }
        if (refusal != null || current == null) {
            link.send(Message.Refuse(refusal ?: "").encode())
            link.close()
            return
        }
        val id = nextFriendId++
        friendLinks[id] = link
        lobby = current.joined(current.seatForNewcomer() ?: return, hello.name, id)
        status = "${hello.name} joined."
        tellLobby()
    }

    /** Host: once every person has picked, every phone is told every hand and the match begins. */
    private fun beginIfAllPicked() {
        val rolled = setup ?: return
        val current = lobby ?: return
        val people = rolled.seats.filter { it.value.human }
        if (people.keys.any { it !in picks }) return
        // A guest's picks are checked like anything else a guest sends: four of the six it was offered, one that can kill.
        val checked = people.mapValues { (seat, offered) ->
            val chosen = picks.getValue(seat).distinct().filter { it in offered.towers }
            val towers = chosen.mapNotNull { id -> GameData.TROOPS.firstOrNull { it.id == id } }
            if (chosen.size == GameData.DRAFT_PICKS && GameData.hasDamageDealer(towers)) chosen else fallbackHand(offered.towers)
        }
        val hands = rolled.hands(checked)
        val guests = HashMap<Int, Link>()
        for ((id, link) in friendLinks) {
            link.send(Message.Begin(matchId, hands).encode())
            guests[current.seatOf(id)] = link
        }
        val match = MatchSession(rolled, hands, 0, matchId, guests, null)
        session = match
        picks.clear()
        phase = FriendsPhase.PLAYING
        onBegin(match)
    }

    /** Four towers out of [offer] that can hold a lane, for a seat whose own picks made no sense. */
    private fun fallbackHand(offer: List<String>): List<String> {
        val towers = offer.mapNotNull { id -> GameData.TROOPS.firstOrNull { it.id == id } }
        val dealers = towers.filter { it.baseDps >= GameData.MIN_DRAFT_DPS }
        return (dealers + towers).distinct().take(GameData.DRAFT_PICKS).map { it.id }
    }

    private companion object {
        const val PUMP_MS = 50L
        const val HELLO_WITHIN_MS = 6000L
    }
}
