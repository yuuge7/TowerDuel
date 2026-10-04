package com.towerduel.game.ui.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import com.towerduel.game.engine.SoundCue
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

private const val SAMPLE_RATE = 22_050

/**
 * Every sound in the game, synthesized at startup: there are no audio files in the project.
 * Each cue is rendered to a short WAV in the cache directory and handed to a SoundPool.
 */
class SoundFx(context: Context) {

    @Volatile var enabled = true

    private val pool = SoundPool.Builder()
        .setMaxStreams(10)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val soundIds = IntArray(SoundCue.entries.size)
    private val lastPlayedMs = LongArray(SoundCue.entries.size)

    init {
        val dir = File(context.cacheDir, "sfx").apply { mkdirs() }
        // Synthesis takes a moment, so it stays off the main thread. Until a cue is loaded,
        // playing it is simply silent.
        thread(name = "sfx-synth", isDaemon = true) {
            for (cue in SoundCue.entries) {
                val file = File(dir, "${cue.name.lowercase()}.wav")
                runCatching {
                    file.writeBytes(wav(render(cue)))
                    soundIds[cue.ordinal] = pool.load(file.path, 1)
                }
            }
        }
    }

    /** Plays [cue] unless the same cue played a moment ago; rapid fire would otherwise turn into noise. */
    fun play(cue: SoundCue, volume: Float = 1f) {
        if (!enabled) return
        val id = soundIds[cue.ordinal]
        if (id == 0) return
        val now = SystemClock.uptimeMillis()
        if (now - lastPlayedMs[cue.ordinal] < minGapMs(cue)) return
        lastPlayedMs[cue.ordinal] = now
        val v = (volume * loudness(cue)).coerceIn(0f, 1f)
        // A little pitch variation keeps repeated pops from sounding mechanical.
        val rate = if (cue == SoundCue.POP || cue == SoundCue.SHOOT) 0.88f + Random.nextFloat() * 0.3f else 1f
        pool.play(id, v, v, 1, 0, rate)
    }

    fun release() = pool.release()

    private fun minGapMs(cue: SoundCue): Long = when (cue) {
        SoundCue.SHOOT -> 85
        SoundCue.POP -> 55
        SoundCue.SHOOT_HEAVY, SoundCue.ZAP -> 110
        SoundCue.BOOM, SoundCue.POP_BIG -> 120
        SoundCue.FREEZE -> 260
        SoundCue.COIN -> 160
        SoundCue.LEAK -> 140
        else -> 0
    }

    private fun loudness(cue: SoundCue): Float = when (cue) {
        SoundCue.SHOOT -> 0.22f
        SoundCue.SHOOT_HEAVY -> 0.4f
        SoundCue.ZAP -> 0.3f
        SoundCue.FREEZE -> 0.25f
        SoundCue.POP -> 0.55f
        SoundCue.BOOM -> 0.6f
        SoundCue.CLICK -> 0.6f
        else -> 0.8f
    }

    // -----------------------------------------------------------------------
    // Synthesis
    // -----------------------------------------------------------------------

    private fun render(cue: SoundCue): FloatArray = when (cue) {
        SoundCue.POP -> mix(sweep(0.075f, 950f, 280f, decay = 34f), noise(0.045f, decay = 70f, gain = 0.5f))
        SoundCue.POP_BIG -> mix(sweep(0.19f, 430f, 110f, decay = 16f), noise(0.1f, decay = 34f, gain = 0.6f))
        SoundCue.SHOOT -> mix(sweep(0.05f, 1900f, 900f, decay = 60f, gain = 0.6f), noise(0.03f, decay = 110f, gain = 0.5f))
        SoundCue.SHOOT_HEAVY -> mix(sweep(0.13f, 240f, 80f, decay = 22f), noise(0.07f, decay = 50f, gain = 0.7f))
        SoundCue.ZAP -> buzz(0.15f, 1500f, 700f, decay = 16f, jitter = 0.5f)
        SoundCue.FREEZE -> mix(sweep(0.24f, 2600f, 1500f, decay = 11f, gain = 0.5f), sweep(0.24f, 3900f, 2300f, decay = 13f, gain = 0.3f))
        SoundCue.BOOM -> mix(sweep(0.34f, 120f, 38f, decay = 9f), rumble(0.3f, decay = 11f, gain = 0.9f))
        SoundCue.COIN -> seq(tone(0.06f, 1319f, decay = 12f), tone(0.16f, 1976f, decay = 14f))
        SoundCue.LEAK -> buzz(0.28f, 320f, 110f, decay = 7f, jitter = 0.05f)
        SoundCue.PLACE -> mix(sweep(0.12f, 300f, 130f, decay = 26f), noise(0.03f, decay = 90f, gain = 0.5f))
        SoundCue.UPGRADE -> seq(tone(0.07f, 1047f, 9f), tone(0.07f, 1319f, 9f), tone(0.07f, 1568f, 9f), tone(0.2f, 2093f, 10f))
        SoundCue.SELL -> seq(tone(0.07f, 1568f, 14f), tone(0.14f, 1047f, 14f))
        SoundCue.SEND -> mix(sweep(0.14f, 320f, 760f, decay = 14f, gain = 0.8f), noise(0.1f, decay = 26f, gain = 0.25f))
        SoundCue.ROUND -> seq(horn(0.14f, 440f), horn(0.3f, 659f))
        SoundCue.WARNING -> seq(horn(0.13f, 880f), horn(0.13f, 622f), horn(0.13f, 880f), horn(0.2f, 622f))
        SoundCue.EVENT -> seq(tone(0.09f, 784f, 8f), tone(0.09f, 1175f, 8f), tone(0.24f, 1568f, 7f))
        SoundCue.WIN -> seq(horn(0.13f, 523f), horn(0.13f, 659f), horn(0.13f, 784f), horn(0.5f, 1047f))
        SoundCue.LOSE -> seq(horn(0.2f, 392f), horn(0.2f, 330f), horn(0.5f, 262f))
        SoundCue.CLICK -> mix(sweep(0.04f, 1300f, 800f, decay = 70f), noise(0.012f, decay = 200f, gain = 0.4f))
        SoundCue.DENIED -> seq(buzz(0.07f, 190f, 170f, decay = 8f, jitter = 0f), FloatArray(SAMPLE_RATE / 40), buzz(0.09f, 160f, 140f, decay = 8f, jitter = 0f))
    }

