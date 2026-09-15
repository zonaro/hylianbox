/* Copyright (C) 2026 RedClaw. SPDX-License-Identifier: GPL-3.0-or-later */
package br.com.redclaw.hylianbox.tracker.assets

import br.com.redclaw.hylianbox.tracker.assets.mapping.EquippedItemIconMap
import br.com.redclaw.hylianbox.tracker.assets.mapping.IconArchiveFormat
import br.com.redclaw.hylianbox.tracker.model.TrackerGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EquippedItemIconMapTest {
    @Test fun usesStableKeysAndCompleteSupportedRanges() {
        assertEquals("equipped_03", EquippedItemIconMap.assetKey(3))
        assertEquals(65, EquippedItemIconMap.mappings(TrackerGame.OOT).size)
        assertEquals(83, EquippedItemIconMap.mappings(TrackerGame.MM).size)
        assertTrue(EquippedItemIconMap.supports(TrackerGame.OOT, 0x40))
        assertFalse(EquippedItemIconMap.supports(TrackerGame.OOT, 0x41))
        assertTrue(EquippedItemIconMap.supports(TrackerGame.MM, 0x52))
    }

    @Test fun mapsItemIdsToTheirNativeHudTextureOffsets() {
        val ootBow = EquippedItemIconMap.mappings(TrackerGame.OOT).single { it.itemId == "equipped_03" }
        val mmMirrorShield = EquippedItemIconMap.mappings(TrackerGame.MM).single { it.itemId == "equipped_52" }
        assertEquals(0x3000, ootBow.offset)
        assertEquals(IconArchiveFormat.RAW, ootBow.archiveFormat)
        assertEquals(0x52000, mmMirrorShield.offset)
        assertEquals(IconArchiveFormat.YAR, mmMirrorShield.archiveFormat)
    }
}
