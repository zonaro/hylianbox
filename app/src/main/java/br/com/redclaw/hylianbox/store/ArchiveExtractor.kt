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
import java.io.InputStream

/**
 * Single entry point for extracting a patch/ROM from a downloaded archive, dispatching on the
 * archive extension:
 *
 * - `.zip` → [ZipExtractor] (JDK, streaming)
 * - `.7z` → [SevenZExtractor] (commons-compress + xz for LZMA2)
 * - `.rar` → [RarExtractor] (junrar, RAR up to v7 incl. RAR5)
 *
 * Shared by [DownloadManager] (direct catalog downloads) and the WebView manual-download flow so
 * both support the same archive formats (DRY).
 *
 * Pure (no Android deps) so it can be unit-tested on the JVM.
 */
object ArchiveExtractor {
    /** Hard ceiling that prevents a compressed archive from expanding without bound in memory. */
    const val MAX_EXTRACTED_ENTRY_BYTES: Long = 128L * 1024L * 1024L
    /** Regex matching a patch-like inner file (BPS/IPS/XDELTA). */
    const val PATCH_ENTRY_REGEX = ".*\\.(bps|ips|xdelta)$"

    /** Regex matching any installable inner file (patch or raw ROM). */
    const val INSTALLABLE_ENTRY_REGEX = ".*\\.(bps|ips|xdelta|n64|z64|v64)$"

    /** True when [urlOrName] ends with a supported archive extension. */
    fun isArchive(urlOrName: String): Boolean {
        val lower = urlOrName.lowercase().substringBefore('?').substringBefore('#')
        return lower.endsWith(".zip") || lower.endsWith(".7z") || lower.endsWith(".rar")
    }

    /**
     * Return the value that identifies an archive for a download, preferring the response/file
     * [filename] over [url]. Browser download endpoints commonly use opaque URLs such as
     * `download.php?id=42`; in that case only Content-Disposition exposes the real `.zip`, `.7z`
     * or `.rar` name.
     */
    fun archiveSource(url: String, filename: String): String? =
            when {
                isArchive(filename) -> filename
                isArchive(url) -> url
                else -> null
            }

    /** Archive container format, detected from a download URL or file name. */
    enum class ArchiveFormat {
        ZIP,
        SEVEN_Z,
        RAR
    }

    /** Detect the container [ArchiveFormat] from [urlOrName] (URL or file name). */
    fun formatOf(urlOrName: String): ArchiveFormat {
        val lower = urlOrName.lowercase().substringBefore('?').substringBefore('#')
        return when {
            lower.endsWith(".7z") -> ArchiveFormat.SEVEN_Z
            lower.endsWith(".rar") -> ArchiveFormat.RAR
            else -> ArchiveFormat.ZIP
        }
    }

    /** True when [name] ends with a supported patch extension. */
    fun isPatchName(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".bps") || lower.endsWith(".ips") || lower.endsWith(".xdelta")
    }

    /**
     * Extract the entry named [entryName] from [archiveFile] (format detected from [archiveFile]'s
     * own extension).
     */
    fun extractEntry(archiveFile: File, entryName: String): ByteArray =
            extractEntry(archiveFile, entryName, formatOf(archiveFile.name))

    /**
     * Extract the entry named [entryName] from [archiveFile], whose container [format] is detected
     * from the download URL (the temp file itself may carry a `.tmp` extension).
     */
    fun extractEntry(archiveFile: File, entryName: String, format: ArchiveFormat): ByteArray =
            when (format) {
                ArchiveFormat.SEVEN_Z -> SevenZExtractor.extractEntry(archiveFile, entryName)
                ArchiveFormat.RAR -> RarExtractor.extractEntry(archiveFile, entryName)
                ArchiveFormat.ZIP -> ZipExtractor.extractEntry(archiveFile, entryName)
            }

    /**
     * Extract the first entry matching [regex] (case-insensitive) from [archiveFile] (format
     * detected from [archiveFile]'s own extension).
     */
    fun extractFirstMatching(archiveFile: File, regex: String): ByteArray =
            extractFirstMatching(archiveFile, regex, formatOf(archiveFile.name))

    /**
     * Extract the first entry matching [regex] (case-insensitive) from [archiveFile], whose
     * container [format] is detected from the download URL (the temp file itself may carry a `.tmp`
     * extension).
     */
    fun extractFirstMatching(archiveFile: File, regex: String, format: ArchiveFormat): ByteArray =
            when (format) {
                ArchiveFormat.SEVEN_Z -> SevenZExtractor.extractFirstMatching(archiveFile, regex)
                ArchiveFormat.RAR -> RarExtractor.extractFirstMatching(archiveFile, regex)
                ArchiveFormat.ZIP -> ZipExtractor.extractFirstMatching(archiveFile, regex)
            }

    /**
     * Name of the first entry matching [regex] (case-insensitive) in [archiveFile], or null when
     * none matches.
     */
    fun findFirstMatchingName(archiveFile: File, regex: String): String? =
            findFirstMatchingName(archiveFile, regex, formatOf(archiveFile.name))

    /**
     * Name of the first entry matching [regex] (case-insensitive) in [archiveFile], whose container
     * [format] is detected from the download URL (the temp file itself may carry a `.tmp`
     * extension). Returns null when none matches.
     */
    fun findFirstMatchingName(archiveFile: File, regex: String, format: ArchiveFormat): String? =
            when (format) {
                ArchiveFormat.SEVEN_Z -> SevenZExtractor.findFirstMatchingName(archiveFile, regex)
                ArchiveFormat.RAR -> RarExtractor.findFirstMatchingName(archiveFile, regex)
                ArchiveFormat.ZIP -> findFirstZipName(archiveFile, regex)
            }

    private fun findFirstZipName(zipFile: File, regex: String): String? {
        val pattern = Regex(regex, RegexOption.IGNORE_CASE)
        try {
            java.util.zip.ZipInputStream(zipFile.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && pattern.matches(entry.name)) {
                        return entry.name
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } catch (_: Exception) {}
        return null
    }
}

/** Read one archive entry while enforcing [maxBytes], including when its size is not declared. */
internal fun readArchiveEntryLimited(
        input: InputStream,
        maxBytes: Long = ArchiveExtractor.MAX_EXTRACTED_ENTRY_BYTES
): ByteArray {
    val out = BoundedArchiveOutputStream(maxBytes)
    input.copyTo(out, 64 * 1024)
    return out.toByteArray()
}

/** Byte accumulator used by streaming and callback-based archive readers. */
internal class BoundedArchiveOutputStream(private val maxBytes: Long) : ByteArrayOutputStream() {
    override fun write(value: Int) {
        ensureCapacityFor(1)
        super.write(value)
    }

    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        ensureCapacityFor(length)
        super.write(bytes, offset, length)
    }

    private fun ensureCapacityFor(additional: Int) {
        if (additional < 0 || count.toLong() + additional > maxBytes) {
            throw StoreException.InvalidPatch(
                    "Archive entry exceeds the safe extraction limit of $maxBytes bytes"
            )
        }
    }
}
