package com.claude.neonshooter

import android.app.Activity
import android.graphics.*
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.util.Log
import kotlin.math.*
import kotlin.random.Random

class GameView(private val act: Activity) : SurfaceView(act), SurfaceHolder.Callback, Runnable {

    private enum class St { MENU, PLAY, OVER }
    private class B(var x: Float, var y: Float, val vx: Float, val vy: Float, val mine: Boolean)
    private class E(var x: Float, var y: Float, val type: Int, var hp: Int, val bx: Float, val amp: Float, val t0: Float) {
        val maxHp = hp
        var age = 0f
        var dead = false
        var fire = Random.nextFloat() * 2f + 1f
        val hw = if (type == 2) 19f else if (type == 3) 11f else if (type == 4) 10f else if (type == 1) 9f else 8f
        val hh = 8f
    }
    private class Pt(var x: Float, var y: Float, val vx: Float, val vy: Float, var life: Float, val max: Float)
    private class Item(var x: Float, var y: Float, val type: Int) { var age = 0f }
    private class Btn(val id: String, val label: String, val r: RectF)
    private class Star(var x: Float, val y0: Float, val sp: Float, val sz: Float) { var y = y0 }

    // palette: gray / yellow / red / white (+ orange warning)
    private val BG = 0xFF111318.toInt(); private val STAR = 0xFF454D59.toInt()
    private val YEL = 0xFF35D9F2.toInt(); private val YGL = 0xFFFFFF75.toInt()
    private val RED = 0xFFFF6268.toInt(); private val RGL = 0xFFFFB4B8.toInt()
    private val DRED = 0xFFFFA044.toInt(); private val ORG = 0xFFFFB547.toInt()
    private val BOSS_COLOR = 0xFFFF4D91.toInt(); private val BOSS_GLOW = 0xFFFFA4CF.toInt()
    private val WHT = 0xFFFFFFFF.toInt(); private val WGL = 0xFFFFFFD6.toInt()

    private val lock = Any()
    private var thread: Thread? = null
    @Volatile private var running = false
    private val W = 220f
    private var sw = 0; private var sh = 0; private var s = 1f; private var wh = 320f
    private var st = St.MENU
    private val sfx = Sfx(act)
    private val prefs = act.getSharedPreferences("neon", 0)
    private var best = prefs.getInt("best", 0)

    private val bullets = ArrayList<B>(); private val enemies = ArrayList<E>()
    private val parts = ArrayList<Pt>(); private val stars = ArrayList<Star>()
    private val items = ArrayList<Item>()
    private var px = 90f; private var py = 270f
    private var lives = 3; private var score = 0; private var wave = 0
    private var invul = 0f; private var fireT = 0f; private var time = 0f
    private var weaponLevel = 1; private var weaponTimer = 0f
    private var rapidTimer = 0f; private var shieldTimer = 0f
    private var rocketTimer = 0f
    private var waiting = false; private var waveT = 0f; private var bannerT = 0f; private var overT = 0f
    private var kills = 0
    private val unlocked = HashSet<Int>()
    private var lx = 0f; private var ly = 0f

