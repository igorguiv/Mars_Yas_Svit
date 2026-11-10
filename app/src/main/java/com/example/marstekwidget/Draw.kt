package com.example.marstekwidget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface

object Draw {
    fun socBar(w: Int, h: Int, soc: Double, t: WTheme): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = t.track
        c.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), h / 2f, h / 2f, p)
        val fw = (w * soc.coerceIn(0.0, 100.0) / 100.0).toFloat()
        if (fw > 0f) {
            p.color = when {
                soc < 20 -> t.red
                soc < 40 -> t.amber
                else -> t.green
            }
            c.drawRoundRect(RectF(0f, 0f, maxOf(fw, h.toFloat()), h.toFloat()), h / 2f, h / 2f, p)
        }
        return bmp
    }


    fun ring(size: Int, frac: Double?, color: Int, track: Int, big: String, small: String?, t: WTheme): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val stroke = size * 0.085f
        val inset = stroke / 2 + 2f
        val rect = RectF(inset, inset, size - inset, size - inset)
        p.style = Paint.Style.STROKE
        p.strokeWidth = stroke
        p.strokeCap = Paint.Cap.ROUND
        p.color = track
        c.drawArc(rect, 0f, 360f, false, p)
        p.color = color
        if (frac == null) c.drawArc(rect, 0f, 360f, false, p)
        else if (frac > 0.0) c.drawArc(rect, -90f, (360.0 * frac.coerceIn(0.0, 1.0)).toFloat(), false, p)
        p.style = Paint.Style.FILL
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.DEFAULT_BOLD
        p.color = t.t1
        p.textSize = size * 0.24f
        val cx = size / 2f
        if (small != null) {
            c.drawText(big, cx, size * 0.50f, p)
            p.typeface = Typeface.DEFAULT
            p.textSize = size * 0.105f
            p.color = t.t2
            c.drawText(small, cx, size * 0.65f, p)
        } else {
            c.drawText(big, cx, size * 0.585f, p)
        }
        return bmp
    }

    fun timeline(w: Int, intervals: List<Iv>, nowMin: Int, t: WTheme): Bitmap {
        val h = 70
        val barH = 30f
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val shape = Path().apply { addRoundRect(RectF(0f, 0f, w.toFloat(), barH), barH / 2, barH / 2, Path.Direction.CW) }
        c.save()
        c.clipPath(shape)
        p.color = t.green
        c.drawRect(0f, 0f, w.toFloat(), barH, p)
        p.color = t.red
        for (iv in intervals) {
            val s = iv.s.coerceIn(0, 1440); val e = iv.e.coerceIn(0, 1440)
            if (e > s) c.drawRect(w * s / 1440f, 0f, w * e / 1440f, barH, p)
        }
        c.restore()
        // маркер "зараз"
        val x = w * nowMin.coerceIn(0, 1440) / 1440f
        p.color = Color.WHITE
        c.drawCircle(x, barH / 2, 17f, p)
        p.color = t.t1
        c.drawCircle(x, barH / 2, 12f, p)
        // підписи годин
        p.color = t.t3
        p.textSize = 24f
        val labels = listOf("00", "06", "12", "18", "24")
        for ((i, l) in labels.withIndex()) {
            val lx = w * i / 4f
            p.textAlign = when (i) { 0 -> Paint.Align.LEFT; 4 -> Paint.Align.RIGHT; else -> Paint.Align.CENTER }
            c.drawText(l, lx, h - 8f, p)
        }
        return bmp
    }


    /** Фактична смуга доби: зелене = світло було, червоне = не було, сіре = ще не настало. */
    fun factTimeline(w: Int, intervals: List<Iv>, nowMin: Int, t: WTheme): Bitmap {
        val h = 70
        val barH = 30f
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val shape = Path().apply { addRoundRect(RectF(0f, 0f, w.toFloat(), barH), barH / 2, barH / 2, Path.Direction.CW) }
        val nowX = w * nowMin.coerceIn(0, 1440) / 1440f
        c.save()
        c.clipPath(shape)
        p.color = t.track
        c.drawRect(0f, 0f, w.toFloat(), barH, p)
        p.color = t.green
        c.drawRect(0f, 0f, nowX, barH, p)
        p.color = t.red
        for (iv in intervals) {
            val s = iv.s.coerceIn(0, nowMin.coerceIn(0, 1440)); val e = iv.e.coerceIn(0, nowMin.coerceIn(0, 1440))
            if (e > s) c.drawRect(w * s / 1440f, 0f, w * e / 1440f, barH, p)
        }
        c.restore()
        p.color = Color.WHITE
        c.drawCircle(nowX, barH / 2, 17f, p)
        p.color = t.t1
        c.drawCircle(nowX, barH / 2, 12f, p)
        p.color = t.t3
        p.textSize = 24f
        val labels = listOf("00", "06", "12", "18", "24")
        for ((i, l) in labels.withIndex()) {
            val lx = w * i / 4f
            p.textAlign = when (i) { 0 -> Paint.Align.LEFT; 4 -> Paint.Align.RIGHT; else -> Paint.Align.CENTER }
            c.drawText(l, lx, h - 8f, p)
        }
        return bmp
    }

    fun chart(
        w: Int, h: Int, hist: List<Pair<Long, Double>>, bands: List<Iv>,
        midnightMs: Long, nowMs: Long, t: WTheme
    ): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val left = 70f; val right = w - 6f; val top = 10f; val bottom = h - 34f
        val plotW = right - left; val plotH = bottom - top
        val winMs = 24 * 3600_000L
        val start = nowMs - winMs

        fun xOf(ms: Long) = left + (ms - start).toFloat() / winMs * plotW
        fun yOf(v: Double) = top + (1f - (v.coerceIn(0.0, 100.0) / 100f).toFloat()) * plotH

        // смуги відключень
        p.color = t.red; p.alpha = 45
        for (iv in bands) {
            val s = maxOf(midnightMs + iv.s * 60_000L, start)
            val e = minOf(midnightMs + iv.e * 60_000L, nowMs + winMs / 4)
            if (e > s) c.drawRect(xOf(s), top, minOf(xOf(e), right), bottom, p)
        }
        p.alpha = 255

        // сітка
        p.strokeWidth = 2f
        p.textSize = 22f
        for (v in listOf(0, 50, 100)) {
            p.color = t.track
            c.drawLine(left, yOf(v.toDouble()), right, yOf(v.toDouble()), p)
            p.color = t.t3
            p.textAlign = Paint.Align.RIGHT
            c.drawText("$v%", left - 8f, yOf(v.toDouble()) + 8f, p)
        }
        p.textAlign = Paint.Align.LEFT; c.drawText("−24 год", left, h - 6f, p)
        p.textAlign = Paint.Align.CENTER; c.drawText("−12", left + plotW / 2, h - 6f, p)
        p.textAlign = Paint.Align.RIGHT; c.drawText("зараз", right, h - 6f, p)

        val pts = hist.filter { it.first >= start }
        if (pts.size < 2) {
            p.color = t.t2; p.textAlign = Paint.Align.CENTER; p.textSize = 26f
            c.drawText("дані накопичуються…", left + plotW / 2, top + plotH / 2, p)
            return bmp
        }
        val line = Path()
        val fill = Path()
        pts.forEachIndexed { i, pt ->
            val x = xOf(pt.first); val y = yOf(pt.second)
            if (i == 0) { line.moveTo(x, y); fill.moveTo(x, bottom); fill.lineTo(x, y) }
            else { line.lineTo(x, y); fill.lineTo(x, y) }
        }
        val lastX = xOf(pts.last().first)
        fill.lineTo(lastX, bottom); fill.close()
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, top, 0f, bottom, (t.line and 0x00FFFFFF) or 0x66000000, (t.line and 0x00FFFFFF) or 0x08000000, Shader.TileMode.CLAMP)
        c.drawPath(fill, p)
        p.shader = null
        p.style = Paint.Style.STROKE; p.strokeWidth = 5f; p.color = t.line
        p.strokeJoin = Paint.Join.ROUND
        c.drawPath(line, p)
        return bmp
    }
}
