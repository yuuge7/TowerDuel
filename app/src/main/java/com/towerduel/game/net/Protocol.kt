package com.towerduel.game.net

import com.towerduel.game.data.Difficulty
import com.towerduel.game.data.GameData
import com.towerduel.game.engine.Command
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/**
 * Everything two phones say to each other. A guest talks only to the host; the host talks to
 * every guest. [decode] trusts nothing it is handed: anything it cannot make sense of is null.
 */
sealed class Message {

    /**
     * A guest's first words: which game it is running, and what its player is called. [build] is
     * the release the guest has installed: balance numbers and bot logic change between releases
     * without [content] showing it, and two phones that differ there would drift apart mid-match.
     */
    class Hello(val version: Int, val build: Int, val content: Int, val name: String) : Message() {
        /** True if a phone that is running [build] can play a match with the one that said this. */
        fun fits(build: Int): Boolean = version == VERSION && this.build == build && content == CONTENT
    }

    /** The host's answer to a guest it cannot seat. [reason] is shown to the player as it is. */
    class Refuse(val reason: String) : Message()

    /** The lobby as it stands, and which seat the guest it is sent to holds. */
    class LobbyState(val lobby: Lobby, val yourSeat: Int) : Message()

    /** The match is rolled: go and draft. */
    class Start(val matchId: Int, val setup: MatchSetup, val yourSeat: Int) : Message()

    /** A guest's four towers. */
    class Picks(val matchId: Int, val towerIds: List<String>) : Message()

    /** Everybody has picked: the hand of every seat, and the match begins. */
    class Begin(val matchId: Int, val hands: Map<Int, List<String>>) : Message()

    /**
     * What a guest's player did, to be carried out in [turn]. Sent for every turn, empty or not,
     * so the host knows the guest has nothing more to add to it. [checksum] is the guest's match
     * as it stood at the start of [checkTurn].
     */
    class Input(val matchId: Int, val turn: Int, val checkTurn: Int, val checksum: Int, val commands: List<Command>) : Message()

    /** Every command of [turn], from everybody, in the order all phones carry them out. */
    class Turn(val matchId: Int, val turn: Int, val commands: List<Command>) : Message()

    /** Two phones no longer hold the same match. Each plays on alone. */
    class Unsync(val matchId: Int) : Message()

    /** The sender is leaving, on purpose. */
    object Bye : Message()

    fun encode(): ByteArray {
        val bytes = ByteArrayOutputStream()
        val out = DataOutputStream(bytes)
        when (this) {
            is Hello -> {
                out.writeByte(HELLO); out.writeInt(version); out.writeInt(build); out.writeInt(content); out.writeUTF(name)
            }
            is Refuse -> { out.writeByte(REFUSE); out.writeUTF(reason) }
            is LobbyState -> {
                out.writeByte(LOBBY)
                out.writeByte(yourSeat)
                out.writeBoolean(lobby.team)
                out.writeByte(lobby.botDifficulty.ordinal)
                for (seat in lobby.seats) {
                    out.writeByte(seat.kind.ordinal)
                    out.writeUTF(seat.name)
                    out.writeInt(seat.id)
                }
            }
            is Start -> { out.writeByte(START); out.writeInt(matchId); out.writeByte(yourSeat); setup.write(out) }
            is Picks -> { out.writeByte(PICKS); out.writeInt(matchId); MatchSetup.writeStrings(out, towerIds) }
            is Begin -> {
                out.writeByte(BEGIN); out.writeInt(matchId); out.writeByte(hands.size)
                for ((seat, hand) in hands) {
                    out.writeByte(seat)
                    MatchSetup.writeStrings(out, hand)
                }
            }
            is Input -> {
                out.writeByte(INPUT); out.writeInt(matchId); out.writeInt(turn); out.writeInt(checkTurn); out.writeInt(checksum)
                writeCommands(out, commands)
            }
            is Turn -> { out.writeByte(TURN); out.writeInt(matchId); out.writeInt(turn); writeCommands(out, commands) }
            is Unsync -> { out.writeByte(UNSYNC); out.writeInt(matchId) }
            Bye -> out.writeByte(BYE)
        }
        return bytes.toByteArray()
    }

