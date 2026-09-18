/*
 * HylianBox - native Android N64 emulator frontend for Zelda ROM hacks.
 * Copyright (C) 2026 RedClaw
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package br.com.redclaw.hylianbox.ui.switchui

import br.com.redclaw.hylianbox.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccentManagerTest {

    @Test
    fun everyAccentOptionHasItsOwnThemeOverlay() {
        val overlays = AccentManager.options.map { AccentManager.getThemeOverlayForKey(it.key) }

        assertEquals(AccentManager.options.size, overlays.distinct().size)
        assertTrue(overlays.none { it == 0 })
    }

    @Test
    fun unknownAccentFallsBackToCyanOverlay() {
        assertEquals(
                R.style.ThemeOverlay_HylianBox_Accent_Cyan,
                AccentManager.getThemeOverlayForKey("unknown")
        )
    }
}
