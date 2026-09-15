/* Copyright (C) 2026 RedClaw. SPDX-License-Identifier: GPL-3.0-or-later */
package br.com.redclaw.hylianbox.tracker.assets.mapping

import br.com.redclaw.hylianbox.tracker.assets.graphics.N64TextureFormat
import br.com.redclaw.hylianbox.tracker.model.TrackerGame

/**
 * Exact HUD item icons used by equipped C/B/R buttons. They are extracted on-device from the
 * user's own ROM; no Nintendo artwork is bundled in the application.
 */
object EquippedItemIconMap {
    fun assetKey(itemId: Int): String = "equipped_${itemId.toString(16).padStart(2, '0')}"

    /** Whether the game's item-icon archive contains the requested equipped-item id. */
    fun supports(game: TrackerGame, itemId: Int): Boolean =
        when (game) {
            TrackerGame.OOT -> itemId in 0x00..0x40
            TrackerGame.MM -> itemId in 0x00..0x52
        }

    fun mappings(game: TrackerGame): List<IconMapping> {
        val archive =
            if (game == TrackerGame.OOT) IconArchiveFormat.RAW else IconArchiveFormat.YAR
        val dma =
            if (game == TrackerGame.OOT) DmaTableOffsets.OOT_ICON_FILE_INDEX
            else DmaTableOffsets.MM_ICON_FILE_INDEX
        val ids = (0x00..0x55).filter { supports(game, it) }
        return ids.map { itemId ->
            IconMapping(
                itemId = assetKey(itemId),
                dmaFileIndex = dma,
                offset = itemId * 0x1000,
                format = N64TextureFormat.RGBA32,
                archiveFormat = archive
            )
        }
    }
}
