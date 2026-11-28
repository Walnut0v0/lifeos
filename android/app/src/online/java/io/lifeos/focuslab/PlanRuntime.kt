package io.lifeos.focuslab

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock

object PlanRuntime {
    private var busy = false
    private var lastTick = 0L
    private var registeredAt = -1L
    fun tick(context: Context) {
        val elapsed = SystemClock.elapsedRealtime()
        if (elapsed - lastTick < 1000) return
        lastTick = elapsed
        reconcile(context)
    }
    fun suppressedKey(task: PlannedTask) = "${task.id}:${task.start}"
    fun stopping(context: Context, code: String, planned: Boolean) {
        if (!PlanStopPolicy.suppresses(code, planned)) return
        val plans = PlanStore(context)
        ScheduleEngine.resolve(plans.load(), System.currentTimeMillis()).task?.let {
            plans.prefs.edit().putString("suppressed_task", suppressedKey(it)).putString("execution_status", "本段已解除，结束前不自动重启").commit()
        }
    }
    @Synchronized fun reconcile(context: Context, forceAlarm: Boolean = false) {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            android.os.Handler(android.os.Looper.getMainLooper()).post { reconcile(context.applicationContext, forceAlarm) }
            return
        }
        if (busy) return
        busy = true
        try {
            val plans = PlanStore(context)
            val plan = plans.load()
            val now = System.currentTimeMillis()
            val decision = ScheduleEngine.resolve(plan, now)
            val store = LabStore(context)
            val selected = decision.task?.takeIf { plans.armed && plans.prefs.getString("suppressed_task", "") != suppressedKey(it) }
            val session = store.session()
            val connected = FocusAccessibilityService.live != null && usageGranted(context) && plans.prefs.contains("prepared_managed")
            if (selected != null && connected) {
                if (session?.planned != true || session.start != selected.start || session.end != selected.end || store.phase() != Phase.ACTIVE) {
                    val managed = plans.prefs.getStringSet("prepared_managed", emptySet())!!.toSet()
                    store.applyPlannedTask(selected, managed)
                }
            } else if (session?.planned == true) store.stop(if (selected != null && !connected) "PLAN_PERMISSION_LOST" else "PLAN_BOUNDARY")
            val status = when {
                !plans.armed -> "自动执行未启用"
                plan == null -> "尚未取得有效计划"
                decision.expired -> "计划已过期或时间异常，已放行"
                decision.unknownMode -> "不支持此模式，当前放行"
                decision.task != null && selected == null -> "本段已解除，结束前不自动重启"
                selected != null && !connected -> "时段已开始，应用识别权限未就绪，暂未管控"
                selected != null -> "正在执行已缓存的计划"
                else -> "当前自由时段"
            }
            if (plans.prefs.getString("execution_status", "") != status) plans.prefs.edit().putString("execution_status", status).apply()
            val boundary = if (plans.armed) decision.nextBoundary else null
            val trigger = boundary ?: 0L
            if (trigger != registeredAt || forceAlarm) {
                val alarm = context.getSystemService(AlarmManager::class.java)
                val pending = PendingIntent.getBroadcast(context, 70, Intent(context, ScheduleReceiver::class.java).setAction("PLAN_BOUNDARY"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                alarm.cancel(pending)
                if (boundary != null) {
                    try {
                        if (store.exact()) alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, boundary, pending)
                        else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, boundary, pending)
                    } catch (_: SecurityException) { alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, boundary, pending) }
                }
                registeredAt = trigger
            }
        } catch (_: Exception) {
            val store = LabStore(context)
            if (store.session()?.planned == true) store.stop("PLAN_RUNTIME_FAULT")
            PlanStore(context).prefs.edit().putString("execution_status", "计划执行异常，已放行").apply()
        } finally { busy = false }
    }
    fun prepare(context: Context) {
        val catalog = AppCatalog(context)
        val apps = catalog.apps()
        AppRules(context).refresh(apps)
        val managed = apps.filterNot { catalog.protected(it.packageName) }.map { it.packageName }.toSet()
        PlanStore(context).prefs.edit().putStringSet("prepared_managed", managed).commit()
    }
}
