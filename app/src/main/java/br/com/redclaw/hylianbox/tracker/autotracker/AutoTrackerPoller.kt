/* Copyright (C) 2026 RedClaw. SPDX-License-Identifier: GPL-3.0-or-later */
package br.com.redclaw.hylianbox.tracker.autotracker

import br.com.redclaw.hylianbox.tracker.autotracker.model.AutoTrackerSnapshot
import br.com.redclaw.hylianbox.tracker.autotracker.model.EquippedItemsSnapshot
import br.com.redclaw.hylianbox.tracker.autotracker.parser.SaveContextParser
import br.com.redclaw.hylianbox.tracker.equipment.SaveContextWriter
import br.com.redclaw.hylianbox.tracker.equipment.TrackerEquipCommand
import br.com.redclaw.hylianbox.tracker.model.TrackerGame
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Single GL-thread reader. The owner obtains RAM outside frameCallback (getMemoryRegion locks
 * coreLock), installs the composite callback, then removes it before dropping this owner/unload.
 * Native RAM never escapes onSnapshot. Time throttling also works with PAL and fast-forward.
 */
class AutoTrackerPoller(
        private val memory: ByteBuffer,
        private val game: TrackerGame,
        private val enabled: () -> Boolean,
        private val onSnapshot: (AutoTrackerSnapshot) -> Unit,
        private val nanoTime: () -> Long = System::nanoTime,
        private val onEquipment: (EquippedItemsSnapshot) -> Unit = {}
) {
    private var lastPoll: Long? = null
    private var lastSnapshot: AutoTrackerSnapshot? = null
    private var lastEquipment: EquippedItemsSnapshot? = null

    @Volatile private var invalidated = false
    private val equipCommands = ConcurrentLinkedQueue<TrackerEquipCommand>()

    fun enqueueEquip(command: TrackerEquipCommand): Boolean {
        if (command.game != game) return false
        equipCommands.offer(command)
        return true
    }
    /** Requests a fresh emission on the next poll, including rapid global OFF/ON toggles. */
    fun invalidate() {
        invalidated = true
    }

    /** Reads only while the caller owns coreLock; never invokes locking core APIs. */
    fun onFrame() {
        while (true) {
            val command = equipCommands.poll() ?: break
            runCatching { SaveContextWriter.apply(memory, command) }
        }
        if (invalidated) {
            lastSnapshot = null
            lastEquipment = null
            invalidated = false
        }
        val now = nanoTime()
        if (lastPoll?.let { now - it < 100_000_000L } == true) return
        lastPoll = now
        val snapshot = SaveContextParser.parse(memory, game) ?: return
        val equippedChanged = snapshot.equippedItems != lastEquipment
        if (equippedChanged) {
            lastEquipment = snapshot.equippedItems
            onEquipment(snapshot.equippedItems)
        }
        if (!enabled()) {
            lastSnapshot = null
            return
        }
        if (snapshot == lastSnapshot) return
        lastSnapshot = snapshot
        onSnapshot(snapshot)
    }
}
