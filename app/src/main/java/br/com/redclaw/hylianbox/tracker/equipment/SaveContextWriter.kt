/* Copyright (C) 2026 RedClaw. SPDX-License-Identifier: GPL-3.0-or-later */
package br.com.redclaw.hylianbox.tracker.equipment

import br.com.redclaw.hylianbox.tracker.autotracker.parser.SaveContextParser
import br.com.redclaw.hylianbox.tracker.model.TrackerGame
import java.nio.ByteBuffer

/** Safe, frame-thread-only writer for the documented OoT/MM NTSC 1.0 SaveContext layouts. */
object SaveContextWriter {
    private data class CItem(
        val inventorySlot: Int,
        val accepted: Set<Int>,
        val buttonItem: Int? = null,
        val equippedSlot: Int = inventorySlot
    )
    private data class Equipment(val shift: Int, val value: Int, val ownedBit: Int?, val bItem: Int? = null)

    private val ootCItems = mapOf(
        "deku_stick" to CItem(0, setOf(0)), "deku_nut" to CItem(1, setOf(1)),
        "bomb_bag" to CItem(2, setOf(2)), "bow" to CItem(3, setOf(3)),
        "fire_arrows" to CItem(4, setOf(4), 0x38, 3), "din_fire" to CItem(5, setOf(5)),
        "slingshot" to CItem(6, setOf(6)), "ocarina" to CItem(7, setOf(7, 8)),
        "bombchu" to CItem(8, setOf(9)), "hookshot_longshot" to CItem(9, setOf(10, 11)),
        "ice_arrows" to CItem(10, setOf(12), 0x39, 3), "farore_wind" to CItem(11, setOf(13)),
        "boomerang" to CItem(12, setOf(14)), "lens_of_truth" to CItem(13, setOf(15)),
        "megaton_hammer" to CItem(15, setOf(17)), "light_arrows" to CItem(16, setOf(18), 0x3A, 3),
        "nayru_love" to CItem(17, setOf(19)),
        "keaton_mask" to CItem(23, setOf(0x24)), "skull_mask" to CItem(23, setOf(0x25)),
        "spooky_mask" to CItem(23, setOf(0x26)), "bunny_hood" to CItem(23, setOf(0x27)),
        "goron_mask" to CItem(23, setOf(0x28)), "zora_mask" to CItem(23, setOf(0x29)),
        "gerudo_mask" to CItem(23, setOf(0x2A)), "mask_of_truth" to CItem(23, setOf(0x2B))
    )

    private val mmCItems = mapOf(
        "ocarina_of_time" to CItem(0, setOf(0, 5)), "bow" to CItem(1, setOf(1)),
        "fire_arrows" to CItem(2, setOf(2), 0x4A, 1), "ice_arrows" to CItem(3, setOf(3), 0x4B, 1),
        "light_arrows" to CItem(4, setOf(4), 0x4C, 1), "bomb_bag" to CItem(6, setOf(6)),
        "bombchu" to CItem(7, setOf(7)), "deku_stick" to CItem(8, setOf(8)),
        "deku_nut" to CItem(9, setOf(9)), "magic_bean" to CItem(10, setOf(10)),
        "pictobox" to CItem(13, setOf(13)), "lens_of_truth" to CItem(14, setOf(14)),
        "hookshot" to CItem(15, setOf(15)), "great_fairy_sword" to CItem(16, setOf(16)),
        "postman_hat" to CItem(0x18, setOf(0x3E)), "all_night_mask" to CItem(0x19, setOf(0x38)),
        "blast_mask" to CItem(0x1A, setOf(0x47)), "stone_mask" to CItem(0x1B, setOf(0x45)),
        "great_fairy_mask" to CItem(0x1C, setOf(0x40)), "deku_mask" to CItem(0x1D, setOf(0x32)),
        "keaton_mask" to CItem(0x1E, setOf(0x3A)), "bremen_mask" to CItem(0x1F, setOf(0x46)),
        "bunny_hood" to CItem(0x20, setOf(0x39)), "don_gero_mask" to CItem(0x21, setOf(0x42)),
        "mask_of_scents" to CItem(0x22, setOf(0x48)), "goron_mask" to CItem(0x23, setOf(0x33)),
        "romani_mask" to CItem(0x24, setOf(0x3C)), "circus_leader_mask" to CItem(0x25, setOf(0x3D)),
        "kafei_mask" to CItem(0x26, setOf(0x37)), "couples_mask" to CItem(0x27, setOf(0x3F)),
        "mask_of_truth" to CItem(0x28, setOf(0x36)), "zora_mask" to CItem(0x29, setOf(0x34)),
        "kamaro_mask" to CItem(0x2A, setOf(0x43)), "gibdo_mask" to CItem(0x2B, setOf(0x41)),
        "garo_mask" to CItem(0x2C, setOf(0x3B)), "captains_hat" to CItem(0x2D, setOf(0x44)),
        "giants_mask" to CItem(0x2E, setOf(0x49)), "fierce_deity_mask" to CItem(0x2F, setOf(0x35))
    )

