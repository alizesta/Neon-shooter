#!/usr/bin/env python3
from pathlib import Path
import zipfile

root = Path("NeonShooter")
game_file = root / "app/src/main/java/com/claude/neonshooter/GameView.kt"
gradle_file = root / "app/build.gradle.kts"
g = game_file.read_text(encoding="utf-8")

def rep(old, new, label):
    global g
    if new in g: return
    if old not in g: raise RuntimeError("Polish patch anchor missing: " + label)
    g = g.replace(old, new, 1)

# If every upgrade is capped, skip the empty choice screen instead of trapping the run.
rep('if (wave > 0 && wave % 2 == 0 && intermissionHandledWave != wave) { intermissionHandledWave = wave; prepareUpgradeChoices(); st = St.UPGRADE; waiting = true; waveT = 0.8f; return }',
    'if (wave > 0 && wave % 2 == 0 && intermissionHandledWave != wave) { intermissionHandledWave = wave; prepareUpgradeChoices(); st = if (upgradeChoices.isNotEmpty()) St.UPGRADE else St.PLAY; waiting = true; waveT = 0.8f; return }',
    "skip empty capped upgrade screen")

# Five ranks per ability, with maxed abilities removed from the choice deck.
rep("    private var droneLevel = 0\n", "    private var droneLevel = 0\n    private var rapidLevel = 0\n    private var dashRank = 0\n    private var shieldRank = 0\n", "upgrade rank fields")
rep('''        val pool = listOf("firepower", "missile", "laser", "drone", "magnet", "shield", "dash", "chain", "hull", "rapid")
        upgradeChoices = pool.shuffled().take(3)''',
'''        val pool = listOf("firepower", "missile", "laser", "drone", "magnet", "shield", "dash", "chain", "hull", "rapid").filter { id ->
            when (id) {
                "firepower" -> weaponLevel < 5
                "missile" -> missileLevel < 5
                "laser" -> laserLevel < 5
                "drone" -> droneLevel < 5
                "magnet" -> magnetLevel < 5
                "shield" -> shieldRank < 5
                "dash" -> dashRank < 5
                "chain" -> chainLevel < 5
                "hull" -> lives < 5
                "rapid" -> rapidLevel < 5
                else -> false
            }
        }
        upgradeChoices = pool.shuffled().take(3)
        if (upgradeChoices.isEmpty()) { st = St.PLAY; waiting = true; waveT = 0.75f }''', "filter maxed abilities")
rep('''            "missile" -> missileLevel = min(3, missileLevel + 1)
            "laser" -> laserLevel = min(3, laserLevel + 1)
            "drone" -> droneLevel = min(2, droneLevel + 1)
            "magnet" -> magnetLevel = min(3, magnetLevel + 1)
            "shield" -> { shieldLevel = min(5, shieldLevel + 2); reflectiveShield = shieldLevel > 0 }
            "dash" -> dashCharges = min(4, dashCharges + 1)
            "chain" -> chainLevel = min(3, chainLevel + 1)
            "hull" -> lives = min(5, lives + 1)
            "rapid" -> rapidTimer = max(rapidTimer, 12f)''',
'''            "missile" -> missileLevel = min(5, missileLevel + 1)
            "laser" -> laserLevel = min(5, laserLevel + 1)
            "drone" -> droneLevel = min(5, droneLevel + 1)
            "magnet" -> magnetLevel = min(5, magnetLevel + 1)
            "shield" -> { shieldRank = min(5, shieldRank + 1); shieldLevel = min(5, shieldLevel + 1); reflectiveShield = shieldLevel > 0 }
            "dash" -> { dashRank = min(5, dashRank + 1); dashCharges = min(5, dashCharges + 1) }
            "chain" -> chainLevel = min(5, chainLevel + 1)
            "hull" -> lives = min(5, lives + 1)
            "rapid" -> { rapidLevel = min(5, rapidLevel + 1); rapidTimer = max(rapidTimer, 8f + rapidLevel * 2f) }''', "stack all upgrades to five")
rep("        weaponLevel = 1; missileLevel = 0; laserLevel = 0; droneLevel = 0\n",
    "        weaponLevel = 1; missileLevel = 0; laserLevel = 0; droneLevel = 0; rapidLevel = 0; dashRank = 0; shieldRank = 0\n", "reset upgrade ranks")
