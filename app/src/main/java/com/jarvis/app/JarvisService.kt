// PATH: app/src/main/java/com/jarvis/app/JarvisService.kt
package com.jarvis.app

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

class JarvisService : Service() {
    companion object { const val ACTION_LISTEN = "com.jarvis.app.LISTEN" }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val busy = AtomicBoolean(false)
    private val YES = setOf("ha", "xa", "ok", "okay", "da", "tasdiqlayman", "davom", "yes", "да")

    private lateinit var store: SecureStore
    private lateinit var tts: Tts
    private lateinit var stt: Stt
    private lateinit var gate: VoiceGate
    private lateinit var overlay: Overlay
    private lateinit var brain: AgentBrain
    private lateinit var security: SecurityProtocol
    private lateinit var guardian: Guardian
    private var enrolled: FloatArray? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        startFg()
        if (!::store.isInitialized) init()
        if (i?.action == ACTION_LISTEN) listen()
        return START_STICKY
    }

    private fun startFg() {
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel("jarvis", "Jarvis", NotificationManager.IMPORTANCE_LOW))
        val pi = PendingIntent.getService(
            this, 0, Intent(this, JarvisService::class.java).setAction(ACTION_LISTEN),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val action = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_btn_speak_now), "Tinglash", pi
        ).build()
        val n = Notification.Builder(this, "jarvis")
            .setContentTitle("Jarvis tayyor")
            .setContentText("Buyruq berish uchun «Tinglash» ni bosing")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .addAction(action).build()
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
    }

    private fun init() {
        store = SecureStore(this)
        tts = Tts(this); stt = Stt(this); gate = VoiceGate(this); overlay = Overlay(this)
        enrolled = store.getS("voice_emb").takeIf { it.isNotEmpty() }?.let { VoiceGate.decode(it) }
        brain = AgentBrain({ store.getS("claude_key") }, Tools(this) { q -> confirm(q) })
        security = SecurityProtocol(this, store) { _ -> confirm("Tasdiqlaysizmi?") }
        guardian = Guardian(this, store)
        startGuardianLoop()
    }

    private fun listen() {
        if (!busy.compareAndSet(false, true)) return
        scope.launch {
            try { handleCommand() }
            catch (e: Exception) { Log.e("Jarvis", "command", e) }
            finally { busy.set(false) }
        }
    }

    private suspend fun handleCommand() {
        overlay.show()
        try {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80).startTone(ToneGenerator.TONE_PROP_BEEP, 150)
            delay(350)
            val pcm = Recorder.record(8000)
