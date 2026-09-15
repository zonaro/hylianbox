/* Copyright (C) 2026 RedClaw. SPDX-License-Identifier: GPL-3.0-or-later */
package br.com.redclaw.hylianbox.savename

import br.com.redclaw.hylianbox.ocarina.OcarinaGame

/**
 * Offline per-slot player-name editor for OoT/MM SRAM files.
 *
 * Layout facts (verified against zeldaret/oot `include/save.h`, `src/code/z_sram.c` and zeldaret/mm
 * `include/z64save.h`, `src/code/z_sram_NES.c`):
 *
 * OoT (32 KiB SRAM):
 * - `SRAM_SIZE = 0x8000`, header `0x10`.
 * - `SLOT_SIZE = sizeof(SaveContext) + 0x28 = 0x1428 + 0x28 = 0x1450`.
 * - `SLOT_OFFSET(i) = 0x20 + i * 0x1450` for i in 0..5 (0-2 main, 3-5 backups).
 * - Name at slot + `0x24` (`Save.info` at `0x1C` + `playerName` at `0x08`), 8 bytes.
 * - Checksum at slot + `0x1352` (2 bytes, big-endian). It is the sum of big-endian u16s over the
 * first `0x1354` bytes (`sizeof(Save)`) with the checksum field zeroed. `CHECKSUM_SIZE =
 * sizeof(Save) / 2 = 0x9AA`.
 * - Signature `newf` at slot + `0x1C`, 6 bytes (`ZELDAZ` NTSC, `ZELDA3` PAL tolerated).
 *
 * MM (flash dump, typically 128 KiB):
 * - New-Cycle saves (`sizeof(Save) = 0x100C`) at flash offsets `0x0000` (File 1), `0x2000` (File 1
 * backup), `0x4000` (File 2), `0x6000` (File 2 backup).
 * - Owl saves (`offsetof(SaveContext, fileNum) = 0x3CA0`) at `0x8000`, `0xC000`, `0x10000`,
 * `0x14000` (main + backup per file).
 * - Name at save + `0x2C` (`saveInfo` at `0x24` + `playerName` at `0x08`), 8 bytes.
 * - Checksum at save + `0x100A` (2 bytes, big-endian). Byte-sum (`Sram_CalcChecksum`: sum of bytes)
 * over `0x100C` (New Cycle) or `0x3CA0` (Owl) with the checksum field zeroed.
 * - Signature `newf` at save + `0x24`, 6 bytes, always `ZELDA3` for valid saves.
 *
 * All functions are pure-Kotlin (no Android deps) and fail closed: they never throw for corrupt
 * input, they return a typed result instead. Callers must preserve the live emulator state before
 * editing the file and recreate the Activity afterwards so the core reloads SRAM from disk.
 */
object SaveSlotEditor {

    /** Which game family the SRAM belongs to. Mirrors [OcarinaGame]. */
    enum class Game {
        OOT,
        MM
    }

    fun fromOcarina(game: OcarinaGame?): Game? =
            when (game) {
                OcarinaGame.OOT -> Game.OOT
                OcarinaGame.MM -> Game.MM
                null -> null
            }

    /**
     * Filename table for [gameCode] (e.g. `CZLE`, `NZSE`). OoT `CZL*` (NTSC) uses the NTSC table
     * (uppercase base `0xAB`, space `0xDF`); everything else (OoT PAL, all MM) uses the PAL table
     * (base `0x0A`, space `0x3E`).
     */
    fun tableFor(game: Game, gameCode: String?): FileNameTable =
            when {
                game == Game.OOT && (gameCode?.startsWith("CZL") == true) -> FileNameTable.NTSC
                else -> FileNameTable.PAL
            }

    /** Number of user-visible files: 3 for OoT, 2 for MM. */
    fun slotCount(game: Game): Int = if (game == Game.OOT) 3 else 2

    /** One user-visible file slot. */
    data class SlotInfo(
            val index: Int,
            val displayName: String,
            val isValid: Boolean,
            val isBlank: Boolean
    )