for old,new,label in [
 ("missileLevel = min(3, missileLevel + 1)","missileLevel = min(5, missileLevel + 1)","missile pickup cap"),
 ("laserLevel = min(3, laserLevel + 1)","laserLevel = min(5, laserLevel + 1)","laser pickup cap"),
 ("droneLevel = min(2, droneLevel + 1)","droneLevel = min(5, droneLevel + 1)","drone pickup cap"),
 ("magnetLevel = min(3, magnetLevel + 1)","magnetLevel = min(5, magnetLevel + 1)","magnet pickup cap")]:
    rep(old,new,label)

# No extra-life pickups, ship icons, starting bonus hull or repair shop.
rep("score = 0; lives = if (currentShip == 2) 4 else 3;", "score = 0; lives = 3;", "standard ship hull")
rep('''            "shop_repair" -> if (credits >= 180 && lives < 5) { credits -= 180; lives = min(5, lives + 1) }
''', "", "remove repair shop action")
rep('''            "shop_repair", "shop_weapon", "shop_shield", "shop_dash", "shop_continue" -> buyShop(id)''',
    '''            "shop_weapon", "shop_shield", "shop_dash", "shop_continue" -> buyShop(id)''', "remove repair action dispatch")
rep('''                add("shop_repair", "REPAIR 180", y)
                add("shop_weapon", "WEAPON 320", y + 24f)
                add("shop_shield", "SHIELD 260", y + 48f)
                add("shop_dash", "DASH 220", y + 72f)
                add("shop_continue", "CONTINUE", y + 96f)''',
    '''                add("shop_weapon", "WEAPON 320", y)
                add("shop_shield", "SHIELD 260", y + 28f)
                add("shop_dash", "DASH 220", y + 56f)
                add("shop_continue", "CONTINUE", y + 84f)''', "remove repair button")
rep("val dropChance = if (e.type == 2) 0.88f else 0.15f",
    "val dropChance = if (e.type == 2) 0.55f else 0.08f", "reduce random ability drops")
rep("                lives < 5 && roll < 0.28f -> 0\n                roll < 0.25f -> 1",
    "                roll < 0.25f -> 1", "remove heart pickup chance")
rep('''                0 -> { if (lives < 5) lives++ else score += 100 }''',
    '''                0 -> score += 100''', "disable extra life pickup")
rep('''                12 -> { credits += 300; score += 500; lives = min(5, lives + 1); prefs.edit().putInt("credits", credits).apply() }''',
    '''                12 -> { credits += 300; score += 500; prefs.edit().putInt("credits", credits).apply() }''', "gold chest no hull")

# Double wave length while retaining the seven-enemy on-screen cap.
rep("private val enemies = ArrayList<E>()",
    "private val enemies = ArrayList<E>()\n    private val pendingWaveEnemies = ArrayList<E>()\n    private var waveTarget = 1\n    private var waveKills = 0", "wave queue fields")
rep("        eventKind = 0; eventTimer = 0f; meteorTimer = 0f; goldenCrateSpawned = false\n",
    "        eventKind = 0; eventTimer = 0f; meteorTimer = 0f; goldenCrateSpawned = false\n        pendingWaveEnemies.clear(); waveKills = 0; waveTarget = 1\n", "reset wave queue")
rep("        bullets.clear(); enemies.clear(); parts.clear(); items.clear(); playerBeams.clear(); missiles.clear(); enemyLasers.clear(); squadShips.clear(); blackHoles.clear(); chainArcs.clear(); comets.clear()",
    "        bullets.clear(); enemies.clear(); pendingWaveEnemies.clear(); parts.clear(); items.clear(); playerBeams.clear(); missiles.clear(); enemyLasers.clear(); squadShips.clear(); blackHoles.clear(); chainArcs.clear(); comets.clear(); impactRings.clear()",
    "clear queues and impact effects on new run")
