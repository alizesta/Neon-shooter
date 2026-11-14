#!/usr/bin/env python3
from pathlib import Path
import subprocess, zipfile, re

root = Path("NeonShooter")
archive = next(Path(".").glob("NeonShooter_*.zip"))
base = root / "app/src/main/java/com/claude/neonshooter"
game_file = base / "GameView.kt"
sprites_file = base / "Sprites.kt"
play_file = base / "Play.kt"
main_file = base / "MainActivity.kt"

g = game_file.read_text()
already_patched = "rocketTimer" in g
def rep(src, old, new, label):
    if new in src:
        return src
    if old not in src:
        return src
    return src.replace(old, new, 1)

# Wider world viewport: same 2D gameplay, simply zoomed farther out.
g = rep(g, "private val W = 180f", "private val W = 220f", "world width")
# Six pickups: heart, spread weapon, rapid fire, shield, nuke, guided rockets.
g = rep(g, "private class Item(var x: Float, var y: Float, val type: Int) { var age = 0f }",
"""private class Item(var x: Float, var y: Float, val type: Int) { var age = 0f }""", "item class")
g = rep(g, "private var rapidTimer = 0f; private var shieldTimer = 0f",
"""private var rapidTimer = 0f; private var shieldTimer = 0f
    private var rocketTimer = 0f""", "rocket timer")
g = rep(g, "weaponLevel = 1; weaponTimer = 0f; rapidTimer = 0f; shieldTimer = 0f",
"""weaponLevel = 1; weaponTimer = 0f; rapidTimer = 0f; shieldTimer = 0f; rocketTimer = 0f""", "reset powerups")
# Better enemy variety: four silhouettes and behavior types.
g = rep(g, "val hw = if (type == 2) 19f else if (type == 1) 9f else 8f",
"""val hw = if (type == 2) 19f else if (type == 3) 11f else if (type == 4) 10f else if (type == 1) 9f else 8f""", "enemy width")
g = rep(g, "val hp = if (strong) 3 + wave / 10 else if (wave >= 7) 2 else 1\n            enemies.add(E(x, y, if (strong) 1 else 0, hp, x, amp, t0))",
"""val roll = Random.nextFloat()
            val type = when { wave >= 4 && roll < 0.16f -> 3; wave >= 6 && roll < 0.28f -> 4; strong -> 1; else -> 0 }
            val hp = when (type) { 1 -> 3 + wave / 10; 3 -> 2 + wave / 12; 4 -> 2; else -> if (wave >= 7) 2 else 1 }
            enemies.add(E(x, y, type, hp, x, amp, t0))""", "enemy types")
g = rep(g, "if (e.type == 2) {",
"""if (e.type == 2) {""", "boss branch")
# Make type 3 fast zig-zag and type 4 slow heavy; type 1 remains seeker.
g = rep(g, """                if (e.type == 1) {
                    val targetX = (px + sin(e.age * 1.25f + e.t0) * 13f).coerceIn(12f, W - 12f)
                    val shift = 28f * dt
                    e.x += (targetX - e.x).coerceIn(-shift, shift)
                } else {""",
"""                if (e.type == 1) {
                    val targetX = (px + sin(e.age * 1.25f + e.t0) * 13f).coerceIn(12f, W - 12f)
                    val shift = 28f * dt
                    e.x += (targetX - e.x).coerceIn(-shift, shift)
                } else if (e.type == 3) {
                    e.x = (e.bx + sin(e.age * 4.2f + e.t0) * 34f).coerceIn(10f, W - 10f)
                    e.y += spd * 1.18f * dt
                } else if (e.type == 4) {
                    e.x = (e.bx + sin(e.age * 0.9f + e.t0) * 18f).coerceIn(12f, W - 12f)
                } else {""", "enemy movement variety")
# Controlled drops: 12% ordinary enemy, guaranteed boss; no pickup spam.
g = rep(g, "val dropChance = if (e.type == 2) 0.95f else 0.22f",
"""val dropChance = if (e.type == 2) 0.90f else 0.12f""", "drop rate")
g = rep(g, """                lives <= 1 && roll < 0.45f -> 0
                roll < 0.25f -> 0
                roll < 0.55f -> 1
                roll < 0.78f -> 2
                else -> 3""",
"""                lives <= 1 && roll < 0.30f -> 0
                roll < 0.18f -> 0
                roll < 0.38f -> 1
                roll < 0.55f -> 2
                roll < 0.72f -> 3
                roll < 0.86f -> 4
                else -> 5""", "pickup probability")
g = rep(g, "rapidTimer = max(0f, rapidTimer - dt)\n        shieldTimer = max(0f, shieldTimer - dt)",
"""rapidTimer = max(0f, rapidTimer - dt)
        shieldTimer = max(0f, shieldTimer - dt)
        rocketTimer = max(0f, rocketTimer - dt)""", "rocket countdown")
# Rocket upgrade adds homing projectiles, not a permanent firehose.
g = rep(g, """        sfx.play("shoot")
    }

    private fun update(dt: Float)""",
"""        if (rocketTimer > 0f && time % 0.55f < 0.18f) {
            bullets.add(B(px - 7f, py - 5f, -12f, -145f, true))
            bullets.add(B(px + 7f, py - 5f, 12f, -145f, true))
        }
        sfx.play("shoot")
    }

    private fun update(dt: Float)""", "rocket firing")
# Pickups effects, including nuke.
g = rep(g, """                2 -> rapidTimer = 10f
                else -> shieldTimer = 10f""",
"""                2 -> rapidTimer = 9f
                3 -> shieldTimer = 10f
                4 -> {
                    val targets = enemies.toList()
                    targets.forEach { kill(it) }
                    bullets.removeAll { !it.mine }
                    boom(W / 2f, wh * 0.42f, 110, 150f)
                }
                else -> rocketTimer = 12f""", "pickup effects")
# Make enemy projectile sprites smaller (source bitmap is in Sprites.kt).
# More enemy silhouettes in rendering.
g = rep(g, "for (e in enemies) spr(c, if (e.type == 2) bBoss else if (e.type == 1) bE1 else bE0, e.x, e.y)",
"""for (e in enemies) {
            val sprite = when (e.type) { 2 -> bBoss; 1 -> bE1; 3 -> bE2; 4 -> bE3; else -> bE0 }
            spr(c, sprite, e.x, e.y)
        }""", "enemy render")
g = rep(g, "private lateinit var bE0: Bitmap; private lateinit var bE1: Bitmap; private lateinit var bBoss: Bitmap",
"""private lateinit var bE0: Bitmap; private lateinit var bE1: Bitmap; private lateinit var bBoss: Bitmap
    private lateinit var bE2: Bitmap; private lateinit var bE3: Bitmap""", "enemy bitmap fields")
g = rep(g, "bBoss = Sprites.enemy(BOSS_COLOR, BOSS_GLOW, ps, 2)",
"""bBoss = Sprites.enemy(BOSS_COLOR, BOSS_GLOW, ps, 2)
        bE2 = Sprites.enemy(0xFF62DFFF.toInt(), 0xFF9CEBFF.toInt(), ps, 3)
        bE3 = Sprites.enemy(0xFFB68CFF.toInt(), 0xFFD7BCFF.toInt(), ps, 4)""", "enemy bitmap setup")
# Replace pickup letters with distinct, recognizable vector icons.
start = g.index("    private fun drawItem(c: Canvas, item: Item) {")
end = g.index("\n    private fun text(", start)
draw_item = """    private fun drawItem(c: Canvas, item: Item) {
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
"""
g = g[:start] + draw_item + g[end:]
game_file.write_text(g)

