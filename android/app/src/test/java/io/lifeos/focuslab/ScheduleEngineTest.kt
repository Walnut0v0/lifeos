package io.lifeos.focuslab

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class ScheduleEngineTest {
    private val now = Instant.parse("2026-11-26T15:59:00Z").toEpochMilli()
    private val task = PlannedTask("sample", "", now + 60_000, now + 180_000, "focus")
    private fun plan(tasks: List<PlannedTask> = listOf(task), revision: Long = 1) = ScheduleSnapshot(revision, now, "Asia/Manila", now + 2 * ScheduleEngine.MAX_TASK, tasks)
    @Test fun halfOpenCrossMidnightAndMissedAlarmsUseAbsoluteTime() {
        val p = plan(); ScheduleEngine.validate(p)
        assertNull(ScheduleEngine.resolve(p, task.start - 1).task)
        assertEquals(task, ScheduleEngine.resolve(p, task.start).task)
        assertEquals(1, ScheduleEngine.resolve(p, task.end - 1).remaining(task.end - 1))
        assertNull(ScheduleEngine.resolve(p, task.end).task)
        assertNull(ScheduleEngine.resolve(p, task.end + 60_000).task)
        assertEquals(task.end, ScheduleEngine.resolve(p, task.start + 30_000).nextBoundary)
    }
    @Test fun revisionsCancellationAndTimezoneAreDeterministic() {
        val p = plan()
        assertTrue(ScheduleEngine.accepts(p, p))
        assertFalse(ScheduleEngine.accepts(p, plan(revision = 1).copy(tasks = emptyList())))
        assertFalse(ScheduleEngine.accepts(plan(revision = 2), p))
        assertTrue(ScheduleEngine.accepts(p, plan(emptyList(), 2)))
        assertNull(ScheduleEngine.resolve(plan(emptyList(), 2), task.start).task)
        assertEquals(ScheduleEngine.resolve(p, task.start), ScheduleEngine.resolve(p.copy(timezone = "UTC"), task.start))
    }
    @Test fun twoPublishedTasksAndFreeGapNeverReplayMissedWork() {
        val second = task.copy(id = "second", start = task.end + 60_000, end = task.end + 120_000)
        val p = plan(listOf(task, second))
        assertEquals(task, ScheduleEngine.resolve(p, task.start).task)
        assertNull(ScheduleEngine.resolve(p, task.end).task)
        assertEquals(second.start, ScheduleEngine.resolve(p, task.end).nextBoundary)
        assertEquals(second, ScheduleEngine.resolve(p, second.start).task)
        assertNull(ScheduleEngine.resolve(p, second.end + 1).task)
    }
    @Test fun invalidAndUnknownModesFailOpen() {
        assertTrue(runCatching { ScheduleEngine.validate(plan(listOf(task, task.copy(id = "overlap")))) }.isFailure)
        assertTrue(runCatching { ScheduleEngine.validate(plan(listOf(task.copy(end = task.start)))) }.isFailure)
        assertTrue(runCatching { ScheduleEngine.validate(plan().copy(timezone = "Unknown/Zone")) }.isFailure)
        assertTrue(ScheduleEngine.resolve(plan(), now + 3 * ScheduleEngine.MAX_TASK).expired)
        val result = ScheduleEngine.resolve(plan(listOf(task.copy(mode = "unsupported"))), task.start)
        assertNull(result.task); assertTrue(result.unknownMode)
    }
    @Test fun codecRequiresOffsetAndRoundTripsWholeSnapshot() {
        val p = plan(); assertEquals(p, ScheduleCodec.decode(ScheduleCodec.encode(p)))
        assertTrue(runCatching { ScheduleCodec.decode(ScheduleCodec.encode(p).replace("2026-11-26T15:59:00Z", "2026-11-26T15:59:00")) }.isFailure)
        assertTrue(runCatching { ScheduleCodec.decode("x".repeat(ScheduleCodec.MAX_BYTES + 1)) }.isFailure)
        assertTrue(runCatching { ScheduleCodec.decode(ScheduleCodec.encode(p).replace("\"revision\":1", "\"revision\":1.5")) }.isFailure)
        assertTrue(runCatching { ScheduleCodec.decode(ScheduleCodec.encode(p).replace("\"revision\":1", "\"revision\":9223372036854775808")) }.isFailure)
        assertTrue(runCatching { ScheduleCodec.decode(ScheduleCodec.encode(p).replace("\"focus_mode\":\"focus\"", "\"focus_mode\":1")) }.isFailure)
    }
}
