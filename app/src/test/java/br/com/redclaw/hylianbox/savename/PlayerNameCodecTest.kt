/* Copyright (C) 2026 RedClaw. SPDX-License-Identifier: GPL-3.0-or-later */
package br.com.redclaw.hylianbox.savename

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerNameCodecTest {

    @Test
    fun encodeDecodeRoundTripPal() {
        val table = FileNameTable.PAL
        val names = listOf("LINK", "ZELDA", "A", "ABCDEFGH", "A B", "A-B", "A.B", "123", "A1 B2")
        for (name in names) {
            val enc = PlayerNameCodec.encode(name, table)
            assertNotNull("encode $name", enc)
            val dec = PlayerNameCodec.decode(enc!!, table)
            assertEquals(name, dec)
        }
    }

    @Test
    fun encodeDecodeRoundTripNtsc() {
        val table = FileNameTable.NTSC
        val names = listOf("LINK", "ZELDA", "ABCDEFGH", "A B")
        for (name in names) {
            val enc = PlayerNameCodec.encode(name, table)
            assertNotNull(enc)
            assertEquals(name, PlayerNameCodec.decode(enc!!, table))
        }
    }

    @Test
    fun encodePadsWithSpace() {
        val table = FileNameTable.PAL
        val enc = PlayerNameCodec.encode("AB", table)!!
        assertEquals(8, enc.size)
        assertEquals(table.encodeChar('A')!!, enc[0].toInt() and 0xFF)
        assertEquals(table.encodeChar('B')!!, enc[1].toInt() and 0xFF)
        for (i in 2 until 8) assertEquals(table.spaceByte, enc[i].toInt() and 0xFF)
    }

    @Test
    fun normalizeUppercasesAndCollapsesSpaces() {
        assertEquals("A B", PlayerNameCodec.normalize("  a   b  "))
        assertEquals("HELLO", PlayerNameCodec.normalize("hello"))
    }

    @Test
    fun normalizeRejectsEmptyAndTooLong() {
        assertNull(PlayerNameCodec.normalize(""))
        assertNull(PlayerNameCodec.normalize("   "))
        assertNull(PlayerNameCodec.normalize("ABCDEFGHI"))
        assertNotNull(PlayerNameCodec.normalize("ABCDEFGH"))
    }

    @Test
    fun encodeRejectsInvalidChars() {
        val table = FileNameTable.PAL
        assertNull(PlayerNameCodec.encode("A!", table))
        assertNull(PlayerNameCodec.encode("A@", table))
        assertNull(PlayerNameCodec.encode("A#", table))
    }

    @Test
    fun encodeRejectsTooLong() {
        assertNull(PlayerNameCodec.encode("ABCDEFGHI", FileNameTable.PAL))
    }

    @Test
    fun decodeTrimsTrailingSpaces() {
        val table = FileNameTable.PAL
        val enc = PlayerNameCodec.encode("LINK", table)!!
        assertEquals("LINK", PlayerNameCodec.decode(enc, table))
    }

    @Test
    fun decodeUnknownByteBecomesQuestion() {
        val table = FileNameTable.PAL
        val raw = ByteArray(8) { 0xFF.toByte() }
        assertEquals("????????", PlayerNameCodec.decode(raw, table))
    }

    @Test
    fun isBlankName() {
        val table = FileNameTable.PAL
        val blank = ByteArray(8) { table.spaceByte.toByte() }
        assertTrue(PlayerNameCodec.isBlankName(blank, table))
        val notBlank = PlayerNameCodec.encode("A", table)!!
        assertFalse(PlayerNameCodec.isBlankName(notBlank, table))
    }

    @Test
    fun palAndNtscEncodingsDiffer() {
        val pal = PlayerNameCodec.encode("LINK", FileNameTable.PAL)!!
        val ntsc = PlayerNameCodec.encode("LINK", FileNameTable.NTSC)!!
        assertFalse(pal.contentEquals(ntsc))
        // But each decodes back correctly with its own table.
        assertEquals("LINK", PlayerNameCodec.decode(pal, FileNameTable.PAL))
        assertEquals("LINK", PlayerNameCodec.decode(ntsc, FileNameTable.NTSC))
    }

    @Test
    fun linkBytesMatchDecomp() {
        // MM/OoT PAL debug save: "LINK    " -> 0x15 0x12 0x17 0x14 0x3E 0x3E 0x3E 0x3E
        val table = FileNameTable.PAL
        val enc = PlayerNameCodec.encode("LINK", table)!!
        val expected =
                byteArrayOf(
                        0x15,
                        0x12,
                        0x17,
                        0x14,
                        0x3E.toByte(),
                        0x3E.toByte(),
                        0x3E.toByte(),
                        0x3E.toByte()
                )
        assertArrayEquals(expected, enc)
    }
}
