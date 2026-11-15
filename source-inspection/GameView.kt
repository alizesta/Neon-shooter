package com.claude.neonshooter

import android.app.Activity
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.graphics.*
import android.content.Intent
import android.net.Uri
import android.media.MediaPlayer
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.Surface
import android.os.Build
import android.view.SurfaceView
import android.util.Log
import kotlin.math.*
import kotlin.random.Random

class GameView(private val act: Activity) : SurfaceView(act), SurfaceHolder.Callback, Runnable {

    private enum class St { MENU, PLAY, PAUSE, OVER, UPGRADE, SHOP, AD_BREAK, ABOUT, SHIPS, SHIP_UPGRADE }
    private class B(var x: Float, var y: Float, var vx: Float, var vy: Float, var mine: Boolean) { val hitEnemies = HashSet<E>() }
    private class E(var x: Float, var y: Float, val type: Int, var hp: Int, val bx: Float, val amp: Float, val t0: Float) {
        var bossSkills: List<String> = emptyList()
        var bossSkillTimer = 2.0f
        val maxHp = hp
        var age = 0f
        var dead = false
        var fire = Random.nextFloat() * 2f + 1f
        var healTimer = 2.2f
        var phase = 1
        var attackWarned = false
        var warning = 0f
        var hitFlash = 0f
        val hw = if (type == 2) 19f else if (type == 3) 11f else if (type == 4) 10f else if (type == 1) 9f else 8f
        val hh = 8f
    }
    private class Pt(var x: Float, var y: Float, val vx: Float, val vy: Float, var life: Float, val max: Float)
    private class Item(var x: Float, var y: Float, val type: Int, val amount: Int = 0) { var age = 0f }
    private class PlayerBeam(val x: Float, val bottom: Float, val width: Float, var life: Float)
    private class Missile(var x: Float, var y: Float, var vx: Float, var vy: Float, val damage: Int, var age: Float = 0f)
    private class EnemyLaser(var x: Float, var warning: Float, var active: Float, val width: Float, val drift: Float, val originY: Float = 40f)
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
    private class Comet(var x: Float, var y: Float, var vx: Float, var vy: Float, val length: Float, var life: Float, val maxLife: Float)
    private class Arc(val x1: Float, val y1: Float, val x2: Float, val y2: Float, var life: Float = 0.24f)
    private class ImpactRing(val x: Float, val y: Float, var life: Float = 0.22f, val maxLife: Float = 0.22f)

    // palette: gray / yellow / red / white (+ orange warning)
    private val BG = 0xFF111318.toInt(); private val STAR = 0xFF454D59.toInt()
    private val YEL = 0xFFFFE08A.toInt(); private val YGL = 0xFFFFFF75.toInt()
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
    private val pendingWaveEnemies = ArrayList<E>()
    private var waveTarget = 1
    private var waveKills = 0
    private val parts = ArrayList<Pt>(); private val stars = ArrayList<Star>()
    private val items = ArrayList<Item>()
    private val playerBeams = ArrayList<PlayerBeam>()
    private val missiles = ArrayList<Missile>()
    private val enemyLasers = ArrayList<EnemyLaser>()
    private val squadShips = ArrayList<SweepShip>()
    private val blackHoles = ArrayList<BlackHole>()
    private val comets = ArrayList<Comet>()
    private val impactRings = ArrayList<ImpactRing>()
    private var screenShake = 0f
    private var shakePower = 0f
    private var damageFlash = 0f
    private var pressedButtonId = ""
    private var buttonPulse = 0f
    private lateinit var bossSprites: Array<Bitmap>
    private var px = 90f; private var py = 270f
    private var lives = 3; private var score = 0; private var wave = 0
    private var invul = 0f; private var fireT = 0f; private var time = 0f
    private var weaponLevel = 1
    private var missileLevel = 0
    private var laserLevel = 0
    private var droneLevel = 0
    private var empRank = 0
    private var empPulseTimer = 0f
    private var ricochetRank = 0
    private var gravityWellRank = 0
    private var overloadRank = 0
    private var nanobotsRank = 0
    private var scannerRank = 0
    private var salvageDroneRank = 0
    private var killStreakRank = 0
    private var ricochetShieldRank = 0
    private var gravityPulseTimer = 0f
    private var gravityActiveTimer = 0f
    private var overloadTimer = 0f
    private var nanobotTimer = 0f
    private var scannerPulseTimer = 0f
    private var scannerTimer = 0f
    private var salvageTimer = 0f
    private var dashRank = 0
    private var shieldRank = 0
    private var hullRank = 0
    private var healthRegenRank = 0
    private var armorRank = 0
    private var criticalRank = 0
    private var overdriveRank = 0
    private var explosiveRank = 0
    private var piercingRank = 0
    private var stasisRank = 0
    private var scavengerRank = 0
    private var multishotRank = 0
    private var phoenixRank = 0
    private val selectedAbilities = linkedSetOf<String>()
    private var extraAbilitiesUnlocked = false
    private var healthRegenTimer = 0f
    private var phoenixUsed = false
    private var shopHandledWave = -1
    private var upgradeHandledWave = -1
    private var legendaryTime = 0f
    private var legendaryCooldown = 0f
    private var weaponTimer = 0f
    private var piercingTimer = 0f
    private var enemySlowTimer = 0f
    private var squadTimer = 0f; private var squadSpawnTimer = 0f
    private var cometSpawnTimer = 2.5f
    private var rapidTimer = 0f; private var shieldTimer = 0f
private var rocketTimer = 0f; private var magnetTimer = 0f
    private var laserCooldown = 0f; private var missileCooldown = 0f
    private var controlTouch = false
    private var waiting = false; private var waveT = 0f; private var bannerT = 0f; private var overT = 0f
    private var kills = 0
    private var credits = prefs.getInt("credits", 0)
    private var bestWave = prefs.getInt("best_wave", 0)
    private var currentShip = prefs.getInt("ship", 0).coerceIn(0, 4)
    private var upgradePoints = prefs.getInt("ship_upgrade_points", 0)
    private var dailyDay = prefs.getLong("daily_day", -1L)
    private var dailyKills = prefs.getInt("daily_kills", 0)
    private var dailyDone = prefs.getBoolean("daily_done", false)
    private var upgradeChoices = emptyList<String>()
    private var upgradesTaken = 0
    private var intermissionHandledWave = -1
    private var rewardedContinueUsed = false
    private var rewardedContinuePending = false
    private var interstitialShownThroughWave = -1
    private var adStatusText = ""
    private var adStatusTimer = 0f
    private var bonusDamage = 0
    private var fireRateBonus = 0f
    private var magnetLevel = 0
    private var shieldLevel = 0
    private var shieldHp = 0
    private var shieldMaxHp = 0
    private var shieldHitTimer = 0f
    private var shieldHitAngle = 0f
    private var reflectiveShield = false
    private var shieldReflectCooldown = 0f
    private var dashCharges = 0
    private var dashCooldown = 0f
    private var chainLevel = 0
    private var chainCooldown = 0f
    private var comboCount = 0
    private var comboTimer = 0f
    private var chainReactionActive = false
    private val chainArcs = ArrayList<Arc>()
    private var eventKind = 0
    private var eventTimer = 0f
    private var meteorTimer = 0f
    private var goldenCrateSpawned = false
    private var lx = 0f; private var ly = 0f

    private lateinit var bPlayer: Bitmap; private lateinit var bMini: Bitmap
    private lateinit var bE0: Bitmap; private lateinit var bE1: Bitmap; private lateinit var bBoss: Bitmap
    private lateinit var bE2: Bitmap; private lateinit var bE3: Bitmap; private lateinit var bBoss2: Bitmap; private lateinit var bBoss3: Bitmap
    private lateinit var bPB: Bitmap; private lateinit var bEB: Bitmap

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val spritePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans-serif", Typeface.BOLD) }

    private var musicPlayer: MediaPlayer? = null
    private var musicPrepared = false

    init { for (i in 1..currentShip) prefs.edit().putBoolean("ship_unlocked_" + i, true).apply(); refreshDaily(); holder.addCallback(this); TapsellAds.preloadAll(); prepareMusic() }

    private fun prepareMusic() {
        if (musicPrepared) return
        musicPrepared = true
        try {
            musicPlayer = MediaPlayer.create(act, R.raw.ambient_theme)?.apply {
                isLooping = true
                setVolume(0.28f, 0.28f)
            }
            syncMusic()
        } catch (e: Exception) {
            Log.e("NeonShooter", "Background music unavailable", e)
        }
    }

    private fun syncMusic() {
        try {
            val player = musicPlayer ?: return
            player.setVolume(if (st == St.PAUSE) 0.14f else 0.28f, if (st == St.PAUSE) 0.14f else 0.28f)
            if (sfx.enabled) {
                if (!player.isPlaying) player.start()
            } else if (player.isPlaying) player.pause()
        } catch (e: Exception) {
            Log.e("NeonShooter", "Background music state error", e)
        }
    }

