#!/usr/bin/env python3
"""Neon Shooter 1.6: persistent shield health, clear laser origins, glass UI and ad slots."""
from pathlib import Path
import re
import zipfile

root = Path("NeonShooter")
game_file = root / "app/src/main/java/com/claude/neonshooter/GameView.kt"
main_file = root / "app/src/main/java/com/claude/neonshooter/MainActivity.kt"
app_file = root / "app/src/main/java/com/claude/neonshooter/App.kt"
play_file = root / "app/src/main/java/com/claude/neonshooter/Play.kt"
gradle_file = root / "app/build.gradle.kts"
strings_file = root / "app/src/main/res/values/strings.xml"
g = game_file.read_text(encoding="utf-8")


def rep(old: str, new: str, label: str):
    global g
    if new in g:
        return
    if old not in g:
        raise RuntimeError("Neon 1.6 patch anchor missing: " + label)
    g = g.replace(old, new, 1)


# Remove Google Play Games UI and calls. Local score/best-wave storage remains.
for old, new, label in [
    ('            "lb" -> Play.showLeaderboard(act)\n', "", "leaderboard action"),
    ('            "ach" -> Play.showAchievements(act)\n', "", "achievement menu action"),
    ('            "signin" -> Play.signIn(act)\n', "", "Google sign-in action"),
    ('                add("lb", "جدول امتیازات", y + 30f)\n', "", "leaderboard button"),
    ('                add("ach", "دستاوردها", y + 60f)\n', "", "achievements button"),
    ('                add("signin", if (Play.signedIn) "GOOGLE: ONLINE" else "GOOGLE SIGN-IN", y + 90f)\n', "", "Google button"),
    ('                add("sound", if (sfx.enabled) "SOUND: ON" else "SOUND: OFF", y + 120f)\n                add("ship", "SHIP: " + shipName(), y + 150f)',
     '                add("sound", if (sfx.enabled) "SOUND: ON" else "SOUND: OFF", y + 30f)\n                add("ship", "SHIP: " + shipName(), y + 60f)', "compact local menu"),
    ('    private fun ach(resId: Int) { if (unlocked.add(resId)) Play.unlock(act, resId) }',
     '    private fun ach(resId: Int) { unlocked.add(resId) }', "local achievement tracking"),
    ('            act.runOnUiThread { try { Play.submit(act, score) } catch (e: Exception) { Log.e("NeonShooter", "Score submit skipped", e) } }\n',
     "", "Google score submission"),
]:
    if old not in g:
        raise RuntimeError("Neon 1.6 patch anchor missing: " + label)
    g = g.replace(old, new, 1)

main = main_file.read_text(encoding="utf-8")
main = main.replace("        Play.check(this)\n", "")
main_file.write_text(main, encoding="utf-8")
if app_file.exists():
    app = app_file.read_text(encoding="utf-8")
    app = app.replace("import com.google.android.gms.games.PlayGamesSdk\n", "")
    app = app.replace("        try { PlayGamesSdk.initialize(this) } catch (e: Throwable) {}\n", "")
    app_file.write_text(app, encoding="utf-8")
if play_file.exists():
    play_file.unlink()

gradle = gradle_file.read_text(encoding="utf-8")
gradle = re.sub(r'\s*implementation\("com\.google\.android\.gms:play-services-games-v2:[^"]+"\)\s*', "\n", gradle)
gradle_file.write_text(gradle, encoding="utf-8")

# Persistent shield health: rank increases capacity; each intercepted projectile removes one point.
rep("    private var shieldLevel = 0\n    private var reflectiveShield = false",
    "    private var shieldLevel = 0\n    private var shieldHp = 0\n    private var shieldMaxHp = 0\n    private var reflectiveShield = false", "shield health fields")
