package io.lifeos.focuslab

import android.content.Context
import android.content.Intent

object FocusActions {
    @Suppress("DEPRECATION")
    fun cameraPackage(context: Context): String? = try {
        val intent = Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        val pm = context.packageManager
        val systemCameras = pm.queryIntentActivities(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo }.filter {
                it.applicationInfo.flags and (android.content.pm.ApplicationInfo.FLAG_SYSTEM or android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0 && pm.getLaunchIntentForPackage(it.packageName) != null
            }
        val preferred = pm.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        systemCameras.firstOrNull { it.packageName == preferred }?.packageName ?: systemCameras.firstOrNull()?.packageName
    } catch (_: Exception) { null }

    fun openAllowed(context: Context, packageName: String): Boolean {
        val store = LabStore(context)
        if (packageName !in store.whitelist && packageName != cameraPackage(context)) return false
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            FocusAccessibilityService.live?.clearOverlay("已打开白名单应用，专注继续")
            store.log("WHITELIST_APP_OPENED")
            true
        } catch (_: Exception) { store.log("WHITELIST_APP_LAUNCH_FAILED"); false }
    }
}
