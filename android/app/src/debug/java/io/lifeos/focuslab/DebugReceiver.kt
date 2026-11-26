package io.lifeos.focuslab

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Bounded, ADB-only reproduction of the user's already-configured test. No new targets/permissions. */
class DebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = LabStore(context)
        try {
            when (intent.getStringExtra("operation")) {
                "start" -> {
                    val seconds = intent.getIntExtra("seconds", 30)
                    require(seconds in 1..60)
                    require(FocusAccessibilityService.live != null)
                    require(store.targets.isNotEmpty() && store.targets.none { AppCatalog(context).protected(it) })
                    val mode = intent.getStringExtra("mode") ?: "OVERLAY"
                    require(mode == "HOME" || mode == "OVERLAY")
                    val assist = intent.getBooleanExtra("usage_assist", store.usageAssistOptIn)
                    require(!assist || usageGranted(context))
                    store.mode = mode
                    store.start(System.currentTimeMillis(), seconds * 1000L, true)
                    if (assist) {
                        ForegroundProbe.enableForTest(seconds.toLong())
                    }
                    store.log(if (assist) "USAGE_ASSIST_ENABLED" else "USAGE_ASSIST_DISABLED")
                    resultData = "STARTED_BOUNDED_TEST"
                }
                "stop" -> { store.stop(); resultData = "STOPPED" }
                "snapshot" -> resultData = store.report()
                else -> resultData = "UNSUPPORTED_OPERATION"
            }
        } catch (_: Exception) { store.stop("DEBUG_COMMAND_FAILED"); resultData = "TEST_NOT_STARTED_CHECK_CONNECTION_TARGETS_AND_PERMISSIONS" }
    }
}