# GAMEPLAY V4: remove all artificial bloom, flatten every sprite, improve projectile feel,
# and give successive bosses distinct movement patterns.
g = g.replace("private val YEL = 0xFFFFEA00.toInt()", "private val YEL = 0xFF35D9F2.toInt()")
g = g.replace("private val YEL = 0xFFFFD400.toInt()", "private val YEL = 0xFF35D9F2.toInt()")
g = g.replace("dy / d * 82f", "dy / d * 112f").replace("dy / d * 75f", "dy / d * 105f")
g = g.replace("0f, 66f, false", "0f, 102f, false").replace("0f, 70f, false", "0f, 108f, false")
g = g.replace("0f, -145f, true", "0f, -210f, true")
g = g.replace("if (player) 3 else 4", "if (player) 3 else 3")
g = g.replace("if (player) 8 else 4", "if (player) 5 else 4")
g = g.replace("val width = if (player) 3 else 4\\n        val height = if (player) 8 else 4",
              "val width = if (player) 3 else 3\\n        val height = if (player) 5 else 4")
# Boss style cycles every five waves. The same boss family now has three attack/movement profiles.
g = g.replace("E(W / 2, -20f, 2, 42 + wave * 7, W / 2, 0f, 0f)",
              "E(W / 2, -20f, 2, 42 + wave * 7, W / 2, 0f, ((wave / 5) % 3).toFloat())")
g = g.replace("e.x = W / 2 + sin(e.age * 0.82f) * (W / 2 - e.hw - 8f)",
"""when (e.t0.toInt() % 3) {
                    0 -> e.x = W / 2 + sin(e.age * 0.82f) * (W / 2 - e.hw - 8f)
                    1 -> { e.x = W / 2 + sin(e.age * 1.65f) * (W / 2 - e.hw - 8f); e.y = 38f + sin(e.age * 1.2f) * 9f }
                    else -> { e.x = W / 2 + sin(e.age * 0.55f) * (W / 2 - e.hw - 8f); e.y += 9f * dt }
                }""")
if "bBoss2" not in g:
    g = g.replace("private lateinit var bE2: Bitmap; private lateinit var bE3: Bitmap",
                  "private lateinit var bE2: Bitmap; private lateinit var bE3: Bitmap; private lateinit var bBoss2: Bitmap; private lateinit var bBoss3: Bitmap")
    g = g.replace("bE3 = Sprites.enemy(0xFFB68CFF.toInt(), 0xFFD7BCFF.toInt(), ps, 4)",
                  "bE3 = Sprites.enemy(0xFFB68CFF.toInt(), 0xFFD7BCFF.toInt(), ps, 4)\n        bBoss2 = Sprites.enemy(0xFF35D9D2.toInt(), 0xFF35D9D2.toInt(), ps, 5)\n        bBoss3 = Sprites.enemy(0xFFB46BFF.toInt(), 0xFFB46BFF.toInt(), ps, 6)")
    g = g.replace("2 -> bBoss; 1 -> bE1", "2 -> when (e.t0.toInt() % 3) { 0 -> bBoss; 1 -> bBoss2; else -> bBoss3 }; 1 -> bE1")
# Remove all bitmap shadow and shader effects from gameplay rendering, including item badges.
g_lines = []
for line in g.splitlines():
    if "setShadowLayer(" in line or "LinearGradient(" in line or "RadialGradient(" in line or "SweepGradient(" in line:
        continue
    g_lines.append(line)
g = "\n".join(g_lines) + "\n"
g_lines = []
seen_g = set()
for line in g.splitlines():
    if ("private var rocketTimer" in line or "private lateinit var bE2" in line or "private lateinit var bE3" in line):
        if line in seen_g:
            continue
        seen_g.add(line)
    g_lines.append(line)
g = "\n".join(g_lines) + "\n"

