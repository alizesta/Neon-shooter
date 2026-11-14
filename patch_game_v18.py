from pathlib import Path
p = Path('NeonShooter/app/src/main/java/com/claude/neonshooter/GameView.kt')
s = p.read_text(encoding='utf-8')

def rep(old, new, label):
    global s
    if old not in s:
        raise SystemExit(f'patch target missing: {label}')
    s = s.replace(old, new, 1)

# Replace timed Rapid Fire upgrade with a permanent EMP pulse.
rep('    private var rapidLevel = 0\n', '    private var empRank = 0\n    private var empPulseTimer = 0f\n', 'emp fields')
rep('        "rapid" -> rapidLevel\n', '        "emp" -> empRank\n', 'emp rank')
rep('"hull", "rapid",\n            "health_regen"', '"hull", "emp",\n            "health_regen"', 'ability pool')
rep('            "rapid" -> { rapidLevel = min(5, rapidLevel + 1); rapidTimer = max(rapidTimer, 8f + rapidLevel * 2f) }\n', '            "emp" -> { empRank = min(5, empRank + 1); empPulseTimer = min(empPulseTimer, 0.5f) }\n', 'emp upgrade')
rep('        healthRegenTimer = 0f; phoenixUsed = false; legendaryTime = 0f; legendaryCooldown = 0f\n', '        healthRegenTimer = 0f; empPulseTimer = 0f; empRank = 0; phoenixUsed = false; legendaryTime = 0f; legendaryCooldown = 0f\n', 'reset emp')
rep('        weaponLevel = 1; missileLevel = 0; laserLevel = 0; droneLevel = 0; rapidLevel = 0; dashRank = 0; shieldRank = if (currentShip == 2) 1 else 0\n', '        weaponLevel = 1; missileLevel = 0; laserLevel = 0; droneLevel = 0; dashRank = 0; shieldRank = if (currentShip == 2) 1 else 0\n', 'remove rapid ability reset')

# Pulse periodically damages every living enemy; higher ranks increase damage and frequency.
rep('        val enemyDt = dt * when { enemySlowTimer > 0f -> 0.1f; stasisRank > 0 -> 1f - stasisRank * 0.08f; else -> 1f }\n', '''        val enemyDt = dt * when { enemySlowTimer > 0f -> 0.1f; stasisRank > 0 -> 1f - stasisRank * 0.08f; else -> 1f }
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
''', 'emp pulse update')
rep('            rapidTimer > 0f -> "RAPID " + (rapidTimer.toInt() + 1)\n', '            empRank > 0 -> "EMP PULSE " + empRank + "  " + (empPulseTimer.toInt().coerceAtLeast(0)) + "s\n            rapidTimer > 0f -> "RAPID " + (rapidTimer.toInt() + 1)\n', 'emp hud')

rep('        "health_regen" -> "HEALTH REGEN"\n', '        "health_regen" -> "HEALTH REGEN"\n        "emp" -> "EMP PULSE"\n', 'emp title')
rep('        "health_regen" -> "REPAIR HULL OVER TIME"\n', '        "health_regen" -> "REPAIR HULL OVER TIME"\n        "emp" -> "PULSE DAMAGES ALL ENEMIES"\n', 'emp info')
rep('        "firepower", "rapid" -> 0xFFFFB020.toInt()\n', '        "firepower" -> 0xFFFFB020.toInt()\n        "emp" -> 0xFF67E8F9.toInt()\n', 'emp color')
rep('            "hull", "health_regen", "phoenix" -> { c.drawLine(ox-7f*s,oy,ox+7f*s,oy,paint); c.drawLine(ox,oy-7f*s,ox,oy+7f*s,paint); c.drawCircle(ox,oy,9f*s,paint) }\n', '            "hull", "health_regen", "phoenix" -> { c.drawLine(ox-7f*s,oy,ox+7f*s,oy,paint); c.drawLine(ox,oy-7f*s,ox,oy+7f*s,paint); c.drawCircle(ox,oy,9f*s,paint) }\n            "emp" -> { c.drawCircle(ox,oy,3f*s,paint); c.drawCircle(ox,oy,7f*s,paint); c.drawCircle(ox,oy,11f*s,paint) }\n', 'emp icon')

