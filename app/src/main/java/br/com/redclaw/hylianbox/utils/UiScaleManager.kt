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

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources

/**
 * Global interface scale (1..5, default 3 = current 1.0x scale).
 *
 * The scale is applied by overriding the [Resources] density ([density] and [scaledDensity]) plus
 * [Configuration.fontScale] on every activity via `attachBaseContext(wrap(newBase))` and on the
 * application context at startup.
 *
 * The emulator touch overlay ([br.com.redclaw.hylianbox.gamepad] views and
 * `GameActivityViewModel.setupGamePads`) sizes everything as a fraction of the overlay width/height
 * in physical pixels and reads `Resources.getSystem().displayMetrics.density`, so it is immune to
 * this scale by construction. Placements must stay frozen.
 */
object UiScaleManager {
    const val MIN_SCALE = 1
    const val MAX_SCALE = 5
    const val DEFAULT_SCALE = 3
    const val BASE_SCALE = 3f

    /** Scale factor for a raw scale value (3 -> 1.0x). Clamps to [MIN_SCALE]..[MAX_SCALE]. */
    fun factor(scale: Int): Float = scale.coerceIn(MIN_SCALE, MAX_SCALE) / BASE_SCALE

    /** Persisted scale, delegated to [CorePrefs] (the single source of truth). */
    fun getScale(context: Context): Int = CorePrefs.getUiScale(context)

    fun setScale(context: Context, scale: Int) {
        CorePrefs.setUiScale(context, scale)
    }

    /** Current scale factor for [context]. */
    fun scaleFactor(context: Context): Float = factor(getScale(context))

    /**
     * Wraps [base] in a context whose resources carry the scaled density.
     *
     * Uses [Resources.getSystem] as the base (system density, scaledDensity and fontScale) to avoid
     * compounding when activities are recreated: the factor is always applied once on top of the
     * real device metrics, never on top of an already-scaled context.
     */
    fun wrap(base: Context): Context {
        val scale = CorePrefs.getUiScale(base)
        val f = factor(scale)
        if (f == 1f) return base
        val systemMetrics = Resources.getSystem().displayMetrics
        val systemConfig = Resources.getSystem().configuration
        val config = Configuration(base.resources.configuration)
        config.fontScale = systemConfig.fontScale * f
        val newContext = base.createConfigurationContext(config)
        val metrics = newContext.resources.displayMetrics
        metrics.density = systemMetrics.density * f
        metrics.scaledDensity = systemMetrics.scaledDensity * f
        return newContext
    }

    /**
     * Applies the persisted scale to the app context resources (call from Application.onCreate).
     *
     * Intentionally a no-op: the application context MUST stay on system density so the emulator
     * touch overlay (created with the application context in `GameActivityViewModel.setupGamePads`)
     * remains immune to the global UI scale. Activities receive the scale via [wrap] in
     * `attachBaseContext`.
     */
    fun applyAtStartup(@Suppress("UNUSED_PARAMETER") context: Context) {
        return
    }

    /** Applies [scale] in-place to [res] with the same math as [wrap]. */
    fun applyToResources(res: Resources, scale: Int) {
        applyFactor(res, factor(scale))
    }

    /** Recreates [activity] so the new scale takes effect. */
    fun recreateToApply(activity: Activity) {
        activity.recreate()
    }

    private fun applyFactor(res: Resources, f: Float) {
        if (f == 1f) return
        val systemMetrics = Resources.getSystem().displayMetrics
        val systemConfig = Resources.getSystem().configuration
        val config = res.configuration
        config.fontScale = systemConfig.fontScale * f
        val metrics = res.displayMetrics
        metrics.density = systemMetrics.density * f
        metrics.scaledDensity = systemMetrics.scaledDensity * f
    }
}