# GAMEPLAY V5 - canonical weapons, permanent upgrades, boss attacks and touch controls
if "private class PlayerBeam" not in g:
    g = g.replace(chr(1) + "if (e.type == 2) {", "if (e.type == 2) {")
    g = re.sub(r'(^    private var rocketTimer = 0f\n)+', '    private var rocketTimer = 0f\n', g, flags=re.M)
    g = re.sub(r'(^        rocketTimer = max\(0f, rocketTimer - dt\)\n)+', '        rocketTimer = max(0f, rocketTimer - dt)\n', g, flags=re.M)
    if 'private class PlayerBeam' not in g:
        g = g.replace(
            '    private class Item(var x: Float, var y: Float, val type: Int) { var age = 0f }',
            '''    private class Item(var x: Float, var y: Float, val type: Int) { var age = 0f }
    private class PlayerBeam(val x: Float, val bottom: Float, val width: Float, var life: Float)
    private class Missile(var x: Float, var y: Float, var vx: Float, var vy: Float, val damage: Int, var age: Float = 0f)
    private class EnemyLaser(var x: Float, var warning: Float, var active: Float, val width: Float, val drift: Float)
    private class SweepShip(var x: Float, var y: Float)'''
        )
    if 'var phase = Random.nextFloat()' not in g:
        g = g.replace(
            '    private class Star(var x: Float, val y0: Float, val sp: Float, val sz: Float) { var y = y0 }',
            '''    private class Star(var x: Float, val y0: Float, val sp: Float, val sz: Float) {
        var y = y0
        var phase = Random.nextFloat() * 6.283f
        val twinkle = Random.nextFloat() < 0.42f
    }'''
        )
    old_power_fields = '''    private var weaponLevel = 1; private var weaponTimer = 0f
    private var rapidTimer = 0f; private var shieldTimer = 0f
    private var rocketTimer = 0f'''
    new_power_fields = '''    private var weaponLevel = prefs.getInt("upgrade_weapon", 1).coerceIn(1, 5)
    private var missileLevel = prefs.getInt("upgrade_missile", 0).coerceIn(0, 3)
    private var laserLevel = prefs.getInt("upgrade_laser", 0).coerceIn(0, 3)
    private var droneLevel = prefs.getInt("upgrade_drone", 0).coerceIn(0, 2)
    private var weaponTimer = 0f
    private var rapidTimer = 0f; private var shieldTimer = 0f; private var magnetTimer = 0f
    private var rocketTimer = 0f
    private var laserCooldown = 0f; private var missileCooldown = 0f
    private var controlTouch = false'''
    if old_power_fields in g:
        g = g.replace(old_power_fields, new_power_fields, 1)
    elif 'private var missileLevel = prefs.getInt("upgrade_missile"' not in g:
        raise RuntimeError("V5: power-up fields anchor not found")
    if 'private val playerBeams = ArrayList<PlayerBeam>()' not in g:
        g = g.replace(
            '    private val items = ArrayList<Item>()',
            '''    private val items = ArrayList<Item>()
    private val playerBeams = ArrayList<PlayerBeam>()
    private val missiles = ArrayList<Missile>()
    private val enemyLasers = ArrayList<EnemyLaser>()
    private val squadShips = ArrayList<SweepShip>()
    private lateinit var bossSprites: Array<Bitmap>'''
        )
    if 'bossSprites = Array(10)' not in g:
        g = g.replace(
            '        bBoss3 = Sprites.enemy(0xFFB46BFF.toInt(), 0xFFB46BFF.toInt(), ps, 6)',
            '''        bBoss3 = Sprites.enemy(0xFFB46BFF.toInt(), 0xFFB46BFF.toInt(), ps, 6)
        val bossPalette = intArrayOf(0xFFFF4D91.toInt(), 0xFF32D8F2.toInt(), 0xFFFF9B42.toInt(), 0xFF9C7BFF.toInt(), 0xFFFF5964.toInt(), 0xFF52E0A0.toInt(), 0xFFFFD14A.toInt(), 0xFF4E9EFF.toInt(), 0xFFFF70D4.toInt(), 0xFFE8F1FF.toInt())
        bossSprites = Array(10) { i -> Sprites.enemy(bossPalette[i], bossPalette[i], ps, 5 + i) }'''
        )
    g = g.replace('((wave / 5) % 3).toFloat()', '((wave / 5 - 1).coerceIn(0, 9)).toFloat()')

    movement_pattern = r'(e\.age \+= dt\n            )if \(e\.type == 2\) \{\s*if \(e\.y < 42f\)[\s\S]*?\n            \} else \{\n                e\.y \+= spd \* dt'
    movement_replacement = r'''\g<1>if (e.type == 2) {
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
                e.y += spd * dt'''
    g, movement_count = re.subn(movement_pattern, movement_replacement, g, count=1)
    if movement_count != 1:
        raise RuntimeError("V5: could not normalise boss movement block")

    if 'val summonedEnemies = ArrayList<E>()' not in g:
        g = g.replace('        val spd = min(18f + wave * 1.15f, 38f)\n        for (e in enemies)',
                      '        val spd = min(18f + wave * 1.15f, 38f)\n        val summonedEnemies = ArrayList<E>()\n        for (e in enemies)')
    attack_pattern = r'e\.fire -= dt\n            if \(e\.fire <= 0f\) \{\n                if \(e\.type == 2\) \{[\s\S]*?\n            \}\n        \}\n        enemies\.removeAll \{ it\.y > wh \+ 12f \}'
    attack_replacement = r'''e.fire -= dt
            if (e.fire <= 0f) {
                if (e.type == 2) {
                    val bossId = e.t0.toInt().coerceIn(0, 9)
                    val enraged = e.hp * 2 <= e.maxHp
                    e.fire = max(0.48f, (if (enraged) 0.72f else 1.12f) - wave * 0.006f)
                    val base = atan2((py - e.y).toDouble(), (px - e.x).toDouble()).toFloat()
                    val speed = 74f + min(wave.toFloat(), 50f) * 1.1f
                    when (bossId) {
                        0 -> for (i in -2..2) { val a = base + i * 0.16f; bullets.add(B(e.x, e.y + 8f, cos(a) * speed, sin(a) * speed, false)) }
                        1 -> enemyLasers.add(EnemyLaser(e.x, 0.62f, 0.72f, 7f, if (sin(e.age) >= 0f) 24f else -24f))
                        2 -> {
                            for (side in -1..1 step 2) summonedEnemies.add(E((e.x + side * 16f).coerceIn(12f, W - 12f), e.y + 8f, 3, 2 + wave / 16, e.x + side * 16f, 18f, e.age + side))
                            for (i in -1..1) { val a = base + i * 0.22f; bullets.add(B(e.x, e.y + 8f, cos(a) * speed, sin(a) * speed, false)) }
                        }
                        3 -> for (i in 0 until 18) { val a = i * (2f * PI.toFloat() / 18f) + e.age * 0.18f; bullets.add(B(e.x, e.y, cos(a) * speed * 0.82f, sin(a) * speed * 0.82f, false)) }
                        4 -> for (i in 0..2) { val a = e.age * 1.7f + i * (2f * PI.toFloat() / 3f); bullets.add(B(e.x, e.y, cos(a) * speed, sin(a) * speed, false)); bullets.add(B(e.x, e.y, cos(a + PI.toFloat()) * speed, sin(a + PI.toFloat()) * speed, false)) }
                        5 -> repeat(7) { val x = Random.nextFloat() * W; bullets.add(B(x, e.y + 8f, (px - x) * 0.08f, speed * 0.9f, false)) }
                        6 -> for (i in -3..3) { val a = base + i * 0.12f; bullets.add(B(e.x, e.y + 8f, cos(a) * speed, sin(a) * speed, false)) }
                        7 -> {
                            enemyLasers.add(EnemyLaser(e.x - 26f, 0.55f, 0.7f, 6f, 18f))
                            enemyLasers.add(EnemyLaser(e.x + 26f, 0.55f, 0.7f, 6f, -18f))
                        }
                        8 -> {
                            for (i in 0 until 12) { val a = i * (2f * PI.toFloat() / 12f) + e.age * 0.24f; bullets.add(B(e.x, e.y, cos(a) * speed, sin(a) * speed, false)) }
                            for (i in -1..1) { val a = base + i * 0.2f; bullets.add(B(e.x, e.y + 8f, cos(a) * speed * 1.12f, sin(a) * speed * 1.12f, false)) }
                        }
                        else -> {
                            enemyLasers.add(EnemyLaser(e.x, 0.48f, 0.58f, 8f, if (px > e.x) 20f else -20f))
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
                            else -> bullets.add(B(e.x, e.y + 7f, cos(base) * speed, sin(base) * speed, false))
                        }
                    }
                }
            }
        }
        enemies.addAll(summonedEnemies)
        for (laser in enemyLasers) {
            laser.x += laser.drift * dt
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
        enemies.removeAll { it.y > wh + 12f }'''
    g, attack_count = re.subn(attack_pattern, attack_replacement, g, count=1)
    if attack_count != 1:
        raise RuntimeError("V5: could not replace enemy and boss firing logic")

    shoot_pattern = r'    private fun shoot\(\) \{[\s\S]*?\n    \}\n\n    private fun update\(dt: Float\)'
    shoot_replacement = r'''    private fun shoot() {
        val count = min(1 + (weaponLevel - 1) * 2, 9)
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

    private fun activateSquadron() {
        for (offset in floatArrayOf(-42f, -14f, 14f, 42f)) {
            squadShips.add(SweepShip((px + offset).coerceIn(8f, W - 8f), wh + 14f))
        }
        bullets.removeAll { !it.mine }
        sfx.play("power")
    }

    private fun update(dt: Float)'''
    if "private fun fireLaser()" not in g:
        g, shoot_count = re.subn(shoot_pattern, shoot_replacement, g, count=1)
        if shoot_count != 1:
            raise RuntimeError("V5: could not install player laser and missile controls")

    touch_pattern = r'    override fun onTouchEvent\(e: MotionEvent\): Boolean \{[\s\S]*?\n    \}\n\n    private fun onBtn'
    touch_replacement = r'''    override fun onTouchEvent(e: MotionEvent): Boolean {
        synchronized(lock) {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lx = e.x; ly = e.y; controlTouch = false
                    if (st == St.PLAY) {
                        val x = e.x / s; val y = e.y / s
                        if (y >= wh - 34f && x >= W - 96f) {
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

    private fun onBtn'''
    g, touch_count = re.subn(touch_pattern, touch_replacement, g, count=1)
    if touch_count != 1:
        raise RuntimeError("V5: touch controls anchor not found")

    g = re.sub(
        r'        weaponLevel = 1; weaponTimer = 0f; rapidTimer = 0f; shieldTimer = 0f; rocketTimer = 0f(?:; rocketTimer = 0f)*',
        '        weaponTimer = 0f; rapidTimer = 0f; shieldTimer = 0f; magnetTimer = 0f\n        laserCooldown = 0f; missileCooldown = 0f; rocketTimer = 0f',
        g, count=1
    )
    g = g.replace(
        '        bullets.clear(); enemies.clear(); parts.clear(); items.clear()',
        '        bullets.clear(); enemies.clear(); parts.clear(); items.clear(); playerBeams.clear(); missiles.clear(); enemyLasers.clear(); squadShips.clear()'
    )
    g = g.replace(
        '        shieldTimer = max(0f, shieldTimer - dt)',
        '''        shieldTimer = max(0f, shieldTimer - dt)
        magnetTimer = max(0f, magnetTimer - dt)
        laserCooldown = max(0f, laserCooldown - dt)
        missileCooldown = max(0f, missileCooldown - dt)
        for (beam in playerBeams) beam.life -= dt
        playerBeams.removeAll { it.life <= 0f }'''
    )
    g = g.replace(
        '        if (fireT <= 0f) { fireT = if (rapidTimer > 0f) 0.095f else 0.17f; shoot() }',
        '        if (fireT <= 0f) { fireT = if (rapidTimer > 0f) 0.075f else max(0.085f, 0.17f - (weaponLevel - 1) * 0.012f); shoot() }'
    )
    if 'for (missile in missiles)' not in g:
        g = g.replace(
            '        // bullets\n        for (b in bullets)',
            '''        // Homing player missiles and four-lane support squadron sweep.
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
        for (b in bullets)'''
        )
    g = g.replace(
        '''        for (item in items) {
            item.age += dt
            item.y += (24f + min(wave.toFloat(), 10f)) * dt
            item.x = (item.x + sin(item.age * 2.4f) * 8f * dt).coerceIn(8f, W - 8f)
        }''',
        '''        for (item in items) {
            item.age += dt
            if (magnetTimer > 0f) {
                item.x += (px - item.x) * min(1f, 3.0f * dt)
                item.y += (py - item.y) * min(1f, 3.0f * dt)
            } else {
                item.y += (24f + min(wave.toFloat(), 10f)) * dt
                item.x = (item.x + sin(item.age * 2.4f) * 8f * dt).coerceIn(8f, W - 8f)
            }
        }'''
    )
    collect_pattern = r'            when \(item\.type\) \{\n                0 -> \{ if \(lives < 5\) lives\+\+ else score \+= 100 \}[\s\S]*?\n            \}'
    collect_replacement = r'''            when (item.type) {
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
                else -> activateSquadron()
            }'''
    g, collect_count = re.subn(collect_pattern, collect_replacement, g, count=1)
    if collect_count != 1:
        raise RuntimeError("V5: item collection switch not found")
    drop_pattern = r'        val dropChance = if \(e\.type == 2\) 0\.90f else 0\.12f[\s\S]*?            items\.add\(Item\(e\.x, e\.y, kind\)\)\n        \}'
    drop_replacement = r'''        val dropChance = if (e.type == 2) 0.95f else 0.15f
        if (Random.nextFloat() < dropChance) {
            val roll = Random.nextFloat()
            val kind = when {
                lives <= 1 && roll < 0.10f -> 0
                roll < 0.25f -> 1
                roll < 0.39f -> 2
                roll < 0.51f -> 3
                roll < 0.61f -> 4
                roll < 0.74f -> 5
                roll < 0.83f -> 6
                roll < 0.90f -> 7
                roll < 0.96f -> 8
                else -> 9
            }
            items.add(Item(e.x, e.y, kind))
        }'''
    if "val dropChance = if (e.type == 2) 0.95f else 0.15f" not in g:
        g, drop_count = re.subn(drop_pattern, drop_replacement, g, count=1)
        if drop_count != 1:
            raise RuntimeError("V5: pickup drop table not found")
    g = g.replace(
        '''            4 -> 0xFFFF8A35.toInt()
            else -> 0xFFB68CFF.toInt()''',
        '''            4 -> 0xFFFF8A35.toInt()
            5 -> 0xFFB68CFF.toInt()
            6 -> 0xFFFF5C65.toInt()
            7 -> 0xFF64E5B0.toInt()
            8 -> 0xFF4F9EFF.toInt()
            else -> 0xFFFFD45C.toInt()'''
    )
    g = g.replace(
        '''            else -> { // guided rocket
                val rocket = Path().apply { moveTo(0f, -6f); quadTo(4f, -2f, 2.5f, 3f); lineTo(0f, 5f); lineTo(-2.5f, 3f); quadTo(-4f, -2f, 0f, -6f); close() }
                c.drawPath(rocket, p); c.drawLine(-2.5f, 1f, -5f, 4f, p); c.drawLine(2.5f, 1f, 5f, 4f, p)
                c.drawCircle(0f, -1.5f, 1.2f, p); c.drawLine(0f, 5f, 0f, 7f, p)
            }''',
        '''            5 -> { c.drawLine(-5f, 4f, -5f, -3f, p); c.drawLine(0f, 4f, 0f, -6f, p); c.drawLine(5f, 4f, 5f, -3f, p); c.drawCircle(-5f, -3f, 1.5f, p); c.drawCircle(0f, -6f, 1.5f, p); c.drawCircle(5f, -3f, 1.5f, p) }
            6 -> { c.drawLine(0f, -6f, 0f, 6f, p); c.drawLine(-4f, -3f, 4f, -3f, p); c.drawLine(-4f, 3f, 4f, 3f, p); c.drawCircle(0f, 0f, 2.2f, p) }
            7 -> { c.drawCircle(-4f, 0f, 2.3f, p); c.drawCircle(4f, 0f, 2.3f, p); c.drawLine(-2f, 0f, 2f, 0f, p) }
            8 -> { c.drawArc(RectF(-5f, -5f, 5f, 5f), 35f, 290f, false, p); c.drawLine(-5f, -5f, -5f, -1f, p); c.drawLine(-5f, -5f, -1f, -5f, p) }
            else -> { for (x in floatArrayOf(-5f, -1.7f, 1.7f, 5f)) { c.drawLine(x, 4f, x, -3f, p); c.drawLine(x, -3f, x - 1.3f, -1f, p); c.drawLine(x, -3f, x + 1.3f, -1f, p) } }'''
    )
    g = g.replace(
        '        for (b in bullets) spr(c, if (b.mine) bPB else bEB, b.x, b.y)',
        '''        for (b in bullets) spr(c, if (b.mine) bPB else bEB, b.x, b.y)
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
        paint.style = Paint.Style.FILL'''
    )
    if 'private fun drawWeaponControls' not in g:
        g = g.replace(
            '    private fun drawButtons(c: Canvas) {',
            '''    private fun drawWeaponControls(c: Canvas) {
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

    private fun drawButtons(c: Canvas) {'''
        )
        g = g.replace(
            '        if (powerLabel.isNotEmpty()) text(c, powerLabel, W / 2, wh - 10f, 7f, 0xFF92E9FF.toInt())',
            '''        if (powerLabel.isNotEmpty()) text(c, powerLabel, W / 2, wh - 10f, 7f, 0xFF92E9FF.toInt())
        if (st == St.PLAY) {
            for (i in 0 until droneLevel) {
                val dx = if (i == 0) -17f else 17f
                val wing = Path().apply { moveTo((px + dx) * s, (py - 5f) * s); lineTo((px + dx + 4f) * s, (py + 3f) * s); lineTo((px + dx) * s, (py + 1f) * s); lineTo((px + dx - 4f) * s, (py + 3f) * s); close() }
                paint.color = 0xFF63DDBB.toInt(); c.drawPath(wing, paint)
            }
            drawWeaponControls(c)
        }'''
        )
    g = g.replace(
        '        for (star in stars) { star.y += star.sp * dt; if (star.y > wh) { star.y = 0f; star.x = Random.nextFloat() * W } }',
        '        for (star in stars) { star.y += star.sp * dt; star.phase += dt * (1.2f + star.sp * 0.07f); if (star.y > wh) { star.y = 0f; star.x = Random.nextFloat() * W } }'
    )
    g = g.replace(
        '        for (star in stars) {\n            paint.color = STAR\n            c.drawRect(star.x * s, star.y * s, (star.x + star.sz) * s, (star.y + star.sz) * s, paint)\n        }',
        '        for (star in stars) {\n            paint.color = STAR\n            paint.alpha = if (star.twinkle) (75f + (sin(star.phase) + 1f) * 90f).toInt().coerceIn(45, 240) else 190\n            c.drawRect(star.x * s, star.y * s, (star.x + star.sz) * s, (star.y + star.sz) * s, paint)\n        }\n        paint.alpha = 255'
    )
    g = re.sub(r'(^        bE2 = Sprites\.enemy\(0xFF62DFFF\.toInt\(\), 0xFF9CEBFF\.toInt\(\), ps, 3\)\n){2,}', '        bE2 = Sprites.enemy(0xFF62DFFF.toInt(), 0xFF9CEBFF.toInt(), ps, 3)\n', g, flags=re.M)
    g = re.sub(r'(^        bE3 = Sprites\.enemy\(0xFFB68CFF\.toInt\(\), 0xFFD7BCFF\.toInt\(\), ps, 4\)\n){2,}', '        bE3 = Sprites.enemy(0xFFB68CFF.toInt(), 0xFFD7BCFF.toInt(), ps, 4)\n', g, flags=re.M)
    g = g.replace('            weaponTimer > 0f -> "POWER " + (weaponTimer.toInt() + 1)',
                  '            magnetTimer > 0f -> "MAGNET " + (magnetTimer.toInt() + 1)\n            weaponLevel > 1 -> "WEAPON LV " + weaponLevel')
    g = g.replace('2 -> when (e.t0.toInt() % 3) { 0 -> bBoss; 1 -> bBoss2; else -> bBoss3 }',
                  '2 -> bossSprites[e.t0.toInt().coerceIn(0, 9)]')
    g = g.replace('        enemies.firstOrNull { it.type == 2 }?.let { bs ->',
                  '        enemies.firstOrNull { it.type == 2 }?.let { bs ->\n            text(c, "BOSS " + (bs.t0.toInt() + 1) + "/10", W / 2f, 17f, 7f, WHT)')
    g += "\n// GAMEPLAY V5 - canonical weapons, permanent upgrades, boss attacks and touch controls\n"
    game_file.write_text(g, encoding="utf-8")


