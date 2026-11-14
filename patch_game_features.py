#!/usr/bin/env python3
from pathlib import Path
import re
import zipfile

root = Path("NeonShooter")
archive = next(Path(".").glob("NeonShooter_*.zip"))
game_file = root / "app/src/main/java/com/claude/neonshooter/GameView.kt"
gradle_file = root / "app/build.gradle.kts"
g = game_file.read_text(encoding="utf-8")

def rep(old, new, label):
    global g
    if new in g:
        return
    if old not in g:
        raise RuntimeError("Gameplay feature patch anchor missing: " + label)
    g = g.replace(old, new, 1)

# ---- progression states, mutable projectiles, enemy roles and effects ----
rep("private enum class St { MENU, PLAY, PAUSE, OVER }",
    "private enum class St { MENU, PLAY, PAUSE, OVER, UPGRADE, SHOP }", "game states")
rep("private class B(var x: Float, var y: Float, var vx: Float, var vy: Float, val mine: Boolean)",
    "private class B(var x: Float, var y: Float, var vx: Float, var vy: Float, var mine: Boolean)", "reflectable projectiles")
rep("        var fire = Random.nextFloat() * 2f + 1f\n",
    "        var fire = Random.nextFloat() * 2f + 1f\n        var healTimer = 2.2f\n        var phase = 1\n        var attackWarned = false\n        var warning = 0f\n", "enemy state")
rep("    private class Comet(var x: Float, var y: Float, val vx: Float, val vy: Float, val length: Float, var life: Float, val maxLife: Float)\n",
    "    private class Comet(var x: Float, var y: Float, val vx: Float, val vy: Float, val length: Float, var life: Float, val maxLife: Float)\n    private class Arc(val x1: Float, val y1: Float, val x2: Float, val y2: Float, var life: Float = 0.24f)\n", "chain arc class")

rep("    private var kills = 0\n    private val unlocked = HashSet<Int>()",
"""    private var kills = 0
    private var credits = prefs.getInt("credits", 0)
    private var bestWave = prefs.getInt("best_wave", 0)
    private var currentShip = prefs.getInt("ship", 0).coerceIn(0, 3)
    private var dailyDay = prefs.getLong("daily_day", -1L)
    private var dailyKills = prefs.getInt("daily_kills", 0)
    private var dailyDone = prefs.getBoolean("daily_done", false)
    private var upgradeChoices = emptyList<String>()
    private var upgradesTaken = 0
    private var intermissionHandledWave = -1
    private var bonusDamage = 0
    private var fireRateBonus = 0f
    private var magnetLevel = 0
    private var shieldLevel = 0
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
    private val unlocked = HashSet<Int>()""", "progression fields")

rep("    init { holder.addCallback(this) }",
    "    init { refreshDaily(); holder.addCallback(this) }", "daily mission initialization")

# ---- daily mission, ship classes, upgrade deck and shop ----
anchor = "    private fun onBtn(id: String) {"
insert = """    private fun refreshDaily() {
        val today = System.currentTimeMillis() / 86_400_000L
        if (dailyDay != today) {
            dailyDay = today
            dailyKills = 0
            dailyDone = false
            prefs.edit().putLong("daily_day", today).putInt("daily_kills", 0).putBoolean("daily_done", false).apply()
        }
    }

    private fun shipName(): String = when (currentShip) {
        1 -> "VIPER"
        2 -> "TITAN"
        3 -> "PHANTOM"
        else -> "STRIKER"
    }

    private fun shipMoveMultiplier(): Float = when (currentShip) {
        1 -> 1.22f
        2 -> 0.86f
        3 -> 1.04f
        else -> 1f
    }

    private fun shipFireDelay(): Float = when (currentShip) {
        1 -> 0.92f
        2 -> 1.12f
        3 -> 0.82f
        else -> 1f
    }

    private fun cycleShip() {
        val highestUnlocked = (bestWave / 10).coerceIn(0, 3)
        currentShip = (currentShip + 1) % (highestUnlocked + 1)
        prefs.edit().putInt("ship", currentShip).apply()
    }

    private fun prepareUpgradeChoices() {
        val pool = listOf("firepower", "missile", "laser", "drone", "magnet", "shield", "dash", "chain", "hull", "rapid")
        upgradeChoices = pool.shuffled().take(3)
    }

    private fun applyUpgrade(id: String) {
        when (id) {
            "firepower" -> { weaponLevel = min(5, weaponLevel + 1); bonusDamage++ ; fireRateBonus = min(0.28f, fireRateBonus + 0.035f) }
            "missile" -> missileLevel = min(3, missileLevel + 1)
            "laser" -> laserLevel = min(3, laserLevel + 1)
            "drone" -> droneLevel = min(2, droneLevel + 1)
            "magnet" -> magnetLevel = min(3, magnetLevel + 1)
            "shield" -> { shieldLevel = min(5, shieldLevel + 2); reflectiveShield = shieldLevel > 0 }
            "dash" -> dashCharges = min(4, dashCharges + 1)
            "chain" -> chainLevel = min(3, chainLevel + 1)
            "hull" -> lives = min(5, lives + 1)
            "rapid" -> rapidTimer = max(rapidTimer, 12f)
        }
        val combo = when {
            missileLevel > 0 && droneLevel > 0 -> "MISSILE DRONES"
            laserLevel > 0 && weaponLevel >= 3 -> "PRISM LASER"
            magnetLevel > 0 && shieldLevel > 0 -> "MAGNETIC AEGIS"
            else -> ""
        }
        if (combo.isNotEmpty()) bannerT = 2.8f
        sfx.play("power")
    }

    private fun chooseUpgrade(index: Int) {
        if (index !in upgradeChoices.indices) return
        applyUpgrade(upgradeChoices[index])
        upgradeChoices = emptyList()
        st = St.PLAY
        waiting = true
        waveT = 0.75f
    }

    private fun buyShop(id: String) {
        when (id) {
            "shop_repair" -> if (credits >= 180 && lives < 5) { credits -= 180; lives = min(5, lives + 1) }
            "shop_weapon" -> if (credits >= 320) { credits -= 320; weaponLevel = min(5, weaponLevel + 1); bonusDamage++ }
            "shop_shield" -> if (credits >= 260) { credits -= 260; shieldLevel = min(5, shieldLevel + 2); reflectiveShield = true }
            "shop_dash" -> if (credits >= 220) { credits -= 220; dashCharges = min(4, dashCharges + 1) }
            "shop_continue" -> { st = St.PLAY; waiting = true; waveT = 0.8f }
        }
        prefs.edit().putInt("credits", credits).apply()
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
        1 -> "METEOR SHOWER"
        2 -> "BLACK HOLE SURGE"
        3 -> "CHAIN REACTION"
        4 -> "GOLDEN CRATE"
        5 -> "HUNTER SQUAD"
        else -> ""
    }

"""
if insert not in g:
    if anchor not in g: raise RuntimeError("Missing onBtn anchor")
    g = g.replace(anchor, insert + anchor, 1)

