/*
 * HylianBox - native Android N64 emulator frontend for Zelda ROM hacks.
 * Copyright (C) 2026 RedClaw
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package br.com.redclaw.hylianbox.ocarina

/**
 * Maps Auto-Ocarina song ids ([OcarinaSong.id]) to item-tracker song ids (`TrackerState.foundSongs`
 * entries). Custom catalog songs have no tracker counterpart and map to null.
 */
object OcarinaSongTrackerMap {

    private val ootMap: Map<String, String> =
            mapOf(
                    "oot_zeldas_lullaby" to "zeldas_lullaby",
                    "oot_eponas_song" to "eponas_song",
                    "oot_sarias_song" to "sarias_song",
                    "oot_suns_song" to "sun_song",
                    "oot_song_of_time" to "song_of_time",
                    "oot_song_of_storms" to "song_of_storms",
                    "oot_minuet_of_forest" to "minuet_of_forest",
                    "oot_bolero_of_fire" to "bolero_of_fire",
                    "oot_serenade_of_water" to "serenade_of_water",
                    "oot_requiem_of_spirit" to "requiem_of_spirit",
                    "oot_nocturne_of_shadow" to "nocturne_of_shadow",
                    "oot_prelude_of_light" to "prelude_of_light",
            )

    private val mmMap: Map<String, String> =
            mapOf(
                    "mm_song_of_time" to "song_of_time",
                    "mm_eponas_song" to "eponas_song",
                    "mm_song_of_storms" to "song_of_storms",
                    "mm_song_of_healing" to "song_of_healing",
                    "mm_song_of_soaring" to "song_of_soaring",
                    "mm_sonata_of_awakening" to "sonata_of_awakening",
                    "mm_goron_lullaby" to "goron_lullaby",
                    "mm_new_wave_bossa_nova" to "new_wave_bossa_nova",
                    "mm_elegy_of_emptiness" to "elegy_of_emptiness",
                    "mm_oath_to_order" to "oath_to_order",
                    "mm_inverted_song_of_time" to "song_of_time",
                    "mm_song_of_double_time" to "song_of_double_time",
            )

    fun trackerSongId(ocarinaSongId: String): String? =
            ootMap[ocarinaSongId] ?: mmMap[ocarinaSongId]
}