rep('''        if (wave % 5 == 0) {
            enemies.add(E(W / 2, -20f, 2, 150 + wave * 22, W / 2, 0f, ((wave / 5 - 1).coerceIn(0, 9)).toFloat()).also { it.fire = 1.35f })
            if (eventKind == 5) repeat(3) { val x = 25f + it * (W - 50f) / 2f; enemies.add(E(x, -42f - it * 20f, 6, 6 + wave / 8, x, 18f, it.toFloat())) }
            return
        }
        val n = min(8 + wave / 2, 15)''',
'''        if (wave % 5 == 0) {
            waveTarget = enemies.count { !it.dead } + 1 + if (eventKind == 5) 3 else 0
            enemies.add(E(W / 2, -20f, 2, 300 + wave * 44, W / 2, 0f, ((wave / 5 - 1).coerceIn(0, 9)).toFloat()).also { it.fire = 1.35f })
            if (eventKind == 5) repeat(3) { val x = 25f + it * (W - 50f) / 2f; enemies.add(E(x, -42f - it * 20f, 6, 6 + wave / 8, x, 18f, it.toFloat())) }
            return
        }
        val n = min(16 + wave, 30)
        waveTarget = n + enemies.count { !it.dead }''', "double wave enemy count")
rep('''            if (enemies.size < 7 && enemies.none { abs(it.x - x) < 24f && abs(it.y - y) < 30f }) enemies.add(E(x, y, type, hp, x, amp, t0))''',
'''            if (enemies.size + pendingWaveEnemies.size < waveTarget &&
                (enemies + pendingWaveEnemies).none { abs(it.x - x) < 24f && abs(it.y - y) < 30f }) {
                pendingWaveEnemies.add(E(x, y, type, hp, x, amp, t0))
            }''', "queue enemies rather than discard overflow")
rep('''        }
    }

    private fun boom(x: Float, y: Float, n: Int, sp: Float = 60f) {''',
'''        }
        while (enemies.size < 7 && pendingWaveEnemies.isNotEmpty()) enemies.add(pendingWaveEnemies.removeAt(0))
    }

    private fun boom(x: Float, y: Float, n: Int, sp: Float = 60f) {''', "start queued wave enemies")
rep('''        if (enemies.isEmpty()) {
            if (!waiting)''',
'''        while (enemies.size < 7 && pendingWaveEnemies.isNotEmpty()) enemies.add(pendingWaveEnemies.removeAt(0))
        if (enemies.isEmpty() && pendingWaveEnemies.isEmpty()) {
            if (!waiting)''', "wave waits for pending enemies")
rep("        e.dead = true\n        val basePoints",
    "        e.dead = true\n        waveKills = min(waveTarget, waveKills + 1)\n        val basePoints", "wave progress kill count")

# Hit feel: flash rings, micro shake, full damage shake and red impact flash.
rep("        var warning = 0f\n        val hw =",
    "        var warning = 0f\n        var hitFlash = 0f\n        val hw =", "enemy flash state")
rep("    private class Arc(val x1: Float, val y1: Float, val x2: Float, val y2: Float, var life: Float = 0.24f)",
    "    private class Arc(val x1: Float, val y1: Float, val x2: Float, val y2: Float, var life: Float = 0.24f)\n    private class ImpactRing(val x: Float, val y: Float, var life: Float = 0.22f, val maxLife: Float = 0.22f)", "impact ring type")
rep("    private val comets = ArrayList<Comet>()",
    "    private val comets = ArrayList<Comet>()\n    private val impactRings = ArrayList<ImpactRing>()\n    private var screenShake = 0f\n    private var shakePower = 0f\n    private var damageFlash = 0f\n    private var pressedButtonId = \"\"\n    private var buttonPulse = 0f", "impact and button states")
rep("        invul = 2.4f\n        boom(px, py, 24, 70f)",
    "        invul = 2.4f\n        screenShake = 0.26f; shakePower = 4.8f; damageFlash = 0.22f\n        impactRings.add(ImpactRing(px, py, 0.32f, 0.32f))\n        boom(px, py, 24, 70f)", "player damage feedback")
rep("        e.dead = true\n        waveKills = min(waveTarget, waveKills + 1)",
    "        e.dead = true\n        screenShake = max(screenShake, if (e.type == 2) 0.18f else 0.045f)\n        shakePower = max(shakePower, if (e.type == 2) 2.8f else 0.8f)\n        impactRings.add(ImpactRing(e.x, e.y, if (e.type == 2) 0.38f else 0.18f, if (e.type == 2) 0.38f else 0.18f))\n        waveKills = min(waveTarget, waveKills + 1)", "enemy destruction feedback")