# ---- menu actions and clickable upgrade/shop cards ----
rep('            "signin" -> Play.signIn(act)\n            "sound" -> sfx.enabled = !sfx.enabled',
    '            "signin" -> Play.signIn(act)\n            "ship" -> cycleShip()\n            "upgrade_0" -> chooseUpgrade(0)\n            "upgrade_1" -> chooseUpgrade(1)\n            "upgrade_2" -> chooseUpgrade(2)\n            "shop_repair", "shop_weapon", "shop_shield", "shop_dash", "shop_continue" -> buyShop(id)\n            "sound" -> sfx.enabled = !sfx.enabled', "menu and progression actions")
rep('                add("sound", if (sfx.enabled) "SOUND: ON" else "SOUND: OFF", y + 120f)',
    '                add("sound", if (sfx.enabled) "SOUND: ON" else "SOUND: OFF", y + 120f)\n                add("ship", "SHIP: " + shipName(), y + 150f)', "ship selector button")
rep('            St.OVER -> { val y = wh * 0.58f; add("play", "شروع دوباره", y); add("menu", "منو", y + 30f) }\n            else -> {}',
"""            St.OVER -> { val y = wh * 0.58f; add("play", "RETRY", y); add("menu", "MENU", y + 30f) }
            St.UPGRADE -> {
                val top = wh * 0.62f
                for (i in 0..2) l.add(Btn("upgrade_$i", "TAKE", RectF(5f + i * 72f, top, 67f + i * 72f, top + 25f)))
            }
            St.SHOP -> {
                val y = wh * 0.46f
                add("shop_repair", "REPAIR 180", y)
                add("shop_weapon", "WEAPON 320", y + 24f)
                add("shop_shield", "SHIELD 260", y + 48f)
                add("shop_dash", "DASH 220", y + 72f)
                add("shop_continue", "CONTINUE", y + 96f)
            }
            else -> {}""", "upgrade and shop buttons")

# ---- reset run-only stats, retain credits and unlocked ship progress ----
rep('        bullets.clear(); enemies.clear(); parts.clear(); items.clear(); playerBeams.clear(); missiles.clear(); enemyLasers.clear(); squadShips.clear(); blackHoles.clear()',
    '        bullets.clear(); enemies.clear(); parts.clear(); items.clear(); playerBeams.clear(); missiles.clear(); enemyLasers.clear(); squadShips.clear(); blackHoles.clear(); chainArcs.clear(); comets.clear()', "run reset collections")