helper = '''    private fun drawFittedText(c: Canvas, value: String, x: Float, y: Float, size: Float, color: Int, maxWidth: Float) {
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

'''
rep('    private fun drawUpgrade(c: Canvas) {\n', helper + '    private fun drawUpgrade(c: Canvas) {\n', 'card helpers')
rep('''            text(c, upgradeTitle(id) + "  " + (rank + 1) + "/5", cx, wh * 0.515f, 5.8f, WHT)
            text(c, upgradeInfo(id), cx, wh * 0.55f, 4.4f, 0xFF9CEBFF.toInt())
''', '''            drawFittedText(c, upgradeTitle(id) + "  " + (rank + 1) + "/5", cx, wh * 0.515f, 5.8f, WHT, 54f)
            drawWrappedCardText(c, upgradeInfo(id), cx, wh * 0.548f, 4.1f, 0xFF9CEBFF.toInt(), 52f)
''', 'card text layout')

old = '''        paint.color = 0xCC25313D.toInt()
        c.drawRoundRect(18f * s, 21f * s, 72f * s, 25f * s, 1.8f * s, 1.8f * s, paint)
        val baseHealth = lives.coerceAtMost(5)
        paint.color = if (baseHealth >= 3) 0xFF55DDAA.toInt() else if (baseHealth == 2) 0xFFFFC857.toInt() else 0xFFFF5968.toInt()
        c.drawRoundRect(18f * s, 21f * s, (18f + 54f * baseHealth / 5f) * s, 25f * s, 1.8f * s, 1.8f * s, paint)
        if (lives > 5) {
            paint.color = 0xCC25313D.toInt(); c.drawRoundRect(18f*s, 27f*s, 72f*s, 31f*s, 1.8f*s, 1.8f*s, paint)
            paint.color = 0xFFFFC857.toInt(); c.drawRoundRect(18f*s, 27f*s, (18f + 54f * (lives - 5).coerceAtMost(5) / 5f) * s, 31f*s, 1.8f*s, 1.8f*s, paint)
        }
        if (lives > 5) text(c, "2x", 77f, 31f, 7f, 0xFFFFC857.toInt(), Paint.Align.LEFT)
        else text(c, lives.toString() + "/5", 77f, 27f, 6f, 0xFFFFD45C.toInt(), Paint.Align.LEFT)
'''
new = '''        paint.color = 0xCC25313D.toInt()
        c.drawRoundRect(18f * s, 21f * s, 72f * s, 25f * s, 1.8f * s, 1.8f * s, paint)
        val baseHealth = lives.coerceAtMost(5)
        val extraHealth = (lives - 5).coerceIn(0, 5)
        paint.color = if (baseHealth >= 3) 0xFF55DDAA.toInt() else if (baseHealth == 2) 0xFFFFC857.toInt() else 0xFFFF5968.toInt()
        c.drawRoundRect(18f * s, 21f * s, (18f + 54f * baseHealth / 5f) * s, 23f * s, 1.0f * s, 1.0f * s, paint)
        if (extraHealth > 0) {
            paint.color = 0xFFFFC857.toInt()
            c.drawRoundRect(18f * s, 23f * s, (18f + 54f * extraHealth / 5f) * s, 25f * s, 1.0f * s, 1.0f * s, paint)
        }
        if (lives > 5) text(c, "2x", 77f, 25f, 7f, 0xFFFFC857.toInt(), Paint.Align.LEFT)
        else text(c, lives.toString() + "/5", 77f, 25f, 6f, 0xFFFFD45C.toInt(), Paint.Align.LEFT)
'''
rep(old, new, 'single stacked health bar')

p.write_text(s, encoding='utf-8')
gradle = Path('NeonShooter/app/build.gradle.kts')
g = gradle.read_text(encoding='utf-8')
if 'versionCode = 10' not in g or 'versionName = "1.7"' not in g:
    raise SystemExit('version bump target missing')
g = g.replace('versionCode = 10', 'versionCode = 11', 1).replace('versionName = "1.7"', 'versionName = "1.8"', 1)
gradle.write_text(g, encoding='utf-8')
print('patched Neon Shooter 1.8')
