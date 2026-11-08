// PATH: app/src/main/java/com/jarvis/app/MainActivity.kt
package com.jarvis.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.*
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*

class MainActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var store: SecureStore

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        store = SecureStore(this)
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE, Manifest.permission.POST_NOTIFICATIONS), 1)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 40, 40, 40) }
        setContentView(ScrollView(this).apply { addView(root) })
        if (store.stealth) lockedUi(root) else mainUi(root)
    }

    private fun et(root: LinearLayout, hint: String, secret: Boolean = false): EditText =
        EditText(this).apply {
            this.hint = hint
            if (secret) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            root.addView(this)
        }

    private fun btn(root: LinearLayout, text: String, onClick: () -> Unit) =
        Button(this).apply { this.text = text; setOnClickListener { onClick() }; root.addView(this) }

    private fun lockedUi(root: LinearLayout) {
        val pin = et(root, "PIN", true)
        btn(root, "Ochish") {
            if (SecurityProtocol(this, store) { false }.unlock(pin.text.toString())) recreate()
            else toast("Noto'g'ri PIN")
        }
    }

    private fun mainUi(root: LinearLayout) {
        val key = et(root, "Groq API kaliti (gsk_...)", true)
        val pin = et(root, "Yangi PIN (2-daraja himoyani ochish uchun)", true)
        val phrase = et(root, "Maxfiy ibora (og'ir vaziyat uchun)")
        val speaker = et(root, "Bluetooth kolonka nomi")
        val ssid = et(root, "Wi-Fi nomi (masalan Camelot 5G)")
        val wpass = et(root, "Wi-Fi paroli", true)
        val watched = et(root, "Kuzatiladigan ilova paketlari (vergul bilan)")
        val limit = et(root, "Kunlik limit (daqiqa)").apply { inputType = InputType.TYPE_CLASS_NUMBER }

        btn(root, "Saqlash") {
            fun put(k: String, v: String) { if (v.isNotBlank()) store.putS(k, v.trim()) }
            put("claude_key", key.text.toString())
            put("watched", watched.text.toString()); put("limit_min", limit.text.toString())
            if (pin.text.isNotBlank()) store.setPin(pin.text.toString())
            if (phrase.text.isNotBlank()) store.duressPhrase = phrase.text.toString()
            if (speaker.text.isNotBlank()) store.speakerName = speaker.text.toString().trim()
            if (ssid.text.isNotBlank() && wpass.text.isNotBlank()) {
                store.putS("wifi_ssid", ssid.text.toString().trim())
                store.saveWifi(ssid.text.toString().trim(), wpass.text.toString())
            }
            listOf(key, pin, wpass).forEach { it.setText("") }
            toast("Saqlandi")
        }

        btn(root, "Ovoz izini yozish (5 marta, 3 soniya)") {
            scope.launch {
                try {
                    val gate = withContext(Dispatchers.Default) { VoiceGate(this@MainActivity) }
                    val clips = mutableListOf<ShortArray>()
                    for (i in 1..5) {
                        toast("$i/5: 3 soniya tabiiy gapiring")
                        delay(700)
                        clips += Recorder.record(3000, fixed = true)
                    }
                    store.putS("voice_emb", VoiceGate.encode(withContext(Dispatchers.Default) { gate.enroll(clips) }))
                    toast("Ovoz izi saqlandi")
                } catch (e: Exception) { toast("Xato: ${e.message}") }
            }
        }

        listOf(
            "Overlay ruxsati" to Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
            "Accessibility yoqish" to Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
            "Bildirishnomalarni o'qish" to Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
            "Foydalanish statistikasi (Guardian)" to Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        ).forEach { (t, i) -> btn(root, t) { startActivity(i) } }

        btn(root, "Jarvisni ishga tushirish") {
            ContextCompat.startForegroundService(this, Intent(this, JarvisService::class.java))
            toast("Jarvis ishga tushdi: bildirishnomada «Tinglash» tugmasi bor")
        }

        btn(root, "TINGLASH (buyruq berish)") {
            ContextCompat.startForegroundService(this,
                Intent(this, JarvisService::class.java).setAction(JarvisService.ACTION_LISTEN))
        }

        val rep = store.getS("last_report")
        if (rep.isNotEmpty()) root.addView(TextView(this).apply { text = "Oxirgi protokol hisoboti:\n$rep" })
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