rep('        score = 0; lives = 3; wave = 0; kills = 0; invul = 2.2f; fireT = 0f; time = 0f',
"""        score = 0; lives = if (currentShip == 2) 4 else 3; wave = 0; kills = 0; invul = 2.2f; fireT = 0f; time = 0f
        weaponLevel = 1; missileLevel = 0; laserLevel = 0; droneLevel = 0
        bonusDamage = if (currentShip == 3) 1 else 0; fireRateBonus = 0f; magnetLevel = 0
        shieldLevel = if (currentShip == 2) 1 else 0; reflectiveShield = shieldLevel > 0
        dashCharges = 0; chainLevel = 0; comboCount = 0; comboTimer = 0f
        eventKind = 0; eventTimer = 0f; meteorTimer = 0f; goldenCrateSpawned = false
        shieldReflectCooldown = 0f; dashCooldown = 0f; chainCooldown = 0f
        upgradesTaken = 0; intermissionHandledWave = -1; upgradeChoices = emptyList()""", "run upgrade reset")

# Best-wave unlock progression is persistent.
rep('    private fun spawnWave() {\n        wave++',
"""    private fun spawnWave() {
        wave++
        if (wave > bestWave) { bestWave = wave; prefs.edit().putInt("best_wave", bestWave).apply() }
        eventKind = 0; eventTimer = 0f; meteorTimer = 0f; goldenCrateSpawned = false
        if (wave >= 4 && (wave % 4 == 0 || Random.nextFloat() < 0.24f)) activateRandomEvent()""", "wave progression and event roll")

# Random event mechanics.
event_helpers = """    private fun activateRandomEvent() {
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

"""
if "private fun activateRandomEvent()" not in g:
    anchor = "    private fun spawnWave() {"
    if anchor not in g: raise RuntimeError("Missing spawnWave for events")
    g = g.replace(anchor, event_helpers + anchor, 1)

# Add healer and hunter enemies to later waves.
rep('val type = when { wave >= 4 && roll < 0.16f -> 3; wave >= 6 && roll < 0.28f -> 4; strong -> 1; else -> 0 }',
    'val type = when { wave >= 10 && roll < 0.13f -> 6; wave >= 8 && roll < 0.21f -> 5; wave >= 6 && roll < 0.32f -> 4; wave >= 4 && roll < 0.48f -> 3; strong -> 1; else -> 0 }', "enemy role probability")
rep('val hp = when (type) { 1 -> 5 + wave / 8; 3 -> 4 + wave / 10; 4 -> 3 + wave / 12; else -> 2 + wave / 12 }',
    'val hp = when (type) { 1 -> 5 + wave / 8; 3 -> 4 + wave / 10; 4 -> 3 + wave / 12; 5 -> 5 + wave / 10; 6 -> 4 + wave / 10; else -> 2 + wave / 12 }', "enemy role health")
# A hunter surge event gets a dense group even when the normal formation is small.
rep('            enemies.add(E(W / 2, -20f, 2, 110 + wave * 18, W / 2, 0f, ((wave / 5 - 1).coerceIn(0, 9)).toFloat()).also { it.fire = 1.7f })\n            return',
    '            enemies.add(E(W / 2, -20f, 2, 150 + wave * 22, W / 2, 0f, ((wave / 5 - 1).coerceIn(0, 9)).toFloat()).also { it.fire = 1.35f })\n            if (eventKind == 5) repeat(3) { val x = 25f + it * (W - 50f) / 2f; enemies.add(E(x, -42f - it * 20f, 6, 6 + wave / 8, x, 18f, it.toFloat())) }\n            return', "boss wave hunters")

# Score combo, credits and daily mission progress.
rep('        when (e.type) { 0 -> score += 10; 1 -> score += 30; else -> score += 500 }',
"""        val basePoints = when (e.type) { 2 -> 500; 1 -> 30; 5 -> 45; 6 -> 55; else -> 10 }
        comboCount = if (comboTimer > 0f) comboCount + 1 else 1
        comboTimer = 3.2f
        val multiplier = (1 + comboCount / 5).coerceIn(1, 5)
        score += basePoints * multiplier
        credits += if (e.type == 2) 90 else if (e.type == 5 || e.type == 6) 7 else 4
        prefs.edit().putInt("credits", credits).apply()
        refreshDaily()
        if (!dailyDone) {
            dailyKills++
            if (dailyKills >= 25) {
                dailyDone = true
                credits += 500
                prefs.edit().putInt("credits", credits).putBoolean("daily_done", true).apply()
            }
            prefs.edit().putInt("daily_kills", dailyKills).apply()
        }""", "combo credits daily mission")
rep('        if (e.type == 2) { ach(R.string.achievement_boss_slayer); bullets.removeAll { !it.mine } }',
    '        if (e.type == 2) { ach(R.string.achievement_boss_slayer); bullets.removeAll { !it.mine } }', "boss reward")
# Event chain reaction: nearby ships detonate, guarded against recursive chaining.
rep('        }\n    }\n\n    private fun hurt() {',
"""        }
        if (eventKind == 3 && !chainReactionActive) {
            chainReactionActive = true
            val nearby = enemies.filter { it !== e && !it.dead && hypot(it.x - e.x, it.y - e.y) < 42f }
            for (target in nearby) kill(target)
            chainReactionActive = false
        }
    }

    private fun hurt() {""", "chain reaction explosion")
