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

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.content.ContextCompat
import br.com.redclaw.hylianbox.R

/** Marker implemented by an Activity whose dialogs must cover gameplay without replacing it. */
interface GameplayDialogHost

/**
 * Applies the shared presentation contract for every interactive surface opened over gameplay.
 *
 * The surface remains a real [Dialog], so opening it does not navigate away from or recreate the
 * emulator Activity/core. Visually, however, its window is edge-to-edge, opaque, non-floating and
 * uses the same Switch background as normal screens. Back still dismisses through the dialog's
 * normal lifecycle; outside-tap dismissal is disabled because there is no visible outside area.
 */
object GameplayFullscreenDialog {

    /** Returns true through themed [ContextWrapper] layers used by AppCompat dialogs. */
    fun isGameplayContext(context: Context): Boolean {
        var current: Context? = context
        val visited = HashSet<Context>()
        while (current != null && visited.add(current)) {
            if (current is GameplayDialogHost) return true
            current = (current as? ContextWrapper)?.baseContext
        }
        return false
    }

    /** Shows [dialog] and immediately applies the fullscreen gameplay presentation. */
    fun <T : Dialog> show(dialog: T): T {
        dialog.show()
        apply(dialog)
        return dialog
    }

    /** Applies fullscreen sizing and immersive bars to an already-created/shown [dialog]. */
    fun apply(dialog: Dialog) {
        if (!isGameplayContext(dialog.context)) return
        dialog.setCanceledOnTouchOutside(false)
        val window = dialog.window ?: return
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setBackgroundDrawable(
                ColorDrawable(ContextCompat.getColor(dialog.context, R.color.switch_bg))
        )
        window.decorView.setPadding(0, 0, 0, 0)
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        SwitchImmersive.enterFullscreen(window)
    }

    /** Finds the Activity below AppCompat's themed context wrappers. */
    fun activity(context: Context): Activity? {
        var current: Context? = context
        val visited = HashSet<Context>()
        while (current != null && visited.add(current)) {
            if (current is Activity) return current
            current = (current as? ContextWrapper)?.baseContext
        }
        return null
    }
}
