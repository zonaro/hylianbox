/* Copyright (C) 2026 RedClaw. SPDX-License-Identifier: GPL-3.0-or-later */
package br.com.redclaw.hylianbox.savename

/**
 * Filename (player name) character encoding for OoT/MM N64 saves.
 *
 * Source: zeldaret/oot `include/message.h` FILENAME_* macros and zeldaret/mm `src/code/z_message.c`
 * player-name handling.
 *
 * Two tables exist:
 * - [NTSC]: OoT NTSC (CZL*) — uppercase base 0xAB, space 0xDF.
 * - [PAL]: OoT PAL (NZL*) and all MM — uppercase base 0x0A, space 0x3E.
 *
 * v1 supports ASCII uppercase A-Z, digits 0-9, space, dash and period. Lowercase input is
 * uppercased before encoding. Everything else is rejected.
 */
enum class FileNameTable {
    NTSC,
    PAL;

    val spaceByte: Int
        get() = if (this == NTSC) 0xDF else 0x3E
    private val dashByte: Int
        get() = if (this == NTSC) 0xE4 else 0x3F
    private val periodByte: Int
        get() = if (this == NTSC) 0xEA else 0x40
    private val upperBase: Int
        get() = if (this == NTSC) 0xAB else 0x0A

    fun encodeChar(c: Char): Int? =
            when {
                c in 'A'..'Z' -> (c - 'A') + upperBase
                c in '0'..'9' -> (c - '0')
                c == ' ' -> spaceByte
                c == '-' -> dashByte
                c == '.' -> periodByte
                else -> null
            }

    fun decodeChar(b: Int): Char? =
            when {
                b in upperBase until upperBase + 26 -> 'A' + (b - upperBase)
                b in 0x00..0x09 -> '0' + b
                b == spaceByte -> ' '
                b == dashByte -> '-'
                b == periodByte -> '.'
                else -> null
            }
}

/**
 * Pure-Kotlin codec for the 8-byte player-name field. No Android dependencies, so it runs on JVM
 * unit tests.
 */
object PlayerNameCodec {
    const val NAME_LENGTH = 8
    const val MAX_DISPLAY_LENGTH = 8

    /**
     * Normalize user input: trim, uppercase, collapse inner whitespace runs to a single space.
     * Returns null when the result is empty or exceeds 8 chars.
     */
    fun normalize(raw: String): String? {
        val collapsed = raw.trim().uppercase().replace(Regex("\\s+"), " ")
        if (collapsed.isEmpty() || collapsed.length > MAX_DISPLAY_LENGTH) return null
        return collapsed
    }

    /**
     * Encode a display name to exactly 8 game bytes, padded with the table space byte. Returns null
     * when the name is invalid for [table].
     */
    fun encode(displayName: String, table: FileNameTable): ByteArray? {
        val normal = normalize(displayName) ?: return null
        val out = ByteArray(NAME_LENGTH) { table.spaceByte.toByte() }
        for ((i, c) in normal.withIndex()) {
            val b = table.encodeChar(c) ?: return null
            out[i] = b.toByte()
        }
        return out
    }

    /**
     * Decode 8 game bytes to a display string, trimming trailing spaces. Unknown bytes become '?'.
     * Never throws.
     */
    fun decode(raw: ByteArray, table: FileNameTable): String {
        val bytes = if (raw.size >= NAME_LENGTH) raw.take(NAME_LENGTH) else raw.toList()
        val chars = bytes.map { b -> table.decodeChar(b.toInt() and 0xFF) ?: '?' }
        return chars.joinToString("").trimEnd()
    }

    /** True when every byte is the table space byte (fresh/empty name). */
    fun isBlankName(raw: ByteArray, table: FileNameTable): Boolean {
        val space = table.spaceByte
        val bytes = if (raw.size >= NAME_LENGTH) raw.take(NAME_LENGTH) else raw.toList()
        return bytes.isNotEmpty() && bytes.all { (it.toInt() and 0xFF) == space }
    }
}
