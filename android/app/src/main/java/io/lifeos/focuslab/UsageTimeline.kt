package io.lifeos.focuslab

enum class UsageKind { RESUME, PAUSE, SCREEN_ON, SCREEN_OFF, UNLOCK, LOCK, BOOT, SHUTDOWN }
data class UsagePoint(val at: Long, val kind: UsageKind, val app: String? = null)
data class UsageSlice(val start: Long, val end: Long, val app: String?, val interactive: Boolean?, val unlocked: Boolean?)
data class UsageSummary(val slices: List<UsageSlice>, val interactiveMs: Long, val unlockedMs: Long, val unlockCount: Int,
    val screenUnknownMs: Long, val lockUnknownMs: Long, val foregroundUnknownMs: Long, val byApp: Map<String, Long>)

/** Most-recent resumed app estimate, with explicit unknown coverage. No overlapping total. */
object UsageTimeline {
    fun summarize(points: List<UsagePoint>, from: Long, until: Long): UsageSummary {
        require(until >= from)
        var interactive: Boolean? = null
        var locked: Boolean? = null
        var app: String? = null
        var cursor = from
        var unlockCount = 0
        val slices = mutableListOf<UsageSlice>()
        fun close(at: Long) {
            val end = at.coerceIn(from, until)
            if (end > cursor) slices += UsageSlice(cursor, end, if (interactive == true && locked != true) app else null, interactive,
                if (interactive == false) false else if (interactive == true && locked != null) !locked!! else null)
            cursor = end
        }
        points.distinct().sortedBy { it.at }.filter { it.at <= until }.forEach { event ->
            if (event.at >= from) close(event.at)
            when (event.kind) {
                UsageKind.RESUME -> app = event.app
                UsageKind.PAUSE -> if (app == event.app) app = null
                UsageKind.SCREEN_ON -> interactive = true
                UsageKind.SCREEN_OFF -> { interactive = false; app = null }
                UsageKind.LOCK -> { locked = true; app = null }
                UsageKind.UNLOCK -> { if (locked == true && event.at >= from && event.at < until) unlockCount++; locked = false }
                UsageKind.BOOT, UsageKind.SHUTDOWN -> { interactive = null; locked = null; app = null }
            }
        }
        close(until)
        fun duration(predicate: (UsageSlice) -> Boolean) = slices.filter(predicate).sumOf { it.end - it.start }
        val byApp = slices.filter { it.app != null }.groupBy { it.app!! }.mapValues { (_, spans) -> spans.sumOf { it.end - it.start } }
        return UsageSummary(slices, duration { it.interactive == true }, duration { it.unlocked == true }, unlockCount,
            duration { it.interactive == null }, duration { it.unlocked == null }, duration { it.interactive == true && it.app == null }, byApp)
    }
}
