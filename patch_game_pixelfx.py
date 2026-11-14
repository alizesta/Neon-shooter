#!/usr/bin/env python3
"""Neon Shooter 1.6 VFX pass, adapting selected MIT Pixelfx visual recipes to Android Canvas."""
from pathlib import Path

root = Path("NeonShooter")
game_file = root / "app/src/main/java/com/claude/neonshooter/GameView.kt"
g = game_file.read_text(encoding="utf-8")

def rep(old, new, label):
    global g
    if new in g:
        return
    if old not in g:
        raise RuntimeError("Pixelfx integration anchor missing: " + label)
    g = g.replace(old, new, 1)

# Force-field hit feedback: each intercepted shot produces a visible ripple on the shield.
rep("    private var shieldMaxHp = 0\n",
    "    private var shieldMaxHp = 0\n    private var shieldHitTimer = 0f\n    private var shieldHitAngle = 0f\n",
    "shield impact state")
rep("        for (ring in impactRings) ring.life -= dt\n",
    "        for (ring in impactRings) ring.life -= dt\n        shieldHitTimer = max(0f, shieldHitTimer - dt)\n",
    "shield impact timer")
rep('''                shieldHp = (shieldHp - 1).coerceAtLeast(0); reflectiveShield = shieldHp > 0
                sfx.play("power")''',
'''                shieldHp = (shieldHp - 1).coerceAtLeast(0); reflectiveShield = shieldHp > 0
                shieldHitTimer = 0.24f
                shieldHitAngle = atan2(b.y - py, b.x - px)
                sfx.play("power")''', "shield projectile impact pulse")

# Reusable native Canvas VFX inspired by Pixelfx force-field, plasma explosion and black-hole.
helpers = r"""
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
"""
if "private fun drawPixelfxShield(" not in g:
    anchor = "    private fun drawBlackHole(c: Canvas, hole: BlackHole) {"
    if anchor not in g:
        raise RuntimeError("Pixelfx integration anchor missing: VFX helper insertion")
    g = g.replace(anchor, helpers + "\n" + anchor, 1)

# Replace the simple impact ring with the plasma burst treatment.
rep('''        for (ring in impactRings) { val t = 1f - ring.life / ring.maxLife; paint.style = Paint.Style.STROKE; paint.strokeWidth = (1.8f - t) * s; paint.color = Color.argb((220f * (1f - t)).toInt().coerceIn(0, 220), 185, 235, 255); c.drawCircle(ring.x * s, ring.y * s, (2.5f + t * 13f) * s, paint); paint.style = Paint.Style.FILL }''',
'''        for (ring in impactRings) drawPixelfxImpact(c, ring)''', "plasma impact rendering")

# Replace the plain shield circle with a larger, animated hex-glass force field.
rep('''        if (shieldTimer > 0f || shieldHp > 0) {
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
        }''',
'''        if (shieldTimer > 0f || shieldHp > 0) {
            drawPixelfxShield(c)
            paint.clearShadowLayer(); paint.style = Paint.Style.FILL
        }''', "force field rendering")

# Pixelfx homing-missile recipe: layered exhaust, heat core and trailing sparks.
rep('''        for (missile in missiles) {
            c.save()
            c.rotate(Math.toDegrees(atan2(missile.vy, missile.vx).toDouble()).toFloat() + 90f, missile.x * s, missile.y * s)''',
'''        for (missile in missiles) {
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
            c.rotate(Math.toDegrees(heading.toDouble()).toFloat() + 90f, missile.x * s, missile.y * s)''', "missile plasma exhaust")

# Black-hole accretion disk: multiple moving arcs and infalling light motes.
rep('''        paint.color = 0xFFB59BFF.toInt()
        c.drawCircle(cx, cy, r * 0.54f, paint)
        paint.style = Paint.Style.FILL
    }''',
'''        paint.color = 0xFFB59BFF.toInt()
        c.drawCircle(cx, cy, r * 0.54f, paint)
        paint.style = Paint.Style.STROKE
        for (i in 0..3) {
            val orbit = r * (0.72f + i * 0.095f)
            val start = (time * (72f + i * 28f) + i * 83f) % 360f
            paint.color = when (i) { 0 -> 0xFFD8C5FF.toInt(); 1 -> 0xFF8C63FF.toInt(); 2 -> 0xFF35D9F2.toInt(); else -> 0x667C6CFF }
            paint.strokeWidth = (2.2f - i * 0.32f) * s
            c.drawArc(cx - orbit, cy - orbit * 0.48f, cx + orbit, cy + orbit * 0.48f, start, 66f + i * 13f, false, paint)
        }
        paint.style = Paint.Style.FILL
        for (i in 0 until 10) {
            val a = time * (1.15f + i * 0.025f) + i * 0.628f
            val d = r * (0.95f - ((time * 0.28f + i * 0.11f) % 0.52f))
            val fx = cx + cos(a) * d
            val fy = cy + sin(a) * d * 0.52f
            paint.color = if (i % 3 == 0) 0xFFE7D9FF.toInt() else 0xFF8F77FF.toInt()
            c.drawCircle(fx, fy, (1.0f + (i % 2) * 0.45f) * s, paint)
        }
    }''', "black hole accretion VFX")

game_file.write_text(g, encoding="utf-8")

# Keep the upstream MIT attribution with the distributed Android app.
assets = root / "app/src/main/assets"
assets.mkdir(parents=True, exist_ok=True)
notice = assets / "THIRD_PARTY_NOTICES.txt"
notice.write_text("""Neon Shooter includes visual-effect adaptations inspired by pixelfx.
Repository: https://github.com/patch-gg/pixelfx
Copyright (c) 2026 Patch
Copyright (c) 2026 Hugo Duprez
License: MIT

The MIT license permits use, modification, distribution and commercial use.
The visual routines used in Neon Shooter are adapted to Android Canvas and are
not a bundled copy of the JavaScript runtime.

MIT License

Copyright (c) 2026 Patch
Copyright (c) 2026 Hugo Duprez

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
""", encoding="utf-8")

# Refresh the Android source archive so the APK and downloadable source match.
import zipfile
archive = next(Path(".").glob("NeonShooter_*.zip"))
tmp = archive.with_suffix(".pixelfx.tmp.zip")
with zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
    for f in sorted(root.rglob("*")):
        if f.is_file():
            z.write(f, Path(root.name) / f.relative_to(root))
tmp.replace(archive)
print("Pixelfx-inspired force field, plasma impacts, missile exhaust and black-hole VFX applied.")
