package br.com.redclaw.hylianbox.hud

import br.com.redclaw.hylianbox.tracker.autotracker.parser.SaveContextParser
import br.com.redclaw.hylianbox.tracker.model.TrackerGame
import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HudHiderTest {
    @Test
    fun hidesAndRestoresOotAndMmAcrossAllSupportedByteLanes() {
        for (game in TrackerGame.values()) {
            for (lane in listOf(0, 1, 3)) {
                val base = SaveContextParser.base(game)
                val memory = fixture(game, lane)
                val visibility = if (game == TrackerGame.OOT) 0x13E8 else 0x3F20
                putU16(memory, base, visibility + 2, lane, 50)

                val hider = HudHider()
                assertTrue(hider.onFrame(memory, game, true, false, false))
                assertEquals(6, getU16(memory, base, visibility, lane))
                assertEquals(6, getU16(memory, base, visibility + 2, lane))
                assertEquals(1, getU16(memory, base, visibility + 4, lane))

                assertFalse(hider.onFrame(memory, game, true, false, false))
                assertTrue(hider.onFrame(memory, game, false, false, false))
                assertEquals(50, getU16(memory, base, visibility, lane))
                assertEquals(50, getU16(memory, base, visibility + 2, lane))
                assertEquals(1, getU16(memory, base, visibility + 4, lane))
            }
        }
    }

    @Test
    fun controllerAndHardcorePreventHidingWithoutForcingHudAll() {
        val game = TrackerGame.OOT
        val base = SaveContextParser.base(game)
        val visibility = 0x13E8

        for ((controller, hardcore) in listOf(true to false, false to true)) {
            val memory = fixture(game, 0)
            putU16(memory, base, visibility + 2, 0, 9)

            assertFalse(HudHider().onFrame(memory, game, true, controller, hardcore))
            assertEquals(9, getU16(memory, base, visibility + 2, 0))
        }
    }

    @Test
    fun supportsRelocatedCompatibleSaveContext() {
        val game = TrackerGame.OOT
        val base = SaveContextParser.base(game) + 0x200
        val memory = fixture(game, lane = 3, base = base)

        assertTrue(HudHider().onFrame(memory, game, true, false, false, base))
        assertEquals(6, getU16(memory, base, 0x13E8, 3))
    }

    private fun fixture(
            game: TrackerGame,
            lane: Int,
            base: Int = SaveContextParser.base(game)
    ): ByteBuffer {
        val memory = ByteBuffer.allocate(base + 0x5000)
        val oot = game == TrackerGame.OOT
        val signatureOffset = if (oot) 0x1C else 0x24
        val signature = if (oot) "ZELDAZ" else "ZELDA3"
        signature.forEachIndexed { index, char ->
            memory.put((base + signatureOffset + index) xor lane, char.code.toByte())
        }
        memory.put((base + if (oot) 0x2F else 0x35) xor lane, 0x30)
        return memory
    }

    private fun getU16(memory: ByteBuffer, base: Int, offset: Int, lane: Int): Int =
            ((memory.get((base + offset) xor lane).toInt() and 0xFF) shl 8) or
                    (memory.get((base + offset + 1) xor lane).toInt() and 0xFF)

    private fun putU16(
            memory: ByteBuffer,
            base: Int,
            offset: Int,
            lane: Int,
            value: Int
    ) {
        memory.put((base + offset) xor lane, (value ushr 8).toByte())
        memory.put((base + offset + 1) xor lane, value.toByte())
    }
}