rep("                    e.hp -= 1 + bonusDamage + if (currentShip == 3) 1 else 0\n                    boom(b.x, b.y, 2, 25f)",
    "                    e.hp -= 1 + bonusDamage + if (currentShip == 3) 1 else 0\n                    e.hitFlash = 0.14f\n                    impactRings.add(ImpactRing(b.x, b.y))\n                    screenShake = max(screenShake, 0.055f); shakePower = max(shakePower, 0.7f)\n                    boom(b.x, b.y, 4, 32f)", "bullet impact")
rep("        for (p in parts) { p.x += p.vx * dt; p.y += p.vy * dt; p.life -= dt }\n        parts.removeAll { it.life <= 0f }",
    "        for (p in parts) { p.x += p.vx * dt; p.y += p.vy * dt; p.life -= dt }\n        parts.removeAll { it.life <= 0f }\n        for (ring in impactRings) ring.life -= dt\n        impactRings.removeAll { it.life <= 0f }\n        screenShake = max(0f, screenShake - dt); damageFlash = max(0f, damageFlash - dt); buttonPulse = max(0f, buttonPulse - dt)\n        for (enemy in enemies) enemy.hitFlash = max(0f, enemy.hitFlash - dt)", "update hit effects")
rep("                        buttons().firstOrNull { it.r.contains(e.x / s, e.y / s) }?.let { onBtn(it.id) }",
    "                        buttons().firstOrNull { it.r.contains(e.x / s, e.y / s) }?.let { pressedButtonId = it.id; buttonPulse = 0.18f; onBtn(it.id) }", "button press feedback")
rep("        paint.alpha = 255\n        paint.style = Paint.Style.FILL\n        when (st) {",
    "        paint.alpha = 255\n        paint.style = Paint.Style.FILL\n        c.save()\n        if (screenShake > 0f) { val strength = shakePower * (screenShake / 0.26f); c.translate(sin(time * 91f) * strength * s, cos(time * 77f) * strength * s) }\n        when (st) {", "apply screen shake")
rep("            St.SHOP -> drawShop(c)\n        }\n    }\n\n    private fun drawGame(c: Canvas) {",
    "            St.SHOP -> drawShop(c)\n        }\n        c.restore()\n        if (damageFlash > 0f) { paint.style = Paint.Style.FILL; paint.color = Color.argb((58f * damageFlash / 0.22f).toInt().coerceIn(0, 58), 255, 42, 66); c.drawRect(0f, 0f, sw.toFloat(), sh.toFloat(), paint) }\n    }\n\n    private fun drawGame(c: Canvas) {", "damage flash overlay")
rep("            spr(c, sprite, e.x, e.y)\n            if (e.type == 2 && e.warning > 0f) {",
    "            spr(c, sprite, e.x, e.y)\n            if (e.hitFlash > 0f) { paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.5f * s; paint.color = Color.argb((210f * e.hitFlash / 0.14f).toInt().coerceIn(0, 210), 255, 255, 255); c.drawCircle(e.x * s, e.y * s, (e.hw + 3f) * s, paint); paint.style = Paint.Style.FILL }\n            if (e.type == 2 && e.warning > 0f) {", "enemy flash render")
rep("        for (item in items) drawItem(c, item)",
    "        for (ring in impactRings) { val t = 1f - ring.life / ring.maxLife; paint.style = Paint.Style.STROKE; paint.strokeWidth = (1.8f - t) * s; paint.color = Color.argb((220f * (1f - t)).toInt().coerceIn(0, 220), 185, 235, 255); c.drawCircle(ring.x * s, ring.y * s, (2.5f + t * 13f) * s, paint); paint.style = Paint.Style.FILL }\n        for (item in items) drawItem(c, item)", "impact ring render")

# Health is the ship's hull only; X marks replace floating ship-life icons.
rep("        for (i in 0 until lives) spr(c, bMini, W - 12f - i * 12f, 10f)\n", "", "remove extra ship icons")
rep("        c.drawRoundRect(18f * s, 21f * s, (18f + 54f * lives / 5f) * s, 25f * s, 1.8f * s, 1.8f * s, paint)\n        val powerLabel",
    "        c.drawRoundRect(18f * s, 21f * s, (18f + 54f * lives / 5f) * s, 25f * s, 1.8f * s, 1.8f * s, paint)\n        text(c, \"×\".repeat(lives), 80f, 27f, 7f, 0xFFFFD45C.toInt(), Paint.Align.LEFT)\n        val powerLabel", "hull X markers")

