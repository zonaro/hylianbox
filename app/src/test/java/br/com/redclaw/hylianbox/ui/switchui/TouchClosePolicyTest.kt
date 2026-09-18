package br.com.redclaw.hylianbox.ui.switchui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchClosePolicyTest {
    @Test
    fun touchDeviceStartsVisibleOnlyWithoutPhysicalController() {
        assertTrue(TouchClosePolicy.initial(hasTouchscreen = true, hasController = false))
        assertFalse(TouchClosePolicy.initial(hasTouchscreen = true, hasController = true))
        assertFalse(TouchClosePolicy.initial(hasTouchscreen = false, hasController = false))
    }

    @Test
    fun lastInputChangesVisibility() {
        assertTrue(TouchClosePolicy.afterTouch(hasTouchscreen = true))
        assertFalse(TouchClosePolicy.afterTouch(hasTouchscreen = false))
        assertFalse(TouchClosePolicy.afterNonTouch())
    }
}
