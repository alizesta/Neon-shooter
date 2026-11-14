package com.claude.neonshooter

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

    }

    private fun glowOval(canvas: Canvas, rect: RectF, color: Int, radius: Float) {

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
        val fp = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xFFFF9A32.toInt() }
        c.drawPath(flame, fp)

        val body = Path().apply {
            moveTo(6.5f, 0.3f)
            lineTo(8.2f, 5.8f); lineTo(12.8f, 10.7f)
            lineTo(12.3f, 13.2f); lineTo(8.2f, 11.8f)
            lineTo(7.6f, 16.4f); lineTo(6.5f, 15.6f)
            lineTo(5.4f, 16.4f); lineTo(4.8f, 11.8f)
            lineTo(0.7f, 13.2f); lineTo(0.2f, 10.7f)
            lineTo(4.8f, 5.8f); close()
        }
        glow(c, body, glowColor, 4.2f)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        c.drawPath(body, fill)
        fill.style = Paint.Style.STROKE
        fill.strokeWidth = 0.8f
        fill.strokeJoin = Paint.Join.ROUND
        fill.color = 0xFFFFFFB8.toInt()
        c.drawPath(body, fill)
        fill.style = Paint.Style.FILL

        val cockpit = RectF(5.0f, 4.2f, 8.0f, 10.3f)
        glowOval(c, cockpit, 0xFFFFFFA6.toInt(), 2.0f)
        val glass = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xFFB9F5FF.toInt() }
        c.drawOval(cockpit, glass)
        val detail = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = 0xFFFFF7D1.toInt()
            strokeWidth = 0.55f
            style = Paint.Style.STROKE
        }
        c.drawLine(6.5f, 11f, 6.5f, 14.5f, detail)
        c.drawLine(2.5f, 12.2f, 4.2f, 11.3f, detail)
        c.drawLine(10.5f, 11.3f, 12.2f, 12.2f, detail)
    }

    fun enemy(color: Int, glowColor: Int, ps: Int, kind: Int): Bitmap {
        val width = when (kind) { 2 -> 21; 5 -> 25; 6 -> 23; 1 -> 14; else -> 12 }
        val height = when (kind) { 2 -> 13; 5 -> 17; 6 -> 15; 1 -> 11; else -> 10 }
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
                5 -> Path().apply {
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
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
            c.drawPath(body, fill)
            fill.shader = null
            fill.style = Paint.Style.STROKE
            fill.strokeWidth = 0.65f
            fill.color = 0xFFFFE0E0.toInt()
            c.drawPath(body, fill)
            fill.style = Paint.Style.FILL

            val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xFFFFF5D8.toInt() }
            val eyeY = if (kind == 2) 6.6f else 5.2f
            val eyeOffset = if (kind == 2) 5.0f else 2.6f
            c.drawCircle(width / 2f - eyeOffset, eyeY, 0.9f, eyePaint)
            c.drawCircle(width / 2f + eyeOffset, eyeY, 0.9f, eyePaint)
            val core = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xFFFFF1C4.toInt() }
            c.drawCircle(width / 2f, if (kind == 2) 8.2f else 7.1f, if (kind == 2) 1.0f else 0.7f, core)
        }
    }

    fun bullet(color: Int, glowColor: Int, ps: Int, player: Boolean): Bitmap {
        val width = if (player) 3 else 3
        val height = if (player) 5 else 4
        return bitmap(width, height, ps) { c ->
            val rect = if (player) RectF(1.2f, 0.2f, 2.8f, 4.8f)
                else RectF(0.8f, 0.8f, 3.2f, 3.2f)
            glowOval(c, rect, glowColor, if (player) 1.8f else 1.2f)
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
            c.drawOval(rect, fill)
        }
    }
}
