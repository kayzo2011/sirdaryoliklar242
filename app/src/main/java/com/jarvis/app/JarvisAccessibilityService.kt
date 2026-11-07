// PATH: app/src/main/java/com/jarvis/app/JarvisAccessibilityService.kt
package com.jarvis.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class JarvisAccessibilityService : AccessibilityService() {
    companion object { @Volatile var instance: JarvisAccessibilityService? = null }

    override fun onServiceConnected() { instance = this }
    override fun onUnbind(intent: Intent?): Boolean { instance = null; return super.onUnbind(intent) }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun currentPackage(): String? = rootInActiveWindow?.packageName?.toString()

    fun clickByText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        var n: AccessibilityNodeInfo? = root.findAccessibilityNodeInfosByText(text).firstOrNull() ?: return false
        while (n != null) {
            if (n.isClickable) return n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            n = n.parent
        }
        return false
    }

    fun typeText(text: String): Boolean {
        val f = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        val b = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return f.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)
    }

    fun dumpScreen(limit: Int = 3000): String {
        val sb = StringBuilder()
        fun walk(n: AccessibilityNodeInfo?) {
            if (n == null || sb.length > limit) return
            val t = n.text ?: n.contentDescription
            if (!t.isNullOrBlank()) sb.append(if (n.isClickable) "[tugma] " else "").append(t).append('\n')
            for (i in 0 until n.childCount) walk(n.getChild(i))
        }
        walk(rootInActiveWindow)
        return sb.toString().take(limit).ifEmpty { "(ekran bo'sh yoki o'qib bo'lmadi)" }
    }
}
