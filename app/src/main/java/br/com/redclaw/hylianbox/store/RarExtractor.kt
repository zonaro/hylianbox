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

import com.github.junrar.Archive
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Extracts patch/ROM entries from `.rar` archives (e.g. Hylian Modding's `The Helix Blade
 * Demo.rar`, RAR5).
 *
 * Backed by junrar (`com.github.junrar:junrar`, free UnRAR license — see `docs/` licensing notes:
 * extraction-only use, no RAR compression). RAR needs random access, so extraction always works
 * from a [File] on disk — which is exactly what [DownloadManager] and the WebView installer have.
 *
 * Pure (no Android deps) so it can be unit-tested on the JVM.
 */
object RarExtractor {
    /** Extract the entry named [entryName] from [rarFile]. */
    fun extractEntry(rarFile: File, entryName: String): ByteArray {
        Archive(rarFile).use { archive ->
            var header = archive.nextFileHeader()
            while (header != null) {
                if (!header.isDirectory && header.fileName == entryName) {
                    return readHeader(archive, header)
                }
                header = archive.nextFileHeader()
            }
        }
        throw StoreException.GenericError("Entry '$entryName' not found in archive")
    }

    /**
     * Extract the first entry whose name matches [regex] (case-insensitive). Used when a catalog
     * declares a `.rar` archive without the inner patch filename: we pick the first
     * `*.bps`/`*.ips`/`*.xdelta` inside it.
     */
    fun extractFirstMatching(rarFile: File, regex: String): ByteArray {
        val pattern = Regex(regex, RegexOption.IGNORE_CASE)
        Archive(rarFile).use { archive ->
            var header = archive.nextFileHeader()
            while (header != null) {
                if (!header.isDirectory && pattern.matches(header.fileName)) {
                    return readHeader(archive, header)
                }
                header = archive.nextFileHeader()
            }
        }
        throw StoreException.GenericError("No entry matching '$regex' found in archive")
    }

    /**
     * Name of the first entry matching [regex] (case-insensitive), or null. Used to preserve the
     * inner file extension when materializing bytes.
     */
    fun findFirstMatchingName(rarFile: File, regex: String): String? {
        val pattern = Regex(regex, RegexOption.IGNORE_CASE)
        Archive(rarFile).use { archive ->
            var header = archive.nextFileHeader()
            while (header != null) {
                if (!header.isDirectory && pattern.matches(header.fileName)) {
                    return header.fileName
                }
                header = archive.nextFileHeader()
            }
        }
        return null
    }

    private fun readHeader(
            archive: Archive,
            header: com.github.junrar.rarfile.FileHeader
    ): ByteArray {
        val out = ByteArrayOutputStream()
        archive.extractFile(header, out)
        return out.toByteArray()
    }
}
