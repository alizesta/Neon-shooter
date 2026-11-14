from pathlib import Path
import re

root = Path("NeonShooter")
game_file = root / "app/src/main/java/com/claude/neonshooter/GameView.kt"
g = game_file.read_text(encoding="utf-8")

def rep(old, new, label):
    global g
    if new in g:
        return
    if old not in g:
        raise RuntimeError("Neon 1.9 patch anchor missing: " + label)
    g = g.replace(old, new, 1)

# Eight new selectable abilities.
rep("    private var empRank = 0\n    private var empPulseTimer = 0f\n",
"""    private var empRank = 0
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
""", "new ability state")

rep('        "emp" -> empRank\n', '''        "emp" -> empRank
        "ricochet" -> ricochetRank
        "gravity_well" -> gravityWellRank
        "overload" -> overloadRank
        "nanobots" -> nanobotsRank
        "scanner" -> scannerRank
        "salvage_drone" -> salvageDroneRank
        "kill_streak" -> killStreakRank
        "ricochet_shield" -> ricochetShieldRank
''', "ability rank mapping")

rep('"hull", "emp",', '"hull", "emp", "ricochet", "gravity_well", "overload", "nanobots", "scanner", "salvage_drone", "kill_streak", "ricochet_shield",', "ability pool")

rep('''            "emp" -> { empRank = min(5, empRank + 1); empPulseTimer = min(empPulseTimer, 0.5f) }
''', '''            "emp" -> { empRank = min(5, empRank + 1); empPulseTimer = min(empPulseTimer, 0.5f) }
            "ricochet" -> ricochetRank = min(5, ricochetRank + 1)
            "gravity_well" -> { gravityWellRank = min(5, gravityWellRank + 1); gravityPulseTimer = min(gravityPulseTimer, 0.5f) }
            "overload" -> { overloadRank = min(5, overloadRank + 1); overloadTimer = max(overloadTimer, 2.5f) }
            "nanobots" -> { nanobotsRank = min(5, nanobotsRank + 1); nanobotTimer = min(nanobotTimer, 1f) }
            "scanner" -> { scannerRank = min(5, scannerRank + 1); scannerPulseTimer = min(scannerPulseTimer, 0.5f) }
            "salvage_drone" -> { salvageDroneRank = min(5, salvageDroneRank + 1); salvageTimer = min(salvageTimer, 1f) }
            "kill_streak" -> killStreakRank = min(5, killStreakRank + 1)
            "ricochet_shield" -> ricochetShieldRank = min(5, ricochetShieldRank + 1)
''', "ability upgrade effects")

rep('''        healthRegenTimer = 0f; empPulseTimer = 0f; empRank = 0; phoenixUsed = false; legendaryTime = 0f; legendaryCooldown = 0f
''', '''        healthRegenTimer = 0f; empPulseTimer = 0f; empRank = 0; phoenixUsed = false; legendaryTime = 0f; legendaryCooldown = 0f
        ricochetRank = 0; gravityWellRank = 0; overloadRank = 0; nanobotsRank = 0; scannerRank = 0; salvageDroneRank = 0; killStreakRank = 0; ricochetShieldRank = 0
        gravityPulseTimer = 0f; gravityActiveTimer = 0f; overloadTimer = 0f; nanobotTimer = 0f; scannerPulseTimer = 0f; scannerTimer = 0f; salvageTimer = 0f
''', "reset new ability state")

# Timed ability loops: gravity pull, overload, nanobot repairs, scanner pulse and salvage.
rep('''        val enemyDt = dt * when { enemySlowTimer > 0f -> 0.1f; stasisRank > 0 -> 1f - stasisRank * 0.08f; else -> 1f }
''', '''        overloadTimer = max(0f, overloadTimer - dt)
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
''', "ability timers and combat effects")