# Normalize Kotlin generated by earlier patch passes. Keep this outside the V5
# insertion guard so rerunning CI on an already-patched archive stays safe.
g = g.replace(chr(1) + "if (e.type == 2) {", "if (e.type == 2) {")
g = "\n".join(line for line in g.splitlines() if not line.startswith("# V5 complete") and not line.startswith("# GAMEPLAY V5")) + "\n"
# The previous V4 patch accidentally declared rocketTimer more than once.
# V5 uses a dedicated missile cooldown, so discard the obsolete rocket timer entirely.
g = re.sub(r'(?m)^\s*private var rocketTimer = 0f\s*\n', '', g)
g = re.sub(r'(?m)^\s*rocketTimer = max\(0f, rocketTimer - dt\)\s*\n', '', g)
g = g.replace('; rocketTimer = 0f', '')
setup_keys = (
    "val bossPalette =", "bossSprites = Array(10)", "bE2 = Sprites.enemy(",
    "bE3 = Sprites.enemy(", "bBoss2 = Sprites.enemy(", "bBoss3 = Sprites.enemy("
)
seen_setup = set()
seen_decl = set()
clean_lines = []
for line in g.splitlines():
    # Deduplicate fields inserted by multiple historical patch passes.
    if "private var rocketTimer =" in line:
        parts = []
        for part in line.split(";"):
            if "private var rocketTimer =" in part:
                if "rocketTimer" in seen_decl:
                    continue
                seen_decl.add("rocketTimer")
            parts.append(part.strip())
        line = "; ".join(parts)
        if not line:
            continue
    key = next((k for k in setup_keys if line.strip().startswith(k)), None)
    if key:
        if key in seen_setup:
            continue
        seen_setup.add(key)
    clean_lines.append(line)
