// PATH: app/src/main/java/com/jarvis/app/Overlay.kt
package com.jarvis.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager

class Overlay(private val ctx: Context) {
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var view: View? = null

    fun show() = main.post {
        if (view != null || !Settings.canDrawOverlays(ctx)) return@post
        val lp = WindowManager.LayoutParams(320, 320, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; y = 120 }
        view = WaveView(ctx).also { wm.addView(it, lp) }
    }

    fun hide() = main.post { view?.let { wm.removeView(it) }; view = null }
}

private class WaveView(ctx: Context) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 6f; color = 0xFF4FC3F7.toInt()
    }
    private var t = 0f
    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1400; repeatCount = ValueAnimator.INFINITE
        addUpdateListener { t = it.animatedValue as Float; invalidate() }
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); anim.start() }
    override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }
    override fun onDraw(c: Canvas) {
        val cx = width / 2f; val cy = height / 2f; val max = minOf(cx, cy)
        for (i in 0..2) {
            val p = (t + i / 3f) % 1f
            paint.alpha = ((1 - p) * 255).toInt()
            c.drawCircle(cx, cy, max * 0.3f + max * 0.7f * p, paint)
        }
    }
}
