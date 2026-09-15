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

package br.com.redclaw.hylianbox.store

import java.io.ByteArrayOutputStream
import java.io.File
import org.apache.commons.compress.archivers.sevenz.SevenZFile

/**
 * Extracts patch/ROM entries from `.7z` archives (e.g. Hylian Modding's `Master of Time Revisited
 * v1.4.2.7z`).
 *
 * Backed by Apache commons-compress ([SevenZFile], Apache-2.0) with LZMA(2) support from
 * `org.tukaani:xz`. Unlike [ZipExtractor] (streaming), 7z needs random access, so extraction always
 * works from a [File] on disk — which is exactly what [DownloadManager] and the WebView installer
 * already have.
 *
 * Pure (no Android deps) so it can be unit-tested on the JVM.
 */
object SevenZExtractor {
    /** Extract the entry named [entryName] from [sevenZFile]. */
    fun extractEntry(sevenZFile: File, entryName: String): ByteArray {
        SevenZFile(sevenZFile).use { archive ->
            var entry = archive.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name == entryName) {
                    return readCurrentEntry(archive)
                }
                entry = archive.nextEntry
            }
        }
        throw StoreException.GenericError("Entry '$entryName' not found in archive")
    }

    /**
     * Extract the first entry whose name matches [regex] (case-insensitive). Used when a catalog
     * declares a `.7z` archive without the inner patch filename: we pick the first
     * `*.bps`/`*.ips`/`*.xdelta` inside it.
     */
    fun extractFirstMatching(sevenZFile: File, regex: String): ByteArray {
        val pattern = Regex(regex, RegexOption.IGNORE_CASE)
        SevenZFile(sevenZFile).use { archive ->
            var entry = archive.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && pattern.matches(entry.name)) {
                    return readCurrentEntry(archive)
                }
                entry = archive.nextEntry
            }
        }
        throw StoreException.GenericError("No entry matching '$regex' found in archive")
    }

    /**
     * Name of the first entry matching [regex] (case-insensitive), or null. Used to preserve the
     * inner file extension when materializing bytes.
     */
    fun findFirstMatchingName(sevenZFile: File, regex: String): String? {
        val pattern = Regex(regex, RegexOption.IGNORE_CASE)
        SevenZFile(sevenZFile).use { archive ->
            var entry = archive.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && pattern.matches(entry.name)) {
                    return entry.name
                }
                entry = archive.nextEntry
            }
        }
        return null
    }

    private fun readCurrentEntry(archive: SevenZFile): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        var read: Int
        while (archive.read(buf).also { read = it } > 0) {
            out.write(buf, 0, read)
        }
        return out.toByteArray()
    }
}
