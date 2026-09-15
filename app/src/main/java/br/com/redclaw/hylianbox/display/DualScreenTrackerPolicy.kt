/*
 * HylianBox - native Android N64 emulator frontend for Zelda ROM hacks.
 * Copyright (C) 2026 RedClaw
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package br.com.redclaw.hylianbox.display

import java.util.concurrent.CopyOnWriteArrayList

/** Decides when the non-game display must be reserved for the live item tracker. */
object DualScreenTrackerPolicy {
    fun shouldPinTracker(
            gameIsOnAnotherDisplay: Boolean,
            physicalControllerConnected: Boolean,
            trackerSupported: Boolean
    ): Boolean =
            gameIsOnAnotherDisplay && physicalControllerConnected && trackerSupported
}

/** Process-local signal for game video sent to a remote streaming screen. */
object RemoteGameDisplayState {
    private val listeners = CopyOnWriteArrayList<(Boolean) -> Unit>()

    @Volatile var isStreaming: Boolean = false
        private set

    fun setStreaming(active: Boolean) {
        if (isStreaming == active) return
        isStreaming = active
        listeners.forEach { it(active) }
    }

    fun addListener(listener: (Boolean) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (Boolean) -> Unit) {
        listeners.remove(listener)
    }
}