    private fun samples(seconds: Float) = (seconds * SAMPLE_RATE).toInt().coerceAtLeast(1)

    /** A sine gliding from [from] to [to] Hz with an exponential fade. */
    private fun sweep(seconds: Float, from: Float, to: Float, decay: Float, gain: Float = 1f): FloatArray {
        val n = samples(seconds)
        val out = FloatArray(n)
        var phase = 0.0
        for (i in 0 until n) {
            val t = i / n.toFloat()
            phase += 2.0 * PI * (from + (to - from) * t) / SAMPLE_RATE
            out[i] = (sin(phase) * exp(-decay * i / SAMPLE_RATE.toFloat())).toFloat() * gain * attack(i)
        }
        return out
    }

    private fun tone(seconds: Float, freq: Float, decay: Float): FloatArray {
        val n = samples(seconds)
        val out = FloatArray(n)
        for (i in 0 until n) {
            val t = i / SAMPLE_RATE.toFloat()
            val bell = sin(2.0 * PI * freq * t) + 0.35 * sin(2.0 * PI * freq * 2.0 * t)
            out[i] = (bell * exp(-decay * t)).toFloat() * 0.7f * attack(i)
        }
        return out
    }

    /** A soft brassy note: a few harmonics with a slow fade, shaped at both ends. */
    private fun horn(seconds: Float, freq: Float): FloatArray {
        val n = samples(seconds)
        val out = FloatArray(n)
        for (i in 0 until n) {
            val t = i / SAMPLE_RATE.toFloat()
            val wave = sin(2.0 * PI * freq * t) + 0.5 * sin(2.0 * PI * freq * 2.0 * t) + 0.25 * sin(2.0 * PI * freq * 3.0 * t)
            val release = ((n - i) / (SAMPLE_RATE * 0.03f)).coerceAtMost(1f)
            out[i] = (wave * exp(-2.5 * t)).toFloat() * 0.5f * attack(i) * release
        }
        return out
    }

    /** A square-ish wave; [jitter] makes it crackle like electricity. */
    private fun buzz(seconds: Float, from: Float, to: Float, decay: Float, jitter: Float): FloatArray {
        val n = samples(seconds)
        val out = FloatArray(n)
        val rnd = Random(from.toInt())
        var phase = 0.0
        var wobble = 0f
        for (i in 0 until n) {
            val t = i / n.toFloat()
            if (i % 40 == 0) wobble = (rnd.nextFloat() - 0.5f) * 2f * jitter
            phase += 2.0 * PI * (from + (to - from) * t) * (1f + wobble) / SAMPLE_RATE
            val square = if (sin(phase) >= 0) 1f else -1f
            out[i] = square * 0.45f * exp(-decay * i / SAMPLE_RATE.toFloat()) * attack(i)
        }
        return out
    }

    private fun noise(seconds: Float, decay: Float, gain: Float): FloatArray {
        val n = samples(seconds)
        val rnd = Random(n)
        return FloatArray(n) { i -> (rnd.nextFloat() * 2f - 1f) * gain * exp(-decay * i / SAMPLE_RATE.toFloat()) }
    }

    /** Low-passed noise: the body of an explosion. */
    private fun rumble(seconds: Float, decay: Float, gain: Float): FloatArray {
        val n = samples(seconds)
        val rnd = Random(n + 7)
        val out = FloatArray(n)
        var smooth = 0f
        for (i in 0 until n) {
            smooth += ((rnd.nextFloat() * 2f - 1f) - smooth) * 0.08f
            out[i] = smooth * 3f * gain * exp(-decay * i / SAMPLE_RATE.toFloat())
        }
        return out
    }

    /** A few milliseconds of fade-in, so a sound never starts with a click. */
    private fun attack(i: Int): Float = (i / (SAMPLE_RATE * 0.003f)).coerceAtMost(1f)

    private fun mix(vararg parts: FloatArray): FloatArray {
        val out = FloatArray(parts.maxOf { it.size })
        for (part in parts) for (i in part.indices) out[i] += part[i]
        return out
    }

    private fun seq(vararg parts: FloatArray): FloatArray {
        val out = FloatArray(parts.sumOf { it.size })
        var at = 0
        for (part in parts) {
            part.copyInto(out, at)
            at += part.size
        }
        return out
    }

    /** Wraps mono float samples as a 16-bit PCM WAV file. */
    private fun wav(samples: FloatArray): ByteArray {
        val peak = samples.maxOf { kotlin.math.abs(it) }.coerceAtLeast(1f)
        val dataSize = samples.size * 2
        val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray()).putInt(36 + dataSize).put("WAVE".toByteArray())
        buffer.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
        buffer.putInt(SAMPLE_RATE).putInt(SAMPLE_RATE * 2).putShort(2).putShort(16)
        buffer.put("data".toByteArray()).putInt(dataSize)
        for (s in samples) buffer.putShort((s / peak * 0.9f * Short.MAX_VALUE).toInt().toShort())
        return buffer.array()
    }
}