rep('"shield" -> { shieldRank = min(5, shieldRank + 1); shieldLevel = min(5, shieldLevel + 1); reflectiveShield = shieldLevel > 0 }',
    '"shield" -> { shieldRank = min(5, shieldRank + 1); shieldLevel = shieldRank; shieldMaxHp = 10 + shieldRank * 6; shieldHp = min(shieldMaxHp, if (shieldHp <= 0) shieldMaxHp else shieldHp + 8); reflectiveShield = shieldHp > 0 }',
    "shield upgrade health")
rep('"shop_shield" -> if (credits >= 260) { credits -= 260; shieldLevel = min(5, shieldLevel + 2); reflectiveShield = true }',
    '"shop_shield" -> if (credits >= 260) { credits -= 260; shieldRank = min(5, shieldRank + 1); shieldLevel = shieldRank; shieldMaxHp = 10 + shieldRank * 6; shieldHp = min(shieldMaxHp, shieldHp + 10); reflectiveShield = shieldHp > 0 }',
    "shop shield health")
rep('        weaponLevel = 1; missileLevel = 0; laserLevel = 0; droneLevel = 0; rapidLevel = 0; dashRank = 0; shieldRank = 0',
    '        weaponLevel = 1; missileLevel = 0; laserLevel = 0; droneLevel = 0; rapidLevel = 0; dashRank = 0; shieldRank = if (currentShip == 2) 1 else 0',
    "ship shield rank reset")
rep('        shieldLevel = if (currentShip == 2) 1 else 0; reflectiveShield = shieldLevel > 0',
    '        shieldLevel = shieldRank; shieldMaxHp = if (shieldRank > 0) 16 else 0; shieldHp = shieldMaxHp; reflectiveShield = shieldHp > 0',
    "initial ship shield health")
rep('        if (shieldLevel > 0) { shieldLevel--; reflectiveShield = shieldLevel > 0; invul = 1.2f; boom(px, py, 18, 58f); sfx.play("boom"); return }',
    '        if (shieldHp > 0) { shieldHp--; reflectiveShield = shieldHp > 0; invul = 0.42f; boom(px, py, 12, 42f); sfx.play("power"); return }',
    "shield absorbs hull collision")
rep('if (!b.mine && shieldLevel > 0 && shieldReflectCooldown <= 0f && hypot(b.x - px, b.y - py) < 16f)',
    'if (!b.mine && shieldHp > 0 && hypot(b.x - px, b.y - py) < 27f)',
    "shield catches shots across full dome")
rep('                shieldLevel--; reflectiveShield = shieldLevel > 0\n                shieldReflectCooldown = 0.22f',
    '                shieldHp = (shieldHp - 1).coerceAtLeast(0); reflectiveShield = shieldHp > 0\n                sfx.play("power")',
    "shield loses one hp per projectile")

# Every laser gets an explicit firing point; the source glows at the emitter.
rep('enemyLasers.add(EnemyLaser(x, 0.72f, 0.34f, 15f, (Random.nextFloat() - 0.5f) * 18f))',
    'enemyLasers.add(EnemyLaser(x, 0.72f, 0.34f, 15f, (Random.nextFloat() - 0.5f) * 18f, 0f))',
    "meteor laser origin")
rep('enemyLasers.add(EnemyLaser(e.x - 34f, 0.42f, 0.58f, 7f, 20f))',
    'enemyLasers.add(EnemyLaser(e.x - 34f, 0.42f, 0.58f, 7f, 20f, e.y))',
    "left boss laser origin")
rep('enemyLasers.add(EnemyLaser(e.x + 34f, 0.42f, 0.58f, 7f, -20f))',
    'enemyLasers.add(EnemyLaser(e.x + 34f, 0.42f, 0.58f, 7f, -20f, e.y))',
    "right boss laser origin")
rep('''        for (beam in playerBeams) {
            paint.style = Paint.Style.STROKE; paint.strokeWidth = beam.width * s; paint.color = 0xFF54E8FF.toInt()
            c.drawLine(beam.x * s, beam.bottom * s, beam.x * s, 0f, paint)
        }''',
'''        for (beam in playerBeams) {
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
        paint.style = Paint.Style.FILL''', "player laser emitters")
