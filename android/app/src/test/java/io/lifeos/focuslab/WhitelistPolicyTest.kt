package io.lifeos.focuslab

import org.junit.Assert.*
import org.junit.Test

class WhitelistPolicyTest {
    @Test fun selectedAppIsAllowedWhileOtherManagedAppsAreBlocked() {
        assertFalse(WhitelistPolicy.blocks(true, true, true, false, true))
        assertTrue(WhitelistPolicy.blocks(true, true, false, false, true))
    }
    @Test fun safetyUnknownStateAndExpiryAlwaysRelease() {
        assertFalse(WhitelistPolicy.blocks(true, true, false, true, true))
        assertFalse(WhitelistPolicy.blocks(true, false, false, false, true))
        assertFalse(WhitelistPolicy.blocks(true, true, false, false, false))
        assertFalse(WhitelistPolicy.blocks(false, true, false, false, true))
    }
    @Test fun countdownUsesRemainingTimeWithoutGoingNegative() {
        assertEquals("00:00", countdownText(-1))
        assertEquals("00:01", countdownText(1))
        assertEquals("01:00", countdownText(60_000))
        assertEquals("25:00", countdownText(25 * 60_000))
    }
}
