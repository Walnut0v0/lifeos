package io.lifeos.uipreview

import io.lifeos.uipreview.ui.screens.*
import org.junit.Assert.*
import org.junit.Test

class PrototypeStateTest {
    @Test fun degradedScenariosNeverClaimActiveEnforcement() {
        DemoScenario.entries.filter { it != DemoScenario.FOCUS }.forEach {
            val state = PrototypeState(it)
            assertFalse(state.active)
            assertEquals(0f, state.progress, .0001f)
        }
    }
    @Test fun missingSourceAndPermissionShowMissingData() {
        assertTrue(PrototypeState(DemoScenario.DISCONNECTED).tasks.isEmpty())
        for (scenario in listOf(DemoScenario.PERMISSION, DemoScenario.DISCONNECTED, DemoScenario.EMPTY_STATS)) {
            assertFalse(PrototypeState(scenario).hasStatistics)
        }
    }
}