g = "\n".join(clean_lines) + "\n"
game_file.write_text(g, encoding="utf-8")


# Flat 2D ship and enemy art; replace blur-mask glows with smooth concentric strokes.
sp = sprites_file.read_text()
sp = rep(sp, "val width = if (player) 4 else 7\n        val height = if (player) 9 else 7",
"""val width = if (player) 3 else 4
        val height = if (player) 8 else 4""", "small bullets")
sp = rep(sp, "else RectF(0.5f, 0.5f, 6.5f, 6.5f)",
"""else RectF(0.8f, 0.8f, 3.2f, 3.2f)""", "small enemy projectile")
sp = rep(sp, "if (player) 2.8f else 3.2f",
"""if (player) 1.8f else 1.2f""", "bullet glow")
sp = sp.replace("""        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawPath(path, p)""",
"""        for (i in 3 downTo 1) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                alpha = 22 + i * 13
                style = Paint.Style.STROKE
                strokeWidth = radius * i * 0.55f
                strokeJoin = Paint.Join.ROUND
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawPath(path, p)
        }""")
sp = sp.replace("""        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawOval(rect, p)""",
"""        for (i in 3 downTo 1) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color; alpha = 24 + i * 12
                style = Paint.Style.STROKE; strokeWidth = radius * i * 0.5f
            }
            canvas.drawOval(rect, p)
        }""")
# Flatten ship body: no gradients, no 3D shading, crisp solid yellow fill and white outline.
sp = sp.replace("""        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, 0f, 18f,
                intArrayOf(0xFFFFFFB5.toInt(), color, 0xFFD99A00.toInt()),
                null, Shader.TileMode.CLAMP)
        }
        c.drawPath(body, fill)
        fill.shader = null
        fill.style = Paint.Style.STROKE
        fill.strokeWidth = 0.75f
        fill.color = 0xFFFFF7C2.toInt()
        c.drawPath(body, fill)
        fill.style = Paint.Style.FILL""",
"""        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        c.drawPath(body, fill)
        fill.style = Paint.Style.STROKE
        fill.strokeWidth = 0.8f
        fill.strokeJoin = Paint.Join.ROUND
        fill.color = 0xFFFFFFB8.toInt()
        c.drawPath(body, fill)
        fill.style = Paint.Style.FILL""")
sprites_file.write_text(sp)

# V4 final pass: strip all bitmap bloom, shadows, and gradient setup.
sp = sp.replace("""        for (i in 3 downTo 1) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                alpha = 22 + i * 13
                style = Paint.Style.STROKE
                strokeWidth = radius * i * 0.55f
                strokeJoin = Paint.Join.ROUND
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawPath(path, p)
        }""", "")
sp = sp.replace("""        for (i in 3 downTo 1) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color; alpha = 24 + i * 12
                style = Paint.Style.STROKE; strokeWidth = radius * i * 0.5f
            }
            canvas.drawOval(rect, p)
        }""", "")
