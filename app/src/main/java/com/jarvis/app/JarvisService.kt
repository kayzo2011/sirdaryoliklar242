// PATH: app/src/main/java/com/jarvis/app/JarvisService.kt
package com.jarvis.app

import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

class JarvisService : Service() {
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
    private var porcupine: PorcupineManager? = null
    private var enrolled: FloatArray? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        startFg()
        if (!::store.isInitialized) init()
        return START_STICKY
    }

    private fun startFg() {
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel("jarvis", "Jarvis", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "jarvis")
            .setContentTitle("Jarvis tinglayapti")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now).build()
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
    }

    private fun init() {
        store = SecureStore(this)
        tts = Tts(this); stt = Stt(this); gate = VoiceGate(this); overlay = Overlay(this)
        enrolled = store.getS("voice_emb").takeIf { it.isNotEmpty() }?.let { VoiceGate.decode(it) }
        brain = AgentBrain({ store.getS("claude_key") }, Tools(this) { q -> confirm(q) })
        security = SecurityProtocol(this, store) { _ -> confirm("Tasdiqlaysizmi?") }
        guardian = Guardian(this, store)
        startWake()
        startGuardianLoop()
    }

    private fun startWake() {
        val key = store.getS("porcupine_key")
        if (key.isEmpty()) { stopSelf(); return }
        porcupine = PorcupineManager.Builder()
            .setAccessKey(key)
            .setKeyword(Porcupine.BuiltInKeyword.JARVIS)
            .setSensitivity(0.7f)
            .build(this, PorcupineManagerCallback { onWake() })
        porcupine?.start()
    }

    private fun onWake() {
        if (!busy.compareAndSet(false, true)) return
        porcupine?.stop()
        scope.launch {
            try { delay(250); handleCommand() }
            catch (e: Exception) { Log.e("Jarvis", "command", e) }
            finally { busy.set(false); porcupine?.start() }
        }
    }

    private suspend fun handleCommand() {
        overlay.show()
        try {
            val pcm = Recorder.record(8000)
            val owner = enrolled?.let { gate.isOwner(pcm, it) } ?: false
            val text = stt.transcribe(pcm)
            if (text.isBlank()) return

            // Maxfiy ibora: jim, ovozsiz ishlaydi. Hisobot faqat shifrlangan xotiraga yoziladi.
            security.onSpeech(text, owner)?.let { steps ->
                store.putS("last_report", steps.joinToString("\n") {
                    "${it.name}: ${if (it.ok) "OK" else "XATO"} - ${it.note}"
                })
                return
            }
            if (store.stealth || !owner) return   // qulflangan yoki egasi emas: jim

            tts.say(brain.run(text))
        } finally { overlay.hide() }
    }

    private suspend fun confirm(q: String): Boolean {
        tts.say(q); delay(250)
        val pcm = Recorder.record(4000)
        if (enrolled?.let { gate.isOwner(pcm, it) } != true) return false
        return stt.transcribe(pcm).lowercase().split(Regex("[^a-zа-яё']+")).any { it in YES }
    }

    private fun startGuardianLoop() = scope.launch {
        while (isActive) {
            delay(30_000)
            val msg = guardian.check() ?: continue
            val svc = JarvisAccessibilityService.instance
            val pkg = svc?.currentPackage()
            if (pkg != null && pkg in guardian.watched()) {
                svc?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                if (!busy.get()) tts.say(msg)
            }
        }
    }

    override fun onDestroy() {
        porcupine?.stop(); porcupine?.delete()
        scope.cancel()
        if (::tts.isInitialized) tts.shutdown()
        super.onDestroy()
    }
}