rep('''        for (laser in enemyLasers) {
            paint.style = Paint.Style.STROKE; paint.strokeWidth = (if (laser.warning > 0f) 1.2f else laser.width) * s
            paint.color = if (laser.warning > 0f) 0xFFFFD45C.toInt() else 0xFFFF5868.toInt()
            c.drawLine(laser.x * s, laser.originY * s, laser.x * s, sh.toFloat(), paint)
        }''',
'''        for (laser in enemyLasers) {
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
        paint.style = Paint.Style.FILL''', "enemy laser emitters")

# Increase the shield beyond the ship's silhouette and show its remaining health in the HUD.
rep('''        if (shieldTimer > 0f || shieldLevel > 0) {
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.4f * s; paint.color = 0xFFFF79D8.toInt()
            c.drawCircle(px * s, py * s, 16f * s, paint)
            paint.clearShadowLayer(); paint.style = Paint.Style.FILL
        }''',
'''        if (shieldTimer > 0f || shieldHp > 0) {
            val shieldBounds = RectF((px - 27f) * s, (py - 27f) * s, (px + 27f) * s, (py + 27f) * s)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 4f * s; paint.color = 0x3355DFFF.toInt()
            val shieldPulse = 25f + sin(time * 4.8f) * 1.4f
            c.drawCircle(px * s, py * s, shieldPulse * s, paint)
            paint.strokeWidth = 1.7f * s; paint.color = 0xFF55DFFF.toInt()
            c.drawCircle(px * s, py * s, 25f * s, paint)
            if (shieldHp > 0 && shieldMaxHp > 0) {
                paint.strokeWidth = 2.4f * s; paint.color = 0xFFFF6688.toInt()
                c.drawArc(shieldBounds, -90f, 360f * shieldHp.toFloat() / shieldMaxHp.toFloat(), false, paint)
            }
            paint.clearShadowLayer(); paint.style = Paint.Style.FILL
        }''', "larger shield shell")
rep('''        text(c, "×".repeat(lives), 80f, 27f, 7f, 0xFFFFD45C.toInt(), Paint.Align.LEFT)
        val powerLabel = when {''',
'''        text(c, "×".repeat(lives), 80f, 27f, 7f, 0xFFFFD45C.toInt(), Paint.Align.LEFT)
        if (shieldRank > 0) {
            text(c, "SH", 7f, 36f, 5.5f, 0xFF8BDFFF.toInt(), Paint.Align.LEFT)
            paint.color = 0x88303C4A.toInt(); c.drawRoundRect(18f*s, 30f*s, 72f*s, 33f*s, 1.5f*s, 1.5f*s, paint)
            paint.color = if (shieldHp > shieldMaxHp / 3) 0xFF55DFFF.toInt() else 0xFFFF6688.toInt()
            val shieldFrac = if (shieldMaxHp > 0) shieldHp.toFloat() / shieldMaxHp else 0f
            c.drawRoundRect(18f*s, 30f*s, (18f + 54f*shieldFrac)*s, 33f*s, 1.5f*s, 1.5f*s, paint)
        }
        val powerLabel = when {''', "shield health HUD")

# Standard, recognizable ability colors; magnet uses the blue/red polarity convention.
rep('''    private fun drawAbilityIcon(c: Canvas, id: String, cx: Float, cy: Float) {
        val color = when (id) { "drone", "missile" -> 0xFF62E7FF.toInt(); "shield", "hull" -> 0xFFFF83D9.toInt(); "chain", "rapid" -> 0xFFFFD45C.toInt(); "magnet" -> 0xFF72F0C0.toInt(); else -> 0xFF9CEBFF.toInt() }''',
'''    private fun abilityColor(id: String): Int = when (id) {
        "drone", "laser" -> 0xFF38BDF8.toInt()
        "missile" -> 0xFFFF5B4D.toInt()
        "firepower", "rapid" -> 0xFFFFB020.toInt()
        "magnet" -> 0xFF3B82F6.toInt()
        "shield" -> 0xFF22D3EE.toInt()
        "dash" -> 0xFFFFD54A.toInt()
        "chain" -> 0xFFA78BFA.toInt()
        "hull" -> 0xFF4ADE80.toInt()
        else -> 0xFFB8E7FF.toInt()
    }

    private fun drawAbilityIcon(c: Canvas, id: String, cx: Float, cy: Float) {
        val color = abilityColor(id)''', "standard ability palette")
