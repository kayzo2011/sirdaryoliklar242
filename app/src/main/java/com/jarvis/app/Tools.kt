// PATH: app/src/main/java/com/jarvis/app/Tools.kt
package com.jarvis.app

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.ContactsContract
import kotlinx.coroutines.delay
import org.json.JSONObject

class Tools(private val ctx: Context, private val ask: suspend (String) -> Boolean) {

    suspend fun execute(name: String, a: JSONObject): String = try {
        val svc = JarvisAccessibilityService.instance
        when (name) {
            "open_app" -> {
                val i = ctx.packageManager.getLaunchIntentForPackage(a.getString("package"))
                if (i == null) "ilova topilmadi" else {
                    ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); delay(1200); "ochildi"
                }
            }
            "read_screen" -> svc?.dumpScreen() ?: "accessibility yoqilmagan"
            "click" -> if (svc?.clickByText(a.getString("text")) == true) "bosildi" else "topilmadi"
            "type_text" -> if (svc?.typeText(a.getString("text")) == true) "yozildi" else "maydon topilmadi"
            "press" -> {
                val act = when (a.getString("key")) {
                    "back" -> AccessibilityService.GLOBAL_ACTION_BACK
                    "home" -> AccessibilityService.GLOBAL_ACTION_HOME
                    else -> AccessibilityService.GLOBAL_ACTION_RECENTS
                }
                if (svc?.performGlobalAction(act) == true) "ok" else "bajarilmadi"
            }
            "read_notifications" -> synchronized(JarvisNotificationListener.recent) {
                JarvisNotificationListener.recent.takeLast(10).joinToString("\n").ifEmpty { "bildirishnoma yo'q" }
            }
            "call_contact" -> callContact(a.getString("name"))
            "set_volume" -> {
                val am = ctx.getSystemService(AudioManager::class.java)
                val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                am.setStreamVolume(AudioManager.STREAM_MUSIC, max * a.getString("percent").toInt().coerceIn(0, 100) / 100, 0)
                "ok"
            }
            "ask_user" -> if (ask(a.getString("question"))) "ha" else "yo'q"
            else -> "noma'lum vosita"
        }
    } catch (e: Exception) { "xatolik: ${e.message}" }

    private fun callContact(name: String): String {
        val c = ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?", arrayOf("%$name%"), null)
        val num = c?.use { if (it.moveToFirst()) it.getString(0) else null } ?: return "kontakt topilmadi"
        ctx.startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$num")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return "qo'ng'iroq qilinmoqda"
    }
}
