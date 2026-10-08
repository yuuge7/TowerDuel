package com.towerduel.game.engine

import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * One thing a seat does to a match. Between friends a match is steered by nothing else: each
 * phone runs the same engine and they exchange only these, so a command has to say everything
 * needed to repeat it elsewhere, and nothing that could differ from phone to phone.
 *
 * [seat] is the engine's seat number: 0 and 1 defend the first lane, 2 and 3 the second.
 */
sealed class Command {
    abstract val seat: Int

    /** [towerId] is one of the seat's drafted towers. */
    data class Place(override val seat: Int, val towerId: String, val x: Float, val y: Float) : Command()
    data class Upgrade(override val seat: Int, val instanceId: Long) : Command()
    data class Sell(override val seat: Int, val instanceId: Long) : Command()
    data class Retarget(override val seat: Int, val instanceId: Long) : Command()
    data class Send(override val seat: Int, val unitId: String) : Command()

    /** Not a player's doing: the seat's player has gone, and from here a bot plays on for them. */
    data class HandOver(override val seat: Int) : Command()

    fun write(out: DataOutputStream) {
        when (this) {
            is Place -> {
                out.writeByte(PLACE); out.writeByte(seat); out.writeUTF(towerId); out.writeFloat(x); out.writeFloat(y)
            }
            is Upgrade -> { out.writeByte(UPGRADE); out.writeByte(seat); out.writeLong(instanceId) }
            is Sell -> { out.writeByte(SELL); out.writeByte(seat); out.writeLong(instanceId) }
            is Retarget -> { out.writeByte(RETARGET); out.writeByte(seat); out.writeLong(instanceId) }
            is Send -> { out.writeByte(SEND); out.writeByte(seat); out.writeUTF(unitId) }
            is HandOver -> { out.writeByte(HAND_OVER); out.writeByte(seat) }
        }
    }

    companion object {
        private const val PLACE = 1
        private const val UPGRADE = 2
        private const val SELL = 3
        private const val RETARGET = 4
        private const val SEND = 5
        private const val HAND_OVER = 6

        /** @throws java.io.IOException if what follows in [input] is not a command. */
        fun read(input: DataInputStream): Command {
            val kind = input.readUnsignedByte()
            val seat = input.readUnsignedByte()
            return when (kind) {
                PLACE -> Place(seat, input.readUTF(), input.readFloat(), input.readFloat())
                UPGRADE -> Upgrade(seat, input.readLong())
                SELL -> Sell(seat, input.readLong())
                RETARGET -> Retarget(seat, input.readLong())
                SEND -> Send(seat, input.readUTF())
                HAND_OVER -> HandOver(seat)
                else -> throw java.io.IOException("unknown command $kind")
            }
        }
    }
}