# Side-mounted wave completion bar.
rep("        if (bannerT > 0f && st == St.PLAY) {",
'''        if (waveTarget > 0 && (st == St.PLAY || st == St.PAUSE)) {
            val x = (W - 5f) * s; val top = 42f * s; val bottom = (wh - 48f) * s
            val progress = (waveKills.toFloat() / waveTarget.toFloat()).coerceIn(0f, 1f)
            paint.color = 0x88434D5A.toInt(); c.drawRoundRect(x, top, x + 2.6f * s, bottom, 1.3f * s, 1.3f * s, paint)
            paint.color = 0xFF72E8FF.toInt(); c.drawRoundRect(x, bottom - (bottom - top) * progress, x + 2.6f * s, bottom, 1.3f * s, 1.3f * s, paint)
            paint.color = 0xFFD2D8E0.toInt(); c.drawCircle(x + 1.3f * s, top, 1.8f * s, paint)
        }
        if (bannerT > 0f && st == St.PLAY) {''', "wave progress meter")

# Unique vector icons on each ability card.
icon_func = '''    private fun drawAbilityIcon(c: Canvas, id: String, cx: Float, cy: Float) {
        val color = when (id) { "drone", "missile" -> 0xFF62E7FF.toInt(); "shield", "hull" -> 0xFFFF83D9.toInt(); "chain", "rapid" -> 0xFFFFD45C.toInt(); "magnet" -> 0xFF72F0C0.toInt(); else -> 0xFF9CEBFF.toInt() }
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.35f * s; paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND; paint.color = color
        val ox = cx * s; val oy = cy * s
        when (id) {
            "drone" -> { c.drawCircle(ox - 7f*s, oy, 3.2f*s, paint); c.drawCircle(ox + 7f*s, oy, 3.2f*s, paint); c.drawLine(ox-3f*s,oy,ox+3f*s,oy,paint); c.drawCircle(ox,oy-1f*s,2f*s,paint) }
            "missile" -> { val p=Path().apply { moveTo(ox,oy-9f*s); lineTo(ox+4f*s,oy+4f*s); lineTo(ox,oy+2f*s); lineTo(ox-4f*s,oy+4f*s); close() }; c.drawPath(p,paint); c.drawLine(ox-3f*s,oy+5f*s,ox-5f*s,oy+8f*s,paint); c.drawLine(ox+3f*s,oy+5f*s,ox+5f*s,oy+8f*s,paint) }
            "laser" -> { c.drawLine(ox,oy-9f*s,ox,oy+8f*s,paint); c.drawLine(ox-5f*s,oy-5f*s,ox+5f*s,oy-5f*s,paint); c.drawLine(ox-5f*s,oy+5f*s,ox+5f*s,oy+5f*s,paint) }
            "firepower" -> for (dx in floatArrayOf(-5f,0f,5f)) { c.drawLine((cx+dx)*s,(cy+6f)*s,(cx+dx)*s,(cy-5f)*s,paint); c.drawLine((cx+dx)*s,(cy-5f)*s,(cx+dx-2f)*s,(cy-2f)*s,paint); c.drawLine((cx+dx)*s,(cy-5f)*s,(cx+dx+2f)*s,(cy-2f)*s,paint) }
            "magnet" -> { val p=Path().apply { moveTo(ox-7f*s,oy-6f*s); lineTo(ox-7f*s,oy+2f*s); quadTo(ox,oy+12f*s,ox+7f*s,oy+2f*s); lineTo(ox+7f*s,oy-6f*s) }; c.drawPath(p,paint); c.drawLine(ox-7f*s,oy-6f*s,ox-3f*s,oy-6f*s,paint); c.drawLine(ox+3f*s,oy-6f*s,ox+7f*s,oy-6f*s,paint) }
            "shield" -> { val p=Path().apply { moveTo(ox,oy-9f*s); lineTo(ox+7f*s,oy-5f*s); lineTo(ox+5f*s,oy+4f*s); lineTo(ox,oy+9f*s); lineTo(ox-5f*s,oy+4f*s); lineTo(ox-7f*s,oy-5f*s); close() }; c.drawPath(p,paint) }
            "dash" -> { c.drawLine(ox-8f*s,oy+5f*s,ox+5f*s,oy-5f*s,paint); c.drawLine(ox+5f*s,oy-5f*s,ox+1f*s,oy-5f*s,paint); c.drawLine(ox+5f*s,oy-5f*s,ox+4f*s,oy-1f*s,paint) }
            "chain" -> { c.drawOval(ox-9f*s,oy-5f*s,ox+1f*s,oy+5f*s,paint); c.drawOval(ox-1f*s,oy-5f*s,ox+9f*s,oy+5f*s,paint) }
            "hull" -> { c.drawLine(ox-7f*s,oy,ox+7f*s,oy,paint); c.drawLine(ox,oy-7f*s,ox,oy+7f*s,paint) }
            else -> { val p=Path().apply { moveTo(ox+1f*s,oy-9f*s); lineTo(ox-5f*s,oy+1f*s); lineTo(ox-1f*s,oy+1f*s); lineTo(ox-3f*s,oy+8f*s); lineTo(ox+6f*s,oy-3f*s); lineTo(ox+1f*s,oy-3f*s); close() }; c.drawPath(p,paint) }
        }
        paint.style = Paint.Style.FILL
    }

'''
rep("    private fun drawUpgrade(c: Canvas) {", icon_func + "    private fun drawUpgrade(c: Canvas) {", "ability icon function")
rep('''            paint.style = Paint.Style.FILL
            text(c, upgradeTitle(id), cx, wh * 0.485f, 6.1f, WHT)
            text(c, upgradeInfo(id), cx, wh * 0.525f, 4.6f, 0xFF9CEBFF.toInt())''',
'''            paint.style = Paint.Style.FILL
            drawAbilityIcon(c, id, cx, wh * 0.462f)
            val rank = when (id) {
                "firepower" -> weaponLevel
                "missile" -> missileLevel
                "laser" -> laserLevel
                "drone" -> droneLevel
                "magnet" -> magnetLevel
                "shield" -> shieldRank
                "dash" -> dashRank
                "chain" -> chainLevel
                "hull" -> lives
                else -> rapidLevel
            }
            text(c, upgradeTitle(id) + "  " + (rank + 1) + "/5", cx, wh * 0.515f, 5.8f, WHT)
            text(c, upgradeInfo(id), cx, wh * 0.55f, 4.4f, 0xFF9CEBFF.toInt())''', "upgrade icon and rank display")

