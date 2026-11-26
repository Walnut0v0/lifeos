package io.lifeos.focuslab

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.SystemClock

object ForegroundProbe {
    var enabledUntilElapsed = 0L
    fun enableForTest(seconds: Long) { enabledUntilElapsed = SystemClock.elapsedRealtime() + seconds.coerceIn(1, 60) * 1000 }
    fun targetStillResumed(context: Context, targets: Set<String>): Boolean {
        if (SystemClock.elapsedRealtime() >= enabledUntilElapsed || !usageGranted(context)) return false
        return try {
            val end = System.currentTimeMillis()
            val events = context.getSystemService(UsageStatsManager::class.java).queryEvents(end - 5000, end)
            val event = UsageEvents.Event()
            val state = ResumedAppState()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val pkg = event.packageName ?: continue
                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> state.resumed(pkg)
                    UsageEvents.Event.ACTIVITY_PAUSED -> state.paused(pkg)
                }
            }
            state.isTarget(targets)
        } catch (_: Exception) { false }
    }
}
