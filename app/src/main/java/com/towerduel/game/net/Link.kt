package com.towerduel.game.net

import java.util.ArrayDeque

/**
 * A two-way pipe to one other phone that carries whole messages, in order, or breaks. Nothing in
 * it blocks: whoever owns a link calls [poll] from its own loop, so everything above the link
 * runs on one thread.
 */
interface Link {
    /** False once the other side has gone or the pipe has failed. Messages already received can still be polled. */
    val isOpen: Boolean

    fun send(message: ByteArray)

    /** The next message that has arrived, or null if there is none right now. */
    fun poll(): ByteArray?

    fun close()
}

/**
 * Two ends of a link inside one process, for tests and for trying a friends match on one phone.
 * A message takes [delay] calls of [step] to cross, so the two ends can be made to wait for each
 * other the way two phones do.
 */
class LoopbackLink private constructor(private val delay: () -> Int) : Link {
    private class Parcel(val bytes: ByteArray, var stepsLeft: Int)

    private lateinit var other: LoopbackLink
    private val inFlight = ArrayDeque<Parcel>()
    private val arrived = ArrayDeque<ByteArray>()
    private var open = true

    override val isOpen: Boolean get() = open

    override fun send(message: ByteArray) {
        if (open && other.open) other.inFlight.add(Parcel(message, delay()))
    }

    override fun poll(): ByteArray? = arrived.poll()

    override fun close() {
        open = false
        other.open = false
    }

    /** Moves time on by one step for the messages on their way to this end. They arrive in the order sent. */
    fun step() {
        for (parcel in inFlight) parcel.stepsLeft--
        while (inFlight.isNotEmpty() && inFlight.peek().stepsLeft <= 0) arrived.add(inFlight.poll().bytes)
    }

    companion object {
        /** The two ends of one link. [delay] is asked once per message for how many steps it takes. */
        fun pair(delay: () -> Int = { 0 }): Pair<LoopbackLink, LoopbackLink> {
            val a = LoopbackLink(delay)
            val b = LoopbackLink(delay)
            a.other = b
            b.other = a
            return a to b
        }
    }
}
