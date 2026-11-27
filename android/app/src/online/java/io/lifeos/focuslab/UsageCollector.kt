package io.lifeos.focuslab

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

object UsageCollector {
    private val lock = Any()
    private fun directory(context: Context) = File(context.filesDir, "usage").apply { mkdirs() }
    private fun kind(type: Int): UsageKind? = when (type) {
        UsageEvents.Event.ACTIVITY_RESUMED -> UsageKind.RESUME
        UsageEvents.Event.ACTIVITY_PAUSED -> UsageKind.PAUSE
        UsageEvents.Event.SCREEN_INTERACTIVE -> UsageKind.SCREEN_ON
        UsageEvents.Event.SCREEN_NON_INTERACTIVE -> UsageKind.SCREEN_OFF
        UsageEvents.Event.KEYGUARD_HIDDEN -> UsageKind.UNLOCK
        UsageEvents.Event.KEYGUARD_SHOWN -> UsageKind.LOCK
        UsageEvents.Event.DEVICE_STARTUP -> UsageKind.BOOT
        UsageEvents.Event.DEVICE_SHUTDOWN -> UsageKind.SHUTDOWN
        else -> null
    }
    fun collect(context: Context) = synchronized(lock) {
        val prefs = PlanStore(context).prefs
        if (!prefs.getBoolean("usage_enabled", false)) return@synchronized
        if (!usageGranted(context)) { prefs.edit().putString("usage_status", "使用情况权限不可用，统计覆盖中断").apply(); return@synchronized }
        val now = System.currentTimeMillis()
        val enabledAt = prefs.getLong("usage_since", now)
        if (enabledAt > now) { prefs.edit().putString("usage_status", "系统时间早于启用时间，暂不推算").apply(); return@synchronized }
        val google = GoogleAuthorization.isGoogle(SyncConfig(context).read())
        val zone = if (google) ZoneId.of(FocusExchangeV1.REPORT_ZONE) else ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val dayStart = today.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli().coerceAtLeast(enabledAt)
        // Recompute today/yesterday from system metadata for idempotence, including a warmup.
        val queryStart = (dayStart - 24 * 60 * 60_000L).coerceAtLeast(enabledAt)
        val events = context.getSystemService(UsageStatsManager::class.java).queryEvents(queryStart, now)
        val points = mutableListOf<UsagePoint>()
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            kind(event.eventType)?.let { kind -> points += UsagePoint(event.timeStamp, kind, if (kind in setOf(UsageKind.RESUME, UsageKind.PAUSE)) event.packageName else null) }
            require(points.size <= 100_000)
        }
        val rules = AppRules(context).knownCategories
        val alias = prefs.getString("device_alias", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("device_alias", it).commit() }
        for (date in listOf(today.minusDays(1), today)) {
            val start = date.atStartOfDay(zone).toInstant().toEpochMilli().coerceAtLeast(enabledAt)
            val end = minOf(date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), now)
            if (end <= start) continue
            val summary = UsageTimeline.summarize(points, start, end)
            val byCategory = summary.byApp.entries.groupBy { rules[it.key]?.name ?: "UNKNOWN" }.mapValues { (_, entries) -> entries.sumOf { it.value } }
            val hourly = mutableMapOf<String, Long>()
            summary.slices.filter { it.app != null }.forEach { span ->
                var cursor = span.start
                while (cursor < span.end) {
                    val hour = Instant.ofEpochMilli(cursor).atZone(zone).withMinute(0).withSecond(0).withNano(0)
                    val next = minOf(span.end, hour.plusHours(1).toInstant().toEpochMilli())
                    val key = hour.toOffsetDateTime().toString()
                    hourly[key] = (hourly[key] ?: 0) + next - cursor
                    cursor = next
                }
            }
            val json = JSONObject().put("schema_version", 1).put("date", date.toString()).put("timezone", zone.id).put("device_alias", alias)
                .put("coverage_start", Instant.ofEpochMilli(start).toString()).put("coverage_end", Instant.ofEpochMilli(end).toString())
                .put("query_start", Instant.ofEpochMilli(queryStart).toString()).put("screen_interactive_ms", summary.interactiveMs)
                .put("unlocked_interactive_ms", summary.unlockedMs).put("unlock_count", summary.unlockCount)
                .put("usage_by_app", JSONObject(summary.byApp)).put("usage_by_category", JSONObject(byCategory)).put("hourly_usage_ms", JSONObject(hourly))
                .put("screen_state_unknown_ms", summary.screenUnknownMs).put("lock_state_unknown_ms", summary.lockUnknownMs)
                .put("foreground_unknown_ms", summary.foregroundUnknownMs).put("quality", "most_recent_resumed_estimate; multiwindow_not_measured; current_categories")
                .put("actual_enforced_ms", JSONObject.NULL).put("updated_at", Instant.ofEpochMilli(now).toString())
            json.put("unlock_count_known", points.any { it.kind == UsageKind.LOCK || it.kind == UsageKind.UNLOCK })
                .put("is_final", date < today)
            val missing = JSONArray(summary.slices.filter { it.interactive == null || it.unlocked == null }.map {
                JSONObject().put("start_at", Instant.ofEpochMilli(it.start).toString()).put("end_at", Instant.ofEpochMilli(it.end).toString())
                    .put("reason", if (it.interactive == null) "SCREEN_STATE_UNKNOWN" else "LOCK_STATE_UNKNOWN")
            })
            json.put("missing_intervals", missing)
            val plan = PlanStore(context).load()
            val matched = mutableMapOf<String, Long>()
            val restricted = mutableMapOf<String, Long>()
            val managed = prefs.getStringSet("prepared_managed", emptySet())!!.toSet()
            val allowed = LabStore(context).whitelist
            plan?.tasks?.filter { it.mode == "focus" }?.forEach { task ->
                matched[task.id] = summary.slices.filter { it.app != null }.sumOf { (minOf(it.end, task.end, plan.validUntil) - maxOf(it.start, task.start)).coerceAtLeast(0) }
                restricted[task.id] = summary.slices.filter { it.app in managed && it.app !in allowed }.sumOf { (minOf(it.end, task.end, plan.validUntil) - maxOf(it.start, task.start)).coerceAtLeast(0) }
            }
            json.put("planned_task_phone_usage_ms", JSONObject(matched))
                .put("planned_non_allowed_estimate_ms", JSONObject(restricted)).put("matched_plan_revision", plan?.revision ?: 0)
                .put("plan_match_quality", "latest_cached_plan_and_current_rules; not_actual_enforcement")
            if (prefs.getBoolean("usage_details", false)) json.put("intervals", JSONArray(summary.slices.filter { it.app != null }.map { span ->
                JSONObject().put("event_id", "${alias}:$date:${span.start}:${span.app}").put("app_package", span.app)
                    .put("category", rules[span.app]?.name ?: "UNKNOWN").put("start_at", Instant.ofEpochMilli(span.start).toString())
                    .put("end_at", Instant.ofEpochMilli(span.end).toString()).put("duration_ms", span.end - span.start).put("quality", "foreground_estimate")
            }))
            val file = AtomicFile(File(directory(context), "$date-${zone.id.hashCode()}.json"))
            val output = file.startWrite()
            try { output.write(json.toString().toByteArray()); file.finishWrite(output) }
            catch (error: Exception) { file.failWrite(output); throw error }
        }
        val cutoff = today.minusDays((prefs.getInt("usage_retention", 7).coerceIn(1, 30) - 1).toLong())
        if (!google || prefs.getBoolean("usage_prune_enabled", false)) directory(context).listFiles()?.filter { it.extension == "json" && it.name.take(10) < cutoff.toString() }?.forEach { it.delete() }
        prefs.edit().putLong("last_collection", now).putString("usage_status", "已更新；未知区间单独列出，前台不等于注意力").apply()
    }
    fun reports(context: Context): List<JSONObject> = synchronized(lock) {
        directory(context).listFiles()?.filter { it.extension == "json" }?.sortedByDescending { it.name }?.mapNotNull {
            runCatching { JSONObject(it.readText()) }.getOrNull()
        } ?: emptyList()
    }
    fun upload(context: Context, client: SyncAdapter) {
        val prefs = PlanStore(context).prefs
        val details = prefs.getBoolean("usage_details", false) && prefs.getBoolean("usage_details_upload", false)
        reports(context).forEach { report ->
            if (client is GoogleSheetsAdapter && report.optString("timezone") != FocusExchangeV1.REPORT_ZONE) return@forEach
            if (!prefs.getBoolean("usage_upload", false) || !prefs.getBoolean("usage_enabled", false)) return
            val intervals = report.optJSONArray("intervals")
            report.remove("intervals")
            report.put("retention_days", prefs.getInt("usage_retention", 7).coerceIn(1, 30))
            report.put("has_details", details && intervals != null)
            client.uploadSummary(report.toString())
            if (details && intervals != null) {
                val digest = java.security.MessageDigest.getInstance("SHA-256").digest(intervals.toString().toByteArray()).joinToString("") { "%02x".format(it) }
                val count = maxOf(1, (intervals.length() + 199) / 200)
                for (index in 0 until count) {
                    if (!prefs.getBoolean("usage_upload", false) || !prefs.getBoolean("usage_details_upload", false) || !prefs.getBoolean("usage_details", false)) return
                    val batch = JSONObject().put("schema_version", 1).put("date", report.getString("date"))
                        .put("timezone", report.getString("timezone")).put("device_alias", report.getString("device_alias"))
                        .put("batch_id", digest).put("chunk_index", index).put("total_chunks", count)
                        .put("intervals", JSONArray((index * 200 until minOf(intervals.length(), (index + 1) * 200)).map { intervals.getJSONObject(it) }))
                    client.uploadIntervals(batch.toString())
                }
            }
        }
        prefs.edit().putLong("last_usage_upload", System.currentTimeMillis()).putString("usage_upload_status", "统计已上传").apply()
    }
    fun clear(context: Context) = synchronized(lock) {
        PlanStore(context).prefs.edit().putBoolean("usage_enabled", false).putBoolean("usage_upload", false)
            .putBoolean("usage_details", false).putBoolean("usage_details_upload", false).remove("usage_since").remove("last_collection").commit()
        directory(context).listFiles()?.forEach { it.delete() }
    }
}