rep('        if (shieldTimer > 0f) {\n            shieldTimer = 0f; invul = 1.2f',
    '        if (shieldLevel > 0) { shieldLevel--; reflectiveShield = shieldLevel > 0; invul = 1.2f; boom(px, py, 18, 58f); sfx.play("boom"); return }\n        if (shieldTimer > 0f) {\n            shieldTimer = 0f; invul = 1.2f', "reflective shield charges")
rep('            st = St.OVER; overT = 0f',
    '            st = St.OVER; overT = 0f; comboCount = 0; comboTimer = 0f', "combo reset on death")

# Laser plus firepower combo creates a three-beam prism.
laser_old = """    private fun fireLaser() {
        if (st != St.PLAY || laserLevel <= 0 || laserCooldown > 0f) return
        val width = 5f + laserLevel * 2.2f
        playerBeams.add(PlayerBeam(px, py, width, 0.52f))
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
    }"""
laser_new = """    private fun fireLaser() {
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
    }"""
rep(laser_old, laser_new, "prism laser combo")

rep('        val count = 2 + missileLevel\n',
    '        val count = 2 + missileLevel + if (missileLevel > 0 && droneLevel > 0) 2 else 0\n', "missile drone combo")
rep('missiles.add(Missile((px + offset).coerceIn(5f, W - 5f), py - 8f, offset * 0.8f, -115f, 2 + missileLevel * 2))',
    'missiles.add(Missile((px + offset).coerceIn(5f, W - 5f), py - 8f, offset * 0.8f, -115f, 2 + missileLevel * 2 + bonusDamage))', "missile upgrade damage")

# Ship-specific fire rate and wave intermission screens.
rep('        if (fireT <= 0f) { fireT = if (rapidTimer > 0f) 0.075f else 0.19f; shoot() }',
    '        if (fireT <= 0f) { fireT = (if (rapidTimer > 0f) 0.075f else 0.19f) * shipFireDelay() * (1f - fireRateBonus); shoot() }', "ship fire tuning")
rep('        squadSpawnTimer = max(0f, squadSpawnTimer - dt)\n',
    '        squadSpawnTimer = max(0f, squadSpawnTimer - dt)\n        shieldReflectCooldown = max(0f, shieldReflectCooldown - dt); dashCooldown = max(0f, dashCooldown - dt); chainCooldown = max(0f, chainCooldown - dt)\n        comboTimer = max(0f, comboTimer - dt); if (comboTimer <= 0f) comboCount = 0\n        for (arc in chainArcs) arc.life -= dt\n        chainArcs.removeAll { it.life <= 0f }\n', "new effect timers")
rep('            else { waveT -= dt; if (waveT <= 0f) { spawnWave(); waiting = false } }',
"""            else {
                waveT -= dt
                if (waveT <= 0f) {
                    if (wave > 0 && wave % 5 == 0 && intermissionHandledWave != wave) { intermissionHandledWave = wave; st = St.SHOP; waiting = true; waveT = 0.8f; return }
                    if (wave > 0 && wave % 2 == 0 && intermissionHandledWave != wave) { intermissionHandledWave = wave; prepareUpgradeChoices(); st = St.UPGRADE; waiting = true; waveT = 0.8f; return }
                    spawnWave(); waiting = false
                }
            }""", "wave upgrade and shop intermission")

# Event clocks and meteor shower volleys.
rep('        time += dt; invul -= dt; fireT -= dt; bannerT -= dt',
"""        if (st != St.PLAY) return

        time += dt; invul -= dt; fireT -= dt; bannerT -= dt
        if (eventTimer > 0f) {
            eventTimer = max(0f, eventTimer - dt)
            if (eventKind == 1) {
                meteorTimer -= dt
                if (meteorTimer <= 0f) {
                    val x = Random.nextFloat() * (W - 18f) + 9f
                    enemyLasers.add(EnemyLaser(x, 0.72f, 0.34f, 15f, (Random.nextFloat() - 0.5f) * 18f))
                    meteorTimer = 0.62f
                }
            }
            if (eventTimer <= 0f) eventKind = 0
        }""", "random event timers")

# Enemy roles: healer and hunter movement, boss phase thresholds, and warning clocks.
rep('        for (e in enemies) {\n            e.age += enemyDt',
"""        for (e in enemies) {
            e.age += enemyDt
            e.warning = max(0f, e.warning - enemyDt)
            if (e.type == 2) {
                val newPhase = when { e.hp <= e.maxHp / 3 -> 3; e.hp <= (e.maxHp * 2) / 3 -> 2; else -> 1 }
                if (newPhase > e.phase) { e.phase = newPhase; e.fire = 0.35f; boom(e.x, e.y, 28, 80f) }
            }
            if (e.type == 2) {""", "boss phases and enemy timers")
