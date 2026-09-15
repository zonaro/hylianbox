/* Copyright (C) 2026 RedClaw. SPDX-License-Identifier: GPL-3.0-or-later */
package br.com.redclaw.hylianbox.tracker.equipment

import br.com.redclaw.hylianbox.tracker.model.TrackerGame

enum class TrackerCButton { RIGHT, DOWN, LEFT }

enum class TrackerEquipAction { C_ITEM, OOT_EQUIPMENT, NONE }

data class TrackerEquipCommand(
    val game: TrackerGame,
    val itemId: String,
    val cButton: TrackerCButton? = null
)

interface TrackerEquipmentHost {
    fun enqueueTrackerEquip(command: TrackerEquipCommand): Boolean
}