    // ---------- surface / thread ----------
    override fun surfaceCreated(h: SurfaceHolder) {}
    override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, hh: Int) {
        if (Build.VERSION.SDK_INT >= 30) {
            try { h.surface.setFrameRate(90f, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT) } catch (_: Exception) {}
        }
        synchronized(lock) { if (w != sw || hh != sh || !::bPlayer.isInitialized) setup(w, hh) }
        if (thread == null) { running = true; thread = Thread(this).also { it.start() } }
        syncMusic()
    }
    override fun surfaceDestroyed(h: SurfaceHolder) {
        running = false
        try { thread?.join() } catch (e: InterruptedException) {}
        thread = null
        try { musicPlayer?.pause() } catch (_: Exception) {}
    }

    override fun onDetachedFromWindow() {
        try { musicPlayer?.stop(); musicPlayer?.release() } catch (_: Exception) {}
        musicPlayer = null; musicPrepared = false
        super.onDetachedFromWindow()
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
        val targetFrameNanos = 1_000_000_000L / 90L
        var last = System.nanoTime()
        while (running) {
            val frameStart = System.nanoTime()
            val dt = min(0.05f, (frameStart - last) / 1e9f); last = frameStart
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
            val remaining = targetFrameNanos - (System.nanoTime() - frameStart)
            if (remaining > 0L) {
                try { Thread.sleep(remaining / 1_000_000L, (remaining % 1_000_000L).toInt()) }
                catch (e: InterruptedException) { if (!running) break }
            } else Thread.yield()
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
                        if (y >= wh - 34f && x in 48f..108f && legendaryTier() > 0) {
                            activateLegendary()
                            controlTouch = true
                        } else if (x >= W - 32f && y <= 30f) {
                            st = St.PAUSE
                            syncMusic()
                            controlTouch = true
                        } else if (y >= wh - 34f && x <= 42f) {
                            performDash()
                            controlTouch = true
                        } else if (y >= wh - 34f && x >= W - 96f) {
                            if (x >= W - 52f) fireLaser() else launchMissiles()
                            controlTouch = true
                        }
                    } else if (!(st == St.OVER && overT < 0.7f)) {
                        buttons().firstOrNull { it.r.contains(e.x / s, e.y / s) }?.let { pressedButtonId = it.id; buttonPulse = 0.18f; onBtn(it.id) }
                    }
                }
                MotionEvent.ACTION_MOVE -> if (st == St.PLAY && !controlTouch) {
                    px = (px + (e.x - lx) / s * shipMoveMultiplier()).coerceIn(10f, W - 10f)
                    py = (py + (e.y - ly) / s).coerceIn(30f, wh - 48f)
                    lx = e.x; ly = e.y
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> controlTouch = false
            }
        }
        return true
    }

    private fun refreshDaily() {
        val today = System.currentTimeMillis() / 86_400_000L
        if (dailyDay != today) {
            dailyDay = today
            dailyKills = 0
            dailyDone = false
            prefs.edit().putLong("daily_day", today).putInt("daily_kills", 0).putBoolean("daily_done", false).apply()
        }
    }

    private fun shipName(id: Int = currentShip): String = when (id) {
        1 -> "افعی"
        2 -> "تایتان"
        3 -> "شبح"
        4 -> "ققنوس"
        else -> "مهاجم"
    }

    private fun shipPrice(id: Int): Int = intArrayOf(0, 450, 900, 1550, 2400)[id.coerceIn(0, 4)]
    private fun shipOwned(id: Int): Boolean = id == 0 || prefs.getBoolean("ship_unlocked_" + id, false)
    private fun shipUpgradeRank(stat: String, ship: Int = currentShip): Int = prefs.getInt("ship_upgrade_" + ship + "_" + stat, 0).coerceIn(0, 5)

    private fun buyOrSelectShip(id: Int) {
        if (id !in 0..4) return
        if (!shipOwned(id)) {
            val cost = shipPrice(id)
            if (credits < cost) { adStatusText = "سکه کافی نیست"; adStatusTimer = 1.6f; return }
            credits -= cost
            prefs.edit().putInt("credits", credits).putBoolean("ship_unlocked_" + id, true).apply()
        }
        currentShip = id
        prefs.edit().putInt("ship", currentShip).apply()
        adStatusText = "سفینه " + shipName() + " انتخاب شد"; adStatusTimer = 1.4f
    }

    private fun buyShipUpgrade(stat: String) {
        val rank = shipUpgradeRank(stat)
        if (rank >= 5 || upgradePoints < rank + 1) return
        upgradePoints -= rank + 1
        prefs.edit().putInt("ship_upgrade_points", upgradePoints)
            .putInt("ship_upgrade_" + currentShip + "_" + stat, rank + 1).apply()
    }

    private fun shipMoveMultiplier(): Float = (when (currentShip) {
        1 -> 1.22f
        2 -> 0.86f
        3 -> 1.04f
        4 -> 1.10f
        else -> 1f
    } * (1f + shipUpgradeRank("mobility") * 0.055f)) * (if (legendaryTime > 0f && currentShip == 1) 1.65f else 1f)

    private fun shipFireDelay(): Float = (when (currentShip) {
        1 -> 0.92f
        2 -> 1.12f
        3 -> 0.82f
        4 -> 0.94f
        else -> 1f
    } * (1f - shipUpgradeRank("attack") * 0.045f)).coerceAtLeast(0.68f)

    private fun cycleShip() {
        val highestUnlocked = (bestWave / 10).coerceIn(0, 4)
        currentShip = (currentShip + 1) % (highestUnlocked + 1)
        prefs.edit().putInt("ship", currentShip).apply()
    }

    private fun abilityRank(id: String): Int = when (id) {
        "firepower" -> weaponLevel
        "missile" -> missileLevel
        "laser" -> laserLevel
        "drone" -> droneLevel
        "magnet" -> magnetLevel
        "shield" -> shieldRank
        "dash" -> dashRank
        "chain" -> chainLevel
        "hull" -> hullRank
        "emp" -> empRank
        "ricochet" -> ricochetRank
        "gravity_well" -> gravityWellRank
        "overload" -> overloadRank
        "nanobots" -> nanobotsRank
        "scanner" -> scannerRank
        "salvage_drone" -> salvageDroneRank
        "kill_streak" -> killStreakRank
        "ricochet_shield" -> ricochetShieldRank
        "health_regen" -> healthRegenRank
        "armor" -> armorRank
        "critical" -> criticalRank
        "overdrive" -> overdriveRank
        "explosive" -> explosiveRank
        "piercing" -> piercingRank
        "stasis" -> stasisRank
        "scavenger" -> scavengerRank
        "multishot" -> multishotRank
        "phoenix" -> phoenixRank
        else -> 5
    }

    private fun prepareUpgradeChoices() {
        val all = listOf("firepower", "missile", "laser", "drone", "magnet", "shield", "dash", "chain", "hull", "emp", "ricochet", "gravity_well", "overload", "nanobots", "scanner", "salvage_drone", "kill_streak", "ricochet_shield",
            "health_regen", "armor", "critical", "overdrive", "explosive", "piercing", "stasis", "scavenger", "multishot", "phoenix")
        if (!extraAbilitiesUnlocked && wave > 50 && selectedAbilities.size >= 5 && selectedAbilities.all { abilityRank(it) >= 5 }) {
            extraAbilitiesUnlocked = true
        }
        val eligible = all.filter { id ->
            abilityRank(id) < 5 && (id in selectedAbilities || selectedAbilities.size < 5 || extraAbilitiesUnlocked)
        }
        upgradeChoices = eligible.shuffled().take(3)
        if (upgradeChoices.isEmpty()) { st = St.PLAY; waiting = true; waveT = 0.75f }
    }

    private fun applyUpgrade(id: String) {
        when (id) {
            "firepower" -> { weaponLevel = min(5, weaponLevel + 1); bonusDamage++ ; fireRateBonus = min(0.28f, fireRateBonus + 0.035f) }
            "missile" -> missileLevel = min(5, missileLevel + 1)
            "laser" -> laserLevel = min(5, laserLevel + 1)
            "drone" -> droneLevel = min(5, droneLevel + 1)
            "magnet" -> magnetLevel = min(5, magnetLevel + 1)
            "shield" -> { shieldRank = min(5, shieldRank + 1); shieldLevel = shieldRank; shieldMaxHp = 10 + shieldRank * 6; shieldHp = min(shieldMaxHp, if (shieldHp <= 0) shieldMaxHp else shieldHp + 8); reflectiveShield = shieldHp > 0 }
            "dash" -> { dashRank = min(5, dashRank + 1); dashCharges = min(5, dashCharges + 1) }
            "chain" -> chainLevel = min(5, chainLevel + 1)
            "hull" -> { hullRank = min(5, hullRank + 1); lives = min(10, lives + 1) }
            "emp" -> { empRank = min(5, empRank + 1); empPulseTimer = min(empPulseTimer, 0.5f) }
            "ricochet" -> ricochetRank = min(5, ricochetRank + 1)
            "gravity_well" -> { gravityWellRank = min(5, gravityWellRank + 1); gravityPulseTimer = min(gravityPulseTimer, 0.5f) }
            "overload" -> { overloadRank = min(5, overloadRank + 1); overloadTimer = max(overloadTimer, 2.5f) }
            "nanobots" -> { nanobotsRank = min(5, nanobotsRank + 1); nanobotTimer = min(nanobotTimer, 1f) }
            "scanner" -> { scannerRank = min(5, scannerRank + 1); scannerPulseTimer = min(scannerPulseTimer, 0.5f) }
            "salvage_drone" -> { salvageDroneRank = min(5, salvageDroneRank + 1); salvageTimer = min(salvageTimer, 1f) }
            "kill_streak" -> killStreakRank = min(5, killStreakRank + 1)
            "ricochet_shield" -> ricochetShieldRank = min(5, ricochetShieldRank + 1)
            "health_regen" -> { healthRegenRank = min(5, healthRegenRank + 1); healthRegenTimer = min(healthRegenTimer, 5f) }
            "armor" -> armorRank = min(5, armorRank + 1)
            "critical" -> criticalRank = min(5, criticalRank + 1)
            "overdrive" -> { overdriveRank = min(5, overdriveRank + 1); fireRateBonus = min(0.58f, fireRateBonus + 0.075f) }
            "explosive" -> explosiveRank = min(5, explosiveRank + 1)
            "piercing" -> piercingRank = min(5, piercingRank + 1)
            "stasis" -> stasisRank = min(5, stasisRank + 1)
            "scavenger" -> scavengerRank = min(5, scavengerRank + 1)
            "multishot" -> multishotRank = min(5, multishotRank + 1)
            "phoenix" -> phoenixRank = min(5, phoenixRank + 1)
        }
        val combo = when {
            missileLevel > 0 && droneLevel > 0 -> "پهپاد موشکی"
            laserLevel > 0 && weaponLevel >= 3 -> "لیزر منشوری"
            magnetLevel > 0 && shieldLevel > 0 -> "سپر مغناطیسی"
            else -> ""
        }
        if (combo.isNotEmpty()) bannerT = 2.8f
        sfx.play("power")
    }

    private fun chooseUpgrade(index: Int) {
        if (index !in upgradeChoices.indices) return
        val chosen = upgradeChoices[index]
        selectedAbilities.add(chosen)
        applyUpgrade(chosen)
        upgradeChoices = emptyList()
        st = St.PLAY
        waiting = true
        waveT = 0.75f
    }

    private fun buyShop(id: String) {
        when (id) {
            "shop_repair" -> if (credits >= 180 && lives < 5) { credits -= 180; lives = min(5, lives + 1) }
            "shop_weapon" -> if (credits >= 320) { credits -= 320; weaponLevel = min(5, weaponLevel + 1); bonusDamage++ }
            "shop_shield" -> if (credits >= 260) { credits -= 260; shieldRank = min(5, shieldRank + 1); shieldLevel = shieldRank; shieldMaxHp = 10 + shieldRank * 6; shieldHp = min(shieldMaxHp, shieldHp + 10); reflectiveShield = shieldHp > 0 }
            "shop_dash" -> if (credits >= 220) { credits -= 220; dashCharges = min(4, dashCharges + 1) }
            "shop_continue" -> { st = St.PLAY; waiting = true; waveT = 0.8f }
        }
        prefs.edit().putInt("credits", credits).apply()
    }

    private fun legendaryTier(): Int = maxOf(wave, bestWave) / 10

    private fun legendaryName(): String = when (currentShip) {
        0 -> "فراشتاب"
        1 -> "پس‌سوز"
        2 -> "ایجیس"
        4 -> "ققنوس طلایی"
        else -> "مخفی‌کاری"
    }

    private fun activateLegendary() {
        if (st != St.PLAY || legendaryTier() <= 0 || legendaryCooldown > 0f || legendaryTime > 0f) return
        legendaryTime = 5f + shipUpgradeRank("gold") * 0.7f
        legendaryCooldown = max(11f, 20f - shipUpgradeRank("gold") * 1.4f)
        if (currentShip == 2) invul = max(invul, 5f)
        if (currentShip == 4) {
            lives = min(10, lives + 1)
            invul = max(invul, 3.2f)
            bullets.removeAll { !it.mine }
            enemyLasers.clear()
            for (enemy in enemies.toList()) {
                if (enemy.type == 2) { enemy.hp -= 70 + wave * 3; if (enemy.hp <= 0) kill(enemy) }
                else kill(enemy)
            }
            boom(px, py, 65, 130f)
        }
        bannerT = 1.6f
        sfx.play("power")
    }

    private fun performDash() {
        if (dashCharges <= 0 || dashCooldown > 0f || st != St.PLAY) return
        val direction = if (px < W / 2f) 1f else -1f
        px = (px + direction * 58f).coerceIn(12f, W - 12f)
        dashCharges--
        dashCooldown = 1.15f
        invul = max(invul, 0.62f)
        boom(px, py, 16, 65f)
        sfx.play("power")
    }

    private fun eventName(): String = when (eventKind) {
        1 -> "بارش شهاب‌سنگ"
        2 -> "خیزش سیاه‌چاله"
        3 -> "واکنش زنجیره‌ای"
        4 -> "جعبه طلایی"
        5 -> "گروه شکارچی"
        else -> ""
    }

    private fun onBtn(id: String) {
        when (id) {
            "play" -> startGame()
            "menu" -> { st = St.MENU; syncMusic() }
            "about" -> st = St.ABOUT
            "aboutBack" -> st = St.MENU
            "telegram" -> try { act.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/gateneon"))) } catch (e: Exception) { Log.e("NeonShooter", "Telegram link unavailable", e) }
            "resume" -> { st = St.PLAY; syncMusic() }
            "pauseRestart" -> startGame()
            "ship" -> st = St.SHIPS
            "shipup" -> st = St.SHIP_UPGRADE
            "shipBack" -> st = St.MENU
            "shipUpgradeBack" -> st = St.SHIPS
            "ship_buy_1" -> buyOrSelectShip(1)
            "ship_buy_2" -> buyOrSelectShip(2)
            "ship_buy_3" -> buyOrSelectShip(3)
            "ship_buy_4" -> buyOrSelectShip(4)
            "ship_select_0" -> buyOrSelectShip(0)
            "ship_select_1" -> buyOrSelectShip(1)
            "ship_select_2" -> buyOrSelectShip(2)
            "ship_select_3" -> buyOrSelectShip(3)
            "ship_select_4" -> buyOrSelectShip(4)
            "shipup_attack" -> buyShipUpgrade("attack")
            "shipup_armor" -> buyShipUpgrade("armor")
            "shipup_mobility" -> buyShipUpgrade("mobility")
            "shipup_hull" -> buyShipUpgrade("hull")
            "shipup_gold" -> buyShipUpgrade("gold")
            "upgrade_0" -> chooseUpgrade(0)
            "upgrade_1" -> chooseUpgrade(1)
            "upgrade_2" -> chooseUpgrade(2)
            "shop_weapon", "shop_shield", "shop_dash", "shop_continue" -> buyShop(id)
            "sound" -> { sfx.enabled = !sfx.enabled; syncMusic() }
            "extraLife" -> {
                if (st == St.OVER && !rewardedContinueUsed && !rewardedContinuePending) {
                    rewardedContinuePending = true
                    TapsellAds.showRewarded(act as androidx.fragment.app.FragmentActivity) { rewardConfirmed ->
                        act.runOnUiThread {
                            synchronized(lock) {
                                rewardedContinuePending = false
                                if (rewardConfirmed && st == St.OVER && !rewardedContinueUsed) {
                                    rewardedContinueUsed = true
                                    lives = 1
                                    bullets.removeAll { !it.mine }
                                    enemyLasers.clear()
                                    invul = 3.0f
                                    waiting = true
                                    waveT = 1.1f
                                    overT = 0f
                                    st = St.PLAY
                                    adStatusText = "جان اضافه فعال شد"
                                    adStatusTimer = 1.8f
                                } else if (st == St.OVER) {
                                    adStatusText = "تبلیغ کامل نشد"
                                    adStatusTimer = 1.8f
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun buttons(): List<Btn> {
        val l = ArrayList<Btn>()
        fun add(id: String, label: String, y: Float) { l.add(Btn(id, label, RectF(50f, y, 170f, y + 22f))) }
        when (st) {
            St.MENU -> {
                val y = wh * 0.44f
                add("play", "شروع بازی", y)
                add("sound", if (sfx.enabled) "صدا: روشن" else "صدا: خاموش", y + 22f)
                add("ship", "آشیانه سفینه", y + 44f)
                add("shipup", "ارتقای سفینه", y + 66f)
                add("about", "درباره", y + 88f)
            }
            St.ABOUT -> {
                val y = wh * 0.62f
                add("telegram", "تلگرام", y)
                add("aboutBack", "بازگشت", y + 30f)
            }
            St.PAUSE -> {
                val y = wh * 0.38f
                add("resume", "ادامه", y)
                add("pauseRestart", "شروع مجدد", y + 30f)
                add("menu", "منوی اصلی", y + 60f)
            }
            St.OVER -> {
                if (rewardedContinuePending) {
                    add("extraLife", "در انتظار تبلیغ...", wh * 0.49f)
                } else if (!rewardedContinueUsed) {
                    val y = wh * 0.49f
                    add("extraLife", "جان اضافه | تماشای تبلیغ", y)
                    add("play", "تلاش دوباره", y + 28f)
                    add("menu", "منو", y + 56f)
                } else {
                    val y = wh * 0.58f
                    add("play", "تلاش دوباره", y)
                    add("menu", "منو", y + 30f)
                }
            }
            St.UPGRADE -> {
                val top = wh * 0.62f
                for (i in 0..2) l.add(Btn("upgrade_$i", "انتخاب", RectF(5f + i * 72f, top, 67f + i * 72f, top + 25f)))
            }
            St.SHOP -> {
                val y = wh * 0.46f
                add("shop_weapon", "سلاح | ۳۲۰ سکه", y)
                add("shop_shield", "سپر | ۲۶۰ سکه", y + 28f)
                add("shop_dash", "جهش | ۲۲۰ سکه", y + 56f)
                add("shop_continue", "ادامه", y + 84f)
            }
            St.SHIPS -> {
                val y = wh * 0.38f
                for (i in 0..4) {
                    val label = if (shipOwned(i)) { if (currentShip == i) "فعال: " + shipName(i) else "انتخاب " + shipName(i) } else "خرید " + shipPrice(i) + " سکه • " + shipName(i)
                    l.add(Btn(if (shipOwned(i)) "ship_select_$i" else "ship_buy_$i", label, RectF(12f, y + i * 23f, 208f, y + i * 23f + 20f)))
                }
                add("shipBack", "بازگشت", wh * 0.77f)
            }
            St.SHIP_UPGRADE -> {
                val y = wh * 0.38f
                val stats = listOf("attack" to "قدرت سلاح", "armor" to "زره", "mobility" to "سرعت", "hull" to "جان اولیه", "gold" to "توان طلایی")
                stats.forEachIndexed { i, pair ->
                    val rank = shipUpgradeRank(pair.first)
                    val label = if (rank >= 5) pair.second + " MAX" else pair.second + " " + rank + "/۵ • هزینه " + (rank + 1)
                    l.add(Btn("shipup_" + pair.first, label, RectF(12f, y + i * 23f, 208f, y + i * 23f + 20f)))
                }
                add("shipUpgradeBack", "بازگشت به آشیانه", wh * 0.77f)
            }
            else -> {}
        }
        return l
    }

    // ---------- game logic ----------
    private fun startGame() {
        bullets.clear(); enemies.clear(); pendingWaveEnemies.clear(); parts.clear(); items.clear(); playerBeams.clear(); missiles.clear(); enemyLasers.clear(); squadShips.clear(); blackHoles.clear(); chainArcs.clear(); comets.clear(); impactRings.clear()
        score = 0; lives = 3 + shipUpgradeRank("hull"); wave = 0; kills = 0; invul = 2.2f; fireT = 0f; time = 0f
        weaponLevel = 1; missileLevel = 0; laserLevel = 0; droneLevel = 0; dashRank = 0; shieldRank = if (currentShip == 2) 1 else 0
        bonusDamage = (if (currentShip == 3 || currentShip == 4) 1 else 0) + shipUpgradeRank("attack"); fireRateBonus = 0f; magnetLevel = 0
        shieldLevel = shieldRank; shieldMaxHp = if (shieldRank > 0) 16 else 0; shieldHp = shieldMaxHp; reflectiveShield = shieldHp > 0
        dashCharges = 0; chainLevel = 0; comboCount = 0; comboTimer = 0f
        eventKind = 0; eventTimer = 0f; meteorTimer = 0f; goldenCrateSpawned = false
        pendingWaveEnemies.clear(); waveKills = 0; waveTarget = 1
        shieldReflectCooldown = 0f; dashCooldown = 0f; chainCooldown = 0f
        upgradesTaken = 0; intermissionHandledWave = -1; shopHandledWave = -1; upgradeHandledWave = -1; upgradeChoices = emptyList()
        selectedAbilities.clear(); extraAbilitiesUnlocked = false; hullRank = 0; healthRegenRank = 0; armorRank = 0; criticalRank = 0; overdriveRank = 0
        explosiveRank = 0; piercingRank = 0; stasisRank = 0; scavengerRank = 0; multishotRank = 0; phoenixRank = 0
        healthRegenTimer = 0f; empPulseTimer = 0f; empRank = 0; phoenixUsed = false; legendaryTime = 0f; legendaryCooldown = 0f
        ricochetRank = 0; gravityWellRank = 0; overloadRank = 0; nanobotsRank = 0; scannerRank = 0; salvageDroneRank = 0; killStreakRank = 0; ricochetShieldRank = 0
        gravityPulseTimer = 0f; gravityActiveTimer = 0f; overloadTimer = 0f; nanobotTimer = 0f; scannerPulseTimer = 0f; scannerTimer = 0f; salvageTimer = 0f
        weaponLevel = 1; missileLevel = 0; laserLevel = 0; droneLevel = 0
        prefs.edit().remove("upgrade_weapon").remove("upgrade_missile").remove("upgrade_laser").remove("upgrade_drone").apply()
        weaponTimer = 0f; rapidTimer = 0f; shieldTimer = 0f; magnetTimer = 0f
        laserCooldown = 3.5f; missileCooldown = 6f
        piercingTimer = 0f; enemySlowTimer = 0f; squadTimer = 0f; squadSpawnTimer = 0f
        px = W / 2; py = wh - 45f; waiting = true; waveT = 1.4f
        rewardedContinueUsed = false
        rewardedContinuePending = false
        interstitialShownThroughWave = -1
        adStatusText = ""; adStatusTimer = 0f
        st = St.PLAY
        TapsellAds.preloadAll()
        sfx.play("start")
    }


    private fun activateRandomEvent() {
        eventKind = Random.nextInt(1, 6)
        eventTimer = 9f
        when (eventKind) {
            1 -> meteorTimer = 0.25f
            2 -> {
                val x = Random.nextFloat() * (W - 76f) + 38f
                blackHoles.add(BlackHole(x, -20f, 54f, 88000f, if (Random.nextBoolean()) 12f else -12f))
            }
            3 -> chainReactionActive = false
            4 -> {
                if (!goldenCrateSpawned) {
                    items.add(Item(W / 2f, -12f, 12))
                    goldenCrateSpawned = true
                }
            }
            5 -> repeat(3 + wave / 8) {
                val x = Random.nextFloat() * (W - 24f) + 12f
                enemies.add(E(x, -18f - it * 18f, 6, 4 + wave / 8, x, 12f, Random.nextFloat() * 6f))
            }
        }
        bannerT = 2.8f
    }

    private fun chooseBossSkills(): List<String> {
        val all = listOf("firepower", "missile", "laser", "drone", "magnet", "shield", "dash", "chain", "hull", "emp", "ricochet", "gravity_well", "overload", "nanobots", "scanner", "salvage_drone", "kill_streak", "ricochet_shield", "health_regen", "armor", "critical", "overdrive", "explosive", "piercing", "stasis", "scavenger", "multishot", "phoenix")
        val owned = all.filter { abilityRank(it) > 0 || it in selectedAbilities }.distinct()
        return owned.shuffled().take(2)
    }

    private fun spawnWave() {
        wave++
        if (wave > bestWave) { bestWave = wave; prefs.edit().putInt("best_wave", bestWave).apply() }
        eventKind = 0; eventTimer = 0f; meteorTimer = 0f; goldenCrateSpawned = false
        if (wave >= 4 && (wave % 4 == 0 || Random.nextFloat() < 0.24f)) activateRandomEvent()
        if (wave >= 3 && wave % 3 == 0 && blackHoles.size < 2) {
            val side = if (Random.nextBoolean()) 1f else -1f
            blackHoles.add(BlackHole(Random.nextFloat() * (W - 76f) + 38f, -18f, 48f + Random.nextFloat() * 12f, 72000f, side * (5f + Random.nextFloat() * 8f)))
        }
        bannerT = 2.0f
        if (wave % 5 == 0) {
            waveTarget = enemies.count { !it.dead } + 1 + if (eventKind == 5) 3 else 0
            val bossBaseHp = 300 + wave * 44
            val bossHp = if (wave == 5) (bossBaseHp * 0.70f).roundToInt() else bossBaseHp
            enemies.add(E(W / 2, -20f, 2, bossHp, W / 2, 0f, ((wave / 5 - 1).coerceIn(0, 9)).toFloat()).also { it.fire = 1.35f; it.bossSkills = chooseBossSkills() })
            if (eventKind == 5) repeat(3) { val x = 25f + it * (W - 50f) / 2f; enemies.add(E(x, -42f - it * 20f, 6, 6 + wave / 8, x, 18f, it.toFloat())) }
            return
        }
        val n = min(16 + wave, 30)
        waveTarget = n + enemies.count { !it.dead }
        fun mk(x: Float, y: Float, amp: Float, t0: Float) {
            val strong = wave >= 3 && Random.nextFloat() < 0.38f
            val roll = Random.nextFloat()
            val type = when { wave >= 10 && roll < 0.13f -> 6; wave >= 8 && roll < 0.21f -> 5; wave >= 6 && roll < 0.32f -> 4; wave >= 4 && roll < 0.48f -> 3; strong -> 1; else -> 0 }
            val baseHp = when (type) { 1 -> 5 + wave / 8; 3 -> 4 + wave / 10; 4 -> 3 + wave / 12; 5 -> 5 + wave / 10; 6 -> 4 + wave / 10; else -> 2 + wave / 12 }
            val difficulty = if (wave >= 10) 1.25f + min(wave - 10, 25) * 0.018f else 1f
            val hp = ceil(baseHp * difficulty).toInt()
            if (enemies.size + pendingWaveEnemies.size < waveTarget) {
                pendingWaveEnemies.add(E(x, y, type, hp, x, amp, t0))
            }
        }
        when (wave % 4) {
            0 -> for (i in 0 until n) { val col = i % 8; val row = i / 8; mk((col + 0.5f) * W / 8f, -10f - row * 26f, 0f, 0f) }
            1 -> for (i in 0 until n) mk(W / 2, -10f - i * 24f, 55f, i * 0.6f)
            2 -> for (i in 0 until n) {
                val side = if (i % 2 == 0) 1 else -1; val k = (i + 1) / 2
                mk(W / 2 + side * k * 20f, -10f - k * 14f, 0f, 0f)
            }
            else -> for (i in 0 until n) {
                val lane = i % 2
                val row = i / 2
                val x = if (lane == 0) 18f + row * 17f else W - 18f - row * 17f
                mk(x.coerceIn(10f, W - 10f), -12f - row * 22f, if (wave >= 10) 34f else 22f, row * 0.42f)
            }
        }
        while (enemies.size < 7 && pendingWaveEnemies.isNotEmpty()) enemies.add(pendingWaveEnemies.removeAt(0))
    }

    private fun boom(x: Float, y: Float, n: Int, sp: Float = 60f) {
        repeat(n) {
            val a = Random.nextFloat() * 6.283f; val v = Random.nextFloat() * sp + 10f
            parts.add(Pt(x, y, cos(a) * v, sin(a) * v, 0.6f, 0.6f))
        }
    }

    private fun kill(e: E) {
        e.dead = true
        if (killStreakRank > 0) score += min(500, comboCount * killStreakRank * 3)
        screenShake = max(screenShake, if (e.type == 2) 0.18f else 0.045f)
        shakePower = max(shakePower, if (e.type == 2) 2.8f else 0.8f)
        impactRings.add(ImpactRing(e.x, e.y, if (e.type == 2) 0.38f else 0.18f, if (e.type == 2) 0.38f else 0.18f))
        waveKills = min(waveTarget, waveKills + 1)
        val basePoints = when (e.type) { 2 -> 500; 1 -> 30; 5 -> 45; 6 -> 55; else -> 10 }
        comboCount = if (comboTimer > 0f) comboCount + 1 else 1
        comboTimer = 3.2f
        val multiplier = (1 + comboCount / 5).coerceIn(1, 5)
        score += basePoints * multiplier
        refreshDaily()
        if (!dailyDone) {
            dailyKills++
            if (dailyKills >= 25) {
                dailyDone = true
                credits += 500
                prefs.edit().putInt("credits", credits).putBoolean("daily_done", true).apply()
            }
            prefs.edit().putInt("daily_kills", dailyKills).apply()
        }
        boom(e.x, e.y, if (e.type == 2) 70 else 16, if (e.type == 2) 90f else 55f)
        sfx.play(if (e.type == 2) "bigboom" else "boom")
        kills++
        if (e.type == 2) {
            val earned = if (wave > 0 && wave % 25 == 0) 4 else 2
            upgradePoints += earned
            prefs.edit().putInt("ship_upgrade_points", upgradePoints).apply()
            adStatusText = "پاداش باس: $earned امتیاز ارتقا"
            adStatusTimer = 2.0f
            repeat(5) { i -> items.add(Item(e.x + (i - 2) * 5.5f, e.y + abs(i - 2) * 2f, 13, 20 + scavengerRank * 4)) }
        } else if (Random.nextFloat() < 0.48f) {
            items.add(Item(e.x, e.y, 13, 5 + scavengerRank * 2))
        }
        val dropChance = if (e.type == 2) 0.55f else 0.08f
        if (Random.nextFloat() < dropChance) {
            val roll = Random.nextFloat()
            val kind = when {
                lives < 10 && roll < 0.28f -> 0
                roll < 0.25f -> 1
                roll < 0.39f -> 2
                roll < 0.51f -> 3
                roll < 0.54f -> 4
                roll < 0.74f -> 5
                roll < 0.83f -> 6
                roll < 0.90f -> 7
                roll < 0.96f -> 8
                roll < 0.985f -> 9
                roll < 0.997f -> 10
                else -> 11
            }
            items.add(Item(e.x, e.y, kind))
        }
        if (eventKind == 3 && !chainReactionActive) {
            chainReactionActive = true
            val nearby = enemies.filter { it !== e && !it.dead && hypot(it.x - e.x, it.y - e.y) < 42f }
            for (target in nearby) kill(target)
            chainReactionActive = false
        }
    }

    private fun hurt() {
        if (invul > 0f || legendaryTime > 0f && currentShip == 3) return
        val permanentArmor = shipUpgradeRank("armor")
        if ((armorRank * 0.075f + permanentArmor * 0.055f).coerceAtMost(0.48f) > Random.nextFloat()) { invul = 0.55f; boom(px, py, 8, 34f); return }
        if (ricochetShieldRank > 0 && Random.nextFloat() < ricochetShieldRank * 0.12f) {
            invul = 0.72f
            val nearest = enemies.filter { !it.dead }.minByOrNull { hypot(it.x - px, it.y - py) }
            if (nearest != null) {
                nearest.hp -= 2 + ricochetShieldRank
                nearest.hitFlash = 0.24f
                if (nearest.hp <= 0) kill(nearest)
                impactRings.add(ImpactRing(nearest.x, nearest.y, 0.3f, 0.3f))
            }
            impactRings.add(ImpactRing(px, py, 0.32f, 0.32f))
            sfx.play("power")
            return
        }
        if (shieldHp > 0) { shieldHp--; reflectiveShield = shieldHp > 0; invul = 0.42f; boom(px, py, 12, 42f); sfx.play("power"); return }
        if (shieldTimer > 0f) {
            shieldTimer = 0f; invul = 1.2f
            boom(px, py, 18, 58f); sfx.play("boom")
            return
        }
        lives = (lives - 1).coerceAtLeast(0)
        invul = 2.4f
        screenShake = 0.26f; shakePower = 4.8f; damageFlash = 0.22f
        impactRings.add(ImpactRing(px, py, 0.32f, 0.32f))
        boom(px, py, 24, 70f)
        sfx.play("hurt")
        if (lives == 0 && phoenixRank > 0 && !phoenixUsed) {
            phoenixUsed = true
            lives = min(5, 1 + phoenixRank)
            invul = 3.2f
            boom(px, py, 46, 115f)
            bannerT = 2.2f
            sfx.play("power")
        }
        if (lives == 0) {
            st = St.OVER; overT = 0f; comboCount = 0; comboTimer = 0f
            sfx.play("over")
            if (score > best) { best = score; prefs.edit().putInt("best", best).apply() }
        }
    }

    private fun shoot() {
        val count = min(1 + (weaponLevel - 1), 5)
        for (i in 0 until count) {
            val offset = i - (count - 1) / 2
            bullets.add(B(px + offset * 3.6f, py - 9f, offset * 13f, -178f, true))
        }
        repeat(multishotRank) { rank ->
            val spread = 12f + rank * 2.2f
            bullets.add(B(px - spread, py - 7f, -18f - rank * 2f, -170f, true))
            bullets.add(B(px + spread, py - 7f, 18f + rank * 2f, -170f, true))
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
        if (st != St.PLAY || laserLevel <= 0 || laserCooldown > 0f) return
        val width = 5f + laserLevel * 2.2f
        val offsets = if (laserLevel > 0 && weaponLevel >= 3) floatArrayOf(-12f, 0f, 12f) else floatArrayOf(0f)
        val damage = 2 + laserLevel * 3 + bonusDamage
        for (offset in offsets) {
            val beamX = (px + offset).coerceIn(5f, W - 5f)
            playerBeams.add(PlayerBeam(beamX, py, width, 0.52f))
            for (enemy in enemies) {
                if (!enemy.dead && enemy.y < py && abs(enemy.x - beamX) < width + enemy.hw * 0.35f) {
                    enemy.hp -= damage
                    boom(enemy.x, enemy.y, 5, 38f)
                    if (enemy.hp <= 0) kill(enemy)
                }
            }
        }
        laserCooldown = max(2.0f, (5.5f - laserLevel * 0.75f) * shipFireDelay())
        sfx.play("laser")
    }

    private fun launchMissiles() {
        if (st != St.PLAY || missileCooldown > 0f) return
        val count = 2 + missileLevel + if (missileLevel > 0 && droneLevel > 0) 2 else 0
        for (i in 0 until count) {
            val offset = (i - (count - 1) / 2) * 7f
            missiles.add(Missile((px + offset).coerceIn(5f, W - 5f), py - 8f, offset * 0.8f, -115f, 2 + missileLevel * 2 + bonusDamage))
        }
        missileCooldown = max(1.8f, 5.2f - missileLevel * 0.65f)
        sfx.play("missile")
    }

    private fun spawnSupportShips() { for (offset in floatArrayOf(-42f, -14f, 14f, 42f)) squadShips.add(SweepShip((px + offset).coerceIn(8f, W - 8f), wh + 14f)) }
    private fun activateSquadron() { squadTimer = 12f; squadSpawnTimer = 0f; bullets.removeAll { !it.mine }; sfx.play("power") }

    private fun update(frameDt: Float) {
        val dt = frameDt * 0.85f
        for (star in stars) {
            star.phase += dt * (2.4f + star.sp * 0.16f)
            star.y += (3.5f + star.sp * 0.28f) * dt
            for (hole in blackHoles) {
                val dx = hole.x - star.x; val dy = hole.y - star.y; val d2 = dx * dx + dy * dy
                if (d2 < hole.radius * hole.radius * 5.0f && d2 > 16f) {
                    val d = sqrt(d2)
                    val pull = min(75f, hole.radius * hole.radius * 0.10f / (d2 + 35f)) * dt * 60f
                    star.x = (star.x + dx / d * pull).coerceIn(-4f, W + 4f)
                    star.y += dy / d * pull
                }
            }
            if (star.y > wh) star.y = -1f
            if (star.y > wh + 2f) {
                star.y = -2f
                star.x = Random.nextFloat() * W
            }
        }
        cometSpawnTimer -= dt
        if (cometSpawnTimer <= 0f) {
            val speed = 30f + Random.nextFloat() * 18f
            val edge = Random.nextInt(4)
            val startX: Float
            val startY: Float
            val targetX: Float
            val targetY: Float
            when (edge) {
                0 -> { startX = Random.nextFloat() * W; startY = -10f; targetX = Random.nextFloat() * W; targetY = wh + 10f }
                1 -> { startX = Random.nextFloat() * W; startY = wh + 10f; targetX = Random.nextFloat() * W; targetY = -10f }
                2 -> { startX = -10f; startY = Random.nextFloat() * wh; targetX = W + 10f; targetY = Random.nextFloat() * wh }
                else -> { startX = W + 10f; startY = Random.nextFloat() * wh; targetX = -10f; targetY = Random.nextFloat() * wh }
            }
            val dx = targetX - startX
            val dy = targetY - startY
            val distance = max(1f, hypot(dx, dy))
            val vx = dx / distance * speed
            val vy = dy / distance * speed
            val life = (distance / speed + 0.8f).coerceIn(3.5f, 9f)
            comets.add(Comet(startX, startY, vx, vy, 7f + Random.nextFloat() * 7f, life, life))
            cometSpawnTimer = 4f + Random.nextFloat() * 7f
        }
        for (comet in comets) {
            comet.x += comet.vx * dt
            comet.y += comet.vy * dt
            comet.life -= dt
        }
        comets.removeAll { it.life <= 0f || it.x < -22f || it.x > W + 22f || it.y < -22f || it.y > wh + 22f }
        for (p in parts) { p.x += p.vx * dt; p.y += p.vy * dt; p.life -= dt }
        parts.removeAll { it.life <= 0f }
        for (ring in impactRings) ring.life -= dt
        shieldHitTimer = max(0f, shieldHitTimer - dt)
        impactRings.removeAll { it.life <= 0f }
        screenShake = max(0f, screenShake - dt); damageFlash = max(0f, damageFlash - dt); buttonPulse = max(0f, buttonPulse - dt)
        for (enemy in enemies) enemy.hitFlash = max(0f, enemy.hitFlash - dt)
        if (st == St.OVER) overT += dt
        adStatusTimer = max(0f, adStatusTimer - dt)
        if (st != St.PLAY) return

        if (st != St.PLAY) return

        time += dt; invul -= dt; fireT -= dt; bannerT -= dt
        if (healthRegenRank > 0) {
            healthRegenTimer -= dt
            if (healthRegenTimer <= 0f) {
                if (lives < 10) { lives++; bannerT = max(bannerT, 0.9f) }
                healthRegenTimer = max(7f, 34f - healthRegenRank * 5f)
            }
        }
        legendaryTime = max(0f, legendaryTime - dt)
        legendaryCooldown = max(0f, legendaryCooldown - dt)
        if (eventTimer > 0f) {
            eventTimer = max(0f, eventTimer - dt)
            if (eventKind == 1) {
                meteorTimer -= dt
                if (meteorTimer <= 0f) {
                    val x = Random.nextFloat() * (W - 18f) + 9f
                    enemyLasers.add(EnemyLaser(x, 0.72f, 0.34f, 15f, (Random.nextFloat() - 0.5f) * 18f, 0f))
                    meteorTimer = 0.62f
                }
            }
            if (eventTimer <= 0f) eventKind = 0
        }
        weaponTimer = max(0f, weaponTimer - dt)
        rapidTimer = max(0f, rapidTimer - dt)
        shieldTimer = max(0f, shieldTimer - dt)
        magnetTimer = max(0f, magnetTimer - dt)
        laserCooldown = max(0f, laserCooldown - dt)
        missileCooldown = max(0f, missileCooldown - dt)
        piercingTimer = max(0f, piercingTimer - dt)
        enemySlowTimer = max(0f, enemySlowTimer - dt)
        overloadTimer = max(0f, overloadTimer - dt)
        gravityActiveTimer = max(0f, gravityActiveTimer - dt)
        scannerTimer = max(0f, scannerTimer - dt)
        if (gravityWellRank > 0) {
            gravityPulseTimer -= dt
            if (gravityPulseTimer <= 0f) {
                gravityActiveTimer = 1.65f
                gravityPulseTimer = max(5.0f, 11.0f - gravityWellRank * 1.15f)
                impactRings.add(ImpactRing(px, py, 0.42f, 0.42f))
                sfx.play("power")
            }
            if (gravityActiveTimer > 0f) {
                for (target in enemies) if (!target.dead) {
                    val dx = px - target.x
                    val dy = py - target.y
                    val distance = hypot(dx, dy).coerceAtLeast(1f)
                    val pull = (18f + gravityWellRank * 8f) * dt
                    target.x += dx / distance * pull
                    target.y += dy / distance * pull
                    if (gravityActiveTimer > 1.35f) {
                        target.hp -= max(1, gravityWellRank / 2)
                        target.hitFlash = 0.12f
                        if (target.hp <= 0) kill(target)
                    }
                }
            }
        }
        if (nanobotsRank > 0) {
            nanobotTimer -= dt
            if (nanobotTimer <= 0f) {
                if (lives < 10) lives = min(10, lives + 1)
                if (shieldMaxHp > 0) shieldHp = min(shieldMaxHp, shieldHp + 1)
                nanobotTimer = max(4.5f, 18f - nanobotsRank * 2.5f)
                impactRings.add(ImpactRing(px, py, 0.25f, 0.25f))
            }
        }
        if (scannerRank > 0) {
            scannerPulseTimer -= dt
            if (scannerPulseTimer <= 0f) {
                scannerTimer = 2.2f
                scannerPulseTimer = max(4.5f, 10f - scannerRank * 1.1f)
                enemies.filter { !it.dead }.minByOrNull { hypot(it.x - px, it.y - py) }?.let {
                    it.hitFlash = 0.6f
                    impactRings.add(ImpactRing(it.x, it.y, 0.3f, 0.3f))
                }
            }
        }
        if (salvageDroneRank > 0) {
            salvageTimer -= dt
            if (salvageTimer <= 0f) {
                credits += salvageDroneRank * 2
                salvageTimer = max(3f, 12f - salvageDroneRank * 1.6f)
            }
        }
        val enemyDt = dt * when { enemySlowTimer > 0f -> 0.1f; stasisRank > 0 -> 1f - stasisRank * 0.08f; else -> 1f }
        if (empRank > 0) {
            empPulseTimer -= dt
            if (empPulseTimer <= 0f) {
                enemies.toList().filter { !it.dead }.forEach { target ->
                    target.hp -= empRank
                    target.hitFlash = 0.16f
                    boom(target.x, target.y, 5 + empRank * 2, 34f + empRank * 8f)
                    if (target.hp <= 0) kill(target)
                }
                empPulseTimer = max(3.2f, 8.2f - empRank * 1.0f)
                impactRings.add(ImpactRing(px, py, 0.32f, 0.32f))
                sfx.play("power")
            }
        }
        squadTimer = max(0f, squadTimer - dt)
        squadSpawnTimer = max(0f, squadSpawnTimer - dt)
        shieldReflectCooldown = max(0f, shieldReflectCooldown - dt); dashCooldown = max(0f, dashCooldown - dt); chainCooldown = max(0f, chainCooldown - dt)
        comboTimer = max(0f, comboTimer - dt); if (comboTimer <= 0f) comboCount = 0
        for (arc in chainArcs) arc.life -= dt
        chainArcs.removeAll { it.life <= 0f }
        if (squadTimer > 0f && squadSpawnTimer <= 0f) { spawnSupportShips(); squadSpawnTimer = 2.4f }
        if (laserLevel > 0 && laserCooldown <= 0f) fireLaser()
        if (missileCooldown <= 0f) launchMissiles()
        for (beam in playerBeams) beam.life -= dt
        playerBeams.removeAll { it.life <= 0f }
        if (fireT <= 0f) { fireT = (if (rapidTimer > 0f) 0.075f else 0.19f) * shipFireDelay() * (1f - fireRateBonus) * (if (legendaryTime > 0f && currentShip == 0) 0.52f else 1f); shoot() }

        // waves
        while (enemies.size < 7 && pendingWaveEnemies.isNotEmpty()) enemies.add(pendingWaveEnemies.removeAt(0))
        if (enemies.isEmpty() && pendingWaveEnemies.isEmpty()) {
            if (!waiting) { waiting = true; waveT = 1.6f }
            else {
                waveT -= dt
                if (waveT <= 0f) {
                    if (wave > 0 && wave % 5 == 0 && interstitialShownThroughWave < wave) {
                        interstitialShownThroughWave = wave
                        st = St.AD_BREAK
                        act.runOnUiThread {
                            TapsellAds.showInterstitial(act as androidx.fragment.app.FragmentActivity) {
                                synchronized(lock) {
                                    if (st == St.AD_BREAK) { st = St.PLAY; bannerT = max(bannerT, 0.55f) }
                                }
                            }
                        }
                        return
                    }
                    if (wave > 0 && wave % 5 == 0 && shopHandledWave != wave) { shopHandledWave = wave; st = St.SHOP; waiting = true; waveT = 0.8f; return }
                    if (wave > 0 && upgradeHandledWave != wave) { upgradeHandledWave = wave; prepareUpgradeChoices(); st = if (upgradeChoices.isNotEmpty()) St.UPGRADE else St.PLAY; waiting = true; waveT = 0.8f; return }
                    spawnWave(); waiting = false
                }
            }
        }

        // enemies
        val spd = if (wave >= 10) min(38f + (wave - 10) * 0.35f, 50f) else min(18f + wave * 1.15f, 38f)
        val summonedEnemies = ArrayList<E>()
        for (e in enemies) {
            e.age += enemyDt
            e.warning = max(0f, e.warning - enemyDt)
            if (e.type == 2) {
                e.bossSkillTimer -= enemyDt
                if (e.bossSkillTimer <= 0f && e.bossSkills.any { it in setOf("health_regen", "nanobots", "phoenix", "hull") }) {
                    e.hp = min(e.maxHp, e.hp + max(2, e.maxHp / 55))
                    e.bossSkillTimer = 2.2f
                    boom(e.x, e.y, 6, 24f)
                }
                if (e.bossSkills.any { it == "magnet" || it == "gravity_well" }) {
                    val dx = e.x - px; val dy = e.y - py; val d = max(1f, hypot(dx, dy))
                    if (d < 150f) { px = (px + dx / d * 10f * enemyDt).coerceIn(10f, W - 10f); py = (py + dy / d * 6f * enemyDt).coerceIn(30f, wh - 48f) }
                }
                val newPhase = when { e.hp <= e.maxHp / 3 -> 3; e.hp <= (e.maxHp * 2) / 3 -> 2; else -> 1 }
                if (newPhase > e.phase) { e.phase = newPhase; e.fire = 0.35f; boom(e.x, e.y, 28, 80f) }
            }
            if (e.type == 2) {
                val bossId = e.t0.toInt().coerceIn(0, 9)
                val span = W / 2f - e.hw - 8f
                if (e.y < 42f) e.y += 32f * enemyDt
                when (bossId) {
                    0 -> e.x = W / 2f + sin(e.age * 0.62f) * span
                    1 -> { e.x = W / 2f + sin(e.age * 1.55f) * span; e.y = 40f + sin(e.age * 1.2f) * 8f }
                    2 -> { e.x += (px - e.x).coerceIn(-55f * enemyDt, 55f * enemyDt); e.y = 38f + sin(e.age * 0.8f) * 12f }
                    3 -> e.x = W / 2f + sin(e.age * 0.42f) * span
                    4 -> { e.x = W / 2f + sin(e.age * 2.15f) * span; e.y = 38f + abs(sin(e.age * 0.72f)) * 16f }
                    5 -> { e.x = W / 2f + sin(e.age * 0.86f) * span; e.y = 36f + sin(e.age * 1.7f) * 9f }
                    6 -> e.x = W / 2f + sin(e.age * 0.31f) * span
                    7 -> { e.x = W / 2f + sin(e.age * 1.08f) * span; e.y = 40f + abs(sin(e.age * 0.52f)) * 18f }
                    8 -> e.x = W / 2f + sin(e.age * 1.82f) * span
                    else -> { e.x = W / 2f + sin(e.age * 0.94f) * span; e.y = 37f + sin(e.age * 1.35f) * 12f }
                }
            } else {
                e.y += spd * enemyDt
                if (e.type == 1) {
                    val targetX = (px + sin(e.age * 1.25f + e.t0) * 13f).coerceIn(12f, W - 12f)
                    val shift = 28f * enemyDt
                    e.x += (targetX - e.x).coerceIn(-shift, shift)
                } else if (e.type == 3) {
                    e.x = (e.bx + sin(e.age * 4.2f + e.t0) * 34f).coerceIn(10f, W - 10f)
                    e.y += spd * 1.18f * enemyDt
                } else if (e.type == 4) {
                    e.x = (e.bx + sin(e.age * 0.9f + e.t0) * 18f).coerceIn(12f, W - 12f)
                } else if (e.type == 5) {
                    e.y -= spd * 0.38f * enemyDt
                    e.x = (e.bx + sin(e.age * 0.8f + e.t0) * 12f).coerceIn(12f, W - 12f)
                } else if (e.type == 6) {
                    e.y += spd * 0.35f * enemyDt
                    val targetX = (px + sin(e.age * 2.3f + e.t0) * 9f).coerceIn(10f, W - 10f)
                    val shift = 54f * enemyDt
                    e.x += (targetX - e.x).coerceIn(-shift, shift)
                } else {
                    val sway = max(e.amp, 10f + min(wave.toFloat(), 10f))
                    e.x = (e.bx + sin(e.age * 1.7f + e.t0) * sway).coerceIn(10f, W - 10f)
                }
                if (wave >= 10 && e.type != 5) {
                    val style = (e.type + wave + e.t0.toInt()).mod(3)
                    val targetX = (px + (px - e.bx) * 0.12f).coerceIn(10f, W - 10f)
                    when (style) {
                        0 -> e.x += (targetX - e.x).coerceIn(-42f * enemyDt, 42f * enemyDt)
                        1 -> e.x = (e.x + sin(e.age * 2.9f + e.t0) * (12f + min(wave - 10, 18) * 0.45f) * enemyDt).coerceIn(10f, W - 10f)
                        else -> if (e.y > 18f) e.x += (px + sin(e.age * 2.1f + e.t0) * 20f - e.x).coerceIn(-58f * enemyDt, 58f * enemyDt)
                    }
                }
            }
            if (e.type != 2) for (hole in blackHoles) {
                val dx = hole.x - e.x; val dy = hole.y - e.y; val d2 = dx * dx + dy * dy
                if (d2 < hole.radius * hole.radius * 4f && d2 > 20f) {
                    val d = sqrt(d2); val pull = min(42f, hole.radius * hole.radius * 0.06f / (d2 + 50f)) * enemyDt * 60f
                    e.x = (e.x + dx / d * pull).coerceIn(5f, W - 5f)
                    e.y += dy / d * pull
                }
            }
            e.fire -= enemyDt
            if (e.fire <= 0f) {
                if (e.type == 2 && !e.attackWarned) { e.attackWarned = true; e.warning = 0.68f; e.fire = 0.68f; continue }
                if (e.type == 2) { e.attackWarned = false; e.warning = 0f } 
                if (e.type == 2) {
                    val bossId = e.t0.toInt().coerceIn(0, 9)
                    val enraged = e.hp * 2 <= e.maxHp
                    e.fire = max(0.38f, (if (enraged) 0.72f else 1.12f) - wave * 0.006f) * (if (e.bossSkills.any { it in setOf("overdrive", "firepower", "missile", "laser", "multishot") }) 0.72f else 1f)
                    val base = atan2((py - e.y).toDouble(), (px - e.x).toDouble()).toFloat()
                    val speed = (74f + min(wave.toFloat(), 50f) * 1.1f) * (1f + (e.phase - 1) * 0.2f)
                    when (bossId) {
                        0 -> for (i in -2..2) { val a = base + i * 0.16f; bullets.add(B(e.x, e.y + 8f, cos(a) * speed, sin(a) * speed, false)) }
                        1 -> enemyLasers.add(EnemyLaser(e.x, 0.62f, 0.72f, 7f, if (sin(e.age) >= 0f) 24f else -24f, e.y))
                        2 -> {
                            for (side in -1..1 step 2) summonedEnemies.add(E((e.x + side * 16f).coerceIn(12f, W - 12f), e.y + 8f, 3, 2 + wave / 16, e.x + side * 16f, 18f, e.age + side))
                            for (i in -1..1) { val a = base + i * 0.22f; bullets.add(B(e.x, e.y + 8f, cos(a) * speed, sin(a) * speed, false)) }
                        }
                        3 -> for (i in 0 until 18) { val a = i * (2f * PI.toFloat() / 18f) + e.age * 0.18f; bullets.add(B(e.x, e.y, cos(a) * speed * 0.82f, sin(a) * speed * 0.82f, false)) }
                        4 -> for (i in 0..2) { val a = e.age * 1.7f + i * (2f * PI.toFloat() / 3f); bullets.add(B(e.x, e.y, cos(a) * speed, sin(a) * speed, false)); bullets.add(B(e.x, e.y, cos(a + PI.toFloat()) * speed, sin(a + PI.toFloat()) * speed, false)) }
                        5 -> repeat(7) { val x = Random.nextFloat() * W; bullets.add(B(x, e.y + 8f, (px - x) * 0.08f, speed * 0.9f, false)) }
                        6 -> for (i in -3..3) { val a = base + i * 0.12f; bullets.add(B(e.x, e.y + 8f, cos(a) * speed, sin(a) * speed, false)) }
                        7 -> {
                            enemyLasers.add(EnemyLaser(e.x - 26f, 0.55f, 0.7f, 6f, 18f, e.y))
                            enemyLasers.add(EnemyLaser(e.x + 26f, 0.55f, 0.7f, 6f, -18f, e.y))
                        }
                        8 -> {
                            for (i in 0 until 12) { val a = i * (2f * PI.toFloat() / 12f) + e.age * 0.24f; bullets.add(B(e.x, e.y, cos(a) * speed, sin(a) * speed, false)) }
                            for (i in -1..1) { val a = base + i * 0.2f; bullets.add(B(e.x, e.y + 8f, cos(a) * speed * 1.12f, sin(a) * speed * 1.12f, false)) }
                        }
                        else -> {
                            enemyLasers.add(EnemyLaser(e.x, 0.48f, 0.58f, 8f, if (px > e.x) 20f else -20f, e.y))
                            for (i in -2..2) { val a = base + i * 0.18f; bullets.add(B(e.x, e.y + 8f, cos(a) * speed, sin(a) * speed, false)) }
                        }
                    }
                    if (e.bossSkills.any { it in setOf("multishot", "drone", "critical", "ricochet") }) {
                        for (i in -1..1) { val a = base + i * 0.10f; bullets.add(B(e.x, e.y + 8f, cos(a) * speed * 1.18f, sin(a) * speed * 1.18f, false)) }
                    }
                    if (e.phase >= 2) {
                        for (i in 0 until 10) {
                            val a = i * (2f * PI.toFloat() / 10f) + e.age * 0.28f
                            bullets.add(B(e.x, e.y, cos(a) * speed * 0.78f, sin(a) * speed * 0.78f, false))
                        }
                    }
                    if (e.phase >= 3) {
                        enemyLasers.add(EnemyLaser(e.x - 34f, 0.42f, 0.58f, 7f, 20f, e.y))
                        enemyLasers.add(EnemyLaser(e.x + 34f, 0.42f, 0.58f, 7f, -20f, e.y))
                    }
                } else if (e.type == 5) {
                    e.fire = 2.5f
                    val allies = enemies.filter { it !== e && !it.dead && hypot(it.x - e.x, it.y - e.y) < 68f }
                    for (ally in allies) ally.hp = min(ally.maxHp, ally.hp + 1)
                    boom(e.x, e.y, 7, 30f)
                } else {
                    val baseInterval = when (e.type) {
                        1 -> max(0.48f, 1.12f - wave * 0.012f)
                        3 -> max(0.58f, 1.36f - wave * 0.013f)
                        4 -> max(0.72f, 1.62f - wave * 0.011f)
                        6 -> max(0.44f, 0.92f - wave * 0.009f)
                        else -> max(0.82f, 1.82f - wave * 0.010f)
                    }
                    e.fire = baseInterval * if (wave >= 10) 0.80f else 1f
                    if (e.y > 4f && e.y < wh * 0.72f) {
                        val dx = px + sin(time * 1.6f + e.t0) * 9f - e.x
                        val dy = py - e.y
                        val base = atan2(dy.toDouble(), dx.toDouble()).toFloat()
                    val speed = (72f + min(wave.toFloat(), 45f) * 1.4f) * if (wave >= 10) 1.16f else 1f
                    if (wave >= 10) {
                        when ((e.type + wave + (e.age * 0.7f).toInt()).mod(4)) {
                            0 -> for (i in -2..2) { val a = base + i * 0.19f; bullets.add(B(e.x, e.y + 7f, cos(a) * speed, sin(a) * speed, false)) }
                            1 -> for (i in -1..1) { val a = base + i * 0.28f; val v = speed * if (i == 0) 1.28f else 0.92f; bullets.add(B(e.x, e.y + 7f, cos(a) * v, sin(a) * v, false)) }
                            2 -> {
                                for (side in -1..1 step 2) { val a = base + side * 0.38f; bullets.add(B(e.x, e.y + 7f, cos(a) * speed * 1.12f, sin(a) * speed * 1.12f, false)) }
                                bullets.add(B(e.x, e.y + 7f, cos(base) * speed * 0.78f, sin(base) * speed * 0.78f, false))
                            }
                            else -> {
                                for (i in -2..2) { val a = base + i * 0.13f; val v = speed * if (e.type == 6) 1.25f else 0.88f; bullets.add(B(e.x, e.y + 7f, cos(a) * v, sin(a) * v, false)) }
                            }
                        }
                    } else when (e.type) {
                        1 -> for (i in -1..1) { val a = base + i * 0.15f; bullets.add(B(e.x, e.y + 7f, cos(a) * speed, sin(a) * speed, false)) }
                        3 -> for (i in -1..1) { val a = base + i * 0.23f; bullets.add(B(e.x, e.y + 7f, cos(a) * speed * 1.1f, sin(a) * speed * 1.1f, false)) }
                        4 -> for (i in -2..2) { val a = base + i * 0.12f; bullets.add(B(e.x, e.y + 7f, cos(a) * speed * 0.88f, sin(a) * speed * 0.88f, false)) }
                        6 -> for (i in -2..2) { val a = base + i * 0.24f; bullets.add(B(e.x, e.y + 7f, cos(a) * speed * 1.25f, sin(a) * speed * 1.25f, false)) }
                        else -> for (i in -1..1) { val a = base + i * 0.12f; bullets.add(B(e.x, e.y + 7f, cos(a) * speed, sin(a) * speed, false)) }
                    }
                    }
                }
            }
        }
        enemies.addAll(summonedEnemies)
        for (laser in enemyLasers) {
            laser.x += laser.drift * enemyDt
            if (laser.warning > 0f) laser.warning = max(0f, laser.warning - enemyDt)
            else if (laser.active > 0f) {
                laser.active = max(0f, laser.active - enemyDt)
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
        for (hole in blackHoles) {
            val dx = hole.x - px; val dy = hole.y - py; val d2 = dx * dx + dy * dy
            if (d2 < hole.radius * hole.radius * 3.2f && d2 > 30f) {
                val d = sqrt(d2); val pull = min(30f, hole.radius * hole.radius * 0.035f / (d2 + 65f)) * dt * 60f
                px = (px + dx / d * pull).coerceIn(8f, W - 8f)
                py = (py + dy / d * pull).coerceIn(24f, wh - 36f)
            }
        }
        for (comet in comets) for (hole in blackHoles) {
            val dx = hole.x - comet.x; val dy = hole.y - comet.y; val d2 = dx * dx + dy * dy
            if (d2 < hole.radius * hole.radius * 4f && d2 > 25f) {
                val d = sqrt(d2); val force = min(55f, hole.radius * hole.radius * 0.07f / (d2 + 45f))
                comet.vx += dx / d * force * dt * 60f; comet.vy += dy / d * force * dt * 60f
            }
        }
        
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
            val bulletDt = if (b.mine) dt else enemyDt
            if (!b.mine && droneLevel > 0) {
                for (i in 0 until droneLevel) {
                    val angle = time * 2.25f + i * PI.toFloat()
                    val dx = px + cos(angle) * 18f - b.x
                    val dy = py + sin(angle) * 10f - b.y
                    if (hypot(dx, dy) < 6.5f) {
                        b.mine = true
                        b.vx = dx.coerceIn(-80f, 80f)
                        b.vy = -165f
                        break
                    }
                }
            }
            if (!b.mine && shieldHp > 0 && hypot(b.x - px, b.y - py) < 27f) {
                b.mine = true
                val target = enemies.filter { !it.dead }.minByOrNull { hypot(it.x - b.x, it.y - b.y) }
                if (target != null) {
                    val a = atan2((target.y - b.y).toDouble(), (target.x - b.x).toDouble()).toFloat()
                    b.vx = cos(a) * 205f; b.vy = sin(a) * 205f
                    chainArcs.add(Arc(px, py, target.x, target.y))
                } else { b.vx = 0f; b.vy = -205f }
                shieldHp = (shieldHp - 1).coerceAtLeast(0); reflectiveShield = shieldHp > 0
                shieldHitTimer = 0.24f
                shieldHitAngle = atan2(b.y - py, b.x - px)
                sfx.play("power")
                sfx.play("power")
            }
            for (hole in blackHoles) {
                val dx = hole.x - b.x
                val dy = hole.y - b.y
                val d2 = dx * dx + dy * dy
                if (d2 < hole.radius * hole.radius && d2 > 9f) {
                    val distance = sqrt(d2)
                    val pull = min(1800f, hole.strength * 13f / (d2 + 65f)) * bulletDt
                    b.vx = (b.vx + dx / distance * pull).coerceIn(-420f, 420f)
                    b.vy = (b.vy + dy / distance * pull).coerceIn(-420f, 420f)
                    if (distance < hole.radius * 0.30f) {
                        val tangentX = -dy / distance
                        val tangentY = dx / distance
                        val kick = min(260f, (hole.radius * 0.30f - distance) * 8f) * dt
                        val side = if (b.vx * tangentX + b.vy * tangentY >= 0f) 1f else -1f
                        b.vx = (b.vx - dx / distance * kick * 1.8f + tangentX * kick * side).coerceIn(-460f, 460f)
                        b.vy = (b.vy - dy / distance * kick * 1.8f + tangentY * kick * side).coerceIn(-460f, 460f)
                    }
                }
            }
            b.x += b.vx * bulletDt
            b.y += b.vy * bulletDt
        }
        bullets.removeAll { it.y < -10f || it.y > wh + 10f || it.x < -10f || it.x > W + 10f }

        // floating pickups
        for (item in items) {
            item.age += dt
            if (magnetTimer > 0f || magnetLevel > 0) {
                val pullRate = if (magnetLevel > 0 && shieldLevel > 0) 5.2f else if (magnetLevel > 0) 3.8f else 3.0f
                item.x += (px - item.x) * min(1f, pullRate * dt)
                item.y += (py - item.y) * min(1f, pullRate * dt)
            } else {
                item.y += (24f + min(wave.toFloat(), 10f)) * dt
                item.x = (item.x + sin(item.age * 2.4f) * 8f * dt).coerceIn(8f, W - 8f)
            }
            for (hole in blackHoles) {
                val dx = hole.x - item.x; val dy = hole.y - item.y; val d2 = dx * dx + dy * dy
                if (d2 < hole.radius * hole.radius * 3.5f && d2 > 25f) {
                    val d = sqrt(d2); val force = min(48f, hole.radius * hole.radius * 0.065f / (d2 + 50f)) * dt * 60f
                    item.x += dx / d * force; item.y += dy / d * force
                }
            }
        }
        val collected = ArrayList<Item>()
        for (item in items) if (hypot(item.x - px, item.y - py) < 15f) {
            when (item.type) {
                0 -> { lives = min(10, lives + 1); score += 100 }
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
                11 -> enemySlowTimer = max(enemySlowTimer, 10f)
                12 -> { credits += 300; score += 500; prefs.edit().putInt("credits", credits).apply() }
                13 -> { credits += item.amount.coerceAtLeast(1); score += item.amount * 2; prefs.edit().putInt("credits", credits).apply() }
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
                    var ricocheted = false
                    if (ricochetRank > 0 && b.hitEnemies.size <= ricochetRank) {
                        val nextTarget = enemies.filter { it !== e && !it.dead && !b.hitEnemies.contains(it) }
                            .minByOrNull { hypot(it.x - b.x, it.y - b.y) }
                        if (nextTarget != null) {
                            val angle = atan2(nextTarget.y - b.y, nextTarget.x - b.x)
                            val speed = hypot(b.vx, b.vy).coerceAtLeast(150f)
                            b.vx = cos(angle) * speed
                            b.vy = sin(angle) * speed
                            ricocheted = true
                            impactRings.add(ImpactRing(b.x, b.y, 0.18f, 0.18f))
                        }
                    }
                    if (!ricocheted && piercingTimer <= 0f && b.hitEnemies.size > piercingRank) deadB.add(b)
                    val criticalDamage = if (criticalRank > 0 && Random.nextFloat() < criticalRank * 0.09f) 2 else 0
                    var hitDamage = 1 + bonusDamage + (if (currentShip == 3 || currentShip == 4) 1 else 0) + criticalDamage + (if (overloadTimer > 0f) 1 else 0) + (if (scannerTimer > 0f) scannerRank else 0)
                    if (e.type == 2 && e.bossSkills.any { it in setOf("shield", "armor", "ricochet_shield", "hull") } && Random.nextFloat() < 0.48f) hitDamage = 0
                    e.hp -= hitDamage
                    e.hitFlash = 0.14f
                    impactRings.add(ImpactRing(b.x, b.y))
                    screenShake = max(screenShake, 0.055f); shakePower = max(shakePower, 0.7f)
                    boom(b.x, b.y, 4, 32f)
                    if (explosiveRank > 0) {
                        for (nearby in enemies) if (nearby !== e && !nearby.dead && hypot(nearby.x - e.x, nearby.y - e.y) < 16f + explosiveRank * 3.5f) {
                            nearby.hp -= explosiveRank
                            if (nearby.hp <= 0) kill(nearby)
                        }
                    }
                    if (chainLevel > 0 && chainCooldown <= 0f) {
                        val next = enemies.filter { it !== e && !it.dead }.minByOrNull { hypot(it.x - e.x, it.y - e.y) }
                        if (next != null && hypot(next.x - e.x, next.y - e.y) < 78f) {
                            chainArcs.add(Arc(e.x, e.y, next.x, next.y))
                            next.hp -= chainLevel
                            chainCooldown = 0.48f
                            boom((e.x + next.x) / 2f, (e.y + next.y) / 2f, 5, 34f)
                            if (next.hp <= 0) kill(next)
                        }
                    }
                    if (e.hp <= 0) kill(e)
                    break
                }
            }
        }
        enemies.removeAll { it.dead }

        // player hit
        if (invul <= 0f && !(legendaryTime > 0f && currentShip == 3)) {
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
        val coinScale = if (item.type == 13) 0.66f else 1f
        val scale = s * coinScale * (1f + sin(item.age * 5f) * 0.06f)
        val color = when (item.type) {
            13 -> 0xFFFFD34F.toInt()
            0 -> 0xFFFF647C.toInt()
            1 -> 0xFF55D9FF.toInt()
            2 -> 0xFFFFD84D.toInt()
            3 -> 0xFFFF79D8.toInt()
            4 -> 0xFFFF8A35.toInt()
            10 -> 0xFF72F0C0.toInt()
            11 -> 0xFFFFD45C.toInt()
            12 -> 0xFFFFC94A.toInt()
            else -> 0xFFB68CFF.toInt()
        }
        c.save()
        c.translate(cx, cy)
        c.scale(scale, scale)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.8f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; this.color = color }
        // A crisp circular badge; no bitmap blur, so the glow cannot become a square.
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.5f; paint.color = color
        c.drawCircle(0f, 0f, 9f, paint)
        paint.strokeWidth = 0.7f
        c.drawCircle(0f, 0f, 7.2f, paint)
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
            5 -> { // guided missile
                val rocket = Path().apply { moveTo(0f, -7f); quadTo(4f, -2f, 2.5f, 3f); lineTo(0f, 5f); lineTo(-2.5f, 3f); quadTo(-4f, -2f, 0f, -7f); close() }
                c.drawPath(rocket, p)
                c.drawLine(-2f, 2f, -5f, 5f, p); c.drawLine(2f, 2f, 5f, 5f, p)
                c.drawLine(0f, 5f, 0f, 7f, p)
            }
            6 -> { // laser beam
                c.drawLine(0f, -7f, 0f, 7f, p)
                c.drawLine(-3f, -4f, 3f, -4f, p); c.drawLine(-3f, 4f, 3f, 4f, p)
                c.drawLine(-5f, -6f, -3f, -4f, p); c.drawLine(5f, 6f, 3f, 4f, p)
            }
            7 -> { // support drone
                c.drawCircle(0f, 0f, 3f, p)
                c.drawLine(-7f, -3f, -3f, -1f, p); c.drawLine(3f, -1f, 7f, -3f, p)
                c.drawLine(-6f, 3f, -2f, 2f, p); c.drawLine(2f, 2f, 6f, 3f, p)
                c.drawCircle(-7f, -3f, 1.2f, p); c.drawCircle(7f, -3f, 1.2f, p)
            }
            8 -> { // magnet
                val magnet = Path().apply { moveTo(-5f, -5f); lineTo(-5f, 2f); quadTo(-5f, 7f, 0f, 7f); quadTo(5f, 7f, 5f, 2f); lineTo(5f, -5f) }
                c.drawPath(magnet, p)
                c.drawLine(-7f, -5f, -3f, -5f, p); c.drawLine(3f, -5f, 7f, -5f, p)
            }
            9 -> { // support squadron
                for (x in floatArrayOf(-5f, 0f, 5f)) {
                    val ship = Path().apply { moveTo(x, -5f); lineTo(x + 3f, 4f); lineTo(x, 2f); lineTo(x - 3f, 4f); close() }
                    c.drawPath(ship, p)
                }
            }
            10 -> { // piercing rounds
                c.drawLine(-5f, -4f, 5f, 4f, p)
                c.drawLine(-5f, 0f, 5f, 0f, p)
                c.drawLine(-5f, 4f, 5f, -4f, p)
            }
            11 -> { // enemy slowdown
                c.drawCircle(0f, 0f, 5.5f, p)
                c.drawLine(0f, 0f, 0f, -4f, p)
                c.drawLine(0f, 0f, 3.5f, 2.5f, p)
                c.drawLine(-7f, 6f, -3f, 6f, p)
                c.drawLine(-5f, 4f, -5f, 6f, p)
            }
            12 -> { // golden crate
                c.drawRoundRect(-5f, -4f, 5f, 5f, 1.2f, 1.2f, p)
                c.drawLine(-5f, -1f, 5f, -1f, p); c.drawLine(0f, -4f, 0f, 5f, p)
                c.drawLine(-2f, -6f, 2f, -6f, p)
            }
            13 -> { // dropped coin
                c.drawCircle(0f, 0f, 6.4f, p); c.drawCircle(0f, 0f, 4.4f, p)
                c.drawLine(0f, -3.2f, 0f, 3.2f, p); c.drawLine(-2f, -1.5f, 2f, -1.5f, p)
                c.drawLine(-2f, 1.5f, 2f, 1.5f, p)
            }
            else -> { // squadron
                for (x in floatArrayOf(-5f, 0f, 5f)) {
                    val ship = Path().apply { moveTo(x, -5f); lineTo(x + 3f, 4f); lineTo(x, 2f); lineTo(x - 3f, 4f); close() }
                    c.drawPath(ship, p)
                }
            }
        }
        c.restore()
    }

    private fun text(c: Canvas, t: String, x: Float, y: Float, size: Float, color: Int, align: Paint.Align = Paint.Align.CENTER) {
        tp.textSize = size * s
        tp.color = color
        val isPersian = t.any { it in '\u0600'..'\u06FF' || it in '\uFB50'..'\uFDFF' || it in '\uFE70'..'\uFEFF' }
        if (!isPersian) {
            tp.textAlign = align
            c.drawText(t, x * s, y * s, tp)
            return
        }
        val width = (tp.measureText(t) * 1.22f + 10f * s).roundToInt().coerceIn(1, max(1, sw))
        val layout = StaticLayout.Builder.obtain(t, 0, t.length, tp, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setTextDirection(TextDirectionHeuristics.RTL)
            .setIncludePad(false)
            .build()
        val left = when (align) {
            Paint.Align.LEFT -> x * s
            Paint.Align.RIGHT -> x * s - width
            else -> x * s - width / 2f
        }
        c.save()
        c.translate(left, y * s - layout.getLineBaseline(0))
        layout.draw(c)
        c.restore()
    }

    private fun renderFrame(c: Canvas) {
        if (!::bPlayer.isInitialized) return
        c.drawColor(BG)
        paint.style = Paint.Style.FILL
        for (star in stars) {
            val pulse = (sin(star.phase) + 1f) * 0.5f
            paint.color = 0xFFD0D4DA.toInt()
            paint.alpha = if (star.twinkle) (95f + pulse * 105f).toInt().coerceIn(80, 205) else 145
            val radius = star.sz * if (star.twinkle) (0.55f + pulse * 0.35f) else 0.62f
            var wx = star.x; var wy = star.y
            for (hole in blackHoles) {
                val dx = wx - hole.x; val dy = wy - hole.y; val d = max(1f, hypot(dx, dy))
                val reach = hole.radius * 2.65f
                if (d < reach) {
                    val bend = ((reach - d) / reach).coerceIn(0f, 1f)
                    val swirl = sin(hole.spin + d * 0.035f) * bend * hole.radius * 0.26f
                    wx += -dx / d * bend * hole.radius * 0.24f - dy / d * swirl
                    wy += -dy / d * bend * hole.radius * 0.24f + dx / d * swirl
                    paint.alpha = (paint.alpha * (1f - bend * 0.30f)).toInt().coerceIn(35, 205)
                }
            }
            if (abs(wx - star.x) + abs(wy - star.y) > 1.2f) {
                paint.strokeWidth = max(0.6f, radius * 0.55f) * s
                c.drawLine(star.x * s, star.y * s, wx * s, wy * s, paint)
                paint.style = Paint.Style.FILL
            }
            c.drawCircle(wx * s, wy * s, radius * s, paint)
            paint.alpha = if (star.twinkle) (95f + pulse * 105f).toInt().coerceIn(80, 205) else 145
        }
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = 0.48f * s
        paint.color = 0xFFD4D8DE.toInt()
        for (comet in comets) {
            val fade = (115f * (comet.life / comet.maxLife)).toInt().coerceIn(24, 115)
            val velocity = max(1f, hypot(comet.vx, comet.vy))
            val tailX = (comet.x - comet.vx / velocity * comet.length) * s
            val tailY = (comet.y - comet.vy / velocity * comet.length) * s
            paint.alpha = (fade * 0.22f).toInt()
            c.drawLine(comet.x*s,comet.y*s,tailX,tailY,paint)
            paint.alpha = (fade * 0.48f).toInt()
            c.drawLine(comet.x*s,comet.y*s,(comet.x+(tailX/s-comet.x)*0.55f)*s,(comet.y+(tailY/s-comet.y)*0.55f)*s,paint)
            paint.style = Paint.Style.FILL; paint.alpha = fade
            c.drawCircle(comet.x*s,comet.y*s,0.72f*s,paint)
            paint.style = Paint.Style.STROKE
        }
        paint.alpha = 255
        paint.style = Paint.Style.FILL
        c.save()
        if (screenShake > 0f) { val strength = shakePower * (screenShake / 0.26f); c.translate(sin(time * 91f) * strength * s, cos(time * 77f) * strength * s) }
        when (st) {
            St.MENU -> drawMenu(c)
            St.ABOUT -> drawAbout(c)
            St.PLAY -> drawGame(c)
            St.PAUSE -> drawPause(c)
            St.OVER -> { drawGame(c); drawOver(c) }
            St.AD_BREAK -> { drawGame(c); drawAdBreak(c) }
            St.UPGRADE -> drawUpgrade(c)
            St.SHOP -> drawShop(c)
            St.SHIPS -> drawShipHangar(c)
            St.SHIP_UPGRADE -> drawShipUpgrades(c)
        }
        c.restore()
        if (damageFlash > 0f) { paint.style = Paint.Style.FILL; paint.color = Color.argb((58f * damageFlash / 0.22f).toInt().coerceIn(0, 58), 255, 42, 66); c.drawRect(0f, 0f, sw.toFloat(), sh.toFloat(), paint) }
    }


    private fun drawNeonAbilityVfx(c: Canvas) {
        val cx = px * s
        val cy = py * s
        if (overloadTimer > 0f) {
            val pulse = 0.5f + 0.5f * sin(time * 18f)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = (1.2f + pulse * 1.5f) * s
            paint.color = Color.argb((130 + pulse * 100).toInt(), 255, 170, 55)
            c.drawCircle(cx, cy, (18f + pulse * 5f) * s, paint)
            for (i in 0..3) {
                val a = time * 8f + i * 1.5708f
                val bolt = Path()
                bolt.moveTo(cx + cos(a) * 20f * s, cy + sin(a) * 20f * s)
                bolt.lineTo(cx + cos(a + 0.18f) * 27f * s, cy + sin(a + 0.18f) * 27f * s)
                bolt.lineTo(cx + cos(a - 0.10f) * 33f * s, cy + sin(a - 0.10f) * 33f * s)
                paint.color = 0xFFFFD27A.toInt()
                c.drawPath(bolt, paint)
            }
        }
        if (gravityActiveTimer > 0f) {
            val progress = 1f - gravityActiveTimer / 1.65f
            val radius = (48f - progress * 12f) * s
            paint.style = Paint.Style.STROKE
            for (i in 0..2) {
                paint.color = if (i == 1) 0xFFB18CFF.toInt() else 0xFF4CEBFF.toInt()
                paint.strokeWidth = (2.2f - i * 0.45f) * s
                c.drawArc(RectF(cx - radius * (0.55f + i * 0.18f), cy - radius * (0.30f + i * 0.12f),
                    cx + radius * (0.55f + i * 0.18f), cy + radius * (0.30f + i * 0.12f)),
                    time * (90f + i * 35f), 205f, false, paint)
            }
            paint.style = Paint.Style.FILL
            paint.color = 0xFF16102E.toInt()
            c.drawCircle(cx, cy, 5f * s, paint)
            paint.style = Paint.Style.STROKE
        }
        if (nanobotsRank > 0) {
            paint.style = Paint.Style.FILL
            for (i in 0 until nanobotsRank + 2) {
                val a = time * 2.7f + i * (6.28318f / (nanobotsRank + 2))
                val r = 21f + 3f * sin(time * 5f + i)
                paint.color = if (i % 2 == 0) 0xFF76FFD6.toInt() else 0xFF7BE8FF.toInt()
                c.drawCircle(cx + cos(a) * r * s, cy + sin(a) * r * s, 1.5f * s, paint)
            }
        }
        if (salvageDroneRank > 0) {
            val a = time * 1.8f
            val dx = cx + cos(a) * 31f * s
            val dy = cy + sin(a) * 20f * s
            paint.color = 0xFFFFD66B.toInt()
            c.drawCircle(dx, dy, 3.0f * s, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f * s
            c.drawCircle(dx, dy, 5.0f * s, paint)
            paint.style = Paint.Style.FILL
        }
        if (ricochetShieldRank > 0) {
            val pulse = 0.5f + 0.5f * sin(time * 4.5f)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.1f * s
            paint.color = Color.argb((85f + pulse * 75f).toInt(), 90, 220, 255)
            c.drawCircle(cx, cy, (26f + pulse * 2f) * s, paint)
            c.drawArc(RectF(cx - 30f*s, cy - 30f*s, cx + 30f*s, cy + 30f*s), time * 70f, 115f, false, paint)
            paint.style = Paint.Style.FILL
        }
        if (scannerTimer > 0f) {
            for (target in enemies) if (!target.dead) {
                val r = (target.hw + 7f) * s
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.1f * s
                paint.color = 0xFFFF5E8A.toInt()
                c.drawCircle(target.x * s, target.y * s, r, paint)
                c.drawLine((target.x - target.hw - 4f)*s, target.y*s, (target.x - target.hw + 2f)*s, target.y*s, paint)
                c.drawLine((target.x + target.hw - 2f)*s, target.y*s, (target.x + target.hw + 4f)*s, target.y*s, paint)
            }
            paint.style = Paint.Style.FILL
        }
    }

    private fun drawGame(c: Canvas) {
        for (hole in blackHoles) drawBlackHole(c, hole)
        drawNeonAbilityVfx(c)
        for (e in enemies) {
            val sprite = when (e.type) { 2 -> bossSprites[e.t0.toInt().coerceIn(0, 9)]; 1 -> bE1; 3 -> bE2; 4 -> bE3; 5 -> bossSprites[5]; 6 -> bossSprites[6]; else -> bE0 }
            spr(c, sprite, e.x, e.y)
            if (e.hitFlash > 0f) { paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.5f * s; paint.color = Color.argb((210f * e.hitFlash / 0.14f).toInt().coerceIn(0, 210), 255, 255, 255); c.drawCircle(e.x * s, e.y * s, (e.hw + 3f) * s, paint); paint.style = Paint.Style.FILL }
            if (e.type == 2 && e.warning > 0f) {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.1f * s
                paint.color = 0xFFFFD45C.toInt()
                c.drawLine(e.x * s, (e.y + 8f) * s, px * s, py * s, paint)
                paint.style = Paint.Style.FILL
            }
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
        for (ring in impactRings) drawPixelfxImpact(c, ring)
        for (item in items) drawItem(c, item)
        for (b in bullets) { c.save(); c.scale(0.72f, 0.72f, b.x * s, b.y * s); spr(c, if (b.mine) bPB else bEB, b.x, b.y); c.restore() }
        for (arc in chainArcs) {
            paint.style = Paint.Style.STROKE; paint.strokeWidth = (1f + arc.life * 4f) * s
            paint.color = 0xFF73F5FF.toInt(); paint.alpha = (255f * (arc.life / 0.24f)).toInt().coerceIn(0, 255)
            c.drawLine(arc.x1 * s, arc.y1 * s, arc.x2 * s, arc.y2 * s, paint)
            paint.alpha = 255; paint.style = Paint.Style.FILL
        }
        for (beam in playerBeams) {
            val sourceY = (beam.bottom - 8f).coerceAtLeast(0f)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = (beam.width + 2f) * s; paint.color = 0x6654E8FF.toInt()
            c.drawLine(beam.x * s, sourceY * s, beam.x * s, 0f, paint)
            paint.strokeWidth = beam.width * s; paint.color = 0xFF54E8FF.toInt()
            c.drawLine(beam.x * s, sourceY * s, beam.x * s, 0f, paint)
            paint.style = Paint.Style.FILL; paint.color = 0xFFEAFBFF.toInt()
            c.drawCircle(beam.x * s, sourceY * s, 2.8f * s, paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 1f * s; paint.color = 0xFF54E8FF.toInt()
            c.drawCircle(beam.x * s, sourceY * s, 4.5f * s, paint)
        }
        paint.style = Paint.Style.FILL
        paint.style = Paint.Style.FILL
        for (missile in missiles) {
            val heading = atan2(missile.vy, missile.vx)
            val tx = missile.x * s
            val ty = missile.y * s
            for (trail in 1..5) {
                val d = trail * 2.7f * s
                val fx = tx - cos(heading) * d
                val fy = ty - sin(heading) * d
                paint.color = Color.argb((150f * (1f - trail / 6f)).toInt(), 255, 92 + trail * 18, 38)
                c.drawCircle(fx, fy, (3.1f - trail * 0.42f) * s, paint)
                paint.color = Color.argb((190f * (1f - trail / 6f)).toInt(), 255, 210, 115)
                c.drawCircle(fx, fy, (1.25f - trail * 0.13f) * s, paint)
            }
            paint.color = 0xFFFFF0B8.toInt()
            c.drawCircle(tx - cos(heading) * 5f * s, ty - sin(heading) * 5f * s, 2.2f * s, paint)
            c.save()
            c.rotate(Math.toDegrees(heading.toDouble()).toFloat() + 90f, missile.x * s, missile.y * s)
            val rocketPath = Path().apply { moveTo(0f, -7f * s); lineTo(3f * s, 4f * s); lineTo(0f, 2f * s); lineTo(-3f * s, 4f * s); close() }
            paint.color = 0xFFFFA64D.toInt(); c.drawPath(rocketPath, paint); c.restore()
        }
        for (ship in squadShips) {
            val path = Path().apply { moveTo(ship.x * s, (ship.y - 7f) * s); lineTo((ship.x + 5f) * s, (ship.y + 5f) * s); lineTo(ship.x * s, (ship.y + 2f) * s); lineTo((ship.x - 5f) * s, (ship.y + 5f) * s); close() }
            paint.color = 0xFF66E8FF.toInt(); c.drawPath(path, paint)
        }
        for (laser in enemyLasers) {
            val laserColor = if (laser.warning > 0f) 0xFFFFD45C.toInt() else 0xFFFF5868.toInt()
            paint.style = Paint.Style.STROKE; paint.strokeWidth = (if (laser.warning > 0f) 1.2f else laser.width + 2f) * s
            paint.color = if (laser.warning > 0f) 0x66FFD45C.toInt() else 0x66FF5868.toInt()
            c.drawLine(laser.x * s, laser.originY * s, laser.x * s, sh.toFloat(), paint)
            paint.strokeWidth = (if (laser.warning > 0f) 1.2f else laser.width) * s; paint.color = laserColor
            c.drawLine(laser.x * s, laser.originY * s, laser.x * s, sh.toFloat(), paint)
            paint.style = Paint.Style.FILL; paint.color = laserColor
            c.drawCircle(laser.x * s, laser.originY * s, 3.2f * s, paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 1f * s
            c.drawCircle(laser.x * s, laser.originY * s, 5.6f * s, paint)
        }
        paint.style = Paint.Style.FILL
        paint.style = Paint.Style.FILL
        if (shieldTimer > 0f || shieldHp > 0) {
            drawPixelfxShield(c)
            paint.clearShadowLayer(); paint.style = Paint.Style.FILL
        }
        if ((st == St.PLAY || st == St.PAUSE) && (invul <= 0f || (time * 12).toInt() % 2 == 0)) {
            val oldAlpha = spritePaint.alpha
            if (legendaryTime > 0f && currentShip == 3) spritePaint.alpha = 92
            drawPlayerShip(c, px, py)
            spritePaint.alpha = oldAlpha
        }
        for (p in parts) {
            val k = 1f - p.life / p.max
            paint.color = if (k < 0.3f) WHT else if (k < 0.6f) YEL else RED
            paint.alpha = (255 * (p.life / p.max)).toInt().coerceIn(0, 255)
            c.drawRect(p.x * s, p.y * s, (p.x + 1.6f) * s, (p.y + 1.6f) * s, paint)
        }
        paint.alpha = 255
        text(c, "امتیاز $score", 6f, 14f, 9f, YEL, Paint.Align.LEFT)
        if (comboCount >= 3) text(c, "ترکیب x" + (1 + comboCount / 5).coerceAtMost(5), W / 2f, 14f, 8f, 0xFFFFD45C.toInt())
        text(c, "جان", 7f, 27f, 6f, 0xFF9CEBFF.toInt(), Paint.Align.LEFT)
        paint.style = Paint.Style.FILL
        paint.color = 0xCC25313D.toInt()
        c.drawRoundRect(18f * s, 21f * s, 72f * s, 25f * s, 1.8f * s, 1.8f * s, paint)
        val baseHealth = lives.coerceAtMost(5)
        val extraHealth = (lives - 5).coerceIn(0, 5)
        paint.color = if (baseHealth >= 3) 0xFF55DDAA.toInt() else if (baseHealth == 2) 0xFFFFC857.toInt() else 0xFFFF5968.toInt()
        c.drawRoundRect(18f * s, 21f * s, (18f + 54f * baseHealth / 5f) * s, 23f * s, 1.0f * s, 1.0f * s, paint)
        if (extraHealth > 0) {
            paint.color = 0xFFFFC857.toInt()
            c.drawRoundRect(18f * s, 23f * s, (18f + 54f * extraHealth / 5f) * s, 25f * s, 1.0f * s, 1.0f * s, paint)
        }
        if (lives > 5) text(c, "۲×", 77f, 25f, 7f, 0xFFFFC857.toInt(), Paint.Align.LEFT)
        else text(c, lives.toString() + "/۵", 77f, 25f, 6f, 0xFFFFD45C.toInt(), Paint.Align.LEFT)
        if (shieldRank > 0) {
            text(c, "سپر", 7f, 42f, 5.5f, 0xFF8BDFFF.toInt(), Paint.Align.LEFT)
            paint.color = 0x88303C4A.toInt(); c.drawRoundRect(18f*s, 36f*s, 72f*s, 39f*s, 1.5f*s, 1.5f*s, paint)
            paint.color = if (shieldHp > shieldMaxHp / 3) 0xFF55DFFF.toInt() else 0xFFFF6688.toInt()
            val shieldFrac = if (shieldMaxHp > 0) shieldHp.toFloat() / shieldMaxHp else 0f
            c.drawRoundRect(18f*s, 36f*s, (18f + 54f*shieldFrac)*s, 39f*s, 1.5f*s, 1.5f*s, paint)
        }
        val powerLabel = when {
            magnetLevel > 0 && shieldLevel > 0 -> "سپر مغناطیسی"
            shieldLevel > 0 -> "سپر بازتابی " + shieldLevel
            enemySlowTimer > 0f -> "کند شدن دشمن " + (enemySlowTimer.toInt() + 1)
            piercingTimer > 0f -> "گلوله نفوذی " + (piercingTimer.toInt() + 1)
            shieldTimer > 0f -> "سپر " + (shieldTimer.toInt() + 1)
            empRank > 0 -> "موج الکترومغناطیسی " + empRank + "  " + (empPulseTimer.toInt().coerceAtLeast(0)) + "ثانیه"
            rapidTimer > 0f -> "شلیک سریع " + (rapidTimer.toInt() + 1)
            magnetTimer > 0f -> "آهنربا " + (magnetTimer.toInt() + 1)
            weaponLevel > 1 -> "قدرت سلاح " + weaponLevel
            else -> ""
        }
        if (powerLabel.isNotEmpty()) text(c, powerLabel, W / 2, wh - 10f, 7f, 0xFF92E9FF.toInt())
        if (st == St.PLAY) {
            for (i in 0 until droneLevel) {
                val angle = time * 2.25f + i * PI.toFloat()
                val dx = cos(angle) * 18f
                val dy = sin(angle) * 10f
                val wing = Path().apply { moveTo((px + dx) * s, (py + dy - 5f) * s); lineTo((px + dx + 4f) * s, (py + dy + 3f) * s); lineTo((px + dx) * s, (py + dy + 1f) * s); lineTo((px + dx - 4f) * s, (py + dy + 3f) * s); close() }
                paint.color = 0xFF63DDBB.toInt(); c.drawPath(wing, paint)
            }
            if (dashCharges > 0) {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 0.9f * s
                paint.color = if (dashCooldown <= 0f) 0xFF55DFFF.toInt() else 0xFF66717E.toInt()
                c.drawRoundRect(8f * s, (wh - 32f) * s, 43f * s, (wh - 7f) * s, 4f * s, 4f * s, paint)
                paint.style = Paint.Style.FILL
                drawAbilityIcon(c, "dash", 25.5f, wh - 19.5f)
                text(c, dashCharges.toString(), 37f, wh - 9f, 3.8f, if (dashCooldown <= 0f) 0xFF8BEFFF.toInt() else 0xFF86919D.toInt())
            }
            drawLegendaryControl(c)
            drawPauseControl(c)
        }
        enemies.firstOrNull { it.type == 2 }?.let { bs ->
            text(c, "غول " + (bs.t0.toInt() + 1) + "/۱۰  فاز " + bs.phase, W / 2f, 17f, 7f, WHT)
            bs.bossSkills.take(2).forEachIndexed { index, skill -> drawAbilityIcon(c, skill, W / 2f - 13f + index * 26f, 31f) }
            val frac = bs.hp.toFloat() / bs.maxHp
            paint.color = DRED; c.drawRect(20f * s, 20f * s, (20f + 140f * frac) * s, 23f * s, paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = s * 0.6f; paint.color = RED
            c.drawRect(20f * s, 20f * s, 160f * s, 23f * s, paint); paint.style = Paint.Style.FILL
        }
        if (waveTarget > 0 && (st == St.PLAY || st == St.PAUSE)) {
            val x = (W - 5f) * s; val top = 42f * s; val bottom = (wh - 48f) * s
            val progress = (waveKills.toFloat() / waveTarget.toFloat()).coerceIn(0f, 1f)
            paint.color = 0x88434D5A.toInt(); c.drawRoundRect(x, top, x + 2.6f * s, bottom, 1.3f * s, 1.3f * s, paint)
            paint.color = 0xFF72E8FF.toInt(); c.drawRoundRect(x, bottom - (bottom - top) * progress, x + 2.6f * s, bottom, 1.3f * s, 1.3f * s, paint)
            paint.color = 0xFFD2D8E0.toInt(); c.drawCircle(x + 1.3f * s, top, 1.8f * s, paint)
        }
        if (bannerT > 0f && st == St.PLAY) {
            val boss = wave % 5 == 0
            val combo = if (missileLevel > 0 && droneLevel > 0) "پهپاد موشکی" else if (laserLevel > 0 && weaponLevel >= 3) "لیزر منشوری" else if (magnetLevel > 0 && shieldLevel > 0) "سپر مغناطیسی" else ""
            text(c, if (boss) "!! باس !!" else if (eventKind > 0) eventName() else "مرحله $wave", W / 2, wh * 0.4f, 15f, if (boss) RED else WHT)
            if (combo.isNotEmpty()) text(c, combo, W / 2f, wh * 0.4f + 13f, 7f, 0xFF72F0C0.toInt())
        }
    }


    private fun drawPixelfxShield(c: Canvas) {
        val cx = px * s
        val cy = py * s
        val pulse = 0.5f + 0.5f * sin(time * 5.2f)
        val radius = (27f + pulse * 1.2f) * s
        val health = if (shieldMaxHp > 0) shieldHp.toFloat() / shieldMaxHp.toFloat() else 0f
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = 0x3355DFFF
        paint.strokeWidth = 5.2f * s
        c.drawCircle(cx, cy, radius, paint)
        paint.color = 0xCC55DFFF.toInt()
        paint.strokeWidth = 1.45f * s
        c.drawCircle(cx, cy, radius, paint)
        paint.color = 0x667BCBFF
        paint.strokeWidth = 0.8f * s
        c.drawCircle(cx, cy, radius - 3.5f * s, paint)

        // Hexagonal glass plates, with a moving highlight along the dome.
        for (row in -2..2) {
            for (col in -3..3) {
                val hx = cx + (col * 8.2f + if (row % 2 == 0) 0f else 4.1f) * s
                val hy = cy + row * 7.1f * s
                val nx = (hx - cx) / radius
                val ny = (hy - cy) / radius
                if (nx * nx + ny * ny > 0.82f) continue
                val a = (0.14f + 0.18f * (0.5f + 0.5f * sin(time * 3f + col * 0.9f + row))).coerceIn(0.08f, 0.42f)
                paint.color = Color.argb((a * 255f).toInt(), 105, 220, 255)
                paint.strokeWidth = 0.65f * s
                val hex = Path()
                for (k in 0..6) {
                    val angle = (k * 60f + 30f) * Math.PI.toFloat() / 180f
                    val x = hx + cos(angle) * 4.2f * s
                    val y = hy + sin(angle) * 3.7f * s
                    if (k == 0) hex.moveTo(x, y) else hex.lineTo(x, y)
                }
                c.drawPath(hex, paint)
            }
        }

        // The hit cracks race away from the exact point of impact and fade.
        if (shieldHitTimer > 0f) {
            val age = 1f - shieldHitTimer / 0.24f
            val reach = (age * 30f + 5f) * s
            paint.color = Color.argb((230f * shieldHitTimer / 0.24f).toInt(), 225, 250, 255)
            paint.strokeWidth = 1.25f * s
            for (branch in -2..2) {
                val a = shieldHitAngle + branch * 0.34f
                val p = Path()
                p.moveTo(cx + cos(a) * radius * 0.72f, cy + sin(a) * radius * 0.72f)
                p.lineTo(cx + cos(a + 0.12f) * (radius * 0.72f + reach * 0.55f), cy + sin(a + 0.12f) * (radius * 0.72f + reach * 0.55f))
                p.lineTo(cx + cos(a - 0.06f) * (radius * 0.72f + reach), cy + sin(a - 0.06f) * (radius * 0.72f + reach))
                c.drawPath(p, paint)
            }
            paint.style = Paint.Style.FILL
            paint.color = 0xCCFFFFFF.toInt()
            c.drawCircle(cx + cos(shieldHitAngle) * radius, cy + sin(shieldHitAngle) * radius, 2.5f * s, paint)
            paint.style = Paint.Style.STROKE
        }

        // Health is shown as a segmented arc, so shield durability reads at a glance.
        paint.strokeWidth = 2.8f * s
        paint.color = if (health > 0.33f) 0xFF55DFFF.toInt() else 0xFFFF6688.toInt()
        c.drawArc(RectF(cx - (radius + 3f*s), cy - (radius + 3f*s), cx + (radius + 3f*s), cy + (radius + 3f*s)), -90f, 360f * health, false, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawPixelfxImpact(c: Canvas, ring: ImpactRing) {
        val progress = (1f - ring.life / ring.maxLife).coerceIn(0f, 1f)
        val fade = (1f - progress).coerceIn(0f, 1f)
        val cx = ring.x * s
        val cy = ring.y * s
        val radius = (4f + progress * 35f) * s

        // White-hot core, cyan plasma shell and a second violet shock ring.
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = Color.argb((220f * fade).toInt(), 220, 250, 255)
        paint.strokeWidth = (2.8f - progress * 1.7f) * s
        c.drawCircle(cx, cy, radius * 0.48f, paint)
        paint.color = Color.argb((210f * fade).toInt(), 75, 220, 255)
        paint.strokeWidth = (2.2f - progress * 1.2f) * s
        c.drawCircle(cx, cy, radius, paint)
        paint.color = Color.argb((135f * fade).toInt(), 167, 110, 255)
        paint.strokeWidth = 1.05f * s
        c.drawCircle(cx, cy, radius * 1.23f, paint)

        // Short forked arcs and radial sparks, deterministic for a clean visual rhythm.
        for (i in 0 until 8) {
            val a = i * (Math.PI.toFloat() / 4f) + time * 1.8f
            val inner = radius * 0.55f
            val outer = radius * (0.92f + 0.24f * sin(i * 7.1f + progress * 5f))
            val mx = cx + cos(a + 0.10f * sin(i.toFloat())) * (inner + outer) * 0.5f
            val my = cy + sin(a + 0.10f * cos(i.toFloat())) * (inner + outer) * 0.5f
            val ex = cx + cos(a) * outer
            val ey = cy + sin(a) * outer
            val arc = Path()
            arc.moveTo(cx + cos(a) * inner, cy + sin(a) * inner)
            arc.lineTo(mx, my)
            arc.lineTo(ex, ey)
            paint.color = Color.argb((190f * fade).toInt(), 115, 225, 255)
            paint.strokeWidth = (1.15f - progress * 0.55f) * s
            c.drawPath(arc, paint)
            paint.style = Paint.Style.FILL
            paint.color = Color.argb((220f * fade).toInt(), 225, 248, 255)
            c.drawCircle(ex, ey, (1.0f + fade) * s, paint)
            paint.style = Paint.Style.STROKE
        }
        paint.style = Paint.Style.FILL
    }

    private fun drawBlackHole(c: Canvas, hole: BlackHole) {
        val cx = hole.x * s
        val cy = hole.y * s
        val r = hole.radius * s
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.1f * s
        paint.color = 0xFFFFC247.toInt()
        c.drawCircle(cx, cy, r * (0.68f + 0.035f * sin(hole.spin)), paint)
        paint.strokeWidth = 0.7f * s
        paint.color = 0xFFFFE6A6.toInt()
        c.drawCircle(cx, cy, r * (0.84f + 0.025f * cos(hole.spin * 1.3f)), paint)
        c.save()
        c.rotate(Math.toDegrees(hole.spin.toDouble()).toFloat(), cx, cy)
        paint.strokeWidth = 1.5f * s
        paint.color = 0xFFFFD166.toInt()
        c.drawArc(cx - r, cy - r, cx + r, cy + r, 18f, 118f, false, paint)
        c.drawArc(cx - r, cy - r, cx + r, cy + r, 198f, 118f, false, paint)
        c.restore()
        paint.style = Paint.Style.FILL
        paint.color = 0xFF020308.toInt()
        c.drawCircle(cx, cy, r * 0.52f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * s
        paint.color = 0xFFD89425.toInt()
        c.drawCircle(cx, cy, r * 0.54f, paint)
        paint.style = Paint.Style.STROKE
        for (i in 0..3) {
            val orbit = r * (0.72f + i * 0.095f)
            val start = (time * (72f + i * 28f) + i * 83f) % 360f
            paint.color = when (i) { 0 -> 0xFFFFE7A3.toInt(); 1 -> 0xFFFFC247.toInt(); 2 -> 0xFFD89425.toInt(); else -> 0x66FFD166 }
            paint.strokeWidth = (2.2f - i * 0.32f) * s
            c.drawArc(cx - orbit, cy - orbit * 0.48f, cx + orbit, cy + orbit * 0.48f, start, 66f + i * 13f, false, paint)
        }
        paint.style = Paint.Style.FILL
        for (i in 0 until 10) {
            val a = time * (1.15f + i * 0.025f) + i * 0.628f
            val d = r * (0.95f - ((time * 0.28f + i * 0.11f) % 0.52f))
            val fx = cx + cos(a) * d
            val fy = cy + sin(a) * d * 0.52f
            paint.color = if (i % 3 == 0) 0xFFFFF0C2.toInt() else 0xFFFFB52E.toInt()
            c.drawCircle(fx, fy, (1.0f + (i % 2) * 0.45f) * s, paint)
        }
    }

    private fun drawLegendaryControl(c: Canvas) {
        if (legendaryTier() <= 0) return
        val left = 48f; val top = wh - 32f; val right = 108f; val bottom = wh - 7f
        val ready = legendaryCooldown <= 0f && legendaryTime <= 0f
        val accent = if (ready) 0xFFFFD45C.toInt() else 0xFF677786.toInt()
        paint.style = Paint.Style.FILL; paint.color = 0x4435D9F2.toInt()
        c.drawRoundRect(left*s, top*s, right*s, bottom*s, 4f*s, 4f*s, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.1f*s; paint.color = accent
        c.drawRoundRect(left*s, top*s, right*s, bottom*s, 4f*s, 4f*s, paint)
        paint.style = Paint.Style.FILL
        val glyph = when (currentShip) { 0 -> "overdrive"; 1 -> "dash"; 2 -> "shield"; 3 -> "stasis"; else -> "phoenix" }
        drawAbilityIcon(c, glyph, (left + right) / 2f, wh - 19.5f)
        if (!ready && legendaryTime <= 0f) text(c, (legendaryCooldown.toInt() + 1).toString(), right - 7f, wh - 9f, 3.8f, accent)
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

    private fun drawGlassPanel(c: Canvas, left: Float, top: Float, right: Float, bottom: Float, accent: Int, alpha: Int = 218) {
        val rect = RectF(left*s, top*s, right*s, bottom*s)
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(alpha, 5, 13, 27)
        c.drawRoundRect(rect, 8f*s, 8f*s, paint)
        paint.color = Color.argb(24, 255, 255, 255)
        c.drawRoundRect(RectF(rect.left + 1f*s, rect.top + 1f*s, rect.right - 1f*s, rect.top + 8f*s), 6f*s, 6f*s, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f*s; paint.color = Color.argb(58, Color.red(accent), Color.green(accent), Color.blue(accent))
        c.drawRoundRect(rect, 8f*s, 8f*s, paint)
        paint.strokeWidth = 1.05f*s; paint.color = accent
        c.drawRoundRect(rect, 8f*s, 8f*s, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawAdSlot(c: Canvas, centerY: Float, width: Float = 150f, height: Float = 23f) {
        val left = (W - width) * 0.5f
        val top = centerY - height * 0.5f
        drawGlassPanel(c, left, top, left + width, top + height, 0x5577CFE8, 92)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 0.65f*s; paint.color = 0x5577CFE8
        c.drawRoundRect(left*s, top*s, (left+width)*s, (top+height)*s, 4f*s, 4f*s, paint)
        paint.style = Paint.Style.FILL
    }

    private fun upgradeTitle(id: String): String = when (id) {
        "firepower" -> "قدرت آتش"
        "missile" -> "موشک‌ها"
        "laser" -> "لیزر"
        "drone" -> "پهپادها"
        "magnet" -> "آهنربا"
        "shield" -> "سپر بازتابی"
        "dash" -> "جهش اضطراری"
        "chain" -> "صاعقه زنجیره‌ای"
        "hull" -> "جان +۱"
        "health_regen" -> "بازیابی جان"
        "emp" -> "موج الکترومغناطیسی"
        "ricochet" -> "گلوله کمانه‌ای"
        "gravity_well" -> "چاه گرانشی"
        "overload" -> "اضافه‌بار سلاح"
        "nanobots" -> "نانوماشین‌ها"
        "scanner" -> "ردیاب نقطه ضعف"
        "salvage_drone" -> "پهپاد جمع‌آوری"
        "kill_streak" -> "زنجیره نابودی"
        "ricochet_shield" -> "سپر کمانه‌ای"
        "armor" -> "زره نانو"
        "critical" -> "ضربه حیاتی"
        "overdrive" -> "فراشتاب"
        "explosive" -> "گلوله انفجاری"
        "piercing" -> "گلوله نفوذی"
        "stasis" -> "میدان سکون"
        "scavenger" -> "غارتگر"
        "multishot" -> "شلیک چندگانه"
        "phoenix" -> "هسته ققنوس"
        else -> "شلیک سریع"
    }

    private fun upgradeInfo(id: String): String = when (id) {
        "firepower" -> "آسیب و سرعت شلیک بیشتر"
        "missile" -> "موشک‌های هدف‌گیر بیشتر"
        "laser" -> "لیزر قدرتمندتر"
        "drone" -> "چرخش و رهگیری گلوله‌ها"
        "magnet" -> "جذب خودکار آیتم‌ها"
        "shield" -> "بازتاب گلوله‌های دشمن"
        "dash" -> "یک جهش ایمن در میدان"
        "chain" -> "انتقال صاعقه به دشمن نزدیک"
        "hull" -> "افزایش یک واحد جان"
        "health_regen" -> "ترمیم تدریجی بدنه سفینه"
        "emp" -> "آسیب موجی به همه دشمن‌ها"
        "ricochet" -> "گلوله‌ها به هدف تازه کمانه می‌کنند"
        "gravity_well" -> "کشیدن و آسیب به دشمنان نزدیک"
        "overload" -> "افزایش موقت آسیب سلاح"
        "nanobots" -> "ترمیم تدریجی جان و سپر"
        "scanner" -> "نشانه‌گذاری برای آسیب بیشتر"
        "salvage_drone" -> "پهپاد سکه بیشتری جمع می‌کند"
        "kill_streak" -> "نابودی پیاپی امتیاز اضافه می‌دهد"
        "ricochet_shield" -> "احتمال بازتاب ضربه ورودی"
        "armor" -> "احتمال خنثی‌کردن ضربه"
        "critical" -> "احتمال واردکردن آسیب اضافه"
        "overdrive" -> "افزایش دائمی سرعت شلیک"
        "explosive" -> "آسیب به دشمنان اطراف"
        "piercing" -> "عبور گلوله از چند هدف"
        "stasis" -> "کندکردن همه دشمن‌ها"
        "scavenger" -> "سکه بیشتر با هر نابودی"
        "multishot" -> "افزودن مسیرهای شلیک جانبی"
        "phoenix" -> "یک بار احیای خودکار"
        else -> "شلیک سریع برای مدت محدود"
    }

    private fun abilityColor(id: String): Int = when (id) {
        "drone", "laser" -> 0xFF38BDF8.toInt()
        "missile" -> 0xFFFF5B4D.toInt()
        "firepower" -> 0xFFFFB020.toInt()
        "emp" -> 0xFF67E8F9.toInt()
        "ricochet", "scanner" -> 0xFFFF668F.toInt()
        "gravity_well" -> 0xFFB18CFF.toInt()
        "overload" -> 0xFFFFB347.toInt()
        "nanobots" -> 0xFF63F5C5.toInt()
        "salvage_drone", "kill_streak" -> 0xFFFFD66B.toInt()
        "ricochet_shield" -> 0xFF65DFFF.toInt()
        "magnet" -> 0xFF3B82F6.toInt()
        "shield" -> 0xFF22D3EE.toInt()
        "dash" -> 0xFFFFD54A.toInt()
        "chain" -> 0xFFA78BFA.toInt()
        "hull", "health_regen", "phoenix" -> 0xFF4ADE80.toInt()
        "armor", "stasis" -> 0xFF22D3EE.toInt()
        "critical", "explosive" -> 0xFFFF5B4D.toInt()
        "overdrive" -> 0xFFFFB020.toInt()
        "piercing", "multishot" -> 0xFFA78BFA.toInt()
        "scavenger" -> 0xFFFFD45C.toInt()
        else -> 0xFFB8E7FF.toInt()
    }

    private fun drawAbilityIcon(c: Canvas, id: String, cx: Float, cy: Float) {
        val color = abilityColor(id)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.35f * s; paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND; paint.color = color
        val ox = cx * s; val oy = cy * s
        when (id) {
            "drone" -> { c.drawCircle(ox - 7f*s, oy, 3.2f*s, paint); c.drawCircle(ox + 7f*s, oy, 3.2f*s, paint); c.drawLine(ox-3f*s,oy,ox+3f*s,oy,paint); c.drawCircle(ox,oy-1f*s,2f*s,paint) }
            "missile" -> { val p=Path().apply { moveTo(ox,oy-9f*s); lineTo(ox+4f*s,oy+4f*s); lineTo(ox,oy+2f*s); lineTo(ox-4f*s,oy+4f*s); close() }; c.drawPath(p,paint); c.drawLine(ox-3f*s,oy+5f*s,ox-5f*s,oy+8f*s,paint); c.drawLine(ox+3f*s,oy+5f*s,ox+5f*s,oy+8f*s,paint) }
            "laser" -> { c.drawLine(ox,oy-9f*s,ox,oy+8f*s,paint); c.drawLine(ox-5f*s,oy-5f*s,ox+5f*s,oy-5f*s,paint); c.drawLine(ox-5f*s,oy+5f*s,ox+5f*s,oy+5f*s,paint) }
            "firepower" -> for (dx in floatArrayOf(-5f,0f,5f)) { c.drawLine((cx+dx)*s,(cy+6f)*s,(cx+dx)*s,(cy-5f)*s,paint); c.drawLine((cx+dx)*s,(cy-5f)*s,(cx+dx-2f)*s,(cy-2f)*s,paint); c.drawLine((cx+dx)*s,(cy-5f)*s,(cx+dx+2f)*s,(cy-2f)*s,paint) }
            "magnet" -> {
                val p=Path().apply { moveTo(ox-7f*s,oy-6f*s); lineTo(ox-7f*s,oy+2f*s); quadTo(ox,oy+12f*s,ox+7f*s,oy+2f*s); lineTo(ox+7f*s,oy-6f*s) }
                paint.color = 0xFFE6EDF5.toInt(); c.drawPath(p,paint)
                paint.color = 0xFF3B82F6.toInt(); c.drawLine(ox-7f*s,oy-6f*s,ox-7f*s,oy+1f*s,paint); c.drawLine(ox-7f*s,oy-6f*s,ox-3f*s,oy-6f*s,paint)
                paint.color = 0xFFEF4444.toInt(); c.drawLine(ox+7f*s,oy-6f*s,ox+7f*s,oy+1f*s,paint); c.drawLine(ox+3f*s,oy-6f*s,ox+7f*s,oy-6f*s,paint)
            }
            "shield" -> { val p=Path().apply { moveTo(ox,oy-9f*s); lineTo(ox+7f*s,oy-5f*s); lineTo(ox+5f*s,oy+4f*s); lineTo(ox,oy+9f*s); lineTo(ox-5f*s,oy+4f*s); lineTo(ox-7f*s,oy-5f*s); close() }; c.drawPath(p,paint) }
            "dash" -> { c.drawLine(ox-8f*s,oy+5f*s,ox+5f*s,oy-5f*s,paint); c.drawLine(ox+5f*s,oy-5f*s,ox+1f*s,oy-5f*s,paint); c.drawLine(ox+5f*s,oy-5f*s,ox+4f*s,oy-1f*s,paint) }
            "chain" -> { c.drawOval(ox-9f*s,oy-5f*s,ox+1f*s,oy+5f*s,paint); c.drawOval(ox-1f*s,oy-5f*s,ox+9f*s,oy+5f*s,paint) }
            "hull", "health_regen", "phoenix" -> { c.drawLine(ox-7f*s,oy,ox+7f*s,oy,paint); c.drawLine(ox,oy-7f*s,ox,oy+7f*s,paint); c.drawCircle(ox,oy,9f*s,paint) }
            "emp" -> { c.drawCircle(ox,oy,3f*s,paint); c.drawCircle(ox,oy,7f*s,paint); c.drawCircle(ox,oy,11f*s,paint) }
            "ricochet" -> { c.drawLine(ox-9f*s,oy+5f*s,ox+6f*s,oy-5f*s,paint); c.drawLine(ox+6f*s,oy-5f*s,ox+2f*s,oy-5f*s,paint); c.drawLine(ox+6f*s,oy-5f*s,ox+6f*s,oy-1f*s,paint) }
            "gravity_well" -> { c.drawCircle(ox,oy,4f*s,paint); c.drawCircle(ox,oy,9f*s,paint); c.drawArc(RectF(ox-12f*s,oy-7f*s,ox+12f*s,oy+7f*s),20f,260f,false,paint) }
            "overload" -> { c.drawLine(ox+2f*s,oy-10f*s,ox-4f*s,oy+1f*s,paint); c.drawLine(ox-4f*s,oy+1f*s,ox+3f*s,oy+1f*s,paint); c.drawLine(ox+3f*s,oy+1f*s,ox-2f*s,oy+10f*s,paint) }
            "nanobots", "salvage_drone" -> { c.drawCircle(ox,oy,3f*s,paint); c.drawCircle(ox-8f*s,oy-4f*s,2f*s,paint); c.drawCircle(ox+8f*s,oy+4f*s,2f*s,paint) }
            "scanner" -> { c.drawCircle(ox,oy,8f*s,paint); c.drawLine(ox-11f*s,oy,ox-5f*s,oy,paint); c.drawLine(ox+5f*s,oy,ox+11f*s,oy,paint) }
            "kill_streak" -> { c.drawLine(ox-9f*s,oy+7f*s,ox-2f*s,oy,paint); c.drawLine(ox-2f*s,oy,ox+3f*s,oy+3f*s,paint); c.drawLine(ox+3f*s,oy+3f*s,ox+10f*s,oy-8f*s,paint) }
            "ricochet_shield" -> { c.drawCircle(ox,oy,9f*s,paint); c.drawArc(RectF(ox-12f*s,oy-12f*s,ox+12f*s,oy+12f*s),-35f,250f,false,paint) }
            "armor", "stasis" -> { c.drawCircle(ox,oy,8f*s,paint); c.drawLine(ox-6f*s,oy,ox+6f*s,oy,paint); c.drawLine(ox,oy-6f*s,ox,oy+6f*s,paint) }
            "critical" -> { c.drawCircle(ox,oy,7f*s,paint); c.drawLine(ox-9f*s,oy,ox-3f*s,oy,paint); c.drawLine(ox+3f*s,oy,ox+9f*s,oy,paint); c.drawLine(ox,oy-9f*s,ox,oy-3f*s,paint); c.drawLine(ox,oy+3f*s,ox,oy+9f*s,paint) }
            "overdrive", "explosive" -> { c.drawCircle(ox,oy,3f*s,paint); c.drawLine(ox,oy-10f*s,ox,oy-5f*s,paint); c.drawLine(ox+10f*s,oy,ox+5f*s,oy,paint); c.drawLine(ox,oy+10f*s,ox,oy+5f*s,paint); c.drawLine(ox-10f*s,oy,ox-5f*s,oy,paint) }
            "piercing" -> { c.drawLine(ox-8f*s,oy+5f*s,ox+7f*s,oy-5f*s,paint); c.drawLine(ox+7f*s,oy-5f*s,ox+3f*s,oy-5f*s,paint); c.drawLine(ox+7f*s,oy-5f*s,ox+6f*s,oy-1f*s,paint) }
            "scavenger" -> { c.drawCircle(ox,oy,7f*s,paint); c.drawLine(ox-3f*s,oy,ox+3f*s,oy,paint); c.drawLine(ox,oy-4f*s,ox,oy+4f*s,paint) }
            "multishot" -> { for (dx in floatArrayOf(-6f,0f,6f)) c.drawLine((cx+dx)*s,(cy+6f)*s,(cx+dx)*s,(cy-7f)*s,paint) }
            else -> { val p=Path().apply { moveTo(ox+1f*s,oy-9f*s); lineTo(ox-5f*s,oy+1f*s); lineTo(ox-1f*s,oy+1f*s); lineTo(ox-3f*s,oy+8f*s); lineTo(ox+6f*s,oy-3f*s); lineTo(ox+1f*s,oy-3f*s); close() }; c.drawPath(p,paint) }
        }
        paint.style = Paint.Style.FILL
    }

    private fun drawFittedText(c: Canvas, value: String, x: Float, y: Float, size: Float, color: Int, maxWidth: Float) {
        tp.textSize = size * s
        var fitted = size
        while (tp.measureText(value) > maxWidth * s && fitted > 2.8f) {
            fitted -= 0.2f
            tp.textSize = fitted * s
        }
        text(c, value, x, y, fitted, color)
    }

    private fun drawWrappedCardText(c: Canvas, value: String, x: Float, y: Float, size: Float, color: Int, maxWidth: Float) {
        tp.textSize = size * s
        val words = value.split(' ')
        val lines = ArrayList<String>()
        var line = ""
        for (word in words) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (line.isNotEmpty() && tp.measureText(candidate) > maxWidth * s) {
                lines.add(line); line = word
            } else line = candidate
        }
        if (line.isNotEmpty()) lines.add(line)
        lines.take(2).forEachIndexed { index, part -> drawFittedText(c, part, x, y + index * 5.0f, size, color, maxWidth) }
    }

    private fun drawUpgrade(c: Canvas) {
        paint.color = 0xB807101D.toInt(); c.drawRect(0f, 0f, sw.toFloat(), sh.toFloat(), paint)
        drawGlassPanel(c, 5f, wh * 0.30f, W - 5f, wh * 0.76f, 0xFF47DFFF.toInt(), 232)
        text(c, if (extraAbilitiesUnlocked) "یکی را انتخاب کن • " + selectedAbilities.size + " بازشده" else "یکی را انتخاب کن • " + selectedAbilities.size + "/۵ جایگاه", W / 2f, wh * 0.37f, 11f, 0xFF9CEBFF.toInt())
        text(c, if (extraAbilitiesUnlocked) "توانایی‌های تازه باز شدند" else "تا مرحله ۵۰ حداکثر ۵ توانایی", W / 2f, wh * 0.405f, 6f, 0xFFFFD45C.toInt())
        for (i in 0..2) {
            val id = upgradeChoices.getOrNull(i) ?: continue
            val cx = 36f + i * 74f
            paint.color = abilityColor(id)
            drawGlassPanel(c, cx - 30f, wh * 0.43f, cx + 30f, wh * 0.60f, abilityColor(id), 190)
            paint.style = Paint.Style.FILL
            drawAbilityIcon(c, id, cx, wh * 0.462f)
            val rank = abilityRank(id)
            drawFittedText(c, upgradeTitle(id) + "  " + (rank + 1) + "/۵", cx, wh * 0.515f, 5.8f, WHT, 54f)
            drawWrappedCardText(c, upgradeInfo(id), cx, wh * 0.548f, 4.1f, 0xFF9CEBFF.toInt(), 52f)
        }
        drawButtons(c)
        drawAdSlot(c, wh * 0.84f)
    }

    private fun drawShop(c: Canvas) {
        paint.color = 0xB807101D.toInt(); c.drawRect(0f, 0f, sw.toFloat(), sh.toFloat(), paint)
        drawGlassPanel(c, 25f, wh * 0.28f, 195f, wh * 0.84f, 0xFFFFD45C.toInt(), 232)
        text(c, "آشیانه بین مراحل", W / 2f, wh * 0.34f, 12f, 0xFFFFD45C.toInt())
        text(c, "سکه: " + credits, W / 2f, wh * 0.40f, 9f, WHT)
        drawButtons(c)
        drawAdSlot(c, wh * 0.78f, 138f, 20f)
    }

    private fun drawPause(c: Canvas) {
        paint.style = Paint.Style.FILL
        paint.color = 0xFF07101D.toInt()
        c.drawRect(0f, 0f, sw.toFloat(), sh.toFloat(), paint)
        // High-opacity glass card hides ships directly behind the pause controls.
        drawGlassPanel(c, 18f, wh * 0.29f, 202f, wh * 0.91f, 0xFF47DFFF.toInt(), 248)
        text(c, "بازی متوقف شد", W / 2f, wh * 0.345f, 17f, 0xFF9CEBFF.toInt())
        drawButtons(c)
        text(c, "قابلیت‌های فعال", W / 2f, wh * 0.645f, 8f, 0xFFFFD45C.toInt())
        if (selectedAbilities.isEmpty()) {
            text(c, "هنوز قابلیتی انتخاب نشده", W / 2f, wh * 0.70f, 6f, 0xFF9BAABC.toInt())
        } else {
            val list = selectedAbilities.toList()
            val cols = 4
            val cellW = 49f
            val left = 12f
            list.take(28).forEachIndexed { index, id ->
                val col = index % cols
                val row = index / cols
                val cellX = left + col * cellW
                val cellY = wh * 0.685f + row * 10.1f
                val rank = abilityRank(id).coerceIn(0, 5)
                paint.style = Paint.Style.FILL
                paint.color = 0x332BDAEF.toInt()
                c.drawRoundRect(cellX*s, (cellY-5f)*s, (cellX+cellW-2f)*s, (cellY+4.2f)*s, 2.5f*s, 2.5f*s, paint)
                drawAbilityIcon(c, id, cellX + 7f, cellY - 0.3f)
                drawFittedText(c, upgradeTitle(id) + " " + rank + "/۵", cellX + 29f, cellY + 1.7f, 4.2f, if(rank >= 5) 0xFFFFD45C.toInt() else 0xFFE7F8FF.toInt(), 36f)
            }
        }
        drawAdSlot(c, wh * 0.93f, 132f, 14f)
    }

    private fun drawWeaponControls(c: Canvas) {
        fun control(iconId: String, left: Float, right: Float, ready: Boolean, counter: String = "") {
            val top = wh - 32f
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 0.9f * s
            paint.color = if (ready) 0xFF55DFFF.toInt() else 0xFF66717E.toInt()
            c.drawRoundRect(left * s, top * s, right * s, (wh - 7f) * s, 4f * s, 4f * s, paint)
            paint.style = Paint.Style.FILL
            drawAbilityIcon(c, iconId, (left + right) / 2f, wh - 19.5f)
            if (counter.isNotEmpty()) text(c, counter, right - 5f, wh - 9f, 3.8f, if (ready) 0xFF8BEFFF.toInt() else 0xFF86919D.toInt())
        }
        if (dashCharges > 0) control("dash", 8f, 43f, dashCooldown <= 0f, dashCharges.toString())
        control("missile", W - 94f, W - 53f, missileCooldown <= 0f)
        control("laser", W - 49f, W - 8f, laserCooldown <= 0f)
    }

    private fun drawButtonIcon(c: Canvas, id: String, cx: Float, cy: Float) {
        val color = when { id.startsWith("shop_") -> 0xFFFFD45C.toInt(); id.startsWith("upgrade_") -> 0xFF55DFFF.toInt(); id == "about" || id == "telegram" -> 0xFF55DFFF.toInt(); else -> 0xFF9CEBFF.toInt() }
        paint.style = Paint.Style.STROKE; paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
        paint.strokeWidth = 1.8f * s; paint.color = color
        val x = cx * s; val y = cy * s
        if (id.startsWith("upgrade_")) {
            c.drawLine(x, y - 8f * s, x, y + 3f * s, paint)
            c.drawLine(x, y + 3f * s, x - 4.5f * s, y - 1.5f * s, paint)
            c.drawLine(x, y + 3f * s, x + 4.5f * s, y - 1.5f * s, paint)
            c.drawLine(x - 8f * s, y + 7f * s, x + 8f * s, y + 7f * s, paint)
            paint.style = Paint.Style.FILL
            return
        }
        if (id.startsWith("shipup_")) {
            drawAbilityIcon(c, when(id.substringAfter("_")) { "attack" -> "firepower"; "armor" -> "shield"; "mobility" -> "dash"; "hull" -> "hull"; else -> "phoenix" }, cx, cy)
            return
        }
        if (id.startsWith("ship_")) {
            val shipId = id.substringAfterLast("_").toIntOrNull() ?: 0
            drawPlayerShip(c, cx, cy, shipId)
            return
        }
        when (id) {
            "play", "resume", "shop_continue" -> { val p=Path().apply { moveTo(x-6f*s,y-9f*s); lineTo(x+9f*s,y); lineTo(x-6f*s,y+9f*s); close() }; paint.style=Paint.Style.FILL; c.drawPath(p,paint) }
            "sound" -> { val p=Path().apply { moveTo(x-10f*s,y-4f*s); lineTo(x-5f*s,y-4f*s); lineTo(x+1f*s,y-9f*s); lineTo(x+1f*s,y+9f*s); lineTo(x-5f*s,y+4f*s); lineTo(x-10f*s,y+4f*s); close() }; paint.style=Paint.Style.FILL; c.drawPath(p,paint); paint.style=Paint.Style.STROKE; c.drawArc(x-4f*s,y-9f*s,x+13f*s,y+9f*s,-60f,120f,false,paint); c.drawArc(x-7f*s,y-13f*s,x+18f*s,y+13f*s,-60f,120f,false,paint) }
            "ship" -> { val p=Path().apply { moveTo(x,y-11f*s); lineTo(x+8f*s,y+9f*s); lineTo(x,y+5f*s); lineTo(x-8f*s,y+9f*s); close() }; paint.style=Paint.Style.FILL; c.drawPath(p,paint); paint.style=Paint.Style.STROKE; c.drawLine(x-11f*s,y+3f*s,x-6f*s,y+5f*s,paint); c.drawLine(x+11f*s,y+3f*s,x+6f*s,y+5f*s,paint) }
            "about" -> { c.drawCircle(x,y,9f*s,paint); paint.style=Paint.Style.FILL; c.drawCircle(x,y-5f*s,1.4f*s,paint); c.drawRoundRect(x-1.5f*s,y-1f*s,x+1.5f*s,y+6f*s,1f*s,1f*s,paint) }
            "aboutBack", "menu" -> { if(id=="menu") { for(row in -1..1) for(col in -1..1) c.drawCircle(x+col*6f*s,y+row*6f*s,1.9f*s,paint) } else { c.drawLine(x+9f*s,y,x-8f*s,y,paint); c.drawLine(x-8f*s,y,x-1f*s,y-7f*s,paint); c.drawLine(x-8f*s,y,x-1f*s,y+7f*s,paint) } }
            "pauseRestart" -> { c.drawArc(x-9f*s,y-9f*s,x+9f*s,y+9f*s,35f,285f,false,paint); c.drawLine(x+7f*s,y-8f*s,x+10f*s,y-1f*s,paint); c.drawLine(x+7f*s,y-8f*s,x,y-8f*s,paint) }
            "telegram" -> { val p=Path().apply { moveTo(x-11f*s,y-2f*s); lineTo(x+11f*s,y-8f*s); lineTo(x+4f*s,y+10f*s); lineTo(x,y+3f*s); close() }; paint.style=Paint.Style.FILL; c.drawPath(p,paint); paint.style=Paint.Style.STROKE; c.drawLine(x,y+3f*s,x+7f*s,y-4f*s,paint) }
            "extraLife", "shop_repair" -> { val p=Path().apply { moveTo(x,y+9f*s); cubicTo(x-17f*s,y-2f*s,x-8f*s,y-13f*s,x,y-6f*s); cubicTo(x+8f*s,y-13f*s,x+17f*s,y-2f*s,x,y+9f*s) }; c.drawPath(p,paint) }
            "shop_weapon" -> { c.drawLine(x-8f*s,y+8f*s,x+8f*s,y-8f*s,paint); c.drawLine(x+4f*s,y-8f*s,x+8f*s,y-8f*s,paint); c.drawLine(x+8f*s,y-8f*s,x+8f*s,y-4f*s,paint) }
            "shop_shield" -> { val p=Path().apply { moveTo(x,y-10f*s); lineTo(x+9f*s,y-6f*s); lineTo(x+7f*s,y+4f*s); lineTo(x,y+11f*s); lineTo(x-7f*s,y+4f*s); lineTo(x-9f*s,y-6f*s); close() }; c.drawPath(p,paint) }
            "shop_dash" -> { c.drawLine(x-9f*s,y+8f*s,x+8f*s,y-8f*s,paint); c.drawLine(x+8f*s,y-8f*s,x+2f*s,y-8f*s,paint); c.drawLine(x+8f*s,y-8f*s,x+8f*s,y-2f*s,paint) }
            else -> { drawAbilityIcon(c, "firepower", cx, cy); return }
        }
        paint.style = Paint.Style.FILL
    }

    private fun drawButtons(c: Canvas) {
        for (b in buttons()) {
            val pressed = b.id == pressedButtonId && buttonPulse > 0f
            val col = when {
                b.id.startsWith("upgrade_") || b.id == "play" -> 0xFF55DFFF.toInt()
                b.id.startsWith("shop_") -> 0xFFFFD45C.toInt()
                b.id == "ship" -> 0xFFB68CFF.toInt()
                b.id == "telegram" -> 0xFF38A9F5.toInt()
                else -> 0xFF9CEBFF.toInt()
            }
            val r = if (pressed) RectF(b.r.left + 1.2f, b.r.top + 1.2f, b.r.right - 1.2f, b.r.bottom - 1.2f) else b.r
            paint.style = Paint.Style.FILL
            paint.color = if (pressed) 0xAAFFFFFF.toInt() else if (b.id.startsWith("shop_")) 0x44FFB020.toInt() else 0x4435D9F2.toInt()
            c.drawRoundRect(r.left*s,r.top*s,r.right*s,r.bottom*s,5f*s,5f*s,paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 3.2f * s; paint.color = Color.argb(52, Color.red(col), Color.green(col), Color.blue(col))
            c.drawRoundRect(r.left*s,r.top*s,r.right*s,r.bottom*s,5f*s,5f*s,paint)
            paint.strokeWidth = (if (pressed) 1.9f else 1.2f) * s; paint.color = col
            c.drawRoundRect(r.left*s,r.top*s,r.right*s,r.bottom*s,5f*s,5f*s,paint)
            if (st == St.MENU) {
                val cy = (r.top + r.bottom) / 2f
                drawButtonIcon(c, b.id, r.left + 18f, cy)
                drawFittedText(c, b.label, (r.left + r.right) / 2f + 10f, cy + 2.1f, 6.1f, 0xFFEAFBFF.toInt(), 82f)
            } else if ((st == St.SHIPS || st == St.SHIP_UPGRADE) && (b.id.startsWith("ship_") || b.id.startsWith("shipup_"))) {
                val cy = (r.top + r.bottom) / 2f
                drawButtonIcon(c, b.id, r.left + 18f, cy)
                drawFittedText(c, b.label, (r.left + r.right) / 2f + 13f, cy + 2f, 4.8f, 0xFFEAFBFF.toInt(), 142f)
            } else drawButtonIcon(c, b.id, (r.left+r.right)/2f, r.top + 11f)
        }
    }

    private fun drawPlayerShip(c: Canvas, x: Float, y: Float, shipId: Int = currentShip) {
        val colors = intArrayOf(0xFFFFD34F.toInt(), 0xFF45E6FF.toInt(), 0xFFFF8758.toInt(), 0xFFB78CFF.toInt(), 0xFFFF6EB5.toInt())
        val glows = intArrayOf(0xFFFFFFA8.toInt(), 0xFFB8F7FF.toInt(), 0xFFFFD0A6.toInt(), 0xFFE3D2FF.toInt(), 0xFFFFC3E4.toInt())
        val id = shipId.coerceIn(0, 4)
        val color = colors[id]
        c.save()
        c.translate(x * s, y * s)
        c.scale(s, s)
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(65, Color.red(color), Color.green(color), Color.blue(color))
        c.drawCircle(0f, 1f, 17f, paint)
        val body = Path()
        when (id) {
            0 -> {
                body.moveTo(0f,-17f); body.lineTo(7f,7f); body.lineTo(0f,3f); body.lineTo(-7f,7f); body.close()
                val wings = Path().apply { moveTo(-5f,1f); lineTo(-17f,10f); lineTo(-8f,9f); lineTo(0f,5f); lineTo(8f,9f); lineTo(17f,10f); lineTo(5f,1f); close() }
                paint.color = color; c.drawPath(wings, paint)
            }
            1 -> {
                body.moveTo(0f,-19f); body.cubicTo(9f,-5f,5f,8f,0f,12f); body.cubicTo(-5f,8f,-9f,-5f,0f,-19f); body.close()
                val fins = Path().apply { moveTo(-4f,0f); lineTo(-18f,13f); lineTo(-7f,10f); lineTo(0f,6f); lineTo(7f,10f); lineTo(18f,13f); lineTo(4f,0f); close() }
                paint.color = color; c.drawPath(fins, paint)
            }
            2 -> {
                body.moveTo(-6f,-15f); body.lineTo(6f,-15f); body.lineTo(11f,7f); body.lineTo(0f,12f); body.lineTo(-11f,7f); body.close()
                val armor = Path().apply { moveTo(-7f,-1f); lineTo(-20f,8f); lineTo(-15f,14f); lineTo(-3f,8f); lineTo(3f,8f); lineTo(15f,14f); lineTo(20f,8f); lineTo(7f,-1f); close() }
                paint.color = color; c.drawPath(armor, paint)
            }
            3 -> {
                body.moveTo(0f,-19f); body.lineTo(5f,8f); body.lineTo(0f,5f); body.lineTo(-5f,8f); body.close()
                val ghost = Path().apply { moveTo(-4f,-2f); lineTo(-17f,15f); lineTo(-12f,5f); lineTo(-4f,3f); lineTo(4f,3f); lineTo(12f,5f); lineTo(17f,15f); lineTo(4f,-2f); close() }
                paint.color = color; c.drawPath(ghost, paint)
            }
            else -> {
                body.moveTo(0f,-20f); body.cubicTo(13f,-4f,9f,10f,0f,13f); body.cubicTo(-9f,10f,-13f,-4f,0f,-20f); body.close()
                val flames = Path().apply { moveTo(-4f,2f); lineTo(-20f,15f); lineTo(-12f,3f); lineTo(-5f,6f); lineTo(0f,2f); lineTo(5f,6f); lineTo(12f,3f); lineTo(20f,15f); lineTo(4f,2f); close() }
                paint.color = color; c.drawPath(flames, paint)
            }
        }
        paint.color = color
        c.drawPath(body, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f
        paint.color = glows[id]
        c.drawPath(body, paint)
        paint.style = Paint.Style.FILL
        paint.color = glows[id]
        c.drawRoundRect(-2.1f,-8f,2.1f,3f,2f,2f,paint)
        paint.color = if (id == 2) 0xFFFF9B42.toInt() else 0xFFFFE08A.toInt()
        val flame = Path().apply { moveTo(-3f,8f); lineTo(0f,18f + sin(time * 18f) * 2f); lineTo(3f,8f); close() }
        c.drawPath(flame, paint)
        paint.style = Paint.Style.FILL
        c.restore()
    }

    private fun drawShipHangar(c: Canvas) {
        paint.color = 0xE807101D.toInt()
        c.drawRect(0f,0f,sw.toFloat(),sh.toFloat(),paint)
        drawGlassPanel(c,5f,wh*0.27f,W-5f,wh*0.86f,0xFFFFD45C.toInt(),244)
        text(c,"آشیانه ناوگان",W/2f,wh*0.32f,12f,0xFFFFD45C.toInt())
        text(c,"سکه: $credits",W/2f,wh*0.35f,7f,WHT)
        for (i in 0..4) {
            val y = wh*0.38f + i*23f
            val owned = shipOwned(i)
            drawPlayerShip(c,25f,y+9f,i)
            text(c,shipName(i),48f,y+12f,5.4f,if(currentShip==i) 0xFFFFD45C.toInt() else WHT,Paint.Align.LEFT)
            text(c,when(i){0->"متوازن";1->"سرعت بالا";2->"زره سنگین";3->"شلیک تند";else->"آتش ققنوس"},48f,y+18f,3.8f,0xFF9CEBFF.toInt(),Paint.Align.LEFT)
            if (!owned) text(c,shipPrice(i).toString()+" سکه",190f,y+12f,4.5f,0xFFFFD45C.toInt(),Paint.Align.RIGHT)
            else if (currentShip==i) text(c,"فعال",190f,y+12f,4.5f,0xFF55DDAA.toInt(),Paint.Align.RIGHT)
        }
        drawButtons(c)
    }

    private fun drawShipUpgrades(c: Canvas) {
        paint.color = 0xE807101D.toInt()
        c.drawRect(0f,0f,sw.toFloat(),sh.toFloat(),paint)
        drawGlassPanel(c,5f,wh*0.27f,W-5f,wh*0.86f,0xFF47DFFF.toInt(),244)
        text(c,"کارگاه ارتقای سفینه",W/2f,wh*0.32f,11f,0xFF9CEBFF.toInt())
        text(c,shipName()+"  •  امتیاز ارتقا: $upgradePoints",W/2f,wh*0.35f,6.1f,0xFFFFD45C.toInt())
        val stats = listOf("attack" to "قدرت سلاح", "armor" to "زره و دفع ضربه", "mobility" to "سرعت مانور", "hull" to "جان شروع", "gold" to "توان طلایی")
        stats.forEachIndexed { i,pair ->
            val rank = shipUpgradeRank(pair.first)
            val y = wh*0.38f + i*23f
            text(c,pair.second,W/2f,y+8f,5.5f,WHT)
            text(c,"سطح " + rank + "/۵  •  هزینه " + (rank+1) + " امتیاز",W/2f,y+15f,4.1f,0xFF9CEBFF.toInt())
        }
        drawButtons(c)
    }

    private fun drawMenu(c: Canvas) {
        drawGlassPanel(c, 18f, wh * 0.35f, 202f, wh * 0.80f, 0xFF47DFFF.toInt(), 224)
        text(c, "نئون", W / 2, wh * 0.16f, 34f, YEL)
        text(c, "شوتر", W / 2, wh * 0.16f + 26f, 24f, RED)
        drawPlayerShip(c, W / 2, wh * 0.33f + sin(System.nanoTime() / 4e8f) * 3f)
        text(c, "سفینه: " + shipName() + "   سکه: " + credits, W / 2f, wh * 0.395f, 6.5f, 0xFF80E7FF.toInt())
        text(c, if (dailyDone) "ماموریت روزانه کامل شد: +۵۰۰" else "ماموریت روزانه: ۲۵ نابودی " + dailyKills + "/۲۵", W / 2f, wh * 0.417f, 6f, 0xFFFFD45C.toInt())
        text(c, "بهترین امتیاز: $best   رکورد مرحله: $bestWave", W / 2, wh * 0.44f, 7f, WHT)
        drawButtons(c)
        drawAdSlot(c, wh * 0.83f)
    }

    private fun drawAbout(c: Canvas) {
        paint.color = 0xFF07101D.toInt(); c.drawRect(0f, 0f, sw.toFloat(), sh.toFloat(), paint)
        drawGlassPanel(c, 17f, wh * 0.28f, 203f, wh * 0.80f, 0xFF47DFFF.toInt(), 244)
        text(c, "درباره نئون شوتر", W / 2f, wh * 0.36f, 16f, 0xFF9CEBFF.toInt())
        text(c, "در موج‌های بی‌پایان دشمنان دوام بیاور،", W / 2f, wh * 0.43f, 6.4f, WHT)
        text(c, "قدرت‌هایت را ارتقا بده و باس‌ها را شکست بده.", W / 2f, wh * 0.46f, 6.4f, WHT)
        text(c, "پنج سفینه با توانایی‌های طلایی متفاوت", W / 2f, wh * 0.49f, 6.4f, WHT)
        text(c, "و رویدادهای ویژه در انتظار تو هستند.", W / 2f, wh * 0.52f, 6.4f, WHT)
        text(c, "کانال رسمی بازی", W / 2f, wh * 0.57f, 6f, 0xFFFFD45C.toInt())
        drawButtons(c)
    }

    private fun drawOver(c: Canvas) {
        paint.color = 0xAA000000.toInt(); c.drawRect(0f, 0f, sw.toFloat(), sh.toFloat(), paint)
        drawGlassPanel(c, 25f, wh * 0.24f, 195f, wh * 0.75f, 0xFFFF6688.toInt(), 226)
        text(c, "پایان بازی", W / 2, wh * 0.3f, 22f, RED)
        text(c, "امتیاز $score", W / 2, wh * 0.3f + 20f, 11f, YEL)
        text(c, if (score >= best) "رکورد جدید!" else "بهترین: $best", W / 2, wh * 0.3f + 34f, 8f, WHT)
        if (adStatusTimer > 0f) text(c, adStatusText, W / 2f, wh * 0.45f, 7f, 0xFFFFD45C.toInt())
        if (overT >= 0.7f) drawButtons(c)
        drawAdSlot(c, wh * 0.84f)
    }

    private fun drawAdBreak(c: Canvas) {
        paint.style = Paint.Style.FILL
        paint.color = 0xCC07111D.toInt()
        c.drawRect(0f, 0f, sw.toFloat(), sh.toFloat(), paint)
        drawGlassPanel(c, 25f, wh * 0.42f, 195f, wh * 0.58f, 0xFF47DFFF.toInt(), 230)
        text(c, "مرحله کامل شد", W / 2f, wh * 0.48f, 10f, 0xFF9CEBFF.toInt())
        text(c, "آماده‌سازی مرحله بعد", W / 2f, wh * 0.53f, 6.5f, WHT)
    }
}

