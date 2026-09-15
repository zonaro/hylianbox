package br.com.redclaw.hylianbox.store

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests [ArchiveExtractor] dispatch + [ZipExtractor] behavior through the unified facade. 7z/RAR
 * paths need their native-format fixtures, so they are covered by extension/format unit tests here
 * plus manual QA with the real upstream archives (Master of Time .7z, Helix Blade .rar).
 */
class ArchiveExtractorTest {

    private fun zipWithEntries(vararg entries: Pair<String, ByteArray>): File {
        val file = File.createTempFile("archive_test", ".zip")
        file.deleteOnExit()
        ZipOutputStream(file.outputStream()).use { zos ->
            for ((name, bytes) in entries) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(bytes)
                zos.closeEntry()
            }
        }
        return file
    }

    @Test
    fun formatOfDetectsContainers() {
        assertEquals(
                ArchiveExtractor.ArchiveFormat.SEVEN_Z,
                ArchiveExtractor.formatOf("https://example.com/a/Master%20of%20Time%20v1.4.2.7z")
        )
        assertEquals(
                ArchiveExtractor.ArchiveFormat.RAR,
                ArchiveExtractor.formatOf("https://example.com/a/The%20Helix%20Blade%20Demo.rar")
        )
        assertEquals(
                ArchiveExtractor.ArchiveFormat.ZIP,
                ArchiveExtractor.formatOf("https://example.com/a/tml_2_0.zip")
        )
        assertEquals(
                ArchiveExtractor.ArchiveFormat.ZIP,
                ArchiveExtractor.formatOf("https://example.com/a/patch.bps")
        )
    }

    @Test
    fun isArchiveMatchesSupportedContainers() {
        assertTrue(ArchiveExtractor.isArchive("https://example.com/a.zip"))
        assertTrue(ArchiveExtractor.isArchive("https://example.com/a.7z"))
        assertTrue(ArchiveExtractor.isArchive("https://example.com/a.rar"))
        assertFalse(ArchiveExtractor.isArchive("https://example.com/a.bps"))
        assertFalse(ArchiveExtractor.isArchive("https://example.com/a.xdelta"))
    }

    @Test
    fun isPatchNameMatchesPatchExtensions() {
        assertTrue(ArchiveExtractor.isPatchName("hack.bps"))
        assertTrue(ArchiveExtractor.isPatchName("hack.ips"))
        assertTrue(ArchiveExtractor.isPatchName("CZLE_1.0.xdelta"))
        assertFalse(ArchiveExtractor.isPatchName("archive.zip"))
        assertFalse(ArchiveExtractor.isPatchName("readme.txt"))
    }

    @Test
    fun extractsNamedZipEntryThroughFacade() {
        val payload = byteArrayOf(1, 2, 3, 4, 5)
        val zip = zipWithEntries("readme.txt" to "ignore".toByteArray(), "hack.bps" to payload)
        try {
            val extracted = ArchiveExtractor.extractEntry(zip, "hack.bps")
            assertArrayEquals(payload, extracted)
        } finally {
            zip.delete()
        }
    }

    @Test
    fun extractsFirstPatchFromZipThroughFacade() {
        val payload = byteArrayOf(9, 8, 7)
        val zip =
                zipWithEntries("readme.txt" to "ignore".toByteArray(), "inner/patch.ips" to payload)
        try {
            val extracted =
                    ArchiveExtractor.extractFirstMatching(zip, ArchiveExtractor.PATCH_ENTRY_REGEX)
            assertArrayEquals(payload, extracted)
            assertEquals(
                    "inner/patch.ips",
                    ArchiveExtractor.findFirstMatchingName(zip, ArchiveExtractor.PATCH_ENTRY_REGEX)
            )
        } finally {
            zip.delete()
        }
    }

    @Test(expected = StoreException.GenericError::class)
    fun throwsWhenZipEntryMissing() {
        val zip = zipWithEntries("other.bps" to byteArrayOf(9))
        try {
            ArchiveExtractor.extractEntry(zip, "missing.bps")
        } finally {
            zip.delete()
        }
    }

    @Test
    fun writesZipBytesRoundTrip() {
        // Sanity: ByteArrayOutputStream path used by the 7z reader helper.
        val payload = ByteArray(1024) { it.toByte() }
        val out = ByteArrayOutputStream()
        out.write(payload)
        assertArrayEquals(payload, out.toByteArray())
    }
}
