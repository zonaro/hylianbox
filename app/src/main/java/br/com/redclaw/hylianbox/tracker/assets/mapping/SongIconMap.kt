/*
 * HylianBox - native Android N64 emulator frontend for Zelda ROM hacks.
 * Copyright (C) 2026 RedClaw
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package br.com.redclaw.hylianbox.tracker.assets.mapping

import br.com.redclaw.hylianbox.tracker.assets.graphics.N64TextureFormat
import br.com.redclaw.hylianbox.tracker.model.TrackerGame

/**
 * Single shared song-note texture (`gSongNoteTex` / `gItemIconSongNoteTex`, IA8 16x24), tinted per
 * song at runtime via PrimColor.
 *
 * OoT and MM ship ONE staff-note glyph; the game tints it per song (see `sSongsPrimRed/Green/Blue`
 * in zeldaret/oot and `sQuestSongsPrim*` in zeldaret/mm). Extraction is on-device from the user's
 * own base ROM; no Nintendo artwork is bundled.
 *
 * Sources:
 * - OoT: `icon_item_static` DMA 8, offset 0x88040, IA8 16x24 (384 bytes).
 * - MM: `icon_item_static_yar` DMA 19 YAR, offset 0x62000, IA8 16x24.
 */
object SongIconMap {

    const val ASSET_KEY = "song_note"

    const val OOT_NOTE_OFFSET = 0x88040
    const val MM_NOTE_OFFSET = 0x62000
    const val NOTE_WIDTH = 16
    const val NOTE_HEIGHT = 24

    val OOT_NOTE =
            IconMapping(
                    ASSET_KEY,
                    DmaTableOffsets.OOT_ICON_FILE_INDEX,
                    OOT_NOTE_OFFSET,
                    width = NOTE_WIDTH,
                    height = NOTE_HEIGHT,
                    format = N64TextureFormat.IA8,
                    archiveFormat = IconArchiveFormat.RAW,
            )

    val MM_NOTE =
            IconMapping(
                    ASSET_KEY,
                    DmaTableOffsets.MM_ICON_FILE_INDEX,
                    MM_NOTE_OFFSET,
                    width = NOTE_WIDTH,
                    height = NOTE_HEIGHT,
                    format = N64TextureFormat.IA8,
                    archiveFormat = IconArchiveFormat.YAR,
            )

    fun mappingFor(game: TrackerGame): IconMapping =
            when (game) {
                TrackerGame.OOT -> OOT_NOTE
                TrackerGame.MM -> MM_NOTE
            }

    /**
     * Runtime PrimColor tint for a tracker song id (ARGB). Unknown ids fall back to opaque white.
     */
    fun tintFor(trackerSongId: String): Int =
            when (trackerSongId) {
                "minuet_of_forest", "sonata_of_awakening" -> 0xFF96FF64.toInt()
                "bolero_of_fire", "goron_lullaby" -> 0xFFFF5028.toInt()
                "serenade_of_water", "new_wave_bossa_nova" -> 0xFF6496FF.toInt()
                "requiem_of_spirit", "elegy_of_emptiness" -> 0xFFFFA000.toInt()
                "nocturne_of_shadow", "oath_to_order" -> 0xFFFF64FF.toInt()
                "prelude_of_light" -> 0xFFFFF064.toInt()
                else -> 0xFFFFFFFF.toInt()
            }
}
