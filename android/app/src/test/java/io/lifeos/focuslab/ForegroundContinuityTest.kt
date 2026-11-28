package io.lifeos.focuslab

import org.junit.Assert.*
import org.junit.Test

class ForegroundContinuityTest {
    @Test fun retainsAnUnchangedForegroundAcrossAPlanBoundary() {
        val evidence = ForegroundContinuity()
        evidence.begin(100000, 10000); evidence.resumed("test.target"); evidence.finish(100000, 10000)
        for (i in 1..60) { evidence.begin(100000L + i * 1000, 10000L + i * 1000); evidence.finish(100000L + i * 1000, 10000L + i * 1000) }
        assertEquals("test.target", evidence.candidate)
        evidence.paused("test.target"); assertNull(evidence.candidate)
    }
    @Test fun sleepGapsClockRollbackAndRevocationDiscardEvidence() {
        for (times in listOf(107000L to 17000L, 99000L to 11000L, 101000L to 9000L)) {
            val evidence = ForegroundContinuity()
            evidence.begin(100000, 10000); evidence.resumed("test.target"); evidence.finish(100000, 10000)
            evidence.begin(times.first, times.second); assertNull(evidence.candidate)
        }
        val evidence = ForegroundContinuity()
        evidence.resumed("test.target"); evidence.reset(); assertNull(evidence.candidate)
    }
}
