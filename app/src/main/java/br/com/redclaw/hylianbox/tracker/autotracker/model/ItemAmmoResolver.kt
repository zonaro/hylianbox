/* Copyright (C) 2026 RedClaw. SPDX-License-Identifier: GPL-3.0-or-later */
package br.com.redclaw.hylianbox.tracker.autotracker.model

import br.com.redclaw.hylianbox.tracker.model.TrackerGame

/**
 * Pure resolver mapping an equipped button item id to its live ammo count.
 *
 * Mirrors the games' own HUD rules (`Interface_DrawAmmoCount` in zeldaret/oot
 * `src/code/z_parameter.c` and zeldaret/mm `src/code/z_parameter.c`): only items with a quantity
 * concept return a count, everything else returns null (no badge).
 *
 * OOT button ids: 0x00 stick, 0x01 nut, 0x02 bomb, 0x03 bow, 0x38-0x3A fire/ice/light (share bow
 * ammo), 0x06 slingshot, 0x09 bombchu, 0x10 magic bean. MM button ids: 0x01 bow, 0x4A-0x4C
 * fire/ice/light (share bow ammo), 0x06 bomb, 0x07 bombchu, 0x08 stick, 0x09 nut, 0x0A beans, 0x0C
 * powder keg, 0x0D pictograph (quest flag, not the ammo array).
 */
object ItemAmmoResolver {
    private const val NONE = 0xFF
    private const val MM_QUEST_PICTOGRAPH_BIT = 0x19

    fun ammoFor(game: TrackerGame, itemId: Int, ammo: List<Int>, questFlags: Int): Int? {
        if (itemId == NONE) return null
        if (ammo.isEmpty()) return null
        fun at(index: Int): Int? = ammo.getOrNull(index)
        return when (game) {
            TrackerGame.OOT ->
                    when (itemId) {
                        0x00 -> at(0)
                        0x01 -> at(1)
                        0x02 -> at(2)
                        0x03, 0x38, 0x39, 0x3A -> at(3)
                        0x06 -> at(6)
                        0x09 -> at(8)
                        0x10 -> at(14)
                        else -> null
                    }
            TrackerGame.MM ->
                    when (itemId) {
                        0x01, 0x4A, 0x4B, 0x4C -> at(1)
                        0x06 -> at(6)
                        0x07 -> at(7)
                        0x08 -> at(8)
                        0x09 -> at(9)
                        0x0A -> at(10)
                        0x0C -> at(12)
                        0x0D -> if (questFlags and (1 shl MM_QUEST_PICTOGRAPH_BIT) != 0) 1 else 0
                        else -> null
                    }
        }
    }
}
