package io.lifeos.focuslab

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.delay
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

object FocusPermissions {
    fun accessibilityEnabled(activity: android.content.Context): Boolean = try {
        val own = ComponentName(activity, FocusAccessibilityService::class.java)
        Settings.Secure.getString(activity.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            .orEmpty().split(':').any { ComponentName.unflattenFromString(it) == own }
    } catch (_: Exception) { false }

    fun accessibilityLabel(activity: android.content.Context): String = when {
        FocusAccessibilityService.live != null -> "应用识别已连接"
        accessibilityEnabled(activity) -> "应用识别已授权，服务尚未连接"
        else -> "应用识别尚未授权"
    }
}

/** One prompt per missing capability per screen visit; never grants or changes system settings. */
@Composable fun PermissionPrompts(activity: Activity, pulse: Int, requested: Int) {
    var resumed by remember { mutableStateOf((activity as? LifecycleOwner)?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true) }
    DisposableEffect(activity) {
        val owner = activity as? LifecycleOwner
        val observer = LifecycleEventObserver { _, _ -> resumed = owner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }
    val enabled = remember(pulse / 4) { FocusPermissions.accessibilityEnabled(activity) }
    val connected = FocusAccessibilityService.live != null
    val usage = remember(pulse / 4) { usageGranted(activity) }
    val exact = remember(pulse / 4) { LabStore(activity).exact() }
    val notifications = remember(pulse / 4) {
        Build.VERSION.SDK_INT < 33 || activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
    var shown by remember { mutableStateOf(emptySet<String>()) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf(false) }
    LaunchedEffect(requested) { if (requested > 0) shown = emptySet() }
    LaunchedEffect(enabled, connected, usage, exact, notifications, shown, resumed) {
        if (!resumed) return@LaunchedEffect
        delay(1500) // Give an already-authorized service time to reconnect before suggesting action.
        val missing = listOfNotNull(
            if (!connected) if (enabled) "reconnect" else "accessibility" else null,
            if (!usage) "usage" else null,
            if (!exact) "alarm" else null,
            if (!notifications) "notifications" else null
        )
        // A slow system rebind can complete while this prompt is visible.
        // Remove stale guidance instead of asking the user to grant an existing permission again.
        if (dialog !in missing) dialog = null
        if (dialog == null) dialog = missing.firstOrNull { it !in shown }
    }
    val kind = dialog?.takeIf { resumed } ?: return
    fun close() { shown = shown + kind; dialog = null; error = false }
    val explanation = when (kind) {
        "accessibility" -> "需要你在系统页面开启 LifeOS 的无障碍服务，才能识别并拦截受限应用。只使用应用切换信息，不读取页面内容。"
        "reconnect" -> "系统开关仍然开启，但 LifeOS 服务尚未连接。若稍等后仍未恢复，请在无障碍页面把 LifeOS 关闭再开启一次。应用无法替你恢复系统连接。"
        "usage" -> "需要使用情况访问来确认当前应用，减少荣耀桌面事件造成的误判。专注识别只在内存处理；全天记录仍由独立开关控制。"
        "alarm" -> "允许“闹钟和提醒”可提高后台到点执行的及时性。不允许时仍会检查当前时段，但系统可能延迟。"
        else -> "允许通知后，可以从通知栏快速返回专注页；拒绝不会阻止你使用计时。"
    }
    AlertDialog(onDismissRequest = { close() }, title = { Text(when (kind) {
        "accessibility" -> "开启应用识别"; "reconnect" -> "应用识别需要重新连接"
        "usage" -> "允许前台应用识别"; "alarm" -> "允许准时执行计划"; else -> "允许专注提醒"
    }) }, text = { Text(if (error) "系统没有提供这个授权入口，请稍后重试。" else explanation) },
        confirmButton = { TextButton(onClick = {
            try {
                if (kind == "notifications" && Build.VERSION.SDK_INT >= 33) ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 410)
                else activity.startActivity(when (kind) {
                    "usage" -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).setData(Uri.parse("package:${activity.packageName}"))
                    "alarm" -> Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).setData(Uri.parse("package:${activity.packageName}"))
                    else -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                })
                close()
            } catch (_: Exception) { error = true }
        }) { Text(if (kind == "notifications") "允许通知" else "前往授权") } },
        dismissButton = { androidx.compose.foundation.layout.Column {
            TextButton(onClick = {
                close(); shown = setOf("reconnect", "accessibility", "usage", "alarm", "notifications")
            }) { Text("稍后") }
            if (LabStore(activity).phase() == Phase.ACTIVE) TextButton(onClick = {
                LabStore(activity).stop("WHITELIST_EMERGENCY_STOP"); close()
            }) { Text("应急立即解除", color = MaterialTheme.colorScheme.error) }
        } })
}