# Make buttons bright, filled, and visibly pressed instead of gray outlines.
rep('''    private fun drawButtons(c: Canvas) {
        for (b in buttons()) {
            val col = if (b.id == "play") YEL else 0xFFCCCCCC.toInt()
            paint.style = Paint.Style.STROKE; paint.strokeWidth = s * 0.8f; paint.color = col
            c.drawRoundRect(b.r.left * s, b.r.top * s, b.r.right * s, b.r.bottom * s, 3f * s, 3f * s, paint)
            paint.clearShadowLayer(); paint.style = Paint.Style.FILL
            text(c, b.label, (b.r.left + b.r.right) / 2f, b.r.top + 14.5f, if (b.id.startsWith("upgrade_")) 6.5f else 9f, col)
        }
    }''',
'''    private fun drawButtons(c: Canvas) {
        for (b in buttons()) {
            val pressed = b.id == pressedButtonId && buttonPulse > 0f
            val col = when {
                b.id.startsWith("upgrade_") || b.id == "play" -> 0xFF55DFFF.toInt()
                b.id.startsWith("shop_") -> 0xFFFFD45C.toInt()
                b.id == "ship" -> 0xFFB68CFF.toInt()
                else -> 0xFF9CEBFF.toInt()
            }
            val r = if (pressed) RectF(b.r.left + 1.2f, b.r.top + 1.2f, b.r.right - 1.2f, b.r.bottom - 1.2f) else b.r
            paint.style = Paint.Style.FILL
            paint.color = if (pressed) 0xAAFFFFFF.toInt() else if (b.id.startsWith("upgrade_")) 0x5535D9F2.toInt() else 0x332BD8F4.toInt()
            c.drawRoundRect(r.left*s,r.top*s,r.right*s,r.bottom*s,4f*s,4f*s,paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = (if (pressed) 1.8f else 1.15f) * s; paint.color = col
            c.drawRoundRect(r.left*s,r.top*s,r.right*s,r.bottom*s,4f*s,4f*s,paint)
            paint.style = Paint.Style.FILL
            text(c, b.label, (r.left+r.right)/2f, r.top+14.5f, if (b.id.startsWith("upgrade_")) 7f else 9f, if (pressed) WHT else col)
        }
    }''', "bright responsive buttons")

