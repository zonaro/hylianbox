/*
 * HylianBox - native Android N64 emulator frontend for Zelda ROM hacks.
 * Copyright (C) 2026 RedClaw
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package br.com.redclaw.hylianbox.ui.switchui

/** Pure modality policy for the touch-only circular close affordance. */
internal object TouchClosePolicy {
    fun initial(hasTouchscreen: Boolean, hasController: Boolean): Boolean =
            hasTouchscreen && !hasController

    fun afterTouch(hasTouchscreen: Boolean): Boolean = hasTouchscreen

    fun afterNonTouch(): Boolean = false
}
