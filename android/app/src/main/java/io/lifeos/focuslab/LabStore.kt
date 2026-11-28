package io.lifeos.focuslab

import android.app.*
import android.content.*
import android.os.*
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject

class LabStore(val context: Context) {
    private val prefs = context.getSharedPreferences("lab", Context.MODE_PRIVATE)
    val isWhitelistSession: Boolean get() = prefs.getString("policy", "LAB") == "WHITELIST" && session() != null
    var whitelist: Set<String>
        get() = prefs.getStringSet("whitelist", emptySet())!!.toSet()
        set(value) {
            AppRules(context).replaceEffectiveSet(value)
        }
    val managedApps: Set<String> get() = prefs.getStringSet("managed_apps", emptySet())!!.toSet()
    val blockedApps: Set<String> get() = (if (isWhitelistSession) managedApps - whitelist else targets).filterNot { IntegrationHooks.allows(context, it) }.toSet()
    fun remainingMillis(): Long = session()?.let { if (it.manual) it.elapsedEnd - SystemClock.elapsedRealtime() else it.end - System.currentTimeMillis() }?.coerceAtLeast(0) ?: 0
    fun startWhitelist(duration: Long, managedSnapshot: Set<String>? = null, appSnapshot: List<LabApp>? = null) {
        require(duration in 1000..FocusPolicy.MAX_DURATION)
        require(FocusAccessibilityService.live != null) { "请先开启应用识别服务" }
        require(usageGranted(context)) { "请先允许前台应用识别" }
        val catalog = AppCatalog(context)
        val apps = appSnapshot ?: catalog.apps()
        val managed = managedSnapshot ?: apps.filterNot { catalog.protected(it.packageName) }.map { it.packageName }.toSet()
        if (session() != null) stop("WHITELIST_REPLACED")
        AppRules(context).refresh(apps)
        start(System.currentTimeMillis(), duration, true, "WHITELIST")
        check(prefs.edit().putStringSet("managed_apps", managed).commit())
        ForegroundProbe.enableForFocus(remainingMillis())
        log("WHITELIST_STARTED")
    }
    fun applyPlannedTask(task: PlannedTask, managed: Set<String>) {
        require(task.mode == "focus" && task.end > System.currentTimeMillis())
        require(task.end - task.start in 1..ScheduleEngine.MAX_TASK)
        stop("PLAN_REVISED")
        val json = JSONObject().put("start", task.start).put("end", task.end).put("manual", false)
            .put("elapsedEnd", 0).put("boot", boot()).put("duration", task.end - task.start).put("planned", true)
        check(prefs.edit().putString("session", json.toString()).putString("policy", "WHITELIST").putStringSet("managed_apps", managed).commit())
        reschedule(); notice(); log("PLAN_TASK_ACTIVE")
    }
    var usageAssistOptIn: Boolean
        get() = prefs.getBoolean("usage_assist_opt_in", false)
        set(value) { prefs.edit().putBoolean("usage_assist_opt_in", value).apply() }
    var overlayStatus: String
        get() = prefs.getString("overlay_status", "尚未进行覆盖层测试") ?: "尚未进行覆盖层测试"
        set(value) { prefs.edit().putString("overlay_status", value).apply() }
    var targets: Set<String>
        get() = prefs.getStringSet("targets", emptySet())!!.toSet()
        set(value) { stop("TARGETS_CHANGED"); prefs.edit().putStringSet("targets", value.toSet()).commit() }
    var mode: String
        get() = prefs.getString("mode", "HOME") ?: "HOME"
        set(value) { stop("MODE_CHANGED"); prefs.edit().putString("mode", value).commit() }
    fun boot(): Int = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
    fun session(): Session? = try {
        prefs.getString("session", null)?.let { raw ->
            require(prefs.getString("policy", "LAB") in setOf("LAB", "WHITELIST"))
            val j = JSONObject(raw)
            Session(j.getLong("start"), j.getLong("end"), j.getBoolean("manual"), j.getLong("elapsedEnd"), j.getInt("boot"), j.getLong("duration"), j.optBoolean("planned", false))
        }
    } catch (_: Exception) { stop("CONFIG_INVALID"); null }
    fun phase(): Phase {
        val current = session()
        val result = FocusPolicy.phase(current, System.currentTimeMillis(), SystemClock.elapsedRealtime(), boot())
        if (result == Phase.FAULT) stop("STATE_INVALID")
        if (current != null && result == Phase.IDLE) stop("SESSION_EXPIRED")
        return result
    }
    fun start(start: Long, duration: Long, manual: Boolean, policy: String = "LAB") {
        require(duration in 1..FocusPolicy.MAX_DURATION && start > 0)
        require(policy == "LAB" || policy == "WHITELIST")
        stop("REPLACED")
        overlayStatus = "本次测试尚未显示覆盖层"
        val s = Session(start, start + duration, manual, if (manual) SystemClock.elapsedRealtime() + duration else 0, boot(), duration)
        val j = JSONObject().put("start", s.start).put("end", s.end).put("manual", s.manual)
            .put("elapsedEnd", s.elapsedEnd).put("boot", s.boot).put("duration", s.duration)
        check(prefs.edit().putString("session", j.toString()).putString("policy", policy).commit())
        log("SESSION_SAVED")
        reschedule()
        notice()
    }
    fun stop(code: String = "USER_STOP") {
        val wasPlanned = try { JSONObject(prefs.getString("session", "{}") ?: "{}").optBoolean("planned", false) } catch (_: Exception) { false }
        IntegrationHooks.stopping(context, code, wasPlanned)
        ForegroundProbe.enabledUntilElapsed = 0L
        // Armed automatic execution observes one current package across task boundaries.
        // Resetting here during PLAN_REVISED would lose evidence for an app opened long ago.
        if (!IntegrationHooks.watchesForeground(context)) ForegroundProbe.resetCurrent()
        prefs.edit().remove("session").remove("policy").commit()
        val alarms = context.getSystemService(AlarmManager::class.java)
        alarms.cancel(pending(1)); alarms.cancel(pending(2))
        context.getSystemService(NotificationManager::class.java).cancel(1)
        val clearUi = Runnable { FocusAccessibilityService.live?.clearOverlay(); MainActivity.experimentsRef.get()?.stop() }
        if (Looper.myLooper() == Looper.getMainLooper()) clearUi.run() else Handler(Looper.getMainLooper()).post(clearUi)
        context.stopService(Intent(context, ShortTestService::class.java))
        log(code)
    }
    private fun pending(id: Int): PendingIntent = PendingIntent.getBroadcast(context, id,
        Intent(context, ScheduleReceiver::class.java).setAction(if (id == 1) "START" else "END"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun exact(): Boolean = Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    fun reschedule() {
        val s = session() ?: return
        val a = context.getSystemService(AlarmManager::class.java)
        a.cancel(pending(1)); a.cancel(pending(2))
        val p = phase()
        if (p == Phase.IDLE || p == Phase.FAULT) { stop("EXPIRED_OR_INVALID"); return }
        if (isWhitelistSession && usageGranted(context)) ForegroundProbe.enableForFocus(remainingMillis())
        fun schedule(id: Int, at: Long) {
            val type = if (s.manual) AlarmManager.ELAPSED_REALTIME_WAKEUP else AlarmManager.RTC_WAKEUP
            try {
                if (exact()) a.setExactAndAllowWhileIdle(type, at, pending(id))
                else { a.setAndAllowWhileIdle(type, at, pending(id)); log("ALARM_INEXACT") }
            } catch (_: SecurityException) { a.setAndAllowWhileIdle(type, at, pending(id)); log("ALARM_PERMISSION_LOST") }
        }
        if (p == Phase.SCHEDULED) schedule(1, s.start)
        schedule(2, if (s.manual) s.elapsedEnd else s.end)
    }
    @Synchronized fun log(code: String, deltaMs: Long? = null) {
        // Codes are fixed by source; never pass exceptions, package names or UI content.
        val events = try { JSONArray(prefs.getString("events", "[]")) } catch (_: Exception) { JSONArray() }
        val next = JSONArray()
        for (i in maxOf(0, events.length() - 99) until events.length()) next.put(events.getJSONObject(i))
        next.put(JSONObject().put("at", System.currentTimeMillis()).put("code", code).apply { if (deltaMs != null) put("delta_ms", deltaMs) })
        prefs.edit().putString("events", next.toString()).apply()
    }
    fun clearLogs() { prefs.edit().remove("events").commit() }
    fun report(): String = JSONObject().put("version", BuildConfig.VERSION_NAME)
        .put("manual_category_count", AppRules(context).userCategories.size)
        .put("app_exception_count", AppRules(context).appOverrides.size)
        .put("allowed_category_count", AppRules(context).allowedCategories.size)
        .put("whitelist_session", isWhitelistSession).put("whitelist_count", whitelist.size)
        .put("managed_count", managedApps.size).put("remaining_ms", remainingMillis())
        .put("api", Build.VERSION.SDK_INT).put("manufacturer", Build.MANUFACTURER)
        .put("android_release", Build.VERSION.RELEASE).put("mode", if (isWhitelistSession) "WHITELIST" else mode)
        .put("target_aliases", JSONArray(targets.indices.map { "TEST_APP_${it + 1}" }))
        .put("exact_alarm", exact()).put("service_connected", FocusAccessibilityService.live != null)
        .put("notification_enabled", context.getSystemService(NotificationManager::class.java).areNotificationsEnabled())
        .put("ordinary_overlay_allowed", Settings.canDrawOverlays(context))
        .put("battery_optimization_exempt", context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName))
        .put("foreground_service_running", ShortTestService.running)
        .put("overlay_status", overlayStatus)
        .put("usage_assist_test_active", SystemClock.elapsedRealtime() < ForegroundProbe.enabledUntilElapsed)
        .put("usage_assist_opt_in", usageAssistOptIn)
        .put("capability_snapshot_at", System.currentTimeMillis())
        .put("phase", phase().name).put("events", JSONArray(prefs.getString("events", "[]") ?: "[]"))
        .put("manual_results", JSONObject(prefs.getString("results", "{}") ?: "{}"))
        .put("magicos_verdict", "NOT_RUN_UNTIL_MANUALLY_TESTED").toString(2)
    fun result(id: String, result: String) {
        val j = JSONObject(prefs.getString("results", "{}") ?: "{}"); j.put(id, result)
        prefs.edit().putString("results", j.toString()).apply()
    }
    fun notice() {
        val n = context.getSystemService(NotificationManager::class.java)
        n.createNotificationChannel(NotificationChannel("lab", "专注测试及停止入口", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getBroadcast(context, 3, Intent(context, ScheduleReceiver::class.java).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE)
        try { n.notify(1, Notification.Builder(context, "lab").setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(if (isWhitelistSession) "正在专注 · ${session()?.let { FocusPresentation.range(it.start, it.end) }.orEmpty()}" else "LifeOS 测试已配置")
            .setContentText(if (isWhitelistSession) "点此返回计时页，白名单应用可用" else "可随时打开应用或点击停止解除测试")
            .setContentIntent(open).apply { if (!isWhitelistSession) addAction(Notification.Action.Builder(null, "停止所有测试", stop).build()) }.build()) }
        catch (_: SecurityException) { log("NOTIFICATION_UNAVAILABLE") }
    }
}