# Light-gray round stars; tiny light-gray comets with a fine fading trail.
rep('''            paint.color = if (star.twinkle) 0xFFBDEFFF.toInt() else STAR
            paint.alpha = if (star.twinkle) (28f + pulse * 225f).toInt().coerceIn(20, 255) else 185
            val size = star.sz * if (star.twinkle) (0.65f + pulse * 0.75f) else 1f
            c.drawRect(star.x * s, star.y * s, (star.x + size) * s, (star.y + size) * s, paint)''',
'''            paint.color = 0xFFD0D4DA.toInt()
            paint.alpha = if (star.twinkle) (95f + pulse * 105f).toInt().coerceIn(80, 205) else 145
            val radius = star.sz * if (star.twinkle) (0.55f + pulse * 0.35f) else 0.62f
            c.drawCircle(star.x * s, star.y * s, radius * s, paint)''', "round gray stars")
rep('''            val speed = 34f + Random.nextFloat() * 18f
            comets.add(Comet(Random.nextFloat() * W, -3f, (Random.nextFloat() - 0.5f) * 8f, speed, 4f + Random.nextFloat() * 4f, 5.5f, 5.5f))
            cometSpawnTimer = 5f + Random.nextFloat() * 7f''',
'''            val speed = 27f + Random.nextFloat() * 10f
            comets.add(Comet(Random.nextFloat() * W, -3f, (Random.nextFloat() - 0.5f) * 3f, speed, 5f + Random.nextFloat() * 2f, 6.5f, 6.5f))
            cometSpawnTimer = 7f + Random.nextFloat() * 8f''', "tiny slow comet")
rep('''        paint.strokeWidth = 0.55f * s
        paint.color = 0xFF9FC7E5.toInt()
        for (comet in comets) {
            paint.alpha = (34f * (comet.life / comet.maxLife)).toInt().coerceIn(7, 34)
            c.drawLine(comet.x * s, comet.y * s, (comet.x - comet.vx * 0.13f) * s, (comet.y - comet.length) * s, paint)
        }''',
'''        paint.strokeWidth = 0.48f * s
        paint.color = 0xFFD4D8DE.toInt()
        for (comet in comets) {
            val fade = (115f * (comet.life / comet.maxLife)).toInt().coerceIn(24, 115)
            val tailX = (comet.x - comet.vx * 0.08f) * s
            val tailY = (comet.y - comet.length) * s
            paint.alpha = (fade * 0.22f).toInt()
            c.drawLine(comet.x*s,comet.y*s,tailX,tailY,paint)
            paint.alpha = (fade * 0.48f).toInt()
            c.drawLine(comet.x*s,comet.y*s,(comet.x+(tailX/s-comet.x)*0.55f)*s,(comet.y+(tailY/s-comet.y)*0.55f)*s,paint)
            paint.style = Paint.Style.FILL; paint.alpha = fade
            c.drawCircle(comet.x*s,comet.y*s,0.72f*s,paint)
            paint.style = Paint.Style.STROKE
        }''', "gray comet tail")

# Upgrade version and rebuild matching source archive.
gradle = gradle_file.read_text(encoding="utf-8")
gradle = gradle.replace("versionCode = 7", "versionCode = 8").replace('versionName = "1.4"', 'versionName = "1.5"')
gradle_file.write_text(gradle, encoding="utf-8")
game_file.write_text(g, encoding="utf-8")
archive = next(Path(".").glob("NeonShooter_*.zip"))
tmp = archive.with_suffix(".polish.tmp.zip")
with zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
    for f in sorted(root.rglob("*")):
        if f.is_file(): z.write(f, Path(root.name) / f.relative_to(root))
tmp.replace(archive)
print("Neon Shooter 1.5 gameplay and visual polish ready.")
