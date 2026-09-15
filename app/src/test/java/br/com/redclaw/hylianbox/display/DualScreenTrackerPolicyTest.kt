package br.com.redclaw.hylianbox.display

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DualScreenTrackerPolicyTest {
    @Test
    fun `pins only when another display controller and tracker are all available`() {
        assertTrue(DualScreenTrackerPolicy.shouldPinTracker(true, true, true))
        assertFalse(DualScreenTrackerPolicy.shouldPinTracker(false, true, true))
        assertFalse(DualScreenTrackerPolicy.shouldPinTracker(true, false, true))
        assertFalse(DualScreenTrackerPolicy.shouldPinTracker(true, true, false))
    }
}
