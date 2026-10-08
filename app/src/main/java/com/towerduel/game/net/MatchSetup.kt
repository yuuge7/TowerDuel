package com.towerduel.game.net

import com.towerduel.game.data.AiPersonality
import com.towerduel.game.data.Difficulty
import com.towerduel.game.data.EnemySendType
import com.towerduel.game.data.GameData
import com.towerduel.game.data.MapDef
import com.towerduel.game.data.MapTheme
import com.towerduel.game.data.MatchModifier
import com.towerduel.game.data.TroopType
import com.towerduel.game.engine.AiController
import com.towerduel.game.engine.GameEngine
import com.towerduel.game.engine.MapGenerator
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import kotlin.random.Random

/** Who plays one seat of a friends match. */
class SetupSeat(
    /** A person on one of the phones; false for a bot. */
    val human: Boolean,
    /** The player's name, or the bot's. */
    val name: String,
    /** For a bot: the rival it is (its face and its play style). Empty for a person. */
    val rivalId: String,
    /** A person's draft offer, or the hand a bot has already picked from its own. Tower ids. */
    val towers: List<String>
)

/**
 * Everything the host rolled for one friends match, as it is sent to every phone: the seed the
 * simulation runs from, the map, the rules, the roster, and who sits where with which towers.
 * Two phones that build their engine from the same setup and the same picks hold the same match.
 */
class MatchSetup(
    val seed: Long,
    val map: MapDef,
    val ruleIds: List<String>,
    val rosterIds: List<String>,
    /** Seat number to who plays it; a 1 v 1 has seats 0 and 2 only. */
    val seats: Map<Int, SetupSeat>,
    val botDifficulty: Difficulty
) {
    val team: Boolean get() = seats.size > 2

    val rules: List<MatchModifier> get() = ruleIds.mapNotNull { id -> GameData.MODIFIERS.firstOrNull { it.id == id } }
    val modifier: MatchModifier get() = rules.reduceOrNull { all, rule -> all + rule } ?: GameData.NO_RULE
    val roster: List<EnemySendType> get() = rosterIds.map(GameData::unit)

    fun personalityOf(seat: Int): AiPersonality =
        GameData.RIVALS.firstOrNull { it.id == seats[seat]?.rivalId }?.personality ?: AiPersonality.BALANCED

    /** The hands every seat goes into the match with: [picks] for the people, the pre-picked ones for the bots. */
    fun hands(picks: Map<Int, List<String>>): Map<Int, List<String>> =
        seats.mapValues { (index, seat) -> if (seat.human) picks[index] ?: seat.towers.take(GameData.DRAFT_PICKS) else seat.towers }

    /** The match itself. Every phone calls this with the same [hands] and gets the same engine. */
    fun engine(hands: Map<Int, List<String>>): GameEngine {
        fun hand(seat: Int): List<TroopType>? = hands[seat]?.mapNotNull { id -> GameData.TROOPS.firstOrNull { it.id == id } }
        return GameEngine(
            map, modifier, hand(0).orEmpty(), hand(2).orEmpty(), roster, seed,
            allyDraft = hand(1), aiAllyDraft = hand(3)
        )
    }

    fun write(out: DataOutputStream) {
        out.writeLong(seed)
        out.writeUTF(map.id)
        out.writeUTF(map.name)
        out.writeByte(map.theme.ordinal)
        out.writeByte(map.pathPoints.size)
        for ((x, y) in map.pathPoints) {
            out.writeFloat(x)
            out.writeFloat(y)
        }
        writeStrings(out, ruleIds)
        writeStrings(out, rosterIds)
        out.writeByte(botDifficulty.ordinal)
        out.writeByte(seats.size)
        for ((index, seat) in seats) {
            out.writeByte(index)
            out.writeBoolean(seat.human)
            out.writeUTF(seat.name)
            out.writeUTF(seat.rivalId)
            writeStrings(out, seat.towers)
        }
    }

    companion object {
        /**
         * Rolls a match for [lobby]: map, rules, roster, a draft offer for every person and a
         * picked hand for every bot. Under Mirror Match everybody is offered the same towers.
         */
        fun roll(lobby: Lobby, rng: Random = Random.Default): MatchSetup {
            val rules = GameData.randomRules(rng)
            val mirror = rules.any { it.mirrorDraft }
            val shared = GameData.randomDraft(rng = rng)
            val botFaces = GameData.RIVALS.shuffled(rng)
            val seats = LinkedHashMap<Int, SetupSeat>()
            for ((index, seat) in lobby.seats.withIndex()) {
                when (seat.kind) {
                    SeatKind.HOST, SeatKind.FRIEND -> {
                        val offer = if (mirror) shared else GameData.randomDraft(rng = rng)
                        seats[index] = SetupSeat(human = true, name = seat.name, rivalId = "", towers = offer.map { it.id })
                    }
                    SeatKind.BOT -> {
                        val face = botFaces[index]
                        val hand = AiController.pickDraft(if (mirror) shared else GameData.randomDraft(rng = rng), lobby.botDifficulty, rng)
                        seats[index] = SetupSeat(human = false, name = face.name, rivalId = face.id, towers = hand.map { it.id })
                    }
                    SeatKind.OPEN, SeatKind.CLOSED -> Unit
                }
            }
            return MatchSetup(
                seed = rng.nextLong(),
                map = MapGenerator.randomMap(rng),
                ruleIds = rules.map { it.id },
                rosterIds = GameData.randomRoster(rng).map { it.id },
                seats = seats,
                botDifficulty = lobby.botDifficulty
            )
        }

        /** @throws IOException if [input] does not hold a setup this version can play. */
        fun read(input: DataInputStream): MatchSetup {
            val seed = input.readLong()
            val mapId = input.readUTF()
            val mapName = input.readUTF()
            val theme = MapTheme.entries.getOrNull(input.readUnsignedByte()) ?: throw IOException("unknown map theme")
            val points = List(input.readUnsignedByte()) { input.readFloat() to input.readFloat() }
            if (MapGenerator.problemWith(points) != null) throw IOException("unplayable map")
            val ruleIds = readStrings(input)
            val rosterIds = readStrings(input)
            if (ruleIds.any { id -> GameData.MODIFIERS.none { it.id == id } }) throw IOException("unknown rule")
            if (rosterIds.any { id -> GameData.ENEMY_SENDS.none { it.id == id } }) throw IOException("unknown unit")
            val difficulty = Difficulty.entries.getOrNull(input.readUnsignedByte()) ?: throw IOException("unknown difficulty")
            val seats = LinkedHashMap<Int, SetupSeat>()
            repeat(input.readUnsignedByte()) {
                val index = input.readUnsignedByte()
                val seat = SetupSeat(input.readBoolean(), input.readUTF(), input.readUTF(), readStrings(input))
                if (seat.towers.any { id -> GameData.TROOPS.none { it.id == id } }) throw IOException("unknown tower")
                seats[index] = seat
            }
            if (0 !in seats || 2 !in seats || seats.keys.any { it !in 0 until Lobby.SEATS }) throw IOException("bad seats")
            return MatchSetup(seed, MapDef(mapId, mapName, theme, points), ruleIds, rosterIds, seats, difficulty)
        }

        fun writeStrings(out: DataOutputStream, strings: List<String>) {
            out.writeByte(strings.size)
            for (s in strings) out.writeUTF(s)
        }

        fun readStrings(input: DataInputStream): List<String> = List(input.readUnsignedByte()) { input.readUTF() }
    }
}
