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

import android.content.res.ColorStateList
import android.view.View
import android.widget.ImageView
import br.com.redclaw.hylianbox.R
import br.com.redclaw.hylianbox.ocarina.OcarinaGame
import br.com.redclaw.hylianbox.views.BadgeType
import br.com.redclaw.hylianbox.views.HackLibraryEntry

/**
 * Binds a [HackLibraryEntry]'s family/type badge onto an [ImageView], keeping the chip background
 * and icon tint consistent with the configured accent. OoT/MM identity comes from the glyph rather
 * than fixed family colors.
 *
 * Extracted from the legacy grid adapter so both the Switch home row and the (Phase C) grid screen
 * render badges identically without duplicating the per-family branching (DRY).
 */
object BadgeBinder {

    /**
     * Applies the badge for [entry] to [badge], or hides it when none applies.
     *
     * Vanilla base ROMs ([BadgeType.VANILLA] or [HackLibraryEntry.isVanilla]) carry no badge by
     * design. Store hacks ([BadgeType.HACK]) delegate to [bindFamily] so the family icon matches
     * the rest of the app.
     */
    fun bind(badge: ImageView, entry: HackLibraryEntry) {
        val type = entry.badge
        // Vanilla base ROMs must have no badge.
        if (type == null || type == BadgeType.VANILLA || entry.isVanilla) {
            badge.visibility = View.GONE
            return
        }
        bindFamily(badge, entry.family)
    }

    /** Resolves the game family from a catalog `supportedGames` string (e.g. "OoT", "MM"). */
    fun familyFromSupportedGames(supportedGames: String?): OcarinaGame? =
            when {
                supportedGames?.contains("OoT", ignoreCase = true) == true -> OcarinaGame.OOT
                supportedGames?.contains("MM", ignoreCase = true) == true -> OcarinaGame.MM
                else -> null
            }

    /** Resolves the game family from a N64 header `gameCode` (e.g. "CZLE" -> OoT, "NSME" -> MM). */
    fun familyFromGameCode(gameCode: String?): OcarinaGame? =
            when {
                gameCode == null -> null
                gameCode.startsWith("CZL", ignoreCase = true) -> OcarinaGame.OOT
                gameCode.startsWith("NZL", ignoreCase = true) ||
                        gameCode.startsWith("NSM", ignoreCase = true) ||
                        gameCode.startsWith("NZS", ignoreCase = true) -> OcarinaGame.MM
                else -> null
            }

    /**
     * Resolves the game family for a [HackEntry], preferring [supportedGames] and falling back to
     * [br.com.redclaw.hylianbox.data.model.BaseRomRef.gameCode]. This guarantees the badge is
     * shown even when the catalog omits `supportedGames` (curated PICKS entries).
     */
    fun familyForHack(hack: br.com.redclaw.hylianbox.data.model.HackEntry): OcarinaGame? =
            familyFromSupportedGames(hack.supportedGames)
                    ?: familyFromGameCode(hack.baseRom.gameCode)

    /**
     * Applies the family glyph while keeping both badge fill and icon contrast derived from the
     * configured accent. Family identity comes from the glyph, not a private yellow/purple color.
     */
    fun bindFamily(imageView: ImageView, family: OcarinaGame?) {
        val drawableRes: Int
        val contentDescriptionRes: Int
        when (family) {
            OcarinaGame.OOT -> {
                drawableRes = R.drawable.ic_oot
                contentDescriptionRes = R.string.game_oot
            }
            OcarinaGame.MM -> {
                drawableRes = R.drawable.ic_mm
                contentDescriptionRes = R.string.game_mm
            }
            null -> {
                drawableRes = R.drawable.ic_hack
                contentDescriptionRes = R.string.hack_badge_content_description
            }
        }

        imageView.visibility = View.VISIBLE
        imageView.setImageResource(drawableRes)
        imageView.background = AccentManager.createBadgeBackground(imageView.context)
        imageView.imageTintList =
                ColorStateList.valueOf(AccentManager.getOnAccentColor(imageView.context))
        imageView.contentDescription = imageView.context.getString(contentDescriptionRes)
    }
}