rep('''            "magnet" -> { val p=Path().apply { moveTo(ox-7f*s,oy-6f*s); lineTo(ox-7f*s,oy+2f*s); quadTo(ox,oy+12f*s,ox+7f*s,oy+2f*s); lineTo(ox+7f*s,oy-6f*s) }; c.drawPath(p,paint); c.drawLine(ox-7f*s,oy-6f*s,ox-3f*s,oy-6f*s,paint); c.drawLine(ox+3f*s,oy-6f*s,ox+7f*s,oy-6f*s,paint) }''',
'''            "magnet" -> {
                val p=Path().apply { moveTo(ox-7f*s,oy-6f*s); lineTo(ox-7f*s,oy+2f*s); quadTo(ox,oy+12f*s,ox+7f*s,oy+2f*s); lineTo(ox+7f*s,oy-6f*s) }
                paint.color = 0xFFE6EDF5.toInt(); c.drawPath(p,paint)
                paint.color = 0xFF3B82F6.toInt(); c.drawLine(ox-7f*s,oy-6f*s,ox-7f*s,oy+1f*s,paint); c.drawLine(ox-7f*s,oy-6f*s,ox-3f*s,oy-6f*s,paint)
                paint.color = 0xFFEF4444.toInt(); c.drawLine(ox+7f*s,oy-6f*s,ox+7f*s,oy+1f*s,paint); c.drawLine(ox+3f*s,oy-6f*s,ox+7f*s,oy-6f*s,paint)
            }''', "blue red magnet icon")
rep('paint.color = when (id) { "shield", "dash" -> 0xFFFF79D8.toInt(); "chain" -> 0xFF72F0C0.toInt(); else -> 0xFF55DFFF.toInt() }',
    'paint.color = abilityColor(id)', "upgrade card colors")

# Reusable glass surfaces and empty ad placeholders (no ad SDK, requests or content yet).
glass_helpers = '''    private fun drawGlassPanel(c: Canvas, left: Float, top: Float, right: Float, bottom: Float, accent: Int, alpha: Int = 218) {
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

'''
if "private fun drawGlassPanel(" not in g:
    anchor = "    private fun upgradeTitle(id: String): String = when (id) {"
    if anchor not in g:
        raise RuntimeError("Neon 1.6 patch anchor missing: glass helper insertion")
    g = g.replace(anchor, glass_helpers + anchor, 1)

# Upgrade and shop surfaces become layered glass panels; buttons retain neon edge lighting.
rep('''        paint.color = 0xE8101722.toInt(); c.drawRect(0f, 0f, sw.toFloat(), sh.toFloat(), paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.2f * s; paint.color = 0xFF47DFFF.toInt()
        c.drawRoundRect(5f * s, wh * 0.30f * s, (W - 5f) * s, wh * 0.76f * s, 7f * s, 7f * s, paint)
        paint.style = Paint.Style.FILL''',
'''        paint.color = 0xB807101D.toInt(); c.drawRect(0f, 0f, sw.toFloat(), sh.toFloat(), paint)
        drawGlassPanel(c, 5f, wh * 0.30f, W - 5f, wh * 0.76f, 0xFF47DFFF.toInt(), 232)''', "upgrade glass background")
