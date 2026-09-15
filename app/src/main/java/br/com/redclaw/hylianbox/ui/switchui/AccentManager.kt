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

package br.com.redclaw.hylianbox.ui.switchui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import androidx.core.content.ContextCompat
import br.com.redclaw.hylianbox.R
import br.com.redclaw.hylianbox.utils.CorePrefs

/**
 * Manages the Nintendo Switch UI accent color.
 *
 * The accent color is user-selectable from a predefined palette and persisted in
 * [CorePrefs] (key `pref_switch_accent`). This class provides the resolved color
 * int for the current accent, plus helper drawables (focus borders, badge backgrounds)
 * that use the dynamic accent color instead of the static `switch_accent` resource.
 *
 * The palette keys match the color resource names in `colors.xml` (e.g., "cyan",
 * "green_light", "blue") and the display strings in `strings.xml`.
 */
object AccentManager {

    /** All available accent options, in display order. */
    val options: List<AccentOption> = listOf(
        AccentOption("cyan", R.string.accent_cyan, R.color.accent_cyan),
        AccentOption("green_light", R.string.accent_green_light, R.color.accent_green_light),
        AccentOption("green_dark", R.string.accent_green_dark, R.color.accent_green_dark),
        AccentOption("blue", R.string.accent_blue, R.color.accent_blue),
        AccentOption("yellow", R.string.accent_yellow, R.color.accent_yellow),
        AccentOption("pink", R.string.accent_pink, R.color.accent_pink),
        AccentOption("red", R.string.accent_red, R.color.accent_red),
        AccentOption("violet", R.string.accent_violet, R.color.accent_violet),
        AccentOption("teal", R.string.accent_teal, R.color.accent_teal),
        AccentOption("orange", R.string.accent_orange, R.color.accent_orange),
        AccentOption("purple", R.string.accent_purple, R.color.accent_purple),
        AccentOption("indigo", R.string.accent_indigo, R.color.accent_indigo),
    )

    /** Returns the currently selected accent key (e.g., "cyan"). */
    fun getCurrentAccentKey(context: Context): String =
        CorePrefs.getSwitchAccent(context)

    /** Sets the accent color and persists it. */
    fun setAccent(context: Context, accentKey: String) {
        CorePrefs.setSwitchAccent(context, accentKey)
    }

    /** Returns the resolved color int for the current accent. */
    fun getAccentColor(context: Context): Int {
        val key = getCurrentAccentKey(context)
        val option = options.find { it.key == key } ?: options.first()
        return ContextCompat.getColor(context, option.colorRes)
    }

    /** Returns the resolved color int for a specific accent key. */
    fun getAccentColorForKey(context: Context, accentKey: String): Int {
        val option = options.find { it.key == accentKey } ?: options.first()
        return ContextCompat.getColor(context, option.colorRes)
    }

    /** Returns the display label for the current accent. */
    fun getCurrentAccentLabel(context: Context): String {
        val key = getCurrentAccentKey(context)
        val option = options.find { it.key == key } ?: options.first()
        return context.getString(option.labelRes)
    }

    /** Returns the display label for a specific accent key. */
    fun getAccentLabel(context: Context, accentKey: String): String {
        val option = options.find { it.key == accentKey } ?: options.first()
        return context.getString(option.labelRes)
    }

    /** Creates a rectangular focus border drawable using the current accent color. */
    fun createFocusBorder(context: Context): GradientDrawable {
        val color = getAccentColor(context)
        val drawable = GradientDrawable()
        drawable.shape = GradientDrawable.RECTANGLE
        drawable.setColor(android.graphics.Color.TRANSPARENT)
        drawable.setStroke(
            context.resources.getDimensionPixelSize(R.dimen.switch_focus_border_width),
            color
        )
        drawable.setCornerRadius(
            context.resources.getDimension(R.dimen.switch_card_corner_radius)
        )
        return drawable
    }

    /** Creates a round focus border drawable using the current accent color. */
    fun createRoundFocusBorder(context: Context): GradientDrawable {
        val color = getAccentColor(context)
        val drawable = GradientDrawable()
        drawable.shape = GradientDrawable.OVAL
        drawable.setColor(android.graphics.Color.TRANSPARENT)
        drawable.setStroke(
            context.resources.getDimensionPixelSize(R.dimen.switch_focus_border_width),
            color
        )
        return drawable
    }