    companion object {
        /** The messages themselves. Raise it when their bytes change, so an older build is told apart cleanly. */
        const val VERSION = 1

        /** The longest message a link should accept: far above any real one. */
        const val MAX_BYTES = 64 * 1024

        private const val MAX_COMMANDS = 200
        private const val MAX_NAME = 24

        private const val HELLO = 1
        private const val REFUSE = 2
        private const val LOBBY = 3
        private const val START = 4
        private const val PICKS = 5
        private const val BEGIN = 6
        private const val INPUT = 7
        private const val TURN = 8
        private const val UNSYNC = 9
        private const val BYE = 10

        /**
         * A number that differs between two builds whose towers, units or rules differ: such
         * builds would run the same commands to different matches, so they must not be paired.
         * Taken from the text of the content, not its hashCode: an enum hashes differently in
         * every process, and two phones with the same build would never agree.
         */
        val CONTENT: Int by lazy {
            fingerprint(GameData.TROOPS.toString() + GameData.ENEMY_SENDS.toString() + GameData.MODIFIERS.toString())
        }

        private val DECIMAL = Regex("""-?\d+\.\d+(?:[eE][-+]?\d+)?""")

        /**
         * A hash of [text] that does not depend on how a phone writes a fraction out. Android
         * versions do not all print a Float the same way ("0.001" on one, "1.0E-3" on another),
         * so every decimal number is read back and counted by its bits, not by its digits.
         */
        internal fun fingerprint(text: String): Int =
            DECIMAL.replace(text) { it.value.toFloatOrNull()?.toRawBits()?.toString(16) ?: it.value }.hashCode()

        /** A name as it may be shown and sent: trimmed, not empty, not long. */
        fun cleanName(name: String): String = name.trim().take(MAX_NAME).ifEmpty { "Player" }

        fun decode(bytes: ByteArray): Message? {
            val input = DataInputStream(ByteArrayInputStream(bytes))
            return try {
                when (input.readUnsignedByte()) {
                    HELLO -> Hello(input.readInt(), input.readInt(), input.readInt(), cleanName(input.readUTF()))
                    REFUSE -> Refuse(input.readUTF().take(120))
                    LOBBY -> {
                        val yourSeat = input.readUnsignedByte()
                        val team = input.readBoolean()
                        val difficulty = Difficulty.entries.getOrNull(input.readUnsignedByte()) ?: return null
                        val seats = List(Lobby.SEATS) {
                            val kind = SeatKind.entries.getOrNull(input.readUnsignedByte()) ?: return null
                            // An open seat or a bot's has no name; only a person's needs cleaning.
                            val name = input.readUTF()
                            val person = kind == SeatKind.HOST || kind == SeatKind.FRIEND
                            LobbySeat(kind, if (person) cleanName(name) else "", input.readInt())
                        }
                        LobbyState(Lobby(team, seats, difficulty), yourSeat)
                    }
                    START -> {
                        val matchId = input.readInt()
                        val yourSeat = input.readUnsignedByte()
                        Start(matchId, MatchSetup.read(input), yourSeat)
                    }
                    PICKS -> Picks(input.readInt(), MatchSetup.readStrings(input))
                    BEGIN -> {
                        val matchId = input.readInt()
                        val hands = LinkedHashMap<Int, List<String>>()
                        repeat(input.readUnsignedByte()) { hands[input.readUnsignedByte()] = MatchSetup.readStrings(input) }
                        Begin(matchId, hands)
                    }
                    INPUT -> Input(input.readInt(), input.readInt(), input.readInt(), input.readInt(), readCommands(input))
                    TURN -> Turn(input.readInt(), input.readInt(), readCommands(input))
                    UNSYNC -> Unsync(input.readInt())
                    BYE -> Bye
                    else -> null
                }
            } catch (e: IOException) {
                null
            }
        }

        private fun writeCommands(out: DataOutputStream, commands: List<Command>) {
            out.writeShort(commands.size)
            for (command in commands) command.write(out)
        }

        private fun readCommands(input: DataInputStream): List<Command> {
            val count = input.readUnsignedShort()
            if (count > MAX_COMMANDS) throw IOException("too many commands")
            return List(count) { Command.read(input) }
        }
    }
}
