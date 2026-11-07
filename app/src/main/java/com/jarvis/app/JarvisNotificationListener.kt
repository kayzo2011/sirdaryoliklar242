// PATH: app/src/main/java/com/jarvis/app/JarvisNotificationListener.kt
package com.jarvis.app

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class JarvisNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val e = sbn.notification.extras
        val line = "${sbn.packageName}: ${e.getCharSequence(Notification.EXTRA_TITLE) ?: ""} - ${e.getCharSequence(Notification.EXTRA_TEXT) ?: ""}"
        synchronized(recent) { recent.add(line); if (recent.size > 30) recent.removeAt(0) }
    }
    companion object { val recent = mutableListOf<String>() }
}
