package com.towerduel.game

import com.towerduel.game.data.Difficulty
import com.towerduel.game.data.GameData
import com.towerduel.game.data.LaneSpace
import com.towerduel.game.engine.AiController
import com.towerduel.game.engine.Command
import com.towerduel.game.engine.MatchOutcome
import com.towerduel.game.net.Link
import com.towerduel.game.net.Lobby
import com.towerduel.game.net.LobbySeat
import com.towerduel.game.net.LoopbackLink
import com.towerduel.game.net.MatchSession
import com.towerduel.game.net.MatchSetup
import com.towerduel.game.net.Message
import com.towerduel.game.net.SeatKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Friends matches, headless: every phone is a [MatchSession] in this process, joined to the host
 * by links that deliver late and unevenly. What matters is that all of them play the very same
 * match to the very same end, and that the match survives a phone leaving, dying or going quiet.
 */
class LockstepTest {

    /** One phone at the table: its session, and the taps of a player who tries a bit of everything. */
    private class Phone(val seat: Int, val session: MatchSession, private val hand: List<String>, seed: Int) {
        private val rng = Random(seed)
        var playing = true

        /** Every so often: build somewhere, upgrade or sell something, send a unit. Much of it will be refused. */
        fun play() {
            if (!playing || rng.nextInt(24) != 0) return
            val engine = session.engine
            val own = engine.seat(seat) ?: return
            val mine = own.towers.filter { it.owner === own }
            val command = when (rng.nextInt(10)) {
                0, 1, 2, 3 -> Command.Place(
                    seat, hand[rng.nextInt(hand.size)],
                    rng.nextFloat() * LaneSpace.WIDTH, rng.nextFloat() * LaneSpace.HEIGHT
                )
                4, 5 -> mine.randomOrNull(rng)?.let { Command.Upgrade(seat, it.instanceId) }
                6 -> mine.randomOrNull(rng)?.let { Command.Retarget(seat, it.instanceId) }
                7 -> if (mine.size > 6) Command.Sell(seat, mine.random(rng).instanceId) else null
                else -> Command.Send(seat, engine.roster[rng.nextInt(engine.roster.size)].id)
            }
            if (command != null) session.issue(command)
        }
    }

    private class Table(val phones: List<Phone>, val links: List<LoopbackLink>, val guestEnds: Map<Int, LoopbackLink>) {
        val host: Phone get() = phones.first { it.seat == 0 }
        fun phone(seat: Int): Phone = phones.first { it.seat == seat }
    }

    private fun lobby(vararg kinds: SeatKind): Lobby = Lobby(
        team = kinds[1] != SeatKind.CLOSED,
        seats = kinds.mapIndexed { i, kind -> LobbySeat(kind, if (kind == SeatKind.BOT) "" else "P$i", i) },
        botDifficulty = Difficulty.MEDIUM
    )

    /** Seats everybody and deals the match, sending the setup to the guests as bytes, the way a phone gets it. */
    private fun table(lobby: Lobby, seed: Int, latency: Int): Table {
        val rng = Random(seed)
        val setup = MatchSetup.roll(lobby, rng)
        val picks = setup.seats.filter { it.value.human }.mapValues { (_, seat) ->
            val offer = seat.towers.map { id -> GameData.TROOPS.first { it.id == id } }
            AiController.pickDraft(offer, Difficulty.MEDIUM, rng).map { it.id }
        }
        val hands = setup.hands(picks)
        val links = ArrayList<LoopbackLink>()
        val hostEnds = HashMap<Int, Link>()
        val guestEnds = HashMap<Int, LoopbackLink>()
        for (seat in picks.keys) {
            if (seat == 0) continue
            val (hostEnd, guestEnd) = LoopbackLink.pair { if (latency == 0) 0 else rng.nextInt(latency + 1) }
            links.add(hostEnd)
            links.add(guestEnd)
            hostEnds[seat] = hostEnd
            guestEnds[seat] = guestEnd
        }
        val phones = ArrayList<Phone>()
        phones.add(Phone(0, MatchSession(setup, hands, 0, MATCH, hostEnds, null), hands.getValue(0), seed * 31))
        for ((seat, end) in guestEnds) {
            val sent = (Message.decode(Message.Start(MATCH, setup, seat).encode()) as Message.Start).setup
            phones.add(Phone(seat, MatchSession(sent, hands, seat, MATCH, emptyMap(), end), hands.getValue(seat), seed * 31 + seat))
        }
        return Table(phones, links, guestEnds)
    }