    private val ootEquipment = mapOf(
        "kokiri_sword" to Equipment(0, 1, 0, 0x3B), "master_sword" to Equipment(0, 2, 1, 0x3C),
        "biggoron_sword" to Equipment(0, 3, null, 0x3D),
        "deku_shield" to Equipment(4, 1, 4), "hylian_shield" to Equipment(4, 2, 5),
        "mirror_shield" to Equipment(4, 3, 6), "kokiri_tunic" to Equipment(8, 1, 8),
        "goron_tunic" to Equipment(8, 2, 9), "zora_tunic" to Equipment(8, 3, 10),
        "iron_boots" to Equipment(12, 2, 13), "hover_boots" to Equipment(12, 3, 14)
    )

    fun action(game: TrackerGame, itemId: String): TrackerEquipAction = when {
        (if (game == TrackerGame.OOT) ootCItems else mmCItems).containsKey(itemId) -> TrackerEquipAction.C_ITEM
        game == TrackerGame.OOT && ootEquipment.containsKey(itemId) -> TrackerEquipAction.OOT_EQUIPMENT
        else -> TrackerEquipAction.NONE
    }

    fun apply(memory: ByteBuffer, command: TrackerEquipCommand, baseOffset: Int = SaveContextParser.base(command.game)): Boolean {
        if (SaveContextParser.parse(memory, command.game, baseOffset) == null) return false
        val lane = SaveContextParser.detectByteLane(memory, command.game, baseOffset) ?: return false
        fun u8(offset: Int) = memory.get((baseOffset + offset) xor lane).toInt() and 0xFF
        fun put8(offset: Int, value: Int) { memory.put((baseOffset + offset) xor lane, value.toByte()) }
        fun u16(offset: Int) = (u8(offset) shl 8) or u8(offset + 1)
        fun put16(offset: Int, value: Int) { put8(offset, value ushr 8); put8(offset + 1, value) }

        command.cButton?.let { target ->
            val spec = (if (command.game == TrackerGame.OOT) ootCItems else mmCItems)[command.itemId] ?: return false
            val inventoryOffset = if (command.game == TrackerGame.OOT) 0x74 else 0x70
            val inventoryItem = u8(inventoryOffset + spec.inventorySlot)
            if (inventoryItem !in spec.accepted) return false
            val buttonItem = spec.buttonItem ?: inventoryItem
            if (command.game == TrackerGame.OOT) {
                val index = when (target) { TrackerCButton.LEFT -> 0; TrackerCButton.DOWN -> 1; TrackerCButton.RIGHT -> 2 }
                swapOrAssign(index, buttonItem, spec.equippedSlot, 0x69, 0x6C, 0..2, ::u8, ::put8)
            } else {
                val index = when (target) { TrackerCButton.LEFT -> 1; TrackerCButton.DOWN -> 2; TrackerCButton.RIGHT -> 3 }
                swapOrAssign(index, buttonItem, spec.equippedSlot, 0x4C, 0x5C, 1..3, ::u8, ::put8)
            }
            return true
        }

        if (command.game != TrackerGame.OOT) return false
        val spec = ootEquipment[command.itemId] ?: return false
        val owned = u16(0x9C)
        if (spec.ownedBit?.let { owned and (1 shl it) == 0 } == true) return false
        if (command.itemId == "biggoron_sword" && u8(0x3E) != 1) return false
        val current = u16(0x70)
        put16(0x70, (current and (0xF shl spec.shift).inv()) or (spec.value shl spec.shift))
        spec.bItem?.let { put8(0x68, it) }
        return true
    }

    private fun swapOrAssign(
        target: Int, item: Int, slot: Int, buttonOffset: Int, slotOffset: Int,
        range: IntRange, read: (Int) -> Int, write: (Int, Int) -> Unit
    ) {
        range.firstOrNull { it != target && read(slotOffset + it) == slot }?.let { source ->
            write(buttonOffset + source, read(buttonOffset + target))
            write(slotOffset + source, read(slotOffset + target))
        }
        write(buttonOffset + target, item)
        write(slotOffset + target, slot)
    }
}
