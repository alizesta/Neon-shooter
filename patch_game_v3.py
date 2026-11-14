#!/usr/bin/env python3
from pathlib import Path
import subprocess, zipfile

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
game_file.write_text(g)
(root.parent / "GAMEVIEW_V4_DUMP.kt").write_text(g, encoding="utf-8")

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
sprites_file.write_text(sp)

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

# Update archive, commit the source archive, then let CI build the APK.
tmp = archive.with_suffix(".tmp.zip")
with zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
    for f in sorted(root.rglob("*")):
        if f.is_file(): z.write(f, Path(root.name) / f.relative_to(root))
tmp.replace(archive)
subprocess.run(["git","config","user.name","github-actions[bot]"],check=True)
subprocess.run(["git","config","user.email","41898282+github-actions[bot]@users.noreply.github.com"],check=True)
subprocess.run(["git","add",archive.name, "GAMEVIEW_V4_DUMP.kt"],check=True)
if subprocess.run(["git","diff","--cached","--quiet"]).returncode:
    subprocess.run(["git","commit","-m","feat(game): refine 2D art, powerups and sign-in feedback"],check=True)
    subprocess.run(["git","push","origin","HEAD:main"],check=True)
print("Gameplay v3 source updated.")