rep('                } else if (e.type == 4) {\n                    e.x = (e.bx + sin(e.age * 0.9f + e.t0) * 18f).coerceIn(12f, W - 12f)\n                } else {',
"""                } else if (e.type == 4) {
                    e.x = (e.bx + sin(e.age * 0.9f + e.t0) * 18f).coerceIn(12f, W - 12f)
                } else if (e.type == 5) {
                    e.y -= spd * 0.38f * enemyDt
                    e.x = (e.bx + sin(e.age * 0.8f + e.t0) * 12f).coerceIn(12f, W - 12f)
                } else if (e.type == 6) {
                    e.y += spd * 0.35f * enemyDt
                    val targetX = (px + sin(e.age * 2.3f + e.t0) * 9f).coerceIn(10f, W - 10f)
                    val shift = 54f * enemyDt
                    e.x += (targetX - e.x).coerceIn(-shift, shift)
                } else {""", "healer and hunter movement")

# The baseline already opens the boss branch; remove the duplicate opener introduced above.
duplicate_boss_open = '            if (e.type == 2) {\n            if (e.type == 2) {'
if duplicate_boss_open in g:
    g = g.replace(duplicate_boss_open, '            if (e.type == 2) {', 1)


# Boss attack warnings: a visible wind-up before each damaging pattern.
rep('            if (e.fire <= 0f) {\n                if (e.type == 2) {',
"""            if (e.fire <= 0f) {
                if (e.type == 2 && !e.attackWarned) { e.attackWarned = true; e.warning = 0.68f; e.fire = 0.68f; continue }
                if (e.type == 2) { e.attackWarned = false; e.warning = 0f } 
                if (e.type == 2) {""", "boss attack warning")
rep('                    val speed = 74f + min(wave.toFloat(), 50f) * 1.1f',
    '                    val speed = (74f + min(wave.toFloat(), 50f) * 1.1f) * (1f + (e.phase - 1) * 0.2f)', "phase attack speed")
# A later phase adds radial pressure and phase three adds twin warning lanes.
rep('                    }\n                } else {\n                    e.fire = when (e.type) {',
"""                    }
                    if (e.phase >= 2) {
                        for (i in 0 until 10) {
                            val a = i * (2f * PI.toFloat() / 10f) + e.age * 0.28f
                            bullets.add(B(e.x, e.y, cos(a) * speed * 0.78f, sin(a) * speed * 0.78f, false))
                        }
                    }
                    if (e.phase >= 3) {
                        enemyLasers.add(EnemyLaser(e.x - 34f, 0.42f, 0.58f, 7f, 20f))
                        enemyLasers.add(EnemyLaser(e.x + 34f, 0.42f, 0.58f, 7f, -20f))
                    }
                } else if (e.type == 5) {
                    e.fire = 2.5f
                    val allies = enemies.filter { it !== e && !it.dead && hypot(it.x - e.x, it.y - e.y) < 68f }
                    for (ally in allies) ally.hp = min(ally.maxHp, ally.hp + 1)
                    boom(e.x, e.y, 7, 30f)
                } else {
                    e.fire = when (e.type) {""", "boss phase attack and healer behavior")
rep('                        4 -> max(0.72f, 1.62f - wave * 0.011f)\n                        else -> max(0.82f, 1.82f - wave * 0.010f)',
    '                        4 -> max(0.72f, 1.62f - wave * 0.011f)\n                        6 -> max(0.44f, 0.92f - wave * 0.009f)\n                        else -> max(0.82f, 1.82f - wave * 0.010f)', "hunter fire rate")
rep('                            4 -> for (i in -2..2) { val a = base + i * 0.12f; bullets.add(B(e.x, e.y + 7f, cos(a) * speed * 0.88f, sin(a) * speed * 0.88f, false)) }\n                            else ->',
    '                            4 -> for (i in -2..2) { val a = base + i * 0.12f; bullets.add(B(e.x, e.y + 7f, cos(a) * speed * 0.88f, sin(a) * speed * 0.88f, false)) }\n                            6 -> for (i in -2..2) { val a = base + i * 0.24f; bullets.add(B(e.x, e.y + 7f, cos(a) * speed * 1.25f, sin(a) * speed * 1.25f, false)) }\n                            else ->', "hunter firing pattern")

# Defensive orbiting drones intercept incoming bullets as well as firing.
rep('        for (b in bullets) {\n            val bulletDt = if (b.mine) dt else enemyDt',
"""        for (b in bullets) {
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
            if (!b.mine && shieldLevel > 0 && shieldReflectCooldown <= 0f && hypot(b.x - px, b.y - py) < 16f) {
                b.mine = true
                val target = enemies.filter { !it.dead }.minByOrNull { hypot(it.x - b.x, it.y - b.y) }
                if (target != null) {
                    val a = atan2((target.y - b.y).toDouble(), (target.x - b.x).toDouble()).toFloat()
                    b.vx = cos(a) * 205f; b.vy = sin(a) * 205f
                    chainArcs.add(Arc(px, py, target.x, target.y))
                } else { b.vx = 0f; b.vy = -205f }
                shieldLevel--; reflectiveShield = shieldLevel > 0
                shieldReflectCooldown = 0.22f
                sfx.play("power")
            }""", "drone interception and bullet reflection")

