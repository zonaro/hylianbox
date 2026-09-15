package br.com.redclaw.hylianbox.input

import android.view.InputDevice
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputDeviceUtilsTest {
    @Test
    fun rejectsSourcesThatOnlyShareAndroidSourceClassBits() {
        assertFalse(InputDeviceUtils.isControllerSource(InputDevice.SOURCE_KEYBOARD))
        assertFalse(InputDeviceUtils.isControllerSource(InputDevice.SOURCE_TOUCHSCREEN))
        assertFalse(InputDeviceUtils.isControllerSource(InputDevice.SOURCE_MOUSE))
    }

    @Test
    fun acceptsCompleteControllerSourcesAndCombinedDevices() {
        assertTrue(InputDeviceUtils.isControllerSource(InputDevice.SOURCE_GAMEPAD))
        assertTrue(InputDeviceUtils.isControllerSource(InputDevice.SOURCE_JOYSTICK))
        assertTrue(InputDeviceUtils.isControllerSource(InputDevice.SOURCE_DPAD))
        assertTrue(
                InputDeviceUtils.isControllerSource(
                        InputDevice.SOURCE_KEYBOARD or InputDevice.SOURCE_GAMEPAD
                )
        )
    }
}