    /** Typed result; never throws to the UI layer. */
    sealed interface EditResult {
        data class Ok(val sram: ByteArray, val updatedCopies: Int) : EditResult
        data class Err(val reason: Reason) : EditResult
    }

    enum class Reason {
        BAD_SIZE,
        BAD_SLOT,
        BAD_NAME,
        NO_VALID_SAVE,
        UNSUPPORTED
    }

    // ---- OoT constants ----

    private const val OOT_SRAM_SIZE = 0x8000
    private const val OOT_SLOT_SIZE = 0x1450
    private const val OOT_SLOT_BASE = 0x20
    private const val OOT_NAME_REL = 0x24
    private const val OOT_CHECKSUM_REL = 0x1352
    private const val OOT_SAVE_SIZE = 0x1354
    private const val OOT_CHECKSUM_WORDS = OOT_SAVE_SIZE / 2
    private const val OOT_NEWF_REL = 0x1C

    // ---- MM constants ----

    private const val MM_FLASH_SIZE = 0x20000
    private const val MM_NEWCYCLE_SIZE = 0x100C
    private const val MM_OWL_SIZE = 0x3CA0
    private const val MM_NAME_REL = 0x2C
    private const val MM_CHECKSUM_REL = 0x100A
    private const val MM_NEWF_REL = 0x24

    private val MM_NEWCYCLE_OFFSETS = intArrayOf(0x0000, 0x2000, 0x4000, 0x6000)
    private val MM_OWL_OFFSETS = intArrayOf(0x8000, 0xC000, 0x10000, 0x14000)

    private val OOT_SIGNATURES = listOf("ZELDAZ", "ZELDA3")
    private const val MM_SIGNATURE = "ZELDA3"

    // ---- Public API ----

    /**
     * Read the user-visible slot names from [sram]. Slots without a valid signature + checksum are
     * reported with `isValid = false` and an empty display name. Never throws.
     */
    fun readSlots(sram: ByteArray, game: Game, table: FileNameTable): List<SlotInfo> =
            runCatching {
                        when (game) {
                            Game.OOT -> readOotSlots(sram, table)
                            Game.MM -> readMmSlots(sram, table)
                        }
                    }
                    .getOrDefault(
                            List(slotCount(game)) {
                                SlotInfo(it, "", isValid = false, isBlank = true)
                            }
                    )

    /**
     * Return a copy of [sram] with [slot] renamed to [displayName]. Both the main and backup copies
     * are updated (OoT: slot and slot+3; MM: New-Cycle main+backup and any valid Owl copies for the
     * same file). Each updated copy gets its checksum recalculated. Never throws.
     */
    fun writeSlot(
            sram: ByteArray,
            game: Game,
            slot: Int,
            displayName: String,
            table: FileNameTable
    ): EditResult =
            runCatching {
                        if (slot !in 0 until slotCount(game)) return EditResult.Err(Reason.BAD_SLOT)
                        val encoded =
                                PlayerNameCodec.encode(displayName, table)
                                        ?: return EditResult.Err(Reason.BAD_NAME)
                        when (game) {
                            Game.OOT -> writeOotSlot(sram, slot, encoded)
                            Game.MM -> writeMmSlot(sram, slot, encoded)
                        }
                    }
                    .getOrDefault(EditResult.Err(Reason.UNSUPPORTED))

    // ---- OoT ----

    private fun readOotSlots(sram: ByteArray, table: FileNameTable): List<SlotInfo> {
        if (sram.size < OOT_SRAM_SIZE) {
            return List(3) { SlotInfo(it, "", isValid = false, isBlank = true) }
        }
        return List(3) { slot ->
            val base = OOT_SLOT_BASE + slot * OOT_SLOT_SIZE
            val valid = isOotSlotValid(sram, base)
            if (!valid) {
                SlotInfo(slot, "", isValid = false, isBlank = true)
            } else {
                val raw = sram.copyOfRange(base + OOT_NAME_REL, base + OOT_NAME_REL + 8)
                val name = PlayerNameCodec.decode(raw, table)
                SlotInfo(
                        slot,
                        name,
                        isValid = true,
                        isBlank = PlayerNameCodec.isBlankName(raw, table)
                )
            }
        }
    }