# Magnet plus shield combo strengthens the pull; the passive magnet works all run.
rep('            if (magnetTimer > 0f) {\n                item.x += (px - item.x) * min(1f, 3.0f * dt)\n                item.y += (py - item.y) * min(1f, 3.0f * dt)',
    '            if (magnetTimer > 0f || magnetLevel > 0) {\n                val pullRate = if (magnetLevel > 0 && shieldLevel > 0) 5.2f else if (magnetLevel > 0) 3.8f else 3.0f\n                item.x += (px - item.x) * min(1f, pullRate * dt)\n                item.y += (py - item.y) * min(1f, pullRate * dt)', "magnet shield combo")
rep('                10 -> piercingTimer = max(piercingTimer, 8f)\n                11 -> enemySlowTimer = max(enemySlowTimer, 10f)\n                else -> activateSquadron()',
    '                10 -> piercingTimer = max(piercingTimer, 8f)\n                11 -> enemySlowTimer = max(enemySlowTimer, 10f)\n                12 -> { credits += 300; score += 500; lives = min(5, lives + 1); prefs.edit().putInt("credits", credits).apply() }\n                else -> activateSquadron()', "slowdown and golden crate pickup")
# Golden crate has its own gold color and chest icon.
rep('            11 -> 0xFFFFD45C.toInt()\n            else -> 0xFFB68CFF.toInt()',
    '            11 -> 0xFFFFD45C.toInt()\n            12 -> 0xFFFFC94A.toInt()\n            else -> 0xFFB68CFF.toInt()', "gold crate color")
rep('            else -> { // squadron\n                for (x in floatArrayOf(-5f, 0f, 5f)) {',
    '            12 -> { // golden crate\n                c.drawRoundRect(-5f, -4f, 5f, 5f, 1.2f, 1.2f, p)\n                c.drawLine(-5f, -1f, 5f, -1f, p); c.drawLine(0f, -4f, 0f, 5f, p)\n                c.drawLine(-2f, -6f, 2f, -6f, p)\n            }\n            else -> { // squadron\n                for (x in floatArrayOf(-5f, 0f, 5f)) {', "gold crate icon")

# Strengthen damage, chain-lightning jumps from each player hit to a nearby enemy.
rep('                    e.hp--; boom(b.x, b.y, 2, 25f)\n                    if (e.hp <= 0) kill(e)',
"""                    e.hp -= 1 + bonusDamage + if (currentShip == 3) 1 else 0
                    boom(b.x, b.y, 2, 25f)
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
                    if (e.hp <= 0) kill(e)""", "chain lightning hit effect")

# New enemy sprites, combo and event rendering.
rep('            val sprite = when (e.type) { 2 -> bossSprites[e.t0.toInt().coerceIn(0, 9)]; 1 -> bE1; 3 -> bE2; 4 -> bE3; else -> bE0 }',
    '            val sprite = when (e.type) { 2 -> bossSprites[e.t0.toInt().coerceIn(0, 9)]; 1 -> bE1; 3 -> bE2; 4 -> bE3; 5 -> bossSprites[5]; 6 -> bossSprites[6]; else -> bE0 }', "enemy role rendering")
rep('            spr(c, sprite, e.x, e.y)\n            if (e.type != 2) {',
"""            spr(c, sprite, e.x, e.y)
            if (e.type == 2 && e.warning > 0f) {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.1f * s
                paint.color = 0xFFFFD45C.toInt()
                c.drawLine(e.x * s, (e.y + 8f) * s, px * s, py * s, paint)
                paint.style = Paint.Style.FILL
            }
            if (e.type != 2) {""", "boss aim telegraph")
rep('        for (beam in playerBeams) {',
"""        for (arc in chainArcs) {
            paint.style = Paint.Style.STROKE; paint.strokeWidth = (1f + arc.life * 4f) * s
            paint.color = 0xFF73F5FF.toInt(); paint.alpha = (255f * (arc.life / 0.24f)).toInt().coerceIn(0, 255)
            c.drawLine(arc.x1 * s, arc.y1 * s, arc.x2 * s, arc.y2 * s, paint)
            paint.alpha = 255; paint.style = Paint.Style.FILL
        }
        for (beam in playerBeams) {""", "lightning arcs")
rep('        paint.style = Paint.Style.FILL\n        if (shieldTimer > 0f) {',
    '        paint.style = Paint.Style.FILL\n        if (shieldTimer > 0f || shieldLevel > 0) {', "reflective shield visual")
rep('                val dx = if (i == 0) -17f else 17f\n                val wing = Path().apply { moveTo((px + dx) * s, (py - 5f) * s); lineTo((px + dx + 4f) * s, (py + 3f) * s); lineTo((px + dx) * s, (py + 1f) * s); lineTo((px + dx - 4f) * s, (py + 3f) * s); close() }',
    '                val angle = time * 2.25f + i * PI.toFloat()\n                val dx = cos(angle) * 18f\n                val dy = sin(angle) * 10f\n                val wing = Path().apply { moveTo((px + dx) * s, (py + dy - 5f) * s); lineTo((px + dx + 4f) * s, (py + dy + 3f) * s); lineTo((px + dx) * s, (py + dy + 1f) * s); lineTo((px + dx - 4f) * s, (py + dy + 3f) * s); close() }', "orbiting drone rendering")
