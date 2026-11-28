package io.lifeos.focuslab

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.SystemClock

object ForegroundProbe {
    var enabledUntilElapsed = 0L
    private val current = ForegroundContinuity()
    fun resetCurrent() = current.reset()
    /** Only the current foreground, queried during opted-in focus or armed automatic execution. */
    fun observeCurrent(context: Context): String? {
        if (!usageGranted(context)) { resetCurrent(); return null }
        val elapsed = SystemClock.elapsedRealtime()
        if (current.cached(elapsed)) return current.candidate
        val now = System.currentTimeMillis()
        val begin = current.begin(now, elapsed)
        return try {
            val events = context.getSystemService(UsageStatsManager::class.java).queryEvents(begin, now)
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> event.packageName?.let { current.resumed(it) }
                    UsageEvents.Event.ACTIVITY_PAUSED -> event.packageName?.let { current.paused(it) }
                    UsageEvents.Event.SCREEN_NON_INTERACTIVE, UsageEvents.Event.DEVICE_SHUTDOWN -> current.reset()
                }
            }
            current.finish(now, elapsed)
            current.candidate
        } catch (_: Exception) { resetCurrent(); null }
    }
    fun enableForTest(seconds: Long) { enabledUntilElapsed = SystemClock.elapsedRealtime() + seconds.coerceIn(1, 60) * 1000 }
    fun enableForFocus(durationMillis: Long) { enabledUntilElapsed = SystemClock.elapsedRealtime() + durationMillis.coerceIn(0, ScheduleEngine.MAX_TASK) }
    fun decision(context: Context, targets: Set<String>): String {
        if (enabledUntilElapsed == 0L) return "ASSIST_NOT_ENABLED"
        if (SystemClock.elapsedRealtime() >= enabledUntilElapsed) return "ASSIST_EXPIRED"
        if (!usageGranted(context)) return "ASSIST_PERMISSION_MISSING"
        val candidate = observeCurrent(context)
        return when {
            candidate in targets -> "ASSIST_TARGET_CONFIRMED"
            candidate == null -> "ASSIST_NO_CURRENT_RESUME"
            else -> "ASSIST_OTHER_APP_RESUMED"
        }
    }
}
