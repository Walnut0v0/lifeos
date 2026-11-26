package io.lifeos.focuslab

import org.junit.Assert.*
import org.junit.Test

class UsageTimelineTest {
    @Test fun reorderedDuplicatesPauseAndScreenOffDoNotInflateUsage() {
        val points = listOf(UsagePoint(0, UsageKind.SCREEN_ON), UsagePoint(0, UsageKind.LOCK), UsagePoint(10, UsageKind.UNLOCK),
            UsagePoint(10, UsageKind.RESUME, "a"), UsagePoint(30, UsageKind.RESUME, "b"), UsagePoint(40, UsageKind.PAUSE, "a"),
            UsagePoint(60, UsageKind.SCREEN_OFF), UsagePoint(30, UsageKind.RESUME, "b"))
        val summary = UsageTimeline.summarize(points.reversed(), 0, 100)
        // Same timestamp events preserve source order; unordered equal timestamps stay conservative.
        val ordered = UsageTimeline.summarize(points, 0, 100)
        assertEquals(60, ordered.interactiveMs)
        assertEquals(50, ordered.unlockedMs)
        assertEquals(20L, ordered.byApp["a"])
        assertEquals(30L, ordered.byApp["b"])
        assertTrue(summary.byApp.values.sum() <= summary.interactiveMs)
        assertEquals(1, ordered.unlockCount)
    }
    @Test fun missingInitialStateAndRebootAreUnknownRatherThanInventedUse() {
        val summary = UsageTimeline.summarize(listOf(UsagePoint(20, UsageKind.SCREEN_ON), UsagePoint(40, UsageKind.SHUTDOWN), UsagePoint(70, UsageKind.BOOT)), 0, 100)
        assertEquals(20, summary.interactiveMs)
        assertEquals(80, summary.screenUnknownMs)
        assertEquals(0, summary.unlockCount)
        assertEquals(0, summary.unlockedMs)
    }
    @Test fun midnightSplitsAndWarmupCarryStateWithoutDoubleCounting() {
        val points = listOf(UsagePoint(0, UsageKind.SCREEN_ON), UsagePoint(0, UsageKind.UNLOCK), UsagePoint(10, UsageKind.RESUME, "a"), UsagePoint(150, UsageKind.SCREEN_OFF))
        val first = UsageTimeline.summarize(points, 0, 100)
        val second = UsageTimeline.summarize(points, 100, 200)
        assertEquals(90L, first.byApp["a"])
        assertEquals(50L, second.byApp["a"])
        assertEquals(150, first.interactiveMs + second.interactiveMs)
        assertEquals(0, second.unlockCount)
    }
}
