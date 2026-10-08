package com.towerduel.game

import com.towerduel.game.data.Difficulty
import com.towerduel.game.data.GameData
import com.towerduel.game.engine.Command
import com.towerduel.game.engine.MapGenerator
import com.towerduel.game.net.Lobby
import com.towerduel.game.net.LobbySeat
import com.towerduel.game.net.MatchSetup
import com.towerduel.game.net.Message
import com.towerduel.game.net.SeatKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** The lobby's seats, and what goes over the link between two phones. */
class FriendsProtocolTest {

    private fun kinds(lobby: Lobby) = lobby.seats.map { it.kind }

    @Test
    fun aFreshLobbyIsAOneOnOneWaitingForOneFriend() {
        val lobby = Lobby.hostedBy("Ana")
        assertEquals(listOf(SeatKind.HOST, SeatKind.CLOSED, SeatKind.OPEN, SeatKind.CLOSED), kinds(lobby))
        assertNotNull(lobby.problem)
        assertEquals(2, lobby.seatForNewcomer())
        // A 1 v 1 is two people: the seat across cannot go to a bot.
        assertEquals(lobby, lobby.tapped(2))

        val full = lobby.joined(2, "Bo", id = 1)
        assertNull(full.problem)
        assertNull(full.seatForNewcomer())
        assertEquals(2, full.seatOf(1))
    }

    @Test
    fun aTwoOnTwoTakesFriendsAndBotsInAnySeat() {
        var lobby = Lobby.hostedBy("Ana").withTeams(true)!!
        assertEquals(listOf(SeatKind.HOST, SeatKind.OPEN, SeatKind.OPEN, SeatKind.OPEN), kinds(lobby))
        // Friends are seated across the table first, then beside the host.
        lobby = lobby.joined(lobby.seatForNewcomer()!!, "Bo", id = 1)
        lobby = lobby.joined(lobby.seatForNewcomer()!!, "Cy", id = 2)
        assertEquals(2, lobby.seatOf(1))
        assertEquals(1, lobby.seatOf(2))
        assertNotNull("one seat is still open", lobby.problem)

        // Three people and a bot: two against one and the bot.
        lobby = lobby.tapped(3)
        assertEquals(SeatKind.BOT, lobby.seats[3].kind)
        assertNull(lobby.problem)

        // The host moves a friend by tapping them: Bo goes from across the table to the seat a bot gave up.
        lobby = lobby.tapped(3)
        lobby = lobby.tapped(2)
        assertEquals(3, lobby.seatOf(1))
        assertEquals(SeatKind.OPEN, lobby.seats[2].kind)

        // A friend who leaves frees the seat again.
        assertEquals(SeatKind.OPEN, lobby.left(3).seats[3].kind)
    }

    @Test
    fun goingBackToOneOnOneKeepsTheOneFriend_andRefusesWithTwo() {
        val pair = Lobby.hostedBy("Ana").withTeams(true)!!.joined(1, "Bo", id = 1)
        val duel = pair.withTeams(false)!!
        assertEquals(listOf(SeatKind.HOST, SeatKind.CLOSED, SeatKind.FRIEND, SeatKind.CLOSED), kinds(duel))
        assertEquals(2, duel.seatOf(1))

        assertNull(pair.joined(2, "Cy", id = 2).withTeams(false))
    }

    @Test
    fun aRolledSetupCrossesTheLinkUnchanged() {
        val lobby = Lobby(
            team = true,
            seats = listOf(LobbySeat(SeatKind.HOST, "Ana"), LobbySeat(SeatKind.FRIEND, "Bo", 1), LobbySeat(SeatKind.FRIEND, "Cy", 2), LobbySeat(SeatKind.BOT)),
            botDifficulty = Difficulty.HARD
        )
        for (seed in 1..40) {
            val setup = MatchSetup.roll(lobby, Random(seed))
            val read = (Message.decode(Message.Start(seed, setup, 2).encode()) as Message.Start)
            assertEquals(seed, read.matchId)
            assertEquals(2, read.yourSeat)
            val got = read.setup
            assertEquals(setup.seed, got.seed)
            assertEquals(setup.map, got.map)
            assertEquals(setup.ruleIds, got.ruleIds)
            assertEquals(setup.rosterIds, got.rosterIds)
            assertEquals(Difficulty.HARD, got.botDifficulty)
            assertEquals(setup.seats.keys.toList(), got.seats.keys.toList())
            for ((index, seat) in setup.seats) {
                val other = got.seats.getValue(index)
                assertEquals(seat.human, other.human)
                assertEquals(seat.name, other.name)
                assertEquals(seat.rivalId, other.rivalId)
                assertEquals(seat.towers, other.towers)
            }
            // Every person can hold a lane with what they are offered, every bot with what it picked.
            assertNull(MapGenerator.problemWith(setup.map.pathPoints))
            for (seat in setup.seats.values) {
                val towers = seat.towers.map { id -> GameData.TROOPS.first { it.id == id } }
                assertEquals(if (seat.human) GameData.DRAFT_OFFER else GameData.DRAFT_PICKS, towers.size)
                assertTrue(GameData.hasDamageDealer(towers))
            }
            assertTrue(setup.seats.getValue(3).rivalId.isNotEmpty())
            assertFalse(setup.seats.getValue(1).human && setup.seats.getValue(1).rivalId.isNotEmpty())
        }
    }