    private lateinit var bPlayer: Bitmap; private lateinit var bMini: Bitmap
    private lateinit var bE0: Bitmap; private lateinit var bE1: Bitmap; private lateinit var bBoss: Bitmap
    private lateinit var bE2: Bitmap; private lateinit var bE3: Bitmap; private lateinit var bBoss2: Bitmap; private lateinit var bBoss3: Bitmap
    private lateinit var bPB: Bitmap; private lateinit var bEB: Bitmap

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val spritePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) }

    init { holder.addCallback(this) }

    // ---------- surface / thread ----------
    override fun surfaceCreated(h: SurfaceHolder) {}
    override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, hh: Int) {
        synchronized(lock) { if (w != sw || hh != sh || !::bPlayer.isInitialized) setup(w, hh) }
        if (thread == null) { running = true; thread = Thread(this).also { it.start() } }
    }
    override fun surfaceDestroyed(h: SurfaceHolder) {
        running = false
        try { thread?.join() } catch (e: InterruptedException) {}
        thread = null
    }

    private fun setup(w: Int, h: Int) {
        sw = w; sh = h; s = w / W; wh = h / s
        val ps = max(2, (s * 2).roundToInt())
        bPlayer = Sprites.player(YEL, YGL, ps)
        bMini = Sprites.player(YEL, YGL, max(1, ps / 2))
        bE0 = Sprites.enemy(RED, RGL, ps, 0)
        bE1 = Sprites.enemy(DRED, 0xFFFFD18A.toInt(), ps, 1)
        bBoss = Sprites.enemy(BOSS_COLOR, BOSS_GLOW, ps, 2)
        bE2 = Sprites.enemy(0xFF62DFFF.toInt(), 0xFF9CEBFF.toInt(), ps, 3)
        bE3 = Sprites.enemy(0xFFB68CFF.toInt(), 0xFFD7BCFF.toInt(), ps, 4)
        bBoss2 = Sprites.enemy(0xFF35D9D2.toInt(), 0xFF35D9D2.toInt(), ps, 5)
        bBoss3 = Sprites.enemy(0xFFB46BFF.toInt(), 0xFFB46BFF.toInt(), ps, 6)
        bE2 = Sprites.enemy(0xFF62DFFF.toInt(), 0xFF9CEBFF.toInt(), ps, 3)
        bE3 = Sprites.enemy(0xFFB68CFF.toInt(), 0xFFD7BCFF.toInt(), ps, 4)
        bBoss2 = Sprites.enemy(0xFF35D9D2.toInt(), 0xFF35D9D2.toInt(), ps, 5)
        bBoss3 = Sprites.enemy(0xFFB46BFF.toInt(), 0xFFB46BFF.toInt(), ps, 6)
        bPB = Sprites.bullet(WHT, WGL, ps, true)
        bEB = Sprites.bullet(ORG, 0xFFFFD28C.toInt(), ps, false)
        if (stars.isEmpty()) repeat(60) {
            val l = Random.nextInt(3)
            stars.add(Star(Random.nextFloat() * W, Random.nextFloat() * wh, floatArrayOf(8f, 16f, 28f)[l], if (l == 2) 1.6f else 1f))
        }
        if (st != St.PLAY) { px = W / 2; py = wh - 45f }
    }

    override fun run() {
        var last = System.nanoTime()
        while (running) {
            val now = System.nanoTime()
            val dt = min(0.033f, (now - last) / 1e9f); last = now
            try { synchronized(lock) { update(dt) } }
            catch (e: Exception) {
                Log.e("NeonShooter", "Game update recovered from an error", e)
                synchronized(lock) { invul = max(invul, 1.0f); bullets.clear() }
            }
            var c: Canvas? = null
            try {
                c = holder.lockCanvas()
                if (c != null) synchronized(lock) { renderFrame(c) }
            } catch (e: Exception) {
                Log.e("NeonShooter", "Frame render skipped", e)
            } finally {
                if (c != null) try { holder.unlockCanvasAndPost(c) } catch (e: Exception) {}
            }
            try { Thread.sleep(8) } catch (e: InterruptedException) {}
        }
    }

    // ---------- input ----------
    override fun onTouchEvent(e: MotionEvent): Boolean {
        synchronized(lock) {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lx = e.x; ly = e.y
                    if (st != St.PLAY && !(st == St.OVER && overT < 0.7f)) {
                        buttons().firstOrNull { it.r.contains(e.x / s, e.y / s) }?.let { onBtn(it.id) }
                    }
                }
                MotionEvent.ACTION_MOVE -> if (st == St.PLAY) {
                    px = (px + (e.x - lx) / s).coerceIn(10f, W - 10f)
                    py = (py + (e.y - ly) / s).coerceIn(30f, wh - 14f)
                    lx = e.x; ly = e.y
                }
            }
        }
        return true
    }

    private fun onBtn(id: String) {
        when (id) {
            "play" -> startGame()
            "menu" -> st = St.MENU
            "lb" -> Play.showLeaderboard(act)
            "ach" -> Play.showAchievements(act)
            "signin" -> Play.signIn(act)
            "sound" -> sfx.enabled = !sfx.enabled
        }
    }

    private fun buttons(): List<Btn> {
        val l = ArrayList<Btn>()
        fun add(id: String, label: String, y: Float) { l.add(Btn(id, label, RectF(30f, y, 150f, y + 22f))) }
        when (st) {
            St.MENU -> {
                val y = wh * 0.45f
                add("play", "PLAY", y)
                add("lb", "LEADERBOARD", y + 30f)
                add("ach", "ACHIEVEMENTS", y + 60f)
                add("signin", if (Play.signedIn) "GOOGLE: ONLINE" else "GOOGLE SIGN-IN", y + 90f)
                add("sound", if (sfx.enabled) "SOUND: ON" else "SOUND: OFF", y + 120f)
            }
            St.OVER -> { val y = wh * 0.58f; add("play", "RETRY", y); add("menu", "MENU", y + 30f) }
            else -> {}
        }
        return l
    }

    // ---------- game logic ----------
    private fun startGame() {
        bullets.clear(); enemies.clear(); parts.clear(); items.clear()
        score = 0; lives = 3; wave = 0; kills = 0; invul = 2.2f; fireT = 0f; time = 0f
        weaponLevel = 1; weaponTimer = 0f; rapidTimer = 0f; shieldTimer = 0f; rocketTimer = 0f; rocketTimer = 0f
        px = W / 2; py = wh - 45f; waiting = true; waveT = 1.4f
        st = St.PLAY
        sfx.play("start")
    }

    private fun ach(resId: Int) { if (unlocked.add(resId)) Play.unlock(act, resId) }

    private fun spawnWave() {
        wave++
        bannerT = 2.0f
        if (wave >= 10) ach(R.string.achievement_wave_10)
        if (wave % 5 == 0) {
            enemies.add(E(W / 2, -20f, 2, 42 + wave * 7, W / 2, 0f, ((wave / 5) % 3).toFloat()).also { it.fire = 1.7f })
            return
        }
        val n = min(8 + wave / 2, 15)
        fun mk(x: Float, y: Float, amp: Float, t0: Float) {
            val strong = wave >= 3 && Random.nextFloat() < 0.38f
            val roll = Random.nextFloat()
            val type = when { wave >= 4 && roll < 0.16f -> 3; wave >= 6 && roll < 0.28f -> 4; strong -> 1; else -> 0 }
            val hp = when (type) { 1 -> 3 + wave / 10; 3 -> 2 + wave / 12; 4 -> 2; else -> if (wave >= 7) 2 else 1 }
            enemies.add(E(x, y, type, hp, x, amp, t0))
        }
        when (wave % 3) {
            0 -> for (i in 0 until n) mk((i + 0.5f) * W / n, -10f - (i % 2) * 14f, 0f, 0f)
            1 -> for (i in 0 until n) mk(W / 2, -10f - i * 24f, 55f, i * 0.6f)
            else -> for (i in 0 until n) {
                val side = if (i % 2 == 0) 1 else -1; val k = (i + 1) / 2
                mk(W / 2 + side * k * 20f, -10f - k * 14f, 0f, 0f)
            }
        }
    }

    private fun boom(x: Float, y: Float, n: Int, sp: Float = 60f) {
        repeat(n) {
            val a = Random.nextFloat() * 6.283f; val v = Random.nextFloat() * sp + 10f
            parts.add(Pt(x, y, cos(a) * v, sin(a) * v, 0.6f, 0.6f))
        }
    }

    private fun kill(e: E) {
        e.dead = true
        when (e.type) { 0 -> score += 10; 1 -> score += 30; else -> score += 500 }
        boom(e.x, e.y, if (e.type == 2) 70 else 16, if (e.type == 2) 90f else 55f)
        sfx.play(if (e.type == 2) "bigboom" else "boom")
        kills++
        if (kills == 1) ach(R.string.achievement_first_kill)
        if (score >= 1000) ach(R.string.achievement_score_1000)
        if (e.type == 2) { ach(R.string.achievement_boss_slayer); bullets.removeAll { !it.mine } }
        val dropChance = if (e.type == 2) 0.90f else 0.12f
        if (Random.nextFloat() < dropChance) {
            val roll = Random.nextFloat()
            val kind = when {
                lives <= 1 && roll < 0.30f -> 0
                roll < 0.18f -> 0
                roll < 0.38f -> 1
                roll < 0.55f -> 2
                roll < 0.72f -> 3
                roll < 0.86f -> 4
                else -> 5
            }
            items.add(Item(e.x, e.y, kind))
        }
    }

    private fun hurt() {
        if (invul > 0f) return
        if (shieldTimer > 0f) {
            shieldTimer = 0f; invul = 1.2f
            boom(px, py, 18, 58f); sfx.play("boom")
            return
        }
        lives = (lives - 1).coerceAtLeast(0)
        invul = 2.4f
        boom(px, py, 24, 70f)
        sfx.play("hurt")
        if (lives == 0) {
            st = St.OVER; overT = 0f
            sfx.play("over")
            if (score > best) { best = score; prefs.edit().putInt("best", best).apply() }
            act.runOnUiThread { try { Play.submit(act, score) } catch (e: Exception) { Log.e("NeonShooter", "Score submit skipped", e) } }
        }
    }

    private fun shoot() {
        val lvl = max(min(1 + wave / 4, 3), if (weaponTimer > 0f) weaponLevel else 1)
        when (lvl) {
            1 -> bullets.add(B(px, py - 9f, 0f, -170f, true))
            2 -> { bullets.add(B(px - 4f, py - 9f, 0f, -170f, true)); bullets.add(B(px + 4f, py - 9f, 0f, -170f, true)) }
            else -> {
                bullets.add(B(px, py - 9f, 0f, -170f, true))
                bullets.add(B(px - 5f, py - 6f, -22f, -165f, true)); bullets.add(B(px + 5f, py - 6f, 22f, -165f, true))
            }
        }
        if (rocketTimer > 0f && time % 0.55f < 0.18f) {
            bullets.add(B(px - 7f, py - 5f, -12f, -145f, true))
            bullets.add(B(px + 7f, py - 5f, 12f, -145f, true))
        }
        if (rocketTimer > 0f && time % 0.55f < 0.18f) {
            bullets.add(B(px - 7f, py - 5f, -12f, -145f, true))
            bullets.add(B(px + 7f, py - 5f, 12f, -145f, true))
        }
        sfx.play("shoot")
    }

    private fun update(dt: Float) {
        for (star in stars) { star.y += star.sp * dt; if (star.y > wh) { star.y = 0f; star.x = Random.nextFloat() * W } }
        for (p in parts) { p.x += p.vx * dt; p.y += p.vy * dt; p.life -= dt }
        parts.removeAll { it.life <= 0f }
        if (st == St.OVER) overT += dt
        if (st != St.PLAY) return

        time += dt; invul -= dt; fireT -= dt; bannerT -= dt
        weaponTimer = max(0f, weaponTimer - dt)
        rapidTimer = max(0f, rapidTimer - dt)
        shieldTimer = max(0f, shieldTimer - dt)
        rocketTimer = max(0f, rocketTimer - dt)
        rocketTimer = max(0f, rocketTimer - dt)
        if (fireT <= 0f) { fireT = if (rapidTimer > 0f) 0.095f else 0.17f; shoot() }

        // waves
        if (enemies.isEmpty()) {
            if (!waiting) { waiting = true; waveT = 1.6f }
            else { waveT -= dt; if (waveT <= 0f) { spawnWave(); waiting = false } }
        }

        // enemies
        val spd = min(18f + wave * 1.15f, 38f)
        for (e in enemies) {
            e.age += dt
            if (e.type == 2) {
                if (e.y < 42f) e.y += 26f * dt
                when (e.t0.toInt() % 3) {
                    0 -> when (e.t0.toInt() % 3) {
                    0 -> when (e.t0.toInt() % 3) {
                    0 -> when (e.t0.toInt() % 3) {
                    0 -> when (e.t0.toInt() % 3) {
                    0 -> when (e.t0.toInt() % 3) {
                    0 -> when (e.t0.toInt() % 3) {
                    0 -> when (e.t0.toInt() % 3) {
                    0 -> when (e.t0.toInt() % 3) {
                    0 -> e.x = W / 2 + sin(e.age * 0.82f) * (W / 2 - e.hw - 8f)
                    1 -> { e.x = W / 2 + sin(e.age * 1.65f) * (W / 2 - e.hw - 8f); e.y = 38f + sin(e.age * 1.2f) * 9f }
                    else -> { e.x = W / 2 + sin(e.age * 0.55f) * (W / 2 - e.hw - 8f); e.y += 9f * dt }
                }
                    1 -> { e.x = W / 2 + sin(e.age * 1.65f) * (W / 2 - e.hw - 8f); e.y = 38f + sin(e.age * 1.2f) * 9f }
                    else -> { e.x = W / 2 + sin(e.age * 0.55f) * (W / 2 - e.hw - 8f); e.y += 9f * dt }
                }
                    1 -> { e.x = W / 2 + sin(e.age * 1.65f) * (W / 2 - e.hw - 8f); e.y = 38f + sin(e.age * 1.2f) * 9f }
                    else -> { e.x = W / 2 + sin(e.age * 0.55f) * (W / 2 - e.hw - 8f); e.y += 9f * dt }
                }
                    1 -> { e.x = W / 2 + sin(e.age * 1.65f) * (W / 2 - e.hw - 8f); e.y = 38f + sin(e.age * 1.2f) * 9f }
                    else -> { e.x = W / 2 + sin(e.age * 0.55f) * (W / 2 - e.hw - 8f); e.y += 9f * dt }
                }
                    1 -> { e.x = W / 2 + sin(e.age * 1.65f) * (W / 2 - e.hw - 8f); e.y = 38f + sin(e.age * 1.2f) * 9f }
                    else -> { e.x = W / 2 + sin(e.age * 0.55f) * (W / 2 - e.hw - 8f); e.y += 9f * dt }
                }
                    1 -> { e.x = W / 2 + sin(e.age * 1.65f) * (W / 2 - e.hw - 8f); e.y = 38f + sin(e.age * 1.2f) * 9f }
                    else -> { e.x = W / 2 + sin(e.age * 0.55f) * (W / 2 - e.hw - 8f); e.y += 9f * dt }
                }
                    1 -> { e.x = W / 2 + sin(e.age * 1.65f) * (W / 2 - e.hw - 8f); e.y = 38f + sin(e.age * 1.2f) * 9f }
                    else -> { e.x = W / 2 + sin(e.age * 0.55f) * (W / 2 - e.hw - 8f); e.y += 9f * dt }
                }
                    1 -> { e.x = W / 2 + sin(e.age * 1.65f) * (W / 2 - e.hw - 8f); e.y = 38f + sin(e.age * 1.2f) * 9f }
                    else -> { e.x = W / 2 + sin(e.age * 0.55f) * (W / 2 - e.hw - 8f); e.y += 9f * dt }
                }
                    1 -> { e.x = W / 2 + sin(e.age * 1.65f) * (W / 2 - e.hw - 8f); e.y = 38f + sin(e.age * 1.2f) * 9f }
                    else -> { e.x = W / 2 + sin(e.age * 0.55f) * (W / 2 - e.hw - 8f); e.y += 9f * dt }
                }
            } else {
                e.y += spd * dt
                if (e.type == 1) {
                    val targetX = (px + sin(e.age * 1.25f + e.t0) * 13f).coerceIn(12f, W - 12f)
                    val shift = 28f * dt
                    e.x += (targetX - e.x).coerceIn(-shift, shift)
                } else if (e.type == 3) {
                    e.x = (e.bx + sin(e.age * 4.2f + e.t0) * 34f).coerceIn(10f, W - 10f)
                    e.y += spd * 1.18f * dt
                } else if (e.type == 4) {
                    e.x = (e.bx + sin(e.age * 0.9f + e.t0) * 18f).coerceIn(12f, W - 12f)
                } else {
                    val sway = max(e.amp, 10f + min(wave.toFloat(), 10f))
                    e.x = (e.bx + sin(e.age * 1.7f + e.t0) * sway).coerceIn(10f, W - 10f)
                }
            }
            e.fire -= dt
            if (e.fire <= 0f) {
                if (e.type == 2) {
                    e.fire = max(0.7f, 1.3f - wave * 0.02f)
                    for (i in -2..2) {
                        val a = (PI / 2 + i * 0.3).toFloat()
                        bullets.add(B(e.x, e.y + 8f, cos(a) * 60f, sin(a) * 60f, false))
                    }
                } else {
                    e.fire = Random.nextFloat() * 2.5f + 2.5f - min(wave * 0.05f, 1.5f)
                    if (e.y > 4f && e.y < wh * 0.55f) {
                        if (e.type == 1) {
                            val leadX = px + sin(time * 1.3f) * 8f
                            val dx = leadX - e.x; val dy = py - e.y; val d = max(1f, hypot(dx, dy))
                            bullets.add(B(e.x, e.y + 8f, dx / d * 82f, dy / d * 112f, false))
                        } else {
                            val drift = ((px - e.x) * 0.12f).coerceIn(-16f, 16f)
                            bullets.add(B(e.x, e.y + 8f, drift, 66f, false))
                        }
                    }
                }
            }
        }
        enemies.removeAll { it.y > wh + 12f }

        // bullets
        for (b in bullets) { b.x += b.vx * dt; b.y += b.vy * dt }
        bullets.removeAll { it.y < -10f || it.y > wh + 10f || it.x < -10f || it.x > W + 10f }

        // floating pickups
        for (item in items) {
            item.age += dt
            item.y += (24f + min(wave.toFloat(), 10f)) * dt
            item.x = (item.x + sin(item.age * 2.4f) * 8f * dt).coerceIn(8f, W - 8f)
        }
        val collected = ArrayList<Item>()
        for (item in items) if (hypot(item.x - px, item.y - py) < 15f) {
            when (item.type) {
                0 -> { if (lives < 5) lives++ else score += 100 }
                1 -> { weaponLevel = min(3, weaponLevel + 1); weaponTimer = 15f }
                2 -> rapidTimer = 9f
                3 -> shieldTimer = 10f
                4 -> {
                    val targets = enemies.toList()
                    targets.forEach { kill(it) }
                    bullets.removeAll { !it.mine }
                    boom(W / 2f, wh * 0.42f, 110, 150f)
                }
                else -> rocketTimer = 12f
            }
            collected.add(item); boom(item.x, item.y, 10, 42f); sfx.play("power")
        }
        items.removeAll(collected)
        items.removeAll { it.y > wh + 10f }

        // player bullets vs enemies
        val deadB = HashSet<B>()
        for (b in bullets) {
            if (!b.mine) continue
            for (e in enemies) {
                if (e.dead || e.y < -4f) continue
                if (abs(b.x - e.x) < e.hw + 1.5f && abs(b.y - e.y) < e.hh + 3f) {
                    deadB.add(b); e.hp--; boom(b.x, b.y, 2, 25f)
                    if (e.hp <= 0) kill(e)
                    break
                }
            }
        }
        enemies.removeAll { it.dead }

        // player hit
        if (invul <= 0f) {
            var hit = false
            for (b in bullets) if (!b.mine && abs(b.x - px) < 6f && abs(b.y - py) < 6f) { deadB.add(b); hit = true; break }
            if (!hit) for (e in enemies) if (abs(e.x - px) < e.hw + 2f && abs(e.y - py) < e.hh + 2f) {
                if (e.type != 2) { e.dead = true; boom(e.x, e.y, 14) }
                hit = true; break
            }
            if (hit) hurt()
        }
        bullets.removeAll(deadB)
        enemies.removeAll { it.dead }
    }

    // ---------- drawing ----------
    private fun spr(c: Canvas, b: Bitmap, x: Float, y: Float) {
        c.drawBitmap(b, x * s - b.width / 2f, y * s - b.height / 2f, spritePaint)
    }

    private fun drawItem(c: Canvas, item: Item) {
        val cx = item.x * s
        val cy = item.y * s
        val scale = s * (1f + sin(item.age * 5f) * 0.06f)
        val color = when (item.type) {
            0 -> 0xFFFF647C.toInt()
            1 -> 0xFF55D9FF.toInt()
            2 -> 0xFFFFD84D.toInt()
            3 -> 0xFFFF79D8.toInt()
            4 -> 0xFFFF8A35.toInt()
            else -> 0xFFB68CFF.toInt()
        }
        c.save()
        c.translate(cx, cy)
        c.scale(scale, scale)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.8f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; this.color = color }
        // A crisp circular badge; no bitmap blur, so the glow cannot become a square.
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.5f; paint.color = color
        c.drawCircle(0f, 0f, 8f, paint)
        paint.strokeWidth = 0.7f
        c.drawCircle(0f, 0f, 6.4f, paint)
        paint.strokeWidth = 1.8f
        when (item.type) {
            0 -> { // heart
                val path = Path().apply { moveTo(0f, 6f); cubicTo(-3f, 3f, -8f, 0f, -6f, -4f); cubicTo(-4f, -8f, -1f, -6f, 0f, -3f); cubicTo(2f, -7f, 6f, -8f, 7f, -4f); cubicTo(8f, 0f, 3f, 4f, 0f, 6f) }
                c.drawPath(path, p)
            }
            1 -> { // triple-shot upgrade
                c.drawLine(-4.5f, 2.5f, -4.5f, -2f, p); c.drawLine(0f, 2.5f, 0f, -4f, p); c.drawLine(4.5f, 2.5f, 4.5f, -2f, p)
                c.drawLine(-6f, -0.5f, -4.5f, -2f, p); c.drawLine(-3f, -0.5f, -4.5f, -2f, p)
                c.drawLine(-1.5f, -2f, 0f, -4f, p); c.drawLine(1.5f, -2f, 0f, -4f, p)
                c.drawLine(3f, -0.5f, 4.5f, -2f, p); c.drawLine(6f, -0.5f, 4.5f, -2f, p)
            }
            2 -> { // rapid-fire energy bolt
                val bolt = Path().apply { moveTo(1f, -6f); lineTo(-3.5f, 0f); lineTo(-0.5f, 0f); lineTo(-2f, 6f); lineTo(4f, -1.5f); lineTo(1f, -1.5f); close() }
                c.drawPath(bolt, p); c.drawLine(-5f, 4.5f, -3.5f, 3f, p); c.drawLine(4.5f, -5f, 5.5f, -6f, p)
            }
            3 -> { // shield
                val shield = Path().apply { moveTo(0f, -6f); lineTo(5f, -4f); lineTo(4f, 2f); lineTo(0f, 6f); lineTo(-4f, 2f); lineTo(-5f, -4f); close() }
                c.drawPath(shield, p); c.drawLine(-2f, 0f, -0.2f, 2f, p); c.drawLine(-0.2f, 2f, 3f, -2f, p)
            }
            4 -> { // nuke blast
                val burst = Path().apply { moveTo(0f, -6f); lineTo(1.5f, -2f); lineTo(5f, -4f); lineTo(3f, 0f); lineTo(6f, 2f); lineTo(2f, 3f); lineTo(1f, 6f); lineTo(-1.5f, 3f); lineTo(-5f, 4f); lineTo(-3f, 0f); lineTo(-6f, -2f); lineTo(-2f, -3f); close() }
                c.drawPath(burst, p); c.drawCircle(0f, 0f, 1.8f, p)
            }
            else -> { // guided rocket
                val rocket = Path().apply { moveTo(0f, -6f); quadTo(4f, -2f, 2.5f, 3f); lineTo(0f, 5f); lineTo(-2.5f, 3f); quadTo(-4f, -2f, 0f, -6f); close() }
                c.drawPath(rocket, p); c.drawLine(-2.5f, 1f, -5f, 4f, p); c.drawLine(2.5f, 1f, 5f, 4f, p)
                c.drawCircle(0f, -1.5f, 1.2f, p); c.drawLine(0f, 5f, 0f, 7f, p)
            }
        }
        c.restore()
    }

    private fun text(c: Canvas, t: String, x: Float, y: Float, size: Float, color: Int, align: Paint.Align = Paint.Align.CENTER) {
        tp.textSize = size * s; tp.color = color; tp.textAlign = align
        c.drawText(t, x * s, y * s, tp)
    }

    private fun renderFrame(c: Canvas) {
        if (!::bPlayer.isInitialized) return
        c.drawColor(BG)
        paint.style = Paint.Style.FILL
        for (star in stars) {
            paint.color = STAR
            c.drawRect(star.x * s, star.y * s, (star.x + star.sz) * s, (star.y + star.sz) * s, paint)
        }
        when (st) {
            St.MENU -> drawMenu(c)
            St.PLAY -> drawGame(c)
            St.OVER -> { drawGame(c); drawOver(c) }
        }
    }

    private fun drawGame(c: Canvas) {
        for (e in enemies) {
            val sprite = when (e.type) { 2 -> when (e.t0.toInt() % 3) { 0 -> bBoss; 1 -> bBoss2; else -> bBoss3 }; 1 -> bE1; 3 -> bE2; 4 -> bE3; else -> bE0 }
            spr(c, sprite, e.x, e.y)
        }
        for (item in items) drawItem(c, item)
        for (b in bullets) spr(c, if (b.mine) bPB else bEB, b.x, b.y)
        if (shieldTimer > 0f) {
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.4f * s; paint.color = 0xFFFF79D8.toInt()
            c.drawCircle(px * s, py * s, 16f * s, paint)
            paint.clearShadowLayer(); paint.style = Paint.Style.FILL
        }
        if (st == St.PLAY && (invul <= 0f || (time * 12).toInt() % 2 == 0)) spr(c, bPlayer, px, py)
        for (p in parts) {
            val k = 1f - p.life / p.max
            paint.color = if (k < 0.3f) WHT else if (k < 0.6f) YEL else RED
            paint.alpha = (255 * (p.life / p.max)).toInt().coerceIn(0, 255)
            c.drawRect(p.x * s, p.y * s, (p.x + 1.6f) * s, (p.y + 1.6f) * s, paint)
        }
        paint.alpha = 255
        text(c, "SCORE $score", 6f, 14f, 9f, YEL, Paint.Align.LEFT)
        for (i in 0 until lives) spr(c, bMini, W - 12f - i * 12f, 10f)
        val powerLabel = when {
            shieldTimer > 0f -> "SHIELD " + (shieldTimer.toInt() + 1)
            rapidTimer > 0f -> "RAPID " + (rapidTimer.toInt() + 1)
            weaponTimer > 0f -> "POWER " + (weaponTimer.toInt() + 1)
            else -> ""
        }
        if (powerLabel.isNotEmpty()) text(c, powerLabel, W / 2, wh - 10f, 7f, 0xFF92E9FF.toInt())
        enemies.firstOrNull { it.type == 2 }?.let { bs ->
            val frac = bs.hp.toFloat() / bs.maxHp
            paint.color = DRED; c.drawRect(20f * s, 20f * s, (20f + 140f * frac) * s, 23f * s, paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = s * 0.6f; paint.color = RED
            c.drawRect(20f * s, 20f * s, 160f * s, 23f * s, paint); paint.style = Paint.Style.FILL
        }
        if (bannerT > 0f && st == St.PLAY) {
            val boss = wave % 5 == 0
            text(c, if (boss) "!! BOSS !!" else "WAVE $wave", W / 2, wh * 0.4f, 16f, if (boss) RED else WHT)
        }
    }

    private fun drawButtons(c: Canvas) {
        for (b in buttons()) {
            val col = if (b.id == "play") YEL else 0xFFCCCCCC.toInt()
            paint.style = Paint.Style.STROKE; paint.strokeWidth = s * 0.8f; paint.color = col
            c.drawRoundRect(b.r.left * s, b.r.top * s, b.r.right * s, b.r.bottom * s, 3f * s, 3f * s, paint)
            paint.clearShadowLayer(); paint.style = Paint.Style.FILL
            text(c, b.label, W / 2, b.r.top + 14.5f, 9f, col)
        }
    }

    private fun drawMenu(c: Canvas) {
        text(c, "NEON", W / 2, wh * 0.16f, 34f, YEL)
        text(c, "SHOOTER", W / 2, wh * 0.16f + 26f, 24f, RED)
        spr(c, bPlayer, W / 2, wh * 0.33f + sin(System.nanoTime() / 4e8f) * 3f)
        text(c, "BEST $best", W / 2, wh * 0.42f, 8f, WHT)
        drawButtons(c)
    }

    private fun drawOver(c: Canvas) {
        paint.color = 0xAA000000.toInt(); c.drawRect(0f, 0f, sw.toFloat(), sh.toFloat(), paint)
        text(c, "GAME OVER", W / 2, wh * 0.3f, 22f, RED)
        text(c, "SCORE $score", W / 2, wh * 0.3f + 20f, 11f, YEL)
        text(c, if (score >= best) "NEW BEST!" else "BEST $best", W / 2, wh * 0.3f + 34f, 8f, WHT)
        if (overT >= 0.7f) drawButtons(c)
    }
}