sp = sp.replace("val width = if (player) 3 else 4", "val width = if (player) 3 else 3")
sp = sp.replace("val height = if (player) 8 else 4", "val height = if (player) 5 else 4")
sp = sp.replace("val rect = if (player) RectF(1.2f, 0.2f, 2.8f, 8.7f)", "val rect = if (player) RectF(1.2f, 0.2f, 2.8f, 4.8f)")
if "5 -> Path().apply" not in sp:
    sp = sp.replace("val width = if (kind == 2) 21 else if (kind == 1) 14 else 12",
                    "val width = when (kind) { 2 -> 21; 5 -> 25; 6 -> 23; 1 -> 14; else -> 12 }")
    sp = sp.replace("val height = if (kind == 2) 13 else if (kind == 1) 11 else 10",
                    "val height = when (kind) { 2 -> 13; 5 -> 17; 6 -> 15; 1 -> 11; else -> 10 }")
    sp = sp.replace("""                else -> Path().apply {
                    moveTo(6f, 0.7f)""", """                5 -> Path().apply {
                    moveTo(12.5f, 0.5f); lineTo(17f, 4f); lineTo(24f, 5f)
                    lineTo(21.5f, 9f); lineTo(18f, 9.5f); lineTo(15f, 15.5f)
                    lineTo(12.5f, 12f); lineTo(10f, 15.5f); lineTo(7f, 9.5f)
                    lineTo(3.5f, 9f); lineTo(1f, 5f); lineTo(8f, 4f); close()
                }
                6 -> Path().apply {
                    moveTo(11.5f, 0.5f); lineTo(17f, 2.5f); lineTo(22f, 7f)
                    lineTo(18f, 8f); lineTo(20f, 13f); lineTo(14f, 11f)
                    lineTo(11.5f, 14.5f); lineTo(9f, 11f); lineTo(3f, 13f)
                    lineTo(5f, 8f); lineTo(1f, 7f); lineTo(6f, 2.5f); close()
                }
                else -> Path().apply {
                    moveTo(6f, 0.7f)""")
    old_ship = """        val body = Path().apply {
            moveTo(6.5f, 0.5f)
            cubicTo(8.4f, 3.0f, 9.3f, 6.2f, 9.6f, 9.7f)
            lineTo(12.6f, 13.1f)
            quadTo(13.0f, 14.0f, 11.7f, 13.7f)
            lineTo(8.5f, 12.0f)
            lineTo(7.5f, 16.7f)
            quadTo(6.5f, 18.0f, 5.5f, 16.7f)
            lineTo(4.5f, 12.0f)
            lineTo(1.3f, 13.7f)
            quadTo(0.0f, 14.0f, 0.4f, 13.1f)
            lineTo(3.4f, 9.7f)
            cubicTo(3.7f, 6.2f, 4.6f, 3.0f, 6.5f, 0.5f)
            close()
        }"""
    new_ship = """        val body = Path().apply {
            moveTo(6.5f, 0.3f)
            lineTo(8.2f, 5.8f); lineTo(12.8f, 10.7f)
            lineTo(12.3f, 13.2f); lineTo(8.2f, 11.8f)
            lineTo(7.6f, 16.4f); lineTo(6.5f, 15.6f)
            lineTo(5.4f, 16.4f); lineTo(4.8f, 11.8f)
            lineTo(0.7f, 13.2f); lineTo(0.2f, 10.7f)
            lineTo(4.8f, 5.8f); close()
        }"""
    sp = sp.replace(old_ship, new_ship)
sp = sp.replace("""        val fp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                intArrayOf(0xFFFFF4A8.toInt(), 0xFFFF9A32.toInt(), 0x00FF4D00),
                null, Shader.TileMode.CLAMP)
        }""", """        val fp = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xFFFF9A32.toInt() }""")
sp = sp.replace("""        val glass = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                intArrayOf(0xFFFFFFFF.toInt(), 0xFFB9F5FF.toInt(), 0xFF4C99C9.toInt()),
                null, Shader.TileMode.CLAMP)
        }""", """        val glass = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xFFB9F5FF.toInt() }""")
sp = sp.replace("""            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    intArrayOf(0xFFFFD0D2.toInt(), color, 0xFF9C183D.toInt()),
                    null, Shader.TileMode.CLAMP)
            }""", """            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }""")
sp = sp.replace("""            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    intArrayOf(0xFFFFFFFF.toInt(), color, 0xFFFFFF82.toInt()),
                    null, Shader.TileMode.CLAMP)
                    intArrayOf(0xFFFFF0C8.toInt(), color, 0xFFFF7630.toInt()),
                    null, Shader.TileMode.CLAMP)
            }""", """            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }""")

# V5: add ten distinctive flat-colour boss silhouettes.
if "7 -> Path().apply" not in sp:
    sp = sp.replace(
        'val width = when (kind) { 2 -> 21; 5 -> 25; 6 -> 23; 1 -> 14; else -> 12 }',
        'val width = when (kind) { 2 -> 21; 5 -> 25; 6 -> 23; 7 -> 25; 8 -> 23; 9 -> 26; 10 -> 24; 11 -> 25; 12 -> 27; 13 -> 22; 14 -> 26; 1 -> 14; else -> 12 }'
    )
    sp = sp.replace(
        'val height = when (kind) { 2 -> 13; 5 -> 17; 6 -> 15; 1 -> 11; else -> 10 }',
        'val height = when (kind) { 2 -> 13; 5 -> 17; 6 -> 15; 7 -> 17; 8 -> 16; 9 -> 17; 10 -> 18; 11 -> 16; 12 -> 17; 13 -> 16; 14 -> 18; 1 -> 11; else -> 10 }'
    )
    sp = sp.replace(
        '                else -> Path().apply {',
        '''                7 -> Path().apply {
                    moveTo(12.5f, 0.5f); lineTo(16f, 5f); lineTo(24f, 2f); lineTo(20f, 8f); lineTo(23f, 15f); lineTo(14f, 12f)
                    lineTo(12.5f, 17f); lineTo(11f, 12f); lineTo(2f, 15f); lineTo(5f, 8f); lineTo(1f, 2f); lineTo(9f, 5f); close()
                }
                8 -> Path().apply {
                    moveTo(11.5f, 0.5f); lineTo(22f, 8f); lineTo(11.5f, 15.5f); lineTo(1f, 8f); close()
                    moveTo(11.5f, 3.5f); lineTo(16f, 8f); lineTo(11.5f, 12.5f); lineTo(7f, 8f); close()
                }
                9 -> Path().apply {
                    moveTo(5f, 1f); lineTo(21f, 1f); lineTo(25f, 5f); lineTo(21f, 9f); lineTo(18f, 8f)
                    lineTo(18f, 16f); lineTo(8f, 16f); lineTo(8f, 8f); lineTo(5f, 9f); lineTo(1f, 5f); close()
                }
                10 -> Path().apply {
                    moveTo(12f, 0.5f); lineTo(15f, 6f); lineTo(23f, 3f); lineTo(19f, 9f); lineTo(24f, 16f)
                    lineTo(15f, 13f); lineTo(12f, 18f); lineTo(9f, 13f); lineTo(0f, 16f); lineTo(5f, 9f); lineTo(1f, 3f); lineTo(9f, 6f); close()
                }
                11 -> Path().apply {
                    moveTo(12.5f, 1f); lineTo(18f, 5f); lineTo(24f, 3f); lineTo(21f, 10f); lineTo(24f, 15f); lineTo(16f, 13f)
                    lineTo(12.5f, 16f); lineTo(9f, 13f); lineTo(1f, 15f); lineTo(4f, 10f); lineTo(1f, 3f); lineTo(7f, 5f); close()
                }
                12 -> Path().apply {
                    moveTo(12.5f, 0.5f); lineTo(16f, 5f); lineTo(24f, 5f); lineTo(19f, 9f); lineTo(21f, 16f)
                    lineTo(13f, 13f); lineTo(6f, 17f); lineTo(7f, 10f); lineTo(1f, 6f); lineTo(9f, 5f); close()
                }
                13 -> Path().apply {
                    moveTo(7f, 0.5f); lineTo(18f, 0.5f); lineTo(22f, 5f); lineTo(22f, 12f); lineTo(16f, 16f)
                    lineTo(8f, 16f); lineTo(2f, 12f); lineTo(2f, 5f); close()
                }
                14 -> Path().apply {
                    moveTo(13f, 0.5f); lineTo(18f, 4f); lineTo(25f, 3f); lineTo(22f, 9f); lineTo(25f, 15f)
                    lineTo(17f, 14f); lineTo(13f, 18f); lineTo(9f, 14f); lineTo(1f, 15f); lineTo(4f, 9f); lineTo(1f, 3f); lineTo(8f, 4f); close()
                }
                else -> Path().apply {'''
    )
    sp = sp.replace('val eyeY = if (kind == 2) 6.6f else 5.2f', 'val eyeY = if (kind >= 5) 7.2f else if (kind == 2) 6.6f else 5.2f')
    sp = sp.replace('val eyeOffset = if (kind == 2) 5.0f else 2.6f', 'val eyeOffset = if (kind >= 5) 7.0f else if (kind == 2) 5.0f else 2.6f')
    sp = sp.replace('if (kind == 2) 8.2f else 7.1f', 'if (kind >= 5) 9.0f else if (kind == 2) 8.2f else 7.1f')
    sp = sp.replace('if (kind == 2) 1.0f else 0.7f', 'if (kind >= 5) 1.5f else if (kind == 2) 1.0f else 0.7f')
    sp = sp.replace('glow(c, body, glowColor, if (kind == 2) 5.2f else 3.6f)', 'glow(c, body, glowColor, if (kind >= 5) 5.8f else if (kind == 2) 5.2f else 3.6f')