rep('        text(c, "SCORE $score", 6f, 14f, 9f, YEL, Paint.Align.LEFT)',
    '        text(c, "SCORE $score", 6f, 14f, 9f, YEL, Paint.Align.LEFT)\n        if (comboCount >= 3) text(c, "COMBO x" + (1 + comboCount / 5).coerceAtMost(5), W / 2f, 14f, 8f, 0xFFFFD45C.toInt())', "combo HUD")
rep('        val powerLabel = when {',
    '        val powerLabel = when {\n            magnetLevel > 0 && shieldLevel > 0 -> "MAGNETIC AEGIS"\n            shieldLevel > 0 -> "REFLECT " + shieldLevel', "upgrade status HUD")
rep('            text(c, "غول " + (bs.t0.toInt() + 1) + "/10", W / 2f, 17f, 7f, WHT)',
    '            text(c, "غول " + (bs.t0.toInt() + 1) + "/10  PHASE " + bs.phase, W / 2f, 17f, 7f, WHT)', "boss phase HUD")
rep('        if (bannerT > 0f && st == St.PLAY) {\n            val boss = wave % 5 == 0\n            text(c, if (boss) "!! BOSS !!" else "WAVE $wave", W / 2, wh * 0.4f, 16f, if (boss) RED else WHT)\n        }',
"""        if (bannerT > 0f && st == St.PLAY) {
            val boss = wave % 5 == 0
            val combo = if (missileLevel > 0 && droneLevel > 0) "MISSILE DRONES" else if (laserLevel > 0 && weaponLevel >= 3) "PRISM LASER" else if (magnetLevel > 0 && shieldLevel > 0) "MAGNETIC AEGIS" else ""
            text(c, if (boss) "!! BOSS !!" else if (eventKind > 0) eventName() else "WAVE $wave", W / 2, wh * 0.4f, 15f, if (boss) RED else WHT)
            if (combo.isNotEmpty()) text(c, combo, W / 2f, wh * 0.4f + 13f, 7f, 0xFF72F0C0.toInt())
        }""", "event and combo banner")
# Dash touch target and visible control.
rep('            drawPauseControl(c)',
"""            if (dashCharges > 0) {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 0.9f * s
                paint.color = if (dashCooldown <= 0f) 0xFF55DFFF.toInt() else 0xFF66717E.toInt()
                c.drawRoundRect(8f * s, (wh - 32f) * s, 43f * s, (wh - 7f) * s, 4f * s, 4f * s, paint)
                paint.style = Paint.Style.FILL
                text(c, "DASH " + dashCharges, 25.5f, wh - 16f, 6f, 0xFF8BEFFF.toInt())
            }
            drawPauseControl(c)""", "dash visual")
rep('                        } else if (y >= wh - 34f && x >= W - 96f) {',
    '                        } else if (y >= wh - 34f && x <= 42f) {\n                            performDash()\n                            controlTouch = true\n                        } else if (y >= wh - 34f && x >= W - 96f) {', "dash touch control")
rep('px = (px + (e.x - lx) / s).coerceIn(10f, W - 10f)',
    'px = (px + (e.x - lx) / s * shipMoveMultiplier()).coerceIn(10f, W - 10f)', "ship movement stat")
rep('        control("موشک", W - 94f, W - 53f, missileCooldown <= 0f)\n        control("لیزر", W - 49f, W - 8f, laserCooldown <= 0f)',
    '        if (dashCharges > 0) control("DASH " + dashCharges, 8f, 43f, dashCooldown <= 0f)\n        control("MISSILE", W - 94f, W - 53f, missileCooldown <= 0f)\n        control("LASER", W - 49f, W - 8f, laserCooldown <= 0f)', "dash UI")

# Main menu: ship selection, unlock progression, daily mission and credit balance.
rep('        text(c, "BEST $best", W / 2, wh * 0.42f, 8f, WHT)\n        drawButtons(c)',
    '        text(c, "SHIP " + shipName() + "   CREDITS " + credits, W / 2f, wh * 0.395f, 6.5f, 0xFF80E7FF.toInt())\n        text(c, if (dailyDone) "DAILY COMPLETE +500" else "DAILY 25 KILLS " + dailyKills + "/25", W / 2f, wh * 0.417f, 6f, 0xFFFFD45C.toInt())\n        text(c, "BEST $best  WAVE $bestWave", W / 2, wh * 0.44f, 7f, WHT)\n        drawButtons(c)', "menu progression display")

