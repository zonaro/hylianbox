/*
 * HylianBox - native Android N64 emulator frontend for Zelda ROM hacks.
 * Copyright (C) 2026 RedClaw
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package br.com.redclaw.hylianbox.utils

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import br.com.redclaw.hylianbox.ui.switchui.AccentManager
import br.com.redclaw.hylianbox.ui.switchui.SwitchBackButton

/**
 * Base activity applying the global interface scale ([UiScaleManager]).
 *
 * Wraps the base context via [UiScaleManager.wrap], re-applies the scaled densityDpi to every
 * override configuration from AppCompatDelegate (night-mode/locale) and re-ensures the scaled
 * density on every [getResources] call, so dp layouts scale together with sp text. The emulator
 * touch overlay stays excluded by construction (application context, system density).
 */
open class ScaledAppCompatActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply the persisted accent before AppCompat inflates or tints any view. This is the
        // global fallback for platform/Material controls; custom Switch views still use
        // AccentManager directly for their runtime-created drawables.
        setTheme(AccentManager.getThemeOverlay(this))
        super.onCreate(savedInstanceState)
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(UiScaleManager.wrap(newBase))
    }

    override fun applyOverrideConfiguration(overrideConfiguration: Configuration?) {
        overrideConfiguration?.let { UiScaleManager.applyToOverrideConfig(it, this) }
        super.applyOverrideConfiguration(overrideConfiguration)
    }

    override fun getResources(): Resources {
        val res = super.getResources()
        UiScaleManager.ensureScaled(res, this)
        return res
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        SwitchBackButton.dispatchTouch(this, ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) SwitchBackButton.dispatchNonTouch(this)
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        SwitchBackButton.dispatchNonTouch(this)
        return super.dispatchGenericMotionEvent(ev)
    }
}
