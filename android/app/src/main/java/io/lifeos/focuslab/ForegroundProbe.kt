package io.lifeos.focuslab

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.SystemClock

object ForegroundProbe {
    var enabledUntilElapsed = 0L
    fun enableForTest(seconds: Long) { enabledUntilElapsed = SystemClock.elapsedRealtime() + seconds.coerceIn(1, 60) * 1000 }
    fun enableForFocus(durationMillis: Long) { enabledUntilElapsed = SystemClock.elapsedRealtime() + durationMillis.coerceIn(0, FocusPolicy.MAX_DURATION) }
    fun decision(context: Context, targets: Set<String>): String {
        if (enabledUntilElapsed == 0L) return "ASSIST_NOT_ENABLED"
        if (SystemClock.elapsedRealtime() >= enabledUntilElapsed) return "ASSIST_EXPIRED"
        if (!usageGranted(context)) return "ASSIST_PERMISSION_MISSING"
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
            when {
                state.isTarget(targets) -> "ASSIST_TARGET_CONFIRMED"
                state.candidate == null -> "ASSIST_NO_CURRENT_RESUME"
                else -> "ASSIST_OTHER_APP_RESUMED"
            }
        } catch (_: Exception) { "ASSIST_QUERY_FAILED" }
    }
}