    /** Runs every phone a frame at a time until all of them have a result. [each] is called once a frame. */
    private fun play(table: Table, jitter: Random? = null, each: (frame: Int) -> Unit = {}): Int {
        var frame = 0
        while (table.phones.any { it.playing && it.session.engine.outcome == MatchOutcome.ONGOING }) {
            assertTrue("the match never ended", frame < MAX_FRAMES)
            for (link in table.links) link.step()
            each(frame)
            for (phone in table.phones) {
                if (!phone.playing) continue
                phone.play()
                // An uneven frame rate: now and then a phone skips a frame, or draws a long one.
                val dt = if (jitter == null) FRAME else FRAME * jitter.nextInt(0, 4)
                phone.session.advance(dt)
            }
            frame++
        }
        return frame
    }

    private fun assertSameMatch(table: Table) {
        val host = table.host.session
        assertNotEquals(MatchOutcome.ONGOING, host.engine.outcome)
        assertTrue("a real match, not one over in seconds: ${host.tick} steps", host.tick > 60 * 60)
        println("friends match: ${table.phones.size} phones, ${host.tick / 60} s, round ${host.engine.round}, ${host.engine.outcome}")
        for (phone in table.phones) {
            val session = phone.session
            assertFalse("seat ${phone.seat} fell out of step: ${session.notice}", session.solo)
            assertEquals("seat ${phone.seat} ended in another step", host.tick, session.tick)
            assertEquals("seat ${phone.seat} saw another result", host.engine.outcome, session.engine.outcome)
            assertEquals("seat ${phone.seat} holds another match", host.engine.checksum(), session.engine.checksum())
            assertEquals(host.engine.playerField.lives, session.engine.playerField.lives)
            assertEquals(host.engine.aiField.lives, session.engine.aiField.lives)
        }
    }

    @Test
    fun twoPhonesPlayTheSameDuel() {
        for (seed in 1..3) {
            val table = table(lobby(SeatKind.HOST, SeatKind.CLOSED, SeatKind.FRIEND, SeatKind.CLOSED), seed, latency = 5)
            play(table)
            assertSameMatch(table)
            // Both players did things, and the things happened: the lanes are not empty.
            assertTrue(table.host.session.engine.playerField.stats.towersBuilt > 0)
            assertTrue(table.host.session.engine.aiField.stats.towersBuilt > 0)
        }
    }

    @Test
    fun threeFriendsAndABot_twoAgainstOneAndTheBot() {
        val table = table(lobby(SeatKind.HOST, SeatKind.FRIEND, SeatKind.FRIEND, SeatKind.BOT), seed = 4, latency = 6)
        assertEquals(3, table.phones.size)
        play(table)
        assertSameMatch(table)
        assertTrue(table.host.session.engine.isTeamMatch)
        // The bot built on its lane on every phone alike, without a word being sent about it.
        assertTrue(table.host.session.engine.aiAllyField!!.stats.towersBuilt > 0)
    }

    @Test
    fun fourFriends_andAFriendOnEachSideWithABot() {
        val four = table(lobby(SeatKind.HOST, SeatKind.FRIEND, SeatKind.FRIEND, SeatKind.FRIEND), seed = 5, latency = 4)
        play(four)
        assertSameMatch(four)

        val split = table(lobby(SeatKind.HOST, SeatKind.BOT, SeatKind.FRIEND, SeatKind.BOT), seed = 6, latency = 4)
        assertEquals(2, split.phones.size)
        play(split)
        assertSameMatch(split)

        val together = table(lobby(SeatKind.HOST, SeatKind.FRIEND, SeatKind.BOT, SeatKind.BOT), seed = 7, latency = 4)
        play(together)
        assertSameMatch(together)
    }