rep('''            paint.style = Paint.Style.STROKE; paint.strokeWidth = 0.9f * s
            paint.color = abilityColor(id)
            c.drawRoundRect((cx - 30f) * s, wh * 0.43f * s, (cx + 30f) * s, wh * 0.60f * s, 4f * s, 4f * s, paint)
            paint.style = Paint.Style.FILL''',
'''            paint.color = abilityColor(id)
            drawGlassPanel(c, cx - 30f, wh * 0.43f, cx + 30f, wh * 0.60f, abilityColor(id), 190)
            paint.style = Paint.Style.FILL''', "upgrade card glass")
rep('''        paint.color = 0xE8101722.toInt(); c.drawRect(0f, 0f, sw.toFloat(), sh.toFloat(), paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.2f * s; paint.color = 0xFFFFD45C.toInt()
        c.drawRoundRect(25f * s, wh * 0.28f * s, 195f * s, wh * 0.84f * s, 8f * s, 8f * s, paint)
        paint.style = Paint.Style.FILL''',
'''        paint.color = 0xB807101D.toInt(); c.drawRect(0f, 0f, sw.toFloat(), sh.toFloat(), paint)
        drawGlassPanel(c, 25f, wh * 0.28f, 195f, wh * 0.84f, 0xFFFFD45C.toInt(), 232)''', "shop glass background")
rep('''    private fun drawPause(c: Canvas) {
        paint.style = Paint.Style.FILL
        paint.color = 0xD9101722.toInt()
        c.drawRect(0f, 0f, sw.toFloat(), sh.toFloat(), paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f * s
        paint.color = 0xFF47DFFF.toInt()
        c.drawRoundRect(25f * s, (wh * 0.34f) * s, 195f * s, (wh * 0.69f) * s, 8f * s, 8f * s, paint)
        text(c, "بازی متوقف شد", W / 2f, wh * 0.395f, 19f, 0xFF9CEBFF.toInt())
        paint.style = Paint.Style.FILL
        drawButtons(c)
    }''',
'''    private fun drawPause(c: Canvas) {
        paint.style = Paint.Style.FILL
        paint.color = 0xA507101D.toInt()
        c.drawRect(0f, 0f, sw.toFloat(), sh.toFloat(), paint)
        // High-opacity glass card hides ships directly behind the pause controls.
        drawGlassPanel(c, 25f, wh * 0.34f, 195f, wh * 0.72f, 0xFF47DFFF.toInt(), 248)
        text(c, "بازی متوقف شد", W / 2f, wh * 0.395f, 19f, 0xFF9CEBFF.toInt())
        drawButtons(c)
        drawAdSlot(c, wh * 0.665f, 132f, 20f)
    }''', "pause glass panel and empty ad slot")

# Button fill, outer glow and pressed depth.
rep('''            paint.style = Paint.Style.FILL
            paint.color = if (pressed) 0xAAFFFFFF.toInt() else if (b.id.startsWith("upgrade_")) 0x5535D9F2.toInt() else 0x332BD8F4.toInt()
            c.drawRoundRect(r.left*s,r.top*s,r.right*s,r.bottom*s,4f*s,4f*s,paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = (if (pressed) 1.8f else 1.15f) * s; paint.color = col
            c.drawRoundRect(r.left*s,r.top*s,r.right*s,r.bottom*s,4f*s,4f*s,paint)''',
'''            paint.style = Paint.Style.FILL
            paint.color = if (pressed) 0xAAFFFFFF.toInt() else if (b.id.startsWith("shop_")) 0x44FFB020.toInt() else 0x4435D9F2.toInt()
            c.drawRoundRect(r.left*s,r.top*s,r.right*s,r.bottom*s,5f*s,5f*s,paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 3.2f * s; paint.color = Color.argb(52, Color.red(col), Color.green(col), Color.blue(col))
            c.drawRoundRect(r.left*s,r.top*s,r.right*s,r.bottom*s,5f*s,5f*s,paint)
            paint.strokeWidth = (if (pressed) 1.9f else 1.2f) * s; paint.color = col
            c.drawRoundRect(r.left*s,r.top*s,r.right*s,r.bottom*s,5f*s,5f*s,paint)''', "neon glass buttons")