# Overload adds a visible temporary fire-rate and damage boost.
rep('''        if (fireT <= 0f) { fireT = (if (rapidTimer > 0f) 0.075f else 0.19f) * shipFireDelay() * (1f - fireRateBonus); shoot() }
''', '''        if (fireT <= 0f) { fireT = (if (rapidTimer > 0f) 0.075f else 0.19f) * shipFireDelay() * (1f - fireRateBonus) * (if (overloadTimer > 0f) 0.58f else 1f); shoot() }
''', "overload fire rate")

# Ricochet bullets seek a fresh target after a hit instead of disappearing.
rep('''                    b.hitEnemies.add(e)
                    if (piercingTimer <= 0f && b.hitEnemies.size > piercingRank) deadB.add(b)
''', '''                    b.hitEnemies.add(e)
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
''', "ricochet targeting")

rep('''                    e.hp -= 1 + bonusDamage + (if (currentShip == 3) 1 else 0) + criticalDamage
''', '''                    e.hp -= 1 + bonusDamage + (if (currentShip == 3) 1 else 0) + criticalDamage + (if (overloadTimer > 0f) 1 else 0) + (if (scannerTimer > 0f) scannerRank else 0)
''', "overload and scanner damage")

# Ricochet shield gives a ranked chance to bounce damage back as a force-field burst.
rep('''        if (armorRank > 0 && Random.nextFloat() < armorRank * 0.075f) { invul = 0.55f; boom(px, py, 8, 34f); return }
''', '''        if (armorRank > 0 && Random.nextFloat() < armorRank * 0.075f) { invul = 0.55f; boom(px, py, 8, 34f); return }
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
''', "ricochet shield deflection")

# Kill streak boosts score from sustained kills.
rep('''        e.dead = true
''', '''        e.dead = true
        if (killStreakRank > 0) score += min(500, comboCount * killStreakRank * 3)
''', "kill streak bonus")

# PixelFX-inspired native Canvas VFX: EMP/black-hole rings, scanner target lock,
# nanobot orbit motes, overload arcs, salvage drone and reflective force field.
vfx = r"""
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

"""
if "private fun drawNeonAbilityVfx(" not in g:
    anchor = "    private fun drawGame(c: Canvas) {"
    if anchor not in g:
        raise RuntimeError("Neon 1.9 patch anchor missing: drawGame")
    g = g.replace(anchor, vfx + anchor, 1)

rep('''        for (hole in blackHoles) drawBlackHole(c, hole)
''', '''        for (hole in blackHoles) drawBlackHole(c, hole)
        drawNeonAbilityVfx(c)
''', "render ability VFX")