    @Test
    fun unevenFrameRatesAndASlowLinkStillAgree() {
        val table = table(lobby(SeatKind.HOST, SeatKind.FRIEND, SeatKind.FRIEND, SeatKind.BOT), seed = 8, latency = 14)
        play(table, jitter = Random(99))
        assertSameMatch(table)
    }

    @Test
    fun aGuestWhoLeavesIsReplacedByABot_andTheMatchGoesOn() {
        val table = table(lobby(SeatKind.HOST, SeatKind.FRIEND, SeatKind.FRIEND, SeatKind.BOT), seed = 9, latency = 5)
        val leaver = table.phone(1)
        play(table) { frame ->
            if (frame == 900) {
                leaver.session.leave()
                leaver.playing = false
            }
        }
        // The two who stayed are still in the same match, and it ended.
        val stayed = Table(table.phones.filter { it !== leaver }, table.links, table.guestEnds)
        assertSameMatch(stayed)
        assertTrue(stayed.host.session.notice!!.contains("left"))
    }

    @Test
    fun aGuestWhoseLinkDiesIsReplacedTheSameWay() {
        val table = table(lobby(SeatKind.HOST, SeatKind.CLOSED, SeatKind.FRIEND, SeatKind.CLOSED), seed = 10, latency = 3)
        val guest = table.phone(2)
        play(table) { frame -> if (frame == 600) table.guestEnds.getValue(2).close() }
        // Each plays on alone against a bot, and each match ends.
        assertTrue(table.host.session.solo)
        assertTrue(guest.session.solo)
        assertNotEquals(MatchOutcome.ONGOING, table.host.session.engine.outcome)
        assertNotEquals(MatchOutcome.ONGOING, guest.session.engine.outcome)
    }

    @Test
    fun whenTheHostLeavesEveryGuestPlaysOnAlone() {
        val table = table(lobby(SeatKind.HOST, SeatKind.FRIEND, SeatKind.FRIEND, SeatKind.BOT), seed = 11, latency = 5)
        play(table) { frame ->
            if (frame == 700) {
                table.host.session.leave()
                table.host.playing = false
            }
        }
        for (seat in listOf(1, 2)) {
            val session = table.phone(seat).session
            assertTrue(session.solo)
            assertNotNull(session.notice)
            assertNotEquals(MatchOutcome.ONGOING, session.engine.outcome)
        }
    }

    @Test
    fun aPhoneThatGoesQuietIsWaitedForAndThenDropped() {
        val table = table(lobby(SeatKind.HOST, SeatKind.FRIEND, SeatKind.FRIEND, SeatKind.BOT), seed = 12, latency = 4)
        val quiet = table.phone(2)
        var waitedFor: String? = null
        var stalledFrames = 0
        play(table) { frame ->
            // Its app is in the background: no frames, no messages, but the link stays up.
            if (frame == 500) quiet.playing = false
            val waiting = table.host.session.waitingFor
            if (waiting != null) {
                waitedFor = waiting
                stalledFrames++
            }
        }
        assertEquals("P2", waitedFor)
        assertTrue("the others waited about ten seconds, not for ever: $stalledFrames frames", stalledFrames in 300..900)
        val stayed = Table(table.phones.filter { it !== quiet }, table.links, table.guestEnds)
        assertSameMatch(stayed)
    }

    @Test
    fun theSameSetupAndCommandsGiveTheSameMatchEveryTime() {
        fun run(): Int {
            val table = table(lobby(SeatKind.HOST, SeatKind.CLOSED, SeatKind.FRIEND, SeatKind.CLOSED), seed = 13, latency = 0)
            play(table)
            return table.host.session.engine.checksum() * 31 + table.host.session.tick
        }
        assertEquals(run(), run())
    }

    private companion object {
        const val MATCH = 7
        const val FRAME = 1f / 60f

        /** Twelve minutes of frames: longer than any match with its sudden death. */
        const val MAX_FRAMES = 60 * 60 * 12
    }
}
