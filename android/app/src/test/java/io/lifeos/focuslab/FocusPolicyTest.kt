package io.lifeos.focuslab

import org.junit.Assert.*
import org.junit.Test

class FocusPolicyTest {
    @Test fun scheduledBoundariesAndCrossMidnight() {
        val start = 1_800_028_740_000L
        val s = Session(start, start + 60_000)
        assertEquals(Phase.SCHEDULED, FocusPolicy.phase(s, start - 1, 0, 1))
        assertEquals(Phase.ACTIVE, FocusPolicy.phase(s, start, 0, 1))
        assertEquals(Phase.ACTIVE, FocusPolicy.phase(s, s.end - 1, 0, 1))
        assertEquals(Phase.IDLE, FocusPolicy.phase(s, s.end, 0, 1))
        assertEquals(Phase.IDLE, FocusPolicy.phase(s, s.end + 1_000_000, 0, 1))
    }
    @Test fun manualIgnoresWallClockButNotReboot() {
        val s = Session(100_000, 160_000, true, 61_000, 4)
        assertEquals(Phase.ACTIVE, FocusPolicy.phase(s, 999_999_999, 2_000, 4))
        assertEquals(Phase.ACTIVE, FocusPolicy.phase(s, 1, 2_000, 4))
        assertEquals(Phase.IDLE, FocusPolicy.phase(s, 1, 61_000, 4))
        assertEquals(Phase.FAULT, FocusPolicy.phase(s, 1, 2_000, 5))
        assertEquals(Phase.FAULT, FocusPolicy.phase(s, 1, 0, -1))
    }
    @Test fun invalidAndOversizedSessionsFailOpen() {
        listOf(Session(0, 100), Session(100, 100), Session(100, 99), Session(100, 100 + FocusPolicy.MAX_DURATION + 1), Session(100, 200, duration = 50), Session(100, 200, true, 999_999, 1)).forEach {
            assertEquals(Phase.FAULT, FocusPolicy.phase(it, 150, 0, 1))
        }
    }
    @Test fun safetyAlwaysWinsAndStoppingRemovesAuthority() {
        assertTrue(FocusPolicy.shouldBlock(true, true, false, true))
        assertFalse(FocusPolicy.shouldBlock(true, true, true, true))
        assertFalse(FocusPolicy.shouldBlock(true, true, false, false))
        assertFalse(FocusPolicy.shouldBlock(true, false, false, true))
        assertFalse(FocusPolicy.shouldBlock(false, true, false, true))
        assertEquals(Phase.IDLE, FocusPolicy.phase(null, 150, 0, 1))
    }
}
