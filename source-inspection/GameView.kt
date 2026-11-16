package com.claude.neonshooter

import android.app.Activity
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.TextUtils
import android.graphics.*
import android.content.Intent
import android.net.Uri
import android.media.MediaPlayer
import android.media.AudioAttributes
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.Surface
import android.os.Build
import android.view.SurfaceView
import android.util.Log
import kotlin.math.*
import kotlin.random.Random

class GameView(private val act: Activity) : SurfaceView(act), SurfaceHolder.Callback, Runnable {

    private enum class St { MENU, PLAY, PAUSE, OVER, UPGRADE, SHOP, AD_BREAK, ABOUT, SHIPS, SHIP_UPGRADE, AUDIO_SETTINGS }
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
        var phaseSummonTimer = 6.0f
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
    private val stageBackgrounds = intArrayOf(0xFF111318.toInt(),0xFF07152F.toInt(),0xFF180C2D.toInt(),0xFF062629.toInt(),0xFF2A0B1A.toInt())
    private val stageStarColors = intArrayOf(0xFFD0D4DA.toInt(),0xFFFF5964.toInt(),0xFFC18CFF.toInt(),0xFF39E6D0.toInt(),0xFFFFD166.toInt())
    private fun stagePaletteIndex(): Int = ((max(1,wave)-1)/25)%stageBackgrounds.size
    private fun stageBackgroundColor(): Int = stageBackgrounds[stagePaletteIndex()]
    private fun stageStarColor(): Int = stageStarColors[stagePaletteIndex()]
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
    private var musicVolume = prefs.getFloat("music_volume", 0.84f).coerceIn(0f, 1f)
    private var sfxVolume = prefs.getFloat("sfx_volume", 0.23f).coerceIn(0f, 1f)
    private var audioReturnState = St.MENU
    private var activeAudioSlider = 0
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
    private val bossConstellationNames = arrayOf("جبار", "دب‌اکبر", "عقرب", "اسد", "کمان", "ثور", "سگ بزرگ", "غراب", "مار آبی", "مار")
    private val bossConstellationColors = intArrayOf(
        0xFF8BE9FF.toInt(), 0xFFFFC76A.toInt(), 0xFFFF8A9D.toInt(), 0xFFC5A1FF.toInt(), 0xFF70E5C2.toInt(),
        0xFF83C8FF.toInt(), 0xFFFFD97A.toInt(), 0xFFFFA7D8.toInt(), 0xFF9DEBFF.toInt(), 0xFFB6F28D.toInt()
    )
    private val bossConstellationPoints: Array<Array<Pair<Float, Float>>> = arrayOf(
        arrayOf(-10f to -7f, -4f to -2f, 0f to 0f, 5f to -2f, 10f to -7f, -6f to 8f, 0f to 4f, 7f to 8f),
        arrayOf(-10f to -2f, -5f to -8f, 2f to -6f, 9f to -8f, 6f to 0f, 0f to 7f, -8f to 5f),
        arrayOf(-10f to -9f, -5f to -5f, 0f to -1f, 6f to 1f, 10f to 7f, 3f to 9f, -3f to 6f, -8f to 9f),
        arrayOf(-10f to 6f, -5f to -6f, 0f to 4f, 5f to -6f, 10f to 6f),
        arrayOf(-10f to -6f, -5f to -2f, 0f to -7f, 5f to -3f, 10f to 2f, 5f to 8f, -4f to 5f),
        arrayOf(-10f to -8f, -4f to -2f, 0f to 5f, 5f to -3f, 10f to -8f, 0f to -10f, 0f to 10f),
        arrayOf(-8f to -5f, -2f to -9f, 4f to -5f, 10f to -8f, 1f to 0f, 1f to 8f),
        arrayOf(-10f to -7f, 7f to -7f, 10f to 3f, -7f to 3f, 0f to -1f, 7f to 8f, -7f to 8f),
        arrayOf(-10f to -8f, -3f to -2f, 4f to 5f, 10f to 9f, -9f to 9f, -2f to 3f, 6f to -4f),
        arrayOf(-10f to 8f, -6f to 1f, -1f to -5f, 5f to -8f, 10f to -2f, 6f to 5f, 0f to 9f, -7f to 5f)
    )
    private val bossConstellationEdges: Array<Array<Pair<Int, Int>>> = arrayOf(
        arrayOf(0 to 1, 1 to 2, 2 to 3, 3 to 4, 2 to 6, 6 to 5, 6 to 7),
        arrayOf(0 to 1, 1 to 2, 2 to 3, 2 to 4, 4 to 5, 5 to 6, 6 to 0),
        arrayOf(0 to 1, 1 to 2, 2 to 3, 3 to 4, 4 to 5, 5 to 6, 6 to 7),
        arrayOf(0 to 1, 1 to 2, 2 to 3, 3 to 4),
        arrayOf(0 to 1, 1 to 2, 2 to 3, 3 to 4, 4 to 5, 5 to 6, 6 to 1),
        arrayOf(0 to 1, 1 to 2, 2 to 3, 3 to 4, 1 to 5, 3 to 6),
        arrayOf(0 to 1, 1 to 2, 2 to 3, 2 to 4, 4 to 5),
        arrayOf(0 to 1, 1 to 2, 2 to 3, 3 to 0, 0 to 4, 4 to 5, 4 to 6),
        arrayOf(0 to 1, 1 to 2, 2 to 3, 3 to 4, 4 to 5, 5 to 6),
        arrayOf(0 to 1, 1 to 2, 2 to 3, 3 to 4, 4 to 5, 5 to 6, 6 to 7, 7 to 0)
    )
    private val stageNames = arrayOf(
        "آندرومدا",
        "دلو",
        "عقاب",
        "بره",
        "جبار",
        "ارابه‌ران",
        "گاوران",
        "خرچنگ",
        "بزماهی",
        "دب‌اکبر",
        "نهنگ",
        "تاج شمالی",
        "جام",
        "دجاجه",
        "عقرب",
        "دلفین",
        "اژدها",
        "دوپیکر",
        "هرکول",
        "اسد",
        "ترازو",
        "گرگ",
        "شلیاق",
        "مارافسای",
        "کمان",
        "حوت",
        "برساوش",
        "اسب‌بالدار",
        "ققنوس",
        "ثور",
        "مثلث",
        "سنبله",
        "روباهک",
        "ذات‌الکرسی",
        "سگ بزرگ",
        "سگ کوچک",
        "قنطورس",
        "قیفاووس",
        "کبوتر",
        "غراب",
        "صلیب جنوبی",
        "ماهی زرین",
        "نهر",
        "درنا",
        "مار آبی",
        "هندی",
        "سوسمار",
        "تک‌شاخ",
        "هشتک",
        "مار"
    )
    private val stageDescriptions = arrayOf(
        "کهکشان آندرومدا، همسایه بزرگ راه شیری، در این صورت فلکی دیده می‌شود",
        "دلو، آب‌ریز آسمان و یکی از صورت‌های فلکی منطقةالبروج است",
        "عقاب میزبان ستاره درخشان نسر واقع است",
        "حَمَل یا بره، یکی از صورت‌های فلکی دایرةالبروج است",
        "جبار، شکارچی آسمان، با سه ستاره کمربندش شناخته می‌شود",
        "ارابه‌ران میزبان ستاره درخشان عَیوق است",
        "گاوران یا شبان، ستاره سرخ‌رنگ سماک رامح را در خود دارد",
        "خرچنگ از کم‌نورترین صورت‌های فلکی منطقةالبروج است",
        "بزماهی، نماد اسطوره‌ای بز دریایی در آسمان است",
        "دب‌اکبر با آرایش مشهور ملاقه بزرگ شناخته می‌شود",
        "نهنگ، صورت فلکی پهناوری در آسمان جنوبی است",
        "تاج شمالی، ردیفی خمیده از ستارگان شبیه تاج دارد",
        "جام، جام افسانه‌ای خدایان در اسطوره‌های یونانی است",
        "دجاجه یا قو، صلیب شمالی را در امتداد بدن خود دارد",
        "عقرب با ستاره سرخ قلب‌العقرب درخشان‌تر شناخته می‌شود",
        "دلفین، با چهار ستاره لوزی‌شکل کوچک خود پیدا می‌شود",
        "اژدها مانند رشته‌ای بلند، دور قطب شمال آسمان می‌پیچد",
        "دوپیکر با دو ستاره روشن کاستور و پولوکس شناخته می‌شود",
        "هرکول، پهلوان اسطوره‌ای، یکی از صورت‌های فلکی بزرگ شمالی است",
        "اسد یا شیر، ستاره درخشان قلب‌الاسد را در خود دارد",
        "ترازو نماد ترازوی عدالت در منطقةالبروج است",
        "گرگ میان صورت‌های فلکی قنطورس و عقرب قرار گرفته است",
        "شلیاق میزبان ستاره درخشان نسر واقع است",
        "مارافسای، مردی را نشان می‌دهد که ماری را در دست گرفته است",
        "کمان در جهت مرکز کهکشان راه شیری قرار دارد",
        "حوت، دو ماهی را نشان می‌دهد که با بندی به هم متصل‌اند",
        "برساوش، نام قهرمان اسطوره‌ای و خاستگاه بارش شهابی برساوشی است",
        "اسب بالدار با چهار ستاره بزرگ مربع پاییزی را می‌سازد",
        "ققنوس، پرنده افسانه‌ای و صورت فلکی آسمان جنوبی است",
        "ثور یا گاو، خوشه پروین و ستاره سرخ دَبَران را در خود جای داده است",
        "مثلث میزبان کهکشان مارپیچی مشهور M33 است",
        "سنبله، خوشه کهکشانی بزرگ سنبله را در پس‌زمینه خود دارد",
        "روباهک صورت فلکی کوچکی میان قو و تیر است",
        "ذات‌الکرسی با پنج ستاره شاخص، شکل W یا M می‌سازد",
        "سگ بزرگ میزبان شباهنگ، درخشان‌ترین ستاره آسمان شب است",
        "سگ کوچک با ستاره روشن پروکیون شناخته می‌شود",
        "قنطورس میزبان آلفا قنطورس، نزدیک‌ترین سامانه ستاره‌ای به خورشید است",
        "قیفاووس نامش را از پادشاه افسانه‌ای گرفته است",
        "کبوتر، صورت فلکی کوچکی در جنوب خرگوش است",
        "غراب با چهار ستاره اصلی، شکلی شبیه ذوزنقه دارد",
        "صلیب جنوبی برای پیدا کردن جهت جنوب آسمان به کار می‌آید",
        "ماهی زرین بخشی از آسمان نزدیک ابر ماژلانی بزرگ را در بر می‌گیرد",
        "نهر، رودخانه‌ای بلند و پیچان در آسمان جنوبی است",
        "درنا، پرنده‌ای بلندپا در صورت‌های فلکی جنوبی است",
        "مار آبی از نظر مساحت بزرگ‌ترین صورت فلکی آسمان است",
        "هندی، صورت فلکی کم‌نور آسمان جنوبی است",
        "سوسمار، نواری کوچک از ستارگان میان ذات‌الکرسی و دجاجه است",
        "تک‌شاخ، میان جبار و سگ بزرگ و کوچک قرار دارد",
        "هشتک نزدیک قطب جنوب آسمان قرار گرفته است",
        "مار به دو بخش سر و دم تقسیم می‌شود که مارافسای میان آن‌هاست"
    )
    private val selectedAbilities = linkedSetOf<String>()
    private var extraAbilitiesUnlocked = false
    private var healthRegenTimer = 0f
    private var phoenixUsed = false
    private var shopHandledWave = -1
    private var upgradeHandledWave = -1
    private var legendaryTime = 0f
    private var legendaryCharge = 0
    private var legendaryChargeSuppressed = false
    private val legendaryChargeNeeded = 15
    private var weaponTimer = 0f
    private var piercingTimer = 0f
    private var enemySlowTimer = 0f
    private var squadTimer = 0f; private var squadSpawnTimer = 0f
    private var cometSpawnTimer = 2.5f
    private var rapidTimer = 0f; private var shieldTimer = 0f
private var rocketTimer = 0f; private var magnetTimer = 0f
    private var laserCooldown = 0f; private var missileCooldown = 0f
    private var controlTouch = false
    private var waiting = false; private var waveT = 0f; private var bannerT = 0f; private var stageIntroTimer = 0f; private var overT = 0f
    private var kills = 0
    private var credits = prefs.getInt("credits", 0)
    private var bestWave = prefs.getInt("best_wave", 0)
    private var currentShip = prefs.getInt("ship", 0).coerceIn(0, 4)
    private var upgradePoints = prefs.getInt("ship_upgrade_points", 0)
    private var dailyDay = prefs.getLong("daily_day", -1L)
    private var dailyKills = prefs.getInt("daily_kills", 0)
    private var dailyDone = prefs.getBoolean("daily_done", false)
    private var dailyAdRewardDay = prefs.getInt("daily_ad_reward_day", -1)
    private var dailyAdPending = false
    private var upgradeChoices = emptyList<String>()
    private var upgradeRefreshUsed = false
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

    init {
        sfx.enabled = true
        sfx.volume = sfxVolume
        for (i in 1..currentShip) prefs.edit().putBoolean("ship_unlocked_" + i, true).apply()
        refreshDaily()
        holder.addCallback(this)
        TapsellAds.preloadAll()
        prepareMusic()
    }

    private fun prepareMusic() {
        if (musicPlayer != null) return
        try {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val player = MediaPlayer.create(act, R.raw.ambient_theme, attributes, 0)
            if (player == null) {
                musicPrepared = false
                Log.e("NeonShooter", "Background music resource could not be opened")
                return
            }
            player.isLooping = true
            player.setVolume(musicVolume, musicVolume)
            player.setOnErrorListener { failedPlayer, what, extra ->
                Log.e("NeonShooter", "Background music playback failed: $what/$extra")
                try { failedPlayer.release() } catch (_: Exception) {}
                if (musicPlayer === failedPlayer) musicPlayer = null
                musicPrepared = false
                true
            }
            musicPlayer = player
            musicPrepared = true
            syncMusic()
        } catch (e: Exception) {
            musicPrepared = false
            musicPlayer = null
            Log.e("NeonShooter", "Background music unavailable", e)
        }
    }

