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
 * The scale is applied by overriding the [Resources] density ([density], [densityDpi] and
 * [scaledDensity]) on every activity via `attachBaseContext(wrap(newBase))`.
 *
 * The density is encoded in [Configuration.densityDpi] (not only in the mutable [DisplayMetrics]):
 * wrappers applied after ours (AppCompatDelegate night-mode/locale) re-derive metrics from the
 * [Configuration], so a metrics-only change would be lost for dp layouts while [fontScale] survived
 * — which is why only fonts scaled. [fontScale] itself is kept at the system value so text scales
 * exactly like the rest of the layout (via the scaled density), without double scaling.
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
        // Keep the user's font choice untouched: text scales exactly like the
        // rest of the layout through the scaled density below, without doubling.
        config.fontScale = systemConfig.fontScale
        // Encode the scale in densityDpi so it survives later wrappers
        // (AppCompat night-mode/locale) that re-derive DisplayMetrics from the
        // Configuration. A metrics-only change would be reset for dp layouts
        // while fontScale survived, scaling fonts alone.
        config.densityDpi = (systemMetrics.densityDpi * f).toInt()
        val newContext = base.createConfigurationContext(config)
        val metrics = newContext.resources.displayMetrics
        metrics.density = systemMetrics.density * f
        metrics.densityDpi = (systemMetrics.densityDpi * f).toInt()
        // Preserve the user's font-size ratio: scaledDensity follows density,
        // so sp text scales exactly like dp layout (no double scaling).
        metrics.scaledDensity =
                metrics.density * (systemMetrics.scaledDensity / systemMetrics.density)
        return newContext
    }

    /**
     * Applies the persisted scale to an AppCompat override configuration.
     *
     * Called from `ScaledAppCompatActivity.applyOverrideConfiguration`: AppCompatDelegate
     * re-applies night-mode/locale configurations after `attachBaseContext`, deriving metrics from
     * the base [Configuration] and discarding a metrics-only density change. Encoding the scale in
     * [Configuration.densityDpi] here keeps dp layouts scaled. The system fontScale is left
     * untouched (preserved by [wrap]) so text does not scale twice.
     */
    fun applyToOverrideConfig(config: Configuration, context: Context) {
        val f = factor(getScale(context))
        if (f == 1f) return
        config.densityDpi = (Resources.getSystem().displayMetrics.densityDpi * f).toInt()
    }

    /**
     * Re-ensures the scaled density on already-created [Resources].
     *
     * Idempotent and free of `updateConfiguration` (avoids recursion via `getResources`): when the
     * current density differs from the expected system density times the scale factor, sets
     * density, densityDpi and scaledDensity, preserving the user's font-size ratio. No-op when the
     * factor is 1x.
     */
    fun ensureScaled(res: Resources, context: Context) {
        val f = factor(getScale(context))
        if (f == 1f) return
        val systemMetrics = Resources.getSystem().displayMetrics
        val expected = systemMetrics.density * f
        val metrics = res.displayMetrics
        if (metrics.density != expected) {
            metrics.density = expected
            metrics.densityDpi = (systemMetrics.densityDpi * f).toInt()
            metrics.scaledDensity =
                    metrics.density * (systemMetrics.scaledDensity / systemMetrics.density)
        }
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
        config.fontScale = systemConfig.fontScale
        config.densityDpi = (systemMetrics.densityDpi * f).toInt()
        res.updateConfiguration(config, res.displayMetrics)
        val metrics = res.displayMetrics
        metrics.density = systemMetrics.density * f
        metrics.densityDpi = (systemMetrics.densityDpi * f).toInt()
        metrics.scaledDensity =
                metrics.density * (systemMetrics.scaledDensity / systemMetrics.density)
    }
}
