package io.lifeos.focuslab

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.util.UUID
import javax.net.ssl.HttpsURLConnection

/** drive.file only; reads Schedule, writes only phone-owned ranges using RAW. */
class GoogleSheetsAdapter(private val context: Context, private val credentials: SyncCredentials) : SyncAdapter {
    private data class Grid(val id: Int, var rows: Int)
    private val grids = mutableMapOf<String, Grid>()
    private val indexes = mutableMapOf<String, MutableMap<List<String>, Int>>()
    private val prefs = PlanStore(context).prefs
    private val alias = prefs.getString("device_alias", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("device_alias", it).commit() }
    private fun encode(text: String) = URLEncoder.encode(text, "UTF-8")
    private fun request(path: String, body: JSONObject? = null): JSONObject {
        require(GoogleAuthorization.isGoogle(credentials))
        val connection = URL(credentials.endpoint + path).openConnection() as HttpsURLConnection
        try {
            connection.instanceFollowRedirects = false; connection.connectTimeout = 8000; connection.readTimeout = 15000
            connection.setRequestProperty("Authorization", "Bearer ${credentials.token}")
            if (body != null) {
                val bytes = body.toString().toByteArray(); require(bytes.size <= 2_000_000)
                connection.requestMethod = "POST"; connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setFixedLengthStreamingMode(bytes.size); connection.outputStream.use { it.write(bytes) }
            }
            val status = connection.responseCode
            if (status != 200) {
                val text = connection.errorStream?.use { String(it.readBytes().take(65536).toByteArray()) }.orEmpty()
                error(when {
                    status == 401 -> "AUTH_REQUIRED"
                    status == 403 && (text.contains("SERVICE_DISABLED") || text.contains("accessNotConfigured")) -> "GOOGLE_API_DISABLED"
                    status == 403 || status == 404 -> "FILE_NOT_AUTHORIZED"
                    status == 429 -> "RATE_LIMITED"
                    else -> "GOOGLE_HTTP_FAILED"
                })
            }
            val output = java.io.ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8192)
                while (true) { val n = input.read(buffer); if (n < 0) break; require(output.size() + n <= 2_000_000); output.write(buffer, 0, n) }
            }
            return JSONObject(output.toString("UTF-8"))
        } finally { connection.disconnect() }
    }
    private fun read(range: String): JSONArray = request("/values/${encode(range)}?valueRenderOption=FORMULA").optJSONArray("values") ?: JSONArray()
    private fun cell(tab: String): String = read("'$tab'!A2").optJSONArray(0)?.optString(0).orEmpty().also { require(it.length <= FocusExchangeV1.MAX_CELL) { "CELL_TOO_LARGE" } }
    fun verify() {
        if (grids.isNotEmpty()) return
        val sheets = request("?fields=sheets(properties(sheetId,title,gridProperties))").getJSONArray("sheets")
        for (i in 0 until sheets.length()) {
            val p = sheets.getJSONObject(i).getJSONObject("properties")
            grids[p.getString("title")] = Grid(p.getInt("sheetId"), p.getJSONObject("gridProperties").getInt("rowCount"))
        }
        require(FocusExchangeV1.headers.keys.all { it in grids }) { "WORKBOOK_SCHEMA" }
        val ranges = FocusExchangeV1.headers.map { (tab, columns) -> "'$tab'!A1:${('A'.code + columns.size - 1).toChar()}1" }
        val results = request("/values:batchGet?valueRenderOption=FORMULA&" + ranges.joinToString("&") { "ranges=${encode(it)}" }).getJSONArray("valueRanges")
        FocusExchangeV1.headers.entries.forEachIndexed { i, (_, columns) ->
            val row = results.getJSONObject(i).optJSONArray("values")?.optJSONArray(0) ?: JSONArray()
            require(FocusExchangeV1.sameRow(columns, row)) { "WORKBOOK_SCHEMA" }
        }
    }
    private fun write(range: String, values: List<List<Any>>) {
        require(!range.startsWith("'Schedule'") && !range.startsWith("'Protocol'") && !range.startsWith("'Change Requests'"))
        values.forEach { FocusExchangeV1.row(it) }
        request("/values:batchUpdate", JSONObject().put("valueInputOption", "RAW").put("data", JSONArray().put(JSONObject().put("range", range).put("values", JSONArray(values.map { JSONArray(it) })))))
        val actual = read(range)
        require(values.indices.all { FocusExchangeV1.sameRow(values[it], actual.optJSONArray(it) ?: JSONArray()) }) { "WRITE_VERIFY_FAILED" }
    }
    private fun upsert(tab: String, row: List<Any>, keyColumns: Int) {
        verify()
        val grid = grids.getValue(tab)
        val index = indexes.getOrPut(tab) {
            val found = mutableMapOf<List<String>, Int>()
            require(grid.rows <= 20000) { "ROW_LIMIT" }
            var start = 2
            while (start <= grid.rows) {
                val data = read("'$tab'!A$start:${('A'.code + keyColumns - 1).toChar()}${minOf(grid.rows, start + 499)}")
                for (i in 0 until data.length()) {
                    val r = data.optJSONArray(i) ?: continue
                    val key = (0 until keyColumns).map { r.optString(it) }
                    if (key.all { it.isEmpty() }) continue
                    require(key.none { it.isEmpty() } && key !in found) { "DUPLICATE_ROW_KEY" }
                    found[key] = start + i
                }
                start += 500
            }
            found
        }
        val key = row.take(keyColumns).map { it.toString() }
        val position = index[key] ?: ((index.values.maxOrNull() ?: 1) + 1)
        if (position > grid.rows) {
            request(":batchUpdate", JSONObject().put("requests", JSONArray().put(JSONObject().put("appendDimension", JSONObject().put("sheetId", grid.id).put("dimension", "ROWS").put("length", 500)))))
            grid.rows += 500
        }
        write("'$tab'!A$position:${('A'.code + row.size - 1).toChar()}$position", listOf(row))
        index[key] = position
    }
    fun initialPlan(): Boolean { verify(); return FocusExchangeV1.publishedPlan(cell("Schedule")) == null }
    override fun fetchPlan(): String {
        verify(); publishMode()
        return FocusExchangeV1.publishedPlan(cell("Schedule")) ?: error("UNPUBLISHED")
    }
    fun publishMode() {
        verify()
        if (!prefs.getBoolean("modes_sync", false)) return
        val rules = AppRules(context)
        val mode = JSONObject().put("mode_id", "focus").put("name", "专注")
            .put("allowed_categories", JSONArray(rules.allowedCategories.map { FocusExchangeV1.category(it) }.sorted()))
            .put("allow_packages", JSONArray(rules.appOverrides.filterValues { it }.keys.sorted()))
            .put("deny_packages", JSONArray(rules.appOverrides.filterValues { !it }.keys.sorted()))
        val modes = JSONArray().put(mode)
        val old = JSONObject(cell("Focus Modes"))
        require(old.getInt("schema_version") == 1)
        require(old.isNull("device_alias") || old.getString("device_alias") == alias) { "DIFFERENT_DEVICE" }
        if (old.optString("device_alias") == alias && old.getJSONArray("modes").toString() == modes.toString()) return
        val revision = old.getLong("revision") + 1
        require(revision in 1..9_007_199_254_740_991L)
        val value = JSONObject().put("schema_version", 1).put("revision", revision).put("device_alias", alias).put("updated_at", Instant.now().toString()).put("modes", modes)
        write("'Focus Modes'!A2", listOf(listOf(FocusExchangeV1.jsonCell(value))))
    }
    fun syncState(code: String = "") {
        val plans = PlanStore(context)
        val task = ScheduleEngine.resolve(plans.load(), System.currentTimeMillis()).task
        val current = LabStore(context)
        val active = current.phase() == Phase.ACTIVE && FocusAccessibilityService.live != null
        fun at(key: String): String = prefs.getLong(key, 0).takeIf { it > 0 }?.let { Instant.ofEpochMilli(it).toString() }.orEmpty()
        val state = when { active -> "active"; plans.armed && task != null -> "degraded"; else -> "free" }
        val status = when { code == "UNPUBLISHED" -> "uninitialized"; code == "AUTH_REQUIRED" -> "auth_required"; code.isNotEmpty() -> "error"; else -> "ok" }
        val row = listOf(alias, BuildConfig.VERSION_NAME, Instant.now().toString(), at("last_sync"), plans.load()?.revision ?: 0L,
            task?.id ?: "", state, if (usageGranted(context)) "granted" else "denied", at("last_usage_upload"),
            prefs.getBoolean("usage_upload", false), false, false, status, code, Instant.now().toString())
        upsert("Sync State", row, 1)
    }
    override fun uploadSummary(body: String) {
        val report = JSONObject(body)
        val unknown = report.optLong("screen_state_unknown_ms") >= java.time.Duration.between(Instant.parse(report.getString("coverage_start")), Instant.parse(report.getString("coverage_end"))).toMillis()
        val apps = report.getJSONObject("usage_by_app")
        val categories = AppRules(context).knownCategories
        val appRows = apps.keys().asSequence().sorted().map { pkg -> JSONObject().put("app_package", pkg)
            .put("category_id", categories[pkg]?.let { FocusExchangeV1.category(it) } ?: "unknown").put("foreground_ms", apps.getLong(pkg)) }.toList()
        val categoryRows = appRows.groupBy { it.getString("category_id") }.map { (id, values) -> JSONObject().put("category_id", id).put("foreground_ms", values.sumOf { it.getLong("foreground_ms") }) }
        val focus = JSONObject().put("planned_ms", JSONObject.NULL).put("enforced_ms", JSONObject.NULL).put("restricted_foreground_ms", JSONObject.NULL)
            .put("block_attempts", JSONObject.NULL).put("early_exits", JSONObject.NULL).put("bypass_ms", JSONObject.NULL)
        val missing = report.optJSONArray("missing_intervals") ?: JSONArray().put(JSONObject().put("start_at", report.getString("coverage_start")).put("end_at", report.getString("coverage_end")).put("reason", "INITIAL_STATE_AND_POLICY_COVERAGE_NOT_CONFIRMED"))
        val row = listOf(alias, report.getString("date"), FocusExchangeV1.REPORT_ZONE, report.getString("coverage_start"), report.getString("coverage_end"),
            if (unknown) "" else report.optLong("screen_interactive_ms"), if (report.optLong("lock_state_unknown_ms") > 0) "" else report.optLong("unlocked_interactive_ms"),
            if (report.optBoolean("unlock_count_known", false)) report.optInt("unlock_count") else "", FocusExchangeV1.jsonCell(JSONArray(appRows)), FocusExchangeV1.jsonCell(JSONArray(categoryRows)),
            FocusExchangeV1.jsonCell(report.optJSONArray("hourly_screen_ms") ?: JSONArray(List(24) { JSONObject.NULL })), FocusExchangeV1.jsonCell(focus), FocusExchangeV1.jsonCell(missing), "partial",
            report.optBoolean("is_final", false), report.getString("updated_at"))
        require(report.getString("timezone") == FocusExchangeV1.REPORT_ZONE) { "REPORT_TIMEZONE" }
        upsert("Usage Daily", row, 2)
    }
    override fun uploadIntervals(body: String) { error("GOOGLE_TIMELINE_NOT_READY") }
    override fun deleteUsage() { error("GOOGLE_DELETE_REQUIRES_REVIEW") }
}
