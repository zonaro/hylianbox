/* Copyright (C) 2026 RedClaw. SPDX-License-Identifier: GPL-3.0-or-later */
package br.com.redclaw.hylianbox.savename

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveSlotEditorTest {

    // ---- Helpers to build minimal valid SRAM images ----

    private fun buildOotSram(
            slotNames: List<String?>,
            table: FileNameTable = FileNameTable.PAL
    ): ByteArray {
        val sram = ByteArray(0x8000)
        // Header magic at 0x03..0x0A: 0x98 0x09 0x10 0x21 'Z' 'E' 'L' 'D' 'A'
        sram[0x03] = 0x98.toByte()
        sram[0x04] = 0x09.toByte()
        sram[0x05] = 0x10.toByte()
        sram[0x06] = 0x21.toByte()
        sram[0x07] = 'Z'.code.toByte()
        sram[0x08] = 'E'.code.toByte()
        sram[0x09] = 'L'.code.toByte()
        sram[0x0A] = 'D'.code.toByte()
        sram[0x0B] = 'A'.code.toByte()
        for (slot in 0 until 6) {
            val base = 0x20 + slot * 0x1450
            val name = slotNames.getOrNull(slot % 3)
            if (name == null) {
                // Leave as zeros -> invalid (no ZELDA signature)
                continue
            }
            // newf
            val sig = if (table == FileNameTable.NTSC) "ZELDAZ" else "ZELDA3"
            for (i in sig.indices) sram[base + 0x1C + i] = sig[i].code.toByte()
            // playerName
            val enc = PlayerNameCodec.encode(name, table)!!
            enc.copyInto(sram, base + 0x24)
            // Minimal valid Save: set healthCapacity to 0x30 so checksum is deterministic
            sram[base + 0x2C] = 0x00
            sram[base + 0x2D] = 0x30
            // Checksum
            val sum = ootChecksum(sram, base)
            sram[base + 0x1352] = ((sum ushr 8) and 0xFF).toByte()
            sram[base + 0x1353] = (sum and 0xFF).toByte()
        }
        return sram
    }

    private fun ootChecksum(sram: ByteArray, base: Int): Int {
        var sum = 0
        var i = 0
        while (i < 0x1354) {
            val off = base + i
            if (off == base + 0x1352 || off + 1 == base + 0x1352) {
                i += 2
                continue
            }
            val hi = sram[off].toInt() and 0xFF
            val lo = sram[off + 1].toInt() and 0xFF
            sum = (sum + (hi shl 8 or lo)) and 0xFFFF
            i += 2
        }
        return sum
    }

    private fun buildMmFlash(
            fileNames: List<String?>,
            table: FileNameTable = FileNameTable.PAL
    ): ByteArray {
        val sram = ByteArray(0x20000)
        val offsets = intArrayOf(0x0000, 0x2000, 0x4000, 0x6000)
        for (file in 0 until 2) {
            val name = fileNames.getOrNull(file) ?: continue
            for (k in 0 until 2) {
                val base = offsets[file * 2 + k]
                val sig = "ZELDA3"
                for (i in sig.indices) sram[base + 0x24 + i] = sig[i].code.toByte()
                val enc = PlayerNameCodec.encode(name, table)!!
                enc.copyInto(sram, base + 0x2C)
                sram[base + 0x34] = 0x00
                sram[base + 0x35] = 0x30
                val sum = mmChecksum(sram, base, 0x100C)
                sram[base + 0x100A] = ((sum ushr 8) and 0xFF).toByte()
                sram[base + 0x100B] = (sum and 0xFF).toByte()
            }
        }
        return sram
    }

    private fun mmChecksum(sram: ByteArray, base: Int, size: Int): Int {
        var sum = 0
        var i = 0
        while (i < size) {
            val off = base + i
            if (off == base + 0x100A || off == base + 0x100A + 1) {
                i++
                continue
            }
            sum = (sum + (sram[off].toInt() and 0xFF)) and 0xFFFF
            i++
        }
        return sum
    }

    // ---- OoT tests ----

    @Test
    fun ootReadSlots() {
        val sram = buildOotSram(listOf("LINK", "ZELDA", null))
        val slots = SaveSlotEditor.readSlots(sram, SaveSlotEditor.Game.OOT, FileNameTable.PAL)
        assertEquals(3, slots.size)
        assertTrue(slots[0].isValid)
        assertEquals("LINK", slots[0].displayName)
        assertTrue(slots[1].isValid)
        assertEquals("ZELDA", slots[1].displayName)
        assertFalse(slots[2].isValid)
    }

    @Test
    fun ootWriteSlotUpdatesMainAndBackupAndPreservesOthers() {
        val sram = buildOotSram(listOf("LINK", "ZELDA", "GANON"))
        val result =
                SaveSlotEditor.writeSlot(
                        sram,
                        SaveSlotEditor.Game.OOT,
                        1,
                        "SHEIK",
                        FileNameTable.PAL
                )
        assertTrue(result is SaveSlotEditor.EditResult.Ok)
        val out = (result as SaveSlotEditor.EditResult.Ok).sram
        val slots = SaveSlotEditor.readSlots(out, SaveSlotEditor.Game.OOT, FileNameTable.PAL)
        assertEquals("LINK", slots[0].displayName)
        assertEquals("SHEIK", slots[1].displayName)
        assertEquals("GANON", slots[2].displayName)
        // Backup copy also updated
        val backupBase = 0x20 + (1 + 3) * 0x1450
        val raw = out.copyOfRange(backupBase + 0x24, backupBase + 0x24 + 8)
        assertEquals("SHEIK", PlayerNameCodec.decode(raw, FileNameTable.PAL))
    }

    @Test
    fun ootWriteSlotChecksumValid() {
        val sram = buildOotSram(listOf("LINK", null, null))
        val result =
                SaveSlotEditor.writeSlot(
                        sram,
                        SaveSlotEditor.Game.OOT,
                        0,
                        "MARIN",
                        FileNameTable.PAL
                ) as
                        SaveSlotEditor.EditResult.Ok
        val out = result.sram
        val slots = SaveSlotEditor.readSlots(out, SaveSlotEditor.Game.OOT, FileNameTable.PAL)
        assertTrue(slots[0].isValid)
        assertEquals("MARIN", slots[0].displayName)
    }

    @Test
    fun ootWriteSlotRejectsBadName() {
        val sram = buildOotSram(listOf("LINK", null, null))
        val result =
                SaveSlotEditor.writeSlot(
                        sram,
                        SaveSlotEditor.Game.OOT,
                        0,
                        "TOOLONGNAME",
                        FileNameTable.PAL
                )
        assertTrue(result is SaveSlotEditor.EditResult.Err)
        assertEquals(
                SaveSlotEditor.Reason.BAD_NAME,
                (result as SaveSlotEditor.EditResult.Err).reason
        )
    }

    @Test
    fun ootWriteSlotRejectsBadSlot() {
        val sram = buildOotSram(listOf("LINK", null, null))
        val result =
                SaveSlotEditor.writeSlot(sram, SaveSlotEditor.Game.OOT, 3, "A", FileNameTable.PAL)
        assertTrue(result is SaveSlotEditor.EditResult.Err)
        assertEquals(
                SaveSlotEditor.Reason.BAD_SLOT,
                (result as SaveSlotEditor.EditResult.Err).reason
        )
    }

    @Test
    fun ootWriteSlotFailsClosedOnNoValidSave() {
        val sram = ByteArray(0x8000)
        val result =
                SaveSlotEditor.writeSlot(
                        sram,
                        SaveSlotEditor.Game.OOT,
                        0,
                        "LINK",
                        FileNameTable.PAL
                )
        assertTrue(result is SaveSlotEditor.EditResult.Err)
        assertEquals(
                SaveSlotEditor.Reason.NO_VALID_SAVE,
                (result as SaveSlotEditor.EditResult.Err).reason
        )
    }

    @Test
    fun ootWriteSlotFailsClosedOnBadSize() {
        val sram = ByteArray(100)
        val result =
                SaveSlotEditor.writeSlot(
                        sram,
                        SaveSlotEditor.Game.OOT,
                        0,
                        "LINK",
                        FileNameTable.PAL
                )
        assertTrue(result is SaveSlotEditor.EditResult.Err)
        assertEquals(
                SaveSlotEditor.Reason.BAD_SIZE,
                (result as SaveSlotEditor.EditResult.Err).reason
        )
    }

    // ---- MM tests ----

    @Test
    fun mmReadSlots() {
        val sram = buildMmFlash(listOf("LINK", "ZELDA"))
        val slots = SaveSlotEditor.readSlots(sram, SaveSlotEditor.Game.MM, FileNameTable.PAL)
        assertEquals(2, slots.size)
        assertTrue(slots[0].isValid)
        assertEquals("LINK", slots[0].displayName)
        assertTrue(slots[1].isValid)
        assertEquals("ZELDA", slots[1].displayName)
    }

    @Test
    fun mmWriteSlotUpdatesBothCopies() {
        val sram = buildMmFlash(listOf("LINK", "ZELDA"))
        val result =
                SaveSlotEditor.writeSlot(
                        sram,
                        SaveSlotEditor.Game.MM,
                        0,
                        "TINGLE",
                        FileNameTable.PAL
                )
        assertTrue(result is SaveSlotEditor.EditResult.Ok)
        val out = (result as SaveSlotEditor.EditResult.Ok).sram
        val slots = SaveSlotEditor.readSlots(out, SaveSlotEditor.Game.MM, FileNameTable.PAL)
        assertEquals("TINGLE", slots[0].displayName)
        assertEquals("ZELDA", slots[1].displayName)
    }

    @Test
    fun mmWriteSlotChecksumValid() {
        val sram = buildMmFlash(listOf("LINK", null))
        val result =
                SaveSlotEditor.writeSlot(
                        sram,
                        SaveSlotEditor.Game.MM,
                        0,
                        "MARIN",
                        FileNameTable.PAL
                ) as
                        SaveSlotEditor.EditResult.Ok
        val out = result.sram
        val slots = SaveSlotEditor.readSlots(out, SaveSlotEditor.Game.MM, FileNameTable.PAL)
        assertTrue(slots[0].isValid)
        assertEquals("MARIN", slots[0].displayName)
    }

    @Test
    fun mmWriteSlotFailsClosedOnNoValidSave() {
        val sram = ByteArray(0x20000)
        val result =
                SaveSlotEditor.writeSlot(sram, SaveSlotEditor.Game.MM, 0, "LINK", FileNameTable.PAL)
        assertTrue(result is SaveSlotEditor.EditResult.Err)
        assertEquals(
                SaveSlotEditor.Reason.NO_VALID_SAVE,
                (result as SaveSlotEditor.EditResult.Err).reason
        )
    }

    @Test
    fun mmWriteSlotRejectsBadSlot() {
        val sram = buildMmFlash(listOf("LINK", null))
        val result =
                SaveSlotEditor.writeSlot(sram, SaveSlotEditor.Game.MM, 2, "A", FileNameTable.PAL)
        assertTrue(result is SaveSlotEditor.EditResult.Err)
        assertEquals(
                SaveSlotEditor.Reason.BAD_SLOT,
                (result as SaveSlotEditor.EditResult.Err).reason
        )
    }
}
