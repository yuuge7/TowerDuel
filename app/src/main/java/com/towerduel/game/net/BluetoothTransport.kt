package com.towerduel.game.net

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/** A phone this one is paired with, as the system's Bluetooth settings know it. */
class PairedPhone(val name: String, val address: String)

/**
 * Friends matches over classic Bluetooth, between phones that are already paired in the system's
 * settings. That is deliberate: connecting to a paired phone needs no scanning, so the app asks
 * for the one "nearby devices" permission on Android 12 and later, and for nothing at all before.
 *
 * Everything that can wait on the radio runs on a thread of its own and leaves its result where
 * the caller's loop picks it up; nothing here calls back.
 */
@SuppressLint("MissingPermission") // every entry point is behind hasPermission()
class BluetoothTransport(private val context: Context) {

    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    /** False on a device with no Bluetooth at all. */
    val exists: Boolean get() = adapter != null

    /** Android 12 and later ask the player; before that the install itself was the permission. */
    fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    fun isOn(): Boolean = hasPermission() && runCatching { adapter?.isEnabled == true }.getOrDefault(false)

    /** This phone's own Bluetooth name: what a friend sees it as in their list. */
    fun ownName(): String = (if (hasPermission()) runCatching { adapter?.name }.getOrNull() else null) ?: Build.MODEL

    fun pairedPhones(): List<PairedPhone> {
        if (!isOn()) return emptyList()
        return runCatching {
            adapter?.bondedDevices.orEmpty().map { PairedPhone(it.name ?: it.address, it.address) }.sortedBy { it.name.lowercase() }
        }.getOrDefault(emptyList())
    }

    /** Starts taking friends in. Null if the radio would not open a door (off, or no permission). */
    fun listen(): Listener? {
        if (!isOn()) return null
        val server = runCatching { adapter?.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SERVICE) }.getOrNull() ?: return null
        return Listener(server)
    }

    /** Starts connecting to the paired phone at [address], which must be hosting. */
    fun connect(address: String): Connecting = Connecting(adapter, address)

    /** The host's open door. Friends who have come through wait in [poll]. */
    class Listener internal constructor(private val server: BluetoothServerSocket) {
        private val arrived = ConcurrentLinkedQueue<Link>()

        @Volatile
        private var open = true

        init {
            thread(name = "towerduel-accept", isDaemon = true) {
                while (open) {
                    val socket = try {
                        server.accept()
                    } catch (e: IOException) {
                        break
                    }
                    arrived.add(BluetoothLink(socket))
                }
            }
        }

        fun poll(): Link? = arrived.poll()

        fun close() {
            open = false
            runCatching { server.close() }
            while (true) (arrived.poll() ?: break).close()
        }
    }

    /** One attempt to reach a host. [link] appears when it worked, [failed] turns true when it did not. */
    class Connecting internal constructor(adapter: BluetoothAdapter?, address: String) {
        @Volatile
        var link: Link? = null
            private set

        @Volatile
        var failed = false
            private set

        @Volatile
        private var cancelled = false

        @Volatile
        private var socket: BluetoothSocket? = null

        init {
            thread(name = "towerduel-connect", isDaemon = true) {
                try {
                    val device = adapter?.getRemoteDevice(address) ?: throw IOException("no adapter")
                    var tries = 0
                    while (!cancelled) {
                        val s = device.createRfcommSocketToServiceRecord(SERVICE)
                        socket = s
                        try {
                            s.connect()
                            if (cancelled) s.close() else link = BluetoothLink(s)
                            break
                        } catch (e: IOException) {
                            runCatching { s.close() }
                            // A first try often fails for no reason the second one shares: the phones have
                            // not spoken since they were paired, or the host opened its lobby a moment ago.
                            if (++tries >= CONNECT_TRIES) throw e
                            Thread.sleep(RETRY_AFTER_MS)
                        }
                    }
                } catch (e: Exception) {
                    // IOException: not hosting or out of reach. SecurityException: the permission was taken away.
                    failed = true
                }
            }
        }

        fun cancel() {
            cancelled = true
            runCatching { socket?.close() }
            link?.close()
        }
    }

    private companion object {
        const val SERVICE_NAME = "TowerDuel"
        const val CONNECT_TRIES = 2
        const val RETRY_AFTER_MS = 400L

        /** This game's own service id: what a host listens on and a guest asks for. */
        val SERVICE: UUID = UUID.fromString("7c1f6d4e-3b0a-4f6e-9a57-54d0c7a1e2b9")
    }
}

/**
 * A [Link] over one Bluetooth socket. Messages are framed by their length. A reader thread
 * collects what arrives and a writer thread sends, so neither [send] nor [poll] ever waits on
 * the radio.
 */
private class BluetoothLink(private val socket: BluetoothSocket) : Link {
    private val arrived = ConcurrentLinkedQueue<ByteArray>()
    private val writer = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "towerduel-send").apply { isDaemon = true } }
    private val output = DataOutputStream(BufferedOutputStream(socket.outputStream))

    @Volatile
    private var open = true

    override val isOpen: Boolean get() = open

    init {
        thread(name = "towerduel-receive", isDaemon = true) {
            try {
                val input = DataInputStream(BufferedInputStream(socket.inputStream))
                while (open) {
                    val length = input.readInt()
                    if (length < 0 || length > Message.MAX_BYTES) throw IOException("not one of ours")
                    val bytes = ByteArray(length)
                    input.readFully(bytes)
                    arrived.add(bytes)
                }
            } catch (e: IOException) {
                // The other phone has gone, or this end was closed.
            }
            shut()
        }
    }

    override fun send(message: ByteArray) {
        if (!open) return
        runCatching {
            writer.execute {
                try {
                    output.writeInt(message.size)
                    output.write(message)
                    output.flush()
                } catch (e: IOException) {
                    shut()
                }
            }
        }
    }

    override fun poll(): ByteArray? = arrived.poll()

    override fun close() {
        if (!open) return
        // Let what is already queued (a goodbye, say) go out before the socket closes.
        runCatching { writer.execute { shut() } }
        writer.shutdown()
    }

    private fun shut() {
        open = false
        runCatching { socket.close() }
    }
}
