package io.lifeos.focuslab

import android.app.*
import android.content.*
import android.os.*
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject

class LabStore(val context: Context) {
    private val prefs = context.getSharedPreferences("lab", Context.MODE_PRIVATE)
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
            val j = JSONObject(raw)
            Session(j.getLong("start"), j.getLong("end"), j.getBoolean("manual"), j.getLong("elapsedEnd"), j.getInt("boot"), j.getLong("duration"))
        }
    } catch (_: Exception) { stop("CONFIG_INVALID"); null }
    fun phase(): Phase {
        val current = session()
        val result = FocusPolicy.phase(current, System.currentTimeMillis(), SystemClock.elapsedRealtime(), boot())
        if (result == Phase.FAULT) stop("STATE_INVALID")
        if (current != null && result == Phase.IDLE) stop("SESSION_EXPIRED")
        return result
    }
    fun start(start: Long, duration: Long, manual: Boolean) {
        require(duration in 1..FocusPolicy.MAX_DURATION && start > 0)
        stop("REPLACED")
        overlayStatus = "本次测试尚未显示覆盖层"
        val s = Session(start, start + duration, manual, if (manual) SystemClock.elapsedRealtime() + duration else 0, boot(), duration)
        val j = JSONObject().put("start", s.start).put("end", s.end).put("manual", s.manual)
            .put("elapsedEnd", s.elapsedEnd).put("boot", s.boot).put("duration", s.duration)
        check(prefs.edit().putString("session", j.toString()).commit())
        log("SESSION_SAVED")
        reschedule()
        notice()
    }
    fun stop(code: String = "USER_STOP") {
        prefs.edit().remove("session").commit()
        val alarms = context.getSystemService(AlarmManager::class.java)
        alarms.cancel(pending(1)); alarms.cancel(pending(2))
        context.getSystemService(NotificationManager::class.java).cancel(1)
        FocusAccessibilityService.live?.clearOverlay()
        MainActivity.experimentsRef.get()?.stop()
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
    fun report(): String = JSONObject().put("version", "0.1.1-poc")
        .put("api", Build.VERSION.SDK_INT).put("manufacturer", Build.MANUFACTURER)
        .put("android_release", Build.VERSION.RELEASE).put("mode", mode)
        .put("target_aliases", JSONArray(targets.indices.map { "TEST_APP_${it + 1}" }))
        .put("exact_alarm", exact()).put("service_connected", FocusAccessibilityService.live != null)
        .put("notification_enabled", context.getSystemService(NotificationManager::class.java).areNotificationsEnabled())
        .put("ordinary_overlay_allowed", Settings.canDrawOverlays(context))
        .put("battery_optimization_exempt", context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName))
        .put("foreground_service_running", ShortTestService.running)
        .put("overlay_status", overlayStatus)
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
            .setContentTitle("LifeOS 测试已配置").setContentText("可随时打开应用或点击停止解除测试")
            .setContentIntent(open).addAction(Notification.Action.Builder(null, "停止所有测试", stop).build()).build()) }
        catch (_: SecurityException) { log("NOTIFICATION_UNAVAILABLE") }
    }
}
