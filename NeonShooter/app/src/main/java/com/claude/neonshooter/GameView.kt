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

    private enum class St { MENU, PLAY, PAUSE, OVER }
    private class B(var x: Float, var y: Float, var vx: Float, var vy: Float, val mine: Boolean) { val hitEnemies = HashSet<E>() }
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
    private class PlayerBeam(var x: Float, var bottom: Float, val width: Float, var life: Float)
    private class Missile(var x: Float, var y: Float, var vx: Float, var vy: Float, val damage: Int, var age: Float = 0f)
    private class EnemyLaser(var x: Float, var warning: Float, var active: Float, val width: Float, val drift: Float, val owner: E? = null, val offset: Float = 0f)
    private class SweepShip(var x: Float, var y: Float)
    private class Btn(val id: String, val label: String, val r: RectF)
    private class Star(var x: Float, val y0: Float, val sp: Float, val sz: Float) {
        var y = y0
        var phase = Random.nextFloat() * 6.283f
        val twinkle = Random.nextFloat() < 0.72f
    }
    private class BlackHole(var x: Float, var y: Float, val radius: Float, val strength: Float, val drift: Float) {
        var age = 0f
        var spin = Random.nextFloat() * 6.283f
    }
    private class Comet(var x: Float, var y: Float, val vx: Float, val vy: Float, val length: Float, var life: Float, val maxLife: Float)

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
    private val playerBeams = ArrayList<PlayerBeam>()
    private val missiles = ArrayList<Missile>()
    private val enemyLasers = ArrayList<EnemyLaser>()
    private val squadShips = ArrayList<SweepShip>()
    private val blackHoles = ArrayList<BlackHole>()
    private val comets = ArrayList<Comet>()
    private lateinit var bossSprites: Array<Bitmap>
    private var px = 90f; private var py = 270f
    private var lives = 3; private var score = 0; private var wave = 0
    private var invul = 0f; private var fireT = 0f; private var time = 0f
    private var weaponLevel = prefs.getInt("upgrade_weapon", 1).coerceIn(1, 5)
    private var missileLevel = prefs.getInt("upgrade_missile", 0).coerceIn(0, 3)
    private var laserLevel = prefs.getInt("upgrade_laser", 0).coerceIn(0, 3)
    private var droneLevel = prefs.getInt("upgrade_drone", 0).coerceIn(0, 2)
    private var weaponTimer = 0f
    private var piercingTimer = 0f
    private var squadTimer = 0f; private var squadSpawnTimer = 0f
    private var cometSpawnTimer = 2.5f
    private var rapidTimer = 0f; private var shieldTimer = 0f
