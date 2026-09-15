/*
 * HylianBox - native Android N64 emulator frontend for Zelda ROM hacks.
 * Copyright (C) 2026 RedClaw
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package br.com.redclaw.hylianbox.tracker.assets.graphics

import java.io.File

/**
 * Decodes N64 texture formats (RGBA32, RGBA16, CI8+TLUT, IA8, I8) to 32-bit ARGB_8888.
 *
 * Pure Kotlin for the decode logic; [saveAsPng] uses Android Bitmap (call on Android only).
 */
object TextureDecoder {

    /**
     * Decode an RGBA32 (8b per channel) texture to ARGB_8888 pixels.
     *
     * Each pixel is 4 bytes: R G B A.
     */
    fun decodeRGBA32(data: ByteArray, offset: Int, width: Int, height: Int): IntArray {
        val count = width * height
        require(offset + count * 4 <= data.size) {
            "RGBA32: not enough data (need ${count * 4}, have ${data.size - offset})"
        }
        val pixels = IntArray(count)
        var idx = offset
        for (i in 0 until count) {
            val r = data[idx++].toInt() and 0xFF
            val g = data[idx++].toInt() and 0xFF
            val b = data[idx++].toInt() and 0xFF
            val a = data[idx++].toInt() and 0xFF
            pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        return pixels
    }

    /**
     * Decode an RGBA16 (5551) texture to ARGB_8888 pixels.
     *
     * Each pixel is 2 bytes BE: RRRRR GGGGG BBBBB A.
     */
    fun decodeRGBA16(data: ByteArray, offset: Int, width: Int, height: Int): IntArray {
        val count = width * height
        require(offset + count * 2 <= data.size) {
            "RGBA16: not enough data (need ${count * 2}, have ${data.size - offset})"
        }
        val pixels = IntArray(count)
        var idx = offset
        for (i in 0 until count) {
            val b1 = data[idx++].toInt() and 0xFF
            val b2 = data[idx++].toInt() and 0xFF
            val raw = (b1 shl 8) or b2
            val r = ((raw ushr 11) and 0x1F) * 255 / 31
            val g = ((raw ushr 6) and 0x1F) * 255 / 31
            val b = ((raw ushr 1) and 0x1F) * 255 / 31
            val a = if ((raw and 0x01) != 0) 255 else 0
            pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        return pixels
    }

    /**
     * Decode a CI8 (8-bit indexed) texture using a 256-entry RGBA16 TLUT.
     *
     * @param pixelData raw indexed bytes
     * @param pixelOffset offset into [pixelData]
     * @param tlutData raw TLUT bytes (256 * 2 = 512 bytes RGBA16)
     * @param tlutOffset offset into [tlutData]
     */
    fun decodeCI8(
            pixelData: ByteArray,
            pixelOffset: Int,
            width: Int,
            height: Int,
            tlutData: ByteArray,
            tlutOffset: Int,
    ): IntArray {
        val count = width * height
        require(pixelOffset + count <= pixelData.size) { "CI8: not enough pixel data" }
        require(tlutOffset + 512 <= tlutData.size) {
            "CI8: not enough TLUT data (need 512, have ${tlutData.size - tlutOffset})"
        }
        val palette = decodeRGBA16(tlutData, tlutOffset, 256, 1)
        return IntArray(count) { i ->
            val index = pixelData[pixelOffset + i].toInt() and 0xFF
            palette[index]
        }
    }

    /**
     * Decode an IA8 texture (1 byte/pixel) to ARGB_8888.
     *
     * N64 IA8 packs 4 bits intensity + 4 bits alpha per byte (high nibble intensity, low nibble
     * alpha). The song-note texture (`gSongNoteTex` / `gItemIconSongNoteTex`, IA8 16x24) is a white
     * glyph tinted at runtime via PrimColor, so the extracted PNG must stay bright white for
     * [android.widget.ImageView.setColorFilter] tinting.
     *
     * Simplification: the full byte is used as both luminance and alpha (`I = A = byte`), i.e.
     * grayscale white where bright = opaque and dark = transparent. For the canonical values this
     * matches the nibble expansion (0xFF -> opaque white, 0x00 -> transparent) while keeping
     * mid-tones bright enough to tint vividly. Documented here so the approximation is explicit and
     * testable.
     */
    fun decodeIA8(data: ByteArray, offset: Int, width: Int, height: Int): IntArray {
        val count = width * height
        require(offset + count <= data.size) {
            "IA8: not enough data (need $count, have ${data.size - offset})"
        }
        return IntArray(count) { i ->
            val v = data[offset + i].toInt() and 0xFF
            (v shl 24) or (v shl 16) or (v shl 8) or v
        }
    }

    /** Decode an I8 texture (1 byte/pixel grayscale, opaque) to ARGB_8888. */
    fun decodeI8(data: ByteArray, offset: Int, width: Int, height: Int): IntArray {
        val count = width * height
        require(offset + count <= data.size) {
            "I8: not enough data (need $count, have ${data.size - offset})"
        }
        return IntArray(count) { i ->
            val v = data[offset + i].toInt() and 0xFF
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
    }

    /** Save ARGB pixels as a PNG file. Android-only (uses `android.graphics.Bitmap`). */
    fun saveAsPng(pixels: IntArray, width: Int, height: Int, outFile: File) {
        val bmp =
                android.graphics.Bitmap.createBitmap(
                        pixels,
                        width,
                        height,
                        android.graphics.Bitmap.Config.ARGB_8888
                )
        outFile.outputStream().use { out ->
            bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
        }
        bmp.recycle()
    }
}