sprite_lines = []
for line in sp.splitlines():
    if line.strip().startswith("glow(c, body, glowColor, if (kind >= 5)"):
        line = "            glow(c, body, glowColor, if (kind >= 5) 5.8f else if (kind == 2) 5.2f else 3.6f)"
    sprite_lines.append(line)
sp = "\n".join(sprite_lines) + "\n"

sprites_file.write_text(sp)
# Temporary CI source snapshot to pinpoint the remaining Kotlin syntax error.
Path("SPRITES_V5_DUMP.kt").write_text(sp, encoding="utf-8")

# Google Play Games sign-in: initialize status on launch and expose failures instead of swallowing them.
play = play_file.read_text()
if "import android.widget.Toast" not in play:
    play = play.replace("import com.google.android.gms.games.PlayGames", "import com.google.android.gms.games.PlayGames\nimport android.widget.Toast")
play_lines = []
toast_seen = False
for line in play.splitlines():
    if line == "import android.widget.Toast":
        if toast_seen:
            continue
        toast_seen = True
    play_lines.append(line)
play = "\n".join(play_lines) + "\n"
play = play.replace("""                signedIn = t.isSuccessful && t.result.isAuthenticated
            }
        } catch (e: Throwable) {}
    }

    fun signIn(a: Activity) {
        try {
            PlayGames.getGamesSignInClient(a).signIn().addOnCompleteListener { t ->
                signedIn = t.isSuccessful && t.result.isAuthenticated
            }
        } catch (e: Throwable) {}
    }""",
"""                signedIn = t.isSuccessful && t.result.isAuthenticated
            }
        } catch (e: Throwable) { signedIn = false }
    }

    fun signIn(a: Activity) {
        try {
            PlayGames.getGamesSignInClient(a).signIn().addOnCompleteListener { t ->
                signedIn = t.isSuccessful && t.result.isAuthenticated
                val message = if (signedIn) "Google Play Games connected" else "Google Play Games sign-in failed. Check Play Console setup."
                Toast.makeText(a, message, Toast.LENGTH_LONG).show()
            }
        } catch (e: Throwable) {
            signedIn = false
            Toast.makeText(a, "Sign-in error: " + (e.message ?: "unknown"), Toast.LENGTH_LONG).show()
        }
    }""")
play_file.write_text(play)

main = main_file.read_text()
if "Play.check(this)" not in main:
    main = main.replace("        setContentView(GameView(this))", "        setContentView(GameView(this))\n        Play.check(this)")
main_file.write_text(main)

# Remove temporary source dumps created while diagnosing the previous patch.
for dump_name in ("GAMEVIEW_V4_DUMP.kt", "SPRITES_V4_DUMP.kt"):
    dump_path = root.parent / dump_name
    if dump_path.exists():
        dump_path.unlink()


# GAMEPLAY V6 - pause overlay, readable health bars, gravitational wells and reliable pickups.
g = game_file.read_text(encoding="utf-8")

def v6rep(old, new, label):
    global g
    if new in g:
        return
    if old not in g:
        raise RuntimeError("V6 patch target missing: " + label)
    g = g.replace(old, new, 1)

