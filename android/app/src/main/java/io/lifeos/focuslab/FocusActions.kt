package io.lifeos.focuslab

import android.content.Context
import android.content.Intent

object FocusActions {
    fun openAllowed(context: Context, packageName: String): Boolean {
        val store = LabStore(context)
        if (!store.isWhitelistSession || store.phase() != Phase.ACTIVE || packageName !in store.whitelist) return false
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            FocusAccessibilityService.live?.clearOverlay("已打开白名单应用，专注继续")
            store.log("WHITELIST_APP_OPENED")
            true
        } catch (_: Exception) { store.log("WHITELIST_APP_LAUNCH_FAILED"); false }
    }
}
