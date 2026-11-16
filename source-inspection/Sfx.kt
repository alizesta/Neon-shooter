package com.claude.neonshooter

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.random.Random

/** Atari-style sounds: square waves + noise, generated in code (no audio assets). */
class Sfx(ctx: Context) {
    private val SR = 22050
    private val pool = SoundPool.Builder().setMaxStreams(8).setAudioAttributes(
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    ).build()
    private val ids = HashMap<String, Int>()
    var enabled = true
    var volume = 0.23f

    init {
        add(ctx, "shoot", gen(1100.0, 500.0, 60, 0.22))
        add(ctx, "boom", gen(2500.0, 300.0, 200, 0.45, true))
        add(ctx, "bigboom", gen(1800.0, 150.0, 650, 0.55, true))
        add(ctx, "hurt", gen(320.0, 60.0, 300, 0.4))
        add(ctx, "power", gen(560.0, 1450.0, 110, 0.32))
        add(ctx, "start", seq(80, 523.0, 659.0, 784.0, 1046.0))
        add(ctx, "over", seq(140, 440.0, 370.0, 311.0, 233.0))
    }

    private fun add(ctx: Context, name: String, pcm: ShortArray) {
        try {
            val f = File(ctx.cacheDir, "$name.wav")
            f.writeBytes(wav(pcm))
            ids[name] = pool.load(f.absolutePath, 1)
        } catch (e: Exception) {}
    }

    fun play(name: String) {
        if (!enabled) return
        val gain = volume.coerceIn(0f,1f)
        try { ids[name]?.let { pool.play(it,gain,gain,1,0,1f) } } catch (e: Exception) {}
    }

    private fun seq(noteMs: Int, vararg f: Double): ShortArray {
        val parts = f.map { gen(it, it, noteMs, 0.3) }
        val out = ShortArray(parts.sumOf { it.size })
        var o = 0
        for (p in parts) { p.copyInto(out, o); o += p.size }
        return out
    }

    private fun gen(f0: Double, f1: Double, ms: Int, vol: Double, noise: Boolean = false): ShortArray {
        val n = SR * ms / 1000
        val out = ShortArray(n)
        var ph = 0.0; var held = 1.0; var cnt = 0
        for (i in 0 until n) {
            val t = i.toDouble() / n
            val f = f0 + (f1 - f0) * t
            ph += f / SR
            val v = if (noise) {
                if (cnt-- <= 0) { held = if (Random.nextBoolean()) 1.0 else -1.0; cnt = (SR / f).toInt().coerceAtLeast(1) }
                held
            } else if (ph % 1.0 < 0.5) 1.0 else -1.0
            out[i] = (v * vol * (1 - t) * 32767).toInt().toShort()
        }
        return out
    }

    private fun wav(pcm: ShortArray): ByteArray {
        val bb = ByteBuffer.allocate(44 + pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray()); bb.putInt(36 + pcm.size * 2)
        bb.put("WAVE".toByteArray()); bb.put("fmt ".toByteArray())
        bb.putInt(16); bb.putShort(1); bb.putShort(1); bb.putInt(SR); bb.putInt(SR * 2)
        bb.putShort(2); bb.putShort(16)
        bb.put("data".toByteArray()); bb.putInt(pcm.size * 2)
        for (x in pcm) bb.putShort(x)
        return bb.array()
    }
}
