/* Copyright (C) 2026 RedClaw. SPDX-License-Identifier: GPL-3.0-or-later */
package br.com.redclaw.hylianbox.tracker.autotracker.parser

import br.com.redclaw.hylianbox.tracker.autotracker.model.AutoTrackerSnapshot
import br.com.redclaw.hylianbox.tracker.model.TrackerGame
import java.nio.ByteBuffer

/**
 * Resolves a relocated vanilla-layout SaveContext without scanning all RDRAM in one frame.
 *
 * Many ROM hacks preserve the original structure but link it at a different RDRAM address. The
 * vanilla address remains the fast path. On failure, candidates are visited outwards from that
 * address in small, four-byte-aligned batches. A signature match is never sufficient by itself:
 * [SaveContextParser.parse] must also accept the candidate's game mode, health, magic and bounds.
 *
 * This deliberately does not guess layouts that insert or remove fields inside SaveContext.
 */
class SaveContextLocator(
        private val game: TrackerGame,
        private val preferredBase: Int = SaveContextParser.base(game),
        private val candidatesPerPass: Int = DEFAULT_CANDIDATES_PER_PASS
) {
    init {
        require(candidatesPerPass > 0)
    }

    var resolvedBase: Int? = null
        private set

    private var searchDistance = 0
    private var searchExhausted = false
    private val signatureCandidates = ArrayList<Int>(MAX_SIGNATURE_CANDIDATES)

    /** Returns a validated snapshot when the fixed or dynamically located context is ready. */
    fun parse(buffer: ByteBuffer): AutoTrackerSnapshot? {
        resolvedBase?.let { resolved ->
            return SaveContextParser.parse(buffer, game, resolved)
        }

        SaveContextParser.parse(buffer, game, preferredBase)?.let { snapshot ->
            resolvedBase = preferredBase
            return snapshot
        }

        signatureCandidates.forEach { candidate ->
            SaveContextParser.parse(buffer, game, candidate)?.let { snapshot ->
                resolvedBase = candidate
                return snapshot
            }
        }
        if (searchExhausted) return null

        var visited = 0
        while (visited < candidatesPerPass) {
            searchDistance += ALIGNMENT
            val lower = preferredBase.toLong() - searchDistance
            val upper = preferredBase.toLong() + searchDistance
            if (lower < 0 && upper >= buffer.limit().toLong()) {
                searchExhausted = true
                return null
            }

            if (lower >= 0) {
                parseCandidate(buffer, lower.toInt())?.let { return it }
                visited++
            }
            if (visited < candidatesPerPass && upper < buffer.limit()) {
                parseCandidate(buffer, upper.toInt())?.let { return it }
                visited++
            }
        }
        return null
    }

    private fun parseCandidate(buffer: ByteBuffer, candidate: Int): AutoTrackerSnapshot? {
        if (SaveContextParser.detectByteLane(buffer, game, candidate) == null) return null
        if (signatureCandidates.size < MAX_SIGNATURE_CANDIDATES &&
                        candidate !in signatureCandidates) {
            signatureCandidates.add(candidate)
        }
        return SaveContextParser.parse(buffer, game, candidate)?.also { resolvedBase = candidate }
    }

    internal companion object {
        private const val ALIGNMENT = 4
        private const val MAX_SIGNATURE_CANDIDATES = 16

        // At 10 polls/second this checks about 1.25 MiB of address distance per second while
        // keeping the work bounded on the core frame thread.
        const val DEFAULT_CANDIDATES_PER_PASS = 32 * 1024
    }
}
