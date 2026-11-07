// PATH: app/src/main/java/com/jarvis/app/SecurityProtocol.kt
package com.jarvis.app

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.provider.Settings
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.delay
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

// ---------- Shifrlangan xotira: PIN xeshi, Wi-Fi parollari, stealth holati ----------
class SecureStore(ctx: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        ctx, "jarvis_secure",
        MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var stealth: Boolean
        get() = prefs.getBoolean("stealth", false)
        set(v) = prefs.edit().putBoolean("stealth", v).apply()

    var duressPhrase: String
        get() = prefs.getString("phrase", "") ?: ""
        set(v) = prefs.edit().putString("phrase", v.trim().lowercase()).apply()

    var speakerName: String
        get() = prefs.getString("speaker", "") ?: ""
        set(v) = prefs.edit().putString("speaker", v).apply()

    fun saveWifi(ssid: String, pass: String) = prefs.edit().putString("wifi:$ssid", pass).apply()
    fun wifiPass(ssid: String): String? = prefs.getString("wifi:$ssid", null)
    fun putS(k: String, v: String) = prefs.edit().putString("s:$k", v).apply()
    fun getS(k: String): String = prefs.getString("s:$k", "") ?: ""

    private fun hash(pin: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)).encoded

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString("pin_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("pin_hash", Base64.encodeToString(hash(pin, salt), Base64.NO_WRAP))
            .apply()
    }

    fun checkPin(pin: String): Boolean {
        val now = System.currentTimeMillis()
        if (now < prefs.getLong("locked_until", 0)) return false
        val salt = Base64.decode(prefs.getString("pin_salt", null) ?: return false, Base64.NO_WRAP)
        val stored = Base64.decode(prefs.getString("pin_hash", null) ?: return false, Base64.NO_WRAP)
        val ok = MessageDigest.isEqual(hash(pin, salt), stored)
        if (ok) {
            prefs.edit().putInt("fails", 0).apply()
        } else {
            val fails = prefs.getInt("fails", 0) + 1
            val e = prefs.edit().putInt("fails", fails)
            if (fails >= 5) e.putLong("locked_until", now + 5 * 60_000L).putInt("fails", 0)
            e.apply()
        }
        return ok
    }
}

data class Step(val name: String, val ok: Boolean, val note: String)

// ---------- Asosiy modul ----------
class SecurityProtocol(
    private val ctx: Context,
    private val store: SecureStore,
    /** Ovozli tasdiq: TTS bilan savol berib, "ha" javobini kutadi. */
    private val confirmByVoice: suspend (String) -> Boolean
) {
    /** STT natijasini shu yerga bering. ownerVerified = VoiceGate.isOwner(...) natijasi. */
    suspend fun onSpeech(text: String, ownerVerified: Boolean): List<Step>? {
        val phrase = store.duressPhrase
        if (!ownerVerified || phrase.isEmpty() || !text.lowercase().contains(phrase)) return null
        return runProtocol()
    }

    suspend fun runProtocol(): List<Step> {
        val steps = mutableListOf<Step>()
        steps += safe("Kolonka") { connectSpeaker(store.speakerName) }
        steps += safe("Wi-Fi") { joinWifi(store.getS("wifi_ssid").ifEmpty { "Camelot 5G" }) }
        steps += safe("Google tarixi") { wipeGoogleHistory() }
        steps += safe("2-daraja himoya") { enableStealth() }  // oxirida: ekranni qulflaydi
        return steps  // TTS bilan haqiqiy natijani ayting
    }

    private suspend fun safe(name: String, block: suspend () -> Step): Step =
        try { block() } catch (e: Exception) { Step(name, false, e.message ?: "xatolik") }

    private fun svc() = JarvisAccessibilityService.instance

    // 1. Kolonka: Bluetooth sozlamalarini ochib, kolonkani bosadi.
    // Boshqa telefonni uzib bo'lmaydi; kolonka bitta ulanishni qo'llasa, u bo'shagach ulanadi.
    private suspend fun connectSpeaker(name: String): Step {
        if (name.isBlank()) return Step("Kolonka", false, "Kolonka nomi kiritilmagan")
        val s = svc() ?: return Step("Kolonka", false, "Accessibility yoqilmagan")
        ctx.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        delay(1500)
        val ok = s.clickByText(name)
        return Step("Kolonka", ok,
            if (ok) "Ulanish bosildi (Ali ulangan bo'lsa, kolonka rad etishi mumkin)" else "Ro'yxatda topilmadi")
    }

    // 2. Wi-Fi: faqat saqlangan (oq ro'yxat) tarmoq, parol shifrlangan xotiradan.
    private fun joinWifi(ssid: String): Step {
        val pass = store.wifiPass(ssid) ?: return Step("Wi-Fi", false, "$ssid paroli saqlanmagan")
        val suggestion = WifiNetworkSuggestion.Builder()
            .setSsid(ssid).setWpa2Passphrase(pass).setIsAppInteractionRequired(false).build()
        val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val r = wm.addNetworkSuggestions(listOf(suggestion))
        val ok = r == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS ||
                 r == WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_DUPLICATE
        return Step("Wi-Fi", ok, if (ok) "$ssid taklifi qo'shildi, tizim ulanadi" else "Rad etildi (kod $r)")
    }

    // 3. Google tarixi: ovozli tasdiqdan keyingina, qaytarib bo'lmaydi.
    private suspend fun wipeGoogleHistory(): Step {
        if (!confirmByVoice("Google tarixi butunlay o'chiriladi va qaytarilmaydi. Tasdiqlaysizmi?"))
            return Step("Google tarixi", false, "Tasdiqlanmadi, o'chirilmadi")
        val s = svc() ?: return Step("Google tarixi", false, "Accessibility yoqilmagan")
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://myactivity.google.com/"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        delay(4000)
        // Tilga bog'liq: interfeys tiliga mos matnlarni qo'shing.
        val flow = listOf(
            listOf("Delete", "O'chirish", "Удалить"),
            listOf("All time", "Butun vaqt", "Все время"),
            listOf("Next", "Keyingi", "Далее"),
            listOf("Delete", "O'chirish", "Удалить")
        )
        for (options in flow) {
            if (options.none { s.clickByText(it) }) return Step("Google tarixi", false, "Tugma topilmadi, qo'lda yakunlang")
            delay(1500)
        }
        return Step("Google tarixi", true, "O'chirish buyrug'i berildi (natijani tekshiring)")
    }

    // 4. Stealth: Jarvis ma'lumotlari qulflanadi (VaultGuard o'qiydi), ekran qulflanadi.
    // O'yinlarni Private Space ga oldindan ko'chiring: ekran qulflanganda ular ham yashirinadi.
    private fun enableStealth(): Step {
        store.stealth = true
        val locked = svc()?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN) ?: false
        return Step("2-daraja himoya", true,
            if (locked) "Yoqildi, ekran qulflandi" else "Jarvis ma'lumotlari yopildi (ekran qulflanmadi)")
    }

    // Ochish: faqat PIN bilan (5 xato = 5 daqiqa blok).
    fun unlock(pin: String): Boolean {
        val ok = store.checkPin(pin)
        if (ok) store.stealth = false
        return ok
    }
}

// ---------- Jarvisning o'z ma'lumotlarini o'qiydigan har bir joyda chaqiring ----------
object VaultGuard {
    fun requireOpen(store: SecureStore) {
        check(!store.stealth) { "Qulflangan: PIN bilan oching" }
    }
}