v6rep(
    "private enum class St { MENU, PLAY, OVER }",
    "private enum class St { MENU, PLAY, PAUSE, OVER }",
    "pause state"
)
v6rep(
    "private class B(var x: Float, var y: Float, val vx: Float, val vy: Float, val mine: Boolean)",
    "private class B(var x: Float, var y: Float, var vx: Float, var vy: Float, val mine: Boolean)",
    "mutable projectile velocity"
)
v6rep(
    """    private class Star(var x: Float, val y0: Float, val sp: Float, val sz: Float) {
        var y = y0
        var phase = Random.nextFloat() * 6.283f
        val twinkle = Random.nextFloat() < 0.42f
    }""",
    """    private class Star(var x: Float, val y0: Float, val sp: Float, val sz: Float) {
        var y = y0
        var phase = Random.nextFloat() * 6.283f
        val twinkle = Random.nextFloat() < 0.72f
    }
    private class BlackHole(var x: Float, var y: Float, val radius: Float, val strength: Float, val drift: Float) {
        var age = 0f
        var spin = Random.nextFloat() * 6.283f
    }""",
    "black hole class"
)
v6rep(
    "    private val squadShips = ArrayList<SweepShip>()",
    "    private val squadShips = ArrayList<SweepShip>()\n    private val blackHoles = ArrayList<BlackHole>()",
    "black hole collection"
)
v6rep(
    """                        if (y >= wh - 34f && x >= W - 96f) {
                            if (x >= W - 52f) fireLaser() else launchMissiles()
                            controlTouch = true
                        }""",
    """                        if (x >= W - 32f && y <= 30f) {
                            st = St.PAUSE
                            controlTouch = true
                        } else if (y >= wh - 34f && x >= W - 96f) {
                            if (x >= W - 52f) fireLaser() else launchMissiles()
                            controlTouch = true
                        }""",
    "pause touch target"
)
v6rep(
    """            "menu" -> st = St.MENU
            "lb" -> Play.showLeaderboard(act)""",
    """            "menu" -> st = St.MENU
            "resume" -> st = St.PLAY
            "pauseRestart" -> startGame()
            "lb" -> Play.showLeaderboard(act)""",
    "pause actions"
)
v6rep(
    """        fun add(id: String, label: String, y: Float) { l.add(Btn(id, label, RectF(30f, y, 150f, y + 22f))) }""",
    """        fun add(id: String, label: String, y: Float) { l.add(Btn(id, label, RectF(50f, y, 170f, y + 22f))) }""",
    "center menu buttons"
)
v6rep(
    """            St.OVER -> { val y = wh * 0.58f; add("play", "RETRY", y); add("menu", "MENU", y + 30f) }
            else -> {}""",
    """            St.PAUSE -> {
                val y = wh * 0.42f
                add("resume", "RESUME", y)
                add("pauseRestart", "RESTART", y + 30f)
                add("menu", "MAIN MENU", y + 60f)
            }
            St.OVER -> { val y = wh * 0.58f; add("play", "RETRY", y); add("menu", "MENU", y + 30f) }
            else -> {}""",
    "pause menu buttons"
)
v6rep(
    """        bullets.clear(); enemies.clear(); parts.clear(); items.clear(); playerBeams.clear(); missiles.clear(); enemyLasers.clear(); squadShips.clear()""",
    """        bullets.clear(); enemies.clear(); parts.clear(); items.clear(); playerBeams.clear(); missiles.clear(); enemyLasers.clear(); squadShips.clear(); blackHoles.clear()""",
    "reset gravity wells"
)
v6rep(
    """    private fun spawnWave() {
        wave++
        bannerT = 2.0f""",
    """    private fun spawnWave() {
        wave++
        if (wave >= 3 && wave % 3 == 0 && blackHoles.size < 2) {
            val side = if (Random.nextBoolean()) 1f else -1f
            blackHoles.add(BlackHole(Random.nextFloat() * (W - 76f) + 38f, -18f, 48f + Random.nextFloat() * 12f, 72000f, side * (5f + Random.nextFloat() * 8f)))
        }
        bannerT = 2.0f""",
    "wave black hole spawn"
)
v6rep(
    """        val dropChance = if (e.type == 2) 0.95f else 0.15f""",
    """        val dropChance = if (e.type == 2) 0.95f else 0.24f""",
    "pickup drop rate"
)
v6rep(
    """                lives <= 1 && roll < 0.10f -> 0""",
    """                lives < 5 && roll < 0.28f -> 0""",
    "extra life drop"
)
v6rep(
    """        // Homing player missiles and four-lane support squadron sweep.""",
    """        // Gravitational wells bend both player and enemy projectiles as they pass nearby.
        for (hole in blackHoles) {
            hole.age += dt
            hole.spin += dt * 1.8f
            hole.y += 8f * dt
            hole.x = (hole.x + sin(hole.age * 0.8f) * hole.drift * dt).coerceIn(18f, W - 18f)
        }
        blackHoles.removeAll { it.age > 11f || it.y > wh + 55f }
        
        // Homing player missiles and four-lane support squadron sweep.""",
    "black hole movement"
)
v6rep(
    """        for (b in bullets) { b.x += b.vx * dt; b.y += b.vy * dt }""",
    """        for (b in bullets) {
            for (hole in blackHoles) {
                val dx = hole.x - b.x
                val dy = hole.y - b.y
                val d2 = dx * dx + dy * dy
                if (d2 < hole.radius * hole.radius && d2 > 9f) {
                    val distance = sqrt(d2)
                    val pull = min(95f, hole.strength / (d2 + 100f)) * dt
                    b.vx = (b.vx + dx / distance * pull).coerceIn(-220f, 220f)
                    b.vy = (b.vy + dy / distance * pull).coerceIn(-220f, 220f)
                }
            }
            b.x += b.vx * dt
            b.y += b.vy * dt
        }""",
    "projectile gravity"
)
v6rep(
    """        for (e in enemies) {
            val sprite = when (e.type) { 2 -> bossSprites[e.t0.toInt().coerceIn(0, 9)]; 1 -> bE1; 3 -> bE2; 4 -> bE3; else -> bE0 }
            spr(c, sprite, e.x, e.y)
        }""",
    """        for (hole in blackHoles) drawBlackHole(c, hole)
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
        }""",
    "enemy hp bars"
)
v6rep(
    """        for (i in 0 until lives) spr(c, bMini, W - 12f - i * 12f, 10f)
        val powerLabel""",
    """        for (i in 0 until lives) spr(c, bMini, W - 12f - i * 12f, 10f)
        text(c, "HP", 7f, 27f, 6f, 0xFF9CEBFF.toInt(), Paint.Align.LEFT)
        paint.style = Paint.Style.FILL
        paint.color = 0xCC25313D.toInt()
        c.drawRoundRect(18f * s, 21f * s, 72f * s, 25f * s, 1.8f * s, 1.8f * s, paint)
        paint.color = if (lives >= 3) 0xFF55DDAA.toInt() else if (lives == 2) 0xFFFFC857.toInt() else 0xFFFF5968.toInt()
        c.drawRoundRect(18f * s, 21f * s, (18f + 54f * lives / 5f) * s, 25f * s, 1.8f * s, 1.8f * s, paint)
        val powerLabel""",
    "player hp bar"
)
v6rep(
    """            drawWeaponControls(c)
        }
        enemies.firstOrNull""",
    """            drawWeaponControls(c)
            drawPauseControl(c)
        }
        enemies.firstOrNull""",
    "pause button render"
)
v6rep(
    """        if (st == St.PLAY && (invul <= 0f || (time * 12).toInt() % 2 == 0)) spr(c, bPlayer, px, py)""",
    """        if ((st == St.PLAY || st == St.PAUSE) && (invul <= 0f || (time * 12).toInt() % 2 == 0)) spr(c, bPlayer, px, py)""",
    "show ship during pause"
)
v6rep(
    """            St.PLAY -> drawGame(c)
            St.OVER -> { drawGame(c); drawOver(c) }""",
    """            St.PLAY -> drawGame(c)
            St.PAUSE -> { drawGame(c); drawPause(c) }
            St.OVER -> { drawGame(c); drawOver(c) }""",
    "pause render state"
)
v6rep(
    """    private fun drawWeaponControls(c: Canvas) {""",
    """    private fun drawBlackHole(c: Canvas, hole: BlackHole) {
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

    private fun drawWeaponControls(c: Canvas) {""",
    "black hole and pause draw functions"
)
v6rep(
    """        val sprite = when""",
    """        val sprite = when""",
    "sprite marker"
)
v6rep(
    """            St.MENU -> drawMenu(c)
            St.PLAY -> drawGame(c)""",
    """            St.MENU -> drawMenu(c)
            St.PLAY -> drawGame(c)""",
    "render state guard"
)
v6rep(
    """        for (star in stars) {
            paint.color = STAR
            paint.alpha = if (star.twinkle) (75f + (sin(star.phase) + 1f) * 90f).toInt().coerceIn(45, 240) else 190
            c.drawRect(star.x * s, star.y * s, (star.x + star.sz) * s, (star.y + star.sz) * s, paint)
        }""",
    """        for (star in stars) {
            val pulse = (sin(star.phase) + 1f) * 0.5f
            paint.color = if (star.twinkle) 0xFFBDEFFF.toInt() else STAR
            paint.alpha = if (star.twinkle) (28f + pulse * 225f).toInt().coerceIn(20, 255) else 185
            val size = star.sz * if (star.twinkle) (0.65f + pulse * 0.75f) else 1f
            c.drawRect(star.x * s, star.y * s, (star.x + size) * s, (star.y + size) * s, paint)
        }""",
    "visible star twinkle"
)
v6rep(
    """if (stars.isEmpty()) repeat(60)""",
    """if (stars.isEmpty()) repeat(90)""",
    "more background stars"
)
v6rep(
    """star.phase += dt * (1.2f + star.sp * 0.07f)""",
    """star.phase += dt * (2.4f + star.sp * 0.16f)""",
    "faster star shimmer"
)
v6rep(
    """    private fun drawMenu(c: Canvas) {""",
    """    private fun drawMenu(c: Canvas) {""",
    "menu function guard"
)

# Keep stars conspicuous on all displays and center the menu title/button group.
# Rebuild once with the pause and gravity features applied.
game_file.write_text(g, encoding="utf-8")

# Increment app version so Android accepts the new package as a newer release.
gradle_file = root / "app/build.gradle.kts"
gradle_text = gradle_file.read_text(encoding="utf-8")
gradle_text = re.sub(r"versionCode\s*=\s*\d+", "versionCode = 2", gradle_text)
gradle_text = re.sub(r'versionName\s*=\s*"[^"]+"', 'versionName = "1.1"', gradle_text)
if 'create("release")' not in gradle_text:
    gradle_text = gradle_text.replace(
        "    compileOptions {",
        """    signingConfigs {
        create("release") {
            val keyPath = System.getenv("NEON_KEYSTORE_PATH")
            if (!keyPath.isNullOrBlank()) {
                storeFile = file(keyPath)
                storePassword = System.getenv("NEON_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("NEON_KEY_ALIAS")
                keyPassword = System.getenv("NEON_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
        }
    }
    compileOptions {"""
    )
gradle_file.write_text(gradle_text, encoding="utf-8")


# Update archive, commit the source archive, then let CI build the APK.
tmp = archive.with_suffix(".tmp.zip")
with zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
    for f in sorted(root.rglob("*")):
        if f.is_file(): z.write(f, Path(root.name) / f.relative_to(root))
tmp.replace(archive)
subprocess.run(["git","config","user.name","github-actions[bot]"],check=True)
subprocess.run(["git","config","user.email","41898282+github-actions[bot]@users.noreply.github.com"],check=True)
subprocess.run(["git","add","-A"],check=True)
if subprocess.run(["git","diff","--cached","--quiet"]).returncode:
    subprocess.run(["git","commit","-m","feat(game): refine 2D art, powerups and sign-in feedback"],check=True)
    subprocess.run(["git","push","origin","HEAD:main"],check=True)
print("Gameplay v3 source updated.")