# Upgrade and shop screens render explanatory cards and purchase totals.
draw_screens = """    private fun upgradeTitle(id: String): String = when (id) {
        "firepower" -> "FIREPOWER"
        "missile" -> "MISSILES"
        "laser" -> "LASER"
        "drone" -> "DRONES"
        "magnet" -> "MAGNET"
        "shield" -> "REFLECT SHIELD"
        "dash" -> "EMERGENCY DASH"
        "chain" -> "CHAIN LIGHTNING"
        "hull" -> "HULL +1"
        else -> "RAPID FIRE"
    }

    private fun upgradeInfo(id: String): String = when (id) {
        "firepower" -> "+DAMAGE +FIRE RATE"
        "missile" -> "MORE HOMING ROCKETS"
        "laser" -> "STRONGER LASER"
        "drone" -> "ORBIT + INTERCEPT"
        "magnet" -> "PASSIVE ITEM PULL"
        "shield" -> "REFLECT ENEMY SHOTS"
        "dash" -> "ONE SAFE TELEPORT"
        "chain" -> "ARC TO NEARBY ENEMY"
        "hull" -> "RESTORE ONE LIFE"
        else -> "TEMPORARY FAST SHOTS"
    }

    private fun drawUpgrade(c: Canvas) {
        paint.color = 0xE8101722.toInt(); c.drawRect(0f, 0f, sw.toFloat(), sh.toFloat(), paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.2f * s; paint.color = 0xFF47DFFF.toInt()
        c.drawRoundRect(5f * s, wh * 0.30f * s, (W - 5f) * s, wh * 0.76f * s, 7f * s, 7f * s, paint)
        paint.style = Paint.Style.FILL
        text(c, "CHOOSE ONE UPGRADE", W / 2f, wh * 0.37f, 13f, 0xFF9CEBFF.toInt())
        for (i in 0..2) {
            val id = upgradeChoices.getOrNull(i) ?: continue
            val cx = 36f + i * 74f
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 0.9f * s
            paint.color = when (id) { "shield", "dash" -> 0xFFFF79D8.toInt(); "chain" -> 0xFF72F0C0.toInt(); else -> 0xFF55DFFF.toInt() }
            c.drawRoundRect((cx - 30f) * s, wh * 0.43f * s, (cx + 30f) * s, wh * 0.60f * s, 4f * s, 4f * s, paint)
            paint.style = Paint.Style.FILL
            text(c, upgradeTitle(id), cx, wh * 0.485f, 6.1f, WHT)
            text(c, upgradeInfo(id), cx, wh * 0.525f, 4.6f, 0xFF9CEBFF.toInt())
        }
        drawButtons(c)
    }

    private fun drawShop(c: Canvas) {
        paint.color = 0xE8101722.toInt(); c.drawRect(0f, 0f, sw.toFloat(), sh.toFloat(), paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.2f * s; paint.color = 0xFFFFD45C.toInt()
        c.drawRoundRect(25f * s, wh * 0.28f * s, 195f * s, wh * 0.84f * s, 8f * s, 8f * s, paint)
        paint.style = Paint.Style.FILL
        text(c, "BETWEEN-WAVE HANGAR", W / 2f, wh * 0.34f, 12f, 0xFFFFD45C.toInt())
        text(c, "CREDITS " + credits, W / 2f, wh * 0.40f, 9f, WHT)
        drawButtons(c)
    }

"""
if "private fun drawUpgrade(c: Canvas)" not in g:
    anchor = "    private fun drawPause(c: Canvas) {"
    if anchor not in g: raise RuntimeError("Missing drawPause for progression screens")
    g = g.replace(anchor, draw_screens + anchor, 1)

# Update the central renderer to show the new screens.
rep('            St.OVER -> { drawGame(c); drawOver(c) }\n        }',
    '            St.OVER -> { drawGame(c); drawOver(c) }\n            St.UPGRADE -> drawUpgrade(c)\n            St.SHOP -> drawShop(c)\n        }', "new state rendering")
# Card buttons are centered on their own rectangles, not the old menu center.
rep('            text(c, b.label, W / 2, b.r.top + 14.5f, 9f, col)',
    '            text(c, b.label, (b.r.left + b.r.right) / 2f, b.r.top + 14.5f, if (b.id.startsWith("upgrade_")) 6.5f else 9f, col)', "button positioning")

# The boss phases and events stay visible without hiding score / player controls.
game_file.write_text(g, encoding="utf-8")

# Bump the signed Android package so it installs over the previous build.
gradle = gradle_file.read_text(encoding="utf-8")
gradle = re.sub(r"versionCode\s*=\s*\d+", "versionCode = 7", gradle)
gradle = re.sub(r'versionName\s*=\s*"[^"]+"', 'versionName = "1.4"', gradle)
gradle_file.write_text(gradle, encoding="utf-8")

# Rebuild the project source archive so the downloadable ZIP matches the APK.
tmp = archive.with_suffix(".features.tmp.zip")
with zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
    for f in sorted(root.rglob("*")):
        if f.is_file():
            z.write(f, Path(root.name) / f.relative_to(root))
tmp.replace(archive)
print("Neon Shooter progression, ships, events, bosses, shield, dash and shop prepared.")
