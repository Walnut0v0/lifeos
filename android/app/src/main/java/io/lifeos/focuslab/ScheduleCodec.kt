package io.lifeos.focuslab

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime

object ScheduleCodec {
    const val MAX_BYTES = 262144
    private fun integer(json: JSONObject, key: String): Long {
        val value = json.get(key)
        require(value is Long || value is Int)
        return (value as Number).toLong()
    }
    private fun string(json: JSONObject, key: String): String = json.get(key).also { require(it is String) } as String
    private fun timestamp(value: String): Long = OffsetDateTime.parse(value).toInstant().toEpochMilli()
    fun decode(raw: String): ScheduleSnapshot {
        require(raw.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
        val json = JSONObject(raw)
        require(integer(json, "schema_version") == 1L)
        val array = json.getJSONArray("tasks")
        require(array.length() <= 512)
        return ScheduleSnapshot(integer(json, "revision"), timestamp(string(json, "generated_at")), string(json, "timezone"), timestamp(string(json, "valid_until")),
            (0 until array.length()).map { i -> array.getJSONObject(i).let {
                PlannedTask(string(it, "task_id"), if (it.has("title")) string(it, "title") else "", timestamp(string(it, "start_at")), timestamp(string(it, "end_at")), string(it, "focus_mode"))
            } }.sortedBy { it.start }).also { ScheduleEngine.validate(it) }
    }
    fun encode(plan: ScheduleSnapshot): String = JSONObject().put("schema_version", 1).put("revision", plan.revision)
        .put("generated_at", Instant.ofEpochMilli(plan.generatedAt).toString()).put("timezone", plan.timezone)
        .put("valid_until", Instant.ofEpochMilli(plan.validUntil).toString()).put("tasks", JSONArray(plan.tasks.map {
            JSONObject().put("task_id", it.id).put("title", it.title).put("start_at", Instant.ofEpochMilli(it.start).toString())
                .put("end_at", Instant.ofEpochMilli(it.end).toString()).put("focus_mode", it.mode)
        })).toString()
}
