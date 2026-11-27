package io.lifeos.focuslab

import android.content.Context
import androidx.work.*
import java.net.URL
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection

interface SyncAdapter {
    fun fetchPlan(): String
    fun uploadSummary(body: String)
    fun uploadIntervals(body: String)
    fun deleteUsage()
}
class ExchangeClient(private val credentials: SyncCredentials) : SyncAdapter {
    override fun fetchPlan() = request("/v1/plan")
    override fun uploadSummary(body: String) { request("/v1/usage", body) }
    override fun uploadIntervals(body: String) { request("/v1/intervals", body) }
    override fun deleteUsage() { request("/v1/usage", delete = true) }
    fun request(path: String, body: String? = null, delete: Boolean = false): String {
        require(path in setOf("/v1/plan", "/v1/usage", "/v1/intervals"))
        val connection = URL(credentials.endpoint + path).openConnection() as HttpsURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 8000; connection.readTimeout = 15000
            connection.setRequestProperty("Authorization", "Bearer ${credentials.token}")
            connection.setRequestProperty("Accept", "application/json")
            if (delete) { require(path == "/v1/usage" && body == null); connection.requestMethod = "DELETE" }
            if (body != null) {
                val bytes = body.toByteArray(Charsets.UTF_8)
                require(bytes.size <= ScheduleCodec.MAX_BYTES)
                connection.requestMethod = "PUT"; connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }
            val status = connection.responseCode
            require(status == 200) { if (status in setOf(401, 403)) "AUTH_REQUIRED" else "HTTP_FAILED" }
            return connection.inputStream.use { stream ->
                val bytes = stream.readNBytesCompat(ScheduleCodec.MAX_BYTES + 1)
                require(bytes.size <= ScheduleCodec.MAX_BYTES)
                String(bytes, Charsets.UTF_8)
            }
        } finally { connection.disconnect() }
    }
}
private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (output.size() < limit) { val n = read(buffer, 0, minOf(buffer.size, limit - output.size())); if (n < 0) break; output.write(buffer, 0, n) }
    return output.toByteArray()
}
class SyncWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    companion object {
        private val lock = Any()
        fun deleteRemote(credentials: SyncCredentials) = synchronized(lock) { ExchangeClient(credentials).deleteUsage() }
    }
    override fun doWork(): Result = synchronized(lock) { perform() }
    private fun perform(): Result {
        val context = applicationContext
        val plans = PlanStore(context)
        try {
            runCatching { UsageCollector.collect(context) }.onFailure {
                plans.prefs.edit().putString("usage_status", "统计暂不可用，覆盖中断；计划同步仍继续").apply()
            }
            val saved = SyncConfig(context).read() ?: return Result.success()
            val credentials = GoogleAuthorization.refreshed(context, saved)
            val google = if (GoogleAuthorization.isGoogle(credentials)) GoogleSheetsAdapter(context, credentials) else null
            val client: SyncAdapter = google ?: ExchangeClient(credentials)
            val raw = client.fetchPlan()
            if (isStopped) return Result.success()
            val changed = plans.accept(raw, credentials)
            if (changed || plans.prefs.getStringSet("prepared_managed", null) == null) PlanRuntime.prepare(context)
            plans.prefs.edit().putLong("last_sync", System.currentTimeMillis()).putString("sync_status", "已同步").commit()
            PlanRuntime.reconcile(context, true)
            if (plans.prefs.getBoolean("usage_enabled", false) && plans.prefs.getBoolean("usage_upload", false)) {
                runCatching { UsageCollector.upload(context, client) }.onFailure {
                    plans.prefs.edit().putString("usage_upload_status", "统计上传失败，保留本机数据；计划已同步").apply()
                }
            }
            runCatching { google?.syncState() }
            return Result.success()
        } catch (error: Exception) {
            if (error.message == "SOURCE_CHANGED" || isStopped) return Result.success()
            if (error.message == "UNPUBLISHED") {
                plans.prefs.edit().putLong("last_sync", System.currentTimeMillis()).putString("sync_status", "已连通，计划尚未发布；当前放行").commit()
                runCatching { SyncConfig(context).read()?.let { GoogleSheetsAdapter(context, it).syncState("UNPUBLISHED") } }
                PlanRuntime.reconcile(context)
                return Result.success()
            }
            val status = when {
                error.message == "AUTH_REQUIRED" -> "访问授权失效，保留未过期缓存"
                error.message == "REVISION_CONFLICT" -> "版本倒退或同版本内容冲突，拒绝替换缓存"
                error.message == "SNAPSHOT_EXPIRED" -> "收到的计划已过期，保留未过期缓存"
                error.message == "GOOGLE_API_DISABLED" -> "Google项目中的必需API尚未启用"
                error.message == "FILE_NOT_AUTHORIZED" -> "未获得这份Google表的访问权，请重新选择交换表"
                error.message == "WORKBOOK_SCHEMA" -> "所选文件不符合Focus Exchange协议，未执行计划"
                error is org.json.JSONException || error is java.time.DateTimeException || (error is IllegalArgumentException && error.message != "HTTP_FAILED") -> "计划格式或边界无效，拒绝替换缓存"
                else -> "同步失败，保留未过期缓存；请检查网络或授权"
            }
            plans.prefs.edit().putString("sync_status", status).apply()
            PlanRuntime.reconcile(context)
            return if (error.message in setOf("AUTH_REQUIRED", "FILE_NOT_AUTHORIZED", "GOOGLE_API_DISABLED", "WORKBOOK_SCHEMA", "DIFFERENT_DEVICE")) Result.failure()
                else if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
object SyncWork {
    private var lastRequest = 0L
    fun schedule(context: Context) {
        val work = WorkManager.getInstance(context)
        if (SyncConfig(context).read() != null || PlanStore(context).prefs.getBoolean("usage_enabled", false)) {
            // Collection runs offline too; failed network reads preserve the cache.
            work.enqueueUniquePeriodicWork("exchange-periodic", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).build())
        } else work.cancelUniqueWork("exchange-periodic")
    }
    fun request(context: Context, force: Boolean = false) {
        schedule(context)
        val now = android.os.SystemClock.elapsedRealtime()
        if (!force && lastRequest > 0 && now - lastRequest < 30_000) return
        lastRequest = now
        WorkManager.getInstance(context).enqueueUniqueWork("exchange-now", if (force) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<SyncWorker>().build())
    }
}