# Menu, shop, upgrade and end screens reserve blank ad areas; gameplay remains unobstructed.
rep('''    private fun drawMenu(c: Canvas) {
        text(c, "NEON", W / 2, wh * 0.16f, 34f, YEL)''',
'''    private fun drawMenu(c: Canvas) {
        drawGlassPanel(c, 25f, wh * 0.37f, 195f, wh * 0.73f, 0xFF47DFFF.toInt(), 224)
        text(c, "NEON", W / 2, wh * 0.16f, 34f, YEL)''', "menu glass panel")
rep('''        text(c, "BEST $best  WAVE $bestWave", W / 2, wh * 0.44f, 7f, WHT)
        drawButtons(c)
    }

    private fun drawOver(c: Canvas) {
        paint.color = 0xAA000000.toInt(); c.drawRect(0f, 0f, sw.toFloat(), sh.toFloat(), paint)''',
'''        text(c, "BEST $best  WAVE $bestWave", W / 2, wh * 0.44f, 7f, WHT)
        drawButtons(c)
        drawAdSlot(c, wh * 0.83f)
    }

    private fun drawOver(c: Canvas) {
        paint.color = 0xAA000000.toInt(); c.drawRect(0f, 0f, sw.toFloat(), sh.toFloat(), paint)
        drawGlassPanel(c, 25f, wh * 0.24f, 195f, wh * 0.75f, 0xFFFF6688.toInt(), 226)''', "menu ad slot and gameover glass")
rep('''        if (overT >= 0.7f) drawButtons(c)
    }
}''',
'''        if (overT >= 0.7f) drawButtons(c)
        drawAdSlot(c, wh * 0.84f)
    }
}''', "gameover ad slot")
rep('''        text(c, "CREDITS " + credits, W / 2f, wh * 0.40f, 9f, WHT)
        drawButtons(c)
    }''',
'''        text(c, "CREDITS " + credits, W / 2f, wh * 0.40f, 9f, WHT)
        drawButtons(c)
        drawAdSlot(c, wh * 0.78f, 138f, 20f)
    }''', "shop ad slot")
rep('''        }
        drawButtons(c)
    }

    private fun drawShop(c: Canvas) {''',
'''        }
        drawButtons(c)
        drawAdSlot(c, wh * 0.84f)
    }

    private fun drawShop(c: Canvas) {''', "upgrade ad slot")

# Remove placeholder Google IDs while retaining local achievement resource names.
if strings_file.exists():
    strings = strings_file.read_text(encoding="utf-8")
    strings = re.sub(r'\s*<string name="(?:app_id|leaderboard_high_score)"[^>]*>.*?</string>', "", strings)
    strings_file.write_text(strings, encoding="utf-8")

# Bump package version for in-place installation over 1.5.
gradle = gradle_file.read_text(encoding="utf-8")
gradle = re.sub(r"versionCode\s*=\s*(\d+)", lambda m: "versionCode = " + str(int(m.group(1)) + 1), gradle, count=1)
gradle = re.sub(r'versionName\s*=\s*"[^"]+"', 'versionName = "1.6"', gradle, count=1)
gradle_file.write_text(gradle, encoding="utf-8")

game_file.write_text(g, encoding="utf-8")

# Refresh nested Android source archive so the uploaded ZIP matches the APK.
archive = next(Path(".").glob("NeonShooter_*.zip"))
tmp = archive.with_suffix(".neon16.tmp.zip")
with zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
    for f in sorted(root.rglob("*")):
        if f.is_file():
            z.write(f, Path(root.name) / f.relative_to(root))
tmp.replace(archive)
print("Neon Shooter 1.6 shield, laser emitters, glass UI and empty ad slots prepared.")
