package io.lifeos.focuslab

import org.junit.Assert.*
import org.junit.Test

class ResumedAppStateTest {
    @Test fun auxiliaryLauncherEventDoesNotInventAnActivityTransition() {
        val state = ResumedAppState()
        state.resumed("test.home")
        state.resumed("test.target")
        // A non-activity accessibility event must not overwrite actual resumed-activity evidence.
        assertTrue(state.isTarget(setOf("test.target")))
        state.resumed("test.settings")
        assertFalse(state.isTarget(setOf("test.target")))
    }
    @Test fun pauseAloneMustReleaseEvenWithoutAHomeResume() {
        val state = ResumedAppState()
        assertFalse(state.isTarget(setOf("test.target")))
        state.resumed("test.target")
        state.paused("test.home")
        assertTrue(state.isTarget(setOf("test.target")))
        state.paused("test.target")
        assertFalse(state.isTarget(setOf("test.target")))
        state.resumed("test.home")
        assertFalse(state.isTarget(setOf("test.target")))
    }
}