# Add labels, descriptions, colors and glyphs for the eight new cards.
rep('''        "emp" -> "EMP PULSE"
''', '''        "emp" -> "EMP PULSE"
        "ricochet" -> "RICOCHET"
        "gravity_well" -> "GRAVITY WELL"
        "overload" -> "OVERLOAD"
        "nanobots" -> "NANOBOTS"
        "scanner" -> "WEAK POINT SCANNER"
        "salvage_drone" -> "SALVAGE DRONE"
        "kill_streak" -> "KILL STREAK"
        "ricochet_shield" -> "RICOCHET SHIELD"
''', "ability card names")
rep('''        "emp" -> "PULSE DAMAGES ALL ENEMIES"
''', '''        "emp" -> "PULSE DAMAGES ALL ENEMIES"
        "ricochet" -> "BULLETS BOUNCE TO NEW TARGETS"
        "gravity_well" -> "PULL AND DAMAGE NEARBY ENEMIES"
        "overload" -> "TEMPORARY FIRE RATE AND DAMAGE BOOST"
        "nanobots" -> "REPAIR HULL AND SHIELD OVER TIME"
        "scanner" -> "MARK TARGETS FOR BONUS DAMAGE"
        "salvage_drone" -> "DRONE COLLECTS EXTRA CREDITS"
        "kill_streak" -> "CHAINED KILLS GRANT BONUS SCORE"
        "ricochet_shield" -> "CHANCE TO REFLECT INCOMING DAMAGE"
''', "ability card descriptions")
rep('''        "emp" -> 0xFF67E8F9.toInt()
''', '''        "emp" -> 0xFF67E8F9.toInt()
        "ricochet", "scanner" -> 0xFFFF668F.toInt()
        "gravity_well" -> 0xFFB18CFF.toInt()
        "overload" -> 0xFFFFB347.toInt()
        "nanobots" -> 0xFF63F5C5.toInt()
        "salvage_drone", "kill_streak" -> 0xFFFFD66B.toInt()
        "ricochet_shield" -> 0xFF65DFFF.toInt()
''', "ability card colors")
rep('''            "emp" -> { c.drawCircle(ox,oy,3f*s,paint); c.drawCircle(ox,oy,7f*s,paint); c.drawCircle(ox,oy,11f*s,paint) }
''', '''            "emp" -> { c.drawCircle(ox,oy,3f*s,paint); c.drawCircle(ox,oy,7f*s,paint); c.drawCircle(ox,oy,11f*s,paint) }
            "ricochet" -> { c.drawLine(ox-9f*s,oy+5f*s,ox+6f*s,oy-5f*s,paint); c.drawLine(ox+6f*s,oy-5f*s,ox+2f*s,oy-5f*s,paint); c.drawLine(ox+6f*s,oy-5f*s,ox+6f*s,oy-1f*s,paint) }
            "gravity_well" -> { c.drawCircle(ox,oy,4f*s,paint); c.drawCircle(ox,oy,9f*s,paint); c.drawArc(RectF(ox-12f*s,oy-7f*s,ox+12f*s,oy+7f*s),20f,260f,false,paint) }
            "overload" -> { c.drawLine(ox+2f*s,oy-10f*s,ox-4f*s,oy+1f*s,paint); c.drawLine(ox-4f*s,oy+1f*s,ox+3f*s,oy+1f*s,paint); c.drawLine(ox+3f*s,oy+1f*s,ox-2f*s,oy+10f*s,paint) }
            "nanobots", "salvage_drone" -> { c.drawCircle(ox,oy,3f*s,paint); c.drawCircle(ox-8f*s,oy-4f*s,2f*s,paint); c.drawCircle(ox+8f*s,oy+4f*s,2f*s,paint) }
            "scanner" -> { c.drawCircle(ox,oy,8f*s,paint); c.drawLine(ox-11f*s,oy,ox-5f*s,oy,paint); c.drawLine(ox+5f*s,oy,ox+11f*s,oy,paint) }
            "kill_streak" -> { c.drawLine(ox-9f*s,oy+7f*s,ox-2f*s,oy,paint); c.drawLine(ox-2f*s,oy,ox+3f*s,oy+3f*s,paint); c.drawLine(ox+3f*s,oy+3f*s,ox+10f*s,oy-8f*s,paint) }
            "ricochet_shield" -> { c.drawCircle(ox,oy,9f*s,paint); c.drawArc(RectF(ox-12f*s,oy-12f*s,ox+12f*s,oy+12f*s),-35f,250f,false,paint) }
''', "ability card glyphs")

gradle = root / "app/build.gradle.kts"
build = gradle.read_text(encoding="utf-8")
if 'versionCode = 11' not in build or 'versionName = "1.8"' not in build:
    raise RuntimeError("Neon 1.9 version bump target missing")
build = build.replace("versionCode = 11", "versionCode = 12", 1).replace('versionName = "1.8"', 'versionName = "1.9"', 1)
gradle.write_text(build, encoding="utf-8")
game_file.write_text(g, encoding="utf-8")

# Keep PixelFX MIT attribution alongside the existing notices.
notice = root / "app/src/main/assets/THIRD_PARTY_NOTICES.txt"
if notice.exists():
    n = notice.read_text(encoding="utf-8")
    if "Patch-gg PixelFX" not in n:
        n += "\nPixelFX visual-effect techniques adapted for native Android Canvas: https://github.com/patch-gg/pixelfx\nMIT License, Copyright (c) 2026 Patch and Hugo Duprez.\n"
        notice.write_text(n, encoding="utf-8")
print("Neon Shooter 1.9: eight new abilities and PixelFX-inspired native Android VFX integrated.")
