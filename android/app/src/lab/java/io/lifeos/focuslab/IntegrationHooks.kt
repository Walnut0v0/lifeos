package io.lifeos.focuslab

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable

object IntegrationHooks {
    fun resume(context: Context) { if (LabStore(context).session()?.planned == true) LabStore(context).stop("OFFLINE_VARIANT") }
    fun tick(context: Context) { if (LabStore(context).session()?.planned == true) LabStore(context).stop("OFFLINE_VARIANT") }
    fun system(context: Context, action: String?) = Unit
    fun stopping(context: Context, code: String, planned: Boolean) = Unit
    fun allows(context: Context, packageName: String): Boolean = false
    fun debug(context: Context, intent: Intent): String? = null
    @Composable fun Status(activity: Activity, pulse: Int) = Unit
}
