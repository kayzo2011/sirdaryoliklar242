package com.jarvis.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.ServiceCompat
import kotlin.math.min
import kotlin.math.sqrt

class JarvisService : Service() {
    private var recorder: AudioRecord? = null
    private var wave: WaveView? = null
    @Volatile private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (running) return START_STICKY
        running = true

        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("jarvis", "Jarvis", NotificationManager.IMPORTANCE_LOW)
        )
        val notif = android.app.Notification.Builder(this, "jarvis")
            .setContentTitle("Jarvis tinglayapti")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()
        ServiceCompat.startForeground(this, 1, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)

        showOverlay()
        startListening()
        return START_STICKY
    }

    private fun showOverlay() {
        val d = resources.displayMetrics.density
        val v = WaveView(this)
        val lp = WindowManager.LayoutParams(
            (180 * d).toInt(), (56 * d).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (48 * d).toInt()
        }
        getSystemService(WindowManager::class.java).addView(v, lp)
        wave = v
    }

    private fun startListening() {
        val rate = 16000
        val minBuf = AudioRecord.getMinBufferSize(
            rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val rec = AudioRecord(
            MediaRecorder.AudioSource.MIC, rate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf * 2
        )
        recorder = rec
        rec.startRecording()
        Thread {
            val buf = ShortArray(1024)
            while (running) {
                val n = rec.read(buf, 0, buf.size)
                if (n <= 0) continue
                var sum = 0.0
                for (i in 0 until n) sum += buf[i] * buf[i].toDouble()
                val level = min(1f, (sqrt(sum / n) / 5000.0).toFloat())
                wave?.post { wave?.push(level) }
            }
        }.start()
    }

    override fun onDestroy() {
        running = false
        recorder?.run { stop(); release() }
        wave?.let { getSystemService(WindowManager::class.java).removeView(it) }
        wave = null
        super.onDestroy()
    }
}
