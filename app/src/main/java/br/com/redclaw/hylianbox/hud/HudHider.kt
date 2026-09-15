/* Copyright (C) 2026 RedClaw. SPDX-License-Identifier: GPL-3.0-or-later */
package br.com.redclaw.hylianbox.hud

import br.com.redclaw.hylianbox.tracker.autotracker.parser.SaveContextParser
import br.com.redclaw.hylianbox.tracker.model.TrackerGame
import java.nio.ByteBuffer

/**
 * Per-session writer that hides the C and B gameplay buttons through the games' native HUD
 * visibility transition. The requested mode is reinforced if game code changes it, and the full
 * HUD is restored once when the feature stops applying.
 *
 * Calls happen inside the core-owned frame callback. The supplied buffer must not be retained
 * beyond that callback.
 */
class HudHider {
    private companion object {
        /** OoT `SaveContext` field offsets (NTSC 1.0 layout). */
        const val OOT_NEXT_HUD_VISIBILITY_OFFSET = 0x13E8

        /** MM `SaveContext` field offsets (US layout). */
        const val MM_NEXT_HUD_VISIBILITY_OFFSET = 0x3F20

        /** Timer follows next visibility by four bytes in both supported layouts. */
        const val HUD_VISIBILITY_TIMER_OFFSET_DELTA = 4

        /**
         * HUD visibility that keeps A + hearts + magic + minimap but drops B and C: OoT
         * `HUD_VISIBILITY_A_HEARTS_MAGIC_MINIMAP_FORCE` (6) dims B/C via
         * `Interface_DimButtonAlphas` and raises A/hearts/magic; MM
         * `HUD_VISIBILITY_A_HEARTS_MAGIC_MINIMAP_WITH_OVERWRITE` (6) does the same via
         * `Interface_UpdateButtonAlphas`.
         */
        const val HUD_A_HEARTS_MAGIC_MINIMAP: Short = 6

        /** Full HUD restore used when the feature is off or a controller is connected. */
        const val HUD_ALL: Short = 50
    }

    private var hidingApplied = false

    private fun nextHudVisibilityOffset(game: TrackerGame): Int =
            if (game == TrackerGame.OOT) OOT_NEXT_HUD_VISIBILITY_OFFSET
            else MM_NEXT_HUD_VISIBILITY_OFFSET

    /**
     * Call once per frame from the composited frame callback. [baseOffset] may point to a relocated
     * but otherwise compatible SaveContext. Returns true only when a visibility transition was
     * written.
     */
    fun onFrame(
            memory: ByteBuffer,
            game: TrackerGame,
            enabled: Boolean,
            hasPhysicalController: Boolean,
            isHardcoreEnabled: Boolean,
            baseOffset: Int = SaveContextParser.base(game)
    ): Boolean {
        val hide = enabled && !hasPhysicalController && !isHardcoreEnabled
        if (!hide && !hidingApplied) return false
        // Only act during normal gameplay; parse() rejects menu/title/incompatible RAM.
        if (SaveContextParser.parse(memory, game, baseOffset) == null) return false
        val lane = SaveContextParser.detectByteLane(memory, game, baseOffset) ?: return false
        val visOffset = nextHudVisibilityOffset(game)
        val target = if (hide) HUD_A_HEARTS_MAGIC_MINIMAP else HUD_ALL
        val currentOffset = visOffset + 2
        val timerOffset = visOffset + HUD_VISIBILITY_TIMER_OFFSET_DELTA
        if (!hasU16(memory, baseOffset, visOffset, lane) ||
                        !hasU16(memory, baseOffset, currentOffset, lane) ||
                        !hasU16(memory, baseOffset, timerOffset, lane)) {
            return false
        }

        if (getU16(memory, baseOffset, currentOffset, lane) == target.toInt()) {
            if (!hide) hidingApplied = false
            return false
        }

        // Mirrors Interface_ChangeHudVisibilityMode: current + next are updated together and the
        // timer starts at one. Without the timer, the first alpha step leaves B/C fully visible.
        putU16(memory, baseOffset, visOffset, lane, target)
        putU16(memory, baseOffset, currentOffset, lane, target)
        putU16(memory, baseOffset, timerOffset, lane, 1)
        hidingApplied = hide
        return true
    }

    private fun hasU16(buffer: ByteBuffer, base: Int, offset: Int, lane: Int): Boolean =
            ((base + offset) xor lane) in 0 until buffer.limit() &&
                    ((base + offset + 1) xor lane) in 0 until buffer.limit()

    private fun getU16(buffer: ByteBuffer, base: Int, offset: Int, lane: Int): Int =
            ((buffer.get((base + offset) xor lane).toInt() and 0xFF) shl 8) or
                    (buffer.get((base + offset + 1) xor lane).toInt() and 0xFF)

    /** Writes both logical bytes through the detected lane without moving buffer position. */
    private fun putU16(buffer: ByteBuffer, base: Int, offset: Int, lane: Int, value: Number) {
        val v = value.toInt()
        buffer.put((base + offset) xor lane, ((v ushr 8) and 0xFF).toByte())
        buffer.put((base + offset + 1) xor lane, (v and 0xFF).toByte())
    }
}
