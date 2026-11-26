package io.lifeos.focuslab

import java.time.ZoneId

data class PlannedTask(val id: String, val title: String, val start: Long, val end: Long, val mode: String)
data class ScheduleSnapshot(val revision: Long, val generatedAt: Long, val timezone: String, val validUntil: Long, val tasks: List<PlannedTask>)
data class ScheduleDecision(val task: PlannedTask?, val nextBoundary: Long?, val expired: Boolean, val unknownMode: Boolean) {
    fun remaining(now: Long): Long = task?.let { (it.end - now).coerceAtLeast(0) } ?: 0
}
object ScheduleEngine {
    const val MAX_TASK = 24 * 60 * 60_000L
    const val MAX_HORIZON = 8 * MAX_TASK
    fun validate(plan: ScheduleSnapshot) {
        require(plan.revision > 0 && plan.generatedAt > 0 && plan.validUntil > plan.generatedAt)
        require(plan.validUntil - plan.generatedAt <= MAX_HORIZON)
        ZoneId.of(plan.timezone)
        require(plan.tasks.size <= 512 && plan.tasks.map { it.id }.distinct().size == plan.tasks.size)
        var previousEnd = Long.MIN_VALUE
        plan.tasks.sortedBy { it.start }.forEach {
            require(it.id.matches(Regex("[A-Za-z0-9_.-]{1,80}")) && it.title.length <= 120 && it.mode.length in 1..40)
            require(it.start > 0 && it.end > it.start && it.end - it.start <= MAX_TASK)
            require(it.start >= plan.generatedAt - MAX_HORIZON && it.end <= plan.validUntil)
            require(it.start >= previousEnd) { "Overlapping tasks" }
            previousEnd = it.end
        }
    }
    fun resolve(plan: ScheduleSnapshot?, now: Long): ScheduleDecision {
        if (plan == null) return ScheduleDecision(null, null, false, false)
        if (now >= plan.validUntil || now < plan.generatedAt - 5 * 60_000) return ScheduleDecision(null, null, true, false)
        val current = plan.tasks.firstOrNull { now >= it.start && now < it.end }
        val unknown = current != null && current.mode !in setOf("focus", "free")
        val boundary = (plan.tasks.flatMap { listOf(it.start, it.end) } + plan.validUntil).filter { it > now }.minOrNull()
        return ScheduleDecision(current?.takeIf { it.mode == "focus" }, boundary, false, unknown)
    }
    fun accepts(previous: ScheduleSnapshot?, next: ScheduleSnapshot): Boolean {
        validate(next)
        return previous == null || next.revision > previous.revision || next == previous
    }
}
