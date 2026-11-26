package io.lifeos.focuslab

import android.content.*
import android.os.SystemClock

class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = LabStore(context)
        try {
            if (intent.action == "STOP") { store.stop(); return }
            val s = store.session()
            if (intent.action == Intent.ACTION_BOOT_COMPLETED && s?.manual == true) { store.stop("MANUAL_BOOT_STOP"); return }
            if (intent.action == "START" || intent.action == "END") {
                if (s != null) {
                    val actual = if (s.manual) SystemClock.elapsedRealtime() else System.currentTimeMillis()
                    val expected = if (intent.action == "START") s.start else if (s.manual) s.elapsedEnd else s.end
                    store.log(if (intent.action == "START") "ALARM_START" else "ALARM_END", actual - expected)
                }
            } else store.log("SYSTEM_RECONCILE")
            store.reschedule()
            FocusAccessibilityService.live?.reconcile()
        } catch (_: Exception) { store.stop("RECEIVER_FAULT") }
    }
}
