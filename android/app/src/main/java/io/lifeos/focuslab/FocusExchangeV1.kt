package io.lifeos.focuslab

import org.json.JSONArray
import org.json.JSONObject

/** Public protocol only; never contains a real workbook ID or device configuration. */
object FocusExchangeV1 {
    const val REPORT_ZONE = "Asia/Singapore"
    const val MAX_CELL = 40000
    val headers = linkedMapOf(
        "Protocol" to listOf("rule_id", "规则 / 字段", "规范"),
        "Schedule" to listOf("snapshot_json"),
        "Focus Modes" to listOf("modes_json"),
        "App Catalog" to listOf("device_alias", "app_package", "app_label", "category_id", "category_source", "installed", "updated_at"),
        "Usage Daily" to listOf("device_alias", "date", "timezone", "coverage_start", "coverage_end", "screen_interactive_ms", "unlocked_interactive_ms", "unlock_count", "usage_by_app_json", "usage_by_category_json", "hourly_usage_json", "focus_summary_json", "missing_intervals_json", "quality", "is_final", "updated_at"),
        "Usage Timeline" to listOf("event_id", "device_alias", "date", "timezone", "app_package", "app_label", "category_id", "start_at", "end_at", "duration_ms", "task_id", "focus_mode", "policy_result", "quality", "updated_at"),
        "Sync State" to listOf("device_alias", "client_version", "last_poll_at", "last_success_at", "applied_revision", "active_task_id", "enforcement_state", "usage_permission", "usage_sync_at", "daily_sync_enabled", "timeline_sync_enabled", "catalog_sync_enabled", "status", "error_code", "updated_at"),
        "Change Requests" to listOf("request_id", "device_alias", "created_at", "task_id", "request_type", "requested_change", "base_revision", "status", "resolved_at", "published_revision", "response_note")
    )
    fun publishedPlan(raw: String): String? {
        require(raw.length <= MAX_CELL) { "CELL_TOO_LARGE" }
        val j = JSONObject(raw)
        require(j.get("schema_version") == 1 && j.getString("timezone") == REPORT_ZONE) { "INVALID_SNAPSHOT" }
        if (j.optString("state") == "unpublished" && j.optLong("revision", -1) == 0L) {
            require(j.getJSONArray("tasks").length() == 0 && j.isNull("generated_at") && j.isNull("valid_until"))
            return null
        }
        require(j.getString("state") == "published") { "INVALID_SNAPSHOT" }
        val plan = ScheduleCodec.decode(raw)
        require(plan.revision <= 9_007_199_254_740_991L)
        return ScheduleCodec.encode(plan)
    }
    fun category(category: AppCategory) = when (category) {
        AppCategory.STUDY -> "education"
        else -> category.name.lowercase(java.util.Locale.ROOT)
    }
    fun jsonCell(value: Any): String = value.toString().also { require(it.length <= MAX_CELL) { "CELL_TOO_LARGE" } }
    fun row(values: List<Any>): List<Any> = values.onEach { require(it !is String || it.length <= MAX_CELL) { "CELL_TOO_LARGE" } }
    fun sameRow(expected: List<Any>, actual: JSONArray): Boolean = expected.indices.all { i ->
        val a = actual.opt(i) ?: ""
        val e = expected[i]
        if (a is Number && e is Number) a.toDouble() == e.toDouble() else a == e
    }
}
