package br.com.redclaw.hylianbox.tracker.equipment

import br.com.redclaw.hylianbox.tracker.model.TrackerGame
import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveContextWriterTest {
    private fun fixture(game: TrackerGame, lane: Int = 0): ByteBuffer {
        val buffer = ByteBuffer.allocate(0x4000)
        val signature = if (game == TrackerGame.OOT) "ZELDAZ" else "ZELDA3"
        val signatureOffset = if (game == TrackerGame.OOT) 0x1C else 0x24
        signature.forEachIndexed { index, char ->
            buffer.put((signatureOffset + index) xor lane, char.code.toByte())
        }
        buffer.put((if (game == TrackerGame.OOT) 0x2F else 0x35) xor lane, 0x30)
        repeat(if (game == TrackerGame.OOT) 24 else 48) { index ->
            buffer.put(((if (game == TrackerGame.OOT) 0x74 else 0x70) + index) xor lane, 0xFF.toByte())
        }
        return buffer
    }

    private fun put8(buffer: ByteBuffer, offset: Int, lane: Int, value: Int) {
        buffer.put(offset xor lane, value.toByte())
    }

    private fun u8(buffer: ByteBuffer, offset: Int, lane: Int): Int =
        buffer.get(offset xor lane).toInt() and 0xFF

    @Test fun assignsOotCItemAndPreservesByteLanes() {
        for (lane in listOf(0, 1, 3)) {
            val memory = fixture(TrackerGame.OOT, lane)
            put8(memory, 0x77, lane, 3)
            assertTrue(SaveContextWriter.apply(
                memory,
                TrackerEquipCommand(TrackerGame.OOT, "bow", TrackerCButton.RIGHT),
                0
            ))
            assertEquals(3, u8(memory, 0x6B, lane))
            assertEquals(3, u8(memory, 0x6E, lane))
        }
    }

    @Test fun swapsExistingCItemInsteadOfDuplicatingItsSlot() {
        val memory = fixture(TrackerGame.OOT)
        put8(memory, 0x77, 0, 3)
        put8(memory, 0x69, 0, 3)
        put8(memory, 0x6C, 0, 3)
        put8(memory, 0x6B, 0, 2)
        put8(memory, 0x6E, 0, 2)

        assertTrue(SaveContextWriter.apply(
            memory,
            TrackerEquipCommand(TrackerGame.OOT, "bow", TrackerCButton.RIGHT),
            0
        ))
        assertEquals(2, u8(memory, 0x69, 0))
        assertEquals(2, u8(memory, 0x6C, 0))
        assertEquals(3, u8(memory, 0x6B, 0))
        assertEquals(3, u8(memory, 0x6E, 0))
    }

    @Test fun usesExplicitMmMaskSlotAndMagicArrowBowSlot() {
        val memory = fixture(TrackerGame.MM)
        put8(memory, 0x70 + 0x1D, 0, 0x32)
        assertTrue(SaveContextWriter.apply(
            memory,
            TrackerEquipCommand(TrackerGame.MM, "deku_mask", TrackerCButton.DOWN),
            0
        ))
        assertEquals(0x32, u8(memory, 0x4E, 0))
        assertEquals(0x1D, u8(memory, 0x5E, 0))

        put8(memory, 0x70 + 2, 0, 2)
        assertTrue(SaveContextWriter.apply(
            memory,
            TrackerEquipCommand(TrackerGame.MM, "fire_arrows", TrackerCButton.LEFT),
            0
        ))
        assertEquals(0x4A, u8(memory, 0x4D, 0))
        assertEquals(1, u8(memory, 0x5D, 0))
    }

    @Test fun equipsOnlyOwnedOotEquipmentAndPreservesOtherNibbles() {
        val memory = fixture(TrackerGame.OOT)
        put8(memory, 0x9C, 0, 0x01)
        put8(memory, 0x9D, 0, 0x21)
        put8(memory, 0x70, 0, 0x23)
        put8(memory, 0x71, 0, 0x40)
        assertTrue(SaveContextWriter.apply(
            memory,
            TrackerEquipCommand(TrackerGame.OOT, "kokiri_sword"),
            0
        ))
        assertEquals(0x23, u8(memory, 0x70, 0))
        assertEquals(0x41, u8(memory, 0x71, 0))
        assertEquals(0x3B, u8(memory, 0x68, 0))

        assertFalse(SaveContextWriter.apply(
            memory,
            TrackerEquipCommand(TrackerGame.OOT, "mirror_shield"),
            0
        ))
    }

    @Test fun rejectsMissingInventoryAndInvalidSaveContext() {
        val valid = fixture(TrackerGame.MM)
        assertFalse(SaveContextWriter.apply(
            valid,
            TrackerEquipCommand(TrackerGame.MM, "hookshot", TrackerCButton.LEFT),
            0
        ))
        assertFalse(SaveContextWriter.apply(
            ByteBuffer.allocate(0x4000),
            TrackerEquipCommand(TrackerGame.OOT, "bow", TrackerCButton.LEFT),
            0
        ))
    }

    @Test fun exposesOnlyGameLegalLongPressActions() {
        assertEquals(TrackerEquipAction.OOT_EQUIPMENT, SaveContextWriter.action(TrackerGame.OOT, "master_sword"))
        assertEquals(TrackerEquipAction.C_ITEM, SaveContextWriter.action(TrackerGame.OOT, "bow"))
        assertEquals(TrackerEquipAction.C_ITEM, SaveContextWriter.action(TrackerGame.MM, "great_fairy_sword"))
        assertEquals(TrackerEquipAction.NONE, SaveContextWriter.action(TrackerGame.MM, "sword"))
        assertEquals(TrackerEquipAction.NONE, SaveContextWriter.action(TrackerGame.MM, "shield"))
    }
}
