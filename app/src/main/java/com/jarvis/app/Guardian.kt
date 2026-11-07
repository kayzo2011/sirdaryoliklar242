// PATH: app/src/main/java/com/jarvis/app/Guardian.kt
package com.jarvis.app

import android.app.usage.UsageStatsManager
import android.content.Context
import java.util.Calendar

/** Kuzatiladigan ilovalar kunlik limitga yetsa ogohlantiradi va bosh ekranga qaytaradi. */
class Guardian(private val ctx: Context, private val store: SecureStore) {
    fun watched(): List<String> =
        store.getS("watched").split(",").map { it.trim() }.filter { it.isNotEmpty() }

    fun check(): String? {
        val w = watched()
        val limit = store.getS("limit_min").toIntOrNull() ?: return null
        if (w.isEmpty()) return null
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        }.timeInMillis
        val stats = ctx.getSystemService(UsageStatsManager::class.java)
            .queryAndAggregateUsageStats(start, System.currentTimeMillis())
        val min = w.sumOf { stats[it]?.totalTimeInForeground ?: 0L } / 60000
        return if (min >= limit) "Limitga yetding: bugun $min daqiqa. To'xta." else null
    }
}
