package io.lifeos.focuslab

import org.junit.Assert.*
import org.junit.Test

class PlanStopPolicyTest {
    @Test fun reconnectDoesNotTurnATemporaryGapIntoUserCancellation() {
        for (code in listOf("PLAN_PERMISSION_LOST", "WHITELIST_PERMISSION_LOST", "SERVICE_UNBOUND", "PLAN_REVISED", "SESSION_EXPIRED")) {
            assertFalse(code, PlanStopPolicy.suppresses(code, true))
        }
    }
    @Test fun explicitExitAndRuntimeFaultRemainSuppressed() {
        for (code in listOf("WHITELIST_EMERGENCY_STOP", "WHITELIST_CONFIRMED_EXIT", "USER_STOP", "SERVICE_FAULT", "PLAN_RUNTIME_FAULT")) {
            assertTrue(code, PlanStopPolicy.suppresses(code, true))
        }
        assertFalse(PlanStopPolicy.suppresses("SERVICE_UNBOUND", false))
    }
}
