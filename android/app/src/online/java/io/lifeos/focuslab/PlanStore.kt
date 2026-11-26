package io.lifeos.focuslab

import android.content.Context
import android.util.AtomicFile
import java.io.File

class PlanStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "schedule-v1.json"))
    val prefs = context.getSharedPreferences("online", Context.MODE_PRIVATE)
    companion object {
        private val lock = Any()
        private var cachedPath = ""
        private var cachedStamp = -1L
        private var cachedPlan: ScheduleSnapshot? = null
    }
    fun load(): ScheduleSnapshot? = synchronized(lock) {
        val stamp = file.baseFile.lastModified()
        if (cachedPath != file.baseFile.path || cachedStamp != stamp) {
            cachedPlan = runCatching { file.openRead().use { ScheduleCodec.decode(it.readBytes().toString(Charsets.UTF_8)) } }.getOrNull()
            cachedPath = file.baseFile.path; cachedStamp = stamp
        }
        cachedPlan
    }
    fun accept(raw: String): Boolean = synchronized(lock) {
        val next = ScheduleCodec.decode(raw)
        require(next.validUntil > System.currentTimeMillis()) { "SNAPSHOT_EXPIRED" }
        val old = load()
        require(ScheduleEngine.accepts(old, next)) { "REVISION_CONFLICT" }
        if (next == old) return@synchronized false
        val output = file.startWrite()
        try { output.write(ScheduleCodec.encode(next).toByteArray()); file.finishWrite(output) }
        catch (error: Exception) { file.failWrite(output); throw error }
        cachedPath = file.baseFile.path; cachedStamp = file.baseFile.lastModified(); cachedPlan = next
        true
    }
    var armed: Boolean
        get() = prefs.getBoolean("armed", false)
        set(value) { prefs.edit().putBoolean("armed", value).commit() }
}
