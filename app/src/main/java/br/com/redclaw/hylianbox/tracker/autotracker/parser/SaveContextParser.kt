/* Copyright (C) 2026 RedClaw. SPDX-License-Identifier: GPL-3.0-or-later */
package br.com.redclaw.hylianbox.tracker.autotracker.parser

import br.com.redclaw.hylianbox.tracker.autotracker.model.AutoTrackerSnapshot
import br.com.redclaw.hylianbox.tracker.autotracker.model.EquippedItemsSnapshot
import br.com.redclaw.hylianbox.tracker.autotracker.model.ItemAmmoResolver
import br.com.redclaw.hylianbox.tracker.model.TrackerGame
import java.nio.ByteBuffer

/**
 * Layout facts: zeldaret/oot include/save.h and zeldaret/mm include/z64save.h. Initial scope is the
 * NTSC 1.0 layouts. A matching save signature is required even for hacks. RDRAM may expose N64
 * bytes directly or reversed within 32-bit host words. Detect using the save signature, not
 * ByteBuffer.order (which cannot correct byte-addressed inventory slots).
 */
object SaveContextParser {
    private val byteLanes = intArrayOf(0, 3, 1)

    /** Fixed physical RDRAM offset for the supported NTSC layout. */
    fun base(game: TrackerGame): Int = if (game == TrackerGame.OOT) 0x11A5D0 else 0x1EF670

    /** Detects the byte-address lane after validating the game signature. */
    fun detectByteLane(buffer: ByteBuffer, game: TrackerGame, baseOffset: Int = base(game)): Int? {
        val signatureOffset = if (game == TrackerGame.OOT) 0x1C else 0x24
        val signature = if (game == TrackerGame.OOT) "ZELDAZ" else "ZELDA3"
        if (baseOffset < 0 || baseOffset > buffer.limit() - signatureOffset - signature.length)
                return null
        for (xor in byteLanes) {
            var matches = true
            for (i in signature.indices) {
                if ((buffer.get((baseOffset + signatureOffset + i) xor xor).toInt() and 255) !=
                                signature[i].code) {
                    matches = false
                    break
                }
            }
            if (matches) return xor
        }
        return null
    }

    /** Returns a scalar snapshot, or null for incompatible, uninitialized or menu RAM. */
    fun parse(
            buffer: ByteBuffer,
            game: TrackerGame,
            baseOffset: Int = base(game)
    ): AutoTrackerSnapshot? {
        val oot = game == TrackerGame.OOT
        val gameModeOffset = if (oot) 0x135C else 0x3CA8
        val required = gameModeOffset + 4
        if (baseOffset < 0 || baseOffset > buffer.limit() - required || baseOffset % 4 != 0)
                return null
        val lane = detectByteLane(buffer, game, baseOffset) ?: return null
        fun u8(offset: Int): Int = buffer.get((baseOffset + offset) xor lane).toInt() and 255
        fun u16(offset: Int): Int = (u8(offset) shl 8) or u8(offset + 1)
        fun u32(offset: Int): Int = (u16(offset) shl 16) or u16(offset + 2)
        if (u32(gameModeOffset) != 0) return null
        val health = u16(if (oot) 0x2E else 0x34)
        val magic = u8(if (oot) 0x3A else 0x40)
        val doubleMagic = u8(if (oot) 0x3C else 0x41)
        // Reject title screens, uninitialized RAM, and incompatible modified save layouts.
        if (health !in 0x10..0x140 || health % 0x10 != 0 || magic !in 0..1 || doubleMagic !in 0..1)
                return null
        val itemsOffset = if (oot) 0x74 else 0x70
        val currentEquipment = u16(if (oot) 0x70 else 0x6C)
        val equipment = u16(if (oot) 0x9C else 0x6C)
        val buttonsOffset = if (oot) 0x68 else 0x4C
        val upgradesVal = u32(if (oot) 0xA0 else 0xB8)
        val questFlags = u32(if (oot) 0xA4 else 0xBC)
        val ammoOffset = if (oot) 0x8C else 0xA0
        val ammoSize = if (oot) 16 else 24
        val ammo = List(ammoSize) { u8(ammoOffset + it) }
        val cLeftId = u8(buttonsOffset + 1)
        val cDownId = u8(buttonsOffset + 2)
        val cRightId = u8(buttonsOffset + 3)
        val equipped =
                EquippedItemsSnapshot(
                        cLeft = cLeftId,
                        cDown = cDownId,
                        cRight = cRightId,
                        sword = swordItem(game, currentEquipment and 0xF),
                        shield = shieldItem(game, (currentEquipment ushr 4) and 0xF),
                        cLeftAmmo = ItemAmmoResolver.ammoFor(game, cLeftId, ammo, questFlags),
                        cDownAmmo = ItemAmmoResolver.ammoFor(game, cDownId, ammo, questFlags),
                        cRightAmmo = ItemAmmoResolver.ammoFor(game, cRightId, ammo, questFlags)
                )
        return AutoTrackerSnapshot(
                game,
                List(if (oot) 24 else 48) { u8(itemsOffset + it) },
                equipment,
                upgradesVal,
                questFlags,
                if (magic == 0) 0 else 1 + doubleMagic,
                oot && u8(0x3E) == 1,
                equipped,
                ammo
        )
    }

    private fun swordItem(game: TrackerGame, level: Int): Int =
            when (game) {
                TrackerGame.OOT -> if (level in 1..3) 0x3A + level else EquippedItemsSnapshot.NONE
                TrackerGame.MM -> if (level in 1..4) 0x4C + level else EquippedItemsSnapshot.NONE
            }

    private fun shieldItem(game: TrackerGame, level: Int): Int =
            when (game) {
                TrackerGame.OOT -> if (level in 1..3) 0x3D + level else EquippedItemsSnapshot.NONE
                TrackerGame.MM -> if (level in 1..2) 0x50 + level else EquippedItemsSnapshot.NONE
            }
}
