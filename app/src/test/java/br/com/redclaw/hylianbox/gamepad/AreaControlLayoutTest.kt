package br.com.redclaw.hylianbox.gamepad

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AreaControlLayoutTest {
    @Test
    fun `reference area centers map to the expected controls`() {
        assertControl(AreaControl.DPAD_SWIPE, 0.07f, 0.14f)
        assertControl(AreaControl.L, 0.28f, 0.07f)
        assertControl(AreaControl.ANALOG, 0.25f, 0.55f)
        assertControl(AreaControl.START, 0.50f, 0.20f)
        assertControl(AreaControl.C_LEFT, 0.64f, 0.20f)
        assertControl(AreaControl.C_DOWN, 0.78f, 0.20f)
        assertControl(AreaControl.C_RIGHT, 0.93f, 0.20f)
        assertControl(AreaControl.C_UP, 0.50f, 0.64f)
        assertControl(AreaControl.Z, 0.50f, 0.93f)
        assertControl(AreaControl.R, 0.78f, 0.51f)
        assertControl(AreaControl.A, 0.68f, 0.80f)
        assertControl(AreaControl.B, 0.90f, 0.80f)
    }

    @Test
    fun `button stick zones use the same raw mappings as Standard`() {
        assertEquals(KeyEvent.KEYCODE_BUTTON_L1, AreaControlLayout.zone(AreaControl.C_LEFT).keyCode)
        assertEquals(KeyEvent.KEYCODE_BUTTON_X, AreaControlLayout.zone(AreaControl.C_DOWN).keyCode)
        assertEquals(KeyEvent.KEYCODE_BUTTON_R1, AreaControlLayout.zone(AreaControl.C_RIGHT).keyCode)
        assertEquals(KeyEvent.KEYCODE_BUTTON_A, AreaControlLayout.zone(AreaControl.A).keyCode)
        assertEquals(KeyEvent.KEYCODE_BUTTON_B, AreaControlLayout.zone(AreaControl.B).keyCode)
        assertEquals(5, AreaControlLayout.buttonStickZones.size)
    }

    @Test
    fun `reference glows keep their edge colors and do not define borders`() {
        assertEquals(10, AreaControlLayout.glows.size)
        assertGlow(AreaControl.START, GlowEdge.TOP, AreaControlLayout.COLOR_RED)
        assertGlow(AreaControl.C_LEFT, GlowEdge.TOP, AreaControlLayout.COLOR_YELLOW)
        assertGlow(AreaControl.R, GlowEdge.RIGHT, AreaControlLayout.COLOR_PURPLE)
        assertGlow(AreaControl.Z, GlowEdge.BOTTOM, AreaControlLayout.COLOR_PURPLE)
        assertGlow(AreaControl.A, GlowEdge.BOTTOM, AreaControlLayout.COLOR_BLUE)
        assertGlow(AreaControl.B, GlowEdge.BOTTOM, AreaControlLayout.COLOR_GREEN)
    }

    @Test
    fun `coordinates outside the overlay do not map`() {
        assertNull(AreaControlLayout.hitTest(-0.01f, 0.5f))
        assertNull(AreaControlLayout.hitTest(1f, 0.5f))
        assertNull(AreaControlLayout.hitTest(0.5f, 1f))
    }

    private fun assertControl(control: AreaControl, x: Float, y: Float) {
        assertEquals(control, AreaControlLayout.hitTest(x, y)?.control)
    }

    private fun assertGlow(control: AreaControl, edge: GlowEdge, color: Int) {
        val glow = AreaControlLayout.glows.first { it.control == control }
        assertEquals(edge, glow.edge)
        assertEquals(color, glow.color)
    }
}