private var rocketTimer = 0f; private var magnetTimer = 0f
    private var laserCooldown = 0f; private var missileCooldown = 0f
    private var controlTouch = false
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
        val bossPalette = intArrayOf(0xFFFF4D91.toInt(), 0xFF32D8F2.toInt(), 0xFFFF9B42.toInt(), 0xFF9C7BFF.toInt(), 0xFFFF5964.toInt(), 0xFF52E0A0.toInt(), 0xFFFFD14A.toInt(), 0xFF4E9EFF.toInt(), 0xFFFF70D4.toInt(), 0xFFE8F1FF.toInt())
        bossSprites = Array(10) { i -> Sprites.enemy(bossPalette[i], bossPalette[i], ps, 5 + i) }
        bPB = Sprites.bullet(WHT, WGL, ps, true)
        bEB = Sprites.bullet(ORG, 0xFFFFD28C.toInt(), ps, false)
        if (stars.isEmpty()) repeat(90) {
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
                    lx = e.x; ly = e.y; controlTouch = false
                    if (st == St.PLAY) {
                        val x = e.x / s; val y = e.y / s
                        if (x >= W - 32f && y <= 30f) {
                            st = St.PAUSE
                            controlTouch = true
                        } else if (y >= wh - 34f && x >= W - 96f) {
                            if (x >= W - 52f) fireLaser() else launchMissiles()
                            controlTouch = true
                        }
                    } else if (!(st == St.OVER && overT < 0.7f)) {
                        buttons().firstOrNull { it.r.contains(e.x / s, e.y / s) }?.let { onBtn(it.id) }
                    }
                }
                MotionEvent.ACTION_MOVE -> if (st == St.PLAY && !controlTouch) {
                    px = (px + (e.x - lx) / s).coerceIn(10f, W - 10f)
                    py = (py + (e.y - ly) / s).coerceIn(30f, wh - 48f)
                    lx = e.x; ly = e.y
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> controlTouch = false
            }
        }
        return true
    }

    private fun onBtn(id: String) {
        when (id) {
            "play" -> startGame()
            "menu" -> st = St.MENU
            "resume" -> st = St.PLAY
            "pauseRestart" -> startGame()
            "lb" -> Play.showLeaderboard(act)
            "ach" -> Play.showAchievements(act)
            "signin" -> Play.signIn(act)
            "sound" -> sfx.enabled = !sfx.enabled
        }
    }

    private fun buttons(): List<Btn> {
        val l = ArrayList<Btn>()
        fun add(id: String, label: String, y: Float) { l.add(Btn(id, label, RectF(50f, y, 170f, y + 22f))) }
        when (st) {
            St.MENU -> {
                val y = wh * 0.45f
                add("play", "PLAY", y)
                add("lb", "LEADERBOARD", y + 30f)
                add("ach", "ACHIEVEMENTS", y + 60f)
                add("signin", if (Play.signedIn) "GOOGLE: ONLINE" else "GOOGLE SIGN-IN", y + 90f)
                add("sound", if (sfx.enabled) "SOUND: ON" else "SOUND: OFF", y + 120f)
            }
            St.PAUSE -> {
                val y = wh * 0.42f
                add("resume", "RESUME", y)
                add("pauseRestart", "RESTART", y + 30f)
                add("menu", "MAIN MENU", y + 60f)
            }
            St.OVER -> { val y = wh * 0.58f; add("play", "RETRY", y); add("menu", "MENU", y + 30f) }
            else -> {}
        }
        return l
    }

    // ---------- game logic ----------
    private fun startGame() {
        bullets.clear(); enemies.clear(); parts.clear(); items.clear(); playerBeams.clear(); missiles.clear(); enemyLasers.clear(); squadShips.clear(); blackHoles.clear()
        score = 0; lives = 3; wave = 0; kills = 0; invul = 2.2f; fireT = 0f; time = 0f
        weaponTimer = 0f; rapidTimer = 0f; shieldTimer = 0f; magnetTimer = 0f
        laserCooldown = 3.5f; missileCooldown = 6f
        piercingTimer = 0f; squadTimer = 0f; squadSpawnTimer = 0f
        px = W / 2; py = wh - 45f; waiting = true; waveT = 1.4f
        st = St.PLAY
        sfx.play("start")
    }

    private fun ach(resId: Int) { if (unlocked.add(resId)) Play.unlock(act, resId) }

    private fun spawnWave() {
        wave++
        if (wave >= 3 && wave % 3 == 0 && blackHoles.size < 2) {
            val side = if (Random.nextBoolean()) 1f else -1f
            blackHoles.add(BlackHole(Random.nextFloat() * (W - 76f) + 38f, -18f, 48f + Random.nextFloat() * 12f, 72000f, side * (5f + Random.nextFloat() * 8f)))
        }
        bannerT = 2.0f
        if (wave >= 10) ach(R.string.achievement_wave_10)
        if (wave % 5 == 0) {
            enemies.add(E(W / 2, -20f, 2, 110 + wave * 18, W / 2, 0f, ((wave / 5 - 1).coerceIn(0, 9)).toFloat()).also { it.fire = 1.7f })
            return
        }
        val n = min(8 + wave / 2, 15)
        fun mk(x: Float, y: Float, amp: Float, t0: Float) {
            val strong = wave >= 3 && Random.nextFloat() < 0.38f
            val roll = Random.nextFloat()
            val type = when { wave >= 4 && roll < 0.16f -> 3; wave >= 6 && roll < 0.28f -> 4; strong -> 1; else -> 0 }
            val hp = when (type) { 1 -> 5 + wave / 8; 3 -> 4 + wave / 10; 4 -> 3 + wave / 12; else -> 2 + wave / 12 }
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
        val dropChance = if (e.type == 2) 0.88f else 0.15f
        if (Random.nextFloat() < dropChance) {
            val roll = Random.nextFloat()
            val kind = when {
                lives < 5 && roll < 0.28f -> 0
                roll < 0.25f -> 1
                roll < 0.39f -> 2
                roll < 0.51f -> 3
                roll < 0.54f -> 4
                roll < 0.74f -> 5
                roll < 0.83f -> 6
                roll < 0.90f -> 7
                roll < 0.96f -> 8
                roll < 0.985f -> 9
                else -> 10
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
        val count = min(1 + (weaponLevel - 1), 5)
        for (i in 0 until count) {
            val offset = i - (count - 1) / 2
            bullets.add(B(px + offset * 3.6f, py - 9f, offset * 13f, -178f, true))
        }
        if (droneLevel >= 1) {
            bullets.add(B(px - 17f, py - 5f, -8f, -165f, true))
            bullets.add(B(px + 17f, py - 5f, 8f, -165f, true))
        }
        if (droneLevel >= 2) {
            bullets.add(B(px - 27f, py - 1f, -17f, -155f, true))
            bullets.add(B(px + 27f, py - 1f, 17f, -155f, true))
        }
        sfx.play("shoot")
    }

    private fun fireLaser() {
        if (st != St.PLAY || laserCooldown > 0f) return
        val width = 5f + laserLevel * 2.2f
        playerBeams.add(PlayerBeam(px, py, width, 0.24f))
        val damage = 2 + laserLevel * 3
        for (enemy in enemies) {
            if (!enemy.dead && enemy.y < py && abs(enemy.x - px) < width + enemy.hw * 0.35f) {
                enemy.hp -= damage
                boom(enemy.x, enemy.y, 5, 38f)
                if (enemy.hp <= 0) kill(enemy)
            }
        }
        laserCooldown = max(2.3f, 5.5f - laserLevel * 0.75f)
        sfx.play("laser")
    }

    private fun launchMissiles() {
        if (st != St.PLAY || missileCooldown > 0f) return
        val count = 2 + missileLevel
        for (i in 0 until count) {
            val offset = (i - (count - 1) / 2) * 7f
            missiles.add(Missile((px + offset).coerceIn(5f, W - 5f), py - 8f, offset * 0.8f, -115f, 2 + missileLevel * 2))
        }
        missileCooldown = max(1.8f, 5.2f - missileLevel * 0.65f)
        sfx.play("missile")
    }

    private fun spawnSupportShips() { for (offset in floatArrayOf(-42f, -14f, 14f, 42f)) squadShips.add(SweepShip((px + offset).coerceIn(8f, W - 8f), wh + 14f)) }
    private fun activateSquadron() { squadTimer = 12f; squadSpawnTimer = 0f; bullets.removeAll { !it.mine }; sfx.play("power") }

    private fun update(dt: Float) {
        for (star in stars) star.phase += dt * (2.4f + star.sp * 0.16f)
        cometSpawnTimer -= dt
        if (cometSpawnTimer <= 0f) {
            val speed = 34f + Random.nextFloat() * 18f
            comets.add(Comet(Random.nextFloat() * W, -3f, (Random.nextFloat() - 0.5f) * 8f, speed, 4f + Random.nextFloat() * 4f, 5.5f, 5.5f))
            cometSpawnTimer = 5f + Random.nextFloat() * 7f
        }
        for (comet in comets) {
            comet.x += comet.vx * dt
            comet.y += comet.vy * dt
            comet.life -= dt
        }
        comets.removeAll { it.life <= 0f || it.y > wh + 12f }
        for (p in parts) { p.x += p.vx * dt; p.y += p.vy * dt; p.life -= dt }
        parts.removeAll { it.life <= 0f }
        if (st == St.OVER) overT += dt
        if (st != St.PLAY) return

        time += dt; invul -= dt; fireT -= dt; bannerT -= dt
        weaponTimer = max(0f, weaponTimer - dt)
        rapidTimer = max(0f, rapidTimer - dt)
        shieldTimer = max(0f, shieldTimer - dt)
        magnetTimer = max(0f, magnetTimer - dt)
        laserCooldown = max(0f, laserCooldown - dt)
        missileCooldown = max(0f, missileCooldown - dt)
        piercingTimer = max(0f, piercingTimer - dt)
        squadTimer = max(0f, squadTimer - dt)
        squadSpawnTimer = max(0f, squadSpawnTimer - dt)
        if (squadTimer > 0f && squadSpawnTimer <= 0f) { spawnSupportShips(); squadSpawnTimer = 2.4f }
        if (laserCooldown <= 0f) fireLaser()
        if (missileCooldown <= 0f) launchMissiles()
        for (beam in playerBeams) { beam.x = px; beam.bottom = py; beam.life -= dt }
        playerBeams.removeAll { it.life <= 0f }
        if (fireT <= 0f) { fireT = if (rapidTimer > 0f) 0.075f else 0.19f; shoot() }

        // waves
        if (enemies.isEmpty()) {
            if (!waiting) { waiting = true; waveT = 1.6f }
            else { waveT -= dt; if (waveT <= 0f) { spawnWave(); waiting = false } }
        }

        // enemies
        val spd = min(18f + wave * 1.15f, 38f)
        val summonedEnemies = ArrayList<E>()
        for (e in enemies) {
            if (e.type == 2) {
                val bossId = e.t0.toInt().coerceIn(0, 9)
                val span = W / 2f - e.hw - 8f
                if (e.y < 42f) e.y += 32f * dt
                when (bossId) {
                    0 -> e.x = W / 2f + sin(e.age * 0.62f) * span
                    1 -> { e.x = W / 2f + sin(e.age * 1.55f) * span; e.y = 40f + sin(e.age * 1.2f) * 8f }
                    2 -> { e.x += (px - e.x).coerceIn(-55f * dt, 55f * dt); e.y = 38f + sin(e.age * 0.8f) * 12f }
                    3 -> e.x = W / 2f + sin(e.age * 0.42f) * span
                    4 -> { e.x = W / 2f + sin(e.age * 2.15f) * span; e.y = 38f + abs(sin(e.age * 0.72f)) * 16f }
                    5 -> { e.x = W / 2f + sin(e.age * 0.86f) * span; e.y = 36f + sin(e.age * 1.7f) * 9f }
                    6 -> e.x = W / 2f + sin(e.age * 0.31f) * span
                    7 -> { e.x = W / 2f + sin(e.age * 1.08f) * span; e.y = 40f + abs(sin(e.age * 0.52f)) * 18f }
                    8 -> e.x = W / 2f + sin(e.age * 1.82f) * span
                    else -> { e.x = W / 2f + sin(e.age * 0.94f) * span; e.y = 37f + sin(e.age * 1.35f) * 12f }
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
                    val bossId = e.t0.toInt().coerceIn(0, 9)
                    val enraged = e.hp * 2 <= e.maxHp
                    e.fire = max(0.48f, (if (enraged) 0.72f else 1.12f) - wave * 0.006f)
                    val base = atan2((py - e.y).toDouble(), (px - e.x).toDouble()).toFloat()
                    val speed = 74f + min(wave.toFloat(), 50f) * 1.1f
                    when (bossId) {
                        0 -> for (i in -2..2) { val a = base + i * 0.16f; bullets.add(B(e.x, e.y + 8f, cos(a) * speed, sin(a) * speed, false)) }
                        1 -> enemyLasers.add(EnemyLaser(e.x, 0.62f, 0.72f, 7f, 0f, e))
                        2 -> {
                            for (side in -1..1 step 2) summonedEnemies.add(E((e.x + side * 16f).coerceIn(12f, W - 12f), e.y + 8f, 3, 2 + wave / 16, e.x + side * 16f, 18f, e.age + side))
                            for (i in -1..1) { val a = base + i * 0.22f; bullets.add(B(e.x, e.y + 8f, cos(a) * speed, sin(a) * speed, false)) }
                        }
                        3 -> for (i in 0 until 18) { val a = i * (2f * PI.toFloat() / 18f) + e.age * 0.18f; bullets.add(B(e.x, e.y, cos(a) * speed * 0.82f, sin(a) * speed * 0.82f, false)) }
                        4 -> for (i in 0..2) { val a = e.age * 1.7f + i * (2f * PI.toFloat() / 3f); bullets.add(B(e.x, e.y, cos(a) * speed, sin(a) * speed, false)); bullets.add(B(e.x, e.y, cos(a + PI.toFloat()) * speed, sin(a + PI.toFloat()) * speed, false)) }
                        5 -> repeat(7) { val x = Random.nextFloat() * W; bullets.add(B(x, e.y + 8f, (px - x) * 0.08f, speed * 0.9f, false)) }
                        6 -> for (i in -3..3) { val a = base + i * 0.12f; bullets.add(B(e.x, e.y + 8f, cos(a) * speed, sin(a) * speed, false)) }
                        7 -> {
                            enemyLasers.add(EnemyLaser(e.x - 26f, 0.55f, 0.7f, 6f, 0f, e, -26f))
                            enemyLasers.add(EnemyLaser(e.x + 26f, 0.55f, 0.7f, 6f, 0f, e, 26f))
                        }
                        8 -> {
                            for (i in 0 until 12) { val a = i * (2f * PI.toFloat() / 12f) + e.age * 0.24f; bullets.add(B(e.x, e.y, cos(a) * speed, sin(a) * speed, false)) }
                            for (i in -1..1) { val a = base + i * 0.2f; bullets.add(B(e.x, e.y + 8f, cos(a) * speed * 1.12f, sin(a) * speed * 1.12f, false)) }
                        }
                        else -> {
                            enemyLasers.add(EnemyLaser(e.x, 0.48f, 0.58f, 8f, 0f, e))
                            for (i in -2..2) { val a = base + i * 0.18f; bullets.add(B(e.x, e.y + 8f, cos(a) * speed, sin(a) * speed, false)) }
                        }
                    }
                } else {
                    e.fire = when (e.type) {
                        1 -> max(0.48f, 1.12f - wave * 0.012f)
                        3 -> max(0.58f, 1.36f - wave * 0.013f)
                        4 -> max(0.72f, 1.62f - wave * 0.011f)
                        else -> max(0.82f, 1.82f - wave * 0.010f)
                    }
                    if (e.y > 4f && e.y < wh * 0.72f) {
                        val dx = px + sin(time * 1.6f + e.t0) * 9f - e.x
                        val dy = py - e.y
                        val base = atan2(dy.toDouble(), dx.toDouble()).toFloat()
                        val speed = 72f + min(wave.toFloat(), 45f) * 1.4f
                        when (e.type) {
                            1 -> for (i in -1..1) { val a = base + i * 0.15f; bullets.add(B(e.x, e.y + 7f, cos(a) * speed, sin(a) * speed, false)) }
                            3 -> for (i in -1..1) { val a = base + i * 0.23f; bullets.add(B(e.x, e.y + 7f, cos(a) * speed * 1.1f, sin(a) * speed * 1.1f, false)) }
                            4 -> for (i in -2..2) { val a = base + i * 0.12f; bullets.add(B(e.x, e.y + 7f, cos(a) * speed * 0.88f, sin(a) * speed * 0.88f, false)) }
                            else -> for (i in -1..1) { val a = base + i * 0.12f; bullets.add(B(e.x, e.y + 7f, cos(a) * speed, sin(a) * speed, false)) }
                        }
                    }
                }
            }
        }
        enemies.addAll(summonedEnemies)
        for (laser in enemyLasers) {
            if (laser.owner != null && !laser.owner.dead) laser.x = laser.owner.x + laser.offset
            else if (laser.owner == null) laser.x += laser.drift * dt
            if (laser.warning > 0f) laser.warning = max(0f, laser.warning - dt)
            else if (laser.active > 0f) {
                laser.active = max(0f, laser.active - dt)
                if (invul <= 0f && py < wh && py > 8f && abs(px - laser.x) < laser.width * 0.5f + 6f) {
                    hurt()
                    laser.active = 0f
                }
            }
        }
        enemyLasers.removeAll { it.warning <= 0f && it.active <= 0f }
        enemies.removeAll { it.y > wh + 12f }

        // Gravitational wells bend both player and enemy projectiles as they pass nearby.
        for (hole in blackHoles) {
            hole.age += dt
            hole.spin += dt * 1.8f
            hole.y += 8f * dt
            hole.x = (hole.x + sin(hole.age * 0.8f) * hole.drift * dt).coerceIn(18f, W - 18f)
        }
        blackHoles.removeAll { it.age > 11f || it.y > wh + 55f }
        
        // Homing player missiles and four-lane support squadron sweep.
        for (missile in missiles) {
            missile.age += dt
            val target = enemies.filter { !it.dead }.minByOrNull { hypot(it.x - missile.x, it.y - missile.y) }
            if (target != null) {
                val dx = target.x - missile.x; val dy = target.y - missile.y
                val d = max(1f, hypot(dx, dy))
                val desiredX = dx / d * 175f; val desiredY = dy / d * 175f
                missile.vx += (desiredX - missile.vx).coerceIn(-260f * dt, 260f * dt)
                missile.vy += (desiredY - missile.vy).coerceIn(-260f * dt, 260f * dt)
            } else { missile.vy = -175f; missile.vx *= 0.96f }
            missile.x += missile.vx * dt; missile.y += missile.vy * dt
            val hitTarget = target?.takeIf { !it.dead && hypot(it.x - missile.x, it.y - missile.y) < it.hw + 7f }
            if (hitTarget != null) {
                hitTarget.hp -= missile.damage
                boom(missile.x, missile.y, 12, 65f)
                for (nearby in enemies) if (!nearby.dead && nearby !== hitTarget && hypot(nearby.x - missile.x, nearby.y - missile.y) < 17f) {
                    nearby.hp -= max(1, missile.damage / 2)
                    if (nearby.hp <= 0) kill(nearby)
                }
                if (hitTarget.hp <= 0) kill(hitTarget)
                missile.age = 99f
            }
        }
        missiles.removeAll { it.age > 4f || it.y < -20f || it.x < -20f || it.x > W + 20f }
        for (ship in squadShips) {
            ship.y -= 260f * dt
            bullets.removeAll { !it.mine && abs(it.x - ship.x) < 8f && abs(it.y - ship.y) < 10f }
            for (enemy in enemies) if (!enemy.dead && abs(enemy.x - ship.x) < enemy.hw + 7f && abs(enemy.y - ship.y) < enemy.hh + 9f) kill(enemy)
        }
        squadShips.removeAll { it.y < -20f }
        enemies.removeAll { it.dead }

        // bullets
        for (b in bullets) {
            for (hole in blackHoles) {
                val dx = hole.x - b.x
                val dy = hole.y - b.y
                val d2 = dx * dx + dy * dy
                if (d2 < hole.radius * hole.radius && d2 > 9f) {
                    val distance = sqrt(d2)
                    val pull = min(620f, hole.strength * 5f / (d2 + 80f)) * dt
                    b.vx = (b.vx + dx / distance * pull).coerceIn(-220f, 220f)
                    b.vy = (b.vy + dy / distance * pull).coerceIn(-220f, 220f)
                }
            }
            b.x += b.vx * dt
            b.y += b.vy * dt
        }
        bullets.removeAll { it.y < -10f || it.y > wh + 10f || it.x < -10f || it.x > W + 10f }

        // floating pickups
        for (item in items) {
            item.age += dt
            if (magnetTimer > 0f) {
                item.x += (px - item.x) * min(1f, 3.0f * dt)
                item.y += (py - item.y) * min(1f, 3.0f * dt)
            } else {
                item.y += (24f + min(wave.toFloat(), 10f)) * dt
                item.x = (item.x + sin(item.age * 2.4f) * 8f * dt).coerceIn(8f, W - 8f)
            }
        }
        val collected = ArrayList<Item>()
        for (item in items) if (hypot(item.x - px, item.y - py) < 15f) {
            when (item.type) {
                0 -> { if (lives < 5) lives++ else score += 100 }
                1 -> { weaponLevel = min(5, weaponLevel + 1); prefs.edit().putInt("upgrade_weapon", weaponLevel).apply(); score += 60 }
                2 -> rapidTimer = max(rapidTimer, 10f)
                3 -> shieldTimer = max(shieldTimer, 10f)
                4 -> {
                    enemies.toList().forEach { if (!it.dead) kill(it) }
                    bullets.removeAll { !it.mine }; enemyLasers.clear()
                    boom(W / 2f, wh * 0.42f, 110, 150f)
                }
                5 -> { missileLevel = min(3, missileLevel + 1); prefs.edit().putInt("upgrade_missile", missileLevel).apply(); score += 60 }
                6 -> { laserLevel = min(3, laserLevel + 1); prefs.edit().putInt("upgrade_laser", laserLevel).apply(); score += 60 }
                7 -> { droneLevel = min(2, droneLevel + 1); prefs.edit().putInt("upgrade_drone", droneLevel).apply(); score += 80 }
                8 -> magnetTimer = max(magnetTimer, 9f)
                10 -> piercingTimer = max(piercingTimer, 8f)
                else -> activateSquadron()
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
                if (e.dead || e.y < -4f || b.hitEnemies.contains(e)) continue
                if (abs(b.x - e.x) < e.hw + 1.5f && abs(b.y - e.y) < e.hh + 3f) {
                    b.hitEnemies.add(e)
                    if (piercingTimer <= 0f) deadB.add(b)
                    e.hp--; boom(b.x, b.y, 2, 25f)
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
            10 -> 0xFF72F0C0.toInt()
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
            val pulse = (sin(star.phase) + 1f) * 0.5f
            paint.color = if (star.twinkle) 0xFFBDEFFF.toInt() else STAR
            paint.alpha = if (star.twinkle) (28f + pulse * 225f).toInt().coerceIn(20, 255) else 185
            val size = star.sz * if (star.twinkle) (0.65f + pulse * 0.75f) else 1f
            c.drawRect(star.x * s, star.y * s, (star.x + size) * s, (star.y + size) * s, paint)
        }
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = 0.55f * s
        paint.color = 0xFF9FC7E5.toInt()
        for (comet in comets) {
            paint.alpha = (34f * (comet.life / comet.maxLife)).toInt().coerceIn(7, 34)
            c.drawLine(comet.x * s, comet.y * s, (comet.x - comet.vx * 0.13f) * s, (comet.y - comet.length) * s, paint)
        }
        paint.alpha = 255
        paint.style = Paint.Style.FILL
        when (st) {
            St.MENU -> drawMenu(c)
            St.PLAY -> drawGame(c)
            St.PAUSE -> { drawGame(c); drawPause(c) }
            St.OVER -> { drawGame(c); drawOver(c) }
        }
    }

    private fun drawGame(c: Canvas) {
        for (hole in blackHoles) drawBlackHole(c, hole)
        for (e in enemies) {
            val sprite = when (e.type) { 2 -> bossSprites[e.t0.toInt().coerceIn(0, 9)]; 1 -> bE1; 3 -> bE2; 4 -> bE3; else -> bE0 }
            spr(c, sprite, e.x, e.y)
            if (e.type != 2) {
                val bw = max(10f, e.hw * 1.75f)
                val top = e.y - e.hh - 4f
                val frac = (e.hp.toFloat() / e.maxHp.toFloat()).coerceIn(0f, 1f)
                paint.style = Paint.Style.FILL
                paint.color = 0xCC222A35.toInt()
                c.drawRect((e.x - bw / 2f) * s, top * s, (e.x + bw / 2f) * s, (top + 2.2f) * s, paint)
                paint.color = if (frac > 0.55f) 0xFF54DDAA.toInt() else if (frac > 0.25f) 0xFFFFC857.toInt() else 0xFFFF5868.toInt()
                c.drawRect((e.x - bw / 2f) * s, top * s, (e.x - bw / 2f + bw * frac) * s, (top + 2.2f) * s, paint)
            }
        }
        for (item in items) drawItem(c, item)
        for (b in bullets) spr(c, if (b.mine) bPB else bEB, b.x, b.y)
        for (beam in playerBeams) {
            paint.style = Paint.Style.STROKE; paint.strokeWidth = beam.width * s; paint.color = 0xFF54E8FF.toInt()
            c.drawLine(beam.x * s, beam.bottom * s, beam.x * s, 0f, paint)
        }
        paint.style = Paint.Style.FILL
        for (missile in missiles) {
            c.save()
            c.rotate(Math.toDegrees(atan2(missile.vy, missile.vx).toDouble()).toFloat() + 90f, missile.x * s, missile.y * s)
            val rocketPath = Path().apply { moveTo(0f, -7f * s); lineTo(3f * s, 4f * s); lineTo(0f, 2f * s); lineTo(-3f * s, 4f * s); close() }
            paint.color = 0xFFFFA64D.toInt(); c.drawPath(rocketPath, paint); c.restore()
        }
        for (ship in squadShips) {
            val path = Path().apply { moveTo(ship.x * s, (ship.y - 7f) * s); lineTo((ship.x + 5f) * s, (ship.y + 5f) * s); lineTo(ship.x * s, (ship.y + 2f) * s); lineTo((ship.x - 5f) * s, (ship.y + 5f) * s); close() }
            paint.color = 0xFF66E8FF.toInt(); c.drawPath(path, paint)
        }
        for (laser in enemyLasers) {
            paint.style = Paint.Style.STROKE; paint.strokeWidth = (if (laser.warning > 0f) 1.2f else laser.width) * s
            paint.color = if (laser.warning > 0f) 0xFFFFD45C.toInt() else 0xFFFF5868.toInt()
            c.drawLine(laser.x * s, 0f, laser.x * s, sh.toFloat(), paint)
        }
        paint.style = Paint.Style.FILL
        if (shieldTimer > 0f) {
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.4f * s; paint.color = 0xFFFF79D8.toInt()
            c.drawCircle(px * s, py * s, 16f * s, paint)
            paint.clearShadowLayer(); paint.style = Paint.Style.FILL
        }
        if ((st == St.PLAY || st == St.PAUSE) && (invul <= 0f || (time * 12).toInt() % 2 == 0)) spr(c, bPlayer, px, py)
        for (p in parts) {
            val k = 1f - p.life / p.max
            paint.color = if (k < 0.3f) WHT else if (k < 0.6f) YEL else RED
            paint.alpha = (255 * (p.life / p.max)).toInt().coerceIn(0, 255)
            c.drawRect(p.x * s, p.y * s, (p.x + 1.6f) * s, (p.y + 1.6f) * s, paint)
        }
        paint.alpha = 255
        text(c, "SCORE $score", 6f, 14f, 9f, YEL, Paint.Align.LEFT)
        for (i in 0 until lives) spr(c, bMini, W - 12f - i * 12f, 10f)
        text(c, "HP", 7f, 27f, 6f, 0xFF9CEBFF.toInt(), Paint.Align.LEFT)
        paint.style = Paint.Style.FILL
        paint.color = 0xCC25313D.toInt()
        c.drawRoundRect(18f * s, 21f * s, 72f * s, 25f * s, 1.8f * s, 1.8f * s, paint)
        paint.color = if (lives >= 3) 0xFF55DDAA.toInt() else if (lives == 2) 0xFFFFC857.toInt() else 0xFFFF5968.toInt()
        c.drawRoundRect(18f * s, 21f * s, (18f + 54f * lives / 5f) * s, 25f * s, 1.8f * s, 1.8f * s, paint)
        val powerLabel = when {
            piercingTimer > 0f -> "PIERCING " + (piercingTimer.toInt() + 1)
            shieldTimer > 0f -> "SHIELD " + (shieldTimer.toInt() + 1)
            rapidTimer > 0f -> "RAPID " + (rapidTimer.toInt() + 1)
            magnetTimer > 0f -> "MAGNET " + (magnetTimer.toInt() + 1)
            weaponLevel > 1 -> "WEAPON LV " + weaponLevel
            else -> ""
        }
        if (powerLabel.isNotEmpty()) text(c, powerLabel, W / 2, wh - 10f, 7f, 0xFF92E9FF.toInt())
        if (st == St.PLAY) {
            for (i in 0 until droneLevel) {
                val dx = if (i == 0) -17f else 17f
                val wing = Path().apply { moveTo((px + dx) * s, (py - 5f) * s); lineTo((px + dx + 4f) * s, (py + 3f) * s); lineTo((px + dx) * s, (py + 1f) * s); lineTo((px + dx - 4f) * s, (py + 3f) * s); close() }
                paint.color = 0xFF63DDBB.toInt(); c.drawPath(wing, paint)
            }
            drawWeaponControls(c)
            drawPauseControl(c)
        }
        enemies.firstOrNull { it.type == 2 }?.let { bs ->
            text(c, "BOSS " + (bs.t0.toInt() + 1) + "/10", W / 2f, 17f, 7f, WHT)
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

    private fun drawBlackHole(c: Canvas, hole: BlackHole) {
        val cx = hole.x * s
        val cy = hole.y * s
        val r = hole.radius * s
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.1f * s
        paint.color = 0xFF8C63FF.toInt()
        c.drawCircle(cx, cy, r * (0.68f + 0.035f * sin(hole.spin)), paint)
        paint.strokeWidth = 0.7f * s
        paint.color = 0xFF35D9F2.toInt()
        c.drawCircle(cx, cy, r * (0.84f + 0.025f * cos(hole.spin * 1.3f)), paint)
        c.save()
        c.rotate(Math.toDegrees(hole.spin.toDouble()).toFloat(), cx, cy)
        paint.strokeWidth = 1.5f * s
        paint.color = 0xFFFF75D8.toInt()
        c.drawArc(cx - r, cy - r, cx + r, cy + r, 18f, 118f, false, paint)
        c.drawArc(cx - r, cy - r, cx + r, cy + r, 198f, 118f, false, paint)
        c.restore()
        paint.style = Paint.Style.FILL
        paint.color = 0xFF020308.toInt()
        c.drawCircle(cx, cy, r * 0.52f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * s
        paint.color = 0xFFB59BFF.toInt()
        c.drawCircle(cx, cy, r * 0.54f, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawPauseControl(c: Canvas) {
        val left = (W - 31f) * s
        val top = 5f * s
        val right = (W - 7f) * s
        val bottom = 27f * s
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.9f * s
        paint.color = 0xFF9CEBFF.toInt()
        c.drawRoundRect(left, top, right, bottom, 3f * s, 3f * s, paint)
        paint.style = Paint.Style.FILL
        c.drawRoundRect((W - 24f) * s, 10f * s, (W - 21f) * s, 22f * s, 1f * s, 1f * s, paint)
        c.drawRoundRect((W - 17f) * s, 10f * s, (W - 14f) * s, 22f * s, 1f * s, 1f * s, paint)
    }

    private fun drawPause(c: Canvas) {
        paint.style = Paint.Style.FILL
        paint.color = 0xD9101722.toInt()
        c.drawRect(0f, 0f, sw.toFloat(), sh.toFloat(), paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f * s
        paint.color = 0xFF47DFFF.toInt()
        c.drawRoundRect(25f * s, (wh * 0.34f) * s, 195f * s, (wh * 0.69f) * s, 8f * s, 8f * s, paint)
        text(c, "PAUSED", W / 2f, wh * 0.395f, 19f, 0xFF9CEBFF.toInt())
        paint.style = Paint.Style.FILL
        drawButtons(c)
    }

    private fun drawWeaponControls(c: Canvas) {
        fun control(label: String, left: Float, right: Float, ready: Boolean) {
            val top = wh - 32f
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 0.9f * s
            paint.color = if (ready) 0xFF55DFFF.toInt() else 0xFF66717E.toInt()
            c.drawRoundRect(left * s, top * s, right * s, (wh - 7f) * s, 4f * s, 4f * s, paint)
            paint.style = Paint.Style.FILL
            text(c, label, (left + right) / 2f, wh - 16f, 6.5f, if (ready) 0xFF8BEFFF.toInt() else 0xFF86919D.toInt())
        }
        control("MISSILE", W - 94f, W - 53f, missileCooldown <= 0f)
        control("LASER", W - 49f, W - 8f, laserCooldown <= 0f)
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