    private fun syncMusic() {
        val player = musicPlayer
        if (player == null) {
            musicPrepared = false
            prepareMusic()
            return
        }
        try {
            val gain = (musicVolume * if (st == St.PAUSE) 0.82f else 1f).coerceIn(0f, 1f)
            player.setVolume(gain, gain)
            if (musicVolume > 0.001f) {
                if (!player.isPlaying) player.start()
            } else if (player.isPlaying) {
                player.pause()
            }
            musicPrepared = true
        } catch (e: Exception) {
            Log.e("NeonShooter", "Background music state error", e)
            try { player.release() } catch (_: Exception) {}
            if (musicPlayer === player) musicPlayer = null
            musicPrepared = false
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
        prepareMusic()
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
            val x = e.x / s
            val y = e.y / s
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lx = e.x; ly = e.y; controlTouch = false
                    if (st == St.AUDIO_SETTINGS) {
                        activeAudioSlider = when {
                            abs(y - wh * 0.485f) <= 13f && x in 20f..200f -> 1
                            abs(y - wh * 0.655f) <= 13f && x in 20f..200f -> 2
                            else -> 0
                        }
                        if (activeAudioSlider > 0) updateAudioSliderFromX(x)
                        else buttons().firstOrNull { it.r.contains(x,y) }?.let { pressedButtonId=it.id; buttonPulse=0.18f; onBtn(it.id) }
                    } else if (st == St.PLAY) {
                        if (y >= wh-34f && x in 48f..108f) { activateLegendary(); controlTouch=true }
                        else if (x >= W-32f && y <= 30f) {
                            st = St.PAUSE
                            syncMusic()
                            controlTouch = true
                        }
                        else if (y >= wh-34f && x <= 42f) { performDash(); controlTouch=true }
                        else if (y >= wh-34f && x >= W-96f) { if (x >= W-52f) fireLaser() else launchMissiles(); controlTouch=true }
                        else {
                            Unit
                        }
                    } else if (!(st == St.OVER && overT < 0.7f)) {
                        buttons().firstOrNull { it.r.contains(x,y) }?.let { pressedButtonId=it.id; buttonPulse=0.18f; onBtn(it.id) }
                    } else {
                        Unit
                    }
                }
                MotionEvent.ACTION_MOVE -> when {
                    st == St.AUDIO_SETTINGS && activeAudioSlider > 0 -> updateAudioSliderFromX(x)
                    st == St.PLAY && !controlTouch -> {
                        px = (px + (e.x-lx)/s*shipMoveMultiplier()).coerceIn(10f,W-10f)
                        py = (py + (e.y-ly)/s).coerceIn(30f,wh-48f)
                        lx=e.x; ly=e.y
                    }
                    else -> Unit
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (st == St.AUDIO_SETTINGS && activeAudioSlider > 0) updateAudioSliderFromX(x,true)
                    activeAudioSlider=0
                    controlTouch=false
                }
                else -> Unit
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

    private fun localDayKey(): Int {
        val c = java.util.Calendar.getInstance()
        return c.get(java.util.Calendar.YEAR) * 1000 + c.get(java.util.Calendar.DAY_OF_YEAR)
    }
    private fun dailyAdButtonLabel(): String = when {
        dailyAdPending -> "در انتظار تبلیغ..."
        dailyAdRewardDay == localDayKey() -> "پاداش امروز دریافت شد"
        else -> "تماشای تبلیغ • ۱۰۰ سکه"
    }
    private fun claimDailyCoinReward() {
        val day = localDayKey()
        if (dailyAdPending) return
        if (dailyAdRewardDay == day) {
            adStatusText = "پاداش تبلیغ امروز قبلاً دریافت شده است"
            adStatusTimer = 1.8f
            return
        }
        dailyAdPending = true
        try {
            TapsellAds.showRewarded(act) { confirmed ->
                act.runOnUiThread {
                    synchronized(lock) {
                        dailyAdPending = false
                        if (confirmed && dailyAdRewardDay != day) {
                            dailyAdRewardDay = day
                            credits += 100
                            prefs.edit().putInt("daily_ad_reward_day", day).putInt("credits", credits).apply()
                            adStatusText = "۱۰۰ سکه به موجودی اضافه شد"
                            adStatusTimer = 2.2f
                        } else if (!confirmed) {
                            adStatusText = "تبلیغ کامل نشد؛ سکه‌ای پرداخت نشد"
                            adStatusTimer = 2.0f
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            dailyAdPending = false
            adStatusText = "تبلیغ در دسترس نیست"
            adStatusTimer = 2.0f
            Log.e("NeonShooter", "Daily rewarded ad unavailable", e)
        }
    }
    private fun updateAudioSliderFromX(x: Float, persist: Boolean = false) {
        val v = ((x - 30f) / 160f).coerceIn(0f, 1f)
        when (activeAudioSlider) {
            1 -> {
                musicVolume = v
                val gain = (musicVolume * if (st == St.PAUSE) 0.82f else 1f).coerceIn(0f, 1f)
                try { musicPlayer?.setVolume(gain, gain) } catch (_: Exception) {}
                if (musicVolume > 0.001f) {
                    try { if (musicPlayer != null && musicPlayer?.isPlaying == false) musicPlayer?.start() } catch (_: Exception) {}
                } else try { musicPlayer?.pause() } catch (_: Exception) {}
                if (persist) prefs.edit().putFloat("music_volume", musicVolume).apply()
            }
            2 -> {
                sfxVolume = v
                sfx.volume = v
                if (persist) prefs.edit().putFloat("sfx_volume", v).apply()
            }
        }
    }

    private fun shipName(id: Int = currentShip): String = when (id) {
        1 -> "کاوشگر جیمز وب"
        2 -> "کاوشگر ویجر ۱"
        3 -> "کاوشگر ویجر ۲"
        4 -> "کاوشگر فونیکس"
        else -> "کاوشگر هابل"
    }

    private fun shipPrice(id: Int): Int = intArrayOf(0, 6500, 18000, 42000, 85000)[id.coerceIn(0, 4)]
    private fun shipDescription(id: Int): String = when (id) {
        1 -> "آینه طلایی ۱۸بخشی و سپر خورشیدی پنج‌لایه"
        2 -> "ماموریت اعماق فضا"
        3 -> "کاوشگر سیاره‌ای با شلیک سریع"
        4 -> "مریخ‌نشین سه‌پایه با بدنه مقاوم"
        else -> "تلسکوپ مداری با پنل خورشیدی"
    }
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
        adStatusText = "کاوشگر " + shipName() + " انتخاب شد"; adStatusTimer = 1.4f
    }

    private fun buyShipUpgrade(stat: String) {
        val rank = shipUpgradeRank(stat)
        if (rank >= 5) {
            adStatusText = "این ارتقا به سطح MAX رسیده"
            adStatusTimer = 1.6f
            return
        }
        val cost = rank + 1
        if (upgradePoints < cost) {
            adStatusText = "برای ارتقای " + (if (stat == "gold") "توان طلایی" else "کاوشگر") + " " + cost + " امتیاز باس لازم است"
            adStatusTimer = 2f
            return
        }
        // Permanent ship upgrades, including the golden ability, only spend boss upgrade points.
        upgradePoints -= cost
        prefs.edit().putInt("ship_upgrade_points", upgradePoints)
            .putInt("ship_upgrade_" + currentShip + "_" + stat, rank + 1).apply()
        adStatusText = (if (stat == "gold") "توان طلایی" else "ارتقای کاوشگر") + " به سطح " + (rank + 1) + " رسید"
        adStatusTimer = 1.6f
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

    private fun upgradeAbilityPool(): List<String> {
        val coreAbilities = listOf("firepower", "missile", "laser", "drone", "magnet")
        val allAbilities = coreAbilities + listOf("shield", "dash", "chain", "hull", "emp", "ricochet", "gravity_well", "overload", "nanobots", "scanner", "salvage_drone", "kill_streak", "ricochet_shield",
            "health_regen", "armor", "critical", "overdrive", "explosive", "piercing", "stasis", "scavenger", "multishot", "phoenix")
        if (!extraAbilitiesUnlocked && wave >= 25 && coreAbilities.all { abilityRank(it) >= 5 }) extraAbilitiesUnlocked = true
        return if (extraAbilitiesUnlocked) allAbilities else coreAbilities
    }
    private fun prepareUpgradeChoices() {
        upgradeRefreshUsed = false
        val eligible = upgradeAbilityPool().filter { abilityRank(it) < 5 }
        upgradeChoices = eligible.shuffled().take(3)
        if (upgradeChoices.isEmpty()) { st = St.PLAY; waiting = true; waveT = 0.75f }
    }
    private fun canRefreshUpgradeChoices(): Boolean {
        if (upgradeRefreshUsed) return false
        return upgradeAbilityPool().count { abilityRank(it) < 5 && it !in upgradeChoices } >= 3
    }
    private fun refreshUpgradeChoices() {
        if (!canRefreshUpgradeChoices()) {
            adStatusText = if (upgradeRefreshUsed) "رفرش این مرحله قبلاً استفاده شده" else "سه قابلیت تازه باقی نمانده است"
            adStatusTimer = 1.8f
            return
        }
        upgradeChoices = upgradeAbilityPool().filter { abilityRank(it) < 5 && it !in upgradeChoices }.shuffled().take(3)
        upgradeRefreshUsed = true
        sfx.play("power")
    }

    private fun applyUpgrade(id: String) {
        when (id) {
            "firepower" -> { weaponLevel = min(5, weaponLevel + 1); bonusDamage++ ; fireRateBonus = min(0.28f, fireRateBonus + 0.035f) }
            "missile" -> missileLevel = min(5, missileLevel + 1)
            "laser" -> laserLevel = min(5, laserLevel + 1)
            "drone" -> droneLevel = min(5, droneLevel + 1)
            "magnet" -> {
                magnetLevel = min(5, magnetLevel + 1)
                if (shieldLevel > 0) { shieldMaxHp += 4; shieldHp = min(shieldMaxHp, shieldHp + 4) }
            }
            "shield" -> { shieldRank = min(5, shieldRank + 1); shieldLevel = shieldRank; shieldMaxHp = 10 + shieldRank * 6 + if (magnetLevel > 0) 4 else 0; shieldHp = min(shieldMaxHp, if (shieldHp <= 0) shieldMaxHp else shieldHp + 8); reflectiveShield = shieldHp > 0 }
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
        if (activeCombo().isNotEmpty()) bannerT = 2.8f
        sfx.play("power")
    }

    private fun activeCombo(): String = when {
        missileLevel > 0 && droneLevel > 0 -> "یورش پهپاد موشکی"
        laserLevel > 0 && weaponLevel >= 3 -> "لیزر منشوری"
        magnetLevel > 0 && shieldLevel > 0 -> "سپر مغناطیسی"
        gravityWellRank > 0 && empRank > 0 -> "توفان گرانشی"
        chainLevel > 0 && explosiveRank > 0 -> "زنجیره انفجاری"
        scannerRank > 0 && laserLevel > 0 -> "لیزر نقطه‌زن"
        else -> ""
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
            "shop_weapon" -> if (credits >= 320) { credits -= 320; weaponLevel = min(6, weaponLevel + 1); bonusDamage++ }
            "shop_shield" -> if (credits >= 260) { credits -= 260; shieldRank = min(5, shieldRank + 1); shieldLevel = shieldRank; shieldMaxHp = 10 + shieldRank * 6; shieldHp = min(shieldMaxHp, shieldHp + 10); reflectiveShield = shieldHp > 0 }
            "shop_dash" -> if (credits >= 220) { credits -= 220; dashCharges = min(4, dashCharges + 1) }
            "shop_continue" -> { st = St.PLAY; waiting = true; waveT = 0.8f }
        }
        prefs.edit().putInt("credits", credits).apply()
    }

    private fun legendaryName(): String = when (currentShip) {
        0 -> "طوفان پلاسما"
        1 -> "پس‌سوز کوانتومی"
        2 -> "ایجیس مطلق"
        3 -> "فاز شبح"
        else -> "انفجار ققنوس"
    }

    private fun legendaryDescription(): String = when (currentShip) {
        0 -> "۱۵ نابودی: شلیک پلاسما با سرعت بسیار بالا برای چند ثانیه"
        1 -> "۱۵ نابودی: جهش سریع، افزایش مانور و ایمنی کوتاه"
        2 -> "۱۵ نابودی: بازیابی سپر و مصونیت موقت"
        3 -> "۱۵ نابودی: محو شدن، عبور از گلوله‌ها و کند کردن دشمنان"
        else -> "۱۵ نابودی: پاک‌سازی تیرها، نابودی دشمنان عادی و ضربه سنگین به باس"
    }

    private fun activateLegendary() {
        if (st != St.PLAY || legendaryCharge < legendaryChargeNeeded || legendaryTime > 0f) return
        legendaryCharge = 0
        val duration = 4.5f + shipUpgradeRank("gold") * 0.65f
        when (currentShip) {
            0 -> {
                legendaryTime = duration
                rapidTimer = max(rapidTimer, duration)
            }
            1 -> {
                legendaryTime = duration
                invul = max(invul, 1.25f + shipUpgradeRank("gold") * 0.12f)
                val direction = if (px < W / 2f) 1f else -1f
                px = (px + direction * 76f).coerceIn(12f, W - 12f)
                boom(px, py, 26, 82f)
            }
            2 -> {
                legendaryTime = duration
                shieldHp = shieldMaxHp
                reflectiveShield = shieldHp > 0
                invul = max(invul, duration)
                impactRings.add(ImpactRing(px, py, 0.55f, 0.55f))
            }
            3 -> {
                legendaryTime = duration
                invul = max(invul, duration)
                enemySlowTimer = max(enemySlowTimer, duration * 0.65f)
                bullets.removeAll { !it.mine }
                enemyLasers.clear()
            }
            4 -> {
                legendaryTime = 1.4f
                lives = min(10, lives + 1 + shipUpgradeRank("gold") / 3)
                invul = max(invul, 3.2f + shipUpgradeRank("gold") * 0.3f)
                bullets.removeAll { !it.mine }
                enemyLasers.clear()
                legendaryChargeSuppressed = true
                try {
                    for (enemy in enemies.toList()) {
                        if (enemy.type == 2) {
                            enemy.hp -= 100 + wave * 5 + shipUpgradeRank("gold") * 20
                            if (enemy.hp <= 0) kill(enemy)
                        } else kill(enemy)
                    }
                } finally {
                    legendaryChargeSuppressed = false
                }
                boom(px, py, 65, 130f)
            }
        }
        bannerT = 1.6f
        adStatusText = legendaryName() + " فعال شد"
        adStatusTimer = 1.5f
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

    private fun waveName(level: Int): String =
        stageNames.getOrElse(level - 1) { "مرز ناشناخته $level" }

    private fun persianNumber(value: Int): String = value.toString().map { ch ->
        if (ch in '0'..'9') ('۰'.code + (ch - '0')).toChar() else ch
    }.joinToString("")

    private fun waveTitle(level: Int = wave): String =
        "مرحله " + persianNumber(level) + " • " + waveName(level)

    private fun stageDescription(level: Int = wave): String =
        stageDescriptions.getOrElse(level - 1) { "در این مرحله با یکی از صورت‌های فلکی آسمان روبه‌رو می‌شوی" }

    private fun bossConstellationName(index: Int): String =
        bossConstellationNames[index.coerceIn(0, bossConstellationNames.lastIndex)]

    private fun drawBossConstellation(c: Canvas, index: Int, centerX: Float, centerY: Float, size: Float = 1f, alpha: Int = 160) {
        val safeIndex = index.coerceIn(0, bossConstellationPoints.lastIndex)
        val points = bossConstellationPoints[safeIndex]
        val edges = bossConstellationEdges[safeIndex]
        val color = bossConstellationColors[safeIndex]
        val cr = Color.red(color)
        val cg = Color.green(color)
        val cb = Color.blue(color)

        // The constellation itself forms the boss silhouette: connected nebula lines and bright stars.
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeWidth = 5.2f * size * s
        paint.color = Color.argb((alpha * 0.30f).toInt(), cr, cg, cb)
        paint.maskFilter = BlurMaskFilter(4.2f * size * s, BlurMaskFilter.Blur.NORMAL)
        for ((from, to) in edges) {
            val a = points[from]
            val b = points[to]
            c.drawLine(
                (centerX + a.first * size) * s, (centerY + a.second * size) * s,
                (centerX + b.first * size) * s, (centerY + b.second * size) * s, paint
            )
        }
        paint.maskFilter = null
        paint.strokeWidth = 1.45f * size * s
        paint.color = Color.argb(alpha, cr, cg, cb)
        for ((from, to) in edges) {
            val a = points[from]
            val b = points[to]
            c.drawLine(
                (centerX + a.first * size) * s, (centerY + a.second * size) * s,
                (centerX + b.first * size) * s, (centerY + b.second * size) * s, paint
            )
        }

        paint.style = Paint.Style.FILL
        for ((starIndex, point) in points.withIndex()) {
            val major = starIndex == 0 || starIndex == points.lastIndex || starIndex == points.size / 2
            val radius = (if (major) 2.75f else 1.85f) * size * s
            val x = (centerX + point.first * size) * s
            val y = (centerY + point.second * size) * s
            paint.color = Color.argb((alpha * 0.40f).toInt(), cr, cg, cb)
            paint.maskFilter = BlurMaskFilter(radius * 2.2f, BlurMaskFilter.Blur.NORMAL)
            c.drawCircle(x, y, radius * 2.0f, paint)
            paint.maskFilter = null
            paint.color = Color.argb(alpha, min(255, 225 + cr / 9), min(255, 235 + cg / 12), min(255, 245 + cb / 24))
            c.drawCircle(x, y, radius, paint)
            paint.color = Color.argb(alpha, 255, 255, 255)
            c.drawCircle(x, y, radius * 0.42f, paint)
        }
        paint.style = Paint.Style.FILL
        paint.maskFilter = null
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
            "audio" -> { audioReturnState = st; st = St.AUDIO_SETTINGS }
            "audioBack" -> { st = audioReturnState; activeAudioSlider = 0; syncMusic() }
            "dailyAd" -> claimDailyCoinReward()
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
            "upgrade_refresh" -> refreshUpgradeChoices()
            "shop_weapon", "shop_shield", "shop_dash", "shop_continue" -> buyShop(id)
            "extraLife" -> {
                if (st == St.OVER && !rewardedContinueUsed && !rewardedContinuePending) {
                    rewardedContinuePending = true
                    TapsellAds.showRewarded(act) { rewardConfirmed ->
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
                add("audio", "تنظیم صدا", y + 22f)
                add("ship", "آشیانه کاوشگرها", y + 44f)
                add("shipup", "ارتقای کاوشگر", y + 66f)
                add("about", "درباره", y + 88f)
                add("dailyAd", dailyAdButtonLabel(), y + 110f)
            }
            St.ABOUT -> {
                val y = wh * 0.62f
                add("telegram", "تلگرام", y)
                add("aboutBack", "بازگشت", y + 30f)
            }
            St.PAUSE -> {
                val y = wh * 0.365f
                val rows = listOf("resume" to "ادامه", "pauseRestart" to "شروع مجدد", "audio" to "تنظیم صدا", "menu" to "منوی اصلی")
                rows.forEachIndexed { index, pair ->
                    l.add(Btn(pair.first, pair.second, RectF(54f, y + index * 22f, 166f, y + index * 22f + 18f)))
                }
            }
            St.AUDIO_SETTINGS -> add("audioBack", "بازگشت", wh * 0.78f)
            St.OVER -> {
                val y = wh * 0.54f
                add("play", "تلاش دوباره", y)
                add("menu", "منو", y + 30f)
            }
            St.UPGRADE -> {
                val top = wh * 0.895f
                val gap = 4f
                val width = (W - 18f - gap * 2f) / 3f
                for (i in 0..2) {
                    val left = 9f + i * (width + gap)
                    l.add(Btn("upgrade_$i", "انتخاب قابلیت", RectF(left, top, left + width, top + 19f)))
                }
                val label = if (upgradeRefreshUsed) "استفاده شد" else if (canRefreshUpgradeChoices()) "تازه‌سازی ۳ قابلیت" else "۳ گزینه تازه نیست"
                l.add(Btn("upgrade_refresh", label, RectF(60f, wh * 0.832f, 160f, wh * 0.832f + 17f)))
            }
            St.SHOP -> {
                val y = wh * 0.46f
                val rows = listOf(
                    "shop_weapon" to "سلاح | ۳۲۰ سکه",
                    "shop_shield" to "سپر | ۲۶۰ سکه",
                    "shop_dash" to "جهش | ۲۲۰ سکه",
                    "shop_continue" to "ادامه"
                )
                rows.forEachIndexed { index, pair ->
                    val top = y + index * 28f
                    l.add(Btn(pair.first, pair.second, RectF(50f, top, 170f, top + 22f)))
                }
            }
            St.SHIPS -> {
                val y = wh * 0.37f
                val cardHeight = 25f
                val rowStep = ((wh * 0.82f - y - cardHeight) / 4f).coerceIn(29.75f, 48f)
                for (i in 0..4) {
                    val id = if (shipOwned(i)) "ship_select_$i" else "ship_buy_$i"
                    val top = y + i * rowStep
                    l.add(Btn(id, shipName(i), RectF(9f, top, 211f, top + cardHeight)))
                }
                add("shipBack", "بازگشت", wh * 0.87f)
            }
            St.SHIP_UPGRADE -> {
                val y = wh * 0.405f
                val stats = listOf("attack" to "قدرت سلاح", "armor" to "زره", "mobility" to "سرعت", "hull" to "جان شروع", "gold" to "توان طلایی")
                stats.forEachIndexed { i, pair ->
                    val rank = shipUpgradeRank(pair.first)
                    val cost = rank + 1
                    val price = if (pair.first == "gold") "هزینه " + cost + " امتیاز باس" else cost.toString() + " امتیاز باس"
                    val label = if (rank >= 5) pair.second + " • MAX" else pair.second + " • " + rank + "/۵ • " + price
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
        healthRegenTimer = 0f; empPulseTimer = 0f; empRank = 0; phoenixUsed = false; legendaryTime = 0f; legendaryCharge = 0; legendaryChargeSuppressed = false
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
        waveKills = 0
        if (wave > bestWave) { bestWave = wave; prefs.edit().putInt("best_wave", bestWave).apply() }
        eventKind = 0; eventTimer = 0f; meteorTimer = 0f; goldenCrateSpawned = false
        if (wave >= 4 && (wave % 4 == 0 || Random.nextFloat() < 0.24f)) activateRandomEvent()
        if (wave >= 3 && wave % 3 == 0 && blackHoles.size < 2) {
            val side = if (Random.nextBoolean()) 1f else -1f
            blackHoles.add(BlackHole(Random.nextFloat() * (W - 76f) + 38f, -18f, 48f + Random.nextFloat() * 12f, 72000f, side * (5f + Random.nextFloat() * 8f)))
        }
        stageIntroTimer = 5.0f
        if (wave % 5 == 0) {
            waveTarget = enemies.count { !it.dead } + 1 + if (eventKind == 5) 3 else 0
            val bossBaseHp = 300 + wave * 44
            val earlyBossAdjustedHp = if (wave == 5) (bossBaseHp * 0.70f).roundToInt() else bossBaseHp
            val bossHp = if (wave > 25) earlyBossAdjustedHp * 2 else earlyBossAdjustedHp
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
            val hp = ceil(baseHp * difficulty * if (wave > 25) 2f else 1f).toInt()
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
        if (!legendaryChargeSuppressed) legendaryCharge = (legendaryCharge + if (e.type == 2) 5 else 1).coerceAtMost(legendaryChargeNeeded)
        if (e.type == 2) {
            val earned = if (wave > 0 && wave % 25 == 0) 4 else 2
            upgradePoints += earned
            prefs.edit().putInt("ship_upgrade_points", upgradePoints).apply()
            adStatusText = "پاداش باس: $earned امتیاز ارتقا"
            adStatusTimer = 2.0f
            repeat(10) { i ->
                val offset = (i - 4.5f) * 3.0f
                items.add(Item(e.x + offset, e.y + abs(i - 4.5f) * 0.7f, 13, 10 + scavengerRank * 2))
            }
        } else if (Random.nextFloat() < 0.82f) {
            val coinPieces = 3 + if (scavengerRank >= 4) 1 else 0
            repeat(coinPieces) { i ->
                val offset = (i - (coinPieces - 1) / 2f) * 3.8f
                items.add(Item(e.x + offset, e.y + Random.nextFloat() * 5f, 13, 2 + scavengerRank))
            }
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
        val prismCombo = laserLevel > 0 && weaponLevel >= 3
        val width = (5f + laserLevel * 2.2f) * if (prismCombo) 1.22f else 1f
        val offsets = if (prismCombo) floatArrayOf(-12f, 0f, 12f) else floatArrayOf(0f)
        val damage = 2 + laserLevel * 3 + bonusDamage + (if (prismCombo) 2 else 0) + (if (scannerTimer > 0f && scannerRank > 0) scannerRank * 2 else 0)
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
        val swarmCombo = missileLevel > 0 && droneLevel > 0
        val count = 2 + missileLevel + if (swarmCombo) 2 else 0
        for (i in 0 until count) {
            val offset = (i - (count - 1) / 2) * 7f
            missiles.add(Missile((px + offset).coerceIn(5f, W - 5f), py - 8f, offset * 0.8f, -115f, (2 + missileLevel * 2 + bonusDamage + if (swarmCombo) 1 else 0)))
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
        stageIntroTimer = max(0f, stageIntroTimer - dt)
        if (healthRegenRank > 0) {
            healthRegenTimer -= dt
            if (healthRegenTimer <= 0f) {
                if (lives < 10) { lives++; bannerT = max(bannerT, 0.9f) }
                healthRegenTimer = max(7f, 34f - healthRegenRank * 5f)
            }
        }
        legendaryTime = max(0f, legendaryTime - dt)
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
                if (empRank > 0) {
                    enemySlowTimer = max(enemySlowTimer, 1.4f + empRank * 0.25f)
                    for (target in enemies) if (!target.dead) {
                        target.hp -= empRank * 2
                        target.hitFlash = 0.3f
                        impactRings.add(ImpactRing(target.x, target.y, 0.22f, 0.22f))
                        if (target.hp <= 0) kill(target)
                    }
                }
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
                            TapsellAds.showInterstitial(act) {
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
                if (newPhase > e.phase) { e.phase = newPhase; e.fire = 0.35f; boom(e.x, e.y, 28, 80f); impactRings.add(ImpactRing(e.x, e.y, 0.55f, 0.55f)) }
                if (e.phase >= 3) {
                    e.phaseSummonTimer -= enemyDt
                    if (e.phaseSummonTimer <= 0f && enemies.count { it.type != 2 && !it.dead } + summonedEnemies.size < 5) {
                        for (side in -1..1 step 2) {
                            val sx = (e.x + side * 22f).coerceIn(12f, W - 12f)
                            summonedEnemies.add(E(sx, e.y + 12f, 6, 5 + wave / 10, sx, 14f, e.age + side))
                        }
                        e.phaseSummonTimer = 7.5f
                        boom(e.x, e.y + 10f, 18, 58f)
                    }
                }
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
                val homingSpeed = 175f + missileLevel * 8f + if (droneLevel > 0 && missileLevel > 0) 40f else 0f
                val steering = 320f + missileLevel * 24f
                val desiredX = dx / d * homingSpeed; val desiredY = dy / d * homingSpeed
                missile.vx += (desiredX - missile.vx).coerceIn(-steering * dt, steering * dt)
                missile.vy += (desiredY - missile.vy).coerceIn(-steering * dt, steering * dt)
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
            val magneticShieldRadius = if (magnetLevel > 0 && shieldLevel > 0) 34f else 27f
            if (!b.mine && shieldHp > 0 && hypot(b.x - px, b.y - py) < magneticShieldRadius) {
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
            val magnetRange = when {
                magnetLevel > 0 && shieldLevel > 0 -> 104f
                magnetLevel > 0 -> 88f
                magnetTimer > 0f -> 72f
                else -> 0f
            }
            val itemDistance = hypot(item.x - px, item.y - py)
            if (magnetRange > 0f && itemDistance < magnetRange) {
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
                1 -> { weaponLevel = min(6, weaponLevel + 1); prefs.edit().putInt("upgrade_weapon", weaponLevel).apply(); score += 60 }
                2 -> rapidTimer = max(rapidTimer, 10f)
                3 -> shieldTimer = max(shieldTimer, 10f)
                4 -> {
                    enemies.toList().forEach { if (!it.dead) kill(it) }
                    bullets.removeAll { !it.mine }; enemyLasers.clear()
                    boom(W / 2f, wh * 0.42f, 110, 150f)
                }
                5 -> { missileLevel = min(6, missileLevel + 1); prefs.edit().putInt("upgrade_missile", missileLevel).apply(); score += 60 }
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
                            if (explosiveRank > 0) {
                                for (splash in enemies) if (splash !== e && splash !== next && !splash.dead && hypot(splash.x - next.x, splash.y - next.y) < 20f + explosiveRank * 4f) {
                                    splash.hp -= explosiveRank
                                    splash.hitFlash = 0.16f
                                    if (splash.hp <= 0) kill(splash)
                                }
                                impactRings.add(ImpactRing(next.x, next.y, 0.30f, 0.30f))
                            }
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
        val coinScale = if (item.type == 13) 0.48f else 1f
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

        // Keep numeric runs left-to-right inside the surrounding Persian RTL sentence.
        val bidiText = t.replace(Regex("([0-9۰-۹٠-٩]+)")) { "\u200E${it.value}\u200E" }
        tp.textAlign = Paint.Align.LEFT
        val width = (tp.measureText(bidiText) + 8f * s).roundToInt().coerceIn(1, max(1, sw))
        val layout = StaticLayout.Builder.obtain(bidiText, 0, bidiText.length, tp, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setTextDirection(TextDirectionHeuristics.RTL)
            .setIncludePad(false)
            .setMaxLines(1)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        val left = when (align) {
            Paint.Align.LEFT -> x * s
            Paint.Align.RIGHT -> x * s - width
            else -> x * s - width / 2f
        }
        c.save()
        c.clipRect(0f, 0f, sw.toFloat(), sh.toFloat())
        c.translate(left, y * s - layout.getLineBaseline(0))
        layout.draw(c)
        c.restore()
    }

    private fun renderFrame(c: Canvas) {
        if (!::bPlayer.isInitialized) return
        c.drawColor(stageBackgroundColor())
        paint.style = Paint.Style.FILL
        for (star in stars) {
            val pulse = (sin(star.phase) + 1f) * 0.5f
            paint.color = stageStarColor()
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
        paint.color = stageStarColor()
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
            St.AUDIO_SETTINGS -> drawAudioSettings(c)
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
            if (e.type == 2) {
                val bossId = e.t0.toInt().coerceIn(0, 9)
                val constellationScale = e.hw * 0.085f
                drawBossConstellation(c, bossId, e.x, e.y, constellationScale, 235)
            } else {
                val sprite = when (e.type) { 1 -> bE1; 3 -> bE2; 4 -> bE3; 5 -> bossSprites[5]; 6 -> bossSprites[6]; else -> bE0 }
                spr(c, sprite, e.x, e.y)
            }
            if (e.type == 2 && e.phase >= 2) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = (if (e.phase >= 3) 1.8f else 1.1f) * s
                paint.color = if (e.phase >= 3) 0xFFFF65C8.toInt() else 0xFF63E8FF.toInt()
                for (ring in 0 until e.phase) {
                    val radius = (e.hw + 6f + ring * 5f + sin(time * 4f + ring) * 1.5f) * s
                    c.drawCircle(e.x * s, e.y * s, radius, paint)
                }
                paint.style = Paint.Style.FILL
            }
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
        // Separate score, wave/boss label, currency and pause into distinct columns.
        drawFittedText(c, "امتیاز $score", 6f, 14f, 6.2f, YEL, 58f, Paint.Align.LEFT)
        drawCoinCounter(c, 159f, 14f)
        val activeBoss = enemies.firstOrNull { it.type == 2 && !it.dead }
        if (activeBoss != null) {
            val bossId = activeBoss.t0.toInt().coerceIn(0, 9)
            drawFittedText(c, "باس " + bossConstellationName(bossId) + " • فاز " + activeBoss.phase, 105f, 14f, 4.7f, WHT, 68f)
        } else {
            drawFittedText(c, waveTitle(wave), 105f, 14f, 4.6f, 0xFF9CEBFF.toInt(), 68f)
        }
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
        else drawFittedText(c, lives.toString() + "/۵", 77f, 25f, 5.5f, 0xFFFFD45C.toInt(), 16f, Paint.Align.LEFT)
        if (shieldRank > 0) {
            text(c, "سپر", 7f, 42f, 5.5f, 0xFF8BDFFF.toInt(), Paint.Align.LEFT)
            paint.color = 0x88303C4A.toInt(); c.drawRoundRect(18f*s, 36f*s, 72f*s, 39f*s, 1.5f*s, 1.5f*s, paint)
            paint.color = if (shieldHp > shieldMaxHp / 3) 0xFF55DFFF.toInt() else 0xFFFF6688.toInt()
            val shieldFrac = if (shieldMaxHp > 0) shieldHp.toFloat() / shieldMaxHp else 0f
            c.drawRoundRect(18f*s, 36f*s, (18f + 54f*shieldFrac)*s, 39f*s, 1.5f*s, 1.5f*s, paint)
        }
        if (activeBoss != null) {
            val frac = (activeBoss.hp.toFloat() / activeBoss.maxHp).coerceIn(0f, 1f)
            paint.style = Paint.Style.FILL
            paint.color = 0xAA222A35.toInt()
            c.drawRoundRect(96f*s, 21f*s, 184f*s, 25f*s, 1.5f*s, 1.5f*s, paint)
            paint.color = DRED
            c.drawRoundRect(96f*s, 21f*s, (96f + 88f*frac)*s, 25f*s, 1.5f*s, 1.5f*s, paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 0.65f*s; paint.color = RED
            c.drawRoundRect(96f*s, 21f*s, 184f*s, 25f*s, 1.5f*s, 1.5f*s, paint)
            paint.style = Paint.Style.FILL
            activeBoss.bossSkills.take(2).forEachIndexed { index, skill -> drawAbilityIcon(c, skill, 105f + index * 18f, 37f) }
        }
        if (comboCount >= 3) drawFittedText(c, "ترکیب x" + (1 + comboCount / 5).coerceAtMost(5), 105f, 48f, 5.2f, 0xFFFFD45C.toInt(), 70f)
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
            val bossId = bs.t0.toInt().coerceIn(0, 9)
            drawFittedText(c, bossConstellationName(bossId) + " • فاز " + bs.phase, 105f, 14f, 4.7f, WHT, 68f)
            bs.bossSkills.take(2).forEachIndexed { index, skill -> drawAbilityIcon(c, skill, W / 2f - 13f + index * 26f, 34f) }
            val frac = bs.hp.toFloat() / bs.maxHp
            paint.color = DRED; c.drawRect(20f * s, 22f * s, (20f + 140f * frac) * s, 25f * s, paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = s * 0.6f; paint.color = RED
            c.drawRect(20f * s, 22f * s, 160f * s, 25f * s, paint); paint.style = Paint.Style.FILL
        }
        if (waveTarget > 0 && (st == St.PLAY || st == St.PAUSE)) {
            val x = (W - 5f) * s; val top = 56f * s; val bottom = (wh - 48f) * s
            val progress = (waveKills.toFloat() / waveTarget.toFloat()).coerceIn(0f, 1f)
            paint.color = 0x88434D5A.toInt(); c.drawRoundRect(x, top, x + 2.6f * s, bottom, 1.3f * s, 1.3f * s, paint)
            paint.color = 0xFF72E8FF.toInt(); c.drawRoundRect(x, bottom - (bottom - top) * progress, x + 2.6f * s, bottom, 1.3f * s, 1.3f * s, paint)
            paint.color = 0xFFD2D8E0.toInt(); c.drawCircle(x + 1.3f * s, top, 1.8f * s, paint)
        }
        if (stageIntroTimer > 0f && st == St.PLAY) {
            val boss = wave % 5 == 0
            val bossName = enemies.firstOrNull { it.type == 2 && !it.dead }?.let { bossConstellationName(it.t0.toInt()) }
            val bannerTitle = if (boss && bossName != null) "نبرد با " + bossName else waveTitle(wave)
            drawFittedText(c, bannerTitle, W / 2f, wh * 0.4f, 10.2f, if (boss) RED else WHT, 180f)
            drawFittedText(c, stageDescription(wave), W / 2f, wh * 0.4f + 11f, 4.25f, 0xFF72F0C0.toInt(), 190f)
        } else if (bannerT > 0f && st == St.PLAY) {
            val combo = activeCombo()
            val notice = if (eventKind > 0) eventName() else combo
            if (notice.isNotEmpty()) {
                drawFittedText(c, notice, W / 2f, wh * 0.4f, 7f, 0xFFFFD45C.toInt(), 170f)
            }
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
        val left = 48f; val top = wh - 32f; val right = 108f; val bottom = wh - 7f
        val ready = legendaryCharge >= legendaryChargeNeeded && legendaryTime <= 0f
        val accent = if (ready) 0xFFFFD45C.toInt() else 0xFF718294.toInt()
        paint.style = Paint.Style.FILL
        paint.color = 0xCC101B2A.toInt()
        c.drawRoundRect(left*s, top*s, right*s, bottom*s, 4f*s, 4f*s, paint)
        val progress = (legendaryCharge.toFloat() / legendaryChargeNeeded).coerceIn(0f, 1f)
        if (progress > 0f) {
            paint.color = Color.argb(if (ready) 100 else 54, Color.red(accent), Color.green(accent), Color.blue(accent))
            c.drawRoundRect(left*s, top*s, (left + (right-left)*progress)*s, bottom*s, 4f*s, 4f*s, paint)
        }
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.1f*s; paint.color = accent
        c.drawRoundRect(left*s, top*s, right*s, bottom*s, 4f*s, 4f*s, paint)
        paint.style = Paint.Style.FILL
        val glyph = when (currentShip) { 0 -> "overdrive"; 1 -> "dash"; 2 -> "shield"; 3 -> "stasis"; else -> "phoenix" }
        drawAbilityIcon(c, glyph, left + 14f, top + 10.5f)
        val status = when {
            legendaryTime > 0f -> "فعال"
            ready -> "آماده!"
            else -> "$legendaryCharge/$legendaryChargeNeeded"
        }
        drawFittedText(c, status, left + 42f, top + 14f, 4.1f, if (ready) 0xFFFFE38A.toInt() else 0xFFEAF4FF.toInt(), 33f)
        if (ready) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.85f*s
            paint.color = 0xFFFFE38A.toInt()
            c.drawRoundRect((left-1f)*s,(top-1f)*s,(right+1f)*s,(bottom+1f)*s,5f*s,5f*s,paint)
            paint.style = Paint.Style.FILL
        }
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
        "missile" -> "موشک‌های بیشتر، سریع‌تر و دقیق‌تر"
        "laser" -> "لیزر قدرتمندتر"
        "drone" -> "چرخش و رهگیری گلوله‌ها"
        "magnet" -> "جذب خودکار آیتم‌ها"
        "shield" -> "بازتاب گلوله‌های دشمن"
        "dash" -> "یک جهش ایمن در میدان"
        "chain" -> "انتقال صاعقه به دشمن نزدیک"
        "hull" -> "افزایش یک واحد جان"
        "health_regen" -> "ترمیم تدریجی بدنه کاوشگر"
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
        "drone" -> 0xFF38BDF8.toInt()
        "missile" -> 0xFFFF5B4D.toInt()
        "laser" -> 0xFF48F0E8.toInt()
        "firepower" -> 0xFFFFB020.toInt()
        "magnet" -> 0xFF3B82F6.toInt()
        "shield" -> 0xFF22D3EE.toInt()
        "dash" -> 0xFFFFD54A.toInt()
        "chain" -> 0xFFA78BFA.toInt()
        "hull" -> 0xFF4ADE80.toInt()
        "emp" -> 0xFF67E8F9.toInt()
        "ricochet" -> 0xFFFF668F.toInt()
        "gravity_well" -> 0xFFB18CFF.toInt()
        "overload" -> 0xFFFF8C42.toInt()
        "nanobots" -> 0xFF63F5C5.toInt()
        "scanner" -> 0xFFFF5CA8.toInt()
        "salvage_drone" -> 0xFFFFD66B.toInt()
        "kill_streak" -> 0xFFFF4F85.toInt()
        "ricochet_shield" -> 0xFF65DFFF.toInt()
        "health_regen" -> 0xFF8DFF75.toInt()
        "armor" -> 0xFFB5C4D6.toInt()
        "critical" -> 0xFFFFE45C.toInt()
        "overdrive" -> 0xFFFFA33C.toInt()
        "explosive" -> 0xFFFF3D3D.toInt()
        "piercing" -> 0xFFB9A3FF.toInt()
        "stasis" -> 0xFF8BD3FF.toInt()
        "scavenger" -> 0xFFFFC247.toInt()
        "multishot" -> 0xFFBC72FF.toInt()
        "phoenix" -> 0xFFFF70B5.toInt()
        else -> 0xFFB8E7FF.toInt()
    }

    private fun drawAbilityIcon(c: Canvas, id: String, cx: Float, cy: Float) {
        val color = abilityColor(id)
        val ox = cx * s
        val oy = cy * s
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.35f * s
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = color
        fun line(x1: Float, y1: Float, x2: Float, y2: Float, width: Float = 1.35f) {
            paint.strokeWidth = width * s
            c.drawLine((cx + x1) * s, (cy + y1) * s, (cx + x2) * s, (cy + y2) * s, paint)
        }
        fun circle(x: Float, y: Float, radius: Float, fill: Boolean = false) {
            paint.style = if (fill) Paint.Style.FILL else Paint.Style.STROKE
            c.drawCircle((cx + x) * s, (cy + y) * s, radius * s, paint)
            paint.style = Paint.Style.STROKE
        }
        fun poly(vararg points: Float) {
            if (points.size < 6 || points.size % 2 != 0) return
            val p = Path()
            p.moveTo((cx + points[0]) * s, (cy + points[1]) * s)
            var i = 2
            while (i < points.size) {
                p.lineTo((cx + points[i]) * s, (cy + points[i + 1]) * s)
                i += 2
            }
            c.drawPath(p, paint)
        }
        when (id) {
            "drone" -> {
                circle(0f, 0f, 3f)
                circle(-8f, -5f, 2.5f); circle(8f, -5f, 2.5f)
                circle(-8f, 6f, 2.5f); circle(8f, 6f, 2.5f)
                line(-5f, -2f, -8f, -5f); line(5f, -2f, 8f, -5f)
                line(-5f, 2f, -8f, 6f); line(5f, 2f, 8f, 6f)
            }
            "missile" -> {
                poly(0f, -10f, 4f, 2f, 2f, 6f, 0f, 3f, -2f, 6f, -4f, 2f, 0f, -10f)
                line(-3f, 4f, -7f, 8f); line(3f, 4f, 7f, 8f)
                line(-1.5f, 7f, -1.5f, 10f, 0.9f); line(1.5f, 7f, 1.5f, 10f, 0.9f)
            }
            "laser" -> {
                line(0f, -10f, 0f, 10f, 2.4f)
                poly(-5f, -6f, 0f, -10f, 5f, -6f, 0f, -2f, -5f, -6f)
                poly(-5f, 6f, 0f, 2f, 5f, 6f, 0f, 10f, -5f, 6f)
                line(-3f, 0f, 3f, 0f, 0.8f)
            }
            "firepower" -> {
                poly(-9f, 3f, -6f, -2f, -3f, 3f, 0f, -6f, 3f, 3f, 6f, -2f, 9f, 3f)
                line(-9f, 7f, 9f, 7f); line(-5f, 10f, 5f, 10f)
            }
            "magnet" -> {
                poly(-8f, -8f, -8f, 1f, -6f, 6f, -2f, 9f, 2f, 9f, 6f, 6f, 8f, 1f, 8f, -8f)
                line(-8f, -7f, -3f, -7f, 2.2f); line(3f, -7f, 8f, -7f, 2.2f)
                line(-8f, 0f, -4f, 0f); line(4f, 0f, 8f, 0f)
            }
            "shield" -> poly(0f, -10f, 8f, -6f, 7f, 3f, 0f, 10f, -7f, 3f, -8f, -6f, 0f, -10f)
            "dash" -> {
                poly(-10f, 6f, -2f, -2f, -2f, -7f, 8f, -7f, 8f, -2f, 3f, -2f)
                line(-8f, 9f, -3f, 9f); line(-10f, 4f, -7f, 4f)
                line(4f, -9f, 10f, -9f)
            }
            "chain" -> {
                val left = RectF((cx - 10f) * s, (cy - 5f) * s, (cx + 1f) * s, (cy + 5f) * s)
                val right = RectF((cx - 1f) * s, (cy - 5f) * s, (cx + 10f) * s, (cy + 5f) * s)
                c.drawOval(left, paint); c.drawOval(right, paint)
                line(-4f, -4f, 4f, 4f, 1.8f)
            }
            "hull" -> {
                poly(0f, -10f, 5f, -3f, 9f, 7f, 3f, 5f, 0f, 9f, -3f, 5f, -9f, 7f, -5f, -3f, 0f, -10f)
                line(-5f, -3f, 5f, -3f, 1.8f)
                line(-3f, 1f, 3f, 1f, 1.4f)
                line(-2f, 6f, 2f, 6f, 1.1f)
            }
            "emp" -> {
                circle(0f, 0f, 2.2f, true)
                c.drawArc(RectF((cx - 6f)*s, (cy - 6f)*s, (cx + 6f)*s, (cy + 6f)*s), 205f, 130f, false, paint)
                c.drawArc(RectF((cx - 10f)*s, (cy - 10f)*s, (cx + 10f)*s, (cy + 10f)*s), 25f, 130f, false, paint)
                c.drawArc(RectF((cx - 10f)*s, (cy - 10f)*s, (cx + 10f)*s, (cy + 10f)*s), 205f, 130f, false, paint)
                line(-12f, 0f, -9f, 0f); line(9f, 0f, 12f, 0f)
            }
            "ricochet" -> {
                poly(-9f, 8f, -3f, 1f, 2f, 5f, 8f, -3f, 4f, -3f, 8f, -3f, 8f, 1f)
                line(-9f, -8f, -9f, -2f, 2f)
                line(-6f, -8f, -1f, -8f, 2f)
            }
            "gravity_well" -> {
                val p = Path()
                p.moveTo((cx + 8f) * s, (cy - 6f) * s)
                p.cubicTo((cx + 14f)*s,(cy + 3f)*s,(cx + 3f)*s,(cy + 12f)*s,(cx - 5f)*s,(cy + 7f)*s)
                p.cubicTo((cx - 14f)*s,(cy + 1f)*s,(cx - 7f)*s,(cy - 9f)*s,(cx + 1f)*s,(cy - 6f)*s)
                p.cubicTo((cx + 7f)*s,(cy - 3f)*s,(cx + 3f)*s,(cy + 4f)*s,(cx - 2f)*s,(cy + 2f)*s)
                c.drawPath(p, paint); circle(0f, 0f, 1.4f, true)
            }
            "overload" -> {
                poly(2f, -11f, -7f, 1f, -1f, 1f, -4f, 11f, 8f, -3f, 2f, -3f, 2f, -11f)
                line(-10f, -9f, -7f, -9f); line(8f, 8f, 11f, 8f)
            }
            "nanobots" -> {
                poly(0f, -9f, 6f, -6f, 6f, 1f, 0f, 4f, -6f, 1f, -6f, -6f, 0f, -9f)
                circle(-8f, 7f, 2f); circle(8f, 7f, 2f); circle(0f, 8f, 1.5f, true)
                line(-6f, 2f, -8f, 5f); line(6f, 2f, 8f, 5f)
            }
            "scanner" -> {
                circle(0f, 0f, 7f); circle(0f, 0f, 2f, true)
                line(-11f, -11f, -6f, -11f); line(-11f, -11f, -11f, -6f)
                line(11f, -11f, 6f, -11f); line(11f, -11f, 11f, -6f)
                line(-11f, 11f, -6f, 11f); line(-11f, 11f, -11f, 6f)
                line(11f, 11f, 6f, 11f); line(11f, 11f, 11f, 6f)
            }
            "salvage_drone" -> {
                poly(-10f, -3f, -7f, -7f, 7f, -7f, 10f, -3f, 7f, 4f, -7f, 4f, -10f, -3f)
                line(-6f, 4f, -6f, 8f); line(6f, 4f, 6f, 8f); line(-6f, 8f, 6f, 8f)
                line(0f, -7f, 0f, -10f); circle(0f, -11f, 1.5f, true)
                line(-3f, -1f, 3f, -1f)
            }
            "kill_streak" -> {
                poly(-10f, 8f, -10f, 3f, -5f, 3f, -5f, -2f, 0f, -2f, 0f, -7f, 5f, -7f, 5f, -10f, 10f, -10f)
                line(-10f, 10f, 10f, 10f)
            }
            "ricochet_shield" -> {
                c.drawArc(RectF((cx - 10f)*s, (cy - 10f)*s, (cx + 10f)*s, (cy + 10f)*s), 205f, 125f, false, paint)
                c.drawArc(RectF((cx - 10f)*s, (cy - 10f)*s, (cx + 10f)*s, (cy + 10f)*s), 25f, 125f, false, paint)
                poly(-8f, -8f, -8f, -2f, -2f, -2f)
                poly(8f, 8f, 8f, 2f, 2f, 2f)
                line(-5f, 6f, 5f, -6f, 1.7f)
            }
            "health_regen" -> {
                poly(-10f, -2f, -7f, -7f, -2f, -7f, 0f, -3f, 2f, -7f, 7f, -7f, 10f, -2f, 0f, 9f, -10f, -2f)
                line(-8f, 3f, -4f, 3f); line(-4f, 3f, -2f, 0f); line(-2f, 0f, 1f, 5f); line(1f, 5f, 4f, 2f); line(4f, 2f, 8f, 2f, 1.8f)
            }
            "armor" -> {
                poly(-10f, -7f, -4f, -10f, 4f, -10f, 10f, -7f, 8f, 7f, 0f, 10f, -8f, 7f, -10f, -7f)
                line(-6f, -4f, 6f, -4f, 2f); line(-7f, 0f, 7f, 0f, 2f); line(-5f, 4f, 5f, 4f, 2f)
            }
            "critical" -> {
                circle(0f, 0f, 8f); circle(0f, 0f, 3f)
                poly(0f, -12f, 2f, -4f, 9f, -7f, 4f, -1f, 12f, 2f, 4f, 4f, 7f, 10f, 1f, 6f, -3f, 12f, -4f, 4f, -11f, 7f, -6f, 1f, -12f, -2f, -4f, -4f, 0f, -12f)
            }
            "overdrive" -> {
                poly(-10f, -5f, -3f, -5f, 2f, 0f, -3f, 5f, -10f, 5f, -5f, 0f, -10f, -5f)
                poly(-1f, -5f, 6f, -5f, 11f, 0f, 6f, 5f, -1f, 5f, 4f, 0f, -1f, -5f)
                line(-10f, 9f, 10f, 9f, 0.9f)
            }
            "explosive" -> {
                poly(0f, -11f, 2f, -5f, 7f, -8f, 6f, -2f, 11f, 0f, 6f, 3f, 8f, 9f, 2f, 6f, 0f, 11f, -2f, 6f, -8f, 9f, -6f, 3f, -11f, 0f, -6f, -2f, -8f, -8f, -2f, -5f, 0f, -11f)
                circle(0f, 0f, 2f, true)
            }
            "piercing" -> {
                poly(-10f, -4f, 4f, -4f, 4f, -8f, 11f, 0f, 4f, 8f, 4f, 4f, -10f, 4f, -10f, -4f)
                circle(-3f, 0f, 7f); circle(5f, 0f, 7f)
                line(-3f, -9f, -3f, 9f, 0.8f); line(5f, -9f, 5f, 9f, 0.8f)
            }
            "stasis" -> {
                poly(-8f, -10f, 8f, -10f, 3f, -2f, -3f, -2f, -8f, -10f)
                poly(-8f, 10f, 8f, 10f, 3f, 2f, -3f, 2f, -8f, 10f)
                line(-10f, 0f, -5f, 0f); line(5f, 0f, 10f, 0f)
            }
            "scavenger" -> {
                circle(0f, 0f, 8f)
                circle(0f, 0f, 5f)
                line(-11f, -8f, -6f, -3f); line(-11f, -8f, -4f, -10f)
                line(6f, 8f, 11f, 3f); line(11f, 3f, 11f, 9f)
                line(-3f, 0f, 3f, 0f, 1.8f)
            }
            "multishot" -> {
                poly(-10f, 7f, -7f, -2f, -10f, -1f, -6f, -8f, -2f, -1f, -5f, -2f, -3f, 7f)
                poly(-3f, 7f, 0f, -6f, -3f, -5f, 0f, -11f, 4f, -5f, 1f, -6f, 3f, 7f)
                poly(4f, 7f, 6f, -2f, 3f, -1f, 7f, -8f, 11f, -1f, 8f, -2f, 10f, 7f)
            }
            "phoenix" -> {
                poly(0f, 10f, -6f, 4f, -10f, -4f, -3f, -1f, 0f, -10f, 3f, -1f, 10f, -4f, 6f, 4f, 0f, 10f)
                line(-6f, 4f, 0f, 1f); line(6f, 4f, 0f, 1f)
                line(0f, -1f, 0f, -7f, 1.8f)
            }
            else -> poly(-8f, 0f, 0f, -8f, 8f, 0f, 0f, 8f, -8f, 0f)
        }
        paint.style = Paint.Style.FILL
    }

    private fun drawFittedText(c: Canvas, value: String, x: Float, y: Float, size: Float, color: Int, maxWidth: Float, align: Paint.Align = Paint.Align.CENTER) {
        val safeWidth = max(2f, maxWidth - 2f)
        val widthPx = safeWidth * s
        var fitted = size
        tp.textSize = fitted * s
        while (tp.measureText(value) > widthPx * 0.90f && fitted > 1.75f) {
            fitted = max(1.75f, fitted - 0.15f)
            tp.textSize = fitted * s
        }
        val visibleText = TextUtils.ellipsize(value, tp, widthPx * 0.94f, TextUtils.TruncateAt.END).toString()
        val left = when (align) {
            Paint.Align.LEFT -> x
            Paint.Align.RIGHT -> x - safeWidth
            else -> x - safeWidth / 2f
        }
        c.save()
        c.clipRect(left * s, 0f, (left + safeWidth) * s, sh.toFloat())
        text(c, visibleText, x, y, fitted, color, align)
        c.restore()
    }

    private fun drawWrappedCardText(c: Canvas, value: String, x: Float, y: Float, size: Float, color: Int, maxWidth: Float) {
        tp.textSize = size * s
        val words = value.split(' ')
        val lines = ArrayList<String>()
        var line = ""
        for (word in words) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (line.isNotEmpty() && tp.measureText(candidate) > maxWidth * s * 0.88f) {
                lines.add(line); line = word
            } else line = candidate
        }
        if (line.isNotEmpty()) lines.add(line)
        lines.take(2).forEachIndexed { index, part ->
            c.save()
            c.clipRect((x - maxWidth / 2f) * s, (y - 1.5f + index * 5.0f) * s, (x + maxWidth / 2f) * s, (y + 3.5f + index * 5.0f) * s)
            drawFittedText(c, part, x, y + index * 5.0f, size, color, maxWidth)
            c.restore()
        }
    }

    private fun ownedAbilityIds(): List<String> {
        val all = listOf(
            "firepower", "missile", "laser", "drone", "magnet", "shield", "dash", "chain", "hull",
            "emp", "ricochet", "gravity_well", "overload", "nanobots", "scanner", "salvage_drone",
            "kill_streak", "ricochet_shield", "health_regen", "armor", "critical", "overdrive",
            "explosive", "piercing", "stasis", "scavenger", "multishot", "phoenix"
        )
        return all.filter { abilityRank(it) > 0 || it in selectedAbilities }.distinct()
    }

    private fun drawOwnedAbilityList(
        c: Canvas,
        top: Float,
        bottom: Float,
        columns: Int,
        fontSize: Float,
        left: Float = 7f,
        right: Float = W - 7f
    ) {
        val owned = ownedAbilityIds()
        if (owned.isEmpty()) {
            drawFittedText(c, "هنوز قابلیتی نگرفته‌ای", (left + right) / 2f, (top + bottom) / 2f + 1f, 4.0f, 0xFF9BAABC.toInt(), right - left)
            return
        }
        val safeColumns = columns.coerceIn(1, min(owned.size, 5))
        val usableWidth = max(1f, right - left)
        val cellWidth = usableWidth / safeColumns
        val rows = (owned.size + safeColumns - 1) / safeColumns
        val cellHeight = (bottom - top) / rows
        c.save()
        c.clipRect(left * s, top * s, right * s, bottom * s)
        owned.forEachIndexed { index, id ->
            val col = index % safeColumns
            val row = index / safeColumns
            val cellX = left + col * cellWidth
            val cellTop = top + row * cellHeight
            val cellBottom = min(bottom, cellTop + cellHeight)
            val centerY = (cellTop + cellBottom) / 2f
            val iconX = cellX + min(3.7f, cellWidth * 0.10f)
            val iconRadius = min(2.35f, cellHeight * 0.30f)
            val accent = abilityColor(id)
            c.save()
            c.clipRect(cellX * s, cellTop * s, (cellX + cellWidth) * s, cellBottom * s)
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(34, Color.red(accent), Color.green(accent), Color.blue(accent))
            c.drawRoundRect(
                (cellX + 0.7f) * s, (cellTop + 0.55f) * s,
                (cellX + cellWidth - 0.7f) * s, (cellBottom - 0.55f) * s,
                1.8f * s, 1.8f * s, paint
            )
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.45f * s
            paint.color = Color.argb(120, Color.red(accent), Color.green(accent), Color.blue(accent))
            c.drawCircle(iconX * s, centerY * s, iconRadius * s, paint)
            c.save()
            val iconScale = min(0.20f, iconRadius / 12f)
            c.scale(iconScale, iconScale, iconX * s, centerY * s)
            drawAbilityIcon(c, id, iconX, centerY)
            c.restore()
            paint.style = Paint.Style.FILL
            val label = upgradeTitle(id) + " " + persianNumber(abilityRank(id).coerceIn(0, 5)) + "/۵"
            val labelLeft = cellX + 7.2f
            val labelWidth = max(2f, cellWidth - 8.4f)
            val labelX = labelLeft + labelWidth / 2f
            val labelSize = min(fontSize, min(2.65f, cellHeight * 0.34f)).coerceAtLeast(1.65f)
            drawFittedText(
                c, label, labelX, centerY + 0.9f, labelSize,
                if (abilityRank(id) >= 5) 0xFFFFD45C.toInt() else 0xFFE7F8FF.toInt(),
                labelWidth
            )
            c.restore()
        }
        c.restore()
    }

    private fun drawUpgrade(c: Canvas) {
        paint.color = 0xB807101D.toInt(); c.drawRect(0f, 0f, sw.toFloat(), sh.toFloat(), paint)
        drawGlassPanel(c, 9f, wh * 0.31f, W - 9f, wh * 0.965f, 0xFF47DFFF.toInt(), 232)
        text(c, "یک قابلیت را انتخاب کن", W / 2f, wh * 0.365f, 8.4f, 0xFF9CEBFF.toInt())
        text(c, if (extraAbilitiesUnlocked) "قابلیت‌های تازه باز شده‌اند" else "قابلیت‌های انتخاب‌شده", W / 2f, wh * 0.402f, 5.0f, 0xFFFFD45C.toInt())
        val optionGap = 4f
        val optionWidth = (W - 18f - optionGap * 2f) / 3f
        for (i in 0..2) {
            val id = upgradeChoices.getOrNull(i) ?: continue
            val left = 9f + i * (optionWidth + optionGap)
            val right = left + optionWidth
            val cx = (left + right) / 2f
            c.save()
            c.clipRect(left * s, (wh * 0.425f) * s, right * s, (wh * 0.605f) * s)
            drawGlassPanel(c, left, wh * 0.43f, right, wh * 0.60f, abilityColor(id), 190)
            drawAbilityIcon(c, id, cx, wh * 0.462f)
            val rank = abilityRank(id)
            drawFittedText(c, upgradeTitle(id), cx, wh * 0.512f, 4.9f, WHT, optionWidth - 6f)
            drawFittedText(c, "LVL " + (rank + 1) + "/5", cx, wh * 0.531f, 3.7f, 0xFFFFD45C.toInt(), optionWidth - 8f)
            drawWrappedCardText(c, upgradeInfo(id), cx, wh * 0.557f, 4.0f, 0xFF9CEBFF.toInt(), optionWidth - 8f)
            c.restore()
        }
        drawGlassPanel(c, 9f, wh * 0.615f, W - 9f, wh * 0.825f, 0xFFFFD45C.toInt(), 220)
        drawFittedText(c, "قابلیت‌های گرفته‌شده  •  " + ownedAbilityIds().size, W / 2f, wh * 0.637f, 4.7f, 0xFFFFD45C.toInt(), 180f)
        drawFittedText(c, "توان ویژه: " + legendaryName(), W / 2f, wh * 0.653f, 3.7f, 0xFFFFEBC1.toInt(), 180f)
        drawFittedText(c, legendaryDescription(), W / 2f, wh * 0.667f, 2.9f, 0xFFB9D9E8.toInt(), 180f)
        drawOwnedAbilityList(c, wh * 0.682f, wh * 0.816f, 4, 2.85f, 12f, W - 12f)
        drawButtons(c)
    }

    private fun drawShop(c: Canvas) {
        paint.color = 0xB807101D.toInt(); c.drawRect(0f, 0f, sw.toFloat(), sh.toFloat(), paint)
        drawGlassPanel(c, 25f, wh * 0.28f, 195f, wh * 0.84f, 0xFFFFD45C.toInt(), 232)
        text(c, "آشیانه بین مراحل", W / 2f, wh * 0.34f, 12f, 0xFFFFD45C.toInt())
        text(c, "سکه: " + credits, W / 2f, wh * 0.40f, 9f, WHT)
        drawButtons(c)
        drawAdSlot(c, wh * 0.78f, 138f, 20f)
    }

    private fun shopButtonDescription(id: String): String = when (id) {
        "shop_weapon" -> "افزایش قدرت سلاح و آسیب شلیک"
        "shop_shield" -> "ارتقای سپر و بازیابی مقاومت آن"
        "shop_dash" -> "دریافت یک شارژ جهش اضافه"
        "shop_continue" -> "ادامه نبرد و ورود به موج بعد"
        else -> ""
    }

    private fun drawPause(c: Canvas) {
        paint.style = Paint.Style.FILL
        paint.color = 0xFF07101D.toInt()
        c.drawRect(0f, 0f, sw.toFloat(), sh.toFloat(), paint)
        drawGlassPanel(c, 18f, wh * 0.29f, 202f, wh * 0.93f, 0xFF47DFFF.toInt(), 248)
        text(c, "بازی متوقف شد", W / 2f, wh * 0.335f, 13f, 0xFF9CEBFF.toInt())
        drawCoinCounter(c, 176f, wh * 0.335f)
        drawButtons(c)
        drawFittedText(c, "قابلیت‌های فعال  •  " + ownedAbilityIds().size, W / 2f, wh * 0.675f, 5.9f, 0xFFFFD45C.toInt(), 180f)
        drawOwnedAbilityList(c, wh * 0.70f, wh * 0.915f, 4, 2.8f, 23f, 197f)
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

    private fun drawShipUpgradeIcon(c: Canvas, stat: String, cx: Float, cy: Float) {
        val x = cx * s
        val y = cy * s
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeWidth = 1.45f * s
        when (stat) {
            "attack" -> {
                paint.color = 0xFFFFB64D.toInt()
                c.drawCircle(x, y, 7.2f*s, paint); c.drawCircle(x, y, 2.1f*s, paint)
                c.drawLine(x-11f*s,y,x-4f*s,y,paint); c.drawLine(x+4f*s,y,x+11f*s,y,paint)
                c.drawLine(x,y-11f*s,x,y-4f*s,paint); c.drawLine(x,y+4f*s,x,y+11f*s,paint)
            }
            "armor" -> {
                paint.color = 0xFF69DFFF.toInt()
                val outer = Path().apply { moveTo(x,y-10f*s); lineTo(x+9f*s,y-6f*s); lineTo(x+7f*s,y+4f*s); lineTo(x,y+10f*s); lineTo(x-7f*s,y+4f*s); lineTo(x-9f*s,y-6f*s); close() }
                val inner = Path().apply { moveTo(x,y-5f*s); lineTo(x+4f*s,y-3f*s); lineTo(x+3f*s,y+3f*s); lineTo(x,y+6f*s); lineTo(x-3f*s,y+3f*s); lineTo(x-4f*s,y-3f*s); close() }
                c.drawPath(outer,paint); c.drawPath(inner,paint)
            }
            "mobility" -> {
                paint.color = 0xFFB68CFF.toInt()
                val wing = Path().apply { moveTo(x-10f*s,y+6f*s); lineTo(x-2f*s,y-2f*s); lineTo(x+1f*s,y-9f*s); lineTo(x+4f*s,y-2f*s); lineTo(x+10f*s,y-6f*s); lineTo(x+5f*s,y+3f*s); close() }
                c.drawPath(wing,paint)
                c.drawLine(x-11f*s,y+10f*s,x-3f*s,y+10f*s,paint); c.drawLine(x-8f*s,y+7f*s,x-2f*s,y+7f*s,paint)
            }
            "hull" -> {
                paint.color = 0xFFFF789D.toInt()
                val heart = Path().apply {
                    moveTo(x,y+9f*s)
                    cubicTo(x-3f*s,y+6f*s,x-11f*s,y+1f*s,x-8f*s,y-5f*s)
                    cubicTo(x-6f*s,y-10f*s,x-1f*s,y-8f*s,x,y-4f*s)
                    cubicTo(x+2f*s,y-9f*s,x+7f*s,y-10f*s,x+9f*s,y-5f*s)
                    cubicTo(x+11f*s,y+1f*s,x+3f*s,y+7f*s,x,y+9f*s)
                }
                c.drawPath(heart,paint); c.drawLine(x-3f*s,y,x+3f*s,y,paint); c.drawLine(x,y-3f*s,x,y+3f*s,paint)
            }
            "gold" -> {
                paint.color = 0xFFFFD45C.toInt()
                val star = Path()
                for (i in 0 until 10) {
                    val angle = -PI / 2.0 + i * PI / 5.0
                    val radius = if (i % 2 == 0) 10f else 4f
                    val px = x + cos(angle).toFloat() * radius * s
                    val py = y + sin(angle).toFloat() * radius * s
                    if (i == 0) star.moveTo(px,py) else star.lineTo(px,py)
                }
                star.close(); c.drawPath(star,paint); c.drawCircle(x,y,2f*s,paint)
            }
        }
        paint.style = Paint.Style.FILL
    }

    private fun drawButtonIcon(c: Canvas, id: String, cx: Float, cy: Float) {
        val color = when { id.startsWith("shop_") -> 0xFFFFD45C.toInt(); id.startsWith("upgrade_") -> 0xFF55DFFF.toInt(); id == "about" || id == "telegram" -> 0xFF55DFFF.toInt(); else -> 0xFF9CEBFF.toInt() }
        paint.style = Paint.Style.STROKE; paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
        paint.strokeWidth = 1.8f * s; paint.color = color
        val x = cx * s; val y = cy * s
        if (id == "upgrade_refresh") {
            val rect = RectF(x-8f*s,y-8f*s,x+8f*s,y+8f*s)
            c.drawArc(rect,205f,245f,false,paint)
            c.drawLine(x-7f*s,y-4f*s,x-7f*s,y+1f*s,paint)
            c.drawLine(x-7f*s,y-4f*s,x-2f*s,y-4f*s,paint)
            c.drawLine(x+7f*s,y+4f*s,x+7f*s,y-1f*s,paint)
            c.drawLine(x+7f*s,y+4f*s,x+2f*s,y+4f*s,paint)
            paint.style=Paint.Style.FILL
            return
        }
        if (id.startsWith("upgrade_")) {
            c.drawLine(x, y - 8f * s, x, y + 3f * s, paint)
            c.drawLine(x, y + 3f * s, x - 4.5f * s, y - 1.5f * s, paint)
            c.drawLine(x, y + 3f * s, x + 4.5f * s, y - 1.5f * s, paint)
            c.drawLine(x - 8f * s, y + 7f * s, x + 8f * s, y + 7f * s, paint)
            paint.style = Paint.Style.FILL
            return
        }
        if (id.startsWith("shipup_")) {
            drawShipUpgradeIcon(c, id.substringAfter("_"), cx, cy)
            return
        }
        if (id.startsWith("ship_")) {
            val shipId = id.substringAfterLast("_").toIntOrNull() ?: 0
            c.save()
            c.scale(0.52f, 0.52f, x, y)
            drawPlayerShip(c, cx, cy, shipId)
            c.restore()
            return
        }
        when (id) {
            "play", "resume", "shop_continue" -> { val p=Path().apply { moveTo(x-6f*s,y-9f*s); lineTo(x+9f*s,y); lineTo(x-6f*s,y+9f*s); close() }; paint.style=Paint.Style.FILL; c.drawPath(p,paint) }
            "sound", "audio" -> { val p=Path().apply { moveTo(x-10f*s,y-4f*s); lineTo(x-5f*s,y-4f*s); lineTo(x+1f*s,y-9f*s); lineTo(x+1f*s,y+9f*s); lineTo(x-5f*s,y+4f*s); lineTo(x-10f*s,y+4f*s); close() }; paint.style=Paint.Style.FILL; c.drawPath(p,paint); paint.style=Paint.Style.STROKE; c.drawArc(x-4f*s,y-9f*s,x+13f*s,y+9f*s,-60f,120f,false,paint); c.drawArc(x-7f*s,y-13f*s,x+18f*s,y+13f*s,-60f,120f,false,paint) }
            "ship" -> { val p=Path().apply { moveTo(x,y-11f*s); lineTo(x+8f*s,y+9f*s); lineTo(x,y+5f*s); lineTo(x-8f*s,y+9f*s); close() }; paint.style=Paint.Style.FILL; c.drawPath(p,paint); paint.style=Paint.Style.STROKE; c.drawLine(x-11f*s,y+3f*s,x-6f*s,y+5f*s,paint); c.drawLine(x+11f*s,y+3f*s,x+6f*s,y+5f*s,paint) }
            "about" -> { c.drawCircle(x,y,9f*s,paint); paint.style=Paint.Style.FILL; c.drawCircle(x,y-5f*s,1.4f*s,paint); c.drawRoundRect(x-1.5f*s,y-1f*s,x+1.5f*s,y+6f*s,1f*s,1f*s,paint) }
            "aboutBack", "audioBack", "menu", "shipBack", "shipUpgradeBack" -> { if(id=="menu") { for(row in -1..1) for(col in -1..1) c.drawCircle(x+col*6f*s,y+row*6f*s,1.9f*s,paint) } else { c.drawLine(x+9f*s,y,x-8f*s,y,paint); c.drawLine(x-8f*s,y,x-1f*s,y-7f*s,paint); c.drawLine(x-8f*s,y,x-1f*s,y+7f*s,paint) } }
            "pauseRestart" -> { c.drawArc(x-9f*s,y-9f*s,x+9f*s,y+9f*s,35f,285f,false,paint); c.drawLine(x+7f*s,y-8f*s,x+10f*s,y-1f*s,paint); c.drawLine(x+7f*s,y-8f*s,x,y-8f*s,paint) }
            "telegram" -> { val p=Path().apply { moveTo(x-11f*s,y-2f*s); lineTo(x+11f*s,y-8f*s); lineTo(x+4f*s,y+10f*s); lineTo(x,y+3f*s); close() }; paint.style=Paint.Style.FILL; c.drawPath(p,paint); paint.style=Paint.Style.STROKE; c.drawLine(x,y+3f*s,x+7f*s,y-4f*s,paint) }
            "dailyAd" -> { c.drawCircle(x,y,8f*s,paint); c.drawCircle(x,y,5f*s,paint); c.drawLine(x,y-3f*s,x,y+3f*s,paint) }
            "extraLife", "shop_repair" -> { val p=Path().apply { moveTo(x,y+9f*s); cubicTo(x-17f*s,y-2f*s,x-8f*s,y-13f*s,x,y-6f*s); cubicTo(x+8f*s,y-13f*s,x+17f*s,y-2f*s,x,y+9f*s) }; c.drawPath(p,paint) }
            "shop_weapon" -> {
                c.drawCircle(x,y,5.5f*s,paint)
                c.drawCircle(x,y,1.8f*s,paint)
                c.drawLine(x-11f*s,y,x-7f*s,y,paint); c.drawLine(x+7f*s,y,x+11f*s,y,paint)
                c.drawLine(x,y-11f*s,x,y-7f*s,paint); c.drawLine(x,y+7f*s,x,y+11f*s,paint)
            }
            "shop_shield" -> { val p=Path().apply { moveTo(x,y-10f*s); lineTo(x+9f*s,y-6f*s); lineTo(x+7f*s,y+4f*s); lineTo(x,y+11f*s); lineTo(x-7f*s,y+4f*s); lineTo(x-9f*s,y-6f*s); close() }; c.drawPath(p,paint) }
            "shop_dash" -> {
                val bolt = Path().apply {
                    moveTo(x+1f*s,y-11f*s); lineTo(x-7f*s,y+1f*s); lineTo(x-1f*s,y+1f*s)
                    lineTo(x-4f*s,y+11f*s); lineTo(x+8f*s,y-3f*s); lineTo(x+2f*s,y-3f*s); close()
                }
                paint.style = Paint.Style.FILL
                c.drawPath(bolt,paint)
                paint.style = Paint.Style.STROKE
            }
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
            } else if (st == St.AUDIO_SETTINGS) {
                val cy = (r.top + r.bottom) / 2f
                drawButtonIcon(c, b.id, r.left + 18f, cy)
                drawFittedText(c, b.label, (r.left+r.right)/2f + 8f, cy+2f, 5.8f, 0xFFEAFBFF.toInt(), 80f)
            } else if (st == St.UPGRADE) {
                val cy = (r.top + r.bottom) / 2f
                drawButtonIcon(c, b.id, r.left + 15f, cy)
                drawFittedText(c, b.label, (r.left+r.right)/2f + 8f, cy+2f, 4.8f, 0xFFB9F4FF.toInt(), 82f)
            } else if (st == St.SHOP) {
                val cy = (r.top + r.bottom) / 2f
                val centerX = (r.left + r.right) / 2f + 17f
                c.save()
                c.scale(0.78f, 0.78f, (r.left + 17f) * s, cy * s)
                drawButtonIcon(c, b.id, r.left + 17f, cy)
                c.restore()
                drawFittedText(c, b.label, centerX, cy - 1.7f, 4.4f, 0xFFFFF0C4.toInt(), 82f)
                drawFittedText(c, shopButtonDescription(b.id), centerX, cy + 6.6f, 3.25f, 0xFFE7D5A8.toInt(), 82f)
            } else if (st == St.SHIPS && b.id.startsWith("ship_")) {
                val cy = (r.top + r.bottom) / 2f
                val shipId = b.id.substringAfterLast("_").toIntOrNull() ?: 0
                c.save()
                c.scale(0.90f, 0.90f, (r.left + 18f) * s, cy * s)
                drawButtonIcon(c, b.id, r.left + 18f, cy)
                c.restore()
                val centerX = (r.left + r.right) / 2f + 11f
                val titleColor = if (currentShip == shipId) 0xFFFFD45C.toInt() else 0xFFEAFBFF.toInt()
                drawFittedText(c, shipName(shipId), centerX, cy - 1.9f, 5.5f, titleColor, 142f)
                val state = when {
                    !shipOwned(shipId) -> "قیمت: " + shipPrice(shipId) + " سکه"
                    currentShip == shipId -> "فعال • " + shipDescription(shipId)
                    else -> "آزادشده • " + shipDescription(shipId)
                }
                drawFittedText(c, state, centerX, cy + 7.0f, 3.9f, 0xFF9CEBFF.toInt(), 142f)
            } else if ((st == St.SHIPS || st == St.SHIP_UPGRADE) && (b.id.startsWith("ship_") || b.id.startsWith("shipup_") || b.id == "shipBack" || b.id == "shipUpgradeBack")) {
                val cy = (r.top + r.bottom) / 2f
                c.save()
                c.scale(0.82f, 0.82f, (r.left + 18f) * s, cy * s)
                drawButtonIcon(c, b.id, r.left + 18f, cy)
                c.restore()
                if (st == St.SHIP_UPGRADE && b.id.startsWith("shipup_")) {
                    val stat = b.id.substringAfter("shipup_")
                    drawFittedText(c, b.label, (r.left + r.right) / 2f + 13f, cy - 2.2f, 4.15f, 0xFFEAFBFF.toInt(), 136f)
                    drawFittedText(c, shipUpgradeDescription(stat), (r.left + r.right) / 2f + 13f, cy + 6.0f, 3.35f, 0xFF9CEBFF.toInt(), 136f)
                } else {
                    drawFittedText(c, b.label, (r.left + r.right) / 2f + 13f, cy + 2f, 4.5f, 0xFFEAFBFF.toInt(), 136f)
                }
            } else if (st == St.PAUSE) {
                val cy = (r.top + r.bottom) / 2f
                c.save()
                c.scale(0.82f, 0.82f, (r.left + 18f) * s, cy * s)
                drawButtonIcon(c, b.id, r.left + 18f, cy)
                c.restore()
                drawFittedText(c, b.label, (r.left + r.right) / 2f + 8f, cy + 2f, 5.2f, 0xFFEAFBFF.toInt(), 76f)
            } else {
                val cx = (r.left + r.right) / 2f
                val cy = r.top + 11f
                c.save()
                c.scale(0.82f, 0.82f, cx * s, cy * s)
                drawButtonIcon(c, b.id, cx, cy)
                c.restore()
            }
        }
    }

    private fun drawPlayerShip(c: Canvas, x: Float, y: Float, shipId: Int = currentShip) {
        // Real spacecraft silhouettes, retaining the original palette and neon bloom.
        val colors = intArrayOf(0xFFFFD34F.toInt(), 0xFF45E6FF.toInt(), 0xFFFF8758.toInt(), 0xFFB78CFF.toInt(), 0xFFFF6EB5.toInt())
        val glows = intArrayOf(0xFFFFFFA8.toInt(), 0xFFB8F7FF.toInt(), 0xFFFFD0A6.toInt(), 0xFFE3D2FF.toInt(), 0xFFFFC3E4.toInt())
        val id = shipId.coerceIn(0, 4)
        val color = colors[id]
        val glow = glows[id]
        c.save()
        c.translate(x * s, y * s)
        c.scale(s, s)

        val filled = Path()
        val silhouette = Path()
        val details = Path()
        fun polygon(path: Path, vararg xy: Float) {
            if (xy.size < 6 || xy.size % 2 != 0) return
            path.moveTo(xy[0], xy[1])
            var i = 2
            while (i < xy.size) { path.lineTo(xy[i], xy[i + 1]); i += 2 }
            path.close()
        }
        fun panel(left: Float, top: Float, right: Float, bottom: Float, radius: Float = 1.2f) {
            val p = Path().apply { addRoundRect(RectF(left, top, right, bottom), radius, radius, Path.Direction.CW) }
            filled.addPath(p); silhouette.addPath(p)
        }
        fun hex(path: Path, cx: Float, cy: Float, radius: Float, angle: Float = -PI.toFloat() / 2f) {
            for (i in 0 until 6) {
                val a = angle + i * PI / 3.0
                val px = cx + cos(a).toFloat() * radius
                val py = cy + sin(a).toFloat() * radius
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close()
        }
        fun line(path: Path, x1: Float, y1: Float, x2: Float, y2: Float) {
            path.moveTo(x1, y1); path.lineTo(x2, y2)
        }
        fun circle(path: Path, cx: Float, cy: Float, radius: Float) {
            path.addCircle(cx, cy, radius, Path.Direction.CW)
        }

        when (id) {
            0 -> { // Hubble: optical telescope body and paired solar arrays.
                panel(-4.6f, -13f, 4.6f, 11f, 3.8f)
                panel(-21f, -2.5f, -7f, 5.5f)
                panel(7f, -2.5f, 21f, 5.5f)
                circle(filled, 0f, -10f, 5.2f); circle(silhouette, 0f, -10f, 5.2f)
                line(details, -7f,1.5f, -4f,1.5f); line(details, 4f,1.5f, 7f,1.5f)
                for (px in listOf(-18f,-14f,-10f,10f,14f,18f)) line(details,px,-2f,px,5f)
                line(details,-20f,1.5f,-8f,1.5f); line(details,8f,1.5f,20f,1.5f)
                line(details,-3f,-7f,3f,-7f); line(details,-3f,-1f,3f,-1f); line(details,-3f,7f,3f,7f)
                // Hubble optical baffle rings, antenna dish, star tracker and array cell grid.
                line(details,-4.4f,-9f,4.4f,-9f); line(details,-4.4f,-5.5f,4.4f,-5.5f)
                line(details,-4.2f,4f,4.2f,4f)
                line(silhouette,4.5f,-6f,10f,-10f); circle(silhouette,11f,-11f,2.4f)
                line(details,8.5f,-10f,11.8f,-12.2f)
                line(silhouette,-4f,5f,-9f,11f); circle(silhouette,-10f,12f,1.5f)
                for (py in listOf(-1f,2f,4.5f)) { line(details,-20f,py,-8f,py); line(details,8f,py,20f,py) }
                line(details,-1.5f,-13f,-1.5f,-15f); line(details,1.5f,-13f,1.5f,-15f)
            }
            1 -> { // James Webb: segmented hexagonal mirror above its layered sunshield.
                polygon(filled,-13f,-3f, -9f,-7f, 0f,-10f, 9f,-7f, 13f,-3f, 9f,10f, 0f,15f, -9f,10f)
                val mirror = Path(); hex(mirror,0f,-12f,8.2f)
                filled.addPath(mirror); silhouette.addPath(mirror)
                panel(-3.2f,-3f,3.2f,8f,1.4f)
                hex(details,0f,-12f,5.1f)
                line(details,-4.1f,-15.6f,4.1f,-8.4f); line(details,-4.1f,-8.4f,4.1f,-15.6f)
                line(details,-10f,-3f,10f,-3f); line(details,-9f,0f,9f,0f)
                line(details,-8f,4f,8f,4f); line(details,-6f,8f,6f,8f)
                line(details,-2f,9f,2f,9f)
                // Six visible primary-mirror segments, secondary mirror struts and sunshield spars.
                for (segment in 0 until 6) {
                    val angle = -PI / 2.0 + segment * PI / 3.0
                    val sx = cos(angle).toFloat() * 4.5f
                    val sy = -12f + sin(angle).toFloat() * 4.5f
                    hex(details, sx, sy, 1.85f, -PI.toFloat()/2f)
                }
                line(details,-1f,-19f,0f,-16f); line(details,1f,-19f,0f,-16f)
                line(silhouette,-1f,-19f,1f,-19f); circle(silhouette,0f,-20f,1.4f)
                line(details,-13f,-1f,-17f,-7f); line(details,13f,-1f,17f,-7f)
                line(details,-17f,-7f,-14f,-6f); line(details,17f,-7f,14f,-6f)
                line(details,-6f,11f,-9f,14f); line(details,6f,11f,9f,14f)
            }
            2 -> { // Voyager 1: high-gain dish, instrument booms and RTG boom.
                panel(-3.8f,-5f,3.8f,6f,1.1f)
                circle(filled,0f,-10.2f,9.2f); circle(silhouette,0f,-10.2f,9.2f)
                line(details,-6f,-14f,0f,-6f); line(details,6f,-14f,0f,-6f)
                line(details,0f,-19f,0f,-16f); line(details,-3.5f,-10.2f,3.5f,-10.2f)
                line(details,0f,-13.7f,0f,-6.7f)
                line(silhouette,-3f,5f,10f,14f); panel(8f,11f,12f,16f,0.8f); panel(13f,14f,17f,18f,0.8f)
                line(silhouette,-4f,0f,-17f,5f); circle(silhouette,-18f,5.5f,2.1f)
                line(details,-3f,-2f,3f,-2f); line(details,-3f,2f,3f,2f)
                // High-gain dish ribs, equipment deck, science boom and radioisotope generators.
                circle(details,0f,-10.2f,6.6f)
                for (angle in 0 until 8) {
                    val a = angle * PI / 4.0
                    line(details,0f,-10.2f,cos(a).toFloat()*8.7f,-10.2f+sin(a).toFloat()*8.7f)
                }
                line(details,-2f,-4f,-2f,5f); line(details,2f,-4f,2f,5f)
                line(details,-3.8f,0f,3.8f,0f); line(details,-3.8f,4f,3.8f,4f)
                line(details,10f,14f,14f,17f); line(details,14f,17f,18f,17f)
                line(details,-17f,5.5f,-21f,7f); circle(silhouette,-22f,7.5f,1.5f)
                panel(16f,15f,20f,19f,0.7f); line(details,16f,16f,20f,16f)
            }
            3 -> { // Voyager 2: same spacecraft family, with a distinct dish and boom orientation.
                panel(-3.8f,-4f,3.8f,7f,1.1f)
                circle(filled,-2.8f,-9.4f,8.9f); circle(silhouette,-2.8f,-9.4f,8.9f)
                line(details,-8.5f,-14.5f,-2.8f,-5.5f); line(details,3f,-14.5f,-2.8f,-5.5f)
                line(details,-2.8f,-18.3f,-2.8f,-15.5f); line(details,-6.2f,-9.4f,0.7f,-9.4f)
                line(details,-2.8f,-12.8f,-2.8f,-6.2f)
                line(silhouette,3f,5f,-11f,15f); panel(-14f,13f,-10f,18f,0.8f); panel(-19f,15f,-15f,19f,0.8f)
                line(silhouette,4f,0f,18f,4f); circle(silhouette,19f,4.5f,2.1f)
                line(details,0f,-1f,5f,-1f); line(details,0f,3f,5f,3f)
                // Separate dish ribs, instrument housing bands and the angled RTG boom.
                circle(details,-2.8f,-9.4f,6.2f)
                for (angle in 0 until 8) {
                    val a = angle * PI / 4.0
                    line(details,-2.8f,-9.4f,-2.8f+cos(a).toFloat()*8.4f,-9.4f+sin(a).toFloat()*8.4f)
                }
                line(details,-3.8f,-1f,3.8f,-1f); line(details,-3.8f,3f,3.8f,3f)
                line(details,3f,5f,-8f,12f); line(details,-8f,12f,-12f,16f)
                line(details,-14f,14f,-14f,18f); line(details,-19f,16f,-19f,19f)
                line(details,4f,0f,14f,3f); circle(silhouette,15.5f,3.5f,1.5f)
            }
            else -> { // Phoenix Mars lander: hexagonal deck, side solar panels and three legs.
                val deck = Path(); hex(deck,0f,-1f,8.3f)
                filled.addPath(deck); silhouette.addPath(deck)
                panel(-20f,-5f,-9f,3f,1f); panel(9f,-5f,20f,3f,1f)
                line(silhouette,-8f,0f,-15f,12f); line(silhouette,8f,0f,15f,12f); line(silhouette,0f,6f,0f,17f)
                line(details,-15f,8f,-12f,8f); line(details,12f,8f,15f,8f)
                line(details,-2f,-4f,2f,-4f); line(details,-3f,0f,3f,0f)
                line(details,-18f,-3f,-18f,1f); line(details,-14f,-3f,-14f,1f)
                line(details,14f,-3f,14f,1f); line(details,18f,-3f,18f,1f)
                line(details,-8f,-1f,-12f,-8f); circle(silhouette,-12f,-9f,1.8f)
                // Phoenix weather mast, robotic sampling arm, scoop and solar-cell rows.
                line(silhouette,0f,-8f,0f,-16f); circle(silhouette,0f,-17f,1.8f)
                line(details,-2f,-8f,2f,-8f); line(details,-1.5f,-12f,1.5f,-12f)
                line(silhouette,6f,-2f,13f,-10f); circle(silhouette,14f,-11f,1.7f)
                line(silhouette,14f,-11f,18f,-15f); line(silhouette,18f,-15f,20f,-14f)
                line(details,-17f,-5f,-17f,3f); line(details,-13f,-5f,-13f,3f)
                line(details,13f,-5f,13f,3f); line(details,17f,-5f,17f,3f)
                line(details,-18f,-1f,-10f,-1f); line(details,10f,-1f,18f,-1f)
                line(details,-7f,-7f,7f,-7f); line(details,-6f,5f,6f,5f)
                line(silhouette,-15f,12f,-18f,15f); line(silhouette,15f,12f,18f,15f)
                line(details,-19f,15f,-16f,15f); line(details,16f,15f,19f,15f)
            }
        }

        silhouette.addPath(filled)
        // Original glow width, blur radius, colors and layered outline are intentionally preserved.
        paint.style = Paint.Style.STROKE; paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
        paint.strokeWidth = 7f
        paint.color = Color.argb(68, Color.red(color), Color.green(color), Color.blue(color))
        paint.maskFilter = BlurMaskFilter(7.5f, BlurMaskFilter.Blur.NORMAL)
        c.drawPath(silhouette, paint)
        paint.maskFilter = null; paint.strokeWidth = 2.2f
        paint.color = Color.argb(115, Color.red(glow), Color.green(glow), Color.blue(glow))
        c.drawPath(silhouette, paint)

        paint.style = Paint.Style.FILL; paint.color = color; c.drawPath(filled, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.15f; paint.color = glow
        c.drawPath(details, paint); c.drawPath(filled, paint)
        paint.style = Paint.Style.FILL; paint.color = glow
        when (id) {
            0 -> c.drawCircle(0f,-10f,2.1f,paint)
            1 -> c.drawCircle(0f,-12f,1.6f,paint)
            2 -> c.drawCircle(0f,-10.2f,1.6f,paint)
            3 -> c.drawCircle(-2.8f,-9.4f,1.6f,paint)
            else -> c.drawCircle(0f,-1f,1.5f,paint)
        }
        c.restore()
    }

    private fun drawShipHangar(c: Canvas) {
        paint.color = 0xE807101D.toInt(); c.drawRect(0f,0f,sw.toFloat(),sh.toFloat(),paint)
        drawGlassPanel(c,8f,wh*0.255f,W-8f,wh*0.95f,0xFFFFD45C.toInt(),244)
        text(c,"آشیانه ناوگان",W/2f,wh*0.32f,12f,0xFFFFD45C.toInt())
        text(c,"سکه: $credits",W/2f,wh*0.35f,7f,WHT)
        drawButtons(c)
    }

    private fun shipUpgradeDescription(stat: String): String = when (stat) {
        "attack" -> "سرعت شلیک ۴٫۵٪ بیشتر در هر سطح"
        "armor" -> "۵٫۵٪ شانس خنثی‌کردن ضربه در هر سطح"
        "mobility" -> "سرعت حرکت ۵٫۵٪ بیشتر در هر سطح"
        "hull" -> "یک جان اضافه در شروع هر بازی به‌ازای هر سطح"
        "gold" -> "افزایش قدرت مهارت طلایی کاوشگر؛ زمان اثر بیشتر"
        else -> ""
    }

    private fun drawShipUpgrades(c: Canvas) {
        paint.color = 0xE807101D.toInt(); c.drawRect(0f,0f,sw.toFloat(),sh.toFloat(),paint)
        drawGlassPanel(c,5f,wh*0.27f,W-5f,wh*0.86f,0xFF47DFFF.toInt(),244)
        text(c,"کارگاه ارتقای کاوشگر",W/2f,wh*0.32f,11f,0xFF9CEBFF.toInt())
        text(c,shipName()+"  •  امتیاز ارتقای باس: $upgradePoints",W/2f,wh*0.35f,6.1f,0xFFFFD45C.toInt())
        text(c,"هر ارتقا فقط روی همین کاوشگر اعمال می‌شود",W/2f,wh*0.382f,4.5f,0xFF9CEBFF.toInt())
        drawButtons(c)
    }

    private fun drawCoinCounter(c: Canvas, centerX: Float, centerY: Float) {
        val left = centerX - 19f
        val top = centerY - 7.5f
        paint.style = Paint.Style.FILL
        paint.color = 0xCC101B2A.toInt()
        c.drawRoundRect(left * s, top * s, (left + 38f) * s, (top + 15f) * s, 5f * s, 5f * s, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.9f * s
        paint.color = 0xFFFFD45C.toInt()
        c.drawRoundRect(left * s, top * s, (left + 38f) * s, (top + 15f) * s, 5f * s, 5f * s, paint)
        paint.style = Paint.Style.FILL
        paint.color = 0xFFFFD45C.toInt()
        c.drawCircle((left + 7f) * s, centerY * s, 3.5f * s, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.65f * s
        paint.color = 0xFFFFF0A6.toInt()
        c.drawCircle((left + 7f) * s, centerY * s, 2.2f * s, paint)
        paint.style = Paint.Style.FILL
        drawFittedText(c, credits.toString(), left + 24f, centerY + 2f, 5.2f, 0xFFFFE8A6.toInt(), 19f)
    }

    private fun drawMenu(c: Canvas) {
        drawGlassPanel(c, 20f, wh * 0.35f, 200f, wh * 0.88f, 0xFF47DFFF.toInt(), 224)
        text(c, "نئون", W / 2, wh * 0.16f, 34f, YEL)
        text(c, "شوتر", W / 2, wh * 0.16f + 26f, 24f, RED)
        drawPlayerShip(c, W / 2, wh * 0.33f + sin(System.nanoTime() / 4e8f) * 3f)
        text(c, "کاوشگر: " + shipName(), W / 2f, wh * 0.375f, 6.2f, 0xFF80E7FF.toInt())
        drawCoinCounter(c, 177f, wh * 0.375f)
        text(c, if (dailyDone) "ماموریت روزانه کامل شد: +۵۰۰" else "ماموریت روزانه: ۲۵ نابودی " + dailyKills + "/۲۵", W / 2f, wh * 0.397f, 6f, 0xFFFFD45C.toInt())
        text(c, "بهترین امتیاز: $best   رکورد مرحله: $bestWave", W / 2, wh * 0.419f, 7f, WHT)
        drawButtons(c)
        if (adStatusTimer > 0f) drawFittedText(c, adStatusText, W / 2f, wh * 0.868f, 5.0f, 0xFFFFD45C.toInt(), 180f)
        drawAdSlot(c, wh * 0.946f)
    }

    private fun drawAudioSlider(c: Canvas, y: Float, value: Float, label: String, color: Int) {
        drawFittedText(c, label, 110f, y - 18f, 6.8f, 0xFFEAFBFF.toInt(), 150f)
        drawFittedText(c, "\${(value * 100f).roundToInt()}٪", 190f, y - 18f, 5.5f, color, 26f)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3.2f * s
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = 0xFF465769.toInt()
        c.drawLine(30f*s, y*s, 190f*s, y*s, paint)
        paint.color = color
        c.drawLine(30f*s, y*s, (30f+160f*value)*s, y*s, paint)
        paint.style = Paint.Style.FILL
        paint.color = 0xFF0B1B2B.toInt()
        c.drawCircle((30f+160f*value)*s, y*s, 7f*s, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.6f*s
        paint.color = color
        c.drawCircle((30f+160f*value)*s, y*s, 7f*s, paint)
        paint.style = Paint.Style.FILL
    }
    private fun drawAudioSettings(c: Canvas) {
        paint.color = 0xE807101D.toInt()
        c.drawRect(0f,0f,sw.toFloat(),sh.toFloat(),paint)
        drawGlassPanel(c,10f,wh*0.28f,W-10f,wh*0.88f,0xFF47DFFF.toInt(),242)
        text(c,"تنظیم جداگانه صدا",W/2f,wh*0.36f,13f,0xFF9CEBFF.toInt())
        drawAudioSlider(c,wh*0.485f,musicVolume,"موسیقی",0xFF47DFFF.toInt())
        drawAudioSlider(c,wh*0.655f,sfxVolume,"افکت‌ها (SFX)",0xFFFF8F70.toInt())
        text(c,"هر صدا مستقل ذخیره می‌شود",W/2f,wh*0.72f,5.1f,0xFF9CEBFF.toInt())
        drawButtons(c)
    }

    private fun drawAbout(c: Canvas) {
        paint.color = 0xFF07101D.toInt(); c.drawRect(0f, 0f, sw.toFloat(), sh.toFloat(), paint)
        drawGlassPanel(c, 17f, wh * 0.28f, 203f, wh * 0.80f, 0xFF47DFFF.toInt(), 244)
        text(c, "درباره نئون شوتر", W / 2f, wh * 0.36f, 16f, 0xFF9CEBFF.toInt())
        text(c, "در موج‌های بی‌پایان دشمنان دوام بیاور،", W / 2f, wh * 0.43f, 6.4f, WHT)
        text(c, "قدرت‌هایت را ارتقا بده و باس‌ها را شکست بده.", W / 2f, wh * 0.46f, 6.4f, WHT)
        text(c, "پنج کاوشگر با توانایی‌های طلایی متفاوت", W / 2f, wh * 0.49f, 6.4f, WHT)
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