    /** Creates a teal badge background drawable using the current accent color. */
    fun createBadgeBackground(context: Context): GradientDrawable {
        val color = getAccentColor(context)
        val drawable = GradientDrawable()
        drawable.shape = GradientDrawable.RECTANGLE
        drawable.setColor(color)
        drawable.setCornerRadius(6f)
        return drawable
    }

    /** Creates a ripple drawable for dock buttons using the current accent color. */
    fun createDockRipple(context: Context): RippleDrawable {
        val color = getAccentColor(context)
        val colorStateList = ColorStateList.valueOf(color)
        return RippleDrawable(colorStateList, null, null)
    }

    /** Creates a solid accent button background (4dp corners) for SwitchButton / dialogs. */
    fun createSwitchButtonBackground(context: Context): GradientDrawable {
        val color = getAccentColor(context)
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = 4f * context.resources.displayMetrics.density
        }
    }

    /** Creates a selector for SwitchButton: focused = transparent + accent stroke, default = accent solid. */
    fun createSwitchButtonSelector(context: Context): android.graphics.drawable.StateListDrawable {
        val accent = getAccentColor(context)
        val stroke = context.resources.getDimensionPixelSize(R.dimen.switch_focus_border_width)
        val radius = 4f * context.resources.displayMetrics.density
        val focused = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(android.graphics.Color.TRANSPARENT)
            setStroke(stroke, accent)
            cornerRadius = radius
        }
        val normal = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(accent)
            cornerRadius = radius
        }
        return android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused, android.R.attr.state_enabled), focused)
            addState(intArrayOf(), normal)
        }
    }

    /** Creates the active background for emulator menu toggles (accent solid, card radius). */
    fun createMenuItemActiveBackground(context: Context): GradientDrawable {
        val color = getAccentColor(context)
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = context.resources.getDimension(R.dimen.card_radius)
        }
    }

    /** Creates the inactive menu cell background (panel solid, card radius). */
    fun createMenuItemBackground(context: Context): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(ContextCompat.getColor(context, R.color.switch_panel))
            cornerRadius = context.resources.getDimension(R.dimen.card_radius)
        }
    }

    /** Creates a pill selector for Gamepad Tester mode tabs (selected/pressed = accent solid). */
    fun createGamepadTesterModeSelector(context: Context): android.graphics.drawable.StateListDrawable {
        val accent = getAccentColor(context)
        val panel = ContextCompat.getColor(context, R.color.switch_panel)
        val secondary = ContextCompat.getColor(context, R.color.switch_text_secondary)
        val stroke = context.resources.getDimensionPixelSize(R.dimen.switch_focus_border_width)
        val radius = 24f * context.resources.displayMetrics.density
        fun pill(color: Int, strokeColor: Int? = null, strokeW: Int = 0): GradientDrawable =
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(color)
                cornerRadius = radius
                if (strokeColor != null) setStroke(strokeW, strokeColor)
            }
        val selected = pill(accent)
        val focused = pill(android.graphics.Color.TRANSPARENT, accent, stroke)
        val idle = pill(panel, secondary, (1f * context.resources.displayMetrics.density).toInt())
        return android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_selected), selected)
            addState(intArrayOf(android.R.attr.state_pressed), selected)
            addState(intArrayOf(android.R.attr.state_focused), focused)
            addState(intArrayOf(), idle)
        }
    }

    /** Creates a small badge background (4dp corners) with accent solid. */
    fun createBadgeOverlayBackground(context: Context): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(getAccentColor(context))
            cornerRadius = 4f * context.resources.displayMetrics.density
        }
    }

    /** Returns a ColorStateList for Switch thumb/track where checked = accent. */
    fun createSwitchThumbStateList(context: Context): ColorStateList {
        val accent = getAccentColor(context)
        val unchecked = ContextCompat.getColor(context, R.color.switch_text_primary)
        return ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(accent, unchecked)
        )
    }

    fun createSwitchTrackStateList(context: Context): ColorStateList {
        val accent = getAccentColor(context)
        val unchecked = ContextCompat.getColor(context, R.color.switch_text_secondary)
        return ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(accent, unchecked)
        )
    }

    /** Data class representing a single accent option. */
    data class AccentOption(
        val key: String,
        val labelRes: Int,
        val colorRes: Int
    )
}