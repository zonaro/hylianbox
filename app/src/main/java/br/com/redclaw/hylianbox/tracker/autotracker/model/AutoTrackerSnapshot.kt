/* Copyright (C) 2026 RedClaw. SPDX-License-Identifier: GPL-3.0-or-later */
package br.com.redclaw.hylianbox.tracker.autotracker.model

import br.com.redclaw.hylianbox.tracker.model.TrackerGame

/** Immutable scalar copy; never retains native memory beyond the core callback. */
data class AutoTrackerSnapshot(
        val game: TrackerGame,
        val inventory: List<Int>,
        val equipment: Int,
        val upgrades: Int,
        val questFlags: Int,
        val magicLevel: Int,
        val biggoronOwned: Boolean = false,
        val equippedItems: EquippedItemsSnapshot = EquippedItemsSnapshot(),
        /** Raw ammo array copy (OOT 16 slots at 0x8C, MM 24 slots at 0xA0). Empty when unknown. */
        val ammo: List<Int> = emptyList()
) {
    /** Whether a game-specific quest bit is set. */
    fun hasQuestFlag(bit: Int): Boolean = questFlags and (1 shl bit) != 0
    /** Extracts a packed upgrade level using its game-specific shift and mask. */
    fun upgrade(shift: Int, mask: Int = 7): Int = (upgrades ushr shift) and mask
}

/** Read-only copy of the item ids currently assigned to the gameplay buttons. */
data class EquippedItemsSnapshot(
        val cLeft: Int = NONE,
        val cDown: Int = NONE,
        val cRight: Int = NONE,
        val sword: Int = NONE,
        val shield: Int = NONE,
        /** Live ammo for each C button (null = item has no quantity, no badge). */
        val cLeftAmmo: Int? = null,
        val cDownAmmo: Int? = null,
        val cRightAmmo: Int? = null
) {
    companion object {
        const val NONE = 0xFF
    }
}