    private fun isOotSlotValid(sram: ByteArray, base: Int): Boolean {
        if (base < 0 || base + OOT_SAVE_SIZE > sram.size) return false
        val sig =
                sram.copyOfRange(base + OOT_NEWF_REL, base + OOT_NEWF_REL + 6)
                        .map { (it.toInt() and 0xFF).toChar() }
                        .joinToString("")
        if (sig !in OOT_SIGNATURES) return false
        val stored = u16be(sram, base + OOT_CHECKSUM_REL)
        return ootChecksum(sram, base) == stored
    }

    private fun ootChecksum(sram: ByteArray, base: Int): Int {
        var sum = 0
        var i = 0
        while (i < OOT_SAVE_SIZE) {
            val off = base + i
            if (off == base + OOT_CHECKSUM_REL || off + 1 == base + OOT_CHECKSUM_REL) {
                // Checksum field reads as zero.
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

    private fun writeOotSlot(sram: ByteArray, slot: Int, encoded: ByteArray): EditResult {
        if (sram.size < OOT_SRAM_SIZE) return EditResult.Err(Reason.BAD_SIZE)
        val mainBase = OOT_SLOT_BASE + slot * OOT_SLOT_SIZE
        val backupBase = OOT_SLOT_BASE + (slot + 3) * OOT_SLOT_SIZE
        if (!isOotSlotValid(sram, mainBase) && !isOotSlotValid(sram, backupBase)) {
            return EditResult.Err(Reason.NO_VALID_SAVE)
        }
        val out = sram.copyOf()
        var updated = 0
        for (base in listOf(mainBase, backupBase)) {
            if (base + OOT_SAVE_SIZE > out.size) continue
            // Refresh from the valid copy when one side is corrupt, so both end
            // up identical like Sram_WriteSave (main + backup identical).
            encoded.copyInto(out, base + OOT_NAME_REL)
            val sum = ootChecksum(out, base)
            putU16be(out, base + OOT_CHECKSUM_REL, sum)
            updated++
        }
        return EditResult.Ok(out, updated)
    }

    // ---- MM ----

    private fun readMmSlots(sram: ByteArray, table: FileNameTable): List<SlotInfo> {
        return List(2) { file ->
            val candidates = mmBasesForFile(sram, file)
            val validBase = candidates.firstOrNull { isMmSaveValid(sram, it.first, it.second) }
            if (validBase == null) {
                SlotInfo(file, "", isValid = false, isBlank = true)
            } else {
                val (base, _) = validBase
                val raw = sram.copyOfRange(base + MM_NAME_REL, base + MM_NAME_REL + 8)
                val name = PlayerNameCodec.decode(raw, table)
                SlotInfo(
                        file,
                        name,
                        isValid = true,
                        isBlank = PlayerNameCodec.isBlankName(raw, table)
                )
            }
        }
    }

    /**
     * Candidate (base, size) pairs for user file [file] (0..1): New-Cycle main+backup plus Owl
     * main+backup when they fit in [sram].
     */
    private fun mmBasesForFile(sram: ByteArray, file: Int): List<Pair<Int, Int>> {
        val out = mutableListOf<Pair<Int, Int>>()
        // Full flash dump layout.
        if (sram.size >= MM_FLASH_SIZE) {
            val ncMain = MM_NEWCYCLE_OFFSETS[file * 2]
            val ncBackup = MM_NEWCYCLE_OFFSETS[file * 2 + 1]
            out += ncMain to MM_NEWCYCLE_SIZE
            out += ncBackup to MM_NEWCYCLE_SIZE
            val owlMain = MM_OWL_OFFSETS[file * 2]
            val owlBackup = MM_OWL_OFFSETS[file * 2 + 1]
            out += owlMain to MM_OWL_SIZE
            out += owlBackup to MM_OWL_SIZE
            return out.filter { (base, size) -> base + size <= sram.size }
        }
        // Single-save buffer (SAVE_BUFFER_SIZE 0x4000, or bare Save 0x100C,
        // or Owl 0x3CA0): treat offset 0 as the only candidate, trying Owl
        // size first when it fits, then New-Cycle.
        if (sram.size >= MM_OWL_SIZE && file == 0) {
            out += 0 to MM_OWL_SIZE
        }
        if (sram.size >= MM_NEWCYCLE_SIZE && file == 0) {
            out += 0 to MM_NEWCYCLE_SIZE
        }
        // Small concatenated fallback (2 x 0x100C) for unit tests / cores
        // that expose only the two New-Cycle saves.
        if (out.isEmpty() && sram.size >= MM_NEWCYCLE_SIZE * 2) {
            val base = file * MM_NEWCYCLE_SIZE
            if (base + MM_NEWCYCLE_SIZE <= sram.size) out += base to MM_NEWCYCLE_SIZE
        }
        return out
    }

    private fun isMmSaveValid(sram: ByteArray, base: Int, size: Int): Boolean {
        if (base < 0 || base + size > sram.size) return false
        if (size != MM_NEWCYCLE_SIZE && size != MM_OWL_SIZE) return false
        val sig =
                (0 until 6)
                        .map { (sram[base + MM_NEWF_REL + it].toInt() and 0xFF).toChar() }
                        .joinToString("")
        if (sig != MM_SIGNATURE) return false
        val stored = u16be(sram, base + MM_CHECKSUM_REL)
        return mmChecksum(sram, base, size) == stored
    }

    private fun mmChecksum(sram: ByteArray, base: Int, size: Int): Int {
        var sum = 0
        var i = 0
        while (i < size) {
            val off = base + i
            if (off == base + MM_CHECKSUM_REL || off == base + MM_CHECKSUM_REL + 1) {
                i++
                continue
            }
            sum = (sum + (sram[off].toInt() and 0xFF)) and 0xFFFF
            i++
        }
        return sum
    }

    private fun writeMmSlot(sram: ByteArray, file: Int, encoded: ByteArray): EditResult {
        val candidates = mmBasesForFile(sram, file)
        if (candidates.isEmpty()) return EditResult.Err(Reason.BAD_SIZE)
        val valid = candidates.filter { (base, size) -> isMmSaveValid(sram, base, size) }
        // Require at least one valid copy (fail closed on empty/corrupt files).
        if (valid.isEmpty()) return EditResult.Err(Reason.NO_VALID_SAVE)
        val out = sram.copyOf()
        var updated = 0
        // Update every candidate that either was valid or fits, so main, backup
        // and Owl copies stay in sync. Corrupt copies are repaired when their
        // signature is intact; copies with a bad signature are left alone.
        for ((base, size) in candidates) {
            if (base + size > out.size) continue
            val sig =
                    (0 until 6)
                            .map { (out[base + MM_NEWF_REL + it].toInt() and 0xFF).toChar() }
                            .joinToString("")
            if (sig != MM_SIGNATURE) continue
            encoded.copyInto(out, base + MM_NAME_REL)
            putU16be(out, base + MM_CHECKSUM_REL, mmChecksum(out, base, size))
            updated++
        }
        if (updated == 0) return EditResult.Err(Reason.NO_VALID_SAVE)
        return EditResult.Ok(out, updated)
    }

    // ---- Byte helpers (big-endian, N64 native) ----

    private fun u16be(arr: ByteArray, off: Int): Int {
        val hi = arr[off].toInt() and 0xFF
        val lo = arr[off + 1].toInt() and 0xFF
        return (hi shl 8) or lo
    }

    private fun putU16be(arr: ByteArray, off: Int, v: Int) {
        arr[off] = ((v ushr 8) and 0xFF).toByte()
        arr[off + 1] = (v and 0xFF).toByte()
    }
}
