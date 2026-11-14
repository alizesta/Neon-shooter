#!/usr/bin/env python3
from pathlib import Path
import subprocess
import sys
import zipfile

root = Path("NeonShooter")
archive = next(Path(".").glob("NeonShooter_*.zip"))
marker = root / ".gameplay-update-v1"

if marker.exists():
    # The gameplay patch is already in the ZIP. Apply the small follow-up
    # Kotlin fix without re-running the original source transformations.
    sprites_file = root / "app/src/main/java/com/claude/neonshooter/Sprites.kt"
    sprites = sprites_file.read_text(encoding="utf-8")
    fixes = [
        ("            color = 0xFFFFF7D1.toInt()", "            this.color = 0xFFFFF7D1.toInt()"),
        ("apply { color = 0xFFFFF5D8.toInt() }", "apply { this.color = 0xFFFFF5D8.toInt() }"),
        ("apply { color = 0xFFFFF1C4.toInt() }", "apply { this.color = 0xFFFFF1C4.toInt() }"),
    ]
    changed = False
    for before, after in fixes:
        if before in sprites:
            sprites = sprites.replace(before, after, 1)
            changed = True
    if changed:
        sprites_file.write_text(sprites, encoding="utf-8")
        tmp = archive.with_suffix(".tmp.zip")
        with zipfile.ZipFile(tmp, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as zf:
            for path in sorted(root.rglob("*")):
                if path.is_file():
                    zf.write(path, Path(root.name) / path.relative_to(root))
        tmp.replace(archive)
        subprocess.run(["git", "config", "user.name", "github-actions[bot]"], check=True)
        subprocess.run(["git", "config", "user.email", "41898282+github-actions[bot]@users.noreply.github.com"], check=True)
        subprocess.run(["git", "add", archive.name], check=True)
        if subprocess.run(["git", "diff", "--cached", "--quiet"]).returncode != 0:
            subprocess.run(["git", "commit", "-m", "fix(game): correct vector sprite paint colors"], check=True)
            subprocess.run(["git", "push", "origin", "HEAD:main"], check=True)
            print("Committed the vector sprite color fix.")
    else:
        print("Vector sprite color fix was already present.")
    sys.exit(0)

game_file = root / "app/src/main/java/com/claude/neonshooter/GameView.kt"
sfx_file = root / "app/src/main/java/com/claude/neonshooter/Sfx.kt"
sprites_file = root / "app/src/main/java/com/claude/neonshooter/Sprites.kt"

game = game_file.read_text(encoding="utf-8")

def rep(old, new, label):
    global game
    if old not in game:
        raise SystemExit("Patch anchor not found: " + label)
    game = game.replace(old, new, 1)

rep(
    "import android.view.SurfaceView\n",
    "import android.view.SurfaceView\nimport android.util.Log\n",
    "Log import"
)
rep(
    "    private class Pt(var x: Float, var y: Float, val vx: Float, val vy: Float, var life: Float, val max: Float)\n",
    "    private class Pt(var x: Float, var y: Float, val vx: Float, val vy: Float, var life: Float, val max: Float)\n"
    "    private class Item(var x: Float, var y: Float, val type: Int) { var age = 0f }\n",
    "pickup class"
)
rep(
    "    private val BG = 0xFF2A2A2E.toInt(); private val STAR = 0xFF4A4A52.toInt()\n"
    "    private val YEL = 0xFFFFD400.toInt(); private val YGL = 0xFFFFEB66.toInt()\n"
    "    private val RED = 0xFFFF3B3B.toInt(); private val RGL = 0xFFFF8080.toInt()\n"
    "    private val DRED = 0xFFC4001D.toInt(); private val ORG = 0xFFFF8C00.toInt()\n"
    "    private val WHT = 0xFFFFFFFF.toInt(); private val WGL = 0xFFE0E0FF.toInt()\n",
    "    private val BG = 0xFF111318.toInt(); private val STAR = 0xFF454D59.toInt()\n"
    "    private val YEL = 0xFFFFEA00.toInt(); private val YGL = 0xFFFFFF75.toInt()\n"
    "    private val RED = 0xFFFF6268.toInt(); private val RGL = 0xFFFFB4B8.toInt()\n"
    "    private val DRED = 0xFFFFA044.toInt(); private val ORG = 0xFFFFB547.toInt()\n"
    "    private val BOSS_COLOR = 0xFFFF4D91.toInt(); private val BOSS_GLOW = 0xFFFFA4CF.toInt()\n"
    "    private val WHT = 0xFFFFFFFF.toInt(); private val WGL = 0xFFFFFFD6.toInt()\n",
    "visual palette"
)
rep(
    "    private val bullets = ArrayList<B>(); private val enemies = ArrayList<E>()\n"
    "    private val parts = ArrayList<Pt>(); private val stars = ArrayList<Star>()\n",
    "    private val bullets = ArrayList<B>(); private val enemies = ArrayList<E>()\n"
    "    private val parts = ArrayList<Pt>(); private val stars = ArrayList<Star>()\n"
    "    private val items = ArrayList<Item>()\n",
    "pickup collection"
)
rep(
    "    private var invul = 0f; private var fireT = 0f; private var time = 0f\n",
    "    private var invul = 0f; private var fireT = 0f; private var time = 0f\n"
    "    private var weaponLevel = 1; private var weaponTimer = 0f\n"
    "    private var rapidTimer = 0f; private var shieldTimer = 0f\n",
    "powerup state"
)
rep(
    "    private val paint = Paint()\n",
    "    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)\n"
    "    private val spritePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)\n",
    "antialiased sprite paint"
)
rep(
    "        bPlayer = Sprites.build(Sprites.PLAYER, YEL, YGL, ps)\n"
    "        bMini = Sprites.build(Sprites.PLAYER, YEL, YGL, max(1, ps / 2))\n"
    "        bE0 = Sprites.build(Sprites.ENEMY, RED, RGL, ps)\n"
    "        bE1 = Sprites.build(Sprites.ENEMY2, DRED, RED, ps)\n"
    "        bBoss = Sprites.build(Sprites.BOSS, DRED, RED, ps)\n"
    "        bPB = Sprites.build(Sprites.PBULLET, WHT, WGL, ps)\n"
    "        bEB = Sprites.build(Sprites.EBULLET, ORG, ORG, ps)\n",
    "        bPlayer = Sprites.player(YEL, YGL, ps)\n"
    "        bMini = Sprites.player(YEL, YGL, max(1, ps / 2))\n"
    "        bE0 = Sprites.enemy(RED, RGL, ps, 0)\n"
    "        bE1 = Sprites.enemy(DRED, 0xFFFFD18A.toInt(), ps, 1)\n"
    "        bBoss = Sprites.enemy(BOSS_COLOR, BOSS_GLOW, ps, 2)\n"
    "        bPB = Sprites.bullet(WHT, WGL, ps, true)\n"
    "        bEB = Sprites.bullet(ORG, 0xFFFFD28C.toInt(), ps, false)\n",
    "vector sprite setup"
)
rep(
    "    override fun run() {\n"
    "        var last = System.nanoTime()\n"
    "        while (running) {\n"
    "            val now = System.nanoTime()\n"
    "            val dt = min(0.033f, (now - last) / 1e9f); last = now\n"
    "            synchronized(lock) { update(dt) }\n"
    "            var c: Canvas? = null\n"
    "            try {\n"
    "                c = holder.lockCanvas()\n"
    "                if (c != null) synchronized(lock) { draw(c) }\n"
    "            } catch (e: Exception) {\n"
    "            } finally {\n"
    "                if (c != null) try { holder.unlockCanvasAndPost(c) } catch (e: Exception) {}\n"
    "            }\n"
    "            try { Thread.sleep(6) } catch (e: InterruptedException) {}\n"
    "        }\n"
    "    }\n",
    "    override fun run() {\n"
    "        var last = System.nanoTime()\n"
    "        while (running) {\n"
    "            val now = System.nanoTime()\n"
    "            val dt = min(0.033f, (now - last) / 1e9f); last = now\n"
    "            try { synchronized(lock) { update(dt) } }\n"
    "            catch (e: Exception) {\n"
    "                Log.e(\"NeonShooter\", \"Game update recovered from an error\", e)\n"
    "                synchronized(lock) { invul = max(invul, 1.0f); bullets.clear() }\n"
    "            }\n"
    "            var c: Canvas? = null\n"
    "            try {\n"
    "                c = holder.lockCanvas()\n"
    "                if (c != null) synchronized(lock) { renderFrame(c) }\n"
    "            } catch (e: Exception) {\n"
    "                Log.e(\"NeonShooter\", \"Frame render skipped\", e)\n"
    "            } finally {\n"
    "                if (c != null) try { holder.unlockCanvasAndPost(c) } catch (e: Exception) {}\n"
    "            }\n"
    "            try { Thread.sleep(8) } catch (e: InterruptedException) {}\n"
    "        }\n"
    "    }\n",
    "recoverable game loop"
)
rep(
    "        bullets.clear(); enemies.clear(); parts.clear()\n"
    "        score = 0; lives = 3; wave = 0; kills = 0; invul = 1.5f; fireT = 0f; time = 0f\n"
    "        px = W / 2; py = wh - 45f; waiting = true; waveT = 0.6f\n",
    "        bullets.clear(); enemies.clear(); parts.clear(); items.clear()\n"
    "        score = 0; lives = 3; wave = 0; kills = 0; invul = 2.2f; fireT = 0f; time = 0f\n"
    "        weaponLevel = 1; weaponTimer = 0f; rapidTimer = 0f; shieldTimer = 0f\n"
    "        px = W / 2; py = wh - 45f; waiting = true; waveT = 1.4f\n",
    "new run reset"
)
rep(
    "        bannerT = 1.6f\n"
    "        if (wave >= 10) ach(R.string.achievement_wave_10)\n"
    "        if (wave % 5 == 0) {\n"
    "            enemies.add(E(W / 2, -20f, 2, 30 + wave * 6, W / 2, 0f, 0f).also { it.fire = 1.5f })\n",
    "        bannerT = 2.0f\n"
    "        if (wave >= 10) ach(R.string.achievement_wave_10)\n"
    "        if (wave % 5 == 0) {\n"
    "            enemies.add(E(W / 2, -20f, 2, 42 + wave * 7, W / 2, 0f, 0f).also { it.fire = 1.7f })\n",
    "longer boss wave"
)
rep(
    "        val n = min(5 + wave / 2, 10)\n"
    "        fun mk(x: Float, y: Float, amp: Float, t0: Float) {\n"
    "            val strong = wave >= 3 && Random.nextFloat() < 0.3f\n"
    "            enemies.add(E(x, y, if (strong) 1 else 0, if (strong) 3 else 1, x, amp, t0))\n",
    "        val n = min(8 + wave / 2, 15)\n"
    "        fun mk(x: Float, y: Float, amp: Float, t0: Float) {\n"
    "            val strong = wave >= 3 && Random.nextFloat() < 0.38f\n"
    "            val hp = if (strong) 3 + wave / 10 else if (wave >= 7) 2 else 1\n"
    "            enemies.add(E(x, y, if (strong) 1 else 0, hp, x, amp, t0))\n",
    "longer regular waves"
)
rep(
    "        if (e.type == 2) { ach(R.string.achievement_boss_slayer); bullets.removeAll { !it.mine } }\n",
    "        if (e.type == 2) { ach(R.string.achievement_boss_slayer); bullets.removeAll { !it.mine } }\n"
    "        val dropChance = if (e.type == 2) 0.95f else 0.22f\n"
    "        if (Random.nextFloat() < dropChance) {\n"
    "            val roll = Random.nextFloat()\n"
    "            val kind = when {\n"
    "                lives <= 1 && roll < 0.45f -> 0\n"
    "                roll < 0.25f -> 0\n"
    "                roll < 0.55f -> 1\n"
    "                roll < 0.78f -> 2\n"
    "                else -> 3\n"
    "            }\n"
    "            items.add(Item(e.x, e.y, kind))\n"
    "        }\n",
    "random powerup drops"
)
rep(
    "    private fun hurt() {\n"
    "        lives--\n"
    "        invul = 1.8f\n"
    "        boom(px, py, 24, 70f)\n"
    "        sfx.play(\"hurt\")\n"
    "        if (lives <= 0) {\n"
    "            st = St.OVER; overT = 0f\n"
    "            sfx.play(\"over\")\n"
    "            if (score > best) { best = score; prefs.edit().putInt(\"best\", best).apply() }\n"
    "            Play.submit(act, score)\n"
    "        }\n"
    "    }\n",
    "    private fun hurt() {\n"
    "        if (invul > 0f) return\n"
    "        if (shieldTimer > 0f) {\n"
    "            shieldTimer = 0f; invul = 1.2f\n"
    "            boom(px, py, 18, 58f); sfx.play(\"boom\")\n"
    "            return\n"
    "        }\n"
    "        lives = (lives - 1).coerceAtLeast(0)\n"
    "        invul = 2.4f\n"
    "        boom(px, py, 24, 70f)\n"
    "        sfx.play(\"hurt\")\n"
    "        if (lives == 0) {\n"
    "            st = St.OVER; overT = 0f\n"
    "            sfx.play(\"over\")\n"
    "            if (score > best) { best = score; prefs.edit().putInt(\"best\", best).apply() }\n"
    "            act.runOnUiThread { try { Play.submit(act, score) } catch (e: Exception) { Log.e(\"NeonShooter\", \"Score submit skipped\", e) } }\n"
    "        }\n"
    "    }\n",
    "safe hit and game over"
)
rep(
    "        val lvl = min(1 + wave / 4, 3)\n",
    "        val lvl = max(min(1 + wave / 4, 3), if (weaponTimer > 0f) weaponLevel else 1)\n",
    "weapon level"
)
rep(
    "        time += dt; invul -= dt; fireT -= dt; bannerT -= dt\n"
    "        if (fireT <= 0f) { fireT = 0.17f; shoot() }\n",
    "        time += dt; invul -= dt; fireT -= dt; bannerT -= dt\n"
    "        weaponTimer = max(0f, weaponTimer - dt)\n"
    "        rapidTimer = max(0f, rapidTimer - dt)\n"
    "        shieldTimer = max(0f, shieldTimer - dt)\n"
    "        if (fireT <= 0f) { fireT = if (rapidTimer > 0f) 0.095f else 0.17f; shoot() }\n",
    "powerup timers and rapid fire"
)
rep(
    "            if (!waiting) { waiting = true; waveT = 1.0f }\n",
    "            if (!waiting) { waiting = true; waveT = 1.6f }\n",
    "longer gap between waves"
)
rep(
    "        val spd = min(22f + wave * 1.5f, 50f)\n",
    "        val spd = min(18f + wave * 1.15f, 38f)\n",
    "enemy pace"
)
rep(
    "            if (e.type == 2) {\n"
    "                if (e.y < 32f) e.y += 30f * dt\n"
    "                e.x = W / 2 + sin(e.age * 0.9f) * (W / 2 - e.hw - 6f)\n"
    "            } else {\n"
    "                e.y += spd * dt\n"
    "                e.x = (e.bx + sin(e.age * 2.2f + e.t0) * e.amp).coerceIn(8f, W - 8f)\n"
    "            }\n",
    "            if (e.type == 2) {\n"
    "                if (e.y < 42f) e.y += 26f * dt\n"
    "                e.x = W / 2 + sin(e.age * 0.82f) * (W / 2 - e.hw - 8f)\n"
    "            } else {\n"
    "                e.y += spd * dt\n"
    "                if (e.type == 1) {\n"
    "                    val targetX = (px + sin(e.age * 1.25f + e.t0) * 13f).coerceIn(12f, W - 12f)\n"
    "                    val shift = 28f * dt\n"
    "                    e.x += (targetX - e.x).coerceIn(-shift, shift)\n"
    "                } else {\n"
    "                    val sway = max(e.amp, 10f + min(wave.toFloat(), 10f))\n"
    "                    e.x = (e.bx + sin(e.age * 1.7f + e.t0) * sway).coerceIn(10f, W - 10f)\n"
    "                }\n"
    "            }\n",
    "smarter enemy movement"
)
rep(
    "                            val dx = px - e.x; val dy = py - e.y; val d = max(1f, hypot(dx, dy))\n"
    "                            bullets.add(B(e.x, e.y + 8f, dx / d * 75f, dy / d * 75f, false))\n"
    "                        } else bullets.add(B(e.x, e.y + 8f, 0f, 70f, false))\n",
    "                            val leadX = px + sin(time * 1.3f) * 8f\n"
    "                            val dx = leadX - e.x; val dy = py - e.y; val d = max(1f, hypot(dx, dy))\n"
    "                            bullets.add(B(e.x, e.y + 8f, dx / d * 82f, dy / d * 82f, false))\n"
    "                        } else {\n"
    "                            val drift = ((px - e.x) * 0.12f).coerceIn(-16f, 16f)\n"
    "                            bullets.add(B(e.x, e.y + 8f, drift, 66f, false))\n"
    "                        }\n",
    "smarter enemy shooting"
)
rep(
    "        bullets.removeAll { it.y < -10f || it.y > wh + 10f || it.x < -10f || it.x > W + 10f }\n"
    "\n"
    "        // player bullets vs enemies\n",
    "        bullets.removeAll { it.y < -10f || it.y > wh + 10f || it.x < -10f || it.x > W + 10f }\n"
    "\n"
    "        // floating pickups\n"
    "        for (item in items) {\n"
    "            item.age += dt\n"
    "            item.y += (24f + min(wave.toFloat(), 10f)) * dt\n"
    "            item.x = (item.x + sin(item.age * 2.4f) * 8f * dt).coerceIn(8f, W - 8f)\n"
    "        }\n"
    "        val collected = ArrayList<Item>()\n"
    "        for (item in items) if (hypot(item.x - px, item.y - py) < 15f) {\n"
    "            when (item.type) {\n"
    "                0 -> { if (lives < 5) lives++ else score += 100 }\n"
    "                1 -> { weaponLevel = min(3, weaponLevel + 1); weaponTimer = 15f }\n"
    "                2 -> rapidTimer = 10f\n"
    "                else -> shieldTimer = 10f\n"
    "            }\n"
    "            collected.add(item); boom(item.x, item.y, 10, 42f); sfx.play(\"power\")\n"
    "        }\n"
    "        items.removeAll(collected)\n"
    "        items.removeAll { it.y > wh + 10f }\n"
    "\n"
    "        // player bullets vs enemies\n",
    "pickup movement and collection"
)
rep(
    "            for (b in bullets) if (!b.mine && abs(b.x - px) < 5f && abs(b.y - py) < 5f) { deadB.add(b); hit = true; break }\n"
    "            if (!hit) for (e in enemies) if (abs(e.x - px) < e.hw + 4f && abs(e.y - py) < e.hh + 4f) {\n",
    "            for (b in bullets) if (!b.mine && abs(b.x - px) < 6f && abs(b.y - py) < 6f) { deadB.add(b); hit = true; break }\n"
    "            if (!hit) for (e in enemies) if (abs(e.x - px) < e.hw + 2f && abs(e.y - py) < e.hh + 2f) {\n",
    "fairer collision bounds"
)
rep(
    "    private fun spr(c: Canvas, b: Bitmap, x: Float, y: Float) {\n"
    "        c.drawBitmap(b, x * s - b.width / 2f, y * s - b.height / 2f, null)\n"
    "    }\n",
    "    private fun spr(c: Canvas, b: Bitmap, x: Float, y: Float) {\n"
    "        c.drawBitmap(b, x * s - b.width / 2f, y * s - b.height / 2f, spritePaint)\n"
    "    }\n"
    "\n"
    "    private fun drawItem(c: Canvas, item: Item) {\n"
    "        val data = when (item.type) {\n"
    "            0 -> 0xFF78FFAB.toInt() to \"+\"\n"
    "            1 -> 0xFF62DFFF.toInt() to \"III\"\n"
    "            2 -> 0xFFFFE45C.toInt() to \"R\"\n"
    "            else -> 0xFFFF79D8.toInt() to \"S\"\n"
    "        }\n"
    "        val pulse = 1f + sin(item.age * 5f) * 0.12f\n"
    "        val radius = 5.4f * pulse * s\n"
    "        paint.style = Paint.Style.FILL; paint.color = data.first\n"
    "        paint.setShadowLayer(9f * s, 0f, 0f, data.first)\n"
    "        c.drawCircle(item.x * s, item.y * s, radius, paint)\n"
    "        paint.clearShadowLayer(); paint.style = Paint.Style.STROKE\n"
    "        paint.strokeWidth = 1.2f * s; paint.color = WHT\n"
    "        c.drawCircle(item.x * s, item.y * s, radius, paint)\n"
    "        paint.style = Paint.Style.FILL\n"
    "        tp.textSize = 7f * s; tp.color = 0xFF17191F.toInt(); tp.textAlign = Paint.Align.CENTER\n"
    "        tp.clearShadowLayer()\n"
    "        c.drawText(data.second, item.x * s, item.y * s + 2.4f * s, tp)\n"
    "    }\n",
    "draw powerups"
)
rep(
    "    private fun draw(c: Canvas) {\n",
    "    private fun renderFrame(c: Canvas) {\n",
    "SurfaceView-safe render method"
)
rep(
    "        for (e in enemies) spr(c, if (e.type == 2) bBoss else if (e.type == 1) bE1 else bE0, e.x, e.y)\n"
    "        for (b in bullets) spr(c, if (b.mine) bPB else bEB, b.x, b.y)\n"
    "        if (st == St.PLAY && (invul <= 0f || (time * 12).toInt() % 2 == 0)) spr(c, bPlayer, px, py)\n",
    "        for (e in enemies) spr(c, if (e.type == 2) bBoss else if (e.type == 1) bE1 else bE0, e.x, e.y)\n"
    "        for (item in items) drawItem(c, item)\n"
    "        for (b in bullets) spr(c, if (b.mine) bPB else bEB, b.x, b.y)\n"
    "        if (shieldTimer > 0f) {\n"
    "            paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.4f * s; paint.color = 0xFFFF79D8.toInt()\n"
    "            paint.setShadowLayer(9f * s, 0f, 0f, 0xFFFF79D8.toInt())\n"
    "            c.drawCircle(px * s, py * s, 16f * s, paint)\n"
    "            paint.clearShadowLayer(); paint.style = Paint.Style.FILL\n"
    "        }\n"
    "        if (st == St.PLAY && (invul <= 0f || (time * 12).toInt() % 2 == 0)) spr(c, bPlayer, px, py)\n",
    "render pickups and shield"
)
rep(
    "        for (i in 0 until lives) spr(c, bMini, W - 12f - i * 12f, 10f)\n",
    "        for (i in 0 until lives) spr(c, bMini, W - 12f - i * 12f, 10f)\n"
    "        val powerLabel = when {\n"
    "            shieldTimer > 0f -> \"SHIELD \" + (shieldTimer.toInt() + 1)\n"
    "            rapidTimer > 0f -> \"RAPID \" + (rapidTimer.toInt() + 1)\n"
    "            weaponTimer > 0f -> \"POWER \" + (weaponTimer.toInt() + 1)\n"
    "            else -> \"\"\n"
    "        }\n"
    "        if (powerLabel.isNotEmpty()) text(c, powerLabel, W / 2, wh - 10f, 7f, 0xFF92E9FF.toInt())\n",
    "powerup HUD"
)

game_file.write_text(game, encoding="utf-8")

sprites = r'''package com.claude.neonshooter

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader

/** Smooth, anti-aliased vector sprites with layered neon bloom. */
object Sprites {
    private fun bitmap(width: Int, height: Int, ps: Int, draw: (Canvas) -> Unit): Bitmap {
        val w = (width * ps).coerceAtLeast(1)
        val h = (height * ps).coerceAtLeast(1)
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.scale(ps.toFloat(), ps.toFloat())
        draw(canvas)
        return result
    }

    private fun glow(canvas: Canvas, path: Path, color: Int, radius: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawPath(path, p)
    }

    private fun glowOval(canvas: Canvas, rect: RectF, color: Int, radius: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawOval(rect, p)
    }

    fun player(color: Int, glowColor: Int, ps: Int): Bitmap = bitmap(13, 19, ps) { c ->
        val flame = Path().apply {
            moveTo(4.7f, 13.2f)
            cubicTo(3.7f, 15.2f, 4.5f, 17.1f, 5.7f, 18.2f)
            lineTo(6.5f, 15.2f)
            lineTo(7.4f, 18.2f)
            cubicTo(8.5f, 17.0f, 9.2f, 15.2f, 8.3f, 13.2f)
            close()
        }
        glow(c, flame, 0xFFFF8C32.toInt(), 3.2f)
        val fp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 13f, 0f, 19f,
                intArrayOf(0xFFFFF4A8.toInt(), 0xFFFF9A32.toInt(), 0x00FF4D00),
                null, Shader.TileMode.CLAMP)
        }
        c.drawPath(flame, fp)

        val body = Path().apply {
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
        }
        glow(c, body, glowColor, 4.2f)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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
        fill.style = Paint.Style.FILL

        val cockpit = RectF(5.0f, 4.2f, 8.0f, 10.3f)
        glowOval(c, cockpit, 0xFFFFFFA6.toInt(), 2.0f)
        val glass = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(5f, 4f, 8f, 10f,
                intArrayOf(0xFFFFFFFF.toInt(), 0xFFB9F5FF.toInt(), 0xFF4C99C9.toInt()),
                null, Shader.TileMode.CLAMP)
        }
        c.drawOval(cockpit, glass)
        val detail = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFF7D1.toInt()
            strokeWidth = 0.55f
            style = Paint.Style.STROKE
        }
        c.drawLine(6.5f, 11f, 6.5f, 14.5f, detail)
        c.drawLine(2.5f, 12.2f, 4.2f, 11.3f, detail)
        c.drawLine(10.5f, 11.3f, 12.2f, 12.2f, detail)
    }

    fun enemy(color: Int, glowColor: Int, ps: Int, kind: Int): Bitmap {
        val width = if (kind == 2) 21 else if (kind == 1) 14 else 12
        val height = if (kind == 2) 13 else if (kind == 1) 11 else 10
        return bitmap(width, height, ps) { c ->
            val body = when (kind) {
                1 -> Path().apply {
                    moveTo(7f, 0.5f)
                    cubicTo(10f, 1f, 12f, 3.2f, 12.4f, 6.0f)
                    lineTo(13.8f, 9.4f)
                    quadTo(14.1f, 10.4f, 12.8f, 10.0f)
                    lineTo(9.7f, 8.3f)
                    lineTo(7f, 10.7f)
                    lineTo(4.3f, 8.3f)
                    lineTo(1.2f, 10.0f)
                    quadTo(-0.1f, 10.4f, 0.2f, 9.4f)
                    lineTo(1.6f, 6.0f)
                    cubicTo(2f, 3.2f, 4f, 1f, 7f, 0.5f)
                    close()
                }
                2 -> Path().apply {
                    moveTo(10.5f, 0.7f)
                    cubicTo(15.7f, 0.5f, 19.3f, 3.2f, 20.3f, 7.2f)
                    lineTo(19.4f, 11.2f)
                    quadTo(19.2f, 12.4f, 18.1f, 11.6f)
                    lineTo(14.7f, 9.9f)
                    cubicTo(12.7f, 12.5f, 8.3f, 12.5f, 6.3f, 9.9f)
                    lineTo(2.9f, 11.6f)
                    quadTo(1.8f, 12.4f, 1.6f, 11.2f)
                    lineTo(0.7f, 7.2f)
                    cubicTo(1.7f, 3.2f, 5.3f, 0.5f, 10.5f, 0.7f)
                    close()
                }
                else -> Path().apply {
                    moveTo(6f, 0.7f)
                    cubicTo(9.0f, 0.4f, 10.8f, 2.4f, 10.9f, 4.8f)
                    lineTo(11.7f, 8.5f)
                    quadTo(11.9f, 9.6f, 10.8f, 9.0f)
                    lineTo(9.1f, 7.4f)
                    quadTo(6f, 10.7f, 2.9f, 7.4f)
                    lineTo(1.2f, 9.0f)
                    quadTo(0.1f, 9.6f, 0.3f, 8.5f)
                    lineTo(1.1f, 4.8f)
                    cubicTo(1.2f, 2.4f, 3.0f, 0.4f, 6f, 0.7f)
                    close()
                }
            }
            glow(c, body, glowColor, if (kind == 2) 5.2f else 3.6f)
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, 0f, 0f, height.toFloat(),
                    intArrayOf(0xFFFFD0D2.toInt(), color, 0xFF9C183D.toInt()),
                    null, Shader.TileMode.CLAMP)
            }
            c.drawPath(body, fill)
            fill.shader = null
            fill.style = Paint.Style.STROKE
            fill.strokeWidth = 0.65f
            fill.color = 0xFFFFE0E0.toInt()
            c.drawPath(body, fill)
            fill.style = Paint.Style.FILL

            val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFF5D8.toInt() }
            val eyeY = if (kind == 2) 6.6f else 5.2f
            val eyeOffset = if (kind == 2) 5.0f else 2.6f
            c.drawCircle(width / 2f - eyeOffset, eyeY, 0.9f, eyePaint)
            c.drawCircle(width / 2f + eyeOffset, eyeY, 0.9f, eyePaint)
            val core = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFF1C4.toInt() }
            c.drawCircle(width / 2f, if (kind == 2) 8.2f else 7.1f, if (kind == 2) 1.0f else 0.7f, core)
        }
    }

    fun bullet(color: Int, glowColor: Int, ps: Int, player: Boolean): Bitmap {
        val width = if (player) 4 else 7
        val height = if (player) 9 else 7
        return bitmap(width, height, ps) { c ->
            val rect = if (player) RectF(1.2f, 0.2f, 2.8f, 8.7f)
                else RectF(0.5f, 0.5f, 6.5f, 6.5f)
            glowOval(c, rect, glowColor, if (player) 2.8f else 3.2f)
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = if (player) LinearGradient(0f, 0f, 4f, 0f,
                    intArrayOf(0xFFFFFFFF.toInt(), color, 0xFFFFFF82.toInt()),
                    null, Shader.TileMode.CLAMP)
                else LinearGradient(0f, 0f, 0f, 7f,
                    intArrayOf(0xFFFFF0C8.toInt(), color, 0xFFFF7630.toInt()),
                    null, Shader.TileMode.CLAMP)
            }
            c.drawOval(rect, fill)
        }
    }
}
'''
sprites_file.write_text(sprites, encoding="utf-8")

sfx = sfx_file.read_text(encoding="utf-8")
if 'add(ctx, "power"' not in sfx:
    anchor = '        add(ctx, "hurt", gen(320.0, 60.0, 300, 0.4))\n'
    if anchor not in sfx:
        raise SystemExit("Patch anchor not found: power pickup sound")
    sfx = sfx.replace(anchor, anchor + '        add(ctx, "power", gen(560.0, 1450.0, 110, 0.32))\n', 1)
sfx = sfx.replace(
    '        ids[name]?.let { pool.play(it, 1f, 1f, 1, 0, 1f) }\n',
    '        try { ids[name]?.let { pool.play(it, 1f, 1f, 1, 0, 1f) } } catch (e: Exception) {}\n',
    1
)
sfx_file.write_text(sfx, encoding="utf-8")

marker.write_text("Smooth vector sprites, neon glow, improved AI, longer waves, powerups and hit recovery.\n", encoding="utf-8")

tmp = archive.with_suffix(".tmp.zip")
with zipfile.ZipFile(tmp, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as zf:
    for path in sorted(root.rglob("*")):
        if path.is_file():
            zf.write(path, Path(root.name) / path.relative_to(root))

tmp.replace(archive)

subprocess.run(["git", "config", "user.name", "github-actions[bot]"], check=True)
subprocess.run(["git", "config", "user.email", "41898282+github-actions[bot]@users.noreply.github.com"], check=True)
subprocess.run(["git", "add", archive.name], check=True)
changed = subprocess.run(["git", "diff", "--cached", "--quiet"]).returncode != 0
if changed:
    subprocess.run(["git", "commit", "-m", "feat(game): polish visuals and add gameplay powerups"], check=True)
    subprocess.run(["git", "push", "origin", "HEAD:main"], check=True)
    print("Updated source ZIP committed to main.")
else:
    print("Source ZIP was already up to date.")

print("Applied game update. Building APK from the updated source.")
