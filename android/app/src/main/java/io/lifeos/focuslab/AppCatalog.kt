package io.lifeos.focuslab

import android.content.*
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import android.provider.Settings
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager

data class LabApp(val name: String, val packageName: String, val category: Int, val system: Boolean, val icon: Drawable)
class AppCatalog(private val context: Context) {
    private val pm = context.packageManager
    @Suppress("DEPRECATION")
    fun isHome(packageName: String): Boolean = pm.queryIntentActivities(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0
    ).any { it.activityInfo.packageName == packageName }
    @Suppress("DEPRECATION")
    fun apps(): List<LabApp> = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        .distinctBy { it.activityInfo.packageName }.map {
            val info = it.activityInfo.applicationInfo
            LabApp(it.loadLabel(pm).toString(), info.packageName, info.category,
                info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0, it.loadIcon(pm))
        }.sortedBy { it.name.lowercase() }
    @Suppress("DEPRECATION")
    fun protected(packageName: String): Boolean {
        if (packageName == context.packageName || packageName == "android") return true
        // All system apps are ineligible in the lab, including vendor system UI and emergency components.
        val info = pm.getApplicationInfo(packageName, 0)
        if (info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) return true
        val protected = mutableSetOf<String>()
        listOf(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), Intent(Intent.ACTION_DIAL), Intent(Settings.ACTION_SETTINGS)).forEach { intent ->
            pm.queryIntentActivities(intent, 0).forEach { protected.add(it.activityInfo.packageName) }
        }
        context.getSystemService(InputMethodManager::class.java).inputMethodList.forEach { protected.add(it.packageName) }
        context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage?.let { protected.add(it) }
        return packageName in protected
    }
}
