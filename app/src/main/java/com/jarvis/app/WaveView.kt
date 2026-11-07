package com.jarvis.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

class WaveView(context: Context) : View(context) {
    private val bars = FloatArray(24)
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(150, 0, 0, 0) }
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.CYAN }

    fun push(level: Float) {
        System.arraycopy(bars, 1, bars, 0, bars.size - 1)
        bars[bars.size - 1] = level
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        c.drawRoundRect(RectF(0f, 0f, w, h), h / 2, h / 2, bg)
        val step = (w - h) / bars.size
        for (i in bars.indices) {
            val bh = 6f + bars[i] * (h - 20f)
            val x = h / 2 + i * step
            c.drawRoundRect(RectF(x, (h - bh) / 2, x + step * 0.6f, (h + bh) / 2), 4f, 4f, bar)
        }
    }
}
