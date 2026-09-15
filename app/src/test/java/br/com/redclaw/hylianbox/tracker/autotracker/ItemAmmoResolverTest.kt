/* Copyright (C) 2026 RedClaw. SPDX-License-Identifier: GPL-3.0-or-later */
package br.com.redclaw.hylianbox.tracker.autotracker

import br.com.redclaw.hylianbox.tracker.autotracker.model.ItemAmmoResolver
import br.com.redclaw.hylianbox.tracker.autotracker.parser.SaveContextParser
import br.com.redclaw.hylianbox.tracker.model.TrackerGame
import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ItemAmmoResolverTest {
    private fun fixture(game: TrackerGame, lane: Int = 0): ByteBuffer {
        val b = ByteBuffer.allocate(0x4000)
        val oot = game == TrackerGame.OOT
        val signature = if (oot) "ZELDAZ" else "ZELDA3"
        signature.forEachIndexed { i, c ->
            b.put(((if (oot) 0x1C else 0x24) + i) xor lane, c.code.toByte())
        }
        b.put((if (oot) 0x2F else 0x35) xor lane, 0x30)
        repeat(if (oot) 24 else 48) {
            b.put(((if (oot) 0x74 else 0x70) + it) xor lane, 0xFF.toByte())
        }
        return b
    }

    @Test
    fun ootMapsAmmoItemsToSlots() {
        val ammo = List(16) { it * 2 } // ammo[i] = i*2
        assertEquals(0, ItemAmmoResolver.ammoFor(TrackerGame.OOT, 0x00, ammo, 0))
        assertEquals(2, ItemAmmoResolver.ammoFor(TrackerGame.OOT, 0x01, ammo, 0))
        assertEquals(4, ItemAmmoResolver.ammoFor(TrackerGame.OOT, 0x02, ammo, 0))
        assertEquals(6, ItemAmmoResolver.ammoFor(TrackerGame.OOT, 0x03, ammo, 0))
        // Magic arrows share bow ammo (slot 3).
        assertEquals(6, ItemAmmoResolver.ammoFor(TrackerGame.OOT, 0x38, ammo, 0))
        assertEquals(6, ItemAmmoResolver.ammoFor(TrackerGame.OOT, 0x39, ammo, 0))
        assertEquals(6, ItemAmmoResolver.ammoFor(TrackerGame.OOT, 0x3A, ammo, 0))
        assertEquals(12, ItemAmmoResolver.ammoFor(TrackerGame.OOT, 0x06, ammo, 0))
        assertEquals(16, ItemAmmoResolver.ammoFor(TrackerGame.OOT, 0x09, ammo, 0))
        assertEquals(28, ItemAmmoResolver.ammoFor(TrackerGame.OOT, 0x10, ammo, 0))
    }

    @Test
    fun ootReturnsNullForItemsWithoutQuantity() {
        val ammo = List(16) { 10 }
        assertNull(ItemAmmoResolver.ammoFor(TrackerGame.OOT, 0x0E, ammo, 0)) // boomerang
        assertNull(ItemAmmoResolver.ammoFor(TrackerGame.OOT, 0x0A, ammo, 0)) // hookshot
        assertNull(ItemAmmoResolver.ammoFor(TrackerGame.OOT, 0x07, ammo, 0)) // ocarina
        assertNull(ItemAmmoResolver.ammoFor(TrackerGame.OOT, 0x3B, ammo, 0)) // sword
        assertNull(ItemAmmoResolver.ammoFor(TrackerGame.OOT, 0xFF, ammo, 0)) // NONE
        assertNull(ItemAmmoResolver.ammoFor(TrackerGame.OOT, 0x03, emptyList(), 0))
    }

    @Test
    fun mmMapsAmmoItemsToSlots() {
        val ammo = List(24) { it + 1 } // ammo[i] = i+1
        assertEquals(2, ItemAmmoResolver.ammoFor(TrackerGame.MM, 0x01, ammo, 0))
        assertEquals(2, ItemAmmoResolver.ammoFor(TrackerGame.MM, 0x4A, ammo, 0))
        assertEquals(2, ItemAmmoResolver.ammoFor(TrackerGame.MM, 0x4B, ammo, 0))
        assertEquals(2, ItemAmmoResolver.ammoFor(TrackerGame.MM, 0x4C, ammo, 0))
        assertEquals(7, ItemAmmoResolver.ammoFor(TrackerGame.MM, 0x06, ammo, 0))
        assertEquals(8, ItemAmmoResolver.ammoFor(TrackerGame.MM, 0x07, ammo, 0))
        assertEquals(9, ItemAmmoResolver.ammoFor(TrackerGame.MM, 0x08, ammo, 0))
        assertEquals(10, ItemAmmoResolver.ammoFor(TrackerGame.MM, 0x09, ammo, 0))
        assertEquals(11, ItemAmmoResolver.ammoFor(TrackerGame.MM, 0x0A, ammo, 0))
        assertEquals(13, ItemAmmoResolver.ammoFor(TrackerGame.MM, 0x0C, ammo, 0))
    }

    @Test
    fun mmPictographUsesQuestFlag() {
        val ammo = List(24) { 5 }
        val withPhoto = 1 shl 0x19
        assertEquals(1, ItemAmmoResolver.ammoFor(TrackerGame.MM, 0x0D, ammo, withPhoto))
        assertEquals(0, ItemAmmoResolver.ammoFor(TrackerGame.MM, 0x0D, ammo, 0))
    }

    @Test
    fun mmReturnsNullForItemsWithoutQuantity() {
        val ammo = List(24) { 5 }
        assertNull(ItemAmmoResolver.ammoFor(TrackerGame.MM, 0x0F, ammo, 0)) // hookshot
        assertNull(ItemAmmoResolver.ammoFor(TrackerGame.MM, 0x00, ammo, 0)) // ocarina
        assertNull(ItemAmmoResolver.ammoFor(TrackerGame.MM, 0xFF, ammo, 0))
        assertNull(ItemAmmoResolver.ammoFor(TrackerGame.MM, 0x01, emptyList(), 0))
    }

    @Test
    fun parserExposesOotAmmoPerButton() {
        for (lane in listOf(0, 1, 3)) {
            val b = fixture(TrackerGame.OOT, lane)
            fun put8(offset: Int, value: Int) = b.put(offset xor lane, value.toByte())
            put8(0x69, 0x03) // C-Left: bow
            put8(0x6A, 0x0E) // C-Down: boomerang (no badge)
            put8(0x6B, 0x38) // C-Right: fire arrow (shares bow ammo)
            put8(0x8C + 3, 25) // bow ammo
            val snapshot = SaveContextParser.parse(b, TrackerGame.OOT, 0)!!
            assertEquals(25, snapshot.equippedItems.cLeftAmmo)
            assertNull(snapshot.equippedItems.cDownAmmo)
            assertEquals(25, snapshot.equippedItems.cRightAmmo)
            assertEquals(25, snapshot.ammo[3])
        }
    }

    @Test
    fun parserExposesMmAmmoPerButtonAndPictograph() {
        val b = fixture(TrackerGame.MM)
        b.put(0x4D, 0x01) // C-Left: bow
        b.put(0x4E, 0x0F) // C-Down: hookshot (no badge)
        b.put(0x4F, 0x0D) // C-Right: pictograph
        b.put(0xA0 + 1, 30) // bow ammo
        b.putInt(0xBC, 1 shl 0x19) // photo taken
        val snapshot = SaveContextParser.parse(b, TrackerGame.MM, 0)!!
        assertEquals(30, snapshot.equippedItems.cLeftAmmo)
        assertNull(snapshot.equippedItems.cDownAmmo)
        assertEquals(1, snapshot.equippedItems.cRightAmmo)
    }

    @Test
    fun parserAmmoChangeAloneMarksEquipmentDirty() {
        val b = fixture(TrackerGame.OOT)
        b.put(0x69, 0x03)
        b.put(0x8C + 3, 10)
        val first = SaveContextParser.parse(b, TrackerGame.OOT, 0)!!.equippedItems
        b.put(0x8C + 3, 9) // shot one arrow: same item, lower count
        val second = SaveContextParser.parse(b, TrackerGame.OOT, 0)!!.equippedItems
        assertEquals(10, first.cLeftAmmo)
        assertEquals(9, second.cLeftAmmo)
        assert(first != second)
    }
}