    @Test
    fun everyMessageReadsBackAsItWasSent() {
        val commands = listOf(
            Command.Place(2, "sentry", 41.5f, 17.25f), Command.Upgrade(2, 12L), Command.Sell(2, 3L),
            Command.Retarget(2, 99L), Command.Send(2, "grunt"), Command.HandOver(1)
        )
        val input = Message.decode(Message.Input(5, 120, 117, -77123, commands).encode()) as Message.Input
        assertEquals(5, input.matchId)
        assertEquals(120, input.turn)
        assertEquals(117, input.checkTurn)
        assertEquals(-77123, input.checksum)
        assertEquals(commands, input.commands)

        val turn = Message.decode(Message.Turn(5, 121, commands).encode()) as Message.Turn
        assertEquals(121, turn.turn)
        assertEquals(commands, turn.commands)

        val hello = Message.decode(Message.Hello(Message.VERSION, 10007, Message.CONTENT, "  Bo  ").encode()) as Message.Hello
        assertEquals(10007, hello.build)
        // Only the same messages, the same release and the same content play each other.
        assertTrue(hello.fits(10007))
        assertFalse(hello.fits(10008))
        assertFalse(Message.Hello(Message.VERSION + 1, 10007, Message.CONTENT, "Bo").fits(10007))
        assertFalse(Message.Hello(Message.VERSION, 10007, Message.CONTENT + 1, "Bo").fits(10007))
        assertEquals("Bo", hello.name)
        assertEquals(Message.CONTENT, hello.content)

        val lobby = Lobby.hostedBy("Ana", Difficulty.EASY).withTeams(true)!!.joined(2, "Bo", 4).tapped(1)
        val state = Message.decode(Message.LobbyState(lobby, 2).encode()) as Message.LobbyState
        assertEquals(lobby, state.lobby)
        assertEquals(2, state.yourSeat)

        val hands = mapOf(0 to listOf("sentry", "bomb", "frost", "sniper"), 2 to listOf("gatling", "chain", "hex", "glue"))
        assertEquals(hands, (Message.decode(Message.Begin(5, hands).encode()) as Message.Begin).hands)
        assertEquals(hands.getValue(2), (Message.decode(Message.Picks(5, hands.getValue(2)).encode()) as Message.Picks).towerIds)
        assertEquals("Full", (Message.decode(Message.Refuse("Full").encode()) as Message.Refuse).reason)
        assertTrue(Message.decode(Message.Bye.encode()) is Message.Bye)
        assertEquals(5, (Message.decode(Message.Unsync(5).encode()) as Message.Unsync).matchId)
    }

    @Test
    fun whatIsNotAMessageIsNotRead() {
        assertNull(Message.decode(ByteArray(0)))
        assertNull(Message.decode(byteArrayOf(99, 1, 2, 3)))
        // A message cut short, and a setup that names a tower this build does not have.
        val whole = Message.Turn(1, 2, listOf(Command.Send(0, "grunt"))).encode()
        assertNull(Message.decode(whole.copyOf(whole.size - 3)))
        val setup = MatchSetup.roll(Lobby.hostedBy("Ana").joined(2, "Bo", 1), Random(3))
        val text = String(Message.Start(1, setup, 2).encode(), Charsets.ISO_8859_1)
        val tower = setup.seats.getValue(0).towers.first()
        val forged = text.replaceFirst(tower, "x".repeat(tower.length)).toByteArray(Charsets.ISO_8859_1)
        assertNull(Message.decode(forged))
    }

    @Test
    fun theContentFingerprintIsTheSameEveryTimeItIsTaken() {
        val again = Message.fingerprint(GameData.TROOPS.toString() + GameData.ENEMY_SENDS.toString() + GameData.MODIFIERS.toString())
        assertEquals(again, Message.CONTENT)
    }

    @Test
    fun theFingerprintReadsNumbersNotTheirSpelling() {
        // Two phones may write the same Float differently; they still have the same content.
        assertEquals(Message.fingerprint("Tower(range=0.001, cost=50)"), Message.fingerprint("Tower(range=1.0E-3, cost=50)"))
        assertEquals(Message.fingerprint("Unit(speed=1.17549435E-38)"), Message.fingerprint("Unit(speed=1.1754944E-38)"))
        // A number that really differs, whole or not, is different content.
        assertTrue(Message.fingerprint("Tower(range=5.2, cost=50)") != Message.fingerprint("Tower(range=5.3, cost=50)"))
        assertTrue(Message.fingerprint("Tower(range=5.2, cost=50)") != Message.fingerprint("Tower(range=5.2, cost=55)"))
        assertTrue(Message.fingerprint("Tower(id=sentry)") != Message.fingerprint("Tower(id=mortar)"))
    }
}
