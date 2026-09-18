package br.com.redclaw.hylianbox.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GoogleDriveBackupServiceTest {
    @Test
    fun saveRemotePathKeepsHackFolder() {
        assertEquals(
            listOf("saves", "zelda_hack"),
            remoteParentSegments("saves/zelda_hack/sram_zelda_hack")
        )
    }

    @Test
    fun unsafeRemotePathIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            remoteParentSegments("saves/../state_game")
        }
    }
}
